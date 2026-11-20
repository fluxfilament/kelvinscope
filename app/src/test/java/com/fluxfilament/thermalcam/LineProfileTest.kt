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
import org.junit.Test

class LineProfileTest {

    private val w = 80
    private val h = 60

    @Test
    fun aHorizontalLineReadsEveryPixelOfItsRow() {
        val raw = IntArray(w * h) { (it % w) * 10 + (it / w) }
        val r = LineProfile.sample(raw, w, h, Spot(0.5f / w, 10.5f / h), Spot(79.5f / w, 10.5f / h))
        assertEquals(80, r.samples.size)
        assertEquals(0 * 10 + 10, r.samples.first())
        assertEquals(79 * 10 + 10, r.samples.last())
        assertEquals(790 + 10, r.maxRaw)
        assertEquals(1f, r.maxAt, 1e-6f)
    }

    @Test
    fun aDiagonalTakesOneSamplePerStepOfTheLongerAxis() {
        val raw = IntArray(w * h)
        for (i in 0 until 10) raw[(5 + i) * w + (5 + i)] = 100 + i
        val r = LineProfile.sample(raw, w, h, Spot(5.5f / w, 5.5f / h), Spot(14.5f / w, 14.5f / h))
        assertArrayEquals(IntArray(10) { 100 + it }, r.samples)
        assertEquals(104.5, r.meanRaw, 1e-9)
        assertEquals(100, r.minRaw)
    }

    @Test
    fun aHotSpotInTheMiddleIsFoundThere() {
        val raw = IntArray(w * h) { 1000 }
        raw[30 * w + 40] = 2000
        val r = LineProfile.sample(raw, w, h, Spot(20.5f / w, 30.5f / h), Spot(60.5f / w, 30.5f / h))
        assertEquals(2000, r.maxRaw)
        assertEquals(0.5f, r.maxAt, 1e-6f)
    }

    @Test
    fun aZeroLengthLineIsOnePixel() {
        val raw = IntArray(w * h) { it }
        val r = LineProfile.sample(raw, w, h, Spot(0.5f, 0.5f), Spot(0.5f, 0.5f))
        assertEquals(1, r.samples.size)
    }
}
