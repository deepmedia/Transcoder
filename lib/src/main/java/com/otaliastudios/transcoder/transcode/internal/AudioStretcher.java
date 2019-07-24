package com.otaliastudios.transcoder.transcode.internal;

import androidx.annotation.NonNull;

import java.nio.Buffer;
import java.nio.ShortBuffer;

interface AudioStretcher {

    /**
     * Stretches the input into the output, based on the {@link Buffer#remaining()} value of both.
     * At the end of this method, the {@link Buffer#position()} of both should be equal to their
     * respective {@link Buffer#limit()}.
     *
     * And of course, both {@link Buffer#limit()}s should remain unchanged.
     *
     * @param input input buffer
     * @param output output buffer
     * @param channels audio channels
     */
    void stretch(@NonNull ShortBuffer input, @NonNull ShortBuffer output, int channels);

    AudioStretcher PASSTHROUGH = new AudioStretcher() {
        @Override
        public void stretch(@NonNull ShortBuffer input, @NonNull ShortBuffer output, int channels) {
            if (input.remaining() > output.remaining()) {
                throw new IllegalArgumentException("Illegal use of AudioStretcher.PASSTHROUGH");
            }
            output.put(input);
        }
    };

    AudioStretcher CUT = new AudioStretcher() {
        @Override
        public void stretch(@NonNull ShortBuffer input, @NonNull ShortBuffer output, int channels) {
            if (input.remaining() < output.remaining()) {
                throw new IllegalArgumentException("Illegal use of AudioStretcher.CUT");
            }
            int exceeding = input.remaining() - output.remaining();
            input.limit(input.limit() - exceeding); // Make remaining() the same for both
            output.put(input); // Safely bulk-put
            input.limit(input.limit() + exceeding); // Restore
            input.position(input.limit()); // Make as if we have read it all
        }
    };

    AudioStretcher INSERT = new AudioStretcher() {
        @Override
        public void stretch(@NonNull ShortBuffer input, @NonNull ShortBuffer output, int channels) {
            if (input.remaining() >= output.remaining()) {
                throw new IllegalArgumentException("Illegal use of AudioStretcher.INSERT");
            }
            throw new RuntimeException("AudioStretcher.INSERT not implemented yet.");
        }
    };

    AudioStretcher CUT_OR_INSERT = new AudioStretcher() {
        @Override
        public void stretch(@NonNull ShortBuffer input, @NonNull ShortBuffer output, int channels) {
            if (input.remaining() < output.remaining()) {
                INSERT.stretch(input, output, channels);
            } else if (input.remaining() > output.remaining()) {
                CUT.stretch(input, output, channels);
            } else {
                PASSTHROUGH.stretch(input, output, channels);
            }
        }
    };
}
