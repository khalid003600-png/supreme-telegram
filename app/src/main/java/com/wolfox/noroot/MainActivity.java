package com.wolfox.noroot;
import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;
public final class MainActivity extends Activity {
 @Override public void onCreate(Bundle state) { super.onCreate(state); TextView view=new TextView(this); view.setText("WolFox build verification — no root required"); view.setTextSize(20); view.setPadding(24,48,24,24); setContentView(view); }
}
