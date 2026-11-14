package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Precise accounting of DECLARED muscle credits, not an unvalidated
 * physiological volume threshold. Exercise credit coefficients must
 * eventually come from an independently vetted movement catalog.
 */
enum class TrainingMuscle(val introducedIn:Int=1) {
    CHEST, LATS, UPPER_BACK, FRONT_DELTS, SIDE_DELTS, REAR_DELTS,
    TRICEPS, BICEPS, BRACHIALIS,
    WRIST_FLEXORS, WRIST_EXTENSORS,
    QUADS, HAMSTRINGS, GLUTES, CALVES, ABS,
    HIP_ABDUCTORS(2), HIP_ADDUCTORS(2),
    SPINAL_ERECTORS(3), TRAPS(3)
}

/**
 * Saved policies keep their exact declared coverage. A complete v1 policy
 * still has the original 16 rows; v2 keeps its exact 18 hip-inclusive rows.
 * Current policies also track spinal erectors and trapezius separately.
 * Never fill absent rows with invented zeroes, accept a
 * partial original scope, or silently upgrade a signed policy.
 */
object TrainingMuscleCoverage {
    val legacyV1:List<TrainingMuscle> = Collections.unmodifiableList(
        TrainingMuscle.entries.filter{it.introducedIn==1})
    val legacyV2:List<TrainingMuscle> = Collections.unmodifiableList(
        TrainingMuscle.entries.filter{it.introducedIn<=2})
    val current:List<TrainingMuscle> = Collections.unmodifiableList(
        TrainingMuscle.entries.toList())
    private val scopes=listOf(legacyV1,legacyV2,current).map{it.toSet()}

    fun versionOf(muscles:Collection<TrainingMuscle>):Int? {
        val distinct=muscles.toSet()
        if(distinct.size!=muscles.size)return null
        return scopes.indexOf(distinct).takeIf{it>=0}?.plus(1)
    }
    fun isComplete(muscles:Collection<TrainingMuscle>):Boolean=versionOf(muscles)!=null
}
enum class MuscleCreditRole { DIRECT, INDIRECT }
enum class ExposureSetKind { WARMUP, WORKING, EXTENSION }

data class DeclaredMuscleCredit(
    val muscle: TrainingMuscle,
    val role: MuscleCreditRole,
    val fractionalSet: BigDecimal
)

data class ObservedMuscleSet(
    /** Stable, unique identity of an issued set occurrence, not exercise ID. */
    val setKey: String,
    val kind: ExposureSetKind,
    val outcome: SetOutcome,
    val credits: List<DeclaredMuscleCredit>
)

data class MuscleVolumeTotals(
    val workingDirect: BigDecimal,
    val workingIndirect: BigDecimal,
    val extensionDirect: BigDecimal,
    val extensionIndirect: BigDecimal
) {
    /**
     * Extensions are separate on purpose; no arbitrary conversion of
     * myo-reps/drop mini-sets into regular working sets.
     */
    val workingEquivalent: BigDecimal get()=workingDirect.add(workingIndirect)
}

data class ObservedMuscleVolume(
    val totals: Map<TrainingMuscle,MuscleVolumeTotals>,
    val completedWorking: Int,
    val skippedWorking: Int,
    val failedWorking: Int,
    val completedExtensions: Int,
    val completedWarmups: Int
)

/** Deterministic, bounded and never grants credit for failed/skipped work. */
object MuscleVolumeAccountingDomain {
    private val zero=BigDecimal.ZERO
    private val one=BigDecimal.ONE
    private val validKey=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,127}$")
    private const val MAX_SESSION_SETS=2048

    private fun normalized(value:BigDecimal)=BigDecimal(
        value.stripTrailingZeros().toPlainString())

    fun summarize(observations:List<ObservedMuscleSet>):ObservedMuscleVolume {
        require(observations.size<=MAX_SESSION_SETS)
        require(observations.map {it.setKey}.distinct().size==observations.size)
        require(observations.all {validKey.matches(it.setKey)})
        // Validate even unperformed prescriptions so malformed exercise
        // allocations cannot hide behind skipped or failed positions.
        for(set in observations) {
            if(set.kind!=ExposureSetKind.WARMUP)require(set.credits.isNotEmpty())
            require(set.credits.size<=TrainingMuscle.entries.size)
            require(set.credits.map {it.muscle}.distinct().size==set.credits.size)
            require(set.credits.all {credit ->
                credit.fractionalSet>zero && credit.fractionalSet<=one &&
                    credit.fractionalSet.stripTrailingZeros().scale()<=3
            })
        }

        data class MutableTotals(
            var wd:BigDecimal=zero,var wi:BigDecimal=zero,
            var ed:BigDecimal=zero,var ei:BigDecimal=zero
        )
        val mutable=TrainingMuscle.entries.associateWith {MutableTotals()}
        for(set in observations) {
            if(set.outcome!=SetOutcome.COMPLETED || set.kind==ExposureSetKind.WARMUP)continue
            for(credit in set.credits) {
                val t=checkNotNull(mutable[credit.muscle])
                when(set.kind to credit.role) {
                    ExposureSetKind.WORKING to MuscleCreditRole.DIRECT ->
                        t.wd=t.wd.add(credit.fractionalSet)
                    ExposureSetKind.WORKING to MuscleCreditRole.INDIRECT ->
                        t.wi=t.wi.add(credit.fractionalSet)
                    ExposureSetKind.EXTENSION to MuscleCreditRole.DIRECT ->
                        t.ed=t.ed.add(credit.fractionalSet)
                    ExposureSetKind.EXTENSION to MuscleCreditRole.INDIRECT ->
                        t.ei=t.ei.add(credit.fractionalSet)
                    else -> error("Warmups cannot earn working-set credit")
                }
            }
        }
        val totals=TrainingMuscle.entries.associateWith {muscle->
            val row=checkNotNull(mutable[muscle])
            MuscleVolumeTotals(normalized(row.wd),normalized(row.wi),
                normalized(row.ed),normalized(row.ei))
        }
        return ObservedMuscleVolume(
            totals=Collections.unmodifiableMap(totals),
            completedWorking=observations.count {it.kind==ExposureSetKind.WORKING &&
                it.outcome==SetOutcome.COMPLETED},
            skippedWorking=observations.count {it.kind==ExposureSetKind.WORKING &&
                it.outcome==SetOutcome.SKIPPED},
            failedWorking=observations.count {it.kind==ExposureSetKind.WORKING &&
                it.outcome==SetOutcome.FAILED_ATTEMPT},
            completedExtensions=observations.count {it.kind==ExposureSetKind.EXTENSION &&
                it.outcome==SetOutcome.COMPLETED},
            completedWarmups=observations.count {it.kind==ExposureSetKind.WARMUP &&
                it.outcome==SetOutcome.COMPLETED}
        )
    }
}
