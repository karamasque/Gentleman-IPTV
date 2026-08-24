#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Audit all mipmap density variants for legacy icon consistency
"""
import os
import glob
from PIL import Image

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"

for density in ["mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"]:
    folder = os.path.join(RES_DIR, f"mipmap-{density}")
    sq = os.path.join(folder, "ic_launcher_vault.png")
    rd = os.path.join(folder, "ic_launcher_vault_round.png")
    
    sq_size = Image.open(sq).size if os.path.exists(sq) else "MISSING"
    rd_size = Image.open(rd).size if os.path.exists(rd) else "MISSING"
    print(f"mipmap-{density}: square={sq_size}, round={rd_size}")

