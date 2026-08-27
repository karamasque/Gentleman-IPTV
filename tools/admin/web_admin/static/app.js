/**
 * KaynanamTV — Ultra-Modern Web Admin Dashboard Logic
 * ES6+ Vanilla JS with Auth & Chart.js Integration
 */

// ── State Management ──
let allUsers = [];
let allPayments = [];
let remoteConfig = {};
let selectedUser = null;
let currentFilter = "ALL";
let currentSort = "LAST_ACTIVE";
let searchQuery = "";

let planChartInstance = null;
let activityChartInstance = null;

// ── Auth Helpers ──
function getAuthToken() {
  return localStorage.getItem("ktv_admin_token") || "";
}

function ensureLoginOverlay() {
  let overlay = document.getElementById("login-overlay");
  if (!overlay) {
    overlay = document.createElement("div");
    overlay.className = "login-overlay";
    overlay.id = "login-overlay";
    overlay.innerHTML = `
      <div class="login-card">
        <div class="login-brand">
          <div class="brand-icon">📺</div>
          <h2>KaynanamTV</h2>
          <p>Yönetici Paneli Girişi</p>
        </div>
        <form id="login-form">
          <div class="form-group">
            <label class="form-label">Kullanıcı Adı:</label>
            <input type="text" class="form-control" id="login-username" value="admin" required autocomplete="username">
          </div>
          <div class="form-group">
            <label class="form-label">Yönetici Şifresi:</label>
            <input type="password" class="form-control" id="login-password" placeholder="••••••••••••" required autocomplete="current-password">
          </div>
          <div id="login-error" class="login-error-msg" style="display: none;"></div>
          <button type="submit" class="btn btn-primary" style="width: 100%; margin-top: 10px;" id="btn-login-submit">
            <span>🔐</span>
            <span>Güvenli Giriş Yap</span>
          </button>
        </form>
      </div>
    `;
    document.body.appendChild(overlay);
    bindLoginForm();
  }
  return overlay;
}

function showLoginOverlay() {
  const overlay = ensureLoginOverlay();
  overlay.style.display = "flex";
  bindLoginForm();
  setTimeout(() => {
    const pw = document.getElementById("login-password");
    if (pw) pw.focus();
  }, 100);
}

function hideLoginOverlay() {
  const overlay = ensureLoginOverlay();
  overlay.style.display = "none";
}

async function handleLoginSubmit() {
  const userEl = document.getElementById("login-username");
  const passEl = document.getElementById("login-password");
  const errEl = document.getElementById("login-error");
  const btn = document.getElementById("btn-login-submit");

  if (!userEl || !passEl) return;
  const username = userEl.value.trim();
  const password = passEl.value.trim();

  if (!username || !password) {
    if (errEl) {
      errEl.innerText = "Lütfen kullanıcı adı ve şifre girin!";
      errEl.style.display = "block";
    }
    return;
  }

  if (errEl) errEl.style.display = "none";
  if (btn) {
    btn.disabled = true;
    btn.innerText = "Doğrulanıyor...";
  }

  try {
    const res = await fetch("/api/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password })
    });
    const data = await res.json();
    if (data.success && data.token) {
      localStorage.setItem("ktv_admin_token", data.token);
      hideLoginOverlay();
      showToast("Giriş başarılı! Yönetim paneli açıldı.");
      await initData();
    } else {
      if (errEl) {
        errEl.innerText = data.error || "Hatalı şifre veya kullanıcı adı!";
        errEl.style.display = "block";
      }
    }
  } catch (err) {
    if (errEl) {
      errEl.innerText = "Sunucuya bağlanılamadı: " + err.message;
      errEl.style.display = "block";
    }
  } finally {
    if (btn) {
      btn.disabled = false;
      btn.innerHTML = `<span>🔐</span><span>Güvenli Giriş Yap</span>`;
    }
  }
}

function bindLoginForm() {
  const form = document.getElementById("login-form");
  if (form && !form.getAttribute("data-bound")) {
    form.setAttribute("data-bound", "true");
    form.addEventListener("submit", (e) => {
      e.preventDefault();
      handleLoginSubmit();
    });
  }

  const btn = document.getElementById("btn-login-submit");
  if (btn && !btn.getAttribute("data-bound")) {
    btn.setAttribute("data-bound", "true");
    btn.addEventListener("click", (e) => {
      e.preventDefault();
      handleLoginSubmit();
    });
  }

  const pwInput = document.getElementById("login-password");
  if (pwInput && !pwInput.getAttribute("data-bound")) {
    pwInput.setAttribute("data-bound", "true");
    pwInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        handleLoginSubmit();
      }
    });
  }
}

