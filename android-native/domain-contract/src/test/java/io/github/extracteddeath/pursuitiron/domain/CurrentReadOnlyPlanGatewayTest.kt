package io.github.extracteddeath.pursuitiron.domain

import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.*
import org.junit.Test

class CurrentReadOnlyPlanGatewayTest {
    private val ref = ReadOnlyProgramReference("synthetic-program",42,100,13,"a".repeat(64))
    private val active = CurrentProgramEvidence(
        ref,CurrentProgramDeletionEvidence.CheckedAbsent)
    private val summary = ReadOnlyProgramSummary(
        reference = ref, id = ref.programId, name = "Synthetic plan",
        custom = true, goal = "hypertrophy", weeks = 6,
        days = listOf(ReadOnlyProgramDay("day-1","Upper",listOf("bb-bench"))))

    private fun <T> immediate(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return checkNotNull(outcome) { "Synthetic source must complete synchronously" }.getOrThrow()
    }

    private class Source(
        var first: CurrentProgramEvidence?,
        var second: CurrentProgramEvidence?,
        var summary: ReadOnlyProgramSummary?
    ) : CurrentReadOnlyPlanSource {
        var reads = 0
        var projections = 0
        var failure: Exception? = null
        override suspend fun currentEvidence(programId: String): CurrentProgramEvidence? {
            reads++
            return if (reads == 1) first else second
        }
        override suspend fun projectStructure(programId: String): ReadOnlyProgramSummary? {
            projections++
            failure?.let { throw it }
            return summary
        }
    }

    @Test fun unconnectedSourceCannotProducePersonalPlan() {
        assertSame(ReadOnlyProgramPresentation.Unavailable, immediate {
            CurrentReadOnlyPlanGateway(UnconnectedCurrentReadOnlyPlanSource)
                .inspect(ref.programId)
        })
    }

    @Test fun exactCurrentEvidenceRequiredBeforeAndAfterProjection() {
        val stable = Source(active,active,summary)
        assertTrue(immediate {
            CurrentReadOnlyPlanGateway(stable).inspect(ref.programId)
        } is ReadOnlyProgramPresentation.Structure)
        assertEquals(2,stable.reads)
        assertEquals(1,stable.projections)
        assertSame(ReadOnlyProgramPresentation.Unavailable, immediate {
            CurrentReadOnlyPlanGateway(Source(active,active,summary.copy(id="other")))
                .inspect(ref.programId)
        })
    }

    @Test fun concurrentChangesOrDeletionsNeverReachPlan() {
        val revised = active.copy(reference = ref.copy(programUpdatedAt = 43))
        val deleted = active.copy(deletion = CurrentProgramDeletionEvidence.CheckedAt(42))
        val cases = listOf(
            Source(active,revised,summary),
            Source(active,deleted,summary),
            Source(active,null,summary),
            Source(null,active,summary),
            Source(active,active,null),
            Source(deleted,deleted,summary)
        )
        for (source in cases) {
            assertSame(ReadOnlyProgramPresentation.Unavailable,immediate {
                CurrentReadOnlyPlanGateway(source).inspect(ref.programId)
            })
        }
    }

    @Test fun invalidIdAndFailuresFailClosedButCancellationPropagates() {
        val never = Source(active,active,summary)
        assertSame(ReadOnlyProgramPresentation.Unavailable,immediate {
            CurrentReadOnlyPlanGateway(never).inspect("invalid id")
        })
        assertEquals(0,never.reads)
        val failing = Source(active,active,summary).also {
            it.failure = IllegalStateException("synthetic failure")
        }
        assertSame(ReadOnlyProgramPresentation.Unavailable,immediate {
            CurrentReadOnlyPlanGateway(failing).inspect(ref.programId)
        })
        val cancel = CancellationException("synthetic cancellation")
        val interrupted = Source(active,active,summary).also { it.failure = cancel }
        assertSame(cancel,runCatching {
            immediate { CurrentReadOnlyPlanGateway(interrupted).inspect(ref.programId) }
        }.exceptionOrNull())
    }
}
