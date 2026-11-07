package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/** An athlete's choice of the signed plan, never a claim of earned progression. */
data class NativeAuthoredLoadReturnSlot(
    val slotId:String,val exerciseId:String,val previousWorkoutId:String,
    val higherSets:List<CustomAuthoredLoadReversionSet>
)

sealed interface NativeAuthoredLoadReturnPreview {
    data class Ready(
        val ownerKey:String,val workoutId:String,
        val authored:CustomWorkoutDaySnapshot,
        val consentRevision:Long,val consentFingerprint:String,
        val historyRevision:Long,val settingsRevision:Long,
        val higherSlots:List<NativeAuthoredLoadReturnSlot>,
        val fingerprint:String,val auditedWeeks:Int
    ):NativeAuthoredLoadReturnPreview {
        val mayIssueWorkout:Boolean get()=false
        val isEarnedProgression:Boolean get()=false
    }
    data class Rejected(val reason:NativeCalibrationRefusal):NativeAuthoredLoadReturnPreview
}
