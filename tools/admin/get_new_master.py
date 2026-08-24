#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import time
import shutil
from PIL import Image

search_dirs = [
    r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.user_uploaded",
    r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.tempmediaStorage"
]

all_files = []
for d in search_dirs:
    if os.path.exists(d):
        for f in os.listdir(d):
            p = os.path.join(d, f)
            if os.path.isfile(p):
                all_files.append((p, os.path.getmtime(p), os.path.getsize(p)))

all_files.sort(key=lambda x: x[1], reverse=True)
print("Most recent uploaded media files:")
for p, mtime, size in all_files[:5]:
    print(f"  {p} -> size={size} bytes, mtime={mtime}")
    
# Copy the newest file to tools/admin/KaynanamTV_Final_Master.png
if all_files:
    newest = all_files[0][0]
    dst = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\KaynanamTV_Final_Master.png"
    img = Image.open(newest).convert("RGBA")
    img.save(dst, "PNG")
    print(f"\n[+] Saved newest master to {dst} ({img.size}, mode={img.mode})")
