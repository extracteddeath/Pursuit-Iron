package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinFutureLoadNumberAuthorityDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val external=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf("195","200","205","210","215").map(::n))
    private val assisted=VerifiedLoadStock.Stack(
        LoadUnit.LB,LoadBasis.ASSISTANCE_COUNTERWEIGHT,
        n("80"),n("5"),n("120"))

    private fun review(
        from:String="200",to:String="205",
        source:KotlinFutureLoadSource=KotlinFutureLoadSource.EARNED_NEXT_INCREMENT,
        stock:VerifiedLoadStock?=external,
        unit:LoadUnit=LoadUnit.LB,
        basis:LoadBasis=LoadBasis.MACHINE_STACK,
        limit:String="5"
    )=KotlinFutureLoadNumberAuthorityDomain.review(
        n(from),n(to),unit,basis,stock,n(limit),source)

    @Test fun zeroStockSelectorCannotBecomeOrdinaryIssuedOrFutureWorkingWeight() {
        val modes=listOf(LoadBasis.DUMBBELL_PER_HAND,
            LoadBasis.MACHINE_STACK,LoadBasis.CABLE_STACK,
            LoadBasis.EXERCISE_OVERRIDE,LoadBasis.SMITH_TOTAL,
            LoadBasis.ASSISTANCE_COUNTERWEIGHT)
        for(basis in modes) {
            val stock=VerifiedLoadStock.Totals(LoadUnit.LB,basis,
                listOf(n("0"),n("5"),n("10")))
            assertTrue("Stock actually offers zero: $basis",
                (EquipmentLoadDomain.inspect(stock,n("0"),LoadUnit.LB)
                    as LoadInspection.Confirmed).exact)
            assertFalse("Zero cannot be issued for $basis",
                KotlinSetNumberAuthorityDomain.validPrescribedLoad(n("0"),basis))
            assertEquals("$basis must refuse a forged zero issued target",
                KotlinFutureLoadReview.Rejected(
                    KotlinFutureLoadRefusal.UNVERIFIED_ISSUED_LOAD),
                review(from="0",to="5",stock=stock,basis=basis))
            assertEquals("$basis must not return to an invalid zero target",
                KotlinFutureLoadReview.Rejected(
                    KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD),
                review(from="5",to="0",stock=stock,basis=basis,
                    source=KotlinFutureLoadSource.RETURN_TO_DEMONSTRATED_LOAD))
        }
        val landmine=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.LANDMINE_ADDED_PLATES,
            listOf(n("0"),n("5"),n("10")))
        assertTrue(KotlinSetNumberAuthorityDomain.validPrescribedLoad(
            n("0"),LoadBasis.LANDMINE_ADDED_PLATES))
        val valid=review(from="0",to="5",stock=landmine,
            basis=LoadBasis.LANDMINE_ADDED_PLATES)
        assertTrue("Zero added landmine plates retains legitimate physical meaning",
            valid is KotlinFutureLoadReview.Accepted)
        assertEquals(n("5"),(valid as KotlinFutureLoadReview.Accepted).fact.proposedLoad)
        // The assisted terminal refusal remains more precise than a
        // generic stock failure; zero is not converted to unassisted reps.
        val assistedZero=assisted.copy(minimum=n("0"))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.NO_LOWER_ASSISTED_SETTING),
            review(from="5",to="0",stock=assistedZero,
                basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT,
                source=KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT))
    }

    @Test fun earnedIncrementUsesOnlyImmediateVerifiedNextStockAndNeverWrites() {
        val ready=review() as KotlinFutureLoadReview.Accepted
        assertEquals(n("200"),ready.fact.issuedLoad)
        assertEquals(n("205"),ready.fact.proposedLoad)
        assertEquals(LoadBasis.MACHINE_STACK,ready.fact.basis)
        assertEquals(KotlinFutureLoadSource.EARNED_NEXT_INCREMENT,ready.fact.source)
        assertFalse(ready.fact.mayChangeIssuedWorkout)
        assertFalse(ready.fact.mayModifyHistory)
        assertFalse(ready.fact.maySaveFutureProgram)
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.NOT_ADJACENT_VERIFIED_INCREMENT),
            review(to="210",limit="20"))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.EXCEEDS_AUTHORIZED_INCREMENT),
            review(limit="2.5"))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.WRONG_CHANGE_DIRECTION),
            review(to="195"))
    }

    @Test fun verifiedRecoveryCanReturnToEarlierDemonstratedLoadNotInventAnIncrease() {
        val recovery=KotlinFutureLoadSource.RETURN_TO_DEMONSTRATED_LOAD
        assertTrue(review(from="210",to="195",source=recovery) is
            KotlinFutureLoadReview.Accepted)
        // The historical coach must verify the load was demonstrated.
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.WRONG_CHANGE_DIRECTION),
            review(from="205",to="210",source=recovery))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD),
            review(from="210",to="203",source=recovery))
    }

    @Test fun everyLoadBasisHasOnePhysicalDifficultyDirection() {
        for(basis in LoadBasis.entries) {
            val inverse=basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT
            assertEquals(!inverse,basis.higherSettingIncreasesDifficulty)
            assertEquals(if(inverse)-1 else 1,basis.compareDifficulty(n("105"),n("100")))
            assertEquals(if(inverse)1 else -1,basis.compareDifficulty(n("95"),n("100")))
            assertEquals(0,basis.compareDifficulty(n("100.00"),n("100")))
        }
        val weighted=EquipmentLoadDomain.inspect(external,n("200"),LoadUnit.LB)
            as LoadInspection.Confirmed
        assertEquals(n("205"),weighted.nextHarder)
        assertEquals(n("195"),weighted.nextEasier)
        val counterweight=EquipmentLoadDomain.inspect(assisted,n("100"),LoadUnit.LB)
            as LoadInspection.Confirmed
        assertEquals(n("95"),counterweight.nextHarder)
        assertEquals(n("105"),counterweight.nextEasier)
        val assistedFloor=EquipmentLoadDomain.inspect(assisted,n("80"),LoadUnit.LB)
            as LoadInspection.Confirmed
        assertNull(assistedFloor.nextHarder)
        assertEquals(n("85"),assistedFloor.nextEasier)
    }

    @Test fun assistanceHasExactlyTheOppositeVerifiedPhysicalDirection() {
        val source=KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT
        val ready=review(from="100",to="95",source=source,stock=assisted,
            basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT)
        assertTrue(ready is KotlinFutureLoadReview.Accepted)
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.NOT_ADJACENT_VERIFIED_INCREMENT),
            review(from="100",to="90",source=source,stock=assisted,
                basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT,limit="20"))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.EXCEEDS_AUTHORIZED_INCREMENT),
            review(from="100",to="95",source=source,stock=assisted,
                basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT,limit="2.5"))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION),
            review(from="100",to="105",stock=assisted,
                basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION),
            review(from="200",to="195",source=source,stock=external))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD),
            review(from="80",to="75",source=source,stock=assisted,
                basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT))
    }

    @Test fun verifiedZeroSelectorCannotBecomeAnEarnedAssistedLoad() {
        val stock = assisted.copy(minimum=n("0"))
        assertEquals(KotlinFutureLoadReview.Rejected(KotlinFutureLoadRefusal.NO_LOWER_ASSISTED_SETTING),
            review(from="5", to="0.00", source=KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT,
                stock=stock, basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT))
    }

    @Test fun noUnverifiedUnitBasisTargetLoadOrRoundedCandidateCanAdvance() {
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNVERIFIED_UNIT_OR_BASIS),review(stock=null))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNVERIFIED_UNIT_OR_BASIS),
            review(unit=LoadUnit.KG))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNVERIFIED_UNIT_OR_BASIS),
            review(basis=LoadBasis.CABLE_STACK))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNVERIFIED_ISSUED_LOAD),
            review(from="201"))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD),
            review(to="202.5"))
    }

    @Test fun invalidPrecisionOrBudgetRefusesRatherThanClampingOrRounding() {
        listOf("205.0001","-5","100001").forEach { invalid ->
            assertEquals(KotlinFutureLoadReview.Rejected(
                KotlinFutureLoadRefusal.INVALID_NUMBER_OR_BUDGET),review(to=invalid))
        }
        listOf("0","-2","0.0001","100001").forEach { invalid ->
            assertEquals(KotlinFutureLoadReview.Rejected(
                KotlinFutureLoadRefusal.INVALID_NUMBER_OR_BUDGET),
                review(limit=invalid))
        }
        // Numerically equivalent decimal representations remain equivalent.
        assertTrue(review(from="200.000",to="205.000") is
            KotlinFutureLoadReview.Accepted)
    }

    @Test fun futureCandidateNumbersUseExactlyThePerformedSetWeightEnvelope() {
        val physical=listOf("0","0.001","205","205.0000",
            "99999.999","100000.000","1E+3")
        val invalid=listOf("-0.001","0.0001","205.0001",
            "100000.001","100001","1E-4")
        for(text in physical) {
            assertTrue(text,KotlinSetNumberAuthorityDomain
                .validPhysicalLoadValue(n(text)))
            val rejected=review(to=text) as? KotlinFutureLoadReview.Rejected
            assertNotEquals(text,KotlinFutureLoadRefusal.INVALID_NUMBER_OR_BUDGET,
                rejected?.reason)
        }
        for(text in invalid) {
            assertFalse(text,KotlinSetNumberAuthorityDomain
                .validPhysicalLoadValue(n(text)))
            assertEquals(text,KotlinFutureLoadReview.Rejected(
                KotlinFutureLoadRefusal.INVALID_NUMBER_OR_BUDGET),
                review(to=text))
        }
        // Same physical format is not an approval or stock waiver.
        val reviewed=review(from="200.0000",to="205.000") as
            KotlinFutureLoadReview.Accepted
        assertFalse(reviewed.fact.maySaveFutureProgram)
        assertFalse(reviewed.fact.mayChangeIssuedWorkout)
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.INVALID_NUMBER_OR_BUDGET),
            review(limit="0"))
        assertEquals(KotlinFutureLoadReview.Rejected(
            KotlinFutureLoadRefusal.UNVERIFIED_CANDIDATE_LOAD),
            review(to="204"))
    }


    @Test fun earnedIncreaseCarriesExactPhysicalNeighborProofWithoutRecalculation() {
        val fact=(review() as KotlinFutureLoadReview.Accepted).fact
        val proof=fact.physicalProof
        assertEquals(KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_HIGHER_SETTING,
            proof.reason)
        assertEquals(n("200"),proof.verifiedIssued)
        assertEquals(n("205"),proof.verifiedCandidate)
        assertEquals(n("205"),proof.nextHigher)
        assertEquals(n("195"),proof.nextLower)
        assertEquals(n("5"),proof.absoluteChange)
        assertTrue(proof.requiresIndependentPerformanceEvidence)
        assertFalse(proof.requiresPreviouslyDemonstratedLoad)
        assertFalse(proof.maySaveProgram)
        assertFalse(proof.mayIssueWorkout)
        assertFalse(fact.mayChangeIssuedWorkout)

        // Every decimal representation of the same physical load creates
        // identical review evidence; UI formatting is not a new numeric rule.
        val alternate=(review(from="200.0000",to="205.000") as
            KotlinFutureLoadReview.Accepted).fact
        assertEquals(fact,alternate)
        assertEquals(proof,alternate.physicalProof)
    }

    @Test fun assistanceAndRecoveryHaveDistinctVerifiedPhysicalLineage() {
        val assistance=(review(from="100",to="95",
            source=KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT,
            stock=assisted,basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT)
            as KotlinFutureLoadReview.Accepted).fact.physicalProof
        assertEquals(KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_LOWER_ASSISTANCE,
            assistance.reason)
        assertEquals(n("105"),assistance.nextHigher)
        assertEquals(n("95"),assistance.nextLower)
        assertEquals(n("5"),assistance.absoluteChange)
        assertFalse(assistance.requiresPreviouslyDemonstratedLoad)

        val recovery=(review(from="210",to="195",
            source=KotlinFutureLoadSource.RETURN_TO_DEMONSTRATED_LOAD,
            limit="5") as KotlinFutureLoadReview.Accepted).fact.physicalProof
        assertEquals(
            KotlinFutureLoadPhysicalReason.VERIFIED_LOWER_SETTING_HISTORY_REQUIRED,
            recovery.reason)
        assertEquals(n("210"),recovery.verifiedIssued)
        assertEquals(n("195"),recovery.verifiedCandidate)
        // The candidate intentionally skips the adjacent lower physical
        // setting. This is NOT evidence that 195 lb was previously completed.
        assertEquals(n("205"),recovery.nextLower)
        assertEquals(n("215"),recovery.nextHigher)
        assertEquals(n("15"),recovery.absoluteChange)
        assertTrue(recovery.requiresPreviouslyDemonstratedLoad)
        assertTrue(recovery.requiresIndependentPerformanceEvidence)
        assertFalse(recovery.maySaveProgram)
        assertFalse(recovery.mayIssueWorkout)
    }

    @Test fun invalidCandidateCannotAcquireAPhysicalProof() {
        val blocked=listOf(
            review(to="210",limit="20"),
            review(to="202.5"),
            review(limit="2"),
            review(stock=null),
            review(from="205",to="210",
                source=KotlinFutureLoadSource.RETURN_TO_DEMONSTRATED_LOAD))
        for(result in blocked) {
            assertTrue("A rejected physical candidate must not carry an accepted proof",
                result is KotlinFutureLoadReview.Rejected)
        }
    }
}
