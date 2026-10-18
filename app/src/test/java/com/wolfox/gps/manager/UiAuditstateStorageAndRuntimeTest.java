package com.wolfox.gps.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFStorage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/**
 * Local state-only regression coverage for the UI audit. No test sends an activation/policy
 * request, installs a hook, queries a real provider, or uses a production credential.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiAuditstateStorageAndRuntimeTest {
    private static final String PREFS = "wolfox_gps_prefs";
    private static final String LOCAL_FIXTURE_CODE = "LOCAL-TEST-NOT-A-CREDENTIAL";

    private Context context;
    private SharedPreferences preferences;
    private WFStorage storage;
    private GPSMockManager manager;

    @Before
    public void resetSingletonsAndLocalPreferences() throws Exception {
        context = RuntimeEnvironment.getApplication();
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        preferences.edit().clear().apply();
        resetStorageSingleton();
        storage = WFStorage.getInstance(context);
        manager = GPSMockManager.getInstance();
        setManagerField("appContext", context);
        setManagerField("currentLocation", null);
        setManagerField("mocking", false);
        setManagerField("legacyMockMarkerWarningLogged", false);
    }

    @After
    public void clearOnlyThisLocalFixture() throws Exception {
        if (preferences != null) preferences.edit().clear().apply();
        if (manager != null) {
            setManagerField("appContext", null);
            setManagerField("currentLocation", null);
            setManagerField("mocking", false);
        }
        resetStorageSingleton();
    }

    @Test
    public void firstAuthorizedHostOpen_defaultsVisibleWithoutCreatingAnImplicitRecord() {
        assertFalse("The fixture starts without a user visibility decision",
                preferences.contains("bubble_visible"));

        assertTrue("First host open must reveal the in-Activity control", storage.isBubbleVisible());
        assertFalse("Reading the default must not manufacture a persistent show decision",
                preferences.contains("bubble_visible"));
    }

    @Test
    public void explicitHide_survivesStorageSingletonResetAndLegacyProcessMetadata() throws Exception {
        storage.setBubbleVisible(false);
        preferences.edit()
                .putInt("bubble_requested_process_pid", Integer.MIN_VALUE)
                .putLong("bubble_requested_process_start", Long.MIN_VALUE)
                .apply();

        resetStorageSingleton();
        storage = WFStorage.getInstance(context);

        assertFalse("A legacy explicit hidden decision must never be resurrected", storage.isBubbleVisible());
    }

    @Test
    public void explicitShow_ignoresRetiredProcessIdentityAndRemainsVisibleAfterReset() throws Exception {
        storage.setBubbleVisible(false);
        storage.setBubbleVisible(true);
        preferences.edit()
                .putInt("bubble_requested_process_pid", Integer.MAX_VALUE)
                .putLong("bubble_requested_process_start", Long.MAX_VALUE)
                .apply();

        resetStorageSingleton();
        storage = WFStorage.getInstance(context);

        assertTrue("An explicit show request is intentionally process-independent", storage.isBubbleVisible());
    }

    @Test
    public void coordinateBoundariesPersistImmediately_butInvalidJitterSamplesCannotReplaceSelection() {
        WFLocation southWest = new WFLocation(-90d, -180d, "boundary");
        WFLocation northEast = new WFLocation(90d, 180d, "boundary");

        storage.saveLastLocation(southWest);
        assertLocation(-90d, -180d, storage.getLastLocation());
        storage.saveLastLocation(northEast);
        assertLocation(90d, 180d, storage.getLastLocation());

        storage.saveLastLocation(new WFLocation(90.000001d, 180d));
        storage.saveLastLocation(new WFLocation(Double.NaN, 46.6753d));
        storage.saveLastLocation(new WFLocation(24.7136d, Double.POSITIVE_INFINITY));

        assertLocation(90d, 180d, storage.getLastLocation());
    }

    @Test
    public void expiredLicenseStartRequest_clearsPersistedMockFlagWithoutWaitingForAnotherUiRead()
            throws Exception {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active", System.currentTimeMillis() - 1L,
                "expired local fixture");
        storage.setMocking(true);
        setManagerField("currentLocation", new WFLocation(24.7136d, 46.6753d));

        GPSMockManager.StartResult result = manager.requestVerifiedStart();

        assertEquals(GPSMockManager.StartResult.LICENSE_REQUIRED, result);
        assertFalse("The expired start guard must clear persistent spoofing immediately",
                storage.isMocking());
        assertFalse(manager.isMocking());
    }

    @Test
    public void missingCurrentProcessRuntimeMarker_failsClosedAndDoesNotLeaveMockingDoubled() throws Exception {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active",
                System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1L), "future local fixture");
        setManagerField("currentLocation", new WFLocation(24.7136d, 46.6753d));

        GPSMockManager.StartResult result = manager.requestVerifiedStart();

        assertEquals(GPSMockManager.StartResult.RUNTIME_UNAVAILABLE, result);
        assertFalse("A failed runtime verification must not leave a persisted mock session",
                storage.isMocking());
        assertFalse("A failed runtime verification must not leave another in-memory mock state",
                manager.isMocking());
    }

    private void assertLocation(double latitude, double longitude, WFLocation value) {
        assertTrue("A valid saved location is expected", value != null);
        assertEquals(latitude, value.getLatitude(), 0d);
        assertEquals(longitude, value.getLongitude(), 0d);
    }

    private static void resetStorageSingleton() throws Exception {
        Field field = WFStorage.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    private void setManagerField(String name, Object value) throws Exception {
        Field field = GPSMockManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
