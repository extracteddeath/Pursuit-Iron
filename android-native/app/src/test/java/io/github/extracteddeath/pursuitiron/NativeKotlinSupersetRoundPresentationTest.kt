package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Superset review is journal-derived: no fake completed work or altered loads. */
internal class NativeKotlinSupersetRoundPresentationTest {
    private fun slot(name:String,count:Int=2,group:String?="pair",
        amplify:Boolean=false)=CustomWorkoutSlotSnapshot(
        name,"exercise-$name","family","variant",ComposerRole.ACCESSORY,
        null,group,true,"",
        (1..count).map {i->
            CustomWorkoutSetSnapshot("$name:s$i",name,i,
                CustomAuthoredSet(
                    DraftSetType.WORKING,
                    DraftSetLoad.Verified(LoadUnit.LB,
                        LoadBasis.DUMBBELL_PER_HAND,BigDecimal("25")),
                    RepBand(8,12),2,45,90,
                    if(amplify)CustomSetAmplifier(
                        CustomAmplifierKind.MYO_REPS,4,15) else null))
        })
    private fun start(vararg slots:CustomWorkoutSlotSnapshot)=
        CustomWorkoutJournalDomain.begin("workout-1",CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete","profile","cycle",1,"gym",1),
            1,1,"phase",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("Custom"),
            0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
            slots.toList(),900,null),"a".repeat(64))
    private fun record(current:CustomWorkoutJournalDraft,key:String,
        part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED):
        CustomWorkoutJournalDraft {
        val actual=if(outcome==KotlinJournalSetOutcome.SKIPPED)null else
            KotlinPerformedSet(BigDecimal("25"),LoadUnit.LB,
                if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)0 else 8,
                if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)null else 2)
        return (CustomWorkoutJournalDomain.reduce(current,
            CustomWorkoutJournalOperation.Record(
                "event-"+current.revision,current.revision,
                CustomWorkoutVisitKey(key,part),outcome,actual))
            as CustomWorkoutJournalDecision.Proposed).draft
    }
    @Test fun authenticLinkedRoundOrderAndUnaffectedSnapshot() {
        val j=start(slot("a"),slot("b"),slot("solo",group=null))
        val before=j.snapshot
        val rounds=NativeSupersetRoundReview.rounds(j,"pair")
        assertEquals(2,rounds.size)
        assertEquals(listOf(listOf("a:s1","b:s1"),listOf("a:s2","b:s2")),
            rounds.map{group->group.map{it.set.setKey}})
        assertEquals(j.snapshot,before)
        assertTrue(NativeSupersetRoundReview.rounds(j,"solo").isEmpty())
        assertTrue(NativeSupersetRoundReview.rounds(j,"").isEmpty())
        assertEquals("Not logged",NativeSupersetRoundReview.outcome(j,rounds[0][0].set))
        assertEquals(CustomWorkoutVisitKey("a:s1",CustomWorkoutVisitPart.MAIN),
            NativeSupersetRoundReview.reviewVisit(j,rounds[0][0].set))
    }
    @Test fun amplifierFollowUpIsTheNavigableVisitUntilAcknowledged() {
        var j=start(slot("a",amplify=true),slot("b"))
        j=record(j,"a:s1")
        var first=NativeSupersetRoundReview.rounds(j,"pair")[0][0].set
        assertEquals("Amplifier follow-up due",NativeSupersetRoundReview.outcome(j,first))
        assertEquals(CustomWorkoutVisitKey("a:s1",CustomWorkoutVisitPart.FOLLOW_UP),
            NativeSupersetRoundReview.reviewVisit(j,first))
        j=record(j,"a:s1",CustomWorkoutVisitPart.FOLLOW_UP)
        first=NativeSupersetRoundReview.rounds(j,"pair")[0][0].set
        assertEquals("Main + follow-up completed",
            NativeSupersetRoundReview.outcome(j,first))
        assertFalse(CustomWorkoutExecutionDomain.roundIsResolved(
            j,checkNotNull(NativeSupersetRoundReview.rounds(j,"pair")[0][0].round)))
    }
    @Test fun failedAndSkippedSetsResolveARoundWithoutClaimingSuccess() {
        var j=start(slot("a"),slot("b"))
        j=record(j,"a:s1",outcome=KotlinJournalSetOutcome.SKIPPED)
        j=record(j,"b:s1",outcome=KotlinJournalSetOutcome.FAILED_ATTEMPT)
        val first=NativeSupersetRoundReview.rounds(j,"pair")[0]
        assertTrue(CustomWorkoutExecutionDomain.roundIsResolved(
            j,checkNotNull(first.first().round)))
        assertEquals(listOf("Skipped","Failed attempt"),
            first.map{NativeSupersetRoundReview.outcome(j,it.set)})
    }
    @Test fun invalidUnlinkedGroupsNeverProducePhantomRounds() {
        val single=start(slot("a"),slot("solo",group=null))
        assertTrue(NativeSupersetRoundReview.rounds(single,"pair").isEmpty())
        val four=start(slot("a"),slot("b"),slot("c"),slot("d"))
        assertTrue(NativeSupersetRoundReview.rounds(four,"pair").isEmpty())
    }
}
