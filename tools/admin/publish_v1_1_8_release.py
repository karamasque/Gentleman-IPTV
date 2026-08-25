import urllib.request
import json
import os
import sys
import subprocess

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

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
TAG = "v1.1.8"
APK_PATH = r"D:\Masaüstü\KaynanamTV-IPTV\app\build\outputs\apk\release\app-release.apk"

headers = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github.v3+json",
    "User-Agent": "KaynanamTV-Release-Script"
}

print(f"=== PUBLISHING GITHUB RELEASE {TAG} ===")

# 1. Create or fetch Release
url = f"https://api.github.com/repos/{REPO}/releases"
release_body = (
    "KaynanamTV v1.1.8\n\n"
    "- Sürüm güncellemesi ve genel stabilite optimizasyonları yapıldı.\n"
    "- EPG HTTP ETag / 304 koşullu istek desteği ile gereksiz ağ trafiği ve arka plan ayrıştırma yükü azaltıldı.\n"
    "- IPTV sağlayıcı hesap eşitleme ve temiz kurulum geri yükleme kararlılığı güvenceye alındı.\n"
    "- Performans ve bellek optimizasyonları uygulandı."
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
try:
    with urllib.request.urlopen(req_upload) as resp_upload:
        asset_info = json.loads(resp_upload.read().decode("utf-8"))
        print(f"✅ APK Asset Uploaded: Name: {asset_info['name']}, Size: {asset_info['size']} bytes, Download URL: {asset_info['browser_download_url']}")
except urllib.error.HTTPError as e:
    print(f"Upload error {e.code}: {e.read().decode('utf-8')}")