async function apiFetch(url, options = {}) {
  const token = getAuthToken();
  const headers = options.headers ? { ...options.headers } : {};
  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }
  if (options.body && typeof options.body === "object" && !(options.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
    options.body = JSON.stringify(options.body);
  }
  options.headers = headers;

  const res = await fetch(url, options);
  if (res.status === 401 && url !== "/api/login") {
    localStorage.removeItem("ktv_admin_token");
    showLoginOverlay();
    throw new Error("Oturum süresi doldu veya yetkisiz erişim");
  }
  return res;
}

// ── Helpers ──
function formatTimestamp(ts) {
  if (!ts || ts === 0) return "—";
  try {
    const d = new Date(ts);
    return d.toLocaleString("tr-TR", {
      day: "2-digit",
      month: "2-digit",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit"
    });
  } catch (e) {
    return "—";
  }
}

function formatRelativeTime(ts) {
  if (!ts || ts === 0) return "Hiç girmedi";
  const now = Date.now();
  const diff = now - ts;
  if (diff < 0) return "Az önce";

  const sec = Math.floor(diff / 1000);
  const min = Math.floor(sec / 60);
  const hour = Math.floor(min / 60);
  const day = Math.floor(hour / 24);

  if (min < 1) return "Az önce";
  if (min < 60) return `${min} dk önce`;
  if (hour < 24) return `${hour} sa ${min % 60} dk önce`;
  if (day < 30) return `${day} gün önce`;
  return `${Math.floor(day / 30)} ay önce`;
}

function showToast(message, type = "success") {
  const container = document.getElementById("toast-container");
  const toast = document.createElement("div");
  toast.className = `toast toast-${type}`;
  toast.innerHTML = `
    <span>${type === "success" ? "✅" : "⚠️"}</span>
    <span>${message}</span>
  `;
  container.appendChild(toast);

  setTimeout(() => {
    toast.style.animation = "slideInRight 0.3s ease reverse";
    setTimeout(() => toast.remove(), 280);
  }, 4000);
}

function openModal(id) {
  const m = document.getElementById(id);
  if (m) m.classList.add("active");
}

function closeModal(id) {
  const m = document.getElementById(id);
  if (m) m.classList.remove("active");
}

// ── Navigation Tabs ──
document.querySelectorAll(".nav-item").forEach(item => {
  item.addEventListener("click", () => {
    document.querySelectorAll(".nav-item").forEach(n => n.classList.remove("active"));
    document.querySelectorAll(".tab-pane").forEach(p => p.classList.remove("active"));

    item.classList.add("active");
    const tabId = item.getAttribute("data-tab");
    const targetPane = document.getElementById(tabId);
    if (targetPane) targetPane.classList.add("active");

    const headingMap = {
      "tab-dashboard": ["Genel Bakış & Canlı İstatistikler", "Firebase Firestore & Authentication anlık canlı senkronizasyon"],
      "tab-users": ["Canlı Kullanıcı Yönetimi & Takip", "Kullanıcı lisansları, cihaz bilgileri ve anlık oturum hareketleri"],
      "tab-payments": ["Ödeme Talepleri & Onay Merkezi", "Kullanıcılardan gelen dekont ve satın alma bildirimleri"],
      "tab-config": ["Zorunlu Güncelleme & Uzaktan Yapılandırma", "Tüm TV uygulamalarına anında etki eden sürüm ve güncelleme ayarları"]
    };

    if (headingMap[tabId]) {
      document.getElementById("page-heading").innerText = headingMap[tabId][0];
      document.getElementById("page-subheading").innerText = headingMap[tabId][1];
    }
  });
});

// ── API Functions ──
async function checkStatus() {
  const pill = document.getElementById("conn-status");
  const text = document.getElementById("conn-status-text");

  try {
    const res = await fetch("/api/status");
    const data = await res.json();
    if (data.connected) {
      pill.className = "connection-pill";
      text.innerText = "Firebase Canlı Bağlı";
    } else {
      pill.className = "connection-pill disconnected";
      text.innerText = "Bağlantı Hatası";
    }
  } catch (e) {
    pill.className = "connection-pill disconnected";
    text.innerText = "Sunucu Çevrimdışı";
  }
}

async function loadStats() {
  try {
    const res = await apiFetch("/api/stats");
    if (!res.ok) return;
    const data = await res.json();

    document.getElementById("stat-total-users").innerText = data.totalUsers ?? 0;
    document.getElementById("stat-online-today").innerText = data.onlineToday ?? 0;
    document.getElementById("stat-lifetime-users").innerText = data.lifetimeUsers ?? 0;
    document.getElementById("stat-yearly-users").innerText = data.yearlyUsers ?? 0;
    document.getElementById("stat-free-users").innerText = data.freeUsers ?? 0;
    document.getElementById("stat-expired-users").innerText = data.expiredUsers ?? 0;
    document.getElementById("stat-pending-payments").innerText = data.pendingPayments ?? 0;

    const badge = document.getElementById("sidebar-pending-badge");
    if (data.pendingPayments > 0) {
      badge.style.display = "inline-block";
      badge.innerText = data.pendingPayments;
    } else {
      badge.style.display = "none";
    }

    renderCharts(data);
  } catch (e) {
    console.error("loadStats error:", e);
  }
}

