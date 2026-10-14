package fun.p3nd.wolfox;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingService extends Service {
 private static final String TAG="WolFoxFloating";
 private WindowManager wm;
 private View bubble;
 private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
 @Override public IBinder onBind(Intent intent){return null;}
 @Override public int onStartCommand(Intent intent,int flags,int startId){
  if(bubble!=null)return START_STICKY;
  if(!Settings.canDrawOverlays(this)){Log.e(TAG,"Overlay permission missing");stopSelf();return START_NOT_STICKY;}
  try{
   wm=(WindowManager)getSystemService(WINDOW_SERVICE);
   TextView icon=new TextView(this);
   icon.setText("W");icon.setTextSize(22);icon.setTextColor(0xffffffff);
   icon.setGravity(Gravity.CENTER);icon.setElevation(dp(8));
   GradientDrawable bg=new GradientDrawable();
   bg.setShape(GradientDrawable.OVAL);bg.setColor(0xff1769aa);
   bg.setStroke(dp(2),0xff58b8ff);icon.setBackground(bg);
   WindowManager.LayoutParams params=new WindowManager.LayoutParams(dp(56),dp(56),
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
    PixelFormat.TRANSLUCENT);
   params.gravity=Gravity.TOP|Gravity.START;params.x=dp(16);params.y=dp(180);
   final float[] touch=new float[2];final int[] origin=new int[2];final boolean[] moved={false};
   icon.setOnTouchListener((v,e)->{
    switch(e.getActionMasked()){
     case MotionEvent.ACTION_DOWN:
      touch[0]=e.getRawX();touch[1]=e.getRawY();origin[0]=params.x;origin[1]=params.y;moved[0]=false;return true;
     case MotionEvent.ACTION_MOVE:
      float dx=e.getRawX()-touch[0],dy=e.getRawY()-touch[1];
      if(Math.abs(dx)+Math.abs(dy)>dp(8))moved[0]=true;
      params.x=origin[0]+Math.round(dx);params.y=origin[1]+Math.round(dy);
      try{wm.updateViewLayout(icon,params);}catch(Exception ex){Log.e(TAG,"Overlay move failed",ex);}
      return true;
     case MotionEvent.ACTION_UP:
      if(!moved[0]){Intent open=new Intent(this,MainActivity.class);
       open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);
       try{startActivity(open);}catch(Exception ex){Log.e(TAG,"Cannot open UI",ex);}}
      return true;
     default:return false;
    }
   });
   wm.addView(icon,params);bubble=icon;Log.i(TAG,"Overlay attached");
  }catch(Exception ex){Log.e(TAG,"Failed to attach overlay",ex);stopSelf();return START_NOT_STICKY;}
  return START_STICKY;
 }
 @Override public void onDestroy(){
  if(bubble!=null&&wm!=null){try{wm.removeView(bubble);}catch(Exception e){Log.w(TAG,"Overlay removal",e);}bubble=null;}
  super.onDestroy();
 }
}