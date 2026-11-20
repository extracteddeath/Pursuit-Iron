package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.security.MessageDigest
import java.util.Collections

/**
 * Read-only, version-fenced factual inputs for FUTURE program construction.
 * This replays actual finished workouts and effective saved corrections; it
 * does NOT authenticate that the native storage owner durably committed them.
 *
 * Until the owner has attested stored receipt bytes and source identity this
 * cannot grant program progression, phase promotion or automatic adaptation.
 */
data class KotlinProgramHistoryReadoutRequest(
    val athleteId:String,
    val cycleId:String,
    /** One exact issued program/gym/rules/catalog/source revision. Mixed
     * revisions are separate evidence epochs, never silently blended. */
    val expectedSource:KotlinTrainingSourceRevision,
    val allowedDayIndices:Set<Int>,
    val finishedWorkouts:List<KotlinWorkoutJournalState>,
    val savedCorrections:List<KotlinSavedHistoryCorrectionState> =emptyList()
)

enum class KotlinProgramHistoryReadoutRefusal {
    INVALID_SCOPE, INPUT_LIMIT, SOURCE_MISMATCH, DUPLICATE_WORKOUT,
    CORRECTION_WITHOUT_SOURCE_WORKOUT, INVALID_REPLAY_OR_CORRECTION,
    INVALID_DAY_OR_SET
}

enum class KotlinHistoryComparisonGuard {
    /** This is a factual observation, but not a comparable progression set. */
    INELIGIBLE_SESSION,
    /** A/B changes inside one exercise/session cannot become one trend. */
    MIXED_PHYSICAL_MACHINES,
    /** Machine ID is unconfirmed; do not merge a generic machine history. */
    MACHINE_UNKNOWN,
    /** The whole exercise session is comparable under existing replay
     * policy and exact physical machine/load/rep boundaries. */
    COMPARABLE_REVIEW_ONLY
}

data class KotlinProgramHistoricalWorkingSet(
    val workoutId:String,
    val dayIndex:Int,
    val exerciseId:String,
    val setKey:String,
    val workingOrdinal:Int,
    val issuedLoad:DraftSetLoad,
    val issuedReps:RepBand,
    val targetRir:Int?,
    val outcome:KotlinJournalSetOutcome,
    val performedLoad:BigDecimal?,
    val performedUnit:LoadUnit?,
    val performedReps:Int?,
    val reportedRir:Int?,
    val physicalMachineId:String?,
    val effectiveEventId:String,
    val correctsEventId:String?,
    val comparisonGuard:KotlinHistoryComparisonGuard
)

data class KotlinProgramHistoricalWorkout(
    val workoutId:String,
    val dayIndex:Int,
    val finishEventId:String,
    val journalRevision:Long,
    val correctionRevision:Long,
    val completedWorkingSets:Int,
    val skippedWorkingSets:Int,
    val failedWorkingSets:Int
)

/**
 * A stable factual replay ledger. A digest is not a signature: storing it
 * cannot prove the data came from a committed, owner-authenticated record.
 */
data class KotlinProgramHistoryReadout(
    val schemaVersion:Int,
    val athleteId:String,
    val cycleId:String,
    val source:KotlinTrainingSourceRevision,
    val workouts:List<KotlinProgramHistoricalWorkout>,
    val workingSets:List<KotlinProgramHistoricalWorkingSet>,
    val exactReplayDigest:String
) {
    val canApproveCycle:Boolean get()=false
    val canChangeIssuedWorkout:Boolean get()=false
    val canAuthorizeProgression:Boolean get()=false
    val requiresNativeStorageAttestation:Boolean get()=true
}

sealed interface KotlinProgramHistoryReadoutResult {
    data class Candidate(val ledger:KotlinProgramHistoryReadout):
        KotlinProgramHistoryReadoutResult
    data class Rejected(val reason:KotlinProgramHistoryReadoutRefusal):
        KotlinProgramHistoryReadoutResult
}

