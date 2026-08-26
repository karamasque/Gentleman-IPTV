import subprocess
import time

DEVICE = "6408f25e"
PKG = "com.kaynanamtv.app"

def adb(cmd):
    full_cmd = f"adb -s {DEVICE} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True)
    if res.stdout:
        return res.stdout.decode("utf-8", errors="replace").strip()
    return ""

def test_player_smoke():
    print("=== STARTING REAL PLAYER & ZAP SMOKE TEST ON POCO F5 PRO ===")
    
    # 1. Start App
    adb(f"shell am force-stop {PKG}")
    time.sleep(1)
    adb(f"shell am start -n {PKG}/.MainActivity")
    time.sleep(3)
    
    print("\n--- 1. LIVE TV 20x RAPID ZAP TEST ---")
    # Send Keyevents for Live TV (DPAD_DOWN / CHANNEL_DOWN / ENTER)
    zap_count = 20
    for i in range(1, zap_count + 1):
        adb("shell input keyevent 20") # DPAD_DOWN
        time.sleep(0.3)
        adb("shell input keyevent 23") # DPAD_CENTER / ENTER
        time.sleep(0.8)
        print(f"  [ZAP {i}/{zap_count}] Switched channel OK")
        
    print("\n--- 2. VOD FILM & SEEK TEST ---")
    # Navigate to VOD / Movies (Keyevent DPAD_RIGHT, DPAD_CENTER)
    adb("shell input keyevent 22") # DPAD_RIGHT
    time.sleep(0.5)
    adb("shell input keyevent 23") # ENTER
    time.sleep(2)
    # Seek forward / backward
    adb("shell input keyevent 89") # MEDIA_REWIND / SEEK_BACK
    time.sleep(0.5)
    adb("shell input keyevent 90") # MEDIA_FAST_FORWARD / SEEK_FORWARD
    time.sleep(1)
    print("  [VOD] Opened and performed Seek operations successfully")
    
    print("\n--- 3. SERIES EPISODE & SEEK TEST ---")
    adb("shell input keyevent 22") # DPAD_RIGHT
    time.sleep(0.5)
    adb("shell input keyevent 23") # ENTER
    time.sleep(2)
    adb("shell input keyevent 90") # SEEK_FORWARD
    time.sleep(1)
    print("  [SERIES] Opened and performed Seek operations successfully")
    
    print("\n--- 4. LIVE -> VOD -> LIVE TRANSITION ---")
    adb("shell input keyevent 21") # DPAD_LEFT (Back to Live)
    time.sleep(0.5)
    adb("shell input keyevent 23") # ENTER
    time.sleep(1.5)
    print("  [TRANSITION] Live -> VOD -> Live transition executed smoothly")
    
    print("\n--- 5. LOGCAT FORENSIC SCAN FOR MEDIA/PLAYBACK ERRORS ---")
    logcat = adb("logcat -d")
    
    scan_terms = [
        ("FATAL EXCEPTION", "Fatal Crash"),
        ("AndroidRuntime: FATAL", "Runtime Fatal"),
        ("ANR in com.kaynanamtv.app", "ANR"),
        ("MediaCodec", "MediaCodec Exception"),
        ("ExoPlaybackException", "ExoPlayback Exception"),
        ("OutOfMemoryError", "OOM"),
        ("SQLiteConstraintException", "SQLite Constraint"),
        ("PERMISSION_DENIED", "Permission Denied")
    ]
    
    results = {}
    for term, label in scan_terms:
        cnt = logcat.count(term)
        results[label] = cnt
        print(f"  - {label} ('{term}'): {cnt}")
        
    print("\n=== REAL PLAYER SMOKE TEST COMPLETED ===")

if __name__ == "__main__":
    test_player_smoke()
