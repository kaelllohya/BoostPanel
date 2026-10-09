package com.boost.panel

import android.app.*
import android.content.Context
import android.content.pm.ServiceInfo
import java.io.File

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

object Shell {
    fun run(cmd: String, root: Boolean = false): String = try {
        val p = ProcessBuilder(if (root) listOf("su", "-c", cmd) else listOf("sh", "-c", cmd))
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().readText()
        p.waitFor()
        out
    } catch (e: Exception) { "" }

    fun hasRoot(): Boolean = run("id", true).contains("uid=0")
}

object SysStats {
    @Volatile var useRoot = false

    private fun readStr(path: String): String? = try {
        File(path).readText().trim()
    } catch (e: Exception) {
        if (useRoot) Shell.run("cat $path", true).trim().ifEmpty { null } else null
    }

    private fun readLong(path: String): Long? = readStr(path)?.toLongOrNull()

    /** Proxy beban CPU: rata-rata (freq sekarang / freq maks) semua core. */
    fun cpu(): Int {
        var sum = 0.0; var n = 0
        for (i in 0 until Runtime.getRuntime().availableProcessors()) {
            val base = "/sys/devices/system/cpu/cpu$i/cpufreq/"
            val cur = readLong(base + "scaling_cur_freq")
            val max = readLong(base + "cpuinfo_max_freq")
            if (cur != null && max != null && max > 0) { sum += cur.toDouble() / max; n++ }
        }
        return if (n == 0) -1 else (sum / n * 100).toInt().coerceIn(0, 100)
    }

    fun gpu(): Int {
        val direct = listOf(
            "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",   // Qualcomm
            "/sys/kernel/ged/hal/gpu_utilization",            // MediaTek
            "/sys/kernel/gpu/gpu_busy"
        )
        for (f in direct) {
            val v = readStr(f)?.split(Regex("\\s+"))?.firstOrNull()?.filter { it.isDigit() }?.toIntOrNull()
            if (v != null) return v.coerceIn(0, 100)
        }
        val cur = readLong("/sys/class/kgsl/kgsl-3d0/devfreq/cur_freq")
        val max = readLong("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq")
        if (cur != null && max != null && max > 0) return (cur * 100 / max).toInt().coerceIn(0, 100)
        val dirs = File("/sys/class/devfreq").listFiles()
            ?.filter { it.name.contains("mali") || it.name.contains("gpu") } ?: emptyList()
        for (d in dirs) {
            val c = readLong("${d.path}/cur_freq"); val m = readLong("${d.path}/max_freq")
            if (c != null && m != null && m > 0) return (c * 100 / m).toInt().coerceIn(0, 100)
        }
        return -1
    }
}

object Boost {
    private val gov = arrayOf("powersave", "schedutil", "performance")

    /** Mode CPU: efektif hanya jika root. Tanpa root, mode hanya disimpan sebagai preferensi. */
    fun apply(mode: Int) {
        if (!SysStats.useRoot) return
        Shell.run("for p in /sys/devices/system/cpu/cpu[0-9]*/cpufreq; do echo ${gov[mode]} > \$p/scaling_governor; done", true)
        Shell.run("cmd power set-fixed-performance-mode-enabled ${mode == 2}", true)
    }

    /** Bersihkan RAM; return perkiraan MB yang dibebaskan. */
    fun clean(ctx: Context): Long {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi); val before = mi.availMem
        ctx.packageManager.getInstalledApplications(0)
            .filter { it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM == 0 && it.packageName != ctx.packageName }
            .forEach { am.killBackgroundProcesses(it.packageName) }
        if (SysStats.useRoot) Shell.run("sync; echo 3 > /proc/sys/vm/drop_caches", true)
        Thread.sleep(600)
        am.getMemoryInfo(mi)
        return ((mi.availMem - before) / 1048576).coerceAtLeast(0)
    }
}

object Notif {
    const val CH = "boost"
    fun build(ctx: Context, text: String): Notification {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "Boost Panel", NotificationManager.IMPORTANCE_LOW))
        return Notification.Builder(ctx, CH).setContentTitle("Boost Panel").setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging).setOngoing(true).build()
    }
    fun startFg(s: Service, id: Int, text: String, mediaProjection: Boolean = false) {
        val n = build(s, text)
        if (mediaProjection) s.startForeground(id, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        else s.startForeground(id, n)
    }
}
