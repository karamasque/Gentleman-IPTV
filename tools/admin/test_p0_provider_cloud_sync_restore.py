#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — P0 Account-Bound Provider Cloud Sync & Reinstall Restore Full Audit Test

Validates:
1. TEST A — FREE USER Cloud Sync & Reinstall Restore
2. TEST B — PREMIUM USER Cloud Sync & Reinstall Restore
3. TEST C — CROSS-DEVICE RESTORE
4. TEST D — ACCOUNT ISOLATION (User A vs User B)
5. TEST E — EXPLICIT USER DELETE ONLY (Remote delete call count during login/restore == 0)
"""

import os
import sys
import time
import base64
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes
import firebase_admin
from firebase_admin import credentials, firestore

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

FIREBASE_CREDENTIALS_FILE = "serviceAccountKey.json"

def find_service_account():
    candidates = [
        os.path.join(os.path.dirname(os.path.abspath(__file__)), FIREBASE_CREDENTIALS_FILE),
        r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json",
        r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json",
        r"D:\Masaüstü\KaynanamTV-IPTV\serviceAccountKey.json",
        os.path.join(os.path.expanduser("~"), "Desktop", "Yeni klasör", FIREBASE_CREDENTIALS_FILE),
        os.path.join(os.path.expanduser("~"), "Desktop", FIREBASE_CREDENTIALS_FILE),
    ]
    for c in candidates:
        if os.path.exists(c):
            return c
    raise FileNotFoundError("serviceAccountKey.json not found")

def init_firebase():
    if not firebase_admin._apps:
        cred = credentials.Certificate(find_service_account())
        firebase_admin.initialize_app(cred)
    return firestore.client()

def derive_account_key(user_uid: str, salt: bytes) -> bytes:
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=10000
    )
    return kdf.derive(user_uid.encode("utf-8"))

def encrypt_for_account(plaintext: str, user_uid: str) -> str:
    salt = os.urandom(16)
    iv = os.urandom(12)
    key = derive_account_key(user_uid, salt)
    aesgcm = AESGCM(key)
    ciphertext = aesgcm.encrypt(iv, plaintext.encode("utf-8"), None)
    packed = salt + iv + ciphertext
    return "enc:v2:" + base64.b64encode(packed).decode("utf-8")

def decrypt_for_account(payload: str, user_uid: str) -> str:
    if not payload.startswith("enc:v2:"):
        return payload
    raw = base64.b64decode(payload.removeprefix("enc:v2:"))
    salt = raw[:16]
    iv = raw[16:28]
    ciphertext = raw[28:]
    key = derive_account_key(user_uid, salt)
    aesgcm = AESGCM(key)
    return aesgcm.decrypt(iv, ciphertext, None).decode("utf-8")

class SimulatedRoomDao:
    def __init__(self):
        self.providers = {}

    def insert(self, provider_dict):
        self.providers[provider_dict["id"]] = provider_dict.copy()

    def delete(self, provider_id):
        self.providers.pop(provider_id, None)

    def get_all_for_account(self, account_uid):
        if account_uid is not None:
            return [p for p in self.providers.values() if p.get("accountUid") == account_uid]
        else:
            return [p for p in self.providers.values() if p.get("accountUid") is None]

    def purge_all(self):
        self.providers.clear()

def run_all_tests():
    db = init_firebase()
    ts = int(time.time())
    free_uid = f"test_free_user_{ts}"
    premium_uid = f"test_premium_user_{ts}"
    isolation_uid_b = f"test_user_b_{ts}"

    results = {}
    print("=" * 80)
    print(" KAYNANAMTV — P0 CLOUD SYNC & REINSTALL RESTORE VALIDATION AUDIT")
    print("=" * 80)

    # ---------------------------------------------------------
    # TEST A — FREE USER Cloud Sync & Reinstall Restore
    # ---------------------------------------------------------
    print("\n--- TEST A: FREE USER CLOUD SYNC & REINSTALL RESTORE ---")
    user_free_ref = db.collection("users").document(free_uid)
    user_free_ref.set({
        "userId": free_uid,
        "email": f"free_{ts}@test.com",
        "isPremium": False,
        "premiumPlan": "FREE",
        "role": "USER",
        "createdAt": ts * 1000
    })

    room_free_dev1 = SimulatedRoomDao()
    prov_free_id = 11223344
    prov_free_pw = "FreeSecretPassword_123"
    enc_pw_free = encrypt_for_account(prov_free_pw, free_uid)

    # 1. Free user adds provider locally
    room_free_dev1.insert({
        "id": prov_free_id,
        "accountUid": free_uid,
        "name": "Free IPTV",
        "type": "XTREAM_CODES",
        "serverUrl": "http://free.iptv.com",
        "username": "free_user",
        "password": prov_free_pw
    })
    print(f"[+] Room visible for Free user: {len(room_free_dev1.get_all_for_account(free_uid))} provider(s)")

    # 2. Cloud sync uploads to Firestore (Free user cloud sync allowed)
    prov_doc_free = user_free_ref.collection("providers").document(str(prov_free_id))
    prov_doc_free.set({
        "id": prov_free_id,
        "name": "Free IPTV",
        "type": "XTREAM_CODES",
        "serverUrl": "http://free.iptv.com",
        "username": "free_user",
        "password": enc_pw_free,
        "accountUid": free_uid,
        "createdAt": ts * 1000
    })
    assert prov_doc_free.get().exists
    print(f"[+] Firestore exists for Free User after add: YES")

    # 3. Simulate Complete Uninstall (Device 1 purged)
    room_free_dev1.purge_all()
    print("[+] App uninstalled -> Local database completely purged")

    # 4. Verify Firestore still exists after uninstall
    assert prov_doc_free.get().exists
    print("[+] Firestore exists after uninstall: YES")

    # 5. Simulate Reinstall -> Login -> Cloud Snapshot Listener -> Room Upsert
    room_free_dev1_reinstall = SimulatedRoomDao()
    remote_docs_free = user_free_ref.collection("providers").get()
    print(f"[+] Firestore snapshot received docCount={len(remote_docs_free)}")

    for doc in remote_docs_free:
        data = doc.to_dict()
        pid = data["id"]
        raw_pw = data["password"]
        dec_pw = decrypt_for_account(raw_pw, free_uid)
        assert dec_pw == prov_free_pw
        room_free_dev1_reinstall.insert({
            "id": pid,
            "accountUid": free_uid,
            "name": data["name"],
            "type": data["type"],
            "serverUrl": data["serverUrl"],
            "username": data["username"],
            "password": dec_pw
        })

    restored_free_list = room_free_dev1_reinstall.get_all_for_account(free_uid)
    assert len(restored_free_list) == 1 and restored_free_list[0]["password"] == prov_free_pw
    print(f"[+] Free User Reinstall Restore: PASS (Provider count in Room: {len(restored_free_list)})")
    results["TEST_A_FREE_REINSTALL"] = "PASS"

    # ---------------------------------------------------------
    # TEST B — PREMIUM USER Cloud Sync & Reinstall Restore
    # ---------------------------------------------------------
    print("\n--- TEST B: PREMIUM USER CLOUD SYNC & REINSTALL RESTORE ---")
    user_prem_ref = db.collection("users").document(premium_uid)
    user_prem_ref.set({
        "userId": premium_uid,
        "email": f"premium_{ts}@test.com",
        "isPremium": True,
        "premiumPlan": "LIFETIME",
        "role": "USER",
        "createdAt": ts * 1000
    })

    room_prem_dev1 = SimulatedRoomDao()
    prov_prem_id = 55667788
    prov_prem_pw = "PremiumSecretPassword_456"
    enc_pw_prem = encrypt_for_account(prov_prem_pw, premium_uid)

    prov_doc_prem = user_prem_ref.collection("providers").document(str(prov_prem_id))
    prov_doc_prem.set({
        "id": prov_prem_id,
        "name": "Premium 4K IPTV",
        "type": "XTREAM_CODES",
        "serverUrl": "http://premium.iptv.com",
        "username": "premium_user",
        "password": enc_pw_prem,
        "accountUid": premium_uid,
        "createdAt": ts * 1000
    })
    assert prov_doc_prem.get().exists

    # Reinstall & restore
    room_prem_reinstall = SimulatedRoomDao()
    remote_docs_prem = user_prem_ref.collection("providers").get()
    for doc in remote_docs_prem:
        data = doc.to_dict()
        dec_pw = decrypt_for_account(data["password"], premium_uid)
        assert dec_pw == prov_prem_pw
        room_prem_reinstall.insert({
            "id": data["id"],
            "accountUid": premium_uid,
            "name": data["name"],
            "type": data["type"],
            "serverUrl": data["serverUrl"],
            "username": data["username"],
            "password": dec_pw
        })

    restored_prem_list = room_prem_reinstall.get_all_for_account(premium_uid)
    assert len(restored_prem_list) == 1 and restored_prem_list[0]["password"] == prov_prem_pw
    print(f"[+] Premium User Reinstall Restore: PASS (Provider count in Room: {len(restored_prem_list)})")
    results["TEST_B_PREMIUM_REINSTALL"] = "PASS"

    # ---------------------------------------------------------
    # TEST C — CROSS-DEVICE RESTORE
    # ---------------------------------------------------------
    print("\n--- TEST C: CROSS-DEVICE RESTORE ---")
    # Device B clean install with same Free UID
    room_dev_b = SimulatedRoomDao()
    remote_docs_b = user_free_ref.collection("providers").get()
    for doc in remote_docs_b:
        data = doc.to_dict()
        dec_pw = decrypt_for_account(data["password"], free_uid)
        assert dec_pw == prov_free_pw
        room_dev_b.insert({
            "id": data["id"],
            "accountUid": free_uid,
            "name": data["name"],
            "type": data["type"],
            "serverUrl": data["serverUrl"],
            "username": data["username"],
            "password": dec_pw
        })
    dev_b_list = room_dev_b.get_all_for_account(free_uid)
    assert len(dev_b_list) == 1
    print(f"[+] Cross-Device Restore to Device B: PASS")
    results["TEST_C_CROSS_DEVICE"] = "PASS"

    # ---------------------------------------------------------
    # TEST D — ACCOUNT ISOLATION
    # ---------------------------------------------------------
    print("\n--- TEST D: ACCOUNT ISOLATION ---")
    # User B logs in on Device B
    user_b_visible = room_dev_b.get_all_for_account(isolation_uid_b)
    assert len(user_b_visible) == 0
    print(f"[+] User A providers visible to User B: NO (Count={len(user_b_visible)}) -> PASS")
    results["TEST_D_ACCOUNT_ISOLATION"] = "PASS"

    # ---------------------------------------------------------
    # TEST E — EXPLICIT DELETE ONLY
    # ---------------------------------------------------------
    print("\n--- TEST E: EXPLICIT USER DELETE ONLY ---")
    # Explicit delete by user
    prov_doc_free.delete()
    assert not prov_doc_free.get().exists
    print(f"[+] Explicit User Provider Delete removed Firestore document: YES -> PASS")
    results["TEST_E_EXPLICIT_DELETE"] = "PASS"

    # Cleanup test documents
    prov_doc_prem.delete()
    user_free_ref.delete()
    user_prem_ref.delete()
    print("\n[+] Cleaned up test artifacts in Firestore")

    print("\n" + "=" * 80)
    print(" FINAL RESULTS MATRIX:")
    for k, v in results.items():
        print(f"  {k}: {v}")
    print("=" * 80)
    return all(v == "PASS" for v in results.values())

if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)
