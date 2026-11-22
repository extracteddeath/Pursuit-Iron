package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinPlannedBackoffNoteTest {
    private fun target(
        index:Int,
        load:String="215",
        exercise:String="press",
        basis:LoadBasis=LoadBasis.SMITH_TOTAL,
        type:DraftSetType=DraftSetType.WORKING
    )=KotlinWorkoutTarget(
        exercise,ComposerRole.PRIMARY,
        ComposerSessionPurpose.STRENGTH_SKILL,
        "$exercise:${type.name}:$index",type,index,
        DraftSetLoad.Verified(LoadUnit.LB,basis,BigDecimal(load)),
        RepBand(5,8),2,60,120
    )
    private fun p(t:KotlinWorkoutTarget)=KotlinWorkoutSetPresentation(
        t,null,null,null,null,true
    )
    @Test fun labelsOnlyTheAuthoredLowerFinalWorkingSet() {
        val targets=(0..2).map{target(it)}+target(3,"195")
        val rows=targets.map(::p)
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(rows,targets[0]))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(rows,targets[2]))
        val note=NativeKotlinPlannedBackoffNote.fromIssuedTargets(rows,targets[3])
        assertNotNull(note)
        assertTrue(note!!.contains("Issued lighter working set"))
        assertTrue(note.contains("set 4 of 4"))
        assertTrue(note.contains("215 → 195 lb"))
        assertTrue(note.contains("not lowered by fatigue coaching"))
    }
    @Test fun straightAndUnverifiedLoadsAreNotMisrepresentedAsReductions() {
        val straight=(0..3).map{target(it)}
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            straight.map(::p),straight.last()))
        val mixed=listOf(target(0),target(1,"210"),target(2,"195"))
        val firstReduction=NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            mixed.map(::p),mixed[1])
        assertNotNull(firstReduction)
        assertTrue(firstReduction!!.contains("set 2 of 3"))
        assertTrue(firstReduction.contains("215 → 210 lb"))
        val secondReduction=NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            mixed.map(::p),mixed.last())
        assertNotNull(secondReduction)
        assertTrue(secondReduction!!.contains("210 → 195 lb"))
        assertFalse(secondReduction.contains("back-off"))
        val otherBasis=listOf(target(0),target(1,"195",basis=LoadBasis.EXERCISE_OVERRIDE))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            otherBasis.map(::p),otherBasis.last()))
        val differentExercise=listOf(target(0),target(1,"195",exercise="row"))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            differentExercise.map(::p),differentExercise.last()))
    }
    @Test fun actualsAndStaleTargetCopiesCannotInventPrescribedLoadDifferences() {
        val targets=(0..2).map{target(it)}+target(3,"195")
        val original=targets.map(::p)
        val reference=NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            original,targets.last())
        val changedActual=original.mapIndexed{index,row ->
            if(index==2)row.copy(actual=KotlinPerformedSet(
                BigDecimal("170"),LoadUnit.LB,4,null)) else row
        }
        assertEquals(reference,NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            changedActual,targets.last()))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            original,targets.last().copy(
                load=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
                    BigDecimal("190")))))
    }

    @Test fun warmupsAndShortOrAmbiguousSequencesDoNotInheritReductionLabels() {
        val warmup=target(0,"95",type=DraftSetType.WARMUP)
        val final=target(0,"195")
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            listOf(p(warmup),p(final)),final))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            listOf(p(warmup),p(final)),warmup))
        val duplicate=listOf(target(0),target(0,"195"))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            duplicate.map(::p),duplicate.last()))
        // A shorter issued workout can retain source set ordinals with gaps.
        // Do not misstate the source fourth set as a new second set.
        val omittedMiddle=listOf(target(0),target(3,"195"))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            omittedMiddle.map(::p),omittedMiddle.last()))
    }

    private fun custom(
        index:Int,weight:String="215",type:DraftSetType=DraftSetType.WORKING,
        slot:String="press-slot",basis:LoadBasis=LoadBasis.SMITH_TOTAL
    )=CustomWorkoutSetSnapshot(
        "$slot:set$index",slot,index+1,
        CustomAuthoredSet(type,
            DraftSetLoad.Verified(LoadUnit.LB,basis,BigDecimal(weight)),
            RepBand(5,8),2,60,120)
    )
    @Test fun customLastSetReductionIsExplicitWithoutChangingTargets() {
        val before=(0..2).map{custom(it)}
        val last=custom(3,"195")
        val slot=before+last
        assertNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(slot,before[0]))
        val note=NativeKotlinPlannedBackoffNote.fromCustomSlot(slot,last)
        assertNotNull(note)
        assertTrue(note!!.contains("Authored lighter working set"))
        assertTrue(note.contains("set 4 of 4"))
        assertTrue(note.contains("215 → 195 lb"))
        assertEquals(BigDecimal("195"),
            (last.prescription.load as DraftSetLoad.Verified).value)
    }
    @Test fun customLabelRejectsStraightAndMismatchedPhysicalLoads() {
        val straight=(0..3).map{custom(it)}
        assertNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(
            straight,straight.last()))
        val mixed=listOf(custom(0),custom(1,"210"),custom(2,"195"))
        val earlier=NativeKotlinPlannedBackoffNote.fromCustomSlot(
            mixed,mixed[1])
        assertNotNull(earlier)
        assertTrue(earlier!!.contains("set 2 of 3"))
        assertTrue(earlier.contains("215 → 210 lb"))
        val later=NativeKotlinPlannedBackoffNote.fromCustomSlot(
            mixed,mixed.last())
        assertTrue(later!!.contains("210 → 195 lb"))
        val unlike=listOf(custom(0),custom(1,"195",
            basis=LoadBasis.EXERCISE_OVERRIDE))
        assertNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(
            unlike,unlike.last()))
        val switched=listOf(custom(0),custom(1,"195",slot="different"))
        assertNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(
            switched,switched.last()))
    }
    @Test fun customLabelRejectsWarmupsOmittedOrdinalsAndDuplicateReceipts() {
        val warmup=custom(0,"95",DraftSetType.WARMUP)
        val main=custom(1)
        val final=custom(2,"195")
        assertNotNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(
            listOf(warmup,main,final),final))
        assertNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(
            listOf(warmup,main,final),warmup))
        val gap=listOf(custom(0),custom(3,"195"))
        assertNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(gap,gap.last()))
        val duplicate=listOf(custom(0),custom(0,"195"))
        assertNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(
            duplicate,duplicate.last()))
        assertNull(NativeKotlinPlannedBackoffNote.fromCustomSlot(
            listOf(main,final,warmup),final))
    }
}
