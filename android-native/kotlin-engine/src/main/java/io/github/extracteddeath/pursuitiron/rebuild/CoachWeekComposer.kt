package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections
import kotlin.math.abs

/** Deterministic, read-only weekly candidate composer. Never issues targets. */
enum class ComposerSplit { FULL_BODY, UPPER_LOWER, PUSH_PULL_LEGS, ULPPL }
enum class ComposerFocus { FULL_BODY, UPPER, LOWER, PUSH, PULL, LEGS }
enum class ComposerPattern {
    HORIZONTAL_PRESS, VERTICAL_PRESS, HORIZONTAL_PULL, VERTICAL_PULL,
    SQUAT, HINGE, ISOLATION, CORE
}
/** Catalog-authored exercise role, never guessed from movement name. */
enum class ComposerRole { PRIMARY, SECONDARY, ACCESSORY }
enum class ComposerSessionPurpose {
    UNSPECIFIED, STRENGTH_SKILL, HYPERTROPHY, GENERAL_PREPARATION
}
/** Explicit day-specific intent. Null primary means the caller did not set one. */
data class ComposerSessionIntent(
    val dayIndex:Int,
    val purpose:ComposerSessionPurpose,
    val requiredPrimaryExerciseId:String?=null
)
data class ComposerSessionPlan(
    val dayIndex:Int,
    val purpose:ComposerSessionPurpose,
    val orderedVisits:List<ComposerVisit>,
    val primaryExerciseId:String?,
    val requiredPrimaryScheduled:Boolean
)
sealed interface ComposerEquipment {
    data class Verified(val unit:LoadUnit,val plannedLoad:BigDecimal,
        val inventory:VerifiedLoadStock?):ComposerEquipment
    data object Bodyweight:ComposerEquipment
}
data class ComposerExerciseOption(
    val exerciseId:String,
    val pattern:ComposerPattern,
    val allowedFocus:Set<ComposerFocus>,
    val equipment:ComposerEquipment,
    val credits:List<DeclaredMuscleCredit>,
    val workSeconds:Int,
    val restSeconds:Int,
    val minimumSetsPerVisit:Int,
    val maximumSetsPerVisit:Int,
    val maximumVisits:Int,
    /** Explicit custom-program/pinned exercise appearances. */
    val requiredVisits:Int,
    /** Real calendar separation, including Sunday to next Monday. */
    val minimumCalendarDayGap:Int,
    val priority:Int=1,
    /** Optional caller-defined heavy movement recovery group. */
    val recoveryGroup:String?=null,
    /** Minimum gap BETWEEN days, not a restriction within one session. */
    val minimumGroupCalendarDayGap:Int=1,
    val role:ComposerRole=ComposerRole.SECONDARY,
    /** Historical source's practical rep envelope; NOT clinical certification. */
    val sourcePracticalReps:RepBand?=null
)
data class ComposerDay(val calendarDay:Int,val focus:ComposerFocus,
    val window:DeclaredSessionWindow)
/** Planned durations only. No load, performed set or muscle-dose observation. */
data class ComposerWarmupTiming(val activeSeconds:Int,val restSeconds:Int)
data class ComposerVisitTiming(
    val exerciseId:String,
    val purpose:ComposerSessionPurpose,
    val activeSecondsPerSet:Int,
    val restSecondsPerSet:Int,
    val warmups:List<ComposerWarmupTiming> = emptyList(),
    /** A day-specific policy overrides the shared policy for that purpose. */
    val dayIndex:Int?=null
)
data class ComposerRequest(
    val split:ComposerSplit,
    val phase:TrainingPhase,
    val days:List<ComposerDay>,
    val bounds:List<DeclaredMuscleDoseBounds>,
    val exercises:List<ComposerExerciseOption>,
    val maximumDifferentExercisesPerDay:Int,
    /** A user-supplied preference, NOT a universal training rule. */
    val preferredPatternsPerDay:Int=2,
    /** Missing entries are unassigned, not inferred from phase. */
    val sessionIntents:List<ComposerSessionIntent> = emptyList(),
    /**
     * Athlete-owned cap on distinct BARBELL_TOTAL exercise visits each day.
     * Null means this legacy/unbound composer caller declared no cap.
     * Zero is an explicit prohibition, not an absent preference.
     */
    val maximumBarbellExercisesPerDay:Int?=null,
    /** An explicit restriction on direct triceps credit during LOWER days. */
    val allowDirectTricepsOnLower:Boolean=true,
    /** Empty preserves historical composition. New prescriptions supply
     * their actual per-visit warmups and working-set/rest durations. */
    val visitTimings:List<ComposerVisitTiming> = emptyList()
)
data class ComposerVisit(val dayIndex:Int,val exerciseId:String,
    val pattern:ComposerPattern,val sets:Int)
enum class ComposerStatus { FEASIBLE_DRAFT_FOR_REVIEW, CONSTRAINTS_UNRESOLVED }
/**
 * Search conclusions describe EVIDENCE, not permission to train.
 * A source conflict is a mathematical or inventory bound, not proof that
 * different equipment, an expanded catalog or a changed policy cannot work.
 */
