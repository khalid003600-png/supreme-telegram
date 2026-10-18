package com.wolfox.gps.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.wolfox.gps.DirectBootstrap;
import com.wolfox.gps.ui.ActivationDialog;
import com.wolfox.gps.ui.WolFoxPanel;
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
import org.robolectric.shadows.ShadowAlertDialog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Local stress coverage for the in-Activity control only.
 *
 * <p>No test calls {@link DirectBootstrap#attach(Activity)} because attach starts production
 * monitoring; these tests exercise the local show/hide and touch bridges without credentials,
 * services, a device, or a network endpoint.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiAuditlifecycleStressTest {
    private static final String PREFS = "wolfox_gps_prefs";
    private static final String CONTROL_TAG = "WFX_CONTROL_311";
    private static final String KEY_BUBBLE_VISIBLE = "bubble_visible";

    private Context context;
    private FloatingManager manager;
    private Activity activity;
    private ViewGroup content;

    @Before
    public void resetMainLooperAndSingletonState() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences("wolfox_remote_control", Context.MODE_PRIVATE)
                .edit().clear().commit();
        resetStorageSingleton();
        cancelDirectBootstrapCallbacks();

        manager = FloatingManager.getInstance();
        Activity previous = manager.getCurrentActivity();
        if (previous != null) manager.onActivityDestroyed(previous);
        manager.hide(false);
        drainMainLooper();
        WolFoxPanel.dismiss();
        ActivationDialog.dismiss();
        ShadowAlertDialog.reset();

        activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setContentView(new FrameLayout(activity));
        content = activity.findViewById(android.R.id.content);
        layout(content);
        layout(activity.getWindow().getDecorView());
    }

    @After
    public void clearMainLooperAndSingletonState() throws Exception {
        cancelTouchReveal();
        if (manager != null) {
            manager.hide(false);
            if (activity != null) manager.onActivityPaused(activity);
        }
        drainMainLooper();
        WolFoxPanel.dismiss();
        ActivationDialog.dismiss();
        ShadowAlertDialog.reset();
        if (activity != null && !activity.isFinishing()) activity.finish();
        resetStorageSingleton();
    }

    @Test
    public void firstResumeWithFreshPreferencesAttachesOneIconWithoutOpeningActivation() throws Exception {
        assertTrue("Fresh-install default is visible", WFStorage.getInstance(context).isBubbleVisible());

        manager.onActivityResumed(activity);
        drainMainLooper();

        assertTrue(manager.isVisible());
        assertEquals(1, countControls(content));
        assertNull("Resume attaches only the icon; it must not open activation", currentActivation());
    }

    @Test
    public void storedFalseSurvivesNewStorageInstanceAndFirstResumeRemainsHidden() throws Exception {
        storeBubbleVisible(false);
        resetStorageSingleton();

        assertFalse("An explicit hide is persistent rather than process-token bound",
                WFStorage.getInstance(context).isBubbleVisible());
        manager.onActivityResumed(activity);
        drainMainLooper();

        assertFalse(manager.isVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void hideBefore850MsRevealCancelsPendingRevealAndLeavesStoredHide() {
        storeBubbleVisible(false);

        dispatchTouch(MotionEvent.ACTION_DOWN, 540f, 960f);
        DirectBootstrap.hideControls(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(900L, TimeUnit.MILLISECONDS);
        drainMainLooper();

        assertFalse(WFStorage.getInstance(context).isBubbleVisible());
        assertFalse(manager.isVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void repeatedShowHideTransitionsNeverDuplicateOrResurrectTheControl() {
        storeBubbleVisible(false);

        for (int transition = 0; transition < 12; transition++) {
            DirectBootstrap.showControls(activity);
            drainMainLooper();
            assertTrue("show transition " + transition, manager.isVisible());
            assertEquals(1, countControls(content));

            DirectBootstrap.hideControls(activity);
            drainMainLooper();
            assertFalse("hide transition " + transition, manager.isVisible());
            assertEquals(0, countControls(content));
        }

        Shadows.shadowOf(Looper.getMainLooper()).idleFor(1000L, TimeUnit.MILLISECONDS);
        drainMainLooper();
        assertFalse(WFStorage.getInstance(context).isBubbleVisible());
        assertFalse(manager.isVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void pauseReleasesActivityReferenceAndResumeRestoresOnlyVisibleRequest() {
        manager.onActivityResumed(activity);
        drainMainLooper();
        assertTrue(manager.isVisible());

        manager.onActivityPaused(activity);
        drainMainLooper();

        assertNull("Singleton must not retain a paused Activity", manager.getCurrentActivity());
        assertFalse(manager.isVisible());
        assertEquals(0, countControls(content));

        manager.onActivityResumed(activity);
        drainMainLooper();
        assertTrue(manager.isVisible());
        assertEquals(1, countControls(content));
    }

    private void storeBubbleVisible(boolean visible) {
        preferences().edit().putBoolean(KEY_BUBBLE_VISIBLE, visible).commit();
    }

    private SharedPreferences preferences() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void dispatchTouch(int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try {
            DirectBootstrap.observeTouch(activity, event);
        } finally {
            event.recycle();
        }
    }

    private void cancelTouchReveal() {
        if (activity != null && !activity.isFinishing()) {
            dispatchTouch(MotionEvent.ACTION_CANCEL, 0f, 0f);
        }
    }

    private static void cancelDirectBootstrapCallbacks() throws Exception {
        invokePrivateStatic("cancelReveal");
        invokePrivateStatic("cancelRenders");
    }

    private static void invokePrivateStatic(String methodName) throws Exception {
        Method method = DirectBootstrap.class.getDeclaredMethod(methodName);
        method.setAccessible(true);
        method.invoke(null);
    }

    private static void resetStorageSingleton() throws Exception {
        Field instance = WFStorage.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    private static android.app.Dialog currentActivation() throws Exception {
        Field current = ActivationDialog.class.getDeclaredField("current");
        current.setAccessible(true);
        return (android.app.Dialog) current.get(null);
    }

    private static int countControls(View view) {
        int count = CONTROL_TAG.equals(view.getTag()) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int child = 0; child < group.getChildCount(); child++) {
                count += countControls(group.getChildAt(child));
            }
        }
        return count;
    }

    private static void layout(View view) {
        int width = 1080;
        int height = 1920;
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
    }

    private static void drainMainLooper() {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }
}
