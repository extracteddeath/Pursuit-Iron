package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * One native-owned, owner-scoped transaction fence shared by workout issuance
 * AND every revision-mutating write in the athlete/profile/program/gym/grant
 * store. A second validation before rename is not enough on its own: without
 * this lock, approval can change AFTER validation but BEFORE durable issuance.
 *
 * This file lock works across processes; the process-wide mutex also prevents
 * OverlappingFileLockException between JVM threads/instances. It is NOT an
 * authentication service and does not, by itself, make foreign stores join a
 * transaction. A production app MUST route every applicable approval mutation,
 * account switch and sign-out through the same fence/root, or Kotlin issuance
 * cannot claim cross-store consistency.
 *
 * Lock order is always approval fence -> workout journal lock. Program writers
 * must never acquire a journal lock and then attempt this fence.
 */
class NativeKotlinApprovalRevisionFence(private val appPrivateDirectory: Path) {
    companion object {
        private val processMutex=Any()
        // The coordinator already holds this file lock when it queries the
        // persistent native authority. Reentrant acquisition of a JVM file
        // lock throws OverlappingFileLockException; nesting must reuse it.
        private val ownedPaths=ThreadLocal.withInitial { mutableSetOf<String>() }
        private val validOwner=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    }
    fun <T> withOwnerTransaction(ownerKey:String,operation:()->T):T {
        require(validOwner.matches(ownerKey)) {
            "Invalid owner scope for approval revision transaction"
        }
        val bytes=MessageDigest.getInstance("SHA-256")
            .digest(ownerKey.toByteArray(Charsets.UTF_8))
        val hash=bytes.joinToString("") { "%02x".format(it.toInt() and 255) }
        return synchronized(processMutex) {
            Files.createDirectories(appPrivateDirectory)
            val lock=appPrivateDirectory.resolve("coach-approval-$hash.lock")
                .toAbsolutePath().normalize()
            val key=lock.toString()
            val held=ownedPaths.get()
            if(key in held) {
                operation()
            } else {
                FileChannel.open(lock,
                    StandardOpenOption.CREATE,StandardOpenOption.WRITE).use { channel ->
                    channel.lock().use {
                        held.add(key)
                        try { operation() }
                        finally { held.remove(key) }
                    }
                }
            }
        }
    }
}
