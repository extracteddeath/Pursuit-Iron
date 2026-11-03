package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachCatalogBlockDomainTest {
    private fun n(i:Int)=BigDecimal(i)
    private val db=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n(20),n(25),n(30),n(35)))
    private val gym=CatalogGym(mapOf("dumbbells" to db),emptySet())
    private fun spec(id:String,m:TrainingMuscle,pattern:ComposerPattern,
        role:ComposerRole=ComposerRole.SECONDARY)=CatalogMovement(
        id,id,"default",null,pattern,role,setOf(ComposerFocus.FULL_BODY),
        "dumbbells",LoadBasis.DUMBBELL_PER_HAND,
        listOf(DeclaredMuscleCredit(m,MuscleCreditRole.DIRECT,BigDecimal.ONE)),
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"review-v1")
    private val bench=spec("bench",TrainingMuscle.CHEST,
        ComposerPattern.HORIZONTAL_PRESS,ComposerRole.PRIMARY)
    private val row=spec("row",TrainingMuscle.LATS,
        ComposerPattern.HORIZONTAL_PULL)
    private val squat=spec("squat",TrainingMuscle.QUADS,ComposerPattern.SQUAT)
    private val catalog=listOf(bench,row,squat)
    private val selected=catalog.map {s->
        CatalogExerciseChoice(s.exerciseId,n(30),90,90,2,4,3,1,1,3)
    }
    private fun bounds(cap:Int)=TrainingMuscle.entries.map {m->
        if(m in listOf(TrainingMuscle.CHEST,TrainingMuscle.LATS,TrainingMuscle.QUADS))
            DeclaredMuscleDoseBounds(m,n(2),n(cap),n(cap),1)
        else DeclaredMuscleDoseBounds(m,n(0),n(0),n(0),0)
    }
    private fun intents(strength:Boolean=false)=listOf(
        ComposerSessionIntent(0,
            if(strength)ComposerSessionPurpose.STRENGTH_SKILL
            else ComposerSessionPurpose.GENERAL_PREPARATION,
            if(strength)"bench" else null),
        ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
        ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY)
    )
    private fun candidate(
        cap:Int=8,phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        strength:Boolean=false,
        catalog:List<CatalogMovement> =this.catalog,
        selected:List<CatalogExerciseChoice> =this.selected,
        gym:CatalogGym=this.gym,
        schedule:List<Int> =listOf(0,2,4)
    ):CatalogWeekRequest {
        val template=ComposerRequest(ComposerSplit.FULL_BODY,phase,
            schedule.map {day->
                ComposerDay(day,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,90,null,60))
            },bounds(cap),emptyList(),4,1,intents(strength))
        return CatalogWeekRequest(template,catalog,selected,gym)
    }
    private val policy=LongitudinalReviewPolicy(3,1,2,2,3,2)
    private fun history(success:Boolean,next:TrainingPhase?=null):
        LongitudinalReviewRequest {
        val weeks=(0..1).map {i->
            val id="week"+i
            val workouts=(0..2).map {id+"-session"+it}.toSet()
            val setFacts=(0..2).map {ordinal->
                WorkingSetFact(ordinal,SetOutcome.COMPLETED,n(30),
                    if(success)8 else 3,if(success)3 else 1,n(30),2)
            }
            val exercise=ProgressionWorkoutEvidence(id+"-session0",
                "bench",LoadUnit.LB,RepBand(5,8),3,setFacts)
            LoggedTrainingWeek(i,id,TrainingPhase.HYPERTROPHY,
                workouts,workouts,listOf(exercise),
                if(success)CoachReadiness.NORMAL else CoachReadiness.LOW,
                !success,false)
        }
        return LongitudinalReviewRequest(weeks,setOf("bench"),policy,next)
    }
    private fun policies(rir:Int=3,rest:Int=150)=catalog.flatMap {spec->
        listOf(ComposerSessionPurpose.GENERAL_PREPARATION,
            ComposerSessionPurpose.HYPERTROPHY,
            ComposerSessionPurpose.STRENGTH_SKILL).map {purpose->
            ExercisePrescriptionPolicy(spec.exerciseId,purpose,
                if(purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                    RepBand(3,5) else RepBand(6,10),
                rir,90,rest,SetLoadScheme.STRAIGHT)
        }
    }
    private fun input(
        baseline:CatalogWeekRequest=candidate(),
        proposed:CatalogWeekRequest=candidate(cap=4),
        h:LongitudinalReviewRequest=history(false),
        kind:BlockCandidateKind=BlockCandidateKind.RECOVERY_DELOAD,
        permission:BlockAdaptPermissions=BlockAdaptPermissions(),
        before:List<ExercisePrescriptionPolicy> =policies(),
        after:List<ExercisePrescriptionPolicy> =policies()
    )=CatalogBlockRequest(h,baseline,proposed,before,after,
        kind,permission,if(kind==BlockCandidateKind.RECOVERY_DELOAD)20 else null)
    private fun review(q:CatalogBlockRequest=input())=
        CoachCatalogBlockDomain.review(q)

    @Test fun fullProvenanceEquipmentVolumeAndPrescriptionsLeadOnlyToReview() {
        val result=review()
        assertEquals(CatalogBlockStatus.READY_FOR_HUMAN_REVIEW,result.status)
        val p=checkNotNull(result.fullPrescription)
        assertEquals(PrescribedBlockStatus.READY_FOR_HUMAN_REVIEW,p.status)
        assertTrue(p.proposed.sessions.isNotEmpty())
        assertTrue(p.doseComparisons.all {it.change<=BigDecimal.ZERO})
        assertFalse(result.mayIssueNextWorkout)
        assertFalse(result.mayWriteTrainingHistory)
        assertFalse(result.mayReplaceActiveTrainingEngine)
    }

    @Test fun requestedStrengthPhaseHasItsSpecificMainLiftAndRepTargets() {
        val proposed=candidate(cap=6,phase=TrainingPhase.STRENGTH,
            strength=true)
        val q=input(proposed=proposed,h=history(true,TrainingPhase.STRENGTH),
            kind=BlockCandidateKind.PHASE_TRANSITION)
        val result=review(q)
        assertEquals(CatalogBlockStatus.READY_FOR_HUMAN_REVIEW,result.status)
        val main=result.fullPrescription!!.proposed.sessions.first()
            .exercises.first()
        assertEquals("bench",main.exerciseId)
        assertTrue(main.sets.filter {it.type==DraftSetType.WORKING}
            .all {it.targetReps==RepBand(3,5)})
        assertFalse(result.mayIssueNextWorkout)
    }

    @Test fun unreviewedCreditsBlockBeforeTrainingPoliciesAreConsidered() {
        val unreviewed=catalog.map {
            if(it.exerciseId=="bench")it.copy(
                creditStatus=CatalogCreditStatus.UNREVIEWED)else it
        }
        val result=review(input(baseline=candidate(catalog=unreviewed)))
        assertEquals(CatalogBlockStatus.BASELINE_CATALOG_UNRESOLVED,result.status)
        assertNull(result.fullPrescription)
        assertTrue(result.problems.any {it.contains("credit")})
    }

    @Test fun unavailableCandidateExerciseVariantBlocksEntireBlock() {
        val spec=bench.copy(attachmentId="rope")
        val result=review(input(proposed=candidate(
            cap=4,catalog=listOf(spec,row,squat))))
        assertEquals(CatalogBlockStatus.PROPOSED_CATALOG_UNRESOLVED,result.status)
        assertTrue(result.problems.any {it.contains("attachment")})
        assertNull(result.fullPrescription)
    }

    @Test fun matchingExerciseIdCannotSilentlyChangeItsAttachmentOrFamily() {
        val changed=bench.copy(familyId="machine-press",variantId="new")
        val proposal=candidate(cap=4,catalog=listOf(changed,row,squat))
        val result=review(input(proposed=proposal,
            permission=BlockAdaptPermissions(adaptExercises=true)))
        assertEquals(CatalogBlockStatus.EXERCISE_IDENTITY_CHANGED,result.status)
        assertNull(result.fullPrescription)
        assertTrue(result.problems.any {it.contains("stable ID bench")})
    }

    @Test fun changedCatalogMuscleCreditsRequireExerciseAdaptationPermission() {
        val altered=bench.copy(
            credits=listOf(DeclaredMuscleCredit(TrainingMuscle.CHEST,
                MuscleCreditRole.DIRECT,BigDecimal("0.5"))),
            creditReviewReference="review-v2")
        val proposal=candidate(cap=4,catalog=listOf(altered,row,squat))
        val r=review(input(proposed=proposal))
        assertEquals(CatalogBlockStatus.BLOCK_OR_PRESCRIPTION_UNRESOLVED,
            r.status)
        assertEquals(BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION,
            r.fullPrescription!!.coarseReview.status)
    }

    @Test fun missingPurposePolicyBlocksEvenWhenCatalogAndCoarseWeekPass() {
        val incomplete=policies().filterNot {it.exerciseId=="bench"}
        val r=review(input(after=incomplete))
        assertEquals(CatalogBlockStatus.BLOCK_OR_PRESCRIPTION_UNRESOLVED,
            r.status)
        assertEquals(PrescribedBlockStatus.NEW_PRESCRIPTION_UNRESOLVED,
            r.fullPrescription!!.status)
        assertFalse(r.mayWriteTrainingHistory)
    }

    @Test fun unauthorizedScheduleChangeIsBlockedAfterCatalogValidation() {
        val changed=candidate(cap=4,schedule=listOf(0,3,5))
        val result=review(input(proposed=changed))
        assertEquals(CatalogBlockStatus.BLOCK_OR_PRESCRIPTION_UNRESOLVED,
            result.status)
        assertEquals(BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION,
            result.fullPrescription!!.coarseReview.status)
    }

    @Test fun fabricatedEquipmentIncrementFailsBeforeAnyPrescription() {
        val bad=selected.map {
            if(it.exerciseId=="bench")it.copy(selectedLoad=BigDecimal("32.5"))
            else it
        }
        val r=review(input(proposed=candidate(cap=4,selected=bad)))
        assertEquals(CatalogBlockStatus.PROPOSED_CATALOG_UNRESOLVED,r.status)
        assertNull(r.fullPrescription)
    }

    @Test fun deterministicReviewPreservesAllCustomInputsAndHistory() {
        val q=input()
        val a=review(q)
        val b=review(q)
        assertEquals(a,b)
        assertEquals(TrainingPhase.HYPERTROPHY,q.baseline.template.phase)
        assertTrue(q.baseline.template.exercises.isEmpty())
        assertEquals(n(30),q.baseline.chosen.first().selectedLoad)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val mutable=a.problems as MutableList<String>
            mutable.add("unverified")
        }
    }
}
