package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeCustomCycleDraftFileStoreTest {
    @get:Rule val temp = TemporaryFolder()
    private fun number(v: Int) = BigDecimal(v)
    private val stock = VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.SMITH_TOTAL, listOf(number(135), number(155),
            number(185), number(200)))

    private fun working(weight: Int, kind: CustomAmplifierKind? = null):
        CustomAuthoredSet = CustomAuthoredSet(
            DraftSetType.WORKING,
            DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                number(weight)),
            RepBand(6, 10), 2, 55, 120,
            if (kind == null) null else CustomSetAmplifier(
                kind, 8, 20,
                if (kind == CustomAmplifierKind.DROP_SET)
                    DraftSetLoad.Verified(LoadUnit.LB, LoadBasis.SMITH_TOTAL,
                        number(135))
                else null)
        )

    private fun original(): CustomTrainingCycle {
        val bench = CustomExerciseSlot("bench-1", "bench",
            ComposerRole.PRIMARY,
            listOf(working(185), working(185, CustomAmplifierKind.DROP_SET)),
            attachmentId = "straight-bar", supersetGroup = "pair",
            pinned = true, coachNote = "Keep a controlled eccentric")
        val pullup = CustomExerciseSlot("pull-1", "pullup",
            ComposerRole.SECONDARY, listOf(CustomAuthoredSet(
                DraftSetType.WORKING, DraftSetLoad.ExplicitBodyweight,
                RepBand(5, 8), null, 60, 150)),
            supersetGroup = "pair", pinned = false)
        val base = CustomTrainingBlock(
            "base", "My hypertrophy", TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING, 4,
            CustomSplitIdentity.Freeform("Chest / Back"),
            listOf(CustomTrainingDay(0, "Custom upper",
                ComposerSessionPurpose.HYPERTROPHY, listOf(bench, pullup))))
        val peak = CustomTrainingBlock(
            "strength", "My strength phase", TrainingPhase.STRENGTH,
            CustomBlockPurpose.TRAINING, 3,
            CustomSplitIdentity.Freeform("Strength A"),
            listOf(CustomTrainingDay(2, "Strength",
                ComposerSessionPurpose.STRENGTH_SKILL,
                listOf(bench.copy(slotId = "strength-bench",
                    supersetGroup = null, sets = listOf(working(200)))))))
        return CustomTrainingCycle("cycle-1", 1, "athlete-1",
            AthleteTrainingGoal.POWERBUILDING, "My actual program",
            listOf(base, peak),
            mapOf("base" to listOf(CustomWeekSlotOverride(
                3, 0, "bench-1", listOf(working(200))))),
            "My authored notes must survive cold launch")
    }

    private var signedIn: NativeKotlinOwnerIdentity? =
        NativeKotlinOwnerIdentity("owner-1", "athlete-1")
    private fun repository() = NativeCustomCycleDraftFileStore(
        temp.root.toPath(),
        NativeKotlinApprovalRevisionFence(temp.root.toPath()),
        NativeKotlinCurrentIdentity { signedIn })
    private fun saved(revision: Long) = NativeCustomDraftWriteResult.Committed(revision)

    @Test fun exactTargetsSurviveBaseWeekOverrideAndColdOwnerRead() {
        val start=original()
        val first=start.blocks.first()
        val day=first.days.first()
        val revised=start.copy(blocks=listOf(
            first.copy(days=listOf(day.copy(slots=day.slots.mapIndexed{ index,slot ->
                if(index==0) slot.copy(sets=slot.sets.mapIndexed{setIndex,set ->
                    set.copy(exactRepTarget=if(setIndex==0)8 else 10)
                }) else slot
            }))),start.blocks.last()),
            weekOverrides=mapOf("base" to listOf(CustomWeekSlotOverride(
                3,0,"bench-1",listOf(working(200).copy(exactRepTarget=9))))))
        val encoded=NativeCustomCycleDraftCodec.encode(revised)
        assertEquals(8,java.nio.ByteBuffer.wrap(encoded).getInt(4))
        val roundtrip=NativeCustomCycleDraftCodec.decode(encoded)
        assertEquals(revised,roundtrip)
        assertEquals(listOf(8,10),roundtrip.blocks.first().days.first()
            .slots.first().sets.map{it.exactRepTarget})
        assertEquals(9,roundtrip.weekOverrides.getValue("base")
            .single().sets.single().exactRepTarget)
        assertEquals(saved(1),repository().save("owner-1","cycle-1",0,revised))
        val cold=repository().read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found
        assertEquals(revised,cold.draft)
    }

    @Test fun codecRoundTripPreservesFullCustomCycleAndPartialBlankDraft() {
        val original = original()
        val roundtrip = NativeCustomCycleDraftCodec.decode(
            NativeCustomCycleDraftCodec.encode(original))
        assertEquals(original, roundtrip)
        assertEquals(CustomAmplifierKind.DROP_SET, roundtrip.blocks.first()
            .days.first().slots.first().sets.last().amplifier?.kind)
        assertEquals(3, roundtrip.weekOverrides.getValue("base").single().weekInBlock)
        assertFalse(CustomTrainingCycleDomain.review(roundtrip, listOf(
            CustomExerciseAvailability("bench", stock, false,
                setOf("straight-bar")),
            CustomExerciseAvailability("pullup", null, true)
        )).canIssueWorkout)
        assertThrows(UnsupportedOperationException::class.java) {
            (roundtrip.blocks as MutableList<*>).clear()
        }
        val blank = CustomCycleBuilderDomain.blank(
            "empty", "athlete-1", AthleteTrainingGoal.STRENGTH,
            "My draft", "start", 4, TrainingPhase.STRENGTH,
            CustomSplitIdentity.Freeform("Custom rotation"))!!
        assertEquals(blank, NativeCustomCycleDraftCodec.decode(
            NativeCustomCycleDraftCodec.encode(blank)))
        assertNull(CustomTrainingCycleDomain.review(blank, emptyList()).verifiedDraft)
    }

    @Test fun savedPerSlotProgressionSurvivesBinaryCodecAndColdOwnerRecovery() {
        val baseline=original()
        val before=NativeCustomCycleDraftCodec.encode(baseline)
        assertEquals(8,java.nio.ByteBuffer.wrap(before).getInt(4))
        assertEquals(baseline,NativeCustomCycleDraftCodec.decode(before))
        for (unsupported in listOf(0, 1, 2, 3, 4, 5, 6, 9)) {
            val bytes = before.copyOf()
            java.nio.ByteBuffer.wrap(bytes).putInt(4, unsupported)
            assertThrows(java.io.IOException::class.java) {
                NativeCustomCycleDraftCodec.decode(bytes)
            }
        }
        assertTrue(baseline.blocks.flatMap{it.days}.flatMap{it.slots}
            .all {it.progressionMethod==null})

        val base=baseline.blocks.first()
        val upper=base.days.first()
        val changed=baseline.copy(blocks=listOf(
            base.copy(days=listOf(upper.copy(slots=upper.slots.map {slot->
                when(slot.slotId) {
                    "bench-1" -> slot.copy(
                        progressionMethod=SavedProgressionMethod.DOUBLE)
                    "pull-1" -> slot.copy(
                        progressionMethod=SavedProgressionMethod.BODYWEIGHT_REPS)
                    else -> slot
                }
            }))),
            baseline.blocks.last().copy(days=baseline.blocks.last().days.map {day->
                day.copy(slots=day.slots.map{it.copy(
                    progressionMethod=SavedProgressionMethod.LINEAR)})
            })))
        val payload=NativeCustomCycleDraftCodec.encode(changed)
        assertEquals(8,java.nio.ByteBuffer.wrap(payload).getInt(4))
        assertEquals(changed,NativeCustomCycleDraftCodec.decode(payload))
        assertEquals(saved(1),repository().save("owner-1","cycle-1",0,changed))
        val cold=repository().read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found
        assertEquals(changed,cold.draft)
        val first=cold.draft.blocks.first().days.first().slots
        assertEquals(SavedProgressionMethod.DOUBLE,first.first().progressionMethod)
        assertEquals(SavedProgressionMethod.BODYWEIGHT_REPS,first.last().progressionMethod)
        // CAS and restarts do not erase the athlete's previous choice.
        val next=changed.copy(revision=2,authorNote="Revised week")
        assertEquals(saved(2),repository().save("owner-1","cycle-1",1,next))
        assertEquals(next,(repository().read("owner-1","cycle-1")
            as NativeCustomDraftReadResult.Found).draft)
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            repository().save("owner-1","cycle-1",1,next))
    }

    @Test fun nativeOwnedDraftSurvivesColdOpenAndRevisionedEdits() {
        val draft = original()
        assertEquals(NativeCustomDraftReadResult.Missing,
            repository().read("owner-1", "cycle-1"))
        assertEquals(saved(1), repository().save("owner-1", "cycle-1", 0, draft))
        assertEquals(NativeCustomDraftReadResult.Found(draft),
            repository().read("owner-1", "cycle-1"))
        val change = draft.copy(revision = 2,
            title = "Edited on my phone",
            authorNote = "New authored coaching priorities")
        assertEquals(saved(2), repository().save("owner-1", "cycle-1", 1, change))
        assertEquals(NativeCustomDraftReadResult.Found(change),
            repository().read("owner-1", "cycle-1"))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            repository().save("owner-1", "cycle-1", 1, change))
        assertEquals(NativeCustomDraftReadResult.Found(change),
            repository().read("owner-1", "cycle-1"))
    }

    @Test fun deletionIsTombstonedAndStaleAutosaveCannotResurrect() {
        val draft = original()
        assertEquals(saved(1), repository().save("owner-1", "cycle-1", 0, draft))
        assertEquals(saved(2), repository().delete("owner-1", "cycle-1", 1))
        assertEquals(NativeCustomDraftReadResult.Deleted(2),
            repository().read("owner-1", "cycle-1"))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            repository().save("owner-1", "cycle-1", 1,
                draft.copy(revision = 2)))
        assertEquals(NativeCustomDraftReadResult.Deleted(2),
            repository().read("owner-1", "cycle-1"))
    }

    @Test fun accountAndAthleteBoundariesCannotReadOrOverwriteDraft() {
        val draft = original()
        assertEquals(saved(1), repository().save("owner-1", "cycle-1", 0, draft))
        signedIn = NativeKotlinOwnerIdentity("owner-2", "athlete-1")
        assertEquals(NativeCustomDraftReadResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            repository().read("owner-1", "cycle-1"))
        signedIn = NativeKotlinOwnerIdentity("owner-1", "other-athlete")
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            repository().save("owner-1", "cycle-1", 1,
                draft.copy(revision = 2)))
        assertEquals(NativeCustomDraftReadResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            repository().read("owner-1", "cycle-1"))
        signedIn = null
        assertEquals(NativeCustomDraftReadResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            repository().read("owner-1", "cycle-1"))
        signedIn = NativeKotlinOwnerIdentity("owner-1", "athlete-1")
        assertEquals(NativeCustomDraftReadResult.Found(draft),
            repository().read("owner-1", "cycle-1"))
    }

    @Test fun corruptDraftFailsClosedWithoutDiscardingHistoricalData() {
        assertEquals(saved(1), repository().save("owner-1", "cycle-1", 0, original()))
        val paths = Files.list(temp.root.toPath()).use { stream ->
            stream.filter { it.fileName.toString().startsWith("draft-") &&
                it.fileName.toString().endsWith(".bin") }
                .toArray().map { it as java.nio.file.Path }
        }
        assertEquals(1, paths.size)
        val path = paths.single()
        val data = Files.readAllBytes(path)
        data[data.size / 2] = (data[data.size / 2].toInt() xor 1).toByte()
        Files.write(path, data)
        assertEquals(NativeCustomDraftReadResult.Rejected(
            NativeCustomDraftRefusal.CORRUPT_RECORD),
            repository().read("owner-1", "cycle-1"))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.CORRUPT_RECORD),
            repository().save("owner-1", "cycle-1", 1,
                original().copy(revision = 2)))
        assertArrayEquals(data, Files.readAllBytes(path))
    }

    @Test fun malformedOrCrossCycleDraftCannotBeWritten() {
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.INVALID_DRAFT),
            repository().save("owner-1", "other", 0, original()))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.INVALID_DRAFT),
            repository().save("owner-1", "cycle-1", 0,
                original().copy(revision = 2)))
        assertEquals(NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.STALE_REVISION),
            repository().delete("owner-1", "cycle-1", 0))
        assertEquals(NativeCustomDraftReadResult.Missing,
            repository().read("owner-1", "cycle-1"))
    }
    @Test fun savedProgramPickerUsesVerifiedTitlesInsteadOfCycleIds() {
        val store=repository()
        assertEquals(NativeCustomDraftListResult.Ready(emptyList()),
            store.listOwned("owner-1"))
        val first=original()
        val second=first.copy(cycleId="cycle-2",title="Second program")
        assertEquals(saved(1),store.save("owner-1","cycle-1",0,first))
        assertEquals(saved(1),store.save("owner-1","cycle-2",0,second))
        assertEquals(listOf("My actual program","Second program"),
            (store.listOwned("owner-1") as NativeCustomDraftListResult.Ready)
                .drafts.map {it.title})
        assertEquals(saved(2),store.save("owner-1","cycle-1",1,
            first.copy(revision=2,title="Updated actual program")))
        assertTrue((store.listOwned("owner-1")
            as NativeCustomDraftListResult.Ready).drafts.any {
            it.cycleId=="cycle-1" && it.title=="Updated actual program" &&
                it.revision==2L && it.blocks==2
        })
        assertEquals(saved(2),store.delete("owner-1","cycle-2",1))
        assertEquals(listOf("cycle-1"),(store.listOwned("owner-1")
            as NativeCustomDraftListResult.Ready).drafts.map {it.cycleId})
    }

    @Test fun savedProgramPickerIsOwnerScopedAndFailsClosedOnCorruption() {
        val store=repository()
        val first=original()
        assertEquals(saved(1),store.save("owner-1","cycle-1",0,first))
        signedIn=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeCustomDraftListResult.Ready(emptyList()),
            store.listOwned("other-owner"))
        assertEquals(NativeCustomDraftListResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            store.listOwned("owner-1"))
        signedIn=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(1,(store.listOwned("owner-1")
            as NativeCustomDraftListResult.Ready).drafts.size)
        val corrupt=Files.list(temp.root.toPath()).use{stream->
            stream.filter {it.fileName.toString().startsWith("draft-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().get()
        }
        Files.write(corrupt,byteArrayOf(1,2,3))
        assertEquals(NativeCustomDraftListResult.Rejected(
            NativeCustomDraftRefusal.CORRUPT_RECORD),
            store.listOwned("owner-1"))
        signedIn=null
        assertEquals(NativeCustomDraftListResult.Rejected(
            NativeCustomDraftRefusal.NOT_AUTHORIZED),
            store.listOwned("owner-1"))
    }

    @Test fun authoredAmplifierWorkSecondsPersistAcrossOverrideAndColdOpen() {
        val baseline=original()
        val bytes=NativeCustomCycleDraftCodec.encode(baseline)
        assertEquals(8,bytes[7].toInt())
        val timed=baseline.copy(
            blocks=baseline.blocks.map{block->
                block.copy(days=block.days.map{day->
                    day.copy(slots=day.slots.map{slot->
                        slot.copy(sets=slot.sets.map{set->
                            val amp=set.amplifier
                            if(amp==null)set else set.copy(
                                amplifier=amp.copy(followUpActiveSeconds=45))
                        })
                    })
                })
            },
            weekOverrides=baseline.weekOverrides.mapValues{(_,rows)->
                rows.map{row->row.copy(sets=row.sets.map{set->
                    set.copy(amplifier=CustomSetAmplifier(
                        CustomAmplifierKind.MYO_REPS,4,12,
                        followUpActiveSeconds=30))
                })}
            }
        )
        val encoded=NativeCustomCycleDraftCodec.encode(timed)
        assertEquals(8,encoded[7].toInt())
        val recovered=NativeCustomCycleDraftCodec.decode(encoded)
        assertEquals(timed,recovered)
        assertArrayEquals(encoded,NativeCustomCycleDraftCodec.encode(recovered))
        assertArrayEquals(bytes,NativeCustomCycleDraftCodec.encode(baseline))
        assertEquals(45,timed.blocks.first().days.first().slots.first()
            .sets.last().amplifier?.followUpActiveSeconds)
        assertEquals(30,timed.weekOverrides.getValue("base").single()
            .sets.single().amplifier?.followUpActiveSeconds)
        assertEquals(saved(1),repository().save("owner-1","cycle-1",0,timed))
        assertEquals(NativeCustomDraftReadResult.Found(timed),
            repository().read("owner-1","cycle-1"))
        val cold=NativeCustomCycleDraftFileStore(
            temp.root.toPath(),
            NativeKotlinApprovalRevisionFence(temp.root.toPath()),
            NativeKotlinCurrentIdentity{signedIn})
        assertEquals(NativeCustomDraftReadResult.Found(timed),
            cold.read("owner-1","cycle-1"))
        assertThrows(java.io.IOException::class.java){
            NativeCustomCycleDraftCodec.decode(encoded.copyOf(encoded.size-1))
        }
    }
    @Test fun authoredMyoStageCountsRoundTripDeterministically() {
        val original=original()
        val bytes=NativeCustomCycleDraftCodec.encode(original)
        val newBlocks=original.blocks.map { block -> block.copy(days=block.days.map {
            day -> day.copy(slots=day.slots.map {slot ->
                if(slot.slotId!="bench-1")slot else slot.copy(
                    sets=slot.sets.mapIndexed{ index,set ->
                        if(index!=1)set else set.copy(amplifier=CustomSetAmplifier(
                            CustomAmplifierKind.MYO_REPS,4,20,
                            followUpActiveSeconds=80,followUpRounds=5))
                    })
            })
        })}
        val configured=original.copy(blocks=newBlocks)
        val wire=NativeCustomCycleDraftCodec.encode(configured)
        assertEquals(8,wire[7].toInt())
        assertEquals(configured,NativeCustomCycleDraftCodec.decode(wire))
        assertArrayEquals(wire,NativeCustomCycleDraftCodec.encode(
            NativeCustomCycleDraftCodec.decode(wire)))
        assertArrayEquals(bytes,NativeCustomCycleDraftCodec.encode(
            NativeCustomCycleDraftCodec.decode(bytes)))
    }

    @Test fun stagedRestPauseAndClusterPlansColdReopenBeforeWorkoutIssuance() {
        val baseline=original()
        val oldBytes=NativeCustomCycleDraftCodec.encode(baseline)
        for(amp in listOf(
            CustomSetAmplifier(CustomAmplifierKind.REST_PAUSE,3,20,
                followUpActiveSeconds=45,followUpRounds=3),
            CustomSetAmplifier(CustomAmplifierKind.CLUSTER,2,30,
                followUpActiveSeconds=45,followUpRounds=4))) {
            val withStages=baseline.copy(blocks=baseline.blocks.map{block->
                block.copy(days=block.days.map{day->day.copy(
                    slots=day.slots.map{slot->
                        if(slot.slotId!="bench-1")slot else slot.copy(
                            sets=slot.sets.mapIndexed{index,set->
                                if(index!=1)set else set.copy(amplifier=amp)
                            })
                    })
                })
            })
            val bytes=NativeCustomCycleDraftCodec.encode(withStages)
            assertEquals(8,java.nio.ByteBuffer.wrap(bytes,4,4).int)
            assertEquals(withStages,NativeCustomCycleDraftCodec.decode(bytes))
            assertArrayEquals(bytes,NativeCustomCycleDraftCodec.encode(
                NativeCustomCycleDraftCodec.decode(bytes)))
        }
        assertArrayEquals(oldBytes,NativeCustomCycleDraftCodec.encode(
            NativeCustomCycleDraftCodec.decode(oldBytes)))
    }

    @Test fun twoDropDraftStoresBothLoadsAcrossColdReopen() {
        val original=original()
        val baseline=NativeCustomCycleDraftCodec.encode(original)
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=DraftSetLoad.Verified(LoadUnit.LB,
                LoadBasis.SMITH_TOTAL,number(155)),
            followUpActiveSeconds=35,followUpRounds=2,
            secondDropLoad=DraftSetLoad.Verified(LoadUnit.LB,
                LoadBasis.SMITH_TOTAL,number(135)))
        val two=original.copy(blocks=original.blocks.map{block->
            block.copy(days=block.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    if(slot.slotId!="bench-1")slot
                    else slot.copy(sets=slot.sets.mapIndexed{index,set->
                        if(index!=1)set else set.copy(amplifier=amp)
                    })
                })
            })
        })
        val bytes=NativeCustomCycleDraftCodec.encode(two)
        assertEquals(8,java.nio.ByteBuffer.wrap(bytes,4,4).int)
        assertEquals(two,NativeCustomCycleDraftCodec.decode(bytes))
        assertArrayEquals(bytes,NativeCustomCycleDraftCodec.encode(
            NativeCustomCycleDraftCodec.decode(bytes)))
        assertArrayEquals(baseline,NativeCustomCycleDraftCodec.encode(
            NativeCustomCycleDraftCodec.decode(baseline)))
        assertEquals(saved(1),repository().save("owner-1","cycle-1",0,two))
        assertEquals(NativeCustomDraftReadResult.Found(two),
            repository().read("owner-1","cycle-1"))
        val cold=NativeCustomCycleDraftFileStore(
            temp.root.toPath(),
            NativeKotlinApprovalRevisionFence(temp.root.toPath()),
            NativeKotlinCurrentIdentity {signedIn})
        assertEquals(NativeCustomDraftReadResult.Found(two),
            cold.read("owner-1","cycle-1"))
        val singleDrop=original.copy(blocks=original.blocks.map{block->
            block.copy(days=block.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    if(slot.slotId!="bench-1")slot
                    else slot.copy(sets=slot.sets.mapIndexed{index,set->
                        if(index!=1)set
                        else set.copy(amplifier=amp.copy(
                            followUpRounds=1,secondDropLoad=null))
                    })
                })
            })
        })
        val one=NativeCustomCycleDraftCodec.encode(singleDrop)
        assertEquals(8,java.nio.ByteBuffer.wrap(one,4,4).int)
    }

}
