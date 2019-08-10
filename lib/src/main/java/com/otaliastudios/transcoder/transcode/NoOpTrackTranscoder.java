/*
 * Copyright (C) 2014 Yuya Tanaka
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.otaliastudios.transcoder.transcode;

import android.media.MediaFormat;

import androidx.annotation.NonNull;

public class NoOpTrackTranscoder implements TrackTranscoder {

    public NoOpTrackTranscoder() {
    }

    @Override
    public void setUp(@NonNull MediaFormat desiredOutputFormat) {
    }

    @Override
    public boolean transcode(boolean forceInputEos) {
        return false;
    }

    @Override
    public boolean isFinished() {
        return true;
    }

    @Override
    public void release() {
    }
}
