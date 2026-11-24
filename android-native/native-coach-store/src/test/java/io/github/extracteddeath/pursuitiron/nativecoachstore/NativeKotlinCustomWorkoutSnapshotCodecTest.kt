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
    @Test fun percentageMetadataSurvivesFullAndSwapSnapshotWithoutOrdinaryVersionDrift() {
        val original=fixture()
        val ordinary=NativeKotlinCustomWorkoutSnapshotCodec.encode(original)
        assertEquals(8,ordinary[7].toInt())
        val slot=original.slots.first()
        val last=slot.sets.last()
        val intent=CustomPercentageSetIntent(NamedPercentageScheme.FIVE_THREE_ONE,
            PercentageTier.PRIMARY,PercentageDayRole.STANDARD,2,4,3,3,
            BigDecimal("25.000"),BigDecimal("90"),BigDecimal("90.00"),null,true,false)
        val protocol=last.prescription.copy(reps=RepBand(3,3),exactRepTarget=3,
            amplifier=null,percentageIntent=intent)
        val selected=slot.copy(sets=slot.sets.dropLast(1)+last.copy(prescription=protocol))
        val snapshot=original.copy(slots=listOf(selected,original.slots.last()),
            knownSetAndRestSeconds=original.knownSetAndRestSeconds-15)
        val bytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(snapshot)
        assertEquals(9,bytes[7].toInt())
        assertEquals(snapshot,NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes))
        assertArrayEquals(bytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes)))
        val swap=NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(selected)
        assertEquals(9,swap[7].toInt())
        assertEquals(selected,NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(swap))
        assertFalse(snapshot.isIssued)
        assertArrayEquals(ordinary,NativeKotlinCustomWorkoutSnapshotCodec.encode(original))
        for(invalid in listOf(protocol.copy(percentageIntent=intent.copy(recovery=true)),
            protocol.copy(percentageIntent=intent.copy(weekInBlock=1)),
            protocol.copy(percentageIntent=intent.copy(sourceOrdinal=4)),
            protocol.copy(exactRepTarget=4),
            protocol.copy(load=weight("25")))) {
            val bad=selected.copy(sets=selected.sets.dropLast(1)+last.copy(prescription=invalid))
            assertThrows(IllegalArgumentException::class.java){
                NativeKotlinCustomWorkoutSnapshotCodec.encode(snapshot.copy(
                    slots=listOf(bad,original.slots.last())))
            }
        }
    }

    @Test fun exactTargetsSurviveFullAndSwapSnapshotsAndRejectOutsideBand() {
        val original=fixture()
        val slot=original.slots.first()
        val working=slot.sets.last()
        val selected=slot.copy(sets=slot.sets.dropLast(1)+working.copy(
            prescription=working.prescription.copy(exactRepTarget=10)))
        val schedule=original.copy(slots=listOf(selected,original.slots.last()))
        val reopened=NativeKotlinCustomWorkoutSnapshotCodec.decode(
            NativeKotlinCustomWorkoutSnapshotCodec.encode(schedule))
        assertEquals(schedule,reopened)
        assertEquals(10,reopened.slots.first().sets.last()
            .prescription.exactRepTarget)
        assertEquals(selected,NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(
            NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(selected)))
        val invalid=schedule.copy(slots=listOf(selected.copy(sets=
            selected.sets.dropLast(1)+working.copy(prescription=
                working.prescription.copy(exactRepTarget=13))),original.slots.last()))
        assertThrows(IllegalArgumentException::class.java){
            NativeKotlinCustomWorkoutSnapshotCodec.encode(invalid)
        }
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
    @Test fun reducedSnapshotRoundTripsWithItsOmissionList() {
        val old=fixture()
        val base=NativeKotlinCustomWorkoutSnapshotCodec.encode(old)
        assertEquals(8,base[7].toInt())
        val safe=old.copy(slots=old.slots.map{slot->
            slot.copy(supersetGroup=null,sets=slot.sets.map {set->
                set.copy(prescription=set.prescription.copy(amplifier=null))
            })
        },knownSetAndRestSeconds=3L*145)
        val shortened=safe.copy(
            slots=listOf(safe.slots.first()),
            knownSetAndRestSeconds=290,
            shortSessionLimitMinutes=20,
            shortSessionOmittedSetKeys=listOf(safe.slots.last().sets.single().setKey))
        val encoded=NativeKotlinCustomWorkoutSnapshotCodec.encode(shortened)
        assertEquals(8,encoded[7].toInt())
        assertEquals(shortened,
            NativeKotlinCustomWorkoutSnapshotCodec.decode(encoded))
        assertArrayEquals(encoded,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(encoded)))
        assertArrayEquals(base,NativeKotlinCustomWorkoutSnapshotCodec.encode(old))
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(shortened.copy(
                shortSessionLimitMinutes=null))
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(shortened.copy(
                shortSessionOmittedSetKeys=shortened.slots.first().sets
                    .map{it.setKey}))
        }
        assertThrows(IOException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.decode(
                encoded.copyOf(encoded.size-1))
        }
    }

    @Test fun timedAmplifierPersistsThroughFullAndReducedSnapshotsAndSwap() {
        val old=fixture()
        val base=NativeKotlinCustomWorkoutSnapshotCodec.encode(old)
        assertEquals(8,base[7].toInt())
        val timed=old.copy(slots=old.slots.map{slot->
            slot.copy(sets=slot.sets.map{set->
                val amp=set.prescription.amplifier
                if(amp==null)set else set.copy(
                    prescription=set.prescription.copy(
                        amplifier=amp.copy(followUpActiveSeconds=45)))
            })
        },knownSetAndRestSeconds=old.knownSetAndRestSeconds+90)
        val bytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(timed)
        assertEquals(8,bytes[7].toInt())
        assertEquals(timed,NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes))
        assertArrayEquals(bytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes)))
        assertArrayEquals(base,NativeKotlinCustomWorkoutSnapshotCodec.encode(old))
        val withSelection=timed.copy(shortSessionLimitMinutes=30)
        val selected=NativeKotlinCustomWorkoutSnapshotCodec.encode(withSelection)
        assertEquals(8,selected[7].toInt())
        assertEquals(withSelection,
            NativeKotlinCustomWorkoutSnapshotCodec.decode(selected))
        val timedSlot=timed.slots.first()
        val framed=NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(timedSlot)
        assertEquals(timedSlot,
            NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(framed))
        val oldSlot=old.slots.first()
        val plain=NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(oldSlot)
        assertEquals(oldSlot,NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(plain))
        for (unsupported in 1..6) {
            assertThrows(IOException::class.java) {
                NativeKotlinCustomWorkoutSnapshotCodec.decode(
                    base.copyOf().apply { this[7] = unsupported.toByte() })
            }
            assertThrows(IOException::class.java) {
                NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(
                    plain.copyOf().apply { this[7] = unsupported.toByte() })
            }
        }
        assertThrows(IOException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(plain.copyOfRange(8, plain.size))
        }
        assertThrows(java.io.IOException::class.java){
            NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(
                framed.copyOf(framed.size-1))
        }
        assertThrows(java.io.IOException::class.java){
            NativeKotlinCustomWorkoutSnapshotCodec.decode(
                bytes.copyOf(bytes.size-1))
        }
        assertThrows(IllegalArgumentException::class.java){
            NativeKotlinCustomWorkoutSnapshotCodec.encode(timed.copy(
                knownSetAndRestSeconds=old.knownSetAndRestSeconds))
        }
        assertThrows(IllegalArgumentException::class.java){
            NativeKotlinCustomWorkoutSnapshotCodec.encode(timed.copy(
                slots=timed.slots.map{slot->slot.copy(sets=slot.sets.map{set->
                    val amp=set.prescription.amplifier
                    if(amp==null)set else set.copy(
                        prescription=set.prescription.copy(
                            amplifier=amp.copy(followUpActiveSeconds=0)))
                })},knownSetAndRestSeconds=old.knownSetAndRestSeconds))
        }
    }
    @Test fun myoFiveRoundSnapshotRoundTripsDeterministically() {
        val previous=fixture()
        val base=NativeKotlinCustomWorkoutSnapshotCodec.encode(previous)
        val upgraded=previous.copy(
            slots=previous.slots.map{slot -> slot.copy(sets=slot.sets.map{set ->
                val amp=set.prescription.amplifier
                if(amp?.kind!=CustomAmplifierKind.MYO_REPS)set
                else set.copy(prescription=set.prescription.copy(
                    amplifier=amp.copy(followUpRounds=5)))
            })},
            knownSetAndRestSeconds=previous.knownSetAndRestSeconds+80L)
        val wire=NativeKotlinCustomWorkoutSnapshotCodec.encode(upgraded)
        assertEquals(8,java.nio.ByteBuffer.wrap(wire,4,4).int)
        assertEquals(upgraded,NativeKotlinCustomWorkoutSnapshotCodec.decode(wire))
        val selected=upgraded.slots.last()
        val payload=NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(selected)
        assertEquals(selected,NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(payload))
        assertArrayEquals(base,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(base)))
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(upgraded.copy(
                knownSetAndRestSeconds=previous.knownSetAndRestSeconds))
        }
    }

    @Test fun stagedRestPauseAndClustersRoundTripFullSnapshotsAndSwapsWithoutVersionDrift() {
        val base=fixture()
        val baseBytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(base)
        for(new in listOf(
            CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,3,20,
                followUpActiveSeconds=60,followUpRounds=3),
            CustomSetAmplifier(CustomAmplifierKind.CLUSTER,2,30,
                followUpActiveSeconds=60,followUpRounds=4))) {
            val method=new.kind
            val restSeconds=new.followUpRestSeconds
            val planned=new.followUpRounds
            val old=base.slots.last().sets.single().prescription.amplifier!!
            val staged=base.copy(slots=base.slots.map{slot->
                slot.copy(sets=slot.sets.map{set->
                    if(set.prescription.amplifier?.kind!=CustomAmplifierKind.MYO_REPS)set
                    else set.copy(prescription=set.prescription.copy(amplifier=new))
                })
            },knownSetAndRestSeconds=base.knownSetAndRestSeconds-
                old.followUpRestSeconds*old.followUpRounds+
                restSeconds*planned+60)
            val bytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(staged)
            assertEquals(8,java.nio.ByteBuffer.wrap(bytes,4,4).int)
            assertEquals(staged,NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes))
            assertArrayEquals(bytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
                NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes)))
            val slot=staged.slots.last()
            val swapped=NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(slot)
            assertEquals(slot,NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(swapped))
            val invalid=new.copy(followUpRounds=if(method==CustomAmplifierKind.REST_PAUSE)4 else 6)
            val invalidSnapshot=staged.copy(
                slots=staged.slots.map{row->row.copy(sets=row.sets.map{set->
                    if(set.prescription.amplifier==new)
                        set.copy(prescription=set.prescription.copy(amplifier=invalid))
                    else set
                })},
                knownSetAndRestSeconds=staged.knownSetAndRestSeconds-
                    restSeconds*planned+restSeconds*invalid.followUpRounds)
            assertThrows(IllegalArgumentException::class.java) {
                NativeKotlinCustomWorkoutSnapshotCodec.encode(invalidSnapshot)
            }
            assertThrows(IOException::class.java) {
                NativeKotlinCustomWorkoutSnapshotCodec.decode(
                    bytes.copyOf(bytes.size-1))
            }
        }
        assertArrayEquals(baseBytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(baseBytes)))
    }

    @Test fun finalWorkingSetPartialsEncodeIndependentlyAndBadPlacementFailsClosed() {
        val before=fixture()
        val oldBytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(before)
        val partial=CustomSetAmplifier(CustomAmplifierKind.LENGTHENED_PARTIALS,
            3,0,followUpActiveSeconds=15)
        // Final working set belongs to curl-2; earlier warmup and other
        // exercise drop set remain completely unchanged.
        val updated=before.copy(
            slots=before.slots.map{slot->
                if(slot.slotId!="curl-2")slot
                else slot.copy(sets=slot.sets.map{set->
                    set.copy(prescription=set.prescription.copy(amplifier=partial))
                })
            },knownSetAndRestSeconds=before.knownSetAndRestSeconds-20+15)
        val bytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(updated)
        assertEquals(8,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        assertEquals(updated,NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes))
        assertArrayEquals(bytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes)))
        val slot=updated.slots.last()
        val framed=NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(slot)
        assertEquals(slot,NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(framed))
        val notFinal=updated.copy(
            slots=updated.slots.map{row->
                if(row.slotId!="raise-1")row
                else row.copy(sets=row.sets.mapIndexed{index,set->
                    if(index==0)set else set.copy(prescription=
                        set.prescription.copy(amplifier=null))
                }.mapIndexed{index,set->
                    if(index==0)set.copy(prescription=set.prescription.copy(
                        type=DraftSetType.WORKING,amplifier=partial))
                    else set
                })
            },knownSetAndRestSeconds=updated.knownSetAndRestSeconds+15-15)
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(notFinal)
        }
        val invalidProtocol=updated.copy(
            slots=updated.slots.map{row->row.copy(sets=row.sets.map{set->
                if(set.prescription.amplifier!=partial)set
                else set.copy(prescription=set.prescription.copy(
                    amplifier=partial.copy(followUpRounds=2)))
            })},knownSetAndRestSeconds=updated.knownSetAndRestSeconds)
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(invalidProtocol)
        }
        assertArrayEquals(oldBytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(oldBytes)))
    }

    @Test fun twoDropSnapshotAndSwapRoundTripEachAuthoredWeight() {
        val old=fixture()
        val oldBytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(old)
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=weight("15"),followUpRounds=2,
            secondDropLoad=weight("10"))
        val updated=old.copy(slots=old.slots.map{slot->
            if(slot.slotId!="raise-1")slot
            else slot.copy(sets=slot.sets.map{set->
                if(set.prescription.amplifier?.kind!=CustomAmplifierKind.DROP_SET)
                    set
                else set.copy(prescription=set.prescription.copy(amplifier=amp))
            })
        },knownSetAndRestSeconds=old.knownSetAndRestSeconds+5L)
        val bytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(updated)
        assertEquals(8,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        assertEquals(updated,NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes))
        assertArrayEquals(bytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(bytes)))
        val selected=updated.slots.first()
        val slotWire=NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(selected)
        assertEquals(8,java.nio.ByteBuffer.wrap(slotWire,4,4).int)
        assertEquals(selected,NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(slotWire))
        val broken=updated.copy(slots=updated.slots.map{slot->
            slot.copy(sets=slot.sets.map{set->
                val p=set.prescription
                if(p.amplifier?.kind!=CustomAmplifierKind.DROP_SET)set
                else set.copy(prescription=p.copy(amplifier=checkNotNull(
                    p.amplifier).copy(secondDropLoad=weight("15"))))
            })
        })
        assertThrows(IllegalArgumentException::class.java){
            NativeKotlinCustomWorkoutSnapshotCodec.encode(broken)
        }
        assertThrows(java.io.IOException::class.java){
            NativeKotlinCustomWorkoutSnapshotCodec.decode(
                bytes.copyOf(bytes.size-1))
        }
        assertArrayEquals(oldBytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(oldBytes)))
        val stagedOld=old.copy(slots=old.slots.map{slot->
            slot.copy(sets=slot.sets.map{set->
                val p=set.prescription
                if(p.amplifier?.kind!=CustomAmplifierKind.MYO_REPS)set
                else set.copy(prescription=p.copy(amplifier=checkNotNull(
                    p.amplifier).copy(followUpRounds=5)))
            })
        },knownSetAndRestSeconds=old.knownSetAndRestSeconds+80L)
        val stagedBytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(stagedOld)
        assertEquals(8,java.nio.ByteBuffer.wrap(stagedBytes,4,4).int)
        assertArrayEquals(stagedBytes,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(stagedBytes)))
    }

}
