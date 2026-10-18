package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.util.List;
import java.util.Locale;

/** Searchable favorites with consistent actions for applying, opening, copying and editing. */
public final class FavoritesDialog {
    private FavoritesDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> build(activity));
    }

    private static void build(Activity activity) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            dialog.getWindow().setGravity(Gravity.CENTER);
            dialog.getWindow().setSoftInputMode(
                    android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity, 16), dp(activity, 14), dp(activity, 16), dp(activity, 14));
        root.setBackground(WFDesign.rounded(WFDesign.BACKGROUND, 22, WFDesign.BORDER, 1));

        LinearLayout heading = new LinearLayout(activity);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = new LinearLayout(activity);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(text(activity, "المواقع المفضلة", 19, WFDesign.TEXT, true));
        titles.addView(text(activity, "ابحث عن موقع محفوظ أو أضف موقعًا جديدًا", 11,
                WFDesign.MUTED, false));
        heading.addView(titles, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = WFDesign.closeButton(activity);
        close.setOnClickListener(view -> {
            dialog.dismiss();
            WolFoxPanel.show(activity);
        });
        heading.addView(close, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        root.addView(heading);

        EditText filter = new EditText(activity);
        filter.setSingleLine(true);
        filter.setHint("بحث بالاسم أو العنوان أو الإحداثيات");
        filter.setTextColor(WFDesign.TEXT);
        filter.setHintTextColor(WFDesign.MUTED);
        filter.setTextSize(14);
        filter.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        filter.setBackground(WFDesign.rounded(WFDesign.SURFACE, 12));
        filter.setCompoundDrawablePadding(dp(activity, 8));
        LinearLayout.LayoutParams filterLp = full(activity, 48);
        filterLp.setMargins(0, dp(activity, 12), 0, dp(activity, 9));
        root.addView(filter, filterLp);

        LinearLayout shortcuts = new LinearLayout(activity);
        Button searchNew = button(activity, "⌕  بحث عن موقع", WFDesign.PRIMARY, WFDesign.TEXT);
        Button saveCurrent = button(activity, "＋  حفظ الحالي", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        shortcuts.addView(searchNew, weighted(activity, 44, 6));
        shortcuts.addView(saveCurrent, weighted(activity, 44, 0));
        root.addView(shortcuts);

        ScrollView scroll = new ScrollView(activity);
        scroll.setTag("wolfox.favorites.list.scroll");
        scroll.setFillViewport(true);
        LinearLayout list = new LinearLayout(activity);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(activity, 5), 0, dp(activity, 4));
        scroll.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, listViewportHeight(activity));
        scrollLp.setMargins(0, dp(activity, 5), 0, dp(activity, 4));
        root.addView(scroll, scrollLp);

        final Runnable[] reload = new Runnable[1];
        reload[0] = () -> {
            list.removeAllViews();
            String query = filter.getText().toString().trim().toLowerCase(Locale.ROOT);
            List<FavoriteLocation> favorites = WFStorage.getInstance(activity).getFavorites();
            int shown = 0;
            for (FavoriteLocation favorite : favorites) {
                if (!matches(favorite, query)) continue;
                list.addView(favoriteCard(activity, dialog, favorite, reload), cardMargins(activity));
                shown++;
            }
            if (shown == 0) {
                String message = query.isEmpty()
                        ? "لا توجد مواقع محفوظة بعد\nابحث عن موقع ثم احفظه هنا"
                        : "لا توجد نتائج مطابقة\nجرّب اسمًا أو إحداثيات مختلفة";
                TextView empty = text(activity, message, 14, WFDesign.MUTED, false);
                empty.setGravity(Gravity.CENTER);
                empty.setPadding(dp(activity, 16), dp(activity, 30), dp(activity, 16), dp(activity, 30));
                list.addView(empty, fullWrap());
                if (query.isEmpty()) {
                    Button emptySearch = button(activity, "ابدأ البحث", WFDesign.PRIMARY, WFDesign.TEXT);
                    emptySearch.setOnClickListener(view -> {
                        dialog.dismiss();
                        SearchDialog.show(activity);
                    });
                    list.addView(emptySearch, full(activity, 48));
                }
            }
            WFTheme.applyTree(list);
        };
        reload[0].run();
        filter.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                reload[0].run();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        searchNew.setOnClickListener(view -> {
            dialog.dismiss();
            SearchDialog.show(activity);
        });
        saveCurrent.setOnClickListener(view -> {
            WFLocation location = GPSMockManager.getInstance().getLocation();
            if (location == null || !WFLocationActions.isValid(location)) {
                Toast.makeText(activity, "حدد موقعًا أولًا من الخريطة أو البحث", Toast.LENGTH_SHORT).show();
                return;
            }
            WFLocationActions.promptFavorite(activity, location, location.getLabel(), reload[0]);
        });

        WFDesign.moveCloseToFooter(root);
        WFTheme.applyTree(root);
        ScrollView outerScroll = new ScrollView(activity);
        outerScroll.setTag("wolfox.favorites.dialog.scroll");
        outerScroll.setFillViewport(true);
        outerScroll.setClipToPadding(false);
        outerScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        dialog.setContentView(outerScroll);
        dialog.show();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.86f));
        }
    }

    private static View favoriteCard(Activity activity, Dialog dialog, FavoriteLocation favorite,
                                     Runnable[] reload) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(activity, 12), dp(activity, 10), dp(activity, 12), dp(activity, 10));
        card.setBackground(WFDesign.rounded(WFDesign.SURFACE, 15));

        LinearLayout titleRow = new LinearLayout(activity);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout details = new LinearLayout(activity);
        details.setOrientation(LinearLayout.VERTICAL);
        String name = clean(favorite.getName()).isEmpty() ? "موقع محفوظ" : favorite.getName();
        details.addView(text(activity, name, 15, WFDesign.TEXT, true));
        String address = clean(favorite.getAddress());
        if (!address.isEmpty() && !address.equalsIgnoreCase(name)) {
            TextView addressView = text(activity, address, 11, WFDesign.MUTED, false);
            addressView.setMaxLines(2);
            details.addView(addressView);
        }
        TextView coordinates = text(activity, String.format(Locale.US, "%.6f, %.6f",
                favorite.getLatitude(), favorite.getLongitude()), 12, WFDesign.MUTED, false);
        coordinates.setTextDirection(View.TEXT_DIRECTION_LTR);
        coordinates.setGravity(Gravity.START);
        details.addView(coordinates);
        titleRow.addView(details, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton edit = icon(activity, android.R.drawable.ic_menu_edit, WFDesign.PRIMARY);
        edit.setContentDescription("تعديل اسم " + name);
        edit.setOnClickListener(view -> edit(activity, favorite, reload[0]));
        titleRow.addView(edit, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        ImageButton delete = icon(activity, android.R.drawable.ic_menu_delete, WFDesign.DANGER);
        delete.setContentDescription("حذف " + name);
        delete.setOnClickListener(view -> {
            AlertDialog confirm = new AlertDialog.Builder(activity)
                    .setTitle("حذف من المفضلة")
                    .setMessage("هل تريد حذف «" + name + "»؟")
                    .setNegativeButton("إلغاء", null)
                    .setPositiveButton("حذف", (which, button) -> {
                        WFStorage.getInstance(activity).deleteFavorite(favorite.getId());
                        reload[0].run();
                    }).create();
            confirm.show();
            WFDesign.styleAlertDialog(confirm);
        });
        LinearLayout.LayoutParams deleteLp = new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48));
        deleteLp.setMargins(dp(activity, 4), 0, 0, 0);
        titleRow.addView(delete, deleteLp);
        card.addView(titleRow);

        LinearLayout actions = new LinearLayout(activity);
        Button use = button(activity, "استخدم", WFDesign.PRIMARY, WFDesign.TEXT);
        Button maps = button(activity, "خريطة", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        maps.setContentDescription("عرض " + name + " على الخريطة الداخلية");
        Button copy = button(activity, "نسخ", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        actions.addView(use, weighted(activity, 44, 5));
        actions.addView(maps, weighted(activity, 44, 5));
        actions.addView(copy, weighted(activity, 44, 0));
        LinearLayout.LayoutParams actionsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionsLp.setMargins(0, dp(activity, 8), 0, 0);
        card.addView(actions, actionsLp);

        // Keep the same visible favorite name when the selected location is persisted.
        WFLocation location = new WFLocation(favorite.getLatitude(), favorite.getLongitude(), name);
        use.setOnClickListener(view -> {
            boolean started = GPSMockManager.getInstance().selectAndStart(location);
            if (started && GPSMockManager.getInstance().verifyRuntimeInjection()) {
                Toast.makeText(activity, "تم اختيار الموقع وتشغيله", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                WolFoxPanel.show(activity);
            } else {
                GPSMockManager.getInstance().stopMocking();
                Toast.makeText(activity, "تعذر تشغيل الموقع داخل التطبيق المستهدف", Toast.LENGTH_LONG).show();
            }
        });
        maps.setOnClickListener(view -> MapDialog.showFavoritePreview(activity, favorite));
        copy.setOnClickListener(view -> WFLocationActions.copyCoordinates(activity, location));
        return card;
    }

    private static boolean matches(FavoriteLocation favorite, String query) {
        if (query.isEmpty()) return true;
        if (clean(favorite.getName()).toLowerCase(Locale.ROOT).contains(query)
                || clean(favorite.getAddress()).toLowerCase(Locale.ROOT).contains(query)) return true;
        String normalized = normalizeCoordinates(query);
        String[] parts = normalized.split("[, ]+");
        if (parts.length != 2) return false;
        try {
            double latitude = Double.parseDouble(parts[0]);
            double longitude = Double.parseDouble(parts[1]);
            return Math.abs(favorite.getLatitude() - latitude) < 0.000001d
                    && Math.abs(favorite.getLongitude() - longitude) < 0.000001d;
        } catch (NumberFormatException invalidQuery) {
            return false;
        }
    }

    private static String normalizeCoordinates(String value) {
        StringBuilder normalized = new StringBuilder(value.length());
        String arabic = "٠١٢٣٤٥٦٧٨٩";
        String persian = "۰۱۲۳۴۵۶۷۸۹";
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            int digit = arabic.indexOf(ch);
            if (digit >= 0) ch = (char) ('0' + digit);
            else {
                digit = persian.indexOf(ch);
                if (digit >= 0) ch = (char) ('0' + digit);
            }
            if (ch == '٫') ch = '.';
            if (ch == '،') ch = ',';
            normalized.append(ch);
        }
        return normalized.toString().replaceAll("\\s*,\\s*", ",")
                .replaceAll("\\s+", " ").trim();
    }

    private static void edit(Activity activity, FavoriteLocation favorite, Runnable reload) {
        EditText name = new EditText(activity);
        name.setSingleLine(true);
        name.setText(favorite.getName());
        name.setHint("اسم الموقع");
        LinearLayout box = new LinearLayout(activity);
        box.setPadding(dp(activity, 18), dp(activity, 8), dp(activity, 18), 0);
        box.addView(name, full(activity, 50));
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("تعديل اسم الموقع")
                .setView(box).setNegativeButton("إلغاء", null).setPositiveButton("حفظ", null).create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(view -> {
                    String value = clean(name.getText().toString());
                    if (value.isEmpty()) {
                        name.setError("أدخل اسمًا للموقع");
                        return;
                    }
                    favorite.setName(value);
                    WFStorage.getInstance(activity).updateFavorite(favorite);
                    dialog.dismiss();
                    reload.run();
                }));
        dialog.show();
        WFDesign.styleAlertDialog(dialog);
    }

    private static ImageButton icon(Activity activity, int resource, int color) {
        ImageButton button = new ImageButton(activity);
        button.setImageResource(resource);
        button.setPadding(dp(activity, 10), dp(activity, 10), dp(activity, 10), dp(activity, 10));
        WFDesign.colorIconButton(button,
                resource == android.R.drawable.ic_menu_delete ? "حذف" : "تعديل", color);
        button.setMinimumWidth(dp(activity, 48));
        button.setMinimumHeight(dp(activity, 48));
        return button;
    }

    private static Button button(Activity activity, String label, int background, int foreground) {
        return WFDesign.button(activity, label, background, foreground);
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        return WFDesign.text(activity, value, size, color, bold);
    }

    private static LinearLayout.LayoutParams full(Activity activity, int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(activity, heightDp));
    }

    private static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weighted(Activity activity, int heightDp, int endMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, dp(activity, endMarginDp), 0);
        return params;
    }

    private static LinearLayout.LayoutParams cardMargins(Activity activity) {
        LinearLayout.LayoutParams params = fullWrap();
        params.setMargins(0, 0, 0, dp(activity, 9));
        return params;
    }

    /** The outer scroll keeps the header and footer reachable on short landscape windows. */
    private static int listViewportHeight(Activity activity) {
        int max = dp(activity, 280);
        int screenFraction = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.34f);
        return Math.max(dp(activity, 96), Math.min(max, screenFraction));
    }

    private static int dp(Activity activity, int value) {
        return WFDesign.dp(activity, value);
    }

    private static String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
