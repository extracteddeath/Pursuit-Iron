package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Synthetic source facts test projection rules; owner-backed reads are covered separately. */
class NativeKotlinMixedHistoryFactsDomainTest {
    private fun n(value: Int) = BigDecimal(value)
    private fun load(basis: LoadBasis = LoadBasis.SMITH_TOTAL) =
        DraftSetLoad.Verified(LoadUnit.LB, basis, n(200))
    private val source = KotlinTrainingSourceRevision(
        "athlete-1", 1, "program-1", 1, "gym-1", 1, "catalog-1", "rules-1")

    private fun manifest(i: Int, revision: Long = 3) =
        KotlinHistoryManifestEntry(i.toLong(), "workout-$i", "stored-$i",
            "finish-$i", revision, 0)

    private fun composer(i: Int, fact: KotlinHistoricalSetFact,gym:String="gym-1") =
        NativeKotlinMixedHistoryCompletion.Composer(manifest(i),
            KotlinWorkoutHistoryEvidence(source.copy(gymId=gym),
                "workout-$i", "finish-$i", 3,
                listOf(fact), emptyList()))

    private fun composerFact(
        i: Int, reps: Int? = 8, actualLoad: Int = 200,
        outcome: KotlinJournalSetOutcome = KotlinJournalSetOutcome.COMPLETED,
        basis: LoadBasis = LoadBasis.SMITH_TOTAL,
        type: DraftSetType = DraftSetType.WORKING,
        event: String = "event-$i",
        machineId:String?=null
    ) = KotlinHistoricalSetFact("bench", "set-$i", type, 0,
        load(basis), RepBand(5, 8), 2, outcome,
        if (outcome == KotlinJournalSetOutcome.SKIPPED) null
        else KotlinPerformedSet(n(actualLoad), LoadUnit.LB, reps ?: 0, 2,machineId),
        event, null)

    private fun custom(
        i: Int, vararg visits: CustomHistoricalVisitFact,
        athlete: String = "athlete-1",gym:String=""
    ) = NativeKotlinMixedHistoryCompletion.Custom(manifest(i),
        CustomWorkoutHistoricalEvidence(athlete, "workout-$i", "cycle-1", 2,
            "finish-$i", 3, visits.toList()),gym)

    private fun customVisit(
        i: Int, slot: String, part: CustomWorkoutVisitPart,
        outcome: KotlinJournalSetOutcome,
        actualLoad: Int? = 200,
        reps: Int = 8,
        event: String = "custom-event-$i-$slot-$part",
        basis: LoadBasis = LoadBasis.SMITH_TOTAL,
        type: DraftSetType = DraftSetType.WORKING,
        machineId:String?=null
    ) = CustomHistoricalVisitFact(slot, "bench", "set-$i-$slot", part,
        type, load(basis), RepBand(5, 8), 2,
        if (part == CustomWorkoutVisitPart.FOLLOW_UP)
            CustomAmplifierKind.DROP_SET else null,
        outcome, if (outcome == KotlinJournalSetOutcome.SKIPPED) null
            else KotlinPerformedSet(actualLoad?.let(::n),
                if (actualLoad == null) null else LoadUnit.LB,
                reps, 2,machineId), event)

    private fun timeline(vararg sessions: NativeKotlinMixedHistoryCompletion,
                         revision: Long = 7) = NativeKotlinMixedHistoryTimeline(
        "athlete-1", revision, sessions.toList())

    private fun facts(vararg sessions: NativeKotlinMixedHistoryCompletion):
        NativeKotlinMixedHistoryFacts {
        val result = NativeKotlinMixedHistoryFactsDomain.project(timeline(*sessions))
        assertTrue(result.toString(), result is NativeMixedFactsResult.Review)
        return (result as NativeMixedFactsResult.Review).facts
    }

