#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Crop and analyze the icon from user's LDPlayer screenshot
"""
import os
from PIL import Image

screenshot_path = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.user_uploaded\media_1787592305562.jpg"
if not os.path.exists(screenshot_path):
    # Find latest uploaded
    base = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.user_uploaded"
    files = [os.path.join(base, f) for f in os.listdir(base)]
    files.sort(key=os.path.getmtime, reverse=True)
    screenshot_path = files[0]

print(f"Screenshot path: {screenshot_path}")
ss = Image.open(screenshot_path).convert("RGBA")
print(f"Screenshot dimensions: {ss.size}")

# Find the purple icon inside the screenshot
# The icon is in the left dark card
# Let's search for non-dark pixels in the left half
icon_pixels = []
for y in range(ss.height):
    for x in range(int(ss.width * 0.4)):
        r, g, b, a = ss.getpixel((x, y))
        # Purple/colored pixels (r > 30 and b > 40 and g < 100) or yellow text (r > 150, g > 150)
        if (b > 50 and r > 40) or (r > 150 and g > 150 and b < 50):
            icon_pixels.append((x, y))

if icon_pixels:
    min_x = min(p[0] for p in icon_pixels)
    max_x = max(p[0] for p in icon_pixels)
    min_y = min(p[1] for p in icon_pixels)
    max_y = max(p[1] for p in icon_pixels)
    print(f"Detected icon bounding box in screenshot: [{min_x}, {min_y}, {max_x}, {max_y}] ({max_x - min_x}x{max_y - min_y} px)")
    
    cropped_icon = ss.crop((min_x - 4, min_y - 4, max_x + 4, max_y + 4))
    cropped_out = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\installer_icon_cropped.png"
    cropped_icon.save(cropped_out)
    print(f"[+] Saved cropped installer icon: {cropped_out}")
