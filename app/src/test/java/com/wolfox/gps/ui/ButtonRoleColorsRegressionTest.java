package com.wolfox.gps.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Guards the semantic color contract of visible WolFox actions.  This test creates only native
 * Button widgets: it neither opens application dialogs nor calls application click listeners.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {21, 34})
public class ButtonRoleColorsRegressionTest {
    private static final int FALLBACK_SURFACE = 0xFF102030;
    private static final int UNMAPPED = 0xFF010203;

    private static final Role[] CORE_ROLES = {
            new Role("primary destructive", "تزييف الموقع", "تَزْيِيفُ الموقع", "✦  تزييف الموقع"),
            new Role("search", "بحث", "بَحْث", "⌕  بحث"),
            new Role("paste", "لصق الرابط", "لَصْق الرابط", "⌘  لصق الرابط"),
            new Role("clear", "مسح", "مَسْح", "⌫  مسح"),
            new Role("save", "حفظ", "حِفْظ", "★  حفظ"),
            new Role("copy", "نسخ", "نَسْخ", "⧉  نسخ"),
            new Role("map", "خريطة", "خَريطة", "⌖  خريطة"),
            new Role("favorite", "المفضلة", "المُفَضَّلَة", "★  المفضلة"),
            new Role("selection", "تحديد الموقع وتشغيله", "تَحْدِيد الموقع وتشغيله",
                    "✓  تحديد الموقع وتشغيله"),
            new Role("stop", "إيقاف التزييف", "إِيقَاف التزييف", "■  إيقاف التزييف"),
            new Role("exit", "إغلاق", "إِغْلَاق", "×  إغلاق")
    };

    /** Literal action labels emitted by the current APK, with decorative glyphs removed. */
    private static final String[] APK_BUTTON_LABELS = {
            "تزييف الموقع", "إيقاف التزييف", "بحث", "بحث عن موقع", "ابدأ البحث",
            "لصق الرابط", "لصق", "مسح", "مسح الكل", "حفظ", "حفظ الحالي", "حفظ الموقع",
            "نسخ", "نسخ الإحداثيات", "الخريطة", "الخرائط", "خريطة", "المفضلة",
            "موقع المفضلة", "تحديد الموقع وتشغيله", "تشغيل المحاكاة", "استخدم", "إغلاق",
            "فتح تفاصيل الاشتراك", "عرض تفاصيل الاشتراك", "تحقق من الاشتراك الآن",
            "إدخال أو إعادة تفعيل كود", "تفعيل الآن", "الاشتراك", "موقعي", "تمركز",
            "الإعدادات", "إخفاء الأيقونة", "إخفاء", "إظهار", "رجوع للوحة", "حذف",
            "تعديل", "إلغاء", "توسيع", "تصغير", "سجل العمليات", "نعم"
    };

    @Test public void coreRoles_areDistinctAndKeepTheirMeaningWithIconsOrArabicDiacritics() {
        Set<Integer> colors = new HashSet<>();
        for (Role role : CORE_ROLES) {
            int canonical = semantic(role.label);
            assertTrue("A semantic color is required for " + role.name,
                    colors.add(canonical));
            assertEquals("A decorative prefix must not change the " + role.name + " role",
                    canonical, semantic(role.prefixed));
            assertEquals("Arabic tashkeel must not change the " + role.name + " role",
                    canonical, semantic(role.diacritized));
        }
    }

    @Test public void coreRoles_keepTheirIntentionalColorFamiliesWithoutPinningExactTokens() {
        assertRed("Primary destructive", semantic("تزييف الموقع"));
        assertBlue("Search", semantic("بحث"));
        assertTeal("Paste", semantic("لصق الرابط"));
        assertOrange("Clear", semantic("مسح"));
        assertPurple("Save", semantic("حفظ"));
        assertBlue("Copy indigo", semantic("نسخ"));
        assertTeal("Map", semantic("خريطة"));
        assertGold("Favorite", semantic("المفضلة"));
        assertGreen("Selection", semantic("تحديد الموقع وتشغيله"));
        assertRed("Stop", semantic("إيقاف التزييف"));
    }

