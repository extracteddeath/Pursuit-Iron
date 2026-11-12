package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.Comparator

/**
 * Clean-install restore TRANSACTION, separate from the existing Kotlin engine.
 *
 * This is not an Android import UI, not permission to merge an athlete into an
 * existing installation, and not a recovery approval by itself. The caller
 * must supply an independent, full, staged native-store verifier that reopens
 * the original owner's program, all journals/corrections and entire History,
 * using the normal native decoders. Only after that verifier succeeds can a
 * directory be atomically published. Never use a UI preview as verification.
 *
 * The target MUST NOT exist; even an empty existing store is protected.
 * The caller must not construct/activate the native host concurrently with
 * restore. Cross-process restore attempts share a separate parent file lock.
 */
enum class NativeKotlinCleanRestoreRefusal {
    INVALID_TARGET, EXISTING_TRAINING_DATA, BAD_ARCHIVE,
    UNTRUSTED_STAGING, INVALID_RESTORED_STORE, STAGING_IO,
    ATOMIC_MOVE_UNSUPPORTED, COMMIT_UNCERTAIN
}

sealed interface NativeKotlinCleanRestoreResult {
    data class Committed(
        val owner:NativeKotlinOwnerIdentity,
        val fileCount:Int
    ):NativeKotlinCleanRestoreResult
    data class Rejected(val reason:NativeKotlinCleanRestoreRefusal):
        NativeKotlinCleanRestoreResult
}

object NativeKotlinCleanStoreRestore {
    private val processMutex=Any()
    private val noFollow=LinkOption.NOFOLLOW_LINKS
    private const val MARKER=".restore-in-progress"
    private const val MARKER_BYTES="PursuitIronKotlinStaging-v1"
    private const val MAX_CLEANUP_FILES=8_128
    private const val MAX_RESTORE_FILES=8_000

    private fun existing(path:Path)=Files.exists(path,noFollow)
    private fun validName(name:String)=name=="kotlin-coach-v1"
    private fun privateParent(root:Path):Boolean {
        val parent=root.parent ?:return false
        return root.isAbsolute && root.normalize()==root &&
            validName(root.fileName.toString()) &&
            Files.isDirectory(parent,noFollow) &&
            !Files.isSymbolicLink(parent) &&
            !Files.isSymbolicLink(root)
    }
    private fun writeAndForce(path:Path,contents:ByteArray) {
        FileChannel.open(path,StandardOpenOption.WRITE,
            StandardOpenOption.CREATE_NEW).use {channel->
            val data=ByteBuffer.wrap(contents)
            while(data.hasRemaining()) channel.write(data)
            channel.force(true)
        }
    }
    private fun forcedDirectory(path:Path) {
        FileChannel.open(path,StandardOpenOption.READ).use {
            it.force(true)
        }
    }

    // Sync each nested directory as well as the top-level staging entry.
    // A durable file payload without durable directory entries is not a
    // useful several-month backup after sudden device power loss.
    private fun forceStagedDirectories(stage:Path) {
        val dirs=mutableListOf<Path>()
        Files.walk(stage).use {walk->
            val it=walk.iterator()
            while(it.hasNext()) {
                val path=it.next()
                if(Files.isSymbolicLink(path))
                    throw IOException("staging link not allowed")
                if(Files.isDirectory(path,noFollow))dirs.add(path)
            }
        }
        dirs.sortWith(Comparator {a,b->b.nameCount.compareTo(a.nameCount)})
        dirs.forEach(::forcedDirectory)
    }

