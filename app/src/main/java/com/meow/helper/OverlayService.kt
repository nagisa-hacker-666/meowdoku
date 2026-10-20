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
    @Volatile private var wanted = false
    private var W = 0; private var H = 0

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
            reader = ImageReader.newInstance(W, H, PixelFormat.RGBA_8888, 2)
            vd = p.createVirtualDisplay("meow", W, H, dm.densityDpi, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader!!.surface, null, h)
        }
        reader!!.setOnImageAvailableListener({ r ->
            val img = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try { if (wanted) { wanted = false; val bmp = toBitmap(img); Thread { process(bmp) }.start() } } finally { img.close() }
        }, h)
        addButton()
        return START_NOT_STICKY
    }

    private fun toBitmap(img: Image): Bitmap {
        val p = img.planes[0]
        val full = Bitmap.createBitmap(p.rowStride / p.pixelStride, H, Bitmap.Config.ARGB_8888)
        full.copyPixelsFromBuffer(p.buffer)
        return Bitmap.createBitmap(full, 0, 0, W, H)
    }

    private fun process(bmp: Bitmap) {
        val board = Solver.detect(bmp)
        h.post {
            btn?.visibility = View.VISIBLE
            if (board == null) { Toast.makeText(this, "Grille non détectée 😿", Toast.LENGTH_SHORT).show(); return@post }
            cats = CatView(this, board).also { wm.addView(it, fullParams()) }
        }
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
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 300 }
        val v = TextView(this).apply {
            text = "🐱"; textSize = 28f; setPadding(24, 12, 24, 12)
            background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.parseColor("#CCFFFFFF")); cornerRadius = 100f }
        }
        var sx = 0f; var sy = 0f; var ox = 0; var oy = 0; var moved = false
        v.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; ox = lp.x; oy = lp.y; moved = false }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX - sx) + Math.abs(e.rawY - sy) > 20) moved = true
                    if (moved) { lp.x = ox + (e.rawX - sx).toInt(); lp.y = oy + (e.rawY - sy).toInt(); wm.updateViewLayout(v, lp) }
                }
                MotionEvent.ACTION_UP -> if (!moved) scan()
            }
            true
        }
        btn = v; wm.addView(v, lp)
    }

    private fun scan() {
        cats?.let { wm.removeView(it); cats = null; return }
        btn?.visibility = View.INVISIBLE
        h.postDelayed({ wanted = true }, 250)
        h.postDelayed({ if (wanted) { wanted = false; btn?.visibility = View.VISIBLE; Toast.makeText(this, "Pas de capture, réessaie", Toast.LENGTH_SHORT).show() } }, 2500)
    }

    override fun onDestroy() {
        cats?.let { wm.removeView(it) }; btn?.let { wm.removeView(it) }
        vd?.release(); reader?.close(); proj?.stop()
        super.onDestroy()
    }
}
