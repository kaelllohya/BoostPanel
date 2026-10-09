package com.boost.panel

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.min

class GaugeView(ctx: Context, private val label: String) : View(ctx) {
    var value = -1
        set(v) { field = v; invalidate() }
    var centerText: String? = null
        set(v) { field = v; invalidate() }
    var arcColor: Int? = null
        set(v) { field = v; invalidate() }

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; color = 0x33FFFFFF; strokeCap = Paint.Cap.ROUND }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val big = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xAAFFFFFF.toInt(); textAlign = Paint.Align.CENTER }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val sw = s * 0.09f
        track.strokeWidth = sw; arc.strokeWidth = sw
        big.textSize = s * 0.24f; small.textSize = s * 0.13f
        val p = s * 0.12f
        val r = RectF(p, p, s - p, s - p)
        c.drawArc(r, 135f, 270f, false, track)
        if (value >= 0) {
            arc.color = arcColor ?: when { value >= 80 -> 0xFFFF3B30.toInt(); value >= 50 -> 0xFFFF6B3D.toInt(); else -> 0xFF29B6FF.toInt() }
            c.drawArc(r, 135f, 270f * value / 100f, false, arc)
        }
        val t = centerText ?: if (value >= 0) "$value%" else "N/A"
        c.drawText(t, s / 2, s / 2 + s * 0.04f, big)
        c.drawText(label, s / 2, s / 2 + s * 0.2f, small)
    }
}
