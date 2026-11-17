package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Exact active-web identities, not a general stock-to-strength equivalence. */
class ReviewedMachineAndEzCatalogTest {
    private fun n(value:String)=BigDecimal(value)
    private val ids=listOf("chest-row","preacher","leg-press")
    private val entries=StarterExerciseTranche.entries().takeLast(3)
    private val template=ComposerRequest(ComposerSplit.FULL_BODY,
        TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map {
            ComposerDay(it,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,120,null,60))
        },
        TrainingMuscleCoverage.current.map {
            DeclaredMuscleDoseBounds(it,BigDecimal.ZERO,n("15"),n("30"),0)
        },emptyList(),4,1)
    private val machine=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
        listOf(n("45"),n("65"),n("85")))
    private val ez=VerifiedLoadStock.Barbell(LoadUnit.LB,n("20"),
        mapOf(n("10") to 2,n("5") to 2))
    private fun review(id:String,stock:Map<String,VerifiedLoadStock>,
        facilities:Set<String> = emptySet(),load:BigDecimal=n("65"))=
        CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
            template,
            listOf(CatalogExerciseChoice(id,load,90,120,2,3,2,1,1,5)),
            CatalogGym(stock,emptySet(),facilities)))

    @Test fun newIdsAppendAfterAllExistingDefinitionsWithoutLoadHistoryAliasing() {
        assertEquals(ids,entries.map{it.legacyCuratedId})
        assertEquals(333,StarterExerciseTranche.entries().size)
        assertEquals(ids.size,entries.map {
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        assertTrue(entries.none{it.mayTransferPreviousLoad})
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,
            entries[0].movement.expectedLoadBasis)
        assertEquals(LoadBasis.BARBELL_TOTAL,
            entries[1].movement.expectedLoadBasis)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,
            entries[2].movement.expectedLoadBasis)
        assertEquals(setOf("preacher-bench"),entries[1].movement.requiredFacilityIds)
        assertFalse(entries[0].movement.credits.any {
            it.muscle==TrainingMuscle.FRONT_DELTS
        })
        assertFalse(entries[2].movement.credits.any {
            it.muscle==TrainingMuscle.HAMSTRINGS
        })
    }

    @Test fun eachPhysicalMachineNeedsItsExactStockAndActualIncrement() {
        for(id in listOf("chest-row","leg-press")) {
            val equipment=checkNotNull(entries.single{
                it.legacyCuratedId==id}.movement.equipmentId)
            assertTrue(review(id,mapOf(equipment to machine)).issues.toString(),
                review(id,mapOf(equipment to machine)).issues.isEmpty())
            assertTrue(review(id,emptyMap()).issues.any {
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
            })
            assertTrue(review(id,mapOf(equipment to
                VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                    listOf(n("65"))))).issues.isNotEmpty())
            assertTrue(review(id,mapOf(equipment to machine),
                load=n("66")).issues.any {
                it.kind==CatalogIssueType.LOAD_UNVERIFIED
            })
        }
        assertNotEquals(entries[0].movement.equipmentId,
            entries[2].movement.equipmentId)
    }

    @Test fun preacherUsesEzBarAndRealBenchNeverMachineStack() {
        val stock=mapOf("ez-curl-bar" to ez)
        assertTrue(review("preacher",stock,setOf("preacher-bench"),
            n("40")).issues.toString(),
            review("preacher",stock,setOf("preacher-bench"),
                n("40")).issues.isEmpty())
        assertTrue(review("preacher",stock,
            load=n("40")).issues.any {
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
            })
        assertTrue(review("preacher",mapOf(
            "preacher-curl-machine" to machine),
            setOf("preacher-bench"),n("65")).issues.any {
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
            })
        assertTrue(review("preacher",stock,
            setOf("preacher-bench"),n("43")).issues.any {
                it.kind==CatalogIssueType.LOAD_UNVERIFIED
            })
    }
}
