from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
DESIGN = (ROOT / "app/src/main/java/com/wolfox/gps/ui/WFDesign.java").read_text(encoding="utf-8")
MAP = (ROOT / "app/src/main/java/com/wolfox/gps/ui/MapDialog.java").read_text(encoding="utf-8")
PANEL = (ROOT / "app/src/main/java/com/wolfox/gps/ui/WolFoxPanel.java").read_text(encoding="utf-8")
FAVORITES = (ROOT / "app/src/main/java/com/wolfox/gps/ui/FavoritesDialog.java").read_text(encoding="utf-8")
errors = []

# Palette from COLOR_ROLES_VALIDATION_AR.md plus the new device-tool actions.
expected = {
    "PRIMARY": "0xFF3EB7E7",
    "BUTTON_SELECT": "0xFF34D399",
    "BUTTON_LOCATION": "0xFF34D399",
    "BUTTON_STOP": "0xFF881337",
    "BUTTON_PASTE": "0xFF0E7490",
    "BUTTON_CLEAR": "0xFFFB923C",
    "BUTTON_DELETE": "0xFFB91C1C",
    "BUTTON_SAVE": "0xFF7C3AED",
    "BUTTON_COPY": "0xFF4338CA",
    "BUTTON_MAP": "0xFF2DD4BF",
    "BUTTON_BLUETOOTH": "0xFF0E7490",
    "BUTTON_CAMERA": "0xFF7C3AED",
    "BUTTON_FAVORITE": "0xFFFBBF24",
    "BUTTON_SUBSCRIPTION": "0xFFA21CAF",
    "BUTTON_SETTINGS": "0xFF475569",
    "BUTTON_HIDE": "0xFFBE185D",
    "BUTTON_CLOSE": "0xFF374151",
    "BUTTON_BACK": "0xFF4B5563",
    "BUTTON_ACTIVATE": "0xFF047857",
    "BUTTON_VERIFY": "0xFF164E63",
}
for name, value in expected.items():
    match = re.search(rf"public static final int {name} = (0x[0-9A-Fa-f]+);", DESIGN)
    if not match or match.group(1).upper() != value.upper():
        errors.append(f"{name} must be {value}")

semantic = DESIGN[DESIGN.index("public static int semanticColor"):DESIGN.index("private static String normalizedLabel")]
if 'value.contains("موقعي")' not in semantic or "return BUTTON_LOCATION;" not in semantic:
    errors.append("device-location actions must use BUTTON_LOCATION")
if 'value.contains("بلوتوث")' not in semantic or "return BUTTON_BLUETOOTH;" not in semantic:
    errors.append("Bluetooth actions must use BUTTON_BLUETOOTH")
if 'value.contains("كاميرا")' not in semantic or "return BUTTON_CAMERA;" not in semantic:
    errors.append("camera actions must use BUTTON_CAMERA")
if "button.setMinHeight(dp(context, 48))" not in DESIGN or "button.setMinimumHeight(dp(context, 48))" not in DESIGN:
    errors.append("shared buttons must keep 48dp minimum touch height")
if "WFDesign.moveCloseToFooter(root);" not in PANEL or "WFDesign.moveCloseToFooter(root);" not in FAVORITES:
    errors.append("main panel and favorites must keep close controls in the footer")
if not re.search(r"dp\(activity, 92\), dp\(activity, 48\), Gravity.TOP", MAP):
    errors.append("map preview expand control must be at least 48dp high")
if not re.search(r"header\.addView\(close, new LinearLayout\.LayoutParams\(dp\(activity, 48\), dp\(activity, 48\)\)\)", MAP):
    errors.append("map close control must be a 48dp square")
for name, source in (("panel", PANEL), ("favorites", FAVORITES), ("map", MAP)):
    if "View.LAYOUT_DIRECTION_RTL" not in source:
        errors.append(f"{name} screen must retain RTL layout")

if errors:
    print("FAIL: " + "; ".join(errors))
    sys.exit(1)
print(f"PASS: {len(expected)} design colors, semantic actions, RTL and 48dp touch targets")