enum class ComposerSearchConclusion {
    FEASIBLE_CANDIDATE_FOUND,
    PROVEN_CONFLICT_WITH_CURRENT_INPUTS,
    SEARCH_BUDGET_REACHED,
    UNRESOLVED_LOCAL_SEARCH
}
data class ComposerSearchEvidence(
    val conclusion:ComposerSearchConclusion,
    val greedyRounds:Int,
    val refinementPasses:Int,
    val candidateEvaluations:Int,
    val budgetReached:Boolean,
    val provableInputConflicts:List<String>
)

/** Review capabilities, not save or issuance authorization. Authored defaults deny all edits. */
data class ComposerRepairPermissions(
    val mayAdjustSets:Boolean=false,
    val mayAddVisits:Boolean=false,
    val mayReplaceExercises:Boolean=false,
    val mayMoveVisits:Boolean=false
) {
    companion object {
        val GENERATED_DRAFT=ComposerRepairPermissions(true,true,true,true)
    }
}
data class ComposerDraft(
    val status:ComposerStatus,
    val week:DeclaredTrainingWeek,
    val visits:List<ComposerVisit>,
    val audit:WeeklyDoseFeasibilityReport,
    val excludedUnverifiedExerciseIds:Set<String>,
    val missingRequiredVisits:Map<String,Int>,
    val daysBelowPatternPreference:Set<Int>,
    val sessions:List<ComposerSessionPlan>,
    val daysWithUnmetPrimary:Set<Int>,
    val explanations:List<String>,
    val searchEvidence:ComposerSearchEvidence
) {
    /** A reviewed candidate is NOT user-approved or executable. */
    val canIssueTargets:Boolean get()=false
}

/**
 * Bounded two-stage candidate search: greedily establish a feasible week,
 * then deterministically rebalance by moving or swapping visits without
 * pretending that more volume is always better. A local result is not proof
 * that no better training plan exists; never issues live workout targets.
 */
