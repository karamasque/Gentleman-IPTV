#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import zipfile
import io
from PIL import Image

APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"

with zipfile.ZipFile(APK_PATH, 'r') as z:
    # Read res/3M.png (app_banner)
    banner_bytes = z.read("res/3M.png")
    b_img = Image.open(io.BytesIO(banner_bytes))
    print(f"res/3M.png (TV Banner in Release APK): {b_img.size}, mode: {b_img.mode}")
