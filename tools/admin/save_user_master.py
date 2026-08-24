#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import shutil
import os
from PIL import Image

src = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.user_uploaded\media_1787589954711.jpg"
dst = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\KaynanamTV_Master_Icon_1024.png"

img = Image.open(src).convert("RGBA")
img.save(dst, "PNG")
print(f"Loaded user-uploaded master icon: size={img.size}, mode={img.mode}, saved to {dst}")
