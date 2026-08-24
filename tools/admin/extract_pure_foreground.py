#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Extract pure, unboxed, seamless foreground artwork from KaynanamTV_Master_Icon_1024.png
Zero double composition, pure transparent background around the TV and typography.
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

def extract_seamless_foreground():
    master = Image.open(MASTER_PATH).convert("RGBA")
    w, h = master.size
    
    # 1. Background color inside the master is purple gradient:
    # Top corners and edges are dark purple/black.
    # The TV antenna goes up to y=25.
    # The TV frame has an outer purple/lavender bezel.
    # Let's create an alpha mask that includes the complete TV frame, antenna, aunt, thumbs-up, remote, and KAYNANAM TV banner.
    
    # In master, the outer bezel of the squircle is at radius r~160 from corners.
    # If we mask the background to match the adaptive background perfectly (using radial gradient)
    # OR make the outer square fade smoothly into transparency:
    
    # Let's create a smooth rounded mask for the master card so it blends seamlessly:
    # The master card is 1024x1024 with rounded corners (radius=230px).
    # Outside the rounded squircle is pure black/transparent.
    # If we apply a squircle mask with anti-aliased edge to the master image:
    card_mask = Image.new("L", (w, h), 0)
    draw_m = ImageDraw.Draw(card_mask)
    draw_m.rounded_rectangle([(15, 15), (w - 16, h - 16)], radius=220, fill=255)
    
    # Feather the card mask edge slightly for seamless alpha blending
    card_mask = card_mask.filter(ImageFilter.GaussianBlur(radius=2))
    
    master_masked = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    master_masked.paste(master, (0, 0), card_mask)
    
    # 2. Scale master to safe zone:
    # Target dimension: 320x320 px (fits 66dp safe zone perfectly)
    target_dim = 320
    master_scaled = master_masked.resize((target_dim, target_dim), Image.Resampling.LANCZOS)
    
    # 3. Create 512x512 transparent foreground
    fg_512 = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    pos_x = (512 - target_dim) // 2
    pos_y = (512 - target_dim) // 2
    fg_512.paste(master_scaled, (pos_x, pos_y), master_scaled)
    
    # 4. Create matching background (512x512)
    # Master card color is (67, 8, 107) -> sample exact center and edge colors
    bg_512 = create_radial_gradient(512, 512, (74, 12, 118), (32, 4, 54))
    
    # Save foreground and background drawables
    fg_path = os.path.join(RES_DIR, "drawable", "ic_launcher_foreground_img.png")
    bg_path = os.path.join(RES_DIR, "drawable", "ic_launcher_background_img.png")
    
    fg_512.save(fg_path, "PNG")
    bg_512.save(bg_path, "PNG")
    print(f"[+] Saved Foreground: {fg_path}")
    print(f"[+] Saved Background: {bg_path}")
    
    # 5. Render Previews for 4 Adaptive Masks
    cx, cy = 256.0, 256.0
    r = 170.6
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
        p = os.path.join(PREVIEW_DIR, f"final_preview_{mask_name.lower()}.png")
        masked.save(p, "PNG")
        print(f"[+] Saved Preview: {p}")
        
    # 6. Legacy and TV Mipmaps
    densities = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}
    for dname, size in densities.items():
        folder = os.path.join(RES_DIR, f"mipmap-{dname}")
        os.makedirs(folder, exist_ok=True)
        
        # Phone Squircle (ic_launcher_vault.png)
        sq_icon = master_masked.resize((size, size), Image.Resampling.LANCZOS)
        sq_icon.save(os.path.join(folder, "ic_launcher_vault.png"), "PNG")
        
        # Phone Round (ic_launcher_vault_round.png)
        rd_mask = Image.new("L", (size, size), 0)
        draw_rd = ImageDraw.Draw(rd_mask)
        draw_rd.ellipse([(0, 0), (size-1, size-1)], fill=255)
        
        rd_size = int(size * 0.92)
        rd_master = master_masked.resize((rd_size, rd_size), Image.Resampling.LANCZOS)
        rd_bg = create_radial_gradient(size, size, (74, 12, 118), (32, 4, 54))
        rd_pos = (size - rd_size) // 2
        rd_bg.paste(rd_master, (rd_pos, rd_pos), rd_master)
        
        rd_final = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        rd_final.paste(rd_bg, (0, 0), rd_mask)
        rd_final.save(os.path.join(folder, "ic_launcher_vault_round.png"), "PNG")
        
        # TV icons
        sq_icon.save(os.path.join(folder, "ic_launcher_tv.png"), "PNG")
        rd_final.save(os.path.join(folder, "ic_launcher_tv_round.png"), "PNG")
        
    print("[+] Generated legacy and TV icons in all 5 densities")

if __name__ == "__main__":
    extract_seamless_foreground()
