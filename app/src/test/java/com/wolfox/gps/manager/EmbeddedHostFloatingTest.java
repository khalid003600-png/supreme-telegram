package com.wolfox.gps.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.wolfox.gps.DirectBootstrap;
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

import java.lang.reflect.Field;

/**
 * Local host-embedding coverage only. These tests invoke neither attach(), activation/verification,
 * policy refresh, nor any GPS/network path; showControls() and hideControls() are explicit local
 * Activity bridges.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class EmbeddedHostFloatingTest {
    private static final String CONTROL_TAG = "WFX_CONTROL_311";
    private static final String STORAGE_PREFS = "wolfox_gps_prefs";
    private static final String CONTROL_PREFS = "wolfox_remote_control_variant5";
    private static final String KEY_BUBBLE_VISIBLE = "bubble_visible";

    private Context appContext;
    private WFStorage storage;
    private FloatingManager floating;
    private AlphaHostActivity alpha;
    private BetaHostActivity beta;
    private ViewGroup alphaContent;
    private ViewGroup betaContent;

    @Before
    public void resetEmbeddedHostState() throws Exception {
        appContext = RuntimeEnvironment.getApplication();
        floating = FloatingManager.getInstance();
        floating.hide(false);
        drainMainLooper();

        appContext.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        appContext.getSharedPreferences(CONTROL_PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        resetStorageSingleton();
        storage = WFStorage.getInstance(appContext);

        alpha = Robolectric.buildActivity(AlphaHostActivity.class).setup().get();
        alphaContent = setHostContent(alpha);
        beta = Robolectric.buildActivity(BetaHostActivity.class).setup().get();
        betaContent = setHostContent(beta);
    }

    @After
    public void clearEmbeddedHostState() throws Exception {
        if (floating != null) {
            floating.hide(false);
            drainMainLooper();
        }
        if (alpha != null && !alpha.isFinishing()) alpha.finish();
        if (beta != null && !beta.isFinishing()) beta.finish();
        if (appContext != null) {
            appContext.getSharedPreferences(CONTROL_PREFS, Context.MODE_PRIVATE)
                    .edit().clear().commit();
        }
        resetStorageSingleton();
    }

    @Test
    public void freshInstallDefaultAttachesOneControlWhenAnEmbeddedHostResumes() {
        assertTrue(storage.isBubbleVisible());

        floating.onActivityResumed(alpha);
        drainMainLooper();

        assertTrue(floating.isVisible());
        assertTrue(storage.isBubbleVisible());
        assertEquals(1, countControls(alphaContent));
    }

    @Test
    public void explicitShowTransfersOneInActivityControlBetweenDifferentHostPackages_thenHideStaysHidden() {
        assertNotEquals("com.wolfox.gps", alpha.getPackageName());
        assertNotEquals(alpha.getPackageName(), beta.getPackageName());

        DirectBootstrap.showControls(alpha);
        drainMainLooper();
        View alphaControl = onlyControl(alphaContent);
        assertSame(alphaContent, alphaControl.getParent());
        assertTrue(alphaControl.getLayoutParams() instanceof FrameLayout.LayoutParams);
        assertEquals(1, countControls(alphaContent));

        DirectBootstrap.showControls(beta);
        drainMainLooper();
        View betaControl = onlyControl(betaContent);
        assertSame(betaContent, betaControl.getParent());
        assertTrue(betaControl.getLayoutParams() instanceof FrameLayout.LayoutParams);
        assertEquals(0, countControls(alphaContent));
        assertEquals(1, countControls(betaContent));

        DirectBootstrap.hideControls(beta);
        drainMainLooper();
        floating.onActivityResumed(alpha);
        drainMainLooper();
        floating.onActivityResumed(beta);
        drainMainLooper();

        assertFalse(storage.isBubbleVisible());
        assertFalse(floating.isVisible());
        assertEquals(0, countControls(alphaContent));
        assertEquals(0, countControls(betaContent));
    }

    @Test
    public void cachedBlockedPolicyPreventsExplicitShowWithoutRefreshingNetworkPolicy() {
        appContext.getSharedPreferences(STORAGE_PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_BUBBLE_VISIBLE, false).commit();
        assertFalse(ToolControlManager.getInstance().isBlocked(alpha));
        // Policy is scoped to the host application's preferences, not the module app.
        alpha.getSharedPreferences(CONTROL_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean("blocked", true).commit();
        assertTrue(ToolControlManager.getInstance().isBlocked(alpha));

        DirectBootstrap.showControls(alpha);
        drainMainLooper();

        assertFalse(storage.isBubbleVisible());
        assertFalse(floating.isVisible());
        assertEquals(0, countControls(alphaContent));
    }

    private static ViewGroup setHostContent(Activity activity) {
        activity.setContentView(new FrameLayout(activity));
        return activity.findViewById(android.R.id.content);
    }

    private static View onlyControl(ViewGroup content) {
        View control = content.findViewWithTag(CONTROL_TAG);
        assertTrue("Exactly one in-Activity control is expected", control != null);
        assertEquals(1, countControls(content));
        return control;
    }

    private static int countControls(View view) {
        int found = CONTROL_TAG.equals(view.getTag()) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                found += countControls(group.getChildAt(index));
            }
        }
        return found;
    }

    private static void resetStorageSingleton() throws Exception {
        Field instance = WFStorage.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private static void drainMainLooper() {
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    }

    public static class AlphaHostActivity extends Activity {
        @Override public String getPackageName() {
            return "com.example.alpha";
        }
    }

    public static class BetaHostActivity extends Activity {
        @Override public String getPackageName() {
            return "dev.company.beta";
        }
    }
}
