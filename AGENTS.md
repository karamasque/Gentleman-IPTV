# KaynanamTV Development Rules & Architectural Principles

## Uygulama Güncelleme Politikası (App Update Policy)
- **Zorunlu Güncelleme (Force / Mandatory Update):** Yeni bir sürüm tespit edildiğinde güncelleme penceresi **zorunlu (hard-block)** olmalıdır. 
  - Kullanıcı yeni sürümü indirmeden/kurmadan pencereyi kapatabilir ama bu yapılırsa uygulamada kapatılmalı, arka plana geçememeli ve eski sürümü kullanmaya devam edememelidir.