    @Test public void visibleApkButtonLabels_doNotSilentlyFallBackToTheCommonSurface() {
        for (String label : APK_BUTTON_LABELS) {
            assertNotEquals("Add a semantic role for visible APK action: " + label,
                    UNMAPPED, WFDesign.semanticColor(label, UNMAPPED));
        }

        assertEquals("All save labels must stay purple save actions", semantic("حفظ"),
                semantic("حفظ الحالي"));
        assertEquals("All map labels must stay map actions", semantic("خريطة"),
                semantic("الخرائط"));
        assertEquals("Current-location selection must match the selection action",
                semantic("تحديد الموقع وتشغيله"), semantic("تشغيل المحاكاة"));
        assertEquals("Use must retain the selection action color", semantic("تحديد الموقع وتشغيله"),
                semantic("استخدم"));
    }

    @Test public void semanticButtons_haveDistinctReadableForegroundsInEnabledAndDisabledStates() {
        Context context = RuntimeEnvironment.getApplication();
        for (Role role : CORE_ROLES) {
            int semanticBackground = semantic(role.label);
            Button button = WFDesign.button(context, role.label, FALLBACK_SURFACE, Color.WHITE);

            button.setEnabled(true);
            button.refreshDrawableState();
            assertEquals("Enabled widget must render its semantic surface for " + role.name,
                    semanticBackground, currentButtonFill(button));
            assertContrast("Enabled text for " + role.name, button.getCurrentTextColor(),
                    semanticBackground);

            if (requiresDarkForeground(role.label)) {
                assertTrue(role.name + " uses a light semantic surface and needs dark text",
                        luminance(button.getCurrentTextColor()) < luminance(semanticBackground));
            }

            button.setEnabled(false);
            button.refreshDrawableState();
            assertFalse(button.isEnabled());
            int disabledSurface = currentButtonFill(button);
            assertNotEquals("Disabled " + role.name + " needs its own subdued surface",
                    semanticBackground, disabledSurface);
            assertContrast("Disabled text for " + role.name, button.getCurrentTextColor(),
                    disabledSurface);
        }
    }

    @Test public void recoloringAButton_doesNotReplaceItsExistingClickListener() {
        Context context = RuntimeEnvironment.getApplication();
        Button button = WFDesign.button(context, "بحث", FALLBACK_SURFACE, Color.WHITE);
        final int[] clicks = {0};
        button.setOnClickListener(view -> clicks[0]++);

        WFDesign.colorButton(button, "بحث", FALLBACK_SURFACE, Color.WHITE);
        button.performClick();

        assertEquals("Color-only styling must preserve the original click behavior", 1, clicks[0]);
    }

    @Test public void exitIsNeutralGrayAndRemainsDistinctFromTheDeepRedStopAction() {
        int exit = semantic("إغلاق");
        int stop = semantic("إيقاف التزييف");
        assertNotEquals("Closing a dialog must not look like stopping a running mock", stop, exit);
        assertTrue("Exit is a neutral gray role", channelRange(exit) <= 52);
        assertTrue("Stop remains a red role", Color.red(stop) > Color.green(stop)
                && Color.red(stop) > Color.blue(stop));
    }

    private static boolean requiresDarkForeground(String label) {
        return "مسح".equals(label) || "خريطة".equals(label) || "المفضلة".equals(label)
                || "تحديد الموقع وتشغيله".equals(label);
    }

    private static int semantic(String label) {
        int color = WFDesign.semanticColor(label, UNMAPPED);
        assertNotEquals("Semantic role missing for " + label, UNMAPPED, color);
        return color;
    }

    /** Reads the StateListDrawable selected by the actual native button state, including API 21. */
    private static int currentButtonFill(Button button) {
        Drawable background = button.getBackground();
        assertTrue("Buttons must retain native ripple feedback", background instanceof RippleDrawable);
        Drawable content = ((RippleDrawable) background).getDrawable(0);
        assertTrue("Ripple content must select stateful button surfaces",
                content instanceof StateListDrawable);
        StateListDrawable surfaces = (StateListDrawable) content;
        surfaces.setState(button.getDrawableState());
        Drawable shape = surfaces.getCurrent();
        assertTrue("Selected button surface must be a solid rounded shape",
                shape instanceof GradientDrawable);
        return gradientFill((GradientDrawable) shape);
    }

