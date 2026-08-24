#!/usr/bin/env python3
# -*- coding: utf-8 -*-
with open(r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\apk_extracted\dump_resources.txt", "r", encoding="utf-8", errors="ignore") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "resource 0x7f080122" in line or "resource 0x7f080123" in line or "resource 0x7f080121" in line:
        print("".join(lines[i:i+8]))
