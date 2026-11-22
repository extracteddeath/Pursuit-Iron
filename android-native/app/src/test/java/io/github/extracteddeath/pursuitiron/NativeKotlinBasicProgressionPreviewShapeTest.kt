package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinBasicProgressionPreviewShapeTest {
    private fun slot(
        id: String = "slot",
        count: Int = 2,
        load: DraftSetLoad = DraftSetLoad.Verified(
            LoadUnit.LB, LoadBasis.DUMBBELL_PER_HAND, BigDecimal("20"))
    ): CustomWorkoutSlotSnapshot {
        val sets = (1..count).map { ordinal ->
            CustomWorkoutSetSnapshot(
                "set-" + ordinal, id, ordinal,
                CustomAuthoredSet(
                    DraftSetType.WORKING, load, RepBand(8,12), 2, 40, 90))
        }
        return CustomWorkoutSlotSnapshot(
            id, "exercise", "family", "variant",
            ComposerRole.ACCESSORY, null, null, true, "", sets)
    }

    private fun candidate(): BasicProgressionResult.Candidate =
        BasicProgressionResult.Candidate(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            listOf(BigDecimal("20"),BigDecimal("25")),
            setOf(1),BasicProgressionDecision.EARNED_INCREMENT,"prior",
            dynamicSetDecisions=listOf(
                BasicProgressionDecision.BUILD_REPS,
                BasicProgressionDecision.EARNED_INCREMENT))

    private fun reviewed(
        candidate: BasicProgressionResult.Candidate = candidate()
    ): CustomBasicHistoryResult.Reviewed =
        CustomBasicHistoryResult.Reviewed(
            "prior",1L,candidate,"block",1,"slot",null)

    @Test fun signedWorkingRowsSupportIndependentDynamicSets() {
        assertTrue(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot()),reviewed()))
        assertTrue(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot()),reviewed(candidate().copy(
                proposedRepTargets=listOf(9,10),changedRepOrdinals=setOf(0)))))
    }

    @Test fun missingOrDuplicateSlotAndChangedSourceOrdinalRefuseBeforeComposeIndexing() {
        val source=slot()
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            emptyList(),reviewed()))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(source,source),reviewed()))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(source.copy(sets=source.sets.mapIndexed { index, set ->
                if(index==1)set.copy(ordinalInSlot=4) else set
            })),reviewed()))
    }

    @Test fun staleCandidateCountsAndOutOfRangeOrdinalsRefuseWithoutPartialTargets() {
        val source=listOf(slot())
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            source,reviewed(candidate().copy(
                proposedWorkingLoads=listOf(BigDecimal("25"))))))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            source,reviewed(candidate().copy(increasedOrdinals=setOf(2)))))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            source,reviewed(candidate().copy(
                dynamicSetDecisions=listOf(BasicProgressionDecision.EARNED_INCREMENT)))))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            source,reviewed(candidate().copy(
                proposedRepTargets=listOf(9),changedRepOrdinals=setOf(0)))))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            source,reviewed(candidate().copy(
                proposedWorkingLoads=listOf(BigDecimal("-5"),BigDecimal("25"))))))
    }

    @Test fun bodyweightManualModeMustHaveItsOwnSignedBodyweightSlot() {
        val manual=CustomBasicHistoryResult.ManualBodyweight("slot")
        assertTrue(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot(load=DraftSetLoad.ExplicitBodyweight)),manual))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot()),manual))
        // A rejection contains no proposed set rows and needs no selected slot.
        assertTrue(NativeKotlinBasicProgressionPreviewShape.canRender(
            emptyList(),CustomBasicHistoryResult.Rejected(
                CustomBasicHistoryRefusal.HISTORY_SOURCE_CHANGED)))
    }
}