    @Test fun customSkippedLastNeverBacktracksToEarlierStrongComposerSession() {
        val old = composer(1, composerFact(1))
        val latest = custom(2, customVisit(2, "a",
            CustomWorkoutVisitPart.MAIN, KotlinJournalSetOutcome.SKIPPED))
        val index = facts(old, latest)
        assertEquals("workout-2", index.lastByExercise.getValue("bench").workoutId)
        assertTrue(index.lastByExercise.getValue("bench").isCustom)
        assertEquals(KotlinJournalSetOutcome.SKIPPED,
            index.lastByExercise.getValue("bench").workingSets.single().outcome)
        assertEquals("workout-1", index.highestCompletedLoad.values.single().workoutId)
        assertFalse(index.canPublishPr)
        assertFalse(index.canIssueProgression)
        assertFalse(index.canEditProgram)
    }

    @Test fun customFollowUpsCannotCreateWorkingSetRecords() {
        val main = customVisit(1, "a", CustomWorkoutVisitPart.MAIN,
            KotlinJournalSetOutcome.COMPLETED, actualLoad = 200, reps = 6)
        val follow = customVisit(1, "a", CustomWorkoutVisitPart.FOLLOW_UP,
            KotlinJournalSetOutcome.COMPLETED, actualLoad = 400, reps = 20)
        val index = facts(custom(1, main, follow))
        assertEquals(1, index.lastByExercise.getValue("bench").workingSets.size)
        assertEquals(n(200), index.highestCompletedLoad.values.single().actualLoad)
        assertEquals(1, index.mostRepsAtLoad.size)
        assertEquals(6, index.mostRepsAtLoad.single().reps)
    }

    @Test fun repeatedCustomSlotsRetainTheirDistinctOriginsAndCorrectedFacts() {
        val first = customVisit(1, "slot-a", CustomWorkoutVisitPart.MAIN,
            KotlinJournalSetOutcome.COMPLETED, actualLoad = 200, reps = 8)
        val edited = customVisit(1, "slot-b", CustomWorkoutVisitPart.MAIN,
            KotlinJournalSetOutcome.COMPLETED, actualLoad = 205, reps = 4,
            event = "saved-correction-1")
        val index = facts(custom(1, first, edited))
        val working = index.lastByExercise.getValue("bench").workingSets
        assertEquals(listOf("slot-a", "slot-b"), working.map { it.customSlotId })
        assertEquals("saved-correction-1", working.last().effectiveEventId)
        assertEquals(n(205), index.highestCompletedLoad.values.single().actualLoad)
        assertEquals("slot-b", index.highestCompletedLoad.values.single().customSlotId)
    }

    @Test fun loadsRemainSeparatedByPhysicalBasisAndNumericScale() {
        val one = composer(1, composerFact(1, actualLoad = 200))
        val two = custom(2, customVisit(2, "a", CustomWorkoutVisitPart.MAIN,
            KotlinJournalSetOutcome.COMPLETED, actualLoad = 30, reps = 10,
            basis = LoadBasis.DUMBBELL_PER_HAND))
        val index = facts(one, two)
        assertEquals(2, index.highestCompletedLoad.size)
        assertEquals(2, index.mostRepsAtLoad.size)
        assertEquals(n(200), index.highestCompletedLoad.getValue(
            NativeMixedPhysicalLoadKey("bench", LoadUnit.LB, LoadBasis.SMITH_TOTAL)).actualLoad)
        assertEquals(n(30), index.highestCompletedLoad.getValue(
            NativeMixedPhysicalLoadKey("bench", LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND)).actualLoad)
    }

