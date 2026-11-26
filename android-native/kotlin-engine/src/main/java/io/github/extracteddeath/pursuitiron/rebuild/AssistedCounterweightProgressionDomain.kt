package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Dedicated evidence-only progression for a physical assisted machine.
 *
 * The recorded quantity is the machine's selected COUNTERWEIGHT, not the
 * athlete's lifted mass. Less assistance means greater bodyweight demand.
 * Ordinary higher-external-load progression is permanently inapplicable.
 *
 * Both station identifiers and dated training-day cells MUST originate from
 * authenticated current owner/history; this pure review grants no write.
 */
enum class AssistedCounterweightReviewStatus {
    WRONG_LOAD_BASIS,
    UNCOMPARABLE_DAY_OR_STATION,
    INCOMPLETE_OR_NONUNIFORM_WORK,
    REP_CEILING_NOT_ACHIEVED,
    EFFORT_UNVERIFIED_OR_TOO_HARD,
    ISSUED_LOAD_NOT_PERFORMED,
    COUNTERWEIGHT_NOT_IN_GYM,
    NO_LOWER_CONFIRMED_ASSISTANCE,
    CHANGE_EXCEEDS_ATHLETE_LIMIT,
    LESS_ASSISTANCE_FOR_REVIEW
}

data class AssistedCounterweightReviewRequest(
    val previous:ProgressionWorkoutEvidence,
    val latest:ProgressionWorkoutEvidence,
    val previousMachineInstanceId:String?,
    val latestMachineInstanceId:String?,
    val verifiedStock:VerifiedLoadStock?,
    /** Largest reduction in the displayed assistance units, not added mass. */
    val maximumAssistanceReduction:BigDecimal
)

data class AssistedCounterweightReview(
    val status:AssistedCounterweightReviewStatus,
    val currentAssistance:BigDecimal?,
    val suggestedAssistance:BigDecimal?,
    val assumedGoalRirSetCount:Int,
    val explanation:String
) {
    val canOfferForReview:Boolean get()=
        status==AssistedCounterweightReviewStatus.LESS_ASSISTANCE_FOR_REVIEW &&
            suggestedAssistance!=null
    val mayAutoApply:Boolean get()=false
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayChangeHistory:Boolean get()=false
}

object AssistedCounterweightProgressionDomain {
    private val zero=BigDecimal.ZERO
    private fun equal(a:BigDecimal,b:BigDecimal)=a.compareTo(b)==0
    private fun canonical(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())

