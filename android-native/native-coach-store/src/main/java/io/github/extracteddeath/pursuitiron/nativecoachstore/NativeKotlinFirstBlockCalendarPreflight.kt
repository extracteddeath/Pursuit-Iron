package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * An UNSIGNED first-block calendar proposal, never a retrospective date or a
 * training grant. Unlike future-block dates, there is no applied activation
 * transaction that can serve as a start-date approval. Never persist or
 * represent this preview as an approved start.
 */
data class NativeFirstBlockCalendarDay(
    val absoluteWeek:Int,
    val weekday:Int,
    val localDate:LocalDate,
    val workoutName:String
)

data class NativeFirstBlockCalendarProposal(
    val athleteId:String,
    val cycleId:String,
    val blockId:String,
    val sourceRevision:Long,
    val sourceSha256:String,
    val draftRevision:Long,
    val draftSha256:String,
    val signoffFingerprint:String,
    val selectedMonday:LocalDate,
    val timezoneId:String,
    val weekdays:Set<Int>,
    val firstWeek:List<NativeFirstBlockCalendarDay>,
    val fingerprint:String,
    val previousMonday:LocalDate?,
    val nextMonday:LocalDate?
) {
    val isApproved:Boolean get()=false
    val canIssueWorkout:Boolean get()=false
    val canChangeTrainingNumbers:Boolean get()=false
    val canMarkMissedOrCompleted:Boolean get()=false
    val canInferStartFromHistory:Boolean get()=false
}

enum class NativeFirstBlockCalendarRefusal {
    NOT_AUTHORIZED, UNVERIFIED_SOURCE_OR_CYCLE,
    CURRENT_SIGNOFF_REQUIRED, UNVERIFIED_HISTORY_OR_NEXT_SLOT,
    FIRST_BLOCK_NOT_ELIGIBLE, FUTURE_ACTIVATION_PRESENT,
    TIMEZONE_OR_CLOCK_UNAVAILABLE, INVALID_LOCAL_MONDAY,
    STORAGE_UNAVAILABLE
}
sealed interface NativeFirstBlockCalendarPreview {
    data class Ready(val proposal:NativeFirstBlockCalendarProposal):
        NativeFirstBlockCalendarPreview
    data class Rejected(val reason:NativeFirstBlockCalendarRefusal):
        NativeFirstBlockCalendarPreview
}

/** Internal verified input; supplied only after the owner-fenced live audit. */
internal data class NativeFirstBlockCalendarBasis(
    val athleteId:String,
    val cycleId:String,
    val blockId:String,
    val sourceRevision:Long,
    val sourceSha256:String,
    val draftRevision:Long,
    val draftSha256:String,
    val signoffFingerprint:String,
    val timezoneId:String,
    val workoutNames:Map<Int,String>
)

internal object NativeFirstBlockCalendarDomain {
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}

    fun propose(
        basis:NativeFirstBlockCalendarBasis,
        observedAt:Long,
        requestedMonday:LocalDate?
    ):NativeFirstBlockCalendarPreview {
        val weekdays = basis.workoutNames.keys
        val zone=runCatching{ZoneId.of(basis.timezoneId)}.getOrNull()
            ?:return NativeFirstBlockCalendarPreview.Rejected(
                NativeFirstBlockCalendarRefusal.TIMEZONE_OR_CLOCK_UNAVAILABLE)
        if(zone.id!=basis.timezoneId || weekdays.size !in 3..6 ||
            weekdays.any{it !in 0..6} ||
            observedAt<=0 || !KotlinHistoryCompletionTime.valid(observedAt))
            return NativeFirstBlockCalendarPreview.Rejected(
                NativeFirstBlockCalendarRefusal.TIMEZONE_OR_CLOCK_UNAVAILABLE)
        val today=runCatching {
            Instant.ofEpochMilli(observedAt).atZone(zone).toLocalDate()
        }.getOrNull() ?:return NativeFirstBlockCalendarPreview.Rejected(
            NativeFirstBlockCalendarRefusal.TIMEZONE_OR_CLOCK_UNAVAILABLE)
        val earliest=today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val monday=requestedMonday ?: earliest
        if(monday.dayOfWeek!=DayOfWeek.MONDAY ||
            monday.year !in 2000..2100 ||
            monday.isBefore(earliest) || monday.isAfter(earliest.plusWeeks(12)))
            return NativeFirstBlockCalendarPreview.Rejected(
                NativeFirstBlockCalendarRefusal.INVALID_LOCAL_MONDAY)
        val dates=weekdays.sorted().map{day->
            NativeFirstBlockCalendarDay(1,day,monday.plusDays(day.toLong()),basis.workoutNames.getValue(day))
        }
        val bytes=ByteArrayOutputStream()
        DataOutputStream(bytes).use {out->
            out.writeInt(2) // preview fingerprint schema, not approval schema
            listOf(basis.athleteId,basis.cycleId,basis.blockId,
                basis.sourceSha256,basis.draftSha256,
                basis.signoffFingerprint,basis.timezoneId,
                monday.toString()).forEach(out::writeUTF)
            out.writeLong(basis.sourceRevision)
            out.writeLong(basis.draftRevision)
            out.writeInt(weekdays.size)
            weekdays.sorted().forEach { day ->
                out.writeInt(day)
                out.writeUTF(basis.workoutNames.getValue(day))
            }
        }
        return NativeFirstBlockCalendarPreview.Ready(
            NativeFirstBlockCalendarProposal(
                basis.athleteId,basis.cycleId,basis.blockId,
                basis.sourceRevision,basis.sourceSha256,
                basis.draftRevision,basis.draftSha256,
                basis.signoffFingerprint,monday,basis.timezoneId,
                weekdays.toSortedSet().toSet(),dates,digest(bytes.toByteArray()),
                monday.minusWeeks(1).takeIf { it >= earliest && it.year >= 2000 },
                monday.plusWeeks(1).takeIf { it <= earliest.plusWeeks(12) && it.year <= 2100 }))
    }
}

