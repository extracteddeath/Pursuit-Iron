package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Canonical translation from an issued working-set target plus an effective
 * native journal receipt into factual double-progression evidence.
 *
 * Live coaching, generated History and custom future-session review MUST NOT
 * each reinterpret completed / failed / skipped numbers. Every conversion
 * first crosses the identical KotlinSetNumberAuthorityDomain validation used
 * by both persistent journal reducers and the on-screen entry parser.
 *
 * This source bridge has no right to edit an issued workout, a historical
 * receipt, or a future program. Unknown actual RIR stays unknown.
 */
data class KotlinWorkingSetEvidence(
    val issuedLoad:DraftSetLoad,
    val relation:KotlinSetLoadRelation,
    val machineInstanceId:String?,
    val fact:WorkingSetFact
) {
    val mayChangeHistory:Boolean get()=false
    val mayChangeIssuedTargets:Boolean get()=false
    val mayChangeFutureProgram:Boolean get()=false
}

enum class KotlinWorkingSetEvidenceRefusal {
    INVALID_WORKING_ORDINAL_OR_ISSUED_RIR,
    INVALID_JOURNAL_NUMBERS
}

sealed interface KotlinWorkingSetEvidenceReview {
    data class Accepted(val evidence:KotlinWorkingSetEvidence):
        KotlinWorkingSetEvidenceReview
    data class Rejected(val reason:KotlinWorkingSetEvidenceRefusal):
        KotlinWorkingSetEvidenceReview
}

object KotlinWorkingSetEvidenceDomain {
    /**
     * Each caller provides the actual ordinal of the comparable WORKING
     * series, not a warmup/mini-set ordinal or the arbitrary receipt order.
     * Null receipt/outcome must not be converted to a phantom completed set.
     * Bodyweight may be admitted only when its issued target explicitly says
     * bodyweight; an absent loaded actual is not equivalent.
     */
    fun review(
        workingOrdinal:Int,
        issuedLoad:DraftSetLoad,
        issuedRir:Int?,
        outcome:KotlinJournalSetOutcome,
        actual:KotlinPerformedSet?
    ):KotlinWorkingSetEvidenceReview {
        if(workingOrdinal !in 0..31 ||
            !KotlinSetNumberAuthorityDomain.validRir(issuedRir))
            return KotlinWorkingSetEvidenceReview.Rejected(
                KotlinWorkingSetEvidenceRefusal.INVALID_WORKING_ORDINAL_OR_ISSUED_RIR)
        val reviewed=KotlinSetNumberAuthorityDomain.review(
            issuedLoad,outcome,actual) as? KotlinSetNumberReview.Accepted
            ?:return KotlinWorkingSetEvidenceReview.Rejected(
                KotlinWorkingSetEvidenceRefusal.INVALID_JOURNAL_NUMBERS)
        val setOutcome=when(outcome) {
            KotlinJournalSetOutcome.COMPLETED->SetOutcome.COMPLETED
            KotlinJournalSetOutcome.SKIPPED->SetOutcome.SKIPPED
            KotlinJournalSetOutcome.FAILED_ATTEMPT->SetOutcome.FAILED_ATTEMPT
        }
        val performed=reviewed.fact.performed
        return KotlinWorkingSetEvidenceReview.Accepted(
            KotlinWorkingSetEvidence(
                issuedLoad,reviewed.fact.relation,
                performed?.machineInstanceId,
                WorkingSetFact(workingOrdinal,setOutcome,
                    performed?.load,performed?.reps,performed?.actualRir,
                    (issuedLoad as? DraftSetLoad.Verified)?.value,issuedRir)))
    }
}
