package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class MeasuredWorkoutResultReviewDomainTest {
    private fun plan(id:String="plank"):MeasuredExerciseSetPlan {
        val sides=if(id=="side-plank")setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
            else setOf(MeasuredSide.BOTH)
        val stock=if(id=="farmers")VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            listOf(BigDecimal("35"),BigDecimal("40"))) else null
        return MeasuredExerciseSetPlan(
            "review-workout","review-set",id,"gym",1,"station",
            if(id=="farmers")5000 else 30,sides,
            if(id=="band-pushup")setOf("verified-band-pushup-setup") else emptySet(),
            physicalStock=stock,
            intendedExternalLoad=if(stock!=null)BigDecimal("35") else null,
            unit=if(stock!=null)LoadUnit.LB else null,
            matchedPairConfirmed=id=="farmers",
            bandId=if(id=="band-pushup")"band-blue" else null,
            gymConfirmedBandIds=if(id=="band-pushup")setOf("band-blue") else emptySet()
        )
    }

    private fun observe(
        plan:MeasuredExerciseSetPlan,
        amounts:List<Int>,
        external:BigDecimal?=null,
        elapsed:Int?=null
    )=MeasuredExerciseActual(plan.physicalSetupId,
        plan.sides.sortedBy{it.ordinal}.mapIndexed{i,side->
            MeasuredSideResult(side,amounts[i])},
        actualExternalLoad=external,
        unit=if(external!=null)LoadUnit.LB else null,
        bandId=plan.bandId,
        elapsedSeconds=elapsed)

    private fun save(
        before:MeasuredExerciseJournal,
        event:String,outcome:MeasuredSetOutcome,
        actual:MeasuredExerciseActual?,
        correcting:String?=null
    ):MeasuredExerciseJournal {
        val decision=MeasuredExerciseJournalDomain.reduce(before,
            MeasuredJournalOperation.Record(event,before.revision,
                outcome,actual,correcting))
        assertTrue(decision.toString(),decision is MeasuredJournalDecision.Proposed)
        return (decision as MeasuredJournalDecision.Proposed).journal
    }

    @Test fun pendingAndSkippedNeverDisplayPrescribedNumbersAsActuals() {
        val p=plan()
        val initial=checkNotNull(MeasuredExerciseJournalDomain.begin(p))
        val pending=MeasuredWorkoutResultReviewDomain.review(p,initial)
        assertEquals(MeasuredSetReviewStatus.PENDING,pending.status)
        assertFalse(pending.resolved)
        assertNull(pending.actualSummary)
        val skipped=save(initial,"skipped",MeasuredSetOutcome.SKIPPED,null)
        val view=MeasuredWorkoutResultReviewDomain.review(p,skipped)
        assertEquals("Skipped",view.statusLabel)
        assertTrue(view.resolved)
        assertNull(view.actualSummary)
    }

    @Test fun correctionsReplaceEarlierResultsWithoutFabricatingNewHistory() {
        val p=plan("side-plank")
        val initial=checkNotNull(MeasuredExerciseJournalDomain.begin(p))
        val first=save(initial,"first",MeasuredSetOutcome.COMPLETED,
            observe(p,listOf(22,28)))
        val actual=MeasuredWorkoutResultReviewDomain.review(p,first)
        assertEquals("Left 22 s · Right 28 s",actual.actualSummary)
        val corrected=save(first,"correction",MeasuredSetOutcome.COMPLETED,
            observe(p,listOf(33,31)),"first")
        val view=MeasuredWorkoutResultReviewDomain.review(p,corrected)
        assertEquals(MeasuredSetReviewStatus.COMPLETED,view.status)
        assertEquals("Left 33 s · Right 31 s",view.actualSummary)
        assertFalse(checkNotNull(view.actualSummary).contains("22"))
        assertEquals(2,corrected.receipts.size)
    }

    @Test fun failedAttemptIsResolvedButNeverCalledCompleted() {
        val p=plan()
        val initial=checkNotNull(MeasuredExerciseJournalDomain.begin(p))
        val failed=save(initial,"failed",MeasuredSetOutcome.FAILED_ATTEMPT,
            observe(p,listOf(0)))
        val view=MeasuredWorkoutResultReviewDomain.review(p,failed)
        assertEquals("Failed attempt",view.statusLabel)
        assertEquals("0 s",view.actualSummary)
        assertTrue(view.resolved)
    }

    @Test fun distanceExternalMassAndOptionalTimeKeepPhysicalMeaning() {
        val p=plan("farmers")
        val initial=checkNotNull(MeasuredExerciseJournalDomain.begin(p))
        val logged=save(initial,"carry",MeasuredSetOutcome.COMPLETED,
            observe(p,listOf(5125),BigDecimal("40"),elapsed=59))
        val view=MeasuredWorkoutResultReviewDomain.review(p,logged)
        assertEquals("51.25 m · External 40 lb per hand · Elapsed 59 s",
            view.actualSummary)
        assertFalse(checkNotNull(view.actualSummary).contains("5000"))
    }

    @Test fun bandIsAnIdentityNotAnImaginaryWeight() {
        val p=plan("band-pushup")
        val initial=checkNotNull(MeasuredExerciseJournalDomain.begin(p))
        val logged=save(initial,"band",MeasuredSetOutcome.COMPLETED,
            observe(p,listOf(12)))
        val view=MeasuredWorkoutResultReviewDomain.review(p,logged)
        assertEquals("12 reps · Band band-blue",view.actualSummary)
        assertFalse(checkNotNull(view.actualSummary).contains("lb"))
    }

    @Test fun foreignOrDetachedEffectiveRecordsFailClosed() {
        val p=plan()
        val initial=checkNotNull(MeasuredExerciseJournalDomain.begin(p))
        val recorded=save(initial,"record",MeasuredSetOutcome.COMPLETED,
            observe(p,listOf(27)))
        assertEquals(MeasuredSetReviewStatus.UNVERIFIED,
            MeasuredWorkoutResultReviewDomain.review(
                p.copy(setKey="other"),recorded).status)
        assertEquals(MeasuredSetReviewStatus.UNVERIFIED,
            MeasuredWorkoutResultReviewDomain.review(p,
                recorded.copy(receipts=emptyList())).status)
        assertEquals(MeasuredSetReviewStatus.UNVERIFIED,
            MeasuredWorkoutResultReviewDomain.review(p,null).status)
    }
}
