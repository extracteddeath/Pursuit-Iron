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

    private fun composer(i: Int, fact: KotlinHistoricalSetFact) =
        NativeKotlinMixedHistoryCompletion.Composer(manifest(i),
            KotlinWorkoutHistoryEvidence(source, "workout-$i", "finish-$i", 3,
                listOf(fact), emptyList()))

    private fun composerFact(
        i: Int, reps: Int? = 8, actualLoad: Int = 200,
        outcome: KotlinJournalSetOutcome = KotlinJournalSetOutcome.COMPLETED,
        basis: LoadBasis = LoadBasis.SMITH_TOTAL,
        type: DraftSetType = DraftSetType.WORKING,
        event: String = "event-$i"
    ) = KotlinHistoricalSetFact("bench", "set-$i", type, 0,
        load(basis), RepBand(5, 8), 2, outcome,
        if (outcome == KotlinJournalSetOutcome.SKIPPED) null
        else KotlinPerformedSet(n(actualLoad), LoadUnit.LB, reps ?: 0, 2),
        event, null)

    private fun custom(
        i: Int, vararg visits: CustomHistoricalVisitFact,
        athlete: String = "athlete-1"
    ) = NativeKotlinMixedHistoryCompletion.Custom(manifest(i),
        CustomWorkoutHistoricalEvidence(athlete, "workout-$i", "cycle-1", 2,
            "finish-$i", 3, visits.toList()))

    private fun customVisit(
        i: Int, slot: String, part: CustomWorkoutVisitPart,
        outcome: KotlinJournalSetOutcome,
        actualLoad: Int? = 200,
        reps: Int = 8,
        event: String = "custom-event-$i-$slot-$part",
        basis: LoadBasis = LoadBasis.SMITH_TOTAL,
        type: DraftSetType = DraftSetType.WORKING
    ) = CustomHistoricalVisitFact(slot, "bench", "set-$i-$slot", part,
        type, load(basis), RepBand(5, 8), 2,
        if (part == CustomWorkoutVisitPart.FOLLOW_UP)
            CustomAmplifierKind.DROP_SET else null,
        outcome, if (outcome == KotlinJournalSetOutcome.SKIPPED) null
            else KotlinPerformedSet(actualLoad?.let(::n),
                if (actualLoad == null) null else LoadUnit.LB,
                reps, 2), event)

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
