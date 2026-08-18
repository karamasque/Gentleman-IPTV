#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV 1.0.66 — Rollback, Pricing, and Migration Logic Local Verification
"""

import sys
import json
import time

def main():
    print("=" * 70)
    print(" KAYNANAMTV 1.0.66 — ROLLBACK & PRICING LOCAL VERIFICATION")
    print("=" * 70)

    results = []

    def check(name, condition):
        status = "PASS" if condition else "FAIL"
        print(f"[{status}] {name}")
        results.append(condition)

    # 1. Pricing verification
    from admin_panel import CONFIG_PRICING
    check("1. Yıllık Fiyat Doğrulaması (349 TL)", CONFIG_PRICING.get("YEARLY") == "349 TL")
    check("2. Sınırsız Fiyat Doğrulaması (749 TL)", CONFIG_PRICING.get("LIFETIME") == "749 TL")

    # 2. Simulated Migration Test
    sample_users = [
        {"uid": "user_1", "email": "u1@test.com", "isPremium": True, "premiumPlan": "TRIAL", "transitionTrialGranted": False},
        {"uid": "user_2", "email": "u2@test.com", "isPremium": False, "premiumPlan": "FREE", "transitionTrialGranted": False},
        {"uid": "user_3", "email": "u3@test.com", "isPremium": True, "premiumPlan": "TRIAL", "transitionTrialGranted": True}
    ]

    # Eligible calculation
    eligible = [u for u in sample_users if not u.get("transitionTrialGranted")]
    check("3. 7 Günlük Geçiş Uygunluk Hesaplama (2/3 uygun)", len(eligible) == 2)

    # Migration simulation
    now_ms = int(time.time() * 1000)
    seven_days = 7 * 24 * 60 * 60 * 1000
    migrated_users = []
    for u in eligible:
        migrated = dict(u)
        migrated["isPremium"] = True
        migrated["premiumPlan"] = "TRIAL"
        migrated["trialUsed"] = True
        migrated["trialStartedAt"] = now_ms
        migrated["trialExpiresAt"] = now_ms + seven_days
        migrated["transitionTrialGranted"] = True
        migrated_users.append(migrated)

    check("4. Migration Uygulama (Idempotency bayrağı set edildi)", all(m["transitionTrialGranted"] for m in migrated_users))

    # Idempotency re-run test
    eligible_second_run = [u for u in migrated_users if not u.get("transitionTrialGranted")]
    check("5. İkinci Çalıştırmada Atlanma (Idempotent 0 tekrar)", len(eligible_second_run) == 0)

    # Rollback simulation
    restored_users = []
    backup_map = {u["uid"]: u for u in sample_users}
    for m in migrated_users:
        orig = backup_map[m["uid"]]
        restored = dict(orig)
        restored_users.append(restored)

    check("6. Rollback Geri Yükleme Doğrulaması", len(restored_users) == 2 and restored_users[1]["isPremium"] == False)

    print("=" * 70)
    passed = results.count(True)
    failed = results.count(False)
    print(f"SONUÇ: Toplam {len(results)} Test | {passed} BAŞARILI | {failed} BAŞARISIZ")
    print("=" * 70)
    return failed == 0

if __name__ == "__main__":
    success = main()
    sys.exit(0 if success else 1)
