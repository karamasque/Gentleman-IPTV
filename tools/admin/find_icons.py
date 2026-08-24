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
                if img.size in [(48, 48), (72, 72), (96, 96), (144, 144), (192, 192)]:
                    # Check if it has purple corner/background
                    c = img.getpixel((img.size[0]//2, img.size[1]//2))
                    print(f"Icon Candidate in APK: {name} -> Size: {img.size}, Center pixel: {c}")
            except Exception:
                pass
