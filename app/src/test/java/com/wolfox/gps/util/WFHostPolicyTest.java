package com.wolfox.gps.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Plain-JUnit eligibility checks; authorization and embedding remain explicit host actions. */
public class WFHostPolicyTest {

    @Test
    public void permitsWellFormedUserOwnedAppPackageNamesIncludingPreviousHost() {
        assertTrue(WFHostPolicy.permits("com.example.alpha"));
        assertTrue(WFHostPolicy.permits("dev.company.beta"));
        assertTrue(WFHostPolicy.permits("org.example.Host_42"));
        assertTrue(WFHostPolicy.permits("com.t2.AvailoHader"));
    }

    @Test
    public void rejectsNullPathsAndInvalidPackageSeparators() {
        assertFalse(WFHostPolicy.permits(null));
        assertFalse(WFHostPolicy.permits(""));
        assertFalse(WFHostPolicy.permits("com"));
        assertFalse(WFHostPolicy.permits("com.example/alpha"));
        assertFalse(WFHostPolicy.permits("com\\example.alpha"));
        assertFalse(WFHostPolicy.permits("com..example"));
        assertFalse(WFHostPolicy.permits("com.example.alpha/child"));
    }

    @Test
    public void rejectsAndroidCoreAndSystemUiPackages() {
        assertFalse(WFHostPolicy.permits("android"));
        assertFalse(WFHostPolicy.permits("android.app"));
        assertFalse(WFHostPolicy.permits("com.android.systemui"));
        assertFalse(WFHostPolicy.permits("com.android.systemui.plugins"));
    }

    @Test
    public void rejectsTheModulePackageWhileLeavingOtherValidAppsEligible() {
        assertFalse(WFHostPolicy.permits("com.wolfox.gps"));
        assertTrue(WFHostPolicy.permits("net.owner.authorizedhost"));
    }
}
