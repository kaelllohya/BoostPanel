package com.boost.panel

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

class BoosterService : Service() {
    companion object { @Volatile var running = false }

    private lateinit var wm: WindowManager
    private lateinit var panel: PanelView
    private lateinit var bubble: TextView
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var bubbleLp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        Notif.startFg(this, 1, "Panel booster aktif")
        Thread { SysStats.useRoot = Shell.hasRoot() }.start()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE

        panelLp = WindowManager.LayoutParams(dp(380), -2, type, flags, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = dp(24); y = dp(24) }
        panel = PanelView(this, this).also { it.visibility = View.GONE }
        wm.addView(panel, panelLp)

        bubble = TextView(this).apply {
            text = "⚡"; textSize = 20f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0xDDE53935.toInt()) }
        }
        bubbleLp = WindowManager.LayoutParams(dp(46), dp(46), type, flags, PixelFormat.TRANSLUCENT)
            .apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = dp(160) }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = bubbleLp.x; oy = bubbleLp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (abs(e.rawX - sx) > 10 || abs(e.rawY - sy) > 10) moved = true
                    bubbleLp.x = ox + (e.rawX - sx).toInt(); bubbleLp.y = oy + (e.rawY - sy).toInt()
                    wm.updateViewLayout(bubble, bubbleLp)
                }
                MotionEvent.ACTION_UP -> if (!moved) setPanel(panel.visibility != View.VISIBLE)
            }
            true
        }
        wm.addView(bubble, bubbleLp)
    }

    fun setPanel(show: Boolean) { panel.visibility = if (show) View.VISIBLE else View.GONE }

    fun movePanel(dx: Int, dy: Int) {
        panelLp.x += dx; panelLp.y += dy
        wm.updateViewLayout(panel, panelLp)
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int) = START_STICKY

    override fun onDestroy() {
        running = false
        runCatching { wm.removeView(panel); wm.removeView(bubble) }
        super.onDestroy()
    }
}
