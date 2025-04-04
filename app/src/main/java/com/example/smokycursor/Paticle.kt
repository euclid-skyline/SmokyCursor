package com.example.smokycursor

import android.graphics.Color


// =====================================================================
// Particle Class Definition
// =====================================================================

/**
 * Represents a single particle in a particle system.
 *
 * This class encapsulates the properties and behavior of a particle, including its
 * position, size, movement, visual attributes, and special effects. It also manages
 * the particle's lifecycle and provides methods for calculating its state over time.
 *
 * @property x The current horizontal position of the particle.
 * @property y The current vertical position of the particle.
 * @property radius The current radius of the particle. This value decreases over time based on `baseDecay`.
 * @property velocityX The horizontal velocity of the particle, determining its movement speed in the X direction.
 * @property velocityY The vertical velocity of the particle, determining its movement speed in the Y direction.
 * @property baseDecay The base rate at which the particle's radius decreases over time. A higher value means faster shrinking.
 * @property alpha The opacity of the particle, ranging from 0 (fully transparent) to 255 (fully opaque).
 * @property floatForce A force that influences the particle's vertical movement, typically used for a floating or rising effect. A positive value makes the particle move upward.
 * @property rotation The current rotation angle of the particle (in degrees).
 * @property rotationSpeed The rate at which the particle rotates per update, in degrees.
 */
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
    private val initialRadius: Float = radius       // Initial size when particle created
    private val creationTime: Long = System.currentTimeMillis()     // Timestamp of particle creation in milliseconds

    // Calculated properties

    /**
     * The age of this object in milliseconds.
     *
     * This value is calculated as the difference between the current time
     * and the creation time (`creationTime`).  It represents how long
     * the object has existed.
     *
     * @return The age in milliseconds.
     */
    val age: Long get() = System.currentTimeMillis() - creationTime

    /**
     * Represents the ratio of the current radius to the initial radius.
     *
     * This value is calculated by dividing the current [radius] by the [initialRadius],
     * ensuring that the [initialRadius] is at least 1.0 to prevent division by zero or
     * unexpected behavior. The resulting ratio is then clamped between 0.1 and 1.0.
     *
     * A size ratio of:
     * - 1.0 indicates that the current radius is equal to or greater than the initial radius.
     * - 0.1 indicates that the current radius is 10% of the initial radius, representing the minimum size.
     * - A value between 0.1 and 1.0 indicates the proportional size of the current radius relative to the initial radius.
     *
     * @return A Float representing the size ratio, clamped between 0.1f and 1.0f.
     *
     * @property sizeRatio The ratio of the current radius to the initial radius, clamped between 0.1 and 1.0.
     *                     This provides a normalized measure of the current size relative to the initial size.
     *                     A value of 1.0 means the current radius is at least the initial radius, while 0.1
     *                     means it's at 10% of the initial radius. Values in between represent proportional scaling.
     *                     The minimum size is at 0.1 and the maximum relative size is represented by 1.0.
     *                     The initialRadius is coerced to be at least 1.0 to prevent division by zero.
     *                     This is a read-only property calculated dynamically.
     */
    val sizeRatio: Float
        get() = (radius / initialRadius.coerceAtLeast(1f)).coerceIn(0.1f, 1f)


    /**
     * Calculates the current color based on a linear interpolation between a start and end color,
     * over a specified transition duration.
     *
     * @param start The starting color represented as an ARGB integer.
     * @param end The ending color represented as an ARGB integer.
     * @param transitionDuration The total duration of the color transition in milliseconds.
     * @return The interpolated color at the current point in the transition, represented as an ARGB integer.
     *
     * @throws IllegalArgumentException if `transitionDuration` is zero or negative.
     *
     * This function calculates a color that changes smoothly from `start` to `end` over the given `transitionDuration`.
     * It uses a global variable named `age` (representing elapsed time) to determine the current point in the transition.
     *
     * **How it Works:**
     * 1. **Progress Calculation:** It first calculates the progress of the transition by dividing the current `age` by the `transitionDuration`.
     *    - `progress = age / transitionDuration.toFloat()`
     *    - This yields a value between 0.0 and 1.0, where:
     *      - 0.0 means the transition has not started (at the start color).
     *      - 1.0 means the transition is complete (at the end color).
     *      - Values between 0.0 and 1.0 represent points in the transition.
     * 2. **Progress Clamping:** The calculated `progress` is clamped between 0.0 and 1.0 using `coerceIn(0f, 1f)`.
     *    - This ensures that if `age` is outside the range [0, `transitionDuration`], the progress is still within [0.0, 1.0].
     *    - If `age` is negative it will be considered as `0.0`
     *    - If `age` is greater than `transitionDuration` it will be considered as `1.0`
     * 3. **Color Interpolation:** It then uses a linear interpolation function (`lerpColor`) to determine the color at the calculated `progress`.
     *    - `lerpColor(start, end, progress)`
     */
    fun getCurrentColor(start: Int, end: Int, transitionDuration: Long): Int {
        val progress = (age / transitionDuration.toFloat()).coerceIn(0f, 1f)
        return lerpColor(start, end, progress)
    }

    /**
     * Linearly interpolates between two colors.
     *
     * This function calculates a color that lies between `start` and `end` colors,
     * based on the provided `factor`. The interpolation is performed separately
     * for the alpha, red, green, and blue components of the colors.
     *
     * @param start The starting color (an ARGB color integer).
     * @param end The ending color (an ARGB color integer).
     * @param factor The interpolation factor, a value between 0.0 and 1.0.
     *               - When `factor` is 0.0, the returned color will be the same as `start`.
     *               - When `factor` is 1.0, the returned color will be the same as `end`.
     *               - Values between 0.0 and 1.0 will produce a color proportionally
     *                 between `start` and `end`.
     *               - Values outside the range [0.0, 1.0] are supported but may produce
     *                  colors outside the typical color range.
     * @return An ARGB color integer representing the interpolated color.
     * @throws IllegalArgumentException if factor is not a finite number (NaN, infinity).
     */
    private fun lerpColor(start: Int, end: Int, factor: Float): Int {
        return Color.argb(
            (Color.alpha(start) + (Color.alpha(end) - Color.alpha(start)) * factor).toInt(),
            (Color.red(start) + (Color.red(end) - Color.red(start)) * factor).toInt(),
            (Color.green(start) + (Color.green(end) - Color.green(start)) * factor).toInt(),
            (Color.blue(start) + (Color.blue(end) - Color.blue(start)) * factor).toInt()
        )
    }
}
