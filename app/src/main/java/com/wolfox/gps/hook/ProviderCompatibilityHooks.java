package com.wolfox.gps.hook;

import android.location.Location;
import android.location.LocationManager;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFLog;

import java.util.Collections;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Optional provider response compatibility. Every response is a framework Location
 * created by GPSMockManager and explicitly marked as mock. Real provider availability
 * and vendor-specific objects without a visible mock marker are left untouched.
 */
final class ProviderCompatibilityHooks {
    private static final String TAG = "WolFoxProviders";

    private ProviderCompatibilityHooks() {}

    static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        hookLocationResultList(lpparam.classLoader,
                "com.google.android.gms.location.LocationResult");
        hookLocationResultList(lpparam.classLoader,
                "com.huawei.hms.location.LocationResult");
        hookFusedTask(lpparam.classLoader,
                "com.huawei.hms.location.FusedLocationProviderClient",
                "com.huawei.hms.tasks.Tasks");
        hookMapbox(lpparam.classLoader);
    }

    private static void hookLocationResultList(ClassLoader loader, String className) {
        try {
            Class<?> type = XposedHelpers.findClass(className, loader);
            XposedBridge.hookAllMethods(type, "getLocations", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Location location = activeLocation();
                    if (location != null) param.setResult(Collections.singletonList(location));
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, className + ".getLocations unavailable: " + error.getMessage());
        }
    }

    private static void hookFusedTask(ClassLoader loader, String clientName, String tasksName) {
        try {
            Class<?> client = XposedHelpers.findClass(clientName, loader);
            XposedBridge.hookAllMethods(client, "getLastLocation", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Location location = activeLocation();
                    if (location == null) return;
                    try {
                        Class<?> tasks = XposedHelpers.findClass(tasksName, loader);
                        Object task = tasks.getMethod("forResult", Object.class)
                                .invoke(null, location);
                        if (task != null) param.setResult(task);
                    } catch (Throwable error) {
                        WFLog.d(TAG, tasksName + ": " + error.getMessage());
                    }
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, clientName + " unavailable: " + error.getMessage());
        }
    }

    private static void hookMapbox(ClassLoader loader) {
        try {
            Class<?> result = XposedHelpers.findClass(
                    "com.mapbox.android.core.location.LocationEngineResult", loader);
            XposedBridge.hookAllMethods(result, "getLastLocation", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Location location = activeLocation();
                    if (location != null) param.setResult(location);
                }
            });
            XposedBridge.hookAllMethods(result, "getLocations", new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam param) {
                    Location location = activeLocation();
                    if (location != null) param.setResult(Collections.singletonList(location));
                }
            });
        } catch (Throwable error) {
            WFLog.d(TAG, "Mapbox unavailable: " + error.getMessage());
        }
    }

    private static WFLocation activeModel() {
        GPSMockManager manager = GPSMockManager.getInstance();
        if (!manager.isMocking()) return null;
        WFLocation location = manager.getLocation();
        return location != null && location.isValid() ? location : null;
    }

    private static Location activeLocation() {
        WFLocation model = activeModel();
        return model == null ? null : GPSMockManager.getInstance()
                .buildLocation(LocationManager.GPS_PROVIDER, model);
    }
}
