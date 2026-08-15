package com.alsaeeddev.recapp.ui.components

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View

class ActiveCropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var isPaused: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    var durationText: String = "00:00"
        set(value) {
            field = value
            invalidate()
        }

    private val density = context.resources.displayMetrics.density

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = Color.parseColor("#00F2FE") // Vibrant Studio Cyan
        pathEffect = CornerPathEffect(8f * density)
    }

    private val cornerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f * density
        strokeCap = Paint.Cap.ROUND
        color = Color.parseColor("#FF3B30") // Glowing Red accents
    }

    private val badgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E612131A") // Dark translucent pill
    }

    private val badgeBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = Color.parseColor("#4D00F2FE")
    }

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#FF3B30")
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
        typeface = Typeface.DEFAULT_BOLD
    }

    private val rectF = RectF()
    private val badgeRectF = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val paddingLeft = 12f * density
        val paddingTop = 36f * density
        val boxWidth = width.toFloat() - (24f * density)
        val boxHeight = height.toFloat() - (48f * density)

        if (boxWidth <= 0 || boxHeight <= 0) return

        // Position rectF slightly 2dp outside so stroke never enters crop bounds
        val gap = 2f * density
        rectF.set(
            paddingLeft - gap,
            paddingTop - gap,
            paddingLeft + boxWidth + gap,
            paddingTop + boxHeight + gap
        )

        // 1. Draw outer frame
        canvas.drawRoundRect(rectF, 8f * density, 8f * density, borderPaint)

        // 2. Draw Corner Brackets caps
        val cornerLen = 20f * density
        // Top-Left
        canvas.drawLine(rectF.left, rectF.top, rectF.left + cornerLen, rectF.top, cornerPaint)
        canvas.drawLine(rectF.left, rectF.top, rectF.left, rectF.top + cornerLen, cornerPaint)
        // Top-Right
        canvas.drawLine(rectF.right, rectF.top, rectF.right - cornerLen, rectF.top, cornerPaint)
        canvas.drawLine(rectF.right, rectF.top, rectF.right, rectF.top + cornerLen, cornerPaint)
        // Bottom-Left
        canvas.drawLine(rectF.left, rectF.bottom, rectF.left + cornerLen, rectF.bottom, cornerPaint)
        canvas.drawLine(rectF.left, rectF.bottom, rectF.left, rectF.bottom - cornerLen, cornerPaint)
        // Bottom-Right
        canvas.drawLine(rectF.right, rectF.bottom, rectF.right - cornerLen, rectF.bottom, cornerPaint)
        canvas.drawLine(rectF.right, rectF.bottom, rectF.right, rectF.bottom - cornerLen, cornerPaint)

        // 3. Draw Badge Pill Above the Box
        val badgeH = 26f * density
        val badgeW = 110f * density
        val badgeX = paddingLeft
        val badgeY = paddingTop - badgeH - 6f * density

        badgeRectF.set(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH)
        canvas.drawRoundRect(badgeRectF, 13f * density, 13f * density, badgeBgPaint)
        canvas.drawRoundRect(badgeRectF, 13f * density, 13f * density, badgeBorderPaint)

        // Pulsing dot or Pause icon
        if (isPaused) {
            dotPaint.color = Color.parseColor("#FFCC00") // Yellow for pause
            canvas.drawCircle(badgeX + 16f * density, badgeY + badgeH / 2f, 4f * density, dotPaint)
            canvas.drawText("PAUSED", badgeX + 28f * density, badgeY + badgeH / 2f + 4f * density, textPaint)
        } else {
            // Pulse red dot
            val alpha = (128 + 127 * Math.sin(SystemClock.uptimeMillis() / 200.0)).toInt().coerceIn(0, 255)
            dotPaint.color = Color.argb(alpha, 255, 59, 48)
            canvas.drawCircle(badgeX + 16f * density, badgeY + badgeH / 2f, 4f * density, dotPaint)
            canvas.drawText("REC $durationText", badgeX + 28f * density, badgeY + badgeH / 2f + 4f * density, textPaint)
            postInvalidateDelayed(100)
        }
    }
}
