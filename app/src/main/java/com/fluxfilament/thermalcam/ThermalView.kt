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

    /** An area dragged by its body: index and the move in sensor fractions. */
    var onAreaMoved: ((Int, Float, Float) -> Unit)? = null

    /** An area's corner handle dragged to a point in sensor fractions. */
    var onAreaResized: ((Int, Float, Float) -> Unit)? = null

    /** Max/Min markers, drawn under the spots so a placed spot is never hidden. */
    var extremes: List<ExtremeLabel> = emptyList()

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
    private val areaTextShadow = Paint(areaTextPaint).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = 0xC8000000.toInt()
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
        for (mark in extremes) drawExtreme(canvas, bmp, mark)
        for (spot in spots) drawSpot(canvas, bmp, spot)
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

        // The readings inside the top-left corner as the reader sees it.
        val cx = areaRect.centerX()
        val cy = areaRect.centerY()
        canvas.save()
        canvas.rotate(-labelRotation.toFloat(), cx, cy)
        labelMatrix.setRotate(labelRotation.toFloat(), cx, cy)
        labelBounds.set(areaRect)
        labelMatrix.mapRect(labelBounds)
        val lines = ("A${label.number}\n" + label.text).split('\n')
        var y = labelBounds.top + 4f * density + areaTextPaint.textSize
        for (line in lines) {
            canvas.drawText(line, labelBounds.left + 4f * density, y, areaTextShadow)
            canvas.drawText(line, labelBounds.left + 4f * density, y, areaTextPaint)
            y += areaTextPaint.textSize * 1.15f
        }
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
                if (draggingArea >= 0) {
                    reportAreaDrag(bmp, event.x, event.y)
                    return true
                }
                if (dragging < 0) return false
                reportMove(bmp, event.x, event.y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
