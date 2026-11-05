package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class AdaptiveBlockOptionGenerationDomainTest {
    private val f=GeneratedAdaptiveBlockFixture
    private fun audit()=CustomCoachAuditInputs(
        policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to
            checkNotNull(f.generation().policy)),
        nonSetSecondsByWeekday=mapOf(0 to 2100,2 to 2100,4 to 2100))
    private fun variant(id:String,weight:String?=null)=
        AdaptiveBlockGenerationAlternative(
            id,f.generation(),f.policies().map {p->
                if(p.exerciseId=="bench" && weight!=null)
                    p.copy(purposeWorkingLoad=f.n(weight)) else p
            })
    private fun request(
        alternatives:List<AdaptiveBlockGenerationAlternative>,
        permissions:CustomCycleCoachPermissions=CustomCycleCoachPermissions(
            adaptSetCount=true,adaptExercises=true,adaptLoads=true,
            adaptReps=true,adaptRir=true,adaptRest=true),
        includeKeep:Boolean=true
    )=AdaptiveBlockGenerationRequest(
        f.cycle(),"block-1",f.athlete(),f.movements,
        audit(),permissions,alternatives,includeKeep)

    @Test fun generatedAlternativesAndKeepCurrentReachSameAuditedQualityGate() {
        val input=request(listOf(variant("original_load"),
            variant("higher_verified_load","30")))
        val result=AdaptiveBlockOptionGenerationDomain.review(input)
        assertEquals(AdaptiveBlockGenerationStatus.OPTIONS_EVALUATED,
            result.status)
        assertEquals(2,result.outcomes.size)
        assertTrue(result.outcomes.all {it.eligibleForCycleAudit})
        assertEquals(3,result.comparison.candidatesChecked)
        assertEquals(setOf("keep_current","original_load",
            "higher_verified_load"),
            result.comparison.evaluated.map {it.optionId}.toSet())
        assertTrue(result.comparison.evaluated.any {
            it.optionId=="keep_current" &&
                it.status==AdaptiveCycleOptionStatus.UNCHANGED
        })
        assertNotNull(result.recommendedOptionId)
        assertTrue(result.recommendedOptionId in setOf(
            "keep_current","original_load","higher_verified_load"))
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
        assertEquals(input.approvedCycle,f.cycle())
        assertEquals(result,AdaptiveBlockOptionGenerationDomain.review(input))
    }

    @Test fun permissionDeniedCandidateCannotBeatUnchangedApprovedCycle() {
        val review=AdaptiveBlockOptionGenerationDomain.review(
            request(listOf(variant("changed_work")),
                permissions=CustomCycleCoachPermissions()))
        assertEquals(AdaptiveBlockGenerationStatus.OPTIONS_EVALUATED,
            review.status)
        assertTrue(review.outcomes.single().eligibleForCycleAudit)
        val changed=review.comparison.evaluated.single {
            it.optionId=="changed_work"
        }
        assertEquals(AdaptiveCycleOptionStatus.PERMISSION_REQUIRED,
            changed.status)
        assertNull(changed.qualityScore)
        assertEquals("keep_current",review.recommendedOptionId)
    }

    @Test fun invalidWeightsAndMissingPoliciesHaveExplicitReasons() {
        val input=request(listOf(
            variant("unavailable","27.5"),
            AdaptiveBlockGenerationAlternative("missing",f.generation(),
                f.policies().dropLast(1))))
        val result=AdaptiveBlockOptionGenerationDomain.review(input)
        assertEquals(2,result.outcomes.size)
        assertEquals(listOf(
            GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED,
            GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED),
            result.outcomes.map {it.generated?.status})
        assertEquals(1,result.comparison.candidatesChecked)
        assertEquals("keep_current",result.recommendedOptionId)
    }

    @Test fun futureScheduleAndAlteredTimeLimitsCannotMasqueradeAsCurrentSetup() {
        val current=f.generation()
        val future=current.copy(setup=current.setup.copy(
            reviewed=current.setup.reviewed!!.copy(
                trainingWeekdays=setOf(0,1,2,4),
                declaredRestDays=setOf(3,5,6))))
        val changingLimit=current.copy(setup=current.setup.copy(
            reviewed=current.setup.reviewed!!.copy(
                hardSessionLimit=HardSessionLimit.Minutes(20))))
        val result=AdaptiveBlockOptionGenerationDomain.review(request(
            listOf(
                AdaptiveBlockGenerationAlternative("future_days",
                    future,f.policies()),
                AdaptiveBlockGenerationAlternative("forged_limit",
                    changingLimit,f.policies()))))
        assertEquals(AdaptiveBlockGenerationRefusal.FUTURE_SCHEDULE_REQUIRES_APPROVAL,
            result.outcomes[0].refusal)
        assertEquals(AdaptiveBlockGenerationRefusal.DIFFERENT_ATHLETE_OR_GYM,
            result.outcomes[1].refusal)
        assertTrue(result.outcomes.all {it.generated==null})
        assertEquals(1,result.comparison.candidatesChecked)
    }

    @Test fun wrongPhasePolicyAndDuplicatedOptionIdentityFailClosed() {
        val changedPolicy=f.generation().copy(
            policy=f.generation().policy!!.copy(revision="another-revision"))
        val review=AdaptiveBlockOptionGenerationDomain.review(request(
            listOf(AdaptiveBlockGenerationAlternative("other_policy",
                changedPolicy,f.policies()))))
        assertEquals(AdaptiveBlockGenerationRefusal.MISSING_PHASE_POLICY,
            review.outcomes.single().refusal)
        assertEquals("keep_current",review.recommendedOptionId)
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveBlockOptionGenerationDomain.review(request(
                listOf(variant("same"),variant("same"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveBlockOptionGenerationDomain.review(request(
                listOf(variant("keep_current"))))
        }
    }
}
