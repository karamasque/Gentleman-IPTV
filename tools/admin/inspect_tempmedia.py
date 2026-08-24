#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
from PIL import Image

temp_dir = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.tempmediaStorage"
for f in os.listdir(temp_dir):
    p = os.path.join(temp_dir, f)
    if os.path.isfile(p):
        img = Image.open(p)
        print(f"{f}: size={img.size}, mode={img.mode}")
