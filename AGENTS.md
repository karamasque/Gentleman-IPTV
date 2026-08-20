# KaynanamTV Development Rules & Architectural Principles

## Uygulama Güncelleme Politikası (App Update Policy)
- **Zorunlu Güncelleme (Force / Mandatory Update):** Yeni bir sürüm tespit edildiğinde güncelleme penceresi **zorunlu (hard-block)** olmalıdır. 
  - Kullanıcı yeni sürümü indirmeden/kurmadan pencereyi kapatabilir ama bu yapılırsa uygulamada kapatılmalı, arka plana geçememeli ve eski sürümü kullanmaya devam edememelidir.

## GitHub Dağıtım ve Release Politikası (GitHub Release & Push Workflow)
- **Hedef Repository:** `karamasque/Gentleman-IPTV`
- **Branch:** `master`
- **Sürüm Yükseltme:** `app/build.gradle.kts` içinde `versionCode` ve `versionName` artırılır.
- **Derleme:** `D:\KaynanamTV-IPTV` üzerinden `./gradlew :app:assembleRelease` çalıştırılır.
- **Git Push Kuralı:** Remote URL `https://karamasque:<TOKEN>@github.com/karamasque/Gentleman-IPTV.git` kullanılır. Büyük binary dosyaları (`.apk`, `release_apks/`) git reposuna commit edilmez (`.gitignore` içinde tutulur).
- **Release & Asset Yükleme:** GitHub REST API (`https://api.github.com/repos/karamasque/Gentleman-IPTV/releases`) kullanılarak release oluşturulur ve `app-release.apk` dosyası `KaynanamTV.apk` ismiyle doğrudan asset olarak yüklenir.