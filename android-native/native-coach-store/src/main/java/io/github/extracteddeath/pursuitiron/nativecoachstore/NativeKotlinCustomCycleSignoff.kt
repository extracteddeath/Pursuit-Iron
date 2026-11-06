package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * Athlete consent is NOT a workout approval. A fingerprint is tied to every
 * byte of the immutable authored cycle and reviewed gym/catalog source, plus
 * the complete authored coaching policies, transition continuity ceilings,
 * and declared session overhead.
 * A policy's human-readable revision label alone is not enough.
 */
data class NativeKotlinCustomCycleSignoff(
    val ownerKey: String,
    val athleteId: String,
    val cycleId: String,
    val cycleRevision: Long,
    val sourceRevision: Long,
    val recordRevision: Long,
    val fingerprintSha256: String,
    val policyReferences: Map<TrainingPhase, String>
) {
    val mayIssueWorkout: Boolean get() = false
    val mayAlterHistory: Boolean get() = false
}

enum class NativeCustomSignoffRefusal {
    NOT_AUTHORIZED, SOURCE_MISSING, PROGRAM_MISSING, STALE_SOURCE,
    STALE_PROGRAM, STALE_CONSENT, COACH_REVIEW_UNRESOLVED,
    CONFIRMATION_REQUIRED, CORRUPT_RECORD, STORAGE_UNAVAILABLE
}
sealed interface NativeCustomSignoffRead {
    data object Missing: NativeCustomSignoffRead
    data class Found(val receipt: NativeKotlinCustomCycleSignoff,
        val isCurrent: Boolean): NativeCustomSignoffRead
    data class Revoked(val recordRevision: Long): NativeCustomSignoffRead
    data class Rejected(val reason: NativeCustomSignoffRefusal): NativeCustomSignoffRead
}
sealed interface NativeCustomSignoffWrite {
    data class Committed(val receipt: NativeKotlinCustomCycleSignoff):
        NativeCustomSignoffWrite
    data class Rejected(val reason: NativeCustomSignoffRefusal):
        NativeCustomSignoffWrite
}

internal object NativeCustomCycleSignoffFingerprint {
    private fun DataOutputStream.word(text:String)=writeUTF(text)
    private fun DataOutputStream.decimal(value:BigDecimal)=
        word(value.stripTrailingZeros().toPlainString())
    private fun DataOutputStream.bound(bound:DeclaredMuscleDoseBounds) {
        word(bound.muscle.name);decimal(bound.mev);decimal(bound.mav)
        decimal(bound.mrv);writeInt(bound.minimumDirectDays)
    }
    private fun DataOutputStream.policy(p:AuthoredGoalProgramPolicy) {
        word(p.policyId);word(p.revision);word(p.rationale)
        word(p.athleteId);word(p.athleteRevision)
        word(p.goal.name);word(p.experience.name);word(p.split.name)
        word(p.phase.name);writeInt(p.trainingDays)
        writeInt(p.baselineBounds.size)
        p.baselineBounds.sortedBy { it.muscle.name }.forEach { bound(it) }
        writeInt(p.selectedBounds.size)
        p.selectedBounds.sortedBy { it.muscle.name }.forEach { bound(it) }
        writeInt(p.minimumPurposeDays.size)
        p.minimumPurposeDays.toSortedMap(compareBy{it.name}).forEach {
            (purpose,count)->word(purpose.name);writeInt(count)
        }
        writeInt(p.primaryLiftMinimumDays.size)
        p.primaryLiftMinimumDays.toSortedMap().forEach {
            (exercise,days)->word(exercise);writeInt(days)
        }
    }
    fun calculate(
        cycle: CustomTrainingCycle,
        source: NativeCustomAuthoringSnapshot,
        inputs: CustomCoachAuditInputs
    ):String {
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {out->
            // This is a versioned consent fingerprint, NOT the draft file
            // codec version. Changing it intentionally stales older receipts.
            out.writeInt(2)
            val authored=NativeCustomCycleDraftCodec.encode(cycle)
            val confirmed=NativeKotlinCustomAuthoringSourceCodec.encode(source)
            out.writeInt(authored.size);out.write(authored)
            out.writeInt(confirmed.size);out.write(confirmed)
            out.writeInt(inputs.policiesByPhase.size)
            inputs.policiesByPhase.toSortedMap(compareBy{it.name}).forEach {
                (phase,policy)->out.word(phase.name);out.policy(policy)
            }
            out.writeInt(inputs.nonSetSecondsByWeekday.size)
            inputs.nonSetSecondsByWeekday.toSortedMap().forEach { (day,seconds)->
                out.writeInt(day);out.writeInt(seconds)
            }
            // Omission used to let a changed volume-drop or ramp policy
            // masquerade as the same reviewed program. Serialize ALL four
            // bounds, including absence, in fixed order and canonical units.
            val continuity=inputs.continuityPolicy
            out.writeBoolean(continuity!=null)
            if(continuity!=null) {
                out.decimal(continuity.maximumTotalWorkingSetDropFraction)
                out.decimal(continuity.maximumDirectMuscleDropFraction)
                out.writeBoolean(
                    continuity.maximumTotalWorkingSetRiseFraction!=null)
                continuity.maximumTotalWorkingSetRiseFraction?.let(out::decimal)
                out.writeBoolean(
                    continuity.maximumDirectMuscleRiseFraction!=null)
                continuity.maximumDirectMuscleRiseFraction?.let(out::decimal)
            }
        }
        return MessageDigest.getInstance("SHA-256").digest(bytes.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
