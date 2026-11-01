package io.github.extracteddeath.pursuitiron.domain

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.startCoroutine
import kotlin.coroutines.suspendCoroutine
import org.junit.Assert.*
import org.junit.Test

class ReadOnlyPlanPresentationSessionTest {
    private val ref = ReadOnlyProgramReference("synthetic-program",42,100,13,"a".repeat(64))
    private val checked = CurrentProgramEvidence(ref,CurrentProgramDeletionEvidence.CheckedAbsent)
    private val plan = ReadOnlyProgramSummary(
        ref,ref.programId,"Synthetic plan",true,"hypertrophy",6,
        listOf(ReadOnlyProgramDay("day-1","Upper",listOf("bb-bench"))))

    private class PausedSource(
        private val proof: CurrentProgramEvidence,
        private val structure: ReadOnlyProgramSummary
    ) : CurrentReadOnlyPlanSource {
        val pending = mutableListOf<Continuation<ReadOnlyProgramSummary?>>()
        var evidenceReads = 0
        private val listeners = mutableSetOf<() -> Unit>()
        override fun subscribeCanonicalChanges(onChanged: () -> Unit): AutoCloseable {
            listeners.add(onChanged)
            return AutoCloseable { listeners.remove(onChanged) }
        }
        fun signalCanonicalChange() = listeners.toList().forEach { it() }
        override suspend fun currentEvidence(programId: String): CurrentProgramEvidence {
            evidenceReads++
            return proof
        }
        override suspend fun projectStructure(programId: String): ReadOnlyProgramSummary? =
            suspendCoroutine { continuation -> pending.add(continuation) }
        fun complete(index: Int) { pending[index].resume(structure) }
    }
    private class Pending<T> {
        var outcome: Result<T>? = null
        fun start(block: suspend () -> T) {
            block.startCoroutine(object : Continuation<T> {
                override val context = EmptyCoroutineContext
                override fun resumeWith(result: Result<T>) { outcome = result }
            })
        }
        fun value(): T = checkNotNull(outcome) { "Synthetic read remains suspended" }.getOrThrow()
    }
    private fun fixture(): Pair<ReadOnlyPlanPresentationSession,PausedSource> {
        val source=PausedSource(checked,plan)
        return ReadOnlyPlanPresentationSession(CurrentReadOnlyPlanGateway(source)) to source
    }
    private fun empty(session: ReadOnlyPlanPresentationSession) {
        assertSame(ReadOnlyProgramPresentation.Unavailable,session.current())
    }

    @Test fun unselectedOrBackgroundedPlanCannotReadPersonalStructure() {
        val (session,source)=fixture()
        val request=Pending<ReadOnlyProgramPresentation>()
        request.start { session.refresh() }
        assertSame(ReadOnlyProgramPresentation.Unavailable,request.value())
        assertEquals(0,source.pending.size)
        session.onForeground(null)
        request.start { session.refresh() }
        assertSame(ReadOnlyProgramPresentation.Unavailable,request.value())
        empty(session)
    }

    @Test fun backgroundNavigationAndStoreChangesRejectLateResults() {
        val invalidations = listOf<(ReadOnlyPlanPresentationSession)->Unit>(
            { it.onBackground() },
            { it.onForeground("different-program") },
            { it.onCanonicalStoreChanged() }
        )
        for (invalidate in invalidations) {
            val (session,source)=fixture()
            session.onForeground(ref.programId)
            val request=Pending<ReadOnlyProgramPresentation>()
            request.start { session.refresh() }
            assertNull(request.outcome)
            assertEquals(1,source.pending.size)
            invalidate(session)
            empty(session)
            source.complete(0)
            assertSame(ReadOnlyProgramPresentation.Unavailable,request.value())
            empty(session)
        }
    }

    @Test fun newerReadWinsEvenWhenOlderResultArrivesLast() {
        val (session,source)=fixture()
        session.onForeground(ref.programId)
        val first=Pending<ReadOnlyProgramPresentation>()
        val second=Pending<ReadOnlyProgramPresentation>()
        first.start { session.refresh() }
        second.start { session.refresh() }
        assertEquals(2,source.pending.size)
        source.complete(1)
        val approved=second.value()
        assertTrue(approved is ReadOnlyProgramPresentation.Structure)
        assertSame(approved,session.current())
        source.complete(0)
        assertSame(ReadOnlyProgramPresentation.Unavailable,first.value())
        assertSame(approved,session.current())
        session.onCanonicalStoreChanged()
        empty(session)
    }

    @Test fun personalStructureIsClearedAndRequiresFreshReadAfterResume() {
        val (session,source)=fixture()
        session.onForeground(ref.programId)
        val request=Pending<ReadOnlyProgramPresentation>()
        request.start { session.refresh() }
        source.complete(0)
        assertTrue(request.value() is ReadOnlyProgramPresentation.Structure)
        session.onBackground()
        empty(session)
        session.onForeground(ref.programId)
        empty(session)
        val restarted=Pending<ReadOnlyProgramPresentation>()
        restarted.start { session.refresh() }
        source.complete(1)
        assertTrue(restarted.value() is ReadOnlyProgramPresentation.Structure)
        assertEquals(4,source.evidenceReads)
    }

    @Test fun committedStoreSignalsInvalidateEvenOutstandingReadsAndUnsubscribe() {
        val (session, source) = fixture()
        session.onForeground(ref.programId)
        val subscription = source.subscribeCanonicalChanges {
            session.onCanonicalStoreChanged()
        }
        val pending = Pending<ReadOnlyProgramPresentation>()
        pending.start { session.refresh() }
        source.signalCanonicalChange()
        empty(session)
        source.complete(0)
        assertSame(ReadOnlyProgramPresentation.Unavailable,pending.value())
        empty(session)

        val current=Pending<ReadOnlyProgramPresentation>()
        current.start { session.refresh() }
        source.complete(1)
        assertTrue(current.value() is ReadOnlyProgramPresentation.Structure)
        subscription.close()
        source.signalCanonicalChange()
        assertSame(current.value(),session.current())
    }
}
