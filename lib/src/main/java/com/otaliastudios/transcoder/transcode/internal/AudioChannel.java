package com.otaliastudios.transcoder.transcode.internal;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.internal.MediaCodecBuffers;
import com.otaliastudios.transcoder.remix.AudioRemixer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;
import java.util.ArrayDeque;
import java.util.Queue;

/**
 * Channel of raw audio from decoder to encoder.
 * Performs the necessary conversion between different input & output audio formats.
 *
 * We currently support upmixing from mono to stereo & downmixing from stereo to mono.
 * Sample rate conversion is not supported yet.
 */
public class AudioChannel {

    private static class AudioBuffer {
        int bufferIndex;
        long presentationTimeUs;
        ShortBuffer data;
        boolean endOfStream;
    }

    private static final int BYTES_PER_SHORT = 2;
    private static final long MICROSECS_PER_SEC = 1000000;

    private final Queue<AudioBuffer> mEmptyBuffers = new ArrayDeque<>();
    private final Queue<AudioBuffer> mFilledBuffers = new ArrayDeque<>();

    private final MediaCodec mDecoder;
    private final MediaCodec mEncoder;

    private int mInputSampleRate;
    private int mOutputSampleRate;
    private int mInputChannelCount;
    private int mOutputChannelCount;

    private AudioRemixer mRemixer;

    private final AudioBuffer mOverflowBuffer = new AudioBuffer();

    public AudioChannel(@NonNull final MediaCodec decoder,
                 @NonNull final MediaCodec encoder,
                 @NonNull final MediaFormat outputFormat) {
        mDecoder = decoder;
        mEncoder = encoder;

        mOutputSampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        mOutputChannelCount = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (mOutputChannelCount != 1 && mOutputChannelCount != 2) {
            throw new UnsupportedOperationException("Output channel count (" + mOutputChannelCount + ") not supported.");
        }
    }

    /**
     * Should be the first method to be called, when we get the {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}
     * event from MediaCodec.
     * @param decoderOutputFormat the output format
     */
    public void onDecoderOutputFormat(@NonNull final MediaFormat decoderOutputFormat) {
        mInputSampleRate = decoderOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        if (mInputSampleRate != mOutputSampleRate) {
            throw new UnsupportedOperationException("Audio sample rate conversion not supported yet.");
        }
        mInputChannelCount = decoderOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (mInputChannelCount != 1 && mInputChannelCount != 2) {
            throw new UnsupportedOperationException("Input channel count (" + mInputChannelCount + ") not supported.");
        }
        if (mInputChannelCount > mOutputChannelCount) {
            mRemixer = AudioRemixer.DOWNMIX;
        } else if (mInputChannelCount < mOutputChannelCount) {
            mRemixer = AudioRemixer.UPMIX;
        } else {
            mRemixer = AudioRemixer.PASSTHROUGH;
        }
        mOverflowBuffer.presentationTimeUs = 0;
    }

    /**
     * Drains the decoder, which means scheduling data for processing.
     * We fill a new {@link AudioBuffer} with data (not a copy, just a view of it)
     * and add it to the filled buffers queue.
     *
     * @param bufferIndex the buffer index
     * @param bufferData the buffer data
     * @param presentationTimeUs the presentation time
     * @param endOfStream true if end of stream
     */
    public void drainDecoder(final int bufferIndex,
                             @NonNull ByteBuffer bufferData,
                             final long presentationTimeUs,
                             final boolean endOfStream) {
        if (mRemixer == null) throw new RuntimeException("Buffer received before format!");
        AudioBuffer buffer = mEmptyBuffers.poll();
        if (buffer == null) {
            buffer = new AudioBuffer();
        }
        buffer.bufferIndex = bufferIndex;
        buffer.presentationTimeUs = endOfStream ? 0 : presentationTimeUs;
        buffer.data = endOfStream ? null : bufferData.asShortBuffer();
        buffer.endOfStream = endOfStream;

        if (mOverflowBuffer.data == null) {
            mOverflowBuffer.data = ByteBuffer.allocateDirect(bufferData.capacity())
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();
            mOverflowBuffer.data.clear().flip();
        }
        mFilledBuffers.add(buffer);
    }

