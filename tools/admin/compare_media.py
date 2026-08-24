#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image

p1 = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.tempmediaStorage\media_1787589428429.png"
p2 = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b\.tempmediaStorage\media_1787589437129.png"

im1 = Image.open(p1)
im2 = Image.open(p2)

print("im1 size:", im1.size, "bbox:", im1.getbbox())
print("im2 size:", im2.size, "bbox:", im2.getbbox())
