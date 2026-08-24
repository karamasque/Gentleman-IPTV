#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import shutil
import os
from PIL import Image

src = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.tempmediaStorage\media_1787589437129.png"
dst1 = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\KaynanamTV_Master_Icon_1024.png"
dst2 = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\ic_launcher_vault_art.png"

shutil.copyfile(src, dst1)
shutil.copyfile(src, dst2)
print("Copied master image to dst1 and dst2")

img = Image.open(dst1)
print(f"Master image size: {img.size}, mode: {img.mode}")
