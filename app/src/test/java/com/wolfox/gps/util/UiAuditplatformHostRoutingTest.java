package com.wolfox.gps.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.XmlResourceParser;
import android.os.Looper;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.R;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.WFLocation;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.xmlpull.v1.XmlPullParser;

import java.lang.reflect.Field;

/**
 * Local platform contract tests only: no Xposed/LSPatch hook is installed or emulated, no network
 * request is made, and the runtime fixture must fail closed when it has no verified hook marker.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiAuditplatformHostRoutingTest {
    private static final String PREFS = "wolfox_gps_prefs";

    private Context context;
    private WFStorage storage;
    private GPSMockManager manager;

    @Before
    public void resetMainLooperAndStaticSingletons() throws Exception {
        Shadows.shadowOf(Looper.getMainLooper()).idle();
        context = RuntimeEnvironment.getApplication();
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        resetSingleton(WFStorage.class, "instance");
        resetSingleton(GPSMockManager.class, "instance");
        storage = WFStorage.getInstance(context);
        manager = GPSMockManager.getInstance();
        setField(manager, "appContext", context);
        setField(manager, "currentLocation", null);
        setField(manager, "mocking", false);
    }

    @After
    public void clearMainLooperAndStaticSingletons() throws Exception {
        if (context != null) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit();
        }
        resetSingleton(WFStorage.class, "instance");
        resetSingleton(GPSMockManager.class, "instance");
        Shadows.shadowOf(Looper.getMainLooper()).idle();
    }

    @Test
    public void hostPolicyRoutesOnlyEligibleNonCoreNonModulePackages() {
        assertFalse(WFHostPolicy.permits(null));
        assertFalse(WFHostPolicy.permits("android"));
        assertFalse(WFHostPolicy.permits("android.app"));
        assertFalse(WFHostPolicy.permits("com.android.systemui"));
        assertFalse(WFHostPolicy.permits("com.android.systemui.plugins"));
        assertFalse(WFHostPolicy.permits("com.wolfox.gps"));
        assertTrue(WFHostPolicy.permits("net.owner.authorizedhost"));
    }

    @Test
    public void localRuntimeFixtureFailsClosedWithoutVerifiedHookMarker() {
        storage.saveLicense("LOCAL-TEST-NOT-A-CREDENTIAL", "Active",
                System.currentTimeMillis() + 86_400_000L, "local fixture only");
        manager.setLocation(new WFLocation(24.7136d, 46.6753d, "local fixture"));

        assertFalse(storage.isHookRuntimeActive(ModuleConfig.VERSION_CODE));
        assertEquals(GPSMockManager.StartResult.RUNTIME_UNAVAILABLE,
                manager.requestVerifiedStart());
        assertFalse(manager.isMocking());
        assertFalse(storage.isMocking());
    }

    @Test
    public void manifestPackagesVectorIconAndDataExtractionRules() throws Exception {
        ApplicationInfo info = context.getApplicationInfo();
        assertNotEquals(0, info.icon);
        assertEquals(R.drawable.wolfox_module_icon, info.icon);

        XmlResourceParser parser = context.getResources().getXml(
                R.xml.ui_audit_data_extraction_rules);
        try {
            while (parser.next() != XmlPullParser.START_TAG) {
                // Move to the root element only; the parser is local and has no runtime side effect.
            }
            assertEquals("data-extraction-rules", parser.getName());
        } finally {
            parser.close();
        }
    }

    private static void resetSingleton(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(null, null);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
