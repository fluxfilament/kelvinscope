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
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Thermal on one side of a line, visible light on the other, with the line draggable.
 *
 * Blending the two at 50% would hide the very thing being set here: when the field of
 * view is wrong, a blend looks soft and a split looks broken, and only one of those is
 * actionable. The user drags the line onto an edge of the subject and turns the crop
 * until the edge meets itself across it.
 *
 * Both layers go through the same display transform as the live view, so what lines up
 * here lines up there.
 */
class SplitPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density

    private var thermal: Bitmap? = null
    private var visible: Bitmap? = null

    /** Maps the visible frame onto the thermal pixel grid; supplied by the Compositor. */
    private val visToThermal = Matrix()
    private var haveVisible = false

    private var rotation = ViewTransform.DEFAULT_ROTATION
    private var mirrored = true

    /** Where the dividing line sits, as a fraction of the view's width (or height). */
    private var split = 0.5f

    /**
     * A line across rather than upright: thermal above, visible below. An upright
     * line shows the layers slipping up and down past each other and nothing of a
     * slip sideways, which only a line across can show - so each alignment axis has
     * its line, see [showAxis].
     */
    var horizontal = false
        set(value) {
            if (field == value) return
            field = value
            split = 0.5f
            invalidate()
        }

    private val display = Matrix()
    private val combined = Matrix()
    private val imagePaint = Paint().apply { isFilterBitmap = false }
    private val smoothPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val dividerPaint = Paint().apply { color = ACCENT2 }
    private val gripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ACCENT2 }
    private val gripLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ON_ACCENT
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }
    private val tagBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TAG_BG }
    private val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT
        textSize = 10.5f * density
        typeface = Fonts.mono(context)
    }
    private val gripRect = RectF()
    private val tagRect = RectF()

    fun show(
        thermal: Bitmap,
        visible: Bitmap?,
        visToThermal: Matrix?,
        rotation: Int,
        mirrored: Boolean,
    ) {
        this.thermal = thermal
        this.visible = visible
        this.rotation = rotation
        this.mirrored = mirrored
        haveVisible = visible != null && visToThermal != null
        if (visToThermal != null) this.visToThermal.set(visToThermal)
        invalidate()
    }

    /**
     * Turns the line to the one that shows misalignment along the source frame's x
     * axis ([sensorX], the Align direction) or its y axis (Shift). Which of those is
     * upright on screen depends on how the picture is turned.
     */
    fun showAxis(sensorX: Boolean) {
        val sourceXIsUpright = rotation % 180 != 0
        // A slip along an axis shows across a line parallel to it.
        horizontal = sensorX != sourceXIsUpright
    }

    override fun onDraw(canvas: Canvas) {
        val thermalBitmap = thermal ?: return
        if (width == 0 || height == 0) return
        ViewTransform.matrixFor(
            srcW = thermalBitmap.width.toFloat(),
            srcH = thermalBitmap.height.toFloat(),
            viewW = width.toFloat(),
            viewH = height.toFloat(),
            rotation = rotation,
            mirrored = mirrored,
            into = display,
        )
        canvas.drawBitmap(thermalBitmap, display, imagePaint)

        val w = width.toFloat()
        val h = height.toFloat()
        val at = split * if (horizontal) h else w
        val visibleBitmap = visible
        if (haveVisible && visibleBitmap != null) {
            canvas.save()
            if (horizontal) canvas.clipRect(0f, at, w, h) else canvas.clipRect(at, 0f, w, h)
            combined.set(visToThermal)
            combined.postConcat(display)
            canvas.drawBitmap(visibleBitmap, combined, smoothPaint)
            canvas.restore()
        }

        val long = 22f * density
        val short = 10f * density
        val tick = 6f * density
        val apart = 2f * density
        if (horizontal) {
            canvas.drawRect(0f, at - density, w, at + density, dividerPaint)
            gripRect.set(w / 2f - long, at - short, w / 2f + long, at + short)
            canvas.drawRoundRect(gripRect, apart, apart, gripPaint)
            canvas.drawLine(w / 2f - tick, at - apart, w / 2f + tick, at - apart, gripLine)
            canvas.drawLine(w / 2f - tick, at + apart, w / 2f + tick, at + apart, gripLine)
        } else {
            canvas.drawRect(at - density, 0f, at + density, h, dividerPaint)
            gripRect.set(at - short, h / 2f - long, at + short, h / 2f + long)
            canvas.drawRoundRect(gripRect, apart, apart, gripPaint)
            canvas.drawLine(at - apart, h / 2f - tick, at - apart, h / 2f + tick, gripLine)
            canvas.drawLine(at + apart, h / 2f - tick, at + apart, h / 2f + tick, gripLine)
        }

        val edge = 8f * density
        drawTag(canvas, context.getString(R.string.fov_tag_thermal), edge, true, edge)
        if (horizontal) {
            drawTag(canvas, context.getString(R.string.fov_tag_visible), edge, true, h - edge - TAG_HEIGHT * density)
        } else {
            drawTag(canvas, context.getString(R.string.fov_tag_visible), w - edge, false, edge)
        }
    }

    private fun drawTag(canvas: Canvas, text: String, edge: Float, leftAligned: Boolean, top: Float) {
        val textWidth = tagText.measureText(text)
        val padding = 7f * density
        val left = if (leftAligned) edge else edge - textWidth - 2 * padding
        tagRect.set(left, top, left + textWidth + 2 * padding, top + TAG_HEIGHT * density)
        canvas.drawRoundRect(tagRect, density, density, tagBackground)
        canvas.drawText(text, left + padding, tagRect.bottom - 6f * density, tagText)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                parent?.requestDisallowInterceptTouchEvent(true)
                split = (if (horizontal) event.y / height else event.x / width).coerceIn(0f, 1f)
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private companion object {
        const val ACCENT2 = 0xFF4FC3E0.toInt()
        const val ON_ACCENT = 0xFF0B1A1F.toInt()
        const val TEXT = 0xFFEFE7D8.toInt()
        const val TAG_BG = 0xB8140B07.toInt()
        const val TAG_HEIGHT = 20f
    }
}
