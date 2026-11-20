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

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawCountsTest {

    private val planck = Planck.DEVELOPMENT_UNIT.copy(emissivity = 0.61, correction = Correction(1.03, -0.4))

    @Test
    fun countsAndConversionSurviveTheRoundTrip() {
        val raw = IntArray(80 * 60) { 7000 + it % 977 }
        val bytes = RawCounts.encode(80, 60, raw, planck, mapOf("taken_at" to "2026-11-20T12:00:00Z"))
        val back = RawCounts.decode(bytes)
        assertEquals(80, back.width)
        assertEquals(60, back.height)
        assertArrayEquals(raw, back.raw)
        assertEquals("2026-11-20T12:00:00Z", back.meta["taken_at"])
        val p = RawCounts.planckOf(back.meta)!!
        assertEquals(planck, p)
        // And so the same degrees come out of it.
        assertEquals(planck.rawToCelsius(raw[123]), p.rawToCelsius(back.raw[123]), 0.0)
    }

    @Test
    fun theHeaderIsPlainPgm() {
        val bytes = RawCounts.encode(2, 1, intArrayOf(0x1234, 0xFFFF), planck)
        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.startsWith("P5\n# kelvinscope_counts=1\n"))
        assertTrue(text.contains("\n2 1\n65535\n"))
        // Big-endian samples at the very end.
        assertArrayEquals(
            byteArrayOf(0x12, 0x34, 0xFF.toByte(), 0xFF.toByte()),
            bytes.copyOfRange(bytes.size - 4, bytes.size),
        )
    }

    @Test
    fun aQuarterTurnMatchesTheBitmapRotation() {
        // 3 x 2:   1 2 3      turned 90° clockwise:  4 1
        //          4 5 6                             5 2
        //                                            6 3
        val o = RawCounts.orient(intArrayOf(1, 2, 3, 4, 5, 6), 3, 2, 90, false)
        assertEquals(2, o.width)
        assertEquals(3, o.height)
        assertArrayEquals(intArrayOf(4, 1, 5, 2, 6, 3), o.raw)
    }

    @Test
    fun halfTurnAndMirror() {
        val half = RawCounts.orient(intArrayOf(1, 2, 3, 4, 5, 6), 3, 2, 180, false)
        assertArrayEquals(intArrayOf(6, 5, 4, 3, 2, 1), half.raw)
        val mirrored = RawCounts.orient(intArrayOf(1, 2, 3, 4, 5, 6), 3, 2, 0, true)
        assertArrayEquals(intArrayOf(3, 2, 1, 6, 5, 4), mirrored.raw)
        // 270° (a quarter anticlockwise: 3 6 / 2 5 / 1 4), then flipped left to right.
        val t = RawCounts.orient(intArrayOf(1, 2, 3, 4, 5, 6), 3, 2, 270, true)
        assertArrayEquals(intArrayOf(6, 3, 5, 2, 4, 1), t.raw)
    }

    @Test
    fun csvHasOneLinePerRowInDegrees() {
        val raw = IntArray(4 * 3) { 7000 + it }
        val bytes = RawCounts.encode(4, 3, raw, planck, mapOf("rotation" to "90", "mirrored" to "false"))
        val lines = RawCounts.toCsv(RawCounts.decode(bytes)).trimEnd().lines()
        assertEquals(4, lines.size) // turned: 3 wide, 4 tall
        assertEquals(3, lines[0].split(",").size)
        assertEquals(String.format(java.util.Locale.US, "%.2f", planck.rawToCelsius(raw[8])), lines[0].split(",")[0])
    }
}
