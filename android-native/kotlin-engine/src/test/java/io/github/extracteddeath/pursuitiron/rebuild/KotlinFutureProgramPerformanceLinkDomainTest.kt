package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class KotlinFutureProgramPerformanceLinkDomainTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"cycle-1",4,"gym-1",3,"catalog-5","rules-1")

    private fun generated():Pair<GeneratedAdaptiveBlockRequest,
        GeneratedAdaptiveBlockResult> {
        val input=fixture.request()
        val result=GeneratedAdaptiveBlockDomain.propose(input)
        assertEquals(result.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,result.status)
        return input to result
    }

    private fun history(
        candidate:GeneratedAdaptiveBlockResult,
        issuedWeight:String="25",
        finalReportedReps:Int?=null
    ):Pair<KotlinProgramHistoryReadout,KotlinProgramPerformanceReport> {
        val traced=checkNotNull(candidate.upstreamNumberTrace).sets
            .filter{it.exerciseId=="bench" && it.type==DraftSetType.WORKING}
        assertTrue(traced.isNotEmpty())
        val chosen=traced.groupBy{it.weekday to it.dayIndex}
            .values.first().sortedBy{it.ordinal}
        val journals=(1..3).map {n->
            val targets=chosen.mapIndexed{ordinal,fact->
                KotlinWorkoutTarget(fact.exerciseId,fact.role,fact.purpose,
                    "bench:w$n:s$ordinal",DraftSetType.WORKING,ordinal,
                    DraftSetLoad.Verified(LoadUnit.LB,
                        LoadBasis.DUMBBELL_PER_HAND,fixture.n(issuedWeight)),
                    fact.reps,fact.targetRir,fact.activeSeconds,fact.restSeconds)
            }
            val snapshot=KotlinStagedWorkout(source,"grant-$n","w-$n",
                chosen.first().weekday,chosen.first().dayIndex,
                chosen.first().purpose,45,targets)
            var journal=KotlinWorkoutJournalDomain.begin(snapshot,
                KotlinStoredWorkoutIdentity("stored-$n","w-$n",source,1))
            for((index,target) in targets.withIndex()) {
                val reps=if(n==3 && finalReportedReps!=null)
                    finalReportedReps else target.reps.maximum
                val fact=KotlinPerformedSet(fixture.n(issuedWeight),LoadUnit.LB,
                    reps,2,null)
                journal=(KotlinWorkoutJournalDomain.reduce(journal,
                    KotlinJournalOperation.RecordSet("receipt-$n-$index",
                        journal.revision,target.setKey,
                        KotlinJournalSetOutcome.COMPLETED,fact))
                    as KotlinJournalDecision.Proposed).state
            }
            (KotlinWorkoutJournalDomain.reduce(journal,
                KotlinJournalOperation.Finish("finish-$n",journal.revision))
                as KotlinJournalDecision.Proposed).state
        }
        val scope=KotlinProgramHistoryReadoutRequest(
            "athlete-1","cycle-1",source,setOf(0,1,2),journals)
        val ledger=(KotlinProgramHistoryReadoutDomain.review(scope)
            as KotlinProgramHistoryReadoutResult.Candidate).ledger
        val report=(KotlinProgramPerformanceEvidenceDomain.review(
            ledger,mapOf("w-1" to 1L,"w-2" to 2L,"w-3" to 3L))
            as KotlinProgramPerformanceResult.Reviewed).report
        return ledger to report
    }

    @Test fun ownerReplayCellsOnlyOfferSameDaySameLoadRepCeilingReview() {
        val (input,future)=generated()
        val (ledger,report)=history(future)
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            report.threads.single().recentGate)
        val linked=KotlinFutureProgramPerformanceLinkDomain.review(
            input,future,ledger,report) as KotlinFuturePerformanceLinkResult.Reviewed
        val proven=linked.link.cells.filter {
            it.status==KotlinFuturePerformanceCellStatus.REP_CEILING_REVIEW_ONLY
        }
        assertEquals(1,proven.size)
        assertEquals("bench",proven.single().exerciseId)
        assertEquals(report.threads.single().key.calendarDay,
            proven.single().weekday)
        assertEquals(report.threads.single().key.dayIndex,
            proven.single().dayIndex)
        assertEquals(listOf("w-1","w-2","w-3"),
            proven.single().evaluatedWorkoutIds)
        assertFalse(proven.single().canChangeLoad)
        assertFalse(proven.single().canChangeReps)
        assertFalse(linked.link.canApplyAutomatically)
        assertFalse(linked.link.canIssueWorkout)
        assertTrue(linked.link.cells.any{
            it.status==KotlinFuturePerformanceCellStatus.NO_EXACT_HISTORY
        })
    }

    @Test fun historicalRepsAtTwentyPoundsCannotJustifyTwentyFivePoundFuture() {
        val (input,future)=generated()
        val (ledger,report)=history(future,issuedWeight="20")
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            report.threads.single().recentGate)
        val linked=KotlinFutureProgramPerformanceLinkDomain.review(
            input,future,ledger,report) as KotlinFuturePerformanceLinkResult.Reviewed
        assertTrue(linked.link.cells.none{
            it.status==KotlinFuturePerformanceCellStatus.REP_CEILING_REVIEW_ONLY
        })
        assertEquals(1,linked.link.cells.count{
            it.status==KotlinFuturePerformanceCellStatus.FUTURE_PRESCRIPTION_DIFFERS
        })
    }

    @Test fun nearFailureMostRecentVisitRemainsHistoryGuarded() {
        val (input,future)=generated()
        val (ledger,report)=history(future,finalReportedReps=8)
        assertNotEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            report.threads.single().recentGate)
        val linked=KotlinFutureProgramPerformanceLinkDomain.review(
            input,future,ledger,report) as KotlinFuturePerformanceLinkResult.Reviewed
        assertTrue(linked.link.cells.none{
            it.status==KotlinFuturePerformanceCellStatus.REP_CEILING_REVIEW_ONLY
        })
        assertEquals(1,linked.link.cells.count{
            it.status==KotlinFuturePerformanceCellStatus.HISTORY_GUARDED
        })
    }

    @Test fun mismatchedDayRevisionAndMachineCannotReuseOldEvidence() {
        val (input,future)=generated()
        val (ledger,report)=history(future)
        val numbers=checkNotNull(future.upstreamNumberTrace)
        val altered=future.copy(upstreamNumberTrace=numbers.copy(
            sets=numbers.sets.mapIndexed{i,fact->
                if(i==0)fact.copy(dayIndex=(fact.dayIndex+1)%3) else fact
            }))
        assertEquals(KotlinFuturePerformanceLinkResult.Rejected(
            KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE),
            KotlinFutureProgramPerformanceLinkDomain.review(
                input,altered,ledger,report))
        assertEquals(KotlinFuturePerformanceLinkResult.Rejected(
            KotlinFuturePerformanceLinkRefusal.SOURCE_EPOCH_MISMATCH),
            KotlinFutureProgramPerformanceLinkDomain.review(input,future,
                ledger.copy(source=source.copy(programRevision=3)),report))
        // A distinct real machine must not inherit untagged DB history.
        val alternate=future.copy(proposedOption=
            checkNotNull(future.proposedOption).copy(proposedCycle=
                checkNotNull(future.proposedOption).proposedCycle.let{cycle->
                    cycle.copy(blocks=cycle.blocks.map{block->
                        block.copy(days=block.days.map{day->
                            day.copy(slots=day.slots.map{slot->
                                if(slot.exerciseId=="bench")slot.copy(
                                    machineInstanceId="press-B") else slot
                            })
                        })
                    })
                }))
        val isolated=KotlinFutureProgramPerformanceLinkDomain.review(
            input,alternate,ledger,report) as KotlinFuturePerformanceLinkResult.Reviewed
        assertTrue(isolated.link.cells.none{
            it.status==KotlinFuturePerformanceCellStatus.REP_CEILING_REVIEW_ONLY
        })
    }

    @Test fun issuedNumericalEquivalenceIsCentralAndScaleIndependent() {
        val a=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,fixture.n("25.0"))
        val same=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,fixture.n("25"))
        val differentBasis=DraftSetLoad.Verified(LoadUnit.LB,
            LoadBasis.SMITH_TOTAL,fixture.n("25"))
        assertTrue(KotlinSetNumberAuthorityDomain.sameIssuedTarget(a,same))
        assertFalse(KotlinSetNumberAuthorityDomain.sameIssuedTarget(
            a,differentBasis))
        assertFalse(KotlinSetNumberAuthorityDomain.sameIssuedTarget(
            a,DraftSetLoad.ExplicitBodyweight))
    }
}
