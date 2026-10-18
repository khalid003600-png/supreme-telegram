#!/usr/bin/env python3
"""Read-only LSPatch bundle/module inspection. Does not patch, install or execute APKs."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import stat
import zipfile


def validate_names(archive):
    for info in archive.infolist():
        path = PurePosixPath(info.filename)
        if path.is_absolute() or '..' in path.parts or '\\' in info.filename:
            raise ValueError('Unsafe archive entry')
        if stat.S_ISLNK(info.external_attr >> 16):
            raise ValueError('Symlink archive entry')


def inspect_module(path):
    with zipfile.ZipFile(path) as archive:
        validate_names(archive)
        if archive.testzip() is not None:
            raise ValueError('Module ZIP integrity failed')
        entry = archive.read('assets/xposed_init').decode('utf-8').strip()
        if entry != 'com.wolfox.gps.hook.WolFoxHook':
            raise ValueError('Unexpected legacy Xposed entry point')
        metadata = json.loads(archive.read('assets/lspatch_module_info.json'))
        if metadata.get('id') != 'com.wolfox.gps' or metadata.get('scope') != []:
            raise ValueError('Unexpected module identity or fixed host scope')
        expected_assets = [
            'assets/wolfox_icon.png', 'assets/wolfox_map.html',
            'assets/wolfox/fonts/DINNextLTArabic-Medium.ttf',
            'assets/wolfox/fonts/DINNextLTArabic-Regular.otf',
            'assets/wolfox/ui-text-ar.properties',
        ]
        for asset in expected_assets:
            if asset not in archive.namelist() or not archive.read(asset):
                raise ValueError('Missing required module asset: ' + asset)
        dex = archive.read('classes.dex')
        if b'Lde/robv/android/xposed/IXposedHookLoadPackage;' not in dex:
            raise ValueError('Legacy interface reference not found in module DEX')
        if b'com.t2.AvailoHader' in dex:
            raise ValueError('Obsolete fixed host package remains in DEX')
    return {'sha256': hashlib.sha256(path.read_bytes()).hexdigest(),
            'legacy_entry': entry, 'metadata': metadata, 'assets': expected_assets}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('bundle', type=Path, help='user-supplied lspatch.zip, read-only')
    parser.add_argument('module', type=Path, help='new signed module APK, read-only')
    args = parser.parse_args()
    with zipfile.ZipFile(args.bundle) as archive:
        validate_names(archive)
        config = json.loads(archive.read('lspatch/config.json'))
        module_entries = [name for name in archive.namelist()
                          if name.startswith('lspatch/modules/') and name.endswith('.apk')]
        loader = archive.getinfo('lspatch/loader.dex')
        so_entries = [name for name in archive.namelist()
                      if name.startswith('lspatch/so/') and name.endswith('/liblspatch.so')]
        if loader.file_size == 0 or not module_entries or not so_entries:
            raise ValueError('Incomplete local LSPatch asset bundle')
    result = {
        'bundle_sha256': hashlib.sha256(args.bundle.read_bytes()).hexdigest(),
        'config': config,
        'embedded_module_entries': module_entries,
        'native_entries': so_entries,
        'new_module': inspect_module(args.module),
        'runtime_tested': False,
        'original_apk_modified': False,
        'notice': 'Static contract inspection only. API_CODE 102 is a framework modern-API version; '
                  'the module intentionally uses supported legacy Xposed. No signature bypass is enabled.',
    }
    print(json.dumps(result, ensure_ascii=False, indent=2))
    print('PASS: module legacy entry, separate assets and host policy match the inspected local-loader contract.')


if __name__ == '__main__':
    main()
