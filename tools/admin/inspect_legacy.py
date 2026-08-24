#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Inspect composite legacy icons vs adaptive foreground
"""
import os
from PIL import Image

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"

for density in ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]:
    folder = os.path.join(RES_DIR, f"mipmap-{density}")
    sq = Image.open(os.path.join(folder, "ic_launcher_vault.png")).convert("RGBA")
    rd = Image.open(os.path.join(folder, "ic_launcher_vault_round.png")).convert("RGBA")
    
    # Check if square has background (corner alpha)
    w, h = sq.size
    corners = [sq.getpixel((0, 0)), sq.getpixel((w-1, 0)), sq.getpixel((0, h-1)), sq.getpixel((w-1, h-1))]
    print(f"{density}: sq size={sq.size}, corners alpha={[c[3] for c in corners]}, rd corners alpha={[rd.getpixel((0,0))[3], rd.getpixel((w-1, 0))[3]]}")

