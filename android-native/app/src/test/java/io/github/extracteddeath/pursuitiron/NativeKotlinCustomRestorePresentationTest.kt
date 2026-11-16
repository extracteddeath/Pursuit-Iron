package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinCustomRestorePresentationTest {
    private val stamp=CustomWorkoutSourceStamp(
        "athlete-1","profile-1","cycle-1",3,"gym-1",2)

    private fun slot(
        id:String,work:Int,warm:Int=0,role:ComposerRole=ComposerRole.ACCESSORY,
        group:String?=null
    ):CustomWorkoutSlotSnapshot {
        val sets=buildList {
            repeat(warm){i->
                add(CustomWorkoutSetSnapshot("w1-$id-${i+1}",id,i+1,
                    CustomAuthoredSet(DraftSetType.WARMUP,
                        DraftSetLoad.ExplicitBodyweight,RepBand(6,6),null,60,90)))
            }
            repeat(work){i->
                add(CustomWorkoutSetSnapshot("w1-$id-${i+1+warm}",id,i+1+warm,
                    CustomAuthoredSet(DraftSetType.WORKING,
                        DraftSetLoad.ExplicitBodyweight,RepBand(8,12),2,60,90)))
            }
        }
        return CustomWorkoutSlotSnapshot(id,"exercise-$id","family-$id",
            "variant-$id",role,null,group,false,"",sets)
    }

    private fun full(extra:CustomWorkoutSlotSnapshot=slot("curl",6)):CustomWorkoutDaySnapshot {
        val slots=listOf(slot("press",4,role=ComposerRole.PRIMARY),extra)
        return CustomWorkoutDaySnapshot(
            stamp,1,1,"block-1",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("Custom"),
            0,"Day one",ComposerSessionPurpose.HYPERTROPHY,
            slots,slots.flatMap{it.sets}.sumOf{
                it.prescription.activeSeconds.toLong()+it.prescription.restSeconds
            },300)
    }
    private fun shorten(full:CustomWorkoutDaySnapshot)=
        checkNotNull(CustomShorterSessionReviewDomain.project(full,20))

    @Test fun restoresOnlyOmittedWorkingSetsFromSourceOrderedReviewedFull() {
        val authored=full()
        val short=shorten(authored)
        val result=checkNotNull(NativeKotlinCustomRestorePresentation.inspect(
            short,authored))
        assertEquals(4,result.returningWorkingSets)
        assertEquals(0,result.returningWarmups)
        assertEquals(10,result.originalWorkingSets)
        assertEquals(6,result.retainedWorkingSets)
        assertEquals(listOf("exercise-press","exercise-curl"),
            result.slots.map{it.exerciseId})
        assertEquals(listOf(1,2),result.slots.map{it.originalPosition})
        assertEquals(listOf(0,4),
            result.slots.map{it.returningWorkingSets})
        assertEquals(authored.slots[1].sets.take(2),
            short.slots.last().sets)
        assertNull(authored.shortSessionLimitMinutes)
    }

    @Test fun fullyOmittedWarmupAndOptionalWorkAreShownWithoutPhantomSets() {
        val authored=full(slot("curl",4,warm=1))
        val short=shorten(authored)
        val result=checkNotNull(NativeKotlinCustomRestorePresentation.inspect(
            short,authored))
        assertEquals(4,result.returningWorkingSets)
        assertEquals(1,result.returningWarmups)
        assertEquals(1,result.slots.last().returningWarmups)
        assertEquals(0,short.slots.count{it.slotId=="curl"})
    }

    @Test fun independentEqualExerciseIdsRemainSeparateVisits() {
        val authored=full().let {it.copy(slots=listOf(
            it.slots.first().copy(exerciseId="shared"),
            it.slots.last().copy(exerciseId="shared")
        ))}
        val short=shorten(authored)
        val result=checkNotNull(NativeKotlinCustomRestorePresentation.inspect(
            short,authored))
        assertEquals(listOf("shared","shared"),result.slots.map{it.exerciseId})
        assertEquals(listOf(1,2),result.slots.map{it.originalPosition})
    }

    @Test fun mismatchedOrForgedOriginalsCannotOfferRestoreConfirmation() {
        val authored=full()
        val short=shorten(authored)
        fun inspect(s:CustomWorkoutDaySnapshot,f:CustomWorkoutDaySnapshot)=
            NativeKotlinCustomRestorePresentation.inspect(s,f)
        assertNotNull(inspect(short,authored))
        assertNull(inspect(short.copy(stamp=stamp.copy(gymCatalogSourceRevision=9)),
            authored))
        assertNull(inspect(short.copy(shortSessionOmittedSetKeys=listOf("forged")),
            authored))
        assertNull(inspect(short.copy(shortSessionLimitMinutes=null),authored))
        assertNull(inspect(short,authored.copy(shortSessionLimitMinutes=45)))
        assertNull(inspect(short.copy(slots=short.slots.map{slot->
            slot.copy(sets=slot.sets.map{set->
                set.copy(prescription=set.prescription.copy(targetRir=0))
            })
        }),authored))
        assertNull(inspect(short.copy(knownSetAndRestSeconds=0),authored))
        assertEquals(authored.slots.first().sets,
            full().slots.first().sets)
    }

    @Test fun noCutSavedTimePreferenceStillRestoresOnlyThePreference() {
        val authored=full(slot("curl",2))
        val short=checkNotNull(CustomShorterSessionReviewDomain.project(
            authored,30))
        val result=checkNotNull(NativeKotlinCustomRestorePresentation.inspect(
            short,authored))
        assertEquals(0,result.returningWorkingSets)
        assertEquals(0,result.returningWarmups)
        assertEquals(result.originalWorkingSets,result.retainedWorkingSets)
    }
}
