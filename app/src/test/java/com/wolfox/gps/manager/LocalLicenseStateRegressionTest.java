package com.wolfox.gps.manager;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;

import com.wolfox.gps.util.WFStorage;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Exercises only the persisted, local license state machine.  The fixture token below is
 * intentionally non-production; no activation/verification API, executor, or network path is used.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class LocalLicenseStateRegressionTest {
    private static final String PREFS = "wolfox_gps_prefs";
    private static final String LOCAL_FIXTURE_CODE = "LOCAL-TEST-NOT-A-CREDENTIAL";

    private Context context;
    private WFStorage storage;

    @Before
    public void clearOnlyLocalFixturePreferences() {
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        storage = WFStorage.getInstance(context);
    }

    @Test
    public void activeRecordWithPastExpiryBecomesExpiredAndCannotPassGate() {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active",
                System.currentTimeMillis() - 1L, "past local fixture");

        assertFalse("An elapsed expiry must not be treated as active", storage.isLicenseActive());
        assertEquals("Expired", storage.getLicenseStatus());
        assertEquals("Expiry transition must retain the local record for status display",
                LOCAL_FIXTURE_CODE, storage.getLicenseCode());
        assertFalse("License gate must consume the same local expired state",
                LicenseManager.getInstance().isActive(context));
    }

    @Test
    public void disabledRecordNeverPassesGateEvenWithFutureExpiry() {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Disabled",
                System.currentTimeMillis() + 86_400_000L, "future local fixture");

        assertFalse(storage.isLicenseActive());
        assertEquals("Disabled", storage.getLicenseStatus());
        assertFalse(LicenseManager.getInstance().isActive(context));
    }

    @Test
    public void resetClearsLocalLicenseDetailsAndStopsPersistedMocking() {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active",
                System.currentTimeMillis() + 86_400_000L, "future local fixture");
        storage.saveLicenseDetails(System.currentTimeMillis() - 86_400_000L, 30);
        storage.setMocking(true);

        storage.clearLicense();

        assertEquals("", storage.getLicenseCode());
        assertEquals("", storage.getLicenseStatus());
        assertEquals(0L, storage.getLicenseExpiryEpoch());
        assertEquals(0L, storage.getLicenseStartEpoch());
        assertEquals(0, storage.getLicenseDurationDays());
        assertFalse(storage.isMocking());
        assertFalse(LicenseManager.getInstance().isActive(context));
    }
}