function renderCharts(stats) {
  // Plan Dağılım Grafiği (Doughnut)
  const ctxPlan = document.getElementById("chart-plan-distribution");
  if (ctxPlan) {
    if (planChartInstance) planChartInstance.destroy();
    planChartInstance = new Chart(ctxPlan, {
      type: "doughnut",
      data: {
        labels: ["👑 Sınırsız", "💎 Yıllık", "🆓 Ücretsiz", "❌ Süresi Biten"],
        datasets: [{
          data: [
            stats.lifetimeUsers || 0,
            stats.yearlyUsers || 0,
            stats.freeUsers || 0,
            stats.expiredUsers || 0
          ],
          backgroundColor: ["#f59e0b", "#6366f1", "#10b981", "#ef4444"],
          borderColor: "#111827",
          borderWidth: 3,
          hoverOffset: 6
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: "bottom",
            labels: { color: "#f9fafb", font: { family: "Inter", size: 12, weight: 600 } }
          }
        },
        cutout: "68%"
      }
    });
  }

  // Aktivite Özeti Grafiği (Bar)
  const ctxAct = document.getElementById("chart-activity");
  if (ctxAct) {
    if (activityChartInstance) activityChartInstance.destroy();
    activityChartInstance = new Chart(ctxAct, {
      type: "bar",
      data: {
        labels: ["🟢 Canlı (15 dk)", "🟡 Bugün (24s)", "💎 Toplam Premium", "👥 Toplam Kayıt"],
        datasets: [{
          label: "Kullanıcı Sayısı",
          data: [
            stats.onlineLive || 0,
            stats.onlineToday || 0,
            stats.activePremium || 0,
            stats.totalUsers || 0
          ],
          backgroundColor: ["#10b981", "#38bdf8", "#818cf8", "#4f46e5"],
          borderRadius: 8
        }]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false }
        },
        scales: {
          x: { ticks: { color: "#9ca3af", font: { family: "Inter", size: 11 } }, grid: { display: false } },
          y: { ticks: { color: "#9ca3af", font: { family: "Inter", size: 11 } }, grid: { color: "rgba(255,255,255,0.05)" } }
        }
      }
    });
  }
}

async function loadUsers() {
  try {
    const res = await apiFetch("/api/users");
    if (!res.ok) return;
    const data = await res.json();
    allUsers = data.users || [];
    applyUserFilter();
  } catch (e) {
    console.error("loadUsers error:", e);
  }
}

function applyUserFilter() {
  let filtered = allUsers.filter(u => {
    // 1. Text Search
    if (searchQuery.trim()) {
      const q = searchQuery.toLowerCase();
      const email = (u.email || "").toLowerCase();
      const uid = (u._id || "").toLowerCase();
      const code = (u.code || "").toLowerCase();
      if (!email.includes(q) && !uid.includes(q) && !code.includes(q)) {
        return false;
      }
    }

    // 2. Chip Filter
    const st = u.statusCode;
    if (currentFilter === "LIFETIME") return st === "LIFETIME";
    if (currentFilter === "YEARLY") return st === "YEARLY";
    if (currentFilter === "FREE") return st === "FREE";
    if (currentFilter === "EXPIRED") return st === "EXPIRED";
    if (currentFilter === "ONLINE") {
      const lastAct = u.lastSignInAt || u.lastActiveAt || 0;
      return lastAct > 0 && (Date.now() - lastAct) <= 24 * 3600 * 1000;
    }
    return true;
  });

  // 3. Sorting
  if (currentSort === "LAST_ACTIVE") {
    filtered.sort((a, b) => (b.lastSignInAt || b.lastActiveAt || 0) - (a.lastSignInAt || a.lastActiveAt || 0));
  } else if (currentSort === "CREATED") {
    filtered.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  } else if (currentSort === "PREMIUM_FIRST") {
    filtered.sort((a, b) => {
      const aPrem = ["LIFETIME", "YEARLY"].includes(a.statusCode) ? 1 : 0;
      const bPrem = ["LIFETIME", "YEARLY"].includes(b.statusCode) ? 1 : 0;
      return bPrem - aPrem || (b.lastSignInAt || 0) - (a.lastSignInAt || 0);
    });
  } else if (currentSort === "EMAIL_ASC") {
    filtered.sort((a, b) => (a.email || "").localeCompare(b.email || ""));
  }

  renderUserCards(filtered);
}

