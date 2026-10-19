package com.wolfox.gps.compat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Instrumentation;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanRecord;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.wolfox.gps.ui.WFDesign;
import com.wolfox.gps.util.WFTheme;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** User-initiated local photo preview and bounded BLE discovery; no virtual-camera or device spoofing. */
public final class FeatureIntegration {
    private static final int IMAGE_PICK_REQUEST = 0x6F21;
    private static final int CAMERA_CAPTURE_REQUEST = 0x6F22;
    private static final int BLUETOOTH_ENABLE_REQUEST = 0x6F23;
    private static final int BLUETOOTH_PERMISSION_REQUEST = 0x6F24;
    private static final long BLE_SCAN_DURATION_MS = 10_000L;
    private static final int MAX_DISCOVERED_DEVICES = 20;
    private static final int PREVIEW_MAX_SIDE_PX = 720;

    private static volatile Uri selectedImage;
    private static volatile Bitmap capturedThumbnail;
    private static boolean resultHooked;
    private static BluetoothLeScanner scanner;
    private static ScanCallback callback;
    private static Runnable scanTimeout;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ExecutorService IMAGE_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final Map<String, String> discovered = new LinkedHashMap<>();
    private static final Object DISCOVERED_LOCK = new Object();
    private static WeakReference<ImageView> cameraPreview = new WeakReference<>(null);
    private static WeakReference<TextView> cameraStatus = new WeakReference<>(null);
    private static WeakReference<TextView> bluetoothStatus = new WeakReference<>(null);
    private static WeakReference<TextView> bluetoothResults = new WeakReference<>(null);

    private FeatureIntegration() {}

