package com.kaynanamtv.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.AppColorTheme
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PreferencesRepositoryThemePersistenceTest {

    private val editor: SharedPreferences.Editor = mock()
    private val themePrefs: SharedPreferences = mock {
        on { edit() } doReturn editor
    }
    private val context: Context = mock {
        on { getSharedPreferences(eq("kaynanamtv_theme_cache"), eq(Context.MODE_PRIVATE)) } doReturn themePrefs
    }

    @Test
    fun `AppColorTheme fromName parses all themes and defaults to INDIGO`() {
        assertThat(AppColorTheme.fromName(null)).isEqualTo(AppColorTheme.INDIGO)
        assertThat(AppColorTheme.fromName("PURPLE")).isEqualTo(AppColorTheme.PURPLE)
        assertThat(AppColorTheme.fromName("GREEN")).isEqualTo(AppColorTheme.GREEN)
        assertThat(AppColorTheme.fromName("RED")).isEqualTo(AppColorTheme.RED)
        assertThat(AppColorTheme.fromName("YELLOW")).isEqualTo(AppColorTheme.YELLOW)
        assertThat(AppColorTheme.fromName("BLUE")).isEqualTo(AppColorTheme.BLUE)
        assertThat(AppColorTheme.fromName("BLACK")).isEqualTo(AppColorTheme.BLACK)
        assertThat(AppColorTheme.fromName("PINK")).isEqualTo(AppColorTheme.PINK)
    }

    @Test
    fun `getAppColorThemeSynchronously resolves PURPLE from fast startup cache`() {
        whenever(themePrefs.getString("app_color_theme", null)).thenReturn("PURPLE")
        val cached = themePrefs.getString("app_color_theme", null)
        val resolved = AppColorTheme.fromName(cached)
        assertThat(resolved).isEqualTo(AppColorTheme.PURPLE)
    }

    @Test
    fun `getAppColorThemeSynchronously resolves GREEN from fast startup cache`() {
        whenever(themePrefs.getString("app_color_theme", null)).thenReturn("GREEN")
        val cached = themePrefs.getString("app_color_theme", null)
        val resolved = AppColorTheme.fromName(cached)
        assertThat(resolved).isEqualTo(AppColorTheme.GREEN)
    }

    @Test
    fun `getAppColorThemeSynchronously resolves RED from fast startup cache`() {
        whenever(themePrefs.getString("app_color_theme", null)).thenReturn("RED")
        val cached = themePrefs.getString("app_color_theme", null)
        val resolved = AppColorTheme.fromName(cached)
        assertThat(resolved).isEqualTo(AppColorTheme.RED)
    }

    @Test
    fun `getAppColorThemeSynchronously falls back to INDIGO default when cache is null`() {
        whenever(themePrefs.getString("app_color_theme", null)).thenReturn(null)
        val cached = themePrefs.getString("app_color_theme", null)
        val resolved = AppColorTheme.fromName(cached)
        assertThat(resolved).isEqualTo(AppColorTheme.INDIGO)
    }
}
