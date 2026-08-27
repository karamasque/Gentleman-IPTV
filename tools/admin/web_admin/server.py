#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
╔══════════════════════════════════════════════════════════════════════════╗
║        KaynanamTV — Ultra-Modern Web Yönetici Paneli Backend Sunucusu   ║
║        Python 3.x | ThreadingHTTPServer | Firebase Admin SDK             ║
╚══════════════════════════════════════════════════════════════════════════╝
"""

import os
import sys
import json
import time
import datetime
import threading
import urllib.parse
from http.server import HTTPServer, ThreadingHTTPServer, SimpleHTTPRequestHandler
import firebase_admin
from firebase_admin import credentials, firestore, auth as fb_auth

# UTF-8 Konsol Desteği
if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

PORT = int(os.environ.get("PORT", 5000))
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATIC_DIR = os.path.join(BASE_DIR, "static")
FIREBASE_CREDENTIALS_FILE = "serviceAccountKey.json"

import hashlib

ADMIN_USERNAME = os.environ.get("ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ.get("ADMIN_PASSWORD", "Th3nexus92@")
ADMIN_SECRET_TOKEN = hashlib.sha256(f"{ADMIN_USERNAME}:{ADMIN_PASSWORD}:kaynanam_2026".encode()).hexdigest()
ACTIVE_SESSIONS = {}

db = None
firebase_initialized = False
firebase_init_error = None


def is_authenticated(headers):
    auth_header = headers.get("Authorization", "")
    token = None
    if auth_header.startswith("Bearer "):
        token = auth_header.split("Bearer ")[1].strip()

    if not token:
        cookie_header = headers.get("Cookie", "")
        for part in cookie_header.split(";"):
            if "admin_token=" in part:
                token = part.split("admin_token=")[1].strip()
                break

    if not token:
        return False

    if token == ADMIN_SECRET_TOKEN:
        return True

    if token in ACTIVE_SESSIONS:
        exp = ACTIVE_SESSIONS[token]
        if exp > time.time():
            return True
        else:
            try:
                del ACTIVE_SESSIONS[token]
            except Exception:
                pass
    return False


def init_firebase():
    global db, firebase_initialized, firebase_init_error

    if firebase_initialized and db:
        return True

    # 1. Cloud / Render Environment Variable Desteği
    env_json = os.environ.get("FIREBASE_CREDENTIALS_JSON") or os.environ.get("FIREBASE_SERVICE_ACCOUNT")
    if env_json:
        try:
            if not firebase_admin._apps:
                cred_dict = json.loads(env_json)
                cred = credentials.Certificate(cred_dict)
                firebase_admin.initialize_app(cred)
            db = firestore.client()
            firebase_initialized = True
            firebase_init_error = None
            print("[BİLGİ] Firebase başarıyla bağlandı! (Environment Variable)")
            return True
        except Exception as e:
            print(f"[UYARI] Çevre değişkeni okunamadı: {e}")

    # 2. Dosya Kontrolü (Render /etc/secrets/ dahil)
    candidates = [
        "/etc/secrets/serviceAccountKey.json",
        os.path.join("/etc/secrets", FIREBASE_CREDENTIALS_FILE),
        os.path.join(BASE_DIR, FIREBASE_CREDENTIALS_FILE),
        os.path.join(os.getcwd(), FIREBASE_CREDENTIALS_FILE),
        r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json",
        r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json",
        os.path.join(os.path.expanduser("~"), "Desktop", "Yeni klasör", FIREBASE_CREDENTIALS_FILE),
        os.path.join(os.path.expanduser("~"), "Desktop", FIREBASE_CREDENTIALS_FILE),
    ]

    cred_path = None
    for p in candidates:
        if os.path.exists(p):
            cred_path = p
            break

    if not cred_path:
        firebase_init_error = f"'{FIREBASE_CREDENTIALS_FILE}' dosyası veya 'FIREBASE_CREDENTIALS_JSON' ortam değişkeni bulunamadı!"
        print(f"[HATA] {firebase_init_error}")
        return False

    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        db = firestore.client()
        firebase_initialized = True
        firebase_init_error = None
        print(f"[BİLGİ] Firebase başarıyla bağlandı! ({cred_path})")
        return True
    except Exception as e:
        firebase_init_error = str(e)
        print(f"[HATA] Firebase başlatılamadı: {e}")
        return False


def get_user_plan_status(user):
    is_premium = user.get("isPremium", False)
    plan = str(user.get("premiumPlan", "NONE")).upper()
    prem_exp = user.get("premiumExpiresAt", 0) or 0
    now_ms = time.time() * 1000

    if plan == "LIFETIME" or (is_premium and plan == "LIFETIME"):
        return "LIFETIME", "👑 Sınırsız Premium", "#F59E0B", "Süresiz (Ömür Boyu)"
    elif plan == "YEARLY" and is_premium:
        if prem_exp > now_ms:
            diff_ms = prem_exp - now_ms
            days = int(diff_ms / 86_400_000)
            hours = int((diff_ms % 86_400_000) / 3_600_000)
            rem_str = f"{days} gün {hours} saat" if days > 0 else f"{hours} saat"
            return "YEARLY", "💎 Yıllık Premium", "#818CF8", rem_str
        else:
            return "EXPIRED", "❌ Yıllık Süresi Bitti", "#EF4444", "Süresi Bitti"
    elif is_premium and prem_exp > now_ms:
        diff_ms = prem_exp - now_ms
        days = int(diff_ms / 86_400_000)
        return "YEARLY", "💎 Yıllık Premium", "#818CF8", f"{days} gün kaldı"
    else:
        return "FREE", "🆓 Ücretsiz Üye", "#10B981", "Süresiz (Kısıtlı Özellik)"


def fetch_all_users_combined():
    if not db:
        return []
    # 1. Firestore verileri
    fs_dict = {}
    for doc in db.collection("users").stream():
        d = doc.to_dict()
        d["_id"] = doc.id
        fs_dict[doc.id] = d

    # 2. Firebase Auth verileri
    auth_users = []
    try:
        page = fb_auth.list_users()
        while page:
            for u in page.users:
                auth_users.append(u)
            page = page.get_next_page()
    except Exception as ex:
        print(f"[AUTH WARN] list_users error: {ex}")

    combined = []
    processed_uids = set()

    for au in auth_users:
        uid = au.uid
        processed_uids.add(uid)
        fs_data = fs_dict.get(uid, {})
        
        last_sign_in = au.user_metadata.last_sign_in_timestamp if au.user_metadata else 0
        created_ts = au.user_metadata.creation_timestamp if au.user_metadata else fs_data.get("createdAt", 0)

        item = dict(fs_data)
        item["_id"] = uid
        item["email"] = au.email or fs_data.get("email") or "E-posta yok"
        item["lastSignInAt"] = last_sign_in
        item["createdAt"] = created_ts
        item["disabled"] = au.disabled
        item["code"] = "KTV-" + uid[-6:].upper() if len(uid) >= 6 else "KTV-000000"
        
        st_code, st_title, st_clr, rem_str = get_user_plan_status(item)
        item["statusCode"] = st_code
        item["statusTitle"] = st_title
        item["statusColor"] = st_clr
        item["remainingTimeStr"] = rem_str

        combined.append(item)

    for uid, fs_data in fs_dict.items():
        if uid not in processed_uids:
            item = dict(fs_data)
            item["_id"] = uid
            item["email"] = fs_data.get("email", "E-posta yok")
            item["lastSignInAt"] = fs_data.get("lastSignInAt") or fs_data.get("lastActiveAt") or 0
            item["createdAt"] = fs_data.get("createdAt", 0)
            item["disabled"] = False
            item["code"] = "KTV-" + uid[-6:].upper() if len(uid) >= 6 else "KTV-000000"
            
            st_code, st_title, st_clr, rem_str = get_user_plan_status(item)
            item["statusCode"] = st_code
            item["statusTitle"] = st_title
            item["statusColor"] = st_clr
            item["remainingTimeStr"] = rem_str
            combined.append(item)

    return combined


class AdminRequestHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory=STATIC_DIR, **kwargs)

    def _send_json(self, data, status_code=200):
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        self.send_response(status_code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        content_length = int(self.headers.get("Content-Length", 0))
        if content_length > 0:
            raw = self.rfile.read(content_length).decode("utf-8")
            try:
                return json.loads(raw)
            except Exception:
                return {}
        return {}

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.end_headers()

    def do_GET(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path

        # ── API ENDPOINTS ──
        if path == "/api/status":
            if not firebase_initialized:
                init_firebase()
            self._send_json({
                "connected": firebase_initialized,
                "error": firebase_init_error,
                "version": "v2.5.0 Web Pro",
                "timestamp": int(time.time() * 1000)
            })
            return

        elif path == "/api/auth-check":
            self._send_json({"authenticated": is_authenticated(self.headers)})
            return

        # Korumalı GET API'ler için Auth Kontrolü
        if path.startswith("/api/"):
            if not is_authenticated(self.headers):
                self._send_json({"error": "Yetkisiz erişim. Lütfen giriş yapın.", "unauthorized": True}, 401)
                return

        if path == "/api/stats":
            if not firebase_initialized:
                self._send_json({"error": firebase_init_error or "Firebase bağlı değil"}, 503)
                return

            users = fetch_all_users_combined()
            now_ms = time.time() * 1000

            total_users = len(users)
            online_today = 0
            online_live = 0
            lifetime_count = 0
            yearly_count = 0
            free_count = 0
            expired_count = 0

            for u in users:
                st = u["statusCode"]
                if st == "LIFETIME":
                    lifetime_count += 1
                elif st == "YEARLY":
                    yearly_count += 1
                elif st == "FREE":
                    free_count += 1
                elif st == "EXPIRED":
                    expired_count += 1

                last_act = u.get("lastSignInAt") or u.get("lastActiveAt") or 0
                if last_act > 0:
                    diff_ms = now_ms - last_act
                    if diff_ms <= 15 * 60 * 1000:
                        online_live += 1
                    if diff_ms <= 24 * 3600 * 1000:
                        online_today += 1

            # Pending Payments
            pending_payments = 0
            try:
                for doc in db.collection("payment_requests").stream():
                    d = doc.to_dict()
                    if d.get("status") == "PENDING":
                        pending_payments += 1
            except Exception as e:
                print(f"[WARN] payment_requests count error: {e}")

            self._send_json({
                "totalUsers": total_users,
                "onlineLive": online_live,
                "onlineToday": online_today,
                "lifetimeUsers": lifetime_count,
                "yearlyUsers": yearly_count,
                "activePremium": lifetime_count + yearly_count,
                "freeUsers": free_count,
                "expiredUsers": expired_count,
                "pendingPayments": pending_payments,
                "timestamp": int(now_ms)
            })
            return

        elif path == "/api/users":
            if not firebase_initialized:
                self._send_json({"error": firebase_init_error or "Firebase bağlı değil"}, 503)
                return

            users = fetch_all_users_combined()
            self._send_json({"users": users})
            return

        elif path.startswith("/api/user/"):
            uid = path.replace("/api/user/", "").strip()
            if not firebase_initialized:
                self._send_json({"error": firebase_init_error}, 503)
                return

            doc = db.collection("users").document(uid).get()
            if not doc.exists:
                self._send_json({"error": "Kullanıcı bulunamadı"}, 404)
                return
            data = doc.to_dict()
            data["_id"] = doc.id
            self._send_json(data)
            return

        elif path == "/api/payments":
            if not firebase_initialized:
                self._send_json({"error": firebase_init_error}, 503)
                return

            payments = []
            try:
                for doc in db.collection("payment_requests").stream():
                    d = doc.to_dict()
                    d["_id"] = doc.id
                    payments.append(d)
                payments.sort(key=lambda x: (x.get("requestedAt") or x.get("createdAt") or 0), reverse=True)
            except Exception as e:
                print(f"[WARN] fetch payments: {e}")

            self._send_json({"payments": payments})
            return

        elif path == "/api/config":
            if not firebase_initialized:
                self._send_json({"error": firebase_init_error}, 503)
                return

            try:
                doc = db.collection("app_config").document("app_config").get()
                if doc.exists:
                    cfg = doc.to_dict()
                    self._send_json(cfg)
                else:
                    self._send_json({
                        "latestVersionCode": 93,
                        "latestVersionName": "1.0.93",
                        "minimumSupportedVersionCode": 67,
                        "forceUpdate": True,
                        "apkDownloadUrl": "https://github.com/karamasque/Gentleman-IPTV/releases/latest/download/KaynanamTV.apk",
                        "releaseNotes": ""
                    })
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # Statik Dosyaları Sun
        if path == "/" or path == "":
            self.path = "/index.html"
        return super().do_GET()

    def do_POST(self):
        parsed = urllib.parse.urlparse(self.path)
        path = parsed.path
        body = self._read_body()

        # ── GİRİŞ YAPMA (LOGIN) ──
        if path == "/api/login":
            username = str(body.get("username", "")).strip()
            password = str(body.get("password", "")).strip()

            if username == ADMIN_USERNAME and password == ADMIN_PASSWORD:
                token = ADMIN_SECRET_TOKEN
                ACTIVE_SESSIONS[token] = time.time() + 30 * 86400  # 30 gün geçerli
                self._send_json({"success": True, "token": token, "username": username, "message": "Giriş başarılı!"})
            else:
                self._send_json({"error": "Hatalı kullanıcı adı veya şifre!"}, 401)
            return

        # ── ÇIKIŞ YAPMA (LOGOUT) ──
        if path == "/api/logout":
            auth_header = self.headers.get("Authorization", "")
            if auth_header.startswith("Bearer "):
                t = auth_header.split("Bearer ")[1].strip()
                ACTIVE_SESSIONS.pop(t, None)
            self._send_json({"success": True})
            return

        # Korumalı POST API'ler için Auth Kontrolü
        if not is_authenticated(self.headers):
            self._send_json({"error": "Yetkisiz erişim. Lütfen giriş yapın.", "unauthorized": True}, 401)
            return

        if not firebase_initialized:
            self._send_json({"error": firebase_init_error or "Firebase bağlı değil"}, 503)
            return

        # ── KULLANICI PLANI GÜNCELLEME ──
        if path == "/api/user/grant-plan":
            uid = body.get("uid")
            plan = body.get("plan", "FREE")  # "LIFETIME", "YEARLY", "FREE"
            amount = body.get("amount", 0)

            if not uid:
                self._send_json({"error": "UID eksik"}, 400)
                return

            now_ms = int(time.time() * 1000)
            if plan == "LIFETIME":
                payload = {
                    "isPremium": True,
                    "premiumPlan": "LIFETIME",
                    "premiumExpiresAt": 0,
                    "trialExpiresAt": 0,
                    "trialUsed": True,
                    "isTrialUsed": True,
                    "entitlementUpdatedAt": now_ms,
                    "paidAt": now_ms,
                    "amountPaid": amount or 600,
                    "paymentStatus": "APPROVED",
                    "approvedBy": "web_admin_panel",
                    "updatedAt": now_ms
                }
            elif plan == "YEARLY":
                payload = {
                    "isPremium": True,
                    "premiumPlan": "YEARLY",
                    "premiumExpiresAt": now_ms + 365 * 86_400_000,
                    "trialExpiresAt": 0,
                    "trialUsed": True,
                    "isTrialUsed": True,
                    "entitlementUpdatedAt": now_ms,
                    "paidAt": now_ms,
                    "amountPaid": amount or 300,
                    "paymentStatus": "APPROVED",
                    "approvedBy": "web_admin_panel",
                    "updatedAt": now_ms
                }
            else:  # FREE / REVOKE
                payload = {
                    "isPremium": False,
                    "premiumPlan": "NONE",
                    "premiumExpiresAt": 0,
                    "trialExpiresAt": 0,
                    "trialUsed": True,
                    "isTrialUsed": True,
                    "paymentStatus": "REVOKED",
                    "entitlementUpdatedAt": now_ms,
                    "updatedAt": now_ms
                }

            try:
                db.collection("users").document(uid).update(payload)
                self._send_json({"success": True, "message": f"Plan başarıyla '{plan}' olarak güncellendi.", "payload": payload})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # ── ÖZEL GÜN EKLEME / SÜRE UZATMA ──
        elif path == "/api/user/extend-days":
            uid = body.get("uid")
            days = int(body.get("days", 30))

            if not uid or days <= 0:
                self._send_json({"error": "Geçersiz UID veya gün sayısı"}, 400)
                return

            try:
                doc = db.collection("users").document(uid).get()
                cur_data = doc.to_dict() if (doc and doc.exists) else {}
                cur_prem = cur_data.get("premiumExpiresAt", 0) or 0
                now_ms = int(time.time() * 1000)
                new_exp = int(max(cur_prem, now_ms) + days * 86_400_000)

                payload = {
                    "isPremium": True,
                    "premiumPlan": "YEARLY",
                    "premiumExpiresAt": new_exp,
                    "entitlementUpdatedAt": now_ms,
                    "updatedAt": now_ms
                }
                db.collection("users").document(uid).update(payload)
                self._send_json({"success": True, "newExpiresAt": new_exp, "message": f"+{days} gün eklendi."})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # ── KULLANICI BAN / BAN KALDIRMA ──
        elif path == "/api/user/toggle-disabled":
            uid = body.get("uid")
            disable = bool(body.get("disabled", False))

            if not uid:
                self._send_json({"error": "UID eksik"}, 400)
                return

            try:
                fb_auth.update_user(uid, disabled=disable)
                now_ms = int(time.time() * 1000)
                db.collection("users").document(uid).update({
                    "disabled": disable,
                    "updatedAt": now_ms
                })
                self._send_json({"success": True, "disabled": disable})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # ── ŞİFRE SIFIRLAMA LİNKİ ÜRETME ──
        elif path == "/api/user/reset-password":
            email = body.get("email")
            if not email:
                self._send_json({"error": "E-posta adresi eksik"}, 400)
                return

            try:
                link = fb_auth.generate_password_reset_link(email)
                self._send_json({"success": True, "resetLink": link})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # ── KULLANICIYI SİLME ──
        elif path == "/api/user/delete":
            uid = body.get("uid")
            if not uid:
                self._send_json({"error": "UID eksik"}, 400)
                return

            try:
                try:
                    fb_auth.delete_user(uid)
                except Exception as ex:
                    print(f"[AUTH DELETE WARN]: {ex}")

                db.collection("users").document(uid).delete()
                self._send_json({"success": True, "message": f"Kullanıcı ({uid}) başarıyla silindi."})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # ── ÖDEME TALEBİNİ ONAYLAMA ──
        elif path == "/api/payments/approve":
            req_id = body.get("requestId")
            plan = body.get("plan", "LIFETIME")
            amount = body.get("amount", 600)

            if not req_id:
                self._send_json({"error": "Talep ID eksik"}, 400)
                return

            try:
                req_doc = db.collection("payment_requests").document(req_id).get()
                if not req_doc.exists:
                    self._send_json({"error": "Ödeme talebi bulunamadı"}, 404)
                    return

                req_data = req_doc.to_dict()
                uid = req_data.get("uid") or req_data.get("userId")
                if not uid:
                    self._send_json({"error": "Talebe bağlı kullanıcı UID bulunamadı"}, 400)
                    return

                now_ms = int(time.time() * 1000)
                expires_ms = (now_ms + 365 * 86_400_000) if plan == "YEARLY" else 0

                db.collection("users").document(uid).update({
                    "isPremium": True,
                    "premiumPlan": plan,
                    "premiumExpiresAt": expires_ms,
                    "trialExpiresAt": 0,
                    "trialUsed": True,
                    "isTrialUsed": True,
                    "entitlementUpdatedAt": now_ms,
                    "paidAt": now_ms,
                    "amountPaid": amount,
                    "paymentStatus": "APPROVED",
                    "updatedAt": now_ms
                })

                db.collection("payment_requests").document(req_id).update({
                    "status": "APPROVED",
                    "approvedPlan": plan,
                    "reviewedAt": now_ms,
                    "reviewedBy": "web_admin_panel"
                })

                self._send_json({"success": True, "message": "Ödeme onaylandı ve Premium aktif edildi."})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # ── ÖDEME TALEBİNİ REDDETME ──
        elif path == "/api/payments/reject":
            req_id = body.get("requestId")
            if not req_id:
                self._send_json({"error": "Talep ID eksik"}, 400)
                return

            try:
                now_ms = int(time.time() * 1000)
                db.collection("payment_requests").document(req_id).update({
                    "status": "REJECTED",
                    "reviewedAt": now_ms,
                    "reviewedBy": "web_admin_panel"
                })
                self._send_json({"success": True, "message": "Ödeme talebi reddedildi."})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # ── ÖDEME TALEBİNİ SİLME ──
        elif path == "/api/payments/delete":
            req_id = body.get("requestId")
            if not req_id:
                self._send_json({"error": "Talep ID eksik"}, 400)
                return

            try:
                db.collection("payment_requests").document(req_id).delete()
                self._send_json({"success": True, "message": "Ödeme talebi silindi."})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        # ── TAMAMLANAN ÖDEME TALEPLERİNİ TEMİZLEME ──
        elif path == "/api/payments/clear-processed":
            try:
                docs = [d for d in db.collection("payment_requests").stream() if d.to_dict().get("status") in ["APPROVED", "REJECTED"]]
                count = len(docs)
                if count > 0:
                    for i in range(0, count, 400):
                        batch = db.batch()
                        chunk = docs[i:i+400]
                        for doc in chunk:
                            batch.delete(doc.reference)
                        batch.commit()
                self._send_json({"success": True, "clearedCount": count, "message": f"{count} adet işlenmiş talep temizlendi."})
            except Exception as e:
                print(f"[HATA clear-processed]: {e}")
                self._send_json({"error": str(e)}, 500)
            return

        # ── TÜM ÖDEME TALEPLERİNİ SİLME (HEPSİNİ TEMİZLE) ──
        elif path == "/api/payments/delete-all":
            try:
                docs = list(db.collection("payment_requests").stream())
                count = len(docs)
                if count > 0:
                    for i in range(0, count, 400):
                        batch = db.batch()
                        chunk = docs[i:i+400]
                        for doc in chunk:
                            batch.delete(doc.reference)
                        batch.commit()
                self._send_json({"success": True, "clearedCount": count, "message": f"Tüm ({count} adet) ödeme talebi kalıcı olarak silindi."})
            except Exception as e:
                print(f"[HATA delete-all]: {e}")
                self._send_json({"error": str(e)}, 500)
            return

        # ── REMOTE CONFIG (ZORUNLU GÜNCELLEME) KAYDETME ──
        elif path == "/api/config/save":
            try:
                latest_code = int(body.get("latestVersionCode", 93))
                latest_name = str(body.get("latestVersionName", "1.0.93")).strip()
                min_code = int(body.get("minimumSupportedVersionCode", 67))
                force_update = bool(body.get("forceUpdate", True))
                apk_url = str(body.get("apkDownloadUrl", "")).strip()
                release_notes = str(body.get("releaseNotes", "")).strip()

                payload = {
                    "latestVersionCode": latest_code,
                    "latestVersionName": latest_name,
                    "minimumSupportedVersionCode": min_code,
                    "forceUpdate": force_update,
                    "apkDownloadUrl": apk_url,
                    "releaseNotes": release_notes,
                    "updatedAt": int(time.time() * 1000)
                }

                db.collection("app_config").document("app_config").set(payload, merge=True)
                self._send_json({"success": True, "message": "Zorunlu güncelleme ayarları tüm TV'lere anında yayınlandı!", "payload": payload})
            except Exception as e:
                self._send_json({"error": str(e)}, 500)
            return

        self._send_json({"error": "Bilinmeyen API uç noktası"}, 404)


def run_server():
    init_firebase()
    os.makedirs(STATIC_DIR, exist_ok=True)
    server_address = ("", PORT)
    httpd = ThreadingHTTPServer(server_address, AdminRequestHandler)
    print(f"\n=======================================================")
    print(f"🚀 KaynanamTV Web Yönetim Paneli Başlatıldı!")
    print(f"🌐 Tarayıcı Adresi: http://localhost:{PORT}")
    print(f"=======================================================\n")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\n[BİLGİ] Sunucu kapatılıyor...")
        httpd.shutdown()


if __name__ == "__main__":
    run_server()
