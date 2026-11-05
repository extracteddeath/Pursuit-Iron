package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * REAL app-private file stores and finish transactions, not synthetic
 * evidence: read both saved workouts through the exhaustive native ledger.
 */
class NativeKotlinOwnedLoadCoachingIntegrationTest {
    @get:Rule val temp=TemporaryFolder()
    private val root get()=temp.root.toPath()
    private fun n(value:String)=BigDecimal(value)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
    private val identity=NativeKotlinCurrentIdentity {
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    }
    private val fence by lazy {NativeKotlinApprovalRevisionFence(root)}
    private val approvals by lazy {
        NativeKotlinApprovalFileAuthority(root,fence,identity)
    }
    private val journals by lazy {KotlinNativeJournalFileStore(root)}
    private val corrections by lazy {
        KotlinNativeSavedCorrectionFileStore(root,journals)
    }
    private val history by lazy {NativeKotlinHistoryManifestFileStore(root)}
    private val transactions by lazy {
        NativeKotlinHistoryTransactionCoordinator(
            history,journals,corrections,approvals,fence)
    }
    private val drafts by lazy {
        NativeCustomCycleDraftFileStore(root,fence,identity)
    }
    private val authoring by lazy {
        NativeKotlinCustomAuthoringSourceFileStore(root,fence,identity)
    }
    private val signoffs by lazy {
        NativeKotlinCustomCycleSignoffFileStore(
            root,fence,identity,drafts,authoring)
    }
    private val preflight by lazy {
        NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,authoring,signoffs)
    }
    private val prepared by lazy {
        NativeKotlinCustomWorkoutPreparedStore(
            root,fence,identity,preflight)
    }
    private val custom by lazy {
        NativeKotlinCustomWorkoutJournalFileStore(
            root,fence,identity,prepared,preflight,history,journals)
    }
    private val reader by lazy {
        NativeKotlinMixedHistoryReadCoordinator(
            history,journals,corrections,custom,approvals,identity,fence)
    }

    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("20"),n("25"),n("30"),n("35")))
    private fun option(id:String,muscle:TrainingMuscle,role:ComposerRole)=
        ComposerExerciseOption(
            id,if(muscle==TrainingMuscle.CHEST)
                ComposerPattern.HORIZONTAL_PRESS else ComposerPattern.HORIZONTAL_PULL,
            setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock),
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            90,120,2,2,3,3,1,2,role=role)
    private val program by lazy {
        val window=DeclaredSessionWindow(1,120,null,60)
        val bounds=TrainingMuscle.entries.map { muscle ->
            if(muscle in setOf(TrainingMuscle.CHEST,TrainingMuscle.LATS))
                DeclaredMuscleDoseBounds(muscle,n("2"),n("8"),n("10"),1)
            else DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
        }
        ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.STRENGTH,
            listOf(ComposerDay(0,ComposerFocus.FULL_BODY,window),
                ComposerDay(2,ComposerFocus.FULL_BODY,window),
                ComposerDay(4,ComposerFocus.FULL_BODY,window)),
            bounds,listOf(
                option("db-press",TrainingMuscle.CHEST,ComposerRole.PRIMARY),
                option("db-row",TrainingMuscle.LATS,ComposerRole.SECONDARY)),
            3,1,listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,"db-press"),
                ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
                ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY)))
    }
    private fun policy(id:String,purpose:ComposerSessionPurpose)=
        ExercisePrescriptionPolicy(id,purpose,RepBand(8,12),2,
            90,120,SetLoadScheme.STRAIGHT)
    private val policies=listOf(
        policy("db-press",ComposerSessionPurpose.STRENGTH_SKILL),
        policy("db-press",ComposerSessionPurpose.HYPERTROPHY),
        policy("db-row",ComposerSessionPurpose.STRENGTH_SKILL),
        policy("db-row",ComposerSessionPurpose.HYPERTROPHY))

    private fun accept(id:Int,day:Int):KotlinWorkoutJournalState {
        val grant=KotlinWorkoutSelectionGrant(
            "grant-$id","workout-$id",source,day)
        val approval=NativeKotlinWorkoutApproval(
            "owner-1","athlete-1",source,source,grant,program,policies,
            "storage-$id",id.toLong())
        assertEquals(NativeApprovalWriteResult.Committed(id.toLong()),
            approvals.publish("owner-1",(id-1).toLong(),approval))
        val issued=NativeKotlinWorkoutCoordinator(journals,approvals,fence)
            .issue("owner-1","workout-$id")
        assertTrue(issued.toString(),issued is NativeKotlinIssueResult.Committed)
        return (issued as NativeKotlinIssueResult.Committed).journal
    }
    private fun complete(id:Int,day:Int):Pair<KotlinWorkoutJournalState,String> {
        var journal=accept(id,day)
        var pressKey=""
        for ((index,target) in journal.snapshot.targets.withIndex()) {
            val load=(target.load as DraftSetLoad.Verified)
            if(target.exerciseId=="db-press" &&
                target.type==DraftSetType.WORKING && pressKey.isEmpty())
                pressKey=target.setKey
            val event=KotlinJournalOperation.RecordSet(
                "set-$id-$index",journal.revision,target.setKey,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(load.value,load.unit,12,2))
            val written=journals.appendStored(
                "owner-1",journal.snapshot.workoutId,event,
                approvals::canAccessIssuedWorkout)
            assertTrue(written.toString(),written is NativeJournalFileResult.Durable)
            journal=(written as NativeJournalFileResult.Durable).journal
        }
        assertTrue(pressKey.isNotBlank())
        val end=transactions.complete("owner-1","athlete-1",
            "workout-$id",KotlinJournalOperation.Finish(
                "finish-$id",journal.revision))
        assertTrue(end.toString(),end is NativeHistoryTransactionResult.Durable)
        return journal to pressKey
    }
    private fun review():NativeOwnerLoadReviewResult =
        reader.reviewCurrentApprovedLoad(
            "owner-1","athlete-1",approvals,"db-press",
            n("5"),CoachReadiness.NORMAL,false)

    @Test fun actualSavedWorkoutCorrectionRevokesEarnedProgressionWithoutBacktracking() {
        val enrollment=transactions.enroll("owner-1","athlete-1") {
            owner,athlete ->
            owner=="owner-1" && athlete=="athlete-1"
        }
        assertTrue(enrollment.toString(),
            enrollment is NativeHistoryTransactionResult.Durable)
        val first=complete(1,1)
        val last=complete(2,2)
        val raw=review()
        assertTrue(raw.toString(),raw is NativeOwnerLoadReviewResult.Review)
        val coaching=(raw as NativeOwnerLoadReviewResult.Review)
            .coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-2"),
            coaching.comparedWorkoutIds)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            coaching.coaching.status)
        assertEquals(n("35"),coaching.coaching.candidateLoad)

        val originalEvent=last.first.effectiveSets.getValue(last.second)
            .operation.eventId
        val edit=KotlinSavedSetCorrection(
            "edit-latest",0,last.second,originalEvent,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("30"),LoadUnit.LB,8,2))
        val corrected=transactions.correct(
            "owner-1","athlete-1","workout-2",edit)
        assertTrue(corrected.toString(),
            corrected is NativeHistoryTransactionResult.Durable)
        val after=review() as NativeOwnerLoadReviewResult.Review
        val newest=after.coaching as NativeCurrentLoadReviewResult.Reviewed
        assertEquals(listOf("workout-1","workout-2"),
            newest.comparedWorkoutIds)
        assertNotEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            newest.coaching.status)
        assertNull(newest.coaching.candidateLoad)
        assertEquals(n("30"),
            ((program.exercises.first().equipment as ComposerEquipment.Verified)
                .plannedLoad))
        assertFalse(newest.coaching.maySaveProgram)
        assertTrue(first.first.snapshot.targets.isNotEmpty())
    }
}
