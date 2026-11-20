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
}
