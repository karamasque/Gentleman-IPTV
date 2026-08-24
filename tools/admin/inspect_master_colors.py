#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image

master = Image.open(r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\KaynanamTV_Master_Icon_1024.png").convert("RGBA")
w, h = master.size

print("Master image size:", w, h)
# Find bbox of non-purple background or full image
# In the master image, let's see how the background is composed
print("Pixel at (10, 10):", master.getpixel((10, 10)))
print("Pixel at (256, 10):", master.getpixel((256, 10)))
print("Pixel at (256, 256):", master.getpixel((256, 256)))
