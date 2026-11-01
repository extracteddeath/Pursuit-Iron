package io.github.extracteddeath.pursuitiron.domain

import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** The shared JS/Kotlin v13 fixture contains entirely invented user data. */
class CanonicalCurrentPlanSourceAdapterTest {
    private val fixture by lazy {
        val stream = checkNotNull(javaClass.classLoader
            ?.getResourceAsStream("read-only-transport-golden.json"))
        JSONObject(stream.bufferedReader().use { it.readText() })
    }
    private val wire by lazy { fixture.getJSONObject("wire").toString() }
    private val reference by lazy {
        val ref = fixture.getJSONObject("wire").getJSONObject("reference")
        ReadOnlyProgramReference(
            ref.getString("programId"), ref.getLong("programUpdatedAt"),
            ref.getLong("storeSavedAt"), ref.getInt("storeVersion"),
            ref.getString("snapshotSha256")
        )
    }
    private val active by lazy {
        CanonicalCurrentPlanFrame(
            reference, CurrentProgramDeletionEvidence.CheckedAbsent, wire
        )
    }

    private class FakePort(var frames: List<CanonicalCurrentPlanFrame?>) :
        CanonicalCurrentPlanReadPort {
        var reads = 0
        var subscribed = 0
        var closed = 0
        var failure: Exception? = null
        private val callbacks = mutableSetOf<() -> Unit>()
        override suspend fun readCurrentProjection(programId: String): CanonicalCurrentPlanFrame? {
            failure?.let { throw it }
            val index = reads++
            return if (index < frames.size) frames[index] else frames.lastOrNull()
        }
        override fun subscribeCommittedChanges(onChanged: () -> Unit): AutoCloseable {
            subscribed++
            callbacks.add(onChanged)
            return AutoCloseable { if (callbacks.remove(onChanged)) closed++ }
        }
        fun changeCommitted() = callbacks.toList().forEach { it() }
    }

    private fun <T> immediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return checkNotNull(outcome) { "Synthetic operation must complete" }.getOrThrow()
    }

    private fun presented(port: FakePort): ReadOnlyProgramPresentation = immediate {
        CurrentReadOnlyPlanGateway(
            CanonicalCurrentPlanSourceAdapter(port)
        ).inspect(reference.programId)
    }

    @Test fun exactCanonicalTransportYieldsBoundedReadOnlyStructure() {
        val port = FakePort(listOf(active))
        val approved = presented(port)
        assertTrue(approved is ReadOnlyProgramPresentation.Structure)
        val data = (approved as ReadOnlyProgramPresentation.Structure).program
        assertEquals("Synthetic full body", data.name)
        assertEquals(reference, data.reference)
        assertEquals(listOf("bb-bench", "cable-fly"), data.days[0].exerciseIds)
        assertEquals(3, port.reads) // evidence, projection, evidence again
    }

    @Test fun deletedUntrustedOrMalformedTransportNeverReachesCompose() {
        val writable = JSONObject(wire).apply {
            getJSONObject("program").put("targetWeight", 225)
        }.toString()
        val cases = listOf(
            active.copy(deletion = CurrentProgramDeletionEvidence.CheckedAt(42)),
            active.copy(deletion = CurrentProgramDeletionEvidence.CheckedAt(43)),
            active.copy(deletion = CurrentProgramDeletionEvidence.CheckedAt(-1)),
            active.copy(reference = reference.copy(snapshotSha256 = "b".repeat(64))),
            active.copy(reference = reference.copy(programId = "other-program")),
            active.copy(transportJson = writable),
            active.copy(transportJson = "not JSON"),
            active.copy(transportJson = " ".repeat(65_537))
        )
        for (invalid in cases) {
            assertSame(ReadOnlyProgramPresentation.Unavailable,
                presented(FakePort(listOf(invalid))))
        }
    }

    @Test fun sourceMutationOrRemovalAcrossReadsFailsClosed() {
        val newReference = reference.copy(programUpdatedAt = 43,
            snapshotSha256 = "b".repeat(64))
        val revised = active.copy(reference = newReference)
        val deleted = active.copy(deletion = CurrentProgramDeletionEvidence.CheckedAt(42))
        for (frames in listOf(
            listOf(active, active, revised),
            listOf(active, active, deleted),
            listOf(active, revised, revised),
            listOf(active, null, active)
        )) {
            assertSame(ReadOnlyProgramPresentation.Unavailable,
                presented(FakePort(frames)))
        }
    }

    @Test fun changeObserverIsDisposableAndDoesNotReadOrWriteStorage() {
        val port = FakePort(listOf(active))
        val adapter = CanonicalCurrentPlanSourceAdapter(port)
        var signals = 0
        val handle = adapter.subscribeCanonicalChanges { signals++ }
        port.changeCommitted()
        handle.close()
        port.changeCommitted()
        assertEquals(1, signals)
        assertEquals(1, port.subscribed)
        assertEquals(1, port.closed)
        assertEquals(0, port.reads)
    }

    @Test fun unavailableSourceFailsClosedButCancellationPropagates() {
        val port = FakePort(listOf(active)).also {
            it.failure = IllegalStateException("Synthetic owner unavailable")
        }
        assertSame(ReadOnlyProgramPresentation.Unavailable, presented(port))
        val cancel = CancellationException("Synthetic cancelled")
        val interrupted = FakePort(listOf(active)).also { it.failure = cancel }
        assertSame(cancel,runCatching { presented(interrupted) }.exceptionOrNull())
    }
}
