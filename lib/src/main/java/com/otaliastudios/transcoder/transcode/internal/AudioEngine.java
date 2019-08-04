package com.otaliastudios.transcoder.transcode.internal;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.internal.Logger;
import com.otaliastudios.transcoder.internal.MediaCodecBuffers;
import com.otaliastudios.transcoder.remix.AudioRemixer;
import com.otaliastudios.transcoder.resample.AudioResampler;
import com.otaliastudios.transcoder.resample.DefaultAudioResampler;
import com.otaliastudios.transcoder.stretch.AudioStretcher;
import com.otaliastudios.transcoder.time.TimeInterpolator;

import java.nio.Buffer;
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

    private static final int BYTES_PER_SHORT = 2;

    private static final String TAG = AudioEngine.class.getSimpleName();
    private static final Logger LOG = new Logger(TAG);
    private static final boolean ENABLE_RESAMPLING = true;

    private final Queue<AudioBuffer> mEmptyBuffers = new ArrayDeque<>();
    private final Queue<AudioBuffer> mPendingBuffers = new ArrayDeque<>();
    private final MediaCodec mDecoder;
    private final MediaCodec mEncoder;
    private final int mDecoderSampleRate;
    private final int mEncoderSampleRate;
    private final int mDecoderChannels;
    @SuppressWarnings("FieldCanBeLocal") private final int mEncoderChannels;
    private final AudioRemixer mRemixer;
    private final AudioResampler mResampler;
    private final AudioStretcher mStretcher;
    private final TimeInterpolator mTimeInterpolator;
    private long mLastDecoderUs = Long.MIN_VALUE;
    private long mLastEncoderUs = Long.MIN_VALUE;
    private ShortBuffer mTempBuffer1;
    private ShortBuffer mTempBuffer2;

    /**
     * The AudioEngine should be created when we know the actual decoded format,
     * which means that the decoder has reached {@link MediaCodec#INFO_OUTPUT_FORMAT_CHANGED}.
     *
     * @param decoder a decoder
     * @param decoderOutputFormat the decoder output format
     * @param encoder an encoder
     * @param encoderOutputFormat the encoder output format
     */
    public AudioEngine(@NonNull MediaCodec decoder,
                       @NonNull MediaFormat decoderOutputFormat,
                       @NonNull MediaCodec encoder,
                       @NonNull MediaFormat encoderOutputFormat,
                       @NonNull TimeInterpolator timeInterpolator,
                       @NonNull AudioStretcher audioStretcher) {
        mDecoder = decoder;
        mEncoder = encoder;
        mTimeInterpolator = timeInterpolator;

        // Get sample rate.
        mEncoderSampleRate = encoderOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
        mDecoderSampleRate = decoderOutputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);

        // Check channel count.
        mEncoderChannels = encoderOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        mDecoderChannels = decoderOutputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
        if (mEncoderChannels != 1 && mEncoderChannels != 2) {
            throw new UnsupportedOperationException("Output channel count (" + mEncoderChannels + ") not supported.");
        }
        if (mDecoderChannels != 1 && mDecoderChannels != 2) {
            throw new UnsupportedOperationException("Input channel count (" + mDecoderChannels + ") not supported.");
        }

        // Create remixer, stretcher and resampler.
        if (mDecoderChannels > mEncoderChannels) {
            mRemixer = AudioRemixer.DOWNMIX;
        } else if (mDecoderChannels < mEncoderChannels) {
            mRemixer = AudioRemixer.UPMIX;
        } else {
            mRemixer = AudioRemixer.PASSTHROUGH;
        }
        mStretcher = audioStretcher;
        mResampler = new DefaultAudioResampler();
    }

    /**
     * Returns true if we have raw buffers to be processed.
     * @return true if we have
     */
    private boolean hasPendingBuffers() {
        return !mPendingBuffers.isEmpty();
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
        if (buffer == null) buffer = new AudioBuffer();
        buffer.decoderBufferIndex = bufferIndex;
        buffer.decoderTimestampUs = endOfStream ? 0 : presentationTimeUs;
        buffer.decoderData = endOfStream ? null : bufferData.asShortBuffer();
        buffer.isEndOfStream = endOfStream;
        mPendingBuffers.add(buffer);
    }

    /**
     * Feeds the encoder, which in our case means processing a filled buffer,
     * then releasing it and adding it back to {@link #mEmptyBuffers}.
     *
     * @param encoderBuffers the encoder buffers
     * @param timeoutUs a timeout for this operation
     * @return true if we want to keep working
     */
    public boolean feedEncoder(@NonNull MediaCodecBuffers encoderBuffers, long timeoutUs) {
        if (!hasPendingBuffers()) return false;

        // First of all, see if encoder has buffers that we can write into.
        // If we don't have an output buffer, there's nothing we can do.
        final int encoderBufferIndex = mEncoder.dequeueInputBuffer(timeoutUs);
        if (encoderBufferIndex < 0) return false;
        ShortBuffer encoderBuffer = encoderBuffers.getInputBuffer(encoderBufferIndex).asShortBuffer();
        encoderBuffer.clear();

        // Get the latest raw buffer to be processed.
        AudioBuffer buffer = mPendingBuffers.peek();

        // When endOfStream, just signal EOS and return false.
        //noinspection ConstantConditions
        if (buffer.isEndOfStream) {
            mEncoder.queueInputBuffer(encoderBufferIndex,
                    0,
                    0,
                    0,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
            return false;
        }

        // Process the buffer.
        boolean overflows = process(buffer, encoderBuffer, encoderBufferIndex);
        if (overflows) {
            // If this buffer does overflow, we will keep it in the queue and do
            // not release. It will be used at the next cycle (presumably soon,
            // since we return true).
            return true;
        } else {
            // If this buffer does not overflow, it can be removed from our queue,
            // re-added to empty, and also released from the decoder buffers.
            mPendingBuffers.remove();
            mEmptyBuffers.add(buffer);
            mDecoder.releaseOutputBuffer(buffer.decoderBufferIndex, false);
            return true;
        }
    }

    /**
     * Processes a pending buffer.
     *
     * We have an output buffer of restricted size, and our input size can be already bigger
     * or grow after a {@link TimeInterpolator} operation or a {@link AudioRemixer} one.
     *
     * For this reason, we instead start from the output size and compute the input size that
     * would generate an output of that size. We then select this part of the input and only
     * process.
     *
     * If input is restricted, this means that the input buffer must be processed again.
     * We will return true in this case. At the next cycle, the input buffer should be identical
     * to the previous, but:
     *
     * - {@link Buffer#position()} will be increased to exclude the already processed values
     * - {@link AudioBuffer#decoderTimestampUs} will be increased to include the already processed values
     *
     * So everything should work as expected for repeated cycles.
     *
     * Before returning, this function should release the encoder buffer using
     * {@link MediaCodec#queueInputBuffer(int, int, int, long, int)}.
     *
     * @param buffer coming from decoder. Has valid limit and position
     * @param encoderBuffer coming from encoder. At this point this is in a cleared state
     * @param encoderBufferIndex the index of encoderBuffer so we can release it
     */
    private boolean process(@NonNull AudioBuffer buffer, @NonNull ShortBuffer encoderBuffer, int encoderBufferIndex) {
        // Only process the amount of data that can fill in the encoderBuffer.
        final int outputSize = encoderBuffer.remaining();
        final int inputSize = buffer.decoderData.remaining();
        int processedInputSize = inputSize;

        // 1. Perform TimeInterpolator computation
        long encoderUs = mTimeInterpolator.interpolate(TrackType.AUDIO, buffer.decoderTimestampUs);
        if (mLastDecoderUs == Long.MIN_VALUE) {
            mLastDecoderUs = buffer.decoderTimestampUs;
            mLastEncoderUs = encoderUs;
        }
        long decoderDeltaUs = buffer.decoderTimestampUs - mLastDecoderUs;
        long encoderDeltaUs = encoderUs - mLastEncoderUs;
        mLastDecoderUs = buffer.decoderTimestampUs;
        mLastEncoderUs = encoderUs;
        long stretchUs = encoderDeltaUs - decoderDeltaUs; // microseconds that the TimeInterpolator adds (or removes).
        int stretchShorts = AudioConversions.usToShorts(stretchUs, mDecoderSampleRate, mDecoderChannels);
        LOG.i("process - time stretching - decoderDeltaUs:" + decoderDeltaUs +
                " encoderDeltaUs:" + encoderDeltaUs +
                " stretchUs:" + stretchUs +
                " stretchShorts:" + stretchShorts);
        processedInputSize += stretchShorts;

        // 2. Ask remixers how much space they need for the given input
        processedInputSize = mRemixer.getRemixedSize(processedInputSize);

        // 3. After remixing we'll resample.
        // Resampling will change the input size based on the sample rate ratio.
        if (ENABLE_RESAMPLING) {
            processedInputSize = (int) Math.ceil((double) processedInputSize * mEncoderSampleRate / mDecoderSampleRate);
        }

        // 4. Compare processedInputSize and outputSize. If processedInputSize > outputSize, we overflow.
        // In this case, isolate the valid data.
        boolean overflow = processedInputSize > outputSize;
        int overflowReduction = 0;
        if (overflow) {
            // Compute the input size that matches this output size.
            double ratio = (double) processedInputSize / inputSize; // > 1
            overflowReduction = inputSize - (int) Math.floor((double) outputSize / ratio);
            LOG.w("process - overflowing! Reduction:" + overflowReduction);
            buffer.decoderData.limit(buffer.decoderData.limit() - overflowReduction);
        }
        final int finalInputSize = buffer.decoderData.remaining();
        LOG.i("process - inputSize:" + inputSize +
                " processedInputSize:" + processedInputSize +
                " outputSize:" + outputSize +
                " finalInputSize:" + finalInputSize);

        // 5. Do the stretching. We need a bridge buffer for its output.
        ensureTempBuffer1(finalInputSize + stretchShorts);
        mStretcher.stretch(buffer.decoderData, mTempBuffer1, mDecoderChannels);
        mTempBuffer1.rewind();

        // 6. Do the actual remixing.
        if (ENABLE_RESAMPLING) {
            ensureTempBuffer2(mRemixer.getRemixedSize(finalInputSize + stretchShorts));
            mRemixer.remix(mTempBuffer1, mTempBuffer2);
            mTempBuffer2.rewind();
        } else {
            mRemixer.remix(mTempBuffer1, encoderBuffer);
        }

        // 7. Do the actual resampling.
        if (ENABLE_RESAMPLING) {
            mResampler.resample(mTempBuffer2, mDecoderSampleRate, encoderBuffer, mEncoderSampleRate, mDecoderChannels);
        }

        // 7. Add the bytes we have processed to the decoderTimestampUs, and restore the limit.
        // We need an updated timestamp for the next cycle, since we will cycle on the same input
        // buffer that has overflown.
        if (overflow) {
            buffer.decoderTimestampUs += AudioConversions.shortsToUs(finalInputSize, mDecoderSampleRate, mDecoderChannels);
            buffer.decoderData.limit(buffer.decoderData.limit() + overflowReduction);
        }

        // 8. Write the buffer.
        // This is the encoder buffer: we have likely written it all, but let's use
        // encoderBuffer.position() to know how much anyway.
        mEncoder.queueInputBuffer(encoderBufferIndex,
                0,
                encoderBuffer.position() * BYTES_PER_SHORT,
                encoderUs,
                0
        );

        return overflow;
    }

    private void ensureTempBuffer1(int desiredSize) {
        LOG.w("ensureTempBuffer1 - desiredSize:" + desiredSize);
        if (mTempBuffer1 == null || mTempBuffer1.capacity() < desiredSize) {
            LOG.w("ensureTempBuffer1 - creating new buffer.");
            mTempBuffer1 = ByteBuffer.allocateDirect(desiredSize * BYTES_PER_SHORT)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();
        }
        mTempBuffer1.clear();
        mTempBuffer1.limit(desiredSize);
    }

    private void ensureTempBuffer2(int desiredSize) {
        LOG.w("ensureTempBuffer2 - desiredSize:" + desiredSize);
        if (mTempBuffer2 == null || mTempBuffer2.capacity() < desiredSize) {
            LOG.w("ensureTempBuffer2 - creating new buffer.");
            mTempBuffer2 = ByteBuffer.allocateDirect(desiredSize * BYTES_PER_SHORT)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer();
        }
        mTempBuffer2.clear();
        mTempBuffer2.limit(desiredSize);
    }
}
