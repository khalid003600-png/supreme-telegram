from pathlib import Path
import json,re,sys
R=Path(__file__).resolve().parents[1]
manifest=(R/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
module=json.loads((R/'app/src/main/assets/lspatch_module_info.json').read_text(encoding='utf-8'))
config=(R/'app/src/main/java/com/wolfox/gps/ModuleConfig.java').read_text(encoding='utf-8')
errors=[]
if module.get('version')!='3.1.1' or module.get('versionCode')!=313: errors.append('module metadata version mismatch')
if 'VERSION_NAME = "3.1.1"' not in config or 'VERSION_CODE = 313' not in config: errors.append('ModuleConfig version mismatch')
if '3.1.0' in manifest: errors.append('stale manifest version')
if 'android.permission.SYSTEM_ALERT_WINDOW' in manifest: errors.append('overlay permission must not be requested')
if not (R/'app/src/main/assets/xposed_init').is_file(): errors.append('xposed_init missing')
if not (R/'app/src/main/java/com/wolfox/gps/hook/WolFoxHook.java').is_file(): errors.append('WolFoxHook missing')
if errors:
 print('FAIL:', '; '.join(errors)); sys.exit(1)
print('PASS: build consistency')
