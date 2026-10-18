package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.InputFilter;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFStorage;

import java.util.Locale;

/** Common, validated actions used by search, map and favorites screens. */
public final class WFLocationActions {
    private WFLocationActions() {}

    public static void promptFavorite(Activity activity, WFLocation location, String suggestedName) {
        promptFavorite(activity, location, suggestedName, null);
    }

    public static void promptFavorite(Activity activity, WFLocation location, String suggestedName,
                                      Runnable onSaved) {
        if (activity == null || activity.isFinishing() || !isValid(location)) {
            if (activity != null) Toast.makeText(activity, "حدد موقعًا صالحًا أولًا", Toast.LENGTH_SHORT).show();
            return;
        }
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setFilters(new InputFilter[]{new InputFilter.LengthFilter(80)});
        name.setHint("مثال: المنزل أو العمل");
        name.setTextColor(WFDesign.TEXT);
        name.setHintTextColor(WFDesign.MUTED);
        name.setContentDescription("اسم الموقع المفضل");
        name.setTextSize(15);
        name.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        name.setPadding(WFDesign.dp(activity, 12), 0, WFDesign.dp(activity, 12), 0);
        name.setBackground(WFDesign.rounded(WFDesign.SURFACE, 12));
        String initial = clean(suggestedName);
        if (initial.isEmpty()) initial = "موقع محفوظ";
        name.setText(initial);
        name.setSelection(name.length());

        LinearLayout box = new LinearLayout(activity);
        box.setPadding(WFDesign.dp(activity, 18), WFDesign.dp(activity, 6),
                WFDesign.dp(activity, 18), 0);
        box.addView(name, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, WFDesign.dp(activity, 50)));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("حفظ في المفضلة")
                .setView(box)
                .setNegativeButton("إلغاء", null)
                .setPositiveButton("حفظ", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String label = clean(name.getText().toString());
                    if (label.isEmpty()) {
                        name.setError("أدخل اسمًا للموقع");
                        return;
                    }
                    FavoriteLocation favorite = new FavoriteLocation(
                            label, location.getLatitude(), location.getLongitude());
                    String address = clean(location.getLabel());
                    if (!address.equalsIgnoreCase(label)) favorite.setAddress(address);
                    boolean added = WFStorage.getInstance(activity).addFavoriteIfAbsent(favorite);
                    Toast.makeText(activity,
                            added ? "تم حفظ الموقع في المفضلة" : "هذا الموقع محفوظ مسبقًا بهذا الاسم",
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    if (onSaved != null) onSaved.run();
                }));
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }
        WFDesign.styleAlertDialog(dialog);
    }

    public static void openInMaps(Context context, WFLocation location) {
        if (!isValid(location)) {
            Toast.makeText(context, "إحداثيات الموقع غير صالحة", Toast.LENGTH_SHORT).show();
            return;
        }
        String coordinates = coordinatesText(location, ",");
        Uri uri = Uri.parse("https://www.google.com/maps/search/?api=1&query="
                + Uri.encode(coordinates));
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (!(context instanceof Activity)) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException error) {
            Toast.makeText(context, "لا يوجد تطبيق لفتح رابط الخريطة", Toast.LENGTH_LONG).show();
        }
    }

    public static void copyCoordinates(Context context, WFLocation location) {
        if (!isValid(location)) {
            Toast.makeText(context, "لا توجد إحداثيات صالحة", Toast.LENGTH_SHORT).show();
            return;
        }
        String coordinates = coordinatesText(location, ", ");
        ClipboardManager clipboard = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("WolFox GPS", coordinates));
            Toast.makeText(context, "تم نسخ الإحداثيات", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(context, "تعذر الوصول إلى الحافظة", Toast.LENGTH_SHORT).show();
        }
    }

    public static boolean isValid(WFLocation location) {
        return location != null && location.isValid()
                && !Double.isNaN(location.getLatitude())
                && !Double.isInfinite(location.getLatitude())
                && !Double.isNaN(location.getLongitude())
                && !Double.isInfinite(location.getLongitude());
    }

    private static String coordinatesText(WFLocation location, String separator) {
        return Double.toString(location.getLatitude()) + separator
                + Double.toString(location.getLongitude());
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
