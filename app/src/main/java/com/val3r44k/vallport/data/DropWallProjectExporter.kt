package com.val3r44k.vallport.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object DropWallProjectExporter {
    fun generateProjectJson(context: Context, config: DropWallConfig, strokeCount: Int, filledLoopCount: Int): String {
        return "{\"app\":\"LIQUIWALL\",\"stiffness\":${config.stiffness},\"damping\":${config.damping},\"strokes\":$strokeCount}"
    }

    fun writeJsonToUri(context: Context, uri: Uri, json: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri)?.use {
                it.write(json.toByteArray())
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    fun exportToDocuments(context: Context, config: DropWallConfig, strokeCount: Int, filledLoopCount: Int): File? {
        return null
    }
}
