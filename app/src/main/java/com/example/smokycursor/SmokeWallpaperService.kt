package com.example.smokycursor

import android.graphics.*
import android.service.wallpaper.WallpaperService
import android.view.MotionEvent
import android.view.SurfaceHolder
import kotlin.math.*
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

/**
 * SmokeWallpaperService is a live wallpaper service that simulates a dynamic, interactive smoke effect.
 *
 * This service employs a particle system to generate a visually engaging smoke-like animation.
 * User interactions through touch events significantly influence the smoke's behavior, creating an
 * immersive and responsive experience. The smoke particles are governed by physics-based rules,
 * exhibiting natural-looking movement, decay, and color transitions.
 *
 * ## Core Functionality:
 *
 * -   **Interactive Particle Generation:** Responds to user touches by spawning new smoke particles at the touch location.
 * -   **Physics-Driven Simulation:** Simulates physical phenomena like air resistance, buoyancy (float force), and random noise to achieve realistic particle motion.
 * -   **Dynamic Color Transition:** Smoothly transitions each particle's color from a vibrant orange to a deep blue hue over time.
 * -   **Organic Decay and Fading:** Particles gradually shrink in size and fade out in opacity as they age, mimicking the dissipation of real smoke.
 * -   **Optimized Rendering:** Employs coroutines for smooth, frame-by-frame animation updates and efficient rendering.
 * -   **Customizable Behavior:** Offers a wide array of tunable parameters, allowing for adjustments to particle count, physics properties, color schemes, and more.
 *
 * ## Implementation Details:
 *
 * -   **Wallpaper Engine:** Extends `WallpaperService` and implements a custom `SmokeEngine` as its rendering engine.
 * -   **Coroutine-Based Animation:** `SmokeEngine` utilizes coroutines for asynchronous task management, particularly for the animation loop and fade-out sequences.
 * -   **Particle System:** Manages a collection of `Particle` objects, each representing a single puff of smoke.
 * -   **Touch Interaction:** Processes touch events (`ACTION_DOWN`, `ACTION_MOVE`, `ACTION_UP`, `ACTION_CANCEL`) to dynamically control particle generation and behavior.
 * -   **Canvas Drawing:** Leverages Android's `Canvas` API to draw particles onto the wallpaper surface.
 * - **Physics simulation** Simulate `airResistance`, `noise`, `buoyancy` and `rotation`.
 * - **Size and decay management** Manage the particle size and its decay rate.
 * - **Fade out system** Allow to fade out particles using a coroutine.
 */
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
        /**
         * The duration of the fade-out animation in milliseconds.
         * This determines how long it takes for the element to fade out completely.
         * A higher value results in a slower, more gradual fade-out.
         *
         * Defaults to 5000 milliseconds (5 seconds).
         */
        private val fadeOutDuration = 5000L      // Fade-out duration in ms
        /**
        +     * Represents the progress of the fade-out animation, where 1.0f is fully visible and 0.0f is completely faded out.
        +     * Values between 0.0f and 1.0f represent partial fading.
        +     * Defaults to 1.0f (fully visible).
         */
        private var fadeOutProgress = 1f
        private var fadeOutJob: Job? = null      // Fade-out coroutine reference

        // Color transition parameters

        /**
         * The duration of the color transition between the start and end colors.
         *
         * This value represents the time, in milliseconds, it takes for the color to smoothly
         * transition from the initial color (e.g., orange) to the target color (e.g., blue).
         *
         * For instance, a value of 3000L means the color change will occur over a period of 3 seconds.
         */
        private val colorTransitionDuration = 3000L     // Orange → Blue transition time in ms
        private val startColor = Color.argb(255, 255, 150, 100)  // Orange
        private val endColor = Color.argb(255, 30, 30, 150)      // Dark Blue

        // Rendering tools
        private val paint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.FILL
        }

        private var particlesPerTouch = 5   // Adjustable

        // =====================================================================
        // Core Wallpaper Engine Implementation
        // =====================================================================

        /**
         * Called when the visibility of the drawing surface changes.
         *
         * This method is invoked when the drawing surface becomes visible or hidden.
         * It updates the internal `isVisible` flag and starts or stops the drawing process
         * based on the visibility state.
         *
         * @param visible `true` if the drawing surface is now visible, `false` otherwise.
         */
        override fun onVisibilityChanged(visible: Boolean) {
            isVisible = visible
            if (visible) {
                startDrawing()
            } else {
                stopDrawing()
            }
        }

        /**
         * Callback method invoked when the surface associated with this SurfaceHolder is being destroyed.
         * This is called immediately before the surface is actually destroyed and can be used to
         * release any resources associated with it.
         *
         * In this implementation, it stops the drawing process and then calls the superclasses
         * onSurfaceDestroyed method to perform any additional cleanup.
         *
         * @param holder The SurfaceHolder whose surface is being destroyed.
         */
        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            stopDrawing()
            super.onSurfaceDestroyed(holder)
        }

        /**
         * Called when the lifecycle of this component is about to be destroyed.
         *
         * This method performs necessary cleanup tasks:
         * 1. Cancels the [job] coroutine Job. This ensures that any ongoing coroutines launched
         *    within this component are stopped to prevent potential memory leaks or unexpected behavior
         *    after the component is destroyed.
         * 2. Calls the superclasses [onDestroy] method to perform any necessary cleanup defined
         *    by the superclass. This is important for maintaining proper lifecycle behavior.
         *
         * It's crucial to call `super.onDestroy()` to allow the parent class to handle its own cleanup procedures.
         */
        override fun onDestroy() {
            job.cancel()
            super.onDestroy()
        }

        // =====================================================================
        // Drawing System
        // =====================================================================

        /**
         * Starts the drawing loop.
         *
         * This function initializes and starts a coroutine job responsible for continuously drawing frames.
         * It ensures that any existing drawing job is cancelled before starting a new one.
         * The drawing loop continues as long as the `isVisible` flag is true.
         *
         * The loop performs the following actions:
         *   1. Calls the `drawFrame()` function to render a single frame.
         *   2. Introduces a delay of 16 milliseconds, targeting a frame rate of approximately 60 FPS.
         *
         * The coroutine job is stored in the `drawJob` variable, allowing it to be cancelled later.
         *
         * **Note:**
         * - `drawJob` is a [Job] object that represents the coroutine.
         * - `isVisible` is assumed to be a boolean flag that determines whether drawing should continue.
         * - `drawFrame()` is assumed to be a function responsible for rendering a single frame.
         * - `launch` is a coroutine builder that creates a new coroutine.
         * - `delay` is a suspending function that delays the coroutine execution for a specified duration.
         * - This method is typically called when the component/view becomes visible and needs to start animating/drawing.
         * - It's crucial to have a counterpart method to cancel this job when the view is no longer visible to prevent resource leaks.
         *
         * **Preconditions:**
         * - The `launch` coroutine builder must be available in the current scope (e.g., within a CoroutineScope).
         * - `isVisible` flag should be set appropriately to determine whether drawing is needed.
         * - `drawFrame()` method must be defined and correctly implement the frame rendering logic.
         *
         * **Post-conditions:**
         * - A new coroutine job is started and assigned to `drawJob`.
         * - If `isVisible` is true, `drawFrame()` will be called repeatedly with approximately 60 FPS.
         * - If a previous `drawJob` existed, it will be cancelled before the new job starts.
         *
         * **Side Effects:**
         * - Modifies the `drawJob` variable.
         * - Potentially triggers frequent calls to `drawFrame()`, which could have other side effects depending on its implementation.
         */
        private fun startDrawing() {
            drawJob?.cancel()
            drawJob = launch {
                while (isVisible) {
                    drawFrame()
                    delay(16)  // ~60 FPS
                }
            }
        }

        /**
         * Stops the drawing process.
         *
         * This function performs the following actions:
         * 1. Cancels the current drawing job, if any. This effectively halts any ongoing
         *    coroutine responsible for updating and rendering the particles.
         * 2. Clears the list of particles. This removes all existing particles from
         *    the collection, ensuring that no further rendering of them occurs.
         *
         * This function should be called when you want to immediately stop the drawing
         * and reset the state of the particle system. For example, when the view is detached
         * or when a new drawing operation is about to begin.
         *
         * It ensures that:
         *  - No more particles are drawn or updated.
         *  - All resources associated with the current drawing operation are released.
         *  - The particle system is reset to a clean state, ready for a new drawing session.
         */
        private fun stopDrawing() {
            drawJob?.cancel()
            particles.clear()
        }

        /**
        -         * Draws a single frame on the SurfaceView's canvas.
        -         *
        -         * This function is responsible for:
        -         * 1. Obtaining a lock on the SurfaceHolder's canvas, allowing exclusive drawing.
        -         * 2. Calling [updateAndDrawParticles] to update the particle simulation and render the particles onto the canvas.
        -         * 3. Unlocking and posting the updated canvas to the SurfaceView, making the changes visible.
        -         *
        -         * The drawing process is enclosed in a try-finally block to ensure the canvas is always unlocked and
        -         * posted, even if an exception occurs during the drawing operation. This prevents potential issues like
        -         * the SurfaceView becoming unresponsive.
        -         *
        -         * If `holder.lockCanvas()` returns null, it means the canvas is unavailable. In this case, the function
        -         * will not attempt to draw.
        -         *
        -         * If any error occurs during drawing, it will be implicitly ignored, but the canvas will be unlocked to allow
        -         * other drawing operations or cleanup.
        -         *
        -         * @see SurfaceHolder
        -         * @see Canvas
        -         * @see updateAndDrawParticles
         */
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

        // =====================================================================
        // Particle System Update Logic
        // =====================================================================

        /**
         * Updates the state of the particle system and draws the particles on the canvas.
         *
         * This function performs the following operations:
         * 1. Clears the canvas by filling it with a black background.
         * 2. Generates new particles, adding them to the system.
         * 3. Processes existing particles, updating their positions, states, and drawing them onto the canvas.
         *
         * @param canvas The Canvas object to draw the particles onto. This should be a valid canvas associated with a view or bitmap.
         */
        private fun updateAndDrawParticles(canvas: Canvas) {
            canvas.drawColor(Color.BLACK)
            generateNewParticles()
            processExistingParticles(canvas)
        }

        /**
         * Generates new particles when the screen is being touched.
         *
         * This function checks if the screen is currently being touched (`isTouching` flag).
         * If it is, it creates a number of new particle instances based on the `particlesPerTouch`
         * configuration and adds them to the `particles` collection.
         *
         * The function does nothing if the screen is not being touched.
         *
         * The newly created particles are intended to visually represent the touch interaction.
         */
        private fun generateNewParticles() {
            if (!isTouching) return

            repeat(particlesPerTouch) {
                particles.add(createParticleInstance())
            }
        }

        /**
         * Creates and initializes a new Particle instance.
         *
         * This function generates a new particle with randomized properties based on the current touch position
         * and some predefined base values. The particle's properties include position, radius, velocity, decay,
         * alpha, floating force, rotation, and rotation speed.
         *
         * @return A new [Particle] instance with randomly generated properties.
         *
         * The particle properties are determined as follows:
         *  - **x, y:** The particle's initial x and y coordinates are set to the current touch position ([touchX], [touchY]).
         *  - **radius:** The particle's radius is a random value between 14 and 38 (inclusive).
         *  - **velocityX, velocityY:** The particle's velocity components are calculated based on a random angle and speed.
         *    - `angle`: A random angle between 0 and 2π radians.
         *    - `speed`: A random speed between 1.8 and 5.6 (inclusive).
         *    - The velocity components are then derived using `cos(angle) * speed` and `sin(angle) * speed`.
         *  - **baseDecay:** The particle's base decay rate is taken from [touchDecay].
         *  - **alpha:** The particle's initial alpha (opacity) is set to 255 (fully opaque).
         *  - **floatForce:** The particle's floating force is a random value within the range of -[baseFloatForce]/2 to +[baseFloatForce]/2.
         *  - **rotation:** The particle's initial rotation is a random value between 0 and 360 degrees.
         *  - **rotationSpeed:** The particle's rotation speed is a random value within the range of -[baseRotationSpeed]/2 to +[baseRotationSpeed]/2.
         *
         * The values like [touchX], [touchY], [touchDecay], [baseFloatForce], and [baseRotationSpeed] are assumed to be
         * class properties and are used as constants or references in the particle creation.
         */
        private fun createParticleInstance(): Particle {
            val angle = (Math.random() * 2 * PI).toFloat()
            val speed = (Math.random() * 3.8 + 1.8).toFloat()
            return Particle(
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

        /**
         * Processes the existing particles in the system.
         *
         * This method iterates through the list of existing particles, updates their physics
         * and decay states, checks if they should be removed (e.g., if they have expired),
         * and draws them onto the provided canvas.
         *
         * **Functionality:**
         * 1. **Iteration:** Iterates through each particle in the `particles` list.
         * 2. **Removal Check:**  Checks if a particle should be removed using `shouldRemoveParticle(p)`.
         *    - If a particle should be removed, it is removed from the list, and the loop continues to the next particle.
         * 3. **Update:** If a particle is not removed, its physics and decay states are updated by calling `updateParticlePhysicsAndDecay(p, currentTime)`.
         * 4. **Drawing:** Finally, the particle is drawn onto the canvas using `drawParticle(p, canvas)`.
         * 5. **Time Tracking:** Uses `System.currentTimeMillis()` to get the current time, used for updating particle states.
         * 6. **Concurrent Modification Safety:** Uses an `Iterator` to safely remove particles from the `particles` list while iterating.
         *
         * @param canvas The canvas on which the particles should be drawn.
         */
        private fun processExistingParticles(canvas: Canvas) {
            val currentTime = System.currentTimeMillis()
            val iterator = particles.iterator()

            while (iterator.hasNext()) {
                val p = iterator.next()

//                updateParticlePhysicsAndDecay(p, currentTime)

                // Remove expired particles if required
                if (shouldRemoveParticle(p)) {
                    iterator.remove()
                    continue
                }

                updateParticlePhysicsAndDecay(p, currentTime)

                drawParticle(p, canvas)
            }
        }

        /**
         * Updates the physics and decay properties of a particle.
         *
         * This function simulates the behavior of a particle over time, including its
         * velocity, position, rotation, size, and decay rate. It takes into account
         * factors like air resistance, noise influence, buoyancy (float force), and
         * touch-based decay. The effect of these factors is also modulated based on the
         * particle's size.
         *
         * @param p The [Particle] object to update.
         * @param currentTime The current time in milliseconds, used for noise generation.
         *
         * **Physics Updates:**
         * - **Air Resistance:** The particle's velocity in both x and y directions
         *   is reduced by a factor based on `airResistance` and the `sizeEffect`.
         *   Larger particles experience slightly less resistance.
         * - **Noise Influence:** Perlin noise is sampled based on the particle's
         *   position and current time, adding a chaotic influence to the particle's
         *   velocity. Smaller particles are more heavily influenced by the noise.
         * - **Position Update:** The particle's x and y coordinates are updated based
         *   on its velocity. The y-coordinate also considers a `floatForce`, which
         *   simulates buoyancy, pushing the particle upwards. Smaller particles
         *   experience a larger float force.
         * - **Rotation Update:** The particle's rotation is updated based on its
         *   `rotationSpeed`. Larger particles rotate slower than smaller ones.
         *
         * **Size and Decay Updates:**
         * - **Base Decay:** The particle's decay rate (`baseDecay`) is determined
         *   based on whether the particle is currently touching something (`isTouching`).
         *   If touching, it decays slower, interpolating between `touchDecay` and 0.96
         *   based on `sizeEffect`. If not touching, it decays faster, interpolating
         *   between `releaseDecay` and 0.84 based on `sizeEffect`.
         * - **Radius Update:** The particle's radius is reduced based on `baseDecay`,
         *   using an easing function (`easeInOutQuad`) to smooth the decay.
         *
         * **Calculations:**
         * - **sizeEffect:** A value derived from `p.sizeRatio` that modulates how
         *   physics and decay affect the particle based on its size.
         */
        private fun updateParticlePhysicsAndDecay(p: Particle, currentTime: Long) {
            // Calculate size-impact coefficients
            val sizeEffect = p.sizeRatio.pow(0.75f)
            val inverseSizeEffect = 1.25f - sizeEffect

            // Physics updates using sizeRatio
            p.velocityX *= airResistance * (0.92f + 0.08f * sizeEffect)
            p.velocityY *= airResistance * (0.92f + 0.08f * sizeEffect)

            val (noiseX, noiseY) = getNoiseInfluence(p.x, p.y, currentTime)
            p.velocityX += noiseX * inverseSizeEffect * noiseStrength
            p.velocityY += noiseY * inverseSizeEffect * noiseStrength

            // Position and rotation updates
            p.x += p.velocityX
            p.y += p.velocityY - p.floatForce * inverseSizeEffect
            p.rotation += p.rotationSpeed * (1.4f - sizeEffect * 0.4f)

            // Size and decay updates
            p.baseDecay = if (isTouching) {
                lerp(touchDecay, 0.96f, sizeEffect)
            } else {
                lerp(releaseDecay, 0.84f, sizeEffect)
            }
            p.radius *= easeInOutQuad(p.baseDecay)
        }

        /**
         * Calculates the alpha value for a given particle based on its age and predefined durations.
         *
         * This function determines the opacity of a particle, taking into account a color transition
         * duration and a fade-out duration.  The particle's alpha decreases over time during the fade-out phase.
         *
         * @param p The Particle object for which to calculate the alpha value.
         *          It contains the particle's current age (p.age) and its initial alpha value (p.alpha).
         * @return An integer representing the calculated alpha value for the particle, ranging from 0 to 255 (inclusive).
         *         The alpha value decreases as the particle progresses through the fade-out phase.
         *
         * Algorithm:
         * 1. **Calculate Fade Progress:**
         *    - If the particle's age (`p.age`) is greater than the `colorTransitionDuration`, the particle is in the fade-out phase.
         *    - The `fadeProgress` is calculated as the ratio of the time spent in the fade-out phase (`p.age - colorTransitionDuration`)
         *      to the total `fadeOutDuration`.
         *    - The `fadeProgress` is then clamped between 0.0 and 1.0 using `coerceIn(0f, 1f)`. This ensures the progress never goes below 0 or above 1.
         *    - If the particle's age is less than or equal to `colorTransitionDuration`, it's not in the fade-out phase, and `fadeProgress` is 0.
         * 2. **Calculate Final Alpha:**
         *    - The `finalAlpha` is calculated by reducing the particle's initial alpha (`p.alpha`) based on the `fadeProgress`.
         *    - The formula used is `p.alpha * (1 - fadeProgress)`.
         *    - If `fadeProgress` is 0 (not in fade-out), the `finalAlpha` will be the same as the initial alpha.
         *    - If `fadeProgress` is 1 (fully faded out), the `finalAlpha` will be 0.
         *    - Values between 0 and 1 reduce the final Alpha proportionally
         * 3. **Return Final Alpha:**
         *    - The calculated `finalAlpha` is cast to an integer and returned.
         */
        private fun calculateParticleAlpha(p: Particle): Int {
            // Calculate lifecycle phases
            val fadeProgress = if (p.age > colorTransitionDuration) {
                ((p.age - colorTransitionDuration) / fadeOutDuration.toFloat()).coerceIn(0f, 1f)
            } else 0f

            // Alpha management
            val finalAlpha = (p.alpha * (1 - fadeProgress)).toInt()

            return finalAlpha
        }

        /**
         * Determines whether a particle should be removed based on its alpha value and radius.
         *
         * A particle is considered for removal if either of the following conditions is met:
         * 1. Its final alpha value, as calculated by [calculateParticleAlpha], is less than 4.
         *    This indicates the particle has become too transparent and is no longer visually significant.
         * 2. Its radius is less than 1.5f.
         *    This indicates the particle has shrunk to a very small size and is no longer visually significant.
         *
         * @param p The Particle object to evaluate for removal.
         * @return True if the particle should be removed; false otherwise.
         */
        private fun shouldRemoveParticle(p: Particle): Boolean {
            // Alpha management
            val finalAlpha = calculateParticleAlpha(p)

            return (finalAlpha < 4 || p.radius < 1.5f)
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

        /**
         * Creates a radial gradient shader for a given particle.
         *
         * This function generates a radial gradient that emanates from the particle's center,
         * fading from the particle's current color to transparent at its edge. This creates a
         * soft, diffused visual effect, making the particle appear to fade out towards its
         * boundary.
         *
         * The gradient is centered at the particle's position (`x`, `y`) and extends outward to
         * the particle's radius. The starting color of the gradient is dynamically determined
         * based on the particle's current color transition state, and its alpha (opacity) is
         * adjusted according to its life cycle. The end color of the gradient is always fully
         * transparent.
         *
         * @param p The particle for which to create the gradient. This object must provide the
         *          following:
         *          - `x`: The x-coordinate of the particle's center.
         *          - `y`: The y-coordinate of the particle's center.
         *          - `radius`: The radius of the particle, defining the extent of the gradient.
         *          - `getCurrentColor(startColor, endColor, colorTransitionDuration)`: A method to
         *            retrieve the particle's current color based on the provided parameters. This
         *            method handles the color transition logic.
         *          - `calculateParticleAlpha(p)` (implicitly used): A method or logic, likely
         *            external to the particle but accessible, to determine the particle's current
         *            alpha value.
         * @return A {@link Shader} representing the radial gradient. This shader can be used to
         *         paint a shape or area, applying the gradient effect to its fill.
         *         Specifically, a {@link RadialGradient} is returned.
         *
         * @see Particle
         * @see RadialGradient
         * @see Shader
         * @see Color
         */
        private fun createParticleGradient(p: Particle): Shader {
            // Color transition calculation
            val currentColor = p.getCurrentColor(startColor, endColor, colorTransitionDuration)
            val finalAlpha = calculateParticleAlpha(p)

            // Create color gradient
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

        /**
         * Handles touch events on the view.
         *
         * This function is called whenever a touch event occurs on the view. It tracks
         * the touch position and the state of the touch (whether the user is currently
         * touching the screen or not). It also manages the fading sequence based on
         * the touch state.
         *
         * When a touch begins (ACTION_DOWN) or is in progress (ACTION_MOVE), it:
         *   - Updates the touchX and touchY coordinates to the current touch position.
         *   - Sets isTouching to true, indicating that a touch is active.
         *   - Cancels any existing fade animation.
         *
         * When a touch ends (ACTION_UP) or is canceled (ACTION_CANCEL), it:
         *   - Sets isTouching to false, indicating that the touch has ended.
         *   - Initiates the fade sequence.
         *
         * @param event The MotionEvent object that describes the touch event.
         */
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

        /**
         * Starts a fade-out sequence, smoothly transitioning the `fadeOutProgress` from 1.0 (fully visible) to 0.0 (fully transparent) over a specified duration.
         *
         * This function initiates a coroutine-based animation to achieve the fade-out effect.
         * It ensures smooth fading and handles concurrent fade requests gracefully by canceling any existing fade-out operation before starting a new one.
         *
         * **Fade-Out Process Breakdown:**
         *
         * 1. **Concurrency Management (Cancellation):**
         *    - If a fade-out coroutine (`fadeOutJob`) is currently running, it is immediately canceled. This prevents multiple fade-out sequences from overlapping and interfering with each other, ensuring a clean and predictable visual transition.
         *
         * 2. **Initialization (Progress Reset):**
         *    - The `fadeOutProgress` is reset to 1.0. This sets the starting point of the fade to fully opaque, guaranteeing a consistent starting state for each fade-out, regardless of any previous fade operations.
         *
         * 3. **Coroutine Launch (Animation Execution):**
         *    - A new coroutine is launched using the `launch` function. This coroutine is responsible for executing the fade-out animation asynchronously, allowing other tasks to continue running in parallel.
         *    - The coroutine will continue to run, updating the fade until the `fadeOutProgress` reaches or falls below 0.0.
         *
         * 4. **Time Tracking (Animation Timing):**
         *    - `startTime` is recorded as the current system time when the coroutine begins. This timestamp serves as the reference point for calculating the elapsed time during the animation.
         *    - In each iteration of the animation loop, `elapsed` time (the time passed since `startTime`) is calculated. This is used to determine how far along the fade-out animation is.
         *
         * 5. **Progress Calculation and Update (Opacity Adjustment):**
         *    - The `fadeOutProgress` value is updated in each frame of the animation loop based on the `elapsed` time.
         *    - The formula `1 - (elapsed.toFloat() / fadeOutDuration)` is used to calculate the current fade progress.
         *       - `elapsed`: Represents the time elapsed since the fade-out started.
         *       - `fadeOutDuration`: Represents the total duration of the fade-out animation.
         */
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

        /**
         * Cancels any ongoing fade-out animation and resets the fade-out progress to its end state (1f).
         *
         * This function is typically used to abruptly stop a fade-out effect that might be in progress.
         * By canceling the `fadeOutJob`, we halt any coroutine-based animation that was responsible
         * for gradually decreasing the opacity. Setting `fadeOutProgress` to 1f ensures that
         * the object being faded is fully opaque (i.e., not faded at all) immediately after cancellation.
         *
         * Example scenarios where this might be useful:
         * - A user interaction interrupts the fade-out animation.
         * - The object being faded is no longer relevant and needs to be immediately visible.
         * - A state change requires a reset of the fade-out state.
         */
        private fun cancelFade() {
            fadeOutJob?.cancel()
            fadeOutProgress = 1f
        }

        // =====================================================================
        // Helper Functions
        // =====================================================================

        /**
         * Calculates the noise influence at a given point (x, y) at a specific time.
         *
         * This function models the influence of a dynamic noise field on a specific point in space.
         * The noise is represented as a pair of floating-point values, indicating its effect
         * along the x and y axes, respectively.  The noise field exhibits smooth, sinusoidal
         * variations that evolve over both space and time.
         *
         * **Noise Model:**
         *
         * The noise influence is generated using a sinusoidal function with the following components:
         *
         *   - **Spatial Variation:** The noise smoothly varies across the x and y axes. This variation is
         *     controlled by four spatial frequencies (0.012f, 0.018f for the x-axis component, and 0.015f,
         *     0.022f for the y-axis component). These frequencies determine the "wavelength" of the noise
         *     patterns in each direction.
         *   - **Temporal Variation:** The noise field evolves over time. This temporal change is modeled
         *     as a phase shift that progresses linearly with time. The noise pattern repeats every 60 seconds.
         *   - **Amplitude:** The overall strength of the noise influence is controlled by a fixed amplitude of 0.22f.
         *   - **Sinusoidal Nature:** Cosine and sine functions are used to create smooth, wave-like patterns.
         *     The x-axis influence uses a cosine function, while the y-axis influence uses a sine function.
         *
         * **Mathematical Representation:**
         *
         *   - `noise_x = cos(x * 0.012f + y * 0.018f + phase) * 0.22f`
         *   - `noise_y = sin(x * 0.015f - y * 0.022f + phase) * 0.22f`
         *
         *   Where:
         *      - `x`, `y`: The coordinates of the point.
         *      - `phase`:  A time-dependent phase that is calculated as `(time % 60000) / 1000f * PI.toFloat()`.
         *        It cycles from 0 to PI radians every 60 seconds.
         */
        private fun getNoiseInfluence(x: Float, y: Float, time: Long): Pair<Float, Float> {
            val phase = (time % 60000) / 1000f * PI.toFloat()
            return Pair(
                cos(x * 0.012f + y * 0.018f + phase) * 0.22f,
                sin(x * 0.015f - y * 0.022f + phase) * 0.22f
            )
        }

        /**
         * Linearly interpolates between two floating-point values.
         *
         * This function calculates a value between `start` and `end` based on the interpolation `factor`.
         *
         * - If `factor` is 0.0, the function returns `start`.
         * - If `factor` is 1.0, the function returns `end`.
         * - If `factor` is between 0.0 and 1.0, the function returns a value proportionally between `start` and `end`.
         * - If `factor` is outside the range [0.0, 1.0], the function extrapolates accordingly.
         *
         * @param start The starting value of the interpolation.
         * @param end The ending value of the interpolation.
         * @param factor The interpolation factor, typically between 0.0 and 1.0.
         * @return The linearly interpolated value between `start` and `end`.
         */
        private fun lerp(start: Float, end: Float, factor: Float) =
            start + (end - start) * factor

        /**
         * Easing function implementing the ease-in-out quadratic formula.
         *
         * This function provides a smooth transition effect where the animation
         * starts slowly, accelerates through the middle, and then slows down towards
         * the end. It's commonly used for interpolating values in animations.
         *
         * The function implements the following logic:
         * - For the first half of the animation (factor < 0.5), it applies the ease-in quadratic formula: 2 * factor * factor.
         *   This creates a gradual acceleration from the start.
         * - For the second half of the animation (factor >= 0.5), it applies a mirrored ease-out quadratic formula.
         *   The formula `1 - (-2 * factor + 2)^2 / 2` effectively reverses the acceleration, causing the
         *   animation to slow down towards the end.
         *
         * @param factor The normalized time (or progress) of the animation, ranging from 0.0 to 1.0.
         *          0.0 represents the start of the animation, 1.0 represents the end.
         *          Values outside of [0.0, 1.0] are not clamped, but the resulting output is generally not meaningful.
         * @return The eased value, also generally ranging from 0.0 to 1.0, representing the
         *         interpolated position at time `factor`.
         *         The output value is not clamped to [0.0, 1.0], but it will generally lie within this range for factor in [0.0, 1.0]
         *
         * @sample
         * // Example usage:
         * val easedValue = easeInOutQuad(0.25f) // Returns approximately 0.125
         * val easedValue2 = easeInOutQuad(0.5f) // returns 0.5
         * val easedValue3 = easeInOutQuad(0.75f) // returns approximately 0.875
         * val easedValue4 = easeInOutQuad(0.0f) // returns 0.0
         * val easedValue5 = easeInOutQuad(1.0f) // returns 1.0
         * val easedValue6 = easeInOutQuad(-0.5f) // return 0.5 (output outside of meaningful range)
         */
        private fun easeInOutQuad(factor: Float): Float {
            return if (factor < 0.5) 2 * factor * factor else 1 - (-2 * factor + 2).let { it * it } / 2
        }
    }
}