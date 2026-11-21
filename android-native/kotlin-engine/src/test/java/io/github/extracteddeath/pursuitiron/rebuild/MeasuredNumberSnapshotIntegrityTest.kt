package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Counterexamples: editor-owned collections must never rewrite issued or recorded numbers. */
class MeasuredNumberSnapshotIntegrityTest {
    private data class CallerPlan(
        val plan:MeasuredExerciseSetPlan,
        val sides:MutableSet<MeasuredSide>,
        val fixtures:MutableSet<String>,
        val bands:MutableSet<String>,
        val loads:MutableList<BigDecimal>
    )

    private fun caller(id:String):CallerPlan {
        val definition=checkNotNull(MeasuredExerciseCatalogDomain.find(id))
        val sides=when(definition.sideRule) {
            MeasuredSideRule.BOTH ->mutableSetOf(MeasuredSide.BOTH)
            MeasuredSideRule.LEFT_AND_RIGHT ->
                mutableSetOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE ->mutableSetOf(MeasuredSide.LEFT)
        }
        val fixtures=definition.requiredFixtures.toMutableSet()
        val bands=mutableSetOf("blue-band")
        val loads=mutableListOf(BigDecimal("10"),BigDecimal("20"),BigDecimal("30"))
        val loaded=definition.expectedBasis!=null
        val band=definition.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT
        return CallerPlan(MeasuredExerciseSetPlan(
            "snapshot-workout","snapshot-set",id,"gym",1,"station",10,
            sides,fixtures,
            physicalStock=if(loaded)VerifiedLoadStock.Totals(
                LoadUnit.LB,checkNotNull(definition.expectedBasis),loads) else null,
            intendedExternalLoad=if(loaded)BigDecimal("20") else null,
            unit=if(loaded)LoadUnit.LB else null,
            matchedPairConfirmed=id=="farmers",
            bandId=if(band)"blue-band" else null,
            gymConfirmedBandIds=bands),sides,fixtures,bands,loads)
    }

    private fun actual(plan:MeasuredExerciseSetPlan,amount:Int=15)=
        MeasuredExerciseActual(plan.physicalSetupId,
            plan.sides.sortedBy{it.ordinal}.map{
                MeasuredSideResult(it,amount)}.toMutableList(),
            plan.intendedExternalLoad,plan.unit,plan.bandId)

    private fun record(before:MeasuredExerciseJournal,event:String="record",
        amount:Int=15,corrects:String?=null):MeasuredExerciseJournal =
        (MeasuredExerciseJournalDomain.reduce(before,
            MeasuredJournalOperation.Record(event,before.revision,
                MeasuredSetOutcome.COMPLETED,actual(before.immutablePlan,amount),
                corrects)) as MeasuredJournalDecision.Proposed).journal

    private fun finish(before:MeasuredExerciseJournal):MeasuredExerciseJournal =
        (MeasuredExerciseJournalDomain.reduce(before,
            MeasuredJournalOperation.Finish("finish",before.revision))
            as MeasuredJournalDecision.Proposed).journal

