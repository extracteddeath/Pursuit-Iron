package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * First part of PRIVATE Kotlin beta recovery: a bounded, AES-GCM authenticated
 * snapshot of the entire native training store (INCLUDING local-athlete.bin,
 * signed programs, both journal kinds, corrections, machine choices, consent
 * and history files). There is deliberately NO automatic importer and no UI
 * export until restoration on a clean device has been proven separately.
 *
 * This class NEVER writes to the store. The owner-file transaction fence and
 * a fresh verified-History callback are mandatory. A failed snapshot does not
 * alter any workout. Only known ephemeral lock files are excluded.
 *
 * Binary format PISB v1: magic, format, PBKDF2 iterations, salt, nonce,
 * AES-GCM tag-protected inner owner/athlete and length-prefixed file entries.
 * Passwords are supplied by the user and owned/cleared by the caller.
 */
enum class NativeKotlinTrainingSnapshotRefusal {
    INVALID_OWNER, WEAK_PASSWORD, UNVERIFIED_HISTORY, INVALID_STORAGE,
    CHANGED_DURING_READ, TOO_LARGE, CRYPTO_UNAVAILABLE,
    INVALID_ARCHIVE_OR_PASSWORD, OWNER_MISMATCH
}

sealed interface NativeKotlinTrainingSnapshotResult {
    data class Ready(
        val encrypted:ByteArray,
        val fileCount:Int,
        val unencryptedBytes:Long
    ):NativeKotlinTrainingSnapshotResult
    data class Rejected(val reason:NativeKotlinTrainingSnapshotRefusal):
        NativeKotlinTrainingSnapshotResult
}

data class NativeKotlinTrainingSnapshotFile(
    val relativePath:String,
    val contents:ByteArray
)

sealed interface NativeKotlinTrainingSnapshotRead {
    data class Verified(
        val owner:NativeKotlinOwnerIdentity,
        /** Values must remain private; no write-to-disk API is implied. */
        val files:List<NativeKotlinTrainingSnapshotFile>
    ):NativeKotlinTrainingSnapshotRead
    data class Rejected(val reason:NativeKotlinTrainingSnapshotRefusal):
        NativeKotlinTrainingSnapshotRead
}

object NativeKotlinEncryptedTrainingSnapshot {
    private const val MAGIC=0x50495342 // PISB
    private const val INNER_MAGIC=0x50494246 // PIBF
    private const val VERSION=1
    private const val ITERATIONS=250_000
    private const val SALT_BYTES=16
    private const val NONCE_BYTES=12
    private const val HEADER_BYTES=4+4+4+SALT_BYTES+NONCE_BYTES
    private const val MAX_FILES=8_000
    private const val MAX_BYTES=32*1024*1024
    private const val MAX_FILE_BYTES=16*1024*1024
    private const val MAX_PATH=512
    private val validId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val noFollow=LinkOption.NOFOLLOW_LINKS

    private fun safePath(name:String):Boolean =
        name.isNotEmpty() && name.length<=MAX_PATH &&
            !name.startsWith("/") && !name.contains('\\') &&
            name.split('/').all { part ->
                part.isNotEmpty() && part!="." && part!=".." &&
                    part.none { it.code<32 || it.code==127 }
            }

    private fun passwordValid(password:CharArray):Boolean =
        password.size>=12 && password.size<=256 &&
            password.any { !it.isWhitespace() }

    private fun key(password:CharArray,salt:ByteArray):ByteArray {
        val spec=PBEKeySpec(password,salt,ITERATIONS,256)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun encrypt(payload:ByteArray,password:CharArray):ByteArray {
        val salt=ByteArray(SALT_BYTES).also{SecureRandom().nextBytes(it)}
        val nonce=ByteArray(NONCE_BYTES).also{SecureRandom().nextBytes(it)}
        val header=ByteArrayOutputStream()
        DataOutputStream(header).use {
            it.writeInt(MAGIC)
            it.writeInt(VERSION)
            it.writeInt(ITERATIONS)
            it.write(salt)
            it.write(nonce)
        }
        val keyBytes=key(password,salt)
        return try {
            val cipher=Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE,
                SecretKeySpec(keyBytes,"AES"),GCMParameterSpec(128,nonce))
            val headerBytes=header.toByteArray()
            cipher.updateAAD(headerBytes)
            headerBytes+cipher.doFinal(payload)
        } finally {
            keyBytes.fill(0)
            payload.fill(0)
        }
    }

