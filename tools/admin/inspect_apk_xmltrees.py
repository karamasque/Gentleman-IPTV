#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Inspect the compiled XML files from APK
"""
import os
import subprocess

AAPT2 = r"C:\Users\kilic\AppData\Local\Android\Sdk\build-tools\34.0.0\aapt2.exe"
APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"

# aapt2 dump xmltree
for xml_file in ["res/_U.xml", "res/WI.xml", "res/jK.xml", "res/xM.xml", "res/d8.xml", "res/3M.png"]:
    res = subprocess.run([AAPT2, "dump", "xmltree", APK_PATH, "--file", xml_file], capture_output=True, text=True, encoding="utf-8")
    print(f"\n--- {xml_file} ---")
    print(res.stdout if res.stdout else res.stderr)
