package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinLiveAdviceDraftPolicyTest {
    private val issued=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.SMITH_TOTAL,BigDecimal("215"))
    private val reps=RepBand(5,8)
    private fun changed(draft:CurrentWorkoutEditorValue?,machine:String?=null)=
        NativeKotlinLiveAdviceDraftPolicy.hasUnsubmittedChange(
            issued,reps,machine,draft)

    @Test fun untouchedIssuedInputsStillAllowReadOnlyLiveReview() {
        assertFalse(changed(null))
        assertFalse(changed(CurrentWorkoutEditorValue("215","5")))
        assertEquals(BigDecimal("215"),issued.value)
    }

    @Test fun athleteLoadOrRepEditPausesAdviceWithoutChangingAnyValue() {
        val lighter=CurrentWorkoutEditorValue("210","5")
        val moreReps=CurrentWorkoutEditorValue("215","6")
        assertTrue(changed(lighter))
        assertTrue(changed(moreReps))
        assertTrue(changed(CurrentWorkoutEditorValue("215.0","5")))
        assertEquals("210",lighter.weight)
        assertEquals("6",moreReps.reps)
        assertEquals(BigDecimal("215"),issued.value)
    }

    @Test fun emptyPhysicalStationSeedIsNotTheLoadFromAnotherMachine() {
        val a=CurrentWorkoutEditorValue("","5")
        val entered=CurrentWorkoutEditorValue("215","5")
        assertFalse(changed(a,"machine-A"))
        assertTrue(changed(entered,"machine-A"))
        assertFalse(changed(null,"machine-B"))
        assertNotEquals(levelEditorKey("press:s1","machine-A"),
            levelEditorKey("press:s1","machine-B"))
        val current=mapOf(
            levelEditorKey("press:s1","machine-A") to entered)
        assertTrue(changed(current[levelEditorKey("press:s1","machine-A")],
            "machine-A"))
        assertFalse(changed(current[levelEditorKey("press:s1","machine-B")],
            "machine-B"))
    }

    @Test fun manualBodyweightRepDraftIsSeparateFromAWeightSuggestion() {
        val bodyweight=DraftSetLoad.ExplicitBodyweight
        assertFalse(NativeKotlinLiveAdviceDraftPolicy.hasUnsubmittedChange(
            bodyweight,RepBand(8,12),null,
            CurrentWorkoutEditorValue("","8")))
        assertTrue(NativeKotlinLiveAdviceDraftPolicy.hasUnsubmittedChange(
            bodyweight,RepBand(8,12),null,
            CurrentWorkoutEditorValue("","10")))
    }
}
