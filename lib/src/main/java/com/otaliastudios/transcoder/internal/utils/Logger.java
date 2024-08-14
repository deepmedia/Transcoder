package com.otaliastudios.transcoder.internal.utils;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class Logger {

    public final static int LEVEL_VERBOSE = 0;

    @SuppressWarnings("WeakerAccess")
    public final static int LEVEL_INFO = 1;

    @SuppressWarnings("WeakerAccess")
    public final static int LEVEL_WARNING = 2;

    @SuppressWarnings("WeakerAccess")
    public final static int LEVEL_ERROR = 3;

    private static int sLevel = LEVEL_INFO;

    /**
     * Interface of integers representing log levels.
     * @see #LEVEL_VERBOSE
     * @see #LEVEL_INFO
     * @see #LEVEL_WARNING
     * @see #LEVEL_ERROR
     */
    @SuppressWarnings("WeakerAccess")
    @IntDef({LEVEL_VERBOSE, LEVEL_INFO, LEVEL_WARNING, LEVEL_ERROR})
    @Retention(RetentionPolicy.SOURCE)
    public @interface LogLevel {}

    private final String mTag;
    private final int mLevel;

    public Logger(@NonNull String tag) {
        mTag = tag;
        mLevel = sLevel;
    }

    public Logger(@NonNull String tag, int level) {
        mTag = tag;
        mLevel = level;
    }

    /**
     * Sets the log sLevel for logcat events.
     *
     * @see #LEVEL_VERBOSE
     * @see #LEVEL_INFO
     * @see #LEVEL_WARNING
     * @see #LEVEL_ERROR
     * @param logLevel the desired log sLevel
     */
    public static void setLogLevel(@LogLevel int logLevel) {
        sLevel = logLevel;
    }

    private boolean should(int messageLevel) {
        return mLevel <= messageLevel;
    }

    public void v(String message) { v(message, null); }

    public void i(String message) { i(message, null); }

    public void w(String message) { w(message, null); }

    public void e(String message) { e(message, null); }

    @SuppressWarnings("WeakerAccess")
    public void v(String message, @Nullable Throwable error) {
        log(LEVEL_VERBOSE, message, error);
    }

    public void i(String message, @Nullable Throwable error) {
        log(LEVEL_INFO, message, error);
    }

    public void w(String message, @Nullable Throwable error) {
        log(LEVEL_WARNING, message, error);
    }

    public void e(String message, @Nullable Throwable error) {
        log(LEVEL_ERROR, message, error);
    }

    private void log(int level, String message, @Nullable Throwable throwable) {
        if (!should(level)) return;
        switch (level) {
            case LEVEL_VERBOSE: Log.v(mTag, message, throwable); break;
            case LEVEL_INFO: Log.i(mTag, message, throwable); break;
            case LEVEL_WARNING: Log.w(mTag, message, throwable); break;
            case LEVEL_ERROR: Log.e(mTag, message, throwable); break;
        }
    }
}
