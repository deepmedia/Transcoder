package com.otaliastudios.transcoder.internal.utils;

import androidx.annotation.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ISO6709LocationParser {
    private final Pattern pattern;

    public ISO6709LocationParser() {
        this.pattern = Pattern.compile("([+\\-][0-9.]+)([+\\-][0-9.]+)");
    }

    /**
     * This method parses the given string representing a geographic point location by coordinates in ISO 6709 format
     * and returns the latitude and the longitude in float. If <code>location</code> is not in ISO 6709 format,
     * this method returns <code>null</code>
     *
     * @param location a String representing a geographic point location by coordinates in ISO 6709 format
     * @return <code>null</code> if the given string is not as expected, an array of floats with size 2,
     * where the first element represents latitude and the second represents longitude, otherwise.
     */
    @Nullable
    public float[] parse(@Nullable String location) {
        if (location == null) return null;
        Matcher m = pattern.matcher(location);
        if (m.find() && m.groupCount() == 2) {
            String latstr = m.group(1);
            String lonstr = m.group(2);
            try {
                float lat = Float.parseFloat(latstr);
                float lon = Float.parseFloat(lonstr);
                return new float[]{lat, lon};
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }
}
