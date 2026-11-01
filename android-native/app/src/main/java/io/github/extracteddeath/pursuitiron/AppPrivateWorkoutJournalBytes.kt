package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.util.AtomicFile
import io.github.extracteddeath.pursuitiron.domain.WorkoutJournalBytes
import io.github.extracteddeath.pursuitiron.domain.WorkoutJournalPersistence
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

/**
 * App-owned, no-backup crash recovery staging area. Do NOT treat this as
 * canonical v13 history or use it as a source for saved program projection.
 * A SINGLE owner per app process must serialize operations through its
 * WorkoutJournalPersistence instance. Not wired to live Train until parity.
 */
internal class AppPrivateWorkoutJournalBytes(context: Context) : WorkoutJournalBytes {
    private val file = AtomicFile(File(context.noBackupFilesDir, "workout-pending-v1.json"))

    override fun read(): ByteArray? {
        val stream = try {
            file.openRead()
        } catch (_: FileNotFoundException) {
            return null
        }
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val count = input.read(buffer)
                if (count == -1) break
                if (output.size() + count > WorkoutJournalPersistence.MAX_JOURNAL_BYTES)
                    throw IOException("Oversized private workout journal")
                output.write(buffer,0,count)
            }
            return output.toByteArray()
        }
    }

    override fun commitAtomically(bytes: ByteArray) {
        require(bytes.size in 1..WorkoutJournalPersistence.MAX_JOURNAL_BYTES)
        val output = file.startWrite()
        try {
            output.write(bytes)
            file.finishWrite(output)
        } catch (failure: Exception) {
            file.failWrite(output)
            throw failure
        }
    }
}
