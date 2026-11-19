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
    fun claimed(root:Path,owner:String,workout:String):Boolean {
        val file=path(root,owner,workout)
        return Files.exists(file) || Files.isSymbolicLink(file)
    }
}
