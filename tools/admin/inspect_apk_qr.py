#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import subprocess
from PIL import Image
import os

AAPT2 = r"C:\Users\kilic\AppData\Local\Android\Sdk\build-tools\34.0.0\aapt2.exe"
APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"
OUT_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\apk_extracted"

# 1. XML tree of res/Qr.xml
res = subprocess.run([AAPT2, "dump", "xmltree", APK_PATH, "--file", "res/Qr.xml"], capture_output=True, text=True, encoding="utf-8")
print("--- res/Qr.xml ---")
print(res.stdout)

# 2. Inspect res/IV.png
iv_path = os.path.join(OUT_DIR, "res", "IV.png")
if os.path.exists(iv_path):
    img = Image.open(iv_path)
    print(f"res/IV.png: size={img.size}, mode={img.mode}, bbox={img.getbbox()}")
    
# 3. Inspect res/kg1.png
kg_path = os.path.join(OUT_DIR, "res", "kg1.png")
if os.path.exists(kg_path):
    img_bg = Image.open(kg_path)
    print(f"res/kg1.png: size={img_bg.size}, mode={img_bg.mode}")
