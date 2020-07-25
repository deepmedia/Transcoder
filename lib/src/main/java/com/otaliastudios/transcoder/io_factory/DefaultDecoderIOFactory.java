package com.otaliastudios.transcoder.io_factory;

import android.view.Surface;

import com.otaliastudios.transcoder.transcode.internal.VideoDecoderOutput;
import com.otaliastudios.transcoder.transcode.internal.VideoEncoderInput;
import com.otaliastudios.transcoder.transcode.base.VideoDecoderOutputBase;
import com.otaliastudios.transcoder.transcode.base.VideoEncoderInputBase;

import org.jetbrains.annotations.NotNull;

public class DefaultDecoderIOFactory implements DecoderIOFactory {
    @NotNull
    @Override
    public VideoEncoderInputBase createVideoInput(@NotNull Surface surface) {
        return new VideoEncoderInput(surface);
    }

    @NotNull
    @Override
    public VideoDecoderOutputBase createVideoOutput() {
        return new VideoDecoderOutput();
    }
}
