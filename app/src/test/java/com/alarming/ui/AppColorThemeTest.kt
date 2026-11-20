package com.alarming.ui

import com.alarming.feature.settings.AppColorTheme
import org.junit.Assert.assertEquals
import org.junit.Test

class AppColorThemeTest {
    @Test
    fun eachPaletteHasDistinctLightAndDarkPrimaryColors() {
        val lightPrimaries = AppColorTheme.entries.map { appColorScheme(it, darkTheme = false).primary }
        val darkPrimaries = AppColorTheme.entries.map { appColorScheme(it, darkTheme = true).primary }

        assertEquals(AppColorTheme.entries.size, lightPrimaries.distinct().size)
        assertEquals(AppColorTheme.entries.size, darkPrimaries.distinct().size)
    }

    @Test
    fun persistedPaletteKeysResolveAndUnknownKeysFallBackToTeal() {
        AppColorTheme.entries.forEach { theme ->
            assertEquals(theme, AppColorTheme.fromStorageKey(theme.storageKey))
        }
        assertEquals(AppColorTheme.TEAL, AppColorTheme.fromStorageKey(null))
        assertEquals(AppColorTheme.TEAL, AppColorTheme.fromStorageKey("unknown"))
    }
}
