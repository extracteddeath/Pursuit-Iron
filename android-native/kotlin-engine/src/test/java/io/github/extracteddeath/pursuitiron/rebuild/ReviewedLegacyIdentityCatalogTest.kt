package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Two ACTIVE web IDs, independently reviewed without borrowing a stock load. */
class ReviewedLegacyIdentityCatalogTest {
    private fun n(value:String)=BigDecimal(value)
    private val ids=setOf("prone-rear-delt-raise","cable-serratus")
    private val entries=StarterExerciseTranche.entries().associateBy{it.legacyCuratedId}
    private val cable=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.CABLE_STACK,listOf(n("10"),n("15"),n("20")))
    private val template=ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map{ComposerDay(it,ComposerFocus.FULL_BODY,
            DeclaredSessionWindow(1,120,null,60))},
        TrainingMuscleCoverage.current.map{DeclaredMuscleDoseBounds(
            it,n("0"),n("12"),n("40"),0)},emptyList(),4,1,
        (0..2).map{ComposerSessionIntent(it,ComposerSessionPurpose.HYPERTROPHY)})
    private fun review(id:String,stock:Boolean=true,handle:Boolean=true):CatalogWeekReview {
        val entry=entries.getValue(id)
        return CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
            template,listOf(CatalogExerciseChoice(id,
                if(entry.movement.expectedLoadBasis==null)null else n("15"),
                70,90,2,3,2,1,1,5)),
            CatalogGym(if(stock)mapOf("cable-station" to cable) else emptyMap(),
                if(handle)setOf("single-handle") else emptySet(),emptySet())
        ))
    }
    @Test fun exactWebIdentitiesHaveNoLoadTransferAndNoInventedAbdominalCredit() {
        assertTrue(entries.size>=327)
        assertTrue(entries.keys.containsAll(ids))
        for(id in ids) {
            val entry=entries.getValue(id)
            assertEquals(entry.practicalRepBand,entry.movement.sourcePracticalReps)
            assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
                entry.movement.creditStatus)
            assertFalse(entry.mayTransferPreviousLoad)
        }
        val cableEntry=entries.getValue("cable-serratus")
        assertEquals(RepBand(12,20),cableEntry.practicalRepBand)
        assertEquals(TrainingMuscle.SERRATUS_ANTERIOR,
            cableEntry.movement.credits.single().muscle)
        assertTrue(cableEntry.movement.credits.none {it.muscle==TrainingMuscle.ABS})
        val floor=entries.getValue("prone-rear-delt-raise")
        assertEquals(RepBand(12,25),floor.practicalRepBand)
        assertEquals(TrainingMuscle.REAR_DELTS,
            floor.movement.credits.first().muscle)
    }
    @Test fun cableSerratusRequiresVerifiedLocalStackAndSpecificHandle() {
        val movement=entries.getValue("cable-serratus").movement
        assertEquals("cable-station",movement.equipmentId)
        assertEquals(LoadBasis.CABLE_STACK,movement.expectedLoadBasis)
        assertEquals("single-handle",movement.attachmentId)
        val good=review("cable-serratus")
        assertFalse(good.issues.toString(),good.issues.any {
            it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE ||
                it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE ||
                it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        assertTrue(review("cable-serratus",stock=false).issues.any {
            it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
        })
        assertTrue(review("cable-serratus",handle=false).issues.any {
            it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE
        })
    }
    @Test fun proneRaiseIsTrulyUnweightedAndStillRequiresAthleteBodyweightChoice() {
        val movement=entries.getValue("prone-rear-delt-raise").movement
        assertNull(movement.expectedLoadBasis)
        assertNull(movement.equipmentId)
        assertTrue(movement.requiredFacilityIds.isEmpty())
        assertFalse(review("prone-rear-delt-raise",stock=false,handle=false).issues.any {
            it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE ||
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
    }
}
