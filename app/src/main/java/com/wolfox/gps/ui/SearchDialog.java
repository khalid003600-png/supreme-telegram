package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.MapSearchResolver;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.util.Locale;

/** Address, coordinate and map-link search with save, open, copy and apply actions. */
public final class SearchDialog {
    private SearchDialog() {}

    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> buildAndShow(activity));
    }

    private static void buildAndShow(Activity activity) {
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.CENTER);
        }

        ScrollView scroll = new ScrollView(activity);
        scroll.setTag("wolfox.search.scroll");
        scroll.setFillViewport(true);
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);

        LinearLayout root = new LinearLayout(activity);
        root.setTag("wolfox.search.dialog");
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(activity, 16), dp(activity, 16), dp(activity, 16), dp(activity, 16));
        root.setBackground(WFDesign.rounded(WFDesign.BACKGROUND, 20, WFDesign.BORDER, 1));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text(activity, "⌕  البحث عن موقع", 19, WFDesign.TEXT, true);
        root.addView(title);
        TextView hint = text(activity,
                "ابحث بعنوان أو إحداثيات، أو الصق رابط مشاركة من Google Maps أو Apple Maps.",
                12, WFDesign.MUTED, false);
        LinearLayout.LayoutParams hintLp = fullWrap();
        hintLp.setMargins(0, dp(activity, 5), 0, dp(activity, 10));
        root.addView(hint, hintLp);

        final EditText query = new EditText(activity);
        query.setSingleLine(true);
        query.setFilters(new InputFilter[]{new InputFilter.LengthFilter(512)});
        query.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_URI);
        query.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        query.setHint("الرياض، 24.7136, 46.6753، أو رابط خريطة");
        query.setHintTextColor(WFDesign.MUTED);
        query.setTextColor(WFDesign.TEXT);
        query.setTextSize(14);
        if (Build.VERSION.SDK_INT >= 23) {
            query.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG_LTR);
        } else {
            query.setTextDirection(View.TEXT_DIRECTION_LTR);
        }
        query.setBackground(WFDesign.rounded(WFDesign.SURFACE, 12));
        query.setPadding(dp(activity, 12), 0, dp(activity, 12), 0);
        query.setMinHeight(dp(activity, 48));
        query.setMinimumHeight(dp(activity, 48));
        query.setTag("wolfox.search.input");
        LinearLayout.LayoutParams queryLp = fullWrap();
        queryLp.setMargins(0, 0, 0, dp(activity, 8));
        root.addView(query, queryLp);

        LinearLayout helpers = new LinearLayout(activity);
        helpers.setOrientation(LinearLayout.VERTICAL);
        Button paste = button(activity, "لصق الرابط", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        Button clear = button(activity, "مسح", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        paste.setTag("wolfox.search.paste");
        clear.setTag("wolfox.search.clear");
        helpers.addView(paste, fullWrap());
        helpers.addView(clear, fullWrapWithTopMargin(activity, 6));
        root.addView(helpers, fullWrap());

        final TextView resultText = text(activity, "", 13, WFDesign.TEXT, false);
        resultText.setTag("wolfox.search.result");
        resultText.setGravity(Gravity.CENTER);
        resultText.setVisibility(View.GONE);
        resultText.setPadding(dp(activity, 8), dp(activity, 10),
                dp(activity, 8), dp(activity, 10));
        root.addView(resultText, fullWrap());

        final ProgressBar progress = new ProgressBar(activity);
        progress.setVisibility(View.GONE);
        LinearLayout.LayoutParams progressLp = new LinearLayout.LayoutParams(
                dp(activity, 32), dp(activity, 32));
        progressLp.gravity = Gravity.CENTER_HORIZONTAL;
        root.addView(progress, progressLp);

        final Button apply = button(activity, "✓  تحديد الموقع وتشغيله",
                WFDesign.SUCCESS, WFDesign.BACKGROUND);
        apply.setTag("wolfox.search.confirm");
        apply.setVisibility(View.GONE);
        LinearLayout.LayoutParams applyLp = fullWrap();
        applyLp.setMargins(0, dp(activity, 7), 0, dp(activity, 7));
        root.addView(apply, applyLp);

        LinearLayout resultActions = new LinearLayout(activity);
        resultActions.setOrientation(LinearLayout.VERTICAL);
        Button save = button(activity, "حفظ", WFDesign.ACCENT, WFDesign.BACKGROUND);
        Button maps = button(activity, "الخرائط", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        Button copy = button(activity, "نسخ", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        save.setTag("wolfox.search.save");
        maps.setTag("wolfox.search.maps");
        copy.setTag("wolfox.search.copy");
        resultActions.addView(save, fullWrap());
        resultActions.addView(maps, fullWrapWithTopMargin(activity, 6));
        resultActions.addView(copy, fullWrapWithTopMargin(activity, 6));
        resultActions.setVisibility(View.VISIBLE);
        save.setEnabled(false);
        maps.setEnabled(false);
        copy.setEnabled(false);
        root.addView(resultActions, fullWrap());

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.VERTICAL);
        final Button search = button(activity, "بحث", WFDesign.PRIMARY, WFDesign.TEXT);
        Button close = WFDesign.closeButton(activity);
        search.setTag("wolfox.search.search");
        close.setTag("wolfox.search.close");
        actions.addView(search, fullWrap());
        LinearLayout.LayoutParams closeLp = fullWrapWithTopMargin(activity, 6);
        actions.addView(close, closeLp);
        LinearLayout.LayoutParams actionsLp = fullWrap();
        actionsLp.setMargins(0, dp(activity, 8), 0, 0);
        root.addView(actions, actionsLp);

        WFDesign.moveCloseToFooter(root);
        WFTheme.applyTree(root);
        dialog.setContentView(scroll);

        final WFLocation[] selected = new WFLocation[1];
        final String[] source = {""};
        final String[] searchedText = {""};
        final int[] requestId = {0};
        final MapSearchResolver.Request[] activeSearch = new MapSearchResolver.Request[1];
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final Runnable[] pendingApplyCheck = new Runnable[1];

        paste.setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager)
                    activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard == null || !clipboard.hasPrimaryClip()) {
                Toast.makeText(activity, "الحافظة فارغة", Toast.LENGTH_SHORT).show();
                return;
            }
            ClipData clip = clipboard.getPrimaryClip();
            if (clip == null || clip.getItemCount() == 0) return;
            CharSequence value = clip.getItemAt(0).coerceToText(activity);
            query.setText(value == null ? "" : value.toString().trim());
            query.setSelection(query.length());
            search.performClick();
        });

        clear.setOnClickListener(view -> {
            requestId[0]++;
            cancel(activeSearch);
            query.setText("");
            selected[0] = null;
            resultText.setVisibility(View.GONE);
            apply.setVisibility(View.GONE);
            resultActions.setVisibility(View.VISIBLE);
            save.setEnabled(false);
            maps.setEnabled(false);
            copy.setEnabled(false);
            search.setEnabled(true);
            search.setText("بحث");
            query.requestFocus();
        });
        close.setOnClickListener(view -> {
            dialog.dismiss();
            WolFoxPanel.show(activity);
        });
        query.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                requestId[0]++;
                cancel(activeSearch);
                selected[0] = null;
                apply.setVisibility(View.GONE);
                resultActions.setVisibility(View.VISIBLE);
                save.setEnabled(false);
                maps.setEnabled(false);
                copy.setEnabled(false);
                if (progress.getVisibility() == View.VISIBLE) {
                    progress.setVisibility(View.GONE);
                    search.setEnabled(true);
                    search.setText("بحث");
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        search.setOnClickListener(view -> {
            final String input = query.getText().toString().trim();
            if (input.isEmpty()) {
                query.requestFocus();
                resultText.setText("أدخل اسم موقع أو إحداثيات أو رابط مشاركة");
                resultText.setTextColor(WFDesign.DANGER);
                resultText.setVisibility(View.VISIBLE);
                return;
            }
            cancel(activeSearch);
            final int thisRequest = ++requestId[0];
            searchedText[0] = input;
            selected[0] = null;
            source[0] = "";
            resultText.setVisibility(View.GONE);
            apply.setVisibility(View.GONE);
            resultActions.setVisibility(View.VISIBLE);
            save.setEnabled(false);
            maps.setEnabled(false);
            copy.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            search.setEnabled(false);
            search.setText("جاري البحث…");

            final MapSearchResolver.Request[] issued = new MapSearchResolver.Request[1];
            issued[0] = MapSearchResolver.resolve(activity, input, resolved -> {
                if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()
                        || activeSearch[0] != issued[0] || thisRequest != requestId[0]) return;
                activeSearch[0] = null;
                progress.setVisibility(View.GONE);
                search.setEnabled(true);
                search.setText("بحث");
                resultText.setVisibility(View.VISIBLE);
                if (!resolved.isSuccess()) {
                    resultText.setText(com.wolfox.gps.util.WFUiText.format(
                            activity, "search_error", resolved.getError()));
                    resultText.setTextColor(WFDesign.DANGER);
                    return;
                }
                // Keep the resolver's label on a stable selection. A later resolver callback
                // must not mutate the location eventually handed to the panel/GPS manager.
                WFLocation location = copyResolvedLocation(resolved.getLocation());
                if (!WFLocationActions.isValid(location)) {
                    resultText.setText("×  تعذر قراءة الموقع المحدد");
                    resultText.setTextColor(WFDesign.DANGER);
                    return;
                }
                selected[0] = location;
                source[0] = resolved.getSource();
                String label = location.getLabel() == null ? "" : location.getLabel();
                resultText.setText(com.wolfox.gps.util.WFUiText.format(activity,
                        "search_success", resolved.getSource(), label.isEmpty() ? "" : "\n" + label,
                        String.format(Locale.US, "%.6f, %.6f",
                                location.getLatitude(), location.getLongitude())));
                resultText.setTextColor(WFDesign.TEXT);
                apply.setVisibility(View.VISIBLE);
                resultActions.setVisibility(View.VISIBLE);
                save.setEnabled(true);
                maps.setEnabled(true);
                copy.setEnabled(true);
            });
            activeSearch[0] = issued[0];
        });

        save.setOnClickListener(view -> {
            WFLocation location = selected[0];
            if (WFLocationActions.isValid(location)) {
                String label = location.getLabel();
                WFLocationActions.promptFavorite(activity, location,
                        label == null || label.trim().isEmpty() ? searchedText[0] : label);
            }
        });
        maps.setOnClickListener(view -> WFLocationActions.openInMaps(activity, selected[0]));
        copy.setOnClickListener(view -> WFLocationActions.copyCoordinates(activity, selected[0]));

        apply.setOnClickListener(view -> {
            final WFLocation location = selected[0];
            if (!WFLocationActions.isValid(location)) return;
            final int confirmedRequest = requestId[0];
            apply.setEnabled(false);
            resultText.setText("جاري تأكيد تشغيل الموقع داخل التطبيق…");
            resultText.setTextColor(WFDesign.MUTED);
            final boolean started = GPSMockManager.getInstance().selectAndStart(location);
            Runnable check = new Runnable() {
                @Override public void run() {
                    pendingApplyCheck[0] = null;
                // Do not revive the panel with an old location after the query/selection changed.
                if (!dialog.isShowing() || activity.isFinishing() || activity.isDestroyed()
                        || confirmedRequest != requestId[0]
                        || selected[0] != location) return;
                if (!(started && GPSMockManager.getInstance().verifyRuntimeInjection())) {
                    GPSMockManager.getInstance().stopMocking();
                    apply.setEnabled(true);
                    resultText.setText("×  تعذر تشغيل الموقع في التطبيق المستهدف. أعد فتحه ثم حاول مجددًا.");
                    resultText.setTextColor(WFDesign.DANGER);
                    return;
                }
                WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                        HistoryEntry.Action.SEARCH, location.getLatitude(), location.getLongitude(),
                        searchedText[0] + " • " + source[0]));
                WFStorage.getInstance(activity).addHistory(new HistoryEntry(
                        HistoryEntry.Action.START_MOCK, "تأكيد الموقع من البحث"));
                Toast.makeText(activity, "تم اختيار الموقع وتشغيله", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                WolFoxPanel.show(activity);
                }
            };
            pendingApplyCheck[0] = check;
            mainHandler.postDelayed(check, 220L);
        });

        query.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) {
                search.performClick();
                return true;
            }
            return false;
        });

        dialog.setOnDismissListener(ignored -> {
            cancel(activeSearch);
            Runnable pending = pendingApplyCheck[0];
            if (pending != null) mainHandler.removeCallbacks(pending);
            pendingApplyCheck[0] = null;
        });

        dialog.show();
        if (window != null) {
            window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.90f));
        }
    }

    private static TextView text(Activity activity, String value, int size, int color, boolean bold) {
        return WFDesign.text(activity, value, size, color, bold);
    }

    private static Button button(Activity activity, String value, int background, int color) {
        return WFDesign.button(activity, value, background, color);
    }

    /**
     * Snapshots the resolver result, including its human-readable label, before UI actions can
     * replace the active selection. Package visibility keeps the no-network regression test local.
     */
    static WFLocation copyResolvedLocation(WFLocation resolved) {
        if (!WFLocationActions.isValid(resolved)) return null;
        WFLocation selected = new WFLocation(resolved.getLatitude(), resolved.getLongitude(),
                resolved.getLabel());
        selected.setAltitude(resolved.getAltitude());
        selected.setAccuracy(resolved.getAccuracy());
        selected.setSpeed(resolved.getSpeed());
        selected.setBearing(resolved.getBearing());
        selected.setTimestamp(resolved.getTimestamp());
        return selected;
    }

    private static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams fullWrapWithTopMargin(Activity activity, int topMarginDp) {
        LinearLayout.LayoutParams params = fullWrap();
        params.setMargins(0, dp(activity, topMarginDp), 0, 0);
        return params;
    }

    private static int dp(Activity activity, int value) {
        return WFDesign.dp(activity, value);
    }

    private static void cancel(MapSearchResolver.Request[] request) {
        if (request[0] != null) request[0].cancel();
        request[0] = null;
    }
}
