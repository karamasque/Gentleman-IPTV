# KaynanamTV Development Rules & Architectural Principles

## Uygulama Güncelleme Politikası (App Update Policy)
- **Zorunlu Güncelleme (Force / Mandatory Update):** Yeni bir sürüm tespit edildiğinde güncelleme penceresi **zorunlu (hard-block)** olmalıdır. 
  - Kullanıcı yeni sürümü indirmeden/kurmadan pencereyi kapatabilir ama bu yapılırsa uygulamada kapatılmalı, arka plana geçememeli ve eski sürümü kullanmaya devam edememelidir.

## GitHub Dağıtım ve Release Politikası (GitHub Release & Push Workflow)
- **KESİN KURAL (Açık Onay Şartı):** Kullanıcı açıkça *"paylaş / GitHub'a yolla / push et / release yap"* şeklinde doğrudan talimat vermediği sürece **ASLA** GitHub'a git push veya GitHub Release işlemi YAPILMAYACAKTIR. Kullanıcı *"paylaş"* veya *"GitHub'a yolla"* dediğinde otomatik olarak sürüm yükseltilir (`versionCode` ve `versionName`), release derlenir ve GitHub'a push/release edilir. Normal durumlarda yapılan derlemeler yalnızca yerel olarak üretilir.
- **Hedef Repository:** `karamasque/Gentleman-IPTV`
- **Branch:** `master`
- **Sürüm Yükseltme:** `app/build.gradle.kts` içinde `versionCode` ve `versionName` artırılır.
- **Derleme:** `./gradlew :app:assembleRelease` çalıştırılır.
- **Git Push Kuralı:** Yalnızca kullanıcı açıkça onay verdiğinde remote URL `https://karamasque:<TOKEN>@github.com/karamasque/Gentleman-IPTV.git` kullanılır. Büyük binary dosyaları (`.apk`, `release_apks/`) git reposuna commit edilmez (`.gitignore` içinde tutulur).
- **Release & Asset Yükleme:** Yalnızca kullanıcı açıkça onay verdiğinde GitHub REST API (`https://api.github.com/repos/karamasque/Gentleman-IPTV/releases`) kullanılarak release oluşturulur ve `app-release.apk` dosyası `KaynanamTV.apk` ismiyle doğrudan asset olarak yüklenir.