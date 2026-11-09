package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.LoadBasis
import io.github.extracteddeath.pursuitiron.rebuild.LoadUnit
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeStrengthClubDomainTest {
    private fun fact(id:String,load:String,reps:Int,unit:LoadUnit=LoadUnit.LB,
        basis:LoadBasis=LoadBasis.BARBELL_TOTAL)=NativeMixedCompletedLoadFact(
        key=NativeMixedPhysicalLoadKey(id,unit,basis),
        actualLoad=BigDecimal(load),reps=reps,
        workoutId="w-$id",completionOrder=1L,setKey="s-$id",
        customSlotId=null,effectiveEventId="e-$id"
    )
    private fun history(vararg rows:NativeMixedCompletedLoadFact)=
        NativeKotlinMixedHistoryFacts(
            athleteId="verified-owner",manifestRevision=1L,
            completedWorkoutIds=listOf("w"),
            lastByExercise=emptyMap(),highestCompletedLoad=emptyMap(),
            mostRepsAtLoad=rows.toList()
        )
    @Test fun incompleteLiftEvidenceCannotClaimATotal() {
        val one=NativeStrengthClubDomain.review(history(fact("barbell_bench","300",1)))
        assertNull(one.loggedSinglesTotalLb)
        assertNull(one.projectedTotalLb)
        assertFalse(one.hasLoggedThousandPoundTotal)
        assertEquals(1,one.loggedSinglesByLiftLb.size)
    }
    @Test fun onlyThreeExactBarbellLoggedSinglesCanCrossMilestone() {
        val real=NativeStrengthClubDomain.review(history(
            fact("barbell_bench","300",1),
            fact("back_squat","360",1),
            fact("conventional_deadlift","340",1),
            fact("barbell_bench","999",1,basis=LoadBasis.SMITH_TOTAL),
            fact("dumbbell_bench","9999",1)
        ))
        assertEquals(0,real.loggedSinglesTotalLb!!.compareTo(BigDecimal("1000")))
        assertTrue(real.hasLoggedThousandPoundTotal)
        assertEquals(3,real.loggedSinglesByLiftLb.size)
    }
    @Test fun estimatedTenRepTotalNeverGrantsLoggedSingleAward() {
        val estimates=NativeStrengthClubDomain.review(history(
            fact("barbell_bench","250",10),
            fact("back_squat","300",10),
            fact("conventional_deadlift","320",10)
        ))
        assertNotNull(estimates.projectedTotalLb)
        assertTrue(estimates.projectedTotalLb!!>BigDecimal("1000"))
        assertNull(estimates.loggedSinglesTotalLb)
        assertFalse(estimates.hasLoggedThousandPoundTotal)
    }
    @Test fun kilogramsNormalizeBeforeSummingAndInvalidRepRangesDoNotCount() {
        val normalized=NativeStrengthClubDomain.review(history(
            fact("barbell_bench","100",1,LoadUnit.KG),
            fact("back_squat","400",1),
            fact("conventional_deadlift","380",1),
            fact("barbell_bench","999",11)
        ))
        assertTrue(normalized.loggedSinglesTotalLb!!>BigDecimal("1000"))
        assertTrue(normalized.loggedSinglesTotalLb!!<BigDecimal("1001"))
        assertEquals(0,normalized.projectedByLiftLb.getValue("barbell_bench")
            .compareTo(normalized.loggedSinglesByLiftLb.getValue("barbell_bench")))
    }
    @Test fun correctedEvidenceReplacesOldHighRatherThanCachedAchievement() {
        val old=NativeStrengthClubDomain.review(history(
            fact("barbell_bench","400",1),fact("back_squat","400",1),
            fact("conventional_deadlift","400",1)))
        val corrected=NativeStrengthClubDomain.review(history(
            fact("barbell_bench","200",1),fact("back_squat","300",1),
            fact("conventional_deadlift","350",1)))
        assertTrue(old.hasLoggedThousandPoundTotal)
        assertFalse(corrected.hasLoggedThousandPoundTotal)
        assertEquals(0,corrected.loggedSinglesTotalLb!!.compareTo(BigDecimal("850")))
    }
}
