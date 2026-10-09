package com.boost.panel

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class PanelView(private val ctx: Context, private val svc: BoosterService) : LinearLayout(ctx) {
    private val prefs = ctx.getSharedPreferences("boost", Context.MODE_PRIVATE)
    private val ui = Handler(Looper.getMainLooper())
    private val cpuG = GaugeView(ctx, "CPU")
    private val gpuG = GaugeView(ctx, "GPU")
    private val clock = label("", 12f, Color.WHITE)
    private val hzTv = label("Hz --", 11f, 0xCCFFFFFF.toInt())
    private val modes = ArrayList<TextView>()
    private val tabs = ArrayList<TextView>()
    private val content = FrameLayout(ctx)
    private var recBtn: TextView? = null
    @Volatile private var running = false

    init {
        orientation = VERTICAL
        setPadding(ctx.dp(12), ctx.dp(10), ctx.dp(12), ctx.dp(12))
        background = GradientDrawable().apply {
            setColor(0xF2121218.toInt()); cornerRadius = ctx.dpf(18f); setStroke(ctx.dp(1), 0x44FFFFFF)
        }
        addView(header())
        addView(appRow())
        val body = LinearLayout(ctx).apply { orientation = HORIZONTAL }
        body.addView(sidebar(), LayoutParams(ctx.dp(84), LayoutParams.WRAP_CONTENT))
        body.addView(content, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(body)
        selectTab(0)
    }

    private fun label(t: String, sp: Float, color: Int) = TextView(ctx).apply {
        text = t; textSize = sp; setTextColor(color)
    }

    private fun header(): View {
        val row = LinearLayout(ctx).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = label("⚡ BOOST", 15f, 0xFFFF4D4D.toInt()).apply { setTypeface(typeface, android.graphics.Typeface.BOLD) }
        row.addView(title, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row.addView(clock)
        row.addView(label("  ✕", 16f, Color.WHITE).apply { setOnClickListener { svc.setPanel(false) } })
        var lx = 0f; var ly = 0f
        row.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { lx = e.rawX; ly = e.rawY }
                MotionEvent.ACTION_MOVE -> {
                    svc.movePanel((e.rawX - lx).toInt(), (e.rawY - ly).toInt()); lx = e.rawX; ly = e.rawY
                }
            }
            false
        }
        return row
    }

    private fun appRow(): View {
        val pkgs = listOf("com.facebook.katana", "com.google.android.gm", "com.discord",
            "com.zhiliaoapp.musically", "com.whatsapp", "com.instagram.android", "org.telegram.messenger")
        val row = LinearLayout(ctx).apply { setPadding(0, ctx.dp(8), 0, ctx.dp(8)) }
        for (p in pkgs) {
            val launch = ctx.packageManager.getLaunchIntentForPackage(p) ?: continue
            val icon = ImageView(ctx).apply {
                setImageDrawable(ctx.packageManager.getApplicationIcon(p))
                setOnClickListener {
                    svc.setPanel(false)
                    ctx.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            row.addView(icon, LinearLayout.LayoutParams(ctx.dp(38), ctx.dp(38)).apply { rightMargin = ctx.dp(10) })
        }
        return HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; addView(row) }
    }

    private fun sidebar(): View {
        val col = LinearLayout(ctx).apply { orientation = VERTICAL }
        listOf("Fitur Dasar", "Akses Cepat", "Anti-ganggu").forEachIndexed { i, name ->
            val t = label(name, 12f, 0xAAFFFFFF.toInt()).apply {
                gravity = Gravity.CENTER; setPadding(0, ctx.dp(14), 0, ctx.dp(14))
                setOnClickListener { selectTab(i) }
            }
            tabs.add(t); col.addView(t)
        }
        return col
    }

    private fun selectTab(i: Int) {
        tabs.forEachIndexed { k, t ->
            t.setTextColor(if (k == i) Color.WHITE else 0xAAFFFFFF.toInt())
            t.setTypeface(null, if (k == i) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
        }
        content.removeAllViews()
        content.addView(when (i) { 0 -> basicTab(); 1 -> quickTab(); else -> dndTab() })
    }

    private fun basicTab(): View {
        val col = LinearLayout(ctx).apply { orientation = VERTICAL }
        col.addView(hzTv, LinearLayout.LayoutParams(-2, -2).apply { gravity = Gravity.END })
        val gauges = LinearLayout(ctx).apply { gravity = Gravity.CENTER }
        gauges.addView(cpuG, LinearLayout.LayoutParams(ctx.dp(100), ctx.dp(100)))
        gauges.addView(gpuG, LinearLayout.LayoutParams(ctx.dp(100), ctx.dp(100)))
        col.addView(gauges)

        val seg = LinearLayout(ctx).apply {
            background = GradientDrawable().apply { setColor(0x22FFFFFF); cornerRadius = ctx.dpf(8f) }
        }
        modes.clear()
        listOf("Penghemat", "Seimbang", "Kinerja").forEachIndexed { i, n ->
            val t = label(n, 12f, Color.WHITE).apply {
                gravity = Gravity.CENTER; setPadding(0, ctx.dp(8), 0, ctx.dp(8)); setOnClickListener { setMode(i, true) }
            }
            modes.add(t); seg.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
        }
        col.addView(seg, LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(6); bottomMargin = ctx.dp(10) })
        setMode(prefs.getInt("mode", 1), false)

        val btns = LinearLayout(ctx)
        btns.addView(actionBtn("📷", "Tangkapan layar") { capture("shot") })
        recBtn = null
        btns.addView(actionBtn("⏺", if (CaptureService.recording) "Stop rekam" else "Rekam layar") {
            if (CaptureService.recording) {
                ctx.startService(Intent(ctx, CaptureService::class.java).setAction(CaptureService.ACT_STOP))
                svc.setPanel(false)
            } else capture("record")
        })
        btns.addView(actionBtn("📶", "Wi-Fi") {
            svc.setPanel(false)
            ctx.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        btns.addView(actionBtn("🧹", "Pembersih Memori") {
            Toast.makeText(ctx, "Membersihkan...", Toast.LENGTH_SHORT).show()
            Thread {
                val mb = Boost.clean(ctx)
                ui.post { Toast.makeText(ctx, "Bebas ±$mb MB RAM", Toast.LENGTH_SHORT).show() }
            }.start()
        })
        col.addView(btns)
        return col
    }

    private fun capture(mode: String) {
        svc.setPanel(false)  // sembunyikan panel supaya tidak ikut terekam
        ctx.startActivity(Intent(ctx, CaptureActivity::class.java).putExtra("mode", mode).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun setMode(i: Int, apply: Boolean) {
        modes.forEachIndexed { k, t ->
            t.background = GradientDrawable().apply {
                cornerRadius = ctx.dpf(8f)
                if (k == i) { setColor(0x33FF4D4D); setStroke(ctx.dp(1), 0xFFFF4D4D.toInt()) } else setColor(Color.TRANSPARENT)
            }
            t.setTextColor(if (k == i) 0xFFFF6B6B.toInt() else Color.WHITE)
        }
        if (apply) {
            prefs.edit().putInt("mode", i).apply()
            Thread { Boost.apply(i) }.start()
            val msg = if (SysStats.useRoot) "Mode diterapkan (root)" else "Mode disimpan (efek penuh butuh root)"
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun actionBtn(icon: String, name: String, click: () -> Unit): View {
        val col = LinearLayout(ctx).apply { orientation = VERTICAL; gravity = Gravity.CENTER; setOnClickListener { click() } }
        col.addView(label(icon, 18f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(0x33FFFFFF) }
        }, LinearLayout.LayoutParams(ctx.dp(42), ctx.dp(42)))
        col.addView(label(name, 10f, 0xCCFFFFFF.toInt()).apply { gravity = Gravity.CENTER; maxLines = 2 },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = ctx.dp(4) })
        return col.also { it.layoutParams = LinearLayout.LayoutParams(0, -2, 1f) }
    }

    private fun quickTab(): View {
        val col = LinearLayout(ctx).apply { orientation = VERTICAL; setPadding(ctx.dp(8), 0, 0, 0) }
        listOf(
            "Pengaturan layar" to Settings.ACTION_DISPLAY_SETTINGS,
            "Hemat baterai" to Settings.ACTION_BATTERY_SAVER_SETTINGS,
            "Opsi pengembang" to Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS,
            "Pengaturan suara" to Settings.ACTION_SOUND_SETTINGS
        ).forEach { (n, act) ->
            col.addView(label(n, 13f, Color.WHITE).apply {
                setPadding(ctx.dp(12), ctx.dp(12), ctx.dp(12), ctx.dp(12))
                background = GradientDrawable().apply { setColor(0x22FFFFFF); cornerRadius = ctx.dpf(10f) }
                setOnClickListener {
                    svc.setPanel(false)
                    ctx.startActivity(Intent(act).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = ctx.dp(6) })
        }
        return col
    }

    private fun dndTab(): View {
        val col = LinearLayout(ctx).apply { orientation = VERTICAL; setPadding(ctx.dp(8), ctx.dp(8), 0, 0) }
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val sw = Switch(ctx).apply {
            text = "Jangan ganggu saat main"; setTextColor(Color.WHITE)
            isChecked = nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_NONE
            setOnCheckedChangeListener { _, on ->
                if (!nm.isNotificationPolicyAccessGranted) {
                    svc.setPanel(false)
                    ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } else nm.setInterruptionFilter(
                    if (on) NotificationManager.INTERRUPTION_FILTER_NONE else NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        }
        col.addView(sw)
        col.addView(label("Butuh izin akses Jangan Ganggu sekali saja.", 11f, 0x99FFFFFF.toInt()))
        return col
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Thread {
            val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
            while (running) {
                if (visibility == VISIBLE) {
                    val c = SysStats.cpu(); val g = SysStats.gpu()
                    @Suppress("DEPRECATION") val hz = wm.defaultDisplay.refreshRate.toInt()
                    ui.post {
                        cpuG.value = c; gpuG.value = g
                        hzTv.text = "Layar $hz Hz"; clock.text = fmt.format(Date())
                    }
                }
                try { Thread.sleep(1000) } catch (e: InterruptedException) { break }
            }
        }.start()
    }

    override fun onDetachedFromWindow() { running = false; super.onDetachedFromWindow() }
}
