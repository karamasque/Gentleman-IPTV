#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Definitive Uncropped Launcher Assets Generator
Applies 100% Safe-Zone Scaling:
- Zero cropping across all masks (Circle, Squircle, Rounded Square, Teardrop)
- Complete aunt artwork with full head, headscarf, sunglasses, remote and text visible
- Multi-density TV banner, TV launcher icons, and phone adaptive + legacy icons
"""

import os
import math
from PIL import Image, ImageDraw, ImageFilter

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"

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

def generate_banner(w, h, logo_tight):
    tw, th = logo_tight.size
    banner_bg = create_radial_gradient(w, h, (82, 16, 133), (28, 4, 48))
    draw_b = ImageDraw.Draw(banner_bg)
    draw_b.rectangle([(0, 0), (w - 1, h - 1)], outline=(140, 40, 210, 80), width=max(2, int(w*0.003)))
    
    target_bh = int(h * 0.78)
    target_bw = int(tw * (target_bh / th))
    max_bw = int(w * 0.85)
    if target_bw > max_bw:
        target_bw = max_bw
        target_bh = int(th * (target_bw / tw))
        
    logo_banner = logo_tight.resize((target_bw, target_bh), Image.Resampling.LANCZOS)
    
    # Shadow
    shadow_offset = max(2, int(h * 0.012))
    shadow = Image.new("RGBA", (target_bw, target_bh), (0, 0, 0, 0))
    for y in range(target_bh):
        for x in range(target_bw):
            a = logo_banner.getpixel((x, y))[3]
            if a > 0:
                shadow.putpixel((x, y), (0, 0, 0, int(a * 0.45)))
    shadow = shadow.filter(ImageFilter.GaussianBlur(radius=max(2, int(h * 0.015))))
    
    bx = (w - target_bw) // 2
    by = (h - target_bh) // 2
    
    banner_bg.paste(shadow, (bx, by + shadow_offset), shadow)
    banner_bg.paste(logo_banner, (bx, by), logo_banner)
    return banner_bg

def generate_all():
    # Load base artwork and get tight logo crop
    fg_orig = Image.open(os.path.join(RES_DIR, "drawable", "ic_launcher_foreground_img.png")).convert("RGBA")
    bbox = fg_orig.getbbox()
    logo_tight = fg_orig.crop(bbox)
    tw, th = logo_tight.size
    print(f"Tight aunt artwork: {tw}x{th} px")

    # 1. GENERATE CANONICAL ADAPTIVE FOREGROUND (ic_launcher_foreground_img.png: 512x512)
    # Scaled to 276x280 px (100% safe zone inside 341px circle, 0% crop)
    nw, nh = 276, 280
    res_fg_logo = logo_tight.resize((nw, nh), Image.Resampling.LANCZOS)
    
    fg_canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    pos_x = (512 - nw) // 2
    pos_y = (512 - nh) // 2
    fg_canvas.paste(res_fg_logo, (pos_x, pos_y), res_fg_logo)
    
    fg_out_path = os.path.join(RES_DIR, "drawable", "ic_launcher_foreground_img.png")
    fg_canvas.save(fg_out_path, "PNG")
    print(f"[+] Saved Uncropped Adaptive Foreground: {fg_out_path} (Logo {nw}x{nh} at [{pos_x}, {pos_y}])")

    # 2. GENERATE MULTI-DENSITY TV BANNERS
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
        banner_img = generate_banner(bw, bh, logo_tight)
        banner_img.save(os.path.join(folder_path, "app_banner.png"), "PNG")
        print(f"[+] Saved TV Banner to {folder_name}/app_banner.png ({bw}x{bh})")

    # 3. GENERATE MULTI-DENSITY LEGACY & TV ICONS
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
        
        # --- A. Standard Phone Squircle (ic_launcher_vault.png) ---
        bg_tile = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_sq = ImageDraw.Draw(bg_tile)
        r_sq = int(size * 0.22)
        draw_sq.rounded_rectangle([(0, 0), (size-1, size-1)], radius=r_sq, fill=(67, 8, 107, 255))
        
        # 60% safe-scale for squircle tile to avoid zoom/crop
        lw_sq = int(size * 0.60)
        lh_sq = int(lw_sq * (th / tw))
        logo_res_sq = logo_tight.resize((lw_sq, lh_sq), Image.Resampling.LANCZOS)
        
        pos_x = (size - lw_sq) // 2
        pos_y = (size - lh_sq) // 2
        bg_tile.paste(logo_res_sq, (pos_x, pos_y), logo_res_sq)
        bg_tile.save(os.path.join(folder, "ic_launcher_vault.png"), "PNG")
        
        # --- B. Standard Phone Round (ic_launcher_vault_round.png) ---
        bg_rd = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_rd = ImageDraw.Draw(bg_rd)
        draw_rd.ellipse([(0, 0), (size-1, size-1)], fill=(67, 8, 107, 255))
        
        # 55% safe-scale for circle tile
        lw_rd = int(size * 0.55)
        lh_rd = int(lw_rd * (th / tw))
        logo_res_rd = logo_tight.resize((lw_rd, lh_rd), Image.Resampling.LANCZOS)
        pos_rx = (size - lw_rd) // 2
        pos_ry = (size - lh_rd) // 2
        bg_rd.paste(logo_res_rd, (pos_rx, pos_ry), logo_res_rd)
        bg_rd.save(os.path.join(folder, "ic_launcher_vault_round.png"), "PNG")

        # --- C. TV Specific Icon (ic_launcher_tv.png & ic_launcher_tv_round.png) ---
        tv_sq = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_tv = ImageDraw.Draw(tv_sq)
        draw_tv.rounded_rectangle([(0, 0), (size-1, size-1)], radius=r_sq, fill=(78, 13, 124, 255), outline=(150, 45, 220, 200), width=max(1, int(size*0.02)))
        
        lw_tv = int(size * 0.65)
        lh_tv = int(lw_tv * (th / tw))
        logo_res_tv = logo_tight.resize((lw_tv, lh_tv), Image.Resampling.LANCZOS)
        pos_tvx = (size - lw_tv) // 2
        pos_tvy = (size - lh_tv) // 2
        tv_sq.paste(logo_res_tv, (pos_tvx, pos_tvy), logo_res_tv)
        tv_sq.save(os.path.join(folder, "ic_launcher_tv.png"), "PNG")
        
        # TV round
        tv_rd = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        draw_tvrd = ImageDraw.Draw(tv_rd)
        draw_tvrd.ellipse([(0, 0), (size-1, size-1)], fill=(78, 13, 124, 255), outline=(150, 45, 220, 200), width=max(1, int(size*0.02)))
        
        lw_tvrd = int(size * 0.58)
        lh_tvrd = int(lw_tvrd * (th / tw))
        logo_res_tvrd = logo_tight.resize((lw_tvrd, lh_tvrd), Image.Resampling.LANCZOS)
        pos_tvrdx = (size - lw_tvrd) // 2
        pos_tvrdy = (size - lh_tvrd) // 2
        tv_rd.paste(logo_res_tvrd, (pos_tvrdx, pos_tvrdy), logo_res_tvrd)
        tv_rd.save(os.path.join(folder, "ic_launcher_tv_round.png"), "PNG")

    print("[+] Generated uncropped legacy and TV mipmap icons for all 5 densities (mdpi..xxxhdpi)")

    # 4. XML Adaptive Icons for API 26+ (mipmap-anydpi-v26)
    anydpi_dir = os.path.join(RES_DIR, "mipmap-anydpi-v26")
    os.makedirs(anydpi_dir, exist_ok=True)
    
    tv_xml_content = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background_img"/>
    <foreground android:drawable="@drawable/ic_launcher_foreground"/>
</adaptive-icon>
"""
    with open(os.path.join(anydpi_dir, "ic_launcher_tv.xml"), "w", encoding="utf-8") as f:
        f.write(tv_xml_content)
    with open(os.path.join(anydpi_dir, "ic_launcher_tv_round.xml"), "w", encoding="utf-8") as f:
        f.write(tv_xml_content)
    print("[+] Created TV adaptive icon XMLs in mipmap-anydpi-v26")

if __name__ == "__main__":
    generate_all()
