package com.otaliastudios.transcoder.engine;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * An utility class for storing data relative to a single {@link TrackType}
 * in a map, with handy nullability annotations.
 *
 * @param <T> the map type
 */
public class TrackTypeMap<T> {

    private Map<TrackType, T> map = new HashMap<>();

    public void set(@NonNull TrackType type, @Nullable T value) {
        //noinspection ConstantConditions
        map.put(type, value);
    }

    @Nullable
    public T get(@NonNull TrackType type) {
        return map.get(type);
    }

    @NonNull
    public T require(@NonNull TrackType type) {
        //noinspection ConstantConditions
        return map.get(type);
    }

    @SuppressWarnings("WeakerAccess")
    public void clear() {
        map.clear();
    }

    public boolean has(@NonNull TrackType type) {
        return map.containsKey(type);
    }
}
