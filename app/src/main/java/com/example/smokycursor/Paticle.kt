package com.example.smokycursor

import android.graphics.Color


// =====================================================================
// Particle Class Definition
// =====================================================================

class Particle(
    // Position properties
    var x: Float,        // Current X position
    var y: Float,        // Current Y position

    // Size properties
    var radius: Float,   // Current radius

    // Movement properties
    var velocityX: Float,    // Horizontal velocity
    var velocityY: Float,    // Vertical velocity
    var baseDecay: Float,    // Current decay rate

    // Visual properties
    var alpha: Int,          // Opacity (0-255)

    // Special effects
    val floatForce: Float,
    var rotation: Float,
    var rotationSpeed: Float
) {
    private val initialRadius: Float = radius  // Original size
    // Lifecycle tracking
    private val creationTime: Long = System.currentTimeMillis()

    // Calculated properties
    val age: Long get() = System.currentTimeMillis() - creationTime

    val sizeRatio: Float
        get() = (radius / initialRadius.coerceAtLeast(1f)).coerceIn(0.1f, 1f)

    fun getCurrentColor(start: Int, end: Int, transitionDuration: Long): Int {
        val progress = (age / transitionDuration.toFloat()).coerceIn(0f, 1f)
        return lerpColor(start, end, progress)
    }

    private fun lerpColor(start: Int, end: Int, factor: Float): Int {
        return Color.argb(
            (Color.alpha(start) + (Color.alpha(end) - Color.alpha(start)) * factor).toInt(),
            (Color.red(start) + (Color.red(end) - Color.red(start)) * factor).toInt(),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * factor).toInt(),
            (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * factor).toInt()
        )
    }
}
