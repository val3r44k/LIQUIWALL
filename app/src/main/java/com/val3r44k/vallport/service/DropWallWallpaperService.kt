package com.val3r44k.vallport.service

import android.graphics.Canvas
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.MotionEvent
import android.view.SurfaceHolder
import com.val3r44k.vallport.data.DropWallConfig
import com.val3r44k.vallport.graphics.ParallaxWallpaperRenderer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native Android Live Wallpaper Service — Jelly / Wobble Physics Architecture
 * with robust Accelerometer & Gyroscope sensor binding and instant touch ripple response.
 */
class DropWallWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "DropWallWallpaper"
        private const val TARGET_FPS = 60
        private const val FRAME_INTERVAL_NS = 1_000_000_000L / TARGET_FPS
        private const val SENSOR_SMOOTHING = 0.80f
    }

    override fun onCreateEngine(): Engine {
        return DropWallEngine()
    }

    inner class DropWallEngine : Engine(), SensorEventListener {

        private var config: DropWallConfig = DropWallConfig.load(this@DropWallWallpaperService)
        private val renderer: ParallaxWallpaperRenderer =
            ParallaxWallpaperRenderer(config, this@DropWallWallpaperService)

        private var sensorManager: SensorManager? = null
        private var accelerometer: Sensor? = null
        private var gyroscope: Sensor? = null

        @Volatile
        private var filteredTiltX: Float = 0f
        @Volatile
        private var filteredTiltY: Float = 0f

        private var lastTouchX: Float = 0f
        private var lastTouchY: Float = 0f
        private var isDragging: Boolean = false

        @Volatile
        private var desktopScrollOffset: Float = 0f

        private var renderThread: WallpaperRenderThread? = null
        private val isVisibleState = AtomicBoolean(false)

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(true)
            setOffsetNotificationsEnabled(true)

            sensorManager = getSystemService(SENSOR_SERVICE) as? SensorManager
            accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            gyroscope = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            isVisibleState.set(visible)
            if (visible) {
                config = DropWallConfig.load(this@DropWallWallpaperService)
                renderer.config = config
                renderer.reloadBitmap()
                registerSensors()
                startRenderThread()
            } else {
                unregisterSensors()
                stopRenderThread()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            if (isVisibleState.get()) {
                startRenderThread()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            renderer.onSurfaceDimensionsChanged(width, height)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            stopRenderThread()
        }

        override fun onOffsetsChanged(
            xOffset: Float,
            yOffset: Float,
            xOffsetStep: Float,
            yOffsetStep: Float,
            xPixelOffset: Int,
            yPixelOffset: Int,
        ) {
            super.onOffsetsChanged(xOffset, yOffset, xOffsetStep, yOffsetStep, xPixelOffset, yPixelOffset)
            desktopScrollOffset = (xOffset - 0.5f) * 2f
        }

        override fun onTouchEvent(event: MotionEvent) {
            super.onTouchEvent(event)
            val x = event.x
            val y = event.y

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastTouchX = x
                    lastTouchY = y
                    isDragging = true
                    // Instant touch ripple splash on tap down
                    renderer.onTouchMove(x, y, 1.5f, 1.5f)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) {
                        val dx = x - lastTouchX
                        val dy = y - lastTouchY
                        renderer.onTouchMove(x, y, dx * 2.0f, dy * 2.0f)
                        lastTouchX = x
                        lastTouchY = y
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
        }

        private fun registerSensors() {
            accelerometer?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
            gyroscope?.let { sensor ->
                sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
            }
        }

        private fun unregisterSensors() {
            sensorManager?.unregisterListener(this)
        }

        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    val rawX = -event.values[0]
                    val rawY = event.values[1]
                    filteredTiltX = (SENSOR_SMOOTHING * filteredTiltX) + ((1f - SENSOR_SMOOTHING) * rawX)
                    filteredTiltY = (SENSOR_SMOOTHING * filteredTiltY) + ((1f - SENSOR_SMOOTHING) * rawY)
                }
                Sensor.TYPE_GYROSCOPE -> {
                    val gyroX = event.values[1] * 12f
                    val gyroY = event.values[0] * 12f
                    filteredTiltX += gyroX
                    filteredTiltY += gyroY
                }
            }
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
            // No-op
        }

        @Synchronized
        private fun startRenderThread() {
            if (renderThread == null) {
                renderThread = WallpaperRenderThread(surfaceHolder).apply { start() }
            }
        }

        @Synchronized
        private fun stopRenderThread() {
            renderThread?.let { thread ->
                thread.requestStop()
                try {
                    thread.join(500)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
            renderThread = null
        }

        override fun onDestroy() {
            super.onDestroy()
            unregisterSensors()
            stopRenderThread()
            renderer.release()
        }

        private inner class WallpaperRenderThread(
            private val holder: SurfaceHolder,
        ) : Thread("DropWallRenderThread") {

            private val running = AtomicBoolean(true)

            fun requestStop() {
                running.set(false)
                interrupt()
            }

            override fun run() {
                var lastTimeNs = System.nanoTime()

                while (running.get() && isVisibleState.get()) {
                    val frameStartNs = System.nanoTime()
                    val dtNs = frameStartNs - lastTimeNs
                    lastTimeNs = frameStartNs

                    val dtSeconds = (dtNs / 1_000_000_000.0f).coerceIn(0.001f, 0.033f)

                    renderer.updatePhysics(
                        deltaTimeSeconds = dtSeconds,
                        sensorTiltX = filteredTiltX,
                        sensorTiltY = filteredTiltY,
                        scrollOffsetX = desktopScrollOffset,
                    )

                    drawHardwareFrame()

                    val frameDurationNs = System.nanoTime() - frameStartNs
                    val sleepNs = FRAME_INTERVAL_NS - frameDurationNs
                    if (sleepNs > 1_000_000L) {
                        try {
                            sleep(sleepNs / 1_000_000L, (sleepNs % 1_000_000L).toInt())
                        } catch (_: InterruptedException) {
                            break
                        }
                    }
                }
            }

            private fun drawHardwareFrame() {
                if (!holder.surface.isValid) return

                var canvas: Canvas? = null
                try {
                    canvas = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        holder.lockHardwareCanvas()
                    } else {
                        holder.lockCanvas()
                    }
                    canvas?.let { renderer.renderFrame(it) }
                } catch (e: Exception) {
                    Log.e(TAG, "Error rendering frame", e)
                } finally {
                    if (canvas != null) {
                        try {
                            holder.unlockCanvasAndPost(canvas)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error unlocking canvas", e)
                        }
                    }
                }
            }
        }
    }
}
