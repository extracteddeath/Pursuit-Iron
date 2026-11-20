package io.github.extracteddeath.pursuitiron.rebuild

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Collections

/**
 * Read-only corroboration between the canonical per-exercise History review
 * and owner-observed calendar weeks. An observed Finish date is NOT proof
 * of the plan activation date, and full coverage is NOT a compliant program.
 *
 * No independent numeric progression, exercise rotation, muscle dose or
 * phase policy. It only identifies what still needs a separate full-cycle
 * coaching review and a verified activation/athlete-consent boundary.
 */
enum class KotlinContinuousPlanEvidenceState {
    NO_EXACT_REPEATED_PERFORMANCE,
    INSUFFICIENT_DISTINCT_DATED_WEEKS,
    OBSERVED_WEEKS_NOT_COMPLETE,
    NONCONSECUTIVE_OR_STALE_WEEKS,
    EXPLORATORY_REVIEW_ONLY_ACTIVATION_REQUIRED
}

data class KotlinContinuousPlanCellEvidence(
    val weekday:Int,
    val dayIndex:Int,
    val slotId:String,
    val exerciseId:String,
    val state:KotlinContinuousPlanEvidenceState,
    val supportingWorkoutIds:List<String>,
    val supportingWeekMondays:List<LocalDate>
) {
    val mayChangeWorkingLoad:Boolean get()=false
    val mayChangeRepRange:Boolean get()=false
    val mayChangeWorkingSets:Boolean get()=false
    val mayChangeExercise:Boolean get()=false
}

data class KotlinContinuousPlanEvidenceSummary(
    val sourceReplayDigest:String,
    val generatedRequestDigest:String,
    val cells:List<KotlinContinuousPlanCellEvidence>
) {
    val mayApplyAutomatically:Boolean get()=false
    val mayRecommendPhasePromotion:Boolean get()=false
    val mayAssumeProgramAdherence:Boolean get()=false
}

enum class KotlinContinuousPlanEvidenceRefusal {
    SOURCE_REPLAY_MISMATCH,
    INVALID_HISTORY_WEEK_JOIN
}

sealed interface KotlinContinuousPlanEvidenceResult {
    data class Reviewed(val summary:KotlinContinuousPlanEvidenceSummary):
        KotlinContinuousPlanEvidenceResult
    data class Rejected(val reason:KotlinContinuousPlanEvidenceRefusal):
        KotlinContinuousPlanEvidenceResult
}

object KotlinContinuousPlanEvidenceDomain {
    /** This accepts ONLY the outputs already reviewed by the native owner
     * coordinator; it is not a substitute for the owner-fenced read. */
    fun review(
        linked:KotlinFuturePerformanceLink,
        observed:KotlinObservedScheduleWeeks
    ):KotlinContinuousPlanEvidenceResult {
        fun rejected(reason:KotlinContinuousPlanEvidenceRefusal)=
            KotlinContinuousPlanEvidenceResult.Rejected(reason)
        if(linked.ownerSourceReplayDigest.length!=64 ||
            linked.generatedTraceDigest.length!=64 ||
            linked.ownerSourceReplayDigest!=observed.sourceReplayDigest)
            return rejected(KotlinContinuousPlanEvidenceRefusal
                .SOURCE_REPLAY_MISMATCH)
        val allSessions=observed.weeks.flatMap{week->
            week.sessions.map{it.workoutId to week}
        }
        if(allSessions.map{it.first}.distinct().size!=allSessions.size ||
            observed.weeks.map{it.monday}.distinct().size!=observed.weeks.size ||
            observed.weeks.any{week->
                week.expectedWeekdays!=observed.scheduledWeekdays ||
                week.monday.dayOfWeek.value!=1 ||
                week.sessions.any{session->
                    session.localCompletionDate==null ||
                    session.localCompletionDate.minusDays(
                        (session.localCompletionDate.dayOfWeek.value-1).toLong()
                    )!=week.monday
                }
            } || observed.undatedWorkoutIds.toSet().size!=
                observed.undatedWorkoutIds.size ||
            allSessions.any{it.first in observed.undatedWorkoutIds})
            return rejected(KotlinContinuousPlanEvidenceRefusal
                .INVALID_HISTORY_WEEK_JOIN)
        val byWorkout=allSessions.toMap()
        val latestMonday=observed.weeks.maxOfOrNull{it.monday}
        val results=linked.cells.map{cell->
            val requested=cell.evaluatedWorkoutIds
            val support=requested.mapNotNull {id->
                val week=byWorkout[id] ?:return@mapNotNull null
                val session=week.sessions.singleOrNull{it.workoutId==id}
                    ?:return@mapNotNull null
                if(session.dayIndex!=cell.dayIndex ||
                    session.issuedWeekday!=cell.weekday ||
                    !session.fullIssuedWorkRecorded ||
                    !session.matchesScheduledWeekday)
                    return@mapNotNull null
                week
            }
            val weeks=support.map{it.monday}
            val three=requested.size==3 &&
                requested.distinct().size==3 && support.size==3
            val distinct=three && weeks.distinct().size==3
            val full=distinct && support.all{
                it.coverage==KotlinObservedWeekCoverage
                    .FULL_SCHEDULE_OBSERVED_FOR_REVIEW_ONLY
            } && !observed.hasConflictingCompletionOrder &&
                observed.undatedWorkoutIds.isEmpty() &&
                observed.sourceScheduleCompatible
            val chronology=full && weeks.sorted().zipWithNext().all{(a,b)->
                ChronoUnit.DAYS.between(a,b)==7L
            } && weeks.maxOrNull()==latestMonday
            val state=when {
                cell.status!=KotlinFuturePerformanceCellStatus
                    .REP_CEILING_REVIEW_ONLY ->
                    KotlinContinuousPlanEvidenceState.NO_EXACT_REPEATED_PERFORMANCE
                !distinct ->
                    KotlinContinuousPlanEvidenceState
                        .INSUFFICIENT_DISTINCT_DATED_WEEKS
                !full ->
                    KotlinContinuousPlanEvidenceState
                        .OBSERVED_WEEKS_NOT_COMPLETE
                !chronology ->
                    KotlinContinuousPlanEvidenceState
                        .NONCONSECUTIVE_OR_STALE_WEEKS
                else ->
                    KotlinContinuousPlanEvidenceState
                        .EXPLORATORY_REVIEW_ONLY_ACTIVATION_REQUIRED
            }
            KotlinContinuousPlanCellEvidence(
                cell.weekday,cell.dayIndex,cell.slotId,cell.exerciseId,
                state,Collections.unmodifiableList(requested.toList()),
                Collections.unmodifiableList(weeks.toList()))
        }
        return KotlinContinuousPlanEvidenceResult.Reviewed(
            KotlinContinuousPlanEvidenceSummary(
                observed.sourceReplayDigest,linked.generatedTraceDigest,
                Collections.unmodifiableList(results)))
    }
}
