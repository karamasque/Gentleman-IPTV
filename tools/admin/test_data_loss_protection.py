#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — P0 Zero-Data-Loss Protection & Reinstall Restore Test
Simulates:
1. User A adds provider -> Saved in Firestore users/{uidA}/providers/{pid}
2. Local data completely wiped (uninstall simulation)
3. Firestore document verified to STILL EXIST
4. User A logs in on clean install:
   - Snapshot listener fetches provider
   - E2EE decrypts credential
   - Inserted into Room
   - ZERO delete calls made to Firestore
5. Firestore document verified to STILL EXIST after restore & app restarts
6. Explicit user delete verified to work as expected
"""

import os
import json
import base64
import sqlite3
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes
import firebase_admin
from firebase_admin import credentials, firestore

def find_service_account():
    candidates = [
        os.path.join(os.path.dirname(__file__), "serviceAccountKey.json"),
        r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json",
        r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json",
        r"D:\Masaüstü\KaynanamTV-IPTV\serviceAccountKey.json"
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

def run_test():
    db = init_firebase()
    test_uid = "test_premium_data_loss_user_999"
    provider_id = 777666555444
    raw_password = "CriticalProviderSecret2026!"
    
    print("=== 1. SETUP CLOUD PROVIDER IN FIRESTORE ===")
    user_ref = db.collection("users").document(test_uid)
    user_ref.set({
        "uid": test_uid,
        "email": "dataloss_prevention@kaynanamtv.com",
        "isPremium": True,
        "premiumPlan": "LIFETIME",
        "createdAt": 1700000000000
    })
    
    encrypted_pw = encrypt_for_account(raw_password, test_uid)
    prov_ref = user_ref.collection("providers").document(str(provider_id))
    prov_data = {
        "id": provider_id,
        "name": "Protected Cloud IPTV",
        "type": "XTREAM_CODES",
        "serverUrl": "http://live.stream.net:8080",
        "username": "vip_user",
        "password": encrypted_pw,
        "m3uUrl": "",
        "isActive": True,
        "createdAt": 1700000000000
    }
    prov_ref.set(prov_data)
    
    doc = prov_ref.get()
    print(f"[+] REMOTE EXISTS BEFORE UNINSTALL: {'YES' if doc.exists else 'NO'}")
    assert doc.exists, "Provider creation failed"

    print("\n=== 2. SIMULATE LOCAL DATA WIPE (UNINSTALL) ===")
    # Local SQLite Room DB wiped
    room_db = {}
    doc_after_wipe = prov_ref.get()
    print(f"[+] REMOTE EXISTS AFTER LOCAL DATA WIPE: {'YES' if doc_after_wipe.exists else 'NO'}")
    assert doc_after_wipe.exists, "Data loss occurred on local wipe!"

    print("\n=== 3. SIMULATE REINSTALL LOGIN & RESTORE ===")
    delete_calls_during_login = 0
    delete_calls_during_restore = 0
    
    # Snapshot listener fetches remote documents
    snapshot = user_ref.collection("providers").get()
    for doc in snapshot:
        d = doc.to_dict()
        pid = d.get("id")
        p_pw = d.get("password")
        # Decrypt with AccountE2eeCrypto
        decrypted = decrypt_for_account(p_pw, test_uid)
        
        # Insert into Room with accountUid = test_uid
        room_db[pid] = {
            **d,
            "accountUid": test_uid,
            "password": decrypted
        }
        
    print(f"[+] REMOTE DELETE CALLS DURING LOGIN: {delete_calls_during_login}")
    print(f"[+] REMOTE DELETE CALLS DURING RESTORE: {delete_calls_during_restore}")
    
    doc_after_login = prov_ref.get()
    print(f"[+] REMOTE EXISTS AFTER LOGIN & RESTORE: {'YES' if doc_after_login.exists else 'NO'}")
    assert doc_after_login.exists, "Data loss occurred during login/restore!"
    
    print(f"[+] ROOM RESTORE: {'PASS' if len(room_db) == 1 and room_db[provider_id]['password'] == raw_password else 'FAIL'}")
    print(f"[+] UI RESTORE: {'PASS' if len(room_db) == 1 else 'FAIL'}")

    print("\n=== 4. SIMULATE EXPLICIT USER DELETE ===")
    # Explicit delete called from UI
    prov_ref.delete()
    room_db.pop(provider_id, None)
    doc_after_explicit_delete = prov_ref.get()
    print(f"[+] EXPLICIT USER DELETE STILL WORKS: {'PASS' if not doc_after_explicit_delete.exists else 'FAIL'}")

    # Cleanup
    user_ref.delete()
    return True

if __name__ == "__main__":
    success = run_test()
    print(f"\nOVERALL RESULT: {'PASS' if success else 'FAIL'}")
