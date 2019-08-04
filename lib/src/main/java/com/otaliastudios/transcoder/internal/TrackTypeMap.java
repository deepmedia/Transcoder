package com.otaliastudios.transcoder.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.otaliastudios.transcoder.engine.TrackType;

import java.util.HashMap;
import java.util.Map;

/**
 * An utility class for storing data relative to a single {@link TrackType}
 * in a map, with handy nullability annotations.
 *
 * @param <T> the map type
 */
public class TrackTypeMap<T> {

    public TrackTypeMap() {
    }

    public TrackTypeMap(@NonNull T videoValue, @NonNull T audioValue) {
        set(TrackType.AUDIO, audioValue);
        set(TrackType.VIDEO, videoValue);
    }

    private Map<TrackType, T> map = new HashMap<>();

    public void set(@NonNull TrackType type, @Nullable T value) {
        //noinspection ConstantConditions
        map.put(type, value);
    }

    public void setAudio(@Nullable T value) {
        set(TrackType.AUDIO, value);
    }

    public void setVideo(@Nullable T value) {
        set(TrackType.VIDEO, value);
    }

    @Nullable
    public T get(@NonNull TrackType type) {
        return map.get(type);
    }

    @Nullable
    public T getAudio() {
        return get(TrackType.AUDIO);
    }

    @Nullable
    public T getVideo() {
        return get(TrackType.VIDEO);
    }

    @NonNull
    public T require(@NonNull TrackType type) {
        //noinspection ConstantConditions
        return map.get(type);
    }

    @NonNull
    public T requireAudio() {
        return require(TrackType.AUDIO);
    }

    @NonNull
    public T requireVideo() {
        return require(TrackType.VIDEO);
    }

    public boolean has(@NonNull TrackType type) {
        return map.containsKey(type);
    }

    public boolean hasAudio() {
        return has(TrackType.AUDIO);
    }

    public boolean hasVideo() {
        return has(TrackType.VIDEO);
    }
}
