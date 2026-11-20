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

class MeasureAreasTest {

    private val w = 80
    private val h = 60

    @Test
    fun statsCoverExactlyThePixelsInside() {
        val raw = IntArray(w * h) { 1000 }
        // Columns 10..19, rows 20..29 hold 2000, one pixel inside 3000, one 500.
        for (y in 20..29) for (x in 10..19) raw[y * w + x] = 2000
        raw[25 * w + 15] = 3000
        raw[21 * w + 11] = 500
        val s = MeasureAreas.stats(raw, w, h, Area(10f / w, 20f / h, 20f / w, 30f / h))
        assertEquals(100, s.pixels)
        assertEquals(3000, s.maxRaw)
        assertEquals(500, s.minRaw)
        assertEquals((98 * 2000 + 3000 + 500) / 100.0, s.meanRaw, 1e-9)
    }

    @Test
    fun aSliverStillReadsOnePixel() {
        val raw = IntArray(w * h) { it }
        val s = MeasureAreas.stats(raw, w, h, Area(0.5f, 0.5f, 0.5001f, 0.5001f))
        assertEquals(1, s.pixels)
        assertEquals(30 * w + 40, s.maxRaw)
    }

    @Test
    fun movingStopsAtTheEdge() {
        val areas = MeasureAreas().apply { setCount(1) }
        areas.move(0, 5f, -5f)
        val a = areas.positions()[0]
        assertEquals(1f, a.right, 1e-6f)
        assertEquals(0f, a.top, 1e-6f)
        assertEquals(0.3f, a.width, 1e-6f)
    }

    @Test
    fun resizingKeepsAMinimumSize() {
        val areas = MeasureAreas().apply { setCount(1) }
        areas.resize(0, 0f, 0f)
        val a = areas.positions()[0]
        assertEquals(MeasureAreas.MIN_SIZE, a.width, 1e-6f)
        assertEquals(MeasureAreas.MIN_SIZE, a.height, 1e-6f)
    }

    @Test
    fun countIsCappedAndKeepsExistingAreas() {
        val areas = MeasureAreas()
        areas.setCount(1)
        areas.move(0, 0.1f, 0f)
        val moved = areas.positions()[0]
        areas.setCount(9)
        assertEquals(MeasureAreas.MAX, areas.count)
        assertEquals(moved, areas.positions()[0])
    }
}
