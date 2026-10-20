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
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 96, 48, 48); gravity = Gravity.CENTER_HORIZONTAL }
        l.addView(TextView(this).apply {
            text = "🐱 Meowdoku Helper\n\n1. Autorise l’overlay.\n2. Démarre l’assistant (capture d’écran).\n3. Pour le bot : active « Meowdoku Helper » dans Accessibilité.\n\nDans Meowdoku : touche la bulle 🐱 = affiche les chats. Appui long sur la bulle = bot 🤖 qui joue tout seul (retouche la bulle pour l’arrêter)."
            textSize = 16f
        })
        fun btn(t: String, f: () -> Unit) = l.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        btn("1. Autoriser l’overlay") { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        btn("2. Démarrer l’assistant") {
            if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Autorise d’abord l’overlay", Toast.LENGTH_LONG).show(); return@btn }
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
            val m = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(m.createScreenCaptureIntent(), 42)
        }
        btn("3. Activer le bot (accessibilité)") { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        btn("Arrêter") { startService(Intent(this, OverlayService::class.java).setAction("STOP")) }
        setContentView(ScrollView(this).apply { addView(l) })
    }

    @Deprecated("simple")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        if (req == 42 && res == RESULT_OK && data != null) {
            startForegroundService(Intent(this, OverlayService::class.java).putExtra("code", res).putExtra("data", data))
            moveTaskToBack(true)
        }
    }
}
