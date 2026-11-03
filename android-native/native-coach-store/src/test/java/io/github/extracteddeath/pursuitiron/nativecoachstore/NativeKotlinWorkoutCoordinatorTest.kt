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
    private fun coordinator(auth:NativeKotlinWorkoutAuthority)=
        NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temporary.root.toPath()),auth)
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
}
