#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV 1.0.66 — Live Payment Flow & Admin Approval Verification Test
"""

import os
import sys
import time

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

        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
    return firestore.client()

def main():
    print("=" * 80)
    print(" KAYNANAMTV 1.0.66 — LIVE PAYMENT FLOW TEST")
    print("=" * 80)

    db = init_firebase()
    now_ms = int(time.time() * 1000)
    test_request_id = f"test_req_{now_ms}"
    test_uid = "test_user_qa_entitlement"

    payment_ref = db.collection("payment_requests").document(test_request_id)
    audit_ref = db.collection("premium_audit")

    try:
        # 1. Create PENDING test payment request
        print("[1/4] PENDING ödeme talebi oluşturuluyor (KTV-TEST99)...")
        payment_ref.set({
            "requestId": test_request_id,
            "uid": test_uid,
            "email": "qa_test@kaynanamtv.com",
            "plan": "YEARLY",
            "expectedPrice": "349 TL",
            "paymentCode": "KTV-TEST99",
            "createdAt": now_ms,
            "status": "PENDING",
            "isTest": True
        })
        print("    -> PENDING talebi başarıyla yazıldı.")

        # 2. Read and verify it is visible
        print("[2/4] Admin panel sorgusu simüle ediliyor...")
        doc = payment_ref.get()
        assert doc.exists, "Talep Firestore'da bulunamadı"
        data = doc.to_dict()
        assert data.get("status") == "PENDING", "Durum PENDING değil"
        assert data.get("expectedPrice") == "349 TL", "Fiyat 349 TL değil"
        print(f"    -> Talep doğrulandı: Kod={data.get('paymentCode')}, Tutar={data.get('expectedPrice')}")

        # 3. Simulate Admin Approval
        print("[3/4] Admin onayı uygulanıyor...")
        approved_at = int(time.time() * 1000)
        payment_ref.update({
            "status": "APPROVED",
            "approvedAt": approved_at,
            "approvedBy": "ADMIN_TEST_RUNNER"
        })

        # Write audit log
        audit_event_id = audit_ref.document().id
        audit_doc_ref = audit_ref.document(audit_event_id)
        audit_doc_ref.set({
            "eventId": audit_event_id,
            "targetUid": test_uid,
            "action": "TEST_PAYMENT_APPROVED",
            "plan": "YEARLY",
            "paymentCode": "KTV-TEST99",
            "amount": "349 TL",
            "performedBy": "ADMIN_TEST_RUNNER",
            "timestamp": approved_at,
            "reason": "QA Automated Live Verification"
        })
        print("    -> Admin onayı ve Audit log kaydı başarıyla oluşturuldu.")

        # 4. Clean up test records
        print("[4/4] Test kayıtları temizleniyor...")
        payment_ref.delete()
        audit_doc_ref.delete()
        print("    -> Test belgeleri silindi.")

        print("\n" + "=" * 80)
        print(" [PASS] CANLI ÖDEME AKIŞI VE ADMİN ONAY TESTİ BAŞARIYLA TAMAMLANDI!")
        print("=" * 80)
        return True

    except Exception as e:
        print(f"\n[FAIL] Test sırasında hata: {e}")
        try:
            payment_ref.delete()
        except:
            pass
        return False

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
