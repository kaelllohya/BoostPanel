package com.boost.panel

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("boost", MODE_PRIVATE) }
    private lateinit var carousel: CarouselView
    private lateinit var cpuG: GaugeView
    private lateinit var ramG: GaugeView
    private lateinit var romG: GaugeView
    private lateinit var gpuG: GaugeView
    private lateinit var tempTv: TextView
    private lateinit var battTv: TextView
    private lateinit var netTv: TextView
    private lateinit var tipTv: TextView
    @Volatile private var alive = false

    private val cyan = 0xFF1EE6FF.toInt()

    private fun tv(t: String, sp: Float, color: Int = Color.WHITE) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(color)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val root = FrameLayout(this).apply {
            clipChildren = false
            background = GradientDrawable().apply {
                gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = dpf(520f); setGradientCenter(0.5f, 0.6f)
                colors = intArrayOf(0xFF0F3A7A.toInt(), 0xFF050B1A.toInt())
            }
        }
        root.addView(PlatformView(this), FrameLayout.LayoutParams(dp(560), dp(150), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(6) })
        carousel = CarouselView(this).apply { onSelect = { prefs.edit().putInt("sel", it).apply() } }
        root.addView(carousel, FrameLayout.LayoutParams(-1, dp(230), Gravity.CENTER).apply { topMargin = dp(28) })
        root.addView(topBar(), FrameLayout.LayoutParams(-1, dp(64), Gravity.TOP))
        root.addView(tv("B O O S T", 12f, cyan).apply { setTypeface(typeface, Typeface.BOLD_ITALIC) },
            FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = dp(14) })
        setContentView(root)
        rebuild()
        Thread { SysStats.useRoot = Shell.hasRoot() }.start()
    }

    // ---------- Bar atas ----------
    private fun topBar(): View {
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(6), dp(14), dp(6)) }
        fun gauge(name: String) = GaugeView(this, name)
        cpuG = gauge("CPU"); ramG = gauge("RAM").apply { arcColor = 0xFF37E86B.toInt() }
        romG = gauge("ROM").apply { arcColor = 0xFF37E86B.toInt() }; gpuG = gauge("GPU")
        tempTv = tv("🌡 --°C", 12f); battTv = tv("🔋 --%", 12f); netTv = tv("📶 --", 12f)
        tipTv = tv("Pilih game lalu tekan Main", 11f, 0xCCFFFFFF.toInt()).apply {
            isSingleLine = true; ellipsize = TextUtils.TruncateAt.MARQUEE; marqueeRepeatLimit = -1; isSelected = true
            gravity = Gravity.CENTER
            background = GradientDrawable().apply { cornerRadius = dpf(14f); setColor(0x33000000); setStroke(dp(1), 0x441EE6FF) }
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        fun lp(w: Int = dp(46)) = LinearLayout.LayoutParams(w, dp(46)).apply { rightMargin = dp(8) }
        bar.addView(cpuG, lp()); bar.addView(ramG, lp())
        for (t in listOf(tempTv, battTv, netTv)) bar.addView(t, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(10) })
        bar.addView(tipTv, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(10); leftMargin = dp(4) })
        bar.addView(romG, lp()); bar.addView(gpuG, lp())
        bar.addView(hexBtn("⚙") { showSettings() }, LinearLayout.LayoutParams(dp(42), dp(36)).apply { leftMargin = dp(8) })
        bar.addView(hexBtn("+") { showAdd() }, LinearLayout.LayoutParams(dp(42), dp(36)).apply { leftMargin = dp(8) })
        return bar
    }

    private fun hexBtn(t: String, click: () -> Unit) = tv(t, 18f, cyan).apply {
        gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dpf(8f); setColor(0x330A2A5A); setStroke(dp(1), cyan) }
        setOnClickListener { click() }
    }

    // ---------- Carousel ----------
    private fun games(): List<String> {
        val pm = packageManager
        return (prefs.getString("games", "") ?: "").split(",").filter { it.isNotBlank() && pm.getLaunchIntentForPackage(it) != null }
    }

    private fun rebuild() {
        val g = games()
        val cards = if (g.isEmpty()) listOf(gameCard(null)) else g.map { gameCard(it) }
        carousel.setCards(cards, prefs.getInt("sel", 0))
    }

    private fun gameCard(pkg: String?): View {
        val outer = FrameLayout(this).apply {
            setPadding(dp(5), dp(5), dp(5), dp(5))
            background = GradientDrawable().apply { cornerRadius = dpf(22f); setColor(0x221EE6FF); setStroke(dp(1), 0x551EE6FF) }
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; clipToOutline = true
            background = GradientDrawable().apply { cornerRadius = dpf(18f); setColor(0xFF0F2A55.toInt()); setStroke(dp(2), cyan) }
        }
        val banner = FrameLayout(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF2A6FD6.toInt(), 0xFF0F2A55.toInt()))
        }
        if (pkg == null) {
            banner.addView(tv("+", 54f, cyan).apply { gravity = Gravity.CENTER }, FrameLayout.LayoutParams(-1, -1))
        } else {
            banner.addView(ImageView(this).apply { setImageDrawable(packageManager.getApplicationIcon(pkg)) },
                FrameLayout.LayoutParams(dp(84), dp(84), Gravity.CENTER))
        }
        card.addView(banner, LinearLayout.LayoutParams(-1, 0, 1f))

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(0xFF1E73FF.toInt()); setPadding(dp(12), dp(8), dp(12), dp(8)) }
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val name = if (pkg == null) "Tambah game" else
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        col.addView(tv(name, 18f).apply { setTypeface(typeface, Typeface.BOLD); isSingleLine = true; ellipsize = TextUtils.TruncateAt.END })
        if (pkg != null) col.addView(tv("TIME " + fmtTime(prefs.getLong("t_$pkg", 0)), 11f, 0xDDFFFFFF.toInt()))
        row.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val playBtn = tv(if (pkg == null) "Pilih" else "Main", 15f, 0xFF06306E.toInt()).apply {
            gravity = Gravity.CENTER; setTypeface(typeface, Typeface.BOLD); setPadding(dp(18), dp(8), dp(18), dp(8))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0xFFD6FAFF.toInt(), 0xFF55D8FF.toInt())).apply { cornerRadius = dpf(10f) }
            setOnClickListener { if (isSelectedCard(outer)) { if (pkg == null) showAdd() else play(pkg) } else carousel.select(carousel.indexOfChild(outer)) }
        }
        row.addView(playBtn)
        bottom.addView(row)
        if (pkg != null) {
            val chips = LinearLayout(this).apply { setPadding(0, dp(6), 0, 0) }
            chips.addView(chip(pkg, "ram", "Bersihkan RAM", true, outer))
            chips.addView(chip(pkg, "perf", "Mode Kinerja", false, outer))
            bottom.addView(chips)
        }
        card.addView(bottom, LinearLayout.LayoutParams(-1, -2))
        outer.addView(card, FrameLayout.LayoutParams(-1, -1))
        return outer
    }

    private fun isSelectedCard(v: View) = carousel.indexOfChild(v) == carousel.selected

    private fun chip(pkg: String, key: String, text: String, def: Boolean, owner: View): View {
        val t = tv(text, 10f).apply { setPadding(dp(8), dp(3), dp(8), dp(3)) }
        fun style() {
            val on = prefs.getBoolean("${key}_$pkg", def)
            t.background = GradientDrawable().apply {
                cornerRadius = dpf(6f)
                if (on) { setColor(0xFF0B3F9E.toInt()); setStroke(dp(1), cyan) } else setColor(0x66000000)
            }
        }
        style()
        t.setOnClickListener {
            if (!isSelectedCard(owner)) { carousel.select(carousel.indexOfChild(owner)); return@setOnClickListener }
            prefs.edit().putBoolean("${key}_$pkg", !prefs.getBoolean("${key}_$pkg", def)).apply(); style()
            if (key == "perf" && !SysStats.useRoot) Toast.makeText(this, "Mode Kinerja hanya berefek di HP yang di-root", Toast.LENGTH_SHORT).show()
        }
        return t.also { it.layoutParams = LinearLayout.LayoutParams(-2, -2).apply { rightMargin = dp(6) } }
    }

    private fun fmtTime(ms: Long): String {
        val s = ms / 1000
        return when { s < 60 -> "${s}s"; s < 3600 -> "${s / 60}m"; else -> "${s / 3600}j ${(s % 3600) / 60}m" }
    }

    // ---------- Main game ----------
    private fun play(pkg: String) {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Izinkan tampil di atas app lain dulu", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))); return
        }
        val launch = packageManager.getLaunchIntentForPackage(pkg) ?: return
        if (!BoosterService.running) startForegroundService(Intent(this, BoosterService::class.java))
        val ram = prefs.getBoolean("ram_$pkg", true); val perf = prefs.getBoolean("perf_$pkg", false)
        prefs.edit().putString("launch_pkg", pkg).putLong("launch_ts", System.currentTimeMillis()).apply()
        Thread {
            if (ram) Boost.clean(this)
            if (perf) Boost.apply(2)
            runOnUiThread { startActivity(launch) }
        }.start()
    }

    // ---------- Dialog ----------
    private fun showAdd() {
        val pm = packageManager
        val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
        val cur = games()
        val checked = BooleanArray(apps.size) { apps[it].first in cur }
        AlertDialog.Builder(this).setTitle("Pilih game")
            .setMultiChoiceItems(apps.map { it.second }.toTypedArray(), checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Simpan") { _, _ ->
                val sel = apps.filterIndexed { i, _ -> checked[i] }.map { it.first }
                val ordered = cur.filter { it in sel } + sel.filter { it !in cur }
                prefs.edit().putString("games", ordered.joinToString(",")).apply()
                rebuild()
            }.setNegativeButton("Batal", null).show()
    }

    private fun showSettings() {
        val items = arrayOf(
            "Izin tampil di atas app lain " + if (Settings.canDrawOverlays(this)) "✔" else "✘",
            "Izin notifikasi",
            if (BoosterService.running) "Matikan bubble panel" else "Nyalakan bubble panel",
            "Status root: " + if (SysStats.useRoot) "ada" else "tidak terdeteksi"
        )
        AlertDialog.Builder(this).setTitle("Pengaturan").setItems(items) { _, i ->
            when (i) {
                0 -> startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                1 -> if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
                2 -> if (BoosterService.running) stopService(Intent(this, BoosterService::class.java))
                     else if (Settings.canDrawOverlays(this)) startForegroundService(Intent(this, BoosterService::class.java))
                     else Toast.makeText(this, "Izin overlay belum diberikan", Toast.LENGTH_SHORT).show()
            }
        }.show()
    }

    // ---------- Lifecycle & statistik ----------
    override fun onResume() {
        super.onResume()
        val lp = prefs.getString("launch_pkg", null); val ts = prefs.getLong("launch_ts", 0)
        if (lp != null && ts > 0) {
            val add = System.currentTimeMillis() - ts
            if (add in 1..43_200_000L) prefs.edit().putLong("t_$lp", prefs.getLong("t_$lp", 0) + add).apply()
            prefs.edit().remove("launch_pkg").remove("launch_ts").apply()
            rebuild()
        }
    }

    override fun onStart() {
        super.onStart()
        alive = true
        Thread {
            while (alive) { pollStats(); try { Thread.sleep(2000) } catch (e: InterruptedException) { break } }
        }.start()
    }

    override fun onStop() { alive = false; super.onStop() }

    @Suppress("DEPRECATION")
    override fun onWindowFocusChanged(f: Boolean) {
        super.onWindowFocusChanged(f)
        if (f) window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun pollStats() {
        val cpu = SysStats.cpu(); val gpu = SysStats.gpu()
        val mi = ActivityManager.MemoryInfo()
        (getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(mi)
        val ramUsed = ((mi.totalMem - mi.availMem) * 100 / mi.totalMem).toInt()
        val sf = StatFs(Environment.getDataDirectory().path)
        val romUsed = ((sf.totalBytes - sf.availableBytes) * 100 / sf.totalBytes).toInt()
        val bi = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val temp = (bi?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val lvl = bi?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val sc = (bi?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100).coerceAtLeast(1)
        val cm = getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Seluler"
            else -> "Online"
        }
        val tip = if (temp >= 42f) "Suhu tinggi, istirahatkan HP sebentar sebelum main."
                  else "Perangkat dalam kondisi baik. Pilih game lalu tekan Main."
        runOnUiThread {
            cpuG.value = cpu; gpuG.value = gpu
            ramG.value = ramUsed; ramG.centerText = String.format("%.1fG", mi.availMem / 1e9)
            romG.value = romUsed; romG.centerText = String.format("%.1fG", sf.availableBytes / 1e9)
            tempTv.text = String.format("🌡 %.1f°C", temp); battTv.text = "🔋 ${lvl * 100 / sc}%"
            netTv.text = "📶 $net"; tipTv.text = tip
        }
    }
}