    @Test fun verifiedPhysicalMachineBestsAreSeparatedAcrossGymsAndCorrections() {
        val a=composer(1,composerFact(1,actualLoad=200,machineId="A"))
        val b=composer(2,composerFact(2,actualLoad=140,machineId="B"))
        val otherGym=composer(3,composerFact(3,actualLoad=190,machineId="A"),
            gym="gym-2")
        // A saved correction belongs to the original issued gym and must
        // compete only with A from that gym, not with B or gym-2 A.
        val corrected=custom(4,customVisit(4,"revised",
            CustomWorkoutVisitPart.MAIN,KotlinJournalSetOutcome.COMPLETED,
            actualLoad=205,reps=4,event="saved-correction",
            machineId="A"),gym="gym-1")
        val index=facts(a,b,otherGym,corrected)
        fun key(gym:String,machine:String)=NativeMixedMachineLoadKey(
            gym,"bench",machine,LoadUnit.LB,LoadBasis.SMITH_TOTAL)
        assertEquals(3,index.highestCompletedLoadByMachine.size)
        assertEquals(n(205),index.highestCompletedLoadByMachine
            .getValue(key("gym-1","A")).actualLoad)
        assertEquals("saved-correction",index.highestCompletedLoadByMachine
            .getValue(key("gym-1","A")).effectiveEventId)
        assertEquals(n(140),index.highestCompletedLoadByMachine
            .getValue(key("gym-1","B")).actualLoad)
        assertEquals(n(190),index.highestCompletedLoadByMachine
            .getValue(key("gym-2","A")).actualLoad)
        assertFalse(index.canPublishPr)
    }

    @Test fun unknownCustomGymOrMachineAndFollowUpsCannotEarnStationRecords() {
        val synthetic=custom(1,customVisit(1,"slot",
            CustomWorkoutVisitPart.MAIN,KotlinJournalSetOutcome.COMPLETED,
            machineId="A"))
        val unknownMachine=composer(2,composerFact(2,machineId=null))
        val follow=custom(3,customVisit(3,"slot",
            CustomWorkoutVisitPart.FOLLOW_UP,KotlinJournalSetOutcome.COMPLETED,
            machineId="A",actualLoad=500),gym="gym-1")
        val legitimate=composer(4,composerFact(4,machineId="A",reps=1,
            actualLoad=180))
        val index=facts(synthetic,unknownMachine,follow,legitimate)
        assertEquals(1,index.highestCompletedLoadByMachine.size)
        assertEquals(180,index.highestCompletedLoadByMachine.values
            .single().actualLoad.toInt())
        assertEquals(1,index.bestLoggedSingleByMachine.size)
        assertEquals("workout-4",index.bestLoggedSingleByMachine.values
            .single().workoutId)
    }

    @Test fun physicalMachineSingleRemainsSeparateFromRepetitionBests() {
        val single=composer(1,composerFact(1,reps=1,actualLoad=185,machineId="A"))
        val eight=composer(2,composerFact(2,reps=8,actualLoad=200,machineId="A"))
        val other=composer(3,composerFact(3,reps=1,actualLoad=170,machineId="B"))
        val index=facts(single,eight,other)
        val a=NativeMixedMachineLoadKey("gym-1","bench","A",
            LoadUnit.LB,LoadBasis.SMITH_TOTAL)
        val b=a.copy(machineInstanceId="B")
        assertEquals(n(200),index.highestCompletedLoadByMachine.getValue(a).actualLoad)
        assertEquals(n(185),index.bestLoggedSingleByMachine.getValue(a).actualLoad)
        assertEquals(n(170),index.bestLoggedSingleByMachine.getValue(b).actualLoad)
    }

    @Test fun physicalMachineTaggedBarbellLoadsNeverInflateBarbellBenchmarks() {
        // A moved/swapped physical machine can carry a stale BARBELL_TOTAL
        // target basis, but its actual physical station identity is explicit.
        // No such set can earn a barbell strength standard or club milestone.
        val machine=composer(1,composerFact(1,reps=1,actualLoad=500,
            basis=LoadBasis.BARBELL_TOTAL,machineId="A"))
        val realBarbell=composer(2,composerFact(2,reps=1,actualLoad=200,
            basis=LoadBasis.BARBELL_TOTAL,machineId=null))
        val index=facts(machine,realBarbell)
        val barbellKey=NativeMixedPhysicalLoadKey(
            "bench",LoadUnit.LB,LoadBasis.BARBELL_TOTAL)
        val machineKey=NativeMixedMachineLoadKey(
            "gym-1","bench","A",LoadUnit.LB,LoadBasis.BARBELL_TOTAL)
        assertEquals(n(200),index.bestLoggedSingleByLoad
            .getValue(barbellKey).actualLoad)
        assertEquals(n(200),index.highestCompletedLoad
            .getValue(barbellKey).actualLoad)
        assertEquals(listOf(n(200)),index.mostRepsAtLoad.map {it.actualLoad})
        assertEquals(n(500),index.highestCompletedLoadByMachine
            .getValue(machineKey).actualLoad)
        assertEquals(n(500),index.bestLoggedSingleByMachine
            .getValue(machineKey).actualLoad)
        assertTrue(facts(machine).bestLoggedSingleByLoad.isEmpty())
        assertFalse(index.canPublishPr)
    }

