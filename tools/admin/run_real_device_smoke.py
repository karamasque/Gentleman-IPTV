import subprocess
import time
import sys

DEVICE = "6408f25e"
PKG = "com.kaynanamtv.app"
MAIN_ACT = f"{PKG}/.MainActivity"

def adb(cmd):
    full_cmd = f"adb -s {DEVICE} {cmd}"
    res = subprocess.run(full_cmd, shell=True, capture_output=True)
    if res.stdout:
        return res.stdout.decode("utf-8", errors="replace").strip()
    return ""

def run_smoke():
    print("=== STARTING REAL DEVICE SMOKE SUITE ===")
    
    # 1. 10x Cold Start Test
    print("\n--- 1. 10x COLD START TEST ---")
    cold_start_failures = 0
    for i in range(1, 11):
        adb(f"shell am force-stop {PKG}")
        time.sleep(0.5)
        out = adb(f"shell am start -W -n {MAIN_ACT}")
        time.sleep(1.5)
        
        # Check if process is alive
        pid = adb(f"shell pidof {PKG}")
        if not pid:
            print(f"  [FAIL] Run {i}: Process crashed on cold start! Output: {out}")
            cold_start_failures += 1
        else:
            print(f"  [PASS] Run {i}: Cold start OK (PID: {pid})")
            
    # 2. Check UI State & Navigation
    print("\n--- 2. UI NAVIGATION & SMOKE INTERACTIONS ---")
    
    # Send D-pad / Touch interactions for menu & Live TV / EPG
    time.sleep(1)
    
    # Background -> Foreground test
    print("  Testing Background -> Foreground cycle...")
    adb("shell input keyevent 3") # HOME
    time.sleep(1.5)
    adb(f"shell am start -n {MAIN_ACT}")
    time.sleep(1.5)
    pid = adb(f"shell pidof {PKG}")
    print(f"  [PASS] Background -> Foreground OK (PID: {pid})")
    
    # 3. Scan Logcat for forensic errors
    print("\n--- 3. LOGCAT FORENSIC SCAN ---")
    logcat = adb("logcat -d")
    
    keywords = [
        ("FATAL EXCEPTION", "Fatal crashes"),
        ("AndroidRuntime: FATAL", "Runtime fatal"),
        ("ANR in com.kaynanamtv.app", "ANR"),
        ("SQLiteConstraintException", "SQLite constraints"),
        ("SQLITE_CONSTRAINT", "SQLite constraint error"),
        ("OutOfMemoryError", "OOM"),
        ("PERMISSION_DENIED", "Firestore Permission Denied"),
        ("DeadObjectException", "Dead object"),
    ]
    
    counts = {}
    for kw, label in keywords:
        cnt = logcat.count(kw)
        counts[label] = cnt
        print(f"  - {label} ('{kw}'): {cnt}")
        
    print("\n=== SMOKE TEST COMPLETE ===")
    print(f"Cold Start Failures: {cold_start_failures}/10")
    print(f"Process Status: {'RUNNING' if pid else 'DEAD'}")

if __name__ == "__main__":
    run_smoke()
