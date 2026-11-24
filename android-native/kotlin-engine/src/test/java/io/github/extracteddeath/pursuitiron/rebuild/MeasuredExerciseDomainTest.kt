package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MeasuredExerciseDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private fun plan(id:String,gym:String="gym-one",
        setup:String="setup-one"):MeasuredExerciseSetPlan {
        val d=MeasuredExerciseCatalogDomain.find(id)!!
        val sides=when(d.sideRule) {
            MeasuredSideRule.BOTH ->setOf(MeasuredSide.BOTH)
            MeasuredSideRule.LEFT_AND_RIGHT ->setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE ->setOf(MeasuredSide.LEFT)
        }
        val loaded=d.loadKind==MeasuredLoadKind.VERIFIED_ADDED_LOAD ||
            d.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND
        val band=d.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT
        return MeasuredExerciseSetPlan("workout-1","set-1",id,gym,1,setup,
            when(d.kind) {
                MeasuredExerciseKind.HOLD_SECONDS ->30
                MeasuredExerciseKind.CARRY_CENTIMETERS ->2000
                MeasuredExerciseKind.BANDED_REPETITIONS ->10
            },sides,d.requiredFixtures,
            if(loaded)VerifiedLoadStock.Totals(LoadUnit.LB,
                checkNotNull(d.expectedBasis),listOf(n("10"),n("20"),n("30")))
            else null,
            if(loaded)n("20") else null,
            if(loaded)LoadUnit.LB else null,
            id=="farmers",
            if(band)"band-blue" else null,
            if(band)setOf("band-blue") else emptySet())
    }
    private fun actual(p:MeasuredExerciseSetPlan,amount:Int=15,
        weight:String="20.00"):MeasuredExerciseActual {
        val d=MeasuredExerciseCatalogDomain.find(p.exerciseId)!!
        val loaded=d.loadKind==MeasuredLoadKind.VERIFIED_ADDED_LOAD ||
            d.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND
        return MeasuredExerciseActual(p.physicalSetupId,
            p.sides.sortedBy{it.ordinal}.map{MeasuredSideResult(it,amount)},
            if(loaded)n(weight) else null,
            if(loaded)LoadUnit.LB else null,p.bandId,
            if(d.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)22 else null)
    }
    private fun record(j:MeasuredExerciseJournal,a:MeasuredExerciseActual,
        event:String="record",corrects:String?=null):MeasuredExerciseJournal {
        val result=MeasuredExerciseJournalDomain.reduce(j,
            MeasuredJournalOperation.Record(event,j.revision,
                MeasuredSetOutcome.COMPLETED,a,corrects))
        assertTrue(result.toString(),result is MeasuredJournalDecision.Proposed)
        return (result as MeasuredJournalDecision.Proposed).journal
    }
    private fun finish(j:MeasuredExerciseJournal):MeasuredExerciseJournal {
        val result=MeasuredExerciseJournalDomain.reduce(j,
            MeasuredJournalOperation.Finish("finish-"+j.revision,j.revision))
        assertTrue(result.toString(),result is MeasuredJournalDecision.Proposed)
        return (result as MeasuredJournalDecision.Proposed).journal
    }
    private fun completed(p:MeasuredExerciseSetPlan,amount:Int,
        weight:String="20"):MeasuredExerciseJournal =
        finish(record(MeasuredExerciseJournalDomain.begin(p)!!,
            actual(p,amount,weight)))

    @Test fun all14WebIdentitiesAreRealDistinctTypedCandidates() {
        val expected=setOf("superman","plank","side-plank","plate-pinch",
            "dead-hang","hollow-hold","copenhagen","wall-sit","l-sit",
            "weighted-plank","towel-hang","farmers","suitcase-carry",
            "band-pushup")
        val all=MeasuredExerciseCatalogDomain.all
        assertEquals(expected,all.map{it.exerciseId}.toSet())
        assertEquals(14,all.size)
        assertEquals(11,all.count{it.kind==MeasuredExerciseKind.HOLD_SECONDS})
        assertEquals(2,all.count{it.kind==MeasuredExerciseKind.CARRY_CENTIMETERS})
        assertEquals(1,all.count{it.kind==MeasuredExerciseKind.BANDED_REPETITIONS})
        assertEquals(setOf("plate-pinch","weighted-plank"),
            all.filter{it.loadKind==MeasuredLoadKind.VERIFIED_ADDED_LOAD}
                .map{it.exerciseId}.toSet())
        assertEquals(setOf("farmers","suitcase-carry"),
            all.filter{it.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND}
                .map{it.exerciseId}.toSet())
        for(d in all) {
            val p=plan(d.exerciseId)
            assertTrue(d.exerciseId,MeasuredExerciseDomain.inspect(p)
                is MeasurementPlanResult.Candidate)
            assertTrue(d.exerciseId,MeasuredExerciseDomain.validActual(
                p,MeasuredSetOutcome.COMPLETED,actual(p)))
            assertFalse(p.canIssueWorkout)
            assertFalse(p.canWriteHistory)
        }
    }

    @Test fun timedDistanceAndSideMeasurementCannotMasqueradeAsReps() {
        val side=plan("side-plank")
        assertEquals(MeasurementRefusal.INCORRECT_SIDES,
            (MeasuredExerciseDomain.inspect(side.copy(sides=setOf(MeasuredSide.LEFT)))
                as MeasurementPlanResult.Rejected).reason)
        assertFalse(MeasuredExerciseDomain.validActual(side,
            MeasuredSetOutcome.COMPLETED,actual(side).copy(
                sides=listOf(MeasuredSideResult(MeasuredSide.LEFT,15)))))
        assertEquals(MeasurementRefusal.INVALID_QUANTITY,
            (MeasuredExerciseDomain.inspect(side.copy(targetAmount=3601))
                as MeasurementPlanResult.Rejected).reason)
        assertEquals(MeasurementRefusal.INVALID_QUANTITY,
            (MeasuredExerciseDomain.inspect(plan("farmers").copy(targetAmount=0))
                as MeasurementPlanResult.Rejected).reason)
        assertEquals(MeasurementRefusal.INVALID_QUANTITY,
            (MeasuredExerciseDomain.inspect(plan("band-pushup").copy(targetAmount=101))
                as MeasurementPlanResult.Rejected).reason)
        assertFalse(MeasuredExerciseDomain.validActual(side,
            MeasuredSetOutcome.COMPLETED,actual(side).copy(elapsedSeconds=22)))
        assertFalse(MeasuredExerciseDomain.validActual(side,
            MeasuredSetOutcome.FAILED_ATTEMPT,actual(side)))
        assertTrue(MeasuredExerciseDomain.validActual(side,
            MeasuredSetOutcome.FAILED_ATTEMPT,actual(side).copy(
                sides=listOf(MeasuredSideResult(MeasuredSide.LEFT,0),
                    MeasuredSideResult(MeasuredSide.RIGHT,0)))))
        assertTrue(MeasuredExerciseDomain.validActual(side,
            MeasuredSetOutcome.SKIPPED,null))
        assertFalse(MeasuredExerciseDomain.validActual(side,
            MeasuredSetOutcome.SKIPPED,actual(side)))
    }

    @Test fun stocksAndFixtureNamesArePhysicalNotInvented() {
        for(id in listOf("plate-pinch","weighted-plank","farmers","suitcase-carry")) {
            val p=plan(id)
            assertEquals(MeasurementRefusal.UNVERIFIED_STOCK,
                (MeasuredExerciseDomain.inspect(p.copy(physicalStock=null))
                    as MeasurementPlanResult.Rejected).reason)
            assertEquals(MeasurementRefusal.UNVERIFIED_STOCK,
                (MeasuredExerciseDomain.inspect(p.copy(intendedExternalLoad=n("17")))
                    as MeasurementPlanResult.Rejected).reason)
            assertEquals(MeasurementRefusal.UNVERIFIED_STOCK,
                (MeasuredExerciseDomain.inspect(p.copy(unit=LoadUnit.KG))
                    as MeasurementPlanResult.Rejected).reason)
            assertFalse(MeasuredExerciseDomain.validActual(p,
                MeasuredSetOutcome.COMPLETED,actual(p,weight="17")))
            assertFalse(MeasuredExerciseDomain.validActual(p,
                MeasuredSetOutcome.COMPLETED,actual(p).copy(unit=LoadUnit.KG)))
            assertTrue(MeasuredExerciseDomain.validActual(p,
                MeasuredSetOutcome.COMPLETED,actual(p,weight="30.000")))
        }
        assertEquals(MeasurementRefusal.UNVERIFIED_STOCK,
            (MeasuredExerciseDomain.inspect(plan("farmers").copy(
                matchedPairConfirmed=false)) as MeasurementPlanResult.Rejected).reason)
        assertEquals(MeasurementRefusal.MISSING_FIXTURE,
            (MeasuredExerciseDomain.inspect(plan("plate-pinch").copy(
                confirmedFixtures=emptySet())) as MeasurementPlanResult.Rejected).reason)
        assertEquals(MeasurementRefusal.MISSING_FIXTURE,
            (MeasuredExerciseDomain.inspect(plan("towel-hang").copy(
                confirmedFixtures=setOf("pull-up-bar")))
                as MeasurementPlanResult.Rejected).reason)
    }

    @Test fun physicalZeroSelectorCannotBecomeAnIssuedMeasuredLoad() {
        // A stock selector proves attainability, not the right to issue an
        // unloaded weighted-plank, plate-pinch, or physical carry.
        for(id in listOf("plate-pinch","weighted-plank",
                "farmers","suitcase-carry")) {
            val original=plan(id)
            val def=checkNotNull(MeasuredExerciseCatalogDomain.find(id))
            val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
                checkNotNull(def.expectedBasis),
                listOf(n("0"),n("20")))
            val zero=original.copy(physicalStock=stock,
                intendedExternalLoad=n("0.000"))
            val proof=EquipmentLoadDomain.inspect(stock,n("0"),LoadUnit.LB)
            assertTrue(id,proof is LoadInspection.Confirmed && proof.exact)
            assertEquals(id,MeasurementRefusal.UNVERIFIED_STOCK,
                (MeasuredExerciseDomain.inspect(zero)
                    as MeasurementPlanResult.Rejected).reason)
            assertNull(id,MeasuredExerciseJournalDomain.begin(zero))
            // A corrected athlete-observed zero is a separate physical fact:
            // it cannot turn the ORIGINAL positive prescribed load into zero.
            val positive=zero.copy(intendedExternalLoad=n("20"))
            assertTrue(id,MeasuredExerciseDomain.inspect(positive)
                is MeasurementPlanResult.Candidate)
            assertTrue(id,MeasuredExerciseDomain.validActual(positive,
                MeasuredSetOutcome.COMPLETED,
                actual(positive,weight="0.0")))
            assertEquals(n("20"),positive.intendedExternalLoad)
        }
    }

    @Test fun bandAssistedRepsHaveNoFictitiousWeightEquivalent() {
        val p=plan("band-pushup")
        assertTrue(MeasuredExerciseDomain.validActual(p,
            MeasuredSetOutcome.COMPLETED,actual(p)))
        assertEquals(MeasurementRefusal.UNVERIFIED_BAND,
            (MeasuredExerciseDomain.inspect(p.copy(bandId="band-red"))
                as MeasurementPlanResult.Rejected).reason)
        assertEquals(MeasurementRefusal.WRONG_MEASUREMENT_OR_LOAD,
            (MeasuredExerciseDomain.inspect(p.copy(
                intendedExternalLoad=n("20"))) as MeasurementPlanResult.Rejected).reason)
        assertFalse(MeasuredExerciseDomain.validActual(p,
            MeasuredSetOutcome.COMPLETED,actual(p).copy(bandId="band-red")))
        assertFalse(MeasuredExerciseDomain.validActual(p,
            MeasuredSetOutcome.COMPLETED,actual(p).copy(
                actualExternalLoad=n("20"),unit=LoadUnit.LB)))
    }

    @Test fun proposedJournalReplaysCorrectionsAndFinishWithoutChangingTargets() {
        val p=plan("weighted-plank")
        val initial=MeasuredExerciseJournalDomain.begin(p)!!
        assertFalse(initial.persisted)
        assertFalse(initial.mayChangeTrainingProgram)
        assertEquals(MeasurementRefusal.INCOMPLETE,
            (MeasuredExerciseJournalDomain.reduce(initial,
                MeasuredJournalOperation.Finish("early",0))
                as MeasuredJournalDecision.Rejected).reason)
        val firstActual=actual(p,20,weight="20.0")
        val firstOp=MeasuredJournalOperation.Record("record",0,
            MeasuredSetOutcome.COMPLETED,firstActual)
        val first=MeasuredExerciseJournalDomain.reduce(initial,firstOp)
            as MeasuredJournalDecision.Proposed
        assertEquals(1,first.journal.revision)
        val retry=MeasuredExerciseJournalDomain.reduce(first.journal,firstOp)
            as MeasuredJournalDecision.Proposed
        assertTrue(retry.replayed)
        assertEquals(first.journal,retry.journal)
        assertEquals(MeasurementRefusal.EVENT_COLLISION,
            (MeasuredExerciseJournalDomain.reduce(first.journal,firstOp.copy(
                actual=actual(p,21))) as MeasuredJournalDecision.Rejected).reason)
        assertEquals(MeasurementRefusal.STALE_REVISION,
            (MeasuredExerciseJournalDomain.reduce(first.journal,
                MeasuredJournalOperation.Finish("stale",0))
                as MeasuredJournalDecision.Rejected).reason)
        val corrected=record(first.journal,actual(p,22,weight="20.00"),
            "correction","record")
        assertEquals(p,corrected.immutablePlan)
        assertEquals(22,((corrected.effectiveRecord!!.operation
            as MeasuredJournalOperation.Record).actual!!.sides.single().amount))
        val final=finish(corrected)
        assertEquals(3,final.receipts.size)
        assertNotNull(final.finishedByEventId)
        assertEquals(final,MeasuredExerciseJournalDomain.replay(p,final.receipts))
        assertNull(MeasuredExerciseJournalDomain.replay(p,final.receipts.reversed()))
        assertEquals(MeasurementRefusal.FINISHED,
            (MeasuredExerciseJournalDomain.reduce(final,
                MeasuredJournalOperation.Record("late",final.revision,
                    MeasuredSetOutcome.COMPLETED,actual(p)))
                as MeasuredJournalDecision.Rejected).reason)
    }

    @Test fun comparisonCannotCrossGymMachineBandMassOrIncompleteSides() {
        val plank=plan("plank")
        val first=completed(plank,30)
        assertEquals(MeasuredComparison.HARDER_OR_LONGER,
            MeasuredExerciseComparisonDomain.compare(first,completed(plank,45)))
        assertEquals(MeasuredComparison.SAME,
            MeasuredExerciseComparisonDomain.compare(first,completed(plank,30)))
        assertEquals(MeasuredComparison.MIXED_OR_LOWER,
            MeasuredExerciseComparisonDomain.compare(first,completed(plank,20)))
        assertEquals(MeasuredComparison.NOT_COMPARABLE,
            MeasuredExerciseComparisonDomain.compare(first,
                completed(plan("plank",setup="another-physical-setup"),45)))
        val plate=plan("plate-pinch")
        assertEquals(MeasuredComparison.NOT_COMPARABLE,
            MeasuredExerciseComparisonDomain.compare(completed(plate,20,"20"),
                completed(plate,30,"30")))
        assertEquals(MeasuredComparison.HARDER_OR_LONGER,
            MeasuredExerciseComparisonDomain.compare(completed(plate,20,"20.0"),
                completed(plate,30,"20.000")))
        val sides=plan("copenhagen")
        val prior=completed(sides,20)
        val changed=record(MeasuredExerciseJournalDomain.begin(sides)!!,
            actual(sides,25).copy(sides=listOf(
                MeasuredSideResult(MeasuredSide.LEFT,25),
                MeasuredSideResult(MeasuredSide.RIGHT,15))))
        assertEquals(MeasuredComparison.MIXED_OR_LOWER,
            MeasuredExerciseComparisonDomain.compare(prior,finish(changed)))
        val band=plan("band-pushup")
        assertEquals(MeasuredComparison.NOT_COMPARABLE,
            MeasuredExerciseComparisonDomain.compare(completed(band,10),
                completed(band.copy(bandId="band-red",
                    gymConfirmedBandIds=setOf("band-red")),11)))
    }
}
