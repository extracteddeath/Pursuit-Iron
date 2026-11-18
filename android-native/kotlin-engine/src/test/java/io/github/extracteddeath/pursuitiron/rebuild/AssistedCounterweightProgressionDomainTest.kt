package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AssistedCounterweightProgressionDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private val stock=VerifiedLoadStock.Stack(LoadUnit.LB,
        LoadBasis.ASSISTANCE_COUNTERWEIGHT,n("10"),n("10"),n("80"))
    private fun session(id:String="w1",reps:Int=12,rir:Int?=3,
        assistance:String="40",day:String="upper-1"):ProgressionWorkoutEvidence =
        ProgressionWorkoutEvidence(id,"assisted-pullup",LoadUnit.LB,RepBand(8,12),
            2,(0..1).map{ord->
                WorkingSetFact(ord,SetOutcome.COMPLETED,n(assistance),reps,rir,
                    n(assistance),2)
            },day)
    private fun request(previous:ProgressionWorkoutEvidence=session("w1"),
        latest:ProgressionWorkoutEvidence=session("w2"),
        previousMachine:String?="machine-a",latestMachine:String?="machine-a",
        equipment:VerifiedLoadStock?=stock,max:String="10")=
        AssistedCounterweightReviewRequest(previous,latest,previousMachine,
            latestMachine,equipment,n(max))

    @Test fun reviewedPhysicalAssistantMovesCounterweightDownNotUp() {
        val chosen=AssistedCounterweightProgressionDomain.review(request())
        assertEquals(AssistedCounterweightReviewStatus.LESS_ASSISTANCE_FOR_REVIEW,
            chosen.status)
        assertEquals(n("40"),chosen.currentAssistance)
        assertEquals(n("30"),chosen.suggestedAssistance)
        assertTrue(chosen.canOfferForReview)
        assertTrue(chosen.explanation.contains("LESS counterweight"))
        assertFalse(chosen.mayAutoApply)
        assertFalse(chosen.maySaveProgram)
        assertFalse(chosen.mayIssueWorkout)
        assertFalse(chosen.mayChangeHistory)
        val normal=ConservativeProgressionReview.assess(
            session("w1"),session("w2"),stock,n("10"))
        assertEquals(IncreaseReviewReason.ASSISTANCE_NEEDS_DECREASE_REVIEW,normal.reason)
        assertNull(normal.reviewCandidate)
    }

    @Test fun differentMachinesDaysOrActualWeightNeverEarnAssistanceChange() {
        for(v in listOf(
            request(latestMachine="machine-b"),
            request(previousMachine=null),
            request(latest=session("w2",day="push-3")),
            request(latest=session("w2",assistance="50")),
            request(latest=session("w2",reps=10)),
            request(latest=session("w2",rir=0)),
            request(equipment=VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.MACHINE_STACK,listOf(n("40")))),
            request(max="5")
        )) {
            val result=AssistedCounterweightProgressionDomain.review(v)
            assertFalse("Unsafe assistance review: ${result.status}",result.canOfferForReview)
            assertNull(result.suggestedAssistance)
        }
    }

    @Test fun goalRirAssumptionStaysExplicitAndInventoryMustBeReal() {
        val result=AssistedCounterweightProgressionDomain.review(
            request(previous=session("w1",rir=null),latest=session("w2",rir=null)))
        assertEquals(AssistedCounterweightReviewStatus.LESS_ASSISTANCE_FOR_REVIEW,
            result.status)
        assertEquals(4,result.assumedGoalRirSetCount)
        assertTrue(result.explanation.contains("RIR was not entered for 4 sets"))
        val noLower=AssistedCounterweightProgressionDomain.review(
            request(previous=session("w1",assistance="10"),
                latest=session("w2",assistance="10")))
        assertEquals(AssistedCounterweightReviewStatus.NO_LOWER_CONFIRMED_ASSISTANCE,
            noLower.status)
        val unlisted=AssistedCounterweightProgressionDomain.review(
            request(previous=session("w1",assistance="35"),
                latest=session("w2",assistance="35")))
        assertEquals(AssistedCounterweightReviewStatus.COUNTERWEIGHT_NOT_IN_GYM,
            unlisted.status)
    }

    @Test fun catalogPreservesThreeDistinctAssistedMovementsAndExactMachineStock() {
        val byId=StarterExerciseTranche.entries().associateBy{it.legacyCuratedId}
        val ids=listOf("assisted-dip","assisted-chinup","assisted-pullup")
        assertEquals(365,byId.size)
        assertEquals(ids,StarterExerciseTranche.entries().takeLast(3)
            .map{it.legacyCuratedId})
        for(id in ids) {
            val movement=byId.getValue(id).movement
            assertEquals(LoadBasis.ASSISTANCE_COUNTERWEIGHT,
                movement.expectedLoadBasis)
            assertEquals("assisted-pullup-dip-counterweight",movement.equipmentId)
            assertEquals(setOf("assisted-pullup-dip"),movement.requiredFacilityIds)
            assertEquals(RepBand(8,12),movement.sourcePracticalReps)
            assertTrue(CatalogPhysicalMachineEligibility.eligible(movement))
            assertFalse(byId.getValue(id).mayTransferPreviousLoad)
            assertFalse(CatalogPhysicalMachineEligibility.eligible(
                movement.copy(variantId="unverified-machine")))
        }
        assertEquals(3,ids.map{byId.getValue(it).movement.variantId}.distinct().size)
        assertTrue(stock is VerifiedLoadStock.Stack)
        assertEquals(LoadBasis.ASSISTANCE_COUNTERWEIGHT,stock.basis)
        val inspected=EquipmentLoadDomain.inspect(stock,n("40"),LoadUnit.LB)
            as LoadInspection.Confirmed
        assertTrue(inspected.exact)
        assertEquals(n("50"),inspected.nextAbove)
        assertEquals(n("40"),inspected.atOrBelow)
        assertFalse((EquipmentLoadDomain.inspect(stock,n("45"),LoadUnit.LB)
            as LoadInspection.Confirmed).exact)
    }

    private fun composer(assistedStock:VerifiedLoadStock=stock):ComposerRequest {
        val muscles=TrainingMuscle.entries.map {
            DeclaredMuscleDoseBounds(it,n("0"),n("30"),n("30"),0)
        }
        val exercise=ComposerExerciseOption("assisted-pullup",
            ComposerPattern.VERTICAL_PULL,setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("40"),assistedStock),
            listOf(DeclaredMuscleCredit(TrainingMuscle.LATS,
                MuscleCreditRole.DIRECT,n("1"))),
            60,60,2,2,1,1,1,5,
            role=ComposerRole.SECONDARY,sourcePracticalReps=RepBand(8,12))
        return ComposerRequest(ComposerSplit.FULL_BODY,
            TrainingPhase.HYPERTROPHY,
            listOf(ComposerDay(0,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,90,null,60))),
            muscles,listOf(exercise),2)
    }
    private fun prescription(warmup:String,scheme:SetLoadScheme=SetLoadScheme.STRAIGHT)=
        ExercisePrescriptionPolicy("assisted-pullup",
            ComposerSessionPurpose.UNSPECIFIED,RepBand(8,12),2,60,60,
            scheme,maximumAuthorizedBackoff=if(scheme==SetLoadScheme.LAST_SET_BACKOFF)
                n("10") else null,
            warmups=listOf(DeclaredWarmupStep(n(warmup),5,30,60)),
            backoffReps=if(scheme==SetLoadScheme.LAST_SET_BACKOFF)RepBand(8,12)else null,
            backoffRir=if(scheme==SetLoadScheme.LAST_SET_BACKOFF)2 else null)

    @Test fun assistedWarmupMustBeEasierAndLastSetBackoffMustNotInvert() {
        val good=CoachSessionPrescriptionDomain.draft(composer(),
            listOf(prescription("50")))
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,good.status)
        val sets=good.sessions.single().exercises.single().sets
        assertEquals(DraftSetType.WARMUP,sets.first().type)
        assertEquals(n("50"),(sets.first().load as DraftSetLoad.Verified).value)
        assertTrue(sets.drop(1).all {
            (it.load as DraftSetLoad.Verified).value.compareTo(n("40"))==0
        })
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,
            CoachSessionPrescriptionDomain.draft(composer(),
                listOf(prescription("30"))).status)
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,
            CoachSessionPrescriptionDomain.draft(composer(),
                listOf(prescription("50",SetLoadScheme.LAST_SET_BACKOFF))).status)
    }
}
