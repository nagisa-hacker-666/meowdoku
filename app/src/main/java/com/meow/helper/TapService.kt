package com.meow.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class TapService : AccessibilityService() {
    companion object { @Volatile var inst: TapService? = null }
    override fun onServiceConnected() { inst = this }
    override fun onUnbind(i: Intent?): Boolean { inst = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    /** Enchaîne plusieurs taps dans UN SEUL geste (10 max par geste), puis attend sa fin réelle. */
    fun tapBatch(pts: List<Pair<Float, Float>>): Boolean {
        var ok = true
        for (chunk in pts.chunked(GestureDescription.getMaxStrokeCount())) {
            val b = GestureDescription.Builder()
            chunk.forEachIndexed { i, p ->
                b.addStroke(GestureDescription.StrokeDescription(Path().apply { moveTo(p.first, p.second) }, i * 60L, 30L))
            }
            val latch = CountDownLatch(1); val done = AtomicBoolean(false)
            val sent = dispatchGesture(b.build(), object : GestureResultCallback() {
                override fun onCompleted(d: GestureDescription?) { done.set(true); latch.countDown() }
                override fun onCancelled(d: GestureDescription?) { latch.countDown() }
            }, null)
            if (!sent) { ok = false; continue }
            latch.await(chunk.size * 60L + 600L, TimeUnit.MILLISECONDS)
            if (!done.get()) ok = false
        }
        return ok
    }

    fun tap(x: Float, y: Float): Boolean = tapBatch(listOf(x to y))
}
