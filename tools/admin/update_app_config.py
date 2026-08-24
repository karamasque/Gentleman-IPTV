#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Firestore App Config Updater
Updates the canonical `config/app_config` document in Firestore.
"""

import os
import sys
import time

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

import firebase_admin
from firebase_admin import credentials, firestore

FIREBASE_CREDENTIALS_FILE = "serviceAccountKey.json"

def init_firebase():
    candidates = [
        os.path.join(os.path.dirname(os.path.abspath(__file__)), FIREBASE_CREDENTIALS_FILE),
        r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json",
        r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json",
        os.path.join(os.path.expanduser("~"), "Desktop", "Yeni klasör", FIREBASE_CREDENTIALS_FILE),
        os.path.join(os.path.expanduser("~"), "Desktop", FIREBASE_CREDENTIALS_FILE),
    ]

    cred_path = None
    for p in candidates:
        if os.path.exists(p):
            cred_path = p
            break

    if not cred_path:
        print(f"HATA: '{FIREBASE_CREDENTIALS_FILE}' bulunamadı!")
        return None

    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        return firestore.client()
    except Exception as e:
        print(f"Firebase başlatma hatası: {e}")
        return None

def update_config(
    latest_code=103,
    latest_name="1.1.3",
    min_supported_code=103,
    force_update=True,
    apk_url="https://github.com/karamasque/Gentleman-IPTV/releases/download/v1.1.3/KaynanamTV.apk",
    release_notes="KaynanamTV v1.1.3 zorunlu güncelleme."
):
    db = init_firebase()
    if not db:
        return False

    doc_ref = db.collection("config").document("app_config")
    payload = {
        "latestVersionCode": latest_code,
        "latestVersionName": latest_name,
        "minimumSupportedVersionCode": min_supported_code,
        "forceUpdate": force_update,
        "apkDownloadUrl": apk_url,
        "releaseNotes": release_notes,
        "updatedAt": int(time.time() * 1000)
    }

    try:
        doc_ref.set(payload, merge=True)
        print("✅ Firestore config/app_config başarıyla güncellendi:")
        for k, v in payload.items():
            print(f"   - {k}: {v}")
        return True
    except Exception as e:
        print(f"❌ Firestore güncelleme hatası: {e}")
        return False

if __name__ == "__main__":
    update_config()
