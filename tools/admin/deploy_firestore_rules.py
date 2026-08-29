#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — Controlled Firestore Rules Deployment
Validates and deploys firestore.rules to live Firebase project: kaynanam-tv
"""

import os
import sys
import json
import requests
import google.auth.transport.requests
from google.oauth2 import service_account

def deploy_rules():
    print("=" * 70)
    print(" KAYNANAMTV — CONTROLLED FIRESTORE RULES DEPLOYMENT")
    print("=" * 70)

    # 1. Credentials
    cred_path = r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json"
    if not os.path.exists(cred_path):
        print(f"ERROR: Service account key not found at: {cred_path}")
        sys.exit(1)

    creds = service_account.Credentials.from_service_account_file(
        cred_path,
        scopes=["https://www.googleapis.com/auth/cloud-platform", "https://www.googleapis.com/auth/firebase"]
    )
    req = google.auth.transport.requests.Request()
    creds.refresh(req)
    token = creds.token
    project_id = creds.project_id

    print(f"Project ID: {project_id}")
    if project_id != "kaynanam-tv":
        print(f"ERROR: Expected project 'kaynanam-tv', got '{project_id}'")
        sys.exit(1)

    # 2. Read rules file
    rules_file_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "firestore.rules")
    if not os.path.exists(rules_file_path):
        print(f"ERROR: firestore.rules not found at: {rules_file_path}")
        sys.exit(1)

    with open(rules_file_path, "r", encoding="utf-8") as f:
        rules_content = f.read()

    print(f"Rules file read successfully ({len(rules_content)} bytes)")

    # 3. Create Ruleset
    headers = {
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json"
    }

    create_url = f"https://firebaserules.googleapis.com/v1/projects/{project_id}/rulesets"
    payload = {
        "source": {
            "files": [
                {
                    "name": "firestore.rules",
                    "content": rules_content
                }
            ]
        }
    }

    print("Creating ruleset on Firebase Rules API...")
    resp = requests.post(create_url, headers=headers, json=payload)
    if resp.status_code != 200:
        print(f"ERROR creating ruleset: {resp.status_code} -> {resp.text}")
        sys.exit(1)

    ruleset_data = resp.json()
    ruleset_name = ruleset_data.get("name")
    print(f"Ruleset created successfully: {ruleset_name}")

    # 4. Release Ruleset to cloud.firestore
    release_name = f"projects/{project_id}/releases/cloud.firestore"
    release_url = f"https://firebaserules.googleapis.com/v1/{release_name}"
    release_payload = {
        "release": {
            "name": release_name,
            "rulesetName": ruleset_name
        }
    }

    print("Releasing ruleset to live Firestore (cloud.firestore)...")
    rel_resp = requests.patch(release_url, headers=headers, json=release_payload)
    if rel_resp.status_code not in (200, 201):
        print(f"ERROR releasing ruleset: {rel_resp.status_code} -> {rel_resp.text}")
        sys.exit(1)

    final_release = rel_resp.json()
    print("=" * 70)
    print("SUCCESS: Firestore security rules deployed to live Firebase project!")
    print(f"Active Release: {final_release.get('name')}")
    print(f"Active Ruleset: {final_release.get('rulesetName')}")
    print(f"Update Time: {final_release.get('updateTime')}")
    print("=" * 70)

if __name__ == "__main__":
    deploy_rules()
