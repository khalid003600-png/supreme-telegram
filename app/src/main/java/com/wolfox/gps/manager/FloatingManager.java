package com.wolfox.gps.manager;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.WindowInsets;
import android.graphics.Insets;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.ui.ActivationDialog;
import com.wolfox.gps.ui.WFDesign;
import com.wolfox.gps.ui.WolFoxPanel;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.io.InputStream;
import java.lang.ref.WeakReference;

/** Draggable circular control attached only to the host Activity DecorView. */
public final class FloatingManager {
    private static final String TAG = "FloatingManager";
    private static final long CLICK_THRESHOLD_MS = 240L;
    private static final long LONG_PRESS_HIDE_MS = 720L;
    private static final int NAVY = WFDesign.BACKGROUND;
    private static final int GREEN = WFDesign.SUCCESS;
    private static final int RED = WFDesign.DANGER;
    private static final String CONTROL_TAG = "WFX_CONTROL_311";
    private static final FloatingManager INSTANCE = new FloatingManager();

    private static final class AccessibleFloatingButton extends FrameLayout {
        AccessibleFloatingButton(Context context) { super(context); }

        @Override public boolean performClick() {
            super.performClick();
            return true;
        }
    }

    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<Activity> currentActivity = new WeakReference<>(null);
    private WeakReference<View> floatingButton = new WeakReference<>(null);
    private boolean visible;
    private boolean attached;
    private float initialX;
    private float initialY;
    private float initialTouchX;
    private float initialTouchY;
    private long downAt;
    private int attachToken;
    private boolean longPressTriggered;
    private Runnable pendingLongPress;

    private FloatingManager() {}

    public static FloatingManager getInstance() { return INSTANCE; }

    private Activity currentActivity() { return currentActivity.get(); }

    private View floatingButton() { return floatingButton.get(); }

    private void setCurrentActivity(Activity activity) {
        currentActivity = new WeakReference<>(activity);
    }

    private void setFloatingButton(View button) {
        floatingButton = new WeakReference<>(button);
    }

    public void onActivityResumed(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        setCurrentActivity(activity);
        restoreRequestedVisibility(activity);
    }

    /** Restores only an explicit request in this process; never creates a show request. */
    public void restoreRequestedVisibility(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        setCurrentActivity(activity);
        if (WFStorage.getInstance(activity).isBubbleVisible()
                && !ToolControlManager.getInstance().isBlocked(activity)) {
            visible = true;
            scheduleAttach(activity);
        } else {
            hide(false);
        }
    }

    public void onActivityPaused(Activity activity) {
        if (currentActivity() != activity) return;
        attachToken++;
        detachFromDecorView();
        currentActivity.clear();
    }

    public void onActivityDestroyed(Activity activity) {
        if (activity == null || currentActivity() != activity) return;
        attachToken++;
        detachFromDecorView();
        currentActivity.clear();
    }

