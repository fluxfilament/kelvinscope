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

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** A rectangle as plain numbers, for the placement arithmetic. */
class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {

    /** How much of this box [other] covers, as an area. */
    fun overlap(other: Box): Float {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        return if (w > 0f && h > 0f) w * h else 0f
    }

    fun grown(by: Float) = Box(left - by, top - by, right + by, bottom + by)

    /** True when the segment ([ax], [ay])-([bx], [by]) passes through this box. */
    fun crossedBy(ax: Float, ay: Float, bx: Float, by: Float): Boolean {
        // Liang-Barsky: clip the segment's parameter range against each side.
        var t0 = 0f
        var t1 = 1f
        val dx = bx - ax
        val dy = by - ay
        val p = floatArrayOf(-dx, dx, -dy, dy)
        val q = floatArrayOf(ax - left, right - ax, ay - top, bottom - ay)
        for (i in 0..3) {
            if (p[i] == 0f) {
                if (q[i] < 0f) return false
            } else {
                val t = q[i] / p[i]
                if (p[i] < 0f) t0 = max(t0, t) else t1 = min(t1, t)
                if (t0 > t1) return false
            }
        }
        return true
    }

    override fun toString() = "Box($left, $top, $right, $bottom)"
}

/**
 * Where the reading blocks of measurement areas and of the measuring line go, worked
 * out the same way for the live view and for the card.
 *
 * Each block has a handful of places it could go - above its area's frame, below it,
 * beside it; on either side of the line or past its ends - tried in that order of
 * preference, and the first that keeps clear of everything already on the picture
 * wins: spot labels, Max/Min markers, the blocks placed before it and the line. When
 * none is clear, the one that covers least. A block placed by its own rule alone used
 * to land on whatever was there: an area's block on a spot's reading, the line's
 * block, too wide for either side of an upright line, pushed back across it.
 *
 * Plain numbers in and out, no Android types, so the rules are tested on the JVM.
 * Everything is in one frame: the one the reader sees, with labels upright.
 */
object LabelPlacement {

    /**
     * The block of [w] by [h] for an area with [frame]: above it, below it, beside
     * it, and only as a last resort inside, where it hides what the area measures.
     * [frames] are all the areas' frames, this one included; covering one costs, but
     * less than covering a label. [line] is the measuring line's ends, if shown.
     */
    fun areaBlock(
        frame: Box, w: Float, h: Float, gap: Float, bounds: Box,
        taken: List<Box>, frames: List<Box>, line: FloatArray?,
    ): Box {
        val above = frame.top - gap - h
        val below = frame.bottom + gap
        val candidates = listOf(
            frame.left to above,
            frame.right - w to above,
            frame.left to below,
            frame.right - w to below,
            frame.right + gap to frame.top,
            frame.left - gap - w to frame.top,
            frame.left - gap - w to frame.bottom - h,
            frame.right + gap to frame.bottom - h,
            frame.left + gap to frame.top + gap,
            frame.left + gap to frame.bottom - gap - h,
        )
        return choose(candidates, w, h, gap, bounds, taken, frames, line)
    }

    /**
     * The block of [w] by [h] for the line from ([ax], [ay]) to ([bx], [by]): beside
     * its middle, then beside a quarter of the way from either end, upper side first
     * (right-hand for an upright line); then past either end. Each far enough from
     * the line that no corner comes within [gap] of it. Covering one of the areas'
     * [frames] costs, as for an area's own block.
     */
    fun lineBlock(
        ax: Float, ay: Float, bx: Float, by: Float,
        w: Float, h: Float, gap: Float, bounds: Box, taken: List<Box>,
        frames: List<Box> = emptyList(),
    ): Box {
        var dx = bx - ax
        var dy = by - ay
        val length = hypot(dx, dy)
        if (length < 1e-3f) {
            dx = 1f
            dy = 0f
        } else {
            dx /= length
            dy /= length
        }
        // The normal on the upper side; for an upright line, the right-hand one.
        var nx = dy
        var ny = -dx
        if (ny > 0f || (ny == 0f && nx < 0f)) {
            nx = -nx
            ny = -ny
        }
        val hw = w / 2f
        val hh = h / 2f
        val across = hw * abs(nx) + hh * abs(ny) + gap
        val along = hw * abs(dx) + hh * abs(dy) + gap
        val candidates = ArrayList<Pair<Float, Float>>()
        for (t in floatArrayOf(0.5f, 0.25f, 0.75f)) {
            val px = ax + (bx - ax) * t
            val py = ay + (by - ay) * t
            candidates += (px + nx * across - hw) to (py + ny * across - hh)
            candidates += (px - nx * across - hw) to (py - ny * across - hh)
        }
        candidates += (ax - dx * along - hw) to (ay - dy * along - hh)
        candidates += (bx + dx * along - hw) to (by + dy * along - hh)
        return choose(candidates, w, h, gap, bounds, taken, frames, floatArrayOf(ax, ay, bx, by))
    }

    /**
     * The candidate (top-left corners, in order of preference) that, once pulled
     * inside [bounds], covers least: of [taken] fully, of [soft] at a quarter, and a
     * crossing of [line] as much as covering a whole block. Earlier wins a tie.
     */
    private fun choose(
        candidates: List<Pair<Float, Float>>, w: Float, h: Float, gap: Float, bounds: Box,
        taken: List<Box>, soft: List<Box>, line: FloatArray?,
    ): Box {
        var best: Box? = null
        var bestCost = Float.MAX_VALUE
        for ((index, corner) in candidates.withIndex()) {
            val x = corner.first.coerceIn(bounds.left, max(bounds.left, bounds.right - w))
            val y = corner.second.coerceIn(bounds.top, max(bounds.top, bounds.bottom - h))
            val box = Box(x, y, x + w, y + h)
            val clear = box.grown(gap)
            var cost = index * 1e-3f
            for (other in taken) cost += clear.overlap(other)
            for (other in soft) cost += box.overlap(other) / 4f
            // Half the gap: the line candidates sit exactly a gap away, which must count.
            val nearLine = box.grown(gap / 2f)
            if (line != null && nearLine.crossedBy(line[0], line[1], line[2], line[3])) cost += w * h
            if (cost < bestCost) {
                bestCost = cost
                best = box
            }
        }
        return best!!
    }
}
