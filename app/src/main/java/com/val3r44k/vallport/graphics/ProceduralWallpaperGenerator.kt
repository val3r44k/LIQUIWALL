package com.val3r44k.vallport.graphics

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import kotlin.math.min

/**
 * Generates high-fidelity procedural wallpaper layers (Background & Masked Foreground)
 * with Origin OS aesthetic (deep fluid gradients, glowing orbs, soft droplet glass).
 *
 * Invoked strictly during setup or orientation changes; never called inside the per-frame render loop.
 */
object ProceduralWallpaperGenerator {

    /**
     * Generates a rich background texture with fluid gradient illumination.
     */
    fun createBackgroundBitmap(width: Int, height: Int): Bitmap {
        val safeW = maxOf(width, 1)
        val safeH = maxOf(height, 1)
        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // 1. Deep atmospheric base gradient
        val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, safeW.toFloat(), safeH.toFloat(),
                intArrayOf(
                    Color.rgb(18, 24, 48),    // Deep midnight indigo
                    Color.rgb(32, 45, 84),    // Twilight slate
                    Color.rgb(15, 20, 36)     // Deep abyss navy
                ),
                floatArrayOf(0.0f, 0.55f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, safeW.toFloat(), safeH.toFloat(), basePaint)

        // 2. Soft glowing ambient light orbs
        val orbRadius = min(safeW, safeH) * 0.45f

        val cyanOrbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                safeW * 0.30f, safeH * 0.35f, orbRadius,
                intArrayOf(Color.argb(120, 56, 189, 248), Color.TRANSPARENT),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(safeW * 0.30f, safeH * 0.35f, orbRadius, cyanOrbPaint)

        val violetOrbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                safeW * 0.75f, safeH * 0.65f, orbRadius * 1.1f,
                intArrayOf(Color.argb(130, 168, 85, 247), Color.TRANSPARENT),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(safeW * 0.75f, safeH * 0.65f, orbRadius * 1.1f, violetOrbPaint)

        val warmOrbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                safeW * 0.50f, safeH * 0.85f, orbRadius * 0.9f,
                intArrayOf(Color.argb(90, 244, 114, 182), Color.TRANSPARENT),
                floatArrayOf(0.0f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(safeW * 0.50f, safeH * 0.85f, orbRadius * 0.9f, warmOrbPaint)

        return bitmap
    }

    /**
     * Generates a masked foreground subject layer (Glassy floating droplet / crystal icon)
     * with an alpha mask for depth-based stereoscopic parallax.
     */
    fun createForegroundBitmap(width: Int, height: Int): Bitmap {
        val safeW = maxOf(width, 1)
        val safeH = maxOf(height, 1)
        val bitmap = Bitmap.createBitmap(safeW, safeH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val centerX = safeW * 0.5f
        val centerY = safeH * 0.45f
        val dropRadius = min(safeW, safeH) * 0.22f

        // Glass fluid droplet shape
        val dropletPath = Path().apply {
            moveTo(centerX, centerY - dropRadius * 1.35f)
            cubicTo(
                centerX + dropRadius * 1.25f, centerY - dropRadius * 0.3f,
                centerX + dropRadius * 1.1f, centerY + dropRadius * 1.0f,
                centerX, centerY + dropRadius * 1.1f
            )
            cubicTo(
                centerX - dropRadius * 1.1f, centerY + dropRadius * 1.0f,
                centerX - dropRadius * 1.25f, centerY - dropRadius * 0.3f,
                centerX, centerY - dropRadius * 1.35f
            )
            close()
        }

        // Inner glowing translucent glass fill
        val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                centerX - dropRadius, centerY - dropRadius,
                centerX + dropRadius, centerY + dropRadius,
                intArrayOf(
                    Color.argb(200, 255, 255, 255),
                    Color.argb(120, 147, 197, 253),
                    Color.argb(180, 192, 132, 252)
                ),
                floatArrayOf(0.0f, 0.45f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(dropletPath, glassPaint)

        // Specular highlight rim
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6.0f
            shader = LinearGradient(
                centerX, centerY - dropRadius * 1.4f,
                centerX, centerY + dropRadius * 1.1f,
                intArrayOf(
                    Color.argb(240, 255, 255, 255),
                    Color.argb(70, 255, 255, 255),
                    Color.argb(10, 255, 255, 255)
                ),
                floatArrayOf(0.0f, 0.5f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawPath(dropletPath, rimPaint)

        // Core light highlight
        val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(190, 255, 255, 255)
            style = Paint.Style.FILL
        }
        canvas.drawOval(
            centerX - dropRadius * 0.35f,
            centerY - dropRadius * 0.85f,
            centerX + dropRadius * 0.15f,
            centerY - dropRadius * 0.45f,
            highlightPaint
        )

        return bitmap
    }
}
