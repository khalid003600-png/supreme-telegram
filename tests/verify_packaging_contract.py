#!/usr/bin/env python3
"""Verify the post-build WolFox APK packaging contract without building or executing it.

With no argument the canonical artifact is selected from this checkout's build/local/apk
and its sibling canonical mapping is used.  An optional APK argument is allowed only from
a build/local/apk directory and is checked against that directory's sibling build/local
mapping.  No mapping contents, private paths, certificate data, or ZIP entry names are
printed.
"""
from __future__ import annotations

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
ENTRY_CLASS = "com.wolfox.gps.hook.WolFoxHook"
DIRECT_CLASS = "com.wolfox.gps.DirectBootstrap"
BRIDGE_CLASS = "com.wolfox.gps.ui.MapDialog$MapSearchBridge"
RESOLVER_CLASS = "com.wolfox.gps.util.MapSearchResolver"
CALLBACK_CLASS = RESOLVER_CLASS + "$Callback"
RESULT_CLASS = RESOLVER_CLASS + "$Result"
REQUEST_CLASS = RESOLVER_CLASS + "$Request"
EXACT_ASSETS = (
    "wolfox_icon.png",
    "wolfox_map.html",
    "wolfox/fonts/DINNextLTArabic-Medium.ttf",
    "wolfox/fonts/DINNextLTArabic-Regular.otf",
    "wolfox/ui-text-ar.properties",
)


class ContractError(RuntimeError):
    pass


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ContractError(message)


def choose_artifact(argument: str | None) -> tuple[Path, Path]:
    """Return an APK and its canonical sibling mapping, never a Gradle mapping path."""
    if argument:
        apk = Path(argument).expanduser().resolve()
        require(apk.is_file(), "APK input is unavailable")
        require(apk.parent.name == "apk" and apk.parent.parent.name == "local",
                "optional APK must be located in a build/local/apk directory")
        local = apk.parent.parent
    else:
        local = ROOT / "build" / "local"
        apks = sorted((local / "apk").glob("*_SIGNED.apk"))
        require(len(apks) == 1, "expected exactly one signed APK in the canonical output")
        apk = apks[0]
    require(apk.name.endswith("_SIGNED.apk"), "APK does not have the signed-artifact name")
    mapping = local / "private" / "mapping.txt"
    require(mapping.is_file(), "canonical R8 mapping is unavailable")
    return apk, mapping


def find_build_tools() -> Path:
    explicit = os.environ.get("ANDROID_BUILD_TOOLS")
    if explicit:
        candidates = [Path(explicit).expanduser()]
    else:
        roots: list[Path] = []
        for value in (os.environ.get("ANDROID_SDK_ROOT"), os.environ.get("ANDROID_HOME")):
            if value:
                roots.append(Path(value).expanduser())
        # Standard per-user SDK locations; no checkout-specific or fixed user path is assumed.
        roots.extend((Path.home() / "Android" / "Sdk", Path.home() / "android-sdk"))
        candidates = []
        for root in roots:
            directory = root / "build-tools"
            if directory.is_dir():
                candidates.extend(sorted((path for path in directory.iterdir() if path.is_dir()),
                                         reverse=True))
    for directory in candidates:
        if all((directory / name).is_file() and os.access(directory / name, os.X_OK)
               for name in ("aapt2", "apksigner", "dexdump")):
            return directory
    raise ContractError("Android build tools (aapt2, apksigner, dexdump) are unavailable")


def checked_output(command: list[str], failure: str) -> str:
    completed = subprocess.run(command, text=True, stdout=subprocess.PIPE,
                               stderr=subprocess.PIPE, check=False)
    if completed.returncode != 0:
        raise ContractError(failure)
    return completed.stdout


def parse_mapping(mapping: Path) -> tuple[dict[str, str], dict[str, list[str]]]:
    classes: dict[str, str] = {}
    members: dict[str, list[str]] = {}
    current: str | None = None
    for line in mapping.read_text(encoding="utf-8").splitlines():
        match = re.match(r"^(\S+) -> (\S+):$", line)
        if match:
            current = match.group(1)
            classes[current] = match.group(2)
            members[current] = []
        elif current is not None and line.startswith(" "):
            members[current].append(line.strip())
    return classes, members


def mapped_member(members: dict[str, list[str]], owner: str, source_prefix: str) -> str:
    for line in members.get(owner, []):
        normalized = re.sub(r"^\d+:\d+:", "", line)
        if normalized.startswith(source_prefix) and " -> " in normalized:
            return normalized.rsplit(" -> ", 1)[1]
    raise ContractError("required R8 member mapping is absent")


