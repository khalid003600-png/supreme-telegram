package com.wolfox.model;
import android.app.*;import android.os.*;import android.content.*;import android.location.*;import android.graphics.*;import android.widget.*;import java.io.*;import java.net.*;import java.util.*;import java.util.concurrent.*;
public final class MainActivity extends Activity {
 private LinearLayout root;private TextView status;private EditText lat,lon;private LocationManager manager;private boolean mock;private HttpServer server;private byte[] image;private ImageView preview;private final ExecutorService worker=Executors.newSingleThreadExecutor();
 private String flavor(){return BuildConfig.FLAVOR;}
 private void info(String s){status.setText(s);}
 private Button button(String s,Runnable r){Button b=new Button(this);b.setText(s);root.addView(b);b.setOnClickListener(v->r.run());return b;}
 private EditText field(String s,String val){EditText e=new EditText(this);e.setHint(s);e.setText(val);root.addView(e);return e;}
 @Override public void onCreate(Bundle state){super.onCreate(state);ScrollView sc=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(20,24,20,20);sc.addView(root);setContentView(sc);
 TextView title=new TextView(this);title.setText("WolFox Model — "+flavor());title.setTextSize(23);root.addView(title);
 status=new TextView(this);root.addView(status);manager=(LocationManager)getSystemService(LOCATION_SERVICE);
 lat=field("Latitude","21.4858");lon=field("Longitude","39.1925");
 button("تفعيل موقع تجريبي",()->{try{manager.addTestProvider(LocationManager.GPS_PROVIDER,false,false,false,false,true,true,true,1,1);manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER,true);mock=true;info("تم تفعيل مزود الموقع التجريبي");}catch(Exception e){info("اختر هذا التطبيق كتطبيق موقع وهمي في خيارات المطور: "+e.getMessage());}});
 button("تحديث إحداثيات GPS",()->{try{double a=Double.parseDouble(lat.getText().toString()),b=Double.parseDouble(lon.getText().toString());if(a < -90||a>90||b < -180||b>180)throw new IllegalArgumentException("إحداثيات غير صالحة");Location loc=new Location(LocationManager.GPS_PROVIDER);loc.setLatitude(a);loc.setLongitude(b);loc.setAccuracy(5f);loc.setTime(System.currentTimeMillis());loc.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());manager.setTestProviderLocation(LocationManager.GPS_PROVIDER,loc);info("تم تحديث الموقع التجريبي");}catch(Exception e){info("فشل تحديث GPS: "+e.getMessage());}});
 button("إيقاف GPS التجريبي",()->{try{if(mock)manager.removeTestProvider(LocationManager.GPS_PROVIDER);mock=false;info("تم الإيقاف");}catch(Exception e){info(e.getMessage());}});
 if("gpsBluetooth".equals(flavor())){
  TextView bluetooth=new TextView(this);bluetooth.setText("Bluetooth: بيانات محاكاة داخل التطبيق فقط، لا تغيّر أجهزة النظام");root.addView(bluetooth);
  button("عرض جهاز Bluetooth تجريبي",()->info("WolFox-BLE-Sim | ID: DEMO-01 | RSSI: -55 dBm"));
 }
 if("gpsCamera".equals(flavor())){
  preview=new ImageView(this);preview.setAdjustViewBounds(true);preview.setMinimumHeight(180);root.addView(preview);
  button("اختيار / استبدال صورة البث",()->{Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);pick.setType("image/*");pick.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(pick,12);});
  button("بدء بث الصورة على المنفذ 8080",()->{try{if(image==null){info("اختر صورة أولًا");return;}if(server!=null)server.stop();server=new HttpServer();server.start();info("بث الصورة: http://IP:8080/snapshot.jpg");}catch(Exception e){info("فشل البث: "+e.getMessage());}});
  button("إيقاف البث",()->{if(server!=null)server.stop();server=null;info("تم إيقاف البث");});
 }
 }
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req!=12||result!=RESULT_OK||data==null)return;worker.execute(()->{try(InputStream in=getContentResolver().openInputStream(data.getData())){Bitmap bmp=BitmapFactory.decodeStream(in);if(bmp==null)throw new IOException("invalid image");float scale=Math.min(1f,1280f/Math.max(bmp.getWidth(),bmp.getHeight()));Bitmap resized=Bitmap.createScaledBitmap(bmp,Math.max(1,(int)(bmp.getWidth()*scale)),Math.max(1,(int)(bmp.getHeight()*scale)),true);ByteArrayOutputStream out=new ByteArrayOutputStream();resized.compress(Bitmap.CompressFormat.JPEG,80,out);image=out.toByteArray();runOnUiThread(()->{preview.setImageBitmap(resized);info("الصورة جاهزة وتم تحديثها أثناء البث");});}catch(Exception e){runOnUiThread(()->info(e.toString()));}});}
 private final class HttpServer{private ServerSocket sock;private volatile boolean live;private final ExecutorService clients=Executors.newFixedThreadPool(4);void start()throws Exception{sock=new ServerSocket(8080);live=true;new Thread(()->{while(live){try{Socket c=sock.accept();clients.execute(()->serve(c));}catch(Exception e){if(!live)break;}}}).start();}
 void serve(Socket c){try(Socket socket=c;InputStream in=c.getInputStream();OutputStream out=c.getOutputStream()){socket.setSoTimeout(3000);BufferedReader reader=new BufferedReader(new InputStreamReader(in));String line=reader.readLine();if(line==null)return;while((line=reader.readLine())!=null&&!line.isEmpty()){}byte[] bytes=image;boolean ok=line.startsWith("GET /snapshot.jpg ")&&bytes!=null;String head="HTTP/1.1 "+(ok?"200 OK":"404 Not Found")+"\r\nContent-Type: image/jpeg\r\nContent-Length: "+(ok?bytes.length:0)+"\r\nConnection: close\r\n\r\n";out.write(head.getBytes("US-ASCII"));if(ok)out.write(bytes);}catch(Exception ignored){}}
 void stop(){live=false;try{sock.close();}catch(Exception ignored){}clients.shutdownNow();}}
 @Override protected void onDestroy(){if(server!=null)server.stop();if(mock)try{manager.removeTestProvider(LocationManager.GPS_PROVIDER);}catch(Exception ignored){}worker.shutdownNow();super.onDestroy();}
}