    /** Used only to remove staging that this gate created, never app data. */
    private fun purgePrivateStage(stage:Path,ownedByThisAttempt:Boolean=false) {
        if(!existing(stage))return
        if(Files.isSymbolicLink(stage) ||
            !Files.isDirectory(stage,noFollow))
            throw IOException("unsafe existing staging directory")
        val marker=stage.resolve(MARKER)
        if(existing(marker)) {
            if(Files.isSymbolicLink(marker) ||
                !Files.isRegularFile(marker,noFollow))
                throw IOException("unsafe staging marker path")
            if(!ownedByThisAttempt &&
                Files.readAllBytes(marker).toString(Charsets.UTF_8)!=MARKER_BYTES)
                throw IOException("unknown staging directory: manual review required")
        } else if(!ownedByThisAttempt) {
            // A crash between removing the marker and the atomic rename
            // requires manual review on the next run. Never infer origin.
            throw IOException("unmarked staging directory: manual review required")
        }
        val paths=mutableListOf<Path>()
        Files.walk(stage).use {walk->
            val it=walk.iterator()
            while(it.hasNext()) {
                val path=it.next()
                if(Files.isSymbolicLink(path) ||
                    (!Files.isDirectory(path,noFollow) &&
                     !Files.isRegularFile(path,noFollow)))
                    throw IOException("unsafe stale staging path")
                if(paths.size>=MAX_CLEANUP_FILES)
                    throw IOException("unbounded stale staging contents")
                paths+=path
            }
        }
        paths.sortWith(Comparator {a,b->b.nameCount.compareTo(a.nameCount)})
        for(path in paths)Files.delete(path)
    }

    /**
     * Reverify the exact decrypted bytes AFTER any caller-provided real
     * store verification. Native History readers may create ephemeral locks,
     * but they may never replace journal files or inject a new permanent
     * training record while the restore is being authorized.
     */
    private fun exactStagedBytes(
        stage:Path,
        files:List<NativeKotlinTrainingSnapshotFile>
    ):Boolean {
        val expected=files.associateBy{it.relativePath}
        if(expected.size!=files.size)return false
        val seen=mutableSetOf<String>()
        Files.walk(stage).use {walk->
            val it=walk.iterator()
            while(it.hasNext()) {
                val path=it.next()
                if(Files.isSymbolicLink(path))return false
                if(Files.isDirectory(path,noFollow))continue
                if(!Files.isRegularFile(path,noFollow))return false
                val relative=stage.relativize(path).toString()
                    .replace(java.io.File.separatorChar,'/')
                if(relative==MARKER || relative.endsWith(".lock"))continue
                val original=expected[relative] ?:return false
                if(!seen.add(relative) ||
                    !Files.readAllBytes(path).contentEquals(original.contents))
                    return false
            }
        }
        return seen.size==expected.size
    }

