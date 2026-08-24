#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Master Image Uniform Fit Pipeline
Exact, unaltered scaling of the user-provided PNG.
"""

import os
from PIL import Image, ImageDraw

MASTER_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\KaynanamTV_Final_Master.png"
RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"
PREVIEW_DIR = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b"

def run_pipeline():
    master = Image.open(MASTER_PATH).convert("RGBA")
    mw, mh = master.size
    print(f"Master image loaded: {mw}x{mh}")
    
    # 1. UNIFORM SCALE MASTER IMAGE TO FIT ANDROID SAFE ZONE
    # Android adaptive viewport diameter is 72dp = 341.3 px in 512x512 canvas.
    # 320x320 px fits with safe margin inside 341px circle, squircle and rounded square.
    target_dim = 320
    master_scaled = master.resize((target_dim, target_dim), Image.Resampling.LANCZOS)
    
    # 2. ADAPTIVE FOREGROUND (ic_launcher_foreground_img.png: 512x512)
    # Master image placed centered on 512x512 transparent canvas
    fg_512 = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    pos_x = (512 - target_dim) // 2
    pos_y = (512 - target_dim) // 2
    fg_512.paste(master_scaled, (pos_x, pos_y), master_scaled)
    
    fg_path = os.path.join(RES_DIR, "drawable", "ic_launcher_foreground_img.png")
    fg_512.save(fg_path, "PNG")
    print(f"[+] Saved Foreground: {fg_path} (Scaled to {target_dim}x{target_dim} at [{pos_x}, {pos_y}])")

    # 3. ADAPTIVE BACKGROUND (ic_launcher_background_img.png: 512x512)
    # Sample corner color of master image for seamless match
    c_corner = master.getpixel((10, 10))
    print(f"Master corner color: {c_corner}")
    bg_512 = Image.new("RGBA", (512, 512), c_corner)
    bg_path = os.path.join(RES_DIR, "drawable", "ic_launcher_background_img.png")
    bg_512.save(bg_path, "PNG")
    print(f"[+] Saved Background: {bg_path}")

    # 4. RENDER 4 REAL PREVIEWS WITH ACTUAL MASK
    cx, cy = 256.0, 256.0
    r = 170.6 # 72dp viewport diameter
    
    composite = Image.alpha_composite(bg_512, fg_512)
    
    masks = {
        "CIRCLE": Image.new("L", (512, 512), 0),
        "SQUIRCLE": Image.new("L", (512, 512), 0),
        "ROUNDED_SQUARE": Image.new("L", (512, 512), 0),
        "TEARDROP": Image.new("L", (512, 512), 0)
    }
    
    # Circle
    draw_c = ImageDraw.Draw(masks["CIRCLE"])
    draw_c.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=255)
    
    # Squircle
    draw_sq = ImageDraw.Draw(masks["SQUIRCLE"])
    draw_sq.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.55), fill=255)
    
    # Rounded Square
    draw_rs = ImageDraw.Draw(masks["ROUNDED_SQUARE"])
    draw_rs.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.35), fill=255)
    
    # Teardrop
    draw_td = ImageDraw.Draw(masks["TEARDROP"])
    draw_td.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=255)
    draw_td.rectangle([(cx, cy), (cx + r, cy + r)], fill=255)
    draw_td.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.35), fill=255)
    
    for mask_name, mask_img in masks.items():
        masked = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        masked.paste(composite, (0, 0), mask_img)
        p = os.path.join(PREVIEW_DIR, f"master_fit_{mask_name.lower()}.png")
        masked.save(p, "PNG")
        print(f"[+] Saved Preview: {p}")

    # 5. GENERATE MULTI-DENSITY LEGACY & TV ICONS (mdpi..xxxhdpi)
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dname, size in densities.items():
        folder = os.path.join(RES_DIR, f"mipmap-{dname}")
        os.makedirs(folder, exist_ok=True)
        
        # Phone Squircle (ic_launcher_vault.png) & TV icon
        sq_mask = Image.new("L", (size, size), 0)
        draw_m = ImageDraw.Draw(sq_mask)
        draw_m.rounded_rectangle([(0, 0), (size-1, size-1)], radius=int(size * 0.22), fill=255)
        
        sq_scaled = master.resize((size, size), Image.Resampling.LANCZOS)
        sq_final = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        sq_final.paste(sq_scaled, (0, 0), sq_mask)
        sq_final.save(os.path.join(folder, "ic_launcher_vault.png"), "PNG")
        sq_final.save(os.path.join(folder, "ic_launcher_tv.png"), "PNG")
        
        # Phone Round (ic_launcher_vault_round.png) & TV round icon
        rd_mask = Image.new("L", (size, size), 0)
        draw_rd = ImageDraw.Draw(rd_mask)
        draw_rd.ellipse([(0, 0), (size-1, size-1)], fill=255)
        
        # 90% scale inside circle to avoid clipping corners
        circle_dim = int(size * 0.90)
        rd_scaled = master.resize((circle_dim, circle_dim), Image.Resampling.LANCZOS)
        rd_bg = Image.new("RGBA", (size, size), c_corner)
        rd_pos = (size - circle_dim) // 2
        rd_bg.paste(rd_scaled, (rd_pos, rd_pos), rd_scaled)
        
        rd_final = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rd_final.paste(rd_bg, (0, 0), rd_mask)
        rd_final.save(os.path.join(folder, "ic_launcher_vault_round.png"), "PNG")
        rd_final.save(os.path.join(folder, "ic_launcher_tv_round.png"), "PNG")

    print("[+] Generated legacy and TV icons in all 5 densities")

if __name__ == "__main__":
    run_pipeline()
