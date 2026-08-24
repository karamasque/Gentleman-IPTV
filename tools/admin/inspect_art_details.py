#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image

art = Image.open(r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\ic_launcher_vault_art.png").convert("RGBA")
w, h = art.size
corners = [art.getpixel((0,0)), art.getpixel((w-1,0)), art.getpixel((0,h-1)), art.getpixel((w-1,h-1))]
print("Corners:", corners)

# Sample foreground:
fg = Image.open(r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\ic_launcher_foreground_img.png").convert("RGBA")
print("Foreground bbox:", fg.getbbox())
