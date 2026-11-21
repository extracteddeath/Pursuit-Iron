package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MeasuredExerciseEntryDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private fun plan(id:String):MeasuredExerciseSetPlan {
        val d=MeasuredExerciseCatalogDomain.find(id)!!
        val sides=when(d.sideRule) {
            MeasuredSideRule.BOTH->setOf(MeasuredSide.BOTH)
            MeasuredSideRule.LEFT_AND_RIGHT ->
                setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE->setOf(MeasuredSide.LEFT)
        }
        val loaded=d.loadKind==MeasuredLoadKind.VERIFIED_ADDED_LOAD ||
            d.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND
        val band=d.loadKind==MeasuredLoadKind.BANDED_BODYWEIGHT
        return MeasuredExerciseSetPlan(
            "workout-1","set-1",id,"gym-1",1,"setup-1",
            if(d.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)1500
            else if(band)12 else 30,
            sides,d.requiredFixtures,
            if(loaded)VerifiedLoadStock.Totals(LoadUnit.LB,
                checkNotNull(d.expectedBasis),
                listOf(n("10"),n("20"),n("30"))) else null,
            if(loaded)n("20") else null,
            if(loaded)LoadUnit.LB else null,
            id=="farmers",
            if(band)"band-blue" else null,
            if(band)setOf("band-blue") else emptySet())
    }
    private fun input(
        plan:MeasuredExerciseSetPlan,
        raw:String?=null,
        unit:MeasuredEntryUnit?=null,
        weight:String?=null
    ):MeasuredExerciseEntryInput {
        val d=MeasuredExerciseCatalogDomain.find(plan.exerciseId)!!
        val chosenUnit=unit ?:when(d.kind) {
            MeasuredExerciseKind.HOLD_SECONDS->MeasuredEntryUnit.MINUTES_SECONDS
            MeasuredExerciseKind.CARRY_CENTIMETERS->MeasuredEntryUnit.METERS
            MeasuredExerciseKind.BANDED_REPETITIONS->MeasuredEntryUnit.REPETITIONS
        }
        val value=raw ?:when(d.kind) {
            MeasuredExerciseKind.HOLD_SECONDS->"0:30"
            MeasuredExerciseKind.CARRY_CENTIMETERS->"15.00"
            MeasuredExerciseKind.BANDED_REPETITIONS->"12"
        }
        val loaded=d.loadKind==MeasuredLoadKind.VERIFIED_ADDED_LOAD ||
            d.loadKind==MeasuredLoadKind.VERIFIED_PER_HAND
        return MeasuredExerciseEntryInput(
            plan.sides.associateWith{value},chosenUnit,
            if(loaded)weight ?: "20.000" else null,
            if(loaded)LoadUnit.LB else null,
            plan.physicalSetupId,plan.bandId)
    }
    private fun accepted(
        result:MeasuredExerciseEntryDecision
    ):MeasuredExerciseEntryDecision.Accepted {
        assertTrue(result.toString(),result is MeasuredExerciseEntryDecision.Accepted)
        return result as MeasuredExerciseEntryDecision.Accepted
    }
    private fun refused(
        result:MeasuredExerciseEntryDecision,
        expected:MeasuredEntryRefusal
    ) {
        assertTrue(result.toString(),result is MeasuredExerciseEntryDecision.Rejected)
        assertEquals(expected,(result as MeasuredExerciseEntryDecision.Rejected).reason)
    }

    @Test fun all14PhysicallyVerifiedInputsBecomeCorrectTypedActuals() {
        for(d in MeasuredExerciseCatalogDomain.all) {
            val plan=plan(d.exerciseId)
            val out=accepted(MeasuredExerciseEntryDomain.parse(
                plan,MeasuredSetOutcome.COMPLETED,input(plan)))
            assertFalse(out.isSaved)
            assertFalse(out.mayPublishHistory)
            assertFalse(out.approximateDistanceConversion)
            assertEquals(plan.sides,out.convertedCentimetersBySide.keys)
            val actual=out.actual!!
            assertEquals(plan.physicalSetupId,actual.physicalSetupId)
            assertTrue(MeasuredExerciseDomain.validActual(
                plan,MeasuredSetOutcome.COMPLETED,actual))
            for(side in actual.sides) {
                assertEquals(when(d.kind) {
                    MeasuredExerciseKind.HOLD_SECONDS->30
                    MeasuredExerciseKind.CARRY_CENTIMETERS->1500
                    MeasuredExerciseKind.BANDED_REPETITIONS->12
                },side.amount)
            }
        }
    }

    @Test fun clockAndSecondsAreExplicitAndBoundedNotRepetitions() {
        val p=plan("side-plank")
        val minute=accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,input(p,raw="1:30")))
        assertEquals(setOf(90),minute.actual!!.sides.map{it.amount}.toSet())
        val seconds=accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,
            input(p,raw="90",unit=MeasuredEntryUnit.SECONDS)))
        assertEquals(minute.convertedCentimetersBySide,
            seconds.convertedCentimetersBySide)
        accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,input(p,raw="60:00")))
        for(value in listOf("00:60","61:00","1:2","-1:00","3601","0:00")) {
            val unit=if(value=="3601")MeasuredEntryUnit.SECONDS
                else MeasuredEntryUnit.MINUTES_SECONDS
            refused(MeasuredExerciseEntryDomain.parse(p,
                MeasuredSetOutcome.COMPLETED,input(p,raw=value,unit=unit)),
                MeasuredEntryRefusal.INVALID_QUANTITY)
        }
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,input(p,raw="30",
                unit=MeasuredEntryUnit.REPETITIONS)),
            MeasuredEntryRefusal.WRONG_QUANTITY_UNIT)
    }

    @Test fun positiveFeetCannotRoundIntoAFailedZeroDistanceReceipt() {
        val p=plan("farmers")
        for(outcome in listOf(MeasuredSetOutcome.FAILED_ATTEMPT,MeasuredSetOutcome.COMPLETED))
            refused(MeasuredExerciseEntryDomain.parse(p,outcome,
                input(p,raw="0.01",unit=MeasuredEntryUnit.FEET)),MeasuredEntryRefusal.INVALID_QUANTITY)
        accepted(MeasuredExerciseEntryDomain.parse(p,MeasuredSetOutcome.FAILED_ATTEMPT,
            input(p,raw="0.00",unit=MeasuredEntryUnit.FEET)))
    }

    @Test fun metersAndCentimetersAreExactAndFeetExplicitlyRound() {
        val p=plan("farmers")
        val meters=accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,input(p,raw="15.25")))
        assertEquals(1525,meters.actual!!.sides.single().amount)
        assertFalse(meters.approximateDistanceConversion)
        val cm=accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,input(p,raw="1525",
                unit=MeasuredEntryUnit.CENTIMETERS)))
        assertEquals(1525,cm.actual!!.sides.single().amount)
        assertFalse(cm.approximateDistanceConversion)
        val feet=accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,input(p,raw="2",
                unit=MeasuredEntryUnit.FEET)))
        assertEquals(61,feet.actual!!.sides.single().amount)
        assertTrue(feet.approximateDistanceConversion)
        // 25 ft exactly equals 762 cm; no rounding caveat.
        val exactFeet=accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,input(p,raw="25",
                unit=MeasuredEntryUnit.FEET)))
        assertEquals(762,exactFeet.actual!!.sides.single().amount)
        assertFalse(exactFeet.approximateDistanceConversion)
        for(value in listOf("15.001","15,2","-1","10001","00.1")) {
            refused(MeasuredExerciseEntryDomain.parse(
                p,MeasuredSetOutcome.COMPLETED,input(p,raw=value)),
                MeasuredEntryRefusal.INVALID_QUANTITY)
        }
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,input(p,raw="15",
                unit=MeasuredEntryUnit.SECONDS)),
            MeasuredEntryRefusal.WRONG_QUANTITY_UNIT)
    }

    @Test fun bothSidesAreRequiredAndPhysicalSetupCannotChange() {
        val p=plan("copenhagen")
        val baseline=input(p)
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,baseline.copy(quantityBySide=mapOf(
                MeasuredSide.LEFT to "0:30"))),
            MeasuredEntryRefusal.MISSING_OR_EXTRA_SIDE)
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,baseline.copy(
                physicalSetupId="different-machine")),
            MeasuredEntryRefusal.WRONG_PHYSICAL_SETUP)
        val different=accepted(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,baseline.copy(quantityBySide=mapOf(
                MeasuredSide.LEFT to "1:02",MeasuredSide.RIGHT to "0:48"))))
        assertEquals(mapOf(MeasuredSide.LEFT to 62,MeasuredSide.RIGHT to 48),
            different.convertedCentimetersBySide)
    }

    @Test fun verifiedLoadMustBeAthleteSubmittedAndPhysicallyAvailable() {
        for(id in listOf("plate-pinch","weighted-plank","farmers",
            "suitcase-carry")) {
            val p=plan(id)
            val baseline=input(p)
            refused(MeasuredExerciseEntryDomain.parse(p,
                MeasuredSetOutcome.COMPLETED,
                baseline.copy(externalLoadText=null)),
                MeasuredEntryRefusal.LOAD_ENTRY_REQUIRED)
            refused(MeasuredExerciseEntryDomain.parse(p,
                MeasuredSetOutcome.COMPLETED,
                baseline.copy(externalLoadText="19")),
                MeasuredEntryRefusal.UNVERIFIED_LOAD)
            refused(MeasuredExerciseEntryDomain.parse(p,
                MeasuredSetOutcome.COMPLETED,
                baseline.copy(externalLoadUnit=LoadUnit.KG)),
                MeasuredEntryRefusal.UNVERIFIED_LOAD)
            refused(MeasuredExerciseEntryDomain.parse(p,
                MeasuredSetOutcome.COMPLETED,
                baseline.copy(externalLoadText="20.0000")),
                MeasuredEntryRefusal.UNVERIFIED_LOAD)
            val acceptedDifferent=accepted(MeasuredExerciseEntryDomain.parse(p,
                MeasuredSetOutcome.COMPLETED,
                baseline.copy(externalLoadText="30.00")))
            assertEquals(n("30.00"),acceptedDifferent.actual!!.actualExternalLoad)
            assertEquals(n("20"),p.intendedExternalLoad)
        }
        val bodyweight=plan("plank")
        refused(MeasuredExerciseEntryDomain.parse(bodyweight,
            MeasuredSetOutcome.COMPLETED,input(bodyweight).copy(
                externalLoadText="20",externalLoadUnit=LoadUnit.LB)),
            MeasuredEntryRefusal.UNVERIFIED_LOAD)
    }

    @Test fun bandIdentityNeverBecomesMadeUpStackWeightOrDifferentMachine() {
        val p=plan("band-pushup")
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,
            input(p).copy(bandId="band-red")),
            MeasuredEntryRefusal.WRONG_OR_UNVERIFIED_BAND)
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,
            input(p).copy(externalLoadText="20",externalLoadUnit=LoadUnit.LB)),
            MeasuredEntryRefusal.UNVERIFIED_LOAD)
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,
            input(p,raw="12.0")),
            MeasuredEntryRefusal.INVALID_QUANTITY)
        assertEquals(12,accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,input(p))).actual!!.sides.single().amount)
    }

    @Test fun aCarryOptionalElapsedTimeNeverReplacesDistance() {
        val p=plan("suitcase-carry")
        val a=accepted(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,
            input(p).copy(carryElapsedSecondsText="38")))
        assertEquals(38,a.actual!!.elapsedSeconds)
        assertEquals(1500,a.actual.sides.single().amount)
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.COMPLETED,
            input(p).copy(carryElapsedSecondsText="0")),
            MeasuredEntryRefusal.INVALID_ELAPSED_TIME)
        val plank=plan("plank")
        refused(MeasuredExerciseEntryDomain.parse(plank,
            MeasuredSetOutcome.COMPLETED,
            input(plank).copy(carryElapsedSecondsText="20")),
            MeasuredEntryRefusal.INVALID_ELAPSED_TIME)
    }

    @Test fun failedHoldInClockModeUsesZeroMinutesSecondsForEverySide() {
        val p=plan("side-plank")
        val failure=accepted(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.FAILED_ATTEMPT,input(p,raw="0:00")))
        assertEquals(2,failure.actual!!.sides.size)
        assertTrue(failure.actual.sides.all{it.amount==0})
        assertFalse(failure.approximateDistanceConversion)
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.FAILED_ATTEMPT,input(p,raw="0")),
            MeasuredEntryRefusal.INVALID_QUANTITY)
    }

    @Test fun failureZeroAndSkipHaveExplicitSeparateSemantics() {
        val p=plan("farmers")
        val zero=accepted(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.FAILED_ATTEMPT,input(p,raw="0")))
        assertEquals(0,zero.actual!!.sides.single().amount)
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.FAILED_ATTEMPT,input(p,raw="15")),
            MeasuredEntryRefusal.INVALID_QUANTITY)
        val skipped=accepted(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.SKIPPED,null))
        assertNull(skipped.actual)
        refused(MeasuredExerciseEntryDomain.parse(p,
            MeasuredSetOutcome.SKIPPED,input(p)),
            MeasuredEntryRefusal.INVALID_ACTUAL)
    }

    @Test fun aReviewNeverWritesAthleteHistoryOrCertifiesAnUnapprovedSet() {
        val p=plan("plank")
        refused(MeasuredExerciseEntryDomain.parse(
            p.copy(sides=setOf(MeasuredSide.LEFT)),
            MeasuredSetOutcome.COMPLETED,input(p)),
            MeasuredEntryRefusal.UNAPPROVED_PLAN)
        val preview=accepted(MeasuredExerciseEntryDomain.parse(
            p,MeasuredSetOutcome.COMPLETED,input(p)))
        assertFalse(preview.isSaved)
        assertFalse(preview.mayPublishHistory)
    }
}
