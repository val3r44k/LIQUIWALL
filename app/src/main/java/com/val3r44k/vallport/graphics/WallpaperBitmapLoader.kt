package com.val3r44k.vallport.graphics

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.net.Uri
import android.util.Base64
import android.util.Log
import androidx.core.graphics.scale
import com.val3r44k.vallport.data.DropWallConfig
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

/**
 * Handles loading, center-crop scaling, persistence, background inpainting,
 * per-wallpaper unique mask file isolation, and Base64 serialization.
 */
object WallpaperBitmapLoader {

    private const val TAG = "WallpaperBitmapLoader"

    fun loadBackground(
        targetWidth: Int,
        targetHeight: Int,
        customPath: String?,
    ): Bitmap {
        val safeW = maxOf(targetWidth, 1)
        val safeH = maxOf(targetHeight, 1)

        if (!customPath.isNullOrEmpty()) {
            val file = File(customPath)
            if (file.exists() && (file.length() > 0)) {
                try {
                    val decoded = BitmapFactory.decodeFile(file.absolutePath)
                    if (decoded != null) {
                        return createCropBitmap(decoded, safeW, safeH)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load custom wallpaper from $customPath", e)
                }
            }
        }

        return ProceduralWallpaperGenerator.createBackgroundBitmap(safeW, safeH)
    }

    fun loadForeground(
        targetWidth: Int,
        targetHeight: Int,
        customPath: String?,
    ): Bitmap {
        val safeW = maxOf(targetWidth, 1)
        val safeH = maxOf(targetHeight, 1)

        if (!customPath.isNullOrEmpty()) {
            val file = File(customPath)
            if (file.exists() && (file.length() > 0)) {
                try {
                    val decoded = BitmapFactory.decodeFile(file.absolutePath)
                    if (decoded != null) {
                        return createCropBitmap(decoded, safeW, safeH)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load custom foreground photo", e)
                }
            }
        }

        return ProceduralWallpaperGenerator.createForegroundBitmap(safeW, safeH)
    }

    fun loadMask(
        targetWidth: Int,
        targetHeight: Int,
        customMaskPath: String?,
    ): Bitmap? {
        val safeW = maxOf(targetWidth, 1)
        val safeH = maxOf(targetHeight, 1)

        if (!customMaskPath.isNullOrEmpty()) {
            val file = File(customMaskPath)
            if (file.exists() && (file.length() > 0)) {
                try {
                    val decoded = BitmapFactory.decodeFile(file.absolutePath)
                    if (decoded != null) {
                        if (decoded.width == safeW && decoded.height == safeH) {
                            return decoded
                        }
                        val scaled = decoded.scale(safeW, safeH, true)
                        decoded.recycle()
                        return scaled
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load custom mask", e)
                }
            }
        }
        return null
    }

    fun createInpaintedBackground(bg: Bitmap, mask: Bitmap?): Bitmap {
        if (mask == null || mask.isRecycled) return bg
        val inpainted = bg.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(inpainted)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT)
        }
        canvas.drawBitmap(mask, 0f, 0f, paint)
        return inpainted
    }

    /**
     * Serializes a Bitmap into a Base64 string for local storage / transfer.
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        return try {
            val baos = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            Base64.encodeToString(baos.toByteArray(), Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Error encoding bitmap to Base64", e)
            ""
        }
    }

    /**
     * Deserializes a Base64 string back into a Bitmap.
     */
    fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding Base64 to bitmap", e)
            null
        }
    }

    fun saveCustomImageFromUri(context: Context, uri: Uri): String? {
        return try {
            val timestamp = System.currentTimeMillis()
            val targetFile = File(context.filesDir, "custom_wallpaper_$timestamp.png")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom image from Uri", e)
            null
        }
    }

    /**
     * Saves mask bitmap to a UNIQUE file tied specifically to the project's ID.
     */
    fun saveCustomMaskBitmap(context: Context, imagePath: String?, maskBitmap: Bitmap): String? {
        return try {
            val projId = DropWallConfig.getProjectId(imagePath)
            val fileName = "mask_$projId.png"
            val targetFile = File(context.filesDir, fileName)
            FileOutputStream(targetFile).use { out ->
                maskBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            targetFile.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Error saving custom mask bitmap", e)
            null
        }
    }

    fun clearCustomImage(context: Context) {
        val files = context.filesDir.listFiles()
        files?.forEach { file ->
            if (file.name.startsWith("custom_wallpaper")) {
                file.delete()
            }
        }
    }

    fun clearCustomMask(context: Context, imagePath: String? = null) {
        val projId = DropWallConfig.getProjectId(imagePath)
        val fileName = "mask_$projId.png"
        val file = File(context.filesDir, fileName)
        if (file.exists()) {
            file.delete()
        }
    }

    private fun createCropBitmap(source: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val srcW = source.width.toFloat()
        val srcH = source.height.toFloat()
        if (srcW <= 0f || srcH <= 0f) return source

        val scale = max(targetWidth / srcW, targetHeight / srcH)
        val scaledW = srcW * scale
        val scaledH = srcH * scale
        val dx = (targetWidth - scaledW) * 0.5f
        val dy = (targetHeight - scaledH) * 0.5f

        val output = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)

        val matrix = Matrix().apply {
            postScale(scale, scale)
            postTranslate(dx, dy)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, matrix, paint)
        source.recycle()
        return output
    }
}
