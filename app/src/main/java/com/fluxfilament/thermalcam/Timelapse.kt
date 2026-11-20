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
 * A thermal time-lapse: the sensor's counts every [intervalMillis], kept in memory
 * while recording and turned into a GIF and a set of PGMs when it stops.
 *
 * The GIF is drawn on one scale for the whole series - the coldest and hottest
 * places seen in any frame - so a colour means the same temperature in every frame
 * and warming up shows as the picture changing colour, which auto-gain per frame
 * would hide. The PGMs keep every frame measurable.
 *
 * Capped at [maxFrames]: at 80x60 that is under 40 MB of counts, at 160x120 a
 * little over 150; past it, recording simply stops adding frames.
 */
class Timelapse(val intervalMillis: Long, private val maxFrames: Int = 2000) {

    class Frame(val takenAt: Long, val width: Int, val height: Int, val raw: IntArray)

    private val frames = ArrayList<Frame>()
    private var lastAt = Long.MIN_VALUE

    val count: Int @Synchronized get() = frames.size

    val full: Boolean @Synchronized get() = frames.size >= maxFrames

    /** Keeps a copy of [frame] if one is due; true when it did. */
    @Synchronized
    fun offer(nowMillis: Long, frame: ThermalFrame): Boolean {
        if (frames.size >= maxFrames) return false
        if (lastAt != Long.MIN_VALUE && nowMillis - lastAt < intervalMillis) return false
        // A different camera mid-run would make frames that cannot share one picture.
        frames.firstOrNull()?.let { if (it.width != frame.width || it.height != frame.height) return false }
        lastAt = nowMillis
        frames += Frame(nowMillis, frame.width, frame.height, frame.raw.copyOf())
        return true
    }

    @Synchronized
    fun frames(): List<Frame> = ArrayList(frames)

    companion object {
        /** Playback speed of the GIF: ten frames a second. */
        const val GIF_DELAY_CS = 10

        /** About this long on the longer side, in nearest-neighbour pixels, whatever the sensor. */
        private const val GIF_TARGET_WIDTH = 320

        /**
         * The series as an animated GIF in [palette], turned as on screen, blown up
         * by whole pixels so every sensor pixel stays a sharp block.
         */
        fun gif(frames: List<Frame>, palette: Palette, rotation: Int, mirrored: Boolean): ByteArray {
            require(frames.isNotEmpty())
            // One window for all: the 3x3-mean extremes, so a lone noisy pixel does not
            // stretch the scale for the whole film.
            var lo = Int.MAX_VALUE
            var hi = Int.MIN_VALUE
            for (f in frames) {
                val e = Extremes.of(f.raw, f.width, f.height)
                lo = minOf(lo, e.coldRaw)
                hi = maxOf(hi, e.hotRaw)
            }
            val span = (hi - lo).coerceAtLeast(1).toFloat()
            val first = RawCounts.orient(frames[0].raw, frames[0].width, frames[0].height, rotation, mirrored)
            // By the longer side, so a turned frame is blown up as much as an upright one.
            val scale = (GIF_TARGET_WIDTH / maxOf(first.width, first.height)).coerceAtLeast(1)
            val outW = first.width * scale
            val outH = first.height * scale
            val enc = GifEncoder(outW, outH, palette.lut, GIF_DELAY_CS)
            val indices = ByteArray(outW * outH)
            for (f in frames) {
                val o = RawCounts.orient(f.raw, f.width, f.height, rotation, mirrored)
                for (y in 0 until outH) {
                    val row = (y / scale) * o.width
                    for (x in 0 until outW) {
                        val v = o.raw[row + x / scale]
                        indices[y * outW + x] = (((v - lo) / span) * 255f).toInt().coerceIn(0, 255).toByte()
                    }
                }
                enc.addFrame(indices)
            }
            return enc.finish()
        }
    }
}
