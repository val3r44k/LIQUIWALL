package com.val3r44k.vallport.physics

import kotlin.math.abs

/**
 * 2D Spring-Damper physics point implementing Hooke's Law with viscous damping:
 *
 *   F = -k * (x - x_target) - c * v
 *   a = F / m
 *   v(t + dt) = v(t) + a * dt
 *   x(t + dt) = x(t) + v(t + dt) * dt
 *
 * This is the SOLE physics primitive used in the lightweight layer-based architecture.
 * Outputs are consumed as hardware translation offsets (translationX/Y) — zero pixel processing.
 *
 * Optimized for zero runtime heap allocations in render loops.
 */
class SpringPoint(
    var stiffness: Float = 140.0f,
    var damping: Float = 12.0f,
    var mass: Float = 1.0f,
) {
    var x: Float = 0.0f
        private set
    var y: Float = 0.0f
        private set

    var vx: Float = 0.0f
        private set
    var vy: Float = 0.0f
        private set

    var targetX: Float = 0.0f
    var targetY: Float = 0.0f

    /** Whether the spring is at rest (within threshold). */
    val isAtRest: Boolean
        get() = abs(x - targetX) < 0.05f && abs(y - targetY) < 0.05f
                && abs(vx) < 0.1f && abs(vy) < 0.1f

    /**
     * Resets the spring state directly to a specific coordinate.
     */
    fun reset(initialX: Float, initialY: Float) {
        x = initialX
        y = initialY
        targetX = initialX
        targetY = initialY
        vx = 0.0f
        vy = 0.0f
    }

    /**
     * Injects an instantaneous velocity impulse (e.g. from touch flick).
     */
    fun applyImpulse(impulseX: Float, impulseY: Float) {
        vx += impulseX / mass
        vy += impulseY / mass
    }

    /**
     * Advances simulation by [dt] seconds using semi-implicit Euler integration.
     * [dt] is clamped to [0.001f, 0.033f] to guarantee numerical stability.
     */
    fun update(dt: Float) {
        val clampedDt = dt.coerceIn(0.001f, 0.033f)
        val invMass = 1.0f / mass.coerceAtLeast(0.001f)

        // Hooke's law + viscous damping
        val fx = (-stiffness * (x - targetX)) - (damping * vx)
        val fy = (-stiffness * (y - targetY)) - (damping * vy)

        val ax = fx * invMass
        val ay = fy * invMass

        vx += ax * clampedDt
        vy += ay * clampedDt

        x += vx * clampedDt
        y += vy * clampedDt

        // Rest threshold snapping to prevent microscopic floating-point jitter
        if (abs(x - targetX) < 0.001f && abs(vx) < 0.01f) {
            x = targetX
            vx = 0.0f
        }
        if (abs(y - targetY) < 0.001f && abs(vy) < 0.01f) {
            y = targetY
            vy = 0.0f
        }
    }
}
