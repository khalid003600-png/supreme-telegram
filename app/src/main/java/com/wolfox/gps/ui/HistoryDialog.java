package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.*;

import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistoryDialog {

    private static final int COLOR_BG    = WFDesign.BACKGROUND;
    private static final int COLOR_GOLD  = WFDesign.ACCENT;
    private static final int COLOR_WHITE = WFDesign.TEXT;
    private static final int COLOR_CARD  = WFDesign.SURFACE;
    private static final int COLOR_RED   = WFDesign.DANGER;

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
            w.setLayout(
                    (int)(activity.getResources().getDisplayMetrics().widthPixels * 0.94),
                    (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.80));
            w.setGravity(Gravity.CENTER);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackground(WFDesign.rounded(COLOR_BG, 20, WFDesign.BORDER, 1));
        root.setPadding(dp(activity,14), dp(activity,14), dp(activity,14), dp(activity,14));

        // ─ رأس ─
        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(activity);
        title.setText(String.format(Locale.ROOT, "%s  سجل العمليات", WFTheme.icon("◷")));
        title.setTextColor(COLOR_GOLD);
        title.setTextSize(16);
        title.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Button clearBtn = WFDesign.button(activity, "مسح الكل", COLOR_RED, COLOR_WHITE);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                dp(activity, 82), LinearLayout.LayoutParams.WRAP_CONTENT);
        clearBtn.setLayoutParams(clp);
        header.addView(clearBtn);

        Button closeBtn = WFDesign.closeButton(activity);
        closeBtn.setOnClickListener(v -> {
            dialog.dismiss();
            WolFoxPanel.show(activity);
        });
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(
                dp(activity, 44), dp(activity, 44));
        closeLp.setMargins(dp(activity, 6), 0, 0, 0);
        header.addView(closeBtn, closeLp);

        root.addView(header);
        addDivider(root, activity);

        // ─ قائمة ─
        ScrollView scroll = new ScrollView(activity);
        scroll.setTag("wolfox.history.list.scroll");
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, listViewportHeight(activity)));
        root.addView(scroll);

        WFDesign.moveCloseToFooter(root);
        WFTheme.applyTree(root);
        ScrollView outerScroll = new ScrollView(activity);
        outerScroll.setTag("wolfox.history.dialog.scroll");
        outerScroll.setFillViewport(true);
        outerScroll.setClipToPadding(false);
        outerScroll.addView(root, new ScrollView.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(outerScroll);

        // ─ تحميل ─
        Runnable reload = () -> {
            list.removeAllViews();
            List<HistoryEntry> entries = WFStorage.getInstance(activity).getHistory();
            if (entries.isEmpty()) {
                TextView empty = new TextView(activity);
                empty.setText("لا يوجد سجل بعد");
                empty.setTextColor(WFDesign.MUTED);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(0, dp(activity,24), 0, 0);
                list.addView(empty);
            } else {
                for (HistoryEntry e : entries) {
                    list.addView(buildHistoryItem(activity, e));
                }
            }
            WFTheme.applyTree(list);
        };

        reload.run();

        clearBtn.setOnClickListener(v -> {
            AlertDialog confirm = new AlertDialog.Builder(activity)
                    .setTitle("مسح السجل")
                    .setMessage("هل تريد مسح كل السجل؟")
                    .setPositiveButton("مسح", (d2, w2) -> {
                        WFStorage.getInstance(activity).clearHistory();
                        reload.run();
                    })
                    .setNegativeButton("إلغاء", null)
                    .create();
            confirm.show();
            WFDesign.styleAlertDialog(confirm);
        });

        dialog.show();
        if (w != null) {
            w.setLayout((int)(activity.getResources().getDisplayMetrics().widthPixels * 0.94),
                    (int)(activity.getResources().getDisplayMetrics().heightPixels * 0.80));
        }
    }

    private static View buildHistoryItem(Activity activity, HistoryEntry entry) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(WFDesign.rounded(COLOR_CARD, 10, WFDesign.BORDER, 1));
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.setMargins(0, 0, 0, dp(activity, 3));
        row.setLayoutParams(rowLp);
        row.setPadding(dp(activity,10), dp(activity,8), dp(activity,10), dp(activity,8));

        LinearLayout info = new LinearLayout(activity);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView action = new TextView(activity);
        action.setText(entry.getActionLabel());
        action.setTextColor(COLOR_GOLD);
        action.setTextSize(13);
        info.addView(action);

        if (entry.getDetail() != null && !entry.getDetail().isEmpty()) {
            TextView detail = new TextView(activity);
            detail.setText(entry.getDetail());
            detail.setTextColor(COLOR_WHITE);
            detail.setTextSize(11);
            info.addView(detail);
        }

        if (entry.getLatitude() != 0 || entry.getLongitude() != 0) {
            TextView coord = new TextView(activity);
            coord.setText(String.format(Locale.ROOT, "📍 %.5f, %.5f",
                    entry.getLatitude(), entry.getLongitude()));
            coord.setTextColor(WFDesign.MUTED);
            coord.setTextSize(11);
            coord.setTextDirection(View.TEXT_DIRECTION_LTR);
            coord.setGravity(Gravity.START);
            info.addView(coord);
        }

        row.addView(info);

        TextView time = new TextView(activity);
        time.setText(formatTimestamp(entry.getTimestamp()));
        time.setTextColor(WFDesign.MUTED);
        time.setTextSize(10);
        time.setTextDirection(View.TEXT_DIRECTION_LTR);
        time.setGravity(Gravity.END);
        row.addView(time);

        return row;
    }

    private static void addDivider(LinearLayout parent, Context ctx) {
        View d = new View(ctx);
        d.setBackgroundColor(COLOR_GOLD);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(ctx,1));
        lp.setMargins(0, dp(ctx,8), 0, dp(ctx,8));
        d.setLayoutParams(lp);
        parent.addView(d);
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    /** Avoid a static formatter: Android may change the host locale while this process lives. */
    private static String formatTimestamp(long timestamp) {
        return new SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
                .format(new Date(timestamp));
    }

    private static int listViewportHeight(Activity activity) {
        int max = dp(activity, 280);
        int screenFraction = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.34f);
        return Math.max(dp(activity, 96), Math.min(max, screenFraction));
    }
}
