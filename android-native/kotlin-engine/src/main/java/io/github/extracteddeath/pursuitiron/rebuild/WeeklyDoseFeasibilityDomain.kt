package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Audits an explicitly proposed week against CALLER-SUPPLIED phase-specific
 * MEV/MAV/MRV and time constraints. No invented thresholds, set insertion,
 * exercise selection, or prescription. Infeasible means the submitted week
 * violates declared constraints, NOT that no alternative plan is possible.
 */
enum class TrainingPhase { HYPERTROPHY, STRENGTH, PEAK }

data class DeclaredMuscleDoseBounds(
    val muscle: TrainingMuscle,
    val mev: BigDecimal,
    val mav: BigDecimal,
    val mrv: BigDecimal,
    val minimumDirectDays: Int
)

data class DeclaredSessionWindow(
    val preferredMinimumMinutes: Int,
    /** Null permits 120+ sessions. An upper preference is not a hard cap. */
    val preferredMaximumMinutes: Int?,
    val userHardLimitMinutes: Int?,
    /** Explicitly estimated setup, warmup transitions and other non-set time. */
    val declaredNonSetSeconds: Int
)

data class PlannedWorkingExposure(
    val setKey: String,
    val dayIndex: Int,
    val kind: ExposureSetKind,
    val credits: List<DeclaredMuscleCredit>,
    /** Estimated duration and rest are inputs; never inferred from the bucket. */
    val activeSeconds: Int,
    val restSeconds: Int
)

data class DeclaredTrainingWeek(
    val phase: TrainingPhase,
    val days: Int,
    val windows: List<DeclaredSessionWindow>,
    val exposures: List<PlannedWorkingExposure>,
    /** All 16 muscle groups, even zero-dose exemptions, must be explicit. */
    val bounds: List<DeclaredMuscleDoseBounds>
)

enum class DoseFinding { BELOW_MEV, WITHIN_BOUNDS, ABOVE_MRV }

data class WeeklyMuscleDoseFinding(
    val muscle: TrainingMuscle,
    val directWorkingSets: BigDecimal,
    val indirectWorkingSets: BigDecimal,
    val effectiveWorkingSets: BigDecimal,
    val extensionDirect: BigDecimal,
    val extensionIndirect: BigDecimal,
    val directTrainingDays: Int,
    val requiredDirectDays: Int,
    val finding: DoseFinding,
    val gapToMev: BigDecimal,
    val gapToMav: BigDecimal,
    val excessAboveMrv: BigDecimal,
    val missingDirectDays: Int
)

enum class TimeWindowFinding {
    BELOW_PREFERENCE, WITHIN_PREFERENCE, ABOVE_PREFERENCE, ABOVE_USER_HARD_LIMIT
}
data class DayTimeFinding(
    val dayIndex: Int,
    val declaredSeconds: Long,
    val roundedMinutes: Long,
    val finding: TimeWindowFinding
)

data class WeeklyDoseFeasibilityReport(
    val phase: TrainingPhase,
    val muscles: Map<TrainingMuscle, WeeklyMuscleDoseFinding>,
    val sessions: List<DayTimeFinding>,
    val satisfiesDeclaredHardConstraints: Boolean,
    /** Soft ranges still require review; a preferred max is NOT a hard stop. */
    val daysOutsidePreferredWindow: Set<Int>,
    /** A nominal training day without a real working set needs revision. */
    val daysWithoutWorkingSets: Set<Int>,
    /** Extensions have no certified regular-set conversion rule yet. */
    val requiresExtensionDosePolicy: Boolean,
    /** A triage signal only, never permission to publish a program. */
    val preliminaryReviewNeeded: Boolean
)

