package com.meow.helper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class TapService : AccessibilityService() {
    companion object { @Volatile var inst: TapService? = null }
    override fun onServiceConnected() { inst = this }
    override fun onUnbind(i: Intent?): Boolean { inst = null; return super.onUnbind(i) }
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun stroke(x: Float, y: Float, start: Long) =
        GestureDescription.StrokeDescription(Path().apply { moveTo(x, y) }, start, 50)

    fun tap(x: Float, y: Float): Boolean {
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke(x, y, 0)).build(), null, null)
    }
    fun doubleTap(x: Float, y: Float): Boolean {
        return dispatchGesture(GestureDescription.Builder().addStroke(stroke(x, y, 0)).addStroke(stroke(x, y, 130)).build(), null, null)
    }
}