object CoachWeekComposer {
    private val zero=BigDecimal.ZERO
    private val idPattern=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,54}$")
    private const val MAX_OPTIONS=32
    private const val MAX_VISITS=60
    private const val MAX_ITERATIONS=140
    private const val MAX_REPAIR_PASSES=8
    private const val MAX_REPAIR_EVALUATIONS_PER_PASS=1200

    private fun allowedSplit(request:ComposerRequest):Boolean {
        val focus=request.days.map {it.focus}.toSet()
        return when(request.split) {
            ComposerSplit.FULL_BODY -> focus==setOf(ComposerFocus.FULL_BODY)
            ComposerSplit.UPPER_LOWER ->
                focus==setOf(ComposerFocus.UPPER,ComposerFocus.LOWER)
            ComposerSplit.PUSH_PULL_LEGS ->
                focus==setOf(ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)
            ComposerSplit.ULPPL -> request.days.size in 5..6 &&
                focus==setOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
                    ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)
        }
    }
    private fun verified(option:ComposerExerciseOption):Boolean=when(
        val equipment=option.equipment
    ) {
        ComposerEquipment.Bodyweight -> true
        is ComposerEquipment.Verified -> {
            val result=EquipmentLoadDomain.inspect(equipment.inventory,
                equipment.plannedLoad,equipment.unit)
            result is LoadInspection.Confirmed && result.exact
        }
    }
    private fun validate(request:ComposerRequest) {
        require(request.days.size in 3..6)
        require(request.days.map {it.calendarDay}==
            request.days.map {it.calendarDay}.distinct().sorted())
        require(request.days.all {it.calendarDay in 0..6})
        require(allowedSplit(request))
        require(request.exercises.size in 1..MAX_OPTIONS)
        require(request.maximumDifferentExercisesPerDay in 1..12)
        require(request.preferredPatternsPerDay in 1..4)
        require(request.maximumBarbellExercisesPerDay==null ||
            request.maximumBarbellExercisesPerDay in 0..12)
        require(request.exercises.map {it.exerciseId}.distinct().size==
            request.exercises.size)
        require(request.sessionIntents.size<=request.days.size)
        require(request.sessionIntents.map {it.dayIndex}.distinct().size==
            request.sessionIntents.size)
        require(request.sessionIntents.all {intent ->
            intent.dayIndex in request.days.indices &&
                (intent.requiredPrimaryExerciseId==null ||
                    request.exercises.any {option ->
                        option.exerciseId==intent.requiredPrimaryExerciseId &&
                            option.role==ComposerRole.PRIMARY &&
                            request.days[intent.dayIndex].focus in option.allowedFocus
                    })
        })
        require(request.visitTimings.size<=128)
        require(request.visitTimings.map {
            Triple(it.exerciseId,it.purpose,it.dayIndex)
        }.distinct().size==request.visitTimings.size)
        require(request.visitTimings.all {t->
            request.exercises.any{it.exerciseId==t.exerciseId} &&
                t.activeSecondsPerSet in 1..3600 && t.restSecondsPerSet in 0..3600 &&
                t.warmups.size<=12 && t.warmups.all {
                    it.activeSeconds in 1..3600 && it.restSeconds in 0..3600
                } && (t.dayIndex==null ||
                    request.sessionIntents.any {
                        it.dayIndex==t.dayIndex && it.purpose==t.purpose
                    })
        })
        require(request.exercises.all {option ->
            idPattern.matches(option.exerciseId) &&
            option.allowedFocus.isNotEmpty() &&
            option.credits.isNotEmpty() &&
            option.credits.map {it.muscle}.distinct().size==option.credits.size &&
            option.credits.all {
                it.fractionalSet>zero && it.fractionalSet<=BigDecimal.ONE &&
                    it.fractionalSet.stripTrailingZeros().scale()<=3
            } &&
            option.workSeconds in 1..3600 && option.restSeconds in 0..3600 &&
            option.minimumSetsPerVisit in 1..8 &&
            option.maximumSetsPerVisit in option.minimumSetsPerVisit..12 &&
            option.maximumVisits in 1..request.days.size &&
            option.requiredVisits in 0..option.maximumVisits &&
            option.minimumCalendarDayGap in 1..6 &&
            option.priority in 1..10 &&
            (option.recoveryGroup==null || idPattern.matches(option.recoveryGroup)) &&
            option.minimumGroupCalendarDayGap in 1..6
        })
        WeeklyDoseFeasibilityDomain.audit(DeclaredTrainingWeek(
            request.phase,request.days.size,request.days.map {it.window},
            emptyList(),request.bounds))
    }
    private fun timing(request:ComposerRequest,exerciseId:String,day:Int):ComposerVisitTiming? {
        val purpose=request.sessionIntents.firstOrNull{it.dayIndex==day}?.purpose
            ?:ComposerSessionPurpose.UNSPECIFIED
        return request.visitTimings.singleOrNull {
            it.exerciseId==exerciseId && it.purpose==purpose && it.dayIndex==day
        } ?:request.visitTimings.singleOrNull {
            it.exerciseId==exerciseId && it.purpose==purpose && it.dayIndex==null
        }
    }
    private fun toWeek(request:ComposerRequest,
        visits:List<ComposerVisit>,
        options:Map<String,ComposerExerciseOption>):DeclaredTrainingWeek {
        // A session ends after its last performed set. Counting a post-finish
        // rest makes an underfilled workout appear artificially longer.
        // Sequence is the actual prescribed sequence, not greedy insertion order.
        val lastByDay=orderedSessions(request,visits,options)
            .associate {it.dayIndex to it.orderedVisits.lastOrNull()?.exerciseId}
        val sets=visits.flatMap {visit ->
            val option=checkNotNull(options[visit.exerciseId])
            val timing=timing(request,visit.exerciseId,visit.dayIndex)
            val warmups=timing?.warmups.orEmpty().mapIndexed {ordinal,warmup->
                PlannedWorkingExposure(
                    option.exerciseId+":d"+visit.dayIndex+":w"+ordinal,
                    visit.dayIndex,ExposureSetKind.WARMUP,emptyList(),
                    warmup.activeSeconds,warmup.restSeconds)
            }
            warmups+(0 until visit.sets).map {ordinal ->
                val isFinal=lastByDay[visit.dayIndex]==visit.exerciseId &&
                    ordinal==visit.sets-1
                PlannedWorkingExposure(
                    option.exerciseId+":d"+visit.dayIndex+":s"+ordinal,
                    visit.dayIndex,ExposureSetKind.WORKING,option.credits,
                    timing?.activeSecondsPerSet ?:option.workSeconds,
                    if(isFinal)0 else timing?.restSecondsPerSet ?:option.restSeconds)
            }
        }
        return DeclaredTrainingWeek(request.phase,request.days.size,
            request.days.map {it.window},sets,request.bounds)
    }
    private fun missing(option:ComposerExerciseOption,
        visits:List<ComposerVisit>)=(option.requiredVisits-
        visits.count {it.exerciseId==option.exerciseId}).coerceAtLeast(0)

    private fun isBarbell(option:ComposerExerciseOption):Boolean =
        (option.equipment as? ComposerEquipment.Verified)?.inventory?.basis==
            LoadBasis.BARBELL_TOTAL

    private fun directTriceps(option:ComposerExerciseOption):Boolean =
        option.credits.any {it.muscle==TrainingMuscle.TRICEPS &&
            it.role==MuscleCreditRole.DIRECT}

    private fun mayStart(option:ComposerExerciseOption,dayIndex:Int,
        request:ComposerRequest,visits:List<ComposerVisit>):Boolean {
        if(request.days[dayIndex].focus !in option.allowedFocus)return false
        // A visit is unique by exercise AND training day. Replacement and
        // relocation searches must not create duplicate stable set keys.
        if(visits.any {it.exerciseId==option.exerciseId &&
                it.dayIndex==dayIndex})return false
        if(visits.count {it.exerciseId==option.exerciseId}>=option.maximumVisits)return false
        if(visits.count {it.dayIndex==dayIndex}>=
            request.maximumDifferentExercisesPerDay)return false
        // Reject before scoring, not after an infeasible week is built.
        if(request.days[dayIndex].focus in setOf(
                ComposerFocus.LOWER,ComposerFocus.LEGS) &&
            !request.allowDirectTricepsOnLower && directTriceps(option))
            return false
        val barbellCap=request.maximumBarbellExercisesPerDay
        if(barbellCap!=null && isBarbell(option) &&
            visits.count {existing ->
                existing.dayIndex==dayIndex &&
                    isBarbell(request.exercises.first {
                        it.exerciseId==existing.exerciseId
                    })
            }>=barbellCap)return false
        val day=request.days[dayIndex].calendarDay
        return visits.all {otherVisit ->
            if(otherVisit.dayIndex==dayIndex)return@all true
            val otherOption=request.exercises.first {
                it.exerciseId==otherVisit.exerciseId
            }
            val neededGap=when {
                otherOption.exerciseId==option.exerciseId ->
                    // A shared reviewed recovery group is a floor even for
                    // repeat visits of the very same exercise ID.
                    maxOf(option.minimumCalendarDayGap,
                        if(option.recoveryGroup!=null)
                            option.minimumGroupCalendarDayGap else 1)
                option.recoveryGroup!=null &&
                    option.recoveryGroup==otherOption.recoveryGroup ->
                    maxOf(option.minimumGroupCalendarDayGap,
                        otherOption.minimumGroupCalendarDayGap)
                else ->1
            }
            val otherDay=request.days[otherVisit.dayIndex].calendarDay
            val gap=abs(day-otherDay)
            minOf(gap,7-gap)>=neededGap
        }
    }
    /**
     * Hard promises are not exchangeable numeric weights. A missing named
     * primary, pinned appearance or scheduled workout cannot be "paid for"
     * by chasing additional muscle volume. Each tier is independently
     * minimized before later tiers or soft preferences may influence search.
     * This is a deterministic ranking of unresolved candidates, NOT a claim
     * that any incomplete program is safe to issue.
     */
    private data class Cost(
        val missingPrimaryDays:Int,
        val missingRequiredVisits:Int,
        val emptyScheduledDays:Int,
        val missingDirectMuscleDays:Int,
        val mevDeficitThousandths:Long,
        val soft:Long,
        /** Tie-break only: never compete with dose, time or training priority. */
        val sameStationCrowding:Int,
        val cableSmithVisits:Int
    ):Comparable<Cost> {
        val hardSatisfied:Boolean get()=missingPrimaryDays==0 &&
            missingRequiredVisits==0 && emptyScheduledDays==0 &&
            missingDirectMuscleDays==0 && mevDeficitThousandths==0L
        override fun compareTo(other:Cost):Int=compareValuesBy(this,other,
            {it.missingPrimaryDays},{it.missingRequiredVisits},
            {it.emptyScheduledDays},{it.missingDirectMuscleDays},
            {it.mevDeficitThousandths},{it.soft},
            {it.sameStationCrowding},{it.cableSmithVisits})
    }
    private fun unmetPrimaryDays(request:ComposerRequest,
        visits:List<ComposerVisit>):Set<Int> =
        request.sessionIntents.filter {intent ->
            val id=intent.requiredPrimaryExerciseId
            id!=null && visits.none {
                it.dayIndex==intent.dayIndex && it.exerciseId==id
            }
        }.map {it.dayIndex}.toSet()

    private fun orderedSessions(request:ComposerRequest,
        visits:List<ComposerVisit>,
        options:Map<String,ComposerExerciseOption>):List<ComposerSessionPlan> =
        request.days.indices.map {day ->
            val intent=request.sessionIntents.firstOrNull {it.dayIndex==day}
            val priority=compareBy<ComposerVisit> {
                // The athlete's explicit main lift starts first, even when
                // another primary has a higher general catalog priority.
                if(intent?.requiredPrimaryExerciseId==it.exerciseId)-1
                else when(options.getValue(it.exerciseId).role) {
                    ComposerRole.PRIMARY -> 0
                    ComposerRole.SECONDARY -> 1
                    ComposerRole.ACCESSORY -> 2
                }
            }.thenByDescending {options.getValue(it.exerciseId).priority}
                .thenBy {it.exerciseId}
            val ordered=visits.filter {it.dayIndex==day}.sortedWith(priority)
            val primary=intent?.requiredPrimaryExerciseId
            ComposerSessionPlan(day,intent?.purpose ?: ComposerSessionPurpose.UNSPECIFIED,
                Collections.unmodifiableList(ordered),primary,
                primary==null || ordered.any {it.exerciseId==primary})
        }

    private fun cost(request:ComposerRequest,visits:List<ComposerVisit>,
        audit:WeeklyDoseFeasibilityReport,
        options:Map<String,ComposerExerciseOption>):Cost {
        val mevGap=audit.muscles.values.sumOf {
            it.gapToMev.multiply(BigDecimal("1000")).toLong()
        }
        val missingPrimary=unmetPrimaryDays(request,visits).size
        val missingPinned=request.exercises.sumOf {missing(it,visits)}
        val emptyDays=audit.daysWithoutWorkingSets.size
        val missingDirect=audit.muscles.values.sumOf {it.missingDirectDays}
        val mavGap=audit.muscles.values.sumOf {
            it.gapToMav.multiply(BigDecimal("1000")).toLong()
        }
        val short=audit.sessions.sumOf {
            (request.days[it.dayIndex].window.preferredMinimumMinutes-
                it.roundedMinutes).coerceAtLeast(0L)
        }
        val long=audit.sessions.sumOf {
            val max=request.days[it.dayIndex].window.preferredMaximumMinutes
            if(max==null)0L else (it.roundedMinutes-max).coerceAtLeast(0L)
        }
        val patternGap=request.days.indices.sumOf {day ->
            (request.preferredPatternsPerDay-
                visits.filter {it.dayIndex==day}.map {it.pattern}.distinct().size)
                .coerceAtLeast(0)
        }
        val priority=visits.sumOf {
            checkNotNull(options[it.exerciseId]).priority.toLong()*it.sets
        }
        // A cable station and a Smith rack are distinct. A machine stack
        // might be an entirely separate machine, so never invent a shared
        // physical station for it. These are the only known bases whose
        // repeated use can be distinguished from an otherwise equivalent
        // dumbbell/barbell option using the verified input model.
        val stationVisits=visits.mapNotNull { visit ->
            val stock=(options.getValue(visit.exerciseId).equipment
                as? ComposerEquipment.Verified)?.inventory
            val basis=stock?.basis
            if(basis==LoadBasis.CABLE_STACK || basis==LoadBasis.SMITH_TOTAL)
                visit.dayIndex to basis else null
        }
        val crowding=stationVisits.groupingBy {it}.eachCount()
            .values.sumOf {(it-1).coerceAtLeast(0)}
        // This comes AFTER every existing hard and soft cost. The composer
        // will prefer a source-verified free-weight alternative only when
        // it is equally good on dose, time, priority and pattern coverage.
        // It never adds stock, removes required exercise appearances, or
        // changes the athlete's actual prescribed loads.
        return Cost(missingPrimary,missingPinned,emptyDays,missingDirect,
            mevGap,mavGap*2L+short*180L+long*250L+
                patternGap*400L+visits.sumOf {it.sets}*100L-priority*6L,
            crowding,stationVisits.size)
    }
    /**
     * One candidate safety policy for BOTH initial generation and local
     * refinement. Phase/dose legality, declared user hard-time ceilings and
     * the no-junk-sets-above-MAV rule must never diverge between search paths.
     * This does not grant workout issuance or resolve a missing coach policy.
     */
    private fun withinCandidateLimits(
        request:ComposerRequest,
        current:Cost,
        candidate:WeeklyDoseFeasibilityReport
    ):Boolean {
        if(candidate.muscles.values.any {
            it.finding==DoseFinding.ABOVE_MRV
        })return false
        if(candidate.sessions.any {
            it.finding==TimeWindowFinding.ABOVE_USER_HARD_LIMIT
        })return false
        if(current.hardSatisfied && candidate.muscles.values.any { finding ->
            finding.effectiveWorkingSets >
                request.bounds.first {it.muscle==finding.muscle}.mav
        })return false
        return true
    }


    /**
     * Only prove contradictions from verified inventory and deliberately
     * OPTIMISTIC upper bounds. These proofs ignore session conflicts,
     * calendar recovery, per-day caps and other limits; therefore finding
     * an upper bound below MEV is sufficient to prove this source selection
     * cannot satisfy MEV, while reaching it NEVER proves feasibility.
     */
    private fun provableInputConflicts(
        request:ComposerRequest,
        eligible:List<ComposerExerciseOption>
    ):List<String> {
        val viable=eligible.filter {option ->
            request.days.any {it.focus in option.allowedFocus}
        }
        val ids=viable.map {it.exerciseId}.toSet()
        val conflicts=mutableListOf<String>()
        request.exercises.filter {it.requiredVisits>0 && it.exerciseId !in ids}
            .forEach {option ->
                conflicts+="Required exercise ${option.exerciseId} has no verified eligible placement."
            }
        for(intent in request.sessionIntents) {
            val id=intent.requiredPrimaryExerciseId ?:continue
            if(viable.none { it.exerciseId==id &&
                    request.days[intent.dayIndex].focus in it.allowedFocus })
                conflicts+="Required primary ${id} on day ${intent.dayIndex} has no verified eligible placement."
        }
        for(day in request.days.indices) {
            if(viable.none { request.days[day].focus in it.allowedFocus })
                conflicts+="Scheduled day ${day} has no verified exercise compatible with its focus."
            val window=request.days[day].window
            if(window.userHardLimitMinutes!=null &&
                window.declaredNonSetSeconds.toLong() >
                    window.userHardLimitMinutes.toLong()*60L)
                conflicts+="Scheduled day ${day} has overhead exceeding its hard time limit."
        }
        for(bound in request.bounds) {
            val useful=viable.mapNotNull {option ->
                val credit=option.credits.firstOrNull {it.muscle==bound.muscle}
                if(credit==null)null else option to credit
            }
            val upper=useful.fold(BigDecimal.ZERO) {sum,(option,credit) ->
                sum.add(credit.fractionalSet.multiply(
                    BigDecimal(option.maximumSetsPerVisit.toLong()*
                        option.maximumVisits.toLong())))
            }
            if(upper<bound.mev)
                conflicts+="${bound.muscle} MEV exceeds the optimistic maximum dose from verified exercises."
            if(bound.minimumDirectDays>0) {
                val direct=useful.filter {it.second.role==MuscleCreditRole.DIRECT}
                val visitCeiling=direct.sumOf {it.first.maximumVisits}
                val eligibleDays=request.days.count {day ->
                    direct.any {day.focus in it.first.allowedFocus}
                }
                if(minOf(visitCeiling,eligibleDays)<bound.minimumDirectDays)
                    conflicts+="${bound.muscle} direct-day frequency exceeds verified placement capacity."
            }
        }
        return Collections.unmodifiableList(conflicts)
    }

    private data class RefinementOutcome(
        val visits:List<ComposerVisit>,
        val passes:Int,
        val candidateEvaluations:Int,
        val budgetReached:Boolean
    )
    /**
     * Read-only local refinement. Keep session positions, equipment, pinned
     * appearances and cross-week recovery verifiable at every accepted move.
     * Swaps can recover from a fully occupied day; relocations can rescue an
     * empty day or move work to a more suitable session.
     *
     * Only STRICT cost improvements are accepted, so results are deterministic
     * and the bounded search terminates even when preferences conflict.
     */
    private fun refine(
        request:ComposerRequest,
        seed:List<ComposerVisit>,
        eligible:List<ComposerExerciseOption>,
        permissions:ComposerRepairPermissions
    ):RefinementOutcome {
        val options=eligible.associateBy {it.exerciseId}
        var chosen=seed
        var audit=WeeklyDoseFeasibilityDomain.audit(toWeek(request,chosen,options))
        var passes=0
        var totalEvaluations=0
        var hitEvaluationBudget=false
        repeat(MAX_REPAIR_PASSES) {
            passes++
            val current=cost(request,chosen,audit,options)
            val originalPinnedGaps=request.exercises.associate {
                it.exerciseId to missing(it,chosen)
            }
            val originalUnmetPrimary=unmetPrimaryDays(request,chosen)
            var best=current
            var bestVisits:List<ComposerVisit>?=null
            var bestAudit:WeeklyDoseFeasibilityReport?=null
            var evaluated=0
            fun consider(trial:List<ComposerVisit>) {
                if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)return
                evaluated++
                // Hard athlete-owned promises are not interchangeable
                // numeric penalties. Never sacrifice any previously
                // scheduled pin or named main lift to improve another goal.
                if(request.exercises.any {
                    missing(it,trial)>checkNotNull(
                        originalPinnedGaps[it.exerciseId])
                })return
                if(!originalUnmetPrimary.containsAll(
                    unmetPrimaryDays(request,trial)))return
                val check=WeeklyDoseFeasibilityDomain.audit(
                    toWeek(request,trial,options))
                // Preserve each muscle's already-satisfied minimum dose
                // AND direct-day frequency while repairing other gaps.
                if(audit.muscles.values.any {before ->
                    before.finding==DoseFinding.WITHIN_BOUNDS &&
                        before.missingDirectDays==0 &&
                        check.muscles.getValue(before.muscle).let {after ->
                            after.finding!=DoseFinding.WITHIN_BOUNDS ||
                                after.missingDirectDays>0
                        }
                })return
                // A soft preference does not justify undoing an already
                // satisfied training day. Improving muscle dose elsewhere
                // must never make a previously scheduled day empty or push
                // a previously in-range session outside its time bucket.
                if(!audit.daysWithoutWorkingSets.containsAll(
                    check.daysWithoutWorkingSets))return
                if(audit.sessions.any { before ->
                    before.finding==TimeWindowFinding.WITHIN_PREFERENCE &&
                        check.sessions[before.dayIndex].finding!=
                            TimeWindowFinding.WITHIN_PREFERENCE
                })return
                if(!withinCandidateLimits(request,current,check))return
                val trialCost=cost(request,trial,check,options)
                if(trialCost<best) {
                    best=trialCost
                    bestVisits=trial
                    bestAudit=check
                }
            }
            if(permissions.mayAdjustSets) {
            // A valid authored week may be underfilled even after swaps and
            // relocations: recover missing dose by changing existing set
            // counts, and recover missing training-day variety by adding a
            // verified visit. These are independently audited against MEV,
            // MRV, the user's hard time ceiling, caps, and recovery spacing.
            // Try these BEFORE exchanges so bounded search cannot starve them.
            for(index in chosen.indices) {
                if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)break
                val visit=chosen[index]
                val option=options.getValue(visit.exerciseId)
                if(visit.sets<option.maximumSetsPerVisit)
                    consider(chosen.toMutableList().apply {
                        this[index]=visit.copy(sets=visit.sets+1)
                    })
                if(visit.sets>option.minimumSetsPerVisit)
                    consider(chosen.toMutableList().apply {
                        this[index]=visit.copy(sets=visit.sets-1)
                    })
            }
            }
            if(permissions.mayAddVisits && chosen.size<MAX_VISITS) {
                for(option in eligible) {
                    if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)break
                    for(day in request.days.indices) {
                        if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)break
                        if(!mayStart(option,day,request,chosen))continue
                        consider(chosen+ComposerVisit(day,option.exerciseId,
                            option.pattern,option.minimumSetsPerVisit))
                    }
                }
            }
            // An isolated relocation can be impossible when two days are
            // full. A simultaneous two-visit exchange may still be legal,
            // especially when the shorter session belongs on a different
            // calendar day. Authorize this ONLY as two date moves: neither
            // exercise identity nor authored set count may change.
            //
            // Validate both insertions against the same candidate state,
            // then run the full dose/time audit in consider(). Intermediate
            // states are never published or scored as finished programs.
            if(permissions.mayMoveVisits) {
                for(left in chosen.indices) {
                    if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)break
                    val first=chosen[left]
                    for(right in left+1 until chosen.size) {
                        if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)break
                        val second=chosen[right]
                        if(first.dayIndex==second.dayIndex)continue
                        val without=chosen.filterIndexed { index,_ ->
                            index!=left && index!=right
                        }
                        val firstOption=options.getValue(first.exerciseId)
                        val secondOption=options.getValue(second.exerciseId)
                        if(!mayStart(firstOption,second.dayIndex,request,without))
                            continue
                        val movedFirst=first.copy(dayIndex=second.dayIndex)
                        if(!mayStart(secondOption,first.dayIndex,request,
                                without+movedFirst))continue
                        consider(chosen.toMutableList().apply {
                            this[left]=movedFirst
                            this[right]=second.copy(dayIndex=first.dayIndex)
                        })
                    }
                }
            }
            if(permissions.mayReplaceExercises || permissions.mayMoveVisits)
            for(index in chosen.indices) {
                if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)break
                val original=chosen[index]
                val remaining=chosen.filterIndexed {i,_->i!=index}
                // An exercise replacement must be separately authorized.
                if(permissions.mayReplaceExercises)
                for(alternative in eligible) {
                    if(alternative.exerciseId==original.exerciseId)continue
                    if(!mayStart(alternative,original.dayIndex,request,remaining))
                        continue
                    // Replacing a movement does not also grant set-count
                    // changes. Preserve the authored count when lawful.
                    if(!permissions.mayAdjustSets &&
                        original.sets !in alternative.minimumSetsPerVisit..
                            alternative.maximumSetsPerVisit)continue
                    val sets=if(permissions.mayAdjustSets)
                        alternative.minimumSetsPerVisit else original.sets
                    val replacement=ComposerVisit(original.dayIndex,
                        alternative.exerciseId,alternative.pattern,sets)
                    consider(chosen.toMutableList().apply {this[index]=replacement})
                    if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)break
                }
                val selected=checkNotNull(options[original.exerciseId])
                // Movement relocation is independent from exercise substitution.
                if(permissions.mayMoveVisits)
                for(newDay in request.days.indices) {
                    if(newDay==original.dayIndex)continue
                    if(!mayStart(selected,newDay,request,remaining))continue
                    consider(chosen.toMutableList().apply {
                        this[index]=original.copy(dayIndex=newDay)
                    })
                    if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)break
                }
            }
            totalEvaluations+=evaluated
            if(evaluated>=MAX_REPAIR_EVALUATIONS_PER_PASS)
                hitEvaluationBudget=true
            if(bestVisits==null)return RefinementOutcome(
                chosen,passes,totalEvaluations,hitEvaluationBudget)
            chosen=checkNotNull(bestVisits)
            audit=checkNotNull(bestAudit)
        }
        // Even if this last pass improved the score, a bounded eight-pass
        // search does not prove a local optimum or a globally feasible plan.
        return RefinementOutcome(chosen,passes,totalEvaluations,true)
    }

    /**
     * Diagnostic seam for custom-program tests. No persistence and no
     * unsanctioned exercise insertion: input must already be a lawful week
     * of verified, authored catalog options.
     */
    internal fun rebalanceForReview(
        request:ComposerRequest,
        authoredVisits:List<ComposerVisit>,
        permissions:ComposerRepairPermissions=ComposerRepairPermissions()
    ):List<ComposerVisit> {
        validate(request)
        require(authoredVisits.size<=MAX_VISITS)
        val eligible=request.exercises.filter(::verified).sortedWith(
            compareByDescending<ComposerExerciseOption> {it.priority}
                .thenBy {it.exerciseId})
        val options=eligible.associateBy {it.exerciseId}
        val accepted=mutableListOf<ComposerVisit>()
        for(visit in authoredVisits) {
            val option=options[visit.exerciseId]
                ?:throw IllegalArgumentException("Unverified or unknown movement")
            require(visit.pattern==option.pattern &&
                visit.dayIndex in request.days.indices &&
                visit.sets in option.minimumSetsPerVisit..option.maximumSetsPerVisit &&
                accepted.none {it.dayIndex==visit.dayIndex &&
                    it.exerciseId==visit.exerciseId} &&
                mayStart(option,visit.dayIndex,request,accepted))
            accepted+=visit
        }
        return Collections.unmodifiableList(refine(request,accepted,eligible,
            permissions).visits)
    }

    fun compose(request:ComposerRequest):ComposerDraft {
        validate(request)
        val eligible=request.exercises.filter(::verified).sortedWith(
            compareByDescending<ComposerExerciseOption> {it.priority}
                .thenBy {it.exerciseId})
        val map=eligible.associateBy {it.exerciseId}
        val rejected=request.exercises.filterNot(::verified)
            .map {it.exerciseId}.toSet()
        var chosen:List<ComposerVisit> = emptyList()
        var week=toWeek(request,chosen,map)
        var audit=WeeklyDoseFeasibilityDomain.audit(week)
        var greedyRounds=0
        var greedyBudgetReached=false
        var greedyCandidateEvaluations=0
        for(iteration in 0 until MAX_ITERATIONS) {
            if(chosen.size>=MAX_VISITS) {
                greedyBudgetReached=true
                break
            }
            greedyRounds++
            val current=cost(request,chosen,audit,map)
            var best=current
            var bestChoice:List<ComposerVisit>?=null
            var bestAudit:WeeklyDoseFeasibilityReport?=null
            for(option in eligible)for(day in request.days.indices) {
                val index=chosen.indexOfFirst {
                    it.dayIndex==day && it.exerciseId==option.exerciseId
                }
                val trial=when {
                    index>=0 -> {
                        val old=chosen[index]
                        if(old.sets>=option.maximumSetsPerVisit)continue
                        chosen.toMutableList().apply {
                            this[index]=old.copy(sets=old.sets+1)
                        }
                    }
                    mayStart(option,day,request,chosen) ->
                        chosen+ComposerVisit(day,option.exerciseId,
                            option.pattern,option.minimumSetsPerVisit)
                    else ->continue
                }
                greedyCandidateEvaluations++
                val candidate=toWeek(request,trial,map)
                val check=WeeklyDoseFeasibilityDomain.audit(candidate)
                if(!withinCandidateLimits(request,current,check))continue
                val candidateCost=cost(request,trial,check,map)
                if(candidateCost<best) {
                    best=candidateCost
                    bestChoice=trial
                    bestAudit=check
                }
            }
            if(bestChoice==null)break
            chosen=bestChoice
            audit=checkNotNull(bestAudit)
            week=toWeek(request,chosen,map)
            if(iteration==MAX_ITERATIONS-1)greedyBudgetReached=true
        }
        // The greedy phase cannot replace occupied slots or relocate a
        // useful lift. Reconsider those decisions without violating verified
        // equipment, recovery spacing, dose or athlete hard-time limits.
        val refinement=refine(request,chosen,eligible,
            ComposerRepairPermissions.GENERATED_DRAFT)
        val improved=refinement.visits
        if(improved!=chosen) {
            chosen=improved
            week=toWeek(request,chosen,map)
            audit=WeeklyDoseFeasibilityDomain.audit(week)
        }
        val unmet=request.exercises.filter {missing(it,chosen)>0}
            .associate {it.exerciseId to missing(it,chosen)}
        val missingPatterns=request.days.indices.filter {day->
            chosen.filter {it.dayIndex==day}.map {it.pattern}.distinct().size<
                request.preferredPatternsPerDay
        }.toSet()
        val unmetPrimary=unmetPrimaryDays(request,chosen)
        val sessions=orderedSessions(request,chosen,map)
        val notes=mutableListOf<String>()
        val conflicts=provableInputConflicts(request,eligible)
        val budgetReached=greedyBudgetReached || refinement.budgetReached
        if(unmetPrimary.isNotEmpty())
            notes+="The requested main lift is missing on one or more days; the day purpose remains unresolved."
        if(rejected.isNotEmpty())
            notes+="Some exercise options have no verified usable loading setup."
        if(unmet.isNotEmpty())
            notes+="Pinned exercise appearances could not all be scheduled."
        if(request.maximumBarbellExercisesPerDay!=null && unmet.isNotEmpty() &&
            request.exercises.any {missing(it,chosen)>0 && isBarbell(it)})
            notes+="The authored barbell-day limit may prevent some requested barbell appearances."
        if(!request.allowDirectTricepsOnLower && unmet.isNotEmpty() &&
            request.exercises.any {missing(it,chosen)>0 && directTriceps(it)})
            notes+="A requested direct-triceps exercise may need an eligible upper day."
        if(!audit.satisfiesDeclaredHardConstraints)
            notes+="The candidate has muscle dose, frequency or hard-time conflicts."
        if(audit.daysWithoutWorkingSets.isNotEmpty())
            notes+="Some scheduled days have no working sets."
        if(audit.daysOutsidePreferredWindow.isNotEmpty())
            notes+="Some sessions miss your preferred time range; no filler sets were invented."
        if(missingPatterns.isNotEmpty())
            notes+="Some days have less movement-pattern variety than requested."
        if(notes.isEmpty())
            notes+="Declared constraints pass; the complete program still requires coaching review."
        val feasible=audit.satisfiesDeclaredHardConstraints &&
            unmet.isEmpty() && unmetPrimary.isEmpty() &&
            audit.daysWithoutWorkingSets.isEmpty() &&
            audit.daysOutsidePreferredWindow.isEmpty() &&
            missingPatterns.isEmpty() && !audit.requiresExtensionDosePolicy
        val conclusion=when {
            feasible -> ComposerSearchConclusion.FEASIBLE_CANDIDATE_FOUND
            conflicts.isNotEmpty() ->
                ComposerSearchConclusion.PROVEN_CONFLICT_WITH_CURRENT_INPUTS
            budgetReached -> ComposerSearchConclusion.SEARCH_BUDGET_REACHED
            else -> ComposerSearchConclusion.UNRESOLVED_LOCAL_SEARCH
        }
        if(!feasible) {
            if(conflicts.isNotEmpty())
                notes+="Some constraints cannot be met with the verified current exercise catalog."
            else if(budgetReached)
                notes+="The bounded search reached a limit; a different valid plan may exist."
            else
                notes+="This local search found no better candidate; other solutions may exist."
        }
        val evidence=ComposerSearchEvidence(conclusion,greedyRounds,
            refinement.passes,
            greedyCandidateEvaluations+refinement.candidateEvaluations,
            budgetReached,conflicts)
        return ComposerDraft(
            if(feasible)ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW
                else ComposerStatus.CONSTRAINTS_UNRESOLVED,
            week,Collections.unmodifiableList(chosen),audit,
            Collections.unmodifiableSet(rejected),
            Collections.unmodifiableMap(unmet),
            Collections.unmodifiableSet(missingPatterns),
            Collections.unmodifiableList(sessions),
            Collections.unmodifiableSet(unmetPrimary),
            Collections.unmodifiableList(notes),evidence)
    }
}
