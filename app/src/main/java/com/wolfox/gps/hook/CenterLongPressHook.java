package com.wolfox.gps.hook;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;

import com.wolfox.gps.DirectBootstrap;
import com.wolfox.gps.util.WFStorage;

import java.lang.ref.WeakReference;

/** Observes, but never consumes, an 850 ms press in the center of the host Activity. */
final class CenterLongPressHook {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static WeakReference<Activity> owner = new WeakReference<>(null);
    private static float downX;
    private static float downY;
    private static boolean armed;
    private static Runnable pending;
    private static int revealToken;

    private CenterLongPressHook() {}

    static void observe(Activity activity, MotionEvent event) {
        if (activity == null || event == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (WFStorage.getInstance(activity).isBubbleVisible()) { cancel(); return; }
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        if (decor == null || decor.getWidth() <= 0 || decor.getHeight() <= 0) return;

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancel();
                final int token = ++revealToken;
                float centerX = decor.getWidth() / 2f;
                float centerY = decor.getHeight() / 2f;
                float radius = Math.max(dp(activity, 54), Math.min(decor.getWidth(), decor.getHeight()) * 0.12f);
                if (Math.abs(event.getX() - centerX) <= radius
                        && Math.abs(event.getY() - centerY) <= radius) {
                    owner = new WeakReference<>(activity);
                    downX = event.getX();
                    downY = event.getY();
                    armed = true;
                    pending = new Runnable() {
                        @Override public void run() {
                            Activity current = owner.get();
                            if (token != revealToken || !armed || current == null
                                    || current.isFinishing() || current.isDestroyed()) return;
                            armed = false;
                            pending = null;
                            DirectBootstrap.showControls(current);
                        }
                    };
                    MAIN.postDelayed(pending, 850L);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (armed && (Math.abs(event.getX() - downX) > dp(activity, 22)
                        || Math.abs(event.getY() - downY) > dp(activity, 22))) cancel();
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancel();
                break;
            default:
                break;
        }
    }

    static void cancel() {
        armed = false;
        revealToken++;
        if (pending != null) MAIN.removeCallbacks(pending);
        pending = null;
        owner.clear();
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
