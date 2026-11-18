package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Material 3 Plan review for source-audited vanished Strength exercises.
 * A preview is not an approval: the host rederives all of it inside an
 * owner-scoped transaction and saves ONLY after explicit athlete consent.
 */
internal data class NativeStrengthMenuRecoveryRequest(
    val cycleId:String,
    val blockId:String,
    val expectedCycleRevision:Long,
    val expectedSourceRevision:Long,
    val coaching:CustomCoachAuditInputs,
    val maximumExercisesPerDay:Int,
    val maximumRestoredPerDay:Int=8
)
internal data class NativeStrengthMenuRecoveryReady(
    val request:NativeStrengthMenuRecoveryRequest,
    val expectedPermissionRevision:Long,
    val expectedCoachSettingsRevision:Long,
    val proposedCycle:CustomTrainingCycle,
    val restored:List<StrengthMenuRestoredExercise>
) {
    val maySaveAutomatically:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayAlterHistory:Boolean get()=false
}
internal sealed interface NativeStrengthMenuRecoveryPreview {
    data class Ready(val receipt:NativeStrengthMenuRecoveryReady):
        NativeStrengthMenuRecoveryPreview
    data class Rejected(val explanation:String):NativeStrengthMenuRecoveryPreview
}
internal sealed interface NativeStrengthMenuRecoveryDecision {
    data class Saved(val newCycleRevision:Long):NativeStrengthMenuRecoveryDecision
    data class Rejected(val explanation:String):NativeStrengthMenuRecoveryDecision
}
