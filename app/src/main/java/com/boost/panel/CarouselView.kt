package com.boost.panel

import android.content.Context
import android.view.*
import android.widget.FrameLayout
import kotlin.math.abs

/** Carousel kartu 3D: kartu tengah besar, kiri/kanan kecil dan miring. Geser atau ketuk kartu samping. */
class CarouselView(ctx: Context) : FrameLayout(ctx) {
    var selected = 0; private set
    var onSelect: ((Int) -> Unit)? = null
    private var downX = 0f
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop

    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (abs(vx) > 300) { select(selected + if (vx < 0) 1 else -1); return true }
            return false
        }
    })

    init { clipChildren = false; clipToPadding = false }

    fun setCards(list: List<View>, sel: Int) {
        removeAllViews()
        list.forEachIndexed { i, v ->
            v.cameraDistance = 9000f * resources.displayMetrics.density
            v.setOnClickListener { select(i) }
            addView(v, LayoutParams(context.dp(300), context.dp(190), Gravity.CENTER))
        }
        selected = sel.coerceIn(0, (list.size - 1).coerceAtLeast(0))
        layoutCards(false)
    }

    fun select(i: Int) {
        if (childCount == 0) return
        val n = i.coerceIn(0, childCount - 1)
        if (n == selected) return
        selected = n
        layoutCards(true)
        onSelect?.invoke(n)
    }

    private fun layoutCards(animate: Boolean) {
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val off = i - selected
            val sign = if (off > 0) 1 else if (off < 0) -1 else 0
            val a = abs(off)
            val tx = if (a == 0) 0f else sign * (context.dpf(235f) + (a - 1) * context.dpf(70f))
            val sc = if (a == 0) 1f else 0.72f
            val rot = -sign * 28f
            val alpha = when { a == 0 -> 1f; a == 1 -> 0.8f; else -> 0f }
            v.z = if (a == 0) 10f else 0f
            if (animate) v.animate().translationX(tx).scaleX(sc).scaleY(sc).rotationY(rot).alpha(alpha).setDuration(260).start()
            else { v.translationX = tx; v.scaleX = sc; v.scaleY = sc; v.rotationY = rot; v.alpha = alpha }
        }
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        when (e.action) {
            MotionEvent.ACTION_DOWN -> downX = e.x
            MotionEvent.ACTION_MOVE -> if (abs(e.x - downX) > slop) return true
        }
        return false
    }

    override fun onTouchEvent(e: MotionEvent): Boolean { gd.onTouchEvent(e); return true }
}
