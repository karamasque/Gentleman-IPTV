#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Visual & Geometric Launcher Icon Audit
Analyzes:
1. Foreground artwork geometry, alpha bbox, center of mass, transparent background verification
2. Adaptive XML configs
3. Circle and Squircle clipping masks
4. AndroidManifest references (TV banner, icon, roundIcon)
5. Legacy mipmap consistency
"""

import os
import sys
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

RES_DIR = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res"
MANIFEST_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\AndroidManifest.xml"

def analyze_foreground():
    fg_path = os.path.join(RES_DIR, "drawable", "ic_launcher_foreground_img.png")
    if not os.path.exists(fg_path):
        print(f"ERROR: {fg_path} not found")
        return
    
    img = Image.open(fg_path).convert("RGBA")
    w, h = img.size
    print(f"--- 1. FOREGROUND PNG ANALYSIS ({w}x{h}) ---")
    
    # 1. Alpha Bounding Box & Center of Mass
    min_x, min_y, max_x, max_y = w, h, 0, 0
    total_alpha = 0
    weighted_x = 0
    weighted_y = 0
    
    has_inner_background = False
    corner_pixels = []
    
    for y in range(h):
        for x in range(w):
            r, g, b, a = img.getpixel((x, y))
            if a > 0:
                if x < min_x: min_x = x
                if x > max_x: max_x = x
                if y < min_y: min_y = y
                if y > max_y: max_y = y
                total_alpha += a
                weighted_x += x * a
                weighted_y += y * a
            
            # Check outer margins for transparency
            if (x < 30 or x > w - 30) and (y < 30 or y > h - 30):
                corner_pixels.append(a)

    bbox_w = max_x - min_x + 1
    bbox_h = max_y - min_y + 1
    geo_cx = (min_x + max_x) / 2.0
    geo_cy = (min_y + max_y) / 2.0
    com_x = weighted_x / total_alpha if total_alpha > 0 else 0
    com_y = weighted_y / total_alpha if total_alpha > 0 else 0
    
    corner_non_zero = sum(1 for a in corner_pixels if a > 0)
    
    print(f"Canvas: {w}x{h} px")
    print(f"Artwork BBox: [{min_x}, {min_y}] to [{max_x}, {max_y}] -> {bbox_w}x{bbox_h} px")
    print(f"Geometric Center: ({geo_cx:.1f}, {geo_cy:.1f}) | Canvas Center: ({w/2}, {h/2})")
    print(f"Visual Center of Mass: ({com_x:.1f}, {com_y:.1f})")
    print(f"Corner Non-Zero Alpha Count (out of {len(corner_pixels)}): {corner_non_zero}")
    print(f"Foreground Is Transparent Background (Logo Only): {'YES' if corner_non_zero == 0 else 'NO'}")
    print(f"Double Composition / Outer Tile Present: {'NO' if corner_non_zero == 0 else 'YES'}")
    
    # 2. Adaptive Mask Clipping Test (Safe zone: diameter ~66-72% of 512 = 340-370px)
    # Circle mask test: radius = 165 px (diameter 330px centered at 256, 256)
    # Squircle mask test: rounded rect centered at 256, 256
    circle_clipped_pixels = 0
    squircle_clipped_pixels = 0
    
    mask_circle = Image.new("L", (w, h), 0)
    draw_c = ImageDraw.Draw(mask_circle)
    # Standard circle mask (diameter = 368px -> radius 184px, 72% safe zone)
    draw_c.ellipse([(256 - 184, 256 - 184), (256 + 184, 256 + 184)], fill=255)
    
    mask_squircle = Image.new("L", (w, h), 0)
    draw_s = ImageDraw.Draw(mask_squircle)
    # Standard squircle mask (368x368 with 90px corner radius)
    draw_s.rounded_rectangle([(256 - 184, 256 - 184), (256 + 184, 256 + 184)], radius=90, fill=255)
    
    for y in range(h):
        for x in range(w):
            a = img.getpixel((x, y))[3]
            if a > 50:
                if mask_circle.getpixel((x, y)) == 0:
                    circle_clipped_pixels += 1
                if mask_squircle.getpixel((x, y)) == 0:
                    squircle_clipped_pixels += 1

    print(f"Circle Mask Clipped Pixels (>72% safe zone): {circle_clipped_pixels}")
    print(f"Squircle Mask Clipped Pixels (>72% safe zone): {squircle_clipped_pixels}")
    print(f"Circle Mask Test: {'PASS' if circle_clipped_pixels < 200 else 'CLIPPED'}")
    print(f"Squircle Mask Test: {'PASS' if squircle_clipped_pixels < 200 else 'CLIPPED'}")

def analyze_xml():
    print("\n--- 2. ADAPTIVE ICON XML CONFIGS ---")
    xml_path = os.path.join(RES_DIR, "mipmap-anydpi-v26", "ic_launcher_vault.xml")
    xml_round_path = os.path.join(RES_DIR, "mipmap-anydpi-v26", "ic_launcher_vault_round.xml")
    for p in [xml_path, xml_round_path]:
        if os.path.exists(p):
            with open(p, "r", encoding="utf-8") as f:
                content = f.read().strip()
                print(f"File: {os.path.basename(p)}")
                print(f"  Content:\n{content}\n")
        else:
            print(f"File: {p} NOT FOUND")

def analyze_manifest():
    print("--- 3. ANDROID MANIFEST ICON & TV BANNER AUDIT ---")
    tree = ET.parse(MANIFEST_PATH)
    root = tree.getroot()
    app = root.find("application")
    
    android_ns = "{http://schemas.android.com/apk/res/android}"
    icon = app.get(f"{android_ns}icon")
    round_icon = app.get(f"{android_ns}roundIcon")
    banner = app.get(f"{android_ns}banner")
    
    print(f"android:icon = {icon}")
    print(f"android:roundIcon = {round_icon}")
    print(f"android:banner = {banner}")
    
    # Check TV launcher intent filter
    tv_launcher_found = False
    for activity in app.findall("activity"):
        for intent_filter in activity.findall("intent-filter"):
            for cat in intent_filter.findall("category"):
                if cat.get(f"{android_ns}name") == "android.intent.category.LEANBACK_LAUNCHER":
                    tv_launcher_found = True
                    act_name = activity.get(f"{android_ns}name")
                    print(f"Leanback Launcher Activity: {act_name}")
    print(f"Leanback Launcher Intent Filter Present: {tv_launcher_found}")

def analyze_banner():
    banner_path = os.path.join(RES_DIR, "drawable", "app_banner.png")
    if os.path.exists(banner_path):
        img = Image.open(banner_path)
        print(f"\n--- 4. TV BANNER (app_banner.png) ---")
        print(f"Banner Size: {img.size} (Android TV Leanback standard is 320x180)")
    else:
        print(f"\n--- 4. TV BANNER (app_banner.png) NOT FOUND ---")

if __name__ == "__main__":
    analyze_foreground()
    analyze_xml()
    analyze_manifest()
    analyze_banner()
