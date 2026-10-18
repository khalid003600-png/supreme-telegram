package com.wolfox.gps.util;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;

/** Shared Arabic typography. Module fonts load off the UI thread, including embedded APKs. */
public final class WFTheme {
    private static final String MEDIUM_ASSET = "wolfox/fonts/DINNextLTArabic-Medium.ttf";
    private static final String REGULAR_ASSET = "wolfox/fonts/DINNextLTArabic-Regular.otf";
    private static final int MAX_FONT_BYTES = 512 * 1024;
    private static final Object FONT_LOCK = new Object();
    private static final Typeface FALLBACK_MEDIUM = Typeface.create("sans-serif", Typeface.BOLD);
    private static final Typeface FALLBACK_REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    private static final ArrayList<PendingText> WAITING = new ArrayList<>();
    private static volatile Typeface medium;
    private static volatile Typeface regular;
    private static volatile boolean loading;
    private static volatile boolean loaded;

    private WFTheme() {}

    private static final class PendingText {
        final WeakReference<TextView> view;
        boolean bold;
        PendingText(TextView text, boolean weight) {
            view = new WeakReference<>(text);
            bold = weight;
        }
    }

    /** Non-blocking: the first frame uses system typography until the bundled fonts are ready. */
    public static Typeface regular(Context context) {
        preload(context);
        Typeface ready = regular;
        return ready == null ? FALLBACK_REGULAR : ready;
    }

    public static Typeface medium(Context context) {
        preload(context);
        Typeface ready = medium;
        return ready == null ? FALLBACK_MEDIUM : ready;
    }

    public static void preload(Context context) {
        if (context == null || loaded || loading) return;
        Context application = context.getApplicationContext();
        if (application == null) return; // Never retain an Activity in background work.
        synchronized (FONT_LOCK) {
            if (loaded || loading) return;
            loading = true;
        }
        Thread worker = new Thread(() -> {
            Typeface loadedRegular = load(application, REGULAR_ASSET, Typeface.NORMAL);
            Typeface loadedMedium = load(application, MEDIUM_ASSET, Typeface.BOLD);
            synchronized (FONT_LOCK) {
                regular = loadedRegular;
                medium = loadedMedium;
                loaded = true;
                loading = false;
            }
            new Handler(Looper.getMainLooper()).post(WFTheme::applyPendingFonts);
        }, "WolFox-font-assets");
        worker.setDaemon(true);
        worker.start();
    }

    public static void apply(TextView view, boolean bold) {
        if (view == null) return;
        view.setTypeface(bold ? medium(view.getContext()) : regular(view.getContext()));
        float scaledDensity = view.getResources().getDisplayMetrics().scaledDensity;
        if (scaledDensity > 0f && view.getTextSize() < 13f * scaledDensity) {
            view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 13f);
        }
        if (!loaded) {
            synchronized (FONT_LOCK) {
                if (loaded) {
                    view.setTypeface(bold ? medium : regular);
                    return;
                }
                for (int i = WAITING.size() - 1; i >= 0; i--) {
                    PendingText pending = WAITING.get(i);
                    TextView text = pending.view.get();
                    if (text == null) WAITING.remove(i);
                    else if (text == view) {
                        pending.bold = bold;
                        return;
                    }
                }
                // Bound temporary bookkeeping; no strong references to views or Activities.
                if (WAITING.size() < 1024) WAITING.add(new PendingText(view, bold));
            }
        }
    }

    private static void applyPendingFonts() {
        ArrayList<PendingText> snapshot;
        synchronized (FONT_LOCK) {
            snapshot = new ArrayList<>(WAITING);
            WAITING.clear();
        }
        for (PendingText pending : snapshot) {
            TextView text = pending.view.get();
            if (text != null) text.setTypeface(pending.bold ? medium : regular);
        }
    }

    public static void applyTree(View view) {
        if (view == null) return;
        if (view instanceof TextView) {
            TextView text = (TextView) view;
            Typeface current = text.getTypeface();
            apply(text, current != null && (current == medium || current == FALLBACK_MEDIUM
                    || current.getStyle() == Typeface.BOLD));
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) applyTree(group.getChildAt(i));
        }
    }

    public static String icon(String value) {
        return value + "\uFE0E";
    }

    private static Typeface load(Context context, String asset, int fallbackStyle) {
        try {
            File font = materializeFont(context, asset, WFTheme.class.getClassLoader());
            if (font != null) return Typeface.createFromFile(font);
            return Typeface.createFromAsset(context.getAssets(), asset);
        } catch (Exception | LinkageError ignored) {
            return Typeface.create("sans-serif", fallbackStyle);
        }
    }

    /** Fixed internal assets only, bounded copy, content-addressed cache, atomic replacement. */
    static File materializeFont(Context context, String asset, ClassLoader loader) throws Exception {
        if (!REGULAR_ASSET.equals(asset) && !MEDIUM_ASSET.equals(asset)) {
            throw new IllegalArgumentException("Unsupported module font asset");
        }
        if (loader == null) return null;
        try (InputStream input = loader.getResourceAsStream("assets/" + asset)) {
            if (input == null) return null;
            ByteArrayOutputStream data = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (count == 0) throw new IOException("Font stream made no progress");
                total += count;
                if (total > MAX_FONT_BYTES) throw new IOException("Module font exceeds size limit");
                data.write(buffer, 0, count);
            }
            if (total == 0) throw new IOException("Empty module font");
            byte[] bytes = data.toByteArray();
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder name = new StringBuilder("font-");
            for (byte value : digest) name.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            name.append(".font");
            File directory = new File(context.getCacheDir(), "wolfox-font-assets");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Font cache unavailable");
            File target = new File(directory, name.toString());
            if (target.isFile() && target.length() == bytes.length) return target;
            AtomicFile atomic = new AtomicFile(target);
            FileOutputStream output = null;
            try {
                output = atomic.startWrite();
                output.write(bytes);
                atomic.finishWrite(output);
            } catch (IOException failure) {
                if (output != null) atomic.failWrite(output);
                throw failure;
            }
            return target;
        }
    }
}
