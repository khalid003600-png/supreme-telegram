package fun.p3nd.wolfox.module;
public final class ModuleFeatures {
 public static final String TARGET_PACKAGE="fun.p3nd.wolfox";
 public static boolean accepts(String name){return TARGET_PACKAGE.equals(name);}
 private ModuleFeatures(){}
}