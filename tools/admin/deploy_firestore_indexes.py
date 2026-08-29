#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Controlled Firestore Index Deployment
Validates, deploys, and verifies composite index for private_chats
on live Firebase project: kaynanam-tv
"""

import os
import sys
import json
import time
import requests
import google.auth.transport.requests
from google.oauth2 import service_account

def get_auth_token():
    cred_path = r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json"
    if not os.path.exists(cred_path):
        cred_path = r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json"
    if not os.path.exists(cred_path):
        print(f"ERROR: Service account key not found at: {cred_path}")
        sys.exit(1)

    creds = service_account.Credentials.from_service_account_file(
        cred_path,
        scopes=["https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/datastore"]
    )
    req = google.auth.transport.requests.Request()
    creds.refresh(req)
    if creds.project_id != "kaynanam-tv":
        print(f"ERROR: Expected project 'kaynanam-tv', got '{creds.project_id}'")
        sys.exit(1)
    return creds.token, creds.project_id

def list_indexes(token, project_id, collection_id="private_chats"):
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    url = f"https://firestore.googleapis.com/v1/projects/{project_id}/databases/(default)/collectionGroups/{collection_id}/indexes"
    resp = requests.get(url, headers=headers)
    if resp.status_code != 200:
        print(f"ERROR listing indexes: {resp.status_code} -> {resp.text}")
        sys.exit(1)
    return resp.json().get("indexes", [])

def is_matching_index(idx):
    if idx.get("queryScope") != "COLLECTION":
        return False
    fields = idx.get("fields", [])
    if len(fields) != 2:
        return False
    has_participants = any(f.get("fieldPath") == "participants" and f.get("arrayConfig") == "CONTAINS" for f in fields)
    has_last_message = any(f.get("fieldPath") == "lastMessageAt" and f.get("order") == "DESCENDING" for f in fields)
    return has_participants and has_last_message

def create_index(token, project_id, collection_id="private_chats"):
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }
    url = f"https://firestore.googleapis.com/v1/projects/{project_id}/databases/(default)/collectionGroups/{collection_id}/indexes"
    payload = {
        "queryScope": "COLLECTION",
        "fields": [
            {
                "fieldPath": "participants",
                "arrayConfig": "CONTAINS"
            },
            {
                "fieldPath": "lastMessageAt",
                "order": "DESCENDING"
            }
        ]
    }
    print(f"Sending index creation request to Firestore API: {url}...")
    resp = requests.post(url, headers=headers, json=payload)
    if resp.status_code not in (200, 201, 202):
        print(f"ERROR creating index: {resp.status_code} -> {resp.text}")
        sys.exit(1)
    return resp.json()

def main():
    print("=" * 70)
    print(" KAYNANAMTV — FIRESTORE INDEX DEPLOYMENT")
    print("=" * 70)

    token, project_id = get_auth_token()
    print(f"Firebase Project: {project_id}")

    existing = list_indexes(token, project_id, "private_chats")
    print(f"Existing indexes found for private_chats: {len(existing)}")

    matching = [idx for idx in existing if is_matching_index(idx)]

    if matching:
        idx = matching[0]
        print(f"Index already existed: {idx.get('name')}")
        state = idx.get("state")
        print(f"Current Index State: {state}")
    else:
        print("Required index not found. Creating index...")
        op = create_index(token, project_id, "private_chats")
        op_name = op.get("name")
        print(f"Index creation initiated. Operation / Index: {op_name}")

    # Polling index status until READY
    print("\nVerifying index status on Firestore...")
    for attempt in range(20):
        time.sleep(3)
        token, _ = get_auth_token()
        indexes = list_indexes(token, project_id, "private_chats")
        target = next((idx for idx in indexes if is_matching_index(idx)), None)
        if target:
            state = target.get("state")
            print(f"[{attempt + 1}/20] Index Name: {target.get('name')} | State: {state}")
            if state == "READY":
                print("\nIndex is READY and ACTIVE!")
                return
            elif state in ("CREATING", "BUILDING"):
                print("Index is currently building, waiting...")
        else:
            print(f"[{attempt + 1}/20] Index not yet visible in listing...")

    print("Index deployment complete.")

if __name__ == "__main__":
    main()
