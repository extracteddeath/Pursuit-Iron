package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.util.AtomicFile
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinCurrentIdentity
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinOwnerIdentity
import io.github.extracteddeath.pursuitiron.rebuild.ReviewedAthleteSetup
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

/**
 * DEVICE-LOCAL profile identity for the isolated Kotlin candidate runtime.
 *
 * This is NOT remote authentication, a login token, or a way to import a
 * legacy JavaScript account. It is deliberately inactive on application
 * startup. A future native onboarding host must first obtain a
 * CoachAthleteSetupDomain-reviewed athlete profile and explicit enrollment.
 *
 * A corrupted existing identity must never be replaced by a fresh one:
 * doing so could orphan workouts or implicitly switch owners.
 */
internal class AppPrivateKotlinLocalAthlete(context: Context) :
    NativeKotlinCurrentIdentity {
    private val root=File(context.noBackupFilesDir,"kotlin-coach-v1")
    private val base=File(root,"local-athlete.bin")
    private val file=AtomicFile(base)
    @Volatile private var explicitlyActivated=false
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
    private val magic=0x50494F31 // PIO1
    private val version=1

    sealed interface Enrollment {
        data class Activated(val identity:NativeKotlinOwnerIdentity):Enrollment
        data class Rejected(val reason:String):Enrollment
    }

    private fun existing():Boolean = base.exists() ||
        File(base.path+".bak").exists() || File(base.path+".new").exists()

    private fun decode(bytes:ByteArray):NativeKotlinOwnerIdentity {
        if(bytes.size !in 44..256)throw IOException("invalid local identity length")
        val payload=bytes.copyOfRange(0,bytes.size-32)
        val expected=bytes.copyOfRange(bytes.size-32,bytes.size)
        if(!MessageDigest.isEqual(
                MessageDigest.getInstance("SHA-256").digest(payload),expected))
            throw IOException("local identity checksum mismatch")
        DataInputStream(ByteArrayInputStream(payload)).use { input ->
            if(input.readInt()!=magic || input.readInt()!=version)
                throw IOException("unsupported local identity version")
            val owner=input.readUTF()
            val athlete=input.readUTF()
            if(input.available()!=0 || !id.matches(owner) || !id.matches(athlete))
                throw IOException("corrupt local identity fields")
            return NativeKotlinOwnerIdentity(owner,athlete)
        }
    }

    private fun encode(identity:NativeKotlinOwnerIdentity):ByteArray {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use { output ->
            output.writeInt(magic);output.writeInt(version)
            output.writeUTF(identity.ownerKey);output.writeUTF(identity.athleteId)
        }
        val body=bytes.toByteArray()
        return body+MessageDigest.getInstance("SHA-256").digest(body)
    }

    private fun read():NativeKotlinOwnerIdentity? {
        if(!existing())return null
        if(base.isFile.not() && base.exists())throw IOException("identity path not a file")
        if(java.nio.file.Files.isSymbolicLink(base.toPath()))
            throw IOException("identity link rejected")
        val bytes=try {
            file.openRead().use {input ->
                val data=input.readBytes()
                if(data.size>256)throw IOException("identity record too large")
                data
            }
        } catch(ex:FileNotFoundException) {
            throw IOException("existing identity cannot be recovered",ex)
        }
        return decode(bytes)
    }

    /**
     * Only call after the native onboarding review is complete. This does
     * not grant cross-device account authentication and does not silently
     * activate any identity on restart. An existing different athlete must
     * be reconciled through an explicit data migration, not overwritten.
     */
    @Synchronized fun activateReviewed(reviewed:ReviewedAthleteSetup):Enrollment {
        val athlete=reviewed.athleteProfileId
        if(!id.matches(athlete))
            return Enrollment.Rejected("Athlete identifier is not valid")
        return try {
            root.mkdirs()
            val previous=read()
            if(previous!=null && previous.athleteId!=athlete)
                return Enrollment.Rejected("Different local athlete already owns saved data")
            val current=previous ?: NativeKotlinOwnerIdentity(
                "local-"+UUID.randomUUID(),athlete)
            if(previous==null) {
                val bytes=encode(current)
                val out=file.startWrite()
                try {
                    out.write(bytes)
                    out.fd.sync()
                    file.finishWrite(out)
                } catch(ex:Exception) {
                    file.failWrite(out)
                    throw ex
                }
                if(read()!=current)throw IOException("local identity commit mismatch")
            }
            explicitlyActivated=true
            Enrollment.Activated(current)
        } catch(_:Exception) {
            explicitlyActivated=false
            Enrollment.Rejected("Local athlete identity could not be verified")
        }
    }

    /**
     * Display-only local identifier for EXPLICIT same-athlete restoration.
     * This never activates the owner, authorizes reading gym/workouts, or
     * bypasses a newly reviewed setup. A corrupt existing record is NEVER
     * replaced with a new account just because this lookup returns null.
     */
    @Synchronized fun savedAthleteIdForExplicitResume():String? =
        runCatching { read()?.athleteId }.getOrNull()

    /**
     * Explicit same-device resume only. This validates the saved, checksummed
     * identity without creating or replacing a record. The calling host MUST
     * immediately verify the independently re-reviewed authoring snapshot
     * and deactivate if it is absent, revoked or invalid.
     *
     * This is device-local profile selection, not remote authentication.
     */
    @Synchronized fun activatePreviouslySaved(
        confirmedAthleteId:String
    ):Enrollment {
        if(!id.matches(confirmedAthleteId))
            return Enrollment.Rejected("Saved athlete confirmation is invalid")
        return try {
            val saved=read()
                ?:return Enrollment.Rejected("No saved local athlete to resume")
            if(saved.athleteId!=confirmedAthleteId)
                return Enrollment.Rejected("Saved athlete confirmation does not match")
            explicitlyActivated=true
            Enrollment.Activated(saved)
        } catch(_:Exception) {
            explicitlyActivated=false
            Enrollment.Rejected("Saved athlete identity could not be verified")
        }
    }

    /**
     * Proof of origin only for the separate Kotlin-Lab owner namespace:
     * this class mints "local-"+UUID.randomUUID() on first setup and
     * provides no importing/rebinding API. THIS DOES NOT prove the human
     * had no workout history in the web app or another device.
     */
    @Synchronized fun isIndependentLocalProfileOwner(
        ownerKey:String,athleteId:String
    ):Boolean {
        val verified=signedInIdentity() ?:return false
        val nativeOwner=Regex(
            "^local-[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$")
        return verified.ownerKey==ownerKey &&
            verified.athleteId==athleteId &&
            nativeOwner.matches(ownerKey)
    }

    /** Never claim a previous login on cold startup without native activation. */
    override fun signedInIdentity():NativeKotlinOwnerIdentity? =
        if(explicitlyActivated)runCatching {read()}.getOrNull() else null

    @Synchronized fun deactivate() {
        explicitlyActivated=false
        // The identity file is intentionally retained: sign-out must not
        // destroy training history or create a new athlete on next launch.
    }
}
