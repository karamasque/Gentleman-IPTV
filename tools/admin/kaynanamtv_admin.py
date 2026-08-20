"""
╔══════════════════════════════════════════════════════════════════════════╗
║              KaynanamTV — Ultra Hızlı Yönetici & Canlı Takip Paneli      ║
║              Python 3.x | CustomTkinter | Firebase Admin SDK             ║
║              Sürüm: 2.3.2 (Özel Renkli Premium Kartları & Canlı Takip)   ║
╚══════════════════════════════════════════════════════════════════════════╝
"""

import os
import sys
import json
import time
import datetime
import threading
import tkinter as tk
from tkinter import messagebox, simpledialog
import customtkinter as ctk
import firebase_admin
from firebase_admin import credentials, firestore, auth as fb_auth

# UTF-8 Konsol Desteği
if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

# ─── AYARLAR & SABİTLER ────────────────────────────────────────────────────────
APP_TITLE = "KaynanamTV — Yönetim, Canlı Takip & Güncelleme Merkezi"
APP_VERSION = "v2.3.2 Pro"
FIREBASE_CREDENTIALS_FILE = "serviceAccountKey.json"

# Renk Teması (Modern Koyu / Neon Glassmorphism Arayüz)
CLR_BG          = "#0B0F19"
CLR_SURFACE     = "#111827"
CLR_CARD        = "#1F2937"
CLR_CARD_HOVER  = "#2D3748"
CLR_BORDER      = "#374151"
CLR_INDIGO      = "#4F46E5"
CLR_INDIGO_BRT  = "#6366F1"
CLR_CYAN        = "#06B6D4"
CLR_CYAN_LIGHT  = "#38BDF8"
CLR_GREEN       = "#10B981"
CLR_GREEN_HOVER = "#059669"
CLR_GOLD        = "#F59E0B"
CLR_GOLD_HOVER  = "#D97706"
CLR_RED         = "#EF4444"
CLR_RED_HOVER   = "#DC2626"
CLR_TEXT        = "#F9FAFB"
CLR_TEXT_DIM    = "#9CA3AF"
CLR_ONLINE      = "#22C55E"
CLR_RECENT      = "#EAB308"
CLR_WEEK        = "#3B82F6"
CLR_OFFLINE     = "#9CA3AF"

# Özel Kart Renkleri (Üyelik Türüne Göre)
CARD_BG_LIFETIME = "#261E08"   # Asil Altın Koyu
CARD_BG_YEARLY   = "#1E1B4B"   # Neon İndigo / Mor Koyu
CARD_BG_TRIAL    = "#182620"   # Ferah Zümrüt Koyu
CARD_BG_EXPIRED  = "#1F222A"   # Soluk Nötr Koyu


