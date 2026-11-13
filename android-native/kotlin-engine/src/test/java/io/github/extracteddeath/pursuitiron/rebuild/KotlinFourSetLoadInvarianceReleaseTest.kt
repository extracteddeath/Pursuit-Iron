package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Release blocker #518: a real four-working-set journal is not authority to
 * silently rewrite the immutable issued straight-set prescription.
 */
class KotlinFourSetLoadInvarianceReleaseTest {
    private fun n(v: String) = BigDecimal(v)
    private val source = KotlinTrainingSourceRevision(
        "athlete-release", 4, "program-release", 2, "gym-release", 1,
        "catalog-release", "coach-release"
    )
    private val targets = (0..3).map { index ->
        KotlinWorkoutTarget(
            "machine-press", ComposerRole.PRIMARY,
            ComposerSessionPurpose.STRENGTH_SKILL, "press:s$index",
            DraftSetType.WORKING, index,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL, n("215")),
            RepBand(5, 8), 2, 60, 120
        )
    }
    private val snapshot = KotlinStagedWorkout(
        source, "grant-release", "session-release", 0, 0,
        ComposerSessionPurpose.STRENGTH_SKILL, 50, targets
    )
    private val identity = KotlinStoredWorkoutIdentity(
        "stored-release", "session-release", source, 1
    )
    private fun issued(j: KotlinWorkoutJournalState) =
        j.snapshot.targets.map { (it.load as DraftSetLoad.Verified).value }
    private fun record(
        j: KotlinWorkoutJournalState, event: String, key: String,
        load: String, reps: Int, rir: Int?, machine: String = "A",
        corrects: String? = null
    ): KotlinWorkoutJournalState {
        val op = KotlinJournalOperation.RecordSet(
            event, j.revision, key, KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(load), LoadUnit.LB, reps, rir, machine),
            correctsEventId = corrects
        )
        return (KotlinWorkoutJournalDomain.reduce(j, op)
            as KotlinJournalDecision.Proposed).state
    }
    private fun coldResume(j: KotlinWorkoutJournalState): KotlinWorkoutJournalState {
        val result = KotlinWorkoutJournalReplayDomain.replay(
            snapshot, identity, j.receipts, j.revision, j.finishedByEventId
        )
        assertTrue(result.toString(), result is KotlinJournalReplayResult.Recovered)
        return (result as KotlinJournalReplayResult.Recovered).state
    }

    @Test fun fourSetsNearFailureManualEditAndColdResumeNeverRewriteIssuedLoad() {
        var journal = KotlinWorkoutJournalDomain.begin(snapshot, identity)
        assertEquals(List(4) { n("215") }, issued(journal))
        journal = record(journal, "first", "press:s0", "215", 2, 0)
        assertEquals(List(4) { n("215") }, issued(journal))
        journal = coldResume(journal)
        journal = record(journal, "second", "press:s1", "205", 3, 0)
        assertEquals(List(4) { n("215") }, issued(journal))
        journal = coldResume(journal)
        journal = record(journal, "correct-second", "press:s1",
            "210", 4, 1, corrects = "second")
        assertEquals(n("210"),
            (journal.effectiveSets.getValue("press:s1").operation
                as KotlinJournalOperation.RecordSet).performed?.load)
        journal = coldResume(journal)
        journal = record(journal, "third", "press:s2", "215", 2, 0)
        journal = record(journal, "fourth", "press:s3", "215", 1, 0)
        assertEquals(List(4) { n("215") }, issued(journal))
        assertEquals(snapshot, journal.snapshot)
        val completed = KotlinWorkoutJournalDomain.reduce(
            journal, KotlinJournalOperation.Finish("finish-release", journal.revision)
        ) as KotlinJournalDecision.Proposed
        val recovered = coldResume(completed.state)
        assertEquals(List(4) { n("215") }, issued(recovered))
        assertEquals(snapshot, recovered.snapshot)
        assertEquals("finish-release", recovered.finishedByEventId)
        assertEquals(4, recovered.resolvedSetCount)
        assertEquals(listOf(n("215"), n("210"), n("215"), n("215")),
            (0..3).map {
                (recovered.effectiveSets.getValue("press:s$it").operation
                    as KotlinJournalOperation.RecordSet).performed!!.load
            })
    }

    @Test fun missingRirAndDifferentPhysicalMachineAreFactsNotNewIssuedTargets() {
        var journal = KotlinWorkoutJournalDomain.begin(snapshot, identity)
        journal = record(journal, "machine-a", "press:s0", "215", 5, null, "A")
        journal = record(journal, "machine-b", "press:s1", "165", 7, null, "B")
        journal = coldResume(journal)
        assertEquals(List(4) { n("215") }, issued(journal))
        assertNull((journal.effectiveSets.getValue("press:s1").operation
            as KotlinJournalOperation.RecordSet).performed?.actualRir)
        assertEquals("B", (journal.effectiveSets.getValue("press:s1").operation
            as KotlinJournalOperation.RecordSet).performed?.machineInstanceId)
        assertTrue("press:s2" !in journal.effectiveSets)
    }
}
