#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Inspect app_banner.png structure and layout
"""
import os
from PIL import Image

banner_path = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\app_banner.png"
img = Image.open(banner_path).convert("RGBA")
w, h = img.size

# Find non-background area or content area
# Sample corners to find background color
corners = [img.getpixel((0, 0)), img.getpixel((w-1, 0)), img.getpixel((0, h-1)), img.getpixel((w-1, h-1))]
bg_color = corners[0]
print(f"Banner dimensions: {w}x{h}, Corner color: {bg_color}")

# Find bounding box of content that deviates from background
min_x, min_y, max_x, max_y = w, h, 0, 0
for y in range(h):
    for x in range(w):
        p = img.getpixel((x, y))
        # if pixel difference from background is noticeable
        diff = abs(p[0] - bg_color[0]) + abs(p[1] - bg_color[1]) + abs(p[2] - bg_color[2])
        if diff > 40:
            if x < min_x: min_x = x
            if x > max_x: max_x = x
            if y < min_y: min_y = y
            if y > max_y: max_y = y

content_w = max_x - min_x + 1
content_h = max_y - min_y + 1
print(f"Branding Content BBox: [{min_x}, {min_y}] to [{max_x}, {max_y}] -> {content_w}x{content_h} px")
print(f"Horizontal occupancy: {content_w / w * 100:.1f}%, Vertical occupancy: {content_h / h * 100:.1f}%")

