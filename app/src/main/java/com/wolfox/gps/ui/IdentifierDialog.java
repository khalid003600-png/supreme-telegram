package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.TextView;
import com.wolfox.gps.EditionConfig;
import com.wolfox.gps.compat.BeaconPayload;
import com.wolfox.gps.util.SafeUi;
import com.wolfox.gps.util.WFTheme;
import java.util.UUID;

/** Local test identity; never changes Android ID, device UDID or licensing identity. */
public final class IdentifierDialog {
    private IdentifierDialog() {}
    public static void show(Activity activity) {
        if (!EditionConfig.HAS_MEDIA) return;
        SafeUi.run(activity, "فتح المعرّف", () -> {
            SharedPreferences prefs = activity.getSharedPreferences("wolfox_test_identity_" + EditionConfig.ID, Context.MODE_PRIVATE);
            LinearLayout root = new LinearLayout(activity);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(24, 16, 24, 16);
            root.addView(WFDesign.text(activity, "UUID محلي للتجارب داخل WolFox. لا يغيّر هوية الجهاز أو معرّفات التطبيقات الأخرى.", 13, WFDesign.MUTED, false));
            EditText input = new EditText(activity);
            input.setSingleLine(true);
            input.setText(prefs.getString("uuid", ""));
            input.setHint("UUID");
            root.addView(input);
            TextView status = WFDesign.text(activity, "", 13, WFDesign.TEXT, false);
            root.addView(status);
            Button generate = WFDesign.button(activity, "توليد UUID", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
            Button save = WFDesign.button(activity, "حفظ المعرّف", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
            Button copy = WFDesign.button(activity, "نسخ المعرّف", WFDesign.SURFACE_RAISED, WFDesign.TEXT);
            root.addView(generate); root.addView(save); root.addView(copy);
            generate.setOnClickListener(v -> input.setText(UUID.randomUUID().toString()));
            save.setOnClickListener(v -> SafeUi.run(activity, "حفظ المعرّف", () -> {
                String id = input.getText().toString().trim();
                try { BeaconPayload.parseUuid(id); }
                catch (IllegalArgumentException error) { status.setText("UUID غير صالح"); return; }
                prefs.edit().putString("uuid", id).apply();
                status.setText("تم حفظ المعرّف المحلي");
            }));
            copy.setOnClickListener(v -> SafeUi.run(activity, "نسخ المعرّف", () -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("WolFox UUID", input.getText().toString()));
            }));
            WFTheme.applyTree(root);
            AlertDialog dialog = new AlertDialog.Builder(activity).setTitle("المعرّف التجريبي")
                    .setView(root).setNegativeButton("إغلاق", null).show();
            WFDesign.styleAlertDialog(dialog);
        });
    }
}
