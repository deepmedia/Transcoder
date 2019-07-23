package com.otaliastudios.transcoder.transcode;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.engine.TrackType;
import com.otaliastudios.transcoder.engine.TranscoderMuxer;
import com.otaliastudios.transcoder.transcode.internal.AudioChannel;

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
    protected boolean onFeedEncoder(@NonNull MediaCodec encoder, long timeoutUs) {
        return mAudioChannel.feedEncoder(timeoutUs);
    }

    @Override
    protected void onDecoderOutputFormatChanged(@NonNull MediaFormat format) {
        super.onDecoderOutputFormatChanged(format);
        mAudioChannel.setActualDecodedFormat(format);
    }

    @Override
    protected void onDrainDecoder(@NonNull MediaCodec decoder, int bufferIndex, long presentationTimeUs, boolean endOfStream) {
        if (endOfStream) {
            mAudioChannel.drainDecoderBufferAndQueue(AudioChannel.BUFFER_INDEX_END_OF_STREAM, 0);
        } else {
            mAudioChannel.drainDecoderBufferAndQueue(bufferIndex, presentationTimeUs);
        }
    }
}
