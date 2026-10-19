package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.manager.FloatingManager;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.util.Locale;

public class SettingsDialog {

    private static final int COLOR_BG    = WFDesign.BACKGROUND;
    private static final int COLOR_GOLD  = WFDesign.ACCENT;
    private static final int COLOR_WHITE = WFDesign.TEXT;
    private static final int COLOR_CARD  = WFDesign.SURFACE;

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> buildAndShow(activity));
    }

    private static void buildAndShow(Activity activity) {
        Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawableResource(android.R.color.transparent);
            w.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setLayout(
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.92),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.CENTER);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(WFDesign.rounded(COLOR_BG, 20, WFDesign.BORDER, 1));
        root.setPadding(dp(activity,16), dp(activity,16), dp(activity,16), dp(activity,16));

        // عنوان موحد مع زر إغلاق واضح
        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(activity);
        title.setText(String.format(Locale.ROOT, "%s  الإعدادات", WFTheme.icon("⚙")));
        title.setTextColor(COLOR_GOLD);
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        titleRow.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button headerClose = WFDesign.closeButton(activity);
        headerClose.setOnClickListener(v -> {
            dialog.dismiss();
            WolFoxPanel.show(activity);
        });
        titleRow.addView(headerClose, new LinearLayout.LayoutParams(dp(activity,44), dp(activity,44)));
        root.addView(titleRow);
        addDivider(root, activity);

        // ─ دقة الموقع ─
        root.addView(buildSectionLabel(activity, "دقة الموقع التجريبي"));
        SeekBar accuracyBar = new SeekBar(activity);
        styleSeekBar(accuracyBar);
        accuracyBar.setMax(100);
        accuracyBar.setContentDescription("دقة الموقع التجريبي");
        WFLocation current = GPSMockManager.getInstance().getLocation();
        accuracyBar.setProgress(current == null ? 3 : Math.max(1, (int) current.getAccuracy()));
        root.addView(accuracyBar);

        TextView accuracyVal = new TextView(activity);
        accuracyVal.setText(formatMetric("الدقة: %d متر", accuracyBar.getProgress()));
        accuracyVal.setTextColor(COLOR_WHITE);
        accuracyVal.setTextSize(12);
        root.addView(accuracyVal);

        accuracyBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                int val = Math.max(1, prog);
                accuracyVal.setText(formatMetric("الدقة: %d متر", val));
                WFLocation loc = GPSMockManager.getInstance().getLocation();
                if (loc != null) loc.setAccuracy(val);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        addSpacing(root, activity, 12);

        // ─ ارتفاع مخصص ─
        root.addView(buildSectionLabel(activity, "الارتفاع (Altitude)"));
        EditText altEt = new EditText(activity);
        altEt.setHint("0.0");
        altEt.setHintTextColor(WFDesign.MUTED);
        altEt.setTextColor(COLOR_WHITE);
        altEt.setContentDescription("الارتفاع المخصص");
        altEt.setMinHeight(dp(activity, 48));
        altEt.setMinimumHeight(dp(activity, 48));
        altEt.setInputType(android.text.InputType.TYPE_CLASS_NUMBER
                | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
        altEt.setBackground(WFDesign.rounded(COLOR_CARD, 10, WFDesign.BORDER, 1));
        altEt.setPadding(dp(activity,10), dp(activity,8), dp(activity,10), dp(activity,8));
        root.addView(altEt);
        if (current != null) altEt.setText(String.valueOf(current.getAltitude()));

        addSpacing(root, activity, 12);

        // ─ سرعة مخصصة ─
        root.addView(buildSectionLabel(activity, "السرعة (م/ث)"));
        SeekBar speedBar = new SeekBar(activity);
        styleSeekBar(speedBar);
        speedBar.setMax(200);
        speedBar.setContentDescription("السرعة المخصصة");
        speedBar.setProgress(current == null ? 0 : Math.max(0, (int) current.getSpeed()));
        root.addView(speedBar);

        TextView speedVal = new TextView(activity);
        speedVal.setText(formatMetric("السرعة: %d م/ث", speedBar.getProgress()));
        speedVal.setTextColor(COLOR_WHITE);
        speedVal.setTextSize(12);
        root.addView(speedVal);

        speedBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int prog, boolean user) {
                speedVal.setText(formatMetric("السرعة: %d م/ث", prog));
                WFLocation loc = GPSMockManager.getInstance().getLocation();
                if (loc != null) loc.setSpeed(prog);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });

        addSpacing(root, activity, 12);

        root.addView(buildSectionLabel(activity, "حجم وشفافية الأيقونة"));
        WFStorage storage = WFStorage.getInstance(activity);
        SeekBar sizeBar = new SeekBar(activity);
        styleSeekBar(sizeBar);
        sizeBar.setMax(46);
        sizeBar.setContentDescription("حجم أيقونة WolFox");
        sizeBar.setProgress(storage.getBubbleSizeDp() - 42);
        root.addView(sizeBar);
        TextView sizeVal = new TextView(activity);
        sizeVal.setText(formatMetric("الحجم: %d dp", storage.getBubbleSizeDp()));
        sizeVal.setTextColor(COLOR_WHITE);
        root.addView(sizeVal);
        sizeBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                int size = 42 + value;
                storage.setBubbleSizeDp(size);
                sizeVal.setText(formatMetric("الحجم: %d dp", size));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        SeekBar opacityBar = new SeekBar(activity);
        styleSeekBar(opacityBar);
        opacityBar.setMax(65);
        opacityBar.setContentDescription("شفافية أيقونة WolFox");
        opacityBar.setProgress(Math.max(0, (int) (storage.getBubbleOpacity() * 100f) - 35));
        root.addView(opacityBar);
        TextView opacityVal = new TextView(activity);
        opacityVal.setText(formatMetric("الشفافية: %d%%", 35 + opacityBar.getProgress()));
        opacityVal.setTextColor(COLOR_WHITE);
        root.addView(opacityVal);
        opacityBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int value, boolean user) {
                int percent = 35 + value;
                storage.setBubbleOpacity(percent / 100f);
                opacityVal.setText(formatMetric("الشفافية: %d%%", percent));
                FloatingManager.getInstance().refreshAppearance();
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });

        addSpacing(root, activity, 12);

        // ─ تصحيح / Debug ─
        root.addView(buildSectionLabel(activity, "وضع التصحيح"));
        Switch debugSwitch = new Switch(activity);
        debugSwitch.setText(com.wolfox.gps.util.WFUiText.text(activity, "logging_enable"));
        debugSwitch.setTextColor(COLOR_WHITE);
        debugSwitch.setChecked(true);
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            debugSwitch.setThumbTintList(ColorStateList.valueOf(WFDesign.PRIMARY));
            debugSwitch.setTrackTintList(ColorStateList.valueOf(WFDesign.MUTED));
        }
        debugSwitch.setOnCheckedChangeListener((v, checked) -> WFLog.setDebug(checked));
        root.addView(debugSwitch);

        addSpacing(root, activity, 12);

        // تُعرض بيانات الاشتراك في شاشة مستقلة عن إعدادات الموقع والأيقونة.
        root.addView(buildSectionLabel(activity, "إدارة الاشتراك"));
        TextView subscriptionHint = new TextView(activity);
        subscriptionHint.setText(storage.getLicenseSummaryText());
        subscriptionHint.setTextColor(COLOR_WHITE);
        subscriptionHint.setTextSize(12);
        subscriptionHint.setPadding(dp(activity, 10), dp(activity, 8),
                dp(activity, 10), dp(activity, 8));
        subscriptionHint.setBackground(WFDesign.rounded(COLOR_CARD, 10, WFDesign.BORDER, 1));
        root.addView(subscriptionHint);
        Button openSubscription = WFDesign.button(activity, "تفاصيل الاشتراك والتحقق",
                WFDesign.SURFACE_RAISED, COLOR_WHITE);
        openSubscription.setOnClickListener(v -> {
            dialog.dismiss();
            SubscriptionDialog.show(activity);
        });
        root.addView(openSubscription);

        // Saved locations are managed exclusively from the Favorites section.

        root.addView(buildSectionLabel(activity, "معلومات التطبيق"));
        TextView versionInfo = new TextView(activity);
        versionInfo.setText("WolFox GPS " + com.wolfox.gps.ModuleConfig.VERSION_NAME
                + " • النسخة 3");
        versionInfo.setTextColor(COLOR_WHITE);
        root.addView(versionInfo);
        addSpacing(root, activity, 16);

        // ─ أزرار ─
        LinearLayout btnRow = new LinearLayout(activity);
        btnRow.setOrientation(LinearLayout.HORIZONTAL);

        Button saveBtn = WFDesign.button(activity, "حفظ", COLOR_GOLD, COLOR_BG);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        saveLp.setMargins(0,0,dp(activity,8),0);
        saveBtn.setLayoutParams(saveLp);
        saveBtn.setOnClickListener(v -> {
            try {
                double alt = Double.parseDouble(altEt.getText().toString().trim());
                WFLocation loc = GPSMockManager.getInstance().getLocation();
                if (loc != null) {
                    loc.setAltitude(alt);
                    GPSMockManager.getInstance().setLocation(loc);
                }
            } catch (Exception ignored) {}
            FloatingManager.getInstance().refreshAppearance();
            Toast.makeText(activity, "✅ تم الحفظ", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
        btnRow.addView(saveBtn);

        Button closeBtn = WFDesign.button(activity, "رجوع للوحة",
                WFDesign.SURFACE_RAISED, COLOR_WHITE);
        closeBtn.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        closeBtn.setOnClickListener(v -> { dialog.dismiss(); WolFoxPanel.show(activity); });
        btnRow.addView(closeBtn);

        root.addView(btnRow);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);
        WFDesign.moveCloseToFooter(root);
        WFTheme.applyTree(root);
        dialog.setContentView(scroll);
        dialog.show();
        if (w != null) {
            w.setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels * 0.92),
                    (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.86));
        }
    }

    private static TextView buildSectionLabel(Context ctx, String text) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(COLOR_GOLD);
        tv.setTextSize(13);
        tv.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(ctx, 4));
        tv.setLayoutParams(lp);
        return tv;
    }

    private static void addDivider(LinearLayout parent, Context ctx) {
        View d = new View(ctx);
        d.setBackgroundColor(COLOR_GOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1));
        lp.setMargins(0, dp(ctx,8), 0, dp(ctx,12));
        d.setLayoutParams(lp);
        parent.addView(d);
    }

    private static void addSpacing(LinearLayout parent, Context ctx, int dpVal) {
        View s = new View(ctx);
        s.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx, dpVal)));
        parent.addView(s);
    }

    private static void styleSeekBar(SeekBar bar) {
        bar.setProgressTintList(ColorStateList.valueOf(WFDesign.PRIMARY));
        bar.setThumbTintList(ColorStateList.valueOf(WFDesign.ACCENT));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(WFDesign.SURFACE_RAISED));
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    /** Locale is read at render time so a running host's locale change is not cached. */
    private static String formatMetric(String template, int value) {
        return String.format(Locale.getDefault(), template, value);
    }
}
