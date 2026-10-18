#!/usr/bin/env python3
"""Static guardrails for the internal favorites map and separate subscription screen."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MAP_DIALOG = (ROOT / "app/src/main/java/com/wolfox/gps/ui/MapDialog.java").read_text(encoding="utf-8")
FAVORITES = (ROOT / "app/src/main/java/com/wolfox/gps/ui/FavoritesDialog.java").read_text(encoding="utf-8")
PANEL = (ROOT / "app/src/main/java/com/wolfox/gps/ui/WolFoxPanel.java").read_text(encoding="utf-8")
ACTIVATION = (ROOT / "app/src/main/java/com/wolfox/gps/ui/ActivationDialog.java").read_text(encoding="utf-8")
ASSET = (ROOT / "app/src/main/assets/wolfox_map.html").read_text(encoding="utf-8")
HOOK = (ROOT / "app/src/main/java/com/wolfox/gps/hook/WolFoxHook.java").read_text(encoding="utf-8")

assert "MapDialog.showFavoritePreview(activity, favorite)" in FAVORITES
assert "favoriteViewOnly ? WFTheme.icon(\"⧉\") + \"  نسخ الإحداثيات\"" in MAP_DIALOG
assert "if (favoriteViewOnly) save.setVisibility(View.GONE);" in MAP_DIALOG
readonly_confirm = MAP_DIALOG.split("confirm.setOnClickListener(v -> {", 1)[1].split("webView.evaluateJavascript(", 1)[0]
assert "if (favoriteViewOnly) {" in readonly_confirm
assert "WFLocationActions.copyCoordinates(activity, viewingFavorite.toWFLocation());" in readonly_confirm
assert "return;" in readonly_confirm
assert "GPSMockManager" not in readonly_confirm and "confirmSelection(" not in readonly_confirm
assert "GPSMockManager.getInstance().selectAndStart(location)" in MAP_DIALOG
assert "if (favoriteViewOnly)" in MAP_DIALOG
assert "readOnly ? \"&readonly=1\"" in MAP_DIALOG
assert "var favoriteView = params.get('readonly') === '1';" in ASSET
assert "function touchStart(event)" in ASSET and "favoriteView && event.touches && event.touches.length === 2" in ASSET
assert "retryButton.onclick" in ASSET and "scheduleTileTimeout()" in ASSET
assert "SubscriptionDialog.show(activity)" in PANEL
assert "SubscriptionDialog.show(activity)" in ACTIVATION
assert "علامة mock ظاهرة" in PANEL.lower()
assert "للاختبار فقط" not in PANEL
assert "WFHostPolicy.permits(lpparam.packageName)" in HOOK
assert "AUTHORIZED_HOST_PACKAGE" not in HOOK
print("PASS: favorites opens an internal view-only map")
print("PASS: manual map navigation/retry does not add a mock-start action")
print("PASS: subscription is independently reachable from the panel and activation screen")
print("PASS: mock disclosure and explicitly embedded application-host policy remain explicit")
