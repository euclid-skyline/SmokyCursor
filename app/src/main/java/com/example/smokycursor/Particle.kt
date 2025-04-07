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
    var floatForce: Float,
    var rotation: Float,
    var rotationSpeed: Float,

    var age: Long = 0L  // Particle's age in milliseconds
) {

    private var initialRadius: Float = radius  // Particle's initial radius size at creation time
    val sizeRatio: Float
        get() = (radius / initialRadius.coerceAtLeast(1f)).coerceIn(0.1f, 1f)
//    val sizeRatio: Float
//        get() = radius.coerceIn(0.1f, 1f)

    fun getCurrentColor(start: Int, end: Int, transitionDuration: Long): Int {
        val progress = age.toFloat().div(transitionDuration).coerceIn(0f, 1f)
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

    // Add reset functionality for pooling
    fun reset(
        x: Float, y: Float,
        radius: Float,
        velocityX: Float, velocityY: Float,
        baseDecay: Float,
        alpha: Int,
        floatForce: Float,
        rotation: Float,
        rotationSpeed: Float
    ) {
        this.x = x
        this.y = y
        this.radius = radius
        this.velocityX = velocityX
        this.velocityY = velocityY
        this.baseDecay = baseDecay
        this.alpha = alpha
        this.floatForce = floatForce
        this.rotation = rotation
        this.rotationSpeed = rotationSpeed
        this.age = 0L   // Reset the age counter
    }

    // Factory method to create a new Particle instance
    companion object {
        fun create(
            x: Float,
            y: Float,
            radius: Float,
            velocityX: Float,
            velocityY: Float,
            baseDecay: Float,
            alpha: Int,
            floatForce: Float,
            rotation: Float,
            rotationSpeed: Float
        ): Particle {
            return Particle(x, y, radius, velocityX, velocityY, baseDecay, alpha, floatForce, rotation, rotationSpeed)
        }
    }
}
