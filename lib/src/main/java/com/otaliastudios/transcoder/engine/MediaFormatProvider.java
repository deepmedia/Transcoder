package com.otaliastudios.transcoder.engine;

import android.media.MediaCodec;
import android.media.MediaFormat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.internal.MediaCodecBuffers;
import com.otaliastudios.transcoder.source.DataSource;

import java.io.IOException;

/**
 * Formats from {@link com.otaliastudios.transcoder.source.DataSource#getTrackFormat(TrackType)}
 * might be missing important metadata information like the sample rate or bit rate.
 * These values are needed by {@link com.otaliastudios.transcoder.strategy.TrackStrategy} to
 * compute the output configuration.
 *
 * This class will check the completeness of the input format and if needed, provide a more
 * complete format by decoding the input file until MediaCodec computes all values.
 */
class MediaFormatProvider {

    /**
     * Inspects the given format - coming from {@link DataSource#getTrackFormat(TrackType)},
     * and in case it's not complete, it returns a decoded, complete format.
     *
     * @param source source
     * @param type type
     * @param format format
     * @return a complete format
     */
    @NonNull
    MediaFormat provideMediaFormat(@NonNull DataSource source,
                                   @NonNull TrackType type,
                                   @NonNull MediaFormat format) {
        // If this format is already complete, there's nothing we should do.
        if (isComplete(type, format)) {
            return format;
        }
        MediaFormat newFormat = decodeMediaFormat(source, type, format);
        // If not complete, throw an exception. If we don't throw here,
        // it would likely be thrown by strategies anyway, since they expect a
        // complete format.
        if (!isComplete(type, newFormat)) {
            String message = "Could not get a complete format!";
            message += " hasMimeType:" + newFormat.containsKey(MediaFormat.KEY_MIME);
            if (type == TrackType.VIDEO) {
                message += " hasWidth:" + newFormat.containsKey(MediaFormat.KEY_WIDTH);
                message += " hasHeight:" + newFormat.containsKey(MediaFormat.KEY_HEIGHT);
                message += " hasFrameRate:" + newFormat.containsKey(MediaFormat.KEY_FRAME_RATE);
            } else if (type == TrackType.AUDIO) {
                message += " hasChannels:" + newFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT);
                message += " hasSampleRate:" + newFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE);
            }
            throw new RuntimeException(message);
        }
        return newFormat;
    }

    private boolean isComplete(@NonNull TrackType type, @NonNull MediaFormat format) {
        switch (type) {
            case AUDIO: return isCompleteAudioFormat(format);
            case VIDEO: return isCompleteVideoFormat(format);
            default: throw new RuntimeException("Unexpected type: " + type);
        }
    }

    private boolean isCompleteVideoFormat(@NonNull MediaFormat format) {
        return format.containsKey(MediaFormat.KEY_MIME)
                && format.containsKey(MediaFormat.KEY_HEIGHT)
                && format.containsKey(MediaFormat.KEY_WIDTH)
                && format.containsKey(MediaFormat.KEY_FRAME_RATE);
    }

    private boolean isCompleteAudioFormat(@NonNull MediaFormat format) {
        return format.containsKey(MediaFormat.KEY_MIME)
                && format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)
                && format.containsKey(MediaFormat.KEY_SAMPLE_RATE);
    }

    @NonNull
    private MediaFormat decodeMediaFormat(@NonNull DataSource source,
                                          @NonNull TrackType type,
                                          @NonNull MediaFormat format) {
        source.selectTrack(type);
        MediaCodec decoder;
        try {
            decoder = MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));
            decoder.configure(format, null, null, 0);
        } catch (IOException e) {
            throw new RuntimeException("Can't decode this track", e);
        }
        decoder.start();
        MediaCodecBuffers buffers = new MediaCodecBuffers(decoder);
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        DataSource.Chunk chunk = new DataSource.Chunk();
        MediaFormat result = null;
        while (result == null) {
            result = decodeOnce(type, source, chunk, decoder, buffers, info);
        }
        source.rewind();
        return result;
    }

    @Nullable
    private MediaFormat decodeOnce(@NonNull TrackType type,
                                   @NonNull DataSource source,
                                   @NonNull DataSource.Chunk chunk,
                                   @NonNull MediaCodec decoder,
                                   @NonNull MediaCodecBuffers buffers,
                                   @NonNull MediaCodec.BufferInfo info) {
        // First drain then feed.
        MediaFormat format = drainOnce(decoder, buffers, info);
        if (format != null) return format;
        feedOnce(type, source, chunk, decoder, buffers);
        return null;
    }

    @Nullable
    private MediaFormat drainOnce(@NonNull MediaCodec decoder,
                                  @NonNull MediaCodecBuffers buffers,
                                  @NonNull MediaCodec.BufferInfo info) {
        int result = decoder.dequeueOutputBuffer(info, 0);
        switch (result) {
            case MediaCodec.INFO_TRY_AGAIN_LATER:
                return null;
            case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                return decoder.getOutputFormat();
            case MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED:
                buffers.onOutputBuffersChanged();
                return drainOnce(decoder, buffers, info);
            default: // Drop this data immediately.
                decoder.releaseOutputBuffer(result, false);
                return null;
        }
    }

    private void feedOnce(@NonNull TrackType type,
                          @NonNull DataSource source,
                          @NonNull DataSource.Chunk chunk,
                          @NonNull MediaCodec decoder,
                          @NonNull MediaCodecBuffers buffers) {
        if (!source.canReadTrack(type)) {
            throw new RuntimeException("This should never happen!");
        }
        final int result = decoder.dequeueInputBuffer(0);
        if (result < 0) return;
        chunk.buffer = buffers.getInputBuffer(result);
        source.readTrack(chunk);
        decoder.queueInputBuffer(result,
                0,
                chunk.bytes,
                chunk.timestampUs,
                chunk.isKeyFrame ? MediaCodec.BUFFER_FLAG_SYNC_FRAME : 0);
    }
}
