package com.wolfox.camerastream;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

final class StreamServer {
    private final AtomicReference<byte[]> frame = new AtomicReference<>();
    private final ExecutorService clients = Executors.newFixedThreadPool(8);
    private volatile boolean running;
    private ServerSocket socket;
    private Thread acceptThread;

    void setFrame(byte[] jpeg) { frame.set(jpeg == null ? null : jpeg.clone()); }
    synchronized void start(int port) throws IOException {
        if (running) return;
        socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(port));
        running = true;
        acceptThread = new Thread(() -> {
            while (running) {
                try { Socket client = socket.accept(); client.setSoTimeout(4000); clients.execute(() -> serve(client)); }
                catch (IOException e) { if (running) android.util.Log.w("WolFoxStream", "Accept error", e); }
                catch (RejectedExecutionException e) { break; }
            }
        }, "wolfox-accept");
        acceptThread.start();
    }
    private void serve(Socket client) {
        try (Socket c = client; InputStream in = c.getInputStream(); OutputStream out = c.getOutputStream()) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
            String request = reader.readLine();
            if (request == null) return;
            String line;
            while ((line = reader.readLine()) != null && !line.isEmpty()) { }
            String[] tokens = request.split(" ");
            if (tokens.length < 2 || !"GET".equals(tokens[0])) { header(out, "400 Bad Request", "text/plain", 0); return; }
            if ("/snapshot.jpg".equals(tokens[1])) {
                byte[] image = frame.get();
                if (image == null) { header(out, "503 Service Unavailable", "text/plain", 0); return; }
                header(out, "200 OK", "image/jpeg", image.length); out.write(image); out.flush();
            } else if ("/stream.mjpg".equals(tokens[1])) {
                out.write(("HTTP/1.1 200 OK\r\nContent-Type: multipart/x-mixed-replace; boundary=wolfox\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                while (running && !c.isClosed()) {
                    byte[] image = frame.get();
                    if (image != null) {
                        out.write(("--wolfox\r\nContent-Type: image/jpeg\r\nContent-Length: " + image.length + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                        out.write(image); out.write("\r\n".getBytes(StandardCharsets.US_ASCII)); out.flush();
                    }
                    Thread.sleep(200);
                }
            } else { header(out, "404 Not Found", "text/plain", 0); }
        } catch (Exception ignored) { }
    }
    private static void header(OutputStream out, String code, String type, int length) throws IOException {
        out.write(("HTTP/1.1 " + code + "\r\nContent-Type: " + type + "\r\nContent-Length: " + length + "\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
    }
    synchronized void stop() {
        running = false;
        if (socket != null) try { socket.close(); } catch (IOException ignored) { }
        clients.shutdownNow();
    }
}
