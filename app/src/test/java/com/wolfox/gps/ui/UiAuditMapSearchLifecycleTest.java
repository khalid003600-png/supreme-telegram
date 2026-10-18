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

import com.wolfox.gps.util.MapLinkParser;
import com.wolfox.gps.util.MapSearchResolver;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowDialog;
import org.robolectric.shadows.ShadowLooper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * UI-audit regressions only: local parser paths and cancellation state; no host, GPS, or network.
 * The setup/teardown flushes the main looper and dialog singleton shadow so callbacks cannot leak
 * from one Robolectric case into the next.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiAuditMapSearchLifecycleTest {
    private static final String[] ACTION_TAGS = {
            "wolfox.search.paste", "wolfox.search.clear", "wolfox.search.save",
            "wolfox.search.maps", "wolfox.search.copy", "wolfox.search.search",
            "wolfox.search.close"
    };

    @Before
    public void resetMainLooperAndStaticDialogState() {
        MapDialog.dismiss();
        ShadowLooper.idleMainLooper();
        ShadowDialog.reset();
    }

    @After
    public void dismissDialogsAndFlushCallbacks() {
        for (Dialog dialog : ShadowDialog.getShownDialogs()) {
            if (dialog.isShowing()) dialog.dismiss();
        }
        MapDialog.dismiss();
        ShadowLooper.idleMainLooper();
        ShadowDialog.reset();
    }

    @Test
    public void cancellationBeforeMainDelivery_dropsQueuedResolverCallback() {
        AtomicInteger delivered = new AtomicInteger();
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 513; i++) tooLong.append('x');

        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        try {
            MapSearchResolver.Request request = MapSearchResolver.resolve(
                    activity, tooLong.toString(), result -> delivered.incrementAndGet());
            request.cancel();
            ShadowLooper.idleMainLooper();

            assertTrue(request.isCancelled());
            assertEquals("A cancelled UI owner must receive no posted result", 0, delivered.get());
        } finally {
            activity.finish();
        }
    }

    @Test
    public void dismissDuringLocalResolution_doesNotReviveSearchControls() throws Exception {
        Activity activity = newActivity(360, 640, 1f, Configuration.ORIENTATION_PORTRAIT);
        try {
            SearchDialog.show(activity);
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            View decor = dialog.getWindow().getDecorView();
            EditText input = (EditText) decor.findViewWithTag("wolfox.search.input");
            Button search = (Button) decor.findViewWithTag("wolfox.search.search");
            Button confirm = (Button) decor.findViewWithTag("wolfox.search.confirm");
            assertNotNull(input);
            assertNotNull(search);
            assertNotNull(confirm);

            input.setText("24.7136, 46.6753"); // Local parser; no geocoder/provider request.
            search.performClick();
            dialog.dismiss();
            drainMainFor(300L);

            assertFalse("The dialog was explicitly dismissed", dialog.isShowing());
            assertEquals("A delayed resolver result must not make a dismissed UI actionable",
                    View.GONE, confirm.getVisibility());
        } finally {
            activity.finish();
        }
    }

    @Test
    public void shortLandscapeAndFontScaleTwo_keepActionsDisjointAndScrollable() {
        Activity activity = newActivity(640, 320, 2f, Configuration.ORIENTATION_LANDSCAPE);
        try {
            SearchDialog.show(activity);
            Dialog dialog = ShadowDialog.getLatestDialog();
            assertNotNull(dialog);
            View decor = dialog.getWindow().getDecorView();
            ScrollView scroll = (ScrollView) decor.findViewWithTag("wolfox.search.scroll");
            LinearLayout root = (LinearLayout) decor.findViewWithTag("wolfox.search.dialog");
            assertNotNull(scroll);
            assertNotNull(root);

            scroll.measure(exactly(640), exactly(288));
            scroll.layout(0, 0, 640, 288);
            List<Rect> bounds = new ArrayList<>();
            for (String tag : ACTION_TAGS) {
                Button button = (Button) decor.findViewWithTag(tag);
                assertNotNull("Stable action tag required: " + tag, button);
                Rect rect = boundsIn(root, button);
                assertTrue(tag + " must fit within the RTL dialog width", rect.left >= root.getPaddingLeft()
                        && rect.right <= root.getWidth() - root.getPaddingRight());
                assertTrue(tag + " must retain a touch target", rect.height() > 0);
                bounds.add(rect);
            }
            for (int i = 0; i < bounds.size(); i++) {
                for (int j = i + 1; j < bounds.size(); j++) {
                    assertFalse("Actions overlap at landscape/fontScale 2", Rect.intersects(bounds.get(i), bounds.get(j)));
                }
            }
            assertTrue("The constrained-height dialog uses native scrolling for its footer",
                    root.getHeight() > scroll.getHeight());
            scroll.scrollTo(0, root.getHeight() - scroll.getHeight());
            assertTrue("Lower controls remain reachable instead of being clipped", scroll.getScrollY() > 0);
        } finally {
            activity.finish();
        }
    }

    @Test
    public void googlePlacePath_preservesHumanReadableNameWithExactCoordinates() {
        MapLinkParser.Parsed parsed = MapLinkParser.parse(
                "https://www.google.com/maps/place/Riyadh%20Tower/@24.7136,46.6753,15z");

        assertNotNull(parsed);
        assertTrue(parsed.hasCoordinates());
        assertEquals("Riyadh Tower", parsed.getLabel());
        assertEquals(24.7136d, parsed.getLatitude(), 0.000001d);
        assertEquals(46.6753d, parsed.getLongitude(), 0.000001d);
    }

    @Test
    public void mapAsset_prefersModuleClassLoaderThenUsesBundledAssetFallback() throws Exception {
        ClassLoader moduleLoader = new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                return "assets/wolfox_map.html".equals(name)
                        ? new ByteArrayInputStream("module-map".getBytes(StandardCharsets.UTF_8)) : null;
            }
        };

        try (InputStream module = MapDialog.openMapAsset(moduleLoader, null)) {
            assertEquals("module-map", readUtf8(module));
        }
        try (InputStream fallback = MapDialog.openMapAsset(new ClassLoader(null) {},
                RuntimeEnvironment.getApplication().getAssets())) {
            assertTrue("The normal app asset remains an explicit fallback",
                    readUtf8(fallback).contains("<!doctype html>"));
        }
    }

    private Activity newActivity(int widthDp, int heightDp, float fontScale, int orientation) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        Resources resources = activity.getResources();
        DisplayMetrics metrics = resources.getDisplayMetrics();
        metrics.density = 1f;
        metrics.scaledDensity = fontScale;
        metrics.densityDpi = DisplayMetrics.DENSITY_DEFAULT;
        metrics.widthPixels = widthDp;
        metrics.heightPixels = heightDp;
        Configuration configuration = new Configuration(resources.getConfiguration());
        configuration.fontScale = fontScale;
        configuration.orientation = orientation;
        resources.updateConfiguration(configuration, metrics);
        return activity;
    }

    private void drainMainFor(long durationMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + durationMs;
        while (System.currentTimeMillis() < deadline) {
            ShadowLooper.idleMainLooper();
            Thread.sleep(10L);
        }
        ShadowLooper.idleMainLooper();
    }

    private int exactly(int size) {
        return View.MeasureSpec.makeMeasureSpec(size, View.MeasureSpec.EXACTLY);
    }

    private String readUtf8(InputStream stream) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[256];
        int read;
        while ((read = stream.read(buffer)) >= 0) output.write(buffer, 0, read);
        return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }

    private Rect boundsIn(View ancestor, View view) {
        int left = 0;
        int top = 0;
        View current = view;
        while (current != ancestor) {
            left += current.getLeft();
            top += current.getTop();
            ViewParent parent = current.getParent();
            assertTrue("Control must remain inside the dialog root", parent instanceof View);
            current = (View) parent;
        }
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }
}
