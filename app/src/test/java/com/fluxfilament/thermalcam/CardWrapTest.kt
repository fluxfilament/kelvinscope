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

/** The card's data line, measured in characters instead of pixels. */
class CardWrapTest {

    private fun wrap(text: String, width: Int) = SnapshotCard.wrap(text) { it.length <= width }

    @Test
    fun aLineThatFitsStaysWhole() =
        assertEquals(listOf("a · b"), wrap("a · b", 10))

    @Test
    fun breaksAtSeparatorsFirst() =
        assertEquals(listOf("22.11.2026 · 80×60", "ε 0,95"), wrap("22.11.2026 · 80×60 · ε 0,95", 20))

    @Test
    fun aTokenWiderThanTheLineBreaksAtSpaces() {
        val note = "ПУРПУРНЫМ: TI < 70 % ≤ 16,2 °C (внутри 21 °C, снаружи 5 °C)"
        val lines = wrap(note, 40)
        assertTrue(lines.size > 1)
        assertTrue(lines.all { it.length <= 40 })
        assertEquals(note, lines.joinToString(" "))
    }

    @Test
    fun aNumberKeepsItsUnit() {
        val lines = wrap("снаружи 5 °C)", 9)
        assertTrue(lines.none { it.startsWith("°") || it.startsWith("%") })
    }
}
