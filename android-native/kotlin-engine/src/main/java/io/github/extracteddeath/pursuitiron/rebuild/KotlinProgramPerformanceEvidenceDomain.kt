package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * One conservative numerical interpretation of owner-source-read History.
 * Reuses the existing journal-replay and physical-machine comparison gates.
 * No competing rep/load/set prescription, no fatigue diagnosis or automation.
 *
 * A caller supplied ledger and ordinal map are NOT proof of durable storage.
 * NativeKotlinGlobalHistoryReadCoordinator is responsible for reopening the
 * complete history manifest under its owner transaction before invoking this.
 */
data class KotlinProgramPerformanceKey(
    val dayIndex:Int,
    val calendarDay:Int,
    val purpose:ComposerSessionPurpose,
    val role:ComposerRole,
    val exerciseId:String,
    val physicalMachineId:String?,
    val loadUnit:LoadUnit?,
    val loadBasis:LoadBasis?
)

data class KotlinProgramPerformanceVisit(
    val workoutId:String,
    val completionOrder:Long,
    val dayIndex:Int,
    val calendarDay:Int,
    val workingSets:Int,
    val completedSets:Int,
    val historicalComparable:Boolean,
    val issuedRepBands:List<RepBand>,
    val issuedLoads:List<DraftSetLoad>,
    val targetRir:List<Int?>,
    val performedReps:List<Int?>,
    /** Unknown actual RIR is always NULL, never target RIR. */
    val reportedRir:List<Int?>,
    val wasShortenedForTime:Boolean
)

enum class KotlinProgramPerformanceGate {
    NEEDS_THREE_MATCHED_VISITS,
    AMBIGUOUS_INTERVENING_MACHINE,
    CHANGED_LOAD_BASIS_AFTER_LAST_MATCHED_VISIT,
    CHANGED_WEEKDAY_OR_ROLE_AFTER_LAST_MATCHED_VISIT,
    INCOMPARABLE_RECENT_VISIT,
    PRESCRIPTION_CHANGED,
    ACTUAL_EFFORT_UNKNOWN,
    NOT_REPEATED_AT_REP_CEILING,
    /** Three distinct source-comparable completed visits all reached the
     * same exact authored rep cap without reporting effort below target.
     * A reviewed programming decision is still necessary. */
    REP_CEILING_FOR_REVIEW_ONLY
}

data class KotlinProgramPerformanceThread(
    val key:KotlinProgramPerformanceKey,
    val visits:List<KotlinProgramPerformanceVisit>,
    val recentGate:KotlinProgramPerformanceGate,
    val evaluatedWorkoutIds:List<String>
) {
    val canChangeRepRange:Boolean get()=false
    val canChangeSetCount:Boolean get()=false
    val canPromotePhase:Boolean get()=false
    val canChangeWorkingLoad:Boolean get()=false
}

data class KotlinProgramPerformanceReport(
    val schemaVersion:Int,
    val sourceReplayDigest:String,
    val threads:List<KotlinProgramPerformanceThread>
) {
    val canApplyToProgram:Boolean get()=false
    val canIssueWorkout:Boolean get()=false
}

enum class KotlinProgramPerformanceRefusal {
    INVALID_REPLAY_OR_ORDER,
    UNVERIFIED_SOURCE_FACT
}
sealed interface KotlinProgramPerformanceResult {
    data class Reviewed(val report:KotlinProgramPerformanceReport):
        KotlinProgramPerformanceResult
    data class Rejected(val reason:KotlinProgramPerformanceRefusal):
        KotlinProgramPerformanceResult
}

object KotlinProgramPerformanceEvidenceDomain {
    private const val MIN_EXPOSURES_FOR_REVIEW=3

    /**
     * One actual is not an earned rep ceiling at the *issued* weight when
     * the athlete used a lighter (or heavier) load. Reuse the SAME recorded
     * set-number authority as the journals; never compare weights using a
     * separate shortcut or mistake a coach prefill for a performed set.
     */
    private fun completedAtIssuedLoad(fact:KotlinProgramHistoricalWorkingSet):Boolean {
        if(fact.outcome!=KotlinJournalSetOutcome.COMPLETED ||
            fact.performedReps==null) return false
        val reviewed=KotlinSetNumberAuthorityDomain.review(
            fact.issuedLoad,fact.outcome,KotlinPerformedSet(
                fact.performedLoad,fact.performedUnit,fact.performedReps,
                fact.reportedRir,fact.physicalMachineId
            ))
        return (reviewed as? KotlinSetNumberReview.Accepted)?.fact?.relation==
            KotlinSetLoadRelation.COMPLETED_AS_ISSUED
    }

