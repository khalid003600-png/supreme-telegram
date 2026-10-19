package com.haram.attendance;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.view.View;
import android.webkit.*;
import android.widget.*;
import android.graphics.Color;

public class MainActivity extends Activity {
 private static final String URL="https://haram-attendance.teeqp13.chatgpt.site/";
 private WebView web;
 private ProgressBar progress;
 private ValueCallback<android.net.Uri[]> fileCallback;
 private GeolocationPermissions.Callback geoCallback;
 private String geoOrigin;
 private static final int LOCATION=101, FILE=102;
 @Override public void onCreate(Bundle state){super.onCreate(state);
  LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
  progress=new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);progress.setMax(100);root.addView(progress,new LinearLayout.LayoutParams(-1,6));
  web=new WebView(this);root.addView(web,new LinearLayout.LayoutParams(-1,0,1));
  LinearLayout nav=new LinearLayout(this);nav.setBackgroundColor(Color.rgb(17,39,63));
  addButton(nav,"رجوع",()->{if(web.canGoBack())web.goBack();else web.loadUrl(URL);});
  addButton(nav,"تحديث",()->web.reload());
  addButton(nav,"الرئيسية",()->web.loadUrl(URL));
  addButton(nav,"إغلاق",()->finish());root.addView(nav);
  setContentView(root);
  WebSettings s=web.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setGeolocationEnabled(true);s.setAllowFileAccess(false);s.setJavaScriptCanOpenWindowsAutomatically(false);
  web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){progress.setVisibility(View.GONE);} @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap icon){progress.setVisibility(View.VISIBLE);} @Override public void onReceivedError(WebView v,WebResourceRequest r,WebResourceError e){if(r.isForMainFrame())Toast.makeText(MainActivity.this,"تعذر تحميل الموقع، تحقق من الاتصال",Toast.LENGTH_LONG).show();}});
  web.setWebChromeClient(new WebChromeClient(){@Override public void onProgressChanged(WebView v,int value){progress.setProgress(value);} @Override public void onGeolocationPermissionsShowPrompt(String origin,GeolocationPermissions.Callback callback){if(!origin.equals("https://haram-attendance.teeqp13.chatgpt.site")){callback.invoke(origin,false,false);return;}geoOrigin=origin;geoCallback=callback;if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)callback.invoke(origin,true,false);else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},LOCATION);} @Override public boolean onShowFileChooser(WebView v,ValueCallback<android.net.Uri[]> cb,FileChooserParams params){if(fileCallback!=null)fileCallback.onReceiveValue(null);fileCallback=cb;try{startActivityForResult(params.createIntent(),FILE);return true;}catch(Exception ex){fileCallback.onReceiveValue(null);fileCallback=null;return true;}}});
  web.loadUrl(URL);
 }
 private void addButton(LinearLayout row,String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setOnClickListener(v->action.run());row.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
 @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){super.onRequestPermissionsResult(req,perms,results);if(req==LOCATION&&geoCallback!=null){geoCallback.invoke(geoOrigin,results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED,false);geoCallback=null;}}
 @Override protected void onActivityResult(int req,int result,Intent data){super.onActivityResult(req,result,data);if(req==FILE&&fileCallback!=null){fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result,data));fileCallback=null;}}
 @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
 @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
