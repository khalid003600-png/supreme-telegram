package com.wolfox.gps.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.location.Location;

import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFStorage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;

/**
 * Local regression coverage only.  It does not install or emulate Xposed/LSPatch hooks,
 * issue a license request, or use a production credential.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class GPSMockManagerRegressionTest {
    private static final String PREFS = "wolfox_gps_prefs";
    private static final String LOCAL_FIXTURE_CODE = "LOCAL-TEST-NOT-A-CREDENTIAL";

    private Context context;
    private WFStorage storage;
    private GPSMockManager manager;

    @Before
    public void resetLocalState() throws Exception {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        storage = WFStorage.getInstance(context);
        manager = GPSMockManager.getInstance();
        setPrivateField("appContext", context);
        setPrivateField("currentLocation", null);
        setPrivateField("mocking", false);
    }

    @Test
    public void buildLocation_preservesCoordinatesAndReportsMockOnApi34() {
        WFLocation source = new WFLocation(24.7136d, 46.6753d, "local fixture");
        source.setAltitude(612.5d);
        source.setAccuracy(4.5f);
        source.setSpeed(1.25f);
        source.setBearing(270f);

        Location location = manager.buildLocation("gps", source);

        assertEquals("gps", location.getProvider());
        assertEquals(24.7136d, location.getLatitude(), 0.000001d);
        assertEquals(46.6753d, location.getLongitude(), 0.000001d);
        assertEquals(612.5d, location.getAltitude(), 0.000001d);
        assertEquals(4.5f, location.getAccuracy(), 0.0001f);
        assertEquals(1.25f, location.getSpeed(), 0.0001f);
        assertEquals(270f, location.getBearing(), 0.0001f);
        assertTrue("API 34 Location must retain an explicit, visible mock marker", location.isMock());
    }

    @Test
    public void requestVerifiedStart_returnsLicenseRequiredForDisabledLocalLicense() {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Disabled",
                System.currentTimeMillis() + 86_400_000L, "local fixture only");
        manager.setLocation(new WFLocation(24.7136d, 46.6753d));

        GPSMockManager.StartResult result = manager.requestVerifiedStart();

        assertEquals(GPSMockManager.StartResult.LICENSE_REQUIRED, result);
        assertFalse("Disabled local state is not an active license", LicenseManager.getInstance().isActive(context));
        assertFalse("The start guard must not persist spoofing for an unlicensed context",
                storage.isMocking());
        assertFalse("The manager must remain stopped when the active-license guard fails",
                manager.isMocking());
    }

    @Test
    public void requestVerifiedStart_returnsLocationRequiredBeforeRuntimeCheck() {
        saveActiveLocalFixture();

        GPSMockManager.StartResult result = manager.requestVerifiedStart();

        assertEquals(GPSMockManager.StartResult.LOCATION_REQUIRED, result);
        assertFalse(storage.isMocking());
        assertFalse(manager.isMocking());
    }

    @Test
    public void requestVerifiedStart_returnsRuntimeUnavailableWithoutLocalHookMarker() {
        saveActiveLocalFixture();
        manager.setLocation(new WFLocation(24.7136d, 46.6753d));

        GPSMockManager.StartResult result = manager.requestVerifiedStart();

        assertEquals("Robolectric does not install the host hook; no marker must fail closed",
                GPSMockManager.StartResult.RUNTIME_UNAVAILABLE, result);
        assertFalse("A missing runtime marker must clear any persisted spoofing state",
                storage.isMocking());
        assertFalse(manager.isMocking());
    }

    private void saveActiveLocalFixture() {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active",
                System.currentTimeMillis() + 86_400_000L, "future local fixture");
    }

    private void setPrivateField(String name, Object value) throws Exception {
        Field field = GPSMockManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
