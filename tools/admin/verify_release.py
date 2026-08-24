import urllib.request
import json
import os
import sys
import firebase_admin
from firebase_admin import credentials, firestore

if sys.stdout and hasattr(sys.stdout, "reconfigure"):
    try:
        sys.stdout.reconfigure(encoding="utf-8")
    except Exception:
        pass

print("=" * 60)
print("1. VERIFYING GITHUB RELEASE")
print("=" * 60)
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
TAG = "v1.1.1"

headers = {
    "Authorization": f"token {TOKEN}",
    "Accept": "application/vnd.github.v3+json",
    "User-Agent": "KaynanamTV-Verifier"
}

req_get = urllib.request.Request(f"https://api.github.com/repos/{REPO}/releases/tags/{TAG}", headers=headers)
with urllib.request.urlopen(req_get) as resp:
    rel = json.loads(resp.read().decode("utf-8"))
    print(f"Release Tag: {rel['tag_name']}")
    print(f"Release Name: {rel['name']}")
    print(f"Target Commitish: {rel['target_commitish']}")
    print("Assets:")
    for asset in rel.get("assets", []):
        print(f"  - Name: {asset['name']}, Size: {asset['size']} bytes, URL: {asset['browser_download_url']}")

print("\n" + "=" * 60)
print("2. VERIFYING FIRESTORE LIVE CONFIG")
print("=" * 60)
candidates = [
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "serviceAccountKey.json"),
    r"C:\Users\kilic\Desktop\Yeni klasör\serviceAccountKey.json",
    r"D:\Masaüstü\Yeni klasör\serviceAccountKey.json",
    os.path.join(os.path.expanduser("~"), "Desktop", "Yeni klasör", "serviceAccountKey.json"),
    os.path.join(os.path.expanduser("~"), "Desktop", "serviceAccountKey.json"),
]
cred_path = next(p for p in candidates if os.path.exists(p))
if not firebase_admin._apps:
    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred)
db = firestore.client()

doc = db.collection("config").document("app_config").get()
if doc.exists:
    data = doc.to_dict()
    print("Firestore config/app_config contents:")
    for k, v in data.items():
        print(f"  - {k}: {v}")
else:
    print("ERROR: Document config/app_config does not exist!")

print("=" * 60)
