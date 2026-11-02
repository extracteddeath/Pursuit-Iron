package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.concurrent.CancellationException
import kotlin.coroutines.*

class NativeBackupExportTest {
    private fun <T> immediate(block: suspend ()->T): T {
        var result: Result<T>?=null
        block.startCoroutine(object: Continuation<T> {
            override val context=EmptyCoroutineContext
            override fun resumeWith(outcome: Result<T>) { result=outcome }
        })
        return checkNotNull(result).getOrThrow()
    }
    private class Rig {
        var source="{\"v\":13,\"note\":\"Café 💪\"}".toByteArray();var valid=true
        val copies=ArrayList<ByteArray>();var validations=0;var onValidate: ()->Unit={}
        val owner=object: CanonicalCurrentSnapshotOwner {
            override suspend fun readOwnedCurrentStoreBytes()=source.copyOf().also { copies+=it }
            override fun subscribeCommittedChanges(onChanged: ()->Unit)=AutoCloseable {}
        }
        val engine=object: PinnedCanonicalWorkoutStoreEngine {
            override suspend fun validateInitialSnapshot(exactSnapshot: String): Boolean {
                validations++;assertEquals(String(source,Charsets.UTF_8),exactSnapshot);onValidate();return valid
            }
            override suspend fun prepareCompletion(origin: String,current: String,finishedJournal: String): String?=
                throw AssertionError("Export cannot prepare history or write the store")
        }
        val exporter=NativeBackupExporter(owner,engine)
    }
    private class Document: NativeBackupExportDocument {
        val bytes=ByteArrayOutputStream();var opened=0;var discarded=0;var closedOutput=false;var closedInput=false
        var failWrite=false;var failFlush=false;var failClose=false;var failRead=false;var failDelete=false;var zeroRead=false
        var transform: (ByteArray)->ByteArray={it};var onRead: ()->Unit={};val buffers=ArrayList<ByteArray>()
        override fun openOutput(): OutputStream {
            opened++
            return object: OutputStream() {
                override fun write(value: Int) { if(failWrite)throw IOException("write");bytes.write(value) }
                override fun write(value: ByteArray,offset: Int,count: Int) {
                    if(failWrite)throw IOException("write");bytes.write(value,offset,count)
                }
                override fun flush() { if(failFlush)throw IOException("flush") }
                override fun close() { closedOutput=true;if(failClose)throw IOException("close") }
            }
        }
        override fun openInput(): InputStream {
            val input=ByteArrayInputStream(transform(bytes.toByteArray()))
            return object: InputStream() {
                override fun read(): Int=input.read()
                override fun read(value: ByteArray,offset: Int,count: Int): Int {
                    buffers+=value;onRead()
                    if(failRead)throw IOException("read")
                    return if(zeroRead)0 else input.read(value,offset,minOf(17,count))
                }
                override fun close() { closedInput=true;input.close() }
            }
        }
        override fun discard() { discarded++;if(failDelete)throw IOException("delete");bytes.reset() }
    }
    @Test fun validatedExportPreservesExactUnicodeBytesAndWipesAllDetachedReadBuffers() {
        val rig=Rig();rig.source=("{\"note\":\""+"Café 💪".repeat(4000)+"\"}").toByteArray()
        val original=rig.source.copyOf();val doc=Document()
        val receipt=immediate { rig.exporter.export(doc) }
        assertArrayEquals(original,doc.bytes.toByteArray());assertArrayEquals(original,rig.source)
        assertEquals(original.size.toLong(),receipt.byteCount)
        assertEquals(WorkoutStoreRuntimeCodec.sha(String(original,Charsets.UTF_8)),receipt.snapshotSha256)
        assertEquals(1,rig.validations);assertEquals(0,doc.discarded);assertTrue(doc.closedOutput && doc.closedInput)
        assertTrue((rig.copies+doc.buffers).all { bytes->bytes.all { it==0.toByte() } })
    }
    @Test fun invalidOrRejectedSourcesNeverOpenADestinationAndStillDiscardTheNewEmptyDocument() {
        for(source in listOf(byteArrayOf(),byteArrayOf(0xc3.toByte(),0x28),"\uFEFF{}".toByteArray(),
            ByteArray(ReadOnlyBackupLimits.MAX_SOURCE_BYTES+1))) {
            val rig=Rig();rig.source=source;val doc=Document()
            assertTrue(runCatching { immediate { rig.exporter.export(doc) } }.isFailure)
            assertEquals(0,doc.opened);assertEquals(1,doc.discarded)
            assertTrue(rig.copies.all { bytes->bytes.all { it==0.toByte() } })
        }
        val rig=Rig();rig.valid=false;val doc=Document()
        assertTrue(runCatching { immediate { rig.exporter.export(doc) } }.isFailure)
        assertEquals(0,doc.opened);assertEquals(1,doc.discarded)
    }
    @Test fun changesDuringCanonicalValidationOrDestinationReadbackCannotAcknowledgeAnOldBackup() {
        for(duringValidation in listOf(true,false)) {
            val rig=Rig();val doc=Document();val changed={rig.source=rig.source+byteArrayOf(32)}
            if(duringValidation)rig.onValidate=changed else doc.onRead=changed
            assertTrue(runCatching { immediate { rig.exporter.export(doc) } }.isFailure)
            assertEquals(if(duringValidation)0 else 1,doc.opened);assertEquals(1,doc.discarded)
            assertTrue((rig.copies+doc.buffers).all { bytes->bytes.all { it==0.toByte() } })
        }
    }
    @Test fun writeCloseReadAndCorruptProviderFailuresNeverReturnSuccessOrAlterTheOwnedSource() {
        val cases=listOf<(Document)->Unit>(
            {it.failWrite=true},{it.failFlush=true},{it.failClose=true},{it.failRead=true},{it.zeroRead=true},
            {it.transform={bytes->bytes.dropLast(1).toByteArray()}},
            {it.transform={bytes->bytes+byteArrayOf(32)}},
            {it.transform={bytes->bytes.copyOf().apply { this[0]=0 }}})
        for(configure in cases) {
            val rig=Rig();val original=rig.source.copyOf();val doc=Document();configure(doc)
            assertTrue(runCatching { immediate { rig.exporter.export(doc) } }.isFailure)
            assertEquals(1,doc.discarded);assertTrue(doc.closedOutput);assertArrayEquals(original,rig.source)
            assertTrue((rig.copies+doc.buffers).all { bytes->bytes.all { it==0.toByte() } })
        }
    }
    @Test fun cancellationClosesStreamsDiscardsTheCopyAndWipesReadBuffersEvenIfCleanupFails() {
        val rig=Rig();val original=rig.source.copyOf();val doc=Document();var cancel=false
        doc.onRead={cancel=true};doc.failDelete=true
        assertThrows(CancellationException::class.java) {
            immediate { rig.exporter.export(doc) { if(cancel)throw CancellationException("cancel") } }
        }
        assertTrue(doc.closedOutput && doc.closedInput);assertEquals(1,doc.discarded);assertArrayEquals(original,rig.source)
        assertTrue((rig.copies+doc.buffers).all { bytes->bytes.all { it==0.toByte() } })
    }
}
