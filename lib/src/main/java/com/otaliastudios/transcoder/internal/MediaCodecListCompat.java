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
package com.otaliastudios.transcoder.internal;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This class emulates basic behavior of MediaCodecList in API level &gt;= 21.
 * TODO: implement delegate to MediaCodecList in newer API.
 */
public class MediaCodecListCompat {
    public static final int REGULAR_CODECS = 0;
    public static final int ALL_CODECS = 1;

    public MediaCodecListCompat(int kind) {
        if (kind != REGULAR_CODECS) {
            throw new UnsupportedOperationException("kind other than REGULAR_CODECS is not implemented.");
        }
    }

    public final String findDecoderForFormat(MediaFormat format) {
        return findCoderForFormat(format, false);
    }

    public final String findEncoderForFormat(MediaFormat format) {
        return findCoderForFormat(format, true);
    }

    private String findCoderForFormat(MediaFormat format, boolean findEncoder) {
        String mimeType = format.getString(MediaFormat.KEY_MIME);
        Iterator<MediaCodecInfo> iterator = new MediaCodecInfoIterator();
        while (iterator.hasNext()) {
            MediaCodecInfo codecInfo = iterator.next();
            if (codecInfo.isEncoder() != findEncoder) continue;
            if (Arrays.asList(codecInfo.getSupportedTypes()).contains(mimeType)) {
                return codecInfo.getName();
            }
        }
        return null;
    }

    public final MediaCodecInfo[] getCodecInfos() {
        int codecCount = getCodecCount();
        MediaCodecInfo[] codecInfos = new MediaCodecInfo[codecCount];
        Iterator<MediaCodecInfo> iterator = new MediaCodecInfoIterator();
        for (int i = 0; i < codecCount; i++) {
            codecInfos[i] = getCodecInfoAt(i);
        }
        return codecInfos;
    }

    private static int getCodecCount() {
        return MediaCodecList.getCodecCount();
    }

    private static MediaCodecInfo getCodecInfoAt(int index) {
        return MediaCodecList.getCodecInfoAt(index);
    }

    private final class MediaCodecInfoIterator implements Iterator<MediaCodecInfo> {
        private int mCodecCount = getCodecCount();
        private int mIndex = -1;

        @Override
        public boolean hasNext() {
            return mIndex + 1 < mCodecCount;
        }

        @Override
        public MediaCodecInfo next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            mIndex++;
            return getCodecInfoAt(mIndex);
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
