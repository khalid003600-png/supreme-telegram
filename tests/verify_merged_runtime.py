#!/usr/bin/env python3
"""Structural regression guards for the merged runtime.

This is intentionally a source-level check, not an Xposed/LSPatch execution test.  Robolectric
cannot install the host-process hooks used by the module, so this script verifies the critical
ordering and reachability constraints while the Java unit tests cover local Android state.
It performs no build, APK action, network request, UI launch, or license API call.
"""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
GPS = (ROOT / "app/src/main/java/com/wolfox/gps/manager/GPSMockManager.java").read_text(
    encoding="utf-8"
)
HOOK = (ROOT / "app/src/main/java/com/wolfox/gps/hook/WolFoxHook.java").read_text(
    encoding="utf-8"
)
MAP = (ROOT / "app/src/main/java/com/wolfox/gps/ui/MapDialog.java").read_text(encoding="utf-8")
PANEL = (ROOT / "app/src/main/java/com/wolfox/gps/ui/WolFoxPanel.java").read_text(
    encoding="utf-8"
)
ALL_MAIN_JAVA = "\n".join(
    path.read_text(encoding="utf-8")
    for path in (ROOT / "app/src/main/java").rglob("*.java")
)


def method_body(source: str, signature: str) -> str:
    """Return a balanced Java method body, rejecting an absent or malformed method."""
    start = source.find(signature)
    assert start >= 0, f"missing method signature: {signature}"
    open_brace = source.find("{", start)
    assert open_brace >= 0, f"missing opening brace: {signature}"
    depth = 0
    for index in range(open_brace, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[open_brace + 1:index]
    raise AssertionError(f"unbalanced method: {signature}")


# Runtime probing must use the identical, visible production mock marker before getters run.
probe = method_body(GPS, "public boolean verifyRuntimeInjection()")
marker = probe.find("markAsMock(probe);")
latitude_getter = probe.find("probe.getLatitude()")
longitude_getter = probe.find("probe.getLongitude()")
assert marker >= 0, "runtime probe is not explicitly marked mock"
assert latitude_getter > marker and longitude_getter > marker, (
    "runtime probe reads getters before its visible mock marker is applied"
)

# All emitted framework locations must preserve rather than conceal the platform mock flag.
build_location = method_body(GPS, "public Location buildLocation(String provider, WFLocation wfLoc)")
build_marker = build_location.find("markAsMock(loc);")
assert build_marker >= 0 and build_marker < build_location.find("return loc;"), (
    "buildLocation must mark Location before returning it"
)
assert "location.setMock(true);" in method_body(GPS, "private void markAsMock(Location location)"), (
    "API 31+ locations must retain the framework-visible mock flag"
)
explicit_mock = method_body(HOOK, "private static boolean explicitlyMarkedMock(Location location)")
hook_getter = method_body(HOOK, "private void hookLocationGetter(String method, String field)")
assert "location.isMock()" in explicit_mock, "hook guard no longer observes Location.isMock()"
assert "!explicitlyMarkedMock((Location) param.thisObject)" in hook_getter, (
    "getter replacement must remain restricted to explicitly mock-marked Locations"
)
hook_object = method_body(HOOK, "private void hookLocationObject()")
assert 'hookLocationGetter("isMock"' not in hook_object
assert 'hookLocationGetter("isFromMockProvider"' not in hook_object

# The GPS manager must not have a start path that bypasses the active-license gate.
start_mocking = method_body(GPS, "public void startMocking()")
license_guard = start_mocking.find("!LicenseManager.getInstance().isActive(appContext)")
state_enable = start_mocking.find("mocking = true;")
assert license_guard >= 0 and state_enable > license_guard, (
    "startMocking may enable spoofing without first checking isActive"
)
select_and_start = method_body(GPS, "public boolean selectAndStart(WFLocation location)")
assert "startMocking();" in select_and_start and "mocking = true;" not in select_and_start, (
    "selectAndStart must delegate activation to the active-license-guarded startMocking"
)
verified_start = method_body(GPS, "public StartResult requestVerifiedStart()")
assert "enum StartResult { STARTED, LICENSE_REQUIRED, LOCATION_REQUIRED, RUNTIME_UNAVAILABLE }" in GPS
for result in ("LICENSE_REQUIRED", "LOCATION_REQUIRED", "RUNTIME_UNAVAILABLE", "STARTED"):
    assert f"StartResult.{result}" in verified_start, f"missing explicit start result: {result}"
assert verified_start.find("!LicenseManager.getInstance().isActive(appContext)") < verified_start.find(
    "!hasMockLocation()"
) < verified_start.find("isHookRuntimeActive(ModuleConfig.VERSION_CODE)"), (
    "requestVerifiedStart must fail in license, location, then runtime order"
)
assert verified_start.find("!verifyRuntimeInjection()") < verified_start.find(
    "return StartResult.STARTED;"
), "STARTED must remain contingent on runtime probe verification"

# No standalone TestProvider/root route is introduced by the explicit user-facing control.
for forbidden in (
    "setTestProviderLocation(", "addTestProvider(", "setTestProviderEnabled(",
    "Runtime.getRuntime().exec(", "new ProcessBuilder("
):
    assert forbidden not in ALL_MAIN_JAVA, f"forbidden injection path present: {forbidden}"

# The panel button must be explicit and route all starts through the typed, verified result API.
panel_dialog = method_body(PANEL, "private static Dialog buildDialog(final Activity activity)")
assert 'running ? "إيقاف التزييف" : "تزييف الموقع"' in panel_dialog
assert 'running ? "إيقاف التزييف" : "تزييف الموقع", RED, Color.WHITE' in panel_dialog
assert 'toggle.setTag("wolfox.spoof.toggle");' in panel_dialog
assert "toggle.setTextSize(16);" in panel_dialog
assert "toggle.setMinimumHeight(dp(activity, 54));" in panel_dialog
assert "LinearLayout.LayoutParams toggleLp = fullWrap();" in panel_dialog
assert "switch (manager.requestVerifiedStart())" in panel_dialog
for result in ("LOCATION_REQUIRED", "LICENSE_REQUIRED", "RUNTIME_UNAVAILABLE", "STARTED"):
    assert f"case {result}:" in panel_dialog, f"panel does not handle {result}"
assert "علامة Mock ظاهرة" in panel_dialog

# A favorite preview remains view-only: its confirm action copies data, cannot reach start,
# and loadMap receives the readonly flag.  This inspects reachability rather than opening WebView/UI.
build_dialog = method_body(MAP, "private static void buildAndShow(final Activity activity, final FavoriteLocation viewingFavorite)")
assert "final boolean favoriteViewOnly = viewingFavorite != null;" in build_dialog
assert "if (favoriteViewOnly) save.setVisibility(View.GONE);" in build_dialog
readonly_confirm = build_dialog.split("confirm.setOnClickListener(v -> {", 1)[1].split("webView.evaluateJavascript(", 1)[0]
assert "if (favoriteViewOnly) {" in readonly_confirm
assert "WFLocationActions.copyCoordinates(activity, viewingFavorite.toWFLocation());" in readonly_confirm
assert "return;" in readonly_confirm
assert "GPSMockManager" not in readonly_confirm and "confirmSelection(" not in readonly_confirm
assert "selectedType, activity, false, favoriteViewOnly" in build_dialog
copy_coordinates = method_body(MAP, "private static void copySelectedCoordinates(String value, Activity activity, String label)")
assert "GPSMockManager" not in copy_coordinates and "confirmSelection(" not in copy_coordinates, (
    "read-only favorite copy path must not contain a GPS-start side channel"
)

print("PASS: runtime probe is explicitly mock-marked before getters (static only; no hook claim)")
print("PASS: built locations keep visible mock flags and guarded getters require them")
print("PASS: mock state cannot start through GPS manager without the isActive guard")
print("PASS: typed start failures and the explicit spoof button remain fail-closed and no-root")
print("PASS: favorite-map read-only flow copies coordinates without a GPS-start side channel")
