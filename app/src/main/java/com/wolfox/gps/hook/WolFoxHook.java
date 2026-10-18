package com.wolfox.gps.hook;

import android.app.Application;
import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;

import com.wolfox.gps.ModuleConfig;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFHostPolicy;
import com.wolfox.gps.util.WFLog;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * WolFoxHook — نقطة الدخول للموديول عبر LSPatch
 *
 * يعمل داخل مضيف أدمجه صاحبه أو اختار تحميله عبر إطار hooks متوافق.
 * لا يدمج أو يحمّل نفسه تلقائيًا في التطبيقات الأخرى.
 *
 * المحاكاة تبقى معلنة: لا يتم إخفاء isMock أو isFromMockProvider،
 * ويقتصر تعديل كائنات Location على الكائنات الموسومة كمواقع اختبار.
 */
public class WolFoxHook implements IXposedHookLoadPackage {

    private static final String TAG = "WolFoxHook";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (lpparam == null || lpparam.packageName == null || lpparam.classLoader == null) return;
        if (!WFHostPolicy.permits(lpparam.packageName)) {
            WFLog.d(TAG, "Skipping unsupported application host: " + lpparam.packageName);
            return;
        }
        WFLog.i(TAG, "LSPatch loaded in: " + lpparam.packageName);

        HookSafety.run("install.context", this::hookApplicationContext);
        HookSafety.run("install.locationManager", () -> hookLocationManager(lpparam));
        HookSafety.run("install.locationObject", this::hookLocationObject);
        HookSafety.run("install.gms", () -> hookFusedLocation(lpparam));
        HookSafety.run("install.hms", () -> hookHuaweiLocation(lpparam));
        HookSafety.run("install.providers", () -> ProviderCompatibilityHooks.install(lpparam));
        // Install lifecycle fallback in each selected host process that may contain an Activity.
        // A host may put its UI in a custom process whose name differs from the package. Finding
        // DirectBootstrap in the class loader does not prove that the host calls it:
        // the class is bundled inside the module as well.  ActivityHook checks the
        // runtime host marker and stands down only when direct integration is active.
        HookSafety.run("install.activity", () -> ActivityHook.install(lpparam));

