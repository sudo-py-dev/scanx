package com.scanx.qrscanner

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat

class ScanOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val overlayPaint = Paint().apply {
        color = Color.parseColor("#99000000")
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint().apply {
        color = Color.parseColor("#00D4FF")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val cornerPaint = Paint().apply {
        color = Color.parseColor("#00D4FF")
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val scanLinePaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.FILL
    }

    private var scanLineY = 0f
    private val viewfinderRect = RectF()
    private val cornerLength = 40f
    private var animator: ValueAnimator? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val size = (w * 0.7f).coerceAtMost(h * 0.4f)
        val left = (w - size) / 2f
        val top = (h - size) / 2f
        viewfinderRect.set(left, top, left + size, top + size)

        startScanAnimation()
    }

    private fun startScanAnimation() {
        animator?.cancel()
        animator = ValueAnimator.ofFloat(viewfinderRect.top, viewfinderRect.bottom).apply {
            duration = 2500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                scanLineY = animation.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Draw semi-transparent overlay with cutout
        // Top
        canvas.drawRect(0f, 0f, w, viewfinderRect.top, overlayPaint)
        // Bottom
        canvas.drawRect(0f, viewfinderRect.bottom, w, h, overlayPaint)
        // Left
        canvas.drawRect(0f, viewfinderRect.top, viewfinderRect.left, viewfinderRect.bottom, overlayPaint)
        // Right
        canvas.drawRect(viewfinderRect.right, viewfinderRect.top, w, viewfinderRect.bottom, overlayPaint)

        // Draw thin border
        canvas.drawRect(viewfinderRect, borderPaint)

        // Draw corner brackets
        drawCorners(canvas)

        // Draw scan line with gradient
        drawScanLine(canvas)
    }

    private fun drawCorners(canvas: Canvas) {
        val r = viewfinderRect
        val cl = cornerLength

        // Top-left
        canvas.drawLine(r.left, r.top, r.left + cl, r.top, cornerPaint)
        canvas.drawLine(r.left, r.top, r.left, r.top + cl, cornerPaint)

        // Top-right
        canvas.drawLine(r.right - cl, r.top, r.right, r.top, cornerPaint)
        canvas.drawLine(r.right, r.top, r.right, r.top + cl, cornerPaint)

        // Bottom-left
        canvas.drawLine(r.left, r.bottom - cl, r.left, r.bottom, cornerPaint)
        canvas.drawLine(r.left, r.bottom, r.left + cl, r.bottom, cornerPaint)

        // Bottom-right
        canvas.drawLine(r.right - cl, r.bottom, r.right, r.bottom, cornerPaint)
        canvas.drawLine(r.right, r.bottom - cl, r.right, r.bottom, cornerPaint)
    }

    private fun drawScanLine(canvas: Canvas) {
        if (scanLineY < viewfinderRect.top || scanLineY > viewfinderRect.bottom) return

        val lineHeight = 4f
        val gradient = LinearGradient(
            viewfinderRect.left, scanLineY,
            viewfinderRect.right, scanLineY,
            intArrayOf(
                Color.TRANSPARENT,
                Color.parseColor("#8000D4FF"),
                Color.parseColor("#00D4FF"),
                Color.parseColor("#8000D4FF"),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.2f, 0.5f, 0.8f, 1f),
            Shader.TileMode.CLAMP
        )
        scanLinePaint.shader = gradient
        canvas.drawRect(
            viewfinderRect.left + 2f,
            scanLineY - lineHeight / 2f,
            viewfinderRect.right - 2f,
            scanLineY + lineHeight / 2f,
            scanLinePaint
        )

        // Draw a glow effect below the scan line
        val glowGradient = LinearGradient(
            viewfinderRect.left, scanLineY,
            viewfinderRect.left, scanLineY + 30f,
            Color.parseColor("#4000D4FF"),
            Color.TRANSPARENT,
            Shader.TileMode.CLAMP
        )
        scanLinePaint.shader = glowGradient
        canvas.drawRect(
            viewfinderRect.left + 2f,
            scanLineY,
            viewfinderRect.right - 2f,
            scanLineY + 30f,
            scanLinePaint
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator?.cancel()
    }
}
