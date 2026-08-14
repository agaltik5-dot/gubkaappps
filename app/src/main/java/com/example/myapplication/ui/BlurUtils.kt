package com.example.myapplication.ui

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View

object BlurUtils {
    /**
     * Applies dynamic blur based on view position.
     */
    fun updateViewBlur(view: View, thresholdY: Float, maxBlurRadius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val location = IntArray(2)
        view.getLocationInWindow(location)
        val viewTop = location[1].toFloat()
        val viewHeight = view.height.toFloat()

        // We calculate blur based on how much of the view is above thresholdY
        val distanceIntoZone = thresholdY - viewTop
        
        if (distanceIntoZone <= 0) {
            view.setRenderEffect(null)
            view.alpha = 1.0f
            return
        }

        // Radius reaches max after moving 200px into the zone
        val blurProgress = (distanceIntoZone / 250f).coerceIn(0f, 1f)
        val radius = blurProgress * maxBlurRadius

        // Alpha also decreases slightly to "melt" into the background
        val alphaProgress = (distanceIntoZone / 400f).coerceIn(0f, 0.7f)
        view.alpha = 1.0f - alphaProgress

        if (radius > 1f) {
            val blurEffect = RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
            view.setRenderEffect(blurEffect)
        } else {
            view.setRenderEffect(null)
        }
    }
}
