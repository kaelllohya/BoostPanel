package com.boost.panel

import android.content.Context
import android.graphics.*
import android.view.View

/** Panggung neon di bawah kartu game. */
class PlatformView(ctx: Context) : View(ctx) {
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; color = 0xFF1EE6FF.toInt(); strokeWidth = ctx.dpf(2.5f)
        maskFilter = BlurMaskFilter(ctx.dpf(5f), BlurMaskFilter.Blur.SOLID)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat(); val cx = w / 2
        fill.shader = RadialGradient(cx, h * 0.5f, w * 0.45f,
            intArrayOf(0xFF1B5AB5.toInt(), 0x00123C7A), null, Shader.TileMode.CLAMP)
        c.drawOval(RectF(cx - w * 0.48f, h * 0.1f, cx + w * 0.48f, h * 0.95f), fill)
        fill.shader = null
        fill.color = 0xFF0A1C3C.toInt()
        val outer = RectF(cx - w * 0.42f, h * 0.2f, cx + w * 0.42f, h * 0.85f)
        c.drawOval(outer, fill)
        c.drawOval(outer, ring)
        val inner = RectF(cx - w * 0.3f, h * 0.32f, cx + w * 0.3f, h * 0.72f)
        fill.color = 0xFF0D2A57.toInt()
        c.drawOval(inner, fill)
        c.drawOval(inner, ring)
    }
}
