package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.util.List;
import java.util.Locale;

/** Compact favorites UI matching the WolFox activation/panel visual language. */
public final class FavoritesDialog {
    private static final int BG=0xFF071426, CARD=0xFF0D203B;
    private static final int BLUE=0xFF2787F5, WHITE=0xFFF4F8FF, MUTED=0xFF97ABC5;
    private static final int RED=0xFFFF5563;

    private FavoritesDialog(){}

    public static void show(Activity a){
        if(a==null||a.isFinishing()) return;
        a.runOnUiThread(()->build(a));
    }

    private static void build(Activity a){
        Dialog d=new Dialog(a,android.R.style.Theme_Material_Dialog);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE); d.setCancelable(true);
        if(d.getWindow()!=null)d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        LinearLayout root=new LinearLayout(a); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(a,14),dp(a,12),dp(a,14),dp(a,13));
        root.setBackground(round(BG,22,0xFF214D7D,1));

        LinearLayout head=new LinearLayout(a); head.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text(a,"★  المفضلة",17,BLUE,true);
        head.addView(title,new LinearLayout.LayoutParams(0,dp(a,44),1f));
        Button back=button(a,"‹  رجوع",CARD,WHITE);
        back.setOnClickListener(v->{d.dismiss();WolFoxPanel.show(a);});
        head.addView(back,new LinearLayout.LayoutParams(dp(a,92),dp(a,42)));
        root.addView(head);

        ScrollView sv=new ScrollView(a);
        LinearLayout list=new LinearLayout(a); list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        final Runnable[] reload=new Runnable[1];
        reload[0]=()->{
            list.removeAllViews();
            List<FavoriteLocation> favs=WFStorage.getInstance(a).getFavorites();
            if(favs.isEmpty()){
                TextView empty=text(a,"لا توجد مواقع محفوظة",13,MUTED,false);
                empty.setGravity(Gravity.CENTER); empty.setPadding(0,dp(a,24),0,dp(a,24)); list.addView(empty);
            }
            for(FavoriteLocation f:favs){
                LinearLayout card=new LinearLayout(a); card.setGravity(Gravity.CENTER_VERTICAL);
                card.setPadding(dp(a,12),dp(a,8),dp(a,8),dp(a,8)); card.setBackground(round(CARD,14,0xFF28547E,1));
                LinearLayout info=new LinearLayout(a); info.setOrientation(LinearLayout.VERTICAL);
                info.addView(text(a,(f.getName()==null||f.getName().trim().isEmpty())?"موقع محفوظ":f.getName(),14,WHITE,true));
                info.addView(text(a,String.format(Locale.US,"%.6f, %.6f",f.getLatitude(),f.getLongitude()),11,MUTED,false));
                card.addView(info,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));
                ImageButton pencil=icon(a,android.R.drawable.ic_menu_edit,BLUE);
                pencil.setContentDescription("تعديل " + f.getName());
                pencil.setOnClickListener(v->edit(a,f,reload[0]));
                card.addView(pencil,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));
                ImageButton trash=icon(a,android.R.drawable.ic_menu_delete,RED);
                trash.setContentDescription("حذف " + f.getName());
                trash.setOnClickListener(v->new AlertDialog.Builder(a)
                    .setTitle("حذف الموقع")
                    .setMessage("حذف «" + f.getName() + "» من المفضلة؟")
                    .setNegativeButton("إلغاء",null)
                    .setPositiveButton("حذف",(which,button)->{
                        WFStorage.getInstance(a).deleteFavorite(f.getId()); reload[0].run();
                    }).show());
                card.addView(trash,new LinearLayout.LayoutParams(dp(a,48),dp(a,48)));
                card.setOnClickListener(v->{
                    if(GPSMockManager.getInstance().selectAndStart(f.toWFLocation())){
                        Toast.makeText(a,"تم اختيار وتشغيل الموقع: "+f.getName(),Toast.LENGTH_SHORT).show();
                        d.dismiss(); WolFoxPanel.show(a);
                    }else Toast.makeText(a,"تعذر تشغيل الموقع",Toast.LENGTH_SHORT).show();
                });
                LinearLayout.LayoutParams cp=fullWrap(); cp.setMargins(0,dp(a,6),0,0); list.addView(card,cp);
            }
            WFTheme.applyTree(list);
        };
        reload[0].run();

        WFTheme.applyTree(root); d.setContentView(root); d.show();
        if(d.getWindow()!=null)d.getWindow().setLayout((int)(a.getResources().getDisplayMetrics().widthPixels*.88f),
                (int)(a.getResources().getDisplayMetrics().heightPixels*.45f));
    }

    private static void edit(Activity a,FavoriteLocation f,Runnable reload){
        EditText name=new EditText(a);name.setText(f.getName());name.setHint("اسم الموقع");name.setSingleLine(true);
        new AlertDialog.Builder(a).setTitle("تعديل اسم الموقع").setView(name).setNegativeButton("إلغاء",null)
                .setPositiveButton("حفظ",(x,w)->{String n=name.getText().toString().trim();if(!n.isEmpty()){f.setName(n);WFStorage.getInstance(a).updateFavorite(f);reload.run();}}).show();
    }

    private static ImageButton icon(Context c,int resource,int color){
        ImageButton b=new ImageButton(c);b.setImageResource(resource);b.setColorFilter(color);
        b.setPadding(dp(c,12),dp(c,12),dp(c,12),dp(c,12));
        b.setBackground(round(CARD,12,0xFF28547E,1));return b;
    }
    private static TextView text(Context c,String s,int z,int color,boolean bold){TextView v=new TextView(c);v.setText(s);v.setTextSize(z);v.setTextColor(color);WFTheme.apply(v,bold);return v;}
    private static Button button(Context c,String s,int bg,int color){Button b=new Button(c);b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setTextColor(color);b.setBackground(round(bg,12,0xFF28547E,1));WFTheme.apply(b,true);return b;}
    private static GradientDrawable round(int fill,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(radius*2f);if(sw>0)g.setStroke(sw,stroke);return g;}
    private static LinearLayout.LayoutParams fullWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
}
