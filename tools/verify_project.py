#!/usr/bin/env python3
"""Offline structural checks for the Android project."""
from __future__ import annotations

import importlib.util
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def require(condition: bool, message: str) -> None:
    if not condition:
        raise RuntimeError(message)
    print(f"PASS: {message}")


def load_inspector():
    path = ROOT / "tools" / "inspect_tflite.py"
    spec = importlib.util.spec_from_file_location("inspect_tflite", path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load inspect_tflite.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def main() -> int:
    required_files = [
        ROOT / "settings.gradle.kts",
        ROOT / "gradlew.bat",
        ROOT / "app" / "build.gradle.kts",
        ROOT / "app" / "src" / "main" / "AndroidManifest.xml",
        ROOT / "app" / "src" / "main" / "assets" / "sr_x4.tflite",
        ROOT / "app" / "src" / "main" / "java" / "com" / "delee" / "srdemo" / "MainActivity.kt",
        ROOT / "app" / "src" / "main" / "java" / "com" / "delee" / "srdemo" / "sr" / "SrRunner.kt",
    ]
    for path in required_files:
        require(path.is_file(), f"required file exists: {path.relative_to(ROOT)}")

    for xml_path in (ROOT / "app" / "src" / "main" / "res").rglob("*.xml"):
        ET.parse(xml_path)
    ET.parse(ROOT / "app" / "src" / "main" / "AndroidManifest.xml")
    require(True, "all Android XML resources are well formed")

    gradle = (ROOT / "app" / "build.gradle.kts").read_text(encoding="utf-8")
    require(
        'implementation("com.google.ai.edge.litert:litert:2.1.0")' in gradle,
        "LiteRT 2.1.0 dependency is configured",
    )
    require('noCompress += "tflite"' in gradle, "TFLite assets are not compressed")
    require("verifyBundledSrModel" in gradle, "pre-build model identifier check is configured")

    runner = (
        ROOT
        / "app"
        / "src"
        / "main"
        / "java"
        / "com"
        / "delee"
        / "srdemo"
        / "sr"
        / "SrRunner.kt"
    ).read_text(encoding="utf-8")
    for token in (
        "CompiledModel.create",
        "createInputBuffers",
        "createOutputBuffers",
        "writeFloat",
        ".run(",
        "readFloat",
        ".close()",
        "asCoroutineDispatcher",
    ):
        require(token in runner, f"SrRunner contains lifecycle/API token: {token}")

    inspector = load_inspector()
    model_path = ROOT / "app" / "src" / "main" / "assets" / "sr_x4.tflite"
    summary = inspector.inspect(model_path)
    inspector.verify_sr_contract(summary)
    require(summary["schema_version"] == 3, "TFLite schema version is 3")
    require(True, "model contract is Float32 [1,50,50,3] -> [1,200,200,3]")

    packaged_python = list((ROOT / "app" / "src" / "main" / "assets").glob("*.py"))
    require(not packaged_python, "Python conversion scripts are not packaged into the APK")

    print("\nAll offline project checks passed.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"FAIL: {error}", file=sys.stderr)
        raise SystemExit(1)
