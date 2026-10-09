package com.boost.panel

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

class CaptureActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), 1)
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        if (res == RESULT_OK && data != null) {
            startForegroundService(
                Intent(this, CaptureService::class.java)
                    .putExtra("code", res).putExtra("data", data)
                    .putExtra("mode", intent.getStringExtra("mode"))
            )
        }
        finish()
    }
}
