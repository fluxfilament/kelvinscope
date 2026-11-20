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
import org.junit.Test

class ExtremesTest {

    private val w = 80
    private val h = 60

    private fun flat(level: Int = 8000) = IntArray(w * h) { level }

    private fun blob(raw: IntArray, cx: Int, cy: Int, delta: Int) {
        for (y in cy - 1..cy + 1) for (x in cx - 1..cx + 1) raw[y * w + x] += delta
    }

    @Test
    fun findsAWarmAndACoolPatch() {
        val raw = flat()
        blob(raw, 60, 10, 500)
        blob(raw, 15, 45, -400)
        val e = Extremes.of(raw, w, h)
        assertEquals(60.5f / w, e.hot.u, 1e-6f)
        assertEquals(10.5f / h, e.hot.v, 1e-6f)
        assertEquals(8500, e.hotRaw)
        assertEquals(15.5f / w, e.cold.u, 1e-6f)
        assertEquals(45.5f / h, e.cold.v, 1e-6f)
        assertEquals(7600, e.coldRaw)
    }

    @Test
    fun aSingleNoisyPixelLosesToARealHotSpot() {
        // One pixel 900 counts up is 100 on a 3x3 mean; a 3x3 patch 300 up stays 300.
        val raw = flat()
        raw[5 * w + 5] += 900
        blob(raw, 40, 30, 300)
        val e = Extremes.of(raw, w, h)
        assertEquals(40.5f / w, e.hot.u, 1e-6f)
        assertEquals(30.5f / h, e.hot.v, 1e-6f)
    }

    @Test
    fun aPatchAtTheEdgeIsFoundToo() {
        val raw = flat()
        for (y in 0..1) for (x in 0..1) raw[y * w + x] += 600
        val e = Extremes.of(raw, w, h)
        assertEquals(0.5f / w, e.hot.u, 1e-6f)
        assertEquals(0.5f / h, e.hot.v, 1e-6f)
    }
}
