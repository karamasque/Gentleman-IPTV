#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os

with open(r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\apk_extracted\dump_resources.txt", "r", encoding="utf-8") as f:
    lines = f.readlines()

recording = False
for line in lines:
    if "resource 0x7f0e000" in line or "resource 0x7f08012" in line:
        recording = True
        print("\n" + "="*50)
    elif recording and line.startswith("       resource "):
        recording = False
    
    if recording:
        print(line, end="")
