#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Compare all res PNGs with the user's LDPlayer screenshot
"""
import os
import zipfile
from PIL import Image

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"

for root, dirs, files in os.walk(RES_DIR):
    for f in files:
        if f.endswith(".png"):
            p = os.path.join(root, f)
            try:
                img = Image.open(p)
                rel = os.path.relpath(p, RES_DIR)
                print(f"{rel}: size={img.size}, mode={img.mode}, bbox={img.getbbox()}")
            except Exception as e:
                pass
