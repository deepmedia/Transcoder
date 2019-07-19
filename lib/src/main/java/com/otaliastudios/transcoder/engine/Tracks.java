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

import java.util.HashMap;
import java.util.Map;

/**
 * Contains information about the tracks as read from the {@link MediaExtractor}
 * metadata, plus other track-specific information that we can store here.
 */
@SuppressWarnings("ConstantConditions")
class Tracks {

    private Tracks() { }

    private Map<TrackType, Integer> index = new HashMap<>();
    private Map<TrackType, Integer> outputIndex = new HashMap<>();
    private Map<TrackType, String> mimeType = new HashMap<>();
    private Map<TrackType, MediaFormat> format = new HashMap<>();
    private Map<TrackType, MediaFormat> outputFormat = new HashMap<>();
    private Map<TrackType, TrackStatus> status = new HashMap<>();

    /**
     * The index in the input file.
     * @param type track type
     * @return index
     */
    int index(@NonNull TrackType type) {
        return index.get(type);
    }

    /**
     * The index in the output file.
     * Must be set with {@link #outputIndex(TrackType, int)}.
     * @param type track type
     * @return index
     */
    int outputIndex(@NonNull TrackType type) {
        return outputIndex.get(type);
    }

    /**
     * Sets the index in the output file.
     * @param type track type
     * @param index index
     */
    void outputIndex(@NonNull TrackType type, int index) {
        outputIndex.put(type, index);
    }

    /**
     * The mime type in the input file.
     * @param type track type
     * @return mime type
     */
    @SuppressWarnings("unused")
    @NonNull
    String mimeType(@NonNull TrackType type) {
        return mimeType.get(type);
    }

    /**
     * The format in the input file.
     * @param type track type
     * @return format
     */
    @NonNull
    MediaFormat format(@NonNull TrackType type) {
        return format.get(type);
    }

    /**
     * The format in the output file. This is nullable!
     * Must be set using {@link #outputFormat(TrackType, MediaFormat)}
     * when we know the actual output format, as returned by MediaCodec.
     * @param type track type
     * @return output format
     */
    @Nullable
    MediaFormat outputFormat(@NonNull TrackType type) {
        return outputFormat.get(type);
    }

    /**
     * Sets the format in the output file.
     * @param type track type
     * @param format output format
     */
    void outputFormat(@NonNull TrackType type, @NonNull MediaFormat format) {
        outputFormat.put(type, format);
    }

    /**
     * The track status for this track.
     * @param type track type
     * @return track status
     */
    @NonNull
    TrackStatus status(@NonNull TrackType type) {
        return status.get(type);
    }

    /**
     * Sets the track status for this track.
     * @param type track type
     * @param status status
     */
    void status(@NonNull TrackType type, @NonNull TrackStatus status) {
        this.status.put(type, status);
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
        tracks.index.put(TrackType.VIDEO, -1);
        tracks.index.put(TrackType.AUDIO, -1);
        int trackCount = extractor.getTrackCount();
        for (int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (!tracks.has(TrackType.VIDEO) && mime.startsWith("video/")) {
                tracks.index.put(TrackType.VIDEO, i);
                tracks.mimeType.put(TrackType.VIDEO, mime);
                tracks.format.put(TrackType.VIDEO, format);
            } else if (!tracks.has(TrackType.AUDIO) && mime.startsWith("audio/")) {
                tracks.index.put(TrackType.AUDIO, i);
                tracks.mimeType.put(TrackType.AUDIO, mime);
                tracks.format.put(TrackType.AUDIO, format);
            }
            if (tracks.has(TrackType.VIDEO) && tracks.has(TrackType.AUDIO)) {
                break;
            }
        }
        return tracks;
    }
}
