package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * The only simple one-rep advancement rule for a previously *issued exact*
 * working-set target. It is used by bodyweight and weighted rep-only modes.
 * This calculation has no 1RM, RIR, readiness or load authority.
 */
internal object BasicRepTargetStepDomain {
    fun advance(
        issuedTargets:List<Int>,
        actualReps:List<Int>,
        band:RepBand
    ):List<Int>? {
        if(issuedTargets.isEmpty() || issuedTargets.size>32 ||
            actualReps.size!=issuedTargets.size ||
            issuedTargets.any {it !in band.minimum..band.maximum} ||
            actualReps.any {!KotlinSetNumberAuthorityDomain.validCompletedReps(it)})return null
        return Collections.unmodifiableList(issuedTargets.mapIndexed {i,target ->
            if(actualReps[i]>=target && target<band.maximum) target+1 else target
        })
    }
}

data class BasicUnloadedProgressionScope(
    val athleteId:String,
    val gymId:String,
    val programId:String,
    val blockId:String,
    val trainingDayId:String,
    val exerciseId:String
) {
    fun valid()=listOf(athleteId,gymId,programId,blockId,trainingDayId,exerciseId)
        .all {it.isNotBlank()}
}
/** Actual unweighted completed work has no physical unit or machine load. */
data class BasicUnloadedWorkoutEvidence(
    val workoutId:String,
    val exerciseId:String,
    val targetReps:RepBand,
    val expectedWorkingSets:Int,
    val observations:List<WorkingSetFact>,
    val trainingDayId:String
)

data class BasicUnloadedProgressionRequest(
    val nextWorkoutId:String,
    val nextScope:BasicUnloadedProgressionScope,
    val lastScope:BasicUnloadedProgressionScope?,
    val last:BasicUnloadedWorkoutEvidence?,
    val issuedWorkingSets:List<DraftSetLoad>,
    val issuedExactReps:List<Int>,
    val repBand:RepBand
)
enum class BasicUnloadedRefusal {
    UNVERIFIED_BODYWEIGHT_SOURCE, NO_COMPARABLE_SESSION,
    INCOMPLETE_WORK, INVALID_REP_TARGETS
}
sealed interface BasicUnloadedProgressionResult {
    data class Rejected(val reason:BasicUnloadedRefusal):BasicUnloadedProgressionResult
    data class Candidate(
        val proposedExactReps:List<Int>,
        val advancedOrdinals:Set<Int>,
        val evidenceWorkoutId:String,
        /** At the cap, a harder movement or explicitly added load needs review. */
        val atRepCeiling:Boolean=false
    ):BasicUnloadedProgressionResult {
        val mayChangeIssuedWorkout:Boolean get()=false
        val maySaveProgram:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
    }
}

/**
 * Reps only for explicitly authored unweighted bodyweight.
 * An added-weight vest, assisted pullup, machine, or missing actual is NEVER
 * converted into unweighted work. This does not claim one more rep is
 * physiologically guaranteed; it is an optional target for athlete review.
 */
object BasicBodyweightRepProgressionDomain {
    fun review(request:BasicUnloadedProgressionRequest):BasicUnloadedProgressionResult {
        fun no(reason:BasicUnloadedRefusal)=BasicUnloadedProgressionResult.Rejected(reason)
        val count=request.issuedWorkingSets.size
        if(request.nextWorkoutId.isBlank() || !request.nextScope.valid() ||
            count !in 1..32 || request.issuedWorkingSets.any{
                it!=DraftSetLoad.ExplicitBodyweight
            })return no(BasicUnloadedRefusal.UNVERIFIED_BODYWEIGHT_SOURCE)
        val previous=request.last ?:return no(BasicUnloadedRefusal.NO_COMPARABLE_SESSION)
        if(request.lastScope!=request.nextScope ||
            previous.workoutId.isBlank() || previous.workoutId==request.nextWorkoutId ||
            previous.exerciseId!=request.nextScope.exerciseId ||
            previous.trainingDayId!=request.nextScope.trainingDayId ||
            previous.targetReps!=request.repBand ||
            previous.expectedWorkingSets!=count)
            return no(BasicUnloadedRefusal.NO_COMPARABLE_SESSION)
        val facts=previous.observations.sortedBy{it.workingOrdinal}
        if(facts.size!=count ||
            facts.map{it.workingOrdinal}!=request.issuedWorkingSets.indices.toList() ||
            facts.any {
                it.outcome!=SetOutcome.COMPLETED ||
                it.actualLoad!=null || it.issuedLoad!=null ||
                !KotlinSetNumberAuthorityDomain.validCompletedReps(it.actualReps) ||
                !KotlinSetNumberAuthorityDomain.validRir(it.actualRir)
            })
            return no(BasicUnloadedRefusal.INCOMPLETE_WORK)
        val completedReps=facts.map{it.actualReps!!}
        val proposal=BasicRepTargetStepDomain.advance(
            request.issuedExactReps,completedReps,request.repBand)
            ?:return no(BasicUnloadedRefusal.INVALID_REP_TARGETS)
        val changed=proposal.indices.filter {proposal[it]!=request.issuedExactReps[it]}.toSet()
        return BasicUnloadedProgressionResult.Candidate(proposal,
            Collections.unmodifiableSet(changed),previous.workoutId,
            changed.isEmpty() && completedReps.all{it>=request.repBand.maximum})
    }
}
