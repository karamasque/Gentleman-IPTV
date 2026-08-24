#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Inspect visual artwork details of banner and foreground
"""
import os
from PIL import Image

banner_path = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\app_banner.png"
fg_path = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\ic_launcher_foreground_img.png"

b_img = Image.open(banner_path)
print("Banner format:", b_img.format, b_img.size, b_img.mode)

fg_img = Image.open(fg_path)
print("Foreground format:", fg_img.format, fg_img.size, fg_img.mode)

