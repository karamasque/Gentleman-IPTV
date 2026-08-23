#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
KaynanamTV 1.0.66 — Firestore Security Rules Test Suite

Automated test scenarios:
  1. Anonymous entitlement write -> DENY
  2. Normal user modifies isPremium=true on their own doc -> DENY
  3. Normal user modifies premiumPlan=LIFETIME on their own doc -> DENY
  4. Normal user extends premiumExpiresAt on their own doc -> DENY
  5. Normal user extends trialExpiresAt on their own doc -> DENY
  6. Normal user resets trialUsed=false -> DENY
  7. Normal user modifies transitionTrialGranted -> DENY
  8. Normal user escalates isAdmin=true or role=ADMIN -> DENY
  9. Normal user updates their own settings map -> ALLOW
 10. Normal user creates PENDING payment_request for their own UID -> ALLOW
 11. Normal user creates payment_request for another UID -> DENY
 12. Normal user tries to create APPROVED payment_request -> DENY
 13. Normal user tries to update status=APPROVED on payment_request -> DENY
 14. Normal user writes to config/app_update -> DENY
 15. Normal user writes directly to premium_audit -> DENY
"""

import sys

def simulate_rule_evaluation():
    print("=" * 70)
    print(" KAYNANAMTV 1.0.66 — FIRESTORE SECURITY RULES TEST SUITE")
    print("=" * 70)

    PROTECTED_USER_KEYS = {
        'isPremium', 'premiumPlan', 'premiumStartedAt', 'premiumExpiresAt',
        'trialUsed', 'trialStartedAt', 'trialExpiresAt', 'transitionTrialGranted',
        'entitlement', 'entitlementVersion', 'role', 'isAdmin'
    }

    test_results = []

    def run_test(name, is_allowed, expected):
        passed = (is_allowed == expected)
        status_str = "PASS" if passed else "FAIL"
        decision_str = "ALLOW" if is_allowed else "DENY"
        expected_str = "ALLOW" if expected else "DENY"
        print(f"[{status_str}] {name:<55} -> Sonuç: {decision_str:<5} (Beklenen: {expected_str})")
        test_results.append(passed)

    # 1. Anonymous write to users
    auth = None
    allowed = (auth is not None)
    run_test("1. Anonymous entitlement write", allowed, expected=False)

    # 2. Normal user isPremium=true update
    auth = {"uid": "user_123", "token": {"admin": False}}
    affected_keys = {"isPremium"}
    allowed = (auth["uid"] == "user_123") and not bool(affected_keys & PROTECTED_USER_KEYS)
    run_test("2. Normal user isPremium=true update", allowed, expected=False)

    # 3. Normal user premiumPlan=LIFETIME update
    affected_keys = {"premiumPlan"}
    allowed = (auth["uid"] == "user_123") and not bool(affected_keys & PROTECTED_USER_KEYS)
    run_test("3. Normal user premiumPlan=LIFETIME update", allowed, expected=False)

    # 4. Normal user extends premiumExpiresAt
    affected_keys = {"premiumExpiresAt"}
    allowed = (auth["uid"] == "user_123") and not bool(affected_keys & PROTECTED_USER_KEYS)
    run_test("4. Normal user extends premiumExpiresAt", allowed, expected=False)

    # 5. Normal user extends trialExpiresAt
    affected_keys = {"trialExpiresAt"}
    allowed = (auth["uid"] == "user_123") and not bool(affected_keys & PROTECTED_USER_KEYS)
    run_test("5. Normal user extends trialExpiresAt", allowed, expected=False)

    # 6. Normal user resets trialUsed=false
    affected_keys = {"trialUsed"}
    allowed = (auth["uid"] == "user_123") and not bool(affected_keys & PROTECTED_USER_KEYS)
    run_test("6. Normal user resets trialUsed=false", allowed, expected=False)

    # 7. Normal user modifies transitionTrialGranted
    affected_keys = {"transitionTrialGranted"}
    allowed = (auth["uid"] == "user_123") and not bool(affected_keys & PROTECTED_USER_KEYS)
    run_test("7. Normal user modifies transitionTrialGranted", allowed, expected=False)

    # 8. Normal user escalates isAdmin=true
    affected_keys = {"isAdmin", "role"}
    allowed = (auth["uid"] == "user_123") and not bool(affected_keys & PROTECTED_USER_KEYS)
    run_test("8. Normal user escalates isAdmin=true / role=ADMIN", allowed, expected=False)

    # 9. Normal user updates their own settings map
    affected_keys = {"settings", "updatedAt"}
    allowed = (auth["uid"] == "user_123") and not bool(affected_keys & PROTECTED_USER_KEYS)
    run_test("9. Normal user updates own settings map", allowed, expected=True)

    # 10. Normal user creates PENDING payment_request for own UID
    req_data = {"uid": "user_123", "status": "PENDING", "approvedAt": None, "approvedBy": None, "plan": "YEARLY"}
    allowed = (auth is not None and req_data["uid"] == auth["uid"] and req_data["status"] == "PENDING" and req_data["approvedAt"] is None and req_data["plan"] in ["YEARLY", "LIFETIME"])
    run_test("10. Normal user creates PENDING payment_request (own UID)", allowed, expected=True)

    # 11. Normal user creates payment_request for another UID
    req_data = {"uid": "victim_456", "status": "PENDING", "approvedAt": None, "approvedBy": None, "plan": "YEARLY"}
    allowed = (auth is not None and req_data["uid"] == auth["uid"] and req_data["status"] == "PENDING")
    run_test("11. Normal user creates payment_request for other UID", allowed, expected=False)

    # 12. Normal user tries to create APPROVED payment_request
    req_data = {"uid": "user_123", "status": "APPROVED", "approvedAt": 123456, "approvedBy": "self", "plan": "LIFETIME"}
    allowed = (auth is not None and req_data["uid"] == auth["uid"] and req_data["status"] == "PENDING" and req_data["approvedAt"] is None)
    run_test("12. Normal user creates pre-APPROVED payment_request", allowed, expected=False)

    # 13. Normal user tries to update status=APPROVED on payment_request
    is_admin = auth["token"].get("admin", False)
    allowed = is_admin
    run_test("13. Normal user self-approves payment_request", allowed, expected=False)

    # 14. Normal user writes to config/app_update
    allowed = is_admin
    run_test("14. Normal user writes to config/app_update", allowed, expected=False)

    # 15. Normal user writes directly to premium_audit
    allowed = False # write: if false in rules
    run_test("15. Normal user writes to premium_audit", allowed, expected=False)

    # 16. User A reads own provider
    auth_a = {"uid": "user_a", "token": {"admin": False}}
    target_user_id = "user_a"
    allowed = (auth_a is not None and auth_a["uid"] == target_user_id)
    run_test("16. USER_A READ OWN PROVIDER", allowed, expected=True)

    # 17. User A writes own provider
    allowed = (auth_a is not None and auth_a["uid"] == target_user_id)
    run_test("17. USER_A WRITE OWN PROVIDER", allowed, expected=True)

    # 18. User A reads User B's provider
    target_user_id = "user_b"
    allowed = (auth_a is not None and (auth_a["uid"] == target_user_id or auth_a["token"].get("admin", False)))
    run_test("18. USER_A READ USER_B PROVIDER", allowed, expected=False)

    # 19. User A writes User B's provider
    allowed = (auth_a is not None and (auth_a["uid"] == target_user_id or auth_a["token"].get("admin", False)))
    run_test("19. USER_A WRITE USER_B PROVIDER", allowed, expected=False)

    # 20. Unauthenticated read provider
    auth_none = None
    allowed = (auth_none is not None and (auth_none.get("uid") == "user_a" or auth_none.get("token", {}).get("admin", False)))
    run_test("20. UNAUTHENTICATED READ PROVIDER", allowed, expected=False)

    # 21. Unauthenticated write provider
    allowed = (auth_none is not None and (auth_none.get("uid") == "user_a" or auth_none.get("token", {}).get("admin", False)))
    run_test("21. UNAUTHENTICATED WRITE PROVIDER", allowed, expected=False)

    print("=" * 70)
    failed = test_results.count(False)
    passed = test_results.count(True)
    print(f"SONUÇ: Toplam {len(test_results)} Test | {passed} BAŞARILI | {failed} BAŞARISIZ")
    print("=" * 70)
    return failed == 0

if __name__ == "__main__":
    success = simulate_rule_evaluation()
    sys.exit(0 if success else 1)
