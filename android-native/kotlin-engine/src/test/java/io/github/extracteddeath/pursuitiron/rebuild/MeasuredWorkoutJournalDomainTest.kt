package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MeasuredWorkoutJournalDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private fun plan(id:String,index:Int):MeasuredExerciseSetPlan {
        val d=checkNotNull(MeasuredExerciseCatalogDomain.find(id))
        val sides=when(d.sideRule) {
            MeasuredSideRule.BOTH ->setOf(MeasuredSide.BOTH)
            MeasuredSideRule.LEFT_AND_RIGHT ->
                setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE ->setOf(MeasuredSide.LEFT)
        }
        val loaded=d.loadKind in setOf(
            MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND)
        val band=d.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT
        return MeasuredExerciseSetPlan(
            workoutId="workout-parent",
            setKey="set-$index",
            exerciseId=id,
            gymId="gym-one",gymRevision=4,
            physicalSetupId="station-$index",
            targetAmount=when(d.kind) {
                MeasuredExerciseKind.HOLD_SECONDS ->30
                MeasuredExerciseKind.CARRY_CENTIMETERS ->1500
                MeasuredExerciseKind.BANDED_REPETITIONS ->12
            },
            sides=sides,confirmedFixtures=d.requiredFixtures,
            physicalStock=if(loaded)VerifiedLoadStock.Totals(
                LoadUnit.LB,checkNotNull(d.expectedBasis),
                listOf(n("10"),n("20"),n("30")))else null,
            intendedExternalLoad=if(loaded)n("20")else null,
            unit=if(loaded)LoadUnit.LB else null,
            matchedPairConfirmed=id=="farmers",
            bandId=if(band)"blue-band"else null,
            gymConfirmedBandIds=if(band)setOf("blue-band")else emptySet()
        )
    }
    private fun actual(plan:MeasuredExerciseSetPlan,amount:Int=25):
        MeasuredExerciseActual {
        val definition=checkNotNull(MeasuredExerciseCatalogDomain.find(
            plan.exerciseId))
        val weighted=definition.loadKind in setOf(
            MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND)
        return MeasuredExerciseActual(
            plan.physicalSetupId,
            plan.sides.sortedBy{it.ordinal}.map{MeasuredSideResult(it,amount)},
            if(weighted)n("20")else null,
            if(weighted)LoadUnit.LB else null,
            plan.bandId,
            if(definition.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)42
            else null)
    }
    private fun fullPlan():MeasuredWorkoutPlan {
        val sets=MeasuredExerciseCatalogDomain.all.mapIndexed{i,d->
            plan(d.exerciseId,i+1)
        }
        return MeasuredWorkoutPlan("workout-parent","gym-one",4,sets)
    }
    private fun start(p:MeasuredWorkoutPlan=fullPlan())=
        checkNotNull(MeasuredWorkoutJournalDomain.begin(p))
    private fun accepted(decision:MeasuredWorkoutDecision):
        MeasuredWorkoutDecision.Proposed {
        assertTrue(decision.toString(),decision is MeasuredWorkoutDecision.Proposed)
        return decision as MeasuredWorkoutDecision.Proposed
    }
    private fun rejected(decision:MeasuredWorkoutDecision,
        reason:MeasuredWorkoutRefusal) {
        assertTrue(decision.toString(),decision is MeasuredWorkoutDecision.Rejected)
        assertEquals(reason,(decision as MeasuredWorkoutDecision.Rejected).reason)
    }
    private fun record(state:MeasuredWorkoutJournal,index:Int,
        event:String="event-$index",
        outcome:MeasuredSetOutcome=MeasuredSetOutcome.COMPLETED,
        corrects:String?=null,
        observed:MeasuredExerciseActual?=actual(
            state.approvedPlan.sets[index-1]))=
        MeasuredWorkoutOperation.Record(event,state.revision,
            "set-$index",outcome,observed,corrects)

    @Test fun allFourteenMeasuredExerciseKindsRecordInOneParentAndFinishOnlyTogether() {
        val plan=fullPlan()
        assertEquals(14,plan.sets.size)
        var journal=start(plan)
        assertFalse(journal.canProposeFinish)
        assertNull(journal.finishedByEventId)
        rejected(MeasuredWorkoutJournalDomain.reduce(journal,
            MeasuredWorkoutOperation.Finish("early",0)),
            MeasuredWorkoutRefusal.INCOMPLETE)
        for((index,set) in plan.sets.withIndex()) {
            val operation=record(journal,index+1)
            val next=accepted(MeasuredWorkoutJournalDomain.reduce(
                journal,operation)).journal
            assertEquals(index+1L,next.revision)
            assertEquals(set,
                next.bySetKey.getValue(set.setKey).immutablePlan)
            assertEquals(1L,
                next.bySetKey.getValue(set.setKey).revision)
            assertFalse(next.mayPublishHistory)
            journal=next
        }
        assertEquals(14,journal.bySetKey.size)
        assertTrue(journal.canProposeFinish)
        val finalOperation=MeasuredWorkoutOperation.Finish("finish-once",14)
        val finished=accepted(MeasuredWorkoutJournalDomain.reduce(
            journal,finalOperation)).journal
        assertEquals(15L,finished.revision)
        assertEquals("finish-once",finished.finishedByEventId)
        assertFalse(finished.mayPublishHistory)
        assertFalse(finished.mayChangeTrainingProgram)
        assertFalse(finished.canProposeFinish)
        val recovered=checkNotNull(MeasuredWorkoutJournalDomain.replay(
            plan,finished.receipts))
        assertEquals(finished,recovered)
        assertFalse(recovered.mayPublishHistory)
        assertEquals(plan,fullPlan())
    }

    @Test fun differentWorkoutGymRevisionAndDuplicateSetKeysNeverMakeParent() {
        val original=fullPlan()
        assertNull(MeasuredWorkoutJournalDomain.begin(
            original.copy(sets=original.sets+
                original.sets.first().copy(setKey="set-1"))))
        assertNull(MeasuredWorkoutJournalDomain.begin(
            original.copy(sets=original.sets.mapIndexed{i,set->
                if(i==0)set.copy(workoutId="other-workout")else set
            })))
        assertNull(MeasuredWorkoutJournalDomain.begin(
            original.copy(sets=original.sets.mapIndexed{i,set->
                if(i==0)set.copy(gymId="other-gym")else set
            })))
        assertNull(MeasuredWorkoutJournalDomain.begin(
            original.copy(sets=original.sets.mapIndexed{i,set->
                if(i==0)set.copy(gymRevision=3)else set
            })))
        assertNull(MeasuredWorkoutJournalDomain.begin(
            original.copy(sets=original.sets.mapIndexed{i,set->
                if(i==0)set.copy(targetAmount=0)else set
            })))
        assertNull(MeasuredWorkoutJournalDomain.begin(
            original.copy(sets=emptyList())))
        assertNull(MeasuredWorkoutJournalDomain.begin(
            original.copy(sets=List(65){i->plan("plank",i+100)})))
    }

    @Test fun staleCollidingOrForeignSetReceiptsNeverRewriteOneSavedActual() {
        val initial=start()
        val operation=record(initial,1)
        val once=accepted(MeasuredWorkoutJournalDomain.reduce(
            initial,operation)).journal
        val retry=accepted(MeasuredWorkoutJournalDomain.reduce(once,operation))
        assertTrue(retry.replayed)
        assertEquals(once,retry.journal)
        rejected(MeasuredWorkoutJournalDomain.reduce(once,
            operation.copy(actual=actual(initial.approvedPlan.sets.first(),31))),
            MeasuredWorkoutRefusal.EVENT_COLLISION)
        rejected(MeasuredWorkoutJournalDomain.reduce(once,
            record(initial,2,event="stale")),
            MeasuredWorkoutRefusal.STALE_REVISION)
        rejected(MeasuredWorkoutJournalDomain.reduce(once,
            MeasuredWorkoutOperation.Record("unknown-set",once.revision,
                "set-other",MeasuredSetOutcome.SKIPPED,null)),
            MeasuredWorkoutRefusal.UNKNOWN_SET)
        rejected(MeasuredWorkoutJournalDomain.reduce(once,
            record(once,1,event="untracked-correction")),
            MeasuredWorkoutRefusal.SET_REJECTED)
        val corrected=record(once,1,event="correct-one",
            corrects="event-1",observed=actual(
                initial.approvedPlan.sets.first(),31))
        val updated=accepted(MeasuredWorkoutJournalDomain.reduce(once,
            corrected)).journal
        assertEquals(2L,updated.revision)
        assertEquals(2L,updated.bySetKey.getValue("set-1").revision)
        assertEquals(0L,updated.bySetKey.getValue("set-2").revision)
        assertEquals(2,updated.bySetKey.getValue("set-1").receipts.size)
        rejected(MeasuredWorkoutJournalDomain.reduce(updated,
            record(updated,1,event="wrong-parent",corrects="event-1")),
            MeasuredWorkoutRefusal.SET_REJECTED)
        assertEquals(updated,MeasuredWorkoutJournalDomain.replay(
            initial.approvedPlan,updated.receipts))
    }

    @Test fun skippedAndFailedOutcomesCountAsResolvedButNeverCompletedWork() {
        val two=fullPlan().copy(sets=fullPlan().sets.take(2))
        var journal=start(two)
        journal=accepted(MeasuredWorkoutJournalDomain.reduce(journal,
            record(journal,1,outcome=MeasuredSetOutcome.SKIPPED,
                observed=null))).journal
        assertFalse(journal.canProposeFinish)
        val failed=MeasuredExerciseActual("station-2",
            listOf(MeasuredSideResult(MeasuredSide.BOTH,0)))
        // Zero is an explicit failed attempt, never a successful hold.
        journal=accepted(MeasuredWorkoutJournalDomain.reduce(journal,
            record(journal,2,outcome=MeasuredSetOutcome.FAILED_ATTEMPT,
                observed=failed))).journal
        assertTrue(journal.canProposeFinish)
        assertEquals(MeasuredSetOutcome.SKIPPED,
            (journal.bySetKey.getValue("set-1").effectiveRecord!!.operation
                as MeasuredJournalOperation.Record).outcome)
        val finish=accepted(MeasuredWorkoutJournalDomain.reduce(journal,
            MeasuredWorkoutOperation.Finish("done",journal.revision))).journal
        assertEquals(finish,MeasuredWorkoutJournalDomain.replay(two,finish.receipts))
        rejected(MeasuredWorkoutJournalDomain.reduce(finish,
            MeasuredWorkoutOperation.Record("after",finish.revision,
                "set-1",MeasuredSetOutcome.SKIPPED,null)),
            MeasuredWorkoutRefusal.FINISHED)
    }

    @Test fun coldReplayFailsClosedForCrossSetEventTamperingOrMissingReceipt() {
        val plan=fullPlan().copy(sets=fullPlan().sets.take(2))
        var journal=start(plan)
        journal=accepted(MeasuredWorkoutJournalDomain.reduce(journal,
            record(journal,1))).journal
        journal=accepted(MeasuredWorkoutJournalDomain.reduce(journal,
            record(journal,2))).journal
        val finished=accepted(MeasuredWorkoutJournalDomain.reduce(journal,
            MeasuredWorkoutOperation.Finish("finish",2))).journal
        assertNull(MeasuredWorkoutJournalDomain.replay(plan,
            finished.receipts.drop(1)))
        assertNull(MeasuredWorkoutJournalDomain.replay(plan,
            finished.receipts.mapIndexed{i,receipt->
                if(i==1)receipt.copy(revision=42)else receipt
            }))
        assertNull(MeasuredWorkoutJournalDomain.replay(plan,
            finished.receipts.mapIndexed{i,receipt->
                if(i==0)receipt.copy(operation=
                    (receipt.operation as MeasuredWorkoutOperation.Record)
                        .copy(setKey="set-2"))
                else receipt
            }))
        assertNull(MeasuredWorkoutJournalDomain.replay(plan,
            finished.receipts+finished.receipts.last()))
        assertEquals(finished,MeasuredWorkoutJournalDomain.replay(
            plan,finished.receipts))
    }
}