    /** getColor() arrived after API 21; its GradientState still exposes the ColorStateList. */
    private static int gradientFill(GradientDrawable drawable) {
        try {
            Method getColor = GradientDrawable.class.getMethod("getColor");
            ColorStateList colors = (ColorStateList) getColor.invoke(drawable);
            if (colors != null) return colors.getColorForState(drawable.getState(),
                    colors.getDefaultColor());
        } catch (NoSuchMethodException unavailableOnApi21) {
            // Fall through to the API-21 GradientState representation below.
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not read GradientDrawable.getColor()", error);
        }
        try {
            Object state = drawable.getConstantState();
            Field colorsField = state.getClass().getField("mColorStateList");
            ColorStateList colors = (ColorStateList) colorsField.get(state);
            assertTrue("API-21 button surface must retain its solid ColorStateList", colors != null);
            return colors.getColorForState(drawable.getState(), colors.getDefaultColor());
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("Could not reflect API-21 GradientDrawable color", error);
        }
    }

    private static void assertContrast(String message, int foreground, int background) {
        assertTrue(message + " must be at least 4.5:1 but was " + contrast(foreground, background),
                contrast(foreground, background) >= 4.5d);
    }

    private static double contrast(int a, int b) {
        double la = luminance(a);
        double lb = luminance(b);
        return (Math.max(la, lb) + 0.05d) / (Math.min(la, lb) + 0.05d);
    }

    private static double luminance(int color) {
        return 0.2126d * channel(Color.red(color)) + 0.7152d * channel(Color.green(color))
                + 0.0722d * channel(Color.blue(color));
    }

    private static double channel(int value) {
        double normalized = value / 255d;
        return normalized <= 0.04045d ? normalized / 12.92d
                : Math.pow((normalized + 0.055d) / 1.055d, 2.4d);
    }

    private static int channelRange(int color) {
        return Math.max(Color.red(color), Math.max(Color.green(color), Color.blue(color)))
                - Math.min(Color.red(color), Math.min(Color.green(color), Color.blue(color)));
    }

    private static void assertRed(String role, int color) {
        assertTrue(role + " must remain red", Color.red(color) > Color.green(color)
                && Color.red(color) > Color.blue(color));
    }

    private static void assertBlue(String role, int color) {
        assertTrue(role + " must remain blue/indigo", Color.blue(color) > Color.red(color) + 20
                && Color.blue(color) > Color.green(color) + 20);
    }

    private static void assertTeal(String role, int color) {
        assertTrue(role + " must remain teal/turquoise", Color.green(color) > Color.red(color) + 25
                && Color.blue(color) > Color.red(color) + 25);
    }

    private static void assertOrange(String role, int color) {
        assertTrue(role + " must remain amber/orange", Color.red(color) > Color.green(color)
                && Color.green(color) > Color.blue(color));
    }

    private static void assertPurple(String role, int color) {
        assertTrue(role + " must remain purple", Color.red(color) > Color.green(color)
                && Color.blue(color) > Color.green(color));
    }

    private static void assertGold(String role, int color) {
        assertTrue(role + " must remain gold", Color.red(color) > Color.green(color)
                && Color.green(color) > Color.blue(color));
    }

    private static void assertGreen(String role, int color) {
        assertTrue(role + " must remain green", Color.green(color) > Color.red(color) + 20
                && Color.green(color) > Color.blue(color) + 20);
    }

    private static final class Role {
        final String name;
        final String label;
        final String diacritized;
        final String prefixed;

        Role(String name, String label, String diacritized, String prefixed) {
            this.name = name;
            this.label = label;
            this.diacritized = diacritized;
            this.prefixed = prefixed;
        }
    }
}
