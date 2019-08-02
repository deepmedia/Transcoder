/*
 * Copyright (C) 2015 Yuya Tanaka
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
package com.otaliastudios.transcoder.sink;

import androidx.annotation.NonNull;

import com.otaliastudios.transcoder.Transcoder;
import com.otaliastudios.transcoder.TranscoderListener;
import com.otaliastudios.transcoder.TranscoderOptions;

/**
 * One of the exceptions possibly thrown by
 * {@link Transcoder#transcode(TranscoderOptions)}, which means it can be
 * passed to {@link TranscoderListener#onTranscodeFailed(Throwable)}.
 */
public class InvalidOutputFormatException extends RuntimeException {
    InvalidOutputFormatException(@NonNull String detailMessage) {
        super(detailMessage);
    }
}
