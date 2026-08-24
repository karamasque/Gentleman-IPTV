#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

search_paths = [
    r"C:\Users\kilic\Desktop",
    r"C:\Users\kilic\Downloads",
    r"D:\Masaüstü",
    r"D:\Masaüstü\KaynanamTV-IPTV"
]

for base in search_paths:
    if os.path.exists(base):
        for root, dirs, files in os.walk(base):
            for f in files:
                if "Kaynanam" in f or "Master" in f or "icon" in f.lower():
                    if f.endswith((".png", ".jpg", ".webp")):
                        full = os.path.join(root, f)
                        size = os.path.getsize(full)
                        print(f"Found: {full} ({size} bytes)")
