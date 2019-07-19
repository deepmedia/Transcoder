package com.otaliastudios.transcoder.remix;

import androidx.annotation.NonNull;

import java.nio.ShortBuffer;

/**
 * The simplest {@link AudioRemixer} that does nothing.
 */
public class PassThroughAudioRemixer implements AudioRemixer {

    @Override
    public void remix(@NonNull final ShortBuffer inSBuff, @NonNull final ShortBuffer outSBuff) {
        // Passthrough
        outSBuff.put(inSBuff);
    }
}
