#!/usr/bin/env python3
"""Download the official TensorFlow Lite ESRGAN model without TensorFlow Hub.

This replaces app/src/main/assets/sr_x4.tflite. The official model has the same
Float32 contract used by the app: [1,50,50,3] -> [1,200,200,3], values 0..255.
Only the Python standard library is required.
"""
from __future__ import annotations

import hashlib
import importlib.util
import os
import shutil
import sys
import tempfile
import urllib.request
from pathlib import Path

URL = "https://storage.googleapis.com/download.tensorflow.org/models/tflite/esrgan/ESRGAN.tflite"
ROOT = Path(__file__).resolve().parent.parent
DESTINATION = ROOT / "app" / "src" / "main" / "assets" / "sr_x4.tflite"
MIN_EXPECTED_BYTES = 1_000_000


def validate_tflite(path: Path) -> None:
    data = path.read_bytes()
    if len(data) < MIN_EXPECTED_BYTES:
        raise RuntimeError(
            f"Downloaded file is unexpectedly small: {len(data):,} bytes"
        )
    if data[4:8] != b"TFL3":
        raise RuntimeError("Downloaded file is not a TFLite FlatBuffer (missing TFL3).")

    inspector_path = ROOT / "tools" / "inspect_tflite.py"
    spec = importlib.util.spec_from_file_location("inspect_tflite", inspector_path)
    if spec is None or spec.loader is None:
        raise RuntimeError("Unable to load the bundled TFLite inspector.")
    inspector = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(inspector)
    summary = inspector.inspect(path)
    inspector.verify_sr_contract(summary)


def main() -> int:
    DESTINATION.parent.mkdir(parents=True, exist_ok=True)
    request = urllib.request.Request(
        URL,
        headers={"User-Agent": "srdemo-model-downloader/1.0"},
    )

    temp_path: Path | None = None
    try:
        # Keep the temporary file beside the destination so os.replace() is
        # atomic even when the project and the system temp directory are on
        # different Windows drives.
        with tempfile.NamedTemporaryFile(
            prefix="esrgan-",
            suffix=".tflite",
            dir=DESTINATION.parent,
            delete=False,
        ) as temporary:
            temp_path = Path(temporary.name)
            print(f"Downloading official ESRGAN from:\n  {URL}")
            with urllib.request.urlopen(request, timeout=120) as response:
                shutil.copyfileobj(response, temporary)

        validate_tflite(temp_path)
        digest = hashlib.sha256(temp_path.read_bytes()).hexdigest()
        os.replace(temp_path, DESTINATION)
        temp_path = None

        print(f"Installed: {DESTINATION}")
        print(f"Size: {DESTINATION.stat().st_size:,} bytes")
        print(f"SHA-256: {digest}")
        print("Next: run .\\gradlew.bat clean :app:assembleDebug")
        return 0
    except Exception as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1
    finally:
        if temp_path is not None:
            temp_path.unlink(missing_ok=True)


if __name__ == "__main__":
    raise SystemExit(main())
