package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Shared physical ID claim for typed measured parent journals. This checks
 * existence, not decoded validity: even corrupt or symlinked measured files
 * block reuse by a different workout type. Call under the owner revision fence
 * when creating a new Composer/custom/measured workout.
 */
internal object NativeMeasuredWorkoutIdClaim {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    fun path(root:Path,owner:String,workout:String):Path {
        require(id.matches(owner) && id.matches(workout))
        val bytes=(owner+"\u0000"+workout).toByteArray(Charsets.UTF_8)
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return root.resolve("measured-parent-$hash.bin")
    }
    /** The unreleased freestyle archive uses a scoped prefix, allowing
     * cold Resume without opening another athlete owner's files. */
    fun freestylePrefix(owner:String):String {
        require(id.matches(owner))
        val hash=MessageDigest.getInstance("SHA-256")
            .digest(owner.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return "freestyle-$hash-"
    }
    fun freestylePath(root:Path,owner:String,workout:String):Path {
        val measured=path(root,owner,workout)
        return measured.resolveSibling(freestylePrefix(owner)+
            measured.fileName.toString().removePrefix("measured-parent-"))
    }
    private fun exists(file:Path):Boolean=Files.exists(file) || Files.isSymbolicLink(file)
    fun freestyleClaimed(root:Path,owner:String,workout:String):Boolean =
        exists(freestylePath(root,owner,workout))
    fun claimed(root:Path,owner:String,workout:String):Boolean =
        exists(path(root,owner,workout)) || freestyleClaimed(root,owner,workout)
    /** Same immutable ID digest and actual existing filenames. Corrupt files
     * are claims as well, so an unrelated workout cannot replace their facts. */
    fun anyClaimed(root:Path,owner:String,workout:String):Boolean {
        val measured=path(root,owner,workout)
        val suffix=measured.fileName.toString().removePrefix("measured-parent-")
        return claimed(root,owner,workout) ||
            exists(root.resolve("journal-$suffix")) ||
            exists(root.resolve("issued-custom-$suffix"))
    }
}
