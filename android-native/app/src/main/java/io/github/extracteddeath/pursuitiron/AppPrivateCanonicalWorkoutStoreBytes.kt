package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.util.AtomicFile
import io.github.extracteddeath.pursuitiron.domain.CanonicalWorkoutStoreBytes
import io.github.extracteddeath.pursuitiron.domain.CanonicalWorkoutStorePersistence
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * No-backup app-private canonical snapshot owner. Deliberately not instantiated
 * by the preview; a pinned canonical parser/runtime is required for bootstrap.
 * One process-scoped persistence owner must serialize all writes off Main.
 */
internal class AppPrivateCanonicalWorkoutStoreBytes(context: Context) : CanonicalWorkoutStoreBytes {
    private val base=File(context.noBackupFilesDir,"canonical-workout-store-v1.json")
    private val file=AtomicFile(base)
    private fun hasStoredBytes()=base.exists() || File(base.path+".bak").exists() ||
        File(base.path+".new").exists()

    override fun read(): ByteArray? {
        val stream=try { file.openRead() } catch (failure: FileNotFoundException) {
            if (hasStoredBytes()) throw IOException("Existing canonical store is unreadable",failure)
            return null
        }
        stream.use { input ->
            val output=ByteArrayOutputStream()
            val buffer=ByteArray(4096)
            try {
                while (true) {
                    val count=input.read(buffer)
                    if (count==-1) break
                    if (output.size()+count>CanonicalWorkoutStorePersistence.MAX_STORE_BYTES)
                        throw IOException("Oversized private canonical store")
                    output.write(buffer,0,count)
                }
                return output.toByteArray()
            } finally { buffer.fill(0) }
        }
    }

    override fun commitAtomically(bytes: ByteArray) {
        require(bytes.size in 1..CanonicalWorkoutStorePersistence.MAX_STORE_BYTES)
        val output=file.startWrite()
        try {
            output.write(bytes)
            // Surface sync failures before acknowledging a history transaction.
            output.fd.sync()
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
        val observed=read() ?: throw IOException("Canonical store commit missing")
        try {
            if (!observed.contentEquals(bytes)) throw IOException("Canonical store commit mismatch")
        } finally { observed.fill(0) }
    }
}