function renderUserCards(users) {
  const container = document.getElementById("user-cards-list");
  const countStatus = document.getElementById("user-count-status");
  countStatus.innerText = `Listelenen: ${users.length} / Toplam: ${allUsers.length} kullanıcı`;

  if (users.length === 0) {
    container.innerHTML = `
      <div style="text-align: center; color: var(--txt-muted); padding: 40px 20px;">
        <p>Aramanıza veya filtreye uygun kullanıcı bulunamadı.</p>
      </div>
    `;
    return;
  }

  container.innerHTML = users.map(u => {
    const isSelected = selectedUser && selectedUser._id === u._id;
    const lastAct = u.lastSignInAt || u.lastActiveAt || 0;
    const diff = lastAct ? Date.now() - lastAct : 99999999999;
    
    let dot = "⚪";
    let statusColor = "var(--txt-muted)";
    if (diff <= 15 * 60 * 1000) {
      dot = "🟢";
      statusColor = "var(--clr-green)";
    } else if (diff <= 24 * 3600 * 1000) {
      dot = "🟡";
      statusColor = "var(--clr-gold)";
    } else if (diff <= 7 * 86400 * 1000) {
      dot = "🔵";
      statusColor = "var(--clr-cyan-light)";
    }

    let badgeClass = "badge-green";
    if (u.statusCode === "LIFETIME") badgeClass = "badge-gold";
    else if (u.statusCode === "YEARLY") badgeClass = "badge-indigo";
    else if (u.statusCode === "EXPIRED") badgeClass = "badge-red";

    return `
      <div class="user-card-item ${isSelected ? 'selected' : ''}" onclick="selectUser('${u._id}')">
        <div class="card-top-row">
          <div class="user-email-wrap">
            <span>${dot}</span>
            <span style="color: #ffffff;">${escapeHtml(u.email || 'E-posta yok')}</span>
          </div>
          <span class="user-ktv-code">${escapeHtml(u.code)}</span>
        </div>

        <div class="card-bot-row">
          <span class="badge ${badgeClass}">
            ${escapeHtml(u.statusTitle)} ${u.disabled ? '[BANLI]' : ''}
          </span>
          <span class="activity-text" style="color: ${statusColor}; font-weight: 500;">
            ${formatRelativeTime(lastAct)}
          </span>
        </div>
      </div>
    `;
  }).join("");
}

function selectUser(uid) {
  selectedUser = allUsers.find(u => u._id === uid) || null;
  applyUserFilter();
  renderUserDetail();
}

