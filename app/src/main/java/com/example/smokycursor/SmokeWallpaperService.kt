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
    override fun onCreateEngine(): Engine = SmokeEngine()

    inner class SmokeEngine : Engine(), CoroutineScope {
        // Coroutine setup
        private val job = SupervisorJob()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main + job

        private val particles = mutableListOf<Particle>()
        private val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }
//        private val colors = intArrayOf(
//            Color.argb(80, 100, 100, 100),
//            Color.argb(0, 100, 100, 100)
//        )
        // Modify colors array
        private val colors = intArrayOf(
            Color.argb(80, 255, 150, 100),  // Orange smoke
            Color.argb(0, 255, 150, 100)
        )
        private var touchX = 0f
        private var touchY = 0f
        private var isVisible = false
        private var drawJob: Job? = null

        private inner class Particle(
            var x: Float,
            var y: Float,
            var radius: Float,
            var velocityX: Float,
            var velocityY: Float,
            var alpha: Int
        )

        private fun startDrawing() {
            drawJob?.cancel()
            drawJob = launch {
                while (isVisible) {
                    draw()
                    delay(16) // Maintain ~60 FPS
                }
            }
        }

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

        private fun drawSmoke(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)

            repeat(5) {
                val angle = (Math.random() * 2 * Math.PI).toFloat()
                val speed = (Math.random() * 3 + 1).toFloat()
                particles.add(Particle(
                    touchX,
                    touchY,
                    (Math.random() * 20 + 10).toFloat(),
                    cos(angle) * speed,
                    sin(angle) * speed,
                    255
                ))
            }

            val iterator = particles.iterator()
            while (iterator.hasNext()) {
                val p = iterator.next()
                p.x += p.velocityX
                p.y += p.velocityY
                p.alpha = (p.alpha * 0.92).toInt()
                p.radius *= 0.95f       // Faster shrink from 0.97 to 0.95
                p.velocityX *= 0.88f    // More momentum from 0.92 to 0.88
                p.velocityY *= 0.92f

                if (p.alpha < 5 || p.radius < 2) {
                    iterator.remove()
                } else {
                    paint.shader = RadialGradient(
                        p.x, p.y, p.radius,
                        colors, null, Shader.TileMode.CLAMP
                    )
                    paint.alpha = p.alpha
                    canvas.drawCircle(p.x, p.y, p.radius, paint)
                }
            }
        }

        override fun onTouchEvent(event: MotionEvent) {
            when (event.action) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    touchX = event.x
                    touchY = event.y
                }
            }
            super.onTouchEvent(event)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                startDrawing()
            } else {
                drawJob?.cancel()
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            drawJob?.cancel()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            job.cancel()
            super.onDestroy()
        }
    }
}