/*
 * Copyright (C) 2016 Yuya Tanaka
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
package com.otaliastudios.transcoder.engine.internal;

import androidx.annotation.NonNull;

import java.nio.ByteBuffer;

public class AvcSpsUtils {
    // Refer: http://en.wikipedia.org/wiki/H.264/MPEG-4_AVC#Profiles
    public static final byte PROFILE_IDC_BASELINE = 66;

    @SuppressWarnings("WeakerAccess")
    public static final byte PROFILE_IDC_EXTENDED = 88;

    @SuppressWarnings("WeakerAccess")
    public static final byte PROFILE_IDC_MAIN = 77;

    @SuppressWarnings("WeakerAccess")
    public static final byte PROFILE_IDC_HIGH = 100;

    public static byte getProfileIdc(@NonNull ByteBuffer spsBuffer) {
        // Refer: http://www.cardinalpeak.com/blog/the-h-264-sequence-parameter-set/
        // First byte after NAL.
        return spsBuffer.get(0);
    }

    @NonNull
    public static String getProfileName(byte profileIdc) {
        switch (profileIdc) {
            case PROFILE_IDC_BASELINE: return "Baseline Profile";
            case PROFILE_IDC_EXTENDED: return "Extended Profile";
            case PROFILE_IDC_MAIN: return "Main Profile";
            case PROFILE_IDC_HIGH: return "High Profile";
            default: return "Unknown Profile (" + profileIdc + ")";
        }
    }
}