    /**
     * Feeds the encoder, which in our case means processing a filled buffer from {@link #mFilledBuffers},
     * then releasing it and adding it back to {@link #mEmptyBuffers}.
     *
     * @param encoderBuffers the encoder buffers
     * @param timeoutUs a timeout for this operation
     * @return true if we want to keep working
     */
    public boolean feedEncoder(@NonNull MediaCodecBuffers encoderBuffers, long timeoutUs) {
        final boolean hasOverflow = mOverflowBuffer.data != null && mOverflowBuffer.data.hasRemaining();
        if (mFilledBuffers.isEmpty() && !hasOverflow) {
            // No audio data - Bail out
            return false;
        }

        final int encoderInBuffIndex = mEncoder.dequeueInputBuffer(timeoutUs);
        if (encoderInBuffIndex < 0) {
            // Encoder is full - Bail out
            return false;
        }

        // Drain overflow first
        final ShortBuffer outBuffer = encoderBuffers.getInputBuffer(encoderInBuffIndex).asShortBuffer();
        if (hasOverflow) {
            final long presentationTimeUs = drainOverflow(outBuffer);
            mEncoder.queueInputBuffer(encoderInBuffIndex,
                    0, outBuffer.position() * BYTES_PER_SHORT,
                    presentationTimeUs, 0);
            return true;
        }

        final AudioBuffer inBuffer = mFilledBuffers.poll();
        // At this point inBuffer is not null, because we checked mFilledBuffers.isEmpty()
        // and we don't have overflow (if we had, we got out).
        //noinspection ConstantConditions
        if (inBuffer.endOfStream) {
            mEncoder.queueInputBuffer(encoderInBuffIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return false;
        }

        final long presentationTimeUs = remixAndMaybeFillOverflow(inBuffer, outBuffer);
        mEncoder.queueInputBuffer(encoderInBuffIndex,
                0,
                outBuffer.position() * BYTES_PER_SHORT,
                presentationTimeUs,
                0);
        mDecoder.releaseOutputBuffer(inBuffer.bufferIndex, false);
        mEmptyBuffers.add(inBuffer);
        return true;
    }

    private static long sampleCountToDurationUs(
            final int sampleCount,
            final int sampleRate,
            final int channelCount) {
        return (sampleCount / (sampleRate * MICROSECS_PER_SEC)) / channelCount;
    }

    private long drainOverflow(@NonNull final ShortBuffer outBuff) {
        final ShortBuffer overflowBuff = mOverflowBuffer.data;
        final int overflowLimit = overflowBuff.limit();
        final int overflowSize = overflowBuff.remaining();

        final long beginPresentationTimeUs = mOverflowBuffer.presentationTimeUs +
                sampleCountToDurationUs(overflowBuff.position(),
                        mInputSampleRate,
                        mOutputChannelCount);

        outBuff.clear();
        // Limit overflowBuff to outBuff's capacity
        overflowBuff.limit(outBuff.capacity());
        // Load overflowBuff onto outBuff
        outBuff.put(overflowBuff);

        if (overflowSize >= outBuff.capacity()) {
            // Overflow fully consumed - Reset
            overflowBuff.clear().limit(0);
        } else {
            // Only partially consumed - Keep position & restore previous limit
            overflowBuff.limit(overflowLimit);
        }

        return beginPresentationTimeUs;
    }

    private long remixAndMaybeFillOverflow(final AudioBuffer input,
                                           final ShortBuffer outBuff) {
        final ShortBuffer inBuff = input.data;
        final ShortBuffer overflowBuff = mOverflowBuffer.data;

        outBuff.clear();

        // Reset position to 0, and set limit to capacity (Since MediaCodec doesn't do that for us)
        inBuff.clear();

        if (inBuff.remaining() > outBuff.remaining()) {
            // Overflow
            // Limit inBuff to outBuff's capacity
            inBuff.limit(outBuff.capacity());
            mRemixer.remix(inBuff, outBuff);

            // Reset limit to its own capacity & Keep position
            inBuff.limit(inBuff.capacity());

            // Remix the rest onto overflowBuffer
            // NOTE: We should only reach this point when overflow buffer is empty
            final long consumedDurationUs =
                    sampleCountToDurationUs(inBuff.position(), mInputSampleRate, mInputChannelCount);
            mRemixer.remix(inBuff, overflowBuff);

            // Seal off overflowBuff & mark limit
            overflowBuff.flip();
            mOverflowBuffer.presentationTimeUs = input.presentationTimeUs + consumedDurationUs;
        } else {
            // No overflow
            mRemixer.remix(inBuff, outBuff);
        }

        return input.presentationTimeUs;
    }
}
