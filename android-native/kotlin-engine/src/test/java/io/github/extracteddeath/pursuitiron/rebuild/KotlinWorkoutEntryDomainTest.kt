package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinWorkoutEntryDomainTest {
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",1,"program-1",1,"gym-1",1,"catalog-1","rules-1")
    private val verified=KotlinWorkoutTarget(
        "bench",ComposerRole.PRIMARY,ComposerSessionPurpose.STRENGTH_SKILL,
        "bench:s1",DraftSetType.WORKING,1,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,
            BigDecimal("225")),RepBand(5,8),2,60,120)
    private val body=verified.copy(exerciseId="chin-up",
        setKey="chin:s1",load=DraftSetLoad.ExplicitBodyweight)

    @Test fun actualRirMissingNeverCopiesTargetRir() {
        val parsed=KotlinWorkoutEntryDomain.parse(
            verified,"210","6","") as KotlinSetEntryResult.Accepted
        assertEquals(BigDecimal("210"),parsed.performed.load)
        assertEquals(6,parsed.performed.reps)
        assertNull(parsed.performed.actualRir)
        assertEquals(2,verified.targetRir)
        val exact=KotlinWorkoutEntryDomain.parse(
            verified,"212.5","8","1") as KotlinSetEntryResult.Accepted
        assertEquals(BigDecimal("212.5"),exact.performed.load)
        assertEquals(1,exact.performed.actualRir)
        assertEquals(LoadUnit.LB,exact.performed.unit)
    }

    @Test fun failedAttemptRecordsAttemptedWeightNotSuggestedWeight() {
        val actual=KotlinWorkoutEntryDomain.parse(
            verified,"205","ignored","9",failed=true)
            as KotlinSetEntryResult.Accepted
        assertEquals(BigDecimal("205"),actual.performed.load)
        assertEquals(0,actual.performed.reps)
        assertNull(actual.performed.actualRir)
    }

    @Test fun typedWeightAndRepsAreRequiredWithoutGuesswork() {
        assertEquals(KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.MISSING_WEIGHT),
            KotlinWorkoutEntryDomain.parse(verified,"","5",""))
        assertEquals(KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.MISSING_REPS),
            KotlinWorkoutEntryDomain.parse(verified,"200","",""))
        for(weight in listOf("-2","3e2","38.5001","100001","NaN","1,000")) {
            assertEquals(weight,KotlinSetEntryResult.Rejected(
                KotlinSetEntryRefusal.INVALID_WEIGHT),
                KotlinWorkoutEntryDomain.parse(verified,weight,"5",""))
        }
        for(reps in listOf("0","101","-2","seven","8.5")) {
            assertEquals(reps,KotlinSetEntryResult.Rejected(
                KotlinSetEntryRefusal.INVALID_REPS),
                KotlinWorkoutEntryDomain.parse(verified,"200",reps,""))
        }
        assertEquals(KotlinSetEntryResult.Rejected(KotlinSetEntryRefusal.INVALID_RIR),
            KotlinWorkoutEntryDomain.parse(verified,"200","8","11"))
    }

    @Test fun bodyweightRequiresNoFabricatedWeightAndCanHaveUnknownRir() {
        val completed=KotlinWorkoutEntryDomain.parse(body,"","9","")
            as KotlinSetEntryResult.Accepted
        assertNull(completed.performed.load)
        assertNull(completed.performed.unit)
        assertEquals(9,completed.performed.reps)
        assertNull(completed.performed.actualRir)
        val failed=KotlinWorkoutEntryDomain.parse(body,"","", "",failed=true)
            as KotlinSetEntryResult.Accepted
        assertEquals(0,failed.performed.reps)
        assertNull(failed.performed.load)
    }

    @Test fun submittedActualsSatisfyTheSamePureJournalReducer() {
        val snapshot=KotlinStagedWorkout(source,"grant-1","workout-1",0,0,
            ComposerSessionPurpose.STRENGTH_SKILL,15,listOf(verified))
        val saved=KotlinStoredWorkoutIdentity("saved-1","workout-1",source,1L)
        val journal=KotlinWorkoutJournalDomain.begin(snapshot,saved)
        val parsed=KotlinWorkoutEntryDomain.parse(
            verified,"205","6","0") as KotlinSetEntryResult.Accepted
        val op=KotlinJournalOperation.RecordSet(
            "event-1",0,"bench:s1",KotlinJournalSetOutcome.COMPLETED,
            parsed.performed)
        assertTrue(KotlinWorkoutJournalDomain.reduce(journal,op)
            is KotlinJournalDecision.Proposed)
    }
}
