#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import zipfile
import io
from PIL import Image

APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"

with zipfile.ZipFile(APK_PATH, 'r') as z:
    for name in z.namelist():
        if name.startswith("res/") and name.endswith(".png"):
            data = z.read(name)
            try:
                img = Image.open(io.BytesIO(data))
                # If aspect ratio is 16:9 (640x360 or 960x540 or 1280x720 or 160x90)
                if abs(img.size[0] / img.size[1] - 16/9) < 0.05:
                    print(f"16:9 Banner Candidate in APK: {name} -> Size: {img.size}")
            except Exception:
                pass
