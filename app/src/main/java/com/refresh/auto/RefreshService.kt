package com.refresh.auto

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.util.Log

class RefreshService : AccessibilityService() {
    
    companion object {
        var isRunning = false
        var intervalSeconds = 60
    }
    
    private val handler = Handler(Looper.getMainLooper())
    private val TAG = "RefreshService"
    
    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                swipeDown()
                Log.d(TAG, "Swipe executed")
            }
            handler.postDelayed(this, intervalSeconds * 1000L)
        }
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Service connected")
        handler.postDelayed(refreshRunnable, 5000)
    }
    
    private fun swipeDown() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            val centerX = screenWidth / 2f
            val startY = screenHeight * 0.3f
            val endY = screenHeight * 0.7f
            
            val path = Path()
            path.moveTo(centerX, startY)
            path.lineTo(centerX, endY)
            
            val gesture = GestureDescription.Builder()
                .addStroke(GestureDescription.StrokeDescription(path, 0, 400))
                .build()
            
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Swipe completed")
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.d(TAG, "Swipe cancelled")
                }
            }, null)
        }
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    
    override fun onInterrupt() {}
    
    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(refreshRunnable)
    }
}
