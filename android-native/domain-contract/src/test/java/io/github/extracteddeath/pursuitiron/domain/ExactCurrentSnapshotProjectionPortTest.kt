package io.github.extracteddeath.pursuitiron.domain

import java.util.concurrent.CancellationException
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

/** A wholly synthetic canonical v13 store; never a real backup or profile. */
class ExactCurrentSnapshotProjectionPortTest {
    private val fixture by lazy {
        val resource = checkNotNull(javaClass.classLoader
            ?.getResourceAsStream("read-only-transport-golden.json"))
        JSONObject(resource.bufferedReader().use { it.readText() })
    }
    private val raw by lazy { fixture.getString("rawSnapshot") }
    private val wire by lazy { fixture.getJSONObject("wire") }
    private val reference by lazy {
        val obj = wire.getJSONObject("reference")
        ReadOnlyProgramReference(
            obj.getString("programId"), obj.getLong("programUpdatedAt"),
            obj.getLong("storeSavedAt"), obj.getInt("storeVersion"),
            obj.getString("snapshotSha256")
        )
    }
    private val active by lazy {
        CanonicalCurrentPlanFrame(reference,
            CurrentProgramDeletionEvidence.CheckedAbsent, wire.toString())
    }

    private class FakeOwner(
        private val nextBytes: () -> ByteArray?
    ) : CanonicalCurrentSnapshotOwner {
        val issued = mutableListOf<ByteArray>()
        var listener: (() -> Unit)? = null
        var unsubscribed = 0
        override suspend fun readOwnedCurrentStoreBytes(): ByteArray? =
            nextBytes()?.also { issued.add(it) }
        override fun subscribeCommittedChanges(onChanged: () -> Unit): AutoCloseable {
            listener = onChanged
            return AutoCloseable {
                listener = null
                unsubscribed++
            }
        }
        fun change() { listener?.invoke() }
    }
    private class FakeProjector(
        var frame: CanonicalCurrentPlanFrame?,
        var fault: Exception? = null
    ) : PinnedCanonicalPlanProjector {
        var calls = 0
        var observedText: String? = null
        override suspend fun projectCurrentSavedProgram(
            exactUtf8Snapshot: String, programId: String
        ): CanonicalCurrentPlanFrame? {
            calls++
            observedText = exactUtf8Snapshot
            fault?.let { throw it }
            return frame
        }
    }
    private fun <T> immediate(block: suspend () -> T): T {
        var result: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result = value }
        })
        return checkNotNull(result) { "Synthetic read must complete" }.getOrThrow()
    }

    @Test fun exactCurrentBytesBindCanonicalTransportAndAreAlwaysWiped() {
        val owner = FakeOwner { raw.toByteArray(Charsets.UTF_8) }
        val projector = FakeProjector(active)
        val port = ExactCurrentSnapshotProjectionPort(owner, projector)
        val approved = immediate {
            CurrentReadOnlyPlanGateway(CanonicalCurrentPlanSourceAdapter(port))
                .inspect(reference.programId)
        }
        assertTrue(approved is ReadOnlyProgramPresentation.Structure)
        assertEquals(3, projector.calls)
        assertEquals(3, owner.issued.size)
        assertEquals(raw, projector.observedText)
        assertTrue(owner.issued.all { bytes -> bytes.all { it == 0.toByte() } })
    }

    @Test fun wrongCurrentBytesCannotUseOtherwiseMatchingCanonicalMetadata() {
        val edited = raw.replace("Synthetic full body", "Edited full body")
        val owner = FakeOwner { edited.toByteArray(Charsets.UTF_8) }
        val projector = FakeProjector(active)
        val port = ExactCurrentSnapshotProjectionPort(owner, projector)
        val result = immediate { port.readCurrentProjection(reference.programId) }
        assertNull(result)
        assertEquals(1, projector.calls)
        assertTrue(owner.issued.single().all { it == 0.toByte() })
    }

    @Test fun emptyLargeBomAndMalformedBytesNeverReachTheProjector() {
        val invalid = listOf(
            byteArrayOf(),
            ByteArray(ReadOnlyBackupLimits.MAX_SOURCE_BYTES + 1) { 0x41 },
            byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) +
                raw.toByteArray(Charsets.UTF_8),
            byteArrayOf(0xc3.toByte(), 0x28)
        )
        for (bytes in invalid) {
            val owner = FakeOwner { bytes.copyOf() }
            val projector = FakeProjector(active)
            val port = ExactCurrentSnapshotProjectionPort(owner, projector)
            assertNull(immediate { port.readCurrentProjection(reference.programId) })
            assertEquals(0, projector.calls)
            assertTrue(owner.issued.single().all { it == 0.toByte() })
        }
    }

    @Test fun invalidProgramIdAndAbsentStoreDoNotProjectOrKeepData() {
        val owner = FakeOwner { raw.toByteArray(Charsets.UTF_8) }
        val projector = FakeProjector(active)
        val port = ExactCurrentSnapshotProjectionPort(owner, projector)
        assertNull(immediate { port.readCurrentProjection("contains space") })
        assertEquals(0, owner.issued.size)
        val absent = ExactCurrentSnapshotProjectionPort(FakeOwner { null }, projector)
        assertNull(immediate { absent.readCurrentProjection(reference.programId) })
        assertEquals(0, projector.calls)
    }

    @Test fun projectorErrorsFailClosedAndCancellationPropagatesWithWiping() {
        val owner = FakeOwner { raw.toByteArray(Charsets.UTF_8) }
        val projector = FakeProjector(active,
            IllegalStateException("synthetic sandbox error"))
        val port = ExactCurrentSnapshotProjectionPort(owner, projector)
        assertNull(immediate { port.readCurrentProjection(reference.programId) })
        assertTrue(owner.issued.single().all { it == 0.toByte() })
        val cancel = CancellationException("synthetic canceled")
        projector.fault = cancel
        assertSame(cancel, runCatching {
            immediate { port.readCurrentProjection(reference.programId) }
        }.exceptionOrNull())
        assertTrue(owner.issued.all { it.all { b -> b == 0.toByte() } })
    }

    @Test fun changeNotificationsRemainDisposableWithoutReadingPrivateBytes() {
        val owner = FakeOwner { raw.toByteArray(Charsets.UTF_8) }
        val port = ExactCurrentSnapshotProjectionPort(owner, FakeProjector(active))
        var count=0
        val handle=port.subscribeCommittedChanges { count++ }
        owner.change()
        handle.close()
        owner.change()
        assertEquals(1,count)
        assertEquals(1,owner.unsubscribed)
        assertTrue(owner.issued.isEmpty())
    }
}
