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

    @Test fun oneSourceExerciseCanPreserveTwoDirectArmFunctionsWithoutTwoVisits() {
        val setup=athlete()
        val dual=movement("curl",TrainingMuscle.BICEPS,
            ComposerPattern.ISOLATION,ComposerRole.ACCESSORY).copy(
            credits=listOf(
                DeclaredMuscleCredit(TrainingMuscle.BICEPS,
                    MuscleCreditRole.DIRECT,n("1")),
                DeclaredMuscleCredit(TrainingMuscle.BRACHIALIS,
                    MuscleCreditRole.DIRECT,n("1"))))
        val evidence=StrengthSourceArmReview(
            StrengthSourceArmsStatus.REVIEW_READY,"source-cycle",
            "athlete3",7,"hyper",listOf(
                StrengthSourceArmCandidate("curl",TrainingMuscle.BICEPS,3),
                StrengthSourceArmCandidate("curl",TrainingMuscle.BRACHIALIS,3)),
            "Verified two independently credited direct muscle functions")
        val proposed=request(setup,
            offered=listOf(choose("press",required=1),choose("back"),
                choose("curl",visits=2)),patterns=1).copy(
            policy=policy(setup).copy(phase=TrainingPhase.STRENGTH),
            catalog=catalog+dual,sourceStrengthArms=evidence)
        val reviewed=CoachAthleteExerciseSelectionDomain.review(proposed)
        assertEquals(AthleteSelectionStatus.SHORTLIST_FOR_REVIEW,reviewed.status)
        assertEquals(1,reviewed.choices.count{it.exerciseId=="curl"})
        val curl=reviewed.choices.single{it.exerciseId=="curl"}
        assertEquals(1,curl.requiredVisits)
        assertEquals(2,curl.minimumSetsPerVisit)
        assertEquals(n("25"),curl.selectedLoad)
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.mayOverwriteCustomProgram)
        // A forged source reporting two different exercises for the SAME
        // direct muscle does not create duplicate muscle evidence.
        val repeatedMuscle=CoachAthleteExerciseSelectionDomain.review(
            proposed.copy(sourceStrengthArms=evidence.copy(
                candidates=listOf(
                    StrengthSourceArmCandidate("curl",TrainingMuscle.BICEPS,3),
                    StrengthSourceArmCandidate("curl",TrainingMuscle.BICEPS,3)))))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,
            repeatedMuscle.status)
        assertTrue(repeatedMuscle.issues.any{
            it.kind==AthleteSelectionProblem.SOURCE_ARM_CONTINUITY_UNVERIFIED})
        val changedCredit=CoachAthleteExerciseSelectionDomain.review(
            proposed.copy(catalog=catalog+dual.copy(
                credits=listOf(DeclaredMuscleCredit(
                    TrainingMuscle.BICEPS,MuscleCreditRole.DIRECT,n("1"))))))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,
            changedCredit.status)
    }

    @Test fun strengthShortlistPreservesActualSourceArmVisitsNotIndirectCredits() {
        val setup=athlete()
        val source=StrengthSourceArmReview(StrengthSourceArmsStatus.REVIEW_READY,
            "source-cycle","athlete3",7,"hyper",listOf(
                StrengthSourceArmCandidate("curl",TrainingMuscle.BICEPS,2),
                StrengthSourceArmCandidate("hammer",TrainingMuscle.BRACHIALIS,3),
                StrengthSourceArmCandidate("extension",TrainingMuscle.TRICEPS,2)),
            "Source-audited previous hypertrophy working sets")
        val extra=listOf(
            movement("curl",TrainingMuscle.BICEPS,ComposerPattern.ISOLATION,
                ComposerRole.ACCESSORY),
            movement("hammer",TrainingMuscle.BRACHIALIS,ComposerPattern.ISOLATION,
                ComposerRole.ACCESSORY),
            movement("extension",TrainingMuscle.TRICEPS,ComposerPattern.ISOLATION,
                ComposerRole.ACCESSORY))
        val offer=listOf(choose("press",required=1),choose("back"))+
            extra.map{choose(it.exerciseId,visits=2)}
        val proposed=request(setup,offered=offer,patterns=1).copy(
            catalog=catalog+extra,
            policy=policy(setup).copy(phase=TrainingPhase.STRENGTH),
            sourceStrengthArms=source)
        val answer=CoachAthleteExerciseSelectionDomain.review(proposed)
        assertEquals(AthleteSelectionStatus.SHORTLIST_FOR_REVIEW,answer.status)
        assertEquals(setOf("press","back","curl","hammer","extension"),
            answer.choices.map{it.exerciseId}.toSet())
        for(id in listOf("curl","hammer","extension")) {
            val retained=answer.choices.single{it.exerciseId==id}
            assertEquals(1,retained.requiredVisits)
            assertEquals(2,retained.minimumSetsPerVisit)
            assertEquals(n("25"),retained.selectedLoad)
        }
        assertFalse(answer.mayIssueWorkout)
        assertFalse(answer.mayOverwriteCustomProgram)
        // The ordinary selection without source evidence still retains
        // its existing greedy, unpinned behavior.
        val ordinary=CoachAthleteExerciseSelectionDomain.review(
            proposed.copy(sourceStrengthArms=null))
        assertTrue(ordinary.choices.none {
            it.exerciseId in setOf("curl","hammer","extension")
        })

        val unavailable=CoachAthleteExerciseSelectionDomain.review(
            proposed.copy(offered=offer.filter{it.exerciseId!="hammer"}))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,unavailable.status)
        assertTrue(unavailable.issues.any {
            it.kind==AthleteSelectionProblem.UNAVAILABLE_REQUIRED_EXERCISE
        })
        val stale=CoachAthleteExerciseSelectionDomain.review(
            proposed.copy(sourceStrengthArms=source.copy(
                athleteId="another-athlete")))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,stale.status)
        assertTrue(stale.issues.any{
            it.kind==AthleteSelectionProblem.SOURCE_ARM_CONTINUITY_UNVERIFIED
        })
        val changedFunction=CoachAthleteExerciseSelectionDomain.review(
            proposed.copy(catalog=(catalog+extra).map{
                if(it.exerciseId=="hammer")it.copy(
                    credits=listOf(DeclaredMuscleCredit(
                        TrainingMuscle.BICEPS,MuscleCreditRole.INDIRECT,n("1"))))
                else it
            }))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,changedFunction.status)
        assertTrue(changedFunction.issues.any{
            it.kind==AthleteSelectionProblem.SOURCE_ARM_CONTINUITY_UNVERIFIED
        })
    }


    @Test fun verifiedStrengthForearmFunctionsAreRequiredNotOptionalFillers() {
        val setup=athlete()
        val forearms=listOf(
            movement("wrist-flexion",TrainingMuscle.WRIST_FLEXORS,
                ComposerPattern.ISOLATION,ComposerRole.ACCESSORY),
            movement("wrist-extension",TrainingMuscle.WRIST_EXTENSORS,
                ComposerPattern.ISOLATION,ComposerRole.ACCESSORY))
        val source=StrengthSourceArmReview(StrengthSourceArmsStatus.REVIEW_READY,
            "source-cycle","athlete3",7,"hyper",listOf(
                StrengthSourceArmCandidate("wrist-flexion",
                    TrainingMuscle.WRIST_FLEXORS,3),
                StrengthSourceArmCandidate("wrist-extension",
                    TrainingMuscle.WRIST_EXTENSORS,2)),
            "Final reviewed hypertrophy week")
        val offered=listOf(choose("press",required=1),choose("back"))+
            forearms.map{choose(it.exerciseId)}
        val proposed=request(setup,offered=offered,patterns=1).copy(
            catalog=catalog+forearms,
            policy=policy(setup).copy(phase=TrainingPhase.STRENGTH),
            sourceStrengthArms=source)
        val reviewed=CoachAthleteExerciseSelectionDomain.review(proposed)
        assertEquals(AthleteSelectionStatus.SHORTLIST_FOR_REVIEW,reviewed.status)
        assertEquals(setOf("press","back","wrist-flexion","wrist-extension"),
            reviewed.choices.map{it.exerciseId}.toSet())
        assertTrue(reviewed.choices.filter{
            it.exerciseId.startsWith("wrist-")
        }.all{it.requiredVisits==1 && it.minimumSetsPerVisit==2})
        assertFalse(reviewed.mayIssueWorkout)
        val missing=CoachAthleteExerciseSelectionDomain.review(
            proposed.copy(offered=offered.filter{
                it.exerciseId!="wrist-extension"}))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,missing.status)
        assertTrue(missing.issues.any{
            it.kind==AthleteSelectionProblem.UNAVAILABLE_REQUIRED_EXERCISE})
        val miscredited=CoachAthleteExerciseSelectionDomain.review(
            proposed.copy(catalog=(catalog+forearms).map{
                if(it.exerciseId=="wrist-flexion")it.copy(
                    credits=listOf(DeclaredMuscleCredit(
                        TrainingMuscle.WRIST_EXTENSORS,
                        MuscleCreditRole.DIRECT,n("1"))))
                else it
            }))
        assertEquals(AthleteSelectionStatus.INPUT_UNRESOLVED,miscredited.status)
        assertTrue(miscredited.issues.any{
            it.kind==AthleteSelectionProblem.SOURCE_ARM_CONTINUITY_UNVERIFIED})
    }

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
