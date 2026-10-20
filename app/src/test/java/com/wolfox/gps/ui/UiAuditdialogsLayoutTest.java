package com.wolfox.gps.ui;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wolfox.gps.util.WFStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Regression coverage for dialog geometry only.  It does not click activation, verification,
 * GPS, or visibility controls and therefore cannot exercise a network or start a mock session.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiAuditdialogsLayoutTest {
    private static final String PREFS = "wolfox_gps_prefs";
    private static final int[] COMPACT_WIDTHS_DP = {320, 360};
    private static final int LANDSCAPE_HEIGHT_DP = 320;
    private static final float LARGE_FONT_SCALE = 2f;

    @Before
    public void resetMainLooperAndStaticDialogState() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        Field storageInstance = WFStorage.class.getDeclaredField("instance");
        storageInstance.setAccessible(true);
        storageInstance.set(null, null);
        ActivationDialog.dismiss();
        WolFoxPanel.dismiss();
        ShadowDialog.reset();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @After
    public void dismissDialogsAndDrainMainLooper() {
        dismissShownDialogs();
        ActivationDialog.dismiss();
        WolFoxPanel.dismiss();
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        ShadowDialog.reset();
    }

    @Test
    public void compactLandscapeLargeFont_keepsFooterAfterTitleAndUsesNativeScrolling() {
        for (int widthDp : COMPACT_WIDTHS_DP) {
            assertDialogLayout(widthDp, "التفعيل", activity -> ActivationDialog.showRequired(activity));
            assertDialogLayout(widthDp, "الإعدادات", SettingsDialog::show);
            assertDialogLayout(widthDp, "الاشتراك", SubscriptionDialog::show);
            assertDialogLayout(widthDp, "المواقع المفضلة", FavoritesDialog::show);
            assertDialogLayout(widthDp, "سجل العمليات", HistoryDialog::show);
        }
    }

    @Test
    public void compactLandscapeLargeFont_allVisibleButtonTargetsAreAtLeast48dp() {
        for (int widthDp : COMPACT_WIDTHS_DP) {
            assertButtonTargets(widthDp, "التفعيل", activity -> ActivationDialog.showRequired(activity));
            assertButtonTargets(widthDp, "المواقع المفضلة", FavoritesDialog::show);
            assertButtonTargets(widthDp, "سجل العمليات", HistoryDialog::show);
        }
    }

    private void assertDialogLayout(int widthDp, String title, DialogPresenter presenter) {
        Activity activity = newCompactLandscapeActivity(widthDp);
        try {
            presenter.show(activity);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertNotNull("Dialog must be shown for " + title, dialog);

            Button footer = (Button) dialog.getWindow().getDecorView()
                    .findViewWithTag("wolfox.dialog.close.footer");
            assertNotNull("Footer close must remain discoverable for " + title, footer);
            ScrollView scroll = scrollAncestor(footer);
            assertNotNull("Footer must be inside a native scroll container for " + title, scroll);
            ViewGroup root = (ViewGroup) scroll.getChildAt(0);
            assertNotNull("Scroll content must exist for " + title, root);

            measure(scroll, dialogWidth(widthDp), Math.round(LANDSCAPE_HEIGHT_DP * 0.86f));
            TextView heading = textContaining(root, title);
            assertNotNull("Visible heading is required for " + title, heading);
            assertTrue("Footer close keeps a 48dp target for " + title,
                    footer.getHeight() >= WFDesign.dp(activity, 48));
            assertTrue("Header and close footer must not overlap for " + title,
                    bottomIn(root, heading) <= topIn(root, footer));
            assertTrue("Large Arabic text must extend into native scrolling for " + title,
                    root.getHeight() > scroll.getHeight());

            scroll.scrollTo(0, root.getHeight() - scroll.getHeight());
            assertTrue("Footer must be reachable by native scrolling for " + title,
                    scroll.getScrollY() > 0);
        } finally {
            dismissShownDialogs();
            activity.finish();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private void assertButtonTargets(int widthDp, String title, DialogPresenter presenter) {
        Activity activity = newCompactLandscapeActivity(widthDp);
        try {
            presenter.show(activity);
            Shadows.shadowOf(Looper.getMainLooper()).idle();
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            Button footer = (Button) dialog.getWindow().getDecorView()
                    .findViewWithTag("wolfox.dialog.close.footer");
            ScrollView scroll = scrollAncestor(footer);
            measure(scroll, dialogWidth(widthDp), Math.round(LANDSCAPE_HEIGHT_DP * 0.86f));
            ViewGroup root = (ViewGroup) scroll.getChildAt(0);

            List<Button> buttons = new ArrayList<>();
            collectButtons(root, buttons);
            assertFalse("Expected interactive controls in " + title, buttons.isEmpty());
            for (Button button : buttons) {
                assertTrue("48dp touch target required for “" + button.getText() + "” in " + title,
                        button.getHeight() >= WFDesign.dp(activity, 48));
                assertTrue("Button stays within compact dialog width in " + title,
                        leftIn(root, button) >= root.getPaddingLeft()
                                && rightIn(root, button) <= root.getWidth() - root.getPaddingRight());
            }
        } finally {
            dismissShownDialogs();
            activity.finish();
            Shadows.shadowOf(Looper.getMainLooper()).idle();
        }
    }

    private Activity newCompactLandscapeActivity(int widthDp) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Resources resources = activity.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        metrics.density = 1f;
        metrics.scaledDensity = LARGE_FONT_SCALE;
        metrics.densityDpi = DisplayMetrics.DENSITY_DEFAULT;
        metrics.widthPixels = widthDp;
        metrics.heightPixels = LANDSCAPE_HEIGHT_DP;
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.fontScale = LARGE_FONT_SCALE;
        configuration.orientation = Configuration.ORIENTATION_LANDSCAPE;
        resources.updateConfiguration(configuration, metrics);
        return activity;
    }

    /** Narrower than every production dialog width factor (0.92 or 0.94). */
    private int dialogWidth(int screenWidthDp) {
        return Math.round(screenWidthDp * 0.90f);
    }

    private void measure(View view, int width, int height) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private ScrollView scrollAncestor(View view) {
        ViewParent parent = view.getParent();
        while (parent instanceof View) {
            if (parent instanceof ScrollView) return (ScrollView) parent;
            parent = parent.getParent();
        }
        return null;
    }

    private TextView textContaining(View view, String expected) {
        if (view instanceof TextView
                && ((TextView) view).getText().toString().contains(expected)) {
            return (TextView) view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                TextView found = textContaining(group.getChildAt(i), expected);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void collectButtons(View view, List<Button> buttons) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (view instanceof Button) buttons.add((Button) view);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectButtons(group.getChildAt(i), buttons);
            }
        }
    }

    private int leftIn(View ancestor, View view) {
        return horizontalOffset(ancestor, view, false);
    }

    private int rightIn(View ancestor, View view) {
        return horizontalOffset(ancestor, view, true);
    }

    private int topIn(View ancestor, View view) {
        return verticalOffset(ancestor, view, false);
    }

    private int bottomIn(View ancestor, View view) {
        return verticalOffset(ancestor, view, true);
    }

    private int horizontalOffset(View ancestor, View view, boolean end) {
        int offset = end ? view.getWidth() : 0;
        View current = view;
        while (current != ancestor) {
            offset += current.getLeft();
            assertTrue("Expected control inside dialog root", current.getParent() instanceof View);
            current = (View) current.getParent();
        }
        return offset;
    }

    private int verticalOffset(View ancestor, View view, boolean end) {
        int offset = end ? view.getHeight() : 0;
        View current = view;
        while (current != ancestor) {
            offset += current.getTop();
            assertTrue("Expected control inside dialog root", current.getParent() instanceof View);
            current = (View) current.getParent();
        }
        return offset;
    }

    private void dismissShownDialogs() {
        for (Dialog dialog : ShadowDialog.getShownDialogs()) {
            if (dialog.isShowing()) dialog.dismiss();
        }
    }

    private interface DialogPresenter {
        void show(Activity activity);
    }
}
