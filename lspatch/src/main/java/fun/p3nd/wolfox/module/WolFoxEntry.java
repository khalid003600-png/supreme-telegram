package fun.p3nd.wolfox.module;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import de.robv.android.xposed.XposedBridge;
public final class WolFoxEntry implements IXposedHookLoadPackage {
 @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
  if ("fun.p3nd.wolfox".equals(param.packageName)) XposedBridge.log("WolFox module loaded: "+param.packageName);
 }
}
