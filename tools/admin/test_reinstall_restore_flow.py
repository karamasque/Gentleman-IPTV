#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Clean Install & Reinstall E2EE Provider Restore Flow Test
Simulates:
1. Premium User A uploads provider with AccountE2eeCrypto (PBKDF2-AES-256-GCM)
2. Complete Uninstall (Clean state: Room=empty, DataStore=empty, Keystore=empty)
3. Reinstall & Login with User A
4. Firestore Snapshot Listener fetches provider
5. AccountE2eeCrypto decrypts credential using User A UID
6. Provider is inserted into Room with accountUid = User A UID
7. Cross-device & Reinstall restore verified
"""

import os
import json
import base64
import hashlib
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
    test_uid = "test_reinstall_user_101"
    
    print("=== 1. SETUP PREMIUM USER A IN FIRESTORE ===")
    user_ref = db.collection("users").document(test_uid)
    user_ref.set({
        "uid": test_uid,
        "email": "premium_reinstall@kaynanamtv.com",
        "isPremium": True,
        "premiumPlan": "LIFETIME",
        "createdAt": 1700000000000
    })
    print(f"[+] Created user {test_uid} with LIFETIME premium")
    
    print("\n=== 2. ADD PROVIDER ON DEVICE A (PRE-UNINSTALL) ===")
    provider_id = 9988776655
    raw_password = "SuperSecretXtreamPassword123!"
    encrypted_pw = encrypt_for_account(raw_password, test_uid)
    print(f"[+] Plaintext Password: {raw_password}")
    print(f"[+] Account E2EE Encrypted: {encrypted_pw[:30]}...")
    
    prov_ref = user_ref.collection("providers").document(str(provider_id))
    prov_data = {
        "id": provider_id,
        "name": "Cloud Reinstall IPTV",
        "type": "XTREAM_CODES",
        "serverUrl": "http://live.kaynanamtv.com:8080",
        "username": "kaynanam_user",
        "password": encrypted_pw,
        "m3uUrl": "",
        "isActive": True,
        "createdAt": 1700000000000
    }
    prov_ref.set(prov_data)
    print(f"[+] Saved provider {provider_id} in Firestore users/{test_uid}/providers/{provider_id}")
    
    print("\n=== 3. SIMULATE UNINSTALL (CLEAN STATE) ===")
    # Room DB = Empty
    simulated_room_db = {}
    # Local keystore / preferences = Empty
    print("[+] Device state completely purged (Room=0, DataStore=0, Local Keystore=0)")
    
    print("\n=== 4. SIMULATE REINSTALL & LOGIN WITH USER A ===")
    # Fetch from Firestore as SnapshotListener does
    snapshot = user_ref.collection("providers").get()
    print(f"[+] Fetched {len(snapshot)} remote provider documents from Firestore")
    
    restored_providers = []
    for doc in snapshot:
        d = doc.to_dict()
        pid = d.get("id")
        pname = d.get("name")
        p_pw = d.get("password")
        
        # Test E2EE decryption without any device key
        decrypted_pw = decrypt_for_account(p_pw, test_uid)
        print(f"[+] Restored & Decrypted Provider: {pname} (ID={pid})")
        print(f"    Decrypted Password Matches: {decrypted_pw == raw_password}")
        
        # Simulate Room Insert with accountUid
        simulated_room_db[pid] = {
            **d,
            "accountUid": test_uid,
            "password": decrypted_pw
        }
        restored_providers.append(pid)
        
    print("\n=== 5. VERIFY ROOM DATABASE & ISOLATION AFTER RESTORE ===")
    print(f"[+] Total Providers in Room: {len(simulated_room_db)}")
    for pid, p in simulated_room_db.items():
        print(f"    - ID: {pid}, Name: {p['name']}, accountUid: {p['accountUid']}, Password Decrypted: {'YES' if p['password'] == raw_password else 'NO'}")

    # Cleanup
    prov_ref.delete()
    user_ref.delete()
    print("\n[+] Cleaned up test data in Firestore")
    
    return len(restored_providers) > 0 and simulated_room_db[provider_id]["password"] == raw_password

if __name__ == "__main__":
    success = run_test()
    print(f"\nOVERALL RESULT: {'PASS' if success else 'FAIL'}")
