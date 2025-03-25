package com.example.smokycursor

import android.graphics.*
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class SmokeWallpaperService : WallpaperService() {
    // Main entry point for the wallpaper engine
    override fun onCreateEngine(): Engine = SmokeEngine()

    inner class SmokeEngine : Engine(), CoroutineScope {
        // Coroutine configuration for animation loop
        private val job = SupervisorJob()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main + job

        // Particle system properties
        private val particles = mutableListOf<Particle>()
        private val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Gradient colors for smoke effect
        // (semi-transparent grey to full transparency)
//        private val colors = intArrayOf(
//            Color.argb(80, 100, 100, 100),  // Inner color
//            Color.argb(0, 100, 100, 100)    // Outer color
//        )
        // Orange smoke
        private val colors = intArrayOf(
            Color.argb(80, 255, 150, 100),
            Color.argb(0, 255, 150, 100)
        )
        // Touch tracking variables
        private var touchX = 0f
        private var touchY = 0f
        private var isVisible = false       // Wallpaper visibility state
        private var isTouching = false      // Current touch state
        private var drawJob: Job? = null    // Animation coroutine reference

        // Particle data class
        private inner class Particle(
            var x: Float,           // X position
            var y: Float,           // Y position
            var radius: Float,      // Current size
            var velocityX: Float,   // Horizontal speed
            var velocityY: Float,   // Vertical speed
            var alpha: Int          // Transparency (0-255)
        )

        // Starts the continuous drawing loop
        private fun startDrawing() {
            drawJob?.cancel()   // Cancel previous job if exists
            drawJob = launch {
                while (isVisible) {
                    draw()               // Draw frame
                    delay(16)   // Maintain ~60 FPS
                }
            }
        }

        // Main drawing method
        private fun draw() {
            val holder = surfaceHolder
            var canvas: Canvas? = null
            try {
                canvas = holder.lockCanvas()
                canvas?.let { drawSmoke(it) }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        // Handles particle system updates and rendering
        private fun drawSmoke(canvas: Canvas) {
            // Clear canvas with black background
            canvas.drawColor(Color.BLACK)

            // Generate new particles only while touching
            if (isTouching) {   // Creates 3 particles per frame
                repeat(3) {
                    val angle = (Math.random() * 2 * Math.PI).toFloat()     // Random direction
                    val speed = (Math.random() * 3 + 1).toFloat()           // Random speed
                    particles.add(Particle(
                        touchX,             // Start at touch position
                        touchY,
                        (Math.random() * 20 + 10).toFloat(), // Random size (10-30)
                        cos(angle) * speed,  // X velocity component
                        sin(angle) * speed,  // Y velocity component
                        255                     // Start fully visible
                    ))
                }
            }

            // Update and draw particles
            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()

                // Update particle physics
                p.x += p.velocityX          // Apply velocity
                p.y += p.velocityY

                // Determine decay rate based on touch state
                val decayRate = if (isTouching) 0.92f else 0.85f
                p.alpha = (p.alpha * decayRate).toInt() // Fade out
                p.radius *= decayRate                   // Shrink size
                p.velocityX *= decayRate                // Slow down
                p.velocityY *= decayRate

                // Remove old particles
                if (p.alpha < 5 || p.radius < 2) {
                    iterator.remove()
                } else {
                    paint.shader = RadialGradient(
                        p.x, p.y, p.radius,  // Center and size
                        colors,              // Color gradient
                        null,          // No position array
                        Shader.TileMode.CLAMP
                    )
                    paint.alpha = p.alpha    // Set transparency
                    canvas.drawCircle(p.x, p.y, p.radius, paint)
                }
            }
        }

        // Handles touch events
        override fun onTouchEvent(event: MotionEvent) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchX = event.x        // Update touch position
                    touchY = event.y
                    isTouching = true       // Set touch state
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTouching = false      // Clear touch state
                }
            }
            super.onTouchEvent(event)
        }

        // Handles visibility changes
        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                startDrawing()   // Start animation when visible
            } else {
                drawJob?.cancel() // Stop animation
                particles.clear() // Clear particles
            }
        }

        // Cleanup when surface is destroyed
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            drawJob?.cancel()
            super.onSurfaceDestroyed(holder)
        }

        // Final cleanup
        override fun onDestroy() {
            job.cancel() // Cancel all coroutines
            super.onDestroy()
        }
    }
}