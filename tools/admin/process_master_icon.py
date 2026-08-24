#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Master Icon Processor & Multi-Mask Simulator
"""

import os
import math
from PIL import Image, ImageDraw, ImageFilter

MASTER_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\KaynanamTV_Master_Icon_1024.png"
RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"
PREVIEW_DIR = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b"

def inspect_master():
    img = Image.open(MASTER_PATH).convert("RGBA")
    w, h = img.size
    print(f"Master Icon Dimensions: {w}x{h}")
    # Sample corner and center background colors
    c_tl = img.getpixel((10, 10))
    c_tr = img.getpixel((w-10, 10))
    c_bl = img.getpixel((10, h-10))
    c_br = img.getpixel((w-10, h-10))
    print(f"Corners: TL={c_tl}, TR={c_tr}, BL={c_bl}, BR={c_br}")
    return img

def create_adaptive_layers(master_img):
    w, h = master_img.size
    
    # 1. TIGHT CONTENT BOUNDING BOX OF MASTER ARTWORK
    # If the master image has rounded corners, extract the inner content or full tile
    # For adaptive icon foreground:
    # Android's safe zone diameter is 66dp in 108dp canvas = 512 * 66 / 108 = 312.8 px
    # At diameter = 320 px centered at (256, 256), radius is 160 px (< 170.6 px circle viewport)
    target_size = 320
    
    # Resize master image with high-quality LANCZOS to 320x320
    master_resized = master_img.resize((target_size, target_size), Image.Resampling.LANCZOS)
    
    # Create 512x512 transparent canvas for foreground
    fg_canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    pos_x = (512 - target_size) // 2
    pos_y = (512 - target_size) // 2
    fg_canvas.paste(master_resized, (pos_x, pos_y), master_resized)
    
    # Background layer (512x512 solid/gradient purple)
    # The master icon has rich dark purple (#3c0660 to #1a022b)
    bg_canvas = Image.new("RGBA", (512, 512), (60, 6, 96, 255))
    
    return fg_canvas, bg_canvas

def render_mask_previews(fg, bg):
    composite = Image.alpha_composite(bg, fg)
    
    cx, cy = 256.0, 256.0
    r = 170.6 # 72dp diameter viewport
    
    masks = {}
    
    # 1. Circle
    m_circle = Image.new("L", (512, 512), 0)
    draw_c = ImageDraw.Draw(m_circle)
    draw_c.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=255)
    masks["CIRCLE"] = m_circle
    
    # 2. Squircle
    m_squircle = Image.new("L", (512, 512), 0)
    draw_sq = ImageDraw.Draw(m_squircle)
    draw_sq.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.55), fill=255)
    masks["SQUIRCLE"] = m_squircle
    
    # 3. Rounded Square
    m_round_sq = Image.new("L", (512, 512), 0)
    draw_rs = ImageDraw.Draw(m_round_sq)
    draw_rs.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.35), fill=255)
    masks["ROUNDED_SQUARE"] = m_round_sq
    
    # 4. Teardrop
    m_teardrop = Image.new("L", (512, 512), 0)
    draw_td = ImageDraw.Draw(m_teardrop)
    draw_td.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=255)
    draw_td.rectangle([(cx, cy), (cx + r, cy + r)], fill=255)
    draw_td.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.35), fill=255)
    masks["TEARDROP"] = m_teardrop
    
    for mask_name, mask_img in masks.items():
        masked = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        masked.paste(composite, (0, 0), mask_img)
        p = os.path.join(PREVIEW_DIR, f"master_preview_{mask_name.lower()}.png")
        masked.save(p, "PNG")
        print(f"[+] Saved preview: {p}")

if __name__ == "__main__":
    master = inspect_master()
    fg, bg = create_adaptive_layers(master)
    render_mask_previews(fg, bg)