function renderUserDetail() {
  const pane = document.getElementById("user-detail-pane");
  if (!selectedUser) {
    pane.innerHTML = `
      <div style="text-align: center; color: var(--txt-muted); padding: 40px 20px;">
        <div style="font-size: 40px; margin-bottom: 12px;">👈</div>
        <p style="font-weight: 600;">Detaylarını ve yetkilerini görüntülemek için sol taraftan bir kullanıcı seçin.</p>
      </div>
    `;
    return;
  }

  const u = selectedUser;
  const lastAct = u.lastSignInAt || u.lastActiveAt || 0;
  const created = u.createdAt || 0;
  const premExp = u.premiumExpiresAt || 0;

  const cloudProviders = (u.encryptedProviders || u.providers || []).length;
  const deviceModel = u.deviceModel || u.deviceInfo || "Bilinmiyor / TV";

  pane.innerHTML = `
    <div class="detail-header-card">
      <div class="avatar-circle">👤</div>
      <div style="flex: 1; min-width: 0;">
        <h3 style="font-size: 1.15rem; font-weight: 700; word-break: break-all; color: #ffffff;">
          ${escapeHtml(u.email || 'E-posta yok')}
        </h3>
        <div style="display: flex; gap: 8px; align-items: center; margin-top: 4px;">
          <span class="user-ktv-code">${escapeHtml(u.code)}</span>
          <span style="font-size: 0.8rem; color: var(--txt-secondary);">${escapeHtml(u.statusTitle)}</span>
        </div>
      </div>
    </div>

    <div class="user-meta-table">
      <div class="meta-row">
        <span>Firebase UID:</span>
        <span style="font-size: 0.75rem;">${escapeHtml(u._id)}</span>
      </div>
      <div class="meta-row">
        <span>Kayıt Tarihi:</span>
        <span>${formatTimestamp(created)}</span>
      </div>
      <div class="meta-row">
        <span>Son Oturum:</span>
        <span>${formatTimestamp(lastAct)} (${formatRelativeTime(lastAct)})</span>
      </div>
      <div class="meta-row">
        <span>Premium Bitiş:</span>
        <span style="color: ${u.statusCode === 'LIFETIME' ? 'var(--clr-gold)' : '#ffffff'};">
          ${u.statusCode === 'LIFETIME' ? 'Süresiz (Ömür Boyu)' : (premExp > 0 ? formatTimestamp(premExp) : 'Yok')}
        </span>
      </div>
      <div class="meta-row">
        <span>E2EE Bulut Sağlayıcı:</span>
        <span>${cloudProviders} Adet IPTV Sağlayıcı Kayıtlı</span>
      </div>
      <div class="meta-row">
        <span>Cihaz / Platform:</span>
        <span>${escapeHtml(deviceModel)}</span>
      </div>
      <div class="meta-row">
        <span>Hesap Durumu:</span>
        <span style="color: ${u.disabled ? 'var(--clr-red)' : 'var(--clr-green)'}; font-weight: 700;">
          ${u.disabled ? '🛑 BANLI (Giriş Engellendi)' : '🟢 AKTİF'}
        </span>
      </div>
    </div>

    <div class="actions-group">
      <div class="action-row">
        <button class="btn btn-primary btn-sm" onclick="grantPlan('${u._id}', 'LIFETIME', 600)">
          <span>👑</span>
          <span>Sınırsız Premium Yap</span>
        </button>
        <button class="btn btn-secondary btn-sm" onclick="grantPlan('${u._id}', 'YEARLY', 300)">
          <span>💎</span>
          <span>+1 Yıl Premium Ekle</span>
        </button>
      </div>

      <div class="action-row">
        <button class="btn btn-secondary btn-sm" onclick="openCustomDaysModal('${u._id}')">
          <span>⏳</span>
          <span>Özel Gün Ekle</span>
        </button>
        <button class="btn btn-secondary btn-sm" onclick="grantPlan('${u._id}', 'FREE', 0)">
          <span>🆓</span>
          <span>Ücretsiz Hesaba Çevir</span>
        </button>
      </div>

      <div class="action-row">
        <button class="btn btn-secondary btn-sm" onclick="generatePasswordReset('${escapeHtml(u.email)}')">
          <span>🔑</span>
          <span>Şifre Sıfırlama Linki</span>
        </button>
        <button class="btn btn-secondary btn-sm" onclick="toggleUserDisabled('${u._id}', ${!u.disabled})">
          <span>${u.disabled ? '🟢' : '🛑'}</span>
          <span>${u.disabled ? 'Banını Kaldır' : 'Kullanıcıyı Banla'}</span>
        </button>
      </div>

      <button class="btn btn-danger btn-sm" style="margin-top: 6px;" onclick="deleteUser('${u._id}', '${escapeHtml(u.email)}')">
        <span>🗑️</span>
        <span>Kullanıcıyı Kalıcı Olarak Sil</span>
      </button>
    </div>
  `;
}

// ── User Actions ──
async function grantPlan(uid, plan, amount) {
  const planNames = { "LIFETIME": "Ömür Boyu (Sınırsız)", "YEARLY": "1 Yıllık", "FREE": "Ücretsiz" };
  if (!confirm(`Bu kullanıcıya ${planNames[plan]} lisansı tanımlamak istiyor musunuz?`)) return;

  try {
    const res = await apiFetch("/api/user/grant-plan", {
      method: "POST",
      body: { uid, plan, amount }
    });
    const data = await res.json();
    if (data.success) {
      showToast(`Kullanıcı başarıyla ${planNames[plan]} yapıldı.`);
      await loadUsers();
      await loadStats();
      if (selectedUser && selectedUser._id === uid) selectUser(uid);
    } else {
      showToast(data.error || "İşlem başarısız", "error");
    }
  } catch (e) {
    showToast("Sunucu hatası", "error");
  }
}

function openCustomDaysModal(uid) {
  const u = allUsers.find(x => x._id === uid);
  if (!u) return;
  document.getElementById("modal-custom-user-text").innerText = `${u.email} kullanıcısına eklenecek gün sayısını girin:`;
  document.getElementById("btn-modal-confirm-days").onclick = async () => {
    const days = parseInt(document.getElementById("modal-days-input").value, 10);
    if (!days || days <= 0) return;
    closeModal("modal-custom-days");

    try {
      const res = await apiFetch("/api/user/extend-days", {
        method: "POST",
        body: { uid, days }
      });
      const data = await res.json();
      if (data.success) {
        showToast(`+${days} gün süre eklendi.`);
        await loadUsers();
        if (selectedUser && selectedUser._id === uid) selectUser(uid);
      } else {
        showToast(data.error || "Hata", "error");
      }
    } catch (e) {
      showToast("İşlem hatası", "error");
    }
  };
  openModal("modal-custom-days");
}

