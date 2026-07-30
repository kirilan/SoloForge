package com.kbul.spicycrab.domain.nutrition

import android.content.Context
import java.io.File

object FoodPhotoFiles {
    fun deleteOwned(context: Context, path: String?) {
        if (path == null) return
        val filesDir = runCatching { context.filesDir.canonicalFile }.getOrNull() ?: return
        val target = runCatching { File(path).canonicalFile }.getOrNull() ?: return
        if (target.parentFile == filesDir && target.name.startsWith("food_")) {
            runCatching { target.delete() }
        }
    }

    fun deleteCapture(context: Context, file: File?) {
        if (file == null) return
        val cacheDir = runCatching { context.cacheDir.canonicalFile }.getOrNull() ?: return
        val target = runCatching { file.canonicalFile }.getOrNull() ?: return
        if (target.parentFile == cacheDir && target.name.startsWith("capture_")) {
            runCatching { target.delete() }
        }
    }
}
