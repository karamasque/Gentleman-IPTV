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
TAG = "v1.1.10"

headers = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github.v3+json",
    "User-Agent": "KaynanamTV-Release-Script"
}

print(f"=== 1. DELETING GITHUB RELEASE {TAG} ===")
try:
    req_get = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", headers=headers)
    with urllib.request.urlopen(req_get) as resp_get:
        release_info = json.loads(resp_get.read().decode("utf-8"))
        release_id = release_info["id"]
        print(f"Found release ID {release_id}. Deleting...")
        del_req = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/{release_id}", headers=headers, method="DELETE")
        with urllib.request.urlopen(del_req) as resp_del:
            print(f"✅ GitHub Release {TAG} (ID: {release_id}) successfully deleted!")
except urllib.error.HTTPError as e:
    print(f"Release lookup/delete returned HTTP {e.code}: {e.read().decode('utf-8')}")
except Exception as e:
    print(f"Error deleting release: {e}")

print("\n=== 2. ROLLING BACK FIRESTORE CONFIG TO v1.1.9 ===")
try:
    from update_app_config import update_config
    notes_v1_1_9 = (
        "KaynanamTV v1.1.9\n\n"
        "- Canlı TV kanal açılış ve zap performansı optimize edildi.\n"
        "- Arabelleğe alma ve ilk kare akışı ağ koşullarına göre dengelendi.\n"
        "- Hızlı kanal geçişlerinde kaynak temizleme ve tek canlı bağlantı kuralı güvenceye alındı.\n"
        "- Playback recovery ve MediaCodec kararlılığı iyileştirildi.\n"
        "- Unit test altyapısı stabilize edildi (1200/1200 test PASS)."
    )
    success = update_config(
        latest_code=109,
        latest_name="1.1.9",
        min_supported_code=109,
        force_update=True,
        apk_url="https://github.com/karamasque/Gentleman-IPTV/releases/download/v1.1.9/KaynanamTV.apk",
        release_notes=notes_v1_1_9
    )
    if success:
        print("✅ Firestore config rolled back to v1.1.9 successfully!")
    else:
        print("❌ Firestore rollback failed!")
except Exception as e:
    print(f"Error rolling back firestore config: {e}")
