#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import subprocess

AAPT2 = r"C:\Users\kilic\AppData\Local\Android\Sdk\build-tools\34.0.0\aapt2.exe"
APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"

# Look up 0x7f080121 and 0x7f080122 and 0x7f080123
res = subprocess.run([AAPT2, "dump", "resources", APK_PATH], capture_output=True, text=True, encoding="utf-8")
for line in res.stdout.splitlines():
    if "0x7f08012" in line or "res/" in line and any(x in line for x in ["0x7f080121", "0x7f080122", "0x7f080123"]):
        print(line)
