package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.manager.FloatingManager;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.manager.LicenseManager;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;
import com.wolfox.gps.util.WFLog;

import java.util.Locale;

/** Compact menu: header, expandable rectangular map, status and essential controls. */
public final class WolFoxPanel {
    private static final int BG = WFDesign.BACKGROUND;
    private static final int CARD = WFDesign.SURFACE;
    private static final int CARD_2 = WFDesign.SURFACE_RAISED;
    private static final int BLUE = WFDesign.PRIMARY;
    private static final int WHITE = WFDesign.TEXT;
    private static final int MUTED = WFDesign.MUTED;
    private static final int GREEN = WFDesign.SUCCESS;
    private static final int RED = WFDesign.DANGER;
    private static Dialog currentDialog;

    private WolFoxPanel() {}

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        if (!LicenseManager.getInstance().isActive(activity)) {
            ActivationDialog.showRequired(activity);
            return;
        }
        activity.runOnUiThread(() -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            dismiss();
            currentDialog = buildDialog(activity);
            currentDialog.show();
            Window window = currentDialog.getWindow();
            if (window != null) {
                window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                        dialogHeight(activity));
                window.setGravity(Gravity.CENTER);
            }
        });
    }

    public static void dismiss() {
        try {
            if (currentDialog != null && currentDialog.isShowing()) currentDialog.dismiss();
        } catch (Throwable ignored) {}
        currentDialog = null;
    }

    private static Dialog buildDialog(final Activity activity) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) window.setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 13));
        root.setBackground(round(BG, 22, WFDesign.BORDER, 1));

        root.addView(header(activity, dialog));

        final MapDialog.Preview preview = MapDialog.createPreview(activity, v -> {
            dialog.dismiss();
            MapDialog.show(activity);
        });
        LinearLayout.LayoutParams mapLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, 155));
        mapLp.setMargins(0, dp(activity, 10), 0, dp(activity, 8));
        root.addView(preview.getView(), mapLp);

        View status = statusCard(activity);
        status.setOnClickListener(v -> copyCoordinates(activity));
        final LinearLayout statusSlot = new LinearLayout(activity);
        statusSlot.setOrientation(LinearLayout.VERTICAL);
        statusSlot.setTag("wolfox.spoof.status");
        statusSlot.addView(status);
        root.addView(statusSlot);

        final boolean running = GPSMockManager.getInstance().isMocking();
        Button toggle = button(activity,
                running ? "إيقاف التزييف" : "تزييف الموقع", RED, Color.WHITE);
        toggle.setTextSize(16);
        toggle.setContentDescription(running ? "إيقاف التزييف" : "تزييف الموقع");
        toggle.setTag("wolfox.spoof.toggle");
        toggle.setMinHeight(dp(activity, 54));
        toggle.setMinimumHeight(dp(activity, 54));
        LinearLayout.LayoutParams toggleLp = fullWrap();
        toggleLp.setMargins(0, dp(activity, 8), 0, dp(activity, 8));
        root.addView(toggle, toggleLp);

        final TextView feedback = text(activity, "", 12, MUTED, false);
        feedback.setTag("wolfox.spoof.feedback");
        feedback.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        feedback.setPadding(dp(activity, 6), 0, dp(activity, 6), dp(activity, 8));
        feedback.setVisibility(View.GONE);
        root.addView(feedback, fullWrap());
        final long[] lastClickAt = {-1L};
        toggle.setOnClickListener(v -> {
            if (!toggle.isEnabled() || activity.isFinishing() || activity.isDestroyed()) return;
            long now = SystemClock.uptimeMillis();
            if (lastClickAt[0] >= 0L && now - lastClickAt[0] < 500L) return;
            lastClickAt[0] = now;
            toggle.setEnabled(false);
            toggle.setText("جاري التحقق…");
            GPSMockManager manager = GPSMockManager.getInstance();
            try {
                if (manager.isMocking()) {
                    manager.stopMocking();
                    showFeedback(feedback, "تم إيقاف تزييف الموقع", MUTED);
                } else {
                    switch (manager.requestVerifiedStart()) {
                        case LOCATION_REQUIRED:
                            showFeedback(feedback, "حدد موقعًا أولًا من الخريطة أو البحث", RED);
                            break;
                        case LICENSE_REQUIRED:
                            manager.stopMocking();
                            dialog.dismiss();
                            ActivationDialog.showRequired(activity);
                            return;
                        case RUNTIME_UNAVAILABLE:
                            showFeedback(feedback,
                                    "تعذر التزييف: الموديول غير محمّل أو هوك الموقع لم يستجب. افتح التطبيق المصرّح به داخل بيئة التحميل المتوافقة؛ تثبيت APK وحده لا يكفي.", RED);
                            break;
                        case STARTED:
                            showFeedback(feedback, "تزييف الموقع نشط؛ علامة Mock ظاهرة للتطبيق", GREEN);
                            break;
                    }
                }
            } catch (RuntimeException error) {
                manager.stopMocking();
                WFLog.e("WolFoxPanel", "spoof control failed", error);
                showFeedback(feedback, "تعذر تشغيل التزييف؛ أُوقفت الجلسة. حاول مجددًا بعد التحقق من بيئة التحميل.", RED);
            } finally {
                boolean active = manager.isMocking();
                String label = active ? "إيقاف التزييف" : "تزييف الموقع";
                toggle.setText(label);
                toggle.setContentDescription(label);
                WFDesign.colorButton(toggle, label, RED, Color.WHITE);
                toggle.setEnabled(true);
                statusSlot.removeAllViews();
                View updated = statusCard(activity);
                updated.setOnClickListener(view -> copyCoordinates(activity));
                statusSlot.addView(updated);
            }
        });

        root.addView(row(activity,
                action(activity, WFTheme.icon("⌕") + "  بحث", v -> {
                    dialog.dismiss();
                    SearchDialog.show(activity);
                }),
                action(activity, WFTheme.icon("⌖") + "  موقعي", v -> useDeviceLocation(activity, dialog))));

        root.addView(row(activity,
                action(activity, WFTheme.icon("★") + "  المفضلة", v -> {
                    dialog.dismiss();
                    FavoritesDialog.show(activity);
                }),
                action(activity, WFTheme.icon("＋") + "  حفظ الموقع", v -> saveCurrentLocation(activity))));

        root.addView(row(activity,
                action(activity, WFTheme.icon("◷") + "  الاشتراك", v -> {
                    dialog.dismiss();
                    SubscriptionDialog.show(activity);
                }),
                action(activity, WFTheme.icon("⚙") + "  الإعدادات", v -> {
                    dialog.dismiss();
                    SettingsDialog.show(activity);
                })));

        Button hideBubble = action(activity, WFTheme.icon("○") + "  إخفاء الأيقونة",
                v -> FloatingManager.getInstance().requestHideConfirmation(activity));
        LinearLayout.LayoutParams hideBubbleLp = fullWrap();
        hideBubbleLp.setMargins(0, 0, 0, dp(activity, 6));
        hideBubble.setLayoutParams(hideBubbleLp);
        root.addView(hideBubble);

        TextView footer = text(activity,
                String.format(Locale.ROOT,
                        "تزييف داخل التطبيق • لا يحتاج خيارات المطوّر • علامة Mock ظاهرة • v%s",
                        ModuleConfig.VERSION_NAME),
                10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerLp = fullWrap();
        footerLp.setMargins(0, dp(activity, 5), 0, 0);
        root.addView(footer, footerLp);

        WFDesign.moveCloseToFooter(root);
        WFTheme.applyTree(root);
        ScrollView scroll = new ScrollView(activity);
        scroll.setTag("wolfox.panel.scroll");
        scroll.setFillViewport(false);
        scroll.setClipToPadding(false);
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(ignored -> {
            preview.destroy();
            if (currentDialog == dialog) currentDialog = null;
        });
        return dialog;
    }

    private static void showFeedback(TextView view, String message, int color) {
        view.setText(message);
        view.setTextColor(color);
        view.setVisibility(View.VISIBLE);
    }

    private static void saveCurrentLocation(Activity activity) {
        WFLocation location = GPSMockManager.getInstance().getLocation();
        WFLocationActions.promptFavorite(activity, location,
                location == null ? "" : location.getLabel());
    }

    private static View header(Context context, Dialog dialog) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView badge = text(context, "WF", 13, WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setBackground(round(BLUE, 22, WFDesign.BORDER, 1));
        row.addView(badge, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));

        LinearLayout titles = new LinearLayout(context);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(context, "WolFox GPS", 18, WHITE, true));
        titles.addView(text(context, "لوحة الموقع الذكية", 11, MUTED, false));
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        titleLp.setMargins(dp(context, 10), 0, 0, 0);
        row.addView(titles, titleLp);

        Button close = WFDesign.closeButton(context);
        close.setOnClickListener(v -> dialog.dismiss());
        row.addView(close, new LinearLayout.LayoutParams(dp(context, 44), dp(context, 44)));
        return row;
    }

    private static View statusCard(Context context) {
        boolean running = GPSMockManager.getInstance().isMocking();
        WFLocation location = GPSMockManager.getInstance().getLocation();
        WFStorage storage = WFStorage.getInstance(context);

        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(context, 12), dp(context, 8), dp(context, 12), dp(context, 8));
        card.setBackground(round(CARD, 14, running ? GREEN : RED, 1));
        card.setClickable(true);
        card.setFocusable(true);
        card.setMinimumHeight(dp(context, 48));
        card.setContentDescription(running ? "تزييف الموقع نشط؛ اضغط لنسخ الإحداثيات"
                : "تزييف الموقع متوقف؛ اضغط لنسخ الإحداثيات");

        card.addView(text(context, running ? "●  تزييف الموقع نشط" : "●  تزييف الموقع متوقف",
                14, running ? GREEN : RED, true));
        String selectedName = location == null ? "لم يتم اختيار موقع" : location.getLabel();
        if (selectedName == null || selectedName.trim().isEmpty()) selectedName = "الموقع المحدد";
        TextView name = text(context, selectedName, 13, WHITE, true);
        name.setTag("wolfox.selected.name");
        card.addView(name);
        TextView coordinates = text(context,
                location == null ? "لم يتم اختيار موقع"
                        : String.format(Locale.US, "%.7f, %.7f",
                        location.getLatitude(), location.getLongitude()),
                13, WHITE, false);
        coordinates.setTag("wolfox.selected.coordinates");
        coordinates.setTextDirection(View.TEXT_DIRECTION_LTR);
        coordinates.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        coordinates.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        card.addView(coordinates);
        card.addView(text(context,
                storage.getLicenseSummaryText() + " • اضغط لنسخ الإحداثيات",
                10, MUTED, false));
        card.addView(text(context,
                running ? "موقع تجريبي — لا تُخفى علامة mock عن التطبيق"
                        : "عند التوقف لا تُعدّل بيانات الموقع",
                10, MUTED, false));
        return card;
    }

    private static void useDeviceLocation(Activity activity, Dialog dialog) {
        GPSMockManager.getInstance().stopMocking();
        WFLocation found = GPSMockManager.getInstance().useCurrentDeviceLocation(activity);
        if (found == null) {
            Toast.makeText(activity, "تعذر قراءة الموقع الحالي؛ تحقق من إذن الموقع",
                    Toast.LENGTH_LONG).show();
            dialog.dismiss();
            show(activity);
            return;
        }
        Toast.makeText(activity, "تم تحديد موقع الجهاز", Toast.LENGTH_SHORT).show();
        dialog.dismiss();
        show(activity);
    }

    private static LinearLayout row(Context context, Button left, Button right) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(left, weighted(context, 44, 6));
        row.addView(right, weighted(context, 44, 0));
        LinearLayout.LayoutParams params = fullWrap();
        params.setMargins(0, 0, 0, dp(context, 6));
        row.setLayoutParams(params);
        return row;
    }

    private static Button action(Context context, String label, View.OnClickListener listener) {
        Button button = button(context, label, CARD_2, WHITE);
        button.setOnClickListener(listener);
        return button;
    }

    private static void copyCoordinates(Activity activity) {
        WFLocation location = GPSMockManager.getInstance().getLocation();
        WFLocationActions.copyCoordinates(activity, location);
    }

    private static TextView text(Context context, String value, int size, int color, boolean bold) {
        return WFDesign.text(context, value, size, color, bold);
    }

    private static Button button(Context context, String value, int background, int color) {
        return WFDesign.button(context, value, background, color);
    }

    private static GradientDrawable round(int fill, int radiusDp, int stroke, int strokeDp) {
        return WFDesign.rounded(fill, radiusDp, stroke, strokeDp);
    }

    private static LinearLayout.LayoutParams full(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weighted(Context context, int heightDp, int endMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, dp(context, endMarginDp), 0);
        return params;
    }

    private static int dialogHeight(Activity activity) {
        return (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.86f);
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