def descriptor(class_name: str) -> str:
    return "L" + class_name.replace(".", "/") + ";"


def dex_blocks(dump: str) -> dict[str, str]:
    blocks: dict[str, str] = {}
    for block in dump.split("Class #"):
        match = re.search(r"Class descriptor\s*: '([^']+)'", block)
        if match:
            blocks[match.group(1)] = block
    return blocks


def has_public_method(block: str, name: str, signature: str) -> bool:
    pattern = (r"name\s*: '" + re.escape(name) + r"'\s+type\s*: '" +
               re.escape(signature) + r"'\s+access\s*: 0x[0-9a-f]+ \(PUBLIC(?: [A-Z_]+)*\)")
    return re.search(pattern, block, re.MULTILINE) is not None


def has_method(block: str, name: str, signature: str) -> bool:
    pattern = (r"name\s*: '" + re.escape(name) + r"'\s+type\s*: '" +
               re.escape(signature) + r"'")
    return re.search(pattern, block, re.MULTILINE) is not None


def forbidden_archive_entry(name: str) -> bool:
    lowered = name.lower()
    parts = lowered.split("/")
    forbidden_suffixes = (
        ".java", ".class", ".kt", ".kts", ".jks", ".keystore", ".p12", ".pfx", ".pem", ".key",
    )
    return (
        lowered.endswith(forbidden_suffixes)
        or "mapping.txt" in lowered
        or lowered.endswith("local.properties")
        or "private/" in lowered
        or any(part in {"src", "stubs", "gradle", ".gradle"} for part in parts)
    )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", nargs="?", help="optional signed APK under build/local/apk")
    argument = parser.parse_args().apk
    apk, mapping = choose_artifact(argument)
    tools = find_build_tools()
    passed: list[str] = []

    signature = checked_output([str(tools / "apksigner"), "verify", "--verbose", str(apk)],
                               "APK signature verification failed")
    require("Verifies" in signature, "APK signature verification did not complete")
    passed.append("APK signature")

    with zipfile.ZipFile(apk) as archive:
        require(archive.testzip() is None, "APK ZIP integrity failed")
        names = archive.namelist()
        require("classes.dex" in names, "primary DEX is absent")
        require(not any(forbidden_archive_entry(name) for name in names),
                "APK contains prohibited source, mapping, or private signing material")
        for relative in EXACT_ASSETS:
            source = ASSETS / relative
            require(source.is_file(), "required source asset is absent")
            require(archive.read("assets/" + relative) == source.read_bytes(),
                    "a packaged icon, HTML, or font differs from its source bytes")
        xposed = archive.read("assets/xposed_init").decode("utf-8").strip()
        require(xposed == ENTRY_CLASS, "xposed_init does not preserve the entry class")
        metadata = json.loads(archive.read("assets/lspatch_module_info.json").decode("utf-8"))
        require(metadata.get("scope") == [],
                "Embedded-host build must not pin a fixed package or add a global wildcard")
        require(metadata.get("id") == "com.wolfox.gps" and metadata.get("versionCode") == 313,
                "Embedded-host module identity or version changed unexpectedly")
        dex_names = sorted(name for name in names if re.fullmatch(r"classes(?:[2-9][0-9]*)?\.dex", name))
        require(dex_names and dex_names[0] == "classes.dex", "APK DEX set is invalid")
        with tempfile.TemporaryDirectory(prefix="wolfox-packaging-") as temporary:
            temporary_root = Path(temporary)
            dumps: list[str] = []
            for name in dex_names:
                contents = archive.read(name)
                require(contents.startswith(b"dex\n"), "APK contains a non-DEX code payload")
                dex_file = temporary_root / Path(name).name
                dex_file.write_bytes(contents)
                dumps.append(checked_output([str(tools / "dexdump"), "-a", str(dex_file)],
                                            "DEX inspection failed"))
    passed.append("ZIP integrity and exact assets")

    manifest = checked_output([str(tools / "aapt2"), "dump", "xmltree", str(apk),
                               "--file", "AndroidManifest.xml"], "compiled manifest inspection failed")
    require(re.search(r"android:debuggable[^\n]*=false", manifest) is not None,
            "compiled manifest is debuggable or does not expose a false value")
    require("xposedmodule" in manifest and re.search(r"android:value[^\n]*=true", manifest) is not None,
            "compiled manifest lacks Xposed module metadata")
    passed.append("compiled non-debuggable manifest")

    classes, members = parse_mapping(mapping)
    for required in (ENTRY_CLASS, DIRECT_CLASS, BRIDGE_CLASS, RESOLVER_CLASS, CALLBACK_CLASS, RESULT_CLASS, REQUEST_CLASS):
        require(required in classes, "required R8 class mapping is absent")
    require(classes[ENTRY_CLASS] == ENTRY_CLASS and classes[DIRECT_CLASS] == DIRECT_CLASS,
            "an external Xposed or direct-bootstrap class was renamed")

    dump = "\n".join(dumps)
    blocks = dex_blocks(dump)
    for kept in (descriptor(ENTRY_CLASS), descriptor(DIRECT_CLASS)):
        require(kept in blocks, "a kept external class is absent from DEX")
    direct = blocks[descriptor(DIRECT_CLASS)]
    for name, signature in (
        ("showControls", "(Landroid/app/Activity;)V"),
        ("hideControls", "(Landroid/app/Activity;)V"),
        ("attach", "(Landroid/app/Activity;)V"),
        ("observeTouch", "(Landroid/app/Activity;Landroid/view/MotionEvent;)V"),
    ):
        require(has_public_method(direct, name, signature),
                "a required DirectBootstrap public signature is missing or changed")
    passed.append("kept Xposed entry and DirectBootstrap signatures")

    bridge = blocks.get(descriptor(classes[BRIDGE_CLASS]))
    require(bridge is not None, "mapped JavaScript bridge class is absent from DEX")
    require(has_public_method(bridge, "resolve", "(Ljava/lang/String;)V"),
            "JavaScript bridge resolve method is not public with its required signature")
    require(re.search(r"Annotations on method #\d+ 'resolve'\s+VISIBILITY_RUNTIME "
                      r"Landroid/webkit/JavascriptInterface;", dump) is not None,
            "runtime JavaScriptInterface annotation is absent from DEX")

    resolver_name = mapped_member(
        members, RESOLVER_CLASS,
        "com.wolfox.gps.util.MapSearchResolver$Request resolve(android.content.Context,java.lang.String,com.wolfox.gps.util.MapSearchResolver$Callback)",
    )
    callback_name = mapped_member(
        members, CALLBACK_CLASS,
        "void onComplete(com.wolfox.gps.util.MapSearchResolver$Result)",
    )
    resolver = blocks.get(descriptor(classes[RESOLVER_CLASS]))
    callback = blocks.get(descriptor(classes[CALLBACK_CLASS]))
    require(resolver is not None and callback is not None,
            "mapped resolver callback classes are absent from DEX")
    require(has_method(resolver, resolver_name,
                       "(Landroid/content/Context;Ljava/lang/String;" +
                       descriptor(classes[CALLBACK_CLASS]) + ")" + descriptor(classes[REQUEST_CLASS])),
            "resolver callback dispatch signature is absent from DEX")
    require(has_method(callback, callback_name, "(" + descriptor(classes[RESULT_CLASS]) + ")V"),
            "resolver callback completion signature is absent from DEX")
    request = blocks.get(descriptor(classes[REQUEST_CLASS]))
    require(request is not None, "cancelable resolver request is absent from DEX")
    require(has_method(request, mapped_member(members, REQUEST_CLASS, "void cancel()"), "()V"),
            "resolver cancellation contract is absent from DEX")
    require(has_method(request, mapped_member(members, REQUEST_CLASS, "boolean isCancelled()"), "()Z"),
            "resolver cancellation state contract is absent from DEX")
    passed.append("annotated JavaScript bridge, resolver callbacks and cancelable request")

    require(not re.search(r"Class descriptor\s*: 'Lde/robv/android/xposed/", dump),
            "Xposed API stub class definitions were packaged in DEX")
    passed.append("no Xposed stub definitions")

    internal = [original for original in classes if original.startswith("com.wolfox.gps.")]
    renamed = sum(classes[original] != original for original in internal)
    require(renamed > 0 and classes.get("com.wolfox.gps.manager.GPSMockManager")
            != "com.wolfox.gps.manager.GPSMockManager",
            "R8 did not rename the required internal implementation classes")
    passed.append("R8 internal renaming")

    for item in passed:
        print("PASS:", item)
    print("PASS: packaging contract completed; mapping checked by count only:", renamed,
          "internal classes renamed")
    print("NOTE: R8 renaming is name obfuscation, not DEX encryption or a runtime guarantee")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (ContractError, OSError, ValueError, zipfile.BadZipFile) as error:
        print("FAIL:", error, file=sys.stderr)
        raise SystemExit(1)
