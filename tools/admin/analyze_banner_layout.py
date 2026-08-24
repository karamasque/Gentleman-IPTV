#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image

b_img = Image.open(r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\app_banner.png").convert("RGBA")
w, h = b_img.size

# Look for bright / white / golden text pixels (e.g. RGB > 150)
min_x, min_y, max_x, max_y = w, h, 0, 0
bright_pixels = 0
for y in range(h):
    for x in range(w):
        r, g, b, a = b_img.getpixel((x, y))
        # Text or icon elements are typically much brighter than the dark purple background (67, 8, 107)
        if (r > 120 or g > 120 or b > 180) and a > 100:
            bright_pixels += 1
            if x < min_x: min_x = x
            if x > max_x: max_x = x
            if y < min_y: min_y = y
            if y > max_y: max_y = y

print(f"Bright artwork/text bbox in banner: [{min_x}, {min_y}] to [{max_x}, {max_y}] -> {max_x-min_x+1}x{max_y-min_y+1} px in 640x360 canvas")
print(f"Text/Logo width ratio: {(max_x-min_x+1)/w*100:.1f}%, height ratio: {(max_y-min_y+1)/h*100:.1f}%")