    private fun readFiles(root:Path):List<NativeKotlinTrainingSnapshotFile> {
        if(Files.isSymbolicLink(root) ||
            !Files.isDirectory(root,noFollow))
            throw IllegalArgumentException("missing private store")
        val files=mutableListOf<NativeKotlinTrainingSnapshotFile>()
        var total=0L
        Files.walk(root).use { stream ->
            val iterator=stream.iterator()
            while(iterator.hasNext()) {
                val file=iterator.next()
                if(file==root)continue
                if(Files.isSymbolicLink(file))
                    throw IllegalArgumentException("private store contains link")
                if(Files.isDirectory(file,noFollow))continue
                if(!Files.isRegularFile(file,noFollow))
                    throw IllegalArgumentException("unexpected private store file type")
                // Only lock files are ignored. Interrupted atomic .new/.bak
                // records are preserved rather than silently discarded.
                if(file.fileName.toString().endsWith(".lock"))continue
                val relative=root.relativize(file).toString()
                    .replace(java.io.File.separatorChar,'/')
                if(!safePath(relative) || files.size>=MAX_FILES)
                    throw IllegalArgumentException("unsafe or excessive private paths")
                val size=Files.size(file)
                if(size<0 || size>MAX_FILE_BYTES || total+size>MAX_BYTES)
                    throw IllegalArgumentException("private store too large")
                val bytes=Files.readAllBytes(file)
                if(bytes.size.toLong()!=size)
                    throw IllegalArgumentException("changed private file")
                total+=bytes.size.toLong()
                files+=NativeKotlinTrainingSnapshotFile(relative,bytes)
            }
        }
        files.sortBy {it.relativePath}
        if(files.none {it.relativePath=="local-athlete.bin" ||
                it.relativePath=="local-athlete.bin.bak"})
            throw IllegalArgumentException("no local owner identity in snapshot")
        return files
    }

    private fun unchanged(
        before:List<NativeKotlinTrainingSnapshotFile>,
        after:List<NativeKotlinTrainingSnapshotFile>
    ):Boolean =before.size==after.size && before.indices.all {
        before[it].relativePath==after[it].relativePath &&
            before[it].contents.contentEquals(after[it].contents)
    }

    /**
     * The caller supplies the owner who explicitly requested export, the live
     * identity authority, and a real History validation that runs INSIDE the
     * same owner fence. No temporary unencrypted file is ever produced.
     */
    fun capture(
        root:Path,
        fence:NativeKotlinApprovalRevisionFence,
        requested:NativeKotlinOwnerIdentity,
        currentOwner:()->NativeKotlinOwnerIdentity?,
        verifyHistory:()->Boolean,
        password:CharArray
    ):NativeKotlinTrainingSnapshotResult {
        if(!validId.matches(requested.ownerKey) ||
            !validId.matches(requested.athleteId))
            return NativeKotlinTrainingSnapshotResult.Rejected(
                NativeKotlinTrainingSnapshotRefusal.INVALID_OWNER)
        if(!passwordValid(password))
            return NativeKotlinTrainingSnapshotResult.Rejected(
                NativeKotlinTrainingSnapshotRefusal.WEAK_PASSWORD)
        return try {
            fence.withOwnerTransaction(requested.ownerKey) {
                if(currentOwner()!=requested)
                    return@withOwnerTransaction NativeKotlinTrainingSnapshotResult.Rejected(
                        NativeKotlinTrainingSnapshotRefusal.OWNER_MISMATCH)
                if(!verifyHistory())
                    return@withOwnerTransaction NativeKotlinTrainingSnapshotResult.Rejected(
                        NativeKotlinTrainingSnapshotRefusal.UNVERIFIED_HISTORY)
                val first=readFiles(root)
                // Detect stores that do not use the shared approval fence.
                // Never present an inconsistent mid-write copy as a backup.
                val again=readFiles(root)
                if(!unchanged(first,again) || currentOwner()!=requested)
                    return@withOwnerTransaction NativeKotlinTrainingSnapshotResult.Rejected(
                        NativeKotlinTrainingSnapshotRefusal.CHANGED_DURING_READ)
                val plaintext=ByteArrayOutputStream()
                DataOutputStream(plaintext).use {out->
                    out.writeInt(INNER_MAGIC)
                    out.writeUTF(requested.ownerKey)
                    out.writeUTF(requested.athleteId)
                    out.writeInt(first.size)
                    for(file in first) {
                        out.writeUTF(file.relativePath)
                        out.writeInt(file.contents.size)
                        out.write(file.contents)
                    }
                }
                val size=first.sumOf {it.contents.size.toLong()}
                NativeKotlinTrainingSnapshotResult.Ready(
                    encrypt(plaintext.toByteArray(),password),
                    first.size,size)
            }
        } catch(ex:IllegalArgumentException) {
            val size=ex.message?.contains("large")==true ||
                ex.message?.contains("excessive")==true
            NativeKotlinTrainingSnapshotResult.Rejected(
                if(size)NativeKotlinTrainingSnapshotRefusal.TOO_LARGE
                else NativeKotlinTrainingSnapshotRefusal.INVALID_STORAGE)
        } catch(_:Exception) {
            NativeKotlinTrainingSnapshotResult.Rejected(
                NativeKotlinTrainingSnapshotRefusal.CRYPTO_UNAVAILABLE)
        }
    }

