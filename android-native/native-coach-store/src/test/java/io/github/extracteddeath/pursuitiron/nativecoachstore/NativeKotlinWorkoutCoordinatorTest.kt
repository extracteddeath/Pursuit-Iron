package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinWorkoutCoordinatorTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun n(raw:String)=BigDecimal(raw)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30"),n("35")))
    private fun option(id:String,muscle:TrainingMuscle,role:ComposerRole)=
        ComposerExerciseOption(
            id,if(muscle==TrainingMuscle.CHEST)
                ComposerPattern.HORIZONTAL_PRESS else ComposerPattern.HORIZONTAL_PULL,
            setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock),
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            90,120,2,3,3,2,1,2,role=role)
    private val press=option("db-press",TrainingMuscle.CHEST,ComposerRole.PRIMARY)
    private val row=option("db-row",TrainingMuscle.LATS,ComposerRole.SECONDARY)
    private fun program():ComposerRequest {
        val window=DeclaredSessionWindow(1,120,null,60)
        val bounds=TrainingMuscle.entries.map{muscle ->
            when(muscle) {
                TrainingMuscle.CHEST,TrainingMuscle.LATS ->
                    DeclaredMuscleDoseBounds(muscle,n("2"),n("8"),n("10"),1)
                else -> DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
            }
        }
        return ComposerRequest(
            ComposerSplit.FULL_BODY,TrainingPhase.STRENGTH,
            listOf(ComposerDay(0,ComposerFocus.FULL_BODY,window),
                ComposerDay(2,ComposerFocus.FULL_BODY,window),
                ComposerDay(4,ComposerFocus.FULL_BODY,window)),
            bounds,listOf(press,row),3,1,
            listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,"db-press"),
                ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
                ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY)))
    }
    private fun policy(id:String,purpose:ComposerSessionPurpose,reps:RepBand,
        rir:Int?,rest:Int,scheme:SetLoadScheme=SetLoadScheme.STRAIGHT,
        backoff:BigDecimal?=null,warmups:List<DeclaredWarmupStep> = emptyList(),
        backoffReps:RepBand?=null,backoffRir:Int?=null)=
        ExercisePrescriptionPolicy(id,purpose,reps,rir,90,rest,
            scheme,backoff,warmups,backoffReps,backoffRir)
    private fun policies()=listOf(
        policy("db-press",ComposerSessionPurpose.STRENGTH_SKILL,
            RepBand(3,5),3,240,SetLoadScheme.LAST_SET_BACKOFF,n("5"),
            listOf(DeclaredWarmupStep(n("20"),8,60,60)),RepBand(6,8),2),
        policy("db-press",ComposerSessionPurpose.HYPERTROPHY,
            RepBand(8,12),2,120),
        policy("db-row",ComposerSessionPurpose.STRENGTH_SKILL,
            RepBand(6,10),2,180),
        policy("db-row",ComposerSessionPurpose.HYPERTROPHY,
            RepBand(10,15),null,120))
    private val grant=KotlinWorkoutSelectionGrant("approval-1","session-1",source,0)
    private fun approval()=NativeKotlinWorkoutApproval(
        "owner-1","athlete-1",source,source,grant,program(),policies(),"commit-1",1L)

    private class FakeAuthority(
        var approved:NativeKotlinWorkoutApproval?,
        var signedIn:Boolean=true,
        var afterFirstQuery:(()->Unit)?=null
    ):NativeKotlinWorkoutAuthority {
        var queries=0
        override fun currentApprovedWorkout(ownerKey:String,workoutId:String):
            NativeKotlinWorkoutApproval? {
            queries++
            val result=if(signedIn && ownerKey=="owner-1" && workoutId=="session-1")
                approved else null
            if(queries==1)afterFirstQuery?.invoke()
            return result
        }
        override fun canAccessIssuedWorkout(
            ownerKey:String,source:KotlinTrainingSourceRevision
        )=signedIn && ownerKey=="owner-1" && source.athleteId=="athlete-1"
    }
    private fun fence()=NativeKotlinApprovalRevisionFence(
        temporary.root.toPath())
    private fun coordinator(auth:NativeKotlinWorkoutAuthority)=
        NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temporary.root.toPath()),auth,fence())
    private fun issued(result:NativeKotlinIssueResult):NativeKotlinIssueResult.Committed {
        assertTrue(result.toString(),result is NativeKotlinIssueResult.Committed)
        return result as NativeKotlinIssueResult.Committed
    }
    private fun durable(result:NativeJournalFileResult):NativeJournalFileResult.Durable {
        assertTrue(result.toString(),result is NativeJournalFileResult.Durable)
        return result as NativeJournalFileResult.Durable
    }

    @Test fun approvedFullWeekStagesAndAtomicallyIssuesRealKotlinSession() {
        val auth=FakeAuthority(approval())
        val service=coordinator(auth)
        val result=issued(service.issue("owner-1","session-1"))
        assertFalse(result.replayed)
        assertEquals(2,auth.queries)
        val journal=result.journal
        assertEquals(source,journal.snapshot.source)
        assertEquals("session-1",journal.snapshot.workoutId)
        assertEquals(0,journal.snapshot.calendarDay)
        assertTrue(journal.snapshot.targets.size>=4)
        assertEquals(DraftSetType.WARMUP,journal.snapshot.targets.first().type)
        assertEquals(n("20"),
            (journal.snapshot.targets.first().load as DraftSetLoad.Verified).value)
        assertEquals(journal,durable(coordinator(auth)
            .open("owner-1","session-1")).journal)
        assertTrue(issued(service.issue("owner-1","session-1")).replayed)
    }

    @Test fun completeSessionAndFinishColdReopenWithoutJavaScript() {
        val auth=FakeAuthority(approval())
        val service=coordinator(auth)
        var journal=issued(service.issue("owner-1","session-1")).journal
        for(target in journal.snapshot.targets) {
            val load=target.load as? DraftSetLoad.Verified
            val event=KotlinJournalOperation.RecordSet(
                "receipt-"+journal.revision,journal.revision,target.setKey,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(load?.value,load?.unit,target.reps.minimum,
                    if(target.type==DraftSetType.WARMUP)null else 2))
            journal=durable(service.append("owner-1","session-1",event)).journal
        }
        assertEquals(0,journal.unresolvedSetCount)
        val finish=KotlinJournalOperation.Finish("finish",journal.revision)
        val completed=durable(service.append("owner-1","session-1",finish))
        assertEquals("finish",completed.journal.finishedByEventId)
        assertEquals(completed.journal,
            durable(coordinator(auth).open("owner-1","session-1")).journal)
        assertTrue(durable(coordinator(auth).append(
            "owner-1","session-1",finish)).replayed)
        assertEquals(journal.snapshot.targets,completed.journal.snapshot.targets)
    }

    @Test fun signedOutCrossAthleteOrRevokedGrantNeverIssue() {
        val auth=FakeAuthority(approval(),signedIn=false)
        val service=coordinator(auth)
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.NOT_AUTHORIZED),
            service.issue("owner-1","session-1"))
        auth.signedIn=true
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.NOT_AUTHORIZED),
            service.issue("owner-2","session-1"))
        auth.approved=approval().copy(athleteId="another-athlete")
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.NOT_AUTHORIZED),
            service.issue("owner-1","session-1"))
        auth.approved=approval().copy(grant=grant.copy(workoutId="other-session"))
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.NOT_AUTHORIZED),
            service.issue("owner-1","session-1"))
        auth.approved=approval()
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            service.open("owner-1","session-1"))
    }

    @Test fun midIssueGrantGymOrPlanChangeIsDeniedInsideStoreLock() {
        val changes=listOf<(NativeKotlinWorkoutApproval)->NativeKotlinWorkoutApproval>(
            {it.copy(currentSource=source.copy(programRevision=9))},
            {it.copy(currentSource=source.copy(gymRevision=4))},
            {it.copy(grant=grant.copy(dayIndex=1))},
            {it.copy(storageReceiptId="commit-2")},
            // The selected strength day could stay identical while an
            // approved OTHER day's hypertrophy targets silently change.
            {it.copy(policies=it.policies.map {p->
                if(p.exerciseId=="db-row" &&
                    p.purpose==ComposerSessionPurpose.HYPERTROPHY)
                    p.copy(targetRir=1) else p
            })},
            {it.copy(policies=it.policies.filterNot{p->
                p.exerciseId=="db-row" && p.purpose==ComposerSessionPurpose.HYPERTROPHY
            })}
        )
        for(change in changes) {
            val auth=FakeAuthority(approval())
            auth.afterFirstQuery={auth.approved=change(checkNotNull(auth.approved))}
            val service=coordinator(auth)
            assertEquals(NativeKotlinIssueResult.Rejected(
                NativeKotlinIssueRefusal.APPROVAL_CHANGED,
                storageReason=NativeJournalFileRefusal.NOT_AUTHORIZED),
                service.issue("owner-1","session-1"))
            auth.approved=approval()
            assertEquals(NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_ISSUED),
                service.open("owner-1","session-1"))
        }
    }

    @Test fun incompletePlanOrInvented38Point5PoundsCannotIssue() {
        val auth=FakeAuthority(approval())
        val service=coordinator(auth)
        auth.approved=approval().copy(
            policies=policies().filterNot{it.exerciseId=="db-row" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY})
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,
            KotlinSnapshotRejection.PROGRAM_UNRESOLVED),
            service.issue("owner-1","session-1"))
        auth.approved=approval().copy(program=program().copy(
            exercises=listOf(press.copy(equipment=ComposerEquipment.Verified(
                LoadUnit.LB,n("38.5"),stock)),row)))
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,
            KotlinSnapshotRejection.PROGRAM_UNRESOLVED),
            service.issue("owner-1","session-1"))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_ISSUED),
            service.open("owner-1","session-1"))
    }

    @Test fun competingApprovedProgramUpdateCannotCommitDuringWorkoutIssuance() {
        val auth=FakeAuthority(approval())
        val enteredIssue=java.util.concurrent.CountDownLatch(1)
        val finishReview=java.util.concurrent.CountDownLatch(1)
        val attemptedUpdate=java.util.concurrent.CountDownLatch(1)
        auth.afterFirstQuery={
            enteredIssue.countDown()
            check(finishReview.await(10,java.util.concurrent.TimeUnit.SECONDS))
        }
        val executor=java.util.concurrent.Executors.newFixedThreadPool(2)
        try {
            val issuing=executor.submit<NativeKotlinIssueResult> {
                coordinator(auth).issue("owner-1","session-1")
            }
            assertTrue(enteredIssue.await(10,java.util.concurrent.TimeUnit.SECONDS))
            val modifying=executor.submit {
                attemptedUpdate.countDown()
                // This is the REQUIRED production approval-write protocol.
                // It must use the SAME lock root as workout issuance.
                fence().withOwnerTransaction("owner-1") {
                    auth.approved=approval().copy(
                        currentSource=source.copy(programRevision=9))
                }
            }
            assertTrue(attemptedUpdate.await(10,java.util.concurrent.TimeUnit.SECONDS))
            assertThrows(java.util.concurrent.TimeoutException::class.java) {
                modifying.get(100,java.util.concurrent.TimeUnit.MILLISECONDS)
            }
            finishReview.countDown()
            val original=issued(issuing.get(20,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(source,original.journal.snapshot.source)
            modifying.get(20,java.util.concurrent.TimeUnit.SECONDS)
            assertEquals(9L,auth.approved?.currentSource?.programRevision)
            // The old frozen workout remains valid for its owner. Reissuing
            // under a revoked source may not create a new target.
            assertEquals(original.journal,
                durable(coordinator(auth).open("owner-1","session-1")).journal)
            assertEquals(NativeKotlinIssueResult.Rejected(
                NativeKotlinIssueRefusal.NOT_AUTHORIZED),
                coordinator(auth).issue("owner-1","session-1"))
        } finally {
            finishReview.countDown()
            executor.shutdownNow()
        }
    }

    @Test fun oldFrozenWorkoutCanResumeAfterPlanEditButNeverAfterLogout() {
        val auth=FakeAuthority(approval())
        val service=coordinator(auth)
        val original=issued(service.issue("owner-1","session-1")).journal
        auth.approved=approval().copy(
            currentSource=source.copy(programRevision=9))
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.NOT_AUTHORIZED),
            service.issue("owner-1","session-1"))
        assertEquals(original,durable(coordinator(auth)
            .open("owner-1","session-1")).journal)
        auth.signedIn=false
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_AUTHORIZED),
            coordinator(auth).open("owner-1","session-1"))
        assertEquals(NativeJournalFileResult.Rejected(
            NativeJournalFileRefusal.NOT_AUTHORIZED),
            coordinator(auth).append("owner-1","session-1",
                KotlinJournalOperation.Finish("finish",0)))
    }

    @Test fun repeatedBackoffTargetsRemainExactThroughApprovalIssueAndColdReopen() {
        val compatible=policies().map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(warmups=emptyList(),
                    backoffReps=p.workingReps,backoffRir=p.targetRir)
            else p
        }
        val baseApproval=approval().copy(policies=compatible)
        val request=KotlinWorkoutSnapshotRequest(source,source,
            baseApproval.grant,baseApproval.program,baseApproval.policies)
        val stage=(KotlinWorkoutSnapshotDomain.stage(request)
            as KotlinWorkoutSnapshotResult.Staged).snapshot
        val rows=stage.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.sortedBy{it.ordinal}
        assertEquals(3,rows.size)
        val priorTargets=listOf(4,4,3)
        val observed=listOf(4,3,3)
        val history=ProgressionWorkoutEvidence(
            "prior-repeat","db-press",LoadUnit.LB,rows.first().reps,rows.size,
            rows.mapIndexed{i,set->
                val load=(set.load as DraftSetLoad.Verified).value
                WorkingSetFact(i,SetOutcome.COMPLETED,load,observed[i],
                    3,load,set.targetRir)
            })
        val proposal=KotlinAdaptiveSetPrescriptionDomain.draft(
            AdaptiveSetPrescriptionRequest(
                request,source,history,7,7,CoachReadiness.NORMAL,false,
                previouslyIssuedExactReps=priorTargets))
            as AdaptiveSetPrescriptionResult.Candidate
        assertEquals(AdaptiveRepDecisionKind.REPEAT_TARGET_RIR_ALIGNED_REPS,
            proposal.decisionKind)
        assertEquals(priorTargets,proposal.newRepTargets)
        val acceptance=baseApproval.copy(acceptedRepTargets=
            NativeKotlinAcceptedRepTargets(
                "prior-repeat",7,"db-press",proposal.editedSetKey,
                proposal.observedReps,proposal.newRepTargets,
                CoachReadiness.NORMAL,proposal.decisionKind))
        val persisted=NativeKotlinApprovalCodec.decode(
            NativeKotlinApprovalCodec.encode(acceptance))
        val authority=FakeAuthority(persisted)
        val verifier=NativeKotlinAcceptedRepSnapshotVerifier {owner,record->
            if(owner=="owner-1" && record==persisted)proposal.workout
            else null
        }
        val service=NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temporary.root.toPath()),
            authority,fence(),verifier)
        val issued=issued(service.issue("owner-1","session-1"))
        assertEquals(priorTargets,issued.journal.snapshot.targets.filter{
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.sortedBy{it.ordinal}.map{it.reps.minimum})
        assertEquals(listOf(n("30"),n("30"),n("25")),
            issued.journal.snapshot.targets.filter{
                it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
            }.sortedBy{it.ordinal}.map{
                (it.load as DraftSetLoad.Verified).value
            })
        val restarted=NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temporary.root.toPath()),
            authority,fence())
        assertEquals(issued.journal,
            durable(restarted.open("owner-1","session-1")).journal)
    }

    @Test fun acceptedBackoffRepTargetsCanIssueOnlyFromFullReverifiedSnapshot() {
        val reviewedPolicies=policies().map {p ->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(warmups=emptyList(),
                    backoffReps=p.workingReps,backoffRir=p.targetRir)
            else p
        }
        val regular=approval().copy(policies=reviewedPolicies)
        val next=KotlinWorkoutSnapshotRequest(source,source,
            regular.grant,regular.program,regular.policies)
        val baseline=(KotlinWorkoutSnapshotDomain.stage(next)
            as KotlinWorkoutSnapshotResult.Staged).snapshot
        val working=baseline.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.sortedBy{it.ordinal}
        assertTrue(working.size>=2)
        assertEquals(listOf(n("30"),n("30"),n("25")),
            working.map{(it.load as DraftSetLoad.Verified).value})
        val band=working.first().reps
        val history=ProgressionWorkoutEvidence(
            "prior-finished","db-press",LoadUnit.LB,band,working.size,
            working.mapIndexed{i,set ->
                val actual=(set.load as DraftSetLoad.Verified).value
                WorkingSetFact(i,SetOutcome.COMPLETED,actual,
                    if(i==working.lastIndex)band.minimum
                        else band.minimum+1,
                    if(i==working.lastIndex)4 else 3,
                    actual,set.targetRir)
            })
        val proposed=KotlinAdaptiveSetPrescriptionDomain.draft(
            AdaptiveSetPrescriptionRequest(next,source,history,
                7,7,CoachReadiness.NORMAL,false))
        assertTrue(proposed.toString(),
            proposed is AdaptiveSetPrescriptionResult.Candidate)
        val candidate=proposed as AdaptiveSetPrescriptionResult.Candidate
        assertEquals(working.last().setKey,candidate.editedSetKey)
        assertEquals(working.size,candidate.newRepTargets.size)
        val accepted=regular.copy(acceptedRepTargets=NativeKotlinAcceptedRepTargets(
            "prior-finished",7,"db-press",candidate.editedSetKey,
            candidate.observedReps,candidate.newRepTargets,
            CoachReadiness.NORMAL))
        val authority=FakeAuthority(accepted)
        val withoutVerifier=coordinator(authority).issue("owner-1","session-1")
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,
            KotlinSnapshotRejection.UNVERIFIED_TARGET),
            withoutVerifier)
        // A dishonest verifier could not change loading or other exercises:
        // only the exactly rederived individualized REP fields are allowed.
        val fakeLoad=candidate.workout.copy(
            targets=candidate.workout.targets.map {set ->
                if(set.setKey==working.last().setKey)
                    set.copy(load=DraftSetLoad.Verified(
                        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("30")))
                else set
            })
        val altered=NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temporary.root.toPath()),
            authority,fence(),NativeKotlinAcceptedRepSnapshotVerifier { _,_->
                fakeLoad
            }).issue("owner-1","session-1")
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,
            KotlinSnapshotRejection.UNVERIFIED_TARGET),altered)
        val service=NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temporary.root.toPath()),
            authority,fence(),NativeKotlinAcceptedRepSnapshotVerifier {owner,proof->
                if(owner=="owner-1" && proof==accepted)candidate.workout else null
            })
        val committed=issued(service.issue("owner-1","session-1"))
        val realSets=committed.journal.snapshot.targets.filter {
            it.exerciseId=="db-press" && it.type==DraftSetType.WORKING
        }.sortedBy {it.ordinal}
        assertEquals(listOf(n("30"),n("30"),n("25")),
            realSets.map{(it.load as DraftSetLoad.Verified).value})
        assertEquals(candidate.newRepTargets,
            realSets.map{it.reps.minimum})
        assertTrue(realSets.all{it.reps.minimum==it.reps.maximum})
        assertEquals(baseline.targets.filter{it.exerciseId=="db-row"},
            committed.journal.snapshot.targets.filter{it.exerciseId=="db-row"})
        val reopened=NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temporary.root.toPath()),
            authority,fence())
        assertEquals(committed.journal,durable(
            reopened.open("owner-1","session-1")).journal)
    }

}
