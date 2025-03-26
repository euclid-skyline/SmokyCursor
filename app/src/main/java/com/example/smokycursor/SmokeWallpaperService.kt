package com.example.smokycursor

import android.graphics.*
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.*
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

        // Add new smooth fade-out parameters
        private val touchDecayRate = 0.92f        // Normal decay when touching
        private val releaseDecayRate = 0.89f      // Accelerated decay when released
        private var fadeOutProgress = 1f          // 1.0 -> 0.0 during fade
        private val fadeOutDuration = 600L        // Fade duration in milliseconds
        private var fadeOutJob: Job? = null

        // Particle class
        private inner class Particle(
            var x: Float,           // Current X position
            var y: Float,           // Current Y position
            var radius: Float,      // Current particle size
            var velocityX: Float,   // Horizontal speed
            var velocityY: Float,   // Vertical speed
            var alpha: Int,         // Current opacity (Transparency) (0-255)
            var baseDecay: Float = touchDecayRate,  // Individual particle decay
            var floatForce: Float = (Math.random() * 0.5 - 0.25).toFloat() // Random upward force
        )

        // Animation Loop Management using continuous
        private fun startDrawing() {
            drawJob?.cancel()   // Cancel previous job if exists
            drawJob = launch {
                while (isVisible) {
                    drawFrame()               // Draw frame
                    delay(16)   // Maintain ~60 FPS
                }
            }
        }

        // Main drawing method
        private fun drawFrame() {
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
                    val speed = (Math.random() * 3.5 + 1.5).toFloat()           // Random speed
                    particles.add(Particle(
                        touchX,             // Start at touch position
                        touchY,
                        (Math.random() * 22 + 12).toFloat(), // Random size (12-34)
                        cos(angle) * speed,  // X velocity component
                        sin(angle) * speed,  // Y velocity component
                        255,                    // Start fully visible
                        baseDecay = touchDecayRate,
                        floatForce = (Math.random() * 0.6 - 0.3).toFloat()
                    ))
                }
            }

            // Update and draw particles
            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()

                // Apply smooth decay interpolation
                val decay = if (isTouching) {
                    p.baseDecay = touchDecayRate
                    touchDecayRate
                } else {
                    // Gradually increase decay rate over time
                    p.baseDecay = lerp(touchDecayRate, releaseDecayRate, 1 - fadeOutProgress)
                    p.baseDecay
                }


                // Add floating effect when released
                if (!isTouching) {
                    p.velocityY += p.floatForce
                    p.floatForce *= 0.95f
                }

                // Smooth alpha transition using easing function
                p.alpha = (p.alpha * easeOutQuad(decay)).toInt()

                // Radius decay with different curve
                p.radius *= easeInOutQuad(decay)

                p.velocityX *= decay
                p.velocityY *= decay


                // Update particle physics
                p.x += p.velocityX          // Apply velocity
                p.y += p.velocityY

                // Determine decay rate based on touch state
//                val decayRate = if (isTouching) 0.92f else 0.85f
//                p.alpha = (p.alpha * decayRate).toInt() // Fade out
//                p.radius *= decayRate                   // Shrink size
//                p.velocityX *= decayRate                // Slow down
//                p.velocityY *= decayRate

                // Remove old particles
                if (p.alpha < 4 || p.radius < 1.5f) {
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
                    cancelFade()  // Reset fade if touching again
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTouching = false      // Clear touch state
                    startFadeOutSequence()  // Begin smooth fade-out
                }
            }
            super.onTouchEvent(event)
        }

        private fun startFadeOutSequence() {
            fadeOutJob?.cancel()
            fadeOutProgress = 1f
            fadeOutJob = launch {
                val startTime = System.currentTimeMillis()
                while (fadeOutProgress > 0) {
                    val elapsed = System.currentTimeMillis() - startTime
                    fadeOutProgress = 1 - (elapsed.toFloat() / fadeOutDuration)
                    if (fadeOutProgress < 0) fadeOutProgress = 0f
                    delay(16)
                }
            }
        }

        private fun cancelFade() {
            fadeOutJob?.cancel()
            fadeOutProgress = 1f
        }

        // Easing Functions for Smooth Transitions
        private fun lerp(start: Float, end: Float, progress: Float) =
            start + (end - start) * progress

        private fun easeOutQuad(t: Float) =
            t * (2 - t)

        private fun easeInOutQuad(t: Float) =
            if (t < 0.5f) 2 * t * t else -1 + (4 - 2 * t) * t

//        private fun easeOutQuad(factor: Float) =
//            1 - (1 - factor) * (1 - factor)  // Slow -> Fast transition
//
//        private fun easeInOutQuad(factor: Float) =
//            if (factor < 0.5) {
//                2 * factor.pow(2)
//            }
//            else {  // Slow -> Fast -> Slow
//                val adjusted = 2f * factor - 1f
//                1f - adjusted.pow(2) / 2f
//            }

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