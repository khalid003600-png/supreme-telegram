package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.manager.FloatingManager;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.manager.LicenseManager;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Dedicated, read-only subscription details with an explicit server verification action. */
public final class SubscriptionDialog {
    private SubscriptionDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> buildAndShow(activity));
    }

    private static void buildAndShow(Activity activity) {
        WFStorage storage = WFStorage.getInstance(activity);
        boolean active = storage.isLicenseActive();
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.CENTER);
            window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity, 18), dp(activity, 16), dp(activity, 18), dp(activity, 16));
        root.setBackground(WFDesign.rounded(WFDesign.BACKGROUND, 20, WFDesign.BORDER, 1));

        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = WFDesign.text(activity, WFTheme.icon("◷") + "  الاشتراك", 18,
                WFDesign.ACCENT, true);
        header.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = WFDesign.closeButton(activity);
        close.setOnClickListener(v -> {
            dialog.dismiss();
            returnToParent(activity, storage);
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        root.addView(header);

        TextView badge = WFDesign.text(activity, localizedStatus(storage.getLicenseStatus()), 14,
                statusColor(storage.getLicenseStatus()), true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        badge.setBackground(WFDesign.rounded(WFDesign.SURFACE, 12, WFDesign.BORDER, 1));
        LinearLayout.LayoutParams badgeLp = fullWrap();
        badgeLp.setMargins(0, dp(activity, 12), 0, dp(activity, 8));
        root.addView(badge, badgeLp);

        TextView details = WFDesign.text(activity, detailsWithoutStatus(storage), 14,
                WFDesign.TEXT, false);
        details.setLineSpacing(dp(activity, 3), 1f);
        details.setPadding(dp(activity, 14), dp(activity, 12), dp(activity, 14), dp(activity, 12));
        details.setBackground(WFDesign.rounded(WFDesign.SURFACE, 14, WFDesign.BORDER, 1));
        root.addView(details, fullWrap());

        TextView checkedAt = WFDesign.text(activity, checkedAtText(storage), 11,
                WFDesign.MUTED, false);
        LinearLayout.LayoutParams checkedLp = fullWrap();
        checkedLp.setMargins(0, dp(activity, 8), 0, dp(activity, 12));
        root.addView(checkedAt, checkedLp);

        Button verify = WFDesign.button(activity, "تحقق من الاشتراك الآن",
                WFDesign.PRIMARY, WFDesign.TEXT);
        verify.setOnClickListener(v -> {
            String code = storage.getLicenseCode();
            if (code == null || code.trim().isEmpty()) {
                Toast.makeText(activity, "لا يوجد كود محفوظ للتحقق", Toast.LENGTH_SHORT).show();
                return;
            }
            verify.setEnabled(false);
            LicenseManager.getInstance().verify(activity, code, (isActive, state, message) -> {
                verify.setEnabled(true);
                boolean localActive = storage.isLicenseActive();
                badge.setText(localizedStatus(storage.getLicenseStatus()));
                badge.setTextColor(statusColor(storage.getLicenseStatus()));
                details.setText(detailsWithoutStatus(storage));
                checkedAt.setText(checkedAtText(storage));
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                if (!localActive) {
                    GPSMockManager.getInstance().stopMocking();
                    FloatingManager.getInstance().hide(false);
                    dialog.dismiss();
                    ActivationDialog.showRequired(activity);
                } else if (isActive) {
                    Toast.makeText(activity, "الاشتراك نشط", Toast.LENGTH_SHORT).show();
                }
            });
        });
        root.addView(verify, full(ViewGroup.LayoutParams.WRAP_CONTENT));

        Button activation = WFDesign.button(activity, "إدخال أو إعادة تفعيل كود",
                WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        LinearLayout.LayoutParams activationLp = full(ViewGroup.LayoutParams.WRAP_CONTENT);
        activationLp.setMargins(0, dp(activity, 8), 0, 0);
        root.addView(activation, activationLp);
        activation.setEnabled(!active);
        activation.setAlpha(active ? 0.55f : 1f);
        activation.setOnClickListener(v -> {
            dialog.dismiss();
            ActivationDialog.showRequired(activity);
        });

        WFDesign.moveCloseToFooter(root);
        WFTheme.applyTree(root);
        android.widget.ScrollView scroll = new android.widget.ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(root, new android.widget.ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scroll);
        dialog.setOnDismissListener(ignored -> {});
        if (window != null) {
            window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        dialog.show();
        if (window != null) window.setLayout(
                (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.92f),
                dialogHeight(activity));
    }

    private static String detailsWithoutStatus(WFStorage storage) {
        String text = storage.getLicenseDetailsText();
        int newline = text.indexOf('\n');
        return newline >= 0 ? text.substring(newline + 1) : text;
    }

    private static String checkedAtText(WFStorage storage) {
        long checked = storage.getLicenseCheckedAt();
        return checked > 0L
                ? String.format(Locale.getDefault(), "آخر تحقق: %s",
                        new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                                .format(new Date(checked)))
                : "لم يُتحقق من الاشتراك بعد";
    }

    private static String localizedStatus(String status) {
        if ("Active".equalsIgnoreCase(status)) return "نشط";
        if ("Expired".equalsIgnoreCase(status)) return "منتهي";
        if ("Disabled".equalsIgnoreCase(status)) return "موقوف";
        if ("InvalidCode".equalsIgnoreCase(status)) return "الكود غير صالح";
        if ("TransferRequired".equalsIgnoreCase(status)) return "يتطلب نقلًا";
        return status == null || status.trim().isEmpty() ? "غير مفعّل" : status;
    }

    private static int statusColor(String status) {
        if ("Active".equalsIgnoreCase(status)) return WFDesign.SUCCESS;
        if ("Expired".equalsIgnoreCase(status) || "InvalidCode".equalsIgnoreCase(status)) {
            return WFDesign.DANGER;
        }
        return WFDesign.ACCENT;
    }

    private static void returnToParent(Activity activity, WFStorage storage) {
        if (storage.isLicenseActive()) WolFoxPanel.show(activity);
        else ActivationDialog.showRequired(activity);
    }

    private static LinearLayout.LayoutParams full(int height) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
    }

    private static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static int dialogHeight(Activity activity) {
        return (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.86f);
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
