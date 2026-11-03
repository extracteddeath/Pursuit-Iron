package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinWorkoutPresentationDomainTest {
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",1,"program-1",3,"gym-1",2,"catalog-1","rules-1")
    private val body=DraftSetLoad.ExplicitBodyweight
    private val load=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal("25"))
    private val snapshot=KotlinStagedWorkout(source,"grant-1","workout-1",0,0,
        ComposerSessionPurpose.HYPERTROPHY,47,
        listOf(
            KotlinWorkoutTarget("press",ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY,"press:w0",DraftSetType.WARMUP,
                0,load,RepBand(8,8),null,30,60),
            KotlinWorkoutTarget("press",ComposerRole.PRIMARY,
                ComposerSessionPurpose.HYPERTROPHY,"press:s1",DraftSetType.WORKING,
                1,load,RepBand(8,12),2,60,120),
            KotlinWorkoutTarget("chinup",ComposerRole.SECONDARY,
                ComposerSessionPurpose.HYPERTROPHY,"chin:s1",DraftSetType.WORKING,
                0,body,RepBand(6,10),3,50,90)))
    private val identity=KotlinStoredWorkoutIdentity(
        "stored-1","workout-1",source,1L)
    private fun step(
        journal:KotlinWorkoutJournalState,op:KotlinJournalOperation
    )=(KotlinWorkoutJournalDomain.reduce(journal,op) as
        KotlinJournalDecision.Proposed).state
    private fun recorded(id:String,rev:Long,key:String,
        outcome:KotlinJournalSetOutcome,performed:KotlinPerformedSet?,
        correction:String?=null)=KotlinJournalOperation.RecordSet(
        id,rev,key,outcome,performed,correction)

    @Test fun showsExactSequenceAndNoInventedActuals() {
        val journal=KotlinWorkoutJournalDomain.begin(snapshot,identity)
        val ui=KotlinWorkoutPresentationDomain.inspect(journal)
        assertEquals(listOf("press","chinup"),ui.exercises.map{it.exerciseId})
        assertEquals(listOf("press:w0","press:s1"),
            ui.exercises[0].sets.map{it.target.setKey})
        assertEquals(3,ui.remainingCount)
        assertEquals("press:w0",ui.firstUnresolvedSetKey)
        assertFalse(ui.canFinish)
        assertFalse(ui.finished)
        assertNull(ui.exercises.first().sets.first().actual)
        assertNull(ui.exercises.first().sets.first().outcome)
        assertNull(ui.exercises.first().sets.first().correctsEventId)
        assertEquals(2,ui.exercises[0].sets[1].target.targetRir)
        assertNull(ui.exercises[0].sets[1].actual)
        assertEquals(47L,ui.estimatedMinutes)
    }

    @Test fun skipFailCorrectionAndFinishFollowOnlyCommittedReceipts() {
        var j=KotlinWorkoutJournalDomain.begin(snapshot,identity)
        j=step(j,recorded("event-1",0,"press:w0",
            KotlinJournalSetOutcome.SKIPPED,null))
        j=step(j,recorded("event-2",1,"press:s1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,9,null)))
        var ui=KotlinWorkoutPresentationDomain.inspect(j)
        assertEquals(1,ui.completedCount)
        assertEquals(1,ui.skippedCount)
        assertNull(ui.exercises[0].sets[1].actual?.actualRir)
        assertEquals("event-2",ui.exercises[0].sets[1].correctsEventId)
        assertEquals(1,ui.remainingCount)
        j=step(j,recorded("event-3",2,"press:s1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("22.5"),LoadUnit.LB,8,2),
            correction="event-2"))
        ui=KotlinWorkoutPresentationDomain.inspect(j)
        assertEquals(1,ui.completedCount)
        assertEquals(1,ui.skippedCount)
        assertEquals(3L,ui.revision)
        assertEquals(BigDecimal("22.5"),ui.exercises[0].sets[1].actual?.load)
        assertEquals("event-3",ui.exercises[0].sets[1].correctsEventId)
        j=step(j,recorded("event-4",3,"chin:s1",
            KotlinJournalSetOutcome.FAILED_ATTEMPT,
            KotlinPerformedSet(null,null,0,null)))
        ui=KotlinWorkoutPresentationDomain.inspect(j)
        assertEquals(1,ui.failedCount)
        assertEquals(0,ui.remainingCount)
        assertTrue(ui.canFinish)
        j=step(j,KotlinJournalOperation.Finish("finish-1",4))
        ui=KotlinWorkoutPresentationDomain.inspect(j)
        assertFalse(ui.canFinish)
        assertTrue(ui.finished)
        assertTrue(ui.exercises.flatMap{it.sets}.none{it.canEdit})
        assertEquals(1,ui.completedCount)
        assertEquals(1,ui.skippedCount)
        assertEquals(1,ui.failedCount)
    }
}
