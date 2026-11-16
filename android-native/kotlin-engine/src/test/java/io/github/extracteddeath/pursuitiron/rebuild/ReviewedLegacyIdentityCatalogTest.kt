package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Exact active web IDs, deliberately independent weight and bodyweight bases. */
class ReviewedLegacyIdentityCatalogTest {
    private fun n(value:String)=BigDecimal(value)
    private val ids=setOf("lat-raise","db-curl","bw-pullup","prone-rear-delt-raise")
    private val entries=StarterExerciseTranche.entries().associateBy{it.legacyCuratedId}
    private val dumbbells=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("15"),n("20"),n("25")))
    private val week=ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,120,null,60))},
        TrainingMuscleCoverage.current.map{DeclaredMuscleDoseBounds(
            it,n("0"),n("12"),n("40"),0)},emptyList(),4,1,
        (0..2).map{ComposerSessionIntent(it,ComposerSessionPurpose.HYPERTROPHY)})
    private fun review(id:String,stock:Boolean=true,bar:Boolean=true):CatalogWeekReview {
        val entry=entries.getValue(id)
        return CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
            week,listOf(CatalogExerciseChoice(id,
                if(entry.movement.expectedLoadBasis==null)null else n("20"),
                70,90,2,3,2,1,1,5)),
            CatalogGym(if(stock)mapOf("dumbbell-rack" to dumbbells) else emptyMap(),
                emptySet(),if(bar)setOf("pullup-bar") else emptySet())
        ))
    }
    @Test fun fourSourceIdsHaveIndividualIdentitiesAndConservativeCredits() {
        assertEquals(329,entries.size)
        assertTrue(entries.keys.containsAll(ids))
        for(id in ids) {
            val entry=entries.getValue(id)
            assertFalse(entry.mayTransferPreviousLoad)
            assertEquals(entry.practicalRepBand,entry.movement.sourcePracticalReps)
            assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
                entry.movement.creditStatus)
        }
        assertEquals(RepBand(12,20),entries.getValue("lat-raise").practicalRepBand)
        assertEquals(RepBand(8,12),entries.getValue("db-curl").practicalRepBand)
        assertEquals(RepBand(5,12),entries.getValue("bw-pullup").practicalRepBand)
        assertEquals(RepBand(12,25),entries.getValue("prone-rear-delt-raise").practicalRepBand)
        assertNotEquals(entries.getValue("lat-raise").movement.variantId,
            entries.getValue("db_lateral_raise").movement.variantId)
        assertNotEquals(entries.getValue("db-curl").movement.variantId,
            entries.getValue("db_curl").movement.variantId)
        assertNotEquals(entries.getValue("bw-pullup").movement.variantId,
            entries.getValue("pullup").movement.variantId)
        assertTrue(entries.getValue("db-curl").movement.credits.none{
            it.muscle==TrainingMuscle.BRACHIALIS})
    }
    @Test fun loadedDumbbellsRequireRealPerHandStockAndRejectUnverifiedLoads() {
        for(id in listOf("lat-raise","db-curl")) {
            val movement=entries.getValue(id).movement
            assertEquals("dumbbell-rack",movement.equipmentId)
            assertEquals(LoadBasis.DUMBBELL_PER_HAND,movement.expectedLoadBasis)
            assertFalse(review(id).issues.any{
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE ||
                    it.kind==CatalogIssueType.EQUIPMENT_BASIS_MISMATCH ||
                    it.kind==CatalogIssueType.LOAD_UNVERIFIED})
            assertTrue(review(id,stock=false).issues.any{
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE})
        }
    }
    @Test fun unweightedPullupRequiresPhysicalBarButNeverBorrowsAWeight() {
        val movement=entries.getValue("bw-pullup").movement
        assertNull(movement.expectedLoadBasis)
        assertNull(movement.equipmentId)
        assertEquals(setOf("pullup-bar"),movement.requiredFacilityIds)
        assertFalse(review("bw-pullup").issues.any{
            it.kind==CatalogIssueType.FACILITY_UNAVAILABLE})
        assertTrue(review("bw-pullup",bar=false).issues.any{
            it.kind==CatalogIssueType.FACILITY_UNAVAILABLE})
    }
    @Test fun floorRaiseIsGenuinelyUnweightedAndDistinctFromDumbbells() {
        val movement=entries.getValue("prone-rear-delt-raise").movement
        assertNull(movement.equipmentId)
        assertNull(movement.expectedLoadBasis)
        assertTrue(movement.requiredFacilityIds.isEmpty())
        assertTrue(movement.credits.any{it.muscle==TrainingMuscle.REAR_DELTS})
        assertFalse(review("prone-rear-delt-raise",stock=false,bar=false).issues.any{
            it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE ||
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE})
    }
}
