#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Render visual preview composites for all 4 adaptive icon masks:
- CIRCLE
- SQUIRCLE
- ROUNDED_SQUARE
- TEARDROP
"""

import os
from PIL import Image, ImageDraw
from test_mask_clipping import create_masks

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable"
OUT_DIR = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b"

def render_previews():
    fg_img = Image.open(os.path.join(RES_DIR, "ic_launcher_foreground_img.png")).convert("RGBA")
    bbox = fg_img.getbbox()
    logo_tight = fg_img.crop(bbox)
    tw, th = logo_tight.size
    
    # Scale to 276x280 px
    nw, nh = 276, 280
    res_logo = logo_tight.resize((nw, nh), Image.Resampling.LANCZOS)
    
    # 512x512 transparent foreground
    fg_scaled = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    pos_x = (512 - nw) // 2
    pos_y = (512 - nh) // 2
    fg_scaled.paste(res_logo, (pos_x, pos_y), res_logo)
    
    # Background (purple)
    bg_img = Image.new("RGBA", (512, 512), (67, 8, 107, 255))
    
    # Composite
    composite = Image.alpha_composite(bg_img, fg_scaled)
    
    masks = create_masks(512)
    os.makedirs(OUT_DIR, exist_ok=True)
    
    for mask_name, mask_img in masks.items():
        masked_icon = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        masked_icon.paste(composite, (0, 0), mask_img)
        
        out_path = os.path.join(OUT_DIR, f"preview_{mask_name.lower()}.png")
        masked_icon.save(out_path, "PNG")
        print(f"[+] Rendered preview: {out_path}")
        
    return fg_scaled

if __name__ == "__main__":
    render_previews()
