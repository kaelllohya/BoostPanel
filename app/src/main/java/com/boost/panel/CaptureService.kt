package com.boost.panel

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.view.WindowManager
import android.widget.Toast

class CaptureService : Service() {
    companion object {
        @Volatile var recording = false
        const val ACT_STOP = "stop"
    }

    private var projection: MediaProjection? = null
    private var recorder: MediaRecorder? = null
    private var vd: VirtualDisplay? = null
    private var pfd: ParcelFileDescriptor? = null
    private var stopped = false
    private val ui = Handler(Looper.getMainLooper())

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == ACT_STOP) { stopRecord(); return START_NOT_STICKY }
        Notif.startFg(this, 2, "Perekaman / tangkapan layar", mediaProjection = true)
        val data = i?.getParcelableExtra<Intent>("data")
        if (data == null) { stopSelf(); return START_NOT_STICKY }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        projection = mpm.getMediaProjection(i.getIntExtra("code", 0), data)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stopRecord() }
        }, ui)
        if (i.getStringExtra("mode") == "shot") shot() else record()
        return START_NOT_STICKY
    }

    private fun size(): Triple<Int, Int, Int> {
        val dm = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(dm)
        return Triple(dm.widthPixels and 1.inv(), dm.heightPixels and 1.inv(), dm.densityDpi)
    }

    private fun shot() {
        val (w, h, dpi) = size()
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        val display = projection!!.createVirtualDisplay("shot", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
        ui.postDelayed({
            val img = reader.acquireLatestImage()
            if (img != null) {
                val p = img.planes[0]
                val rowPad = p.rowStride - p.pixelStride * w
                val full = Bitmap.createBitmap(w + rowPad / p.pixelStride, h, Bitmap.Config.ARGB_8888)
                full.copyPixelsFromBuffer(p.buffer); img.close()
                val bmp = Bitmap.createBitmap(full, 0, 0, w, h)
                val cv = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "Boost_${System.currentTimeMillis()}.png")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BoostPanel")
                }
                val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                uri?.let { u -> contentResolver.openOutputStream(u)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                toast("Tangkapan layar tersimpan di Pictures/BoostPanel")
            }
            display.release(); reader.close(); projection?.stop(); stopSelf()
        }, 400)
    }

    private fun record() {
        val (w, h, dpi) = size()
        val cv = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "Boost_${System.currentTimeMillis()}.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BoostPanel")
        }
        val uri: Uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, cv) ?: run { stopSelf(); return }
        pfd = contentResolver.openFileDescriptor(uri, "w")
        @Suppress("DEPRECATION")
        recorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(w, h); setVideoFrameRate(30); setVideoEncodingBitRate(8_000_000)
            setOutputFile(pfd!!.fileDescriptor)
            prepare()
        }
        vd = projection!!.createVirtualDisplay("rec", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, recorder!!.surface, null, null)
        recorder!!.start()
        recording = true
        toast("Merekam layar... buka panel lalu tekan Stop rekam")
    }

    private fun stopRecord() {
        if (stopped) return
        stopped = true
        val wasRecording = recording
        recording = false
        runCatching { if (wasRecording) recorder?.stop() }
        runCatching { recorder?.release() }
        runCatching { vd?.release() }
        runCatching { pfd?.close() }
        runCatching { projection?.stop() }
        if (wasRecording) toast("Rekaman tersimpan di Movies/BoostPanel")
        stopSelf()
    }

    private fun toast(t: String) = ui.post { Toast.makeText(applicationContext, t, Toast.LENGTH_LONG).show() }

    override fun onDestroy() { if (recording) stopRecord(); super.onDestroy() }
}