async function toggleUserDisabled(uid, disabled) {
  const act = disabled ? "banlamak" : "banını kaldırmak";
  if (!confirm(`Kullanıcıyı ${act} istediğinize emin misiniz?`)) return;

  try {
    const res = await apiFetch("/api/user/toggle-disabled", {
      method: "POST",
      body: { uid, disabled }
    });
    const data = await res.json();
    if (data.success) {
      showToast(`Kullanıcı durumu güncellendi: ${disabled ? 'BANLANDI' : 'AKTİF'}`);
      await loadUsers();
      if (selectedUser && selectedUser._id === uid) selectUser(uid);
    }
  } catch (e) {
    showToast("Hata oluştu", "error");
  }
}

async function generatePasswordReset(email) {
  try {
    const res = await apiFetch("/api/user/reset-password", {
      method: "POST",
      body: { email }
    });
    const data = await res.json();
    if (data.success) {
      document.getElementById("modal-reset-link-input").value = data.resetLink;
      document.getElementById("btn-modal-copy-link").onclick = () => {
        navigator.clipboard.writeText(data.resetLink);
        showToast("Şifre sıfırlama linki panoya kopyalandı!");
      };
      openModal("modal-reset-link");
    } else {
      showToast(data.error || "Link üretilemedi", "error");
    }
  } catch (e) {
    showToast("Bağlantı hatası", "error");
  }
}

async function deleteUser(uid, email) {
  if (!confirm(`DİKKAT: ${email} hesabı hem Firebase Auth'tan hem veritabanından kalıcı olarak silinecektir!\nOnaylıyor musunuz?`)) return;

  try {
    const res = await apiFetch("/api/user/delete", {
      method: "POST",
      body: { uid }
    });
    const data = await res.json();
    if (data.success) {
      showToast("Kullanıcı başarıyla silindi.");
      selectedUser = null;
      renderUserDetail();
      await loadUsers();
      await loadStats();
    } else {
      showToast(data.error || "Silinemedi", "error");
    }
  } catch (e) {
    showToast("Hata oluştu", "error");
  }
}

// ── Payments Management ──
async function loadPayments() {
  try {
    const res = await apiFetch("/api/payments");
    if (!res.ok) return;
    const data = await res.json();
    allPayments = data.payments || [];
    renderPaymentsTable(allPayments);
  } catch (e) {
    console.error("loadPayments error:", e);
  }
}

function renderPaymentsTable(payments) {
  const tbody = document.getElementById("payments-table-body");
  if (payments.length === 0) {
    tbody.innerHTML = `
      <tr>
        <td colspan="7" style="text-align: center; color: var(--txt-muted); padding: 30px;">
          Henüz herhangi bir ödeme bildirimi bulunmuyor.
        </td>
      </tr>
    `;
    return;
  }

  tbody.innerHTML = payments.map(p => {
    const date = p.requestedAt || p.createdAt || 0;
    const email = p.userEmail || p.email || "Bilinmiyor";
    const plan = p.requestedPlan || p.plan || "LIFETIME";
    const amount = p.amount || (plan === "LIFETIME" ? "600 TL" : "300 TL");
    const notes = p.paymentCode || p.description || p.notes || "—";
    const status = p.status || "PENDING";

    let statusBadge = `<span class="badge badge-gold">⏳ Bekliyor</span>`;
    if (status === "APPROVED") statusBadge = `<span class="badge badge-green">✓ Onaylandı</span>`;
    else if (status === "REJECTED") statusBadge = `<span class="badge badge-red">❌ Reddedildi</span>`;

    const isPending = status === "PENDING";

    return `
      <tr>
        <td style="color: var(--txt-secondary); font-size: 0.82rem;">${formatTimestamp(date)}</td>
        <td style="font-weight: 700; color: #ffffff;">${escapeHtml(email)}</td>
        <td><span class="badge ${plan === 'LIFETIME' ? 'badge-gold' : 'badge-indigo'}">${escapeHtml(plan)}</span></td>
        <td style="font-family: 'JetBrains Mono', monospace; font-weight: 700; color: var(--clr-green);">${escapeHtml(String(amount))}</td>
        <td style="font-size: 0.85rem; color: var(--txt-secondary);">${escapeHtml(notes)}</td>
        <td>${statusBadge}</td>
        <td>
          <div style="display: flex; gap: 6px;">
            ${isPending ? `
              <button class="btn btn-success btn-sm" onclick="approvePayment('${p._id}', '${plan}', 600)" title="Onayla & Premium Yap">✓ Onayla</button>
              <button class="btn btn-danger btn-sm" onclick="rejectPayment('${p._id}')" title="Talebi Reddet">✕ Reddet</button>
            ` : `
              <button class="btn btn-secondary btn-sm" onclick="deletePayment('${p._id}')" title="Talebi Sil">🗑️ Sil</button>
            `}
          </div>
        </td>
      </tr>
    `;
  }).join("");
}