    /**
     * An old successful set cannot erase a skipped/latest hard set. Compare
     * the latest THREE visits in original durable completion order, not
     * lexical workout ID and not a convenience subset of successful sets.
     */
    fun review(
        ledger:KotlinProgramHistoryReadout,
        completionOrderByWorkout:Map<String,Long>
    ):KotlinProgramPerformanceResult {
        fun denied(reason:KotlinProgramPerformanceRefusal)=
            KotlinProgramPerformanceResult.Rejected(reason)
        val workouts=ledger.workouts.associateBy{it.workoutId}
        if(ledger.schemaVersion!=2 || ledger.exactReplayDigest.length!=64 ||
            workouts.size!=ledger.workouts.size ||
            completionOrderByWorkout.keys!=workouts.keys ||
            completionOrderByWorkout.values.any{it<=0L} ||
            completionOrderByWorkout.values.toSet().size!=workouts.size ||
            ledger.workouts.any{
                it.purpose==ComposerSessionPurpose.UNSPECIFIED ||
                it.dayIndex !in 0..6 ||
                it.calendarDay !in 0..6
            })
            return denied(KotlinProgramPerformanceRefusal.INVALID_REPLAY_OR_ORDER)

        val byWorkout=ledger.workingSets.groupBy{it.workoutId}
        if(byWorkout.keys.any{it !in workouts})
            return denied(KotlinProgramPerformanceRefusal.UNVERIFIED_SOURCE_FACT)
        if(workouts.values.any{w->
            val facts=byWorkout[w.workoutId].orEmpty()
            facts.any{
                it.dayIndex!=w.dayIndex ||
                it.calendarDay!=w.calendarDay ||
                it.purpose!=w.purpose
            } ||
                facts.count{it.outcome==KotlinJournalSetOutcome.COMPLETED}!=
                    w.completedWorkingSets ||
                facts.count{it.outcome==KotlinJournalSetOutcome.SKIPPED}!=
                    w.skippedWorkingSets ||
                facts.count{it.outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT}!=
                    w.failedWorkingSets ||
                facts.map{it.setKey}.distinct().size!=facts.size
        })return denied(KotlinProgramPerformanceRefusal.UNVERIFIED_SOURCE_FACT)

        data class VisitWithKey(
            val key:KotlinProgramPerformanceKey,
            val visit:KotlinProgramPerformanceVisit
        )
        val observed=mutableListOf<VisitWithKey>()
        for(w in ledger.workouts) {
            val sets=byWorkout[w.workoutId].orEmpty()
            for((exerciseId,unordered) in sets.groupBy{it.exerciseId}) {
                val facts=unordered.sortedWith(compareBy(
                    {it.workingOrdinal},{it.setKey}))
                if(facts.map{it.workingOrdinal}.distinct().size!=facts.size ||
                    facts.map{it.role}.distinct().size!=1)
                    return denied(KotlinProgramPerformanceRefusal.UNVERIFIED_SOURCE_FACT)
                val machines=facts.map{it.physicalMachineId}.toSet()
                val machine=machines.singleOrNull()
                val types=facts.map{
                    (it.issuedLoad as? DraftSetLoad.Verified)?.let{
                        load->load.unit to load.basis
                    }
                }.toSet()
                val first=types.singleOrNull()
                val key=KotlinProgramPerformanceKey(w.dayIndex,w.calendarDay,
                    w.purpose,facts.first().role,exerciseId,
                    machine,first?.first,first?.second)
                observed+=VisitWithKey(key,KotlinProgramPerformanceVisit(
                    w.workoutId,
                    checkNotNull(completionOrderByWorkout[w.workoutId]),
                    w.dayIndex,w.calendarDay,facts.size,
                    facts.count{it.outcome==KotlinJournalSetOutcome.COMPLETED},
                    !w.shortenedForTime && facts.isNotEmpty() &&
                        facts.all{completedAtIssuedLoad(it)} &&
                        facts.all{
                            it.comparisonGuard==
                                KotlinHistoryComparisonGuard.COMPARABLE_REVIEW_ONLY
                        } && machines.size==1 && types.size==1,
                    Collections.unmodifiableList(facts.map{it.issuedReps}),
                    Collections.unmodifiableList(facts.map{it.issuedLoad}),
                    Collections.unmodifiableList(facts.map{it.targetRir}),
                    Collections.unmodifiableList(facts.map{it.performedReps}),
                    Collections.unmodifiableList(facts.map{it.reportedRir}),
                    w.shortenedForTime))
            }
        }
        val byKey=observed.groupBy{it.key}
        val threads=byKey.entries.sortedWith(compareBy(
            {it.key.calendarDay},{it.key.dayIndex},
            {it.key.purpose.name},{it.key.role.name},
            {it.key.exerciseId},{it.key.physicalMachineId ?: ""},
            {it.key.loadUnit?.name ?: ""},{it.key.loadBasis?.name ?: ""}
        )).map{(key,parts)->
            val visits=parts.map{it.visit}.sortedWith(
                compareBy({it.completionOrder},{it.workoutId}))
            val recent=visits.takeLast(MIN_EXPOSURES_FOR_REVIEW)
            val gate=when {
                // A newer issued workout carrying the same nominal split
                // index/movement but a different weekday or exercise role
                // makes this older trend stale. Do not resurrect it after a
                // split reorganization.
                observed.any{
                    it.key.dayIndex==key.dayIndex &&
                        it.key.purpose==key.purpose &&
                        it.key.exerciseId==key.exerciseId &&
                        (it.key.calendarDay!=key.calendarDay ||
                            it.key.role!=key.role) &&
                        it.visit.completionOrder>visits.last().completionOrder
                } -> KotlinProgramPerformanceGate
                    .CHANGED_WEEKDAY_OR_ROLE_AFTER_LAST_MATCHED_VISIT
                recent.size<MIN_EXPOSURES_FOR_REVIEW ->
                    KotlinProgramPerformanceGate.NEEDS_THREE_MATCHED_VISITS
                // An intervening visit with UNKNOWN station identity could
                // belong to this station. Explicit B does not contaminate A.
                key.physicalMachineId!=null && observed.any{
                    it.key.dayIndex==key.dayIndex &&
                        it.key.calendarDay==key.calendarDay &&
                        it.key.purpose==key.purpose &&
                        it.key.role==key.role &&
                        it.key.exerciseId==key.exerciseId &&
                        it.key.physicalMachineId==null &&
                        // No upper bound: a later unknown-machine visit
                        // may have been this very station. Do not resurrect
                        // older A facts after an ambiguous latest session.
                        it.visit.completionOrder>=recent.first().completionOrder
                } -> KotlinProgramPerformanceGate.AMBIGUOUS_INTERVENING_MACHINE
                // A newer visit with this SAME day/physical station but
                // a different load basis is a new epoch. The previous
                // thread cannot remain a stale eligible recommendation.
                observed.any{
                    it.key.dayIndex==key.dayIndex &&
                        it.key.calendarDay==key.calendarDay &&
                        it.key.purpose==key.purpose &&
                        it.key.role==key.role &&
                        it.key.exerciseId==key.exerciseId &&
                        it.key.physicalMachineId==key.physicalMachineId &&
                        (it.key.loadUnit!=key.loadUnit ||
                            it.key.loadBasis!=key.loadBasis) &&
                        it.visit.completionOrder>visits.last().completionOrder
                } -> KotlinProgramPerformanceGate
                    .CHANGED_LOAD_BASIS_AFTER_LAST_MATCHED_VISIT
                recent.any{!it.historicalComparable} ->
                    KotlinProgramPerformanceGate.INCOMPARABLE_RECENT_VISIT
                recent.any{it.workingSets!=recent.first().workingSets ||
                    it.issuedRepBands!=recent.first().issuedRepBands ||
                    it.issuedLoads.size!=recent.first().issuedLoads.size ||
                    it.issuedLoads.zip(recent.first().issuedLoads)
                        .any{(a,b)->!KotlinSetNumberAuthorityDomain.sameIssuedTarget(a,b)}
                } -> KotlinProgramPerformanceGate.PRESCRIPTION_CHANGED
                recent.any{v->v.targetRir.any{it==null} ||
                    v.reportedRir.any{it==null} ||
                    v.performedReps.any{it==null}
                } -> KotlinProgramPerformanceGate.ACTUAL_EFFORT_UNKNOWN
                recent.all{v->v.performedReps.indices.all{i->
                    checkNotNull(v.performedReps[i])>=v.issuedRepBands[i].maximum &&
                        checkNotNull(v.reportedRir[i])>=checkNotNull(v.targetRir[i])
                }} -> KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY
                else -> KotlinProgramPerformanceGate.NOT_REPEATED_AT_REP_CEILING
            }
            KotlinProgramPerformanceThread(key,
                Collections.unmodifiableList(visits),
                gate,Collections.unmodifiableList(recent.map{it.workoutId}))
        }
        return KotlinProgramPerformanceResult.Reviewed(
            KotlinProgramPerformanceReport(2,ledger.exactReplayDigest,
                Collections.unmodifiableList(threads)))
    }
}
