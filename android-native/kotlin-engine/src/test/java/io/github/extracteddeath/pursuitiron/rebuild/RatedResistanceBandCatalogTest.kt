package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Band ratings are real source identity and nominal physical device data,
 * not free-weight equivalents or manufactured set receipts. */
class RatedResistanceBandCatalogTest {
    private fun n(raw:String)=BigDecimal(raw)
    private val entries=StarterExerciseTranche.entries()
    private val byId=entries.associateBy{it.legacyCuratedId}
    private val band=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.EXERCISE_OVERRIDE,listOf(n("10"),n("20"),n("30")))
    private val exactGym=CatalogGym(mapOf(
        RatedResistanceBandDomain.EQUIPMENT_ID to band),emptySet(),
        setOf("band-anchor","band-overhead-anchor"))
    private fun template()=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,90,null,60))},
        TrainingMuscle.entries.map{
            DeclaredMuscleDoseBounds(it,n("0"),n("20"),n("30"),0)
        },emptyList(),4,1
    )
    private fun review(id:String,load:String?="20",gym:CatalogGym=exactGym)=
        CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),listOf(
                CatalogExerciseChoice(id,load?.let(::n),90,100,2,3,3,1,1,5)
            ),gym))
    private fun has(r:CatalogWeekReview,kind:CatalogIssueType,id:String)=
        r.issues.any{it.exerciseId==id && it.kind==kind}

    @Test fun allSixteenWebBandIdentitiesKeepDistinctReviewAndPracticalRepBands() {
        assertEquals(370,entries.size)
        val ids=RatedResistanceBandDomain.reviewedExerciseIds()
        assertEquals(16,ids.size)
        assertEquals("spanish-squat",entries[361].legacyCuratedId)
        assertEquals(listOf("assisted-dip","assisted-chinup","assisted-pullup"),
            entries.drop(362).take(3).map{it.legacyCuratedId})
        assertEquals(ids,entries.drop(345).take(16)
            .map{it.legacyCuratedId}.toSet())
        assertEquals(RepBand(10,40),byId.getValue("band_lateral_raise").practicalRepBand)
        assertEquals(RepBand(15,25),byId.getValue("banded-tib-raise").practicalRepBand)
        assertEquals(RepBand(12,15),byId.getValue("bands-chest-press").practicalRepBand)
        for(id in ids) {
            val item=byId.getValue(id)
            assertEquals(id,item.movement.exerciseId)
            assertEquals(RatedResistanceBandDomain.EQUIPMENT_ID,
                item.movement.equipmentId)
            assertEquals(LoadBasis.EXERCISE_OVERRIDE,
                item.movement.expectedLoadBasis)
            assertFalse(item.mayTransferPreviousLoad)
            assertFalse(CatalogPhysicalMachineEligibility.eligible(item.movement))
            assertTrue(item.mappingNotes.contains("band",ignoreCase=true))
        }
        assertEquals(TrainingMuscle.TIBIALIS_ANTERIOR,
            byId.getValue("banded-tib-raise").movement.credits.single().muscle)
        assertEquals(TrainingMuscle.HIP_ADDUCTORS,
            byId.getValue("band-adduction").movement.credits.single().muscle)
        assertEquals(TrainingMuscle.REAR_DELTS,
            byId.getValue("rear-band-pull-apart").movement.credits.single().muscle)
    }

    @Test fun bandStockMustBeManufacturerRatedDiscreteAndCannotPretendToBeMachineStack() {
        val stock=RatedResistanceBandDomain
        assertTrue(stock.confirmedDiscreteStock(stock.EQUIPMENT_ID,band))
        assertFalse(stock.confirmedDiscreteStock(stock.EQUIPMENT_ID,null))
        assertFalse(stock.confirmedDiscreteStock(stock.EQUIPMENT_ID,
            VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                n("10"),n("5"),n("30"))))
        assertFalse(stock.confirmedDiscreteStock(stock.EQUIPMENT_ID,
            VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                listOf(n("20")))))
        assertFalse(stock.confirmedDiscreteStock(stock.EQUIPMENT_ID,
            VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                listOf(n("0"),n("20")))))
        assertFalse(stock.confirmedDiscreteStock(stock.EQUIPMENT_ID,
            VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                listOf(n("20"),n("20.0")))))
        assertFalse(stock.confirmedDiscreteStock(stock.EQUIPMENT_ID,
            VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                listOf(n("2001")))))
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),
            EquipmentLoadDomain.inspect(band,n("20"),LoadUnit.KG))
        assertTrue((EquipmentLoadDomain.inspect(band,n("20"),LoadUnit.LB)
            as LoadInspection.Confirmed).exact)
        assertFalse((EquipmentLoadDomain.inspect(band,n("15"),LoadUnit.LB)
            as LoadInspection.Confirmed).exact)
    }

    @Test fun generatedWeekRejectsMissingBandGuessedRatingContinuousStockAndWrongUnit() {
        val id="band-curl"
        assertTrue(has(review(id,gym=CatalogGym(emptyMap(),emptySet())),
            CatalogIssueType.EQUIPMENT_UNAVAILABLE,id))
        assertTrue(has(review(id,load=null),CatalogIssueType.LOAD_UNVERIFIED,id))
        assertTrue(has(review(id,load="15"),CatalogIssueType.LOAD_UNVERIFIED,id))
        val stack=CatalogGym(mapOf(
            RatedResistanceBandDomain.EQUIPMENT_ID to
                VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                    n("10"),n("5"),n("30"))),emptySet())
        assertTrue(has(review(id,gym=stack),
            CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,id))
        assertTrue(has(review(id,gym=CatalogGym(mapOf(
            "dumbbell-rack" to band),emptySet())),
            CatalogIssueType.EQUIPMENT_UNAVAILABLE,id))
        assertFalse(has(review(id),CatalogIssueType.LOAD_UNVERIFIED,id))
        assertFalse(review(id).mayIssueWorkout)
        assertFalse(review(id).mayOverwriteAuthoredProgram)
    }

    @Test fun anchorIsExplicitAndNoDifferentExerciseBorrowsItsBandLoading() {
        val pulldown="band-pulldown"
        val noAnchor=CatalogGym(exactGym.confirmedStock,emptySet(),emptySet())
        assertTrue(has(review(pulldown,gym=noAnchor),
            CatalogIssueType.FACILITY_UNAVAILABLE,pulldown))
        assertFalse(has(review(pulldown),
            CatalogIssueType.FACILITY_UNAVAILABLE,pulldown))
        assertNotEquals(byId.getValue("band-pulldown").movement.familyId,
            byId.getValue("bands-chest-press").movement.familyId)
        assertNotEquals(byId.getValue("band-curl").movement.variantId,
            byId.getValue("band_lateral_raise").movement.variantId)
        assertFalse(RatedResistanceBandDomain.isRatedBandExercise("band-pushup"))
        assertFalse(RatedResistanceBandDomain.isRatedBandExercise("spanish-squat"))
        assertFalse(RatedResistanceBandDomain.validForReviewedExercise(
            "band-curl",VerifiedLoadStock.Stack(LoadUnit.LB,
                LoadBasis.EXERCISE_OVERRIDE,n("10"),n("5"),n("30"))))
    }
}
