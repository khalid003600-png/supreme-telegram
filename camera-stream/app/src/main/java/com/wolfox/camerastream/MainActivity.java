package com.wolfox.camerastream;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int PICK = 41;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private StreamServer server;
    private ImageView preview;
    private TextView status;
    private EditText receiverUrl;
    private volatile boolean receiving;
    private volatile boolean streaming;
    private volatile byte[] currentJpeg;
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            if (!receiving) return;
            final String base = receiverUrl.getText().toString().trim();
            worker.execute(() -> {
                HttpURLConnection connection = null;
                try {
                    URL url = new URL(base.endsWith("/") ? base + "snapshot.jpg" : base + "/snapshot.jpg");
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setConnectTimeout(2000);
                    connection.setReadTimeout(2000);
                    if (connection.getResponseCode() != 200) throw new IOException("HTTP " + connection.getResponseCode());
                    try (InputStream input = connection.getInputStream()) {
                        Bitmap bitmap = BitmapFactory.decodeStream(input);
                        if (bitmap != null) ui.post(() -> preview.setImageBitmap(bitmap));
                    }
                } catch (Exception error) {
                    ui.post(() -> status.setText("تعذر استقبال الصورة: " + error.getMessage()));
                } finally { if (connection != null) connection.disconnect(); }
            });
            ui.postDelayed(this, 600);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 30, 24, 20);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
        TextView heading = new TextView(this);
        heading.setText("WolFox Camera Stream");
        heading.setTextSize(24);
        root.addView(heading);
        TextView description = new TextView(this);
        description.setText("بث صورة متغيرة عبر Wi-Fi محلي — لا يستبدل كاميرا Android");
        root.addView(description);
        FrameLayout frame = new FrameLayout(this);
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        frame.addView(preview, new FrameLayout.LayoutParams(-1, -1));
        Button replace = new Button(this);
        replace.setText("▣ استبدال الصورة");
        FrameLayout.LayoutParams overlay = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        frame.addView(replace, overlay);
        root.addView(frame, new LinearLayout.LayoutParams(-1, 300));
        replace.setOnClickListener(v -> pickImage());
        Button start = new Button(this);
        start.setText("▶ بدء بث الصورة");
        root.addView(start);
        Button stop = new Button(this);
        stop.setText("■ إيقاف البث");
        root.addView(stop);
        status = new TextView(this);
        status.setText("اختر صورة أولًا");
        root.addView(status);
        TextView label = new TextView(this);
        label.setText("الاستقبال: أدخل عنوان المرسل مثل http://192.168.1.10:8080");
        root.addView(label);
        receiverUrl = new EditText(this);
        receiverUrl.setSingleLine(true);
        receiverUrl.setText("http://192.168.1.10:8080");
        root.addView(receiverUrl);
        Button receive = new Button(this);
        receive.setText("بدء استقبال البث");
        root.addView(receive);
        Button stopReceive = new Button(this);
        stopReceive.setText("إيقاف الاستقبال");
        root.addView(stopReceive);
        start.setOnClickListener(v -> {
            if (currentJpeg == null) { status.setText("اختر صورة أولًا"); return; }
            if (streaming) return;
            try {
                server = new StreamServer();
                server.setFrame(currentJpeg);
                server.start(8080);
                streaming = true;
                status.setText("البث يعمل: http://IP:8080/stream.mjpg — استخدم عنوان Wi-Fi لهذا الجهاز");
            } catch (IOException error) { status.setText("فشل بدء البث: " + error.getMessage()); }
        });
        stop.setOnClickListener(v -> {
            if (server != null) server.stop();
            server = null;
            streaming = false;
            status.setText("تم إيقاف البث");
        });
        receive.setOnClickListener(v -> {
            String address = receiverUrl.getText().toString().trim();
            try {
                URL parsed = new URL(address);
                if (!"http".equals(parsed.getProtocol()) && !"https".equals(parsed.getProtocol())) throw new IOException("protocol");
                if (parsed.getHost().isEmpty()) throw new IOException("host");
            } catch (Exception error) { status.setText("أدخل عنوان HTTP أو HTTPS صحيحًا"); return; }
            receiving = true;
            ui.removeCallbacks(poll);
            ui.post(poll);
            status.setText("جارٍ استقبال البث");
        });
        stopReceive.setOnClickListener(v -> {
            receiving = false;
            ui.removeCallbacks(poll);
            status.setText("تم إيقاف الاستقبال");
        });
    }
    private void pickImage() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        try { startActivityForResult(intent, PICK); }
        catch (android.content.ActivityNotFoundException error) { status.setText("لا يوجد تطبيق لاختيار الصور"); }
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != PICK || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        worker.execute(() -> {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                Bitmap bitmap = BitmapFactory.decodeStream(in);
                if (bitmap == null) throw new IOException("صيغة الصورة غير مدعومة");
                int w = bitmap.getWidth(), h = bitmap.getHeight();
                float scale = Math.min(1f, 1280f / Math.max(w, h));
                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, Math.max(1, (int)(w * scale)), Math.max(1, (int)(h * scale)), true);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                scaled.compress(Bitmap.CompressFormat.JPEG, 80, output);
                byte[] jpg = output.toByteArray();
                ui.post(() -> {
                    currentJpeg = jpg;
                    preview.setImageBitmap(scaled);
                    if (server != null) server.setFrame(jpg);
                    status.setText(streaming ? "تم استبدال الصورة أثناء البث" : "الصورة جاهزة للبث");
                });
            } catch (Exception error) { ui.post(() -> status.setText("تعذر قراءة الصورة: " + error.getMessage())); }
        });
    }
    @Override protected void onDestroy() {
        receiving = false;
        ui.removeCallbacks(poll);
        if (server != null) server.stop();
        worker.shutdownNow();
        super.onDestroy();
    }
}
