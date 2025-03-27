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

        // Touch tracking and animation states variables
        private var touchX = 0f
        private var touchY = 0f
        private var isVisible = false       // Wallpaper visibility state
        private var isTouching = false      // Current touch state
        private var drawJob: Job? = null    // Animation coroutine reference

        // Smooth fade-out/decay parameters
        private val touchDecay = 0.92f        // Normal decay when touching
        private val releaseDecay = 0.89f      // Accelerated decay when released
        private var fadeOutProgress = 1f          // 1.0 -> 0.0 during fade
        private val fadeOutDuration = 600L        // Fade duration in milliseconds
        private var fadeOutJob: Job? = null       // Fade-out coroutine reference

        // Environmental Parameters
        private val airResistance = 0.97f
        private val noiseStrength = 0.15f

        // Particle class
        private inner class Particle(
            // Position Properties
            var x: Float,        // Current X coordinate on screen
            var y: Float,        // Current Y coordinate on screen
            // Size Properties
            var radius: Float,   // Current particle radius
            val initialRadius: Float = radius,  // Original size at creation
            // Motion Properties
            var velocityX: Float,    // Horizontal movement speed (pixels/frame)
            var velocityY: Float,    // Vertical movement speed (pixels/frame)
            // Visual Properties
            var alpha: Int,          // Opacity (0-255)
            var baseDecay: Float = touchDecay,  // Individual decay rate
            // Special Effects Properties
            val floatForce: Float = (Math.random() * 0.6 - 0.3).toFloat(), // Vertical drift (-0.3 to +0.3)
            var rotation: Float = (Math.random() * 360).toFloat(),
            var rotationSpeed: Float = (Math.random() * 4 - 2).toFloat()
        ) {
            val sizeRatio: Float get() = radius / initialRadius.coerceAtLeast(1f)

//            var lifeTime: Long = System.currentTimeMillis()
//            val age: Long get() = System.currentTimeMillis() - lifeTime
//
//            // Track decay progression for this particle
//            var decayProgress: Float = 1f
//
//            fun updateDecay(targetDecay: Float, delta: Float) {
//                baseDecay += (targetDecay - baseDecay) * delta
//                decayProgress = 1 - (baseDecay - touchDecay) / (releaseDecay - touchDecay)
//            }
        }

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
                canvas?.let { updateAndDrawParticles(it) }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }
        }

        // Handles particle system updates and rendering
        private fun updateAndDrawParticles(canvas: Canvas) {
            // Clear canvas with black background
            canvas.drawColor(Color.BLACK)

            // Generate new particles while touching
            if (isTouching) {   // Creates 3 particles per frame
                repeat(3) {
                    val angle = (Math.random() * 2 * Math.PI).toFloat()     // Random direction
                    val speed = (Math.random() * 3.5 + 1.5).toFloat()           // Random speed
                    particles.add(Particle(
                        x = touchX,             // Start at touch position
                        y = touchY,
                        radius = (Math.random() * 22 + 12).toFloat(), // Random size (12-34)
                        velocityX = cos(angle) * speed,  // X velocity component
                        velocityY = sin(angle) * speed,  // Y velocity component
                        alpha = 255,                    // Start fully visible
                    ))
                }
            }

            // Get current time for noise calculation
            val currentTime = System.currentTimeMillis()

            // Update and draw particles
            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()

                // 1. Update baseDecay for this particle
                p.baseDecay = if (isTouching) {
                    touchDecay
                } else {  // Apply smooth decay interpolation
                    lerp(touchDecay, releaseDecay, 1 - fadeOutProgress)
                }

                // 2. Apply environmental effects
                // 2.a Air Resistance
                p.velocityX *= airResistance
                p.velocityY *= airResistance
                // 2.b Add noise-based movement
                val (noiseX, noiseY) = getNoiseInfluence(p.x, p.y, currentTime)
                p.velocityX += noiseX
                p.velocityY += noiseY

                // 3. Update position with velocity and Vertical float force  and rotation
                p.x += p.velocityX
                p.y += p.velocityY - p.floatForce
                p.rotation += p.rotationSpeed

                // 4. Apply decay with easing functions using particle's baseDecay
                p.alpha = (p.alpha * easeOutQuad(p.baseDecay)).toInt()
                p.radius *= easeInOutQuad(p.baseDecay)
                p.velocityX *= p.baseDecay
                p.velocityY *= p.baseDecay

                // 5. Size-based opacity
                p.alpha = (p.alpha * p.sizeRatio).toInt()

                // 6. Remove expired particles
                if (p.alpha < 4 || p.radius < 1.5f) {
                    iterator.remove()
                } else {
                    // 9. Draw particle with rotation
                    paint.shader = RadialGradient(
                        p.x, p.y, p.radius,  // Center and size
                        colors,              // Color gradient
                        null,          // No position array
                        Shader.TileMode.CLAMP
                    )
                    paint.alpha = p.alpha    // Set transparency
                    canvas.run {
                        save()
                        rotate(p.rotation, p.x, p.y)
                        drawCircle(p.x, p.y, p.radius, paint)
                        restore()
                    }
                }
            }
        }

        private fun getNoiseInfluence(x: Float, y: Float, time: Long): Pair<Float, Float> {
            val phase = (time % 60000) / 1000f * PI.toFloat()
            return Pair(
                cos(x * 0.01f + y * 0.02f + phase) * noiseStrength,
                sin(x * 0.02f - y * 0.01f + phase) * noiseStrength
            )
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