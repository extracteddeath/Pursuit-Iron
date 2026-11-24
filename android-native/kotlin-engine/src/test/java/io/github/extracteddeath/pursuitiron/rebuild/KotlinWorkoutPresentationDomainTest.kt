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

    @Test fun repeatedExerciseOccurrencesKeepIssuedSequenceAndIndependentCounts() {
        val first=snapshot.targets[1]
        val middle=snapshot.targets[2]
        val later=first.copy(setKey="press:later",ordinal=2)
        val mixed=snapshot.copy(targets=listOf(first,middle,later))
        var journal=KotlinWorkoutJournalDomain.begin(mixed,identity)
        var projected=KotlinWorkoutPresentationDomain.inspect(journal)
        assertEquals(listOf("press","chinup","press"),
            projected.exercises.map{it.exerciseId})
        assertEquals(listOf("press:s1","chin:s1","press:later"),
            projected.exercises.flatMap{it.sets}.map{it.target.setKey})
        assertEquals(0,KotlinWorkoutPresentationDomain.occurrenceIndex(
            mixed.targets,"press:s1"))
        assertEquals(1,KotlinWorkoutPresentationDomain.occurrenceIndex(
            mixed.targets,"chin:s1"))
        assertEquals(2,KotlinWorkoutPresentationDomain.occurrenceIndex(
            mixed.targets,"press:later"))
        assertEquals(-1,KotlinWorkoutPresentationDomain.occurrenceIndex(
            mixed.targets,"missing"))
        journal=step(journal,recorded("later-done",0,"press:later",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,9,null)))
        projected=KotlinWorkoutPresentationDomain.inspect(journal)
        assertEquals(listOf(0,0,1),projected.exercises.map{it.resolvedCount})
        assertEquals(listOf("press:s1","chin:s1",null),
            projected.exercises.map{it.nextUnresolvedSetKey})
        assertEquals("press:s1",projected.firstUnresolvedSetKey)
        assertEquals(2,projected.remainingCount)
        assertFalse(projected.canFinish)
        assertEquals(mixed.targets,journal.snapshot.targets)
        assertEquals(journal,(KotlinWorkoutJournalReplayDomain.verifyStoredState(journal)
            as KotlinJournalReplayResult.Recovered).journal)
    }

    @Test fun sameLiftWithDifferentRolesOrPurposesHasSeparateOccurrences() {
        val first=snapshot.targets[1]
        val second=first.copy(setKey="press:accessory",ordinal=2,
            role=ComposerRole.ACCESSORY)
        val third=second.copy(setKey="press:strength",ordinal=3,
            purpose=ComposerSessionPurpose.STRENGTH_SKILL)
        val extra=snapshot.copy(targets=listOf(first,second,third))
        val groups=KotlinWorkoutPresentationDomain.inspect(
            KotlinWorkoutJournalDomain.begin(extra,identity)).exercises
        assertEquals(listOf(ComposerRole.PRIMARY,ComposerRole.ACCESSORY,
            ComposerRole.ACCESSORY),groups.map{it.role})
        assertEquals(listOf(ComposerSessionPurpose.HYPERTROPHY,
            ComposerSessionPurpose.HYPERTROPHY,
            ComposerSessionPurpose.STRENGTH_SKILL),groups.map{it.purpose})
        assertEquals(listOf(0,1,2),extra.targets.map{
            KotlinWorkoutPresentationDomain.occurrenceIndex(extra.targets,it.setKey)})
        assertEquals(3,groups.size)
    }

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
        val firstObservation=ui.exercises[0].sets[1]
        assertEquals("event-2",
            (firstObservation.currentReceipt?.operation as
                KotlinJournalOperation.RecordSet).eventId)
        // An ordinary first log has no correction ancestry.
        assertNull(firstObservation.correctsEventId)
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
        assertEquals("event-2",ui.exercises[0].sets[1].correctsEventId)
        assertEquals("event-3",
            (ui.exercises[0].sets[1].currentReceipt?.operation as
                KotlinJournalOperation.RecordSet).eventId)
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

    @Test fun repeatedCorrectionsShowImmediateAncestryNotTheirOwnEventId() {
        var journal=KotlinWorkoutJournalDomain.begin(snapshot,identity)
        journal=step(journal,recorded("first",0,"press:s1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,8,null)))
        var shown=KotlinWorkoutPresentationDomain.inspect(journal)
            .exercises.first().sets.last()
        assertNull(shown.correctsEventId)
        assertEquals("first",
            (shown.currentReceipt?.operation as KotlinJournalOperation.RecordSet).eventId)

        journal=step(journal,recorded("second",1,"press:s1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("22.5"),LoadUnit.LB,9,2),
            correction="first"))
        shown=KotlinWorkoutPresentationDomain.inspect(journal)
            .exercises.first().sets.last()
        assertEquals("first",shown.correctsEventId)
        assertEquals("second",
            (shown.currentReceipt?.operation as KotlinJournalOperation.RecordSet).eventId)
        assertEquals(BigDecimal("22.5"),shown.actual?.load)

        // A second edit corrects the current effective receipt only;
        // it does not relabel the first event or rewrite its historical fact.
        journal=step(journal,recorded("third",2,"press:s1",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("20"),LoadUnit.LB,10,null),
            correction="second"))
        shown=KotlinWorkoutPresentationDomain.inspect(journal)
            .exercises.first().sets.last()
        assertEquals("second",shown.correctsEventId)
        assertEquals("third",
            (shown.currentReceipt?.operation as KotlinJournalOperation.RecordSet).eventId)
        assertEquals(BigDecimal("20"),shown.actual?.load)
        assertNull(shown.actual?.actualRir)
        assertEquals(3,journal.receipts.size)
        assertEquals("first",
            (journal.receipts.first().operation as KotlinJournalOperation.RecordSet).eventId)
        assertEquals("first",
            (journal.receipts[1].operation as KotlinJournalOperation.RecordSet)
                .correctsEventId)
        assertEquals("second",
            (journal.receipts[2].operation as KotlinJournalOperation.RecordSet)
                .correctsEventId)
        assertEquals(journal,(KotlinWorkoutJournalReplayDomain.verifyStoredState(journal)
            as KotlinJournalReplayResult.Recovered).journal)
    }

    @Test fun remainingExerciseKeepsCompletedWarmupsAndOtherExercises() {
        var j=KotlinWorkoutJournalDomain.begin(snapshot,identity)
        j=step(j,recorded("done",0,"press:w0",KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,8,null)))
        val before=j
        val review=WorkoutRemainingExerciseDomain.generated(j,"press")!!
        assertEquals(listOf("press:s1"),review.entries)
        assertEquals(0,review.warmupCount)
        assertEquals(1,review.workingCount)
        assertEquals(1,review.preservedEntryCount)
        j=step(j,recorded("skip-rest",review.journalRevision,review.entries.single(),
            KotlinJournalSetOutcome.SKIPPED,null))
        assertEquals(before.effectiveSets["press:w0"],j.effectiveSets["press:w0"])
        assertFalse(j.effectiveSets.containsKey("chin:s1"))
        assertEquals(before.snapshot,j.snapshot)
        assertEquals(before.originalSnapshot,j.originalSnapshot)
        assertEquals(emptyList<String>(),WorkoutRemainingExerciseDomain.generated(j,"press")!!.entries)
        assertNull(WorkoutRemainingExerciseDomain.generated(j,"missing"))
        assertNull(WorkoutRemainingExerciseDomain.generated(j.copy(finishedByEventId="finish"),"press"))
    }

}
