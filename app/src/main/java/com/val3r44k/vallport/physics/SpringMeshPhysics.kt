package com.val3r44k.vallport.physics

import kotlin.math.min

/**
 * 2D Mass-Spring-Damper lattice for fluid/jelly mesh deformation (Origin OS droplet effect).
 * Features multi-step sub-stepping physics calculation per frame for extreme precision and zero tearing.
 * Supports active corner and border wobble physics.
 */
class SpringMeshPhysics(
    val cols: Int = 16,
    val rows: Int = 28,
) {
    val vertexCount: Int = (cols + 1) * (rows + 1)
    val floatCount: Int = vertexCount * 2

    val origVerts: FloatArray = FloatArray(floatCount)
    val currentVerts: FloatArray = FloatArray(floatCount)
    private val velocities: FloatArray = FloatArray(floatCount)
    private val forces: FloatArray = FloatArray(floatCount)

    // Per-vertex mask weights (0.0 = rigid, 1.0 = full jelly wobble)
    val vertexMaskWeights: FloatArray = FloatArray(vertexCount) { 1.0f }

    var stiffness: Float = 60.0f          // Softer spring constant
    var damping: Float = 16.0f            // Higher damping for smooth settling
    var mass: Float = 1.0f
    var neighborCoupling: Float = 35.0f

    var tiltX: Float = 0.0f
    var tiltY: Float = 0.0f

    private var meshWidth: Float = 0.0f
    private var meshHeight: Float = 0.0f

    fun setup(width: Float, height: Float) {
        meshWidth = width
        meshHeight = height

        val stepX = width / cols.toFloat()
        val stepY = height / rows.toFloat()

        var idx = 0
        for (r in 0..rows) {
            val y = r * stepY
            for (c in 0..cols) {
                val x = c * stepX
                origVerts[idx] = x
                origVerts[idx + 1] = y
                currentVerts[idx] = x
                currentVerts[idx + 1] = y
                velocities[idx] = 0.0f
                velocities[idx + 1] = 0.0f
                forces[idx] = 0.0f
                forces[idx + 1] = 0.0f
                idx += 2
            }
        }
    }

    fun setAllWeights(weight: Float) {
        for (i in vertexMaskWeights.indices) {
            vertexMaskWeights[i] = weight.coerceIn(0f, 1f)
        }
    }

    fun setWeightAt(index: Int, weight: Float) {
        if (index in vertexMaskWeights.indices) {
            vertexMaskWeights[index] = weight.coerceIn(0f, 1f)
        }
    }

    fun smoothWeights() {
        val numCols = cols
        val numRows = rows
        val stride = numCols + 1
        val temp = vertexMaskWeights.clone()

        for (r in 1 until numRows) {
            for (c in 1 until numCols) {
                val idx = r * stride + c
                val sum = temp[idx] * 4.0f +
                        temp[idx - 1] + temp[idx + 1] +
                        temp[idx - stride] + temp[idx + stride]
                vertexMaskWeights[idx] = (sum / 8.0f).coerceIn(0f, 1f)
            }
        }
    }

    fun applyTouchDisturbance(
        touchX: Float,
        touchY: Float,
        deltaX: Float,
        deltaY: Float,
        radius: Float,
        impulseFactor: Float
    ) {
        val rSq = radius * radius
        if (rSq <= 0.0f) return

        var idx = 0
        repeat(rows + 1) {
            repeat(cols + 1) {
                val vIdx = idx / 2
                val weight = vertexMaskWeights[vIdx]
                if (weight > 0.01f) {
                    val currX = currentVerts[idx]
                    val currY = currentVerts[idx + 1]

                    val dx = currX - touchX
                    val dy = currY - touchY
                    val distSq = dx * dx + dy * dy

                    if (distSq < rSq) {
                        val norm = 1.0f - (distSq / rSq)
                        val falloff = norm * norm
                        val pushX = if (deltaX == 0f && deltaY == 0f) dx * 0.25f else deltaX * 0.35f
                        val pushY = if (deltaX == 0f && deltaY == 0f) dy * 0.25f else deltaY * 0.35f
                        velocities[idx] += pushX * falloff * weight * (impulseFactor * 0.02f)
                        velocities[idx + 1] += pushY * falloff * weight * (impulseFactor * 0.02f)
                    }
                }
                idx += 2
            }
        }
    }

    fun update(deltaTimeSeconds: Float) {
        // Multi-step physics calculation (6 substeps per frame for extreme precision and zero tearing)
        val substeps = 6
        val subDt = (deltaTimeSeconds / substeps).coerceIn(0.0005f, 0.01f)

        repeat(substeps) {
            updateInternal(subDt)
        }
    }

    private fun updateInternal(dt: Float) {
        val invMass = 1.0f / mass.coerceAtLeast(0.01f)
        val numCols = cols
        val numRows = rows
        val stride = (numCols + 1) * 2

        // 1. Reset forces and accumulate anchor spring restoration & sensor tilt (including corner vertices)
        var idx = 0
        for (r in 0..numRows) {
            for (c in 0..numCols) {
                val vIdx = idx / 2
                val weight = vertexMaskWeights[vIdx]

                if (weight <= 0.001f) {
                    forces[idx] = 0.0f
                    forces[idx + 1] = 0.0f
                } else {
                    val x = currentVerts[idx]
                    val y = currentVerts[idx + 1]
                    val x0 = origVerts[idx]
                    val y0 = origVerts[idx + 1]
                    val vx = velocities[idx]
                    val vy = velocities[idx + 1]

                    forces[idx] = (-stiffness * (x - x0) - damping * vx + tiltX) * weight
                    forces[idx + 1] = (-stiffness * (y - y0) - damping * vy + tiltY) * weight
                }
                idx += 2
            }
        }

        // 2. Neighbor coupling springs for jelly wave propagation
        for (r in 1 until numRows) {
            val rowOffset = r * stride
            for (c in 1 until numCols) {
                val iA = rowOffset + c * 2
                val iB = rowOffset + (c + 1) * 2
                val wA = vertexMaskWeights[iA / 2]
                val wB = vertexMaskWeights[iB / 2]

                if (wA > 0.001f || wB > 0.001f) {
                    val currDeltaX = currentVerts[iB] - currentVerts[iA]
                    val origDeltaX = origVerts[iB] - origVerts[iA]
                    val dispX = currDeltaX - origDeltaX

                    val springF = dispX * neighborCoupling * ((wA + wB) * 0.5f)
                    forces[iA] += springF
                    if (c + 1 < numCols) {
                        forces[iB] -= springF
                    }
                }
            }
        }

        for (c in 1 until numCols) {
            for (r in 1 until numRows) {
                val iA = r * stride + c * 2
                val iB = (r + 1) * stride + c * 2
                val wA = vertexMaskWeights[iA / 2]
                val wB = vertexMaskWeights[iB / 2]

                if (wA > 0.001f || wB > 0.001f) {
                    val currDeltaY = currentVerts[iB + 1] - currentVerts[iA + 1]
                    val origDeltaY = origVerts[iB + 1] - origVerts[iA + 1]
                    val dispY = currDeltaY - origDeltaY

                    val springF = dispY * neighborCoupling * ((wA + wB) * 0.5f)
                    forces[iA + 1] += springF
                    if (r + 1 < numRows) {
                        forces[iB + 1] -= springF
                    }
                }
            }
        }

        // 3. Integration step (Semi-implicit Euler) with strict bounds across full mesh including corners
        idx = 0
        val maxDisplacement = min(meshWidth, meshHeight) * 0.12f

        for (r in 0..numRows) {
            for (c in 0..numCols) {
                val vIdx = idx / 2
                val weight = vertexMaskWeights[vIdx]

                if (weight <= 0.001f) {
                    currentVerts[idx] = origVerts[idx]
                    currentVerts[idx + 1] = origVerts[idx + 1]
                    velocities[idx] = 0.0f
                    velocities[idx + 1] = 0.0f
                } else {
                    val ax = forces[idx] * invMass
                    val ay = forces[idx + 1] * invMass

                    var vx = velocities[idx] + ax * dt
                    var vy = velocities[idx + 1] + ay * dt

                    vx *= 0.98f
                    vy *= 0.98f

                    velocities[idx] = vx
                    velocities[idx + 1] = vy

                    var nx = currentVerts[idx] + vx * dt
                    var ny = currentVerts[idx + 1] + vy * dt

                    val x0 = origVerts[idx]
                    val y0 = origVerts[idx + 1]
                    nx = nx.coerceIn(x0 - maxDisplacement, x0 + maxDisplacement)
                    ny = ny.coerceIn(y0 - maxDisplacement, y0 + maxDisplacement)

                    currentVerts[idx] = origVerts[idx] + (nx - origVerts[idx]) * weight
                    currentVerts[idx + 1] = origVerts[idx + 1] + (ny - origVerts[idx + 1]) * weight
                }
                idx += 2
            }
        }
    }
}
