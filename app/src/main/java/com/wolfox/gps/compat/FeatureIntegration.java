package com.wolfox.gps.compat;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;
import java.util.ArrayList;

/** Independent Android platform features; no K7 runtime, network activation or license calls. */
public final class FeatureIntegration {
    private static final int IMAGE_REQUEST = 6181;
    private static Uri selectedImage;
    private static boolean resultHooked;
    private static BluetoothLeScanner scanner;
    private static ScanCallback callback;
    private static final ArrayList<String> discovered = new ArrayList<>();
    private FeatureIntegration() {}
    public static synchronized void init(Context context) {
        if (resultHooked) return;
        try {
            XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnActivityResult", new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam p) {
                    Object[] args = p.args;
                    if (args == null || args.length < 4) return;
                    int offset = args.length - 3;
                    if (args[offset] instanceof Integer && args[offset + 1] instanceof Integer && args[offset + 2] instanceof Intent) {
                        onImageSelected((Integer) args[offset], (Integer) args[offset + 1], (Intent) args[offset + 2]);
                    }
                }
            });
            resultHooked = true;
        } catch (Throwable ignored) { /* Standalone hosts can still display the UI. */ }
    }
    public static void toast(String message) { /* Retained as a harmless compatibility entry point. */ }
    public static void refresh() { }
    public static void show(Activity activity) {
        if (activity == null || activity.isFinishing()) return;
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int)(16 * activity.getResources().getDisplayMetrics().density);
        layout.setPadding(pad,pad,pad,pad);
        TextView status = new TextView(activity);
        status.setText("GPS: عبر أدوات WolFox الأصلية\nالصورة: " + (selectedImage == null ? "غير محددة" : selectedImage.toString()) + "\nBLE: " + discovered.size() + " جهاز");
        layout.addView(status);
        Button photo = new Button(activity); photo.setText("اختيار صورة من الجهاز"); layout.addView(photo);
        photo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            try { activity.startActivityForResult(Intent.createChooser(intent,"اختيار صورة"), IMAGE_REQUEST); }
            catch (Exception e) { Toast.makeText(activity,"تعذر فتح الصور",Toast.LENGTH_SHORT).show(); }
        });
        Button ble = new Button(activity); ble.setText("البحث عن أجهزة BLE القريبة"); layout.addView(ble);
        ble.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 31 && activity.checkSelfPermission("android.permission.BLUETOOTH_SCAN") != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{"android.permission.BLUETOOTH_SCAN"}, 6182);
                return;
            }
            if (activity.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 6183);
                return;
            }
            if (Build.VERSION.SDK_INT >= 31 && activity.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
                activity.requestPermissions(new String[]{"android.permission.BLUETOOTH_CONNECT"}, 6184);
                return;
            }
            BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
            BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
            if (adapter == null || !adapter.isEnabled()) { Toast.makeText(activity,"فعّل البلوتوث أولاً",Toast.LENGTH_SHORT).show(); return; }
            stopScan(); discovered.clear();
            try {
                scanner = adapter.getBluetoothLeScanner();
                if (scanner == null) return;
                callback = new ScanCallback() {
                    @Override public void onScanResult(int type, ScanResult result) {
                        if (result == null || result.getDevice() == null) return;
                        String id = result.getDevice().getAddress();
                        if (!discovered.contains(id)) discovered.add(id);
                        status.setText("GPS: WolFox\nالصورة: " + (selectedImage == null ? "غير محددة" : "محددة") + "\nأجهزة BLE: " + discovered.size());
                    }
                };
                scanner.startScan(callback);
            } catch (SecurityException e) { Toast.makeText(activity,"يلزم إذن البلوتوث",Toast.LENGTH_SHORT).show(); }
        });
        Button stop = new Button(activity); stop.setText("إيقاف بحث BLE"); layout.addView(stop); stop.setOnClickListener(v -> stopScan());
        new AlertDialog.Builder(activity).setTitle("أدوات WolFox المستقلة")
            .setView(layout).setPositiveButton("إغلاق",(d,w)->stopScan())
            .setOnDismissListener(d -> stopScan()).show();
    }
    private static void stopScan() {
        if (scanner != null && callback != null) {
            try { scanner.stopScan(callback); } catch (SecurityException ignored) { }
        }
        callback = null; scanner = null;
    }
    public static void onImageSelected(int requestCode, int resultCode, Intent data) {
        if (requestCode == IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) selectedImage = data.getData();
    }
}
