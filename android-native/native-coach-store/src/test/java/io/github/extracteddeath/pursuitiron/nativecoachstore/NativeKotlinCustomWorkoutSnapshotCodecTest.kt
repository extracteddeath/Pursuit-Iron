package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.IOException
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinCustomWorkoutSnapshotCodecTest {
    private fun weight(value:String)=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,BigDecimal(value))
    private fun set(
        slot:String,ordinal:Int,type:DraftSetType,load:DraftSetLoad,
        amplifier:CustomSetAmplifier?=null
    )=CustomWorkoutSetSnapshot(
        "cycle-1:w2:d0:$slot:s$ordinal",slot,ordinal,
        CustomAuthoredSet(type,load,RepBand(8,12),2,55,90,amplifier)
    )
    private fun fixture():CustomWorkoutDaySnapshot {
        val first=CustomWorkoutSlotSnapshot("raise-1","db_lateral_raise",
            "lateral_raise","dumbbell",ComposerRole.ACCESSORY,null,
            "super-1",true,"Controlled eccentric",
            listOf(set("raise-1",1,DraftSetType.WARMUP,weight("10")),
                set("raise-1",2,DraftSetType.WORKING,weight("20"),
                    CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,15,
                        weight("15")))))
        val second=CustomWorkoutSlotSnapshot("curl-2","db_curl",
            "elbow_flexion","supinated-dumbbell",
            ComposerRole.ACCESSORY,null,"super-1",false,
            "No momentum",
            listOf(set("curl-2",1,DraftSetType.WORKING,weight("20"),
                CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20))))
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","profile-3",
                "cycle-1",7,"gym-1",4),
            2,2,"block-1",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("Upper B"),
            0,"Upper A",ComposerSessionPurpose.HYPERTROPHY,
            listOf(first,second),3L*145+15+20,240
        )
    }
    @Test fun exactCustomSessionRoundTripsEverySetAndProvenanceField() {
        val first=fixture()
        val bytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(first)
        val second=NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes)
        assertEquals(first,second)
        assertEquals(bytes.toList(),
            NativeKotlinCustomWorkoutSnapshotCodec.encode(second).toList())
        assertEquals(listOf("raise-1","curl-2"),second.slots.map{it.slotId})
        assertEquals(weight("15"),second.slots.first().sets.last()
            .prescription.amplifier?.followUpLoad)
        assertEquals(CustomAmplifierKind.MYO_REPS,second.slots.last()
            .sets.single().prescription.amplifier?.kind)
        assertFalse(second.isIssued)
        assertFalse(second.mayRecordSets)
        assertFalse(second.mayFinishWorkout)
    }
    @Test fun invalidSetOrdinalAndDurationAreNeverEncodedAsGoodSnapshots() {
        val baseline=fixture()
        val wrongOrdinal=baseline.copy(slots=listOf(
            baseline.slots.first().copy(sets=listOf(
                baseline.slots.first().sets.first().copy(ordinalInSlot=5),
                baseline.slots.first().sets.last())),
            baseline.slots.last()))
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(wrongOrdinal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(
                baseline.copy(knownSetAndRestSeconds=1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(
                baseline.copy(slots=baseline.slots.reversed().map{
                    it.copy(supersetGroup="lonely-"+it.slotId)
                }))
        }
    }
    @Test fun decoderRejectsMutationTruncationAndUnknownSchema() {
        val bytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(fixture())
        assertThrows(IOException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.decode(
                bytes.copyOf(bytes.size-1))
        }
        assertThrows(IOException::class.java) {
            val changed=bytes.clone()
            changed[7]=99
            NativeKotlinCustomWorkoutSnapshotCodec.decode(changed)
        }
        assertThrows(IOException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.decode(
                bytes + byteArrayOf(0))
        }
    }
}
