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
        assertTrue(note!!.contains("Planned last-set back-off"))
        assertTrue(note.contains("set 4 of 4"))
        assertTrue(note.contains("215 → 195 lb"))
        assertTrue(note.contains("not an automatic weight drop"))
    }
    @Test fun straightAndMixedOrUnverifiedLoadsCannotBeLabeledAsBackoff() {
        val straight=(0..3).map{target(it)}
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            straight.map(::p),straight.last()))
        val mixed=listOf(target(0),target(1,"210"),target(2,"195"))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            mixed.map(::p),mixed.last()))
        val otherBasis=listOf(target(0),target(1,"195",basis=LoadBasis.EXERCISE_OVERRIDE))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            otherBasis.map(::p),otherBasis.last()))
        val differentExercise=listOf(target(0),target(1,"195",exercise="row"))
        assertNull(NativeKotlinPlannedBackoffNote.fromIssuedTargets(
            differentExercise.map(::p),differentExercise.last()))
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
    }
}
