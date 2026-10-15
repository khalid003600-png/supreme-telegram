package com.wolfox.gps.util;

import android.util.Log;

/**
 * WFLog — مركزية السجلات للموديول
 */
public class WFLog {

    private static final String TAG_PREFIX = "WolFox|";
    private static boolean debugEnabled = false;

    public static void d(String tag, String msg) {
        if (debugEnabled) Log.d(TAG_PREFIX + tag, msg);
    }

    public static void i(String tag, String msg) {
        if (debugEnabled) Log.i(TAG_PREFIX + tag, msg);
    }

    public static void w(String tag, String msg) {
        if (debugEnabled) Log.w(TAG_PREFIX + tag, msg);
    }

    public static void e(String tag, String msg) {
        Log.e(TAG_PREFIX + tag, "operation failed");
    }

    public static void e(String tag, String msg, Throwable t) {
        Log.e(TAG_PREFIX + tag, "operation failed");
    }

    public static void hook(String method, Object result) {
        if (debugEnabled) {
            Log.d(TAG_PREFIX + "Hook", method + " → " + result);
        }
    }

    public static void setDebug(boolean enabled) {
        debugEnabled = enabled;
    }
}
