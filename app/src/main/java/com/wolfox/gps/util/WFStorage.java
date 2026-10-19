package com.wolfox.gps.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Process;

import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.model.HistoryEntry;
import com.wolfox.gps.model.WFLocation;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * WFStorage — طبقة التخزين الموحدة للموديول
 * يستخدم SharedPreferences بصيغة JSON (بدون SQLite لأننا داخل موديول)
 */
public class WFStorage {

    private static final String PREF_NAME          = "wolfox_gps_prefs_variant5";
    private static final String KEY_FAVORITES      = "favorites";
    private static final String KEY_HISTORY        = "history";
    private static final String KEY_LAST_LAT       = "last_lat";
    private static final String KEY_LAST_LNG       = "last_lng";
    private static final String KEY_LAST_LAT_BITS  = "last_lat_bits";
    private static final String KEY_LAST_LNG_BITS  = "last_lng_bits";
    private static final String KEY_LAST_LABEL     = "last_label";
    private static final String KEY_LAST_ALT       = "last_alt";
    private static final String KEY_LAST_ACCURACY  = "last_accuracy";
    private static final String KEY_LAST_SPEED     = "last_speed";
    private static final String KEY_LAST_BEARING   = "last_bearing";
    private static final String KEY_IS_MOCKING     = "is_mocking";
    private static final String KEY_MAP_TYPE       = "map_type";
    private static final String KEY_LICENSE_STATUS = "license_status";
    private static final String KEY_LICENSE_CODE   = "license_code";
    private static final String KEY_LICENSE_EXPIRY = "license_expiry_epoch";
    private static final String KEY_LICENSE_EXPIRY_TEXT = "license_expiry_text";
    private static final String KEY_LICENSE_START = "license_start_epoch";
    private static final String KEY_LICENSE_DURATION_DAYS = "license_duration_days";
    private static final String KEY_LICENSE_CHECKED_AT = "license_checked_at";
    private static final String KEY_INSTALLATION_ID = "installation_id";
    private static final String KEY_BUBBLE_VISIBLE = "bubble_visible";
    private static final String KEY_BUBBLE_SIZE = "bubble_size_dp";
    private static final String KEY_BUBBLE_OPACITY = "bubble_opacity";
    private static final String KEY_BUBBLE_X = "bubble_x_fraction";
    private static final String KEY_BUBBLE_Y = "bubble_y_fraction";
    private static final String KEY_HOOK_PID = "hook_runtime_pid";
    private static final String KEY_HOOK_VERSION = "hook_runtime_version";
    private static final int    MAX_HISTORY        = 200;

    private static WFStorage instance;
    private final SharedPreferences prefs;

