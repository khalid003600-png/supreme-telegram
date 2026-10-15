# External entry loaded by name from assets/xposed_init.
-keep class com.wolfox.gps.hook.WolFoxHook { public <init>(); public *; }
# Patched hosts can call this class directly by its original name.
-keep class com.wolfox.gps.DirectBootstrap { public *; }
# Android invokes callbacks and JavaScript methods through runtime metadata.
-keepattributes *Annotation*,InnerClasses,EnclosingMethod,Signature
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
# Retain behavior during initial protected rollout; rename internal symbols.
-dontshrink
-dontoptimize
-renamesourcefileattribute Source
