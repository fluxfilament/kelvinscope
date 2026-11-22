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

/**
 * Where the reading blocks of measurement areas and of the measuring line go, worked
 * out the same way for the live view and for the card.
 *
 * Both used to sit where they ran into other things: an area's readings inside its
 * own top-left corner, where a spot placed in the area - the usual case, that is what
 * the area is for - wrote its label straight over them; the line's readings centred
 * on the line, which then struck through them. Now an area's block sits outside the
 * frame and the line's beside the line. Plain numbers in and out, no Android types,
 * so the rules are tested on the JVM.
 *
 * Bounds are the drawable rectangle (left, top, right, bottom) in the same frame as
 * the geometry; blocks are kept inside it as far as they fit.
 */
object LabelPlacement {

    /**
     * Top-left corner of an area's block of [blockW] by [blockH]: above the frame's
     * top edge, [gap] clear of it; below the bottom edge when there is no room above;
     * inside at the top when the frame leaves no room either side. Left edge with the
     * frame's, pulled in to stay within the bounds.
     */
    fun areaBlock(
        frameLeft: Float, frameTop: Float, frameBottom: Float,
        blockW: Float, blockH: Float, gap: Float,
        boundsLeft: Float, boundsTop: Float, boundsRight: Float, boundsBottom: Float,
    ): FloatArray {
        val x = frameLeft.coerceIn(boundsLeft, maxOf(boundsLeft, boundsRight - blockW))
        val above = frameTop - gap - blockH
        val below = frameBottom + gap
        val y = when {
            above >= boundsTop -> above
            below + blockH <= boundsBottom -> below
            else -> (frameTop + gap).coerceIn(boundsTop, maxOf(boundsTop, boundsBottom - blockH))
        }
        return floatArrayOf(x, y)
    }

    /**
     * Centre of the line's block of [blockW] by [blockH]: off the line's middle along
     * its normal, far enough that no corner of the block reaches within [gap] of the
     * line. The side above the line first (to the right for a vertical one), the other
     * side when that one leaves the bounds; pulled back inside the bounds if neither
     * fits whole.
     */
    fun lineBlock(
        ax: Float, ay: Float, bx: Float, by: Float,
        blockW: Float, blockH: Float, gap: Float,
        boundsLeft: Float, boundsTop: Float, boundsRight: Float, boundsBottom: Float,
    ): FloatArray {
        val mx = (ax + bx) / 2f
        val my = (ay + by) / 2f
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
        val hw = blockW / 2f
        val hh = blockH / 2f
        val reach = hw * abs(nx) + hh * abs(ny) + gap
        fun fits(cx: Float, cy: Float) =
            cx - hw >= boundsLeft && cx + hw <= boundsRight && cy - hh >= boundsTop && cy + hh <= boundsBottom
        var cx = mx + nx * reach
        var cy = my + ny * reach
        if (!fits(cx, cy) && fits(mx - nx * reach, my - ny * reach)) {
            cx = mx - nx * reach
            cy = my - ny * reach
        }
        cx = cx.coerceIn(boundsLeft + hw, maxOf(boundsLeft + hw, boundsRight - hw))
        cy = cy.coerceIn(boundsTop + hh, maxOf(boundsTop + hh, boundsBottom - hh))
        return floatArrayOf(cx, cy)
    }
}