    /**
     * Restore only into a MISSING app-private training directory. The
     * independent verifier is REQUIRED; it reads the staging location with
     * the real native decoders and returns false for *any* bad record.
     *
     * A stale partial stage from an interrupted attempt is cleared only
     * when its trusted marker exists. Crashing after atomic publication
     * leaves the entire validated store, never a partial target directory.
     */
    fun restoreToAbsentStore(
        target:Path,
        encryptedArchive:ByteArray,
        password:CharArray,
        expectedOwner:NativeKotlinOwnerIdentity?,
        verifyStaging:(Path,NativeKotlinOwnerIdentity)->Boolean
    ):NativeKotlinCleanRestoreResult {
        if(!privateParent(target))
            return NativeKotlinCleanRestoreResult.Rejected(
                NativeKotlinCleanRestoreRefusal.INVALID_TARGET)
        // Authenticate input BEFORE creating even a staging directory.
        val archive=when(val result=NativeKotlinEncryptedTrainingSnapshot.inspect(
            encryptedArchive,password,expectedOwner)) {
            is NativeKotlinTrainingSnapshotRead.Verified ->result
            is NativeKotlinTrainingSnapshotRead.Rejected ->
                return NativeKotlinCleanRestoreResult.Rejected(
                    NativeKotlinCleanRestoreRefusal.BAD_ARCHIVE)
        }
        if(archive.files.isEmpty() || archive.files.size>MAX_RESTORE_FILES)
            return NativeKotlinCleanRestoreResult.Rejected(
                NativeKotlinCleanRestoreRefusal.BAD_ARCHIVE)
        val parent=target.parent
        val stage=parent.resolve(".kotlin-coach-v1.restore-staging")
        val lock=parent.resolve(".kotlin-coach-v1.restore-gate.lock")
        return synchronized(processMutex) {
            try {
                if(Files.isSymbolicLink(lock))
                    return@synchronized NativeKotlinCleanRestoreResult.Rejected(
                        NativeKotlinCleanRestoreRefusal.UNTRUSTED_STAGING)
                FileChannel.open(lock,StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE).use {channel->
                    channel.lock().use {
                        if(existing(target))
                            return@synchronized NativeKotlinCleanRestoreResult.Rejected(
                                NativeKotlinCleanRestoreRefusal.EXISTING_TRAINING_DATA)
                        if(existing(stage)) {
                            try {
                                purgePrivateStage(stage)
                            } catch(_:Exception) {
                                return@synchronized NativeKotlinCleanRestoreResult.Rejected(
                                    NativeKotlinCleanRestoreRefusal.UNTRUSTED_STAGING)
                            }
                        }
                        Files.createDirectory(stage)
                        val stagingOwnedByThisAttempt=true
                        try {
                            // Include marker creation in rollback scope:
                            // disk-full or interrupted first write must not
                            // strand an untrusted but known empty stage.
                            writeAndForce(stage.resolve(MARKER),
                                MARKER_BYTES.toByteArray(Charsets.UTF_8))
                            for(file in archive.files) {
                                val relative=Paths.get(file.relativePath)
                                val path=stage.resolve(relative).normalize()
                                if(!path.startsWith(stage) || path==stage)
                                    throw IOException("traversal in restored archive")
                                Files.createDirectories(path.parent)
                                if(!path.parent.startsWith(stage))
                                    throw IOException("path escapes staging")
                                writeAndForce(path,file.contents)
                            }
                            // Check that every decrypted byte actually landed.
                            for(file in archive.files) {
                                val path=stage.resolve(file.relativePath)
                                if(!Files.isRegularFile(path,noFollow) ||
                                    Files.isSymbolicLink(path) ||
                                    !Files.readAllBytes(path).contentEquals(file.contents))
                                    throw IOException("staging content mismatch")
                            }
                            // A successful decryption ALONE cannot grant
                            // a restore: caller must validate every ledger,
                            // approval and actual receipt from this directory.
                            val verified=runCatching {
                                verifyStaging(stage,archive.owner)
                            }.getOrDefault(false)
                            if(!verified ||
                                !exactStagedBytes(stage,archive.files))
                                return@synchronized NativeKotlinCleanRestoreResult.Rejected(
                                    NativeKotlinCleanRestoreRefusal.INVALID_RESTORED_STORE)
                            if(existing(target))
                                return@synchronized NativeKotlinCleanRestoreResult.Rejected(
                                    NativeKotlinCleanRestoreRefusal.EXISTING_TRAINING_DATA)
                            // Marker is not part of the real native training
                            // store. Before publication everything is still
                            // private staging, never live app data.
                            Files.delete(stage.resolve(MARKER))
                            forceStagedDirectories(stage)
                            if(existing(target))
                                return@synchronized NativeKotlinCleanRestoreResult.Rejected(
                                    NativeKotlinCleanRestoreRefusal.EXISTING_TRAINING_DATA)
                            try {
                                Files.move(stage,target,StandardCopyOption.ATOMIC_MOVE)
                            } catch(_:AtomicMoveNotSupportedException) {
                                return@synchronized NativeKotlinCleanRestoreResult.Rejected(
                                    NativeKotlinCleanRestoreRefusal.ATOMIC_MOVE_UNSUPPORTED)
                            }
                            // This is one atomic namespace change. Do not
                            // delete target on post-commit fsync failure:
                            // the result is uncertain, never a rollback.
                            try {
                                forcedDirectory(parent)
                            } catch(_:Exception) {
                                return@synchronized NativeKotlinCleanRestoreResult.Rejected(
                                    NativeKotlinCleanRestoreRefusal.COMMIT_UNCERTAIN)
                            }
                            NativeKotlinCleanRestoreResult.Committed(
                                archive.owner,archive.files.size)
                        } finally {
                            // A validated target is never deleted here.
                            // Aborted stage after removing its marker is
                            // left quarantined for manual review instead
                            // of accidentally deleting foreign files.
                            if(existing(stage))runCatching {
                                // The marker is deliberately removed just
                                // before commit; if the atomic rename fails,
                                // safely clean this attempt's known staging.
                                // A later process never gets this privilege.
                                purgePrivateStage(stage,stagingOwnedByThisAttempt)
                            }
                        }
                    }
                }
            } catch(_:Exception) {
                NativeKotlinCleanRestoreResult.Rejected(
                    NativeKotlinCleanRestoreRefusal.STAGING_IO)
            }
        }
    }
}
