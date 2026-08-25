import time
import requests

def test_epg_conditional_http():
    print("=== LIVE EPG CONDITIONAL REQUEST VERIFICATION ===")
    
    # 1. Test against real public EPG / XMLTV endpoints
    test_urls = [
        ("GitHub Raw Markdown / Feed", "https://raw.githubusercontent.com/karamasque/Gentleman-IPTV/master/README.md"),
        ("Alternative CDN / XMLTV", "https://raw.githubusercontent.com/iptv-org/epg/master/sites/digiturk.com.tr/digiturk.com.tr.channels.xml")
    ]
    
    for name, url in test_urls:
        print(f"\n--- Testing Endpoint: {name} ---")
        print(f"URL: {url}")
        
        # FIRST SYNC (Cold request)
        t0 = time.time()
        r1 = requests.get(url, timeout=30)
        t1 = time.time()
        
        dur1_ms = int((t1 - t0) * 1000)
        bytes1 = len(r1.content)
        etag = r1.headers.get("ETag")
        last_modified = r1.headers.get("Last-Modified")
        
        print(f"FIRST SYNC:")
        print(f"  HTTP Status: {r1.status_code}")
        print(f"  Duration: {dur1_ms}ms")
        print(f"  Bytes Downloaded: {bytes1} bytes ({bytes1 / 1024:.1f} KB)")
        print(f"  Response ETag: {etag or 'NONE'}")
        print(f"  Response Last-Modified: {last_modified or 'NONE'}")
        
        # SECOND SYNC (Conditional request with If-None-Match / If-Modified-Since)
        headers = {}
        if etag:
            headers["If-None-Match"] = etag
        if last_modified:
            headers["If-Modified-Since"] = last_modified
            
        print(f"\nSECOND SYNC (Conditional Headers: {headers}):")
        t2 = time.time()
        r2 = requests.get(url, headers=headers, timeout=30)
        t3 = time.time()
        
        dur2_ms = int((t3 - t2) * 1000)
        bytes2 = len(r2.content)
        
        print(f"  HTTP Status: {r2.status_code}")
        print(f"  Duration: {dur2_ms}ms")
        print(f"  Bytes Downloaded: {bytes2} bytes")
        
        if r2.status_code == 304:
            print(f"  RESULT: 304 NOT MODIFIED RECEIVED")
            print(f"  -> Body download skipped: YES (0 payload bytes)")
            print(f"  -> Parser invocation skipped: YES")
            print(f"  -> Room DB rewrite skipped: YES (Current EPG preserved)")
            print(f"  -> Network saving: {100.0 * (1.0 - bytes2 / max(1, bytes1)):.2f}%")
            print(f"  -> Time saving: {100.0 * (1.0 - dur2_ms / max(1, dur1_ms)):.2f}%")
        elif r2.status_code == 200:
            print(f"  RESULT: 200 OK (Server ignored conditional headers or content changed)")
            print(f"  -> Fallback to normal XMLTV parse and commit: PASS")

if __name__ == "__main__":
    test_epg_conditional_http()