# ─── FİREBASE BAĞLANTISI ──────────────────────────────────────────────────────
def init_firebase():
    candidates = [
        os.path.join(os.path.dirname(os.path.abspath(__file__)), FIREBASE_CREDENTIALS_FILE),
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
        return None, f"HATA: '{FIREBASE_CREDENTIALS_FILE}' bulunamadı!\nLütfen JSON dosyasını bu klasöre yerleştirin."

    try:
        if not firebase_admin._apps:
            cred = credentials.Certificate(cred_path)
            firebase_admin.initialize_app(cred)
        db = firestore.client()
        return db, None
    except Exception as e:
        return None, str(e)


# ─── YARDIMCI METOTLAR ────────────────────────────────────────────────────────
def ts_to_str(ts_ms):
    if not ts_ms or ts_ms == 0:
        return "—"
    try:
        dt = datetime.datetime.fromtimestamp(ts_ms / 1000)
        return dt.strftime("%d.%m.%Y %H:%M")
    except Exception:
        return "—"

def format_relative_time(ts_ms):
    if not ts_ms or ts_ms == 0:
        return "Hiç girmedi"
    now_ms = time.time() * 1000
    diff_ms = now_ms - ts_ms
    if diff_ms < 0:
        return "Az önce"
    
    diff_sec = int(diff_ms / 1000)
    diff_min = int(diff_sec / 60)
    diff_hour = int(diff_min / 60)
    diff_day = int(diff_hour / 24)

    if diff_min < 1:
        return "Az önce"
    elif diff_min < 60:
        return f"{diff_min} dk önce"
    elif diff_hour < 24:
        return f"{diff_hour} sa {diff_min % 60} dk önce"
    elif diff_day < 30:
        return f"{diff_day} gün önce"
    else:
        return f"{int(diff_day / 30)} ay önce"

def format_remaining_time(expires_ms):
    if not expires_ms or expires_ms == 0:
        return "Süresiz (Ömür Boyu)"
    now_ms = time.time() * 1000
    diff_ms = expires_ms - now_ms
    if diff_ms <= 0:
        return "Süresi Doldu"
    days = int(diff_ms / 86_400_000)
    hours = int((diff_ms % 86_400_000) / 3_600_000)
    if days > 0:
        return f"{days} gün {hours} saat"
    return f"{hours} saat"

def get_user_plan_status(user):
    is_premium = user.get("isPremium", False)
    plan = str(user.get("premiumPlan", "NONE")).upper()
    trial_exp = user.get("trialExpiresAt", 0) or 0
    prem_exp = user.get("premiumExpiresAt", 0) or 0
    now_ms = time.time() * 1000

    if plan == "LIFETIME":
        return "LIFETIME", "👑 Sınırsız Premium", CLR_GOLD, "Süresiz (Ömür Boyu)"
    elif plan == "YEARLY" and is_premium:
        if prem_exp > now_ms:
            return "YEARLY", "💎 Yıllık Premium", CLR_INDIGO_BRT, format_remaining_time(prem_exp)
        else:
            return "EXPIRED", "❌ Yıllık Süresi Bitti", CLR_RED, "Süresi Doldu"
    elif trial_exp > now_ms:
        return "TRIAL", "⏳ Deneme Süresi", CLR_GREEN, format_remaining_time(trial_exp)
    else:
        return "EXPIRED", "❌ Süresi Doldu", CLR_RED, "Süresi Doldu"


# ─── ANA GUI UYGULAMASI ───────────────────────────────────────────────────────
class KaynanamAdminApp(ctk.CTk):
    def __init__(self):
        super().__init__()
        ctk.set_appearance_mode("dark")
        ctk.set_default_color_theme("blue")

        self.title(f"{APP_TITLE} — {APP_VERSION}")
        self.geometry("1400x880")
        self.minsize(1100, 700)
        self.configure(fg_color=CLR_BG)

        self.db = None
        self.all_users = []
        self.payment_requests = []
        self.remote_config = {}
        self.selected_user = None
        self.display_limit = 25
        self.card_widgets = []

        # Filtre ve Sıralama Durumları
        self._filter_var = ctk.StringVar()
        self._filter_job = None
        self._filter_var.trace_add("write", self._on_filter_changed)
        
        self.selected_chip = "ALL"
        self.sort_mode = "LAST_ACTIVE"

        self._build_header()
        self._build_dashboard_stats()
        self._build_tabview()
        self._connect_firebase()

    def _build_header(self):
        hdr = ctk.CTkFrame(self, fg_color=CLR_SURFACE, height=58, corner_radius=0)
        hdr.pack(fill="x", side="top")
        hdr.pack_propagate(False)

        title_box = ctk.CTkFrame(hdr, fg_color="transparent")
        title_box.pack(side="left", padx=16, pady=10)

        ctk.CTkLabel(title_box, text="📺  KaynanamTV",
                     font=ctk.CTkFont("Segoe UI", 17, "bold"),
                     text_color=CLR_CYAN_LIGHT).pack(side="left")
        ctk.CTkLabel(title_box, text="  |  Yönetim, Canlı Takip & Güncelleme Merkezi",
                     font=ctk.CTkFont("Segoe UI", 13),
                     text_color=CLR_TEXT).pack(side="left")

        right_box = ctk.CTkFrame(hdr, fg_color="transparent")
        right_box.pack(side="right", padx=16)

        self.btn_refresh_all = ctk.CTkButton(
            right_box, text="🔄 Tümünü Yenile", width=120, height=30,
            fg_color=CLR_INDIGO, hover_color=CLR_INDIGO_BRT,
            font=ctk.CTkFont("Segoe UI", 11, "bold"),
            command=self._load_all_data
        )
        self.btn_refresh_all.pack(side="left", padx=(0, 10))

        ver_badge = ctk.CTkFrame(right_box, fg_color=CLR_CARD, corner_radius=6)
        ver_badge.pack(side="left")
        ctk.CTkLabel(ver_badge, text=f" {APP_VERSION} ",
                     font=ctk.CTkFont("Segoe UI", 10, "bold"),
                     text_color=CLR_INDIGO_BRT).pack(padx=8, pady=3)

    def _build_dashboard_stats(self):
        self.stat_bar = ctk.CTkFrame(self, fg_color=CLR_SURFACE, height=68, corner_radius=10)
        self.stat_bar.pack(fill="x", padx=12, pady=(6, 2))
        self.stat_bar.pack_propagate(False)

        self.card_total       = self._stat_card(self.stat_bar, "Toplam Kayıt", "—", CLR_CYAN_LIGHT, "👥")
        self.card_online_today= self._stat_card(self.stat_bar, "Bugün Çevrimiçi", "—", CLR_ONLINE, "🟢")
        self.card_premium     = self._stat_card(self.stat_bar, "Aktif Premium", "—", CLR_INDIGO_BRT, "💎")
        self.card_trial       = self._stat_card(self.stat_bar, "Deneme Süresi", "—", CLR_GREEN, "⏳")
        self.card_expired     = self._stat_card(self.stat_bar, "Süresi Dolan", "—", CLR_RED, "❌")
        self.card_pending_pay = self._stat_card(self.stat_bar, "Bekleyen Ödeme", "—", CLR_GOLD, "💳")

    def _stat_card(self, parent, label, val, clr, icon):
        f = ctk.CTkFrame(parent, fg_color=CLR_CARD, corner_radius=8)
        f.pack(side="left", fill="both", expand=True, padx=4, pady=5)

        left = ctk.CTkFrame(f, fg_color="transparent")
        left.pack(side="left", fill="both", expand=True, padx=10, pady=2)

        lbl_val = ctk.CTkLabel(left, text=val, font=ctk.CTkFont("Segoe UI", 15, "bold"), text_color=clr, anchor="w")
        lbl_val.pack(anchor="w")

        lbl_title = ctk.CTkLabel(left, text=label, font=ctk.CTkFont("Segoe UI", 10), text_color=CLR_TEXT_DIM, anchor="w")
        lbl_title.pack(anchor="w")

        ctk.CTkLabel(f, text=icon, font=ctk.CTkFont(size=18)).pack(side="right", padx=(0, 10))
        return lbl_val

    def _build_tabview(self):
        self.tabview = ctk.CTkTabview(self, fg_color=CLR_BG,
                                      segmented_button_fg_color=CLR_SURFACE,
                                      segmented_button_selected_color=CLR_INDIGO,
                                      segmented_button_selected_hover_color=CLR_INDIGO_BRT,
                                      segmented_button_unselected_color=CLR_CARD,
                                      segmented_button_unselected_hover_color=CLR_CARD_HOVER,
                                      text_color=CLR_TEXT)
        self.tabview.pack(fill="both", expand=True, padx=12, pady=(2, 6))

        self.tab_users = self.tabview.add("👥  Canlı Kullanıcı & Çevrimiçi Takip")
        self.tab_payments = self.tabview.add("💳  Gelen Ödeme Talepleri")
        self.tab_update = self.tabview.add("🚀  Zorunlu Güncelleme Yönetimi")

        self._build_users_tab()
        self._build_payments_tab()
        self._build_update_tab()

    # ═══════════════════════════════════════════════════════════════════════════
    # SEKME 1: CANLI KULLANICI & ÇEVRİMİÇİ TAKİP MERKEZİ
    # ═══════════════════════════════════════════════════════════════════════════
    def _build_users_tab(self):
        self.u_left = ctk.CTkFrame(self.tab_users, fg_color=CLR_SURFACE, width=520, corner_radius=12)
        self.u_left.pack(side="left", fill="y", padx=(4, 6), pady=6)
        self.u_left.pack_propagate(False)

        top_ctrl = ctk.CTkFrame(self.u_left, fg_color="transparent")
        top_ctrl.pack(fill="x", padx=8, pady=(8, 4))

        ctk.CTkEntry(top_ctrl, placeholder_text="🔍 E-posta, UID veya Kod (KTV-...) ara...",
                      textvariable=self._filter_var,
                      fg_color=CLR_CARD, border_color=CLR_BORDER,
                      text_color=CLR_TEXT, placeholder_text_color=CLR_TEXT_DIM,
                      font=ctk.CTkFont("Segoe UI", 12)).pack(side="left", fill="x", expand=True)

        self.sort_menu = ctk.CTkOptionMenu(
            top_ctrl, width=140, height=30,
            values=["🕒 Son Çevrimiçi", "📅 Yeni Kayıt", "💎 Önce Premium", "🔤 E-posta (A-Z)"],
            fg_color=CLR_CARD, button_color=CLR_INDIGO, button_hover_color=CLR_INDIGO_BRT,
            text_color=CLR_TEXT, font=ctk.CTkFont("Segoe UI", 11),
            command=self._on_sort_changed
        )
        self.sort_menu.pack(side="left", padx=(6, 0))

        chips_frame = ctk.CTkFrame(self.u_left, fg_color="transparent")
        chips_frame.pack(fill="x", padx=8, pady=(2, 4))

        self.chip_buttons = {}
        chips_data = [
            ("ALL", "Tümü"),
            ("ONLINE", "🟢 Çevrimiçi"),
            ("TODAY", "🟡 Bugün"),
            ("PREMIUM", "💎 Premium"),
            ("TRIAL", "⏳ Deneme"),
            ("EXPIRED", "❌ Süresi Dolan")
        ]

        for code, text in chips_data:
            btn = ctk.CTkButton(
                chips_frame, text=text, height=24, corner_radius=12,
                fg_color=CLR_INDIGO if code == "ALL" else CLR_CARD,
                hover_color=CLR_INDIGO_BRT,
                font=ctk.CTkFont("Segoe UI", 10, "bold if code == 'ALL' else 'normal'"),
                command=lambda c=code: self._set_filter_chip(c)
            )
            btn.pack(side="left", padx=2, pady=2)
            self.chip_buttons[code] = btn

        self.u_scroll = ctk.CTkScrollableFrame(self.u_left, fg_color="transparent",
                                               scrollbar_button_color=CLR_INDIGO)
        self.u_scroll.pack(fill="both", expand=True, padx=4, pady=2)

        self.status_lbl = ctk.CTkLabel(self.u_left, text="Hazır",
                                       font=ctk.CTkFont("Segoe UI", 11),
                                       text_color=CLR_TEXT_DIM, anchor="w")
        self.status_lbl.pack(fill="x", padx=12, pady=3)

        self.u_right = ctk.CTkFrame(self.tab_users, fg_color=CLR_SURFACE, corner_radius=12)
        self.u_right.pack(side="right", fill="both", expand=True, padx=(6, 4), pady=6)
        self._show_empty_user_detail()

    def _set_filter_chip(self, code):
        self.selected_chip = code
        self.display_limit = 50
        for c, btn in self.chip_buttons.items():
            if c == code:
                btn.configure(fg_color=CLR_INDIGO, font=ctk.CTkFont("Segoe UI", 10, "bold"))
            else:
                btn.configure(fg_color=CLR_CARD, font=ctk.CTkFont("Segoe UI", 10, "normal"))
        self._apply_user_filter()

    def _on_sort_changed(self, choice):
        if "Son Çevrimiçi" in choice:
            self.sort_mode = "LAST_ACTIVE"
        elif "Yeni Kayıt" in choice:
            self.sort_mode = "CREATED"
        elif "Premium" in choice:
            self.sort_mode = "PREMIUM_FIRST"
        elif "E-posta" in choice:
            self.sort_mode = "EMAIL_ASC"
        self._apply_user_filter()

    def _on_filter_changed(self, *args):
        if self._filter_job:
            self.after_cancel(self._filter_job)
        self._filter_job = self.after(250, self._apply_user_filter)

    def _apply_user_filter(self):
        query = self._filter_var.get().strip().lower()
        now_ms = time.time() * 1000

        for w in self.u_scroll.winfo_children():
            w.destroy()
        self.card_widgets.clear()

        filtered = []
        for u in self.all_users:
            email = u.get("email", "").lower()
            uid = u.get("_id", "").lower()
            code = ("ktv-" + uid[-6:]).lower() if len(uid) >= 6 else ""

            if query and not (query in email or query in uid or query in code):
                continue

            last_active = u.get("lastSignInAt") or u.get("lastActiveAt") or 0
            status_code, _, _, _ = get_user_plan_status(u)

            if self.selected_chip == "ONLINE":
                if now_ms - last_active > 15 * 60 * 1000:
                    continue
            elif self.selected_chip == "TODAY":
                if now_ms - last_active > 24 * 60 * 60 * 1000:
                    continue
            elif self.selected_chip == "PREMIUM":
                if status_code not in ["LIFETIME", "YEARLY"]:
                    continue
            elif self.selected_chip == "TRIAL":
                if status_code != "TRIAL":
                    continue
            elif self.selected_chip == "EXPIRED":
                if status_code != "EXPIRED":
                    continue

            filtered.append(u)

        if self.sort_mode == "LAST_ACTIVE":
            filtered.sort(key=lambda x: (x.get("lastSignInAt") or x.get("lastActiveAt") or 0), reverse=True)
        elif self.sort_mode == "CREATED":
            filtered.sort(key=lambda x: x.get("createdAt", 0), reverse=True)
        elif self.sort_mode == "PREMIUM_FIRST":
            filtered.sort(key=lambda x: (0 if get_user_plan_status(x)[0] in ["LIFETIME", "YEARLY"] else 1, -(x.get("lastSignInAt") or 0)))
        elif self.sort_mode == "EMAIL_ASC":
            filtered.sort(key=lambda x: x.get("email", "").lower())

        for u in filtered[:self.display_limit]:
            self._create_user_card(u)

        if len(filtered) > self.display_limit:
            more_btn = ctk.CTkButton(
                self.u_scroll,
                text=f"⬇️ Daha Fazla Göster ({len(filtered) - self.display_limit} kullanıcı daha)",
                fg_color="#374151",
                hover_color=CLR_INDIGO,
                font=ctk.CTkFont("Segoe UI", 11, "bold"),
                height=32,
                command=self._load_more_users
            )
            more_btn.pack(fill="x", padx=4, pady=8)

        self.status_lbl.configure(
            text=f"Listelenen: {min(len(filtered), self.display_limit)} / {len(filtered)} kullanıcı (Toplam: {len(self.all_users)})",
            text_color=CLR_CYAN_LIGHT
        )

    def _load_more_users(self):
        self.display_limit += 50
        self._apply_user_filter()

    def _get_card_styling(self, status_code, is_selected):
        """Üyelik türüne göre özel arka plan ve çerçeve renkleri verir."""
        if status_code == "LIFETIME":
            base_bg = CARD_BG_LIFETIME
            email_clr = "#FDE68A"  # Açık Parlak Altın
            border_clr = CLR_GOLD
        elif status_code == "YEARLY":
            base_bg = CARD_BG_YEARLY
            email_clr = "#E0E7FF"  # Açık Parlak İndigo
            border_clr = CLR_INDIGO_BRT
        elif status_code == "TRIAL":
            base_bg = CARD_BG_TRIAL
            email_clr = "#D1FAE5"  # Açık Parlak Zümrüt
            border_clr = "#059669"
        else:
            base_bg = CARD_BG_EXPIRED
            email_clr = CLR_TEXT_DIM
            border_clr = "#4B5563"

        if is_selected:
            bg_color = "#374151"
            border_width = 2
        else:
            bg_color = base_bg
            border_width = 1

        return bg_color, base_bg, email_clr, border_clr, border_width

    def _create_user_card(self, user):
        uid = user.get("_id", "")
        email = user.get("email", "E-posta yok")
        code = "KTV-" + uid[-6:].upper() if len(uid) >= 6 else "KTV-000000"
        
        status_code, badge_title, badge_clr, remaining_str = get_user_plan_status(user)
        
        last_sign_in = user.get("lastSignInAt") or user.get("lastActiveAt") or 0
        now_ms = time.time() * 1000
        diff_ms = now_ms - last_sign_in if last_sign_in else 9999999999

        if diff_ms <= 15 * 60 * 1000:
            status_dot = "🟢"
            status_text = "Çevrimiçi"
            status_clr = CLR_ONLINE
        elif diff_ms <= 24 * 60 * 60 * 1000:
            status_dot = "🟡"
            status_text = format_relative_time(last_sign_in)
            status_clr = CLR_RECENT
        elif diff_ms <= 7 * 86400 * 1000:
            status_dot = "🔵"
            status_text = format_relative_time(last_sign_in)
            status_clr = CLR_WEEK
        else:
            status_dot = "⚪"
            status_text = format_relative_time(last_sign_in)
            status_clr = CLR_OFFLINE

        is_disabled = user.get("disabled", False)
        display_badge = badge_title + (" [BANLI]" if is_disabled else "")

        is_selected = (self.selected_user and self.selected_user.get("_id") == uid)
        bg_color, base_bg, email_clr, border_clr, border_width = self._get_card_styling(status_code, is_selected)

        # Ana Kart Kutusu (Özel Renkli + Çerçeveli)
        card = ctk.CTkFrame(
            self.u_scroll,
            fg_color=bg_color,
            border_color=border_clr,
            border_width=border_width,
            corner_radius=8,
            cursor="hand2"
        )
        card.pack(fill="x", padx=3, pady=3)
        self.card_widgets.append((uid, card, status_code))

        # Üst Satır: Durum Noktası + E-posta (Sol) | KTV Kodu (Sağ)
        r1 = ctk.CTkFrame(card, fg_color="transparent", cursor="hand2")
        r1.pack(fill="x", padx=10, pady=(7, 2))

        lbl_email_box = ctk.CTkFrame(r1, fg_color="transparent", cursor="hand2")
        lbl_email_box.pack(side="left", fill="x", expand=True)

        lbl_dot = ctk.CTkLabel(lbl_email_box, text=f"{status_dot} ", font=ctk.CTkFont(size=11), cursor="hand2")
        lbl_dot.pack(side="left")

        lbl_email = ctk.CTkLabel(lbl_email_box, text=email, font=ctk.CTkFont("Segoe UI", 12, "bold"), text_color=email_clr, anchor="w", cursor="hand2")
        lbl_email.pack(side="left", fill="x", expand=True)

        lbl_code = ctk.CTkLabel(r1, text=code, font=ctk.CTkFont("Segoe UI", 11, "bold"), text_color=CLR_CYAN_LIGHT, cursor="hand2")
        lbl_code.pack(side="right")

        # Alt Satır: Paket Rozeti (Sol) | Çevrimiçi/Giriş Zamanı (Sağ)
        r2 = ctk.CTkFrame(card, fg_color="transparent", cursor="hand2")
        r2.pack(fill="x", padx=10, pady=(2, 7))

        lbl_plan = ctk.CTkLabel(r2, text=display_badge, font=ctk.CTkFont("Segoe UI", 10, "bold"), text_color=badge_clr, anchor="w", cursor="hand2")
        lbl_plan.pack(side="left")

        lbl_time = ctk.CTkLabel(r2, text=status_text, font=ctk.CTkFont("Segoe UI", 10), text_color=status_clr, anchor="e", cursor="hand2")
        lbl_time.pack(side="right")

        # Tıklama ve Hover Olaylarını Tüm Çocuklara Bağla
        def on_click(event, target_u=user):
            self._select_user_card(target_u)

        def on_enter(event):
            if not (self.selected_user and self.selected_user.get("_id") == uid):
                card.configure(fg_color=CLR_CARD_HOVER)

        def on_leave(event):
            if not (self.selected_user and self.selected_user.get("_id") == uid):
                card.configure(fg_color=base_bg)

        for widget in [card, r1, lbl_email_box, lbl_dot, lbl_email, lbl_code, r2, lbl_plan, lbl_time]:
            widget.bind("<Button-1>", on_click)
            widget.bind("<Enter>", on_enter)
            widget.bind("<Leave>", on_leave)

    def _select_user_card(self, user):
        self.selected_user = user
        target_uid = user.get("_id")

        for uid, card, status_code in self.card_widgets:
            bg_color, base_bg, email_clr, border_clr, border_width = self._get_card_styling(status_code, uid == target_uid)
            card.configure(fg_color=bg_color, border_width=border_width)

        self._show_user_detail(user)

    def _show_empty_user_detail(self):
        for w in self.u_right.winfo_children():
            w.destroy()
        box = ctk.CTkFrame(self.u_right, fg_color="transparent")
        box.place(relx=0.5, rely=0.5, anchor="center")
        ctk.CTkLabel(box, text="👈", font=ctk.CTkFont(size=44)).pack()
        ctk.CTkLabel(box, text="İşlem yapmak ve detayları görmek için\nsol listeden bir kullanıcıya tıklayın.",
                     font=ctk.CTkFont("Segoe UI", 14, "bold"), text_color=CLR_TEXT_DIM, justify="center").pack(pady=8)

    def _show_user_detail(self, user):
        self.selected_user = user
        for w in self.u_right.winfo_children():
            w.destroy()

        uid = user.get("_id", "")
        email = user.get("email", "E-posta yok")
        status_code, badge_title, badge_color, remaining_str = get_user_plan_status(user)
        plan = str(user.get("premiumPlan", "NONE")).upper()
        prem_exp = user.get("premiumExpiresAt", 0) or 0
        trial_exp = user.get("trialExpiresAt", 0) or 0
        created_at = user.get("createdAt", 0)
        last_sign_in = user.get("lastSignInAt") or user.get("lastActiveAt") or 0
        is_disabled = user.get("disabled", False)
        code = "KTV-" + uid[-6:].upper() if len(uid) >= 6 else "KTV-000000"

        scr = ctk.CTkScrollableFrame(self.u_right, fg_color="transparent", scrollbar_button_color=CLR_INDIGO)
        scr.pack(fill="both", expand=True, padx=14, pady=12)

        # Kullanıcı Başlık Kartı
        header_card = ctk.CTkFrame(scr, fg_color=CLR_CARD, corner_radius=12)
        header_card.pack(fill="x", pady=(0, 10))

        top_row = ctk.CTkFrame(header_card, fg_color="transparent")
        top_row.pack(fill="x", padx=16, pady=12)

        left_hdr = ctk.CTkFrame(top_row, fg_color="transparent")
        left_hdr.pack(side="left")

        ctk.CTkLabel(left_hdr, text=email, font=ctk.CTkFont("Segoe UI", 17, "bold"), text_color=CLR_TEXT).pack(anchor="w")
        ctk.CTkLabel(left_hdr, text=f"Ödeme Kodu: {code}   •   UID: {uid}",
                     font=ctk.CTkFont("Segoe UI", 11), text_color=CLR_CYAN_LIGHT).pack(anchor="w", pady=(2, 0))

        badge_box = ctk.CTkFrame(top_row, fg_color="transparent")
        badge_box.pack(side="right")

        if is_disabled:
            ban_badge = ctk.CTkFrame(badge_box, fg_color=CLR_RED, corner_radius=6)
            ban_badge.pack(side="right", padx=3)
            ctk.CTkLabel(ban_badge, text=" 🔒 HESAP BANLI ", font=ctk.CTkFont("Segoe UI", 10, "bold"), text_color="#FFFFFF").pack(padx=6, pady=3)

        badge = ctk.CTkFrame(badge_box, fg_color=badge_color, corner_radius=6)
        badge.pack(side="right", padx=3)
        ctk.CTkLabel(badge, text=f" {badge_title} ", font=ctk.CTkFont("Segoe UI", 10, "bold"),
                     text_color="#000000" if badge_color == CLR_GOLD else "#FFFFFF").pack(padx=8, pady=3)

        # Canlı Giriş & Aktiflik Kartı
        activity_card = ctk.CTkFrame(scr, fg_color=CLR_CARD, corner_radius=12)
        activity_card.pack(fill="x", pady=(0, 10))

        ctk.CTkLabel(activity_card, text="🕒  Canlı Çevrimiçi & Giriş Bilgileri",
                     font=ctk.CTkFont("Segoe UI", 12, "bold"), text_color=CLR_CYAN_LIGHT).pack(anchor="w", padx=16, pady=(10, 6))

        act_grid = ctk.CTkFrame(activity_card, fg_color="transparent")
        act_grid.pack(fill="x", padx=12, pady=(0, 12))

        now_ms = time.time() * 1000
        diff_ms = now_ms - last_sign_in if last_sign_in else 9999999999
        if diff_ms <= 15 * 60 * 1000:
            online_str = "🟢 Şu An Çevrimiçi"
            online_clr = CLR_ONLINE
        elif diff_ms <= 24 * 60 * 60 * 1000:
            online_str = f"🟡 Bugün Aktif ({format_relative_time(last_sign_in)})"
            online_clr = CLR_RECENT
        elif diff_ms <= 7 * 86400 * 1000:
            online_str = f"🔵 Bu Hafta ({format_relative_time(last_sign_in)})"
            online_clr = CLR_WEEK
        else:
            online_str = f"⚪ Çevrimdışı ({format_relative_time(last_sign_in)})"
            online_clr = CLR_OFFLINE

        self._info_box(act_grid, "Çevrimiçi Durumu", online_str, online_clr)
        self._info_box(act_grid, "Son Giriş Tarihi", ts_to_str(last_sign_in), CLR_TEXT)
        self._info_box(act_grid, "Hesap Kayıt Tarihi", ts_to_str(created_at), CLR_TEXT_DIM)

        # Abonelik & Lisans Kartı
        sub_card = ctk.CTkFrame(scr, fg_color=CLR_CARD, corner_radius=12)
        sub_card.pack(fill="x", pady=(0, 10))

        ctk.CTkLabel(sub_card, text="💳  Abonelik & Lisans Süresi",
                     font=ctk.CTkFont("Segoe UI", 12, "bold"), text_color=CLR_GOLD).pack(anchor="w", padx=16, pady=(10, 6))

        sub_grid = ctk.CTkFrame(sub_card, fg_color="transparent")
        sub_grid.pack(fill="x", padx=12, pady=(0, 12))

        target_exp = prem_exp if status_code == "YEARLY" else (trial_exp if status_code == "TRIAL" else 0)
        self._info_box(sub_grid, "Mevcut Durum", badge_title, badge_color)
        self._info_box(sub_grid, "Kalan Süre", remaining_str, CLR_GREEN if status_code in ["LIFETIME", "YEARLY", "TRIAL"] else CLR_RED)
        self._info_box(sub_grid, "Bitiş Tarihi", ts_to_str(target_exp) if target_exp > 0 else ("Süresiz" if status_code == "LIFETIME" else "Doldu"), CLR_TEXT_DIM)

        # Premium Tanımlama Kartı
        action_card = ctk.CTkFrame(scr, fg_color=CLR_CARD, corner_radius=12)
        action_card.pack(fill="x", pady=(0, 10))

        ctk.CTkLabel(action_card, text="⚡  Premium Tanımla & Süre Ekle",
                     font=ctk.CTkFont("Segoe UI", 12, "bold"), text_color=CLR_TEXT).pack(anchor="w", padx=16, pady=(10, 6))

        btn_row1 = ctk.CTkFrame(action_card, fg_color="transparent")
        btn_row1.pack(fill="x", padx=10, pady=4)

        ctk.CTkButton(btn_row1, text="💎 1 Yıl Premium Tanımla (349 TL)",
                      fg_color=CLR_INDIGO, hover_color=CLR_INDIGO_BRT, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._grant_premium_plan(uid, email, "YEARLY", 349)).pack(side="left", fill="x", expand=True, padx=3)

        ctk.CTkButton(btn_row1, text="👑 Sınırsız / Ömür Boyu Ver (749 TL)",
                      fg_color=CLR_GOLD, hover_color=CLR_GOLD_HOVER, text_color="#000000", font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._grant_premium_plan(uid, email, "LIFETIME", 749)).pack(side="left", fill="x", expand=True, padx=3)

        btn_row2 = ctk.CTkFrame(action_card, fg_color="transparent")
        btn_row2.pack(fill="x", padx=10, pady=4)

        ctk.CTkButton(btn_row2, text="⏳ +7 Gün", width=80,
                      fg_color="#0D9488", hover_color="#0F766E", font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._extend_trial_days(uid, 7)).pack(side="left", fill="x", expand=True, padx=2)

        ctk.CTkButton(btn_row2, text="⏳ +30 Gün", width=80,
                      fg_color=CLR_GREEN, hover_color=CLR_GREEN_HOVER, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._extend_trial_days(uid, 30)).pack(side="left", fill="x", expand=True, padx=2)

        ctk.CTkButton(btn_row2, text="⏳ +60 Gün", width=80,
                      fg_color="#15803D", hover_color="#166534", font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._extend_trial_days(uid, 60)).pack(side="left", fill="x", expand=True, padx=2)

        ctk.CTkButton(btn_row2, text="✏️ Özel Gün...", width=95,
                      fg_color="#374151", hover_color="#4B5563", font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._prompt_custom_days(uid, email)).pack(side="left", fill="x", expand=True, padx=2)

        ctk.CTkButton(btn_row2, text="🚫 Lisansı İptal Et", width=120,
                      fg_color="#7F1D1D", hover_color=CLR_RED_HOVER, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._revoke_premium(uid, email)).pack(side="left", fill="x", expand=True, padx=2)

        # Hesap Güvenliği & Ban Kartı
        sec_card = ctk.CTkFrame(scr, fg_color=CLR_CARD, corner_radius=12)
        sec_card.pack(fill="x", pady=(0, 10))

        ctk.CTkLabel(sec_card, text="🔒  Hesap Güvenliği, Ban & Yönetim",
                     font=ctk.CTkFont("Segoe UI", 12, "bold"), text_color=CLR_TEXT).pack(anchor="w", padx=16, pady=(10, 6))

        sec_btn_row = ctk.CTkFrame(sec_card, fg_color="transparent")
        sec_btn_row.pack(fill="x", padx=10, pady=(4, 12))

        ctk.CTkButton(sec_btn_row, text="🔗 Şifre Sıfırlama Linki Üret",
                      fg_color=CLR_CARD_HOVER, hover_color=CLR_INDIGO, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._generate_password_reset_link(email)).pack(side="left", fill="x", expand=True, padx=3)

        ban_btn_text = "🟢 Banı Kaldır" if is_disabled else "🔒 Hesabı Banla (Devre Dışı)"
        ban_btn_clr = CLR_GREEN if is_disabled else "#B91C1C"
        ban_btn_hover = CLR_GREEN_HOVER if is_disabled else "#991B1B"

        ctk.CTkButton(sec_btn_row, text=ban_btn_text,
                      fg_color=ban_btn_clr, hover_color=ban_btn_hover, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._toggle_user_disabled(uid, email, not is_disabled)).pack(side="left", fill="x", expand=True, padx=3)

        ctk.CTkButton(sec_btn_row, text="🗑️ Hesabı Tamamen Sil",
                      fg_color="#450A0A", hover_color=CLR_RED_HOVER, text_color=CLR_RED,
                      font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=lambda: self._confirm_delete_user(uid, email)).pack(side="left", fill="x", expand=True, padx=3)

    def _info_box(self, parent, title, val, clr):
        c = ctk.CTkFrame(parent, fg_color=CLR_CARD, corner_radius=8)
        c.pack(side="left", fill="both", expand=True, padx=3)
        ctk.CTkLabel(c, text=title, font=ctk.CTkFont("Segoe UI", 10), text_color=CLR_TEXT_DIM).pack(pady=(6, 2))
        ctk.CTkLabel(c, text=str(val), font=ctk.CTkFont("Segoe UI", 12, "bold"), text_color=clr).pack(pady=(0, 6))

    # ═══════════════════════════════════════════════════════════════════════════
    # SEKME 2: GELEN ÖDEME TALEPLERİ
    # ═══════════════════════════════════════════════════════════════════════════
    def _build_payments_tab(self):
        p_top = ctk.CTkFrame(self.tab_payments, fg_color="transparent")
        p_top.pack(fill="x", padx=10, pady=8)

        ctk.CTkLabel(p_top, text="💳 TV'den Gelen Havale / EFT Ödeme Bildirimleri",
                     font=ctk.CTkFont("Segoe UI", 16, "bold"), text_color=CLR_CYAN_LIGHT).pack(side="left")

        btn_top_box = ctk.CTkFrame(p_top, fg_color="transparent")
        btn_top_box.pack(side="right")

        ctk.CTkButton(btn_top_box, text="🧹 Tamamlananları Temizle", width=175,
                      fg_color="#374151", hover_color="#4B5563", font=ctk.CTkFont("Segoe UI", 11, "bold"),
                      command=self._clear_processed_payment_requests).pack(side="left", padx=4)

        ctk.CTkButton(btn_top_box, text="↺ Talepleri Yenile", width=130,
                      fg_color=CLR_INDIGO, hover_color=CLR_INDIGO_BRT, font=ctk.CTkFont("Segoe UI", 12, "bold"),
                      command=self._load_payment_requests).pack(side="left", padx=4)

        self.p_scroll = ctk.CTkScrollableFrame(self.tab_payments, fg_color=CLR_SURFACE, corner_radius=12,
                                               scrollbar_button_color=CLR_INDIGO)
        self.p_scroll.pack(fill="both", expand=True, padx=10, pady=(4, 10))

    def _render_payment_requests(self):
        for w in self.p_scroll.winfo_children():
            w.destroy()

        if not self.payment_requests:
            empty = ctk.CTkFrame(self.p_scroll, fg_color="transparent")
            empty.pack(pady=60)
            ctk.CTkLabel(empty, text="🎉", font=ctk.CTkFont(size=40)).pack()
            ctk.CTkLabel(empty, text="Şu anda bekleyen ödeme talebi bulunmuyor.",
                         font=ctk.CTkFont("Segoe UI", 15, "bold"), text_color=CLR_TEXT_DIM).pack(pady=8)
            return

        for req in self.payment_requests:
            req_id = req.get("_id", "")
            uid = req.get("uid") or req.get("userId") or ""
            email = req.get("userEmail") or req.get("email") or "Bilinmeyen E-posta"
            plan = req.get("plan", "YEARLY")
            amount = req.get("expectedPrice") or req.get("amount") or ("349 TL" if plan == "YEARLY" else "749 TL")
            code = req.get("paymentCode") or ("KTV-" + uid[-6:].upper() if len(uid) >= 6 else "—")
            created_at = ts_to_str(req.get("createdAt"))
            status = req.get("status", "PENDING")

            card = ctk.CTkFrame(self.p_scroll, fg_color=CLR_CARD, corner_radius=10)
            card.pack(fill="x", padx=8, pady=6)

            row = ctk.CTkFrame(card, fg_color="transparent")
            row.pack(fill="x", padx=14, pady=12)

            left_col = ctk.CTkFrame(row, fg_color="transparent")
            left_col.pack(side="left", fill="x", expand=True)

            title_row = ctk.CTkFrame(left_col, fg_color="transparent")
            title_row.pack(anchor="w")

            ctk.CTkLabel(title_row, text=f"📌 {code}", font=ctk.CTkFont("Segoe UI", 15, "bold"), text_color=CLR_CYAN_LIGHT).pack(side="left")
            ctk.CTkLabel(title_row, text=f"  —  {email}", font=ctk.CTkFont("Segoe UI", 14, "bold"), text_color=CLR_TEXT).pack(side="left")

            ctk.CTkLabel(left_col, text=f"Müşteri Talebi: {plan} ({amount})   •   Tarih: {created_at}   •   Durum: {status}",
                         font=ctk.CTkFont("Segoe UI", 12), text_color=CLR_TEXT_DIM).pack(anchor="w", pady=(4, 0))

            btn_box = ctk.CTkFrame(row, fg_color="transparent")
            btn_box.pack(side="right")

            if status == "PENDING":
                ctk.CTkButton(btn_box, text="💎 Yıllık Onayla (349 TL)", width=155,
                              fg_color=CLR_INDIGO, hover_color=CLR_INDIGO_BRT, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                              command=lambda r=req: self._approve_payment_custom(r, "YEARLY", 349)).pack(side="left", padx=3)

                ctk.CTkButton(btn_box, text="👑 Sınırsız Onayla (749 TL)", width=165,
                              fg_color=CLR_GOLD, hover_color=CLR_GOLD_HOVER, text_color="#000000", font=ctk.CTkFont("Segoe UI", 11, "bold"),
                              command=lambda r=req: self._approve_payment_custom(r, "LIFETIME", 749)).pack(side="left", padx=3)

                ctk.CTkButton(btn_box, text="❌ Reddet", width=75,
                              fg_color="#7F1D1D", hover_color=CLR_RED_HOVER, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                              command=lambda r=req: self._reject_payment(r)).pack(side="left", padx=3)

                ctk.CTkButton(btn_box, text="🗑️ Sil", width=60,
                              fg_color="#374151", hover_color=CLR_RED_HOVER, text_color=CLR_TEXT, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                              command=lambda r=req: self._delete_payment_request(r)).pack(side="left", padx=3)
            else:
                badge_clr = CLR_GREEN if status == "APPROVED" else CLR_RED
                badge = ctk.CTkFrame(btn_box, fg_color=badge_clr, corner_radius=6)
                badge.pack(side="left", padx=4)
                ctk.CTkLabel(badge, text=f" {status} ", font=ctk.CTkFont("Segoe UI", 11, "bold"), text_color="#FFFFFF").pack(padx=8, pady=4)

                ctk.CTkButton(btn_box, text="🗑️ Talebi Sil", width=95,
                              fg_color="#374151", hover_color=CLR_RED_HOVER, text_color=CLR_TEXT, font=ctk.CTkFont("Segoe UI", 11, "bold"),
                              command=lambda r=req: self._delete_payment_request(r)).pack(side="left", padx=4)

    # ═══════════════════════════════════════════════════════════════════════════
    # SEKME 3: ZORUNLU GÜNCELLEME & SÜRÜM KONTROL MERKEZİ
    # ═══════════════════════════════════════════════════════════════════════════
    def _build_update_tab(self):
        scr = ctk.CTkScrollableFrame(self.tab_update, fg_color=CLR_SURFACE, corner_radius=12,
                                     scrollbar_button_color=CLR_INDIGO)
        scr.pack(fill="both", expand=True, padx=10, pady=8)

        top_box = ctk.CTkFrame(scr, fg_color=CLR_CARD, corner_radius=12)
        top_box.pack(fill="x", padx=12, pady=(8, 12))

        ctk.CTkLabel(top_box, text="🚀  Canlı Zorunlu Güncelleme & Sürüm Yönetimi",
                     font=ctk.CTkFont("Segoe UI", 17, "bold"), text_color=CLR_CYAN_LIGHT).pack(anchor="w", padx=16, pady=(12, 4))

        ctk.CTkLabel(top_box,
                     text="Buradan yeni bir sürüm tanımlayabilir veya 'Zorunlu Güncelleme' kilidini açarak\neski sürüm kullanan tüm TV kullanıcılarının ekranında anında hard-block güncelleme penceresi çıkartabilirsiniz.",
                     font=ctk.CTkFont("Segoe UI", 12), text_color=CLR_TEXT_DIM).pack(anchor="w", padx=16, pady=(0, 12))

        form_card = ctk.CTkFrame(scr, fg_color=CLR_CARD, corner_radius=12)
        form_card.pack(fill="x", padx=12, pady=(0, 12))

        # Satır 1: Sürüm Kodu ve Sürüm Adı
        r1 = ctk.CTkFrame(form_card, fg_color="transparent")
        r1.pack(fill="x", padx=16, pady=(16, 8))

        f_vc = ctk.CTkFrame(r1, fg_color="transparent")
        f_vc.pack(side="left", fill="x", expand=True, padx=(0, 8))
        ctk.CTkLabel(f_vc, text="En Son Sürüm Kodu (latestVersionCode):", font=ctk.CTkFont("Segoe UI", 11, "bold"), text_color=CLR_TEXT).pack(anchor="w")
        self.entry_latest_code = ctk.CTkEntry(f_vc, placeholder_text="Örn: 70", fg_color=CLR_SURFACE, border_color=CLR_BORDER, text_color=CLR_TEXT)
        self.entry_latest_code.pack(fill="x", pady=(4, 0))

        f_vn = ctk.CTkFrame(r1, fg_color="transparent")
        f_vn.pack(side="right", fill="x", expand=True, padx=(8, 0))
        ctk.CTkLabel(f_vn, text="En Son Sürüm Adı (latestVersionName):", font=ctk.CTkFont("Segoe UI", 11, "bold"), text_color=CLR_TEXT).pack(anchor="w")
        self.entry_latest_name = ctk.CTkEntry(f_vn, placeholder_text="Örn: 1.0.70", fg_color=CLR_SURFACE, border_color=CLR_BORDER, text_color=CLR_TEXT)
        self.entry_latest_name.pack(fill="x", pady=(4, 0))

        # Satır 2: Minimum Desteklenen Sürüm Kodu & Zorunlu Kilitleme Switch
        r2 = ctk.CTkFrame(form_card, fg_color="transparent")
        r2.pack(fill="x", padx=16, pady=8)

        f_min = ctk.CTkFrame(r2, fg_color="transparent")
        f_min.pack(side="left", fill="x", expand=True, padx=(0, 8))
        ctk.CTkLabel(f_min, text="Minimum Desteklenen Sürüm Kodu (Bunun altı kilitlenir):", font=ctk.CTkFont("Segoe UI", 11, "bold"), text_color=CLR_TEXT).pack(anchor="w")
        self.entry_min_code = ctk.CTkEntry(f_min, placeholder_text="Örn: 70", fg_color=CLR_SURFACE, border_color=CLR_BORDER, text_color=CLR_TEXT)
        self.entry_min_code.pack(fill="x", pady=(4, 0))

        f_sw = ctk.CTkFrame(r2, fg_color="transparent")
        f_sw.pack(side="right", fill="x", expand=True, padx=(8, 0))
        ctk.CTkLabel(f_sw, text="Zorunlu Güncelleme Kilidi (Force Update):", font=ctk.CTkFont("Segoe UI", 11, "bold"), text_color=CLR_TEXT).pack(anchor="w")
        self.sw_force_update = ctk.CTkSwitch(f_sw, text="Aktif (Eski Sürümleri Kilitle)", font=ctk.CTkFont("Segoe UI", 12, "bold"),
                                             progress_color=CLR_RED, button_color=CLR_TEXT)
        self.sw_force_update.pack(anchor="w", pady=(8, 0))

        # Satır 3: APK İndirme Bağlantısı
        r3 = ctk.CTkFrame(form_card, fg_color="transparent")
        r3.pack(fill="x", padx=16, pady=8)
        ctk.CTkLabel(r3, text="Doğrudan APK İndirme Bağlantısı (apkDownloadUrl):", font=ctk.CTkFont("Segoe UI", 11, "bold"), text_color=CLR_TEXT).pack(anchor="w")
        self.entry_apk_url = ctk.CTkEntry(r3, placeholder_text="https://github.com/Davidona/KaynanamTV-IPTV/releases/latest/download/KaynanamTV.apk",
                                          fg_color=CLR_SURFACE, border_color=CLR_BORDER, text_color=CLR_TEXT)
        self.entry_apk_url.pack(fill="x", pady=(4, 0))

        # Satır 4: Sürüm Notları / Duyuru Metni
        r4 = ctk.CTkFrame(form_card, fg_color="transparent")
        r4.pack(fill="x", padx=16, pady=8)
        ctk.CTkLabel(r4, text="Sürüm / Güncelleme Notları (TV ekranında görünecek mesaj):", font=ctk.CTkFont("Segoe UI", 11, "bold"), text_color=CLR_TEXT).pack(anchor="w")
        self.txt_release_notes = ctk.CTkTextbox(r4, height=90, fg_color=CLR_SURFACE, border_color=CLR_BORDER, text_color=CLR_TEXT, font=ctk.CTkFont("Segoe UI", 12))
        self.txt_release_notes.pack(fill="x", pady=(4, 0))

        # Kaydet / Yayınla Butonları
        btn_row = ctk.CTkFrame(form_card, fg_color="transparent")
        btn_row.pack(fill="x", padx=16, pady=(12, 16))

        ctk.CTkButton(btn_row, text="💾 Sürüm Bilgilerini & Zorunlu Güncellemeyi Kaydet",
                      height=40, fg_color=CLR_INDIGO, hover_color=CLR_INDIGO_BRT,
                      font=ctk.CTkFont("Segoe UI", 13, "bold"),
                      command=self._save_remote_config).pack(side="left", fill="x", expand=True, padx=(0, 6))

        ctk.CTkButton(btn_row, text="🔴 TÜM ESKİ SÜRÜMLERİ ANINDA KİLİTLE (ACİL GÜNCELLEME)",
                      height=40, fg_color="#7F1D1D", hover_color=CLR_RED_HOVER,
                      font=ctk.CTkFont("Segoe UI", 12, "bold"),
                      command=self._force_lock_all_older_versions).pack(side="right", fill="x", expand=True, padx=(6, 0))

    def _render_remote_config(self):
        cfg = self.remote_config
        self.entry_latest_code.delete(0, "end")
        self.entry_latest_code.insert(0, str(cfg.get("latestVersionCode", 71)))

        self.entry_latest_name.delete(0, "end")
        self.entry_latest_name.insert(0, str(cfg.get("latestVersionName", "1.0.71")))

        self.entry_min_code.delete(0, "end")
        self.entry_min_code.insert(0, str(cfg.get("minimumSupportedVersionCode", 71)))

        if cfg.get("forceUpdate", True):
            self.sw_force_update.select()
        else:
            self.sw_force_update.deselect()

        self.entry_apk_url.delete(0, "end")
        self.entry_apk_url.insert(0, str(cfg.get("apkDownloadUrl", "https://github.com/Davidona/KaynanamTV-IPTV/releases/latest/download/KaynanamTV.apk")))

        self.txt_release_notes.delete("1.0", "end")
        self.txt_release_notes.insert("1.0", str(cfg.get("releaseNotes", "KaynanamTV v1.0.71 kararlı sürüm. Fast sync düzeltmeleri ve performans iyileştirmeleri.")))

    def _save_remote_config(self):
        if not self.db:
            messagebox.showerror("Hata", "Firebase veritabanı bağlı değil!")
            return

        try:
            latest_code = int(self.entry_latest_code.get().strip())
            latest_name = self.entry_latest_name.get().strip()
            min_code = int(self.entry_min_code.get().strip())
            force_update = bool(self.sw_force_update.get())
            apk_url = self.entry_apk_url.get().strip()
            release_notes = self.txt_release_notes.get("1.0", "end").strip()

            confirm = messagebox.askyesno(
                "Güncellemeyi Yayınla",
                f"Sürüm Kodu: {latest_code} ({latest_name})\nMinimum Desteklenen Sürüm: {min_code}\nZorunlu Güncelleme: {'AÇIK (Hard-Block)' if force_update else 'KAPALI'}\n\nBu ayarlar tüm TV'lere anında uygulanacaktır. Onaylıyor musunuz?"
            )
            if not confirm:
                return

            def task():
                try:
                    payload = {
                        "latestVersionCode": latest_code,
                        "latestVersionName": latest_name,
                        "minimumSupportedVersionCode": min_code,
                        "forceUpdate": force_update,
                        "apkDownloadUrl": apk_url,
                        "releaseNotes": release_notes,
                        "updatedAt": int(time.time() * 1000)
                    }
                    self.db.collection("config").document("app_config").set(payload, merge=True)
                    self.db.collection("app_config").document("app_config").set(payload, merge=True)
                    self.remote_config = payload
                    self.after(0, lambda: messagebox.showinfo("Başarılı", "✓ Zorunlu güncelleme ve sürüm ayarları başarıyla yayınlandı!"))
                except Exception as e:
                    self.after(0, lambda: messagebox.showerror("Hata", f"Kayıt başarısız:\n{e}"))

            threading.Thread(target=task, daemon=True).start()
        except ValueError:
            messagebox.showerror("Hatalı Giriş", "Lütfen sürüm kodlarını sayısal olarak girin (Örn: 70)!")

    def _force_lock_all_older_versions(self):
        try:
            latest_code = int(self.entry_latest_code.get().strip())
            confirm = messagebox.askyesno(
                "Acil Zorunlu Güncelleme",
                f"DİKKAT: Sürüm kodu {latest_code} altındaki TÜM kullanıcılar zorunlu güncelleme ekranına kilitlenecektir.\n\nOnaylıyor musunuz?"
            )
            if not confirm:
                return

            self.entry_min_code.delete(0, "end")
            self.entry_min_code.insert(0, str(latest_code))
            self.sw_force_update.select()
            self._save_remote_config()
        except ValueError:
            messagebox.showerror("Hata", "Lütfen geçerli bir sürüm kodu girin!")

    # ═══════════════════════════════════════════════════════════════════════════
    # FİREBASE VERİ ÇEKME & BİRLEŞTİRME MOTORU (FIRESTORE + AUTH)
    # ═══════════════════════════════════════════════════════════════════════════
    def _connect_firebase(self):
        def task():
            db, err = init_firebase()
            if err:
                self.after(0, lambda: messagebox.showerror("Firebase Bağlantı Hatası", err))
            else:
                self.db = db
                self.after(0, self._load_all_data)
        threading.Thread(target=task, daemon=True).start()

    def _load_all_data(self):
        self._load_payment_requests()
        self._load_users()
        self._load_remote_config()

    def _load_remote_config(self):
        if not self.db:
            return
        def task():
            try:
                doc = self.db.collection("app_config").document("app_config").get()
                if doc.exists:
                    self.remote_config = doc.to_dict()
                    self.after(0, self._render_remote_config)
            except Exception as e:
                print(f"[WARN] _load_remote_config: {e}")
        threading.Thread(target=task, daemon=True).start()

    def _load_users(self):
        if not self.db:
            return
        self.status_lbl.configure(text="Kullanıcılar ve aktiflik verileri yükleniyor...", text_color=CLR_CYAN_LIGHT)

        def task():
            try:
                # 1. Firestore verilerini çek
                fs_dict = {}
                for doc in self.db.collection("users").stream():
                    d = doc.to_dict()
                    d["_id"] = doc.id
                    fs_dict[doc.id] = d

                # 2. Firebase Auth verilerini çek
                auth_users = []
                try:
                    page = fb_auth.list_users()
                    while page:
                        for u in page.users:
                            auth_users.append(u)
                        page = page.get_next_page()
                except Exception as ex:
                    print(f"[AUTH WARN] list_users error: {ex}")

                # 3. İki kaynağı birleştir
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
                    combined.append(item)

                for uid, fs_data in fs_dict.items():
                    if uid not in processed_uids:
                        combined.append(fs_data)

                # Son giriş tarihine göre sırala
                combined.sort(key=lambda x: (x.get("lastSignInAt") or x.get("lastActiveAt") or 0), reverse=True)
                self.all_users = combined

                # İstatistik Hesaplama
                total = len(combined)
                now_ms = time.time() * 1000
                online_today = sum(1 for u in combined if (now_ms - (u.get("lastSignInAt") or u.get("lastActiveAt") or 0)) <= 24 * 60 * 60 * 1000)
                
                lifetime_count = sum(1 for u in combined if str(u.get("premiumPlan", "")).upper() == "LIFETIME")
                yearly_count = sum(1 for u in combined if str(u.get("premiumPlan", "")).upper() == "YEARLY" and u.get("isPremium", False) and (u.get("premiumExpiresAt", 0) or 0) > now_ms)
                premium_count = lifetime_count + yearly_count
                trial_count = sum(1 for u in combined if str(u.get("premiumPlan", "")).upper() not in ["LIFETIME", "YEARLY"] and (u.get("trialExpiresAt", 0) or 0) > now_ms)
                expired_count = total - premium_count - trial_count

                self.after(0, lambda: [
                    self.card_total.configure(text=str(total)),
                    self.card_online_today.configure(text=str(online_today)),
                    self.card_premium.configure(text=str(premium_count)),
                    self.card_trial.configure(text=str(trial_count)),
                    self.card_expired.configure(text=str(expired_count)),
                    self.status_lbl.configure(text=f"✓ {total} kullanıcı hazır.", text_color=CLR_GREEN),
                    self._apply_user_filter()
                ])
            except Exception as e:
                self.after(0, lambda: self.status_lbl.configure(text=f"Hata: {e}", text_color=CLR_RED))

        threading.Thread(target=task, daemon=True).start()

    def _load_payment_requests(self):
        if not self.db:
            return
        def task():
            try:
                reqs_ref = self.db.collection("payment_requests")
                docs = reqs_ref.stream()
                loaded = []
                for d in docs:
                    data = d.to_dict()
                    data["_id"] = d.id
                    loaded.append(data)

                loaded.sort(key=lambda x: (x.get("status") != "PENDING", -x.get("createdAt", 0)))
                self.payment_requests = loaded

                pending_count = sum(1 for r in loaded if r.get("status") == "PENDING")
                self.after(0, lambda: [
                    self.card_pending_pay.configure(text=str(pending_count)),
                    self._render_payment_requests()
                ])
            except Exception as e:
                print(f"[HATA] _load_payment_requests: {e}")
        threading.Thread(target=task, daemon=True).start()

    # ═══════════════════════════════════════════════════════════════════════════
    # KULLANICI İŞLEMLERİ & YÖNETİM AKSİYONLARI
    # ═══════════════════════════════════════════════════════════════════════════
    def _grant_premium_plan(self, uid, email, plan, amount):
        confirm = messagebox.askyesno(
            "Premium Tanımlama Onayı",
            f"Kullanıcı: {email}\nPaket: {plan} ({amount} TL)\n\nBu kullanıcıya {plan} Premium tanımlamak istiyor musunuz?"
        )
        if not confirm:
            return

        def task():
            try:
                now_ms = int(time.time() * 1000)
                expires_ms = (now_ms + 365 * 86_400_000) if plan == "YEARLY" else 0

                payload = {
                    "isPremium": True,
                    "premiumPlan": plan,
                    "premiumExpiresAt": expires_ms,
                    "entitlementUpdatedAt": now_ms,
                    "paidAt": now_ms,
                    "amountPaid": amount,
                    "paymentStatus": "APPROVED",
                    "approvedBy": "admin_panel",
                    "updatedAt": now_ms
                }
                self.db.collection("users").document(uid).update(payload)

                for u in self.all_users:
                    if u.get("_id") == uid:
                        u.update(payload)
                        break
                if self.selected_user and self.selected_user.get("_id") == uid:
                    self.selected_user.update(payload)

                self.after(0, lambda: [
                    messagebox.showinfo("Başarılı", f"✓ {email} kullanıcısına {plan} Premium tanımlandı!"),
                    self._show_user_detail(self.selected_user) if self.selected_user else None,
                    self._apply_user_filter()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"Premium tanımlanamadı:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    def _extend_trial_days(self, uid, days):
        def task():
            try:
                doc = self.db.collection("users").document(uid).get()
                user_data = doc.to_dict() if (doc and doc.exists) else {}
                cur_trial = user_data.get("trialExpiresAt", 0) or 0
                cur_prem = user_data.get("premiumExpiresAt", 0) or 0
                cur = max(cur_trial, cur_prem)
                now_ms = int(time.time() * 1000)
                new_exp = int(max(cur, now_ms) + days * 86_400_000)

                payload = {
                    "trialExpiresAt": new_exp,
                    "trialUsed": True,
                    "isTrialUsed": True,
                    "entitlementUpdatedAt": now_ms,
                    "updatedAt": now_ms
                }
                self.db.collection("users").document(uid).update(payload)

                for u in self.all_users:
                    if u.get("_id") == uid:
                        u.update(payload)
                        break
                if self.selected_user and self.selected_user.get("_id") == uid:
                    self.selected_user.update(payload)

                self.after(0, lambda: [
                    messagebox.showinfo("Başarılı", f"✓ +{days} gün deneme süresi eklendi!\nYeni Bitiş: {ts_to_str(new_exp)}"),
                    self._show_user_detail(self.selected_user) if self.selected_user else None,
                    self._apply_user_filter()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"Süre uzatılamadı:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    def _prompt_custom_days(self, uid, email):
        days_str = simpledialog.askstring("Özel Gün Ekle", f"{email}\n\nKaç gün süre eklemek istiyorsunuz? (Örn: 15, 45, 90):", parent=self)
        if not days_str or not days_str.isdigit():
            return
        days = int(days_str)
        if days <= 0:
            return
        self._extend_trial_days(uid, days)

    def _revoke_premium(self, uid, email):
        confirm = messagebox.askyesno("Lisans İptali", f"{email} kullanıcısının Premium / Deneme erişimini iptal etmek istediğinize emin misiniz?")
        if not confirm:
            return
        def task():
            try:
                now_ms = int(time.time() * 1000)
                payload = {
                    "isPremium": False,
                    "premiumPlan": "NONE",
                    "premiumExpiresAt": 0,
                    "trialExpiresAt": 0,
                    "trialUsed": False,
                    "isTrialUsed": False,
                    "paymentStatus": "REVOKED",
                    "entitlementUpdatedAt": now_ms,
                    "updatedAt": now_ms
                }
                self.db.collection("users").document(uid).update(payload)

                for u in self.all_users:
                    if u.get("_id") == uid:
                        u.update(payload)
                        break
                if self.selected_user and self.selected_user.get("_id") == uid:
                    self.selected_user.update(payload)

                self.after(0, lambda: [
                    messagebox.showinfo("Başarılı", f"✓ {email} kullanıcısının Premium yetkisi kaldırıldı."),
                    self._show_user_detail(self.selected_user) if self.selected_user else None,
                    self._apply_user_filter()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"İşlem başarısız:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    def _toggle_user_disabled(self, uid, email, disable):
        action_text = "BANLAMAK / DEVRE DIŞI BIRAKMAK" if disable else "BANINI KALDIRMAK (AKTİFLEŞTİRMEK)"
        confirm = messagebox.askyesno("Kullanıcı Durumu Değiştir", f"{email} kullanıcısını {action_text} istediğinize emin misiniz?")
        if not confirm:
            return

        def task():
            try:
                fb_auth.update_user(uid, disabled=disable)
                now_ms = int(time.time() * 1000)
                payload = {
                    "disabled": disable,
                    "updatedAt": now_ms
                }
                self.db.collection("users").document(uid).update(payload)

                for u in self.all_users:
                    if u.get("_id") == uid:
                        u.update(payload)
                        break
                if self.selected_user and self.selected_user.get("_id") == uid:
                    self.selected_user.update(payload)

                self.after(0, lambda: [
                    messagebox.showinfo("Başarılı", f"✓ Kullanıcı durumu güncellendi: {'BANLANDI' if disable else 'AKTİF'}"),
                    self._show_user_detail(self.selected_user) if self.selected_user else None,
                    self._apply_user_filter()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"İşlem başarısız:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    def _generate_password_reset_link(self, email):
        def task():
            try:
                link = fb_auth.generate_password_reset_link(email)
                self.after(0, lambda: [
                    self.clipboard_clear(),
                    self.clipboard_append(link),
                    messagebox.showinfo("Şifre Sıfırlama Linki Panoya Kopyalandı", f"Link panoya kopyalandı:\n\n{link}")
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"Link üretilemedi:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    def _confirm_delete_user(self, uid, email):
        confirm = messagebox.askyesno("DİKKAT: Kullanıcıyı Sil", f"{email} ({uid})\n\nBu kullanıcı hem Firebase Auth'tan hem veritabanından kalıcı olarak silinecektir!\nOnaylıyor musunuz?")
        if not confirm:
            return
        def task():
            try:
                try:
                    fb_auth.delete_user(uid)
                except Exception:
                    pass
                self.db.collection("users").document(uid).delete()
                self.after(0, lambda: [
                    messagebox.showinfo("Silindi", f"✓ {email} hesabı başarıyla silindi."),
                    self._show_empty_user_detail(),
                    self._load_users()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"Kullanıcı silinemedi:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    # ─── ÖDEME TALEBİ AKSİYONLARI ───
    def _approve_payment_custom(self, req, plan, amount):
        req_id = req.get("_id")
        uid = req.get("uid") or req.get("userId")
        email = req.get("userEmail") or req.get("email") or "Kullanıcı"

        if not uid:
            messagebox.showerror("Hata", "Kullanıcı UID bilgisi eksik!")
            return

        confirm = messagebox.askyesno(
            "Ödeme Onayı",
            f"Kullanıcı: {email}\nSeçilen Paket: {plan} ({amount} TL)\n\nÖdeme alındı olarak onaylanıp {plan} Premium tanımlansın mı?"
        )
        if not confirm:
            return

        def task():
            try:
                now_ms = int(time.time() * 1000)
                expires_ms = (now_ms + 365 * 86_400_000) if plan == "YEARLY" else 0

                self.db.collection("users").document(uid).update({
                    "isPremium": True,
                    "premiumPlan": plan,
                    "premiumExpiresAt": expires_ms,
                    "entitlementUpdatedAt": now_ms,
                    "paidAt": now_ms,
                    "amountPaid": amount,
                    "paymentStatus": "APPROVED"
                })

                self.db.collection("payment_requests").document(req_id).update({
                    "status": "APPROVED",
                    "approvedPlan": plan,
                    "reviewedAt": now_ms,
                    "reviewedBy": "admin_panel"
                })

                self.after(0, lambda: [
                    messagebox.showinfo("Başarılı", f"✓ Ödeme onaylandı ve {email} kullanıcısına {plan} Premium tanımlandı!"),
                    self._load_all_data()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"Ödeme onaylanamadı:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    def _reject_payment(self, req):
        req_id = req.get("_id")
        confirm = messagebox.askyesno("Talebi Reddet", "Bu ödeme bildirimini REDDETMEK istediğinize emin misiniz?")
        if not confirm:
            return
        def task():
            try:
                now_ms = int(time.time() * 1000)
                self.db.collection("payment_requests").document(req_id).update({
                    "status": "REJECTED",
                    "reviewedAt": now_ms,
                    "reviewedBy": "admin_panel"
                })
                self.after(0, lambda: [
                    messagebox.showinfo("Talep Reddedildi", "✓ Ödeme talebi reddedildi olarak işaretlendi."),
                    self._load_payment_requests()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"İşlem başarısız:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    def _delete_payment_request(self, req):
        req_id = req.get("_id")
        code = req.get("paymentCode") or req_id
        email = req.get("userEmail") or req.get("email") or "Kullanıcı"

        confirm = messagebox.askyesno(
            "Talebi Sil",
            f"Ödeme Bildirimi: {code}\nKullanıcı: {email}\n\nBu ödeme talebini veritabanından kalıcı olarak silmek istiyor musunuz?"
        )
        if not confirm:
            return

        def task():
            try:
                self.db.collection("payment_requests").document(req_id).delete()
                self.after(0, lambda: [
                    messagebox.showinfo("Silindi", f"✓ Ödeme talebi ({code}) başarıyla silindi."),
                    self._load_payment_requests()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"Talep silinemedi:\n{e}"))
        threading.Thread(target=task, daemon=True).start()

    def _clear_processed_payment_requests(self):
        processed = [r for r in self.payment_requests if r.get("status") != "PENDING"]
        if not processed:
            messagebox.showinfo("Bilgi", "Temizlenecek tamamlanmış veya reddedilmiş talep bulunmuyor.")
            return

        confirm = messagebox.askyesno(
            "Tamamlananları Temizle",
            f"Toplam {len(processed)} adet onaylanmış/reddedilmiş ödeme talebi kalıcı olarak silinecek.\n(Bekleyen taleplere dokunulmaz)\n\nOnaylıyor musunuz?"
        )
        if not confirm:
            return

        def task():
            try:
                batch = self.db.batch()
                for r in processed:
                    ref = self.db.collection("payment_requests").document(r.get("_id"))
                    batch.delete(ref)
                batch.commit()
                self.after(0, lambda: [
                    messagebox.showinfo("Başarılı", f"✓ {len(processed)} adet işlem görmüş ödeme talebi temizlendi."),
                    self._load_payment_requests()
                ])
            except Exception as e:
                self.after(0, lambda: messagebox.showerror("Hata", f"Temizleme başarısız:\n{e}"))
        threading.Thread(target=task, daemon=True).start()


if __name__ == "__main__":
    app = KaynanamAdminApp()
    app.mainloop()
