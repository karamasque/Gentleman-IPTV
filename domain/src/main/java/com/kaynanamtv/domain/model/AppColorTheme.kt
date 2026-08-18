package com.kaynanamtv.domain.model

enum class AppColorTheme {
    INDIGO,  // Varsayılan (lacivert/mor)
    BLACK,
    BLUE,
    RED,
    YELLOW,
    GREEN,
    PURPLE,
    PINK;

    companion object {
        val DEFAULT = INDIGO

        fun fromName(name: String?): AppColorTheme =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
