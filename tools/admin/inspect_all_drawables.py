#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image

for name in ["ic_launcher_vault_art.png", "ic_launcher_foreground_img.png", "app_banner.png", "welcome_bg.png"]:
    p = rf"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\{name}"
    img = Image.open(p).convert("RGBA")
    w, h = img.size
    # find bbox of non-transparent pixels
    bbox = img.getbbox()
    print(f"{name}: size=({w},{h}), bbox={bbox}")
