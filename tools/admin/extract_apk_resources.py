#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Extract and analyze the actual APK resources
"""
import zipfile
import subprocess
import os

AAPT2 = r"C:\Users\kilic\AppData\Local\Android\Sdk\build-tools\34.0.0\aapt2.exe"
APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"
OUT_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\apk_extracted"

os.makedirs(OUT_DIR, exist_ok=True)

with zipfile.ZipFile(APK_PATH, 'r') as z:
    for name in z.namelist():
        if name.startswith("res/") and (name.endswith(".png") or name.endswith(".xml")):
            z.extract(name, OUT_DIR)

print(f"Extracted res folder to {OUT_DIR}")

# Dump APK resources using aapt2 dump resources
res = subprocess.run([AAPT2, "dump", "resources", APK_PATH], capture_output=True, text=True, encoding="utf-8")
with open(os.path.join(OUT_DIR, "dump_resources.txt"), "w", encoding="utf-8") as f:
    f.write(res.stdout)

print("Dumped resources.txt")

# Find ic_launcher in dumped resources
for line in res.stdout.splitlines():
    if "ic_launcher" in line or "app_banner" in line:
        print("  ", line)
