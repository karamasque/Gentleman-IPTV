package com.kaynanamtv.domain.model

enum class Feature(val displayName: String, val description: String) {
    AUTO_IPTV(
        displayName = "Otomatik IPTV",
        description = "Otomatik IPTV tarama, aktif sunucu doğrulama ve tek dokunuşla ekleme özelliği."
    ),
    BACKGROUND_PLAYLIST_UPDATE(
        displayName = "Otomatik Arka Plan Güncelleme",
        description = "Çalma listesi ve kanal güncellemelerini arka planda otomatik senkronize eder."
    ),
    CUSTOM_GROUPS(
        displayName = "Özel Kanal Grupları",
        description = "Kendi özel kanal ve içerik kategorilerinizi oluşturmanızı sağlar."
    ),
    PIN_GROUPS(
        displayName = "Grup Sabitleme",
        description = "Sık izlediğiniz kategorileri listenin en üstüne sabitlemenizi sağlar."
    ),
    CUSTOM_EPG(
        displayName = "Özel EPG Kaynakları",
        description = "Harici XMLTV ve özel yayın akışı bağlantıları eklemenizi sağlar."
    ),
    ADVANCED_PLAYBACK(
        displayName = "Gelişmiş Oynatıcı Ayarları",
        description = "Gelişmiş buffer boyutu, format zorlama ve donanım dekoder kontrolleri."
    ),
    AUDIO_PASSTHROUGH(
        displayName = "Ses Düz Geçişi (Passthrough)",
        description = "Dolby Digital ve DTS çok kanallı ses çıkışını doğrudan ses sisteminize aktarır."
    ),
    TIMESHIFT(
        displayName = "Geri Sarma & Timeshift",
        description = "Canlı yayını durdurma, geri sarma ve kaçırılan anları izleme."
    ),
    PVR(
        displayName = "Yayın Kaydı (PVR)",
        description = "Canlı TV programlarını daha sonra izlemek üzere kaydetme."
    ),
    MULTIVIEW_FULL(
        displayName = "Gelişmiş Çoklu Ekran (MultiView)",
        description = "Aynı anda 4 veya daha fazla kanalı yan yana canlı izleme."
    ),
    CLOUD_SYNC(
        displayName = "Bulut IPTV Senkronizasyonu",
        description = "IPTV sağlayıcılarınızı bulutta saklar ve diğer cihazlarınızda otomatik yükler."
    ),
    TRAKT(
        displayName = "Trakt.tv Entegrasyonu",
        description = "İzleme geçmişinizi ve film/dizi puanlarınızı Trakt hesabınızla eşitler."
    );
}
