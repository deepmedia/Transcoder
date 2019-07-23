package com.otaliastudios.transcoder.transcode;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.engine.TranscoderMuxer;
import com.otaliastudios.transcoder.internal.MediaCodecBuffers;
import com.otaliastudios.transcoder.time.TimeInterpolator;
import com.otaliastudios.transcoder.transcode.internal.AudioEngine;

import java.nio.ByteBuffer;

public class AudioTrackTranscoder extends BaseTrackTranscoder {

    private TimeInterpolator mTimeInterpolator;
    private AudioEngine mAudioEngine;
    private MediaCodec mEncoder; // to create the channel
    private MediaFormat mEncoderOutputFormat; // to create the channel

    public AudioTrackTranscoder(@NonNull MediaExtractor extractor,
                                @NonNull TranscoderMuxer muxer,
                                int trackIndex,
                                @NonNull TimeInterpolator timeInterpolator) {
        super(extractor, muxer, TrackType.AUDIO, trackIndex);
        mTimeInterpolator = timeInterpolator;
    }

    @Override
    protected void onCodecsStarted(@NonNull MediaFormat inputFormat, @NonNull MediaFormat outputFormat, @NonNull MediaCodec decoder, @NonNull MediaCodec encoder) {
        super.onCodecsStarted(inputFormat, outputFormat, decoder, encoder);
        mEncoder = encoder;
        mEncoderOutputFormat = outputFormat;
    }

    @Override
    protected boolean onFeedEncoder(@NonNull MediaCodec encoder, @NonNull MediaCodecBuffers encoderBuffers, long timeoutUs) {
        if (mAudioEngine == null) return false;
        return mAudioEngine.feedEncoder(encoderBuffers, timeoutUs);
    }

    @Override
    protected void onDecoderOutputFormatChanged(@NonNull MediaCodec decoder, @NonNull MediaFormat format) {
        super.onDecoderOutputFormatChanged(decoder, format);
        mAudioEngine = new AudioEngine(decoder, format, mEncoder, mEncoderOutputFormat, mTimeInterpolator);
        mEncoder = null;
        mEncoderOutputFormat = null;
        mTimeInterpolator = null;
    }

    @Override
    protected void onDrainDecoder(@NonNull MediaCodec decoder, int bufferIndex, @NonNull ByteBuffer bufferData, long presentationTimeUs, boolean endOfStream) {
        mAudioEngine.drainDecoder(bufferIndex, bufferData, presentationTimeUs, endOfStream);
    }
}
