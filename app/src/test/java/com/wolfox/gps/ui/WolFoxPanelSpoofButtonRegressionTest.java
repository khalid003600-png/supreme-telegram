package com.wolfox.gps.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.WFLocation;
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
 * UI regression limited to the panel's local, no-location failure response.  It uses an
 * explicitly non-production SharedPreferences fixture solely to make the gated panel visible;
 * it never invokes activation/verification APIs and never asserts a successful spoof start.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class WolFoxPanelSpoofButtonRegressionTest {
    private static final String PREFS = "wolfox_gps_prefs";
    private static final String LOCAL_FIXTURE_CODE = "LOCAL-TEST-NOT-A-CREDENTIAL";

    private Context context;
    private WFStorage storage;
    private GPSMockManager manager;
    private Activity activity;

    @Before
    public void resetLocalState() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        Field storageInstance = WFStorage.class.getDeclaredField("instance");
        storageInstance.setAccessible(true);
        storageInstance.set(null, null);
        storage = WFStorage.getInstance(context);
        manager = GPSMockManager.getInstance();
        setManagerField("appContext", context);
        setManagerField("currentLocation", null);
        setManagerField("mocking", false);
        WolFoxPanel.dismiss();
    }

    @After
    public void dismissPanel() {
        WolFoxPanel.dismiss();
        if (activity != null) activity.finish();
    }

    @Test
    public void spoofButton_hasRequiredIdentityAndNoLocationClickRemainsFailClosed() throws Exception {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active",
                System.currentTimeMillis() + 86_400_000L, "future local fixture");
        activity = Robolectric.buildActivity(Activity.class).setup().get();

        WolFoxPanel.show(activity);

        Dialog dialog = currentDialog();
        assertNotNull("An active local fixture exposes the already-gated panel", dialog);
        Button toggle = dialog.getWindow().getDecorView().findViewWithTag("wolfox.spoof.toggle");
        assertNotNull("The explicit spoof control must be discoverable by its stable tag", toggle);
        assertEquals("تزييف الموقع", toggle.getText().toString());
        assertEquals("تزييف الموقع", toggle.getContentDescription().toString());
        assertTrue(toggle.performClick());
        assertTrue("LOCATION_REQUIRED must re-enable the control for a valid later selection",
                toggle.isEnabled());
        assertTrue("No-location failure keeps the panel open instead of implying a start", dialog.isShowing());
        assertFalse("The click must not enable spoofing without a selected location", manager.isMocking());
        assertFalse(storage.isMocking());
        TextView feedback = dialog.getWindow().getDecorView().findViewWithTag("wolfox.spoof.feedback");
        assertEquals(View.VISIBLE, feedback.getVisibility());
        assertTrue(feedback.getText().toString().contains("حدد موقعًا"));
        assertEquals("تزييف الموقع", toggle.getText().toString());
    }

    @Test public void runtimeUnavailable_hasPersistentExplanationAndKeepsControlsReady() throws Exception {
        WFLocation selected = new WFLocation(24.7135521d, 46.6752963d);
        selected.setLabel("موقع البحث المختار");
        storage.saveLastLocation(selected);
        setManagerField("currentLocation", selected);
        Dialog dialog = showLocalPanel();
        Button toggle = dialog.getWindow().getDecorView().findViewWithTag("wolfox.spoof.toggle");
        toggle.performClick();
        TextView feedback = dialog.getWindow().getDecorView().findViewWithTag("wolfox.spoof.feedback");
        assertEquals(View.VISIBLE, feedback.getVisibility());
        assertTrue(feedback.getText().toString().contains("تعذر التزييف"));
        assertTrue(feedback.getText().toString().contains("تثبيت APK وحده لا يكفي"));
        assertTrue(toggle.isEnabled());
        assertEquals("تزييف الموقع", toggle.getText().toString());
        assertFalse(manager.isMocking());
        assertSameDialog(dialog);
    }

    @Test public void selectedLocationLabelAndCoordinates_areDisplayedFromCurrentState() throws Exception {
        WFLocation selected = new WFLocation(21.3890824d, 39.8579118d);
        selected.setLabel("المفضلة: الموقع الجديد");
        storage.saveLastLocation(selected);
        setManagerField("currentLocation", selected);
        Dialog dialog = showLocalPanel();
        TextView name = dialog.getWindow().getDecorView().findViewWithTag("wolfox.selected.name");
        TextView coordinates = dialog.getWindow().getDecorView().findViewWithTag("wolfox.selected.coordinates");
        assertEquals("المفضلة: الموقع الجديد", name.getText().toString());
        assertEquals("21.3890824, 39.8579118", coordinates.getText().toString());
        assertEquals(View.LAYOUT_DIRECTION_LTR, coordinates.getLayoutDirection());
    }

    @Test public void stoppingLocalRunningFixture_updatesSamePanelAndDebouncesSecondClick() throws Exception {
        // This is only a local stopping-state fixture; no runtime start is simulated or asserted.
        WFLocation selected = new WFLocation(24.7d, 46.6d);
        storage.saveLastLocation(selected);
        setManagerField("currentLocation", selected);
        storage.setMocking(true);
        setManagerField("mocking", true);
        Dialog dialog = showLocalPanel();
        Button toggle = dialog.getWindow().getDecorView().findViewWithTag("wolfox.spoof.toggle");
        assertEquals("إيقاف التزييف", toggle.getText().toString());
        toggle.performClick();
        TextView feedback = dialog.getWindow().getDecorView().findViewWithTag("wolfox.spoof.feedback");
        assertEquals("تم إيقاف تزييف الموقع", feedback.getText().toString());
        assertEquals("تزييف الموقع", toggle.getText().toString());
        assertFalse(storage.isMocking());
        toggle.performClick();
        assertEquals("A rapid second click must not attempt a new session", "تم إيقاف تزييف الموقع", feedback.getText().toString());
        assertSameDialog(dialog);
    }

    private Dialog showLocalPanel() throws Exception {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active", System.currentTimeMillis() + 86_400_000L, "future local fixture");
        activity = Robolectric.buildActivity(Activity.class).setup().get();
        WolFoxPanel.show(activity);
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        Dialog dialog = currentDialog();
        assertNotNull(dialog);
        return dialog;
    }

    private void assertSameDialog(Dialog expected) throws Exception {
        assertTrue(expected.isShowing());
        assertTrue("Feedback must not dismiss/recreate the user's map", currentDialog() == expected);
    }

    private Dialog currentDialog() throws Exception {
        Field field = WolFoxPanel.class.getDeclaredField("currentDialog");
        field.setAccessible(true);
        return (Dialog) field.get(null);
    }

    private void setManagerField(String name, Object value) throws Exception {
        Field field = GPSMockManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
