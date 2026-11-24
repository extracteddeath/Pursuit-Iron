package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

/** First connected Plan slice: append only, never shift or edit an issued week. */
internal data class NativeRecoveryRecipeRequest(
    val cycleId:String,
    val anchorBlockId:String,
    val expectedCycleRevision:Long,
    val expectedSourceRevision:Long,
    val coaching:CustomCoachAuditInputs,
    val maximumWorkingSetsPerSlot:Int,
    val minimumTargetRir:Int
)

internal data class NativeRecoveryRecipeReady(
    val request:NativeRecoveryRecipeRequest,
    val owner:NativeKotlinOwnerIdentity,
    val expectedPermissionRevision:Long,
    val expectedCoachSettingsRevision:Long,
    val proposedCycle:CustomTrainingCycle,
    val baseline:CustomCoachWeekMeasurement,
    val recovery:CustomCoachWeekMeasurement
) {
    val mayIssueWorkout:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
}

internal sealed interface NativeRecoveryRecipePreview {
    data class Ready(val receipt:NativeRecoveryRecipeReady):NativeRecoveryRecipePreview
    data class Rejected(val explanation:String):NativeRecoveryRecipePreview
}

internal sealed interface NativeRecoveryRecipeDecision {
    data class Saved(val newCycleRevision:Long):NativeRecoveryRecipeDecision
    data class Rejected(val explanation:String):NativeRecoveryRecipeDecision
}
