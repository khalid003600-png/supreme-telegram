package com.wolfox.gps.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wolfox.gps.util.WFTheme;

/** Shared design tokens and controls for WolFox's Arabic interfaces. */
public final class WFDesign {
    public static final int BACKGROUND = 0xFF0B1017;
    public static final int SURFACE = 0xFF171F2C;
    public static final int SURFACE_RAISED = 0xFF222C3C;
    public static final int PRIMARY = 0xFF3EB7E7;
    public static final int TEXT = 0xFFF6F8FC;
    public static final int MUTED = 0xFFD0D9E9;
    public static final int SUCCESS = 0xFF38CF8E;
    public static final int DANGER = 0xFFFA565F;
    public static final int ACCENT = 0xFFF59E0B;
    public static final int BORDER = 0xFF2A3546;

    // Bright supporting actions use dark text; destructive actions retain light text.
    public static final int BUTTON_SELECT = 0xFF34D399;
    public static final int BUTTON_STOP = 0xFF881337;
    public static final int BUTTON_PASTE = 0xFF0E7490;
    public static final int BUTTON_CLEAR = 0xFFFB923C;
    public static final int BUTTON_DELETE = 0xFFB91C1C;
    public static final int BUTTON_SAVE = 0xFF7C3AED;
    public static final int BUTTON_COPY = 0xFF4338CA;
    public static final int BUTTON_MAP = 0xFF2DD4BF;
    public static final int BUTTON_LOCATION = 0xFF34D399;
    public static final int BUTTON_BLUETOOTH = 0xFF0E7490;
    public static final int BUTTON_CAMERA = 0xFF7C3AED;
    public static final int BUTTON_FAVORITE = 0xFFFBBF24;
    public static final int BUTTON_SUBSCRIPTION = 0xFFA21CAF;
    public static final int BUTTON_SETTINGS = 0xFF475569;
    public static final int BUTTON_HIDE = 0xFFBE185D;
    public static final int BUTTON_SHOW = 0xFF84CC16;
    public static final int BUTTON_CLOSE = 0xFF374151;
    public static final int BUTTON_BACK = 0xFF4B5563;
    public static final int BUTTON_ACTIVATE = 0xFF047857;
    public static final int BUTTON_EDIT = 0xFFC2410C;
    public static final int BUTTON_EXPAND = 0xFF115E59;
    public static final int BUTTON_HISTORY = 0xFF92400E;
    public static final int BUTTON_VERIFY = 0xFF164E63;

    private WFDesign() {}

    public static GradientDrawable rounded(int fill, int radiusDp) {
        return rounded(fill, radiusDp, BORDER, 1);
    }

