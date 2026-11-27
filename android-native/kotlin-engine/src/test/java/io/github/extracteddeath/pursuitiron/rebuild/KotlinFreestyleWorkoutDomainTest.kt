package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinFreestyleWorkoutDomainTest {
    private val domain=KotlinFreestyleWorkoutDomain
    private fun n(s:String)=BigDecimal(s)
    private val gym=ReviewedGymProfile("gym-1",LoadUnit.LB,
        mapOf("cable-1" to VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.CABLE_STACK,
            listOf(n("20"),n("25"),n("30")))),
        setOf("cable-station"),setOf("rope","straight-bar"))
    private fun setup(g:ReviewedGymProfile=gym,athlete:String="athlete-1")=AthleteSetupReview(
        AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,emptyList(),ReviewedAthleteSetup(
            "profile-1",athlete,AthleteTrainingGoal.POWERBUILDING,AthleteExperience.ADVANCED,
            ComposerSplit.FULL_BODY,setOf(0,2,4),"America/New_York",SessionTimeBucket.SIXTY_TO_NINETY,
            HardSessionLimit.NO_HARD_LIMIT,g,true,true,true,2,emptySet(),emptySet(),emptySet(),
            emptySet(),emptySet(),emptySet(),setOf(1,3,5,6)))
    private fun movement(id:String,attachment:String)=CatalogMovement(
        id,"triceps-family",id,attachment,ComposerPattern.ISOLATION,ComposerRole.SECONDARY,
        setOf(ComposerFocus.FULL_BODY),"cable-1",LoadBasis.CABLE_STACK,
        listOf(DeclaredMuscleCredit(TrainingMuscle.TRICEPS,MuscleCreditRole.DIRECT,BigDecimal.ONE)),
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"review-1",setOf("cable-station"))
    private val catalog=listOf(movement("rope-pushdown","rope"),movement("straight-pushdown","straight-bar"))
    private fun start()=checkNotNull(domain.begin("free-1",checkNotNull(domain.source(setup(),1))))
    private fun next(state:KotlinFreestyleWorkout,op:KotlinFreestyleOperation)=
        (domain.reduce(state,op) as KotlinFreestyleDecision.Proposed).workout
    private fun actual(weight:String)=KotlinPerformedSet(n(weight),LoadUnit.LB,9,null)
    private fun add(state:KotlinFreestyleWorkout,slot:String,exercise:String)=next(state,
        KotlinFreestyleOperation.Add("add-"+slot,state.revision,
            checkNotNull(domain.selectedExercise(state,setup(),1,catalog,slot,exercise))))
    private fun rejected(state:KotlinFreestyleWorkout,op:KotlinFreestyleOperation,reason:KotlinFreestyleRefusal) {
        assertEquals(KotlinFreestyleDecision.Rejected(reason),domain.reduce(state,op))
    }

    @Test fun emptyToFinishedRoutineReplaysActualsAndStableSlotsWithoutTargets() {
        var state=start()
        assertTrue(state.exercises.isEmpty())
        rejected(state,KotlinFreestyleOperation.Finish("empty",0),KotlinFreestyleRefusal.EMPTY_WORKOUT)
        state=add(state,"slot-a","rope-pushdown")
        state=add(state,"slot-b","straight-pushdown")
        val first=KotlinFreestyleOperation.Record("first",state.revision,"slot-a","set-a",
            DraftSetType.WORKING,KotlinJournalSetOutcome.COMPLETED,actual("27.125"))
        state=next(state,first)
        val retry=domain.reduce(state,first) as KotlinFreestyleDecision.Proposed
        assertTrue(retry.replayed)
        assertEquals(state,retry.workout)
        val corrected=first.copy(eventId="corrected",expectedRevision=state.revision,
            actual=actual("25"),correctsEventId="first")
        state=next(state,corrected)
        state=next(state,KotlinFreestyleOperation.Reorder("reorder",state.revision,listOf("slot-b","slot-a")))
        state=next(state,KotlinFreestyleOperation.Record("other",state.revision,"slot-b","set-b",
            DraftSetType.WARMUP,KotlinJournalSetOutcome.COMPLETED,actual("20")))
        val finished=next(state,KotlinFreestyleOperation.Finish("finish",state.revision))
        assertEquals(finished,domain.replay(finished.workoutId,finished.source,finished.receipts))
        assertEquals(corrected,finished.effectiveSets["set-a"])
        assertNull(finished.effectiveSets["set-a"]?.actual?.actualRir)
        assertFalse(finished.mayPublishHistory)
        assertFalse(finished.mayChangeProgram)
        val routine=checkNotNull(domain.routine(finished,"routine-1","Arms"))
        assertEquals(listOf("straight-pushdown","rope-pushdown"),routine.slots.map{it.exerciseId})
        assertEquals(listOf(DraftSetType.WARMUP),routine.slots.first().setTypes)
        val restarted=checkNotNull(domain.startRoutine("free-2",routine,setup(),2,catalog))
        assertEquals(routine.slots.map{it.exerciseId},restarted.exercises.map{it.exerciseId})
        assertTrue(restarted.effectiveSets.isEmpty())
        assertNull(restarted.finishedByEventId)
        assertEquals(2L,restarted.source.gymRevision)
        assertEquals(restarted,domain.replay(restarted.workoutId,restarted.source,restarted.receipts))
        assertNull(domain.startRoutine("free-3",routine,setup(gym.copy(availableAttachments=setOf("rope"))),2,catalog))
        assertNull(domain.startRoutine("free-4",routine,setup(athlete="athlete-2"),2,catalog))
        assertNull(domain.routine(finished.copy(effectiveSets=emptyMap()),"forged","Arms"))
    }

    @Test fun sourceReviewAndCorrectionsDoNotRelabelFactsOrAcceptStaleInputs() {
        var state=start()
        assertNull(domain.selectedExercise(state,setup(),2,catalog,"slot-a","rope-pushdown"))
        assertNull(domain.selectedExercise(state,setup(athlete="other"),1,catalog,"slot-a","rope-pushdown"))
        assertNull(domain.selectedExercise(state,setup(gym.copy(availableAttachments=emptySet())),1,
            catalog,"slot-a","rope-pushdown"))
        state=add(state,"slot-a","rope-pushdown")
        state=add(state,"slot-b","rope-pushdown")
        val record=KotlinFreestyleOperation.Record("first",state.revision,"slot-a","set-a",
            DraftSetType.WORKING,KotlinJournalSetOutcome.COMPLETED,actual("25"))
        rejected(state,record.copy(expectedRevision=0),KotlinFreestyleRefusal.STALE_REVISION)
        rejected(state,record.copy(actual=actual("25").copy(unit=LoadUnit.KG)),KotlinFreestyleRefusal.INVALID_SET)
        rejected(state,record.copy(slotId="missing"),KotlinFreestyleRefusal.UNKNOWN_SLOT)
        rejected(state,KotlinFreestyleOperation.Reorder("bad",state.revision,listOf("slot-a","slot-a")),
            KotlinFreestyleRefusal.INVALID_ORDER)
        state=next(state,record)
        rejected(state,record.copy(actual=actual("30")),KotlinFreestyleRefusal.EVENT_COLLISION)
        rejected(state,record.copy(eventId="bad",expectedRevision=state.revision,slotId="slot-b",
            correctsEventId="first"),KotlinFreestyleRefusal.INVALID_CORRECTION)
        rejected(state,record.copy(eventId="bad",expectedRevision=state.revision,type=DraftSetType.WARMUP,
            correctsEventId="first"),KotlinFreestyleRefusal.INVALID_CORRECTION)
        assertNull(domain.routine(state,"routine","Arms"))
        val finished=next(state,KotlinFreestyleOperation.Finish("finish",state.revision))
        rejected(finished,KotlinFreestyleOperation.Reorder("late",finished.revision,listOf("slot-b","slot-a")),
            KotlinFreestyleRefusal.FINISHED)
        assertNull(domain.replay(finished.workoutId,finished.source,
            finished.receipts.mapIndexed{i,r->if(i==1)r.copy(revision=99) else r}))
    }
}
