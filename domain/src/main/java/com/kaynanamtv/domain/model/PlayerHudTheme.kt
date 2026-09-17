package com.kaynanamtv.domain.model

/**
 * Visual themes for the video player HUD, seekbar, and overlays.
 */
enum class PlayerHudTheme(
    val storageKey: String,
    val primaryColorHex: Long,
    val accentColorHex: Long,
    val glowColorHex: Long,
    val backgroundColorHex: Long,
    val isGlassmorphism: Boolean = false,
    val isMinimalist: Boolean = false
) {
    /**
     * Modern Glassmorphism: Frosted blur translucent glass bar with glowing cyan/violet accents.
     */
    MODERN_GLASS(
        storageKey = "MODERN_GLASS",
        primaryColorHex = 0xFF00F2FE,
        accentColorHex = 0xFF9B51E0,
        glowColorHex = 0x6600F2FE,
        backgroundColorHex = 0xCC111827,
        isGlassmorphism = true
    ),

    /**
     * Neon Cyberpunk: Pure OLED black base with electric cyan and hot magenta neon glow.
     */
    NEON_CYBER(
        storageKey = "NEON_CYBER",
        primaryColorHex = 0xFF00FFFF,
        accentColorHex = 0xFFFF007F,
        glowColorHex = 0x8000FFFF,
        backgroundColorHex = 0xE605050A
    ),

    /**
     * Minimalist Clean: Ultra-slim hairline progress line and unobtrusive white/amber highlights.
     */
    MINIMALIST_CLEAN(
        storageKey = "MINIMALIST_CLEAN",
        primaryColorHex = 0xFFFFFFFF,
        accentColorHex = 0xFFFFB300,
        glowColorHex = 0x33FFFFFF,
        backgroundColorHex = 0x99000000,
        isMinimalist = true
    ),

    /**
     * Cinema Gold: Luxury charcoal dark base with rich amber and metallic gold accents.
     */
    CINEMA_GOLD(
        storageKey = "CINEMA_GOLD",
        primaryColorHex = 0xFFFFD700,
        accentColorHex = 0xFFD4AF37,
        glowColorHex = 0x66FFD700,
        backgroundColorHex = 0xEA161616
    ),

    /**
     * Emerald Matrix: Deep futuristic dark green with vivid glowing matrix green highlights.
     */
    EMERALD_MATRIX(
        storageKey = "EMERALD_MATRIX",
        primaryColorHex = 0xFF00FF66,
        accentColorHex = 0xFF00E676,
        glowColorHex = 0x6600FF66,
        backgroundColorHex = 0xE605150A
    ),

    /**
     * Crimson Velvet: Cinematic deep black with bold scarlet red highlights and badges.
     */
    CRIMSON_RED(
        storageKey = "CRIMSON_RED",
        primaryColorHex = 0xFFE50914,
        accentColorHex = 0xFFFF2A42,
        glowColorHex = 0x66E50914,
        backgroundColorHex = 0xE60A0A0A
    ),

    /**
     * Aurora Nordic: Nordic night sky with shimmering aurora green and glacier cyan gradients.
     */
    AURORA_NORDIC(
        storageKey = "AURORA_NORDIC",
        primaryColorHex = 0xFF00FFA3,
        accentColorHex = 0xFF00B8D4,
        glowColorHex = 0x6600FFA3,
        backgroundColorHex = 0xEB0A141E,
        isGlassmorphism = true
    ),

    /**
     * Sunset Glow: Twilight dark violet base with warm tangerine and fiery coral glow.
     */
    SUNSET_ORANGE(
        storageKey = "SUNSET_ORANGE",
        primaryColorHex = 0xFFFF5E36,
        accentColorHex = 0xFFFF2A68,
        glowColorHex = 0x66FF5E36,
        backgroundColorHex = 0xE8140A1E
    ),

    /**
     * Retro Synthwave: 80s arcade neon purple, electric magenta and golden sunrise gradients.
     */
    RETRO_SYNTHWAVE(
        storageKey = "RETRO_SYNTHWAVE",
        primaryColorHex = 0xFFFF00CC,
        accentColorHex = 0xFF333399,
        glowColorHex = 0x66FF00CC,
        backgroundColorHex = 0xE6100520
    ),

    /**
     * Titanium Stealth: Industrial brushed steel gray and crisp ice-white high-contrast finish.
     */
    TITANIUM_STEEL(
        storageKey = "TITANIUM_STEEL",
        primaryColorHex = 0xFFE2E8F0,
        accentColorHex = 0xFF38BDF8,
        glowColorHex = 0x4D94A3B8,
        backgroundColorHex = 0xEB0F172A
    );

    companion object {
        val DEFAULT = MODERN_GLASS

        fun fromStorageKey(key: String?): PlayerHudTheme {
            return entries.firstOrNull { it.storageKey.equals(key, ignoreCase = true) } ?: DEFAULT
        }
    }
}
