package com.wolfox.gps.compat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.wolfox.gps.ui.WFDesign;
import com.wolfox.gps.util.WFTheme;
import java.util.UUID;

/** A foreground, dialog-scoped iBeacon transmitter, separate from BLE discovery. */
public final class BeaconTools {
    private BeaconTools() {}
    public static void show(Activity activity) {
        if (!com.wolfox.gps.EditionConfig.HAS_BLUETOOTH) return;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
        SharedPreferences prefs = activity.getSharedPreferences("wolfox_beacon", Context.MODE_PRIVATE);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 16, 24, 16);
        TextView note = WFDesign.text(activity,
                "بث iBeacon تجريبي لمدة 60 ثانية. UUID هنا معرّف الإشارة، وليس UDID الهاتف. يتوقف البث عند إغلاق النافذة.",
                13, WFDesign.MUTED, false);
        root.addView(note);
        EditText uuid = field(activity, root, "UUID", prefs.getString("uuid", UUID.randomUUID().toString()), false);
        EditText major = field(activity, root, "Major (0–65535)", String.valueOf(prefs.getInt("major", 1)), true);
        EditText minor = field(activity, root, "Minor (0–65535)", String.valueOf(prefs.getInt("minor", 1)), true);
        TextView status = WFDesign.text(activity, "البث متوقف", 13, WFDesign.TEXT, true);
        root.addView(status);
        Button generate = button(activity, root, "توليد UUID جديد");
        Button save = button(activity, root, "حفظ إعدادات الإشارة");
        Button start = button(activity, root, "بدء بث iBeacon لمدة 60 ثانية");
        Button stop = button(activity, root, "إيقاف البث");
        WFTheme.applyTree(root);
        ScrollView scroll = new ScrollView(activity);
        scroll.addView(root);
        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("Beacon و UUID")
                .setView(scroll).setNegativeButton("إغلاق", null).create();
        Session session = new Session(activity, status, start);
        generate.setOnClickListener(v -> uuid.setText(UUID.randomUUID().toString()));
        save.setOnClickListener(v -> {
            try {
                String id = uuid.getText().toString().trim();
                int a = Integer.parseInt(major.getText().toString());
                int b = Integer.parseInt(minor.getText().toString());
                BeaconPayload.encode(id, a, b, -59);
                prefs.edit().putString("uuid", id).putInt("major", a).putInt("minor", b).apply();
                status.setText("تم حفظ إعدادات الإشارة" + (session.callback == null ? "" : "؛ البث الحالي لم يتغير"));
            } catch (IllegalArgumentException error) { status.setText("تحقق من UUID وMajor وMinor"); }
        });
        start.setOnClickListener(v -> {
            try {
                session.start(BeaconPayload.encode(uuid.getText().toString().trim(),
                        Integer.parseInt(major.getText().toString()), Integer.parseInt(minor.getText().toString()), -59));
            } catch (IllegalArgumentException error) { status.setText("تحقق من UUID وMajor وMinor"); }
        });
        stop.setOnClickListener(v -> session.stop("تم إيقاف البث"));
        dialog.setOnDismissListener(v -> { session.closed = true; session.stop("البث متوقف"); });
        dialog.show();
        WFDesign.styleAlertDialog(dialog);
    }

    private static EditText field(Activity activity, LinearLayout root, String label, String value, boolean number) {
        root.addView(WFDesign.text(activity, label, 13, WFDesign.TEXT, true));
        EditText input = new EditText(activity);
        input.setSingleLine(true);
        input.setInputType(number ? android.text.InputType.TYPE_CLASS_NUMBER
                : android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(value);
        root.addView(input);
        return input;
    }
    private static Button button(Activity activity, LinearLayout root, String label) {
        Button button = WFDesign.button(activity, label, WFDesign.BUTTON_BLUETOOTH, WFDesign.TEXT);
        root.addView(button);
        return button;
    }
    private static final class Session {
        final Activity activity;
        final TextView status;
        final Button startButton;
        BluetoothLeAdvertiser advertiser;
        AdvertiseCallback callback;
        boolean closed;
        Session(Activity a, TextView s, Button b) { activity = a; status = s; startButton = b; }
        void start(byte[] payload) {
            stop("جارٍ التحضير للبث…");
            if (closed) return;
            if (Build.VERSION.SDK_INT >= 31) {
                String permission = Manifest.permission.BLUETOOTH_ADVERTISE;
                try {
                    PackageInfo info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), PackageManager.GET_PERMISSIONS);
                    boolean declared = false;
                    if (info.requestedPermissions != null) for (String p : info.requestedPermissions) if (permission.equals(p)) declared = true;
                    if (!declared) { status.setText("يلزم إعلان BLUETOOTH_ADVERTISE في تطبيق المضيف"); return; }
                } catch (PackageManager.NameNotFoundException error) { status.setText("تعذر فحص صلاحيات المضيف"); return; }
                if (activity.checkSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
                    activity.requestPermissions(new String[]{permission}, 0x6F25);
                    status.setText("امنح إذن الأجهزة القريبة ثم اضغط بدء البث مرة أخرى");
                    return;
                }
            }
            try {
                BluetoothManager manager = (BluetoothManager) activity.getSystemService(Context.BLUETOOTH_SERVICE);
                BluetoothAdapter adapter = manager == null ? null : manager.getAdapter();
                advertiser = adapter == null ? null : adapter.getBluetoothLeAdvertiser();
                if (advertiser == null) { status.setText("شغّل Bluetooth وتحقق من دعم الجهاز للبث"); return; }
                callback = new AdvertiseCallback() {
                    @Override public void onStartSuccess(AdvertiseSettings settings) {
                        activity.runOnUiThread(() -> {
                            if (closed || callback != this) return;
                            status.setText("البث نشط؛ يتوقف تلقائيًا خلال 60 ثانية");
                        });
                    }
                    @Override public void onStartFailure(int code) {
                        activity.runOnUiThread(() -> {
                            if (callback == this) stop("تعذر البث، رمز الخطأ: " + code);
                        });
                    }
                };
                AdvertiseSettings settings = new AdvertiseSettings.Builder().setConnectable(false)
                        .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_POWER).setTimeout(60000).build();
                AdvertiseData data = new AdvertiseData.Builder().setIncludeDeviceName(false)
                        .setIncludeTxPowerLevel(false).addManufacturerData(0x004C, payload).build();
                startButton.setEnabled(false);
                advertiser.startAdvertising(settings, data, callback);
                status.postDelayed(expiry, 60000);
            } catch (SecurityException error) { stop("رفض النظام صلاحية بث Bluetooth؛ تحقق من صلاحيات المضيف"); }
            catch (RuntimeException error) { stop("تعذر بدء بث Bluetooth على هذا الجهاز"); }
        }
        final Runnable expiry = () -> stop("انتهت مدة البث");
        void stop(String message) {
            status.removeCallbacks(expiry);
            AdvertiseCallback previous = callback;
            callback = null;
            if (advertiser != null && previous != null) {
                try { advertiser.stopAdvertising(previous); } catch (RuntimeException ignored) { }
            }
            advertiser = null;
            startButton.setEnabled(true);
            status.setText(message);
        }
    }
}