    fun review(request:AssistedCounterweightReviewRequest):AssistedCounterweightReview {
        val old=request.previous
        val recent=request.latest
        val stock=request.verifiedStock
        fun reject(kind:AssistedCounterweightReviewStatus,
            current:BigDecimal?=null,assumed:Int=0) =
            AssistedCounterweightReview(kind,current,null,assumed,when(kind) {
                AssistedCounterweightReviewStatus.WRONG_LOAD_BASIS ->
                    "A verified physical assisted-machine COUNTERWEIGHT is required."
                AssistedCounterweightReviewStatus.UNCOMPARABLE_DAY_OR_STATION ->
                    "Use two completed workouts on the same training day and exact physical assisted machine; another machine has independent resistance."
                AssistedCounterweightReviewStatus.INCOMPLETE_OR_NONUNIFORM_WORK ->
                    "Every prescribed working set must be completed at the same actual counterweight in both sessions."
                AssistedCounterweightReviewStatus.REP_CEILING_NOT_ACHIEVED ->
                    "Build repetitions at the present assistance before attempting less help."
                AssistedCounterweightReviewStatus.EFFORT_UNVERIFIED_OR_TOO_HARD ->
                    "Two sessions must have at least two RIR, or a clearly identified issued goal-RIR assumption."
                AssistedCounterweightReviewStatus.ISSUED_LOAD_NOT_PERFORMED ->
                    "Logged counterweight must match each immutable issued working-set target."
                AssistedCounterweightReviewStatus.COUNTERWEIGHT_NOT_IN_GYM ->
                    "The current counterweight must be an exact selectable setting in this gym."
                AssistedCounterweightReviewStatus.NO_LOWER_CONFIRMED_ASSISTANCE ->
                    "No lower assisted-machine counterweight is confirmed; keep the prescribed setting."
                AssistedCounterweightReviewStatus.CHANGE_EXCEEDS_ATHLETE_LIMIT ->
                    "The available reduction exceeds the athlete's permitted one-session assistance change."
                AssistedCounterweightReviewStatus.LESS_ASSISTANCE_FOR_REVIEW ->
                    error("An accepted reduction requires an exact suggested setting.")
            })
        if(stock?.basis!=LoadBasis.ASSISTANCE_COUNTERWEIGHT)
            return reject(AssistedCounterweightReviewStatus.WRONG_LOAD_BASIS)
        if(request.maximumAssistanceReduction<=zero ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(request.maximumAssistanceReduction))
            return reject(AssistedCounterweightReviewStatus.CHANGE_EXCEEDS_ATHLETE_LIMIT)
        if(old.workoutId.isBlank() || recent.workoutId.isBlank() ||
            old.workoutId==recent.workoutId ||
            old.exerciseId.isBlank() || old.exerciseId!=recent.exerciseId ||
            old.unit!=recent.unit || stock.unit!=recent.unit ||
            old.targetReps!=recent.targetReps ||
            old.expectedWorkingSets!=recent.expectedWorkingSets ||
            old.trainingDayId.isNullOrBlank() ||
            old.trainingDayId!=recent.trainingDayId ||
            request.previousMachineInstanceId.isNullOrBlank() ||
            request.previousMachineInstanceId!=request.latestMachineInstanceId)
            return reject(AssistedCounterweightReviewStatus.UNCOMPARABLE_DAY_OR_STATION)
        val assessments=runCatching {listOf(old,recent).map { session->
            DoubleProgressionEvidenceDomain.assess(session.targetReps,
                session.expectedWorkingSets,session.observations)
        }}.getOrNull() ?:return reject(
            AssistedCounterweightReviewStatus.INCOMPLETE_OR_NONUNIFORM_WORK)
        if(assessments.zip(listOf(old,recent)).any { (e,session)->
            e.completedWorkingSets!=session.expectedWorkingSets ||
                e.mixedActualLoads ||
                session.observations.size!=session.expectedWorkingSets ||
                session.observations.any { it.actualLoad==null }
        })return reject(AssistedCounterweightReviewStatus.INCOMPLETE_OR_NONUNIFORM_WORK)
        if(assessments.zip(listOf(old,recent)).any { (e,session)->
            e.ceilingOrdinals.size!=session.expectedWorkingSets ||
                e.belowFloorOrdinals.isNotEmpty()
        })return reject(AssistedCounterweightReviewStatus.REP_CEILING_NOT_ACHIEVED)
        val all=(old.observations+recent.observations)
        val effort=all.map(ProgressionEffortDomain::resolve)
        val assumed=effort.count {it.assumed}
        if(effort.any {it.rir==null || checkNotNull(it.rir)<2})
            return reject(AssistedCounterweightReviewStatus.EFFORT_UNVERIFIED_OR_TOO_HARD,
                assumed=assumed)
        if(all.any {set->set.actualLoad==null || set.issuedLoad==null ||
                !equal(set.actualLoad,set.issuedLoad)})
            return reject(AssistedCounterweightReviewStatus.ISSUED_LOAD_NOT_PERFORMED,
                assumed=assumed)
        val previousLoad=old.observations.first().actualLoad!!
        val current=canonical(recent.observations.first().actualLoad!!)
        if(old.observations.any { !equal(it.actualLoad!!,current) } ||
            !equal(previousLoad,current))
            return reject(AssistedCounterweightReviewStatus.INCOMPLETE_OR_NONUNIFORM_WORK,
                current,assumed)
        val exact=EquipmentLoadDomain.inspect(stock,current,recent.unit)
            as? LoadInspection.Confirmed
        if(exact?.exact!=true)
            return reject(AssistedCounterweightReviewStatus.COUNTERWEIGHT_NOT_IN_GYM,
                current,assumed)
        if(current<=zero)return reject(
            AssistedCounterweightReviewStatus.NO_LOWER_CONFIRMED_ASSISTANCE,current,assumed)
        val candidate=exact.nextHarder
        if(candidate==null || !KotlinSetNumberAuthorityDomain.validPrescribedLoad(candidate,exact.basis) ||
            exact.basis.compareDifficulty(candidate,current)<=0)
            return reject(AssistedCounterweightReviewStatus.NO_LOWER_CONFIRMED_ASSISTANCE,
                current,assumed)
        if(current.subtract(candidate)>request.maximumAssistanceReduction)
            return reject(AssistedCounterweightReviewStatus.CHANGE_EXCEEDS_ATHLETE_LIMIT,
                current,assumed)
        return AssistedCounterweightReview(
            AssistedCounterweightReviewStatus.LESS_ASSISTANCE_FOR_REVIEW,current,
            canonical(candidate),assumed,
            "Two comparable same-day, same-machine workouts support reviewing LESS counterweight: " +
                "${current.toPlainString()} to ${candidate.toPlainString()} ${recent.unit.name.lowercase()}. " +
                "The machine will provide less assistance, NOT a higher external load. " +
                "No saved target or recorded set changes automatically."+
                ProgressionEffortDomain.assumptionNote(assumed))
    }
}
