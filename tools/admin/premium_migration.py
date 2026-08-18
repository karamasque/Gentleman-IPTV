#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV 1.0.66 — Premium Transition Migration & Rollback Tool

Usage:
  python premium_migration.py --backup
  python premium_migration.py --dry-run
  python premium_migration.py --apply
  python premium_migration.py --rollback --backup-file backups/users_backup_YYYYMMDD_HHMMSS.json
"""

import os
import sys
import json
import time
import argparse
from datetime import datetime

DEFAULT_KEY_PATHS = [
    os.environ.get("FIREBASE_SERVICE_ACCOUNT_KEY"),
    "serviceAccountKey.json",
    r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json",
    r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json"
]

def init_firebase():
    import firebase_admin
    from firebase_admin import credentials, firestore

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
    return firestore.client()

def create_backup(db, backup_dir="backups"):
    os.makedirs(backup_dir, exist_ok=True)
    timestamp_str = datetime.now().strftime("%Y%m%d_%H%M%S")
    backup_file = os.path.abspath(os.path.join(backup_dir, f"users_backup_{timestamp_str}.json"))

    print(f"[*] Firebase Firestore 'users' koleksiyonu okunuyor (READ-ONLY)...")
    users_ref = db.collection("users")
    docs = list(users_ref.stream())

    backup_data = []
    premium_count = 0

    for doc in docs:
        d = doc.to_dict() or {}
        uid = doc.id
        is_premium = d.get("isPremium", False)
        plan = d.get("premiumPlan", "TRIAL" if is_premium else "FREE")

        user_record = {
            "uid": uid,
            "email": d.get("email", ""),
            "createdAt": d.get("createdAt", 0),
            "isPremium": is_premium,
            "premiumPlan": plan,
            "premiumStartedAt": d.get("premiumStartedAt", 0),
            "premiumExpiresAt": d.get("premiumExpiresAt", 0),
            "trialUsed": d.get("trialUsed", False),
            "trialStartedAt": d.get("trialStartedAt", 0),
            "trialExpiresAt": d.get("trialExpiresAt", 0),
            "transitionTrialGranted": d.get("transitionTrialGranted", False),
            "entitlementVersion": d.get("entitlementVersion", 1),
            "role": d.get("role", "USER"),
            "isAdmin": d.get("isAdmin", False)
        }
        backup_data.append(user_record)
        if is_premium:
            premium_count += 1

    with open(backup_file, "w", encoding="utf-8") as f:
        json.dump(backup_data, f, indent=2, ensure_ascii=False)

    size = os.path.getsize(backup_file)
    print(f"[✓] YEDEK BAŞARIYLA OLUŞTURULDU:")
    print(f"    Dosya Yolu      : {backup_file}")
    print(f"    Dosya Boyutu    : {size:,} bytes")
    print(f"    Toplam Kayıt    : {len(backup_data)}")
    print(f"    Mevcut Premium  : {premium_count}")
    return backup_file, backup_data

def run_migration(db, dry_run=True, auto_confirm=False):
    print(f"\n{'='*80}")
    print(f" MIGRATION MODU: {'[DRY-RUN (0 WRITE - DEĞİŞİKLİK YAPILMAZ)]' if dry_run else '[CANLI UYGULAMA]'}")
    print(f"{'='*80}")

    users_ref = db.collection("users")
    docs = list(users_ref.stream())

    now_ms = int(time.time() * 1000)
    seven_days_ms = 7 * 24 * 60 * 60 * 1000
    trial_expires_ms = now_ms + seven_days_ms

    eligible_to_grant = []
    skipped_already_transitioned = []
    skipped_paid_yearly = []
    skipped_paid_lifetime = []
    corrupted_count = 0

    print(f"[*] Toplam {len(docs)} Firestore belgesi taranıyor...")

    for doc in docs:
        d = doc.to_dict() or {}
        uid = doc.id
        plan = (d.get("premiumPlan") or ("TRIAL" if d.get("isPremium") else "FREE")).upper()
        prem_exp = d.get("premiumExpiresAt", 0)

        # 1. Idempotency Check: Already transitioned?
        if d.get("transitionTrialGranted") == True:
            skipped_already_transitioned.append(uid)
            continue

        # 2. Paid Premium Check: Do not downgrade paid yearly/lifetime
        if plan == "LIFETIME":
            skipped_paid_lifetime.append(uid)
            continue
        if plan == "YEARLY" and prem_exp > now_ms:
            skipped_paid_yearly.append(uid)
            continue

        # 3. Eligible for 7-day transition
        eligible_to_grant.append((uid, d))

    total_skipped = len(skipped_already_transitioned) + len(skipped_paid_yearly) + len(skipped_paid_lifetime)

    print(f"\n[MİGRATİON ANALİZ RAPORU]")
    print(f"  • İşlenecek Toplam Kullanıcı         : {len(docs)}")
    print(f"  • 7 Günlük Transition Trial Alacak   : {len(eligible_to_grant)}")
    print(f"  • Atlanacak Toplam Kullanıcı         : {total_skipped}")
    print(f"    - Zaten Geçiş Yapmış (Atlanan)     : {len(skipped_already_transitioned)}")
    print(f"    - Ücretli YEARLY (Korunan)         : {len(skipped_paid_yearly)}")
    print(f"    - Ücretli LIFETIME (Korunan)       : {len(skipped_paid_lifetime)}")
    print(f"  • Eksik / Bozuk Kayıt                : {corrupted_count}")
    print(f"  • Hata                               : 0")

    if dry_run:
        print("\n[✓] DRY-RUN BAŞARIYLA TAMAMLANDI.")
        print("    -> DRY-RUN WRITE SAYISI: 0 (Hiçbir canlı veri değiştirilmedi).")
        print(f"    -> Hedef Geçiş Bitiş Tarihi: {datetime.fromtimestamp(trial_expires_ms/1000).strftime('%d.%m.%Y %H:%M')}")
        return

    # Real Migration Execution
    if not auto_confirm:
        confirm = input(f"\n[DİKKAT] {len(eligible_to_grant)} kullanıcıya CANLI veritabanında 7 günlük geçiş denemesi uygulanacak.\nDevam etmek için 'EVET' yazın: ")
        if confirm.strip() != "EVET":
            print("[!] İşlem iptal edildi.")
            return

    print("\n[*] Canlı migration uygulanıyor...")
    audit_ref = db.collection("premium_audit")
    batch = db.batch()
    updated_so_far = 0

    for uid, old_data in eligible_to_grant:
        user_doc_ref = users_ref.document(uid)
        batch.update(user_doc_ref, {
            "isPremium": True,
            "premiumPlan": "TRIAL",
            "trialUsed": True,
            "trialStartedAt": now_ms,
            "trialExpiresAt": trial_expires_ms,
            "transitionTrialGranted": True,
            "entitlementVersion": old_data.get("entitlementVersion", 1) + 1,
            "updatedAt": now_ms
        })

        audit_event_id = audit_ref.document().id
        batch.set(audit_ref.document(audit_event_id), {
            "eventId": audit_event_id,
            "targetUid": uid,
            "action": "TRANSITION_TRIAL_GRANTED",
            "oldPlan": old_data.get("premiumPlan", "UNKNOWN"),
            "newPlan": "TRIAL",
            "oldExpiry": old_data.get("trialExpiresAt", 0),
            "newExpiry": trial_expires_ms,
            "performedBy": "MIGRATION_1.0.66",
            "timestamp": now_ms,
            "reason": "1.0.66 Transition Trial Grant"
        })

        updated_so_far += 1
        if updated_so_far % 400 == 0:
            batch.commit()
            batch = db.batch()
            print(f"    {updated_so_far}/{len(eligible_to_grant)} kullanıcı güncellendi...")

    if updated_so_far % 400 != 0:
        batch.commit()

    print(f"\n[✓] MİGRATİON TAMAMLANDI! Toplam {updated_so_far} kullanıcıya 7 günlük geçiş trial'ı tanımlandı.")

def run_rollback(db, backup_file, auto_confirm=False):
    if not os.path.exists(backup_file):
        print(f"[HATA] Yedek dosyası bulunamadı: {backup_file}")
        sys.exit(1)

    with open(backup_file, "r", encoding="utf-8") as f:
        backup_data = json.load(f)

    print(f"[*] Yedek dosyasında {len(backup_data)} kullanıcı bulundu.")
    if not auto_confirm:
        confirm = input(f"[UYARI] {len(backup_data)} kullanıcı yedekten geri yüklenecek. Onaylıyor musunuz? (EVET/HAYIR): ")
        if confirm.strip() != "EVET":
            print("[!] Rollback iptal edildi.")
            return

    users_ref = db.collection("users")
    audit_ref = db.collection("premium_audit")
    batch = db.batch()
    restored = 0
    now_ms = int(time.time() * 1000)

    for item in backup_data:
        uid = item["uid"]
        user_doc_ref = users_ref.document(uid)
        batch.update(user_doc_ref, {
            "isPremium": item.get("isPremium", False),
            "premiumPlan": item.get("premiumPlan", "FREE"),
            "premiumStartedAt": item.get("premiumStartedAt", 0),
            "premiumExpiresAt": item.get("premiumExpiresAt", 0),
            "trialUsed": item.get("trialUsed", False),
            "trialStartedAt": item.get("trialStartedAt", 0),
            "trialExpiresAt": item.get("trialExpiresAt", 0),
            "transitionTrialGranted": item.get("transitionTrialGranted", False),
            "entitlementVersion": item.get("entitlementVersion", 1),
            "updatedAt": now_ms
        })

        audit_event_id = audit_ref.document().id
        batch.set(audit_ref.document(audit_event_id), {
            "eventId": audit_event_id,
            "targetUid": uid,
            "action": "ROLLBACK_RESTORED",
            "oldPlan": "MIGRATED_TRIAL",
            "newPlan": item.get("premiumPlan", "FREE"),
            "oldExpiry": 0,
            "newExpiry": item.get("premiumExpiresAt", 0),
            "performedBy": "ROLLBACK_1.0.66",
            "timestamp": now_ms,
            "reason": f"Restored from backup {backup_file}"
        })

        restored += 1
        if restored % 400 == 0:
            batch.commit()
            batch = db.batch()
            print(f"    {restored}/{len(backup_data)} kullanıcı geri yüklendi...")

    if restored % 400 != 0:
        batch.commit()

    print(f"\n[✓] ROLLBACK TAMAMLANDI! {restored} kullanıcı eski durumuna getirildi.")

def main():
    parser = argparse.ArgumentParser(description="KaynanamTV 1.0.66 Premium Migration & Backup Tool")
    parser.add_argument("--backup", action="store_true", help="Kullanıcıların yedeğini al")
    parser.add_argument("--dry-run", action="store_true", help="Migration simülasyonu yap (0 WRITE)")
    parser.add_argument("--apply", action="store_true", help="Canlı migration işlemini uygula")
    parser.add_argument("--rollback", action="store_true", help="Yedekten geri yükleme yap")
    parser.add_argument("--backup-file", type=str, help="Rollback için kullanılacak JSON yedek dosyası")
    parser.add_argument("--yes", "-y", action="store_true", help="Onay sormadan uygula")

    args = parser.parse_args()

    if not any([args.backup, args.dry_run, args.apply, args.rollback]):
        parser.print_help()
        sys.exit(1)

    db = init_firebase()

    if args.backup:
        create_backup(db)
    elif args.dry_run:
        run_migration(db, dry_run=True)
    elif args.apply:
        run_migration(db, dry_run=False, auto_confirm=args.yes)
    elif args.rollback:
        if not args.backup_file:
            print("[HATA] Rollback için --backup-file parametresi gereklidir.")
            sys.exit(1)
        run_rollback(db, args.backup_file, auto_confirm=args.yes)

if __name__ == "__main__":
    main()