/** Read-only feasibility report. Not a trained/validated program generator. */
object WeeklyDoseFeasibilityDomain {
    private val zero=BigDecimal.ZERO
    private val maxDose=BigDecimal("200")
    private val one=BigDecimal.ONE
    private const val MAX_EXPOSURES=2048
    private val validKey=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,127}$")

    private fun decimal(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())
    private fun validDose(v:BigDecimal)=v>=zero && v<=maxDose &&
        v.stripTrailingZeros().scale()<=3

    fun audit(week: DeclaredTrainingWeek): WeeklyDoseFeasibilityReport {
        require(week.days in 3..6)
        require(week.windows.size==week.days)
        require(week.bounds.size==TrainingMuscle.entries.size)
        require(week.bounds.map {it.muscle}.toSet()==TrainingMuscle.entries.toSet())
        require(week.bounds.all {
            validDose(it.mev) && validDose(it.mav) && validDose(it.mrv) &&
                it.mev<=it.mav && it.mav<=it.mrv &&
                it.minimumDirectDays in 0..week.days
        })
        require(week.windows.all {
            it.preferredMinimumMinutes in 1..360 &&
                (it.preferredMaximumMinutes==null ||
                    it.preferredMaximumMinutes in it.preferredMinimumMinutes..360) &&
                (it.userHardLimitMinutes==null ||
                    it.userHardLimitMinutes in 1..360) &&
                it.declaredNonSetSeconds in 0..(360*60)
        })
        require(week.exposures.size<=MAX_EXPOSURES)
        require(week.exposures.map {it.setKey}.distinct().size==week.exposures.size)
        require(week.exposures.all {set ->
            validKey.matches(set.setKey) &&
            set.dayIndex in 0 until week.days &&
            set.activeSeconds in 1..3600 && set.restSeconds in 0..3600 &&
            (set.kind==ExposureSetKind.WARMUP || set.credits.isNotEmpty()) &&
            set.credits.size<=TrainingMuscle.entries.size &&
            set.credits.map {it.muscle}.distinct().size==set.credits.size &&
            set.credits.all {credit ->
                credit.fractionalSet>zero && credit.fractionalSet<=one &&
                    credit.fractionalSet.stripTrailingZeros().scale()<=3
            }
        })

        val boundsByMuscle=week.bounds.associateBy {it.muscle}
        val muscleFindings=LinkedHashMap<TrainingMuscle,WeeklyMuscleDoseFinding>()
        for(muscle in TrainingMuscle.entries) {
            val bounds=checkNotNull(boundsByMuscle[muscle])
            var direct=zero
            var indirect=zero
            var extDirect=zero
            var extIndirect=zero
            val directDays=HashSet<Int>()
            for(set in week.exposures) {
                if(set.kind==ExposureSetKind.WARMUP)continue
                val credit=set.credits.firstOrNull {it.muscle==muscle} ?:continue
                when(set.kind to credit.role) {
                    ExposureSetKind.WORKING to MuscleCreditRole.DIRECT -> {
                        direct=direct.add(credit.fractionalSet)
                        directDays.add(set.dayIndex)
                    }
                    ExposureSetKind.WORKING to MuscleCreditRole.INDIRECT ->
                        indirect=indirect.add(credit.fractionalSet)
                    ExposureSetKind.EXTENSION to MuscleCreditRole.DIRECT ->
                        extDirect=extDirect.add(credit.fractionalSet)
                    ExposureSetKind.EXTENSION to MuscleCreditRole.INDIRECT ->
                        extIndirect=extIndirect.add(credit.fractionalSet)
                    else -> error("Unsupported credit kind")
                }
            }
            val effective=direct.add(indirect)
            val finding=when {
                effective<bounds.mev -> DoseFinding.BELOW_MEV
                effective>bounds.mrv -> DoseFinding.ABOVE_MRV
                else -> DoseFinding.WITHIN_BOUNDS
            }
            fun gap(target:BigDecimal)=if(target>effective)
                decimal(target.subtract(effective)) else zero
            muscleFindings[muscle]=WeeklyMuscleDoseFinding(
                muscle=muscle,
                directWorkingSets=decimal(direct),
                indirectWorkingSets=decimal(indirect),
                effectiveWorkingSets=decimal(effective),
                extensionDirect=decimal(extDirect),
                extensionIndirect=decimal(extIndirect),
                directTrainingDays=directDays.size,
                requiredDirectDays=bounds.minimumDirectDays,
                finding=finding,
                gapToMev=gap(bounds.mev),
                gapToMav=gap(bounds.mav),
                excessAboveMrv=if(effective>bounds.mrv)
                    decimal(effective.subtract(bounds.mrv)) else zero,
                missingDirectDays=(bounds.minimumDirectDays-directDays.size).coerceAtLeast(0)
            )
        }

        val sessions=week.windows.mapIndexed {index,window->
            val total=week.exposures.asSequence().filter {it.dayIndex==index}
                .sumOf {it.activeSeconds.toLong()+it.restSeconds.toLong()}+
                window.declaredNonSetSeconds
            val minutes=(total+59L)/60L
            val timeStatus=when {
                window.userHardLimitMinutes!=null && minutes>window.userHardLimitMinutes ->
                    TimeWindowFinding.ABOVE_USER_HARD_LIMIT
                minutes<window.preferredMinimumMinutes -> TimeWindowFinding.BELOW_PREFERENCE
                window.preferredMaximumMinutes!=null &&
                    minutes>window.preferredMaximumMinutes ->
                    TimeWindowFinding.ABOVE_PREFERENCE
                else -> TimeWindowFinding.WITHIN_PREFERENCE
            }
            DayTimeFinding(index,total,minutes,timeStatus)
        }
        val hardConstraintsPass=muscleFindings.values.all {
            it.finding==DoseFinding.WITHIN_BOUNDS && it.missingDirectDays==0
        } && sessions.none {it.finding==TimeWindowFinding.ABOVE_USER_HARD_LIMIT}
        val outsidePreference=sessions.filter {
            it.finding!=TimeWindowFinding.WITHIN_PREFERENCE
        }.map {it.dayIndex}.toSet()
        val unconvertedExtensions=week.exposures.any {
            it.kind==ExposureSetKind.EXTENSION
        }
        val activeTrainingDays=week.exposures.asSequence()
            .filter {it.kind==ExposureSetKind.WORKING}
            .map {it.dayIndex}.toSet()
        val missingWorkDays=(0 until week.days).filter {
            it !in activeTrainingDays
        }.toSet()
        return WeeklyDoseFeasibilityReport(
            phase=week.phase,
            muscles=Collections.unmodifiableMap(muscleFindings),
            sessions=Collections.unmodifiableList(sessions),
            satisfiesDeclaredHardConstraints=hardConstraintsPass,
            daysOutsidePreferredWindow=Collections.unmodifiableSet(outsidePreference),
            daysWithoutWorkingSets=Collections.unmodifiableSet(missingWorkDays),
            requiresExtensionDosePolicy=unconvertedExtensions,
            preliminaryReviewNeeded=!hardConstraintsPass ||
                outsidePreference.isNotEmpty() ||
                missingWorkDays.isNotEmpty() || unconvertedExtensions
        )
    }
}
