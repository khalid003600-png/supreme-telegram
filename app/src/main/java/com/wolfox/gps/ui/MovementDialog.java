package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.SafeUi;
import com.wolfox.gps.util.WFTheme;

/** Manual location stepping, available in every edition. No background timer to leak. */
public final class MovementDialog {
    private MovementDialog() {}
    public static void show(Activity activity) {
        SafeUi.run(activity, "فتح الحركة", () -> {
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(24, 16, 24, 16);
            TextView status = WFDesign.text(activity, "حدد موقعًا ثم حرّكه بخطوات يدوية. إعدادات السرعة متاحة من الإعدادات.", 13, WFDesign.MUTED, false);
            root.addView(status);
            EditText distance = new EditText(activity);
            distance.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            distance.setHint("مسافة الخطوة بالمتر"); distance.setText("10"); root.addView(distance);
            String[] labels = {"شمال", "شرق", "جنوب", "غرب"};
            for (int i = 0; i < labels.length; i++) {
                final int bearing = i * 90;
                Button move = WFDesign.button(activity, labels[i], WFDesign.SURFACE_RAISED, WFDesign.TEXT);
                root.addView(move);
                move.setOnClickListener(v -> SafeUi.run(activity, "تحريك الموقع", () -> {
                    double meters;
                    try { meters = Double.parseDouble(distance.getText().toString()); }
                    catch (NumberFormatException error) { status.setText("أدخل مسافة من 0 إلى 1000 متر"); return; }
                    if (Double.isNaN(meters) || Double.isInfinite(meters) || meters <= 0 || meters > 1000) {
                        status.setText("أدخل مسافة من 0 إلى 1000 متر"); return;
                    }
                    GPSMockManager manager = GPSMockManager.getInstance();
                    WFLocation old = manager.getLocation();
                    if (old == null || !old.isValid()) { status.setText("حدد موقعًا أولًا"); return; }
                    double lat = Math.toRadians(old.getLatitude()), lng = Math.toRadians(old.getLongitude());
                    double angle = meters / 6371000d, direction = Math.toRadians(bearing);
                    double nextLat = Math.asin(Math.sin(lat) * Math.cos(angle) + Math.cos(lat) * Math.sin(angle) * Math.cos(direction));
                    double nextLng = lng + Math.atan2(Math.sin(direction) * Math.sin(angle) * Math.cos(lat), Math.cos(angle) - Math.sin(lat) * Math.sin(nextLat));
                    WFLocation next = new WFLocation(Math.toDegrees(nextLat), (Math.toDegrees(nextLng) + 540d) % 360d - 180d);
                    next.setAccuracy(old.getAccuracy()); next.setAltitude(old.getAltitude());
                    next.setSpeed(old.getSpeed()); next.setBearing(bearing);
                    manager.setLocation(next);
                    status.setText("تم تحريك الموقع المحدد؛ حالة تشغيل المحاكاة لم تتغير");
                }));
            }
            Button settings = WFDesign.button(activity, "إعدادات السرعة والدقة", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
            root.addView(settings);
            WFTheme.applyTree(root);
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("الحركة والسرعة")
                    .setView(root).setNegativeButton("إغلاق", null).show();
            settings.setOnClickListener(v -> SafeUi.run(activity, "فتح الإعدادات", () -> { dialog.dismiss(); SettingsDialog.show(activity); }));
            WFDesign.styleAlertDialog(dialog);
        });
    }
}
