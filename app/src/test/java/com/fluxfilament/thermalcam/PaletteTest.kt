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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The colour tables: complete, opaque, and running the way their names promise. */
class PaletteTest {

    private fun r(c: Int) = (c shr 16) and 0xFF
    private fun g(c: Int) = (c shr 8) and 0xFF
    private fun b(c: Int) = c and 0xFF
    private fun luma(c: Int) = 0.299 * r(c) + 0.587 * g(c) + 0.114 * b(c)

    @Test
    fun everyPaletteHas256OpaqueEntries() {
        for (p in Palette.entries) {
            assertEquals(p.name, 256, p.lut.size)
            assertTrue(p.name, p.lut.all { it ushr 24 == 0xFF })
        }
    }

    @Test
    fun theDarkToLightPalettesNeverGetDarker() {
        // On these a brighter colour must always mean hotter, or the picture lies.
        for (p in listOf(Palette.GRAYSCALE, Palette.IRON, Palette.LAVA)) {
            val l = p.lut.map(::luma)
            for (i in 1 until l.size) assertTrue("${p.name} at $i", l[i] >= l[i - 1] - 0.5)
        }
    }

    @Test
    fun rainbowRunsFromVioletToRed() {
        val cold = Palette.RAINBOW.lut.first()
        val hot = Palette.RAINBOW.lut.last()
        assertTrue(b(cold) > g(cold) && r(cold) > g(cold))
        assertTrue(r(hot) > 200 && g(hot) < 40 && b(hot) < 40)
    }

    @Test
    fun arcticSpendsTheColdHalfInBlues() {
        for (i in 0..120) {
            val c = Palette.ARCTIC.lut[i]
            assertTrue("at $i", b(c) >= r(c))
        }
        val hot = Palette.ARCTIC.lut.last()
        assertTrue(r(hot) > 200 && b(hot) < 40)
    }

    @Test
    fun lavaStaysInTheWarmHues() {
        for (c in Palette.LAVA.lut) assertTrue(r(c) >= g(c) && g(c) >= b(c))
    }
}