    public static synchronized void init(Context context) {
        if (resultHooked) return;
        try {
            XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnActivityResult",
                    new XC_MethodHook() {
                        @Override protected void beforeHookedMethod(MethodHookParam p) {
                            Object[] args = p.args;
                            if (args == null || args.length < 4) return;
                            int offset = args.length - 3;
                            if (args[offset] instanceof Integer
                                    && args[offset + 1] instanceof Integer
                                    && args[offset + 2] instanceof Intent) {
                                onImageSelected((Integer) args[offset], (Integer) args[offset + 1],
                                        (Intent) args[offset + 2]);
                            }
                        }
                    });
            resultHooked = true;
        } catch (Throwable ignored) {
            // Photo picker and BLE controls remain available even without the result observer.
        }
    }

    /** Compatibility entry point; the panel now exposes camera and Bluetooth separately. */
    public static void show(Activity activity) {
        showBluetooth(activity);
    }

    public static void showCamera(Activity activity) {
        if (!usable(activity)) return;
        LinearLayout root = root(activity);
        TextView note = WFDesign.text(activity,
                "اختر صورة أو التقط صورة بكاميرا النظام. المعاينة محلية داخل WolFox فقط ولا تستبدل كاميرا التطبيقات الأخرى.",
                13, WFDesign.MUTED, false);
        note.setPadding(0, 0, 0, WFDesign.dp(activity, 10));
        root.addView(note, fullWrap());

        ImageView preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        preview.setContentDescription("معاينة الصورة المحلية المختارة");
        preview.setBackground(WFDesign.rounded(WFDesign.SURFACE, 14));
        // Gallery shortcut floats over the local camera preview while this panel is open.
        FrameLayout previewContainer = new FrameLayout(activity);
        previewContainer.addView(preview, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Button galleryOverlay = WFDesign.button(activity, "▣  الاستديو",
                WFDesign.BUTTON_CAMERA, WFDesign.TEXT);
        galleryOverlay.setContentDescription("اختيار صورة من الاستديو أثناء معاينة الكاميرا");
        FrameLayout.LayoutParams galleryLp = new FrameLayout.LayoutParams(
                WFDesign.dp(activity, 124), WFDesign.dp(activity, 48),
                Gravity.BOTTOM | Gravity.END);
        galleryLp.setMargins(WFDesign.dp(activity, 8), WFDesign.dp(activity, 8),
                WFDesign.dp(activity, 8), WFDesign.dp(activity, 8));
        previewContainer.addView(galleryOverlay, galleryLp);
        LinearLayout.LayoutParams previewLp = full(activity, 200);
        previewLp.setMargins(0, 0, 0, WFDesign.dp(activity, 8));
        root.addView(previewContainer, previewLp);

        TextView status = WFDesign.text(activity, "لم يتم اختيار صورة بعد", 13,
                WFDesign.MUTED, false);
        status.setGravity(android.view.Gravity.CENTER);
        root.addView(status, fullWrap());

        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.HORIZONTAL);
        Button capture = WFDesign.button(activity, "التقاط بالكاميرا", WFDesign.BUTTON_CAMERA,
                WFDesign.TEXT);
        choices.addView(capture, weighted(activity, 1f, 0));
        LinearLayout.LayoutParams choicesLp = fullWrap();
        choicesLp.setMargins(0, WFDesign.dp(activity, 10), 0, WFDesign.dp(activity, 6));
        root.addView(choices, choicesLp);

        Button clear = WFDesign.button(activity, "مسح المعاينة", WFDesign.BUTTON_CLEAR,
                WFDesign.BACKGROUND);
        root.addView(clear, fullWrap());
        WFTheme.applyTree(root);

        cameraPreview = new WeakReference<>(preview);
        cameraStatus = new WeakReference<>(status);
        refreshCameraPreview();
        capture.setOnClickListener(view -> launchSystemCamera(activity));
        galleryOverlay.setOnClickListener(view -> launchImagePicker(activity));
        clear.setOnClickListener(view -> {
            selectedImage = null;
            capturedThumbnail = null;
            refreshCameraPreview();
        });
        showDialog(activity, "الكاميرا والصور", root, null);
    }

    public static void showBluetooth(Activity activity) {
        if (!usable(activity)) return;
        stopScan();
        synchronized (DISCOVERED_LOCK) { discovered.clear(); }

        LinearLayout root = root(activity);
        TextView note = WFDesign.text(activity,
                "يمسح WolFox أجهزة BLE القريبة لمدة 10 ثوانٍ عند طلبك، ويعرض الاسم وقوة الإشارة فقط دون حفظ معرّفات الأجهزة.",
                13, WFDesign.MUTED, false);
        note.setPadding(0, 0, 0, WFDesign.dp(activity, 10));
        root.addView(note, fullWrap());

        TextView status = WFDesign.text(activity, initialBluetoothStatus(activity), 13,
                WFDesign.TEXT, true);
        status.setPadding(0, 0, 0, WFDesign.dp(activity, 8));
        root.addView(status, fullWrap());

        TextView results = WFDesign.text(activity, "لم يبدأ المسح بعد", 13,
                WFDesign.MUTED, false);
        results.setTextDirection(View.TEXT_DIRECTION_RTL);
        ScrollView resultsScroll = new ScrollView(activity);
        resultsScroll.setFillViewport(false);
        resultsScroll.addView(results, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(resultsScroll, full(activity, 150));

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button start = WFDesign.button(activity, "فحص Bluetooth BLE", WFDesign.BUTTON_BLUETOOTH,
                WFDesign.TEXT);
        Button stop = WFDesign.button(activity, "إيقاف الفحص", WFDesign.BUTTON_STOP,
                WFDesign.TEXT);
        actions.addView(start, weighted(activity, 1f, 6));
        actions.addView(stop, weighted(activity, 1f, 0));
        LinearLayout.LayoutParams actionsLp = fullWrap();
        actionsLp.setMargins(0, WFDesign.dp(activity, 10), 0, WFDesign.dp(activity, 6));
        root.addView(actions, actionsLp);

        Button settings = WFDesign.button(activity, "إعدادات Bluetooth", WFDesign.BUTTON_SETTINGS,
                WFDesign.TEXT);
        root.addView(settings, fullWrap());
        WFTheme.applyTree(root);

        bluetoothStatus = new WeakReference<>(status);
        bluetoothResults = new WeakReference<>(results);
        start.setOnClickListener(view -> startBluetoothScan(activity, status, results));
        stop.setOnClickListener(view -> {
            stopScan();
            status.setText("تم إيقاف المسح");
            renderBluetoothResults();
        });
        settings.setOnClickListener(view -> openBluetoothSettings(activity));
        showDialog(activity, "Bluetooth والأجهزة القريبة", root, FeatureIntegration::stopScan);
    }

    /** Retained compatibility entry points; neither method exposes device identifiers or credentials. */
    public static void toast(String message) { }
    public static void refresh() { }

    private static void launchImagePicker(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        try {
            activity.startActivityForResult(Intent.createChooser(intent, "اختيار صورة"),
                    IMAGE_PICK_REQUEST);
        } catch (Exception error) {
            Toast.makeText(activity, "تعذر فتح منتقي الصور", Toast.LENGTH_SHORT).show();
        }
    }

    private static void launchSystemCamera(Activity activity) {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        try {
            // The system camera handles capture; WolFox receives only the user's returned preview.
            activity.startActivityForResult(intent, CAMERA_CAPTURE_REQUEST);
        } catch (Exception error) {
            TextView currentStatus = cameraStatus.get();
            if (currentStatus != null) currentStatus.setText("تعذر فتح كاميرا النظام؛ تحقق من توفر تطبيق كاميرا");
            Toast.makeText(activity, "تعذر فتح كاميرا النظام", Toast.LENGTH_SHORT).show();
        }
    }

    public static void onImageSelected(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) return;
        if (data == null) {
            TextView status = cameraStatus.get();
            if (status != null) status.setText("لم تُرجع الكاميرا صورة للمعاينة");
            return;
        }
        if (requestCode == IMAGE_PICK_REQUEST) {
            selectedImage = data.getData();
            capturedThumbnail = null;
        } else if (requestCode == CAMERA_CAPTURE_REQUEST) {
            selectedImage = data.getData();
            Bitmap thumbnail = null;
            try {
                Object value = data.getExtras() == null ? null : data.getExtras().get("data");
                if (value instanceof Bitmap) thumbnail = (Bitmap) value;
            } catch (RuntimeException ignored) { }
            capturedThumbnail = thumbnail;
        } else {
            return;
        }
        refreshCameraPreview();
    }

    private static void refreshCameraPreview() {
        TextView status = cameraStatus.get();
        if (status != null) {
            boolean selected = selectedImage != null || capturedThumbnail != null;
            status.post(() -> status.setText(selected
                    ? "الصورة جاهزة للمعاينة المحلية داخل WolFox"
                    : "لم يتم اختيار صورة بعد"));
        }
        ImageView image = cameraPreview.get();
        if (image == null) return;
        Bitmap thumbnail = capturedThumbnail;
        Uri uri = selectedImage;
        if (thumbnail != null) {
            image.post(() -> image.setImageBitmap(thumbnail));
        } else if (uri != null) {
            decodePreviewAsync(image, uri);
        } else {
            image.post(() -> image.setImageDrawable(null));
        }
    }

    private static void decodePreviewAsync(ImageView image, Uri uri) {
        WeakReference<ImageView> reference = new WeakReference<>(image);
        Context context = image.getContext().getApplicationContext();
        if (context == null) context = image.getContext();
        final Context appContext = context;
        image.setImageDrawable(null);
        IMAGE_EXECUTOR.execute(() -> {
            Bitmap bitmap = decodeSampled(appContext, uri, PREVIEW_MAX_SIDE_PX);
            ImageView target = reference.get();
            if (target == null) return;
            if (bitmap == null) {
                target.post(() -> {
                    TextView currentStatus = cameraStatus.get();
                    if (currentStatus != null) currentStatus.setText("تعذر عرض الصورة؛ اختر صورة أخرى");
                });
                return;
            }
            target.post(() -> {
                if (uri.equals(selectedImage) && capturedThumbnail == null) {
                    target.setImageBitmap(bitmap);
                }
            });
        });
    }

    private static Bitmap decodeSampled(Context context, Uri uri, int maxSide) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                if (input == null) return null;
                BitmapFactory.decodeStream(input, null, bounds);
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null;
            int sample = 1;
            while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
                sample *= 2;
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = sample;
            try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                return input == null ? null : BitmapFactory.decodeStream(input, null, options);
            }
        } catch (Exception error) {
            return null;
        }
    }

    private static void startBluetoothScan(Activity activity, TextView status, TextView results) {
        stopScan();
        synchronized (DISCOVERED_LOCK) { discovered.clear(); }
        results.setText("جارٍ التحضير للمسح…");
        if (!checkBluetoothPermissions(activity, status)) {
            results.setText("لم يبدأ المسح؛ راجع حالة الأذونات أعلاه");
            return;
        }

        BluetoothAdapter adapter = bluetoothAdapter(activity);
        if (adapter == null) {
            status.setText("لا يتوفر محول Bluetooth على هذا الجهاز");
            return;
        }
        try {
            if (!adapter.isEnabled()) {
                status.setText("وافق على تشغيل Bluetooth في نافذة النظام، ثم ابدأ الفحص مرة أخرى");
                Intent enable = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                try {
                    activity.startActivityForResult(enable, BLUETOOTH_ENABLE_REQUEST);
                } catch (RuntimeException error) {
                    status.setText("تعذر فتح نافذة Bluetooth؛ افتح إعدادات Bluetooth يدويًا");
                }
                return;
            }
            scanner = adapter.getBluetoothLeScanner();
            if (scanner == null) {
                status.setText("ماسح BLE غير متاح؛ تحقق من دعم Bluetooth LE");
                return;
            }
            callback = new ScanCallback() {
                @Override public void onScanResult(int callbackType, ScanResult result) {
                    recordResult(result);
                }

                @Override public void onBatchScanResults(List<ScanResult> batchResults) {
                    if (batchResults == null) return;
                    for (ScanResult result : batchResults) recordResult(result);
                }

                @Override public void onScanFailed(int errorCode) {
                    MAIN.post(() -> {
                        stopScan();
                        TextView current = bluetoothStatus.get();
                        if (current != null) current.setText("تعذر بدء مسح BLE (" + errorCode + ")");
                    });
                }
            };
            scanner.startScan(callback);
            status.setText("جارٍ مسح BLE لمدة 10 ثوانٍ…");
            renderBluetoothResults();
            scanTimeout = () -> {
                stopScan();
                TextView current = bluetoothStatus.get();
                if (current != null) current.setText("انتهى المسح؛ الأجهزة المعروضة لم تُحفظ");
                renderBluetoothResults();
            };
            MAIN.postDelayed(scanTimeout, BLE_SCAN_DURATION_MS);
        } catch (SecurityException error) {
            stopScan();
            status.setText("رفض Android المسح؛ تحقق من صلاحيات تطبيق المضيف");
        } catch (RuntimeException error) {
            stopScan();
            status.setText("تعذر تشغيل مسح Bluetooth");
        }
    }

    private static boolean checkBluetoothPermissions(Activity activity, TextView status) {
        String[] required = Build.VERSION.SDK_INT >= 31
                ? new String[] {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[] {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION};
        ArrayList<String> undeclared = new ArrayList<>();
        for (String permission : required) {
            if (!hostDeclares(activity, permission)) undeclared.add(permission);
        }
        if (!undeclared.isEmpty()) {
            status.setText("لا يمكن المسح داخل هذا المضيف: لا يعلن الصلاحيات المطلوبة في Manifest. يلزم تطبيق تملكه ويصرّح بهذه الأذونات؛ الموديول لا يضيفها إلى التطبيقات الأخرى.");
            return false;
        }
        if (Build.VERSION.SDK_INT < 23) return true;
        ArrayList<String> missing = new ArrayList<>();
        for (String permission : required) {
            if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
            }
        }
        if (!missing.isEmpty()) {
            activity.requestPermissions(missing.toArray(new String[0]), BLUETOOTH_PERMISSION_REQUEST);
            status.setText("أكمل منح صلاحيات Bluetooth في نافذة Android، ثم اضغط الفحص مرة أخرى");
            return false;
        }
        return true;
    }

    private static boolean hostDeclares(Activity activity, String permission) {
        try {
            PackageInfo info = activity.getPackageManager().getPackageInfo(
                    activity.getPackageName(), PackageManager.GET_PERMISSIONS);
            String[] requested = info.requestedPermissions;
            if (requested == null) return false;
            for (String value : requested) if (permission.equals(value)) return true;
        } catch (PackageManager.NameNotFoundException ignored) { }
        return false;
    }

    private static String initialBluetoothStatus(Activity activity) {
        String[] required = Build.VERSION.SDK_INT >= 31
                ? new String[] {Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT}
                : new String[] {Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN,
                        Manifest.permission.ACCESS_FINE_LOCATION};
        for (String permission : required) {
            if (!hostDeclares(activity, permission)) return "يتطلب هذا المضيف صلاحيات Bluetooth المعلنة في Manifest؛ راجع التنبيه قبل المسح";
        }
        if (Build.VERSION.SDK_INT >= 23) {
            for (String permission : required) {
                if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    return "يلزم منح صلاحيات Bluetooth من Android عند بدء الفحص";
                }
            }
        }
        BluetoothAdapter adapter = bluetoothAdapter(activity);
        if (adapter == null) return "لا يتوفر Bluetooth على هذا الجهاز";
        try {
            return adapter.isEnabled() ? "جاهز للمسح عند الضغط على زر الفحص"
                    : "Bluetooth متوقف؛ سيطلب Android موافقتك على تشغيله";
        } catch (SecurityException error) {
            return "تحقق من صلاحيات Bluetooth الخاصة بالمضيف";
        }
    }

    private static BluetoothAdapter bluetoothAdapter(Activity activity) {
        BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager == null ? BluetoothAdapter.getDefaultAdapter() : manager.getAdapter();
    }

    private static void recordResult(ScanResult result) {
        if (result == null || result.getDevice() == null) return;
        try {
            BluetoothDevice device = result.getDevice();
            String internalKey = device.getAddress(); // Used only to deduplicate; never displayed or saved.
            String name = device.getName();
            if (name == null || name.trim().isEmpty()) {
                ScanRecord record = result.getScanRecord();
                name = record == null ? null : record.getDeviceName();
            }
            if (name == null || name.trim().isEmpty()) name = "جهاز BLE";
            String display = name.trim() + "  •  " + result.getRssi() + " dBm";
            synchronized (DISCOVERED_LOCK) {
                if (!discovered.containsKey(internalKey) && discovered.size() >= MAX_DISCOVERED_DEVICES) return;
                discovered.put(internalKey, display);
            }
            renderBluetoothResults();
        } catch (SecurityException ignored) {
            TextView status = bluetoothStatus.get();
            if (status != null) status.post(() -> status.setText("يلزم إذن اتصال Bluetooth لعرض أسماء الأجهزة"));
        }
    }

    private static void renderBluetoothResults() {
        TextView target = bluetoothResults.get();
        if (target == null) return;
        final String content;
        synchronized (DISCOVERED_LOCK) {
            if (discovered.isEmpty()) {
                content = "لم تُرصد أجهزة بعد";
            } else {
                StringBuilder builder = new StringBuilder();
                int index = 1;
                for (String display : discovered.values()) {
                    builder.append(index++).append(". ").append(display).append('\n');
                }
                content = builder.toString().trim();
            }
        }
        target.post(() -> target.setText(content));
    }

    private static void stopScan() {
        Runnable timeout = scanTimeout;
        scanTimeout = null;
        if (timeout != null) MAIN.removeCallbacks(timeout);
        BluetoothLeScanner currentScanner = scanner;
        ScanCallback currentCallback = callback;
        scanner = null;
        callback = null;
        if (currentScanner != null && currentCallback != null) {
            try { currentScanner.stopScan(currentCallback); }
            catch (SecurityException ignored) { }
            catch (RuntimeException ignored) { }
        }
    }

    private static void openBluetoothSettings(Activity activity) {
        try {
            activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
        } catch (Exception error) {
            Toast.makeText(activity, "تعذر فتح إعدادات Bluetooth", Toast.LENGTH_SHORT).show();
        }
    }

    private static AlertDialog showDialog(Activity activity, String title, View content,
                                          Runnable onDismiss) {
        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(content)
                .setNegativeButton("إغلاق", null)
                .create();
        dialog.setOnDismissListener(ignored -> {
            if (onDismiss != null) onDismiss.run();
            if (cameraPreview.get() != null && !cameraPreview.get().isShown()) {
                cameraPreview = new WeakReference<>(null);
                cameraStatus = new WeakReference<>(null);
            }
        });
        dialog.show();
        WFDesign.styleAlertDialog(dialog);
        Window window = dialog.getWindow();
        if (window != null) {
            int width = (int) (activity.getResources().getDisplayMetrics().widthPixels * 0.94f);
            int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.82f);
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.getDecorView().post(() -> {
                if (dialog.isShowing() && window.getDecorView().getHeight() > maxHeight) {
                    window.setLayout(width, maxHeight);
                }
            });
        }
        return dialog;
    }

    private static LinearLayout root(Activity activity) {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        layout.setPadding(WFDesign.dp(activity, 16), WFDesign.dp(activity, 12),
                WFDesign.dp(activity, 16), WFDesign.dp(activity, 12));
        layout.setBackground(WFDesign.rounded(WFDesign.BACKGROUND, 18));
        return layout;
    }

    private static LinearLayout.LayoutParams full(Activity activity, int heightDp) {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                WFDesign.dp(activity, heightDp));
    }

    private static LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static LinearLayout.LayoutParams weighted(Activity activity, float weight, int endMarginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        params.setMargins(0, 0, WFDesign.dp(activity, endMarginDp), 0);
        return params;
    }

    private static boolean usable(Activity activity) {
        return activity != null && !activity.isFinishing()
                && (Build.VERSION.SDK_INT < 17 || !activity.isDestroyed());
    }
}
