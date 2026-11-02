package io.github.extracteddeath.pursuitiron.domain

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

/** A new document selected through Save As, never an existing import source. */
interface NativeBackupExportDocument {
    fun openOutput(): OutputStream
    fun openInput(): InputStream
    fun discard()
}

data class NativeBackupExportReceipt(val byteCount: Long,val snapshotSha256: String)

/** Exact owned v13 bytes, canonical structural validation and destination readback. No store writes. */
class NativeBackupExporter(private val owner: CanonicalCurrentSnapshotOwner,
    private val engine: PinnedCanonicalWorkoutStoreEngine) {
    suspend fun export(document: NativeBackupExportDocument,checkActive: ()->Unit={}): NativeBackupExportReceipt {
        var source: ByteArray?=null;var saved=false
        try {
            checkActive()
            val bytes=checkNotNull(owner.readOwnedCurrentStoreBytes());source=bytes
            require(bytes.size in 1..ReadOnlyBackupLimits.MAX_SOURCE_BYTES)
            val fingerprint=ReadOnlyBackupScreening.fingerprint(ByteArrayInputStream(bytes))
            val text=ReadOnlyBackupSourceGate.exactText(bytes,fingerprint)
            check(engine.validateInitialSnapshot(text))
            checkActive();checkCurrent(bytes)
            document.openOutput().use { output ->
                var offset=0
                while(offset<bytes.size) {
                    checkActive();val count=minOf(8192,bytes.size-offset)
                    output.write(bytes,offset,count);offset+=count
                }
                output.flush()
            }
            checkActive()
            document.openInput().use { input ->
                val buffer=ByteArray(8192);var offset=0
                try {
                    while(true) {
                        checkActive();val count=input.read(buffer)
                        if(count==-1)break
                        require(count>0 && count<=bytes.size-offset)
                        for(i in 0 until count)require(buffer[i]==bytes[offset+i])
                        offset+=count
                    }
                    require(offset==bytes.size)
                } finally { buffer.fill(0) }
            }
            checkActive();checkCurrent(bytes);checkActive()
            val receipt=NativeBackupExportReceipt(fingerprint.byteCount,fingerprint.sha256Hex)
            saved=true;return receipt
        } finally {
            source?.fill(0)
            if(!saved)runCatching { document.discard() }
        }
    }
    private suspend fun checkCurrent(source: ByteArray) {
        val latest=checkNotNull(owner.readOwnedCurrentStoreBytes())
        try { check(source.contentEquals(latest)) }
        finally { latest.fill(0) }
    }
}
