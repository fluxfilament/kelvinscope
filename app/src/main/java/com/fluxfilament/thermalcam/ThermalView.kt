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

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.hypot

/** One spot as the view needs it: where it sits, its number, and what it reads. */
class SpotLabel(val u: Float, val v: Float, val number: Int, val text: String)

/**
 * The measuring line: its ends, the readings to print beside it, and the profile
 * along it in degrees for the graph.
 */
class LineLabel(
    val start: Spot,
    val end: Spot,
    val text: String,
    val profileC: FloatArray,
    val maxText: String,
    val minText: String,
)

/** A measurement area with its readings, one line per figure. */
class AreaLabel(val area: Area, val number: Int, val text: String)

/** A Max or Min marker on the picture; not draggable, it follows the scene. */
class ExtremeLabel(val u: Float, val v: Float, val hot: Boolean, val text: String)

/**
 * Draws the live picture and the spot meters on it, and lets the spots be dragged.
 *
 * A view of its own rather than an ImageView with something layered over it, because
 * the picture and the spots have to agree on exactly one transform. Sharing a matrix
 * between two views is a standing invitation for them to drift apart by a frame or a
 * layout pass, and a spot meter that does not sit where it is drawn is worse than no
 * spot meter.
 */
class ThermalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private var bitmap: Bitmap? = null
    private var rotation = ViewTransform.DEFAULT_ROTATION
    private var mirrored = true

    var spots: List<SpotLabel> = emptyList()

    /** Measurement areas, drawn under everything else on the picture. */
    var areas: List<AreaLabel> = emptyList()

    /**
     * The temperature log's recent run, series as [TrendBuffer.snapshot] gives them,
     * or null when no log is recording. Drawn as a strip across the top.
     */
    var trend: Array<FloatArray?>? = null

    /** The measuring line, or null when it is off. */
    var line: LineLabel? = null

    /** A line end (0 start, 1 end) dragged to a point in sensor fractions. */
    var onLineMoved: ((Int, Float, Float) -> Unit)? = null

    /** An area dragged by its body: index and the move in sensor fractions. */
    var onAreaMoved: ((Int, Float, Float) -> Unit)? = null

    /** An area's corner handle dragged to a point in sensor fractions. */
    var onAreaResized: ((Int, Float, Float) -> Unit)? = null

    /** Max/Min markers, drawn under the spots so a placed spot is never hidden. */
    var extremes: List<ExtremeLabel> = emptyList()

    /**
     * The picture is the last one from before the camera went away or stopped. It
     * stays on screen, labels and all, but under a dark veil, so its readings are
     * not taken for live ones.
     */
    var stale: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private val stalePaint = Paint().apply { color = context.getColor(R.color.ff_stale_veil) }

    /**
     * How far the phone itself is turned, in degrees clockwise from upright.
     *
     * The spot labels are turned back by this much so they stay the right way up for
     * whoever is reading them. The picture is deliberately left alone: the camera is
     * bolted to the phone and turns with it, so the scene is already where it should
     * be, while the reader's head is not attached to either.
     */
    var labelRotation: Int = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    /**
     * An area to outline, in fractions of the sensor frame - the zone the
     * calibration screen averages over. Drawn through the same matrix as the
     * picture, so it lands on the pixels it measures whatever the rotation.
     */
    var zone: RectF? = null
        set(value) {
            field = value
            invalidate()
        }

    /** Called with the spot's index and its new position in sensor coordinates. */
    var onSpotMoved: ((Int, Float, Float) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val matrix = Matrix()
    private val inverse = Matrix()
    private val point = FloatArray(2)

    /** Scratch for placing labels in the turned frame; reused to keep onDraw allocation-free. */
    private val labelMatrix = Matrix()
    private val labelBounds = RectF()
    private val viewInLabel = RectF()
    private val lineInLabel = FloatArray(4)

    private val imagePaint = Paint().apply {
        // 80x60 blown up to a phone screen: interpolation just smears the pixels, and
        // a wrong de-interleave has to stay visible as hard banding.
        isFilterBitmap = false
    }
    private val glyphs = SpotGlyphs(density)

    private val zoneRect = RectF()
    private val zonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    /** A dark rim under the white one, so the outline survives a white-hot scene. */
    private val zoneShadow = Paint(zonePaint).apply {
        strokeWidth = 4f * density
        color = 0x99000000.toInt()
    }

    private var dragging = -1

    /** The line end being dragged, -1 when none. */
    private var draggingLineEnd = -1
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = Color.WHITE
    }
    private val lineShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4.5f * density
        color = 0x99000000.toInt()
    }
    private val graphBackground = Paint().apply { color = 0xB0140B07.toInt() }
    private val graphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        color = 0xFFFFB347.toInt()
        strokeJoin = Paint.Join.ROUND
    }
    private val graphPath = android.graphics.Path()
    private val lineEnds = FloatArray(4)

    /** The area being dragged, and whether by its handle; -1 when none. */
    private var draggingArea = -1
    private var resizingArea = false
    private val lastTouch = FloatArray(2)
    private val areaRect = RectF()
    private val areaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.WHITE
        pathEffect = android.graphics.DashPathEffect(floatArrayOf(6f * density, 4f * density), 0f)
    }
    private val areaShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * density
        color = 0x99000000.toInt()
    }
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val areaTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 11.5f * density
        isFakeBoldText = true
    }

    fun setImage(bitmap: Bitmap, rotation: Int, mirrored: Boolean) {
        this.bitmap = bitmap
        this.rotation = rotation
        this.mirrored = mirrored
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = bitmap ?: return
        if (width == 0 || height == 0) return
        ViewTransform.matrixFor(
            srcW = bmp.width.toFloat(),
            srcH = bmp.height.toFloat(),
            viewW = width.toFloat(),
            viewH = height.toFloat(),
            rotation = rotation,
            mirrored = mirrored,
            into = matrix,
        )
        canvas.drawBitmap(bmp, matrix, imagePaint)
        zone?.let { drawZone(canvas, bmp, it) }
        for (area in areas) drawArea(canvas, bmp, area)
        line?.let { drawLine(canvas, bmp, it) }
        trend?.let { drawTrend(canvas, it) }
        for (mark in extremes) drawExtreme(canvas, bmp, mark)
        for (spot in spots) drawSpot(canvas, bmp, spot)
        if (stale) canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), stalePaint)
    }

    private fun drawExtreme(canvas: Canvas, bmp: Bitmap, mark: ExtremeLabel) {
        point[0] = mark.u * bmp.width
        point[1] = mark.v * bmp.height
        matrix.mapPoints(point)
        val x = point[0]
        val y = point[1]
        canvas.save()
        canvas.rotate(-labelRotation.toFloat(), x, y)
        boundsInLabelFrame(x, y, labelBounds)
        glyphs.drawExtreme(canvas, x, y, mark.hot, mark.text, labelBounds)
        canvas.restore()
    }

    private fun mapLineEnds(bmp: Bitmap, label: LineLabel) {
        lineEnds[0] = label.start.u * bmp.width
        lineEnds[1] = label.start.v * bmp.height
        lineEnds[2] = label.end.u * bmp.width
        lineEnds[3] = label.end.v * bmp.height
        matrix.mapPoints(lineEnds)
    }

    private fun drawLine(canvas: Canvas, bmp: Bitmap, label: LineLabel) {
        mapLineEnds(bmp, label)
        val ax = lineEnds[0]
        val ay = lineEnds[1]
        val bx = lineEnds[2]
        val by = lineEnds[3]
        canvas.drawLine(ax, ay, bx, by, lineShadow)
        canvas.drawLine(ax, ay, bx, by, linePaint)
        for (k in 0..1) {
            canvas.drawCircle(lineEnds[2 * k], lineEnds[2 * k + 1], 6f * density, lineShadow)
            canvas.drawCircle(lineEnds[2 * k], lineEnds[2 * k + 1], 6f * density, linePaint)
        }
        // Readings beside the middle, upright for the reader like every other label,
        // and clear of the line rather than struck through by it.
        val cx = (ax + bx) / 2f
        val cy = (ay + by) / 2f
        canvas.save()
        canvas.rotate(-labelRotation.toFloat(), cx, cy)
        boundsInLabelFrame(cx, cy, viewInLabel)
        System.arraycopy(lineEnds, 0, lineInLabel, 0, 4)
        labelMatrix.setRotate(labelRotation.toFloat(), cx, cy)
        labelMatrix.mapPoints(lineInLabel)
        val lines = listOf(label.text)
        val w = glyphs.blockWidth(lines, areaTextPaint)
        val h = glyphs.blockHeight(lines, areaTextPaint)
        val c = LabelPlacement.lineBlock(
            lineInLabel[0], lineInLabel[1], lineInLabel[2], lineInLabel[3], w, h, 3f * density,
            viewInLabel.left, viewInLabel.top, viewInLabel.right, viewInLabel.bottom,
        )
        glyphs.drawBlock(canvas, lines, areaTextPaint, c[0] - w / 2f, c[1] - h / 2f)
        canvas.restore()
        drawProfile(canvas, label)
    }

    /**
     * The profile graph along the bottom of the view, in screen terms rather than
     * the picture's: it is a chart, read the same way however the phone is held.
     */
    private fun drawProfile(canvas: Canvas, label: LineLabel) {
        val values = label.profileC
        if (values.size < 2) return
        val pad = 8f * density
        val graphH = 64f * density
        val top = height - graphH - pad
        val left = pad
        val right = width - pad
        canvas.drawRect(left, top, right, top + graphH, graphBackground)
        var lo = values.min()
        var hi = values.max()
        if (hi - lo < 1f) {
            val mid = (hi + lo) / 2f
            lo = mid - 0.5f
            hi = mid + 0.5f
        }
        val textW = maxOf(areaTextPaint.measureText(label.maxText), areaTextPaint.measureText(label.minText))
        val plotLeft = left + textW + 10f * density
        val plotRight = right - 6f * density
        val plotTop = top + 6f * density
        val plotBottom = top + graphH - 6f * density
        graphPath.reset()
        for (i in values.indices) {
            val x = plotLeft + (plotRight - plotLeft) * i / (values.size - 1)
            val y = plotBottom - (plotBottom - plotTop) * (values[i] - lo) / (hi - lo)
            if (i == 0) graphPath.moveTo(x, y) else graphPath.lineTo(x, y)
        }
        canvas.drawPath(graphPath, graphPaint)
        canvas.drawText(label.maxText, left + 4f * density, plotTop + areaTextPaint.textSize, areaTextPaint)
        canvas.drawText(label.minText, left + 4f * density, plotBottom, areaTextPaint)
    }

    /**
     * The log's graph across the top of the view: Max red, Min blue, spots in their
     * own colours, all on one shared scale so they can be read against each other.
     */
    private fun drawTrend(canvas: Canvas, series: Array<FloatArray?>) {
        var lo = Float.POSITIVE_INFINITY
        var hi = Float.NEGATIVE_INFINITY
        var count = 0
        for (values in series) {
            if (values == null) continue
            count = maxOf(count, values.size)
            for (v in values) {
                if (v.isNaN()) continue
                if (v < lo) lo = v
                if (v > hi) hi = v
            }
        }
        if (count < 2 || lo > hi) return
        if (hi - lo < 1f) {
            val mid = (hi + lo) / 2f
            lo = mid - 0.5f
            hi = mid + 0.5f
        }
        val pad = 8f * density
        val graphH = 72f * density
        val top = pad
        canvas.drawRect(pad, top, width - pad, top + graphH, graphBackground)
        val hiText = String.format(context.uiLocale(), "%.1f°", hi)
        val loText = String.format(context.uiLocale(), "%.1f°", lo)
        val textW = maxOf(areaTextPaint.measureText(hiText), areaTextPaint.measureText(loText))
        val plotLeft = pad + textW + 10f * density
        val plotRight = width - pad - 6f * density
        val plotTop = top + 6f * density
        val plotBottom = top + graphH - 6f * density
        for ((s, values) in series.withIndex()) {
            if (values == null) continue
            trendPaint.color = TREND_COLOURS[s % TREND_COLOURS.size]
            graphPath.reset()
            var pen = false
            for (i in values.indices) {
                val v = values[i]
                if (v.isNaN()) {
                    pen = false
                    continue
                }
                val x = plotLeft + (plotRight - plotLeft) * i / (count - 1)
                val y = plotBottom - (plotBottom - plotTop) * (v - lo) / (hi - lo)
                if (pen) graphPath.lineTo(x, y) else graphPath.moveTo(x, y)
                pen = true
            }
            canvas.drawPath(graphPath, trendPaint)
        }
        canvas.drawText(hiText, pad + 4f * density, plotTop + areaTextPaint.textSize, areaTextPaint)
        canvas.drawText(loText, pad + 4f * density, plotBottom, areaTextPaint)
    }

    private val trendPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeJoin = Paint.Join.ROUND
    }

    private companion object {
        /** Max, Min, then spots 1-9. */
        val TREND_COLOURS = intArrayOf(
            0xFFFF5A4A.toInt(), 0xFF4FC3E0.toInt(),
            0xFFFFFFFF.toInt(), 0xFFFFD54F.toInt(), 0xFF8BC34A.toInt(), 0xFFFF8AD8.toInt(),
            0xFFB39DDB.toInt(), 0xFFFFAB40.toInt(), 0xFF80CBC4.toInt(), 0xFFBCAAA4.toInt(), 0xFFE0E0E0.toInt(),
        )
    }

    /** The area's rectangle on screen, through the picture's own matrix. */
    private fun mapArea(bmp: Bitmap, area: Area, into: RectF) {
        into.set(area.left * bmp.width, area.top * bmp.height, area.right * bmp.width, area.bottom * bmp.height)
        matrix.mapRect(into)
    }

    /** Where the resize handle - the area's (right, bottom) corner in sensor terms - lands. */
    private fun handlePoint(bmp: Bitmap, area: Area, into: FloatArray) {
        into[0] = area.right * bmp.width
        into[1] = area.bottom * bmp.height
        matrix.mapPoints(into)
    }

    private fun drawArea(canvas: Canvas, bmp: Bitmap, label: AreaLabel) {
        mapArea(bmp, label.area, areaRect)
        canvas.drawRect(areaRect, areaShadow)
        canvas.drawRect(areaRect, areaPaint)
        handlePoint(bmp, label.area, point)
        canvas.drawCircle(point[0], point[1], 5f * density, areaShadow)
        canvas.drawCircle(point[0], point[1], 5f * density, handlePaint)

        // The readings above the frame as the reader sees it, or below it: inside, a
        // spot placed in the area would write over them.
        val cx = areaRect.centerX()
        val cy = areaRect.centerY()
        canvas.save()
        canvas.rotate(-labelRotation.toFloat(), cx, cy)
        boundsInLabelFrame(cx, cy, viewInLabel)
        labelMatrix.setRotate(labelRotation.toFloat(), cx, cy)
        labelBounds.set(areaRect)
        labelMatrix.mapRect(labelBounds)
        val lines = ("A${label.number}\n" + label.text).split('\n')
        val at = LabelPlacement.areaBlock(
            labelBounds.left, labelBounds.top, labelBounds.bottom,
            glyphs.blockWidth(lines, areaTextPaint), glyphs.blockHeight(lines, areaTextPaint), 3f * density,
            viewInLabel.left, viewInLabel.top, viewInLabel.right, viewInLabel.bottom,
        )
        glyphs.drawBlock(canvas, lines, areaTextPaint, at[0], at[1])
        canvas.restore()
    }

    private fun drawZone(canvas: Canvas, bmp: Bitmap, area: RectF) {
        zoneRect.set(
            area.left * bmp.width, area.top * bmp.height,
            area.right * bmp.width, area.bottom * bmp.height,
        )
        matrix.mapRect(zoneRect)
        canvas.drawRect(zoneRect, zoneShadow)
        canvas.drawRect(zoneRect, zonePaint)
    }

    private fun drawSpot(canvas: Canvas, bmp: Bitmap, spot: SpotLabel) {
        point[0] = spot.u * bmp.width
        point[1] = spot.v * bmp.height
        matrix.mapPoints(point)
        val x = point[0]
        val y = point[1]

        glyphs.drawMarker(canvas, x, y)

        canvas.save()
        canvas.rotate(-labelRotation.toFloat(), x, y)
        boundsInLabelFrame(x, y, labelBounds)
        glyphs.drawLabels(canvas, x, y, spot.number, spot.text, labelBounds)
        canvas.restore()
    }

    /**
     * The view's edges as they fall in the label frame turned about ([x], [y]).
     *
     * Keeping a label on screen means comparing it against the screen, and the two
     * frames only agree while the phone is upright. Turning the view's own rectangle
     * by the same angle puts both back in one frame; at right angles the mapped
     * rectangle is exact rather than a bounding box, so nothing is lost by it.
     */
    private fun boundsInLabelFrame(x: Float, y: Float, into: RectF) {
        into.set(0f, 0f, width.toFloat(), height.toFloat())
        if (labelRotation == 0) return
        labelMatrix.setRotate(labelRotation.toFloat(), x, y)
        labelMatrix.mapRect(into)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = nearestSpot(bmp, event.x, event.y)
                if (dragging < 0) {
                    draggingLineEnd = pickLineEnd(bmp, event.x, event.y)
                    if (draggingLineEnd >= 0) {
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                    // Spots first: they are small and placed on purpose; an area is
                    // big enough to be grabbed anywhere else.
                    pickArea(bmp, event.x, event.y)
                    if (draggingArea < 0) return false
                    lastTouch[0] = event.x
                    lastTouch[1] = event.y
                }
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (draggingLineEnd >= 0) {
                    reportLineMove(bmp, event.x, event.y)
                    return true
                }
                if (draggingArea >= 0) {
                    reportAreaDrag(bmp, event.x, event.y)
                    return true
                }
                if (dragging < 0) return false
                reportMove(bmp, event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (draggingLineEnd >= 0) {
                    reportLineMove(bmp, event.x, event.y)
                    draggingLineEnd = -1
                    return true
                }
                if (draggingArea >= 0) {
                    reportAreaDrag(bmp, event.x, event.y)
                    draggingArea = -1
                    return true
                }
                if (dragging < 0) return false
                reportMove(bmp, event.x, event.y)
                dragging = -1
                return true
            }
        }
        return false
    }

    private fun reportMove(bmp: Bitmap, viewX: Float, viewY: Float) {
        if (!matrix.invert(inverse)) return
        point[0] = viewX
        point[1] = viewY
        inverse.mapPoints(point)
        onSpotMoved?.invoke(dragging, point[0] / bmp.width, point[1] / bmp.height)
        invalidate()
    }

    /** Which line end is under the finger: 0, 1, or -1 for neither. */
    private fun pickLineEnd(bmp: Bitmap, viewX: Float, viewY: Float): Int {
        val label = line ?: return -1
        mapLineEnds(bmp, label)
        val reach = 28f * density
        val d0 = hypot(lineEnds[0] - viewX, lineEnds[1] - viewY)
        val d1 = hypot(lineEnds[2] - viewX, lineEnds[3] - viewY)
        return when {
            d0 < reach && d0 <= d1 -> 0
            d1 < reach -> 1
            else -> -1
        }
    }

    private fun reportLineMove(bmp: Bitmap, viewX: Float, viewY: Float) {
        if (!matrix.invert(inverse)) return
        point[0] = viewX
        point[1] = viewY
        inverse.mapPoints(point)
        onLineMoved?.invoke(draggingLineEnd, point[0] / bmp.width, point[1] / bmp.height)
        invalidate()
    }

    /** Sets [draggingArea] and [resizingArea] for a touch at ([viewX], [viewY]). */
    private fun pickArea(bmp: Bitmap, viewX: Float, viewY: Float) {
        draggingArea = -1
        val reach = 28f * density
        // Handles first, topmost area first, so a handle inside another area still works.
        for (index in areas.indices.reversed()) {
            handlePoint(bmp, areas[index].area, point)
            if (hypot(point[0] - viewX, point[1] - viewY) < reach) {
                draggingArea = index
                resizingArea = true
                return
            }
        }
        for (index in areas.indices.reversed()) {
            mapArea(bmp, areas[index].area, areaRect)
            if (areaRect.contains(viewX, viewY)) {
                draggingArea = index
                resizingArea = false
                return
            }
        }
    }

    private fun reportAreaDrag(bmp: Bitmap, viewX: Float, viewY: Float) {
        if (!matrix.invert(inverse)) return
        point[0] = viewX
        point[1] = viewY
        inverse.mapPoints(point)
        val u = point[0] / bmp.width
        val v = point[1] / bmp.height
        if (resizingArea) {
            onAreaResized?.invoke(draggingArea, u, v)
        } else {
            point[0] = lastTouch[0]
            point[1] = lastTouch[1]
            inverse.mapPoints(point)
            onAreaMoved?.invoke(draggingArea, u - point[0] / bmp.width, v - point[1] / bmp.height)
            lastTouch[0] = viewX
            lastTouch[1] = viewY
        }
        invalidate()
    }

    /** Index of the spot under the finger, or -1 if the touch missed them all. */
    private fun nearestSpot(bmp: Bitmap, viewX: Float, viewY: Float): Int {
        // Generous, because a fingertip is far bigger than the marker it is aiming at.
        val reach = 28f * density
        var best = -1
        var bestDistance = reach
        for ((index, spot) in spots.withIndex()) {
            point[0] = spot.u * bmp.width
            point[1] = spot.v * bmp.height
            matrix.mapPoints(point)
            val d = hypot(point[0] - viewX, point[1] - viewY)
            if (d < bestDistance) {
                bestDistance = d
                best = index
            }
        }
        return best
    }
}
