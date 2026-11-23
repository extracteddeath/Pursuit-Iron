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

    private fun candidate(): BasicProgressionResult.Candidate {
        val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,
            listOf("15","20","25","30").map(::BigDecimal))
        val verified=KotlinFutureLoadNumberAuthorityDomain.review(
            BigDecimal("20"),BigDecimal("25"),LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,stock,BigDecimal("5"),
            KotlinFutureLoadSource.EARNED_NEXT_INCREMENT)
            as KotlinFutureLoadReview.Accepted
        return BasicProgressionResult.Candidate(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            listOf(BigDecimal("20"),BigDecimal("25")),
            setOf(1),BasicProgressionDecision.EARNED_INCREMENT,"prior",
            dynamicSetDecisions=listOf(
                BasicProgressionDecision.BUILD_REPS,
                BasicProgressionDecision.EARNED_INCREMENT),
            sourceScope=BasicProgressionScope("athlete","program","gym",
                "block","block:d1","exercise",null,LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND),
            approvedFutureLoadFacts=mapOf(1 to verified.fact))
    }

    private fun reviewed(
        candidate: BasicProgressionResult.Candidate = candidate()
    ): CustomBasicHistoryResult.Reviewed =
        CustomBasicHistoryResult.Reviewed(
            "prior",1L,candidate,"block",1,"slot",null)

    @Test fun signedWorkingRowsSupportIndependentDynamicSets() {
        assertTrue(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot()),reviewed()))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot()),reviewed(candidate().copy(
                proposedRepTargets=listOf(9,10),changedRepOrdinals=setOf(0)))))
    }

    @Test fun staleWeightCannotBeShownAsAnUnchangedWorkingSet() {
        val stale=candidate().copy(proposedWorkingLoads=listOf(BigDecimal("30"),BigDecimal("25")))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(listOf(slot()),reviewed(stale)))
        val source=slot()
        val wrongOwner=source.copy(sets=source.sets.map { it.copy(slotId="other") })
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(listOf(wrongOwner),reviewed()))
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

    @Test fun zeroCandidateRequiresAnAddedPlateConvention() {
        val proposal=reviewed(candidate().copy(proposedWorkingLoads=listOf(
            BigDecimal.ZERO,BigDecimal("25"))))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(listOf(slot()),proposal))
        val landmine=slot(load=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.LANDMINE_ADDED_PLATES,BigDecimal.ZERO))
        val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.LANDMINE_ADDED_PLATES,
            listOf(BigDecimal.ZERO,BigDecimal("25")))
        val approved=(KotlinFutureLoadNumberAuthorityDomain.review(
            BigDecimal.ZERO,BigDecimal("25"),LoadUnit.LB,
            LoadBasis.LANDMINE_ADDED_PLATES,stock,BigDecimal("25"),
            KotlinFutureLoadSource.EARNED_NEXT_INCREMENT)
            as KotlinFutureLoadReview.Accepted).fact
        val landmineClaim=candidate().copy(
            proposedWorkingLoads=listOf(BigDecimal.ZERO,BigDecimal("25")),
            sourceScope=candidate().sourceScope!!.copy(
                basis=LoadBasis.LANDMINE_ADDED_PLATES),
            approvedFutureLoadFacts=mapOf(1 to approved))
        assertTrue(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(landmine),reviewed(landmineClaim)))
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


    @Test fun whyThisWeightRendersOnlyAcceptedPhysicalFacts() {
        val proposed=candidate()
        val accepted=checkNotNull(proposed.approvedFutureLoadFacts[1])
        assertEquals(
            "+5 lb · verified next equipment setting 25 lb",
            NativeKotlinBasicLoadProofText.describe(accepted))
        assertTrue(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot()),reviewed(proposed)))

        val invalid=proposed.copy(approvedFutureLoadFacts=
            proposed.approvedFutureLoadFacts + (1 to
                accepted.copy(physicalProof=accepted.physicalProof.copy(
                    nextHigher=BigDecimal("30")))))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot()),reviewed(invalid)))
        assertFalse(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(slot()),reviewed(proposed.copy(
                approvedFutureLoadFacts=emptyMap()))))
    }

    @Test fun counterweightReasonCommunicatesLessAssistanceNotLoadDecrease() {
        val stock=VerifiedLoadStock.Stack(LoadUnit.LB,
            LoadBasis.ASSISTANCE_COUNTERWEIGHT,
            BigDecimal("5"),BigDecimal("5"),BigDecimal("200"))
        val accepted=(KotlinFutureLoadNumberAuthorityDomain.review(
            BigDecimal("70"),BigDecimal("65"),LoadUnit.LB,
            LoadBasis.ASSISTANCE_COUNTERWEIGHT,stock,BigDecimal("5"),
            KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT)
            as KotlinFutureLoadReview.Accepted).fact
        assertEquals(
            "5 lb less assistance · verified setting 65 lb",
            NativeKotlinBasicLoadProofText.describe(accepted))
        val weighted=slot(load=DraftSetLoad.Verified(
            LoadUnit.LB,LoadBasis.ASSISTANCE_COUNTERWEIGHT,
            BigDecimal("70")))
        val proposal=candidate().copy(
            strategy=BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION,
            proposedWorkingLoads=listOf(BigDecimal("65"),BigDecimal("65")),
            increasedOrdinals=setOf(0,1),
            dynamicSetDecisions=emptyList(),
            sourceScope=candidate().sourceScope!!.copy(
                basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT),
            approvedFutureLoadFacts=mapOf(0 to accepted,1 to accepted))
        assertTrue(NativeKotlinBasicProgressionPreviewShape.canRender(
            listOf(weighted),reviewed(proposal)))
    }
}
