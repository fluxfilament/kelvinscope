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

/** A measurement area in fractions of the sensor frame, like a spot's position. */
data class Area(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** The hottest, coldest and mean counts inside an [Area]. */
data class AreaStats(val maxRaw: Int, val minRaw: Int, val meanRaw: Double, val pixels: Int)

/**
 * Up to [MAX] rectangles, each reporting Max, Min and the mean of what is inside.
 *
 * A spot answers "how hot is this point"; an area answers "how hot does it get
 * anywhere on this part", which is the question for a heatsink, a radiator or a
 * breaker panel, where the hot spot moves and a fixed point keeps missing it.
 * Like [SpotMeter] the list is swapped whole, because the camera thread reads it
 * while the UI thread drags.
 */
class MeasureAreas {

    @Volatile
    private var areas: List<Area> = emptyList()

    val count: Int get() = areas.size

    fun positions(): List<Area> = areas

    fun setCount(n: Int) {
        val wanted = n.coerceIn(0, MAX)
        val list = areas.toMutableList()
        while (list.size < wanted) list += DEFAULTS[list.size]
        while (list.size > wanted) list.removeAt(list.size - 1)
        areas = list
    }

    /** Moves area [index] by ([du], [dv]), keeping its size and keeping it on the frame. */
    fun move(index: Int, du: Float, dv: Float) {
        val a = areas.getOrNull(index) ?: return
        val left = (a.left + du).coerceIn(0f, 1f - a.width)
        val top = (a.top + dv).coerceIn(0f, 1f - a.height)
        replace(index, Area(left, top, left + a.width, top + a.height))
    }

    /**
     * Drags the corner opposite ([Area.left], [Area.top]) to ([u], [v]). The other
     * corner stays put, and the area never gets smaller than [MIN_SIZE].
     */
    fun resize(index: Int, u: Float, v: Float) {
        val a = areas.getOrNull(index) ?: return
        val right = u.coerceIn(a.left + MIN_SIZE, 1f)
        val bottom = v.coerceIn(a.top + MIN_SIZE, 1f)
        replace(index, Area(a.left, a.top, right, bottom))
    }

    private fun replace(index: Int, area: Area) {
        areas = areas.toMutableList().also { it[index] = area }
    }

    companion object {
        const val MAX = 3

        /** Small enough to be dragged with a finger, big enough to hold a few pixels. */
        const val MIN_SIZE = 0.08f

        private val DEFAULTS = listOf(
            Area(0.35f, 0.35f, 0.65f, 0.65f),
            Area(0.08f, 0.10f, 0.33f, 0.40f),
            Area(0.67f, 0.60f, 0.92f, 0.90f),
        )

        /**
         * Statistics over the pixels whose centres fall inside [area]. Never empty: an
         * area narrower than a pixel still reads the pixel under its middle.
         */
        fun stats(raw: IntArray, width: Int, height: Int, area: Area): AreaStats {
            var x0 = kotlin.math.ceil(area.left * width - 0.5f).toInt().coerceIn(0, width - 1)
            var x1 = kotlin.math.floor(area.right * width - 0.5f).toInt().coerceIn(0, width - 1)
            var y0 = kotlin.math.ceil(area.top * height - 0.5f).toInt().coerceIn(0, height - 1)
            var y1 = kotlin.math.floor(area.bottom * height - 0.5f).toInt().coerceIn(0, height - 1)
            if (x1 < x0) {
                x0 = ((area.left + area.right) / 2f * width).toInt().coerceIn(0, width - 1)
                x1 = x0
            }
            if (y1 < y0) {
                y0 = ((area.top + area.bottom) / 2f * height).toInt().coerceIn(0, height - 1)
                y1 = y0
            }
            var max = Int.MIN_VALUE
            var min = Int.MAX_VALUE
            var sum = 0L
            var n = 0
            for (y in y0..y1) {
                for (x in x0..x1) {
                    val v = raw[y * width + x]
                    if (v > max) max = v
                    if (v < min) min = v
                    sum += v
                    n++
                }
            }
            return AreaStats(max, min, sum.toDouble() / n, n)
        }

        fun stats(frame: ThermalFrame, area: Area): AreaStats = stats(frame.raw, frame.width, frame.height, area)
    }
}
