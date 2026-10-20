package com.meow.helper

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48); gravity = Gravity.CENTER_HORIZONTAL }
        l.addView(TextView(this).apply {
            text = "🐱 Meowdoku Helper\n\n1. Autorise l’affichage par-dessus les autres apps.\n2. Démarre l’assistant (autorise la capture d’écran).\n3. Ouvre Meowdoku et touche la bulle 🐱 : les chats apparaissent sur la grille. Retouche la bulle pour les effacer."
            textSize = 16f
        })
        fun btn(t: String, f: () -> Unit) = l.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        btn("1. Autoriser l’overlay") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2. Démarrer l’assistant") {
            if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Autorise d’abord l’overlay", Toast.LENGTH_LONG).show(); return@btn }
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
            val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(m.createScreenCaptureIntent(), 42)
        }
        btn("Arrêter") { startService(Intent(this, OverlayService::class.java).setAction("STOP")) }
        setContentView(l)
    }

    @Deprecated("simple")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 42 && res == RESULT_OK && data != null) {
            val i = Intent(this, OverlayService::class.java).putExtra("code", res).putExtra("data", data)
            startForegroundService(i)
            moveTaskToBack(true)
        }
    }
}
