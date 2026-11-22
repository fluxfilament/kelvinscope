/*
 * Copyright (C) 2026 Damirusnik
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.fluxfilament.thermalcam

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * The interface language picked in the app's settings, or null for the phone's own.
 *
 * Android 13 and later keep a language per app themselves (system settings, apps,
 * Kelvinscope, language - offered from locales_config.xml). The picker writes that
 * same setting rather than one of its own, so the two can never disagree, and the
 * system recreates the activity when it changes. Before 13 there is no such setting:
 * the choice is kept in the app's preferences and laid over the configuration in
 * attachBaseContext, the way the theme is.
 *
 * Lint's AppBundleLocaleChanges is about Play's split APKs, which leave out the
 * languages a phone was not set to; this app ships as one APK with all six.
 */
@SuppressLint("AppBundleLocaleChanges")
object AppLanguage {

    /** The languages there are strings for, in the order the picker lists them. */
    val TAGS = listOf("en", "de", "es", "fr", "pt", "ru")

    private const val PREF = "language"

    /** The picked language's tag, or null while the interface follows the phone. */
    fun current(context: Context, prefs: SharedPreferences): String? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val locales = context.getSystemService(LocaleManager::class.java).applicationLocales
            if (locales.isEmpty) null else locales[0].language
        } else {
            prefs.getString(PREF, null)
        }

    /**
     * Switches the interface to [tag], or back to the phone's language for null.
     * True when the caller has to recreate the activity itself (before Android 13).
     */
    @SuppressLint("ApplySharedPref") // see the comment inside
    fun pick(context: Context, prefs: SharedPreferences, tag: String?): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java).applicationLocales =
                if (tag == null) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
            return false
        }
        // commit, not apply: recreate() reads it back in attachBaseContext at once.
        prefs.edit().putString(PREF, tag).commit()
        return true
    }

    /**
     * Lays the language kept before Android 13 over [config]; true when it changed
     * anything. On 13+ the system has already put it there.
     */
    fun applyTo(config: Configuration, prefs: SharedPreferences): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return false
        val tag = prefs.getString(PREF, null) ?: return false
        config.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return true
    }

    /** [tag]'s name in itself: "Deutsch", "Русский" - findable whatever the interface is in. */
    fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
