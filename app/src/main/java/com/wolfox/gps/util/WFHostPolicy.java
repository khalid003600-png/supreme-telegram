package com.wolfox.gps.util;

import java.util.regex.Pattern;

/** Eligibility only: embedding or loading remains an explicit action by the authorized host. */
public final class WFHostPolicy {
    private static final Pattern PACKAGE_NAME = Pattern.compile(
            "[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)+");

    private WFHostPolicy() {}

    /** No fixed third-party package allowlist; core OS processes are never eligible. */
    public static boolean permits(String packageName) {
        if (packageName == null || !PACKAGE_NAME.matcher(packageName).matches()) return false;
        if ("android".equals(packageName) || packageName.startsWith("android.")) return false;
        if ("com.android.systemui".equals(packageName)
                || packageName.startsWith("com.android.systemui.")) return false;
        return !"com.wolfox.gps".equals(packageName);
    }
}
