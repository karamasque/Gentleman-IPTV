#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image

art = Image.open(r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\ic_launcher_vault_art.png")
print("ic_launcher_vault_art.png size:", art.size, art.mode)
