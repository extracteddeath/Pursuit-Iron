package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

class NativeCustomSavedCorrectionCodecTest {
    private fun n(s:String)=BigDecimal(s)
    private fun receipt(index:Int,machine:String?=null)=
        CustomSavedVisitCorrectionReceipt(index.toLong(),
            CustomSavedVisitCorrection(
                "physical-correction-$index",(index-1).toLong(),
                CustomWorkoutVisitKey(
                    "cycle-1:w2:d0:extension-1:s1",CustomWorkoutVisitPart.MAIN),
                "previous-observation-$index",
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("55"),LoadUnit.LB,
                    12,2,machine)))
    private fun envelope(vararg receipts:CustomSavedVisitCorrectionReceipt)=
        NativeCustomSavedCorrectionEnvelope(
            "owner-1","machine-visit",
            "custom-v2-"+ "a".repeat(64),
            receipts.size.toLong(),receipts.toList())

    @Test fun oldUntaggedCorrectionsRemainExactlyV1AfterColdReplay() {
        val baseline=envelope(receipt(1))
        val saved=NativeCustomSavedCorrectionCodec.encode(baseline)
        // Frame 4 bytes + PICS magic 4 bytes + version 4 bytes.
        assertEquals(1,saved[11].toInt())
        val cold=NativeCustomSavedCorrectionCodec.decode(saved)
        assertEquals(baseline,cold)
        assertNull(cold.receipts.single().correction.performed?.machineInstanceId)
        assertArrayEquals(saved,NativeCustomSavedCorrectionCodec.encode(cold))
    }

    @Test fun physicalMachineTagSurvivesMixedCorrectionHistoryAndColdReplay() {
        val mixed=envelope(receipt(1),receipt(2,"machine-B"))
        val saved=NativeCustomSavedCorrectionCodec.encode(mixed)
        assertEquals(2,saved[11].toInt())
        val cold=NativeCustomSavedCorrectionCodec.decode(saved)
        assertEquals(mixed,cold)
        assertNull(cold.receipts.first().correction.performed?.machineInstanceId)
        assertEquals("machine-B",
            cold.receipts.last().correction.performed?.machineInstanceId)
        assertArrayEquals(saved,NativeCustomSavedCorrectionCodec.encode(cold))
        assertThrows(IOException::class.java){
            val broken=saved.clone()
            broken[broken.lastIndex]=(broken.last().toInt() xor 0x01).toByte()
            NativeCustomSavedCorrectionCodec.decode(broken)
        }
        assertThrows(IOException::class.java){
            NativeCustomSavedCorrectionCodec.decode(saved.copyOf(saved.size-1))
        }
    }

    @Test fun invalidStationIdIsRejectedWithoutWritingAmbiguousCorrection() {
        assertThrows(IllegalArgumentException::class.java) {
            NativeCustomSavedCorrectionCodec.encode(
                envelope(receipt(1,"bad machine id")))
        }
        val tagged=envelope(receipt(1,"A"))
        val bytes=NativeCustomSavedCorrectionCodec.encode(tagged)
        assertEquals(tagged,NativeCustomSavedCorrectionCodec.decode(bytes))
    }
}
