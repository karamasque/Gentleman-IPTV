#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Inspect all users and their providers subcollections in live Firestore
"""
import os
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

def main():
    db = init_firebase()
    users = list(db.collection("users").stream())
    print(f"Total users in Firestore: {len(users)}")
    for u in users:
        u_data = u.to_dict()
        print(f"\nUser: {u.id}")
        print(f"  Email: {u_data.get('email')}")
        print(f"  isPremium: {u_data.get('isPremium')}")
        print(f"  premiumPlan: {u_data.get('premiumPlan')}")
        print(f"  isAdmin: {u_data.get('isAdmin')}")
        
        # Check providers subcollection
        providers = list(db.collection("users").document(u.id).collection("providers").stream())
        print(f"  Providers Count in Firestore: {len(providers)}")
        for p in providers:
            p_data = p.to_dict()
            print(f"    - Provider Doc ID: {p.id}")
            print(f"      id: {p_data.get('id')}")
            print(f"      name: {p_data.get('name')}")
            print(f"      type: {p_data.get('type')}")
            print(f"      serverUrl: {p_data.get('serverUrl')}")
            print(f"      username: {p_data.get('username')}")
            print(f"      password (len={len(str(p_data.get('password')))}): {str(p_data.get('password'))[:20]}...")
            print(f"      createdAt: {p_data.get('createdAt')}")

if __name__ == "__main__":
    main()
