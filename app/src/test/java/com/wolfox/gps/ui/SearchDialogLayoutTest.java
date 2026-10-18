package com.wolfox.gps.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewParent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.wolfox.gps.model.WFLocation;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout coverage plus a local-coordinate resolver path. It never exercises GPS/mock runtime or
 * a remote geocoder/provider: exact coordinates resolve before those fallbacks are considered.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SearchDialogLayoutTest {
    private static final String[] VISIBLE_BUTTON_TAGS = {
            "wolfox.search.paste",
            "wolfox.search.clear",
            "wolfox.search.save",
            "wolfox.search.maps",
            "wolfox.search.copy",
            "wolfox.search.search",
            "wolfox.search.close"
    };

    @After
    public void dismissDialogs() {
        for (Dialog dialog : ShadowDialog.getShownDialogs()) {
            if (dialog.isShowing()) dialog.dismiss();
        }
        ShadowDialog.reset();
    }

    @Test
    public void narrowAndLargeFontLayouts_keepButtonsDisjointAndScrollable() {
        int[] widthsDp = {320, 360, 480};
        float[] fontScales = {1.3f, 1.5f};
        for (int widthDp : widthsDp) {
            for (float fontScale : fontScales) {
                assertLayout(widthDp, fontScale);
            }
        }
    }

    @Test
    public void localCoordinateResult_displaysLabelAndMakesSaveReachableWithoutNetwork()
            throws Exception {
        Activity activity = newActivity(360, 1.3f);
        try {
            SearchDialog.show(activity);
            Dialog searchDialog = ShadowDialog.getLatestDialog();
            View decor = searchDialog.getWindow().getDecorView();
            EditText input = (EditText) decor.findViewWithTag("wolfox.search.input");
            Button search = (Button) decor.findViewWithTag("wolfox.search.search");
            Button confirm = (Button) decor.findViewWithTag("wolfox.search.confirm");
            Button save = (Button) decor.findViewWithTag("wolfox.search.save");
            TextView result = (TextView) decor.findViewWithTag("wolfox.search.result");

            // Exact coordinates are parsed locally before any geocoder/provider fallback.
            input.setText("24.7136, 46.6753");
            search.performClick();
            awaitVisible(confirm);

            assertTrue(result.getText().toString().contains("إحداثيات"));
            assertTrue(result.getText().toString().contains("24.713600, 46.675300"));
            assertTrue("A resolved local result enables saving", save.isEnabled());
            assertTrue("Confirm is reachable but intentionally not clicked (no GPS fake)",
                    confirm.isEnabled());

            save.performClick();
            Dialog favoriteDialog = ShadowDialog.getLatestDialog();
            assertTrue("The save/update flow is reachable from the displayed result",
                    favoriteDialog != null && favoriteDialog != searchDialog
                            && favoriteDialog.isShowing());
        } finally {
            activity.finish();
        }
    }

    @Test
    public void resolvedSelectionCopy_preservesResolverLabelForLaterPanelDisplay() {
        WFLocation resolved = new WFLocation(24.7136d, 46.6753d, "الرياض — نتيجة محللة");
        resolved.setAltitude(17d);
        resolved.setAccuracy(4f);

        WFLocation selected = SearchDialog.copyResolvedLocation(resolved);

        assertNotNull(selected);
        assertTrue(selected != resolved);
        assertEquals("الرياض — نتيجة محللة", selected.getLabel());
        assertEquals(resolved.getLatitude(), selected.getLatitude(), 0d);
        assertEquals(resolved.getLongitude(), selected.getLongitude(), 0d);
        assertEquals(resolved.getAltitude(), selected.getAltitude(), 0d);
    }

    private void assertLayout(int widthDp, float fontScale) {
        ShadowDialog.reset();
        Activity activity = newActivity(widthDp, fontScale);
        try {
            SearchDialog.show(activity);
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertNotNull("Search dialog must be shown for layout measurement", dialog);

            View decor = dialog.getWindow().getDecorView();
            ScrollView scroll = (ScrollView) decor.findViewWithTag("wolfox.search.scroll");
            LinearLayout root = (LinearLayout) decor.findViewWithTag("wolfox.search.dialog");
            EditText input = (EditText) decor.findViewWithTag("wolfox.search.input");
            assertNotNull(scroll);
            assertNotNull(root);
            assertNotNull(input);

            int viewportHeight = 240;
            scroll.measure(exactly(widthDp), exactly(viewportHeight));
            scroll.layout(0, 0, widthDp, viewportHeight);

            assertEquals("Content uses the supplied dp width at density 1", widthDp, root.getWidth());
            assertTrue("The input remains within the dialog width",
                    boundsIn(root, input).left >= root.getPaddingLeft());
            assertTrue("The input remains within the dialog width",
                    boundsIn(root, input).right <= root.getWidth() - root.getPaddingRight());

            List<Rect> buttonBounds = new ArrayList<>();
            for (String tag : VISIBLE_BUTTON_TAGS) {
                Button button = (Button) decor.findViewWithTag(tag);
                assertNotNull("Stable tag required for " + tag, button);
                if (tag.equals("wolfox.search.save") || tag.equals("wolfox.search.maps")
                        || tag.equals("wolfox.search.copy")) {
                    assertFalse("Initial result actions must preserve their disabled state",
                            button.isEnabled());
                } else {
                    assertTrue(tag + " must be initially enabled", button.isEnabled());
                }
                Rect bounds = boundsIn(root, button);
                assertTrue(tag + " starts inside content", bounds.left >= root.getPaddingLeft());
                assertTrue(tag + " ends inside content", bounds.right <= root.getWidth()
                        - root.getPaddingRight());
                assertTrue(tag + " has a measured target", bounds.height() > 0);
                buttonBounds.add(bounds);
            }
            assertEquals("Confirm remains unavailable until resolver success", View.GONE,
                    decor.findViewWithTag("wolfox.search.confirm").getVisibility());

            for (int i = 0; i < buttonBounds.size(); i++) {
                for (int j = i + 1; j < buttonBounds.size(); j++) {
                    assertFalse("Buttons must never overlap at " + widthDp + "dp / " + fontScale,
                            Rect.intersects(buttonBounds.get(i), buttonBounds.get(j)));
                }
            }

            assertTrue("Tall Arabic layouts expose their lower controls through native scrolling",
                    root.getHeight() > scroll.getHeight());
            Rect closeBounds = buttonBounds.get(buttonBounds.size() - 1);
            assertTrue("Not every action is forced into the initial viewport",
                    closeBounds.bottom > scroll.getHeight());
            scroll.scrollTo(0, root.getHeight() - scroll.getHeight());
            assertTrue("The content has a positive native scroll range", scroll.getScrollY() > 0);
        } finally {
            Dialog dialog = ShadowDialog.getLatestDialog();
            if (dialog != null && dialog.isShowing()) dialog.dismiss();
            activity.finish();
        }
    }

    private Activity newActivity(int widthDp, float fontScale) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        configureResources(activity.getResources(), widthDp, fontScale);
        return activity;
    }

    private void configureResources(Resources resources, int widthDp, float fontScale) {
        DisplayMetrics metrics = resources.getDisplayMetrics();
        metrics.density = 1f;
        metrics.scaledDensity = fontScale;
        metrics.densityDpi = DisplayMetrics.DENSITY_DEFAULT;
        metrics.widthPixels = widthDp;
        metrics.heightPixels = 640;
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.fontScale = fontScale;
        resources.updateConfiguration(configuration, metrics);
    }

    private int exactly(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }

    private void awaitVisible(View view) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 1500L;
        while (view.getVisibility() != View.VISIBLE && System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10L);
        }
        ShadowLooper.idleMainLooper();
        assertEquals("Local coordinate resolver must deliver a visible result", View.VISIBLE,
                view.getVisibility());
    }

    private Rect boundsIn(View ancestor, View view) {
        int left = 0;
        int top = 0;
        View current = view;
        while (current != ancestor) {
            left += current.getLeft();
            top += current.getTop();
            ViewParent parent = current.getParent();
            assertTrue("Expected search control to be inside dialog root", parent instanceof View);
            current = (View) parent;
        }
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }
}
