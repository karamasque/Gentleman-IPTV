#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Inspect the full aunt artwork from ic_launcher_vault_art.png and ic_launcher_foreground_img.png
"""
import os
from PIL import Image

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable"

for name in ["ic_launcher_vault_art.png", "ic_launcher_foreground_img.png"]:
    p = os.path.join(RES_DIR, name)
    img = Image.open(p).convert("RGBA")
    print(f"{name}: size={img.size}, bbox={img.getbbox()}")
