package com.pursuitiron.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Progress photos (the design's You → Body). Rules, since the web app never had them:
 * - Private: kept in this app's own storage, never in the JSON backup, never in cloud backup.
 *   A phone-to-phone transfer carries them (data_extraction_rules device-transfer only).
 * - Small: each photo is re-encoded to at most 1600 px on the long side, JPEG 85, EXIF orientation applied
 *   and all other metadata (location included) dropped.
 * - Deleting a photo deletes the file. Uninstalling the app deletes them all.
 */
class Photos(context: Context) {
    data class Photo(val id: String, val date: Long, val pose: String, val file: File)

    private val dir = File(context.filesDir, "photos").apply { mkdirs() }
    private val index = File(dir, "index.json")
    private val resolver = context.contentResolver

    fun list(): List<Photo> = runCatching {
        val a = JSONArray(index.takeIf { it.exists() }?.readText() ?: "[]")
        (0 until a.length()).mapNotNull { i ->
            val o = a.optJSONObject(i) ?: return@mapNotNull null
            val f = File(dir, o.optString("id") + ".jpg")
            if (f.exists()) Photo(o.optString("id"), o.optLong("date"), o.optString("pose", "Front"), f) else null
        }.sortedBy { it.date }
    }.getOrDefault(emptyList())

    suspend fun add(uri: Uri, pose: String): Photo? = withContext(Dispatchers.IO) {
        runCatching {
            val bmp = decodeScaled(uri, 1600) ?: return@runCatching null
            val id = "ph" + System.currentTimeMillis()
            val f = File(dir, "$id.jpg")
            f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            bmp.recycle()
            val p = Photo(id, System.currentTimeMillis(), pose, f)
            write(list() + p)
            p
        }.getOrNull()
    }

    /** Reset everything: every photo file and the index. */
    fun clear() { dir.listFiles()?.forEach { it.delete() }; index.delete() }

    fun delete(id: String) {
        File(dir, "$id.jpg").delete()
        write(list().filter { it.id != id })
    }

    fun setPose(id: String, pose: String) = write(list().map { if (it.id == id) it.copy(pose = pose) else it })

    private fun write(items: List<Photo>) {
        val a = JSONArray(); items.forEach { a.put(JSONObject().put("id", it.id).put("date", it.date).put("pose", it.pose)) }
        val tmp = File(dir, "index.json.tmp"); tmp.writeText(a.toString()); tmp.renameTo(index)
    }

    private fun decodeScaled(uri: Uri, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val raw = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90; ExifInterface.ORIENTATION_ROTATE_180 -> 180; ExifInterface.ORIENTATION_ROTATE_270 -> 270; else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        val scale = minOf(1f, maxSide / maxOf(raw.width, raw.height).toFloat())
        if (rotation == 0 && scale >= 1f) return raw
        val m = Matrix().apply { if (scale < 1f) postScale(scale, scale); if (rotation != 0) postRotate(rotation.toFloat()) }
        return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, m, true).also { if (it !== raw) raw.recycle() }
    }
}
