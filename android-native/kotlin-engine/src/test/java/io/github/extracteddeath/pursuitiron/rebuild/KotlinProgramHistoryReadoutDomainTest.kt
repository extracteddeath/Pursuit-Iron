package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinProgramHistoryReadoutDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"cycle-1",4,"gym-1",3,"catalog-5","coach-rules-2")
    private val issued=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("25"))

    private fun target(index:Int)=KotlinWorkoutTarget(
        "bench",ComposerRole.PRIMARY,ComposerSessionPurpose.HYPERTROPHY,
        "bench:s$index",DraftSetType.WORKING,index,issued,RepBand(8,12),2,
        60,120)

    private fun started(workoutId:String,src:KotlinTrainingSourceRevision=source,
        day:Int=0):KotlinWorkoutJournalState {
        val snapshot=KotlinStagedWorkout(
            src,"grant-1",workoutId,0,day,
            ComposerSessionPurpose.HYPERTROPHY,45,
            listOf(target(0),target(1)))
        return KotlinWorkoutJournalDomain.begin(snapshot,
            KotlinStoredWorkoutIdentity(
                "stored-"+workoutId,workoutId,src,1))
    }

    private fun record(j:KotlinWorkoutJournalState,key:String,
        eventId:String,reps:Int=10,rir:Int?=null,machine:String?="press-A",
        kind:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED
    ):KotlinWorkoutJournalState {
        val actual=if(kind==KotlinJournalSetOutcome.SKIPPED)null
            else KotlinPerformedSet(n("25"),LoadUnit.LB,reps,rir,machine)
        val op=KotlinJournalOperation.RecordSet(
            eventId,j.revision,key,kind,actual)
        return (KotlinWorkoutJournalDomain.reduce(j,op)
            as KotlinJournalDecision.Proposed).state
    }

    private fun finished(workoutId:String,machine:String="press-A",
        secondMachine:String=machine):KotlinWorkoutJournalState {
        var journal=started(workoutId)
        journal=record(journal,"bench:s0","first-$workoutId",
            10,null,machine)
        journal=record(journal,"bench:s1","second-$workoutId",
            9,2,secondMachine)
        return (KotlinWorkoutJournalDomain.reduce(journal,
            KotlinJournalOperation.Finish("finish-$workoutId",
                journal.revision)) as KotlinJournalDecision.Proposed).state
    }

    private fun request(
        journals:List<KotlinWorkoutJournalState>,
        corrections:List<KotlinSavedHistoryCorrectionState> =emptyList()
    )=KotlinProgramHistoryReadoutRequest(
        "athlete-1","cycle-1",source,setOf(0,2,4),journals,corrections)

    private fun accept(input:KotlinProgramHistoryReadoutRequest)=
        (KotlinProgramHistoryReadoutDomain.review(input)
            as KotlinProgramHistoryReadoutResult.Candidate).ledger

    @Test fun replayIsDeterministicAndKeepsIndependentMachinesDaysAndUnknownRir() {
        val a=finished("workout-A","press-A")
        val b=finished("workout-B","press-B")
        val first=accept(request(listOf(b,a)))
        val second=accept(request(listOf(a,b)))
        assertEquals(first,second)
        assertEquals(64,first.exactReplayDigest.length)
        assertEquals(listOf("workout-A","workout-B"),
            first.workouts.map{it.workoutId})
        assertEquals(4,first.workingSets.size)
        // Each finished journal has TWO completed working sets.
        assertEquals(4,first.workouts.sumOf{it.completedWorkingSets})
        assertEquals(0,first.workouts.sumOf{it.skippedWorkingSets})
        assertEquals(setOf("press-A","press-B"),
            first.workingSets.map{it.physicalMachineId}.toSet())
        assertEquals(setOf(KotlinHistoryComparisonGuard.COMPARABLE_REVIEW_ONLY),
            first.workingSets.map{it.comparisonGuard}.toSet())
        assertNull(first.workingSets.single{it.workoutId=="workout-A" &&
            it.setKey=="bench:s0"}.reportedRir)
        assertEquals(2,first.workingSets.single{it.workoutId=="workout-A" &&
            it.setKey=="bench:s0"}.targetRir)
        assertTrue(first.requiresNativeStorageAttestation)
        assertFalse(first.canAuthorizeProgression)
        assertFalse(first.canChangeIssuedWorkout)
        assertFalse(first.canApproveCycle)
        assertThrows(UnsupportedOperationException::class.java) {
            (first.workingSets as MutableList<KotlinProgramHistoricalWorkingSet>)
                .clear()
        }
    }

    @Test fun differentPhysicalMachinesInsideOneWorkoutCannotProduceOneProgression() {
        val mixed=accept(request(listOf(finished("mixed","press-A","press-B"))))
        assertEquals(setOf(KotlinHistoryComparisonGuard.MIXED_PHYSICAL_MACHINES),
            mixed.workingSets.map{it.comparisonGuard}.toSet())
        var legacy=started("legacy")
        legacy=record(legacy,"bench:s0","legacy-0",10,null,null)
        legacy=record(legacy,"bench:s1","legacy-1",10,null,null)
        val done=(KotlinWorkoutJournalDomain.reduce(legacy,
            KotlinJournalOperation.Finish("legacy-finish",legacy.revision))
            as KotlinJournalDecision.Proposed).state
        val unknown=accept(request(listOf(done)))
        assertEquals(setOf(KotlinHistoryComparisonGuard.MACHINE_UNKNOWN),
            unknown.workingSets.map{it.comparisonGuard}.toSet())
    }

    @Test fun savedCorrectionReplacesOnlyEffectiveActualNotOldIssuedTarget() {
        val done=finished("corrected")
        val initial=KotlinSavedHistoryCorrectionDomain.begin(done)
        val corrected=(KotlinSavedHistoryCorrectionDomain.reduce(initial,
            KotlinSavedSetCorrection("correction-1",0,"bench:s0",
                "first-corrected",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("25"),LoadUnit.LB,8,1,"press-A"))
        ) as KotlinSavedCorrectionDecision.Proposed).state
        val first=accept(request(listOf(done)))
        val after=accept(request(listOf(done),listOf(corrected)))
        assertEquals(2,after.workingSets.size)
        assertEquals(1L,after.workouts.single().correctionRevision)
        assertNotEquals(first.exactReplayDigest,after.exactReplayDigest)
        assertEquals("correction-1",after.workingSets.first().effectiveEventId)
        assertEquals("first-corrected",after.workingSets.first().correctsEventId)
        assertEquals(8,after.workingSets.first().performedReps)
        assertEquals(1,after.workingSets.first().reportedRir)
        assertEquals(2,after.workingSets.first().targetRir)
        assertEquals(n("25"),
            (after.workingSets.first().issuedLoad as DraftSetLoad.Verified).value)
        assertEquals(n("25"),(done.snapshot.targets.first().load
            as DraftSetLoad.Verified).value)
    }

    @Test fun damagedOrCrossScopeHistoryFailsClosedWithoutFalseCompletedSets() {
        val valid=finished("original")
        val wrongSource=KotlinTrainingSourceRevision(
            "athlete-1",4,"other-cycle",4,"gym-1",3,
            "catalog-5","coach-rules-2")
        var other=started("wrong",wrongSource)
        other=record(other,"bench:s0","wrong-0")
        other=record(other,"bench:s1","wrong-1")
        other=(KotlinWorkoutJournalDomain.reduce(other,
            KotlinJournalOperation.Finish("wrong-finish",other.revision))
            as KotlinJournalDecision.Proposed).state
        assertEquals(
            KotlinProgramHistoryReadoutResult.Rejected(
                KotlinProgramHistoryReadoutRefusal.SOURCE_MISMATCH),
            KotlinProgramHistoryReadoutDomain.review(request(listOf(other))))
        assertEquals(
            KotlinProgramHistoryReadoutResult.Rejected(
                KotlinProgramHistoryReadoutRefusal.DUPLICATE_WORKOUT),
            KotlinProgramHistoryReadoutDomain.review(request(
                listOf(valid,valid))))
        assertEquals(
            KotlinProgramHistoryReadoutResult.Rejected(
                KotlinProgramHistoryReadoutRefusal.INVALID_SCOPE),
            KotlinProgramHistoryReadoutDomain.review(
                request(listOf(valid)).copy(
                    expectedSource=source.copy(programId="other-cycle"))))
        assertEquals(
            KotlinProgramHistoryReadoutResult.Rejected(
                KotlinProgramHistoryReadoutRefusal.INVALID_SCOPE),
            KotlinProgramHistoryReadoutDomain.review(
                request(listOf(valid)).copy(
                    expectedSource=source.copy(athleteId="another-athlete"))))
        assertEquals(
            KotlinProgramHistoryReadoutResult.Rejected(
                KotlinProgramHistoryReadoutRefusal.INVALID_REPLAY_OR_CORRECTION),
            KotlinProgramHistoryReadoutDomain.review(request(
                listOf(valid.copy(finishedByEventId="forged")))))
        val missed=started("unfinished")
        assertEquals(
            KotlinProgramHistoryReadoutResult.Rejected(
                KotlinProgramHistoryReadoutRefusal.INVALID_REPLAY_OR_CORRECTION),
            KotlinProgramHistoryReadoutDomain.review(request(listOf(missed))))
        assertEquals(
            KotlinProgramHistoryReadoutResult.Rejected(
                KotlinProgramHistoryReadoutRefusal.INVALID_DAY_OR_SET),
            KotlinProgramHistoryReadoutDomain.review(request(
                listOf(valid),emptyList()).copy(allowedDayIndices=setOf(2))))
    }
}
