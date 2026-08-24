#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV — P0 Provider Add & Account Isolation Visibility Test
Simulates Room DAO behavior with accountUid filtering and verifies:
1. Free User A adds provider -> accountUid = uidA -> visible to User A
2. Premium User A adds provider -> accountUid = uidA -> visible to User A
3. Guest adds provider -> accountUid = None -> visible to Guest (None)
4. User A vs User B -> zero cross-account leakage
5. App restart / Re-query -> provider remains visible to owner
"""

import sqlite3

def run_db_simulation():
    conn = sqlite3.connect(":memory:")
    cur = conn.cursor()
    
    # Create providers table matching Room schema
    cur.execute("""
    CREATE TABLE providers (
        id INTEGER PRIMARY KEY,
        account_uid TEXT,
        name TEXT NOT NULL,
        type TEXT NOT NULL,
        server_url TEXT NOT NULL,
        username TEXT NOT NULL,
        password TEXT NOT NULL,
        m3u_url TEXT NOT NULL,
        epg_url TEXT NOT NULL,
        http_user_agent TEXT NOT NULL,
        http_headers TEXT NOT NULL,
        stalker_mac_address TEXT NOT NULL,
        stalker_device_profile TEXT NOT NULL,
        stalker_device_timezone TEXT NOT NULL,
        stalker_device_locale TEXT NOT NULL,
        stalker_serial_number TEXT NOT NULL,
        stalker_device_id TEXT NOT NULL,
        stalker_device_id2 TEXT NOT NULL,
        stalker_signature TEXT NOT NULL,
        stalker_advanced_options_json TEXT NOT NULL,
        stalker_auth_mode TEXT NOT NULL,
        stalker_portal_profile TEXT NOT NULL,
        stalker_portal_fingerprint TEXT NOT NULL,
        stalker_mag_preset TEXT NOT NULL,
        stalker_last_bootstrap_recipe TEXT NOT NULL,
        stalker_endpoint_preference TEXT NOT NULL,
        stalker_cookie_mode TEXT NOT NULL,
        stalker_playback_backend_hint TEXT NOT NULL,
        stalker_last_playback_mode TEXT,
        stalker_credentials_required INTEGER NOT NULL,
        stalker_mac_required INTEGER NOT NULL,
        stalker_uses_temporary_links INTEGER NOT NULL,
        stalker_module_restricted INTEGER NOT NULL,
        stalker_strict_fingerprint_required INTEGER NOT NULL,
        stalker_recipe_fallback_used INTEGER NOT NULL,
        stalker_recipe_rediscovery_attempts INTEGER NOT NULL,
        is_active INTEGER NOT NULL,
        max_connections INTEGER NOT NULL,
        expiration_date INTEGER,
        api_version TEXT,
        allowed_output_formats_json TEXT NOT NULL,
        epg_sync_mode TEXT NOT NULL,
        guide_source_policy TEXT NOT NULL,
        channel_logo_source_policy TEXT NOT NULL,
        xtream_fast_sync_enabled INTEGER NOT NULL,
        xtream_live_sync_mode TEXT NOT NULL,
        m3u_vod_classification_enabled INTEGER NOT NULL,
        status TEXT NOT NULL,
        last_synced_at INTEGER NOT NULL,
        created_at INTEGER NOT NULL
    )
    """)
    conn.commit()

    # Emulate Room DAO: getAllForAccount(accountUid)
    def get_all_for_account(account_uid):
        if account_uid is not None:
            cur.execute("SELECT id, name, account_uid FROM providers WHERE account_uid = ? ORDER BY created_at DESC", (account_uid,))
        else:
            cur.execute("SELECT id, name, account_uid FROM providers WHERE account_uid IS NULL ORDER BY created_at DESC")
        return cur.fetchall()

    def insert_provider(pid, name, account_uid, current_auth_uid):
        # The fixed logic: if provider.accountUid == null and current_auth_uid != null -> bind current_auth_uid
        effective_uid = account_uid or current_auth_uid
        cur.execute("""
        INSERT INTO providers (
            id, account_uid, name, type, server_url, username, password, m3u_url, epg_url,
            http_user_agent, http_headers, stalker_mac_address, stalker_device_profile,
            stalker_device_timezone, stalker_device_locale, stalker_serial_number,
            stalker_device_id, stalker_device_id2, stalker_signature, stalker_advanced_options_json,
            stalker_auth_mode, stalker_portal_profile, stalker_portal_fingerprint,
            stalker_mag_preset, stalker_last_bootstrap_recipe, stalker_endpoint_preference,
            stalker_cookie_mode, stalker_playback_backend_hint, stalker_last_playback_mode,
            stalker_credentials_required, stalker_mac_required, stalker_uses_temporary_links,
            stalker_module_restricted, stalker_strict_fingerprint_required, stalker_recipe_fallback_used,
            stalker_recipe_rediscovery_attempts, is_active, max_connections, expiration_date,
            api_version, allowed_output_formats_json, epg_sync_mode, guide_source_policy,
            channel_logo_source_policy, xtream_fast_sync_enabled, xtream_live_sync_mode,
            m3u_vod_classification_enabled, status, last_synced_at, created_at
        ) VALUES (
            ?, ?, ?, 'XTREAM_CODES', 'http://test.com', 'user', 'pass', '', '',
            '', '', '', '', '', '', '', '', '', '', '', 'AUTO', 'MAG_BASIC', 'BASIC_MAC',
            'GENERIC_SAFE', 'GENERIC_SAFE', 'AUTO', 'NONE', 'AUTO', NULL,
            0, 1, 0, 0, 0, 0, 0, 1, 1, NULL, NULL, '[]', 'UPFRONT', 'AUTO',
            'SUPPLIER_PREFERRED', 1, 'AUTO', 0, 'ACTIVE', 0, 1000
        )
        """, (pid, effective_uid, name))
        conn.commit()
        return effective_uid

    print("=== TEST 1: FREE USER A ADDS PROVIDER ===")
    uid_a = "free_user_a_123"
    effective_a = insert_provider(1001, "Free User Provider", account_uid=None, current_auth_uid=uid_a)
    print(f"Inserted row account_uid: {effective_a} (Expected: {uid_a})")
    rows_a = get_all_for_account(uid_a)
    print(f"Query for User A: {len(rows_a)} found -> {[r[1] for r in rows_a]}")
    assert len(rows_a) == 1 and rows_a[0][0] == 1001, "Test 1 Failed"

    print("\n=== TEST 2: PREMIUM USER A ADDS PROVIDER ===")
    uid_prem = "premium_user_a_456"
    effective_prem = insert_provider(1002, "Premium User Provider", account_uid=None, current_auth_uid=uid_prem)
    print(f"Inserted row account_uid: {effective_prem} (Expected: {uid_prem})")
    rows_prem = get_all_for_account(uid_prem)
    print(f"Query for Premium User: {len(rows_prem)} found -> {[r[1] for r in rows_prem]}")
    assert len(rows_prem) == 1 and rows_prem[0][0] == 1002, "Test 2 Failed"

    print("\n=== TEST 3: GUEST ADDS PROVIDER ===")
    effective_guest = insert_provider(1003, "Guest Provider", account_uid=None, current_auth_uid=None)
    print(f"Inserted row account_uid: {effective_guest} (Expected: None)")
    rows_guest = get_all_for_account(None)
    print(f"Query for Guest (None): {len(rows_guest)} found -> {[r[1] for r in rows_guest]}")
    assert len(rows_guest) == 1 and rows_guest[0][0] == 1003, "Test 3 Failed"

    print("\n=== TEST 4: USER A -> USER B ISOLATION (ZERO LEAKAGE) ===")
    uid_b = "free_user_b_789"
    rows_b = get_all_for_account(uid_b)
    print(f"Query for User B (No providers added yet): {len(rows_b)} found")
    assert len(rows_b) == 0, "Test 4 Failed: User B leaked other user's providers"

    print("\n=== TEST 5: APP RESTART PERSISTENCE ===")
    rows_a_restart = get_all_for_account(uid_a)
    print(f"Re-query for User A after simulated restart: {len(rows_a_restart)} found")
    assert len(rows_a_restart) == 1 and rows_a_restart[0][0] == 1001, "Test 5 Failed"

    print("\nALL SIMULATION CHECKS: PASS")
    return True

if __name__ == "__main__":
    run_db_simulation()
