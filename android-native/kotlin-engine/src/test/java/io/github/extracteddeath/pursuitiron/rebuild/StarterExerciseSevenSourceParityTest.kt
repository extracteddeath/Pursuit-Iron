package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

/**
 * Source IDs from the active web catalog, without load/history equivalence.
 * The catalog is provisional; successful source matching is NOT evidence
 * of 100% functional parity, muscle-dose science, or an athlete baseline.
 */
class StarterExerciseSevenSourceParityTest {
    private val additions=setOf(
        "db-bench","db-fly","db-shoulder","inc-curl",
        "hammer","face-pull","pec-deck"
    )
    private val entries=StarterExerciseTranche.entries()
    private val byId=entries.associateBy{it.legacyCuratedId}

    @Test fun allSevenSourceIdsStayDistinctAndProvisional() {
        assertEquals(49,entries.size)
        assertEquals(49,byId.size)
        assertTrue(byId.keys.containsAll(additions))
        for(id in additions) {
            val entry=checkNotNull(byId[id])
            assertEquals(id,entry.movement.exerciseId)
            assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
                entry.movement.creditStatus)
            assertFalse(entry.mayTransferPreviousLoad)
            assertTrue(entry.mappingNotes.isNotBlank())
            assertTrue(entry.movement.credits.any{
                it.role==MuscleCreditRole.DIRECT
            })
        }
    }

    @Test fun physicalGymAndUnitConventionsAreNotConflated() {
        fun check(id:String,equipment:String,basis:LoadBasis,
            facility:Set<String> =emptySet()) {
            val movement=checkNotNull(byId[id]).movement
            assertEquals(id,movement.exerciseId)
            assertEquals(equipment,movement.equipmentId)
            assertEquals(basis,movement.expectedLoadBasis)
            assertEquals(facility,movement.requiredFacilityIds)
        }
        check("db-bench","dumbbell-rack",LoadBasis.DUMBBELL_PER_HAND,
            setOf("flat-bench"))
        check("db-fly","dumbbell-rack",LoadBasis.DUMBBELL_PER_HAND,
            setOf("flat-bench"))
        check("db-shoulder","dumbbell-rack",LoadBasis.DUMBBELL_PER_HAND)
        check("inc-curl","dumbbell-rack",LoadBasis.DUMBBELL_PER_HAND,
            setOf("incline-bench"))
        check("hammer","dumbbell-rack",LoadBasis.DUMBBELL_PER_HAND)
        check("face-pull","cable-station",LoadBasis.CABLE_STACK)
        assertEquals("rope",byId.getValue("face-pull").movement.attachmentId)
        check("pec-deck","pec-deck-machine",LoadBasis.EXERCISE_OVERRIDE)
        assertNotEquals(byId.getValue("pec-deck").movement.equipmentId,
            byId.getValue("machine_press").movement.equipmentId)
        assertNotEquals(byId.getValue("pec-deck").movement.equipmentId,
            byId.getValue("cable_fly").movement.equipmentId)
    }

    @Test fun exerciseRepRangesMatchActualEffectiveWebSource() {
        val declared=mapOf("db-bench" to RepBand(8,12),
            "db-fly" to RepBand(12,18),
            "db-shoulder" to RepBand(8,12),
            "inc-curl" to RepBand(8,12),
            "hammer" to RepBand(10,15),
            "face-pull" to RepBand(12,20),
            "pec-deck" to RepBand(12,20))
        for((id,repBand) in declared) {
            assertEquals(id,repBand,byId.getValue(id).practicalRepBand)
            assertEquals(id,repBand,byId.getValue(id).movement.sourcePracticalReps)
        }
    }

    @Test fun brachialisAndBicepsAreNotAccidentallyMerged() {
        val neutral=byId.getValue("hammer").movement
        val incline=byId.getValue("inc-curl").movement
        val directNeutral=neutral.credits.filter{
            it.role==MuscleCreditRole.DIRECT
        }.map{it.muscle}.toSet()
        val directIncline=incline.credits.filter{
            it.role==MuscleCreditRole.DIRECT
        }.map{it.muscle}.toSet()
        assertEquals(setOf(TrainingMuscle.BRACHIALIS),directNeutral)
        assertEquals(setOf(TrainingMuscle.BICEPS),directIncline)
        assertTrue(neutral.credits.none{
            it.muscle==TrainingMuscle.WRIST_FLEXORS ||
                it.muscle==TrainingMuscle.WRIST_EXTENSORS
        })
        assertNotEquals(neutral.variantId,incline.variantId)
    }
}
