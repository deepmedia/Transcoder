package com.otaliastudios.transcoder.transcode;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.engine.TranscoderMuxer;
import com.otaliastudios.transcoder.internal.MediaCodecBuffers;
import com.otaliastudios.transcoder.transcode.internal.AudioChannel;

import java.nio.ByteBuffer;

public class AudioTrackTranscoder extends BaseTrackTranscoder {

    private AudioChannel mAudioChannel;

    public AudioTrackTranscoder(@NonNull MediaExtractor extractor,
                                int trackIndex,
                                @NonNull TranscoderMuxer muxer) {
        super(extractor, trackIndex, muxer, TrackType.AUDIO);
    }

    @Override
    protected void onCodecsStarted(@NonNull MediaFormat inputFormat, @NonNull MediaFormat outputFormat, @NonNull MediaCodec decoder, @NonNull MediaCodec encoder) {
        super.onCodecsStarted(inputFormat, outputFormat, decoder, encoder);
        mAudioChannel = new AudioChannel(decoder, encoder, outputFormat);
    }

    @Override
    protected boolean onFeedEncoder(@NonNull MediaCodec encoder, @NonNull MediaCodecBuffers encoderBuffers, long timeoutUs) {
        return mAudioChannel.feedEncoder(encoderBuffers, timeoutUs);
    }

    @Override
    protected void onDecoderOutputFormatChanged(@NonNull MediaFormat format) {
        super.onDecoderOutputFormatChanged(format);
        mAudioChannel.onDecoderOutputFormat(format);
    }

    @Override
    protected void onDrainDecoder(@NonNull MediaCodec decoder, int bufferIndex, @NonNull ByteBuffer bufferData, long presentationTimeUs, boolean endOfStream) {
        mAudioChannel.drainDecoder(bufferIndex, bufferData, presentationTimeUs, endOfStream);
    }
}
