package com.wolfox.gps.util;

import android.content.Context;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Properties;

/** Arabic templates are assets, not host resource IDs, so embedded module classloaders work. */
public final class WFUiText {
    private static final String ASSET = "wolfox/ui-text-ar.properties";
    private static volatile Properties cached;
    private WFUiText() {}

    public static String text(Context context, String key) {
        Properties templates = cached;
        if (templates == null) {
            synchronized (WFUiText.class) {
                templates = cached;
                if (templates == null) cached = templates = load(context);
            }
        }
        String value = templates.getProperty(key);
        if (value == null) throw new IllegalArgumentException("Unknown UI text key");
        return value;
    }

    public static String format(Context context, String key, Object... values) {
        return String.format(Locale.ROOT, text(context, key), values);
    }

    private static Properties load(Context context) {
        Properties defaults = new Properties();
        defaults.setProperty("search_error", "×  %s");
        defaults.setProperty("search_success", "✓  %s%s\n%s");
        defaults.setProperty("simulation_start", "✓\uFE0E  تشغيل المحاكاة");
        defaults.setProperty("logging_enable", "تفعيل الـ Log");
        try {
            ClassLoader loader = WFUiText.class.getClassLoader();
            InputStream stream = loader == null ? null : loader.getResourceAsStream("assets/" + ASSET);
            if (stream == null && context != null) stream = context.getAssets().open(ASSET);
            if (stream != null) {
                try (InputStream input = stream) { defaults.putAll(readTemplates(input)); }
            }
        } catch (IOException ignored) { /* Safe Arabic defaults if a direct host omitted the asset. */ }
        return defaults;
    }

    static Properties readTemplates(InputStream input) throws IOException {
        ByteArrayOutputStream data = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (count == 0) throw new IOException("UI text stream made no progress");
            if (data.size() + count > 8192) throw new IOException("UI text asset exceeds limit");
            data.write(buffer, 0, count);
        }
        Properties templates = new Properties();
        templates.load(new InputStreamReader(new ByteArrayInputStream(data.toByteArray()),
                StandardCharsets.UTF_8));
        return templates;
    }
}
