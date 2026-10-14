package fun.p3nd.wolfox;
import android.app.*;import android.content.*;import android.graphics.PixelFormat;import android.provider.Settings;import android.view.*;import android.widget.*;
public class FloatingService extends Service {
 WindowManager wm;View bubble;
 public android.os.IBinder onBind(Intent i){return null;}
 public int onStartCommand(Intent i,int flags,int id){
 if(bubble!=null||!Settings.canDrawOverlays(this))return START_STICKY;
 wm=(WindowManager)getSystemService(WINDOW_SERVICE);
 TextView t=new TextView(this);t.setText("◉");t.setTextSize(30);t.setTextColor(-1);t.setGravity(Gravity.CENTER);t.setBackgroundTintList(null);t.setBackgroundColor(0xff1769aa);
 WindowManager.LayoutParams p=new WindowManager.LayoutParams((int)(56*getResources().getDisplayMetrics().density),(int)(56*getResources().getDisplayMetrics().density),WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,PixelFormat.TRANSLUCENT);p.gravity=Gravity.TOP|Gravity.LEFT;p.x=20;p.y=220;
 final float[] last=new float[2];final boolean[] moved={false};
 t.setOnTouchListener((v,e)->{switch(e.getAction()){case MotionEvent.ACTION_DOWN:last[0]=e.getRawX();last[1]=e.getRawY();moved[0]=false;return true;case MotionEvent.ACTION_MOVE:float dx=e.getRawX()-last[0],dy=e.getRawY()-last[1];if(Math.abs(dx)+Math.abs(dy)>2)moved[0]=true;p.x+=(int)dx;p.y+=(int)dy;last[0]=e.getRawX();last[1]=e.getRawY();wm.updateViewLayout(t,p);return true;case MotionEvent.ACTION_UP:if(!moved[0]){Intent open=new Intent(this,MainActivity.class);open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(open);}return true;}return false;});
 bubble=t;wm.addView(t,p);return START_STICKY;}
 public void onDestroy(){if(bubble!=null){wm.removeView(bubble);bubble=null;}super.onDestroy();}
}