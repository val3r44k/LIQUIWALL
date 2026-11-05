package com.val3r44k.vallport.data

import android.content.Context
import androidx.core.content.edit
import java.io.File

/**
 * Configuration parameters for the LIQUIWALL spring-damper physics engine,
 * jelly wobble settings, theme accent color, and 100% isolated per-project state storage.
 */
data class DropWallConfig(
    val stiffness: Float = 60.0f,           // Softer Hooke's spring constant k
    val damping: Float = 16.0f,             // Higher viscous damping coefficient c
    val mass: Float = 1.0f,                 // Point mass m
    val tiltSensitivity: Float = 80.0f,     // High-sensitivity accelerometer/gyroscope force factor
    val touchRadius: Float = 280.0f,        // Pixel radius of touch wave dispersion
    val touchImpulse: Float = 950.0f,       // Magnitude of touch disturbance
    val meshCols: Int = 16,                 // Mesh grid columns for drawBitmapMesh
    val meshRows: Int = 28,                 // Mesh grid rows for drawBitmapMesh
    val parallaxEnabled: Boolean = true,    // Global master toggle for jelly wobble effect
    val autoFillCircle: Boolean = true,     // Smart circle auto-fill toggle
    val themeAccentColor: Long = 0xFFCC0000,// Classic YouTube 2015-2017 flat red accent color
    val customImagePath: String? = null,    // Absolute path to custom background image
    val customMaskPath: String? = null,     // Absolute path to custom saved mask image
    val previewHeightDp: Int = 540,         // Height of the preview window in dp
    val previewRatioNumerator: Float = 9f,  // Aspect ratio width factor (e.g. 9)
    val previewRatioDenominator: Float = 16f, // Aspect ratio height factor (e.g. 16 or 19.5)
    val showQuickActions: Boolean = true,   // Show/hide action buttons row
    val showToolToolbar: Boolean = true,    // Show/hide brush tool toolbar
    val showSettingsTabs: Boolean = true,   // Show/hide settings tuning tabs
) {
    val previewAspectRatio: Float
        get() = if (previewRatioDenominator > 0f) previewRatioNumerator / previewRatioDenominator else (9f / 16f)

    companion object {
        private const val PREFS_NAME = "liquiwall_prefs"
        private const val KEY_STIFFNESS = "key_stiffness"
        private const val KEY_DAMPING = "key_damping"
        private const val KEY_TILT = "key_tilt"
        private const val KEY_TOUCH_RADIUS = "key_touch_radius"
        private const val KEY_TOUCH_IMPULSE = "key_touch_impulse"
        private const val KEY_PARALLAX_ENABLED = "key_parallax_enabled"
        private const val KEY_AUTO_FILL_CIRCLE = "key_auto_fill_circle"
        private const val KEY_ACCENT_COLOR = "key_accent_color"
        private const val KEY_CUSTOM_IMAGE = "key_custom_image"
        private const val KEY_CUSTOM_MASK = "key_custom_mask"
        private const val KEY_PREVIEW_HEIGHT = "key_preview_height"
        private const val KEY_PREVIEW_RATIO_NUM = "key_preview_ratio_num"
        private const val KEY_PREVIEW_RATIO_DENOM = "key_preview_ratio_denom"
        private const val KEY_SHOW_QUICK_ACTIONS = "key_show_quick_actions"
        private const val KEY_SHOW_TOOL_TOOLBAR = "key_show_tool_toolbar"
        private const val KEY_SHOW_SETTINGS_TABS = "key_show_settings_tabs"

        fun getProjectId(imagePath: String?): String {
            return if (!imagePath.isNullOrEmpty()) "proj_${imagePath.hashCode()}" else "proj_default_procedural"
        }

        fun load(context: Context): DropWallConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val activeImagePath = prefs.getString(KEY_CUSTOM_IMAGE, null)
            return loadForWallpaper(context, activeImagePath)
        }

        fun save(context: Context, config: DropWallConfig) {
            saveForWallpaper(context, config)
        }

        /**
         * Loads 100% isolated saved settings (jelly stiffness, damping, tilt sensitivity, touch radius, mask path)
         * specifically for a given project/wallpaper ID.
         */
        fun loadForWallpaper(context: Context, imagePath: String?): DropWallConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val default = DropWallConfig()
            val projId = getProjectId(imagePath)

            val projMaskFile = File(context.filesDir, "mask_$projId.png")
            val defaultMaskPath = if (projMaskFile.exists() && projMaskFile.length() > 0) projMaskFile.absolutePath else null

            return DropWallConfig(
                customImagePath = imagePath,
                customMaskPath = prefs.getString("${projId}_mask", defaultMaskPath),
                stiffness = prefs.getFloat("${projId}_stiffness", default.stiffness),
                damping = prefs.getFloat("${projId}_damping", default.damping),
                tiltSensitivity = prefs.getFloat("${projId}_tilt", default.tiltSensitivity),
                touchRadius = prefs.getFloat("${projId}_touch_radius", default.touchRadius),
                touchImpulse = prefs.getFloat(KEY_TOUCH_IMPULSE, default.touchImpulse),
                parallaxEnabled = prefs.getBoolean(KEY_PARALLAX_ENABLED, default.parallaxEnabled),
                autoFillCircle = prefs.getBoolean("${projId}_autofill", default.autoFillCircle),
                themeAccentColor = prefs.getLong(KEY_ACCENT_COLOR, default.themeAccentColor),
                previewHeightDp = prefs.getInt(KEY_PREVIEW_HEIGHT, default.previewHeightDp),
                previewRatioNumerator = prefs.getFloat(KEY_PREVIEW_RATIO_NUM, default.previewRatioNumerator),
                previewRatioDenominator = prefs.getFloat(KEY_PREVIEW_RATIO_DENOM, default.previewRatioDenominator),
                showQuickActions = prefs.getBoolean(KEY_SHOW_QUICK_ACTIONS, default.showQuickActions),
                showToolToolbar = prefs.getBoolean(KEY_SHOW_TOOL_TOOLBAR, default.showToolToolbar),
                showSettingsTabs = prefs.getBoolean(KEY_SHOW_SETTINGS_TABS, default.showSettingsTabs)
            )
        }

        /**
         * Persists 100% isolated saved settings (jelly stiffness, damping, tilt sensitivity, touch radius, mask path)
         * and updates KEY_CUSTOM_IMAGE so the Live Wallpaper Service always receives the exact active image path.
         */
        fun saveForWallpaper(context: Context, config: DropWallConfig) {
            val projId = getProjectId(config.customImagePath)
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit {
                putString(KEY_CUSTOM_IMAGE, config.customImagePath)
                putLong(KEY_ACCENT_COLOR, config.themeAccentColor)
                putBoolean(KEY_PARALLAX_ENABLED, config.parallaxEnabled)
                putString("${projId}_mask", config.customMaskPath)
                putFloat("${projId}_stiffness", config.stiffness)
                putFloat("${projId}_damping", config.damping)
                putFloat("${projId}_tilt", config.tiltSensitivity)
                putFloat("${projId}_touch_radius", config.touchRadius)
                putBoolean("${projId}_autofill", config.autoFillCircle)
            }
        }
    }
}
