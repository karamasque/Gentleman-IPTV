#!/usr/bin/env python3
# -*- coding: utf-8 -*-
import os
import time

now = time.time()
base = r"C:\Users\kilic\.gemini"
for root, dirs, files in os.walk(base):
    for f in files:
        if f.endswith((".png", ".jpg", ".webp")):
            p = os.path.join(root, f)
            try:
                mtime = os.path.getmtime(p)
                # within last 20 minutes
                if now - mtime < 1200:
                    print(f"Recent image: {p} ({os.path.getsize(p)} bytes, mtime={mtime})")
            except Exception:
                pass
