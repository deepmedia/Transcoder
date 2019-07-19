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
package com.otaliastudios.transcoder.engine;

import android.media.MediaExtractor;
import android.media.MediaFormat;

import androidx.annotation.NonNull;

@SuppressWarnings("WeakerAccess")
public class TracksInfo {

    private TracksInfo() { }

    public int videoTrackIndex;
    public String videoTrackMime;
    public MediaFormat videoTrackFormat;

    public int audioTrackIndex;
    public String audioTrackMime;
    public MediaFormat audioTrackFormat;

    // These refer to the compression (not the metadata) and are set
    // internally, then consumed by the queued muxer.
    public TrackStatus videoTrackStatus;
    public TrackStatus audioTrackStatus;

    public boolean hasAudio() {
        return audioTrackIndex >= 0;
    }

    public boolean hasVideo() {
        return videoTrackIndex >= 0;
    }

    @NonNull
    public static TracksInfo fromExtractor(@NonNull MediaExtractor extractor) {
        TracksInfo trackResult = new TracksInfo();
        trackResult.videoTrackIndex = -1;
        trackResult.audioTrackIndex = -1;
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (trackResult.videoTrackIndex < 0 && mime.startsWith("video/")) {
                trackResult.videoTrackIndex = i;
                trackResult.videoTrackMime = mime;
                trackResult.videoTrackFormat = format;
            } else if (trackResult.audioTrackIndex < 0 && mime.startsWith("audio/")) {
                trackResult.audioTrackIndex = i;
                trackResult.audioTrackMime = mime;
                trackResult.audioTrackFormat = format;
            }
            if (trackResult.videoTrackIndex >= 0 && trackResult.audioTrackIndex >= 0) break;
        }
        return trackResult;
    }
}
