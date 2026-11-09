package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinComposerSwapReviewDomainTest {
    private fun n(raw:String)=BigDecimal(raw)
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("20"),n("25"),n("30"),n("35")))
    private fun option(id:String,pattern:ComposerPattern,
        muscle:TrainingMuscle=TrainingMuscle.CHEST,
        equipment:ComposerEquipment=ComposerEquipment.Verified(
            LoadUnit.LB,n("25"),stock)
    )=ComposerExerciseOption(
        id,pattern,setOf(ComposerFocus.UPPER),equipment,
        listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,
            BigDecimal.ONE)),60,90,2,4,3,0,1,
        role=ComposerRole.PRIMARY,
        sourcePracticalReps=RepBand(6,15))
    private val first=option("db-press",ComposerPattern.HORIZONTAL_PRESS)
    private val same=option("chest-machine",ComposerPattern.HORIZONTAL_PRESS)
    private val different=option("chest-fly",ComposerPattern.ISOLATION)
    private val wrongRole=same.copy(exerciseId="triceps-only",
        role=ComposerRole.ACCESSORY)
    private val source=KotlinTrainingSourceRevision("athlete-1",1,
        "program-1",1,"gym-1",1,"catalog-1","rules-1")
    private val firstLoad=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("30"))
    private fun target(id:String,key:String,ordinal:Int,
        role:ComposerRole=ComposerRole.PRIMARY,
        load:DraftSetLoad=firstLoad)=KotlinWorkoutTarget(
        id,role,ComposerSessionPurpose.HYPERTROPHY,key,
        DraftSetType.WORKING,ordinal,load,
        RepBand(8,12),2,60,90)
    private val issued=KotlinStagedWorkout(
        source,"grant-1","workout-1",0,0,
        ComposerSessionPurpose.HYPERTROPHY,45,
        listOf(target("db-press","old-0",0),
            target("db-press","old-1",1),
            target("row","row-0",0,ComposerRole.ACCESSORY)))
    private val identity=KotlinStoredWorkoutIdentity(
        "receipt-1","workout-1",source,1)
    private fun start()=KotlinWorkoutJournalDomain.begin(issued,identity)
    private fun intent(ordinal:Int,load:String="25")=
        KotlinComposerSwapSetIntent(DraftSetType.WORKING,ordinal,
            DraftSetLoad.Verified(LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,n(load)),
            RepBand(8,12),2)
    private fun reviewed(journal:KotlinWorkoutJournalState,
        event:String="swap-1",rev:Long=journal.revision,
        option:ComposerExerciseOption=same,
        sets:List<KotlinComposerSwapSetIntent>=listOf(intent(0),intent(1))
    )=KotlinComposerSwapReviewDomain.review(
        journal,event,rev,"db-press",option,ComposerFocus.UPPER,sets)
    private fun accept(j:KotlinWorkoutJournalState,
        op:KotlinJournalOperation):KotlinWorkoutJournalState {
        val result=KotlinWorkoutJournalDomain.reduce(j,op)
        assertTrue(result.toString(),result is KotlinJournalDecision.Proposed)
        return (result as KotlinJournalDecision.Proposed).state
    }

    @Test fun gymBackedChoicesRankReviewedPatternAndExplicitRole() {
        val ranked=KotlinComposerSwapRankingDomain.rank(
            first,listOf(different,same,wrongRole,first),
            setOf("db-press"),ComposerFocus.UPPER)
        assertEquals(listOf("chest-machine","chest-fly"),
            ranked.map{it.option.exerciseId})
        assertTrue(ranked.first().samePattern)
        assertEquals(1,ranked.first().sharedReviewedMuscleCount)
        assertEquals(listOf("chest-fly"),
            KotlinComposerSwapRankingDomain.rank(
                first,listOf(same,different),setOf("chest-machine"),
                ComposerFocus.UPPER,"fly").map{it.option.exerciseId})
    }

    @Test fun swapPreservesOtherSetActualAndCanUndoThenFinishOriginal() {
        val recorded=accept(start(),KotlinJournalOperation.RecordSet(
            "other-actual",0L,"row-0",KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("30"),LoadUnit.LB,11,2)))
        val proposed=reviewed(recorded)
            as KotlinComposerSwapReview.Ready
        val swapped=accept(recorded,proposed.operation)
        assertEquals(1,swapped.effectiveSets.size)
        assertEquals(2,swapped.unresolvedSetCount)
        assertEquals(issued,swapped.originalSnapshot)
        assertEquals(listOf("chest-machine","chest-machine","row"),
            swapped.snapshot.targets.map{it.exerciseId})
        val recovered=KotlinWorkoutJournalReplayDomain.replay(
            issued,identity,swapped.receipts,swapped.revision,null)
            as KotlinJournalReplayResult.Recovered
        assertEquals(swapped,recovered.journal)
        val undone=accept(swapped,KotlinJournalOperation.UndoSwap(
            "undo-1",2L,"swap-1"))
        assertEquals(issued,undone.snapshot)
        assertEquals(recorded.effectiveSets,undone.effectiveSets)
        assertEquals(3,undone.receipts.size)
        assertEquals(KotlinJournalDecision.Rejected(
            KotlinJournalRefusal.SWAP_UNDO_UNAVAILABLE),
            KotlinWorkoutJournalDomain.reduce(undone,
                KotlinJournalOperation.UndoSwap("undo-again",3L,"swap-1")))
        val firstLogged=accept(undone,KotlinJournalOperation.RecordSet(
            "old-completed-0",3L,"old-0",KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("30"),LoadUnit.LB,10,2)))
        val secondLogged=accept(firstLogged,KotlinJournalOperation.RecordSet(
            "old-completed-1",4L,"old-1",KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("30"),LoadUnit.LB,9,2)))
        val finished=accept(secondLogged,KotlinJournalOperation.Finish(
            "finish-original",5L))
        assertTrue(finished.finishedByEventId=="finish-original")
        assertTrue(KotlinWorkoutHistoryEvidenceDomain.project(finished)
            is KotlinHistoryProjectionResult.Candidate)
    }

    @Test fun swapRefusesRecordedNewOrOldAndUnverifiedReplacementWeights() {
        val firstState=start()
        assertTrue(reviewed(firstState,sets=listOf(intent(0,"27"),
            intent(1))) is KotlinComposerSwapReview.Rejected)
        assertTrue(reviewed(firstState,option=wrongRole)
            is KotlinComposerSwapReview.Rejected)
        assertTrue(reviewed(firstState,sets=listOf(intent(0),
            intent(0))) is KotlinComposerSwapReview.Rejected)
        val operation=(reviewed(firstState)
            as KotlinComposerSwapReview.Ready).operation
        val changed=accept(firstState,operation)
        val started=accept(changed,KotlinJournalOperation.RecordSet(
            "replacement-work",1L,"swap:swap-1:working:0",
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n("25"),LoadUnit.LB,10,2)))
        assertEquals(KotlinJournalDecision.Rejected(
            KotlinJournalRefusal.SWAP_ALREADY_STARTED),
            KotlinWorkoutJournalDomain.reduce(started,
                KotlinJournalOperation.UndoSwap("undo-unsafe",2L,"swap-1")))
        val logged=accept(start(),KotlinJournalOperation.RecordSet(
            "old-work",0L,"old-0",KotlinJournalSetOutcome.SKIPPED,null))
        assertTrue(reviewed(logged) is KotlinComposerSwapReview.Rejected)
        assertEquals(KotlinJournalDecision.Rejected(
            KotlinJournalRefusal.SWAP_IDENTITY_CONFLICT),
            KotlinWorkoutJournalDomain.reduce(changed,
                operation.copy(eventId="swap-new",expectedJournalRevision=1,
                    previousExerciseId="row")))
    }
}