    @Test fun allFourteenPlansRetainTheirOwnSidesFixturesBandsAndPhysicalStock() {
        for(definition in MeasuredExerciseCatalogDomain.all) {
            val supplied=caller(definition.exerciseId)
            val journal=checkNotNull(MeasuredExerciseJournalDomain.begin(supplied.plan))
            val expectedSides=supplied.sides.toSet()
            val expectedFixtures=supplied.fixtures.toSet()
            supplied.sides.clear()
            supplied.fixtures.clear()
            supplied.bands.clear()
            supplied.loads[1]=BigDecimal("999")
            val retained=journal.immutablePlan
            assertEquals(definition.exerciseId,expectedSides,retained.sides)
            assertEquals(expectedFixtures,retained.confirmedFixtures)
            assertEquals(setOf("blue-band"),retained.gymConfirmedBandIds)
            if(retained.physicalStock!=null)
                assertEquals(listOf(BigDecimal("10"),BigDecimal("20"),BigDecimal("30")),
                    (retained.physicalStock as VerifiedLoadStock.Totals).loads)
            assertTrue(definition.exerciseId,MeasuredExerciseDomain.inspect(retained)
                is MeasurementPlanResult.Candidate)
            assertEquals(journal,MeasuredExerciseJournalDomain.replay(retained,journal.receipts))
            assertThrows(UnsupportedOperationException::class.java) {
                (retained.sides as MutableSet<MeasuredSide>).clear()
            }
            assertThrows(UnsupportedOperationException::class.java) {
                (retained.confirmedFixtures as MutableSet<String>).clear()
            }
            assertThrows(UnsupportedOperationException::class.java) {
                (retained.gymConfirmedBandIds as MutableSet<String>).clear()
            }
            (retained.physicalStock as? VerifiedLoadStock.Totals)?.let { stock ->
                assertThrows(UnsupportedOperationException::class.java) {
                    (stock.loads as MutableList<BigDecimal>)[1]=BigDecimal("999")
                }
            }
        }
    }

    @Test fun allFourteenActualsAndCorrectionsSurviveCallerMutationAndReplay() {
        for(definition in MeasuredExerciseCatalogDomain.all) {
            val initial=checkNotNull(MeasuredExerciseJournalDomain.begin(caller(definition.exerciseId).plan))
            val observed=actual(initial.immutablePlan)
            val first=(MeasuredExerciseJournalDomain.reduce(initial,
                MeasuredJournalOperation.Record("first",0,MeasuredSetOutcome.COMPLETED,observed))
                as MeasuredJournalDecision.Proposed).journal
            (observed.sides as MutableList<MeasuredSideResult>).clear()
            assertTrue(checkNotNull((first.effectiveRecord?.operation as?
                MeasuredJournalOperation.Record)?.actual).sides.all{it.amount==15})
            val correction=actual(initial.immutablePlan,18)
            val corrected=(MeasuredExerciseJournalDomain.reduce(first,
                MeasuredJournalOperation.Record("correction",1,
                    MeasuredSetOutcome.COMPLETED,correction,"first"))
                as MeasuredJournalDecision.Proposed).journal
            (correction.sides as MutableList<MeasuredSideResult>).clear()
            val finished=finish(corrected)
            assertEquals(finished,MeasuredExerciseJournalDomain.replay(
                finished.immutablePlan,finished.receipts))
            assertEquals(15,((first.effectiveRecord!!.operation as
                MeasuredJournalOperation.Record).actual!!.sides.first().amount))
            val saved=(corrected.effectiveRecord!!.operation as
                MeasuredJournalOperation.Record).actual!!
            assertTrue(saved.sides.all{it.amount==18})
            assertThrows(UnsupportedOperationException::class.java) {
                (saved.sides as MutableList<MeasuredSideResult>).clear()
            }
        }
    }

