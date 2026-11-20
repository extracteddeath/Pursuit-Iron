package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinProgramPerformanceEvidenceDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",2,"cycle-1",4,"gym-1",3,"catalog-5","rules-1")

    private data class Visit(
        val workoutId:String,val completionOrder:Long,
        val machine:String?="press-A",val day:Int=0,
        val purpose:ComposerSessionPurpose=ComposerSessionPurpose.HYPERTROPHY,
        val reportedRir:Int?=2,val targetRir:Int?=2,
        val reps:Int=12,val load:String="100",
        val outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        val shortened:Boolean=false,
        val loadBasis:LoadBasis=LoadBasis.SMITH_TOTAL,
        val calendarDay:Int=0,
        val role:ComposerRole=ComposerRole.PRIMARY,
        val actualLoad:String?=null
    )

    private fun facts(items:List<Visit>):Pair<KotlinProgramHistoryReadout,Map<String,Long>> {
        val workouts=items.map{v->
            KotlinProgramHistoricalWorkout(
                v.workoutId,v.day,v.calendarDay,"finish-${v.workoutId}",
                3,0,if(v.outcome==KotlinJournalSetOutcome.COMPLETED)2 else 0,
                if(v.outcome==KotlinJournalSetOutcome.SKIPPED)2 else 0,
                if(v.outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)2 else 0,
                v.purpose,v.shortened)
        }
        val sets=items.flatMap{v->
            (0..1).map{ordinal->
                KotlinProgramHistoricalWorkingSet(
                    v.workoutId,v.day,v.calendarDay,
                    "bench",v.role,v.purpose,"s${ordinal}",ordinal,
                    DraftSetLoad.Verified(LoadUnit.LB,v.loadBasis,n(v.load)),
                    RepBand(8,12),v.targetRir,v.outcome,
                    if(v.outcome==KotlinJournalSetOutcome.COMPLETED)
                        n(v.actualLoad ?: v.load) else null,
                    if(v.outcome==KotlinJournalSetOutcome.COMPLETED)
                        LoadUnit.LB else null,
                    if(v.outcome==KotlinJournalSetOutcome.COMPLETED)
                        v.reps else null,
                    if(v.outcome==KotlinJournalSetOutcome.COMPLETED)
                        v.reportedRir else null,
                    v.machine,
                    "logged-${v.workoutId}-${ordinal}",null,
                    if(v.machine==null &&
                        ReviewedFutureWeekProgressionDomain
                            .requiresPhysicalMachineIdentity(v.loadBasis))
                        KotlinHistoryComparisonGuard.MACHINE_UNKNOWN
                    else if(v.outcome!=KotlinJournalSetOutcome.COMPLETED)
                        KotlinHistoryComparisonGuard.INELIGIBLE_SESSION
                    else KotlinHistoryComparisonGuard.COMPARABLE_REVIEW_ONLY)
            }
        }
        val ledger=KotlinProgramHistoryReadout(
            2,source.athleteId,source.programId,source,workouts,sets,
            "a".repeat(64))
        return ledger to items.associate{it.workoutId to it.completionOrder}
    }

    private fun report(items:List<Visit>):KotlinProgramPerformanceReport {
        val (ledger,orders)=facts(items)
        return (KotlinProgramPerformanceEvidenceDomain.review(ledger,orders)
            as KotlinProgramPerformanceResult.Reviewed).report
    }
    private fun a(id:String,order:Long)=Visit(id,order)

    @Test fun threeMatchedCompletedVisitsSupportOnlyReviewNotAutoPrescription() {
        val v=listOf(a("zzz",1),a("aaa",3),a("mmm",2))
        val measured=report(v)
        val t=measured.threads.single()
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            t.recentGate)
        assertEquals(listOf("zzz","mmm","aaa"),t.evaluatedWorkoutIds)
        assertEquals(3,t.visits.size)
        assertEquals(0,t.key.dayIndex)
        assertEquals(0,t.key.calendarDay)
        assertEquals(ComposerRole.PRIMARY,t.key.role)
        assertEquals(2,measured.schemaVersion)
        assertEquals(ComposerSessionPurpose.HYPERTROPHY,t.key.purpose)
        assertEquals("press-A",t.key.physicalMachineId)
        assertEquals(LoadBasis.SMITH_TOTAL,t.key.loadBasis)
        assertEquals(2,t.visits.last().targetRir.first())
        assertEquals(2,t.visits.last().reportedRir.first())
        assertFalse(t.canChangeRepRange)
        assertFalse(t.canChangeSetCount)
        assertFalse(t.canChangeWorkingLoad)
        assertFalse(t.canPromotePhase)
        assertFalse(measured.canApplyToProgram)
        assertEquals(measured,report(v.reversed()))
    }

    @Test fun hardSetUnknownRirOrMissedLatestNeverEarnsHigherRange() {
        val base=listOf(a("w1",1),a("w2",2))
        assertEquals(KotlinProgramPerformanceGate.ACTUAL_EFFORT_UNKNOWN,
            report(base+Visit("w3",3,reportedRir=null))
                .threads.single().recentGate)
        assertEquals(KotlinProgramPerformanceGate.INCOMPARABLE_RECENT_VISIT,
            report(base+Visit("w3",3,outcome=KotlinJournalSetOutcome.SKIPPED))
                .threads.single().recentGate)
        assertEquals(KotlinProgramPerformanceGate.INCOMPARABLE_RECENT_VISIT,
            report(base+Visit("w3",3,shortened=true))
                .threads.single().recentGate)
        assertEquals(KotlinProgramPerformanceGate.NOT_REPEATED_AT_REP_CEILING,
            report(base+Visit("w3",3,reps=10,reportedRir=0))
                .threads.single().recentGate)
        assertEquals(KotlinProgramPerformanceGate.PRESCRIPTION_CHANGED,
            report(base+Visit("w3",3,load="105"))
                .threads.single().recentGate)
        assertEquals("A newly reduced goal RIR cannot borrow prior easier effort",
            KotlinProgramPerformanceGate.PRESCRIPTION_CHANGED,
            report(base+Visit("w3",3,targetRir=1,reportedRir=2))
                .threads.single().recentGate)
        assertEquals("Reverting RIR does not erase an intervening different goal",
            KotlinProgramPerformanceGate.PRESCRIPTION_CHANGED,
            report(listOf(a("w1",1),
                Visit("w2",2,targetRir=1,reportedRir=2),
                a("w3",3))).threads.single().recentGate)
        assertEquals(KotlinProgramPerformanceGate.ACTUAL_EFFORT_UNKNOWN,
            report(base+Visit("w3",3,targetRir=null))
                .threads.single().recentGate)
    }

    @Test fun repCeilingAtDifferentActualLoadCannotCertifyIssuedWeightCapacity() {
        val allLower=report(listOf(
            Visit("w1",1,actualLoad="90"),
            Visit("w2",2,actualLoad="90"),
            Visit("w3",3,actualLoad="90")))
        assertEquals(KotlinProgramPerformanceGate.INCOMPARABLE_RECENT_VISIT,
            allLower.threads.single().recentGate)
        assertFalse(allLower.threads.single().canChangeRepRange)
        assertFalse(allLower.canApplyToProgram)

        val latestLower=report(listOf(a("w1",1),a("w2",2),
            Visit("w3",3,actualLoad="90")))
        assertEquals(KotlinProgramPerformanceGate.INCOMPARABLE_RECENT_VISIT,
            latestLower.threads.single().recentGate)
        assertEquals(listOf("w1","w2","w3"),
            latestLower.threads.single().evaluatedWorkoutIds)

        // The ONE numeric authority compares BigDecimal loads without
        // inventing a change from equivalent number formatting.
        val sameValue=report(listOf(a("w1",1),a("w2",2),
            Visit("w3",3,actualLoad="100.0")))
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            sameValue.threads.single().recentGate)
    }

    @Test fun machineBAndDifferentWeekdayCannotBeBorrowedForA() {
        val result=report(listOf(a("a1",1),a("a2",3),a("a3",5),
            Visit("b1",2,machine="press-B"),
            Visit("b2",4,machine="press-B"),
            Visit("d2",6,day=2)))
        val a=result.threads.single{it.key.physicalMachineId=="press-A" &&
            it.key.dayIndex==0}
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            a.recentGate)
        assertEquals(listOf("a1","a2","a3"),a.evaluatedWorkoutIds)
        assertEquals(KotlinProgramPerformanceGate.NEEDS_THREE_MATCHED_VISITS,
            result.threads.single{it.key.physicalMachineId=="press-B"}
                .recentGate)
        assertEquals(KotlinProgramPerformanceGate.NEEDS_THREE_MATCHED_VISITS,
            result.threads.single{it.key.dayIndex==2}.recentGate)
    }

    @Test fun unknownInterveningMachinePreventsResurrectedATrend() {
        val measured=report(listOf(a("a1",1),a("a2",2),a("a3",3),
            Visit("unknown",4,machine=null)))
        val a=measured.threads.single{it.key.physicalMachineId=="press-A"}
        assertEquals(KotlinProgramPerformanceGate
            .AMBIGUOUS_INTERVENING_MACHINE,a.recentGate)
    }

    @Test fun newerDifferentUnitBasisInvalidatesOldStableMachineThread() {
        val measured=report(listOf(a("a1",1),a("a2",2),a("a3",3),
            Visit("new",4,loadBasis=LoadBasis.MACHINE_STACK)))
        val smith=measured.threads.single{
            it.key.loadBasis==LoadBasis.SMITH_TOTAL
        }
        assertEquals(KotlinProgramPerformanceGate
            .CHANGED_LOAD_BASIS_AFTER_LAST_MATCHED_VISIT,smith.recentGate)
    }

    @Test fun sameProgramDayIndexOnDifferentWeekdaysNeverPoolsThreeVisits() {
        val result=report(listOf(
            a("mon-1",1),a("mon-2",2),
            Visit("wed-1",3,calendarDay=2),
            Visit("wed-2",4,calendarDay=2),
            Visit("wed-3",5,calendarDay=2)))
        val mon=result.threads.single{it.key.calendarDay==0}
        val wed=result.threads.single{it.key.calendarDay==2}
        assertEquals(0,mon.key.dayIndex)
        assertEquals(0,wed.key.dayIndex)
        assertEquals(KotlinProgramPerformanceGate
            .CHANGED_WEEKDAY_OR_ROLE_AFTER_LAST_MATCHED_VISIT,mon.recentGate)
        assertEquals(KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY,
            wed.recentGate)
        assertEquals(listOf("wed-1","wed-2","wed-3"),
            wed.evaluatedWorkoutIds)
    }

    @Test fun changedIssuedExerciseRoleCannotReuseEarlierPrimaryTrend() {
        val result=report(listOf(a("p1",1),a("p2",2),a("p3",3),
            Visit("secondary",4,role=ComposerRole.SECONDARY)))
        val primary=result.threads.single{
            it.key.role==ComposerRole.PRIMARY
        }
        val secondary=result.threads.single{
            it.key.role==ComposerRole.SECONDARY
        }
        assertEquals(KotlinProgramPerformanceGate
            .CHANGED_WEEKDAY_OR_ROLE_AFTER_LAST_MATCHED_VISIT,
            primary.recentGate)
        assertEquals(KotlinProgramPerformanceGate.NEEDS_THREE_MATCHED_VISITS,
            secondary.recentGate)
        val (ledger,order)=facts(listOf(a("good",1)))
        assertEquals(KotlinProgramPerformanceResult.Rejected(
            KotlinProgramPerformanceRefusal.UNVERIFIED_SOURCE_FACT),
            KotlinProgramPerformanceEvidenceDomain.review(
                ledger.copy(workingSets=ledger.workingSets.mapIndexed{i,fact->
                    if(i==0)fact.copy(calendarDay=5) else fact
                }),order))
        assertEquals(KotlinProgramPerformanceResult.Rejected(
            KotlinProgramPerformanceRefusal.UNVERIFIED_SOURCE_FACT),
            KotlinProgramPerformanceEvidenceDomain.review(
                ledger.copy(workingSets=ledger.workingSets.mapIndexed{i,fact->
                    if(i==0)fact.copy(role=ComposerRole.SECONDARY) else fact
                }),order))
    }

    @Test fun duplicateOrdersUnknownPurposeAndMissingFactsFailClosed() {
        val (ledger,ordinals)=facts(listOf(a("w1",1),a("w2",2)))
        assertEquals(KotlinProgramPerformanceResult.Rejected(
            KotlinProgramPerformanceRefusal.INVALID_REPLAY_OR_ORDER),
            KotlinProgramPerformanceEvidenceDomain.review(ledger,
                ordinals.mapValues{1L}))
        assertEquals(KotlinProgramPerformanceResult.Rejected(
            KotlinProgramPerformanceRefusal.INVALID_REPLAY_OR_ORDER),
            KotlinProgramPerformanceEvidenceDomain.review(ledger,
                ordinals-"w1"))
        assertEquals(KotlinProgramPerformanceResult.Rejected(
            KotlinProgramPerformanceRefusal.INVALID_REPLAY_OR_ORDER),
            KotlinProgramPerformanceEvidenceDomain.review(
                ledger.copy(workouts=ledger.workouts.map{
                    it.copy(purpose=ComposerSessionPurpose.UNSPECIFIED)
                }),ordinals))
        assertEquals(KotlinProgramPerformanceResult.Rejected(
            KotlinProgramPerformanceRefusal.UNVERIFIED_SOURCE_FACT),
            KotlinProgramPerformanceEvidenceDomain.review(
                ledger.copy(workingSets=ledger.workingSets.dropLast(1)),ordinals))
    }
}
