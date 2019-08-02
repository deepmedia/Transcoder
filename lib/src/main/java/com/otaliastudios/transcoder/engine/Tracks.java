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

/**
 * Contains information about the tracks as read from the {@link MediaExtractor}
 * metadata, plus other track-specific information that we can store here.
 */
@SuppressWarnings("ConstantConditions")
class Tracks {

    private Tracks() { }

    private TrackTypeMap<Integer> index = new TrackTypeMap<>();
    private TrackTypeMap<String> mimeType = new TrackTypeMap<>();
    private TrackTypeMap<MediaFormat> format = new TrackTypeMap<>();
    private TrackTypeMap<TrackStatus> status = new TrackTypeMap<>();

    /**
     * The index in the input file.
     * @param type track type
     * @return index
     */
    int index(@NonNull TrackType type) {
        return index.get(type);
    }

    /**
     * The mime type in the input file.
     * @param type track type
     * @return mime type
     */
    @SuppressWarnings("unused")
    @NonNull
    String mimeType(@NonNull TrackType type) {
        return mimeType.require(type);
    }

    /**
     * The format in the input file.
     * @param type track type
     * @return format
     */
    @NonNull
    MediaFormat format(@NonNull TrackType type) {
        return format.require(type);
    }

    /**
     * The track status for this track.
     * @param type track type
     * @return track status
     */
    @NonNull
    TrackStatus status(@NonNull TrackType type) {
        return status.require(type);
    }

    /**
     * Sets the track status for this track.
     * @param type track type
     * @param status status
     */
    void status(@NonNull TrackType type, @NonNull TrackStatus status) {
        this.status.set(type, status);
    }

    /**
     * Whether the given track is present in the input file.
     * @param type track type
     * @return true if present
     */
    boolean has(@NonNull TrackType type) {
        return index(type) >= 0;
    }

    @NonNull
    static Tracks create(@NonNull MediaExtractor extractor) {
        Tracks tracks = new Tracks();
        tracks.index.set(TrackType.VIDEO, -1);
        tracks.index.set(TrackType.AUDIO, -1);
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!tracks.has(TrackType.VIDEO) && mime.startsWith("video/")) {
                tracks.index.set(TrackType.VIDEO, i);
                tracks.mimeType.set(TrackType.VIDEO, mime);
                tracks.format.set(TrackType.VIDEO, format);
            } else if (!tracks.has(TrackType.AUDIO) && mime.startsWith("audio/")) {
                tracks.index.set(TrackType.AUDIO, i);
                tracks.mimeType.set(TrackType.AUDIO, mime);
                tracks.format.set(TrackType.AUDIO, format);
            }
            if (tracks.has(TrackType.VIDEO) && tracks.has(TrackType.AUDIO)) {
                break;
            }
        }
        return tracks;
    }
}
