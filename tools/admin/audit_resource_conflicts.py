#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Forensic audit of resource resolution, overrides, and AAPT2 output
"""
import os
import subprocess
import zipfile
import xml.etree.ElementTree as ET

AAPT2 = r"C:\Users\kilic\AppData\Local\Android\Sdk\build-tools\34.0.0\aapt2.exe"
APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"
MANIFEST_MAIN = r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\AndroidManifest.xml"
MERGED_MANIFEST = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\intermediates\merged_manifests\release\processReleaseManifest\AndroidManifest.xml"

def dump_aapt_badging():
    cmd = [AAPT2, "dump", "badging", APK_PATH]
    res = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8")
    for line in res.stdout.splitlines():
        if "application:" in line or "launchable-activity:" in line or "leanback" in line or "icon" in line:
            print("  ", line)

def inspect_manifests():
    print("\n--- 1. MANIFESTS INSPECTION ---")
    for name, p in [("src/main/AndroidManifest.xml", MANIFEST_MAIN), ("Merged Release Manifest", MERGED_MANIFEST)]:
        if os.path.exists(p):
            tree = ET.parse(p)
            root = tree.getroot()
            app = root.find("application")
            ns = "{http://schemas.android.com/apk/res/android}"
            print(f"\n{name}:")
            print(f"  <application> android:icon = {app.get(f'{ns}icon')}")
            print(f"  <application> android:roundIcon = {app.get(f'{ns}roundIcon')}")
            print(f"  <application> android:banner = {app.get(f'{ns}banner')}")
            
            for act in app.findall("activity"):
                act_name = act.get(f"{ns}name")
                act_icon = act.get(f"{ns}icon")
                act_round = act.get(f"{ns}roundIcon")
                act_banner = act.get(f"{ns}banner")
                cats = [c.get(f"{ns}name") for c in act.findall(".//category")]
                if "android.intent.category.LAUNCHER" in cats or "android.intent.category.LEANBACK_LAUNCHER" in cats:
                    print(f"  <activity {act_name}>:")
                    print(f"     icon = {act_icon}")
                    print(f"     roundIcon = {act_round}")
                    print(f"     banner = {act_banner}")
                    print(f"     categories = {cats}")

def find_all_matching_resources():
    print("\n--- 2. ALL MATCHING RESOURCE FILES IN PROJECT ---")
    res_root = r"D:\Masaüstü\KaynanamTV-IPTV\app\src"
    target_names = [
        "ic_launcher_vault",
        "ic_launcher_vault_round",
        "ic_launcher_tv",
        "ic_launcher_tv_round",
        "ic_launcher_foreground",
        "ic_launcher_foreground_img",
        "ic_launcher_background_img",
        "app_banner"
    ]
    for root, dirs, files in os.walk(res_root):
        for f in files:
            base = os.path.splitext(f)[0]
            if base in target_names or f in target_names:
                full = os.path.join(root, f)
                rel = os.path.relpath(full, r"D:\Masaüstü\KaynanamTV-IPTV")
                print(f"  {rel} ({os.path.getsize(full)} bytes)")

if __name__ == "__main__":
    print("--- AAPT2 DUMP BADGING ---")
    dump_aapt_badging()
    inspect_manifests()
    find_all_matching_resources()