    public static GradientDrawable rounded(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dpRadius(radiusDp));
        if (strokeDp > 0) drawable.setStroke(dpRadius(strokeDp), stroke);
        return drawable;
    }

    /** Native feedback, distinct disabled/focus states, and a 48dp touch target, API 21+. */
    public static Button button(Context context, String label, int background, int foreground) {
        Button button = new Button(context);
        button.setAllCaps(false);
        button.setText(label);
        button.setContentDescription(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setGravity(Gravity.CENTER);
        button.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        button.setIncludeFontPadding(true);
        button.setMaxLines(2);
        button.setEllipsize(TextUtils.TruncateAt.END);
        button.setPadding(dp(context, 12), dp(context, 6), dp(context, 12), dp(context, 6));
        button.setBackgroundTintList(null);
        LinearLayout.LayoutParams spacing = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        spacing.setMargins(dp(context, 4), dp(context, 6), dp(context, 4), dp(context, 6));
        button.setLayoutParams(spacing);
        colorButton(button, label, background, foreground);
        button.setFocusable(true);
        WFTheme.apply(button, true);
        return button;
    }

    /** Resolve the action, not ornamental prefixes or Arabic vowel marks. */
    public static int semanticColor(String label, int fallback) {
        if (label == null) return fallback;
        String value = normalizedLabel(label);
        if (value.contains("اغلاق") || value.contains("خروج") || value.contains("الغاء")) return BUTTON_CLOSE;
        if (value.contains("ايقاف")) return BUTTON_STOP;
        if (value.contains("تزييف")) return DANGER;
        if (value.contains("بلوتوث") || value.contains("bluetooth") || value.contains("ble")) return BUTTON_BLUETOOTH;
        if (value.contains("كاميرا") || value.contains("اختيار صورة")) return BUTTON_CAMERA;
        if (value.contains("لصق")) return BUTTON_PASTE;
        if (value.contains("حذف") || value.contains("مسح الكل")) return BUTTON_DELETE;
        if (value.contains("مسح")) return BUTTON_CLEAR;
        if (value.contains("بحث")) return PRIMARY;
        if (value.contains("حفظ")) return BUTTON_SAVE;
        if (value.contains("نسخ")) return BUTTON_COPY;
        if (value.contains("خرائط") || value.contains("خريط")) return BUTTON_MAP;
        if (value.contains("موقعي") || value.contains("موقع الجهاز") || value.contains("توسيط")
                || value.contains("تمركز")) return BUTTON_LOCATION;
        if (value.contains("مفضلة")) return BUTTON_FAVORITE;
        if (value.contains("تحقق") || value.contains("فحص") || value.contains("تحديث")) return BUTTON_VERIFY;
        if (value.contains("تفعيل")) return BUTTON_ACTIVATE;
        if (value.contains("اشتراك")) return BUTTON_SUBSCRIPTION;
        if (value.contains("اعدادات")) return BUTTON_SETTINGS;
        if (value.contains("اخفاء")) return BUTTON_HIDE;
        if (value.contains("اظهار") || value.contains("ظهور")) return BUTTON_SHOW;
        if (value.contains("رجوع") || value.contains("عودة")) return BUTTON_BACK;
        if (value.contains("تاكيد") || value.contains("تحديد") || value.contains("استخدم")
                || value.contains("تشغيل") || value.equals("نعم")) return BUTTON_SELECT;
        if (value.contains("تعديل")) return BUTTON_EDIT;
        if (value.contains("توسيع") || value.contains("تصغير")) return BUTTON_EXPAND;
        if (value.contains("سجل")) return BUTTON_HISTORY;
        return fallback;
    }

    private static String normalizedLabel(String label) {
        StringBuilder normalized = new StringBuilder(label.length());
        for (int i = 0; i < label.length(); i++) {
            char value = label.charAt(i);
            if ((value >= '\u064B' && value <= '\u065F') || value == '\u0670'
                    || value == '\u0640' || value == '\uFE0E' || value == '\uFE0F') continue;
            if (value == 'أ' || value == 'إ' || value == 'آ') value = 'ا';
            normalized.append(value);
        }
        return normalized.toString().trim().toLowerCase(java.util.Locale.ROOT);
    }

    /** Graphite/cyan iOS reference: neutral actions, red stop/delete controls. */
    public static int referenceActionColor(String label, int fallback) {
        String value = normalizedLabel(label == null ? "" : label);
        if (value.contains("ايقاف") || value.contains("حذف") || value.contains("تزييف")) return BUTTON_STOP;
        return SURFACE_RAISED;
    }

    public static void colorButton(Button button, String label, int fallback, int foreground) {
        int background = semanticColor(label, fallback);
        int activeText = isSemanticLabel(label) || contrast(foreground, background) < 4.5d
                ? readableTextColor(background) : foreground;
        button.setTextColor(new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}
        }, new int[] {MUTED, activeText}));
        button.setBackgroundTintList(null);
        button.setBackground(buttonBackground(button.getContext(), background));
    }

    /** Filled edit/delete icons use the same colors and feedback as the text buttons. */
    public static void colorIconButton(ImageButton button, String label, int fallback) {
        int background = referenceActionColor(label, fallback);
        button.setImageTintList(new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}
        }, new int[] {MUTED, readableTextColor(background)}));
        button.setBackgroundTintList(null);
        button.setBackground(buttonBackground(button.getContext(), background));
        button.setMinimumWidth(dp(button.getContext(), 48));
        button.setMinimumHeight(dp(button.getContext(), 48));
    }

    private static boolean isSemanticLabel(String label) {
        return semanticColor(label, Color.TRANSPARENT) != Color.TRANSPARENT;
    }

    private static int readableTextColor(int background) {
        return contrast(Color.WHITE, background) >= contrast(BACKGROUND, background)
                ? Color.WHITE : BACKGROUND;
    }

    private static double contrast(int a, int b) {
        double left = luminance(a), right = luminance(b);
        return (Math.max(left, right) + 0.05d) / (Math.min(left, right) + 0.05d);
    }

    private static double luminance(int color) {
        return 0.2126d * linearChannel(Color.red(color))
                + 0.7152d * linearChannel(Color.green(color))
                + 0.0722d * linearChannel(Color.blue(color));
    }

    private static double linearChannel(int channel) {
        double value = channel / 255d;
        return value <= 0.04045d ? value / 12.92d : Math.pow((value + 0.055d) / 1.055d, 2.4d);
    }

    /** Reuse the existing close listener, but remove its button from the title/header row. */
    public static void moveCloseToFooter(LinearLayout root) {
        Button close = findClose(root);
        if (close == null || !(close.getParent() instanceof ViewGroup)) return;
        ((ViewGroup) close.getParent()).removeView(close);
        close.setText("إغلاق");
        close.setContentDescription("إغلاق");
        if (close.getTag() == null) close.setTag("wolfox.dialog.close.footer");
        close.setTextSize(14);
        close.setMaxLines(1);
        close.setMinHeight(dp(root.getContext(), 48));
        close.setMinimumHeight(dp(root.getContext(), 48));
        close.setPadding(dp(root.getContext(), 12), dp(root.getContext(), 6),
                dp(root.getContext(), 12), dp(root.getContext(), 6));
        colorButton(close, "إغلاق", DANGER, TEXT);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(root.getContext(), 12), 0, dp(root.getContext(), 4));
        root.addView(close, lp);
        TextView credit = new TextView(root.getContext());
        credit.setText(new String(android.util.Base64.decode("2KjYsdmF2KzYqSDZiNiq2LfZiNmK2LEg2K7Yp9mE2K8g2KfZhNiy2KjZitiv2Yo=", android.util.Base64.DEFAULT), java.nio.charset.StandardCharsets.UTF_8));
        credit.setTextSize(13);
        credit.setTypeface(Typeface.create("serif", Typeface.BOLD));
        credit.setTextColor(0xFFFFD68A);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(dp(root.getContext(), 10), dp(root.getContext(), 10),
                dp(root.getContext(), 10), dp(root.getContext(), 10));
        credit.setBackground(rounded(0xFF27324B, 12, 0xFFDBA84E, 1));
        credit.setTag("wolfox.developer.credit");
        LinearLayout.LayoutParams creditLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        creditLp.setMargins(0, dp(root.getContext(), 8), 0, dp(root.getContext(), 6));
        root.addView(credit, creditLp);
    }

    private static Button findClose(View view) {
        if (view instanceof Button && view.getContentDescription() != null
                && view.getContentDescription().toString().startsWith("إغلاق")) return (Button) view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findClose(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    private static RippleDrawable buttonBackground(Context context, int fill) {
        StateListDrawable content = new StateListDrawable();
        content.addState(new int[] {-android.R.attr.state_enabled},
                buttonShape(context, mix(fill, SURFACE, 0.90f), mix(fill, SURFACE, 0.30f)));
        content.addState(new int[] {android.R.attr.state_focused},
                buttonShape(context, fill, ACCENT));
        content.addState(new int[] {}, buttonShape(context, fill, mix(fill, Color.WHITE, 0.18f)));
        ColorStateList ripple = new ColorStateList(new int[][] {
                new int[] {-android.R.attr.state_enabled}, new int[] {}
        }, new int[] {Color.TRANSPARENT, 0x26000000});
        return new RippleDrawable(ripple, content, buttonShape(context, Color.WHITE, Color.WHITE));
    }

    private static GradientDrawable buttonShape(Context context, int fill, int edge) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(context, 14));
        shape.setStroke(dp(context, 1), edge);
        return shape;
    }

    private static int mix(int from, int to, float fraction) {
        return Color.rgb(Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * fraction),
                Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * fraction),
                Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * fraction));
    }

    /** Neutral close color cannot be mistaken for stopping the running GPS session. */
    public static Button closeButton(Context context) {
        Button button = button(context, WFTheme.icon("×"), SURFACE_RAISED, DANGER);
        button.setTextSize(20);
        button.setContentDescription("إغلاق");
        colorButton(button, "إغلاق", DANGER, TEXT);
        button.setPadding(0, 0, 0, 0);
        button.setMinHeight(dp(context, 48));
        button.setMinimumHeight(dp(context, 48));
        button.setMinWidth(dp(context, 48));
        button.setMinimumWidth(dp(context, 48));
        return button;
    }

    /** Apply the shared surface and semantic button colors after AlertDialog.show(). */
    public static void styleAlertDialog(AlertDialog dialog) {
        if (dialog == null) return;
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(rounded(SURFACE, 16, BORDER, 1));
        }
        styleDialogButton(dialog, AlertDialog.BUTTON_POSITIVE, PRIMARY);
        styleDialogButton(dialog, AlertDialog.BUTTON_NEGATIVE, BUTTON_CLOSE);
        styleDialogButton(dialog, AlertDialog.BUTTON_NEUTRAL, BUTTON_CLEAR);
    }

    private static void styleDialogButton(AlertDialog dialog, int which, int fallback) {
        Button button = dialog.getButton(which);
        if (button == null) return;
        colorButton(button, button.getText().toString(), fallback, TEXT);
        button.setAllCaps(false);
        button.setMaxLines(2);
        button.setMinHeight(dp(button.getContext(), 48));
        button.setMinimumHeight(dp(button.getContext(), 48));
        WFTheme.apply(button, true);
    }

    public static TextView text(Context context, CharSequence value, int size,
                                int color, boolean bold) {
        TextView text = new TextView(context);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        WFTheme.apply(text, bold);
        return text;
    }

    public static android.widget.LinearLayout.LayoutParams weighted(int heightDp) {
        return new android.widget.LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    public static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private static int dpRadius(int value) {
        return value * 2;
    }
}
