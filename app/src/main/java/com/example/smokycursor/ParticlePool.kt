package com.example.smokycursor


// Particle Pool Object is Implementation as singleton class
object ParticlePool {
    private const val MAX_POOL_SIZE = 1000
    private val pool = ArrayDeque<Particle>()

    fun obtainParticle(
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
        return if (pool.isNotEmpty()) {
            pool.removeFirst().apply {
                reset(x, y, radius, velocityX, velocityY,
                    baseDecay, alpha, floatForce, rotation, rotationSpeed)
            }
        } else {
            Particle(x, y, radius, velocityX, velocityY, baseDecay,
                alpha, floatForce, rotation, rotationSpeed)
        }
    }

    fun recycle(particle: Particle) {
        if (pool.size < MAX_POOL_SIZE) {
            pool.addLast(particle)
        }
    }

    fun availableCount() = pool.size
}