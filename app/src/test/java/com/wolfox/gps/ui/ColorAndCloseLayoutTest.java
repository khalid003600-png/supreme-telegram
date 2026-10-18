package com.wolfox.gps.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.wolfox.gps.util.WFTheme;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashSet;
import java.util.Set;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class ColorAndCloseLayoutTest {
    private static final String[] SEARCH_ROLES = {
        "لصق الرابط", "مسح", "بحث", "تحديد الموقع وتشغيله", "حفظ", "الخرائط", "نسخ", "إغلاق"
    };

    @Test public void searchRoles_haveDistinctColorsAndReadableTextContrast() {
        Set<Integer> colors = new HashSet<>();
        for (String role : SEARCH_ROLES) {
            int background = WFDesign.semanticColor(role, WFDesign.SURFACE_RAISED);
            assertTrue("Each search action has its own distinct color: " + role, colors.add(background));
            Button b = WFDesign.button(RuntimeEnvironment.getApplication(), role,
                    WFDesign.SURFACE_RAISED, WFDesign.TEXT);
            assertTrue("Actual button text contrast must reach 4.5:1 for " + role,
                    contrast(b.getCurrentTextColor(), background) >= 4.5d);
            assertTrue(b.getCurrentTextColor() == Color.WHITE
                    || b.getCurrentTextColor() == WFDesign.BACKGROUND);
        }
    }

    @Test public void secondaryTypography_hasReadableContrastOnAllSharedSurfaces() {
        for (int surface : new int[] {WFDesign.BACKGROUND, WFDesign.SURFACE, WFDesign.SURFACE_RAISED}) {
            assertTrue(contrast(WFDesign.MUTED, surface) >= 4.5d);
            assertTrue(contrast(WFDesign.TEXT, surface) >= 4.5d);
        }
    }

    @Test public void closeIsMovedBelowTitle_preservesListenerAndDoesNotDuplicate() {
        Context c = RuntimeEnvironment.getApplication();
        LinearLayout root = new LinearLayout(c);
        root.setOrientation(LinearLayout.VERTICAL);
        LinearLayout header = new LinearLayout(c);
        TextView title = WFDesign.text(c, "البحث عن موقع", 19, WFDesign.TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button close = WFDesign.closeButton(c);
        close.setTag("test.close.stable");
        final int[] clicked = {0};
        close.setOnClickListener(v -> clicked[0]++);
        header.addView(close, new LinearLayout.LayoutParams(WFDesign.dp(c, 44), WFDesign.dp(c, 44)));
        root.addView(header);
        root.addView(WFDesign.button(c, "بحث", WFDesign.PRIMARY, WFDesign.TEXT));
        WFDesign.moveCloseToFooter(root);
        WFDesign.moveCloseToFooter(root);
        root.measure(View.MeasureSpec.makeMeasureSpec(WFDesign.dp(c, 320), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(WFDesign.dp(c, 720), View.MeasureSpec.AT_MOST));
        root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
        assertEquals(1, header.getChildCount());
        assertTrue(root.getChildAt(root.getChildCount() - 1) == close);
        assertEquals("test.close.stable", close.getTag());
        assertEquals("إغلاق", close.getText().toString());
        assertTrue(close.getTop() >= header.getBottom());
        assertTrue(close.getMeasuredHeight() >= WFDesign.dp(c, 48));
        close.performClick();
        assertEquals(1, clicked[0]);
    }

    @Test public void typographyRetainsMediumFontAndDoesNotShrinkSmallText() {
        Context c = RuntimeEnvironment.getApplication();
        LinearLayout root = new LinearLayout(c);
        TextView title = WFDesign.text(c, "الموقع المحدد", 19, WFDesign.TEXT, true);
        TextView caption = WFDesign.text(c, "تفاصيل الموقع", 10, WFDesign.MUTED, false);
        root.addView(title);
        root.addView(caption);
        WFTheme.applyTree(root);
        assertTrue(title.getTypeface() == WFTheme.medium(c));
        assertTrue(caption.getTextSize() >= 13f * c.getResources().getDisplayMetrics().scaledDensity);
    }

    private static double contrast(int a, int b) {
        double la = luminance(a), lb = luminance(b);
        return (Math.max(la, lb) + 0.05d) / (Math.min(la, lb) + 0.05d);
    }
    private static double luminance(int color) {
        return 0.2126d * channel(Color.red(color)) + 0.7152d * channel(Color.green(color))
                + 0.0722d * channel(Color.blue(color));
    }
    private static double channel(int value) {
        double c = value / 255d;
        return c <= 0.04045d ? c / 12.92d : Math.pow((c + 0.055d) / 1.055d, 2.4d);
    }
}