    /**
     * Authenticity/format proof for future explicit restore workflow.
     * Import must still verify fresh device ownership and stage an atomic,
     * rollback-safe restore; merely decoding bytes is never an authorization.
     */
    fun inspect(
        archive:ByteArray,password:CharArray,
        expectedOwner:NativeKotlinOwnerIdentity?=null
    ):NativeKotlinTrainingSnapshotRead {
        if(!passwordValid(password) ||
            archive.size<=HEADER_BYTES+16 ||
            archive.size>MAX_BYTES+MAX_FILES*(MAX_PATH+8)+4096)
            return NativeKotlinTrainingSnapshotRead.Rejected(
                NativeKotlinTrainingSnapshotRefusal.INVALID_ARCHIVE_OR_PASSWORD)
        return try {
            val header=archive.copyOfRange(0,HEADER_BYTES)
            val input=DataInputStream(ByteArrayInputStream(header))
            if(input.readInt()!=MAGIC || input.readInt()!=VERSION ||
                input.readInt()!=ITERATIONS)
                return NativeKotlinTrainingSnapshotRead.Rejected(
                    NativeKotlinTrainingSnapshotRefusal.INVALID_ARCHIVE_OR_PASSWORD)
            val salt=ByteArray(SALT_BYTES).also {input.readFully(it)}
            val nonce=ByteArray(NONCE_BYTES).also {input.readFully(it)}
            val keyBytes=key(password,salt)
            val decrypted=try {
                val cipher=Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE,
                    SecretKeySpec(keyBytes,"AES"),GCMParameterSpec(128,nonce))
                cipher.updateAAD(header)
                cipher.doFinal(archive.copyOfRange(HEADER_BYTES,archive.size))
            } finally {keyBytes.fill(0)}
            try {
                if(decrypted.size>MAX_BYTES+MAX_FILES*(MAX_PATH+8)+4096)
                    throw IllegalArgumentException("oversized decoded archive")
                DataInputStream(ByteArrayInputStream(decrypted)).use {bytes->
                    if(bytes.readInt()!=INNER_MAGIC)
                        throw IllegalArgumentException("bad archive payload")
                    val owner=NativeKotlinOwnerIdentity(
                        bytes.readUTF(),bytes.readUTF())
                    if(!validId.matches(owner.ownerKey) ||
                        !validId.matches(owner.athleteId))
                        throw IllegalArgumentException("bad owner")
                    if(expectedOwner!=null && owner!=expectedOwner)
                        return NativeKotlinTrainingSnapshotRead.Rejected(
                            NativeKotlinTrainingSnapshotRefusal.OWNER_MISMATCH)
                    val count=bytes.readInt()
                    if(count !in 1..MAX_FILES)
                        throw IllegalArgumentException("invalid file count")
                    val seen=mutableSetOf<String>()
                    val files=ArrayList<NativeKotlinTrainingSnapshotFile>(count)
                    var total=0L
                    repeat(count) {
                        val path=bytes.readUTF()
                        val length=bytes.readInt()
                        if(!safePath(path) || !seen.add(path) ||
                            length<0 || length>MAX_FILE_BYTES ||
                            total+length>MAX_BYTES)
                            throw IllegalArgumentException("invalid snapshot entry")
                        val content=ByteArray(length)
                        bytes.readFully(content)
                        total+=length
                        files+=NativeKotlinTrainingSnapshotFile(path,content)
                    }
                    if(bytes.available()!=0 || seen.none {
                        it=="local-athlete.bin" || it=="local-athlete.bin.bak"
                    })throw IllegalArgumentException("incomplete backup payload")
                    return NativeKotlinTrainingSnapshotRead.Verified(owner,files)
                }
            } finally {decrypted.fill(0)}
        } catch(_:Exception) {
            NativeKotlinTrainingSnapshotRead.Rejected(
                NativeKotlinTrainingSnapshotRefusal.INVALID_ARCHIVE_OR_PASSWORD)
        }
    }
}