object KotlinProgramHistoryReadoutDomain {
    private const val MAX_WORKOUTS=128
    private const val MAX_WORKING_SETS=4096
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun digest(value:String):String=MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(""){(it.toInt() and 255).toString(16).padStart(2,'0')}

    fun review(
        input:KotlinProgramHistoryReadoutRequest
    ):KotlinProgramHistoryReadoutResult {
        fun refuse(reason:KotlinProgramHistoryReadoutRefusal)=
            KotlinProgramHistoryReadoutResult.Rejected(reason)
        if(!id.matches(input.athleteId) || !id.matches(input.cycleId) ||
            input.allowedDayIndices.isEmpty() ||
            input.allowedDayIndices.any{it !in 0..6})
            return refuse(KotlinProgramHistoryReadoutRefusal.INVALID_SCOPE)
        if(input.finishedWorkouts.size>MAX_WORKOUTS ||
            input.savedCorrections.size>MAX_WORKOUTS)
            return refuse(KotlinProgramHistoryReadoutRefusal.INPUT_LIMIT)
        val byId=input.finishedWorkouts.groupBy{it.snapshot.workoutId}
        if(byId.any{it.value.size!=1})
            return refuse(KotlinProgramHistoryReadoutRefusal.DUPLICATE_WORKOUT)
        val correctionsByWorkout=input.savedCorrections.groupBy{
            it.originalFinishedJournal.snapshot.workoutId
        }
        if(correctionsByWorkout.any{it.value.size!=1})
            return refuse(KotlinProgramHistoryReadoutRefusal.DUPLICATE_WORKOUT)
        if(correctionsByWorkout.keys.any{it !in byId})
            return refuse(
                KotlinProgramHistoryReadoutRefusal.CORRECTION_WITHOUT_SOURCE_WORKOUT)

        val workouts=mutableListOf<KotlinProgramHistoricalWorkout>()
        val sets=mutableListOf<KotlinProgramHistoricalWorkingSet>()
        // Canonical order is keyed by actual workout IDs. No fabricated
        // workout chronology/date is inferred from the ID or input position.
        for(journal in input.finishedWorkouts.sortedBy{it.snapshot.workoutId}) {
            if(journal.snapshot.source!=input.expectedSource ||
                journal.originalSnapshot.source!=input.expectedSource ||
                journal.storedIdentity.source!=input.expectedSource)
                return refuse(KotlinProgramHistoryReadoutRefusal.SOURCE_MISMATCH)
            if(journal.snapshot.dayIndex !in input.allowedDayIndices)
                return refuse(KotlinProgramHistoryReadoutRefusal.INVALID_DAY_OR_SET)
            val correction=correctionsByWorkout[journal.snapshot.workoutId]
                ?.singleOrNull()
            if(correction!=null && correction.originalFinishedJournal!=journal)
                return refuse(KotlinProgramHistoryReadoutRefusal.SOURCE_MISMATCH)
            val projected=if(correction==null)
                KotlinWorkoutHistoryEvidenceDomain.project(journal)
            else KotlinWorkoutHistoryEvidenceDomain.projectSaved(correction)
            val evidence=(projected as? KotlinHistoryProjectionResult.Candidate)
                ?.evidence ?:return refuse(
                KotlinProgramHistoryReadoutRefusal.INVALID_REPLAY_OR_CORRECTION)
            if(evidence.source!=input.expectedSource ||
                evidence.workoutId!=journal.snapshot.workoutId ||
                evidence.finishEventId!=journal.finishedByEventId ||
                evidence.journalRevision!=journal.revision)
                return refuse(KotlinProgramHistoryReadoutRefusal.SOURCE_MISMATCH)
            val working=evidence.sets.filter{it.type==DraftSetType.WORKING}
            if(sets.size+working.size>MAX_WORKING_SETS)
                return refuse(KotlinProgramHistoryReadoutRefusal.INPUT_LIMIT)
            val reviewByExercise=evidence.exerciseReviews.associateBy{
                it.exerciseId
            }
            if(reviewByExercise.size!=evidence.exerciseReviews.size)
                return refuse(KotlinProgramHistoryReadoutRefusal.INVALID_DAY_OR_SET)
            val originalKeys=working.map{it.setKey}
            if(originalKeys.toSet().size!=originalKeys.size ||
                working.any{it.originalOrdinal<0})
                return refuse(KotlinProgramHistoryReadoutRefusal.INVALID_DAY_OR_SET)
            val guardByExercise=working.groupBy{it.exerciseId}.mapValues{
                (exerciseId,exerciseSets)->
                val machineIds=exerciseSets.map{
                    it.actual?.machineInstanceId
                }.toSet()
                val reviewed=reviewByExercise[exerciseId]
                val sourceLoads=exerciseSets.map{it.issuedLoad}
                val uniformSource=sourceLoads.firstOrNull()?.let{first->
                    sourceLoads.all{load->
                        when {
                            first is DraftSetLoad.Verified &&
                                load is DraftSetLoad.Verified ->
                                    first.unit==load.unit &&
                                    first.basis==load.basis &&
                                    first.value.compareTo(load.value)==0
                            else ->first==load
                        }
                    }
                }==true
                when {
                    machineIds.size>1 ->
                        KotlinHistoryComparisonGuard.MIXED_PHYSICAL_MACHINES
                    null in machineIds ->
                        KotlinHistoryComparisonGuard.MACHINE_UNKNOWN
                    reviewed?.progressionGate!=
                        KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW ||
                        !uniformSource ||
                        exerciseSets.any{
                            it.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                                it.actual?.load==null
                        } ->KotlinHistoryComparisonGuard.INELIGIBLE_SESSION
                    else ->KotlinHistoryComparisonGuard.COMPARABLE_REVIEW_ONLY
                }
            }
            for(fact in working) {
                if(KotlinWorkingSetEvidenceDomain.review(
                    fact.originalOrdinal,fact.issuedLoad,fact.issuedRir,
                    fact.outcome,fact.actual
                ) !is KotlinWorkingSetEvidenceReview.Accepted)
                    return refuse(KotlinProgramHistoryReadoutRefusal.INVALID_DAY_OR_SET)
                sets+=KotlinProgramHistoricalWorkingSet(
                    evidence.workoutId,journal.snapshot.dayIndex,
                    fact.exerciseId,fact.setKey,fact.originalOrdinal,
                    fact.issuedLoad,fact.issuedReps,fact.issuedRir,
                    fact.outcome,fact.actual?.load,fact.actual?.unit,
                    fact.actual?.reps,fact.actual?.actualRir,
                    fact.actual?.machineInstanceId,
                    fact.effectiveEventId,fact.correctsEventId,
                    checkNotNull(guardByExercise[fact.exerciseId]))
            }
            workouts+=KotlinProgramHistoricalWorkout(
                evidence.workoutId,journal.snapshot.dayIndex,
                evidence.finishEventId,evidence.journalRevision,
                correction?.correctionRevision ?:0L,
                working.count{it.outcome==KotlinJournalSetOutcome.COMPLETED},
                working.count{it.outcome==KotlinJournalSetOutcome.SKIPPED},
                working.count{it.outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT})
        }
        val immutableWorkouts=Collections.unmodifiableList(workouts.toList())
        val immutableSets=Collections.unmodifiableList(sets.toList())
        val digest=digest(listOf("KOTLIN_PROGRAM_HISTORY_REPLAY_V1",
            input.athleteId,input.cycleId,input.expectedSource.toString(),
            input.allowedDayIndices.sorted().joinToString(","),
            immutableWorkouts.toString(),immutableSets.toString()
        ).joinToString("\n"))
        return KotlinProgramHistoryReadoutResult.Candidate(
            KotlinProgramHistoryReadout(1,input.athleteId,input.cycleId,
                input.expectedSource,immutableWorkouts,immutableSets,digest))
    }
}
