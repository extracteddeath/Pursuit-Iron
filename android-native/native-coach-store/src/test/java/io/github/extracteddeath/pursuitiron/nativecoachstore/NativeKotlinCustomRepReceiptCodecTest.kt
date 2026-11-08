package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.math.BigDecimal
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Test

/** Golden v1 bytes protect existing accepted athlete consent. */
class NativeKotlinCustomRepReceiptCodecTest {
    private fun receipt(
        policy:CustomRepForecastPolicy=CustomRepForecastPolicy.LEGACY_STEP_V1
    )=NativeCustomNextRepReceipt(
        "owner-1","athlete-1","workout-3","cycle-1","block-1",
        5,0,"slot-1",3,4,"a".repeat(64),listOf("set-a","set-b"),
        listOf(8,9),CoachReadiness.NORMAL,false,BigDecimal("5"),policy)

    private fun hash(data:ByteArray)=MessageDigest.getInstance("SHA-256")
        .digest(data).joinToString(""){"%02x".format(it.toInt() and 255)}

    private fun readBody(bytes:ByteArray):DataInputStream {
        val inp=DataInputStream(ByteArrayInputStream(bytes))
        val n=inp.readInt()
        assertEquals(bytes.size-36,n)
        val body=ByteArray(n)
        inp.readFully(body)
        val checksum=ByteArray(32)
        inp.readFully(checksum)
        assertArrayEquals(MessageDigest.getInstance("SHA-256").digest(body),checksum)
        assertEquals(0,inp.available())
        return DataInputStream(ByteArrayInputStream(body))
    }

    private fun readCommon(inp:DataInputStream) {
        assertEquals(0x50495241,inp.readInt())
        // Version must be read by the caller after magic.
    }

    @Test fun legacyV1ReceiptIsByteForByteStable() {
        val receipt=receipt()
        val bytes=NativeKotlinCustomNextRepApprovalFileStore.encode(receipt)
        // Independently frozen canonical DataOutputStream v1 envelope.
        assertEquals(229,bytes.size)
        assertEquals(
            "f44dc7ab715abaa93babe02013e1213579638d9f67a7bb9249c8d9b07f914413",
            hash(bytes))
        val inp=readBody(bytes)
        readCommon(inp)
        assertEquals(1,inp.readInt())
        assertEquals("owner-1",inp.readUTF())
        assertEquals("athlete-1",inp.readUTF())
        assertEquals("workout-3",inp.readUTF())
        assertEquals("cycle-1",inp.readUTF())
        assertEquals("block-1",inp.readUTF())
        assertEquals("slot-1",inp.readUTF())
        assertEquals("a".repeat(64),inp.readUTF())
        assertEquals(5,inp.readInt())
        assertEquals(0,inp.readInt())
        assertEquals(3L,inp.readLong())
        assertEquals(4L,inp.readLong())
        assertEquals("NORMAL",inp.readUTF())
        assertFalse(inp.readBoolean())
        assertEquals("5",inp.readUTF())
        assertEquals(2,inp.readInt())
        assertEquals("set-a",inp.readUTF())
        assertEquals(8,inp.readInt())
        assertEquals("set-b",inp.readUTF())
        assertEquals(9,inp.readInt())
        assertEquals(0,inp.available())
        assertEquals(CustomRepForecastPolicy.LEGACY_STEP_V1,
            receipt.repForecastPolicy)
        assertEquals(receipt,NativeKotlinCustomNextRepApprovalFileStore.decodeCanonical(
            "owner-1","workout-3",bytes))
    }

    @Test fun v2ReceiptRecordsSignedForecastPolicyWithoutChangingV1() {
        val v2=NativeKotlinCustomNextRepApprovalFileStore.encode(
            receipt(CustomRepForecastPolicy.TARGET_RIR_V2))
        val v1=NativeKotlinCustomNextRepApprovalFileStore.encode(receipt())
        assertFalse(v1.contentEquals(v2))
        val inp=readBody(v2)
        readCommon(inp)
        assertEquals(2,inp.readInt())
        repeat(7){inp.readUTF()}
        inp.readInt();inp.readInt()
        inp.readLong();inp.readLong()
        inp.readUTF();inp.readBoolean();inp.readUTF()
        assertEquals(2,inp.readInt())
        repeat(2){inp.readUTF();inp.readInt()}
        assertEquals("TARGET_RIR_V2",inp.readUTF())
        assertEquals(0,inp.available())
        assertEquals(
            "f44dc7ab715abaa93babe02013e1213579638d9f67a7bb9249c8d9b07f914413",
            hash(v1))
        assertEquals(receipt(CustomRepForecastPolicy.TARGET_RIR_V2),
            NativeKotlinCustomNextRepApprovalFileStore.decodeCanonical(
                "owner-1","workout-3",v2))
    }

    @Test fun canonicalReaderRejectsCorruptionWrongOwnerAndUnknownPolicy() {
        val bytes=NativeKotlinCustomNextRepApprovalFileStore.encode(
            receipt(CustomRepForecastPolicy.TARGET_RIR_V2))
        assertThrows(IOException::class.java) {
            NativeKotlinCustomNextRepApprovalFileStore.decodeCanonical(
                "different-owner","workout-3",bytes)
        }
        assertThrows(IOException::class.java) {
            NativeKotlinCustomNextRepApprovalFileStore.decodeCanonical(
                "owner-1","workout-3",bytes.copyOf(bytes.size-1))
        }
        val body=bytes.copyOfRange(4,bytes.size-32)
        // The final UTF field is the V2 forecast enum. Recompute the
        // checksum so rejection proves schema validation, not just hashing.
        body[body.lastIndex]='X'.code.toByte()
        val invalid=bytes.copyOf()
        System.arraycopy(body,0,invalid,4,body.size)
        val digest=MessageDigest.getInstance("SHA-256").digest(body)
        System.arraycopy(digest,0,invalid,invalid.size-32,digest.size)
        assertThrows(IOException::class.java) {
            NativeKotlinCustomNextRepApprovalFileStore.decodeCanonical(
                "owner-1","workout-3",invalid)
        }
    }

    @Test fun impossibleOrModifiedReceiptNeverSerializes() {
        val illegal=receipt().copy(nextMinimumReps=listOf(102,9))
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomNextRepApprovalFileStore.encode(illegal)
        }
        val fabricated=receipt().copy(fingerprint="not-a-valid-hash")
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomNextRepApprovalFileStore.encode(fabricated)
        }
    }
}
