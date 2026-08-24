#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Canonical Master Launcher Icon Generator
Directly sources from user-provided KaynanamTV_Master_Icon_1024.png
"""

import os
import math
from PIL import Image, ImageDraw, ImageFilter

MASTER_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\tools\admin\KaynanamTV_Master_Icon_1024.png"
RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"
PREVIEW_DIR = r"C:\Users\kilic\.gemini\antigravity-ide\brain\784899e3-3516-4658-ba08-e86b3f0c1f0b"

def create_radial_gradient(w, h, color_center, color_edge):
    img = Image.new("RGBA", (w, h))
    cx, cy = w / 2.0, h / 2.0
    max_radius = math.sqrt(cx**2 + cy**2)
    for y in range(h):
        for x in range(w):
            r = math.sqrt((x - cx)**2 + (y - cy)**2)
            ratio = min(1.0, r / max_radius)
            red = int(color_center[0] * (1 - ratio) + color_edge[0] * ratio)
            green = int(color_center[1] * (1 - ratio) + color_edge[1] * ratio)
            blue = int(color_center[2] * (1 - ratio) + color_edge[2] * ratio)
            img.putpixel((x, y), (red, green, blue, 255))
    return img

def extract_clean_master_artwork(master_img):
    """
    Extract the master artwork from the 1024x1024 master icon:
    Includes the TV frame, antennae, aunt, headscarf, sunglasses, remote, thumbs up, and KAYNANAM TV typography.
    Removes the outer squircle border so it sits on pure transparent canvas without double composition.
    """
    w, h = master_img.size
    
    # Analyze background color of the squircle
    # The inner artwork starts inside the purple card
    # Let's crop tight to the inner artwork or isolate the elements with smooth alpha mask
    # Bounding box of the core artwork:
    # Antennae reach top ~y=30, Typography reaches bottom ~y=980
    # Remote is at left ~x=85, Thumbs-up is at right ~x=840
    
    # We create a clean mask for the core artwork + TV set + typography
    # In master_img:
    # Background color is (72, 18, 118)
    # Let's create a transparent layer containing the complete elements:
    artwork = master_img.copy()
    
    # Check pixels outside the rounded TV frame / squircle
    # If the master icon is already on black or transparent outside
    # Remove outer black / dark margins
    cleaned = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    for y in range(h):
        for x in range(w):
            r, g, b, a = artwork.getpixel((x, y))
            # If it's the outer black bezel outside the squircle
            if r < 15 and g < 15 and b < 15:
                continue
            cleaned.putpixel((x, y), (r, g, b, 255))
            
    bbox = cleaned.getbbox()
    print(f"Master image content bbox: {bbox}")
    
    # To avoid double squircle border in adaptive icons:
    # We sample the purple background color from the master: #481276 = (72, 18, 118)
    return cleaned

def build_all_assets():
    master_img = Image.open(MASTER_PATH).convert("RGBA")
    print(f"Loaded Master PNG: {master_img.size}")
    
    # 1. GENERATE CANONICAL FOREGROUND (ic_launcher_foreground_img.png: 512x512)
    # Master image is fitted (CONTAIN) inside the 66dp safe zone (312px diameter circle)
    # Safe zone size = 300x300 px centered at (256, 256) -> perfectly fits Circle, Squircle, Rounded Square, Teardrop
    target_dim = 300
    master_scaled = master_img.resize((target_dim, target_dim), Image.Resampling.LANCZOS)
    
    fg_512 = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    pos_x = (512 - target_dim) // 2
    pos_y = (512 - target_dim) // 2
    fg_512.paste(master_scaled, (pos_x, pos_y), master_scaled)
    
    fg_path = os.path.join(RES_DIR, "drawable", "ic_launcher_foreground_img.png")
    fg_512.save(fg_path, "PNG")
    print(f"[+] Saved Canonical Foreground: {fg_path} (Scaled to {target_dim}x{target_dim} at [{pos_x}, {pos_y}])")

    # 2. GENERATE CANONICAL BACKGROUND (ic_launcher_background_img.png: 512x512)
    # Matching the exact deep purple gradient of the master icon
    bg_512 = create_radial_gradient(512, 512, (82, 16, 133), (28, 4, 48))
    bg_path = os.path.join(RES_DIR, "drawable", "ic_launcher_background_img.png")
    bg_512.save(bg_path, "PNG")
    print(f"[+] Saved Canonical Background: {bg_path}")

    # 3. RENDER 4 REAL PREVIEWS WITH ACTUAL MASK
    cx, cy = 256.0, 256.0
    r = 170.6 # 72dp viewport diameter
    
    composite = Image.alpha_composite(bg_512, fg_512)
    
    masks = {}
    
    # Circle
    m_circle = Image.new("L", (512, 512), 0)
    draw_c = ImageDraw.Draw(m_circle)
    draw_c.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=255)
    masks["CIRCLE"] = m_circle
    
    # Squircle
    m_squircle = Image.new("L", (512, 512), 0)
    draw_sq = ImageDraw.Draw(m_squircle)
    draw_sq.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.55), fill=255)
    masks["SQUIRCLE"] = m_squircle
    
    # Rounded Square
    m_round_sq = Image.new("L", (512, 512), 0)
    draw_rs = ImageDraw.Draw(m_round_sq)
    draw_rs.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.35), fill=255)
    masks["ROUNDED_SQUARE"] = m_round_sq
    
    # Teardrop
    m_teardrop = Image.new("L", (512, 512), 0)
    draw_td = ImageDraw.Draw(m_teardrop)
    draw_td.ellipse([(cx - r, cy - r), (cx + r, cy + r)], fill=255)
    draw_td.rectangle([(cx, cy), (cx + r, cy + r)], fill=255)
    draw_td.rounded_rectangle([(cx - r, cy - r), (cx + r, cy + r)], radius=int(r * 0.35), fill=255)
    masks["TEARDROP"] = m_teardrop
    
    os.makedirs(PREVIEW_DIR, exist_ok=True)
    for mask_name, mask_img in masks.items():
        masked = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
        masked.paste(composite, (0, 0), mask_img)
        p = os.path.join(PREVIEW_DIR, f"canonical_preview_{mask_name.lower()}.png")
        masked.save(p, "PNG")
        print(f"[+] Saved Preview: {p}")

    # 4. GENERATE MULTI-DENSITY LEGACY & TV ICONS
    densities = {
        "mdpi": 48,
        "hdpi": 72,
        "xhdpi": 96,
        "xxhdpi": 144,
        "xxxhdpi": 192
    }

    for dname, size in densities.items():
        folder = os.path.join(RES_DIR, f"mipmap-{dname}")
        os.makedirs(folder, exist_ok=True)
        
        # Squircle (ic_launcher_vault.png)
        # Direct resize of master icon with safe squircle mask
        sq_img = master_img.resize((size, size), Image.Resampling.LANCZOS)
        sq_img.save(os.path.join(folder, "ic_launcher_vault.png"), "PNG")
        
        # Round (ic_launcher_vault_round.png)
        # Round masked master icon
        rd_mask = Image.new("L", (size, size), 0)
        draw_rd = ImageDraw.Draw(rd_mask)
        draw_rd.ellipse([(0, 0), (size-1, size-1)], fill=255)
        
        # Safe scale for circle so corners of typography/antennae are not clipped
        circle_scale_dim = int(size * 0.90)
        circle_master = master_img.resize((circle_scale_dim, circle_scale_dim), Image.Resampling.LANCZOS)
        
        rd_composite = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        bg_rd_tile = create_radial_gradient(size, size, (82, 16, 133), (28, 4, 48))
        
        c_pos = (size - circle_scale_dim) // 2
        bg_rd_tile.paste(circle_master, (c_pos, c_pos), circle_master)
        rd_composite.paste(bg_rd_tile, (0, 0), rd_mask)
        rd_composite.save(os.path.join(folder, "ic_launcher_vault_round.png"), "PNG")

        # TV Specific (ic_launcher_tv.png & ic_launcher_tv_round.png)
        sq_img.save(os.path.join(folder, "ic_launcher_tv.png"), "PNG")
        rd_composite.save(os.path.join(folder, "ic_launcher_tv_round.png"), "PNG")

    print("[+] Generated multi-density legacy and TV icons for mdpi..xxxhdpi")

    # 5. GENERATE MULTI-DENSITY TV BANNERS
    banner_configs = {
        "drawable": (640, 360),
        "drawable-nodpi": (640, 360),
        "drawable-xhdpi": (640, 360),
        "drawable-xxhdpi": (960, 540),
        "drawable-xxxhdpi": (1280, 720)
    }

    for folder_name, (bw, bh) in banner_configs.items():
        folder_path = os.path.join(RES_DIR, folder_name)
        os.makedirs(folder_path, exist_ok=True)
        
        banner_bg = create_radial_gradient(bw, bh, (82, 16, 133), (28, 4, 48))
        draw_b = ImageDraw.Draw(banner_bg)
        draw_b.rectangle([(0, 0), (bw - 1, bh - 1)], outline=(140, 40, 210, 80), width=max(2, int(bw*0.003)))
        
        # In banner, scale master icon to fill ~85% height
        target_bh = int(bh * 0.85)
        master_banner = master_img.resize((target_bh, target_bh), Image.Resampling.LANCZOS)
        
        bx = (bw - target_bh) // 2
        by = (bh - target_bh) // 2
        
        banner_bg.paste(master_banner, (bx, by), master_banner)
        banner_bg.save(os.path.join(folder_path, "app_banner.png"), "PNG")
        print(f"[+] Saved TV Banner: {folder_name}/app_banner.png")

if __name__ == "__main__":
    build_all_assets()
