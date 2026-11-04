package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A production-shaped boundary check: the read-only facts API cannot turn an
 * unowned, unenrolled, or pending native ledger into believable Last/PR data.
 * The pure mixed-facts projection has separate detailed set-level tests.
 */
class NativeKotlinMixedHistoryFactsReadTest {
    @get:Rule val temp = TemporaryFolder()
    private val root get() = temp.root.toPath()
    private var current: NativeKotlinOwnerIdentity? =
        NativeKotlinOwnerIdentity("owner-1", "athlete-1")
    private val identity = NativeKotlinCurrentIdentity { current }
    private val fence by lazy { NativeKotlinApprovalRevisionFence(root) }
    private val history by lazy { NativeKotlinHistoryManifestFileStore(root) }
    private val composer by lazy { KotlinNativeJournalFileStore(root) }
    private val corrections by lazy {
        KotlinNativeSavedCorrectionFileStore(root, composer)
    }
    private val drafts by lazy {
        NativeCustomCycleDraftFileStore(root, fence, identity)
    }
    private val authoring by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(root, fence, identity)
    }
    private val signoffs by lazy {
        NativeKotlinCustomCycleSignoffFileStore(
            root, fence, identity, drafts, authoring)
    }
    private val preflight by lazy {
        NativeKotlinCustomWorkoutPreflight(
            fence, identity, drafts, authoring, signoffs)
    }
    private val prepared by lazy {
        NativeKotlinCustomWorkoutPreparedStore(
            root, fence, identity, preflight)
    }
    private val custom by lazy {
        NativeKotlinCustomWorkoutJournalFileStore(
            root, fence, identity, prepared, preflight, history, composer)
    }
    private val authority = object : NativeKotlinWorkoutAuthority {
        override fun currentApprovedWorkout(
            ownerKey: String, workoutId: String
        ): NativeKotlinWorkoutApproval? = null
        override fun canAccessIssuedWorkout(
            ownerKey: String, source: KotlinTrainingSourceRevision
        ): Boolean = ownerKey == "owner-1" &&
            source.athleteId == "athlete-1"
    }
    private val reader by lazy {
        NativeKotlinMixedHistoryReadCoordinator(history, composer,
            corrections, custom, authority, identity, fence)
    }

    @Test fun nativeFactsRequireRealEnrollmentAndCurrentOwner() {
        val missing = reader.readFacts("owner-1", "athlete-1")
        assertEquals(NativeKotlinMixedFactsReadResult.HistoryRejected(
            NativeKotlinMixedHistoryRefusal.NOT_ENROLLED), missing)

        val enrolled = history.enroll("owner-1", "athlete-1") { owner, athlete ->
            current == NativeKotlinOwnerIdentity(owner, athlete)
        }
        assertTrue(enrolled.toString(), enrolled is NativeHistoryLedgerResult.Durable)
        val opened = reader.readFacts("owner-1", "athlete-1")
        assertTrue(opened.toString(), opened is NativeKotlinMixedFactsReadResult.Review)
        val facts = (opened as NativeKotlinMixedFactsReadResult.Review).facts
        assertEquals("athlete-1", facts.athleteId)
        assertEquals(1L, facts.manifestRevision)
        assertTrue(facts.completedWorkoutIds.isEmpty())
        assertTrue(facts.lastByExercise.isEmpty())
        assertTrue(facts.highestCompletedLoad.isEmpty())
        assertFalse(facts.canPublishPr)
        assertFalse(facts.canIssueProgression)
        assertFalse(facts.canEditProgram)

        current = null
        assertEquals(NativeKotlinMixedFactsReadResult.HistoryRejected(
            NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED),
            reader.readFacts("owner-1", "athlete-1"))
    }

    @Test fun malformedScopeCannotReadEvenPreviouslyEnrolledHistory() {
        assertEquals(NativeKotlinMixedFactsReadResult.HistoryRejected(
            NativeKotlinMixedHistoryRefusal.INVALID_SCOPE),
            reader.readFacts("../owner", "athlete-1"))
        assertEquals(NativeKotlinMixedFactsReadResult.HistoryRejected(
            NativeKotlinMixedHistoryRefusal.INVALID_SCOPE),
            reader.readFacts("owner-1", "../athlete"))
    }
}
