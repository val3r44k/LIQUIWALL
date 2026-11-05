package com.val3r44k.vallport.graphics

import androidx.compose.ui.geometry.Offset
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Robust geometric touch gesture detector for identifying closed loops and circular
 * outlines around objects on the masking canvas.
 */
object CircleGestureDetector {

    private const val MIN_POINTS = 14
    private const val MIN_BOUNDS_SIZE = 35.0f
    private const val MIN_PERIMETER = 120.0f
    private const val MAX_CLOSING_GAP_PX = 85.0f
    private const val MAX_CLOSING_GAP_RATIO = 0.28f

    /**
     * Inspects a sequential list of stroke touch points to determine if they form
     * a completed closed loop or circle.
     */
    fun isClosedLoop(points: List<Offset>): Boolean {
        if (points.size < MIN_POINTS) return false

        val first = points.first()
        val last = points.last()

        // 1. Calculate bounding box and total perimeter
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE

        var perimeter = 0.0f
        for ((i, pt) in points.withIndex()) {
            minX = min(minX, pt.x)
            maxX = max(maxX, pt.x)
            minY = min(minY, pt.y)
            maxY = max(maxY, pt.y)

            if (i > 0) {
                val prev = points[i - 1]
                perimeter += hypot(pt.x - prev.x, pt.y - prev.y)
            }
        }

        val boxWidth = maxX - minX
        val boxHeight = maxY - minY
        if ((boxWidth < MIN_BOUNDS_SIZE) || (boxHeight < MIN_BOUNDS_SIZE)) return false
        if (perimeter < MIN_PERIMETER) return false

        // 2. Measure gap between start and end point
        val endGap = hypot(last.x - first.x, last.y - first.y)
        val boxDiagonal = hypot(boxWidth, boxHeight)
        val maxAllowedGap = max(MAX_CLOSING_GAP_PX, boxDiagonal * MAX_CLOSING_GAP_RATIO)

        val isWithinClosingGap = endGap <= maxAllowedGap
        val hasAdequateLoopPerimeter = perimeter >= (boxDiagonal * 1.8f)

        return isWithinClosingGap && hasAdequateLoopPerimeter
    }
}
