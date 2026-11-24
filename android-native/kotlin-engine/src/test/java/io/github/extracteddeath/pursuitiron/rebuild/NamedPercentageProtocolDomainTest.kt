package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NamedPercentageProtocolDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val scope=BasicProgressionScope(
        "athlete","cycle","gym","strength","day","press",
        "machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
    private val stock=VerifiedLoadStock.Stack(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,n("5"),n("5"),n("500"))

    private fun request(
        scheme:NamedPercentageScheme, week:Int=1, count:Int=3,
        tier:PercentageTier=PercentageTier.PRIMARY,
        role:PercentageDayRole=PercentageDayRole.STANDARD,
        blockWeeks:Int=4
    )=NamedPercentageRequest(scope,scheme,tier,role,
        NamedPercentageProtocolDomain.referenceKind(scheme),
        n("200"),true,week,blockWeeks,count,stock)

    private fun review(request:NamedPercentageRequest)=
        NamedPercentageProtocolDomain.review(request) as NamedPercentageResult.Candidate

    private fun weights(result:NamedPercentageResult.Candidate)=
        result.sets.map{it.verifiedLoad}
    private fun reps(result:NamedPercentageResult.Candidate)=
        result.sets.map{it.minimumReps}
    private fun repOuts(result:NamedPercentageResult.Candidate)=
        result.sets.map{it.repOut}
    private fun numbers(vararg values:String)=values.map(::n)

    @Test fun fiveThreeOneWaveUsesDistinctLoadsAndNeverWrapsPastTheBlock() {
        val scheme=NamedPercentageScheme.FIVE_THREE_ONE
        val first=review(request(scheme))
        assertEquals(numbers("130","150","170"),weights(first))
        assertEquals(listOf(5,5,5),reps(first))
        assertEquals(listOf(false,false,true),repOuts(first))
        val second=review(request(scheme,week=2))
        assertEquals(numbers("140","160","180"),weights(second))
        assertEquals(listOf(3,3,3),reps(second))
        val third=review(request(scheme,week=3))
        assertEquals(numbers("150","170","190"),weights(third))
        assertEquals(listOf(5,3,1),reps(third))
        for(week in listOf(4,5,6,Int.MAX_VALUE)) {
            val recovery=review(request(scheme,week=week))
            assertTrue(recovery.recovery)
            assertEquals(numbers("80","100","120"),weights(recovery))
            assertFalse(recovery.sets.any{it.repOut})
        }
        assertFalse(first.mayIssueWorkout)
        assertFalse(first.mayModifyHistory)
        assertFalse(first.maySaveProgram)
        assertFalse(first.mayChangeReference)
    }

    @Test fun beginnerRetainsFivesProAndWeekSpecificFirstSetLast() {
        val scheme=NamedPercentageScheme.FIVE_THREE_ONE_BEGINNER
        for(week in 1..4) {
            val primary=review(request(scheme,week))
            assertEquals(listOf(5,5,5),reps(primary))
            assertFalse(primary.sets.any{it.repOut})
            val secondary=review(request(scheme,week,5,PercentageTier.SECONDARY))
            assertEquals(List(5){primary.sets.first().verifiedLoad},weights(secondary))
            assertEquals(List(5){5},reps(secondary))
            assertFalse(secondary.sets.any{it.repOut})
        }
        val recovery=review(request(scheme,5,3,PercentageTier.SECONDARY))
        assertEquals(numbers("80","100","120"),weights(recovery))
        assertTrue(recovery.recovery)
    }

    @Test fun madcowAndTexasUseConfirmedTopFiveRepReferenceAndLinearWeekIntent() {
        val ramp=review(request(NamedPercentageScheme.MADCOW,2,5))
        assertEquals(numbers("100","125","150","175","205"),weights(ramp))
        assertEquals(listOf(5,5,5,5,5),reps(ramp))
        assertEquals(0,n("205").compareTo(ramp.sets.last().rawCalculatedLoad))
        val texas=NamedPercentageScheme.TEXAS
        assertEquals(List(5){n("180")},weights(review(request(
            texas,1,5,role=PercentageDayRole.TEXAS_VOLUME))))
        assertEquals(numbers("160","160"),weights(review(request(
            texas,1,2,role=PercentageDayRole.TEXAS_RECOVERY))))
        val intensity=review(request(texas,2,1,role=PercentageDayRole.TEXAS_INTENSITY))
        assertEquals(numbers("200"),weights(intensity))
        assertEquals(0,n("203").compareTo(intensity.sets.single().rawCalculatedLoad))
        assertEquals(listOf(true),repOuts(intensity))
    }

    @Test fun nsunsBudgetKeepsInteriorPeakAndFinalRepOutInOrder() {
        val scheme=NamedPercentageScheme.NSUNS
        val full=review(request(scheme,1,9))
        assertEquals(numbers("150","170","190","180","170","160","150","140","130"),weights(full))
        assertEquals(listOf(5,3,1,3,3,5,5,5,5),reps(full))
        assertEquals(listOf(3,9),full.sets.filter{it.repOut}.map{it.sourceOrdinal})
        val two=review(request(scheme,1,2))
        assertTrue(two.adapted)
        assertEquals(9,two.sourceSetCount)
        assertEquals(listOf(3,9),two.sets.map{it.sourceOrdinal})
        assertEquals(numbers("190","130"),weights(two))
        assertEquals(listOf(true,true),repOuts(two))
        val three=review(request(scheme,1,3))
        assertEquals(listOf(1,3,9),three.sets.map{it.sourceOrdinal})
        val expanded=review(request(scheme,1,11))
        assertEquals(listOf(1,1,1,2,3,4,5,6,7,8,9),expanded.sets.map{it.sourceOrdinal})
        assertFalse(expanded.sets.first().repOut)
    }

    @Test fun gvtRipplerAndJtRetainDistinctWeekAndTierIntent() {
        val gvt=review(request(NamedPercentageScheme.GVT,2,10))
        assertEquals(List(10){n("120")},weights(gvt))
        assertEquals(List(10){10},reps(gvt))
        assertEquals(0,n("122.4").compareTo(gvt.sets.first().rawCalculatedLoad))
        val rippler=NamedPercentageScheme.RIPPLER
        val base=review(request(rippler,2,5))
        assertEquals(listOf(4,3,2,3,4),reps(base))
        assertEquals(numbers("145","160","170","160","150"),weights(base))
        val intensity=review(request(rippler,3,5))
        assertEquals(listOf(3,2,1,2,3),reps(intensity))
        val second=review(request(rippler,3,3,PercentageTier.SECONDARY))
        assertEquals(listOf(8,6,8),reps(second))
        assertEquals(numbers("130","140","130"),weights(second))
        val jt=NamedPercentageScheme.JT
        val primary=review(request(jt,1,6))
        assertEquals(listOf(5,3,2,1,3,5),reps(primary))
        assertEquals(listOf(4,6),primary.sets.filter{it.repOut}.map{it.sourceOrdinal})
        assertEquals(numbers("140","160","175","185","165","150"),weights(primary))
        assertEquals(listOf(10,8,6),reps(review(request(jt,1,3,PercentageTier.SECONDARY))))
    }

    @Test fun explicitGenericRecoveryFreezesCreepAndRemovesEveryRepOut() {
        for(scheme in listOf(NamedPercentageScheme.MADCOW,NamedPercentageScheme.NSUNS,
            NamedPercentageScheme.TEXAS,NamedPercentageScheme.GVT,
            NamedPercentageScheme.RIPPLER,NamedPercentageScheme.JT)) {
            val role=if(scheme==NamedPercentageScheme.TEXAS)
                PercentageDayRole.TEXAS_INTENSITY else PercentageDayRole.STANDARD
            val candidate=review(request(scheme,5,2,role=role))
            assertTrue(candidate.recovery)
            assertFalse(candidate.sets.any{it.repOut})
            for(set in candidate.sets) {
                assertEquals(0,set.sourcePercent.multiply(n("0.6")).compareTo(set.effectivePercent))
                assertTrue(set.verifiedLoad<=set.rawCalculatedLoad)
            }
        }
    }

    @Test fun sharedPhysicalAuthorityRefusesUnknownAndIncompatibleStock() {
        val base=request(NamedPercentageScheme.FIVE_THREE_ONE)
        assertEquals(NamedPercentageResult.Refused(NamedPercentageRefusal.INVALID_SCOPE_OR_REFERENCE),
            NamedPercentageProtocolDomain.review(base.copy(referenceConfirmed=false)))
        assertEquals(NamedPercentageResult.Refused(NamedPercentageRefusal.WRONG_REFERENCE_KIND),
            NamedPercentageProtocolDomain.review(base.copy(referenceKind=PercentageReferenceKind.ONE_REP_MAX)))
        assertEquals(NamedPercentageResult.Refused(NamedPercentageRefusal.INVALID_WEEK_OR_BUDGET),
            NamedPercentageProtocolDomain.review(base.copy(approvedSetBudget=21)))
        assertEquals(NamedPercentageResult.Refused(NamedPercentageRefusal.INCOMPATIBLE_DAY_ROLE),
            NamedPercentageProtocolDomain.review(request(NamedPercentageScheme.TEXAS)))
        assertEquals(NamedPercentageResult.Refused(NamedPercentageRefusal.TIERED_HISTORY_REQUIRED),
            NamedPercentageProtocolDomain.review(request(NamedPercentageScheme.GZCLP)))
        val wrongUnit=base.copy(scope=scope.copy(unit=LoadUnit.KG))
        assertEquals(NamedPercentageResult.Refused(NamedPercentageRefusal.PHYSICAL_LOAD_REFUSED,
            BasicProgrammedLoadRefusal.UNVERIFIED_GYM_STOCK),NamedPercentageProtocolDomain.review(wrongUnit))
        assertEquals(NamedPercentageResult.Refused(NamedPercentageRefusal.PHYSICAL_LOAD_REFUSED,
            BasicProgrammedLoadRefusal.NO_SAFE_AT_OR_BELOW_LOAD),
            NamedPercentageProtocolDomain.review(base.copy(confirmedReference=n("1"))))
        val odd=review(base.copy(confirmedReference=n("203.3")))
        assertEquals(numbers("130","150","170"),weights(odd))
        assertEquals(0,n("132.145").compareTo(odd.sets.first().rawCalculatedLoad))
        assertTrue(odd.sets.all{it.verifiedLoad<=it.rawCalculatedLoad})
    }
}
