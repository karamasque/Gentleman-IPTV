#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image

fg = Image.open(r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\ic_launcher_foreground_img.png").convert("RGBA")
bbox = fg.getbbox()
logo = fg.crop(bbox)
w, h = logo.size
print(f"Logo aspect ratio: {w}x{h} ({w/h:.2f})")

# Check horizontal and vertical density profiles
h_profile = [0] * h
w_profile = [0] * w
for y in range(h):
    for x in range(w):
        if logo.getpixel((x, y))[3] > 30:
            h_profile[y] += 1
            w_profile[x] += 1

print("Vertical profile samples (top, mid, bot):", sum(h_profile[:h//3]), sum(h_profile[h//3:2*h//3]), sum(h_profile[2*h//3:]))
