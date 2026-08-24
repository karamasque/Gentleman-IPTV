#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image
import os

OUT_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\apk_extracted\res"

vault_files = {
    "mdpi (48x48)": os.path.join(OUT_DIR, "yL.png"),
    "hdpi (72x72)": os.path.join(OUT_DIR, "pY.png"),
    "xhdpi (96x96)": os.path.join(OUT_DIR, "79.png"),
    "xxhdpi (144x144)": os.path.join(OUT_DIR, "yN.png"),
    "xxxhdpi (192x192)": os.path.join(OUT_DIR, "2k.png")
}

for density, p in vault_files.items():
    if os.path.exists(p):
        img = Image.open(p)
        print(f"ic_launcher_vault {density}: {p} -> size={img.size}, mode={img.mode}, bbox={img.getbbox()}")
