package com.mantracounter.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Custom view that renders 108 bead positions in a ring.
 * Filled beads are gold, empty are dark. Every 10th bead is slightly larger (milestone).
 */
class MalaProgressView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val totalBeads = 108

    private val paintFilled = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D4AF37")
        style = Paint.Style.FILL
    }

    private val paintEmpty = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#33806040")
        style = Paint.Style.FILL
    }

    private val paintMilestone = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FF6B00")
        style = Paint.Style.FILL
    }

    private val paintGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#60D4AF37")
        style = Paint.Style.FILL
    }

    var progress: Int = 0
        set(value) {
            field = value.coerceIn(0, totalBeads)
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f

        // Ring radius: leave padding for beads
        val ringRadius = min(cx, cy) * 0.82f
        val beadRadius = ringRadius * 0.085f
        val milestoneRadius = beadRadius * 1.45f
        val glowRadius = beadRadius * 1.9f

        for (i in 0 until totalBeads) {
            // Start from top (−90°), go clockwise
            val angle = Math.toRadians((i * 360.0 / totalBeads) - 90.0)
            val bx = cx + ringRadius * cos(angle).toFloat()
            val by = cy + ringRadius * sin(angle).toFloat()

            val isMilestone = (i % 10 == 0)
            val isFilled = i < progress

            if (isFilled) {
                if (isMilestone) {
                    // Glow behind milestone bead
                    canvas.drawCircle(bx, by, glowRadius, paintGlow)
                    canvas.drawCircle(bx, by, milestoneRadius, paintMilestone)
                } else {
                    canvas.drawCircle(bx, by, beadRadius, paintFilled)
                }
            } else {
                val r = if (isMilestone) milestoneRadius * 0.9f else beadRadius
                canvas.drawCircle(bx, by, r, paintEmpty)
            }
        }
    }
}
