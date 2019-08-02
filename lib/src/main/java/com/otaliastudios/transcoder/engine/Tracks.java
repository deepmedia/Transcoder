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
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.source.DataSource;

/**
 * Contains information about the tracks as read from the {@link MediaExtractor}
 * metadata, plus other track-specific information that we can store here.
 */
class Tracks {

    private Tracks() { }

    private TrackTypeMap<String> mimeType = new TrackTypeMap<>();
    private TrackTypeMap<MediaFormat> format = new TrackTypeMap<>();
    private TrackTypeMap<TrackStatus> status = new TrackTypeMap<>();

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
    @Nullable
    MediaFormat format(@NonNull TrackType type) {
        return format.get(type);
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
        return format(type) != null;
    }

    @NonNull
    static Tracks create(@NonNull DataSource source) {
        Tracks tracks = new Tracks();
        MediaFormat audioFormat = source.getFormat(TrackType.AUDIO);
        tracks.format.set(TrackType.AUDIO, audioFormat);
        if (audioFormat != null) {
            tracks.mimeType.set(TrackType.AUDIO, audioFormat.getString(MediaFormat.KEY_MIME));
        }
        MediaFormat videoFormat = source.getFormat(TrackType.VIDEO);
        tracks.format.set(TrackType.VIDEO, videoFormat);
        if (videoFormat != null) {
            tracks.mimeType.set(TrackType.VIDEO, videoFormat.getString(MediaFormat.KEY_MIME));
        }
        return tracks;
    }
}
