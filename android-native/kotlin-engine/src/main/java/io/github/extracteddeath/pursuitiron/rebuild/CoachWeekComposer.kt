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
    val priority:Int=1
)
data class ComposerDay(val calendarDay:Int,val focus:ComposerFocus,
    val window:DeclaredSessionWindow)
data class ComposerRequest(
    val split:ComposerSplit,
    val phase:TrainingPhase,
    val days:List<ComposerDay>,
    val bounds:List<DeclaredMuscleDoseBounds>,
    val exercises:List<ComposerExerciseOption>,
    val maximumDifferentExercisesPerDay:Int,
    /** A user-supplied preference, NOT a universal training rule. */
    val preferredPatternsPerDay:Int=2
)
data class ComposerVisit(val dayIndex:Int,val exerciseId:String,
    val pattern:ComposerPattern,val sets:Int)
enum class ComposerStatus { FEASIBLE_DRAFT_FOR_REVIEW, CONSTRAINTS_UNRESOLVED }
data class ComposerDraft(
    val status:ComposerStatus,
    val week:DeclaredTrainingWeek,
    val visits:List<ComposerVisit>,
    val audit:WeeklyDoseFeasibilityReport,
    val excludedUnverifiedExerciseIds:Set<String>,
    val missingRequiredVisits:Map<String,Int>,
    val daysBelowPatternPreference:Set<Int>,
    val explanations:List<String>
) {
    /** A reviewed candidate is NOT user-approved or executable. */
    val canIssueTargets:Boolean get()=false
}

/**
 * Bounded greedy search: prefer repairing dose/frequency, required visits
 * and empty days; then improve MAV, time and pattern variety. The search
 * is not proof that an alternative feasible program is impossible.
 */
object CoachWeekComposer {
    private val zero=BigDecimal.ZERO
    private val idPattern=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,54}$")
    private const val MAX_OPTIONS=32
    private const val MAX_VISITS=60
    private const val MAX_ITERATIONS=140

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
        require(request.exercises.map {it.exerciseId}.distinct().size==
            request.exercises.size)
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
            option.priority in 1..10
        })
        WeeklyDoseFeasibilityDomain.audit(DeclaredTrainingWeek(
            request.phase,request.days.size,request.days.map {it.window},
            emptyList(),request.bounds))
    }
    private fun toWeek(request:ComposerRequest,
        visits:List<ComposerVisit>,
        options:Map<String,ComposerExerciseOption>):DeclaredTrainingWeek {
        val sets=visits.flatMap {visit ->
            val option=checkNotNull(options[visit.exerciseId])
            (0 until visit.sets).map {ordinal ->
                PlannedWorkingExposure(
                    option.exerciseId+":d"+visit.dayIndex+":s"+ordinal,
                    visit.dayIndex,ExposureSetKind.WORKING,option.credits,
                    option.workSeconds,option.restSeconds)
            }
        }
        return DeclaredTrainingWeek(request.phase,request.days.size,
            request.days.map {it.window},sets,request.bounds)
    }
    private fun missing(option:ComposerExerciseOption,
        visits:List<ComposerVisit>)=(option.requiredVisits-
        visits.count {it.exerciseId==option.exerciseId}).coerceAtLeast(0)

    private fun mayStart(option:ComposerExerciseOption,dayIndex:Int,
        request:ComposerRequest,visits:List<ComposerVisit>):Boolean {
        if(request.days[dayIndex].focus !in option.allowedFocus)return false
        if(visits.count {it.exerciseId==option.exerciseId}>=option.maximumVisits)return false
        if(visits.count {it.dayIndex==dayIndex}>=
            request.maximumDifferentExercisesPerDay)return false
        val day=request.days[dayIndex].calendarDay
        return visits.filter {it.exerciseId==option.exerciseId}.all {
            val other=request.days[it.dayIndex].calendarDay
            val gap=abs(day-other)
            minOf(gap,7-gap)>=option.minimumCalendarDayGap
        }
    }
    private data class Cost(val hard:Long,val soft:Long):Comparable<Cost> {
        override fun compareTo(other:Cost):Int=compareValuesBy(
            this,other,{it.hard},{it.soft})
    }
    private fun cost(request:ComposerRequest,visits:List<ComposerVisit>,
        audit:WeeklyDoseFeasibilityReport,
        options:Map<String,ComposerExerciseOption>):Cost {
        val mevGap=audit.muscles.values.sumOf {
            it.gapToMev.multiply(BigDecimal("1000")).toLong()
        }
        val hard=mevGap*10L+
            audit.muscles.values.sumOf {it.missingDirectDays}*3000L+
            audit.daysWithoutWorkingSets.size*3500L+
            request.exercises.sumOf {missing(it,visits)}*10000L
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
        return Cost(hard,mavGap*2L+short*180L+long*250L+
            patternGap*400L+visits.sumOf {it.sets}*100L-priority*6L)
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
        for(iteration in 0 until MAX_ITERATIONS) {
            if(chosen.size>=MAX_VISITS)break
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
                val candidate=toWeek(request,trial,map)
                val check=WeeklyDoseFeasibilityDomain.audit(candidate)
                if(check.muscles.values.any {
                    it.finding==DoseFinding.ABOVE_MRV
                } || check.sessions.any {
                    it.finding==TimeWindowFinding.ABOVE_USER_HARD_LIMIT
                })continue
                // Do not pad sessions with extra volume above the
                // athlete's MAV once minimums/frequency/visits are met.
                if(current.hard==0L && check.muscles.values.any {finding ->
                    val bound=request.bounds.first {it.muscle==finding.muscle}
                    finding.effectiveWorkingSets>bound.mav
                })continue
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
        }
        val unmet=request.exercises.filter {missing(it,chosen)>0}
            .associate {it.exerciseId to missing(it,chosen)}
        val missingPatterns=request.days.indices.filter {day->
            chosen.filter {it.dayIndex==day}.map {it.pattern}.distinct().size<
                request.preferredPatternsPerDay
        }.toSet()
        val notes=mutableListOf<String>()
        if(rejected.isNotEmpty())
            notes+="Some exercise options have no verified usable loading setup."
        if(unmet.isNotEmpty())
            notes+="Pinned exercise appearances could not all be scheduled."
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
        val feasible=audit.satisfiesDeclaredHardConstraints && unmet.isEmpty() &&
            audit.daysWithoutWorkingSets.isEmpty() &&
            audit.daysOutsidePreferredWindow.isEmpty() &&
            missingPatterns.isEmpty() && !audit.requiresExtensionDosePolicy
        return ComposerDraft(
            if(feasible)ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW
                else ComposerStatus.CONSTRAINTS_UNRESOLVED,
            week,Collections.unmodifiableList(chosen),audit,
            Collections.unmodifiableSet(rejected),
            Collections.unmodifiableMap(unmet),
            Collections.unmodifiableSet(missingPatterns),
            Collections.unmodifiableList(notes))
    }
}
