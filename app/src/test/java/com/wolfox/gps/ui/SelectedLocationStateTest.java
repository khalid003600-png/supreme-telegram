package com.wolfox.gps.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.SharedPreferences;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.FavoriteLocation;
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
 * Local persistence/selection regression coverage only. It neither calls a map provider nor
 * simulates an installed hook. The active value below is an existing non-production fixture used
 * solely to exercise GPSMockManager.selectAndStart's normal license gate.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SelectedLocationStateTest {
    private static final String PREFS = "wolfox_gps_prefs";
    private static final String LOCAL_FIXTURE_CODE = "LOCAL-TEST-NOT-A-CREDENTIAL";

    private Context context;
    private WFStorage storage;
    private GPSMockManager manager;

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
    }

    @Test
    public void exactRiyadhCoordinatesAndLabelSurvivePersistence() {
        WFLocation riyadh = new WFLocation(24.71355212345678d, 46.67529698765432d,
                "الرياض — المنزل");

        storage.saveLastLocation(riyadh);
        WFLocation reloaded = storage.getLastLocation();

        assertNotNull(reloaded);
        assertEquals(Double.doubleToRawLongBits(riyadh.getLatitude()),
                Double.doubleToRawLongBits(reloaded.getLatitude()));
        assertEquals(Double.doubleToRawLongBits(riyadh.getLongitude()),
                Double.doubleToRawLongBits(reloaded.getLongitude()));
        assertEquals("الرياض — المنزل", reloaded.getLabel());
        String mapUrl = MapDialog.mapSourceUrl(reloaded.getLatitude(), reloaded.getLongitude(),
                "satellite", true, false);
        assertTrue(mapUrl.contains("lat=" + Double.toString(riyadh.getLatitude())));
        assertTrue(mapUrl.contains("lng=" + Double.toString(riyadh.getLongitude())));
    }

    @Test
    public void zeroZeroIsARealSelectedLocationAndItsMapUrlIsNotRoundedAway() {
        WFLocation origin = new WFLocation(0d, 0d, "نقطة الأصل");

        storage.saveLastLocation(origin);
        WFLocation reloaded = storage.getLastLocation();

        assertNotNull(reloaded);
        assertEquals(Double.doubleToRawLongBits(0d),
                Double.doubleToRawLongBits(reloaded.getLatitude()));
        assertEquals(Double.doubleToRawLongBits(0d),
                Double.doubleToRawLongBits(reloaded.getLongitude()));
        assertEquals("نقطة الأصل", reloaded.getLabel());
        assertEquals("https://app.local/map.html?lat=0.0&lng=0.0&type=roadmap&preview=1&readonly=1",
                MapDialog.mapSourceUrl(reloaded.getLatitude(), reloaded.getLongitude(), "roadmap",
                        true, true));
    }

    @Test
    public void legacyFloatCoordinatesReadOnceAndMigrateToExactBits() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        float legacyLatitude = 24.7136f;
        float legacyLongitude = 46.6753f;
        prefs.edit().putFloat("last_lat", legacyLatitude).putFloat("last_lng", legacyLongitude)
                .commit();

        WFLocation reloaded = storage.getLastLocation();

        assertNotNull(reloaded);
        assertEquals((double) legacyLatitude, reloaded.getLatitude(), 0d);
        assertEquals((double) legacyLongitude, reloaded.getLongitude(), 0d);
        assertEquals(Double.doubleToRawLongBits((double) legacyLatitude),
                prefs.getLong("last_lat_bits", Long.MIN_VALUE));
        assertEquals(Double.doubleToRawLongBits((double) legacyLongitude),
                prefs.getLong("last_lng_bits", Long.MIN_VALUE));
    }

    @Test
    public void staleNonNumericCoordinateValuesDoNotCreateLocation() {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("last_lat_bits", "not-a-double")
                .putString("last_lng_bits", "not-a-double")
                .commit();

        assertNull(storage.getLastLocation());
    }

    @Test
    public void selectingAnotherFavoriteKeepsItsNameAfterManagerReload() throws Exception {
        storage.saveLicense(LOCAL_FIXTURE_CODE, "Active",
                System.currentTimeMillis() + 86_400_000L, "local fixture only");
        FavoriteLocation jeddah = new FavoriteLocation("جدة — العمل",
                21.54333312345678d, 39.17277898765432d);

        assertTrue(manager.selectAndStart(jeddah.toWFLocation()));
        setManagerField("currentLocation", null);
        WFLocation reloaded = manager.getLocation();

        assertNotNull(reloaded);
        assertEquals("جدة — العمل", reloaded.getLabel());
        assertEquals(Double.doubleToRawLongBits(jeddah.getLatitude()),
                Double.doubleToRawLongBits(reloaded.getLatitude()));
        assertEquals(Double.doubleToRawLongBits(jeddah.getLongitude()),
                Double.doubleToRawLongBits(reloaded.getLongitude()));
    }

    private void setManagerField(String name, Object value) throws Exception {
        Field field = GPSMockManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
}
