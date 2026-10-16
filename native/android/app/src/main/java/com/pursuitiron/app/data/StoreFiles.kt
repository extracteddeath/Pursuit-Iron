package com.pursuitiron.app.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The training store (`wpb:v1` shape, identical to the PWA) and the live workout snapshot.
 * Writes are atomic: a crash mid-write leaves the previous copy, never a half file. The last good
 * store is also kept as store.prev.json so a bad write can be recovered by hand.
 */
class StoreFiles(context: Context) {
    private val dir = File(context.filesDir, "pursuit").apply { mkdirs() }
    private val store = AtomicFile(File(dir, "store.json"))
    private val prev = AtomicFile(File(dir, "store.prev.json"))
    private val live = AtomicFile(File(dir, "live.json"))

    suspend fun readStore(): String? = read(store)
    suspend fun readLive(): String? = read(live)

    suspend fun writeStore(text: String) = withContext(Dispatchers.IO) {
        read(store)?.let { write(prev, it) }
        write(store, text)
    }

    suspend fun writeLive(text: String) = withContext(Dispatchers.IO) {
        if (text.isEmpty()) live.delete() else write(live, text)
    }

    private suspend fun read(f: AtomicFile): String? = withContext(Dispatchers.IO) {
        runCatching { if (f.baseFile.exists()) String(f.readFully(), Charsets.UTF_8) else null }.getOrNull()
    }

    private fun write(f: AtomicFile, text: String) {
        val out = f.startWrite()
        try {
            out.write(text.toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            throw e
        }
    }
}
