package com.asl.wallet.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asl.wallet.core.designsystem.theme.ThemeMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appPrefs by preferencesDataStore("app_prefs")

/** Non-sensitive UI preferences only (theme). Tokens live in the encrypted session store. */
@Singleton
class AppPreferences @Inject constructor(@ApplicationContext private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")

    val themeMode: Flow<ThemeMode> = context.appPrefs.data.map { p ->
        p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.Light
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.appPrefs.edit { it[themeKey] = mode.name }
    }
}
