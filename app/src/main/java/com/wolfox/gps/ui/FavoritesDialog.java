package com.wolfox.gps.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.*;

import com.wolfox.gps.manager.GPSMockManager;
import com.wolfox.gps.model.FavoriteLocation;
import com.wolfox.gps.model.WFLocation;
import com.wolfox.gps.util.WFStorage;
import com.wolfox.gps.util.WFTheme;

import java.util.List;
import java.util.Locale;

/** Compact favorites UI matching the WolFox activation/panel visual language. */
public final class FavoritesDialog {
    private static final int BG=0xFF071426, CARD=0xFF0D203B, CARD2=0xFF123052;
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

        TextView hint=text(a,"المواقع المحفوظة",12,MUTED,false);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint,fullWrap());

        ScrollView sv=new ScrollView(a);
        LinearLayout list=new LinearLayout(a); list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list); root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1f));

        final int[] mode={0}; // 0 normal, 1 edit, 2 delete
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
                if(mode[0]==1){
                    Button e=button(a,"تعديل",BLUE,WHITE); e.setOnClickListener(v->edit(a,f,reload[0]));
                    card.addView(e,new LinearLayout.LayoutParams(dp(a,72),dp(a,38)));
                }else if(mode[0]==2){
                    Button x=button(a,"حذف",RED,WHITE); x.setOnClickListener(v->{
                        WFStorage.getInstance(a).deleteFavorite(f.getId()); reload[0].run();
                    }); card.addView(x,new LinearLayout.LayoutParams(dp(a,72),dp(a,38)));
                }else card.setOnClickListener(v->{
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

        Button manage=button(a,"تحرير المواقع المحفوظة",CARD2,WHITE);
        manage.setOnClickListener(v->{mode[0]=0;reload[0].run();});
        root.addView(manage,full(dp(a,42)));

        LinearLayout modes=new LinearLayout(a);
        Button editMode=button(a,"تعديل المواقع المحفوظة",BLUE,WHITE);
        Button deleteMode=button(a,"حذف المواقع المحفوظة",RED,WHITE);
        editMode.setOnClickListener(v->{mode[0]=1;reload[0].run();});
        deleteMode.setOnClickListener(v->{mode[0]=2;reload[0].run();});
        modes.addView(editMode,weight(a,42,6)); modes.addView(deleteMode,weight(a,42,0));
        LinearLayout.LayoutParams mp=fullWrap();mp.setMargins(0,dp(a,6),0,0);root.addView(modes,mp);

        WFTheme.applyTree(root); d.setContentView(root); d.show();
        if(d.getWindow()!=null)d.getWindow().setLayout((int)(a.getResources().getDisplayMetrics().widthPixels*.92f),
                (int)(a.getResources().getDisplayMetrics().heightPixels*.72f));
    }

    private static void edit(Activity a,FavoriteLocation f,Runnable reload){
        EditText name=new EditText(a);name.setText(f.getName());name.setHint("اسم الموقع");name.setSingleLine(true);
        new AlertDialog.Builder(a).setTitle("تعديل اسم الموقع").setView(name).setNegativeButton("إلغاء",null)
                .setPositiveButton("حفظ",(x,w)->{String n=name.getText().toString().trim();if(!n.isEmpty()){f.setName(n);WFStorage.getInstance(a).updateFavorite(f);reload.run();}}).show();
    }

    private static TextView text(Context c,String s,int z,int color,boolean bold){TextView v=new TextView(c);v.setText(s);v.setTextSize(z);v.setTextColor(color);WFTheme.apply(v,bold);return v;}
    private static Button button(Context c,String s,int bg,int color){Button b=new Button(c);b.setAllCaps(false);b.setText(s);b.setTextSize(12);b.setTextColor(color);b.setBackground(round(bg,12,0xFF28547E,1));WFTheme.apply(b,true);return b;}
    private static GradientDrawable round(int fill,int radius,int stroke,int sw){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(radius*2f);if(sw>0)g.setStroke(sw,stroke);return g;}
    private static LinearLayout.LayoutParams full(int h){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,h);}
    private static LinearLayout.LayoutParams fullWrap(){return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);}
    private static LinearLayout.LayoutParams weight(Context c,int h,int m){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(c,h),1f);p.setMargins(0,0,dp(c,m),0);return p;}
    private static int dp(Context c,int v){return Math.round(v*c.getResources().getDisplayMetrics().density);}
}
