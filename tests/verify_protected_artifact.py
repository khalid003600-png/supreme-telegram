"""Check actual R8 output and APK packaging, not just source settings."""
from pathlib import Path
import os,re,subprocess,zipfile
r=Path(__file__).resolve().parents[1]
b=r/'build/local'
apks=list((b/'apk').glob('*_SIGNED.apk'))
assert len(apks)==1, 'Expected exactly one signed APK'
mapping=(b/'private/mapping.txt').read_text()
for name in ('com.wolfox.gps.hook.WolFoxHook','com.wolfox.gps.DirectBootstrap'):
    assert f'{name} -> {name}:' in mapping, f'External entry renamed: {name}'
assert re.search(r'com\.wolfox\.gps\.manager\.GPSMockManager -> (?!com\.wolfox\.gps\.manager\.GPSMockManager:)',mapping), 'Internal class was not obfuscated'
with zipfile.ZipFile(apks[0]) as z:
    assert z.testzip() is None
    names=z.namelist()
    assert {'classes.dex','assets/xposed_init','assets/lspatch_module_info.json'}<=set(names)
    assert z.read('assets/xposed_init').decode().strip()=='com.wolfox.gps.hook.WolFoxHook'
    assert not any(n.endswith(('.java','.class','.jks','.keystore')) or 'mapping.txt' in n for n in names), 'Private build files packaged'
    assert z.read('classes.dex')==(b/'dex/classes.dex').read_bytes()
sdk=Path(os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME',str(r.parent/'toolchain/android-sdk'))))
bt=Path(os.environ.get('ANDROID_BUILD_TOOLS',str(sdk/'build-tools/34.0.0')))
dump=subprocess.check_output([str(bt/'dexdump'),str(b/'dex/classes.dex')],text=True)
bridge=re.search(r'com\.wolfox\.gps\.ui\.MapDialog\$MapSearchBridge -> ([^:]+):',mapping).group(1)
bridge_descriptor='L'+bridge.replace('.','/')+';'
bridge_block=next(c for c in dump.split('Class #') if "Class descriptor  : '"+bridge_descriptor+"'" in c)
assert re.search(r"name\s*: 'resolve'\s+type\s*: '\(Ljava/lang/String;\)V'",bridge_block), 'JS bridge missing/renamed'
for descriptor in ('Lcom/wolfox/gps/hook/WolFoxHook;','Lcom/wolfox/gps/DirectBootstrap;'):
    assert descriptor in dump, f'External entry missing from DEX: {descriptor}'
assert 'Lde/robv/android/xposed/XposedBridge;' not in re.findall(r"Class descriptor\s*:\s*'([^']+)'",dump), 'Xposed stub packaged'
xml=subprocess.check_output([str(bt/'aapt2'),'dump','xmltree',str(apks[0]),'--file','AndroidManifest.xml'],text=True)
for attr in ('debuggable','allowBackup','usesCleartextTraffic'):
    assert re.search(r'android:'+attr+r'[^\n]*=false',xml), f'{attr} not false in compiled manifest'
print('PASS: protected APK: obfuscation, external entries, JS bridge, manifest, no source/keys/stubs')
