#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV 1.0.66 — Live User Entitlement Analyzer & Backup Tool (READ-ONLY)
"""

import os
import sys
import json
import time
from datetime import datetime

DEFAULT_KEY_PATHS = [
    os.environ.get("FIREBASE_SERVICE_ACCOUNT_KEY"),
    "serviceAccountKey.json",
    r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json",
    r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json"
]

def init_firebase():
    import firebase_admin
    from firebase_admin import credentials, firestore, auth

    if not firebase_admin._apps:
        cred_path = None
        for path in DEFAULT_KEY_PATHS:
            if path and os.path.exists(path):
                cred_path = path
                break

        if not cred_path:
            print("[HATA] Firebase credentials dosyası bulunamadı.")
            sys.exit(1)

        print(f"[*] Firebase Admin SDK başlatılıyor: '{cred_path}'")
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
    return firestore.client(), auth

def main():
    print("=" * 80)
    print(" KAYNANAMTV 1.0.66 — CANLI READ-ONLY KULLANICI ANALİZİ VE YEDEKLEME")
    print("=" * 80)

    db, auth_module = init_firebase()
    now_ms = int(time.time() * 1000)

    # 1. Fetch Auth users
    print("[1/4] Firebase Authentication kullanıcıları okunuyor...")
    auth_users = {}
    page = auth_module.list_users()
    while page:
        for u in page.users:
            auth_users[u.uid] = {
                "email": u.email or "",
                "created_at": u.user_metadata.creation_timestamp if u.user_metadata else 0,
                "disabled": u.disabled
            }
        page = page.get_next_page()

    total_auth = len(auth_users)
    print(f"    -> Firebase Auth Toplam Kullanıcı: {total_auth}")

    # 2. Fetch Firestore users
    print("[2/4] Firestore 'users' koleksiyonu okunuyor (READ-ONLY)...")
    users_ref = db.collection("users")
    docs = list(users_ref.stream())
    total_firestore = len(docs)
    print(f"    -> Firestore 'users' Toplam Belge: {total_firestore}")

    firestore_users = {}
    for doc in docs:
        firestore_users[doc.id] = doc.to_dict() or {}

    # 3. Categorize
    print("[3/4] Kullanıcılar sınıflandırılıyor...")

    active_lifetime = 0
    active_yearly = 0
    active_trial = 0
    expired_trial = 0
    legacy_free_premium = 0
    unknown_premium = 0
    free_users = 0
    already_transitioned = 0
    corrupted_count = 0
    eligible_for_transition = 0
    paid_preserved = 0

    backup_records = []

    for uid, d in firestore_users.items():
        is_prem = d.get("isPremium", False)
        plan = (d.get("premiumPlan") or ("TRIAL" if is_prem else "FREE")).upper()
        trial_used = d.get("trialUsed", False)
        trial_exp = d.get("trialExpiresAt", 0)
        prem_exp = d.get("premiumExpiresAt", 0)
        transitioned = d.get("transitionTrialGranted", False)

        # Classification
        if plan == "LIFETIME":
            active_lifetime += 1
            paid_preserved += 1
        elif plan == "YEARLY" and prem_exp > now_ms:
            active_yearly += 1
            paid_preserved += 1
        elif plan == "TRIAL" and trial_exp > now_ms:
            active_trial += 1
        elif plan == "TRIAL" and trial_exp <= now_ms:
            expired_trial += 1
        elif is_prem and plan not in ["LIFETIME", "YEARLY", "TRIAL"]:
            legacy_free_premium += 1
        elif not is_prem:
            free_users += 1
        else:
            unknown_premium += 1

        # Check eligibility for 7-day transition
        if transitioned:
            already_transitioned += 1
        elif plan in ["LIFETIME"] or (plan == "YEARLY" and prem_exp > now_ms):
            # Paid premium -> DO NOT DOWNGRADE
            pass
        else:
            eligible_for_transition += 1

        if not d.get("email") and not d.get("userId"):
            corrupted_count += 1

        # Build clean backup record
        backup_records.append({
            "uid": uid,
            "email": d.get("email", auth_users.get(uid, {}).get("email", "")),
            "createdAt": d.get("createdAt", 0),
            "isPremium": is_prem,
            "premiumPlan": plan,
            "premiumStartedAt": d.get("premiumStartedAt", 0),
            "premiumExpiresAt": prem_exp,
            "trialUsed": trial_used,
            "trialStartedAt": d.get("trialStartedAt", 0),
            "trialExpiresAt": trial_exp,
            "transitionTrialGranted": transitioned,
            "entitlementVersion": d.get("entitlementVersion", 1),
            "role": d.get("role", "USER"),
            "isAdmin": d.get("isAdmin", False)
        })

    auth_missing_in_firestore = len(set(auth_users.keys()) - set(firestore_users.keys()))
    firestore_orphan_in_auth = len(set(firestore_users.keys()) - set(auth_users.keys()))
    total_premium = active_lifetime + active_yearly + active_trial + legacy_free_premium + unknown_premium

    # 4. Save & Verify Backup
    print("[4/4] Tam JSON yedeği oluşturuluyor...")
    os.makedirs("backups", exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_file = os.path.abspath(os.path.join("backups", f"users_backup_{timestamp}.json"))

    with open(backup_file, "w", encoding="utf-8") as f:
        json.dump(backup_records, f, indent=2, ensure_ascii=False)

    backup_size = os.path.getsize(backup_file)
    with open(backup_file, "r", encoding="utf-8") as f:
        verified_json = json.load(f)
    backup_pass = (backup_size > 0) and (len(verified_json) == total_firestore)

    # Summary Output
    print("\n" + "=" * 80)
    print(" CANLI FIREBASE ANALİZ VE DOĞRULAMA SONUÇLARI")
    print("=" * 80)
    print(f"Firebase Auth                       : {total_auth}")
    print(f"Firestore Users                     : {total_firestore}")
    print(f"Toplam Premium                      : {total_premium}")
    print(f"Free                                : {free_users}")
    print(f"Active Trial                        : {active_trial}")
    print(f"Expired Trial                       : {expired_trial}")
    print(f"YEARLY (Ücretli)                    : {active_yearly}")
    print(f"LIFETIME (Ücretli)                  : {active_lifetime}")
    print(f"Legacy/Ücretsiz Premium             : {legacy_free_premium}")
    print(f"Unknown Premium                     : {unknown_premium}")
    print(f"Auth -> Firestore eksik              : {auth_missing_in_firestore}")
    print(f"Firestore -> Auth yetim              : {firestore_orphan_in_auth}")
    print(f"Bozuk/Eksik entitlement             : {corrupted_count}")
    print(f"Transition Trial daha önce verilmiş : {already_transitioned}")
    print(f"--------------------------------------------------------------------------------")
    print(f"7 gün verilecek                     : {eligible_for_transition}")
    print(f"Atlanacak                           : {already_transitioned + paid_preserved}")
    print(f"Ücretli Premium korunacak           : {paid_preserved}")
    print(f"--------------------------------------------------------------------------------")
    print(f"Backup                              : {'PASS' if backup_pass else 'FAIL'}")
    print(f"Backup gerçek yolu                  : {backup_file}")
    print(f"Backup kullanıcı sayısı             : {len(verified_json)}")
    print(f"Backup boyutu                       : {backup_size:,} bytes")
    print("=" * 80)

if __name__ == "__main__":
    main()
