#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image

art = Image.open(r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\ic_launcher_vault_art.png").convert("RGBA")
bbox = art.getbbox()
print(f"ic_launcher_vault_art.png non-transparent bbox: {bbox}")
if bbox:
    cropped = art.crop(bbox)
    print(f"Cropped tight artwork size: {cropped.size}")
