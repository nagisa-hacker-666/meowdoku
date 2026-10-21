package com.meow.helper

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.DisplayMetrics
import android.view.*
import android.widget.TextView
import android.widget.Toast

class CatView(c: Context, private val b: Board) : View(c) {
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = b.cw * 0.7f }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.parseColor("#E8743B") }
    override fun onDraw(cv: Canvas) {
        for (r in 0 until b.n) {
            val cx = b.left + (b.cols[r] + 0.5f) * b.cw; val cy = b.top + (r + 0.5f) * b.ch
            cv.drawCircle(cx, cy, b.cw * 0.42f, ring)
            cv.drawText("🐱", cx, cy + b.cw * 0.25f, txt)
        }
    }
}

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private val h = Handler(Looper.getMainLooper())
    private var proj: MediaProjection? = null
    private var reader: ImageReader? = null
    private var vd: VirtualDisplay? = null
    private var btn: TextView? = null
    private var cats: CatView? = null
    private var held: Image? = null
    private val lock = Any()
    @Volatile private var bot = false
    private var W = 0; private var H = 0
    // points tapés pour « continuer » entre deux parties (fraction de l'écran)
    private val cont = listOf(0.5f to 0.78f, 0.5f to 0.88f, 0.5f to 0.95f) // Niveau N / Recommencer / Touche pour continuer

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("m", "Meowdoku", NotificationManager.IMPORTANCE_LOW))
        val n = Notification.Builder(this, "m").setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("Meowdoku Helper actif").build()
        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION) else startForeground(1, n)

        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val dm = DisplayMetrics(); @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(dm)
        W = dm.widthPixels; H = dm.heightPixels
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val data = if (Build.VERSION.SDK_INT >= 33) i!!.getParcelableExtra("data", Intent::class.java)!! else @Suppress("DEPRECATION") i!!.getParcelableExtra<Intent>("data")!!
        proj = mpm.getMediaProjection(i.getIntExtra("code", 0), data).also { p ->
            p.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, h)
            reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 3)
            vd = p.createVirtualDisplay("meow", W, H, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, h)
        }
        reader!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            synchronized(lock) { held?.close(); held = img }
        }, h)
        addButton()
        return START_NOT_STICKY
    }

    private fun grab(): Bitmap? = synchronized(lock) {
        val img = held ?: return null
        val p = img.planes[0]; p.buffer.rewind()
        val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, H, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(p.buffer)
        Bitmap.createBitmap(full, 0, 0, W, H)
    }

    private fun fullParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
    }

    private fun addButton() {
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 60 }
        val v = TextView(this).apply {
            text = "🐱"; textSize = 28f; setPadding(24, 12, 24, 12)
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#CCFFFFFF")); cornerRadius = 100f }
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false; var long = false
        val lr = Runnable { if (!moved) { long = true; toggleBot() } }
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false; long = false; h.postDelayed(lr, 700) }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - sx) + Math.abs(e.rawY - sy) > 20) { moved = true; h.removeCallbacks(lr) }
                    if (moved) { lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); wm.updateViewLayout(v, lp) }
                }
                MotionEvent.ACTION_UP -> { h.removeCallbacks(lr); if (!moved && !long) scan() }
            }
            true
        }
        btn = v; wm.addView(v, lp)
    }

    private fun scan() {
        if (bot) { bot = false; return }
        cats?.let { wm.removeView(it); cats = null; return }
        btn?.visibility = View.INVISIBLE
        h.postDelayed({
            Thread {
                val board = grab()?.let { Solver.detect(it) }
                h.post {
                    btn?.visibility = View.VISIBLE
                    if (board == null) Toast.makeText(this, "Grille non détectée 😿", Toast.LENGTH_SHORT).show()
                    else cats = CatView(this, board).also { wm.addView(it, fullParams()) }
                }
            }.start()
        }, 300)
    }

    private fun toggleBot() {
        if (bot) { bot = false; return }
        if (TapService.inst == null) { Toast.makeText(this, "Active d’abord « Meowdoku Helper » dans Accessibilité", Toast.LENGTH_LONG).show(); return }
        cats?.let { wm.removeView(it); cats = null }
        bot = true; btn?.text = "🤖"
        Thread { runBot() }.start()
    }

    private fun say(t: String) = h.post { Toast.makeText(this, t, Toast.LENGTH_SHORT).show() }

    // deux détections à 350 ms d'intervalle doivent donner exactement le même résultat
    private fun stable(): Board? {
        val a = grab()?.let { Solver.detect(it) } ?: return null
        Thread.sleep(350)
        val b = grab()?.let { Solver.detect(it) } ?: return null
        return if (a.n == b.n && a.cols.contentEquals(b.cols)) b else null
    }

    private fun runBot() {
        say("Bot démarré 🤖")
        var boardIdle = 0; var miss = 0; var k = 0; var fails = 0; var lightMiss = 0
        while (bot) {
            val svc = TapService.inst
            if (svc == null) { say("Accessibilité coupée : réactive Meowdoku Helper"); break }
            val board = stable()
            if (board != null) {
                miss = 0
                var acted = false
                if (board.placed.any { !it }) say("Grille ${board.n}×${board.n} : je place ${board.placed.count { !it }} chat(s)")
                for (r in 0 until board.n) {
                    if (!bot) break
                    if (board.placed[r]) continue
                    if (!svc.doubleTap(board.left + (board.cols[r] + 0.5f) * board.cw, board.top + (r + 0.5f) * board.ch)) fails++
                    acted = true; Thread.sleep(600)
                }
                if (acted) { boardIdle = 0; Thread.sleep(1500) }
                else {
                    Thread.sleep(1500)
                    // niveau fini mais la grille reste affichée longtemps : on tente de continuer
                    if (++boardIdle >= 8) { boardIdle = 0; svc.tap(0.5f * W, 0.78f * H) }
                }
            } else {
                // pas de grille : on ne tape que si l'écran est assombri par un popup
                val dark = grab()?.let { Solver.isDark(it) } == true
                if (dark) {
                    lightMiss = 0
                    if (++miss >= 2) { val p = cont[k++ % cont.size]; if (!svc.tap(p.first * W, p.second * H)) fails++; Thread.sleep(1500) }
                    else Thread.sleep(1000)
                } else {
                    miss = 0
                    if (++lightMiss == 20) say("Écran inconnu : je ne touche à rien")
                    Thread.sleep(1000)
                }
            }
            if (fails >= 3) { say("Android refuse les taps : vérifie l’accessibilité"); break }
        }
        bot = false
        h.post { btn?.text = "🐱" }
    }

    override fun onDestroy() {
        bot = false
        cats?.let { wm.removeView(it) }; btn?.let { wm.removeView(it) }
        synchronized(lock) { held?.close(); held = null }
        vd?.release(); reader?.close(); proj?.stop()
        super.onDestroy()
    }
}
