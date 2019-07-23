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
public class AudioEngine {

    private static class AudioBuffer {
        int bufferIndex;
        long presentationTimeUs;
        ShortBuffer data;
        boolean endOfStream;
    }

    private static final int BYTES_PER_SAMPLE_PER_CHANNEL = 2; // Assuming 16bit audio, so 2
    private static final int BYTES_PER_SHORT = 2;
    private static final long MICROSECONDS_PER_SECOND = 1000000L;

    private static long bytesToUs(
            int bytes /* [bytes] */,
            int sampleRate /* [samples/sec] */,
            int channels /* [channel] */
    ) {
        int byteRatePerChannel = sampleRate * BYTES_PER_SAMPLE_PER_CHANNEL; // [bytes/sec/channel]
        int byteRate = byteRatePerChannel * channels; // [bytes/sec]
        return MICROSECONDS_PER_SECOND * bytes / byteRate; // [usec]
    }

    private static long shortsToUs(
            int shorts,
            int sampleRate,
            int channels) {
        return bytesToUs(shorts * BYTES_PER_SHORT, sampleRate, channels);
    }

    private final Queue<AudioBuffer> mEmptyBuffers = new ArrayDeque<>();
    private final Queue<AudioBuffer> mFilledBuffers = new ArrayDeque<>();
    private final MediaCodec mDecoder;
    private final MediaCodec mEncoder;
    private final AudioBuffer mOverflowBuffer = new AudioBuffer();
    private final int mSampleRate;
    private final int mInputChannelCount;
    private final int mOutputChannelCount;
    private final AudioRemixer mRemixer;

    /**
     * The AudioEngine should be created when we know the actual decoded format,
     * which means that the decoder has reached {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}.
     *
     * @param decoder a decoder
     * @param decoderOutputFormat the decoder output format
     * @param encoder an encoder
     * @param encoderOutputFormat the encoder output format
     */
    public AudioEngine(@NonNull final MediaCodec decoder,
                       @NonNull final MediaFormat decoderOutputFormat,
                       @NonNull final MediaCodec encoder,
                       @NonNull final MediaFormat encoderOutputFormat) {
        mDecoder = decoder;
        mEncoder = encoder;

        // Get and check sample rate.
        int outputSampleRate = encoderOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        int inputSampleRate = decoderOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        if (inputSampleRate != outputSampleRate) {
            throw new UnsupportedOperationException("Audio sample rate conversion not supported yet.");
        }
        mSampleRate = inputSampleRate;

        // Check channel count.
        mOutputChannelCount = encoderOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        mInputChannelCount = decoderOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (mOutputChannelCount != 1 && mOutputChannelCount != 2) {
            throw new UnsupportedOperationException("Output channel count (" + mOutputChannelCount + ") not supported.");
        }
        if (mInputChannelCount != 1 && mInputChannelCount != 2) {
            throw new UnsupportedOperationException("Input channel count (" + mInputChannelCount + ") not supported.");
        }

        // Create remixer.
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
        final boolean hasOverflow = hasOverflow();
        final boolean hasBuffers = hasBuffers();
        if (!hasBuffers && !hasOverflow) return false;

        // Prepare to encode - see if encoder has buffers.
        final int encoderBufferIndex = mEncoder.dequeueInputBuffer(timeoutUs);
        if (encoderBufferIndex < 0) return false;
        ShortBuffer encoderBuffer = encoderBuffers.getInputBuffer(encoderBufferIndex).asShortBuffer();

        // If we have overflow data, process that first.
        if (hasOverflow) {
            long presentationTimeUs = drainOverflow(encoderBuffer);
            mEncoder.queueInputBuffer(encoderBufferIndex,
                    0,
                    encoderBuffer.position() * BYTES_PER_SHORT,
                    presentationTimeUs,
                    0);
            return true;
        }

        // At this point buffer is not null, because we checked hasBuffers()
        // and we don't have overflow (if we had, we got out).
        final AudioBuffer decoderBuffer = mFilledBuffers.poll();

        // When endOfStream, just signal EOS and return false.
        //noinspection ConstantConditions
        if (decoderBuffer.endOfStream) {
            mEncoder.queueInputBuffer(encoderBufferIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return false;
        }

        // If not, process data and return true.
        process(decoderBuffer.data, encoderBuffer, decoderBuffer.presentationTimeUs);
        mEncoder.queueInputBuffer(encoderBufferIndex,
                0,
                encoderBuffer.position() * BYTES_PER_SHORT,
                decoderBuffer.presentationTimeUs,
                0);
        mDecoder.releaseOutputBuffer(decoderBuffer.bufferIndex, false);
        mEmptyBuffers.add(decoderBuffer);
        return true;
    }

    /**
     * Returns true if we have overflow data to be drained and set to the encoder.
     * The overflow data is already processed.
     *
     * @return true if data
     */
    private boolean hasOverflow() {
        return mOverflowBuffer.data != null && mOverflowBuffer.data.hasRemaining();
    }

    /**
     * Returns true if we have filled buffers to be processed.
     * @return true if we have
     */
    private boolean hasBuffers() {
        return !mFilledBuffers.isEmpty();
    }

    /**
     * Drains the overflow data into the given {@link ShortBuffer}.The overflow data is
     * already processed, it must just be copied into the given buffer.
     *
     * @param outBuffer output buffer
     * @return the frame timestamp
     */
    private long drainOverflow(@NonNull final ShortBuffer outBuffer) {
        final ShortBuffer overflowBuffer = mOverflowBuffer.data;
        final int overflowLimit = overflowBuffer.limit();
        final int overflowSize = overflowBuffer.remaining();
        final long beginPresentationTimeUs = mOverflowBuffer.presentationTimeUs +
                shortsToUs(overflowBuffer.position(),
                        mSampleRate,
                        mOutputChannelCount);
        outBuffer.clear();
        overflowBuffer.limit(outBuffer.capacity()); // Limit overflowBuffer to outBuffer's capacity
        outBuffer.put(overflowBuffer); // Load overflowBuffer onto outBuffer
        if (overflowSize >= outBuffer.capacity()) {
            overflowBuffer.clear().limit(0); // Overflow fully consumed - Reset
        } else {
            overflowBuffer.limit(overflowLimit); // Only partially consumed - Keep position & restore previous limit
        }

        return beginPresentationTimeUs;
    }

    private void process(@NonNull final ShortBuffer inputBuffer,
                         @NonNull final ShortBuffer outputBuffer,
                         long inputPresentationTimeUs) {
        // Reset position to 0 and set limit to capacity (Since MediaCodec doesn't do that for us)
        outputBuffer.clear();
        inputBuffer.clear();

        if (inputBuffer.remaining() <= outputBuffer.remaining()) {
            // Safe case. Just remix.
            mRemixer.remix(inputBuffer, outputBuffer);
        } else {
            // Overflow!
            // First remix all we can.
            inputBuffer.limit(outputBuffer.capacity());
            mRemixer.remix(inputBuffer, outputBuffer);
            inputBuffer.limit(inputBuffer.capacity());

            // Then remix the rest into mOverflowBuffer.
            // NOTE: We should only reach this point when overflow buffer is empty
            long consumedDurationUs = shortsToUs(inputBuffer.position(), mSampleRate, mInputChannelCount);
            mRemixer.remix(inputBuffer, mOverflowBuffer.data);

            // Flip the overflow buffer and mark the presentation time.
            mOverflowBuffer.data.flip();
            mOverflowBuffer.presentationTimeUs = inputPresentationTimeUs + consumedDurationUs;

        }
    }
}
