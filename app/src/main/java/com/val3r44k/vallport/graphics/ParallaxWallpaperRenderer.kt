package com.val3r44k.vallport.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import com.val3r44k.vallport.data.DropWallConfig
import com.val3r44k.vallport.physics.SpringMeshPhysics

/**
 * Jelly / Wobble Physics Wallpaper Renderer.
 * High-sensitivity accelerometer & gyroscope sensor mapping for instant fluid response.
 */
class ParallaxWallpaperRenderer(
    var config: DropWallConfig = DropWallConfig(),
    private val context: Context? = null,
) {
    val meshPhysics = SpringMeshPhysics(cols = config.meshCols, rows = config.meshRows)

    private var wallpaperBitmap: Bitmap? = null
    private var maskBitmap: Bitmap? = null

    var width: Int = 0
        private set
    var height: Int = 0
        private set

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun onSurfaceDimensionsChanged(newWidth: Int, newHeight: Int) {
        if (newWidth <= 0 || newHeight <= 0) return
        val sizeChanged = (width != newWidth) || (height != newHeight)
        width = newWidth
        height = newHeight

        if (sizeChanged) {
            meshPhysics.stiffness = config.stiffness
            meshPhysics.damping = config.damping
            meshPhysics.mass = config.mass
            meshPhysics.setup(width.toFloat(), height.toFloat())
            reloadBitmap()
        }
    }

    fun reloadBitmap() {
        if (width <= 0 || height <= 0) return

        wallpaperBitmap?.recycle()
        wallpaperBitmap = null

        if (context != null) {
            wallpaperBitmap = WallpaperBitmapLoader.loadBackground(
                context = context,
                targetWidth = width,
                targetHeight = height,
                customPath = config.customImagePath,
            )
            val loadedMask = WallpaperBitmapLoader.loadMask(
                targetWidth = width,
                targetHeight = height,
                customMaskPath = config.customMaskPath,
            )
            if (loadedMask != null) {
                maskBitmap?.recycle()
                maskBitmap = loadedMask
            }
        } else {
            wallpaperBitmap = ProceduralWallpaperGenerator.createBackgroundBitmap(width, height)
        }

        updateVertexMaskWeights()
    }

    fun setCustomMask(mask: Bitmap?) {
        maskBitmap?.recycle()
        maskBitmap = mask
        updateVertexMaskWeights()
    }

    private fun updateVertexMaskWeights() {
        val mask = maskBitmap
        val numCols = meshPhysics.cols
        val numRows = meshPhysics.rows

        var idx = 0
        for (r in 0..numRows) {
            for (c in 0..numCols) {
                val vIdx = idx / 2
                val vx = meshPhysics.origVerts[idx]
                val vy = meshPhysics.origVerts[idx + 1]

                var weight = 1.0f
                if (mask != null && !mask.isRecycled) {
                    val px = vx.toInt().coerceIn(0, mask.width - 1)
                    val py = vy.toInt().coerceIn(0, mask.height - 1)
                    val pixel = mask.getPixel(px, py)
                    weight = Color.alpha(pixel) / 255.0f
                }
                meshPhysics.setWeightAt(vIdx, weight)
                idx += 2
            }
        }
        meshPhysics.smoothWeights()
        meshPhysics.smoothWeights()
    }

    fun updatePhysics(
        deltaTimeSeconds: Float,
        sensorTiltX: Float,
        sensorTiltY: Float,
        scrollOffsetX: Float = 0f,
    ) {
        if (!config.parallaxEnabled) {
            meshPhysics.setup(width.toFloat(), height.toFloat())
            return
        }

        meshPhysics.stiffness = config.stiffness
        meshPhysics.damping = config.damping

        // High-sensitivity sensor multiplier for instant fluid response to slight tilts & shakes
        val sensitivityMultiplier = (config.tiltSensitivity / 30.0f).coerceIn(0.2f, 5.0f)
        val tiltX = (sensorTiltX * 75.0f * sensitivityMultiplier) + (scrollOffsetX * width * 0.2f)
        val tiltY = sensorTiltY * 75.0f * sensitivityMultiplier

        meshPhysics.tiltX = tiltX
        meshPhysics.tiltY = tiltY
        meshPhysics.update(deltaTimeSeconds)
    }

    fun onTouchMove(touchX: Float, touchY: Float, deltaX: Float, deltaY: Float) {
        if (!config.parallaxEnabled) return
        meshPhysics.applyTouchDisturbance(
            touchX = touchX,
            touchY = touchY,
            deltaX = deltaX,
            deltaY = deltaY,
            radius = config.touchRadius,
            impulseFactor = config.touchImpulse,
        )
    }

    fun renderFrame(canvas: Canvas) {
        canvas.drawColor(Color.BLACK, PorterDuff.Mode.CLEAR)

        val bmp = wallpaperBitmap ?: return
        if (bmp.isRecycled) return

        canvas.drawBitmapMesh(
            bmp,
            meshPhysics.cols,
            meshPhysics.rows,
            meshPhysics.currentVerts,
            0,
            null,
            0,
            paint,
        )
    }

    fun release() {
        wallpaperBitmap?.recycle()
        wallpaperBitmap = null
        maskBitmap?.recycle()
        maskBitmap = null
    }
}
