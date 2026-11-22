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
import kotlin.math.abs

/** Reading blocks of areas and the line, in a 600 x 800 picture. */
class LabelPlacementTest {

    private fun area(top: Float, bottom: Float, left: Float = 100f) =
        LabelPlacement.areaBlock(left, top, bottom, 120f, 60f, 4f, 0f, 0f, 600f, 800f)

    @Test
    fun areaBlockGoesAboveTheFrame() {
        val (x, y) = area(top = 300f, bottom = 400f).let { it[0] to it[1] }
        assertEquals(100f, x, 0.01f)
        assertEquals(300f - 4f - 60f, y, 0.01f)
    }

    @Test
    fun areaAtTheTopEdgeHasItsBlockBelow() =
        assertEquals(204f, area(top = 20f, bottom = 200f)[1], 0.01f)

    @Test
    fun areaFillingThePictureKeepsItsBlockInside() {
        val y = area(top = 10f, bottom = 790f)[1]
        assertTrue(y >= 10f && y + 60f <= 800f)
    }

    @Test
    fun areaBlockIsPulledInFromTheRightEdge() =
        assertEquals(600f - 120f, area(top = 300f, bottom = 400f, left = 550f)[0], 0.01f)

    /** True when the block centred at [c] keeps [gap] clear of the infinite line a-b. */
    private fun clearOfLine(c: FloatArray, ax: Float, ay: Float, bx: Float, by: Float, w: Float, h: Float, gap: Float): Boolean {
        val dx = bx - ax
        val dy = by - ay
        val len = kotlin.math.hypot(dx, dy)
        val corners = listOf(-1f to -1f, 1f to -1f, -1f to 1f, 1f to 1f).map { (sx, sy) ->
            ((c[0] + sx * w / 2 - ax) * dy - (c[1] + sy * h / 2 - ay) * dx) / len
        }
        return corners.all { it >= gap - 0.01f } || corners.all { it <= -gap + 0.01f }
    }

    private fun line(ax: Float, ay: Float, bx: Float, by: Float) =
        LabelPlacement.lineBlock(ax, ay, bx, by, 200f, 30f, 4f, 0f, 0f, 600f, 800f)

    @Test
    fun horizontalLineHasItsBlockAbove() {
        val c = line(100f, 400f, 500f, 400f)
        assertTrue(c[1] < 400f)
        assertTrue(clearOfLine(c, 100f, 400f, 500f, 400f, 200f, 30f, 4f))
    }

    @Test
    fun uprightLineHasItsBlockBeside() {
        val c = line(300f, 100f, 300f, 700f)
        assertTrue(abs(c[0] - 300f) >= 100f + 4f - 0.01f)
    }

    @Test
    fun slantedLineIsNotStruckThroughItsBlock() {
        // As on the card of 2026-11-22: a long line at about 60 degrees.
        val c = line(60f, 620f, 380f, 30f)
        assertTrue(clearOfLine(c, 60f, 620f, 380f, 30f, 200f, 30f, 4f))
    }

    @Test
    fun lineAlongTheTopEdgeHasItsBlockBelow() {
        val c = line(100f, 10f, 500f, 10f)
        assertTrue(c[1] > 10f)
        assertTrue(clearOfLine(c, 100f, 10f, 500f, 10f, 200f, 30f, 4f))
    }
}