    public void show(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()
                || ToolControlManager.getInstance().isBlocked(activity)) return;
        setCurrentActivity(activity);
        visible = true;
        WFStorage.getInstance(activity).setBubbleVisible(true);
        scheduleAttach(activity);
    }

    public void hide() { hide(true); }

    public void hide(boolean persist) {
        visible = false;
        final int token = ++attachToken;
        cancelButtonLongPress();
        Activity activity = currentActivity();
        if (persist && activity != null) {
            WFStorage.getInstance(activity).setBubbleVisible(false);
        }
        main.post(new Runnable() {
            @Override public void run() {
                if (token == attachToken && !visible) detachFromDecorView();
            }
        });
    }

    public void requestHideConfirmation(Activity owner) {
        final Activity activity = owner == null ? currentActivity() : owner;
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(new Runnable() {
            @Override public void run() {
                if (activity.isFinishing()) return;
                new AlertDialog.Builder(activity)
                        .setTitle("إخفاء أيقونة WolFox")
                        .setMessage("يمكنك إظهارها مجددًا بالضغط المطوّل في منتصف الشاشة.")
                        .setPositiveButton("إخفاء", (dialog, which) -> {
                            WolFoxPanel.dismiss();
                            hide(true);
                            Toast.makeText(activity,
                                    "للإظهار: اضغط مطوّلًا في منتصف الشاشة",
                                    Toast.LENGTH_LONG).show();
                        })
                        .setNegativeButton("إلغاء", null)
                        .show();
            }
        });
    }

    public boolean isVisible() {
        View button = floatingButton();
        return visible && attached && button != null
                && button.getParent() != null
                && button.getVisibility() == View.VISIBLE;
    }

    public Activity getCurrentActivity() { return currentActivity(); }

    /** Refreshes the full outer ring immediately when spoofing changes. */
    public void refreshAppearance() {
        main.post(new Runnable() {
            @Override public void run() {
                View button = floatingButton();
                Activity activity = currentActivity();
                if (button != null && activity != null) {
                    applyStateRing(button, activity);
                    button.setAlpha(WFStorage.getInstance(activity).getBubbleOpacity());
                    button.invalidate();
                }
            }
        });
    }

    private View buildFloatingButton(Context context) {
        WFStorage storage = WFStorage.getInstance(context);
        int size = dp(context, storage.getBubbleSizeDp());
        int ringWidth = Math.max(dp(context, 3), Math.round(size * 0.065f));

        FrameLayout root = new AccessibleFloatingButton(context);
        root.setPadding(ringWidth, ringWidth, ringWidth, ringWidth);
        root.setElevation(dp(context, 12));
        root.setAlpha(storage.getBubbleOpacity());
        root.setTag(CONTROL_TAG);
        root.setContentDescription("WolFox GPS v3.1.1");
        applyStateRing(root, context);

        Bitmap icon = loadModuleIcon(context);
        if (icon != null) {
            ImageView image = new ImageView(context);
            image.setImageBitmap(icon);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            image.setBackground(circle(NAVY, NAVY, 0));
            image.setClipToOutline(true);
            root.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        } else {
            TextView fallback = new TextView(context);
            fallback.setText(CONTROL_TAG.substring(0, 2));
            fallback.setTextColor(0xFFFFFFFF);
            fallback.setTextSize(Math.max(12, storage.getBubbleSizeDp() / 3f));
            fallback.setGravity(android.view.Gravity.CENTER);
            fallback.setBackground(circle(NAVY, NAVY, 0));
            WFTheme.apply(fallback, true);
            root.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        root.setOnClickListener(view -> openPanel());
        root.setOnTouchListener(buildTouchListener());
        return root;
    }

    private static Bitmap loadModuleIcon(Context context) {
        InputStream stream = null;
        try {
            ClassLoader loader = FloatingManager.class.getClassLoader();
            if (loader != null) stream = loader.getResourceAsStream("assets/wolfox_icon.png");
            if (stream == null) stream = context.getAssets().open("wolfox_icon.png");
            return BitmapFactory.decodeStream(stream);
        } catch (Throwable error) {
            WFLog.d(TAG, "icon fallback: " + error.getMessage());
            return null;
        } finally {
            if (stream != null) {
                try { stream.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static void applyStateRing(View view, Context context) {
        boolean active = GPSMockManager.getInstance().isMocking();
        int edge = active ? GREEN : RED;
        view.setContentDescription(active
                ? "WolFox GPS: محاكاة موقع نشطة، موقع تجريبي قابل للكشف"
                : "WolFox GPS: محاكاة الموقع متوقفة");
        int width = Math.max(dp(context, 3),
                Math.round(WFStorage.getInstance(context).getBubbleSizeDp()
                        * context.getResources().getDisplayMetrics().density * 0.065f));
        view.setBackground(circle(NAVY, edge, width));
        view.setClipToOutline(true);
    }

    private void scheduleAttach(Activity activity) {
        final int token = ++attachToken;
        final WeakReference<Activity> owner = new WeakReference<>(activity);
        main.post(new Runnable() {
            @Override public void run() { attachWithRetry(owner, token, 0); }
        });
    }

    private void attachWithRetry(final WeakReference<Activity> owner, final int token,
            final int attempt) {
        Activity activity = owner.get();
        if (token != attachToken || currentActivity() != activity || !visible
                || activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        boolean added = attachToActivity(activity, token);
        View button = floatingButton();
        if ((!added || button == null || button.getParent() == null)
                && attempt < 4) {
            main.postDelayed(new Runnable() {
                @Override public void run() {
                    attachWithRetry(owner, token, attempt + 1);
                }
            }, 250L * (attempt + 1));
        }
    }

    private boolean attachToActivity(Activity activity, int token) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed() || !visible) return false;
        View content = activity.findViewById(android.R.id.content);
        View decor = activity.getWindow() == null ? null : activity.getWindow().getDecorView();
        final ViewGroup parent = content instanceof ViewGroup
                ? (ViewGroup) content
                : (decor instanceof ViewGroup ? (ViewGroup) decor : null);
        if (parent == null) return false;

        final WFStorage storage = WFStorage.getInstance(activity);
        int size = dp(activity, storage.getBubbleSizeDp());
        View existing = floatingButton();
        if (existing != null && existing.getParent() == parent
                && existing.getLayoutParams() != null
                && existing.getLayoutParams().width == size
                && existing.getLayoutParams().height == size) {
            attached = true;
            existing.setVisibility(View.VISIBLE);
            existing.bringToFront();
            refreshAppearance();
            return true;
        }

        detachFromDecorView();
        View button = buildFloatingButton(activity);
        setFloatingButton(button);
        try {
            parent.addView(button, new FrameLayout.LayoutParams(size, size));
        } catch (Throwable error) {
            WFLog.e(TAG, "attach failed: " + error.getMessage());
            floatingButton.clear();
            attached = false;
            return false;
        }
        attached = true;
        button.setVisibility(View.VISIBLE);
        button.bringToFront();
        parent.invalidate();
        final WeakReference<View> buttonReference = new WeakReference<>(button);
        final WeakReference<ViewGroup> parentReference = new WeakReference<>(parent);
        button.post(new Runnable() {
            @Override public void run() {
                View attachedButton = buttonReference.get();
                ViewGroup attachedParent = parentReference.get();
                if (token != attachToken || attachedButton == null || attachedParent == null
                        || floatingButton() != attachedButton
                        || attachedButton.getParent() != attachedParent) return;
                float[] bounds = safeBounds(attachedParent, attachedButton);
                attachedButton.setX(bounds[0] + (bounds[2] - bounds[0]) * storage.getBubbleXFraction());
                attachedButton.setY(bounds[1] + (bounds[3] - bounds[1]) * storage.getBubbleYFraction());
                attachedButton.setScaleX(0.72f);
                attachedButton.setScaleY(0.72f);
                attachedButton.animate().scaleX(1f).scaleY(1f)
                        .alpha(storage.getBubbleOpacity()).setDuration(220L).start();
                attachedButton.bringToFront();
            }
        });
        WFLog.i(TAG, "circular control attached");
        return true;
    }

    private void detachFromDecorView() {
        cancelButtonLongPress();
        View button = floatingButton();
        try {
            if (button != null && button.getParent() instanceof ViewGroup) {
                ((ViewGroup) button.getParent()).removeView(button);
            }
        } catch (Exception ignored) {}
        if (button != null) {
            button.animate().cancel();
            button.clearAnimation();
            button.setOnTouchListener(null);
            button.setOnClickListener(null);
        }
        floatingButton.clear();
        attached = false;
    }

    private View.OnTouchListener buildTouchListener() {
        return new View.OnTouchListener() {
            @Override public boolean onTouch(final View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        cancelButtonLongPress();
                        longPressTriggered = false;
                        initialX = view.getX();
                        initialY = view.getY();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        downAt = System.currentTimeMillis();
                        view.animate().scaleX(0.92f).scaleY(0.92f).setDuration(80L).start();
                        final WeakReference<View> buttonReference = new WeakReference<>(view);
                        pendingLongPress = new Runnable() {
                            @Override public void run() {
                                View button = buttonReference.get();
                                Activity activity = currentActivity();
                                if (floatingButton() != button || button == null
                                        || button.getParent() == null || activity == null
                                        || activity.isFinishing() || activity.isDestroyed()) return;
                                longPressTriggered = true;
                                pendingLongPress = null;
                                button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                                requestHideConfirmation(activity);
                            }
                        };
                        main.postDelayed(pendingLongPress, LONG_PRESS_HIDE_MS);
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (!(view.getParent() instanceof ViewGroup)) return true;
                        float moved = Math.abs(event.getRawX() - initialTouchX)
                                + Math.abs(event.getRawY() - initialTouchY);
                        if (moved > dp(view.getContext(), 14)) cancelButtonLongPress();
                        ViewGroup parent = (ViewGroup) view.getParent();
                        float x = initialX + event.getRawX() - initialTouchX;
                        float y = initialY + event.getRawY() - initialTouchY;
                        float[] bounds = safeBounds(parent, view);
                        view.setX(Math.max(bounds[0], Math.min(x, bounds[2])));
                        view.setY(Math.max(bounds[1], Math.min(y, bounds[3])));
                        return true;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        cancelButtonLongPress();
                        view.animate().scaleX(1f).scaleY(1f).setDuration(90L).start();
                        if (longPressTriggered) {
                            longPressTriggered = false;
                            return true;
                        }
                        float distance = Math.abs(event.getRawX() - initialTouchX)
                                + Math.abs(event.getRawY() - initialTouchY);
                        if (event.getActionMasked() == MotionEvent.ACTION_UP
                                && System.currentTimeMillis() - downAt <= CLICK_THRESHOLD_MS
                                && distance < dp(view.getContext(), 14)) {
                            view.performClick();
                        } else if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                            snapAndSave(view);
                        }
                        return true;
                    default:
                        return false;
                }
            }
        };
    }

    private void cancelButtonLongPress() {
        if (pendingLongPress != null) main.removeCallbacks(pendingLongPress);
        pendingLongPress = null;
    }

    private void snapAndSave(View view) {
        if (!(view.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) view.getParent();
        float[] bounds = safeBounds(parent, view);
        float maxX = bounds[2] - bounds[0];
        float maxY = bounds[3] - bounds[1];
        float targetX = view.getX() + view.getWidth() / 2f < parent.getWidth() / 2f
                ? bounds[0] : bounds[2];
        ObjectAnimator.ofFloat(view, "x", targetX).setDuration(180L).start();
        Activity activity = currentActivity();
        if (activity != null) {
            WFStorage.getInstance(activity).saveBubblePosition(
                    maxX == 0 ? 0.5f : (targetX - bounds[0]) / maxX,
                    maxY == 0 ? 0.5f : (view.getY() - bounds[1]) / maxY);
        }
    }

    /** Coordinates relative to parent; only subtract bars when content actually draws behind them. */
    private static float[] safeBounds(ViewGroup parent, View button) {
        float left = 0f, top = 0f, right = parent.getWidth(), bottom = parent.getHeight();
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsets wi = parent.getRootWindowInsets();
            if (wi != null) {
                Insets bars = wi.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout());
                int[] parentScreen = new int[2];
                parent.getLocationOnScreen(parentScreen);
                View root = parent.getRootView();
                int[] rootScreen = new int[2];
                root.getLocationOnScreen(rootScreen);
                // Insets are window-relative; avoid applying top inset twice to a content view
                // already laid out below the status bar.
                left = Math.max(0f, rootScreen[0] + bars.left - parentScreen[0]);
                top = Math.max(0f, rootScreen[1] + bars.top - parentScreen[1]);
                right = Math.min(right, rootScreen[0] + root.getWidth() - bars.right - parentScreen[0]);
                bottom = Math.min(bottom, rootScreen[1] + root.getHeight() - bars.bottom - parentScreen[1]);
            }
        }
        right = Math.max(left, right - button.getWidth());
        bottom = Math.max(top, bottom - button.getHeight());
        return new float[]{left, top, right, bottom};
    }

    private void openPanel() {
        Activity activity = currentActivity();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (LicenseManager.getInstance().isActive(activity)) {
            WolFoxPanel.show(activity);
        } else {
            ActivationDialog.showRequired(activity);
        }
    }

    private static GradientDrawable circle(int fill, int stroke, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(fill);
        if (strokeWidth > 0) drawable.setStroke(strokeWidth, stroke);
        return drawable;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
