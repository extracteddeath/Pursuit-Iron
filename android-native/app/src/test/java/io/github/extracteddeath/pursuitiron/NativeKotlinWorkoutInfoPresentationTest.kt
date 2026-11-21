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
        assertTrue(technique.contains("Myo-reps"))
        assertTrue(technique.contains("1 mini-set"))
        assertTrue(technique.contains("5 reps per mini-set"))
        assertTrue(technique.contains("15s between mini-set"))
        assertTrue(technique.contains("40s total follow-up work"))
        assertTrue(technique.contains("same activation load and station"))
        assertEquals(30,shown.workSeconds)
        assertEquals(60,shown.restSeconds)
        val unknown=customWorkoutInfoSets(listOf(custom("unknown",1,DraftSetType.WORKING,
            amplifier=amp.copy(followUpActiveSeconds=null)))).single()
        val missing=checkNotNull(unknown.technique)
        assertTrue(missing.contains("follow-up work time not set"))
        assertFalse(missing.contains("40s total follow-up work"))
    }
    @Test fun emptyDetailsNeverInventAnEntry() {
        assertTrue(customWorkoutInfoSets(emptyList()).isEmpty())
        assertTrue(composerWorkoutInfoSets(emptyList()).isEmpty())
    }
    @Test fun stagedMyoAndClusterInfoCountsActualAuthoredRoundsNotMainSets() {
        val myo=CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,
            followUpActiveSeconds=80,followUpRounds=5)
        val clusters=CustomSetAmplifier(CustomAmplifierKind.CLUSTER,2,30,
            followUpActiveSeconds=60,followUpRounds=3)
        val a=checkNotNull(customWorkoutInfoSets(listOf(custom(
            "myo",1,DraftSetType.WORKING,amplifier=myo))).single().technique)
        assertTrue(a,a.contains("5 mini-sets"))
        assertTrue(a,a.contains("4 reps per mini-set"))
        assertTrue(a,a.contains("20s between mini-sets"))
        assertTrue(a,a.contains("80s total follow-up work"))
        val b=checkNotNull(customWorkoutInfoSets(listOf(custom(
            "cluster",1,DraftSetType.WORKING,amplifier=clusters))).single().technique)
        assertTrue(b,b.contains("3 clusters"))
        assertTrue(b,b.contains("2 reps per cluster"))
        assertTrue(b,b.contains("30s between clusters"))
    }

    @Test fun infoDisplaysBothDistinctAuthoredDropsWithoutClaimingPerformedLoads() {
        fun load(s:String)=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,BigDecimal(s))
        val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
            followUpLoad=load("15"),followUpActiveSeconds=35,
            followUpRounds=2,secondDropLoad=load("10"))
        val parent=custom("drop",1,DraftSetType.WORKING).copy(
            prescription=CustomAuthoredSet(DraftSetType.WORKING,load("20"),
                RepBand(8,12),2,30,60,amp))
        val info=customWorkoutInfoSets(listOf(parent)).single()
        val technique=checkNotNull(info.technique)
        assertTrue(technique,technique.contains("2 weight drops"))
        assertTrue(technique,technique.contains("Drop 1: 15 lb"))
        assertTrue(technique,technique.contains("Drop 2: 10 lb"))
        assertTrue(technique,technique.indexOf("Drop 1:")<technique.indexOf("Drop 2:"))
        assertTrue(technique,technique.contains("35s total follow-up work"))
        assertFalse(technique.contains("performed"))
        assertTrue(info.load,info.load.startsWith("20 lb"))
        assertTrue(info.load,info.load.contains("per"))
        val invalid=parent.copy(prescription=parent.prescription.copy(
            amplifier=amp.copy(secondDropLoad=load("16"))))
        val warning=checkNotNull(customWorkoutInfoSets(listOf(invalid))
            .single().technique)
        assertTrue(warning,warning.contains("drop weights require review"))
        assertFalse(warning.contains("Drop 2: 16"))
    }

    @Test fun lengthenedPartialsRemainDistinctFromFullRangeWorkingReps() {
        val amp=CustomSetAmplifier(CustomAmplifierKind.LENGTHENED_PARTIALS,
            6,0,followUpActiveSeconds=15)
        val text=checkNotNull(customWorkoutInfoSets(listOf(custom(
            "partials",1,DraftSetType.WORKING,amplifier=amp)))
            .single().technique)
        assertTrue(text,text.contains("Lengthened partials"))
        assertTrue(text,text.contains("6 controlled partial reps"))
        assertTrue(text,text.contains("final working set"))
        assertTrue(text,text.contains("same load and station"))
        assertFalse(text.contains("6 reps per partial rep"))
    }

}
