package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachAthleteExerciseSelectionDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
    private fun athlete(excluded:Set<String> = emptySet())=
        CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="r3",athleteProfileId="athlete3",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym",LoadUnit.LB,
                mapOf("dumbbells" to stock),emptySet(),emptySet()),
            allowBodyweight=false,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=excluded,lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
    private fun bounds()=TrainingMuscle.entries.map {
        val active=it in setOf(TrainingMuscle.CHEST,TrainingMuscle.LATS)
        DeclaredMuscleDoseBounds(it,n(if(active)"2"else"0"),
            n(if(active)"4"else"0"),n(if(active)"8"else"0"),
            if(active)1 else 0)
    }
    private fun policy(s:AthleteSetupReview)=
        checkNotNull(s.reviewed).let {a->
            AuthoredGoalProgramPolicy(
                "coach-policy","v3","Reviewed working-set bounds.",
                a.athleteProfileId,a.profileRevision,a.goal,a.experience,
                a.split,TrainingPhase.HYPERTROPHY,
                a.trainingWeekdays.size,bounds(),bounds(),
                mapOf(ComposerSessionPurpose.HYPERTROPHY to 3),
                mapOf("press" to 1))
        }
    private fun movement(
        id:String,muscle:TrainingMuscle,pattern:ComposerPattern,
        role:ComposerRole=ComposerRole.SECONDARY,
        equipmentId:String?="dumbbells",
        basis:LoadBasis?=LoadBasis.DUMBBELL_PER_HAND,
        permitted:Set<ComposerFocus> =setOf(ComposerFocus.FULL_BODY)
    )=CatalogMovement(id,id,"variant",null,pattern,role,permitted,
        equipmentId,basis,listOf(DeclaredMuscleCredit(
            muscle,MuscleCreditRole.DIRECT,n("1"))),
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"credits-r3")
    private val press=movement("press",TrainingMuscle.CHEST,
        ComposerPattern.HORIZONTAL_PRESS,ComposerRole.PRIMARY)
    private val back=movement("back",TrainingMuscle.LATS,
        ComposerPattern.HORIZONTAL_PULL)
    private val iso=movement("chest-iso",TrainingMuscle.CHEST,
        ComposerPattern.ISOLATION)
    private val blocked=movement("machine-row",TrainingMuscle.LATS,
        ComposerPattern.HORIZONTAL_PULL,equipmentId="machine",
        basis=LoadBasis.MACHINE_STACK)
    private val unweighted=movement("pushup",TrainingMuscle.CHEST,
        ComposerPattern.HORIZONTAL_PRESS,equipmentId=null,basis=null)
    private val catalog=listOf(press,back,iso,blocked,unweighted)
    private fun choose(id:String,required:Int=0,load:BigDecimal?=n("25"),
        priority:Int=4,visits:Int=2)=
        CatalogExerciseChoice(id,load,90,180,2,4,visits,required,1,
            priority)
    private fun request(s:AthleteSetupReview=athlete(),
        offered:List<CatalogExerciseChoice> =listOf(
            choose("press",required=1),choose("back"),choose("chest-iso"),
            choose("machine-row"),choose("pushup",load=null)
        ),max:Int=32,patterns:Int=2)=
        AthleteExerciseSelectionRequest(s,policy(s),catalog,offered,
            setOf(ComposerFocus.FULL_BODY),patterns,max)

    @Test fun picksReviewedLoadableCoverageAndPrunesUnneededOptions() {
        val review=CoachAthleteExerciseSelectionDomain.review(request())
        assertEquals(AthleteSelectionStatus.SHORTLIST_FOR_REVIEW,review.status)
        assertTrue(review.issues.isEmpty())
        assertEquals(setOf("press","back"),
            review.choices.map {it.exerciseId}.toSet())
        assertEquals(setOf("machine-row","pushup"),
            review.excluded.map {it.exerciseId}.toSet())
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.mayOverwriteCustomProgram)
    }

    @Test fun impossibleDumbbellLoadIsRejectedNotRounded() {
        val result=CoachAthleteExerciseSelectionDomain.review(request(
            offered=listOf(choose("press",required=1),
                choose("back",load=n("22.5")),choose("chest-iso"))))
        assertTrue(result.excluded.any {
            it.exerciseId=="back" && it.explanation.contains("physically unavailable")
        })
        assertEquals(AthleteSelectionStatus.COVERAGE_UNRESOLVED,result.status)
        assertTrue(result.issues.any {
            it.kind==AthleteSelectionProblem.MISSING_DOSE_COVERAGE
        })
    }

    @Test fun requiredUnavailableMovementFailsWithoutReplacingAuthorship() {
        val r=CoachAthleteExerciseSelectionDomain.review(request(
            offered=listOf(choose("press",required=1),
                choose("machine-row",required=1),
                choose("back"))))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,r.status)
        assertTrue(r.issues.any {
            it.kind==AthleteSelectionProblem.UNAVAILABLE_REQUIRED_EXERCISE
        })
        assertTrue(r.choices.isEmpty())
        assertFalse(r.mayOverwriteCustomProgram)
    }

    @Test fun athleteExclusionCannotBecomeValidByHavingHigherPriority() {
        val s=athlete(setOf("back"))
        val r=CoachAthleteExerciseSelectionDomain.review(request(s=s,
            offered=listOf(choose("press",required=1),
                choose("back",priority=10),choose("chest-iso"))))
        assertTrue(r.excluded.any {
            it.exerciseId=="back" && it.explanation.contains("excluded")
        })
        assertTrue(r.issues.any {
            it.kind==AthleteSelectionProblem.MISSING_DOSE_COVERAGE
        })
    }

    @Test fun selectionBudgetNeverDiscardsPinnedExercise() {
        val r=CoachAthleteExerciseSelectionDomain.review(request(
            offered=listOf(choose("press",required=1),
                choose("back",required=1)),max=1,patterns=1))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,r.status)
        assertTrue(r.issues.any {
            it.kind==AthleteSelectionProblem.TOO_MANY_REQUIRED_EXERCISES
        })
        assertTrue(r.choices.isEmpty())
    }

    @Test fun missingDirectDaysCannotBeHiddenByIndirectVolume() {
        val s=athlete()
        val modified=request(s).copy(
            offered=listOf(choose("press",required=1),
                choose("chest-iso")),
            preferredPatternsPerDay=1)
        val result=CoachAthleteExerciseSelectionDomain.review(modified)
        assertEquals(AthleteSelectionStatus.COVERAGE_UNRESOLVED,result.status)
        assertTrue(result.issues.any {
            it.kind==AthleteSelectionProblem.MISSING_DIRECT_DAYS &&
                it.field=="muscle.LATS"
        })
    }

    @Test fun sourceProfileRevisionMustMatchAndNoSyntheticTargetsAppear() {
        val s=athlete()
        val r=CoachAthleteExerciseSelectionDomain.review(request(s).copy(
            policy=policy(s).copy(athleteRevision="r0")))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,r.status)
        assertTrue(r.choices.isEmpty())
        assertTrue(r.issues.any {
            it.kind==AthleteSelectionProblem.PROFILE_OR_POLICY
        })
    }

    @Test fun fullWebSizedCatalogAndOffersStillSelectOnlyFeasibleStock() {
        val extraCount=376-catalog.size
        val extras=(1..extraCount).map { index ->
            movement("web-source-$index",TrainingMuscle.LATS,
                ComposerPattern.HORIZONTAL_PULL,
                equipmentId="not-stocked",basis=LoadBasis.MACHINE_STACK)
        }
        val all=catalog+extras
        assertEquals(376,all.size)
        val offered=listOf(choose("press",required=1),choose("back"))+
            extras.map{choose(it.exerciseId)}
        val review=CoachAthleteExerciseSelectionDomain.review(
            request(offered=offered).copy(catalog=all))
        assertEquals(AthleteSelectionStatus.SHORTLIST_FOR_REVIEW,review.status)
        assertEquals(setOf("press","back"),review.choices.map{it.exerciseId}.toSet())
        assertEquals(extraCount,review.excluded.size)
        assertFalse(review.mayIssueWorkout)
        val over=all+(1..(CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES+1-
                all.size)).map{index->
            movement("overflow-$index",TrainingMuscle.LATS,
                ComposerPattern.HORIZONTAL_PULL,equipmentId="not-stocked",
                basis=LoadBasis.MACHINE_STACK)
        }
        assertEquals(CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES+1,over.size)
        val refused=CoachAthleteExerciseSelectionDomain.review(
            request(offered=listOf(choose("press",required=1),choose("back")))
                .copy(catalog=over))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,refused.status)
        assertTrue(refused.issues.any{
            it.kind==AthleteSelectionProblem.INVALID_CATALOG})
    }

    @Test fun identicalOffersInDifferentOrderProduceIdenticalReview() {
        val initial=request()
        val a=CoachAthleteExerciseSelectionDomain.review(initial)
        val b=CoachAthleteExerciseSelectionDomain.review(initial.copy(
            offered=initial.offered.reversed()))
        assertEquals(a,b)
        assertEquals(5,initial.offered.size)
        assertEquals("machine-row",initial.catalog[3].exerciseId)
    }
}
