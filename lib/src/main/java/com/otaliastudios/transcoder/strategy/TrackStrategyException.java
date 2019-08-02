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
package com.otaliastudios.transcoder.strategy;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Base class for exceptions thrown by {@link TrackStrategy} by any
 * strategy implementors.
 *
 * These are later caught internally.
 */
public class TrackStrategyException extends RuntimeException {

    @SuppressWarnings("WeakerAccess")
    public final static int TYPE_UNAVAILABLE = 0;

    public final static int TYPE_ALREADY_COMPRESSED = 1;

    private int type;

    @SuppressWarnings("WeakerAccess")
    public TrackStrategyException(int type, @Nullable String detailMessage) {
        super(detailMessage);
        this.type = type;
    }

    @SuppressWarnings("WeakerAccess")
    public TrackStrategyException(int type, @Nullable Exception cause) {
        super(cause);
        this.type = type;
    }

    public int getType() {
        return type;
    }

    @NonNull
    @SuppressWarnings("WeakerAccess")
    public static TrackStrategyException unavailable(@Nullable Exception cause) {
        return new TrackStrategyException(TYPE_UNAVAILABLE, cause);
    }

    @NonNull
    @SuppressWarnings("WeakerAccess")
    public static TrackStrategyException alreadyCompressed(@Nullable String detailMessage) {
        return new TrackStrategyException(TYPE_ALREADY_COMPRESSED, detailMessage);
    }
}
