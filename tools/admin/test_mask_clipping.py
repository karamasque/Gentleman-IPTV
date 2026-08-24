#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Multi-Mask Adaptive Icon Clipping & Visibility Simulator
Tests 4 Android Adaptive Masks:
1. Circle
2. Squircle
3. Rounded Square
4. Teardrop
"""

import os
import math
from PIL import Image, ImageDraw

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable"

def create_masks(size=512):
    # Viewport diameter is 72/108 of size = ~341px (radius 170.6)
    # Centered at (256, 256)
    cx, cy = size / 2.0, size / 2.0
    r = 170.6
    
    # 1. Circle
    mask_circle = Image.new("L", (size, size), 0)
    draw_c = ImageDraw.Draw(mask_circle)
    draw_c.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=255)
    
    # 2. Rounded Square (standard 72dp box with 18dp corner radius)
    mask_round_sq = Image.new("L", (size, size), 0)
    draw_rs = ImageDraw.Draw(mask_round_sq)
    draw_rs.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.4), fill=255)
    
    # 3. Squircle (superellipse / smoother curve)
    mask_squircle = Image.new("L", (size, size), 0)
    draw_sq = ImageDraw.Draw(mask_squircle)
    draw_sq.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.55), fill=255)
    
    # 4. Teardrop (top-left, top-right, bottom-left round, bottom-right sharp)
    mask_teardrop = Image.new("L", (size, size), 0)
    draw_td = ImageDraw.Draw(mask_teardrop)
    # Draw circle and fill bottom-right corner
    draw_td.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=255)
    draw_td.rectangle([(cx, cy), (cx + r, cy + r)], fill=255)
    draw_td.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.4), fill=255)
    
    return {
        "CIRCLE": mask_circle,
        "ROUNDED_SQUARE": mask_round_sq,
        "SQUIRCLE": mask_squircle,
        "TEARDROP": mask_teardrop
    }

def test_scaling():
    fg_path = os.path.join(RES_DIR, "ic_launcher_foreground_img.png")
    fg_img = Image.open(fg_path).convert("RGBA")
    
    # Extract tight logo artwork
    bbox = fg_img.getbbox()
    logo_tight = fg_img.crop(bbox)
    tw, th = logo_tight.size
    print(f"Original tight logo: {tw}x{th} px")
    
    masks = create_masks(512)
    
    # Test current foreground first
    print("\n--- CURRENT FOREGROUND CLIPPING TEST (395x400 px) ---")
    for mask_name, mask_img in masks.items():
        clipped = 0
        total_art_pixels = 0
        for y in range(512):
            for x in range(512):
                a = fg_img.getpixel((x, y))[3]
                if a > 40:
                    total_art_pixels += 1
                    if mask_img.getpixel((x, y)) == 0:
                        clipped += 1
        clip_pct = (clipped / total_art_pixels) * 100 if total_art_pixels > 0 else 0
        print(f"  {mask_name}: {clipped} pixels cut off ({clip_pct:.1f}% OF ARTWORK CROPPED!)")
        
    # Test target sizes
    for target_dim in [340, 320, 312, 305, 295]:
        scale = target_dim / max(tw, th)
        nw, nh = int(tw * scale), int(th * scale)
        res_logo = logo_tight.resize((nw, nh), Image.Resampling.LANCZOS)
        
        test_canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        pos_x = (512 - nw) // 2
        pos_y = (512 - nh) // 2
        test_canvas.paste(res_logo, (pos_x, pos_y), res_logo)
        
        print(f"\n--- TARGET SIZE: {nw}x{nh} px (pos: [{pos_x}, {pos_y}]) ---")
        for mask_name, mask_img in masks.items():
            clipped = 0
            total_art_pixels = 0
            for y in range(512):
                for x in range(512):
                    a = test_canvas.getpixel((x, y))[3]
                    if a > 40:
                        total_art_pixels += 1
                        if mask_img.getpixel((x, y)) == 0:
                            clipped += 1
            clip_pct = (clipped / total_art_pixels) * 100 if total_art_pixels > 0 else 0
            status = "PASS (0% CROP)" if clipped == 0 else f"CLIPPED ({clip_pct:.2f}%)"
            print(f"  {mask_name}: {status}")

if __name__ == "__main__":
    test_scaling()
