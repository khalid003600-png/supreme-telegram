package com.wolfox.gps.util;
import android.app.Activity;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class SafeUiTest {
    @Test public void optionalFailuresDoNotReplaceTheHostsExceptionHandler() {
        Thread.UncaughtExceptionHandler before = Thread.getDefaultUncaughtExceptionHandler();
        SafeUi.run(RuntimeEnvironment.getApplication(), "permission", () -> { throw new SecurityException(); });
        SafeUi.run(RuntimeEnvironment.getApplication(), "runtime", () -> { throw new NoClassDefFoundError(); });
        assertSame(before, Thread.getDefaultUncaughtExceptionHandler());
    }
    @Test public void finishingActivityDoesNotExecuteTheAction() {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.finish();
        SafeUi.run(activity, "closed", () -> { fail("Finishing Activity must not open UI"); });
    }
    @Test(expected = OutOfMemoryError.class) public void fatalErrorsAreNotSwallowed() {
        SafeUi.run(RuntimeEnvironment.getApplication(), "fatal", () -> { throw new OutOfMemoryError(); });
    }
}