/**
 * Authenticates ALL operands under the shared owner transaction. The
 * existing next-cycle resolver independently reopens the complete native
 * history, outstanding prepared/issued workouts, current full-cycle coaching
 * review, signoff and its journal authority before exposing a Week 1 slot.
 *
 * This gate remains READ ONLY. It neither saves an accepted start date nor
 * changes an already issued workout. A separate durable, owner-consented
 * first-block transaction and encrypted backup inventory are still required.
 */
class NativeKotlinFirstBlockCalendarPreflight(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val signoffs:NativeKotlinCustomCycleSignoffFileStore,
    private val nextSessions:NativeKotlinCustomNextCycleSessionReview,
    private val futureCommits:NativeKotlinFutureActivationCommitTransaction,
    private val clock:NativeHistoryCompletionClock=NativeHistoryCompletionClock.System
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun refuse(reason:NativeFirstBlockCalendarRefusal)=
        NativeFirstBlockCalendarPreview.Rejected(reason)

    fun review(ownerKey:String,cycleId:String,
        selectedMonday:LocalDate?=null):NativeFirstBlockCalendarPreview {
        if(!id.matches(ownerKey)||!id.matches(cycleId))
            return refuse(NativeFirstBlockCalendarRefusal.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey) {
            val owner=identity.signedInIdentity()?.takeIf{
                it.ownerKey==ownerKey && id.matches(it.athleteId)
            } ?:return@withOwnerTransaction refuse(
                NativeFirstBlockCalendarRefusal.NOT_AUTHORIZED)
            // A prepared or applied future activation must not be relabeled
            // as a brand-new first cycle. Unrecognized/corrupt also refuses.
            if(!futureCommits.verifiesNoActivation(ownerKey))
                return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.FUTURE_ACTIVATION_PRESENT)
            val verified=(signoffs.recoverReviewedInputs(ownerKey,cycleId)
                as? NativeCustomSavedCoachReview.Ready)
                ?:return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.CURRENT_SIGNOFF_REQUIRED)
            val next=(nextSessions.review(ownerKey,cycleId,verified.inputs)
                as? NativeCustomNextCycleSlot.Next)
                ?:return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.UNVERIFIED_HISTORY_OR_NEXT_SLOT)
            val source=(sources.read(ownerKey) as?
                NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.UNVERIFIED_SOURCE_OR_CYCLE)
            val cycle=(drafts.read(ownerKey,cycleId) as?
                NativeCustomDraftReadResult.Found)?.draft
                ?:return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.UNVERIFIED_SOURCE_OR_CYCLE)
            val profile=source.setup.reviewed
                ?:return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.UNVERIFIED_SOURCE_OR_CYCLE)
            val block=cycle.blocks.firstOrNull()
                ?:return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.FIRST_BLOCK_NOT_ELIGIBLE)
            val days=block.days.map{it.weekday}
            if(next.absoluteWeek!=1 || next.weekday!=days.minOrNull() ||
                block.purpose!=CustomBlockPurpose.TRAINING ||
                block.weeks !in 1..52 || days.size !in 3..6 ||
                days.distinct().size!=days.size ||
                days.any{it !in 0..6} ||
                days.toSet()!=profile.trainingWeekdays ||
                cycle.athleteId!=owner.athleteId ||
                cycle.cycleId!=cycleId || source.athleteId!=owner.athleteId ||
                source.ownerKey!=ownerKey ||
                source.setup.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
                profile.athleteProfileId!=owner.athleteId ||
                verified.receipt.athleteId!=owner.athleteId ||
                verified.receipt.cycleId!=cycleId ||
                verified.receipt.cycleRevision!=cycle.revision ||
                verified.receipt.sourceRevision!=source.revision)
                return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.FIRST_BLOCK_NOT_ELIGIBLE)
            val at=clock.observe()?:return@withOwnerTransaction refuse(
                NativeFirstBlockCalendarRefusal.TIMEZONE_OR_CLOCK_UNAVAILABLE)
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction refuse(
                    NativeFirstBlockCalendarRefusal.NOT_AUTHORIZED)
            NativeFirstBlockCalendarDomain.propose(
                NativeFirstBlockCalendarBasis(
                    owner.athleteId,cycleId,block.blockId,source.revision,
                    sha(NativeKotlinCustomAuthoringSourceCodec.encode(source)),
                    cycle.revision,sha(NativeCustomCycleDraftCodec.encode(cycle)),
                    verified.receipt.fingerprintSha256,
                    profile.timezoneId,block.days.associate { it.weekday to it.label }),
                at,selectedMonday)
        }}.getOrElse{
            refuse(NativeFirstBlockCalendarRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
