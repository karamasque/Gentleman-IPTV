#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import zipfile

APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"

with zipfile.ZipFile(APK_PATH, 'r') as z:
    res_files = [n for n in z.namelist() if n.startswith("res/")]
    print(f"Total res/ entries in APK: {len(res_files)}")
    print("Sample res entries:", res_files[:25])
