package com.wolfox.gps.util;

import android.content.Context;
import android.graphics.Typeface;
import org.robolectric.RuntimeEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class UiAuditModuleFontAssetsTest {
    private static final String FONT = "wolfox/fonts/Cairo-Variable.ttf";

    private static ClassLoader fixture(byte[] bytes, AtomicInteger reads) {
        return new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) {
                assertEquals("assets/" + FONT, name);
                reads.incrementAndGet();
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    @Test public void moduleClassLoaderAssetCopiesExactlyAndReusesContentCache() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        byte[] bytes = new byte[]{1, 2, 3, 42, -1};
        AtomicInteger reads = new AtomicInteger();
        File first = WFTheme.materializeFont(context, FONT, fixture(bytes, reads));
        assertArrayEquals(bytes, Files.readAllBytes(first.toPath()));
        File second = WFTheme.materializeFont(context, FONT, fixture(bytes, reads));
        assertEquals(first.getCanonicalPath(), second.getCanonicalPath());
        assertTrue(first.getCanonicalPath().startsWith(context.getCacheDir().getCanonicalPath()));
        assertEquals(2, reads.get());
    }

    @Test public void missingModuleAssetLeavesHostAssetFallbackAvailable() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        assertNull(WFTheme.materializeFont(context, FONT, new ClassLoader(null) {}));
        assertNull(WFTheme.materializeFont(context, FONT, null));
    }

    @Test public void fontReadIsBoundedAndCannotUseArbitraryAssetPaths() throws Exception {
        Context context = RuntimeEnvironment.getApplication();
        try {
            WFTheme.materializeFont(context, "../../anything", fixture(new byte[]{1}, new AtomicInteger()));
            fail("Arbitrary module paths must be refused");
        } catch (IllegalArgumentException expected) { }
        try {
            WFTheme.materializeFont(context, FONT,
                    fixture(new byte[1024 * 1024 + 1], new AtomicInteger()));
            fail("Oversized fonts must be refused before any cache write");
        } catch (java.io.IOException expected) { }
        try {
            WFTheme.materializeFont(context, FONT, fixture(new byte[0], new AtomicInteger()));
            fail("Empty fonts must be refused");
        } catch (java.io.IOException expected) { }
    }

    @Test public void nullContextReturnsAvailableTypefaceWithoutStartingAssetDiskAccess() throws Exception {
        java.lang.reflect.Field lockField = WFTheme.class.getDeclaredField("FONT_LOCK");
        lockField.setAccessible(true);
        java.lang.reflect.Field loadedField = WFTheme.class.getDeclaredField("loaded");
        loadedField.setAccessible(true);
        synchronized (lockField.get(null)) {
            Typeface regular = WFTheme.regular(null);
            Typeface medium = WFTheme.medium(null);
            assertNotNull(regular);
            assertNotNull(medium);
            assertSame(regular, WFTheme.regular(null));
            assertSame(medium, WFTheme.medium(null));
            if (!loadedField.getBoolean(null)) {
                assertEquals(Typeface.NORMAL, regular.getStyle());
                assertEquals(Typeface.BOLD, medium.getStyle());
            }
        }
    }
}
