package com.wolfox.gps.util;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.wolfox.gps.model.WFLocation;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/** Resilient resolver with a guaranteed callback, local parsing and provider fallbacks. */
public final class MapSearchResolver {
    private static final String TAG = "MapSearchResolver";
    private static final long RESPONSE_DEADLINE_MS = 18000L;
    private static final int MAX_QUERY_LENGTH = 512;
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(24));
    private static final ExecutorService GEOCODER_EXECUTOR = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<Runnable>(8));
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    public interface Callback { void onComplete(Result result); }

    /** Handle owned by a screen/WebView bridge; it prevents delivery after its UI goes away. */
    public interface Request {
        void cancel();
        boolean isCancelled();
    }

    public static final class Result {
        private final WFLocation location;
        private final String source;
        private final String error;

        private Result(WFLocation location, String source, String error) {
            this.location = location;
            this.source = source == null ? "" : source;
            this.error = error == null ? "" : error;
        }

        public boolean isSuccess() { return location != null && location.isValid(); }
        public WFLocation getLocation() { return location; }
        public String getSource() { return source; }
        public String getError() { return error; }
    }

    private MapSearchResolver() {}

    /**
     * Resolves off the UI thread and always posts a non-cancelled result on the main looper.
     * Callers must cancel the returned handle from their dismissal/lifecycle path.
     */
    public static Request resolve(Context context, String input, final Callback callback) {
        final Context app = context == null ? null : context.getApplicationContext();
        final String value = input == null ? "" : input.trim();
        final RequestHandle request = new RequestHandle();
        if (value.length() > MAX_QUERY_LENGTH) {
            MAIN.post(() -> {
                request.deliver(failure("نص البحث طويل جدًا؛ اختصره إلى 512 حرفًا"), callback);
            });
            return request;
        }
        final Runnable timeout = new Runnable() {
            @Override public void run() {
                if (request.deliver(failure("انتهت مهلة البحث؛ تحقق من الإنترنت وحاول مجددًا"), callback)) {
                    request.cancelWork();
                }
            }
        };
        request.setTimeout(timeout);
        MAIN.postDelayed(timeout, RESPONSE_DEADLINE_MS);

        try {
            Future<?> work = EXECUTOR.submit(new Runnable() {
            @Override public void run() {
                if (request.isCancelled()) return;
                Result result;
                try {
                    result = resolveBlocking(app, value, request);
                } catch (Throwable error) {
                    if (!request.isCancelled()) {
                        WFLog.e(TAG, "resolver: " + error.getMessage());
                    }
                    result = failure("تعذر إكمال البحث؛ حاول مرة أخرى");
                }
                final Result completed = result;
                MAIN.post(new Runnable() {
                    @Override public void run() {
                        request.deliver(completed, callback);
                    }
                });
            }
            });
            request.setWork(work);
        } catch (RejectedExecutionException saturated) {
            MAIN.post(() -> {
                request.deliver(failure("خدمة البحث مشغولة؛ انتظر لحظة ثم حاول مجددًا"), callback);
            });
        }
        return request;
    }

    static Result resolveBlocking(Context context, String input) {
        return resolveBlocking(context, input, null);
    }

    private static Result resolveBlocking(Context context, String input, RequestHandle request) {
        if (input == null || input.trim().isEmpty()) {
            return failure("أدخل عنوانًا أو إحداثيات أو رابط خريطة");
        }
        if (input.trim().length() > MAX_QUERY_LENGTH) {
            return failure("نص البحث طويل جدًا؛ اختصره إلى 512 حرفًا");
        }

        if (isCancelled(request)) return failure("تم إلغاء البحث");
        String expanded = input.trim();
        try {
            if (MapLinkParser.needsExpansion(expanded)) {
                expanded = MapLinkParser.expandKnownShortLink(expanded, request);
            }
        } catch (Throwable error) {
            if (!isCancelled(request)) WFLog.d(TAG, "short link: " + error.getMessage());
        }
        if (isCancelled(request)) return failure("تم إلغاء البحث");

        MapLinkParser.Parsed parsed = MapLinkParser.parse(expanded);
        if (parsed != null && parsed.hasCoordinates()) {
            String label = parsed.getLabel().isEmpty() ? parsed.getProvider() : parsed.getLabel();
            WFLocation location = new WFLocation(
                    parsed.getLatitude(), parsed.getLongitude(), label);
            return location.isValid()
                    ? success(location, parsed.getProvider())
                    : failure("الإحداثيات خارج النطاق");
        }

        String query = parsed == null ? input.trim() : parsed.getQuery();
        if (query == null || query.trim().isEmpty()) {
            return failure(parsed != null && parsed.isMapLink()
                    ? "تعذر استخراج موقع من رابط المشاركة"
                    : "لم يتم العثور على الموقع");
        }

        if (isCancelled(request)) return failure("تم إلغاء البحث");
        WFLocation location = geocodeAndroidWithTimeout(context, query, 3500L, request);
        String provider = "بحث الجهاز";
        if (location == null && !isCancelled(request)) {
            location = geocodeNominatim(query, request);
            provider = "OpenStreetMap";
        }
        if (location == null && !isCancelled(request)) {
            location = geocodePhoton(query, request);
            provider = "Photon";
        }
        if (isCancelled(request)) return failure("تم إلغاء البحث");
        if (location == null || !location.isValid()) {
            return failure("لم يتم العثور على الموقع؛ جرّب اسمًا أدق أو الصق رابط المشاركة كاملًا");
        }
        String source = parsed != null && parsed.isMapLink() && !parsed.getProvider().isEmpty()
                ? parsed.getProvider() : provider;
        return success(location, source);
    }

    private static WFLocation geocodeAndroidWithTimeout(
            final Context context, final String query, long timeoutMs, RequestHandle request) {
        if (context == null || !Geocoder.isPresent()) return null;
        Future<WFLocation> future = GEOCODER_EXECUTOR.submit(() -> geocodeAndroid(context, query));
        try {
            WFLocation location = future.get(timeoutMs, TimeUnit.MILLISECONDS);
            return isCancelled(request) ? null : location;
        } catch (Throwable error) {
            future.cancel(true);
            if (!isCancelled(request)) {
                WFLog.d(TAG, "Android geocoder timeout/failure: " + error.getMessage());
            }
            return null;
        }
    }

    private static WFLocation geocodeAndroid(Context context, String query) {
        try {
            Geocoder geocoder = new Geocoder(context, new Locale("ar"));
            List<Address> rows;
            if (Build.VERSION.SDK_INT >= 33) {
                final CountDownLatch latch = new CountDownLatch(1);
                final AtomicReference<List<Address>> result = new AtomicReference<>();
                geocoder.getFromLocationName(query, 1, new Geocoder.GeocodeListener() {
                    @Override public void onGeocode(List<Address> addresses) {
                        result.set(addresses);
                        latch.countDown();
                    }

                    @Override public void onError(String errorMessage) {
                        latch.countDown();
                    }
                });
                if (!latch.await(2800L, TimeUnit.MILLISECONDS)) return null;
                rows = result.get();
            } else {
                rows = geocodeLegacy(geocoder, query);
            }
            if (rows == null || rows.isEmpty()) return null;
            Address row = rows.get(0);
            String label = row.getAddressLine(0);
            WFLocation location = new WFLocation(row.getLatitude(), row.getLongitude(),
                    label == null || label.trim().isEmpty() ? query : label);
            return location.isValid() ? location : null;
        } catch (Throwable error) {
            WFLog.d(TAG, "Android geocoder: " + error.getMessage());
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private static List<Address> geocodeLegacy(Geocoder geocoder, String query)
            throws java.io.IOException {
        return geocoder.getFromLocationName(query, 1);
    }

    private static WFLocation geocodeNominatim(String query, RequestHandle request) {
        HttpURLConnection connection = null;
        try {
            if (isCancelled(request)) return null;
            String endpoint = "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1"
                    + "&accept-language=ar&q=" + URLEncoder.encode(query, "UTF-8");
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            if (request != null) request.onConnectionOpened(connection);
            configure(connection);
            if (isCancelled(request)) return null;
            if (connection.getResponseCode() != 200) return null;
            JSONArray rows = new JSONArray(readLimited(connection.getInputStream(), 131072, request));
            if (rows.length() == 0) return null;
            JSONObject row = rows.getJSONObject(0);
            WFLocation location = new WFLocation(
                    Double.parseDouble(row.getString("lat")),
                    Double.parseDouble(row.getString("lon")),
                    row.optString("display_name", query));
            return location.isValid() ? location : null;
        } catch (Throwable error) {
            WFLog.d(TAG, "Nominatim: " + error.getMessage());
            return null;
        } finally {
            if (request != null) request.onConnectionClosed(connection);
            if (connection != null) connection.disconnect();
        }
    }

    private static WFLocation geocodePhoton(String query, RequestHandle request) {
        HttpURLConnection connection = null;
        try {
            if (isCancelled(request)) return null;
            String endpoint = "https://photon.komoot.io/api/?limit=1&lang=ar&q="
                    + URLEncoder.encode(query, "UTF-8");
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            if (request != null) request.onConnectionOpened(connection);
            configure(connection);
            if (isCancelled(request)) return null;
            if (connection.getResponseCode() != 200) return null;
            JSONObject root = new JSONObject(readLimited(connection.getInputStream(), 131072, request));
            JSONArray features = root.optJSONArray("features");
            if (features == null || features.length() == 0) return null;
            JSONObject feature = features.getJSONObject(0);
            JSONArray coordinates = feature.getJSONObject("geometry").getJSONArray("coordinates");
            JSONObject properties = feature.optJSONObject("properties");
            String label = buildPhotonLabel(properties, query);
            WFLocation location = new WFLocation(
                    coordinates.getDouble(1), coordinates.getDouble(0), label);
            return location.isValid() ? location : null;
        } catch (Throwable error) {
            WFLog.d(TAG, "Photon: " + error.getMessage());
            return null;
        } finally {
            if (request != null) request.onConnectionClosed(connection);
            if (connection != null) connection.disconnect();
        }
    }

    private static String buildPhotonLabel(JSONObject properties, String fallback) {
        if (properties == null) return fallback;
        String[] keys = {"name", "street", "city", "state", "country"};
        StringBuilder label = new StringBuilder();
        for (String key : keys) {
            String value = properties.optString(key, "").trim();
            if (value.isEmpty() || label.indexOf(value) >= 0) continue;
            if (label.length() > 0) label.append("، ");
            label.append(value);
        }
        return label.length() == 0 ? fallback : label.toString();
    }

    private static void configure(HttpURLConnection connection) {
        connection.setConnectTimeout(5500);
        connection.setReadTimeout(5500);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "ar,en;q=0.7");
        connection.setRequestProperty("User-Agent", "WolFox-GPS/3.1.1 Android");
    }

    private static String readLimited(InputStream stream, int limit, RequestHandle request)
            throws Exception {
        if (stream == null) return "";
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            StringBuilder out = new StringBuilder();
            char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (isCancelled(request) || Thread.currentThread().isInterrupted()) {
                    throw new java.io.InterruptedIOException("search cancelled");
                }
                if (out.length() + read > limit) throw new IllegalStateException("response too large");
                out.append(buffer, 0, read);
            }
            return out.toString();
        }
    }

    private static Result success(WFLocation location, String source) {
        return new Result(location, source, "");
    }

    private static Result failure(String error) {
        return new Result(null, "", error);
    }

    private static boolean isCancelled(RequestHandle request) {
        return request != null && request.isCancelled();
    }

    /** Thread-safe cancellation state shared by the executor, main callback and HTTP fallback. */
    private static final class RequestHandle
            implements Request, MapLinkParser.ConnectionCancellation {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);
        private final AtomicBoolean delivered = new AtomicBoolean(false);
        private final AtomicReference<Future<?>> work = new AtomicReference<>();
        private final AtomicReference<HttpURLConnection> connection = new AtomicReference<>();
        private volatile Runnable timeout;

        @Override public void cancel() {
            if (!cancelled.compareAndSet(false, true)) return;
            delivered.set(true);
            Runnable value = timeout;
            if (value != null) MAIN.removeCallbacks(value);
            cancelWork();
        }

        @Override public boolean isCancelled() {
            return cancelled.get();
        }

        void setTimeout(Runnable value) {
            timeout = value;
        }

        void setWork(Future<?> value) {
            work.set(value);
            if (isCancelled()) value.cancel(true);
        }

        @Override public void onConnectionOpened(HttpURLConnection value) {
            connection.set(value);
            if (isCancelled()) value.disconnect();
        }

        @Override public void onConnectionClosed(HttpURLConnection value) {
            if (value != null) connection.compareAndSet(value, null);
        }

        void cancelWork() {
            cancelled.set(true);
            Future<?> future = work.get();
            if (future != null) future.cancel(true);
            HttpURLConnection value = connection.getAndSet(null);
            if (value != null) value.disconnect();
        }

        boolean deliver(Result result, Callback callback) {
            Runnable value = timeout;
            if (value != null) MAIN.removeCallbacks(value);
            if (isCancelled() || !delivered.compareAndSet(false, true)) return false;
            if (callback != null) callback.onComplete(result);
            return true;
        }
    }
}
