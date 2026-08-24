#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Inspect all legacy mipmaps in source and APK
"""
import os
import zipfile
from PIL import Image

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"
APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"

print("--- SOURCE RES MIPMAPS ---")
for d in ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]:
    folder = os.path.join(RES_DIR, f"mipmap-{d}")
    for name in ["ic_launcher_vault.png", "ic_launcher_vault_round.png", "ic_launcher_tv.png"]:
        p = os.path.join(folder, name)
        if os.path.exists(p):
            img = Image.open(p)
            print(f"  {d}/{name}: size={img.size}, mode={img.mode}, bbox={img.getbbox()}")

print("\n--- EXTRACTED APK MIPMAPS ---")
with zipfile.ZipFile(APK_PATH, 'r') as z:
    for info in z.infolist():
        if info.filename.startswith("res/") and info.filename.endswith(".png"):
            data = z.read(info.filename)
            # Try open with PIL
            import io
            try:
                img = Image.open(io.BytesIO(data))
                if img.size in [(48,48), (72,72), (96,96), (144,144), (192,192)]:
                    print(f"  APK {info.filename}: size={img.size}, bbox={img.getbbox()}")
            except Exception:
                pass