        WFLog.i(TAG, "WolFox hooks installed for selected application host");
    }

    private void hookLocationObject() {
        hookLocationGetter("getLatitude", "latitude");
        hookLocationGetter("getLongitude", "longitude");
        hookLocationGetter("getAltitude", "altitude");
        hookLocationGetter("getAccuracy", "accuracy");
        hookLocationGetter("getSpeed", "speed");
        hookLocationGetter("getBearing", "bearing");
    }

    private static boolean explicitlyMarkedMock(Location location) {
        if (location == null) return false;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) return location.isMock();
        } catch (Throwable ignored) {}
        try {
            return location.isFromMockProvider();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void hookLocationGetter(String method, String field) {
        try {
            XposedBridge.hookAllMethods(Location.class, method, new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!GPSMockManager.getInstance().isMocking()
                                    || !(param.thisObject instanceof Location)
                                    || !explicitlyMarkedMock((Location) param.thisObject)) return;
                            WFLocation l = GPSMockManager.getInstance().getLocation();
                            if (l == null) return;
                            if ("latitude".equals(field)) param.setResult(l.getLatitude());
                            else if ("longitude".equals(field)) param.setResult(l.getLongitude());
                            else if ("altitude".equals(field)) param.setResult(l.getAltitude());
                            else if ("accuracy".equals(field)) param.setResult(l.getAccuracy());
                            else if ("speed".equals(field)) param.setResult(l.getSpeed());
                            else if ("bearing".equals(field)) param.setResult(l.getBearing());
                        }
                    });
        } catch (Throwable e) { WFLog.d(TAG, method + ": " + e.getMessage()); }
    }

    // ─── تهيئة السياق ─────────────────────────────────────────────────────────

    private void hookApplicationContext() {
        // Application.attach is the earliest stable context point and also covers hosts
        // whose custom Application overrides onCreate without calling super.
        try {
            XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            Context context = param.args != null && param.args.length > 0
                                    && param.args[0] instanceof Context
                                    ? (Context) param.args[0] : (Context) param.thisObject;
                            contextReady(context, "attach");
                        }
                    });
        } catch (Throwable error) {
            WFLog.e(TAG, "hook Application.attach: " + error.getMessage());
        }

        // Fallback for unusual runtimes where attach has already completed.
        try {
            XposedHelpers.findAndHookMethod(Application.class, "onCreate",
                    new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            contextReady((Context) param.thisObject, "onCreate");
                        }
                    });
        } catch (Throwable error) {
            WFLog.e(TAG, "hook Application.onCreate: " + error.getMessage());
        }

        // Do not reflect into ActivityThread.currentApplication(): it is an unsupported Android
        // private API.  If this module is loaded after Application startup, ActivityHook's next
        // public Activity callback performs the same initialization without claiming a hook ran.
    }

    private static void contextReady(Context context, String source) {
        HookSafety.run("contextReady." + source, () -> {
            if (context == null) return;
            GPSMockManager.getInstance().init(context);
            com.wolfox.gps.util.WFStorage.getInstance(context)
                    .markHookRuntimeActive(ModuleConfig.VERSION_CODE);
            WFLog.i(TAG, "Context ready via " + source);
        });
    }

    // ─── LocationManager ──────────────────────────────────────────────────────

    private void hookLocationManager(XC_LoadPackage.LoadPackageParam lpparam) {

        // 1) getLastKnownLocation
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager.class,
                "getLastKnownLocation", String.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        param.setResult(GPSMockManager.getInstance()
                            .buildLocation((String) param.args[0], wfl));
                        WFLog.hook("getLastKnownLocation", wfl);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "getLastKnownLocation: " + e.getMessage()); }

        // 2) requestLocationUpdates (String, long, float, LocationListener)
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager.class,
                "requestLocationUpdates",
                String.class, long.class, float.class, LocationListener.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        LocationListener listener = (LocationListener) param.args[3];
                        if (listener == null) return;
                        Location loc = GPSMockManager.getInstance()
                            .buildLocation((String) param.args[0], wfl);
                        try { listener.onLocationChanged(loc); } catch (Exception ignored) {}
                        WFLog.hook("requestLocationUpdates→cb", wfl);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "requestLocationUpdates: " + e.getMessage()); }

        // 3) requestSingleUpdate
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager.class,
                "requestSingleUpdate",
                String.class, LocationListener.class, android.os.Looper.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        LocationListener listener = (LocationListener) param.args[1];
                        if (listener == null) return;
                        Location loc = GPSMockManager.getInstance()
                            .buildLocation((String) param.args[0], wfl);
                        try { listener.onLocationChanged(loc); } catch (Exception ignored) {}
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "requestSingleUpdate: " + e.getMessage()); }

        // 4) getCurrentLocation (API 30+).  java.util.function.Consumer is also unavailable
        // before API 24, so resolve it only after the API-30 framework guard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        try {
            XposedHelpers.findAndHookMethod(
                LocationManager.class,
                "getCurrentLocation",
                String.class, android.os.CancellationSignal.class,
                java.util.concurrent.Executor.class,
                lpparam.classLoader.loadClass("java.util.function.Consumer"),
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        if (param.args == null || param.args.length <= 3
                                || !(param.args[0] instanceof String) || param.args[3] == null) return;
                        try {
                            Object consumer = param.args[3];
                            Location loc = GPSMockManager.getInstance()
                                .buildLocation((String) param.args[0], wfl);
                            consumer.getClass().getMethod("accept", Object.class)
                                .invoke(consumer, loc);
                            param.setResult(null);
                        } catch (Exception ex) {
                            WFLog.e(TAG, "getCurrentLocation: " + ex.getMessage());
                        }
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "getCurrentLocation (API30+): " + e.getMessage()); }
        } else {
            WFLog.d(TAG, "getCurrentLocation skipped below API 30");
        }
    }

    // ─── FusedLocationProviderClient ─────────────────────────────────────────

    private void hookFusedLocation(XC_LoadPackage.LoadPackageParam lpparam) {

        // getLastLocation()
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.FusedLocationProviderClient",
                lpparam.classLoader, "getLastLocation",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        Location loc = GPSMockManager.getInstance()
                            .buildLocation(LocationManager.GPS_PROVIDER, wfl);
                        Object task = buildFakeTask(loc, lpparam.classLoader);
                        if (task != null) param.setResult(task);
                        WFLog.hook("Fused.getLastLocation", wfl);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "Fused.getLastLocation: " + e.getMessage()); }

        // LocationResult.getLastLocation()
        try {
            XposedHelpers.findAndHookMethod(
                "com.google.android.gms.location.LocationResult",
                lpparam.classLoader, "getLastLocation",
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!GPSMockManager.getInstance().isMocking()) return;
                        WFLocation wfl = GPSMockManager.getInstance().getLocation();
                        if (wfl == null) return;
                        param.setResult(GPSMockManager.getInstance()
                            .buildLocation(LocationManager.GPS_PROVIDER, wfl));
                        WFLog.hook("LocationResult.getLastLocation", wfl);
                    }
                }
            );
        } catch (Exception e) { WFLog.d(TAG, "LocationResult: " + e.getMessage()); }

    }

    private Object buildFakeTask(Location loc, ClassLoader cl) {
        try {
            Class<?> tasks = cl.loadClass("com.google.android.gms.tasks.Tasks");
            return tasks.getMethod("forResult", Object.class).invoke(null, loc);
        } catch (Exception e) {
            WFLog.e(TAG, "buildFakeTask: " + e.getMessage());
            return null;
        }
    }

    private void hookHuaweiLocation(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.huawei.hms.location.LocationResult", lpparam.classLoader,
                    "getLastLocation", new XC_MethodHook() {
                        @Override protected void afterHookedMethod(MethodHookParam param) {
                            if (!GPSMockManager.getInstance().isMocking()) return;
                            WFLocation l = GPSMockManager.getInstance().getLocation();
                            if (l != null) param.setResult(GPSMockManager.getInstance()
                                    .buildLocation(LocationManager.GPS_PROVIDER, l));
                        }
                    });
        } catch (Exception e) { WFLog.d(TAG, "HMS LocationResult: " + e.getMessage()); }
    }

}