    private WFStorage(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized WFStorage getInstance(Context context) {
        if (instance == null) {
            instance = new WFStorage(context);
        }
        return instance;
    }

    // ─── الموقع الأخير ────────────────────────────────────────────────────────

    /**
     * apply() updates this process's SharedPreferences memory before it returns, while the
     * filesystem write happens off the UI path. Callers can therefore reload this value right
     * after saving without turning coordinate selection into a synchronous disk stall.
     */
    public void saveLastLocation(WFLocation loc) {
        if (!isStorableLocation(loc)) {
            WFLog.w("WFStorage", "رفض حفظ موقع أخير غير صالح");
            return;
        }
        prefs.edit()
             .putLong(KEY_LAST_LAT_BITS, Double.doubleToRawLongBits(loc.getLatitude()))
             .putLong(KEY_LAST_LNG_BITS, Double.doubleToRawLongBits(loc.getLongitude()))
             .putString(KEY_LAST_LABEL, loc.getLabel() == null ? "" : loc.getLabel())
             .putLong(KEY_LAST_ALT, Double.doubleToRawLongBits(loc.getAltitude()))
             .putFloat(KEY_LAST_ACCURACY, loc.getAccuracy())
             .putFloat(KEY_LAST_SPEED, loc.getSpeed())
             .putFloat(KEY_LAST_BEARING, loc.getBearing())
             .apply();
    }

    public WFLocation getLastLocation() {
        WFLocation loc = exactLastLocation();
        if (loc != null) return withLastLocationDetails(loc);

        loc = legacyLastLocation();
        if (loc == null) return null;
        loc = withLastLocationDetails(loc);
        // Preserve the old float values as a read fallback, but migrate a valid old
        // selection immediately so subsequent reads retain all double precision.
        saveLastLocation(loc);
        return loc;
    }

    private WFLocation exactLastLocation() {
        Long latitudeBits = rawLong(KEY_LAST_LAT_BITS);
        Long longitudeBits = rawLong(KEY_LAST_LNG_BITS);
        if (latitudeBits == null || longitudeBits == null) return null;
        WFLocation loc = new WFLocation(Double.longBitsToDouble(latitudeBits),
                Double.longBitsToDouble(longitudeBits));
        return isStorableLocation(loc) ? loc : null;
    }

    private WFLocation legacyLastLocation() {
        Float latitude = rawFloat(KEY_LAST_LAT);
        Float longitude = rawFloat(KEY_LAST_LNG);
        if (latitude == null || longitude == null) return null;
        WFLocation loc = new WFLocation(latitude.doubleValue(), longitude.doubleValue());
        return isStorableLocation(loc) ? loc : null;
    }

    private WFLocation withLastLocationDetails(WFLocation loc) {
        double altitude = rawDouble(KEY_LAST_ALT, 0d);
        loc.setAltitude(isFinite(altitude) ? altitude : 0d);
        loc.setAccuracy(rawFloat(KEY_LAST_ACCURACY, 3f));
        loc.setSpeed(rawFloat(KEY_LAST_SPEED, 0f));
        loc.setBearing(rawFloat(KEY_LAST_BEARING, 0f));
        loc.setLabel(rawString(KEY_LAST_LABEL, ""));
        return loc;
    }

    private Long rawLong(String key) {
        Object value = rawValue(key);
        return value instanceof Long ? (Long) value : null;
    }

    private Float rawFloat(String key) {
        Object value = rawValue(key);
        if (!(value instanceof Float)) return null;
        float result = (Float) value;
        return Float.isNaN(result) || Float.isInfinite(result) ? null : result;
    }

    private float rawFloat(String key, float fallback) {
        Float value = rawFloat(key);
        return value == null ? fallback : value;
    }

    private double rawDouble(String key, double fallback) {
        Long bits = rawLong(key);
        return bits == null ? fallback : Double.longBitsToDouble(bits);
    }

    private String rawString(String key, String fallback) {
        Object value = rawValue(key);
        return value instanceof String ? (String) value : fallback;
    }

    private Object rawValue(String key) {
        try {
            Map<String, ?> all = prefs.getAll();
            return all == null ? null : all.get(key);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isStorableLocation(WFLocation loc) {
        return loc != null && loc.isValid();
    }

    private static boolean isFinite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    // ─── حالة التزييف ─────────────────────────────────────────────────────────

    public void setMocking(boolean active) {
        // apply() publishes the new value to the shared in-memory preferences map before return.
        // The UI and an embedded hook in this process therefore observe the guard immediately,
        // without blocking a touch/callback thread on a disk fsync.
        prefs.edit().putBoolean(KEY_IS_MOCKING, active).apply();
    }

    public boolean isMocking() {
        return prefs.getBoolean(KEY_IS_MOCKING, false);
    }

    /** Records that the embedded hook completed startup in this exact process. */
    public void markHookRuntimeActive(int versionCode) {
        prefs.edit()
                .putInt(KEY_HOOK_PID, Process.myPid())
                .putInt(KEY_HOOK_VERSION, versionCode)
                .apply();
    }

    /** Prevents a stale marker from a previous process or module version being accepted. */
    public boolean isHookRuntimeActive(int versionCode) {
        return prefs.getInt(KEY_HOOK_PID, -1) == Process.myPid()
                && prefs.getInt(KEY_HOOK_VERSION, -1) == versionCode;
    }

    // ─── نوع الخريطة ──────────────────────────────────────────────────────────

    public void setMapType(String type) {
        prefs.edit().putString(KEY_MAP_TYPE, type).apply();
    }

    public String getMapType() {
        return prefs.getString(KEY_MAP_TYPE, "roadmap");
    }

    // ─── حالة الترخيص ─────────────────────────────────────────────────────────

    public void setLicenseStatus(String status) {
        prefs.edit().putString(KEY_LICENSE_STATUS, status).apply();
    }

    public String getLicenseStatus() {
        return prefs.getString(KEY_LICENSE_STATUS, "");
    }

    public boolean isLicenseActive() {
        if (!"Active".equalsIgnoreCase(getLicenseStatus())) return false;
        long expiry = getLicenseExpiryEpoch();
        if (expiry > 0L && expiry <= System.currentTimeMillis()) {
            prefs.edit().putString(KEY_LICENSE_STATUS, "Expired").apply();
            return false;
        }
        return true;
    }

    public void saveLicense(String code, String status, long expiryEpoch, String expiryText) {
        prefs.edit()
                .putString(KEY_LICENSE_CODE, code == null ? "" : code)
                .putString(KEY_LICENSE_STATUS, status == null ? "" : status)
                .putLong(KEY_LICENSE_EXPIRY, Math.max(0L, expiryEpoch))
                .putString(KEY_LICENSE_EXPIRY_TEXT, expiryText == null ? "" : expiryText)
                .putLong(KEY_LICENSE_CHECKED_AT, System.currentTimeMillis())
                .apply();
    }

    public String getLicenseCode() {
        return prefs.getString(KEY_LICENSE_CODE, "");
    }

    public long getLicenseExpiryEpoch() {
        return prefs.getLong(KEY_LICENSE_EXPIRY, 0L);
    }

    public String getLicenseExpiryText() {
        return prefs.getString(KEY_LICENSE_EXPIRY_TEXT, "");
    }

    public void saveLicenseDetails(long startEpoch, int durationDays) {
        SharedPreferences.Editor editor = prefs.edit();
        if (startEpoch > 0L) editor.putLong(KEY_LICENSE_START, startEpoch);
        if (durationDays > 0) editor.putInt(KEY_LICENSE_DURATION_DAYS, durationDays);
        editor.apply();
    }

    public void clearLicenseDetails() {
        prefs.edit().remove(KEY_LICENSE_START).remove(KEY_LICENSE_DURATION_DAYS).apply();
    }

    public long getLicenseStartEpoch() {
        return prefs.getLong(KEY_LICENSE_START, 0L);
    }

    public int getLicenseDurationDays() {
        return prefs.getInt(KEY_LICENSE_DURATION_DAYS, 0);
    }

    public String getLicenseDetailsText() {
        long start = getLicenseStartEpoch();
        long end = getLicenseExpiryEpoch();
        int duration = getLicenseDurationDays();
        if (duration <= 0 && start > 0L && end > start) {
            duration = (int) Math.max(1L, (end - start + DAY_MS - 1L) / DAY_MS);
        }
        String endText = end > 0L ? formatLicenseDate(end) : getLicenseExpiryText();
        if (endText == null || endText.trim().isEmpty()) endText = "غير متاح";
        return "الحالة: " + (getLicenseStatus().isEmpty() ? "غير مفعل" : getLicenseStatus())
                + "\nبداية الاشتراك: " + (start > 0L ? formatLicenseDate(start) : "غير متاح")
                + "\nنهاية / انتهاء الاشتراك: " + endText
                + "\nمدة الاشتراك: " + (duration > 0 ? duration + " يوم" : "غير متاح")
                + "\nالأيام المتبقية: " + remainingDaysText(end);
    }

    public String getLicenseSummaryText() {
        long end = getLicenseExpiryEpoch();
        String endText = end > 0L ? formatLicenseDate(end) : getLicenseExpiryText();
        if (endText == null || endText.trim().isEmpty()) endText = "غير متاح";
        return "ينتهي: " + endText + " • المتبقي: " + remainingDaysText(end);
    }

    public long getLicenseCheckedAt() {
        return prefs.getLong(KEY_LICENSE_CHECKED_AT, 0L);
    }

    private static final long DAY_MS = 86400000L;

    private static String formatLicenseDate(long epoch) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                .format(new Date(epoch));
    }

    private static String remainingDaysText(long endEpoch) {
        if (endEpoch <= 0L) return "غير متاح";
        long difference = endEpoch - System.currentTimeMillis();
        if (difference <= 0L) {
            long expiredDays = (System.currentTimeMillis() - endEpoch) / DAY_MS;
            return expiredDays == 0L ? "0 يوم (منتهي)" : "منتهي منذ " + expiredDays + " يوم";
        }
        long days = (difference + DAY_MS - 1L) / DAY_MS;
        return days + " يوم";
    }

    public void clearLicense() {
        prefs.edit()
                .remove(KEY_LICENSE_CODE)
                .remove(KEY_LICENSE_STATUS)
                .remove(KEY_LICENSE_EXPIRY)
                .remove(KEY_LICENSE_EXPIRY_TEXT)
                .remove(KEY_LICENSE_START)
                .remove(KEY_LICENSE_DURATION_DAYS)
                .remove(KEY_LICENSE_CHECKED_AT)
                .putBoolean(KEY_IS_MOCKING, false)
                .apply();
    }

    public synchronized String getOrCreateInstallationId() {
        String id = prefs.getString(KEY_INSTALLATION_ID, "");
        if (id != null && !id.isEmpty()) return id;
        id = UUID.randomUUID().toString();
        // Keep the identifier stable for a second caller in this process immediately; durability
        // may complete asynchronously because request construction already owns this value.
        prefs.edit().putString(KEY_INSTALLATION_ID, id).apply();
        return id;
    }

    public void setBubbleVisible(boolean visible) {
        // apply() makes the explicit request observable to the next lifecycle/render callback
        // immediately, but never forces the main thread to wait for a disk write.
        prefs.edit().putBoolean(KEY_BUBBLE_VISIBLE, visible).apply();
    }

    public boolean isBubbleVisible() {
        // Absence means first authorized host open and deliberately reveals the in-Activity
        // control. Existing explicit false records remain false; retired PID/start metadata is
        // intentionally ignored so explicit visibility persists across a process restart/update.
        return prefs.getBoolean(KEY_BUBBLE_VISIBLE, true);
    }

    public void setBubbleSizeDp(int value) {
        prefs.edit().putInt(KEY_BUBBLE_SIZE, Math.max(42, Math.min(88, value))).apply();
    }

    public int getBubbleSizeDp() {
        return prefs.getInt(KEY_BUBBLE_SIZE, 56);
    }

    public void setBubbleOpacity(float value) {
        prefs.edit().putFloat(KEY_BUBBLE_OPACITY, Math.max(0.35f, Math.min(1f, value))).apply();
    }

    public float getBubbleOpacity() {
        return prefs.getFloat(KEY_BUBBLE_OPACITY, 0.94f);
    }

    public void saveBubblePosition(float xFraction, float yFraction) {
        prefs.edit()
                .putFloat(KEY_BUBBLE_X, Math.max(0f, Math.min(1f, xFraction)))
                .putFloat(KEY_BUBBLE_Y, Math.max(0f, Math.min(1f, yFraction)))
                .apply();
    }

    public float getBubbleXFraction() { return prefs.getFloat(KEY_BUBBLE_X, 0.96f); }
    public float getBubbleYFraction() { return prefs.getFloat(KEY_BUBBLE_Y, 0.50f); }

    // ─── المفضلة ──────────────────────────────────────────────────────────────

    public List<FavoriteLocation> getFavorites() {
        List<FavoriteLocation> list = new ArrayList<>();
        String json = prefs.getString(KEY_FAVORITES, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(FavoriteLocation.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            WFLog.e("WFStorage", "getFavorites error: " + e.getMessage());
        }
        return list;
    }

    public void saveFavorites(List<FavoriteLocation> list) {
        JSONArray arr = new JSONArray();
        for (FavoriteLocation f : list) {
            try { arr.put(f.toJson()); } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply();
    }

    public void addFavorite(FavoriteLocation fav) {
        if (fav == null) return;
        List<FavoriteLocation> list = getFavorites();
        long nextId = System.currentTimeMillis();
        for (FavoriteLocation current : list) {
            nextId = Math.max(nextId, current.getId() + 1L);
        }
        fav.setId(nextId);
        list.add(fav);
        saveFavorites(list);
    }

    /** Adds a favorite unless the same name and coordinates are already stored. */
    public synchronized boolean addFavoriteIfAbsent(FavoriteLocation fav) {
        if (fav == null || Double.isNaN(fav.getLatitude()) || Double.isInfinite(fav.getLatitude())
                || Double.isNaN(fav.getLongitude()) || Double.isInfinite(fav.getLongitude())
                || fav.getLatitude() < -90d || fav.getLatitude() > 90d
                || fav.getLongitude() < -180d || fav.getLongitude() > 180d) return false;
        String name = fav.getName() == null ? "" : fav.getName().trim();
        for (FavoriteLocation current : getFavorites()) {
            String currentName = current.getName() == null ? "" : current.getName().trim();
            if (currentName.equalsIgnoreCase(name)
                    && Math.abs(current.getLatitude() - fav.getLatitude()) < 0.000001d
                    && Math.abs(current.getLongitude() - fav.getLongitude()) < 0.000001d) {
                return false;
            }
        }
        addFavorite(fav);
        return true;
    }

    public void updateFavorite(FavoriteLocation fav) {
        List<FavoriteLocation> list = getFavorites();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == fav.getId()) {
                fav.setUpdatedAt(System.currentTimeMillis());
                list.set(i, fav);
                break;
            }
        }
        saveFavorites(list);
    }

    public void deleteFavorite(long id) {
        List<FavoriteLocation> list = getFavorites();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).getId() == id) {
                list.remove(i);
                break;
            }
        }
        saveFavorites(list);
    }

    // ─── السجل ────────────────────────────────────────────────────────────────

    public List<HistoryEntry> getHistory() {
        List<HistoryEntry> list = new ArrayList<>();
        String json = prefs.getString(KEY_HISTORY, "[]");
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(HistoryEntry.fromJson(arr.getJSONObject(i)));
            }
        } catch (JSONException e) {
            WFLog.e("WFStorage", "getHistory error: " + e.getMessage());
        }
        return list;
    }

    public void addHistory(HistoryEntry entry) {
        List<HistoryEntry> list = getHistory();
        entry.setId(System.currentTimeMillis());
        list.add(0, entry); // الأحدث في الأعلى
        if (list.size() > MAX_HISTORY) {
            list = list.subList(0, MAX_HISTORY);
        }
        JSONArray arr = new JSONArray();
        for (HistoryEntry h : list) {
            try { arr.put(h.toJson()); } catch (JSONException ignored) {}
        }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
    }

    public void clearHistory() {
        prefs.edit().putString(KEY_HISTORY, "[]").apply();
    }

    // ─── تصدير / استيراد المفضلة ──────────────────────────────────────────────

    public String exportFavoritesJson() {
        return prefs.getString(KEY_FAVORITES, "[]");
    }

    public boolean importFavoritesJson(String json) {
        try {
            JSONArray arr = new JSONArray(json); // للتحقق من الصلاحية
            prefs.edit().putString(KEY_FAVORITES, arr.toString()).apply();
            return true;
        } catch (JSONException e) {
            WFLog.e("WFStorage", "importFavorites error: " + e.getMessage());
            return false;
        }
    }
}