async function approvePayment(requestId, plan, amount) {
  if (!confirm("Ödemeyi onaylayıp kullanıcıya Premium lisansı tanımlamak istiyor musunuz?")) return;

  try {
    const res = await apiFetch("/api/payments/approve", {
      method: "POST",
      body: { requestId, plan, amount }
    });
    const data = await res.json();
    if (data.success) {
      showToast("Ödeme onaylandı ve Premium aktif edildi.");
      await loadPayments();
      await loadStats();
      await loadUsers();
    } else {
      showToast(data.error || "Hata", "error");
    }
  } catch (e) {
    showToast("İşlem hatası", "error");
  }
}

async function rejectPayment(requestId) {
  if (!confirm("Bu ödeme talebini REDDETMEK istediğinize emin misiniz?")) return;

  try {
    const res = await apiFetch("/api/payments/reject", {
      method: "POST",
      body: { requestId }
    });
    const data = await res.json();
    if (data.success) {
      showToast("Ödeme talebi reddedildi.");
      await loadPayments();
      await loadStats();
    }
  } catch (e) {
    showToast("İşlem hatası", "error");
  }
}

async function deletePayment(requestId) {
  if (!confirm("Bu ödeme bildirimini silmek istediğinize emin misiniz?")) return;

  try {
    const res = await apiFetch("/api/payments/delete", {
      method: "POST",
      body: { requestId }
    });
    const data = await res.json();
    if (data.success) {
      showToast("Ödeme talebi silindi.");
      await loadPayments();
      await loadStats();
    }
  } catch (e) {
    showToast("Hata oluştu", "error");
  }
}

document.getElementById("btn-clear-processed-payments").addEventListener("click", async () => {
  if (!confirm("Onaylanmış veya reddedilmiş tüm ödeme kayıtları kalıcı olarak temizlenecek. Onaylıyor musunuz?")) return;

  try {
    const res = await apiFetch("/api/payments/clear-processed", { method: "POST" });
    const data = await res.json();
    if (data.success) {
      showToast(data.message || "Temizleme tamamlandı.");
      await loadPayments();
      await loadStats();
    }
  } catch (e) {
    showToast("İşlem hatası", "error");
  }
});

document.getElementById("btn-delete-all-payments").addEventListener("click", async () => {
  if (!confirm("DİKKAT: Bekleyenler dahil TÜM ödeme bildirimleri kalıcı olarak silinecektir!\nBu işlem geri alınamaz.\n\nOnaylıyor musunuz?")) return;

  try {
    const res = await apiFetch("/api/payments/delete-all", { method: "POST" });
    const data = await res.json();
    if (data.success) {
      showToast(data.message || "Tüm ödeme talepleri silindi.");
      await loadPayments();
      await loadStats();
    } else {
      showToast(data.error || "Hata oluştu", "error");
    }
  } catch (e) {
    showToast("İşlem hatası", "error");
  }
});

// ── Remote Config / Zorunlu Güncelleme ──
async function loadConfig() {
  try {
    const res = await apiFetch("/api/config");
    if (!res.ok) return;
    const cfg = await res.json();
    remoteConfig = cfg;

    document.getElementById("cfg-latest-code").value = cfg.latestVersionCode ?? 93;
    document.getElementById("cfg-latest-name").value = cfg.latestVersionName ?? "1.0.93";
    document.getElementById("cfg-min-code").value = cfg.minimumSupportedVersionCode ?? 67;
    document.getElementById("cfg-force-update").checked = Boolean(cfg.forceUpdate ?? true);
    document.getElementById("cfg-apk-url").value = cfg.apkDownloadUrl || "https://github.com/karamasque/Gentleman-IPTV/releases/latest/download/KaynanamTV.apk";
    
    const defaultNotes = (
      "KaynanamTV v1.0.93 Güncellemesi Yayınlandı!\n\n" +
      "• Film ve Dizi VOD oynatma optimizasyonları ve anında başlatma\n" +
      "• Çoklu Cihaz E2EE Cloud IPTV Senkronizasyonu (Telefon & TV)\n" +
      "• Trakt.tv izleme geçmişi ve scrobble entegrasyonu\n" +
      "• EPG Arşiv TV / Catch-Up geriye dönük yayın izleme\n" +
      "• Sınırsız Ücretsiz üyelik modeli (Deneme süresi kısıtı kaldırıldı)\n" +
      "• Yüksek performanslı Live TV ve donma önleme motoru"
    );
    document.getElementById("cfg-release-notes").value = cfg.releaseNotes || defaultNotes;
  } catch (e) {
    console.error("loadConfig error:", e);
  }
}

