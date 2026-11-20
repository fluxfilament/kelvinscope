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

import android.content.Context
import android.graphics.Typeface

/**
 * The app's typefaces for code that draws text itself rather than through a styled
 * TextView: Golos Text for prose, JetBrains Mono for figures. Both ship in res/font
 * under the SIL Open Font License; the licences are in assets/licenses.
 *
 * Loaded once and kept: a Typeface is immutable and safe to share, and these are
 * asked for on every card and every scale bar.
 */
object Fonts {

    @Volatile
    private var monoCache: Typeface? = null

    /** Figures and labels. Falls back to the system monospace if the font will not load. */
    fun mono(context: Context): Typeface = monoCache ?: load(context).also { monoCache = it }

    private fun load(context: Context): Typeface = try {
        context.resources.getFont(R.font.jetbrains_mono)
    } catch (e: Exception) {
        Typeface.MONOSPACE
    }
}
