package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomHistoricalTechniqueGroupingDomainTest {
    private fun main(
        key:String="set-1",
        kind:CustomAmplifierKind?=CustomAmplifierKind.MYO_REPS,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED
    )=CustomHistoricalVisitFact(
        "slot-1","machine-lateral",key,CustomWorkoutVisitPart.MAIN,
        DraftSetType.WORKING,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            BigDecimal("30")),
        RepBand(12,20),2,kind,outcome,
        if(outcome==KotlinJournalSetOutcome.SKIPPED)null else
            KotlinPerformedSet(BigDecimal("30"),LoadUnit.LB,
                if(outcome==KotlinJournalSetOutcome.COMPLETED)15 else 0,1),
        "main-$key")

    private fun follow(
        original:CustomHistoricalVisitFact,
        stage:Int,
        reps:Int,
        load:String="30",
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED
    )=original.copy(
        part=CustomWorkoutVisitPart.FOLLOW_UP,
        prescribedReps=RepBand(4,4),
        prescribedRir=null,
        outcome=outcome,
        actual=if(outcome==KotlinJournalSetOutcome.SKIPPED)null else
            KotlinPerformedSet(BigDecimal(load),LoadUnit.LB,
                if(outcome==KotlinJournalSetOutcome.COMPLETED)reps else 0,null),
        effectiveEventId="stage-$stage-${original.setKey}",stage=stage)

    @Test fun realMyoStagesStayWithTheirWorkingSetAndOtherSetsKeepOrder() {
        val a=main()
        val b=main("set-2",null)
        val history=listOf(a,follow(a,1,5),follow(a,2,4),
            follow(a,3,3),b)
        val groups=CustomHistoricalTechniqueGroupingDomain.group(history)!!
        assertEquals(listOf("set-1","set-2"),groups.map{it.main.setKey})
        assertEquals(listOf(1,2,3),groups[0].followUps.map{it.stage})
        assertEquals("Myo-reps · 3 mini-sets · 5 + 4 + 3 reps",
            groups[0].techniqueCaption)
        assertNull(groups[1].techniqueCaption)
        assertEquals(history,groups.flatMap{listOf(it.main)+it.followUps})
        assertEquals(2,groups.size) // not five normal working sets
    }

    @Test fun correctionsChangeVisibleActualsNotTargetsOrMainIdentity() {
        val a=main()
        val earlier=listOf(a,follow(a,1,5))
        val corrected=listOf(a,follow(a,1,3).copy(
            effectiveEventId="corrected-stage-1"))
        val before=CustomHistoricalTechniqueGroupingDomain.group(earlier)!!.single()
        val after=CustomHistoricalTechniqueGroupingDomain.group(corrected)!!.single()
        assertEquals("Myo-reps · 1 mini-set · 5 reps",before.techniqueCaption)
        assertEquals("Myo-reps · 1 mini-set · 3 reps",after.techniqueCaption)
        assertEquals(a,after.main)
        assertEquals(earlier[1].prescribedReps,after.followUps.single().prescribedReps)
    }

    @Test fun partialRepsAreNeverLabeledFullRangeAndRealDropLoadsStaySeparate() {
        val partial=main(kind=CustomAmplifierKind.LENGTHENED_PARTIALS)
        val part=CustomHistoricalTechniqueGroupingDomain.group(
            listOf(partial,follow(partial,1,4)))!!.single()
        assertEquals("Lengthened partials · 4 partial reps (not full ROM)",
            part.techniqueCaption)
        val drop=main(key="drop",kind=CustomAmplifierKind.DROP_SET)
        val performed=CustomHistoricalTechniqueGroupingDomain.group(
            listOf(drop,follow(drop,1,10,"20"),
                follow(drop,2,7,"15")))!!.single()
        assertEquals("Drop set · 2 drops · 20 lb × 10 + 15 lb × 7",
            performed.techniqueCaption)
        assertEquals(listOf(BigDecimal("20"),BigDecimal("15")),
            performed.followUps.map{it.actual!!.load})
    }

    @Test fun skippingMainOrStageNeverCreatesFakeCompletedTechniqueWork() {
        val skipped=main(outcome=KotlinJournalSetOutcome.SKIPPED)
        val group=CustomHistoricalTechniqueGroupingDomain.group(listOf(skipped))!!.single()
        assertTrue(group.techniqueCaption!!.contains("not performed"))
        assertTrue(group.followUps.isEmpty())
        val valid=main()
        assertEquals("Myo-reps · no follow-up performed",
            CustomHistoricalTechniqueGroupingDomain.group(listOf(valid))!!
                .single().techniqueCaption)
        val early=CustomHistoricalTechniqueGroupingDomain.group(
            listOf(valid,follow(valid,1,4),follow(valid,2,0,
                outcome=KotlinJournalSetOutcome.SKIPPED)))!!.single()
        assertEquals("Myo-reps · 2 mini-sets · 4 + skipped reps",
            early.techniqueCaption)
    }

    @Test fun corruptOrDetachedStagesNeverSilentlyHideHistoryFacts() {
        val a=main()
        val s1=follow(a,1,4)
        val s2=follow(a,2,3)
        val invalid=listOf(
            listOf(s1,a),
            listOf(a,s1,s1),
            listOf(a,s2),
            listOf(a,s1.copy(exerciseId="another")),
            listOf(a,s1.copy(slotId="another")),
            listOf(a,s1.copy(amplifierKind=CustomAmplifierKind.DROP_SET)),
            listOf(a.copy(outcome=KotlinJournalSetOutcome.SKIPPED,
                actual=null),s1),
            listOf(a.copy(amplifierKind=null),s1),
            listOf(a,a),
            listOf(a,s1.copy(stage=6))
        )
        for(visits in invalid)assertNull(
            "Invalid stage ownership/order must retain flat fallback: $visits",
            CustomHistoricalTechniqueGroupingDomain.group(visits))
    }
}
