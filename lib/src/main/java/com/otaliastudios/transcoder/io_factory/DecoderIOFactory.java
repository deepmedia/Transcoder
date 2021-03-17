package com.otaliastudios.transcoder.io_factory;

import android.view.Surface;

import com.otaliastudios.transcoder.transcode.base.VideoDecoderOutputBase;
import com.otaliastudios.transcoder.transcode.base.VideoEncoderInputBase;

import org.jetbrains.annotations.NotNull;

public interface DecoderIOFactory {
    @NotNull VideoEncoderInputBase createVideoInput(@NotNull Surface surface);
    @NotNull VideoDecoderOutputBase createVideoOutput();
}
