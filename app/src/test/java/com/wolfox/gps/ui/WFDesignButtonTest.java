package com.wolfox.gps.ui;

import static org.junit.Assert.*;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.View;
import android.widget.Button;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** No network or runtime hooks: validates the shared button's real native widget contract. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = {21, 34})
public class WFDesignButtonTest {
    @Test public void button_hasNativeRippleAndAccessibleArabicDefaults() {
        Context c = RuntimeEnvironment.getApplication();
        Button b = WFDesign.button(c, "تزييف الموقع", WFDesign.DANGER, 0xFFFFFFFF);
        assertTrue(b.getBackground() instanceof RippleDrawable);
        assertEquals("تزييف الموقع", b.getContentDescription().toString());
        assertEquals(View.LAYOUT_DIRECTION_RTL, b.getLayoutDirection());
        assertTrue(b.getMinimumHeight() >= WFDesign.dp(c, 48));
        assertEquals(2, b.getMaxLines());
        assertNull("Framework tint must not override explicit semantic colors", b.getBackgroundTintList());
    }

    @Test public void disabledState_changesBothTextAndSurface_withoutChangingLabel() {
        Context c = RuntimeEnvironment.getApplication();
        Button b = WFDesign.button(c, "تزييف الموقع", WFDesign.DANGER, 0xFFFFFFFF);
        RippleDrawable ripple = (RippleDrawable) b.getBackground();
        StateListDrawable surfaces = (StateListDrawable) ripple.getDrawable(0);
        b.setEnabled(true);
        b.refreshDrawableState();
        // Exercise the selector with the real widget state; this test widget is detached.
        surfaces.setState(b.getDrawableState());
        Drawable enabled = surfaces.getCurrent();
        assertEquals(0xFFFFFFFF, b.getCurrentTextColor());
        b.setEnabled(false);
        b.refreshDrawableState();
        surfaces.setState(b.getDrawableState());
        assertFalse(b.isEnabled());
        assertEquals(WFDesign.MUTED, b.getCurrentTextColor());
        assertNotSame("Disabled state must use its own subdued shape", enabled, surfaces.getCurrent());
        assertEquals("تزييف الموقع", b.getText().toString());
    }

    @Test public void focusedState_hasDistinctSurfaceAndCloseRemainsExplicit() {
        Context c = RuntimeEnvironment.getApplication();
        Button b = WFDesign.button(c, "بحث", WFDesign.PRIMARY, WFDesign.TEXT);
        StateListDrawable surfaces = (StateListDrawable) ((RippleDrawable) b.getBackground()).getDrawable(0);
        surfaces.setState(new int[] {android.R.attr.state_enabled});
        Drawable normal = surfaces.getCurrent();
        surfaces.setState(new int[] {android.R.attr.state_enabled, android.R.attr.state_focused});
        assertNotSame(normal, surfaces.getCurrent());
        Button close = WFDesign.closeButton(c);
        assertEquals("إغلاق", close.getContentDescription().toString());
        assertTrue(close.getMinimumWidth() >= WFDesign.dp(c, 44));
        assertTrue(close.getMinimumHeight() >= WFDesign.dp(c, 44));
    }
}
