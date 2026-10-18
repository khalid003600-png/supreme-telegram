package com.wolfox.gps.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

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

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Local UI-lifecycle coverage for the floating control's explicit visibility request.
 *
 * <p>The Active state below is a non-production SharedPreferences fixture. These tests do not
 * call {@link DirectBootstrap#attach(Activity)}, ActivityHook, activation/verification methods,
 * policy refreshes, companion-patch code, or any network/API path. They exercise local public
 * bridges and the same local license gate used by the button instead of bypassing it.</p>
 *
 * <p>ActivityHook, inactive enforcement, and activation success all end at
 * {@link FloatingManager#restoreRequestedVisibility(Activity)}. The direct local tests below
 * therefore assert that this restoration contract never creates a show request by itself; they do
 * not claim coverage of a device or an Xposed runtime.</p>
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class FloatingManagerLifecycleTest {
    private static final String PREFS = "wolfox_gps_prefs";
    private static final String CONTROL_TAG = "WFX_CONTROL_311";
    private static final String KEY_BUBBLE_VISIBLE = "bubble_visible";
    private static final String LOCAL_FIXTURE_CODE = "LOCAL-TEST-NOT-A-CREDENTIAL";

    private Context context;
    private WFStorage storage;
    private FloatingManager manager;
    private Activity activity;
    private ViewGroup content;

    @Before
    public void resetLocalUiState() throws Exception {
        context = RuntimeEnvironment.getApplication();
        requestPreferences().edit().clear().commit();
        context.getSharedPreferences("wolfox_remote_control", Context.MODE_PRIVATE)
                .edit().clear().commit();
        // Robolectric swaps Applications while reusing this sandbox's production singleton.
        // Reset that test-only reference so reads use this test's cleared preferences.
        Field storageInstance = WFStorage.class.getDeclaredField("instance");
        storageInstance.setAccessible(true);
        storageInstance.set(null, null);
        storage = WFStorage.getInstance(context);
        manager = FloatingManager.getInstance();

        // Clear only singleton UI residue left by another local unit test; no production state is
        // changed and no licensing method is stubbed or overridden.
        WolFoxPanel.dismiss();
        ActivationDialog.dismiss();
        ShadowAlertDialog.reset();
        manager.hide(false);
        drainMainLooper();

        activity = Robolectric.buildActivity(Activity.class).setup().get();
        FrameLayout appContent = new FrameLayout(activity);
        activity.setContentView(appContent);
        content = activity.findViewById(android.R.id.content);
        layoutForTouch(content);
        layoutForTouch(activity.getWindow().getDecorView());
    }

    @After
    public void cleanUpLocalUi() {
        cancelDirectReveal();
        WolFoxPanel.dismiss();
        ActivationDialog.dismiss();
        ShadowAlertDialog.reset();
        if (manager != null) {
            manager.hide(false);
            drainMainLooper();
        }
        if (activity != null && !activity.isFinishing()) activity.finish();
    }

    @Test
    public void storageDefaultsVisible_andExplicitRequestPersistsAcrossProcessStarts() {
        assertTrue("A fresh installation shows the in-activity control by default",
                storage.isBubbleVisible());

        storage.setBubbleVisible(true);

        SharedPreferences prefs = requestPreferences();
        assertTrue(storage.isBubbleVisible());
        assertTrue(prefs.getBoolean(KEY_BUBBLE_VISIBLE, false));
    }

    @Test
    public void storedHiddenStartupResume_doesNotAttachControl() {
        storeBubbleVisible(false);
        assertFalse(storage.isBubbleVisible());

        manager.onActivityResumed(activity);
        drainMainLooper();

        assertFalse("onResume must restore a request, not create one", manager.isVisible());
        assertFalse(storage.isBubbleVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void explicitShowThenOnActivityResumed_restoresSingleRequestedControl() throws Exception {
        assertPackedIconIsDecodable();
        View standaloneButton = buildFloatingButton(context);
        assertEquals(CONTROL_TAG, standaloneButton.getTag());
        assertTrue("The packed icon, rather than a text fallback, must be used when present",
                ((ViewGroup) standaloneButton).getChildAt(0) instanceof ImageView);
        assertNotNull(((ImageView) ((ViewGroup) standaloneButton).getChildAt(0)).getDrawable());

        manager.show(activity);
        drainMainLooper();
        assertTrue(manager.isVisible());
        assertTrue(storage.isBubbleVisible());
        assertEquals(1, countControls(content));

        // Restore uses the explicit current-process request and must not stack a second child.
        manager.onActivityResumed(activity);
        drainMainLooper();
        assertTrue(manager.isVisible());
        assertEquals("Only one WFX_CONTROL_311 view may exist after resume", 1,
                countControls(content));
    }

    @Test
    public void explicitHideThenResume_staysHiddenAndKeepsHiddenRequest() {
        manager.show(activity);
        drainMainLooper();
        manager.hide(true);
        drainMainLooper();

        assertFalse(manager.isVisible());
        assertFalse("Explicit hide persists the hidden request", storage.isBubbleVisible());
        assertEquals(0, countControls(content));

        manager.onActivityResumed(activity);
        drainMainLooper();

        assertFalse("A hidden explicit request must not be resurrected on resume", manager.isVisible());
        assertFalse(storage.isBubbleVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void storedFalsePreference_doesNotResurrectControl() {
        requestPreferences().edit()
                .putBoolean(KEY_BUBBLE_VISIBLE, false)
                .commit();
        assertFalse("An explicit stored hide survives a process transition", storage.isBubbleVisible());

        manager.onActivityResumed(activity);
        drainMainLooper();

        assertFalse(manager.isVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void onActivityPaused_detachesButRequestedVisibilityRestoresOnResume() {
        manager.show(activity);
        drainMainLooper();
        assertTrue(manager.isVisible());
        assertTrue(storage.isBubbleVisible());

        manager.onActivityPaused(activity);
        drainMainLooper();

        assertFalse("Pause detaches the decor overlay", manager.isVisible());
        assertTrue("Pause must retain the explicit current-process request", storage.isBubbleVisible());
        assertEquals(0, countControls(content));

        manager.onActivityResumed(activity);
        drainMainLooper();

        assertTrue("Resume restores only the request retained across pause", manager.isVisible());
        assertEquals(1, countControls(content));

        manager.onActivityDestroyed(activity);
        drainMainLooper();
        assertFalse(manager.isVisible());
        assertNull(manager.getCurrentActivity());
        assertEquals(0, countControls(content));
    }

    @Test
    public void centerHoldFor850Ms_revealsOnlyThroughShowControlsBridge() {
        storeBubbleVisible(false);
        assertFalse(storage.isBubbleVisible());

        observeTouch(MotionEvent.ACTION_DOWN, 540f, 960f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(850L, TimeUnit.MILLISECONDS);
        drainMainLooper();

        assertTrue("The completed center hold issues the explicit show request", storage.isBubbleVisible());
        assertTrue(manager.isVisible());
        assertEquals(1, countControls(content));
    }

    @Test
    public void shortCenterHoldCancelledBefore850Ms_doesNotShow() {
        storeBubbleVisible(false);
        observeTouch(MotionEvent.ACTION_DOWN, 540f, 960f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(849L, TimeUnit.MILLISECONDS);
        observeTouch(MotionEvent.ACTION_UP, 540f, 960f);
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(20L, TimeUnit.MILLISECONDS);
        drainMainLooper();

        assertFalse("Cancelling the hold must leave the control hidden", storage.isBubbleVisible());
        assertFalse(manager.isVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void explicitShowHideControlsBridge_persistsBothRequests() {
        storeBubbleVisible(false);
        assertFalse(storage.isBubbleVisible());

        DirectBootstrap.showControls(activity);
        drainMainLooper();

        assertTrue(storage.isBubbleVisible());
        assertTrue(manager.isVisible());
        assertEquals(1, countControls(content));

        DirectBootstrap.hideControls(activity);
        drainMainLooper();

        assertFalse("The public hide bridge must persist a hidden request", storage.isBubbleVisible());
        assertFalse(manager.isVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void staleDelayedHide_isCancelledByNewExplicitShow() {
        manager.show(activity);
        drainMainLooper();
        assertTrue(manager.isVisible());

        // hide() posts a token-bound detach. A newer explicit show must invalidate that stale post.
        manager.hide(true);
        manager.show(activity);
        drainMainLooper();

        assertTrue("A stale hide runnable must not remove the newer requested control",
                manager.isVisible());
        assertTrue(storage.isBubbleVisible());
        assertEquals(1, countControls(content));
    }

    @Test
    public void hideConfirmationPositive_persistsHiddenRequest() {
        manager.show(activity);
        drainMainLooper();

        manager.requestHideConfirmation(activity);
        drainMainLooper();
        AlertDialog confirmation = ShadowAlertDialog.getLatestAlertDialog();
        assertNotNull("Long-press confirmation should be shown locally", confirmation);
        assertTrue(confirmation.isShowing());
        assertNotNull(confirmation.getButton(AlertDialog.BUTTON_POSITIVE));

        confirmation.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
        drainMainLooper();

        assertFalse(storage.isBubbleVisible());
        assertFalse(manager.isVisible());
        assertEquals(0, countControls(content));
    }

    @Test
    public void bubbleTap_routesActiveFixtureToPanel_andInactiveFixtureToActivation() throws Exception {
        saveActiveLocalFixture();
        manager.show(activity);
        drainMainLooper();

        tap(onlyControl());
        drainMainLooper();
        Dialog panel = currentDialog(WolFoxPanel.class, "currentDialog");
        assertNotNull("An Active local record passes the normal panel gate", panel);
        assertTrue(panel.isShowing());
        assertNull("The Active branch must not leave an activation dialog visible",
                currentDialog(ActivationDialog.class, "current"));
        WolFoxPanel.dismiss();
        drainMainLooper();
        assertFalse("dismiss() must close the active WolFoxPanel surface", panel.isShowing());
        assertNull(currentDialog(WolFoxPanel.class, "currentDialog"));

        // This changes only the test's local persisted fixture. The button's production
        // openPanel() still calls LicenseManager.isActive(activity) for this branch.
        storage.clearLicense();
        tap(onlyControl());
        drainMainLooper();
        Dialog activation = currentDialog(ActivationDialog.class, "current");
        assertNotNull("An inactive local record is routed to ActivationDialog", activation);
        assertTrue(activation.isShowing());
        assertNull("The inactive branch must not construct WolFoxPanel", currentDialog(
                WolFoxPanel.class, "currentDialog"));

        ActivationDialog.dismiss();
        assertFalse("dismiss() must close the activation surface", activation.isShowing());
    }

    private SharedPreferences requestPreferences() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void storeBubbleVisible(boolean visible) {
        requestPreferences().edit().putBoolean(KEY_BUBBLE_VISIBLE, visible).commit();
    }

    private void cancelDirectReveal() {
        if (activity != null && !activity.isFinishing()) {
            observeTouch(MotionEvent.ACTION_CANCEL, 0f, 0f);
        }
    }

    private void observeTouch(int action, float x, float y) {
        long now = android.os.SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(now, now, action, x, y, 0);
        try {
            DirectBootstrap.observeTouch(activity, event);
        } finally {
            event.recycle();
        }
    }

    private void assertPackedIconIsDecodable() throws Exception {
        try (InputStream stream = context.getAssets().open("wolfox_icon.png")) {
            Bitmap decoded = BitmapFactory.decodeStream(stream);
            assertNotNull("app/src/main/assets/wolfox_icon.png must be packed for the button", decoded);
            assertEquals(512, decoded.getWidth());
            assertEquals(512, decoded.getHeight());
            decoded.recycle();
        }
    }

    private View buildFloatingButton(Context owner) throws Exception {
        Method method = FloatingManager.class.getDeclaredMethod("buildFloatingButton", Context.class);
        method.setAccessible(true);
        return (View) method.invoke(manager, owner);
    }

    private void saveActiveLocalFixture() {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active",
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1L),
                "future local fixture");
    }

    private View onlyControl() {
        View control = content.findViewWithTag(CONTROL_TAG);
        assertNotNull("show() must attach the tagged control to the Activity content view", control);
        return control;
    }

    private static Dialog currentDialog(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Dialog) field.get(null);
    }

    private static int countControls(View view) {
        int found = CONTROL_TAG.equals(view.getTag()) ? 1 : 0;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) found += countControls(group.getChildAt(i));
        }
        return found;
    }

    private static void tap(View view) {
        long downAt = android.os.SystemClock.uptimeMillis();
        assertTrue(view.dispatchTouchEvent(MotionEvent.obtain(downAt, downAt,
                MotionEvent.ACTION_DOWN, 20f, 20f, 0)));
        assertTrue(view.dispatchTouchEvent(MotionEvent.obtain(downAt, downAt + 20L,
                MotionEvent.ACTION_UP, 20f, 20f, 0)));
    }

    private static void layoutForTouch(View view) {
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