    @Test fun parentAndChildRetainTheSameIndependentPlanAndActual() {
        val source=caller("side-plank")
        val inputs=mutableListOf(source.plan)
        val initial=checkNotNull(MeasuredWorkoutJournalDomain.begin(
            MeasuredWorkoutPlan("snapshot-workout","gym",1,inputs)))
        inputs.clear()
        source.sides.clear()
        val plan=initial.approvedPlan.sets.single()
        assertEquals(setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT),plan.sides)
        val observed=actual(plan)
        val write=MeasuredWorkoutOperation.Record("record",0,plan.setKey,
            MeasuredSetOutcome.COMPLETED,observed)
        val recorded=(MeasuredWorkoutJournalDomain.reduce(initial,write)
            as MeasuredWorkoutDecision.Proposed).journal
        (observed.sides as MutableList<MeasuredSideResult>).clear()
        val correctedActual=actual(plan,19)
        val corrected=(MeasuredWorkoutJournalDomain.reduce(recorded,
            MeasuredWorkoutOperation.Record("correct",1,plan.setKey,
                MeasuredSetOutcome.COMPLETED,correctedActual,"record"))
            as MeasuredWorkoutDecision.Proposed).journal
        (correctedActual.sides as MutableList<MeasuredSideResult>).clear()
        val finished=(MeasuredWorkoutJournalDomain.reduce(corrected,
            MeasuredWorkoutOperation.Finish("finish",2))
            as MeasuredWorkoutDecision.Proposed).journal
        assertEquals(finished,MeasuredWorkoutJournalDomain.replay(
            finished.approvedPlan,finished.receipts))
        assertEquals(plan,finished.bySetKey.getValue(plan.setKey).immutablePlan)
        val retry=MeasuredWorkoutJournalDomain.reduce(finished,
            MeasuredWorkoutOperation.Finish("finish",2)) as MeasuredWorkoutDecision.Proposed
        assertTrue(retry.replayed)
        assertEquals(finished,retry.journal)
        val retained=(finished.receipts.first().operation as
            MeasuredWorkoutOperation.Record).actual!!
        assertEquals(listOf(15,15),retained.sides.map{it.amount})
        assertThrows(UnsupportedOperationException::class.java) {
            (retained.sides as MutableList<MeasuredSideResult>).clear()
        }
    }

    @Test fun staleCorrectionPointerCannotDisplayAnEarlierResultOrProveImprovement() {
        val initial=checkNotNull(MeasuredExerciseJournalDomain.begin(caller("plank").plan))
        val first=record(initial,amount=40)
        val corrected=record(first,"correct",5,"record")
        val stale=corrected.copy(effectiveRecord=first.effectiveRecord)
        assertEquals(MeasuredSetReviewStatus.UNVERIFIED,
            MeasuredWorkoutResultReviewDomain.review(initial.immutablePlan,stale).status)
        val pendingForgery=corrected.copy(effectiveRecord=null)
        assertEquals(MeasuredSetReviewStatus.UNVERIFIED,
            MeasuredWorkoutResultReviewDomain.review(initial.immutablePlan,pendingForgery).status)
        val baseline=finish(record(initial,amount=20))
        assertEquals(MeasuredComparison.NOT_COMPARABLE,
            MeasuredExerciseComparisonDomain.compare(baseline,finish(stale)))
        val fakeFinish=first.copy(finishedByEventId="not-a-receipt")
        assertEquals(MeasuredComparison.NOT_COMPARABLE,
            MeasuredExerciseComparisonDomain.compare(baseline,fakeFinish))
        assertEquals(MeasuredComparison.MIXED_OR_LOWER,
            MeasuredExerciseComparisonDomain.compare(baseline,finish(corrected)))
    }

    @Test fun focusCannotCountCachedRecordsThatDisagreeWithParentReceipts() {
        val p=caller("plank").plan
        val initial=checkNotNull(MeasuredWorkoutJournalDomain.begin(
            MeasuredWorkoutPlan(p.workoutId,p.gymId,p.gymRevision,listOf(p))))
        val child=record(initial.bySetKey.getValue(p.setKey))
        val fabricated=initial.copy(bySetKey=mapOf(p.setKey to child))
        assertNull(MeasuredWorkoutFocusDomain.review(fabricated,null))
        val operation=MeasuredWorkoutOperation.Record("record",0,p.setKey,
            MeasuredSetOutcome.COMPLETED,actual(p))
        val committed=(MeasuredWorkoutJournalDomain.reduce(initial,operation)
            as MeasuredWorkoutDecision.Proposed).journal
        val dropped=committed.copy(bySetKey=initial.bySetKey)
        assertNull(MeasuredWorkoutFocusDomain.review(dropped,null))
        assertNull(MeasuredWorkoutFocusDomain.afterDurableRecord(
            fabricated,committed,p.setKey))
        assertNull(MeasuredWorkoutFocusDomain.afterDurableRecord(
            initial,dropped,p.setKey))
        assertTrue(checkNotNull(MeasuredWorkoutFocusDomain.review(committed,null)).readyToFinish)
    }
}
