package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * ONE policy for physical numbers submitted to ANY native weighted-set
 * journal. The generated and athlete-authored journal adapters must not
 * independently choose weight precision, performed rep/RIR bounds or
 * equipment identity semantics.
 *
 * The immutable issued load, actual performed load, and any future coaching
 * proposal are different facts. This domain only validates and explains a
 * factual submitted observation; it NEVER overwrites an issued target,
 * chooses a coach increase, or treats a target RIR as an actual report.
 */
enum class KotlinSetNumberRefusal {
    MISSING_ACTUAL, SKIP_CONTAINS_ACTUAL, INVALID_MACHINE_ID,
    INVALID_ACTUAL_RIR, INVALID_COMPLETED_REPS, INVALID_FAILED_ATTEMPT,
    WEIGHT_NOT_APPLICABLE_TO_BODYWEIGHT, MISSING_OR_MISMATCHED_WEIGHT,
    INVALID_WEIGHT_RANGE_OR_PRECISION
}

enum class KotlinSetLoadRelation {
    /** No receipt yet; no actual value may be inferred from a target. */
    UNRECORDED,
    /** Skipped has no performed load or reps. */
    SKIPPED,
    /** An attempted weight is not evidence of a completed work set. */
    FAILED_ATTEMPT,
    /** A completed working-set load matches its issued target numerically. */
    COMPLETED_AS_ISSUED,
    /** Athlete recorded a different load; not a new prescription or backoff. */
    COMPLETED_DIFFERENT_ACTUAL
}

data class KotlinSetNumberFact(
    val issuedLoad:DraftSetLoad,
    val outcome:KotlinJournalSetOutcome?,
    val performed:KotlinPerformedSet?,
    val relation:KotlinSetLoadRelation
) {
    /** Never a decision about effort: unreported RIR is genuinely unknown. */
    val actualRir:Int? get()=performed?.actualRir
    val mayModifyIssuedTarget:Boolean get()=false
    val mayChangeFutureProgram:Boolean get()=false
}

sealed interface KotlinSetNumberReview {
    data class Accepted(val fact:KotlinSetNumberFact):KotlinSetNumberReview
    data class Rejected(val reason:KotlinSetNumberRefusal):KotlinSetNumberReview
}

/**
 * All numeric equality is scale-insensitive BigDecimal comparison. Unit and
 * machine identity are factual and never converted or normalized here.
 * An unknown machine ID stays unknown; it must not be assumed equivalent to
 * a known Machine A/B when later evaluating progression.
 */
object KotlinSetNumberAuthorityDomain {
    private val maxLoad=BigDecimal("100000")
    private val zero=BigDecimal.ZERO

    /** ONE physical weight value envelope for reported sets and reviewed
     * future load candidates. The different journal/coach policies still
     * own their own evidence and consent; this only checks numeric format.
     * Also exposed to read-only native previews for source/candidate shape
     * screening; the UI must not duplicate this numeric envelope.
     * A submitted load of zero remains an allowed factual observation,
     * whereas a future positive increment must separately exceed zero. */
    fun validPhysicalLoadValue(value:BigDecimal):Boolean =
        value>=zero && value<=maxLoad &&
            value.stripTrailingZeros().scale()<=3

    /** One scale-independent identity rule for TWO immutable issued targets.
     * Never use physical numeric equality as machine A/B equivalence: the
     * equipment instance is a separate exact history identity key. */
    fun sameIssuedTarget(a:DraftSetLoad,b:DraftSetLoad):Boolean =
        when {
            a is DraftSetLoad.Verified && b is DraftSetLoad.Verified ->
                a.unit==b.unit && a.basis==b.basis &&
                    a.value.compareTo(b.value)==0
            else ->a==b
        }

    fun review(
        issuedLoad:DraftSetLoad,
        outcome:KotlinJournalSetOutcome?,
        actual:KotlinPerformedSet?
    ):KotlinSetNumberReview {
        fun rejected(reason:KotlinSetNumberRefusal) =
            KotlinSetNumberReview.Rejected(reason)
        if(outcome==null) {
            if(actual!=null)return rejected(KotlinSetNumberRefusal.MISSING_ACTUAL)
            return KotlinSetNumberReview.Accepted(
                KotlinSetNumberFact(issuedLoad,null,null,KotlinSetLoadRelation.UNRECORDED))
        }
        if(outcome==KotlinJournalSetOutcome.SKIPPED) {
            if(actual!=null)return rejected(KotlinSetNumberRefusal.SKIP_CONTAINS_ACTUAL)
            return KotlinSetNumberReview.Accepted(
                KotlinSetNumberFact(issuedLoad,outcome,null,KotlinSetLoadRelation.SKIPPED))
        }
        val performed=actual ?:return rejected(KotlinSetNumberRefusal.MISSING_ACTUAL)
        if(performed.machineInstanceId!=null &&
            !KotlinMachineAlternativesDomain.validInstanceId(performed.machineInstanceId))
            return rejected(KotlinSetNumberRefusal.INVALID_MACHINE_ID)
        if(performed.actualRir!=null && performed.actualRir !in 0..10)
            return rejected(KotlinSetNumberRefusal.INVALID_ACTUAL_RIR)
        if(outcome==KotlinJournalSetOutcome.COMPLETED &&
            performed.reps !in 1..100)
            return rejected(KotlinSetNumberRefusal.INVALID_COMPLETED_REPS)
        if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT &&
            (performed.reps!=0 || performed.actualRir!=null))
            return rejected(KotlinSetNumberRefusal.INVALID_FAILED_ATTEMPT)
        val sameLoad=when(issuedLoad) {
            DraftSetLoad.ExplicitBodyweight -> {
                if(performed.load!=null || performed.unit!=null)
                    return rejected(
                        KotlinSetNumberRefusal.WEIGHT_NOT_APPLICABLE_TO_BODYWEIGHT)
                true
            }
            is DraftSetLoad.Verified -> {
                val load=performed.load
                if(load==null || performed.unit!=issuedLoad.unit)
                    return rejected(KotlinSetNumberRefusal.MISSING_OR_MISMATCHED_WEIGHT)
                if(!validPhysicalLoadValue(load))
                    return rejected(
                        KotlinSetNumberRefusal.INVALID_WEIGHT_RANGE_OR_PRECISION)
                load.compareTo(issuedLoad.value)==0
            }
        }
        return KotlinSetNumberReview.Accepted(KotlinSetNumberFact(
            issuedLoad,outcome,performed,
            when(outcome) {
                KotlinJournalSetOutcome.FAILED_ATTEMPT ->
                    KotlinSetLoadRelation.FAILED_ATTEMPT
                KotlinJournalSetOutcome.COMPLETED ->
                    if(sameLoad)KotlinSetLoadRelation.COMPLETED_AS_ISSUED
                    else KotlinSetLoadRelation.COMPLETED_DIFFERENT_ACTUAL
                KotlinJournalSetOutcome.SKIPPED ->
                    error("Skip handled above")
            }
        ))
    }

    /** Same accepted/rejected policy for generated, custom and saved replay. */
    fun valid(
        issuedLoad:DraftSetLoad,
        outcome:KotlinJournalSetOutcome,
        actual:KotlinPerformedSet?
    ):Boolean=review(issuedLoad,outcome,actual) is KotlinSetNumberReview.Accepted
}
