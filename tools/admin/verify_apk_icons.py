#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Direct APK Forensic Verification of Launcher Icons and Banners
"""
import zipfile
import os
import sys

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"

def verify_apk():
    if not os.path.exists(APK_PATH):
        print(f"ERROR: {APK_PATH} not found")
        return False
    
    print(f"Inspecting APK: {APK_PATH} (Size: {os.path.getsize(APK_PATH) / (1024*1024):.2f} MB)")
    
    with zipfile.ZipFile(APK_PATH, 'r') as z:
        namelist = z.namelist()
        
        # Check presence of icons
        icons_found = [n for n in namelist if 'ic_launcher' in n or 'app_banner' in n]
        print(f"\n--- TOTAL MATCHING ICON/BANNER ASSETS IN APK: {len(icons_found)} ---")
        for f in sorted(icons_found):
            info = z.getinfo(f)
            print(f"  - {f} ({info.file_size} bytes)")
            
        print("\n--- CRITICAL ASSET CHECKLIST ---")
        checks = {
            "TV Banner (res/drawable/app_banner.png)": any("app_banner" in n for n in namelist),
            "TV Launcher Icon (ic_launcher_tv)": any("ic_launcher_tv" in n for n in namelist),
            "Phone Vault Icon (ic_launcher_vault)": any("ic_launcher_vault" in n for n in namelist),
            "Adaptive AnyDPI-v26 XMLs": any("anydpi" in n and "ic_launcher" in n for n in namelist),
            "Legacy Mipmaps (mdpi..xxxhdpi)": any("mipmap-xxxhdpi" in n for n in namelist),
        }
        for name, status in checks.items():
            print(f"  {name}: {'PRESENT (PASS)' if status else 'MISSING (FAIL)'}")
            
    return True

if __name__ == "__main__":
    verify_apk()
