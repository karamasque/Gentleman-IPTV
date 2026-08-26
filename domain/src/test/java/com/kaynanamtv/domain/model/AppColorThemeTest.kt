package com.kaynanamtv.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AppColorThemeTest {

    @Test
    fun `fromName parses all valid AppColorTheme enum entries`() {
        for (theme in AppColorTheme.entries) {
            assertThat(AppColorTheme.fromName(theme.name)).isEqualTo(theme)
        }
    }

    @Test
    fun `fromName falls back to DEFAULT when name is null`() {
        assertThat(AppColorTheme.fromName(null)).isEqualTo(AppColorTheme.DEFAULT)
        assertThat(AppColorTheme.fromName(null)).isEqualTo(AppColorTheme.INDIGO)
    }

    @Test
    fun `fromName falls back to DEFAULT when name is invalid or unrecognized`() {
        assertThat(AppColorTheme.fromName("")).isEqualTo(AppColorTheme.DEFAULT)
        assertThat(AppColorTheme.fromName("UNKNOWN_THEME")).isEqualTo(AppColorTheme.DEFAULT)
        assertThat(AppColorTheme.fromName("DARK_NEON")).isEqualTo(AppColorTheme.DEFAULT)
    }

    @Test
    fun `all eight distinct color themes are properly supported`() {
        val expectedThemes = listOf(
            AppColorTheme.INDIGO,
            AppColorTheme.BLACK,
            AppColorTheme.BLUE,
            AppColorTheme.RED,
            AppColorTheme.YELLOW,
            AppColorTheme.GREEN,
            AppColorTheme.PURPLE,
            AppColorTheme.PINK
        )
        assertThat(AppColorTheme.entries).containsExactlyElementsIn(expectedThemes)
    }

    @Test
    fun `DEFAULT constant equals INDIGO`() {
        assertThat(AppColorTheme.DEFAULT).isEqualTo(AppColorTheme.INDIGO)
    }
}
