package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinWorkoutInfoPresentationTest {
    private fun custom(
        key:String,ordinal:Int,type:DraftSetType,slot:String="press",
        amplifier:CustomSetAmplifier?=null
    )=CustomWorkoutSetSnapshot(key,slot,ordinal,CustomAuthoredSet(
        type,DraftSetLoad.ExplicitBodyweight,RepBand(8,12),
        if(type==DraftSetType.WARMUP)null else 2,30,60,amplifier))

    @Test fun customWorkingNumbersRestartAfterTwoWarmupsWithoutChangingKeys() {
        val sets=listOf(custom("warm-1",1,DraftSetType.WARMUP),
            custom("warm-2",2,DraftSetType.WARMUP),
            custom("work-1",3,DraftSetType.WORKING),
            custom("work-2",4,DraftSetType.WORKING))
        val shown=customWorkoutInfoSets(sets)
        assertEquals(listOf("Warm-up 1","Warm-up 2","Working set 1","Working set 2"),shown.map{it.title})
        assertEquals(sets.map{it.setKey},shown.map{it.setKey})
        assertEquals(listOf(1,2,3,4),sets.map{it.ordinalInSlot})
        assertEquals(listOf("Not set","Not set","2","2"),shown.map{it.targetRir})
    }
    @Test fun eachAuthoredSlotKeepsItsOwnNumberingEvenForRepeatedMovement() {
        val sets=listOf(custom("a-warm",1,DraftSetType.WARMUP,"a"),
            custom("a-work",2,DraftSetType.WORKING,"a"),
            custom("b-work",1,DraftSetType.WORKING,"b"))
        assertEquals(listOf("Warm-up 1","Working set 1","Working set 1"),customWorkoutInfoSets(sets).map{it.title})
    }
    @Test fun retainedSetWithOriginalOrdinalKeepsItsIdentityAndVisibleGroupPosition() {
        val saved=custom("retained-s4",4,DraftSetType.WORKING)
        val shown=customWorkoutInfoSets(listOf(saved)).single()
        assertEquals("Working set 1",shown.title)
        assertEquals("retained-s4",shown.setKey)
        assertEquals(4,saved.ordinalInSlot)
    }
    @Test fun generatedInfoKeepsPhysicalLoadAndBothOrdinalGroups() {
        val load=DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.KETTLEBELL_EACH,BigDecimal("25"))
        val sets=listOf(DraftSetType.WARMUP,DraftSetType.WORKING).map{type->
            KotlinWorkoutTarget("bell",ComposerRole.ACCESSORY,ComposerSessionPurpose.HYPERTROPHY,
                type.name,type,0,load,RepBand(8,12),if(type==DraftSetType.WARMUP)null else 2,35,90)
        }
        val shown=composerWorkoutInfoSets(sets)
        assertEquals(listOf("Warm-up 1","Working set 1"),shown.map{it.title})
        assertEquals(sets.map{it.setKey},shown.map{it.setKey})
        assertTrue(shown.all{it.load=="25 lb · per kettlebell" && it.workSeconds==35 && it.restSeconds==90})
        assertEquals(listOf("Not set","2"),shown.map{it.targetRir})
    }
    @Test fun amplifierWorkDurationRemainsDistinctFromRestAndUnknownStaysUnknown() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,5,15,followUpActiveSeconds=40)
        val shown=customWorkoutInfoSets(listOf(custom("amp",1,DraftSetType.WORKING,amplifier=amp))).single()
        val technique=checkNotNull(shown.technique)
        assertTrue(technique.contains("5 follow-up reps"))
        assertTrue(technique.contains("15s follow-up rest"))
        assertTrue(technique.contains("40s follow-up work"))
        assertEquals(30,shown.workSeconds)
        assertEquals(60,shown.restSeconds)
        val unknown=customWorkoutInfoSets(listOf(custom("unknown",1,DraftSetType.WORKING,
            amplifier=amp.copy(followUpActiveSeconds=null)))).single()
        val missing=checkNotNull(unknown.technique)
        assertTrue(missing.contains("follow-up work time not set"))
        assertFalse(missing.contains("40s follow-up work"))
    }
    @Test fun emptyDetailsNeverInventAnEntry() {
        assertTrue(customWorkoutInfoSets(emptyList()).isEmpty())
        assertTrue(composerWorkoutInfoSets(emptyList()).isEmpty())
    }
}
