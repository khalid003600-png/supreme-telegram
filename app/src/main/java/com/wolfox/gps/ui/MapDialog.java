package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.ViewOutlineProvider;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.MapSearchResolver;
import com.wolfox.gps.util.WFLog;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Full map plus a compact reusable map preview for the main menu. */
public final class MapDialog {
    private static final String TAG = "MapDialog";
    private static final int BG = WFDesign.BACKGROUND;
    private static final int CARD = WFDesign.SURFACE;
    private static final int BLUE = WFDesign.PRIMARY;
    private static final int WHITE = WFDesign.TEXT;
    private static volatile WeakReference<Dialog> currentDialogRef = new WeakReference<>(null);

    private MapDialog() {}

    public static final class Preview {
        private final FrameLayout root;
        private WebView webView;
        private MapSearchBridge bridge;

        private Preview(FrameLayout root, WebView webView, MapSearchBridge bridge) {
            this.root = root;
            this.webView = webView;
            this.bridge = bridge;
        }

        public View getView() { return root; }

        public void destroy() {
            WebView value = webView;
            MapSearchBridge valueBridge = bridge;
            webView = null;
            bridge = null;
            if (value == null) return;
            Runnable cleanup = () -> destroyWebView(value, valueBridge);
            if (Looper.myLooper() == Looper.getMainLooper()) cleanup.run();
            else new Handler(Looper.getMainLooper()).post(cleanup);
        }
    }

    /** Creates the rectangular map shown directly after the menu header. */
    public static Preview createPreview(final Activity activity, View.OnClickListener expand) {
        FrameLayout root = new FrameLayout(activity);
        root.setPadding(dp(activity, 1), dp(activity, 1), dp(activity, 1), dp(activity, 1));
        root.setBackground(round(CARD, 15, WFDesign.BORDER, 1));
        root.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        root.setClipToOutline(true);

        final WebView webView = new WebView(activity);
        setupWebView(webView, activity);
        MapSearchBridge bridge = new MapSearchBridge(activity, webView);
        webView.addJavascriptInterface(bridge, "WolFoxSearchBridge");
        root.addView(webView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        Button expandButton = buildButton(activity, WFTheme.icon("⛶") + "  توسيع", BG);
        expandButton.setOnClickListener(expand);
        FrameLayout.LayoutParams expandLp = new FrameLayout.LayoutParams(
                dp(activity, 92), dp(activity, 38), Gravity.TOP | Gravity.START);
        expandLp.setMargins(dp(activity, 8), dp(activity, 8), 0, 0);
        root.addView(expandButton, expandLp);

        WFLocation current = displayLocation(GPSMockManager.getInstance().getLocation());
        final double latitude = current.getLatitude();
        final double longitude = current.getLongitude();
        final String type = WFStorage.getInstance(activity).getMapType();
        webView.post(() -> loadMap(webView, latitude, longitude, type, activity, true, false));
        return new Preview(root, webView, bridge);
    }

    public static void show(final Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        activity.runOnUiThread(() -> buildAndShow(activity, null));
    }

    /** Opens an embedded, view-only map centered on a saved favorite. */
    public static void showFavoritePreview(final Activity activity, final FavoriteLocation favorite) {
        if (activity == null || activity.isFinishing() || favorite == null
                || !WFLocationActions.isValid(favorite.toWFLocation())) {
            if (activity != null) Toast.makeText(activity,
                    "تعذر عرض موقع المفضلة؛ الإحداثيات غير صالحة", Toast.LENGTH_SHORT).show();
            return;
        }
        activity.runOnUiThread(() -> buildAndShow(activity, favorite));
    }

    public static void dismiss() {
        final Dialog dialog = currentDialogRef.get();
        if (dialog == null) return;
        Runnable dismiss = () -> {
            try {
                if (dialog.isShowing()) dialog.dismiss();
            } catch (Throwable error) {
                WFLog.d(TAG, "dialog cleanup: " + error.getMessage());
            }
            if (currentDialogRef.get() == dialog) currentDialogRef = new WeakReference<>(null);
        };
        if (Looper.myLooper() == Looper.getMainLooper()) dismiss.run();
        else new Handler(Looper.getMainLooper()).post(dismiss);
    }

    private static void buildAndShow(final Activity activity, final FavoriteLocation viewingFavorite) {
        if (!isActivityUsable(activity)) return;
        dismiss();
        final boolean favoriteViewOnly = viewingFavorite != null;
        final Dialog dialog = new Dialog(activity, android.R.style.Theme_Material_Dialog);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCancelable(true);
        final Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setGravity(Gravity.CENTER);
        }

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setBackgroundColor(BG);

        LinearLayout header = new LinearLayout(activity);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(activity, 12), dp(activity, 8), dp(activity, 8), dp(activity, 8));

