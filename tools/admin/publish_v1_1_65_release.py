import urllib.request
import json
import os
import sys

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

import subprocess

def get_token():
    token = os.environ.get("GITHUB_TOKEN", "")
    if token:
        return token
    try:
        remote = subprocess.check_output(["git", "remote", "get-url", "origin"], text=True).strip()
        if "@github.com" in remote and "https://" in remote:
            user_token = remote.split("https://")[1].split("@github.com")[0]
            if ":" in user_token:
                return user_token.split(":")[1]
    except Exception:
        pass
    return ""

TOKEN = get_token()
REPO = "karamasque/Gentleman-IPTV"
TAG = "v1.1.65"
APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\release_apks\KaynanamTV.apk"

headers = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github.v3+json",
    "User-Agent": "KaynanamTV-Release-Script"
}

print(f"=== PUBLISHING GITHUB RELEASE {TAG} ===")

# 1. Create or fetch Release
url = f"https://api.github.com/repos/{REPO}/releases"
release_body = (
    "KaynanamTV v1.1.65 (Zorunlu Güncelleme)\n\n"
    "### 🎨 Oynatıcı HUD & Arayüz Temaları (10 Yeni Stil)\n"
    "- **Dinamik Cam (Glassmorphism) Efekti:** 10 farklı özel renk paleti ve yüksek kaliteli buzlu cam (frosted glass) tasarımı eklendi.\n"
    "  1. Modern Cam (Klasik Siyan & Mavi)\n"
    "  2. Neon Siber (Cyberpunk Neon Pembe & Turkuaz)\n"
    "  3. Minimalist Temiz (Sade ve Şeffaf)\n"
    "  4. Sinema Altını (Lüks Amber & Altın)\n"
    "  5. Zümrüt Matrisi (Neon Yeşil & Nane)\n"
    "  6. Kızıl Gece (Ateşli Yakut & Kırmızı)\n"
    "  7. Kutup Işıkları (İskandinav Turkuazı & Aurora)\n"
    "  8. Gün Batımı Turuncusu (Sıcak Portakal & Mercan)\n"
    "  9. Retro Synthwave (80'ler Mor & Magenta)\n"
    "  10. Titanyum Çelik (OLED Siyah & Metalik Gri)\n"
    "- **Tam HUD Senkronizasyonu:** Seçilen tema rengi yalnızca alt bilgi barına değil; yan kanal listesi, hızlı rehber penceresi, parça seçim diyalogları ve tüm OSD kontrollerine anlık olarak yansıtıldı.\n"
    "- **Ayarlar Menüsü:** Oynatma ayarları altına 10 temayı renk önizleme noktalarıyla sunan modern tema seçici eklendi.\n\n"
    "### ⚡ Oynatma ve Zaplama Kararlılığı\n"
    "- Oynatıcı mimarisi en kararlı ve hızlı Media3 (ExoPlayer) çekirdeğine sabitlenerek kanal zap geçişleri ve canlı akış stabilitesi en üst düzeye çıkarıldı.\n"
    "- R8/ProGuard optimizasyonları ve kaynak küçültme ile APK boyutu optimize edildi."
)

data = {
    "tag_name": TAG,
    "target_commitish": "master",
    "name": f"KaynanamTV {TAG}",
    "body": release_body,
    "draft": False,
    "prerelease": False
}

req = urllib.request.Request(url, data=json.dumps(data).encode("utf-8"), headers=headers, method="POST")
release_info = None

try:
    with urllib.request.urlopen(req) as resp:
        release_info = json.loads(resp.read().decode("utf-8"))
        print(f"✅ Release Created: ID {release_info['id']}, URL: {release_info['html_url']}")
except urllib.error.HTTPError as e:
    err_body = e.read().decode("utf-8")
    print(f"HTTP response {e.code}: {err_body}")
    # If release already exists, fetch it
    req_get = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", headers=headers)
    with urllib.request.urlopen(req_get) as resp_get:
        release_info = json.loads(resp_get.read().decode("utf-8"))
        print(f"ℹ️ Found existing release: ID {release_info['id']}")

upload_url_template = release_info["upload_url"]
upload_url = upload_url_template.split("{")[0] + "?name=KaynanamTV.apk"

print(f"Uploading APK ({os.path.getsize(APK_PATH)} bytes) to: {upload_url}...")
with open(APK_PATH, "rb") as f:
    apk_data = f.read()

upload_headers = {
    "Authorization": f"token {TOKEN}",
    "Content-Type": "application/vnd.android.package-archive",
    "User-Agent": "KaynanamTV-Release-Script"
}

# If asset already exists, delete first
existing_assets = release_info.get("assets", [])
for asset in existing_assets:
    if asset["name"] == "KaynanamTV.apk":
        print(f"Deleting existing asset ID {asset['id']}...")
        del_req = urllib.request.Request(asset["url"], headers=headers, method="DELETE")
        try:
            with urllib.request.urlopen(del_req) as resp_del:
                print("Deleted old asset.")
        except Exception as e_del:
            print(f"Warning deleting old asset: {e_del}")

req_upload = urllib.request.Request(upload_url, data=apk_data, headers=upload_headers, method="POST")
with urllib.request.urlopen(req_upload) as resp_up:
    asset_info = json.loads(resp_up.read().decode("utf-8"))
    print(f"✅ APK Asset Uploaded successfully! Download URL: {asset_info['browser_download_url']}")
    print(f"Asset Size: {asset_info['size']} bytes")