document.getElementById("btn-save-config").addEventListener("click", async () => {
  const latestCode = parseInt(document.getElementById("cfg-latest-code").value, 10);
  const latestName = document.getElementById("cfg-latest-name").value.trim();
  const minCode = parseInt(document.getElementById("cfg-min-code").value, 10);
  const forceUpdate = document.getElementById("cfg-force-update").checked;
  const apkUrl = document.getElementById("cfg-apk-url").value.trim();
  const releaseNotes = document.getElementById("cfg-release-notes").value.trim();

  if (!confirm(`Sürüm Ayarları Canlıya Alınacak:\n- Sürüm Kodu: ${latestCode} (${latestName})\n- Min Sürüm Kodu: ${minCode}\n- Zorunlu Güncelleme: ${forceUpdate ? 'AÇIK' : 'KAPALI'}\n\nOnaylıyor musunuz?`)) return;

  try {
    const res = await apiFetch("/api/config/save", {
      method: "POST",
      body: {
        latestVersionCode: latestCode,
        latestVersionName: latestName,
        minimumSupportedVersionCode: minCode,
        forceUpdate,
        apkDownloadUrl: apkUrl,
        releaseNotes
      }
    });
    const data = await res.json();
    if (data.success) {
      showToast("✓ Zorunlu güncelleme ayarları tüm TV'lere anında yayınlandı!");
    } else {
      showToast(data.error || "Kayıt başarısız", "error");
    }
  } catch (e) {
    showToast("Sunucu hatası", "error");
  }
});

document.getElementById("btn-emergency-lock").addEventListener("click", () => {
  const latestCode = parseInt(document.getElementById("cfg-latest-code").value, 10) || 93;
  if (!confirm(`DİKKAT: Sürüm kodu ${latestCode} altındaki TÜM kullanıcılar zorunlu güncelleme ekranına kilitlenecektir.\n\nOnaylıyor musunuz?`)) return;

  document.getElementById("cfg-min-code").value = latestCode;
  document.getElementById("cfg-force-update").checked = true;
  document.getElementById("btn-save-config").click();
});

// ── Search & Filter Listeners ──
let searchDebounceTimer = null;
document.getElementById("user-search-input").addEventListener("input", (e) => {
  clearTimeout(searchDebounceTimer);
  searchDebounceTimer = setTimeout(() => {
    searchQuery = e.target.value;
    applyUserFilter();
  }, 250);
});

document.querySelectorAll(".filter-chips .chip").forEach(chip => {
  chip.addEventListener("click", () => {
    document.querySelectorAll(".filter-chips .chip").forEach(c => c.classList.remove("active"));
    chip.classList.add("active");
    currentFilter = chip.getAttribute("data-filter");
    applyUserFilter();
  });
});

document.getElementById("user-sort-select").addEventListener("change", (e) => {
  currentSort = e.target.value;
  applyUserFilter();
});

document.getElementById("btn-refresh").addEventListener("click", async () => {
  showToast("Veriler yenileniyor...");
  await Promise.all([checkStatus(), loadStats(), loadUsers(), loadPayments(), loadConfig()]);
  showToast("Tüm veriler güncellendi!");
});

function doLogout() {
  if (!confirm("Yönetim panelinden çıkış yapmak istiyor musunuz?")) return;
  try {
    apiFetch("/api/logout", { method: "POST" });
  } catch (e) {}
  localStorage.removeItem("ktv_admin_token");
  showLoginOverlay();
  showToast("Oturum güvenle kapatıldı.");
}

const btnLogout = document.getElementById("btn-logout");
if (btnLogout) btnLogout.addEventListener("click", doLogout);

const btnTopLogout = document.getElementById("btn-top-logout");
if (btnTopLogout) btnTopLogout.addEventListener("click", doLogout);

function escapeHtml(str) {
  if (!str) return "";
  return String(str)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#039;");
}

async function initData() {
  await Promise.all([
    loadStats(),
    loadUsers(),
    loadPayments(),
    loadConfig()
  ]);
}

// ── Initial Bootstrap ──
async function init() {
  bindLoginForm();
  await checkStatus();

  // Token kontrolü
  const token = getAuthToken();
  if (!token) {
    showLoginOverlay();
    return;
  }

  try {
    const authRes = await apiFetch("/api/auth-check");
    const authData = await authRes.json();
    if (authData.authenticated) {
      hideLoginOverlay();
      await initData();
    } else {
      showLoginOverlay();
    }
  } catch (e) {
    showLoginOverlay();
  }

  // Periodic Refresh every 30 seconds
  setInterval(() => {
    if (getAuthToken()) {
      loadStats();
      loadPayments();
    }
  }, 30000);
}

document.addEventListener("DOMContentLoaded", init);
