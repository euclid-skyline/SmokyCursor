package com.example.smokycursor

import android.graphics.*
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

class SmokeWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SmokeEngine()

    inner class SmokeEngine : Engine(), CoroutineScope {
        // =====================================================================
        // Wallpaper Configuration Parameters
        // =====================================================================

        // Coroutine management
        private val job = SupervisorJob()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main + job

        // Particle system configuration
        private val particles = mutableListOf<Particle>()
        private var isVisible = false
        private var drawJob: Job? = null

        // Touch state tracking
        private var touchX = 0f
        private var touchY = 0f
        private var isTouching = false

        // Physics parameters
        private val airResistance = 0.95f        // Air friction coefficient
        private val noiseStrength = 0.25f        // Strength of random movement
        private val baseFloatForce = 0.3f        // Base upward/downward drift
        private val baseRotationSpeed = 2.2f     // Degrees per frame

        // Decay parameters
        private val touchDecay = 0.94f           // Decay rate while touching
        private val releaseDecay = 1.1f          // Decay rate after release
        private val fadeOutDuration = 5000L      // Fade-out duration in ms
        private var fadeOutProgress = 1f         // The progress of the fade-out animation, where 1.0f is fully visible and 0.0f is completely faded-out
        private var fadeOutJob: Job? = null      // Fade-out coroutine reference

        // Color transition parameters
        private val colorTransitionDuration = 3000L     // Orange → Blue transition time in ms
        private val startColor = Color.argb(255, 255, 150, 100)  // Orange
        private val endColor = Color.argb(255, 30, 30, 150)      // Dark Blue

        // Rendering tools
        private val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        // Synchronized particle pool management
        private val particleLock = Any()

        // FPS tracking variables
        private var frameCount = 0
        private var lastFpsUpdateTime = System.nanoTime()
        private var currentFPS = 0f
        private val fpsUpdateInterval = 1_000_000_000L // 1 second in nanoseconds
        private var totalFrameTimeNanos = 0L
        private var fpsVisible = true

        // FPS display properties
        private val fpsPaint = Paint().apply {
            color = Color.GREEN
            textSize = 40f
            typeface = Typeface.MONOSPACE
        }
        private val fpsPosition = PointF(20f, 200f)     // Top-left corner

        private var lastUpdateTime = System.nanoTime()          // Last Frame age in nanoseconds

        // =====================================================================
        // Core Wallpaper Engine Implementation
        // =====================================================================

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {

                // Reset FPS counters
                frameCount = 0
                totalFrameTimeNanos = 0L
                lastFpsUpdateTime = System.nanoTime()

                startDrawing()
            } else {
                stopDrawing()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopDrawing()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            job.cancel()
            super.onDestroy()
        }

        // =====================================================================
        // Drawing System
        // =====================================================================

        private fun startDrawing() {
            drawJob?.cancel()
            drawJob = launch {
                while (isVisible) {
                    drawFrame()
                    delay(16)  // ~60 FPS
                }
            }
        }

        private fun stopDrawing() {
            drawJob?.cancel()
            particles.clear()
        }

        private fun drawFrame() {
            val startTime = System.nanoTime()
            val holder = surfaceHolder
            var canvas: Canvas? = null

            try {
                canvas = holder.lockCanvas()
                canvas?.let {
                    // Existing particle drawing
                    updateAndDrawParticles(it)

                    // Draw FPS overlay
                    if (fpsVisible) drawFpsOverlay(it)

                }
            } finally {
                canvas?.let { holder.unlockCanvasAndPost(it) }
            }

            val frameTimeNanos = System.nanoTime() - startTime
            // Update FPS counter
            updateFpsCounter(frameTimeNanos)
        }

        // =====================================================================
        // Particle System Update Logic
        // =====================================================================

        private fun updateAndDrawParticles(canvas: Canvas) {
            val now = System.nanoTime()
            val deltaTime = (now - lastUpdateTime).toFloat().div(1_000_000_000)  // ns → seconds
            lastUpdateTime = now

            // Clear the canvas
            canvas.drawColor(Color.BLACK)
            generateNewParticles()
            processExistingParticles(canvas, deltaTime)
        }

        // Generate new particles while touching
        private fun generateNewParticles() {
            if (!isTouching) return

            synchronized(particleLock) {
                repeat(3) {
                    particles.add(createParticleInstance())
                }
            }
        }

        private fun createParticleInstance(): Particle {
            val angle = (Math.random() * 2 * PI).toFloat()
            val speed = (Math.random() * 3.8 + 1.8).toFloat()

            return ParticlePool.obtain(
                x = touchX,
                y = touchY,
                radius = (Math.random() * 24 + 14).toFloat(),
                velocityX = cos(angle) * speed,
                velocityY = sin(angle) * speed,
                baseDecay = touchDecay,
                alpha = 255,
                floatForce = (Math.random() * baseFloatForce - baseFloatForce/2).toFloat(),
                rotation = (Math.random() * 360).toFloat(),
                rotationSpeed = (Math.random() * baseRotationSpeed - baseRotationSpeed/2).toFloat(),
            )
        }

        private fun processExistingParticles(canvas: Canvas, deltaTime: Float) {
            synchronized(particleLock) {
                val iterator = particles.iterator()
                while (iterator.hasNext()) {
                    val p = iterator.next()

//                updateParticlePhysicsAndDecay(p)

                    // 9. Remove expired particles if required
                    if (shouldRemoveParticle(p)) {
                        ParticlePool.recycle(p)
                        iterator.remove()
                        continue
                    }

                    // Update age in seconds
                    p.age += deltaTime
                    updateParticlePhysicsAndDecay(p)
                    drawParticle(p, canvas)
                }
            }
        }

        private fun updateParticlePhysicsAndDecay(p: Particle) {
            val currentTime = System.currentTimeMillis()

            // 2. Calculate size-impact coefficients
            val sizeEffect = p.sizeRatio.pow(0.75f)
            val inverseSizeEffect = 1.25f - sizeEffect

            // 4. Physics updates using sizeRatio
            p.velocityX *= airResistance * (0.92f + 0.08f * sizeEffect)
            p.velocityY *= airResistance * (0.92f + 0.08f * sizeEffect)

            val (noiseX, noiseY) = getNoiseInfluence(p.x, p.y, currentTime)
            p.velocityX += noiseX * inverseSizeEffect * noiseStrength
            p.velocityY += noiseY * inverseSizeEffect * noiseStrength

            // 5. Position and rotation updates
            p.x += p.velocityX
            p.y += p.velocityY - p.floatForce * inverseSizeEffect
            p.rotation += p.rotationSpeed * (1.4f - sizeEffect * 0.4f)

            // 6. Size and decay updates
            p.baseDecay = if (isTouching) {
                lerp(touchDecay, 0.96f, sizeEffect)
            } else {
                lerp(releaseDecay, 0.84f, sizeEffect)
            }
            p.radius *= easeInOutQuad(p.baseDecay)
        }

        private fun calculateParticleAlpha(p: Particle): Int {
            // 1. Calculate lifecycle phases
            val fadeProgress = if (p.age > colorTransitionDuration.toFloat().div(1000)) {
                ((p.age - colorTransitionDuration.toFloat().div(1000)) / fadeOutDuration.toFloat().div(1000)).coerceIn(0f, 1f)
            } else 0f

            // 7. Alpha management
            val finalAlpha = (p.alpha * (1 - fadeProgress)).toInt()

            return finalAlpha
        }

        private fun shouldRemoveParticle(p: Particle): Boolean {
            val maxAge = (colorTransitionDuration + fadeOutDuration).toFloat().div(1000)
            return p.age > maxAge || p.radius < 1.5f
        }

        private fun drawParticle(p: Particle, canvas: Canvas) {
            paint.shader = createParticleGradient(p)

            canvas.run {
                save()
                rotate(p.rotation, p.x, p.y)
                drawCircle(p.x, p.y, p.radius, paint)
                restore()
            }
        }

        private fun createParticleGradient(p: Particle): Shader {
            // 3. Color transition calculation
            val currentColor = p.getCurrentColor(startColor, endColor, colorTransitionDuration)
            val finalAlpha = calculateParticleAlpha(p)

            // 8. Create color gradient
            val gradientColors = intArrayOf(
                Color.argb(
                    finalAlpha,
                    Color.red(currentColor),
                    Color.green(currentColor),
                    Color.blue(currentColor)
                ),
                Color.argb(
                    0,
                    Color.red(currentColor),
                    Color.green(currentColor),
                    Color.blue(currentColor)
                )
            )

            return RadialGradient(
                p.x, p.y,
                p.radius,
                gradientColors,
                null,
                Shader.TileMode.CLAMP
            )
        }

        // =====================================================================
        // Input Handling
        // =====================================================================

        override fun onTouchEvent(event: MotionEvent) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchX = event.x
                    touchY = event.y
                    isTouching = true
                    cancelFade()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isTouching = false
                    startFadeSequence()
                }
            }
            super.onTouchEvent(event)
        }

        private fun startFadeSequence() {
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

        // =====================================================================
        // Helper Functions
        // =====================================================================

        private fun getNoiseInfluence(x: Float, y: Float, time: Long): Pair<Float, Float> {
            val phase = (time % 60000) / 1000f * PI.toFloat()
            return Pair(
                cos(x * 0.012f + y * 0.018f + phase) * 0.22f,
                sin(x * 0.015f - y * 0.022f + phase) * 0.22f
            )
        }

        private fun lerp(start: Float, end: Float, factor: Float) =
            start + (end - start) * factor

        private fun easeInOutQuad(t: Float): Float {
            return if (t < 0.5) 2 * t * t else 1 - (-2 * t + 2).let { it * it } / 2
        }

        // =====================================================================
        // New FPS drawing method
        // =====================================================================

        private fun drawFpsOverlay(canvas: Canvas) {
            val fpsText = "FPS: ${"%.1f".format(currentFPS)}"

            // Background for readability
            canvas.drawRect(
                fpsPosition.x - 10f,
                fpsPosition.y - 40f,
                fpsPosition.x + 230f,
                fpsPosition.y + 10f,
                Paint().apply {
                    color = Color.argb(150, 255, 150, 100)
                }
            )

            // FPS text
            canvas.drawText(fpsText, fpsPosition.x, fpsPosition.y, fpsPaint)
        }

        private fun updateFpsCounter(frameTimeNanos: Long) {
            frameCount++
            totalFrameTimeNanos += frameTimeNanos

            val elapsedSinceUpdate = System.nanoTime() - lastFpsUpdateTime
            if (elapsedSinceUpdate >= fpsUpdateInterval) { // 1 second
                currentFPS = when {
                    totalFrameTimeNanos == 0L -> 0f
                    else -> fpsUpdateInterval / (totalFrameTimeNanos / frameCount.toFloat())
                }.coerceAtMost(60f)

                // Reset counters
                frameCount = 0
                totalFrameTimeNanos = 0L
                lastFpsUpdateTime = System.nanoTime()
            }
        }
    }
}