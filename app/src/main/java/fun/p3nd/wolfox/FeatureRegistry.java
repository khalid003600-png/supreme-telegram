package fun.p3nd.wolfox;
public final class FeatureRegistry {
 private FeatureRegistry() {}
 public static final String[] APP_FEATURES={"arabic_web_ui","floating_overlay","favorites","manual_coordinates","developer_mock_location","activation_api"};
 public static final String[] MODULE_FEATURES={"xposed_entrypoint_only"};
 public static boolean supportsSdk(int sdk){ return sdk >= 28; }
}