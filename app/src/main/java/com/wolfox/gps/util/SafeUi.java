package com.wolfox.gps.util;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

/** Guard optional UI operations, without replacing the host's uncaught-exception handler. */
public final class SafeUi {
    private SafeUi() {}
    public static void run(Context context, String operation, Runnable action) {
        if (context == null) return;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) return;
        }
        try { action.run(); }
        catch (RuntimeException | LinkageError failure) { report(context, operation, failure); }
    }
    public static void report(Context context, String operation, Throwable failure) {
        WFLog.e("Compatibility", operation + ": " + failure.getClass().getSimpleName());
        try {
            Toast.makeText(context, "تعذر " + operation + "؛ تحقق من الصلاحيات وتوافق بيئة التحميل", Toast.LENGTH_LONG).show();
        } catch (RuntimeException ignored) { /* The host may already be finishing. */ }
    }
}