    @Test fun loggedSingleIsRetainedAfterMoreRepsAtIdenticalLoad() {
        val one=composer(1,composerFact(1,reps=1,actualLoad=200,
            basis=LoadBasis.BARBELL_TOTAL))
        val five=composer(2,composerFact(2,reps=5,actualLoad=200,
            basis=LoadBasis.BARBELL_TOTAL))
        val result=facts(one,five)
        val key=NativeMixedPhysicalLoadKey("bench",LoadUnit.LB,
            LoadBasis.BARBELL_TOTAL)
        assertEquals(5,result.mostRepsAtLoad.single().reps)
        assertEquals(1,result.bestLoggedSingleByLoad.getValue(key).reps)
        assertEquals("workout-1",
            result.bestLoggedSingleByLoad.getValue(key).workoutId)
    }
    @Test fun newestFailedOrWarmupOnlyAppearanceStillWinsLast() {
        val old = composer(1, composerFact(1))
        val failed = composer(2, composerFact(2, reps = 0,
            outcome = KotlinJournalSetOutcome.FAILED_ATTEMPT))
        val failureIndex = facts(old, failed)
        assertEquals("workout-2", failureIndex.lastByExercise.getValue("bench").workoutId)
        assertEquals(0, failureIndex.lastByExercise.getValue("bench")
            .workingSets.single().actual?.reps)
        val warmup = composer(2, composerFact(2, type = DraftSetType.WARMUP))
        val warmupIndex = facts(old, warmup)
        assertTrue(warmupIndex.lastByExercise.getValue("bench").workingSets.isEmpty())
        assertEquals(1, warmupIndex.highestCompletedLoad.size)
    }

    @Test fun malformedChronologyDuplicateReceiptsAndForeignFactsAreRejected() {
        val first = composer(1, composerFact(1))
        val second = composer(2, composerFact(2))
        assertEquals(NativeMixedFactsResult.Rejected(NativeMixedFactsRefusal.MANIFEST_MISMATCH),
            NativeKotlinMixedHistoryFactsDomain.project(timeline(second, first)))
        assertEquals(NativeMixedFactsResult.Rejected(NativeMixedFactsRefusal.DUPLICATE_COMPLETION),
            NativeKotlinMixedHistoryFactsDomain.project(timeline(first, first)))
        val wrong = custom(2, customVisit(2, "a", CustomWorkoutVisitPart.MAIN,
            KotlinJournalSetOutcome.COMPLETED), athlete = "foreign")
        assertEquals(NativeMixedFactsResult.Rejected(NativeMixedFactsRefusal.CROSS_ATHLETE),
            NativeKotlinMixedHistoryFactsDomain.project(timeline(first, wrong)))
        val missing = NativeKotlinMixedHistoryCompletion.Composer(
            manifest(2).copy(finishEventId = "forged"),
            second.evidence)
        assertEquals(NativeMixedFactsResult.Rejected(NativeMixedFactsRefusal.MANIFEST_MISMATCH),
            NativeKotlinMixedHistoryFactsDomain.project(timeline(first, missing)))
        assertEquals(NativeMixedFactsResult.Rejected(NativeMixedFactsRefusal.INVALID_TIMELINE),
            NativeKotlinMixedHistoryFactsDomain.project(timeline(revision = 0)))
    }
}
