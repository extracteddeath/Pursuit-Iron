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

    @Test fun negativeScaleCorrectionRetainsExactRedoIdentityWithoutChangingEarlierFormats() {
        val first=receipt(1)
        val second=receipt(2,"A")
        val earlier=receipt(3,"A")
        val staged=earlier.copy(correction=earlier.correction.copy(
            visit=earlier.correction.visit.copy(part=CustomWorkoutVisitPart.FOLLOW_UP,stage=3)))
        val original=envelope(first,second,staged)
        val originalBytes=NativeCustomSavedCorrectionCodec.encode(original)
        assertEquals(3,originalBytes[11].toInt())
        val fourth=receipt(4,"A")
        val exact=fourth.copy(correction=fourth.correction.copy(
            visit=staged.correction.visit,
            performed=fourth.correction.performed!!.copy(load=n("2E+2"))))
        val revised=envelope(first,second,staged,exact)
        val wire=NativeCustomSavedCorrectionCodec.encode(revised)
        assertEquals(4,wire[11].toInt())
        val cold=NativeCustomSavedCorrectionCodec.decode(wire)
        assertEquals(revised,cold)
        assertEquals(-2,cold.receipts.last().correction.performed!!.load!!.scale())
        assertArrayEquals(wire,NativeCustomSavedCorrectionCodec.encode(cold))
        assertArrayEquals(originalBytes,NativeCustomSavedCorrectionCodec.encode(
            NativeCustomSavedCorrectionCodec.decode(originalBytes)))
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
    @Test fun stageThreeCorrectionRoundTripsV3WithoutReencodingV1OrV2() {
        val v1=envelope(receipt(1))
        val v2=envelope(receipt(1),receipt(2,"A"))
        val old1=NativeCustomSavedCorrectionCodec.encode(v1)
        val old2=NativeCustomSavedCorrectionCodec.encode(v2)
        val third=CustomSavedVisitCorrectionReceipt(3L,
            CustomSavedVisitCorrection("myo-stage-3",2L,
                CustomWorkoutVisitKey("cycle-1:w2:d0:extension-1:s1",
                    CustomWorkoutVisitPart.FOLLOW_UP,3),
                "myo-stage-3-original",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("55"),LoadUnit.LB,3,2,"A")))
        val staged=envelope(receipt(1),receipt(2,"A"),third)
        val wire=NativeCustomSavedCorrectionCodec.encode(staged)
        assertEquals(3,wire[11].toInt())
        val cold=NativeCustomSavedCorrectionCodec.decode(wire)
        assertEquals(staged,cold)
        assertEquals(3,cold.receipts.last().correction.visit.stage)
        assertArrayEquals(wire,NativeCustomSavedCorrectionCodec.encode(cold))
        assertArrayEquals(old1,NativeCustomSavedCorrectionCodec.encode(
            NativeCustomSavedCorrectionCodec.decode(old1)))
        assertArrayEquals(old2,NativeCustomSavedCorrectionCodec.encode(
            NativeCustomSavedCorrectionCodec.decode(old2)))
        assertThrows(IOException::class.java) {
            NativeCustomSavedCorrectionCodec.decode(wire.copyOf(wire.size-1))
        }
    }

}