        TextView title = new TextView(activity);
        final String favoriteName = favoriteViewOnly && viewingFavorite.getName() != null
                && !viewingFavorite.getName().trim().isEmpty()
                ? viewingFavorite.getName() : "موقع محفوظ";
        title.setText(favoriteViewOnly
                ? WFTheme.icon("⌖") + "  المفضلة: " + favoriteName
                : WFTheme.icon("⌖") + "  WolFox GPS");
        title.setTextColor(BLUE);
        title.setTextSize(16);
        WFTheme.apply(title, true);
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final String[] mapTypes = {"roadmap", "satellite"};
        final Spinner spinner = new Spinner(activity);
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity,
                android.R.layout.simple_spinner_item,
                new String[]{"مخطط", "قمر صناعي"}) {
            @Override public View getView(int position, View view, ViewGroup parent) {
                View value = super.getView(position, view, parent);
                WFTheme.applyTree(value);
                return value;
            }

            @Override public View getDropDownView(int position, View view, ViewGroup parent) {
                View value = super.getDropDownView(position, view, parent);
                WFTheme.applyTree(value);
                return value;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        LinearLayout.LayoutParams spinnerLp = new LinearLayout.LayoutParams(
                dp(activity, 118), dp(activity, 40));
        spinnerLp.setMargins(dp(activity, 4), 0, dp(activity, 4), 0);
        header.addView(spinner, spinnerLp);
        if (favoriteViewOnly) spinner.setVisibility(View.GONE);

        Button close = WFDesign.closeButton(activity);
        close.setOnClickListener(v -> {
            dialog.dismiss();
            if (!favoriteViewOnly) WolFoxPanel.show(activity);
        });
        header.addView(close, new LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)));
        root.addView(header);

        final WebView webView = new WebView(activity);
        setupWebView(webView, activity);
        final MapSearchBridge bridge = new MapSearchBridge(activity, webView);
        webView.addJavascriptInterface(bridge, "WolFoxSearchBridge");
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = new LinearLayout(activity);
        footer.setOrientation(LinearLayout.HORIZONTAL);
        footer.setPadding(dp(activity, 8), dp(activity, 6), dp(activity, 8), dp(activity, 6));
        footer.setBackgroundColor(CARD);
        final Button confirm = WFDesign.button(activity,
                favoriteViewOnly ? WFTheme.icon("⧉") + "  نسخ الإحداثيات"
                        : WFTheme.icon("✓") + "  تشغيل المحاكاة",
                WFDesign.PRIMARY, WFDesign.TEXT);
        footer.addView(confirm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        Button save = WFDesign.button(activity,
                WFTheme.icon("★") + "  حفظ", WFDesign.ACCENT, WFDesign.BACKGROUND);
        if (favoriteViewOnly) save.setVisibility(View.GONE);
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 0.8f);
        saveLp.setMargins(dp(activity, 6), 0, 0, 0);
        footer.addView(save, saveLp);
        Button center = WFDesign.button(activity,
                favoriteViewOnly ? WFTheme.icon("⌖") + "  موقع المفضلة"
                        : WFTheme.icon("⌖") + "  تمركز",
                WFDesign.SURFACE_RAISED, WFDesign.TEXT);
        LinearLayout.LayoutParams centerLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        centerLp.setMargins(dp(activity, 6), 0, 0, 0);
        footer.addView(center, centerLp);
        root.addView(footer);

        WFDesign.moveCloseToFooter(root);
        WFTheme.applyTree(root);
        dialog.setContentView(root);
        final Handler mainHandler = new Handler(Looper.getMainLooper());
        final Runnable[] pendingConfirm = new Runnable[1];

        WFLocation current = displayLocation(favoriteViewOnly ? viewingFavorite.toWFLocation()
                : GPSMockManager.getInstance().getLocation());
        final double latitude = current.getLatitude();
        final double longitude = current.getLongitude();
        final String selectedType = WFStorage.getInstance(activity).getMapType();
        for (int i = 0; i < mapTypes.length; i++) {
            if (mapTypes[i].equals(selectedType)) {
                spinner.setSelection(i, false);
                break;
            }
        }
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onNothingSelected(AdapterView<?> parent) {}

            @Override public void onItemSelected(AdapterView<?> parent, View view,
                                                 int position, long id) {
                if (!isWebViewLive(webView)) return;
                WFTheme.applyTree(view);
                String type = mapTypes[position];
                WFStorage.getInstance(activity).setMapType(type);
                webView.evaluateJavascript(
                        "if(typeof setMapType==='function') setMapType('" + type + "');", null);
            }
        });

        confirm.setOnClickListener(v -> {
            if (!isWebViewLive(webView)) return;
            if (favoriteViewOnly) {
                // A favorite preview is deliberately copy-only: do not use a potentially
                // changed WebView selection and never enter the start-selection path.
                WFLocationActions.copyCoordinates(activity, viewingFavorite.toWFLocation());
                return;
            }
            webView.evaluateJavascript("getSelectedLocation()", new ValueCallback<String>() {
                @Override public void onReceiveValue(String value) {
                    confirmSelection(value, confirm, activity, dialog, mainHandler, pendingConfirm);
                }
            });
        });
        save.setOnClickListener(v -> {
            if (isWebViewLive(webView)) {
                webView.evaluateJavascript("getSelectedLocation()", value -> saveSelection(value, activity));
            }
        });
        center.setOnClickListener(v -> {
            if (!isWebViewLive(webView)) return;
            WFLocation location = displayLocation(favoriteViewOnly ? viewingFavorite.toWFLocation()
                    : GPSMockManager.getInstance().getLocation());
            webView.evaluateJavascript("if(typeof centerMap==='function') centerMap("
                    + location.getLatitude() + "," + location.getLongitude() + ");", null);
        });

        dialog.setOnDismissListener(ignored -> {
            Runnable pending = pendingConfirm[0];
            if (pending != null) mainHandler.removeCallbacks(pending);
            pendingConfirm[0] = null;
            destroyWebView(webView, bridge);
            if (currentDialogRef.get() == dialog) currentDialogRef = new WeakReference<>(null);
        });
        currentDialogRef = new WeakReference<>(dialog);
        dialog.show();
        if (window != null) {
            window.setLayout((int) (activity.getResources().getDisplayMetrics().widthPixels * 0.96f),
                    (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.90f));
        }
        webView.post(() -> loadMap(webView, latitude, longitude,
                selectedType, activity, false, favoriteViewOnly));
    }

    private static void confirmSelection(String value, final Button button,
                                         final Activity activity, final Dialog dialog,
                                         Handler mainHandler, Runnable[] pendingConfirm) {
        if (!isDialogUsable(dialog, activity) || value == null || "null".equals(value)) return;
        try {
            JSONArray point = new JSONArray(value);
            final double latitude = point.getDouble(0);
            final double longitude = point.getDouble(1);
            final WFLocation location = new WFLocation(latitude, longitude,
                    "اختيار من الخريطة");
            button.setEnabled(false);
            button.setText("جاري تأكيد الحقن…");
            final boolean started = GPSMockManager.getInstance().selectAndStart(location);
            Runnable verification = new Runnable() {
                @Override public void run() {
                pendingConfirm[0] = null;
                if (!isDialogUsable(dialog, activity)) return;
                if (!(started && GPSMockManager.getInstance().verifyRuntimeInjection())) {
                    GPSMockManager.getInstance().stopMocking();
                    button.setEnabled(true);
                    button.setText(com.wolfox.gps.util.WFUiText.text(activity, "simulation_start"));
                    Toast.makeText(activity,
                            "تم حفظ النقطة لكن هوك الموقع لم يستجب؛ أعد فتح التطبيق",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                Toast.makeText(activity,
                        String.format(Locale.US, "تم تشغيل محاكاة الموقع: %.5f, %.5f",
                                latitude, longitude), Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                WolFoxPanel.show(activity);
                }
            };
            pendingConfirm[0] = verification;
            mainHandler.postDelayed(verification, 220L);
        } catch (Throwable error) {
            if (isActivityUsable(activity)) {
                WFLog.e(TAG, "selected point: " + error.getMessage());
                Toast.makeText(activity, "تعذر قراءة النقطة المحددة", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private static void saveSelection(String value, Activity activity) {
        if (!isActivityUsable(activity)) return;
        if (value == null || "null".equals(value)) {
            Toast.makeText(activity, "حدد نقطة على الخريطة أولًا", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray point = new JSONArray(value);
            WFLocation location = new WFLocation(point.getDouble(0), point.getDouble(1),
                    "اختيار من الخريطة");
            if (!WFLocationActions.isValid(location)) {
                Toast.makeText(activity, "إحداثيات النقطة غير صالحة", Toast.LENGTH_SHORT).show();
                return;
            }
            WFLocationActions.promptFavorite(activity, location, location.getLabel());
        } catch (Throwable error) {
            WFLog.e(TAG, "save selected point: " + error.getMessage());
            Toast.makeText(activity, "تعذر حفظ النقطة المحددة", Toast.LENGTH_SHORT).show();
        }
    }

    private static void copySelectedCoordinates(String value, Activity activity, String label) {
        if (!isActivityUsable(activity)) return;
        if (value == null || "null".equals(value)) {
            Toast.makeText(activity, "لم تُحمّل إحداثيات الخريطة بعد", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            JSONArray point = new JSONArray(value);
            WFLocation location = new WFLocation(point.getDouble(0), point.getDouble(1), label);
            WFLocationActions.copyCoordinates(activity, location);
        } catch (Throwable error) {
            WFLog.e(TAG, "copy selected coordinates: " + error.getMessage());
            Toast.makeText(activity, "تعذر نسخ الإحداثيات المحددة", Toast.LENGTH_SHORT).show();
        }
    }

    private static void setupWebView(WebView webView, Activity activity) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setGeolocationEnabled(false);
        // The map is injected with an HTTPS base URL and only loads HTTPS tiles.
        // It never needs access to local files, content providers or file:// bridges.
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setLoadsImagesAutomatically(true);
        settings.setBlockNetworkImage(false);
        settings.setBlockNetworkLoads(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        settings.setDefaultTextEncodingName("utf-8");
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        webView.setBackgroundColor(BG);
        WebView.setWebContentsDebuggingEnabled(false);
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public void onGeolocationPermissionsShowPrompt(
                    String origin, GeolocationPermissions.Callback callback) {
                callback.invoke(origin, false, false);
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                WFLog.d(TAG, "map loaded");
            }

            @Override public void onReceivedError(WebView view, int code,
                                                  String description, String failingUrl) {
                WFLog.e(TAG, "WebView " + code + ": " + description);
            }
        });
    }

    private static void loadMap(WebView webView, double latitude, double longitude,
                                String type, Activity activity, boolean preview, boolean readOnly) {
        if (!isWebViewLive(webView) || !isActivityUsable(activity)) return;
        String sourceUrl = mapSourceUrl(latitude, longitude, type, preview, readOnly);
        try {
            InputStream stream = openMapAsset(MapDialog.class.getClassLoader(), activity.getAssets());
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            try (InputStream input = stream) {
                byte[] buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            }
            String html = new String(output.toByteArray(), StandardCharsets.UTF_8);
            // Keep file/content access disabled and preserve query parameters.
            if (isWebViewLive(webView)) {
                webView.loadDataWithBaseURL(sourceUrl, html, "text/html", "utf-8", null);
            }
        } catch (Throwable error) {
            WFLog.e(TAG, "map asset: " + error.getMessage());
            if (isWebViewLive(webView)) {
                webView.loadDataWithBaseURL("https://app.local/",
                        fallbackMap(latitude, longitude), "text/html", "utf-8", null);
            }
        }
    }

    /**
     * LSPatch can load an embedded module through a cache-APK class loader, separate from host
     * assets. Prefer that module-local resource; only then use the host asset fallback.
     */
    static InputStream openMapAsset(ClassLoader moduleLoader, AssetManager hostAssets)
            throws IOException {
        InputStream moduleAsset = moduleLoader == null ? null
                : moduleLoader.getResourceAsStream("assets/wolfox_map.html");
        if (moduleAsset != null) return moduleAsset;
        if (hostAssets == null) throw new IOException("module map asset missing");
        return hostAssets.open("wolfox_map.html");
    }

    /** Builds the local WebView source URL without rounding model coordinates. */
    static String mapSourceUrl(double latitude, double longitude, String type,
                               boolean preview, boolean readOnly) {
        String safeType = "satellite".equals(type) ? "satellite" : "roadmap";
        return "https://app.local/map.html?lat=" + Double.toString(latitude)
                + "&lng=" + Double.toString(longitude) + "&type=" + safeType
                + (preview ? "&preview=1" : "")
                + (readOnly ? "&readonly=1" : "");
    }

    private static WFLocation displayLocation(WFLocation location) {
        return WFLocationActions.isValid(location) ? location
                : new WFLocation(24.7136d, 46.6753d);
    }

    private static String fallbackMap(double latitude, double longitude) {
        return "<!doctype html><html dir='rtl'><head><meta name='viewport' "
                + "content='width=device-width,initial-scale=1'><style>html,body{height:100%;margin:0;"
                + "background:#071426;color:#fff;font-family:sans-serif;display:grid;place-items:center;"
                + "text-align:center}.card{padding:18px;border:1px solid #2787f5;border-radius:16px;"
                + "background:#0d203b}.coord{direction:ltr;margin-top:8px;color:#9fc7ff}</style></head>"
                + "<body><div class='card'><b>الخريطة غير متاحة</b><div class='coord'>"
                + latitude + ", " + longitude + "</div></div><script>var selectedLat=" + latitude
                + ",selectedLng=" + longitude + ";function getSelectedLocation(){return [selectedLat,selectedLng]}"
                + "function centerMap(a,b){selectedLat=a;selectedLng=b}function setMapType(t){}</script>"
                + "</body></html>";
    }

    private static void destroyWebView(WebView webView, MapSearchBridge bridge) {
        if (webView == null) return;
        try {
            if (bridge != null) bridge.destroy();
            webView.stopLoading();
            // onPause is per-view. Avoid WebView.pauseTimers(), which pauses unrelated host WebViews.
            webView.onPause();
            webView.loadUrl("about:blank");
            webView.removeJavascriptInterface("WolFoxSearchBridge");
            webView.setWebChromeClient(null);
            webView.setWebViewClient(null);
            ViewParent parent = webView.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup) parent).removeView(webView);
            webView.clearHistory();
            webView.removeAllViews();
            webView.destroy();
        } catch (Throwable error) {
            WFLog.d(TAG, "WebView cleanup: " + error.getMessage());
        }
    }

    private static Button buildButton(Context context, String value, int background) {
        return WFDesign.button(context, value, background, WHITE);
    }

    private static GradientDrawable round(int fill, int radiusDp, int stroke, int strokeDp) {
        return WFDesign.rounded(fill, radiusDp, stroke, strokeDp);
    }

    private static final class MapSearchBridge {
        private final WeakReference<Activity> activityRef;
        private final WeakReference<WebView> webViewRef;
        private MapSearchResolver.Request activeRequest;
        private long requestGeneration;
        private boolean destroyed;

        private MapSearchBridge(Activity activity, WebView webView) {
            activityRef = new WeakReference<>(activity);
            webViewRef = new WeakReference<>(webView);
        }

        @JavascriptInterface public void resolve(String query) {
            Activity activity = activityRef.get();
            WebView webView = webViewRef.get();
            if (!isActivityUsable(activity) || !isWebViewLive(webView)) return;
            final long generation;
            synchronized (this) {
                if (destroyed) return;
                if (activeRequest != null) activeRequest.cancel();
                activeRequest = null;
                generation = ++requestGeneration;
            }
            final MapSearchResolver.Request[] issued = new MapSearchResolver.Request[1];
            issued[0] = MapSearchResolver.resolve(activity, query, result -> {
                synchronized (MapSearchBridge.this) {
                    if (destroyed || generation != requestGeneration) return;
                    activeRequest = null;
                }
                WebView target = webViewRef.get();
                Activity owner = activityRef.get();
                if (!isActivityUsable(owner) || !isWebViewLive(target)) return;
                try {
                    if (!result.isSuccess()) {
                        target.evaluateJavascript("window.onNativeSearchError("
                                + JSONObject.quote(result.getError()) + ");", null);
                        return;
                    }
                    WFLocation location = result.getLocation();
                    target.evaluateJavascript("window.onNativeSearchResult("
                            + location.getLatitude() + "," + location.getLongitude() + ","
                            + JSONObject.quote(location.getLabel() == null ? "" : location.getLabel())
                            + "," + JSONObject.quote(result.getSource()) + ");", null);
                } catch (Throwable error) {
                    WFLog.e(TAG, "map search callback: " + error.getMessage());
                }
            });
            synchronized (this) {
                if (destroyed || generation != requestGeneration) issued[0].cancel();
                else activeRequest = issued[0];
            }
        }

        void destroy() {
            synchronized (this) {
                destroyed = true;
                requestGeneration++;
                if (activeRequest != null) activeRequest.cancel();
                activeRequest = null;
            }
        }
    }

    private static boolean isActivityUsable(Activity activity) {
        return activity != null && !activity.isFinishing() && !activity.isDestroyed();
    }

    private static boolean isDialogUsable(Dialog dialog, Activity activity) {
        return isActivityUsable(activity) && dialog != null && dialog.isShowing();
    }

    private static boolean isWebViewLive(WebView webView) {
        return webView != null && webView.getParent() != null;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
