#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV 1.0.66 — Python Admin & Payment Approval CLI/Tool

Features:
  - List Pending / Approved / Rejected Payment Requests
  - Approve Payment Request (YEARLY / LIFETIME) with Idempotency & Audit Log
  - Reject Payment Request
  - Manual Grant / Revoke Premium with Audit Log
  - Safe Credential Loader (Environment Variable or Secure Path)
"""

import os
import sys
import time
import argparse
from datetime import datetime

CONFIG_PRICING = {
    "YEARLY": "349 TL",
    "LIFETIME": "749 TL"
}

BANK_INFO = {
    "ACCOUNT_HOLDER": "Emre Kılıç",
    "BANK_NAME": "QNB Finansbank",
    "IBAN": "TR64 0015 7000 0000 0068 7735 18"
}

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
            print("[HATA] Firebase credentials bulunamadı.")
            print("Lütfen FIREBASE_SERVICE_ACCOUNT_KEY ortam değişkenini ayarlayın veya geçerli bir dosya yolu belirtin.")
            sys.exit(1)
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
    return firestore.client()

def list_payment_requests(db, status_filter=None):
    req_ref = db.collection("payment_requests")
    if status_filter:
        docs = list(req_ref.where("status", "==", status_filter.upper()).stream())
    else:
        docs = list(req_ref.stream())

    print(f"\n{'='*80}")
    print(f" KAYNANAMTV ÖDEME TALEPLERİ ({status_filter or 'TÜMÜ'}) — Toplam: {len(docs)}")
    print(f"{'='*80}")
    print(f"{'TALEP ID':<22} | {'KOD':<10} | {'PAKET':<8} | {'TUTAR':<8} | {'DURUM':<10} | {'E-POSTA'}")
    print(f"{'-'*80}")

    for doc in docs:
        d = doc.to_dict() or {}
        req_id = doc.id[:20]
        code = d.get("paymentCode", "-")
        plan = d.get("plan", "-")
        price = d.get("expectedPrice", "-")
        status = d.get("status", "PENDING")
        email = d.get("email", "-")
        print(f"{req_id:<22} | {code:<10} | {plan:<8} | {price:<8} | {status:<10} | {email}")
    print(f"{'='*80}\n")

def approve_payment(db, request_id, admin_name="ADMIN_CLI"):
    req_doc_ref = db.collection("payment_requests").document(request_id)
    doc = req_doc_ref.get()
    if not doc.exists:
        print(f"[HATA] Talep bulunamadı: {request_id}")
        return False

    req_data = doc.to_dict() or {}
    status = req_data.get("status", "PENDING")
    
    # IDEMPOTENCY CHECK
    if status == "APPROVED":
        print(f"[UYARI] Bu ödeme talebi ({request_id}) ZATEN ONAYLANMIŞ. Tekrar işlem yapılmadı (Idempotent koruması).")
        return True

    uid = req_data.get("uid")
    plan = req_data.get("plan", "YEARLY").upper()
    email = req_data.get("email", "")
    code = req_data.get("paymentCode", "")

    expected_price = CONFIG_PRICING.get(plan, "Bilinmeyen")
    print(f"\n[ONAY İNCELEMESİ]")
    print(f"  Kullanıcı UID: {uid}")
    print(f"  E-posta      : {email}")
    print(f"  Paket        : {plan}")
    print(f"  Ödeme Kodu   : {code}")
    print(f"  Tanımlı Fiyat: {expected_price}")
    print(f"  Talep Fiyatı : {req_data.get('expectedPrice')}")

    confirm = input("\nBanka hesabından bu ödemeyi doğruladınız mı? Premium'u aktif etmek için (EVET/HAYIR): ")
    if confirm.strip() != "EVET":
        print("[!] Onay iptal edildi.")
        return False

    now_ms = int(time.time() * 1000)
    user_doc_ref = db.collection("users").document(uid)
    user_doc = user_doc_ref.get()
    old_data = user_doc.to_dict() or {} if user_doc.exists else {}

    if plan == "LIFETIME":
        new_expiry = 0
        new_plan = "LIFETIME"
    else:
        # Yearly = 365 Days
        new_expiry = now_ms + (365 * 24 * 60 * 60 * 1000)
        new_plan = "YEARLY"

    # Transaction / Batch update
    batch = db.batch()

    # 1. Update User Entitlement
    batch.update(user_doc_ref, {
        "isPremium": True,
        "premiumPlan": new_plan,
        "premiumStartedAt": now_ms,
        "premiumExpiresAt": new_expiry,
        "entitlementVersion": old_data.get("entitlementVersion", 1) + 1,
        "updatedAt": now_ms
    })

    # 2. Update Payment Request status
    batch.update(req_doc_ref, {
        "status": "APPROVED",
        "approvedAt": now_ms,
        "approvedBy": admin_name
    })

    # 3. Create Premium Audit Record
    audit_ref = db.collection("premium_audit")
    event_id = audit_ref.document().id
    batch.set(audit_ref.document(event_id), {
        "eventId": event_id,
        "targetUid": uid,
        "action": f"{new_plan}_ACTIVATED",
        "oldPlan": old_data.get("premiumPlan", "FREE"),
        "newPlan": new_plan,
        "oldExpiry": old_data.get("premiumExpiresAt", 0),
        "newExpiry": new_expiry,
        "performedBy": admin_name,
        "timestamp": now_ms,
        "paymentRequestId": request_id,
        "reason": f"Payment request {code} approved"
    })

    batch.commit()
    print(f"\n[✓] BAŞARILI: {email} için {new_plan} Premium aktif edildi ve denetim kaydı oluşturuldu.")
    return True

def reject_payment(db, request_id, reason="Ödeme doğrulanamadı", admin_name="ADMIN_CLI"):
    req_doc_ref = db.collection("payment_requests").document(request_id)
    doc = req_doc_ref.get()
    if not doc.exists:
        print(f"[HATA] Talep bulunamadı: {request_id}")
        return False

    req_data = doc.to_dict() or {}
    now_ms = int(time.time() * 1000)

    req_doc_ref.update({
        "status": "REJECTED",
        "approvedAt": now_ms,
        "approvedBy": admin_name,
        "notes": reason
    })

    print(f"[✓] Talep {request_id} REDDEDİLDİ.")
    return True

def manual_grant(db, uid, plan, days=365, admin_name="ADMIN_CLI", reason="Manual Admin Grant"):
    user_doc_ref = db.collection("users").document(uid)
    doc = user_doc_ref.get()
    if not doc.exists:
        print(f"[HATA] Kullanıcı bulunamadı: {uid}")
        return False

    old_data = doc.to_dict() or {}
    now_ms = int(time.time() * 1000)

    if plan.upper() == "LIFETIME":
        new_expiry = 0
        new_plan = "LIFETIME"
    else:
        new_expiry = now_ms + (days * 24 * 60 * 60 * 1000)
        new_plan = "YEARLY"

    batch = db.batch()
    batch.update(user_doc_ref, {
        "isPremium": True,
        "premiumPlan": new_plan,
        "premiumStartedAt": now_ms,
        "premiumExpiresAt": new_expiry,
        "entitlementVersion": old_data.get("entitlementVersion", 1) + 1,
        "updatedAt": now_ms
    })

    audit_ref = db.collection("premium_audit")
    event_id = audit_ref.document().id
    batch.set(audit_ref.document(event_id), {
        "eventId": event_id,
        "targetUid": uid,
        "action": "ADMIN_MANUAL_GRANT",
        "oldPlan": old_data.get("premiumPlan", "FREE"),
        "newPlan": new_plan,
        "oldExpiry": old_data.get("premiumExpiresAt", 0),
        "newExpiry": new_expiry,
        "performedBy": admin_name,
        "timestamp": now_ms,
        "reason": reason
    })

    batch.commit()
    print(f"[✓] Kullanıcı {uid} için {new_plan} manuel olarak tanımlandı.")
    return True

def main():
    parser = argparse.ArgumentParser(description="KaynanamTV Admin & Payment Management Tool")
    parser.add_argument("--list-requests", choices=["PENDING", "APPROVED", "REJECTED", "ALL"], help="Ödeme taleplerini listele")
    parser.add_argument("--approve", type=str, help="Ödeme talebini onayla (Request ID)")
    parser.add_argument("--reject", type=str, help="Ödeme talebini reddet (Request ID)")
    parser.add_argument("--reason", type=str, default="Ödeme doğrulanamadı", help="Red gerekçesi")
    parser.add_argument("--manual-grant", type=str, help="Manuel Premium ver (UID)")
    parser.add_argument("--plan", choices=["YEARLY", "LIFETIME"], default="YEARLY", help="Manuel plan türü")

    args = parser.parse_args()

    if not any([args.list_requests, args.approve, args.reject, args.manual_grant]):
        parser.print_help()
        sys.exit(1)

    try:
        db = init_firebase()
    except Exception as e:
        print(f"[!] Firebase başlatılamadı: {e}")
        sys.exit(1)

    if args.list_requests:
        status = None if args.list_requests == "ALL" else args.list_requests
        list_payment_requests(db, status)
    elif args.approve:
        approve_payment(db, args.approve)
    elif args.reject:
        reject_payment(db, args.reject, args.reason)
    elif args.manual_grant:
        manual_grant(db, args.manual_grant, args.plan)

if __name__ == "__main__":
    main()
