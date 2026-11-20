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

/**
 * Where the hottest and the coldest place in a frame are, for the Max/Min markers
 * that follow them around the picture.
 *
 * Found on 3x3 means, the same window a spot meter reads, rather than on single
 * pixels: one pixel of a Lepton is noisy enough that the bare maximum would hop
 * between neighbours from frame to frame, and the figure beside the marker would not
 * match what a spot put on the same place reads.
 */
data class Extremes(val hot: Spot, val hotRaw: Int, val cold: Spot, val coldRaw: Int) {

    companion object {
        fun of(frame: ThermalFrame): Extremes = of(frame.raw, frame.width, frame.height)

        fun of(raw: IntArray, width: Int, height: Int): Extremes {
            var hotAt = 0
            var coldAt = 0
            var hot = Int.MIN_VALUE
            var cold = Int.MAX_VALUE
            for (y in 0 until height) {
                for (x in 0 until width) {
                    var sum = 0
                    var n = 0
                    for (yy in maxOf(0, y - 1)..minOf(height - 1, y + 1)) {
                        for (xx in maxOf(0, x - 1)..minOf(width - 1, x + 1)) {
                            sum += raw[yy * width + xx]
                            n++
                        }
                    }
                    val mean = sum / n
                    val i = y * width + x
                    if (mean > hot) {
                        hot = mean
                        hotAt = i
                    }
                    if (mean < cold) {
                        cold = mean
                        coldAt = i
                    }
                }
            }
            return Extremes(centre(hotAt, width, height), hot, centre(coldAt, width, height), cold)
        }

        /** The middle of pixel [i], in the 0..1 coordinates spots use. */
        private fun centre(i: Int, width: Int, height: Int) =
            Spot((i % width + 0.5f) / width, (i / width + 0.5f) / height)
    }
}
