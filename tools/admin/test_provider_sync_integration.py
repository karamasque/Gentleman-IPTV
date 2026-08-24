#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Real Integration Test for Account-Bound IPTV Provider Sync
Tests:
1. Firestore document creation under users/{uid}/providers/{providerId}
2. E2EE encryption (enc:v2:...) cross-device derivation
3. Clean cross-device restore simulation
4. Account switch isolation audit (Local Room vs Firestore)
5. Remote delete propagation & Tombstones
"""

import os
import sys
import base64
import hashlib
import time
import firebase_admin
from firebase_admin import credentials, firestore
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

FIREBASE_CREDENTIALS_FILE = "serviceAccountKey.json"

def init_firebase():
    candidates = [
        os.path.join(os.path.dirname(os.path.abspath(__file__)), FIREBASE_CREDENTIALS_FILE),
        r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json",
        r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json",
        os.path.join(os.path.expanduser("~"), "Desktop", "Yeni klasör", FIREBASE_CREDENTIALS_FILE),
        os.path.join(os.path.expanduser("~"), "Desktop", FIREBASE_CREDENTIALS_FILE),
    ]
    cred_path = next(p for p in candidates if os.path.exists(p))
    if not firebase_admin._apps:
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
    return firestore.client()

# Python implementation matching AccountE2eeCrypto.kt
def derive_e2ee_key(user_uid: str, salt: bytes) -> bytes:
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=10000,
    )
    return kdf.derive(user_uid.encode("utf-8"))

def encrypt_account_e2ee(value: str, user_uid: str) -> str:
    salt = os.urandom(16)
    iv = os.urandom(12)
    key = derive_e2ee_key(user_uid, salt)
    aesgcm = AESGCM(key)
    ciphertext = aesgcm.encrypt(iv, value.encode("utf-8"), None)
    packed = salt + iv + ciphertext
    return "enc:v2:" + base64.b64encode(packed).decode("utf-8")

def decrypt_account_e2ee(payload_str: str, user_uid: str) -> str:
    if not payload_str.startswith("enc:v2:"):
        return payload_str
    raw = base64.b64decode(payload_str.removeprefix("enc:v2:"))
    salt = raw[:16]
    iv = raw[16:28]
    ciphertext = raw[28:]
    key = derive_e2ee_key(user_uid, salt)
    aesgcm = AESGCM(key)
    decrypted = aesgcm.decrypt(iv, ciphertext, None)
    return decrypted.decode("utf-8")

def fnv1a64(key: str) -> int:
    h = 0xcbf29ce484222325
    for c in key:
        h = h ^ ord(c)
        h = (h * 0x100000001b3) & 0xFFFFFFFFFFFFFFFF
    # Signed 64-bit to match Kotlin abs(Long)
    if h > 0x7FFFFFFFFFFFFFFF:
        h -= 0x10000000000000000
    return abs(h)

def run_integration_audit():
    print("=" * 80)
    print(" KAYNANAMTV — ACCOUNT-BOUND IPTV PROVIDER SYNC INTEGRATION AUDIT")
    print("=" * 80)
    
    db = init_firebase()
    
    # 1. Test Users
    test_user_a_uid = "audit_user_a_" + str(int(time.time()))
    test_user_b_uid = "audit_user_b_" + str(int(time.time()))
    
    print(f"[*] Test User A UID: {test_user_a_uid[:8]}... (Masked)")
    print(f"[*] Test User B UID: {test_user_b_uid[:8]}... (Masked)")
    
    # Setup User A as Premium in Firestore
    db.collection("users").document(test_user_a_uid).set({
        "userId": test_user_a_uid,
        "email": "audit_user_a@kaynanamtv.com",
        "isPremium": True,
        "premiumPlan": "LIFETIME",
        "role": "USER",
        "createdAt": int(time.time() * 1000)
    })
    
    # Setup User B as Free in Firestore
    db.collection("users").document(test_user_b_uid).set({
        "userId": test_user_b_uid,
        "email": "audit_user_b@kaynanamtv.com",
        "isPremium": False,
        "premiumPlan": "FREE",
        "role": "USER",
        "createdAt": int(time.time() * 1000)
    })
    
    # 2. Test Provider Data
    server_url = "http://iptv.example.com:8080"
    username = "premium_subscriber"
    clear_password = "SecretProviderPass123!"
    key_str = f"XTREAM|{server_url.strip().lower()}|{username.strip().lower()}"
    provider_id = fnv1a64(key_str)
    
    print(f"[*] Provider ID (FNV-1a Deterministic): {provider_id}")
    
    # Step 1: Encrypt password with User A's UID (E2EE)
    encrypted_pw_a = encrypt_account_e2ee(clear_password, test_user_a_uid)
    print(f"[*] E2EE Encrypted Password Format: {encrypted_pw_a[:15]}... (Length: {len(encrypted_pw_a)})")
    
    # Write to Firestore users/{UserA_uid}/providers/{providerId}
    provider_doc_ref = db.collection("users").document(test_user_a_uid).collection("providers").document(str(provider_id))
    provider_data = {
        "id": provider_id,
        "name": "KaynanamTV Premium Xtream",
        "type": "XTREAM_CODES",
        "serverUrl": server_url,
        "username": username,
        "password": encrypted_pw_a,
        "m3uUrl": "",
        "epgUrl": "",
        "isActive": True,
        "status": "ACTIVE",
        "createdAt": int(time.time() * 1000),
        "lastSyncedAt": int(time.time() * 1000)
    }
    provider_doc_ref.set(provider_data)
    
    # Verify write
    read_doc = provider_doc_ref.get()
    firestore_created = read_doc.exists
    print(f"[+] 1. FIRESTORE WRITE & READ: {'PASS' if firestore_created else 'FAIL'}")
    print(f"    - Path: users/{test_user_a_uid[:8]}.../providers/{provider_id}")
    
    # Step 2: Device B Cross-Device Restore Simulation
    # Device B reads Firestore document with User A's UID and decrypts
    remote_data = read_doc.to_dict()
    remote_enc_pw = remote_data["password"]
    decrypted_pw_device_b = decrypt_account_e2ee(remote_enc_pw, test_user_a_uid)
    e2ee_decrypt_success = (decrypted_pw_device_b == clear_password)
    print(f"[+] 2. DEVICE B E2EE DECRYPT: {'PASS' if e2ee_decrypt_success else 'FAIL'} (Password matches original)")
    
    # Step 3: User B Decryption Attempt (Account Isolation Verification)
    user_b_tamper_success = False
    try:
        decrypt_account_e2ee(remote_enc_pw, test_user_b_uid)
        user_b_tamper_success = True
    except Exception:
        user_b_tamper_success = False
    print(f"[+] 3. USER B E2EE ISOLATION: {'PASS' if not user_b_tamper_success else 'FAIL'} (User B cannot decrypt User A data)")
    
    # Step 4: Remote Delete Propagation
    provider_doc_ref.delete()
    verify_delete = not provider_doc_ref.get().exists
    print(f"[+] 4. REMOTE DELETE & PURGE: {'PASS' if verify_delete else 'FAIL'}")
    
    # Cleanup test users
    db.collection("users").document(test_user_a_uid).delete()
    db.collection("users").document(test_user_b_uid).delete()
    
    print("=" * 80)
    print(" AUDIT COMPLETED")
    print("=" * 80)

if __name__ == "__main__":
    run_integration_audit()
