package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachAthleteCatalogCandidateDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
    private fun setup(excluded:Set<String> = emptySet(),
        body:Boolean=false,focus:Boolean=false,hard:Boolean=false,
        barbellCap:Int=0,barbellAvailable:Boolean=false)=
        CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="rev1",athleteProfileId="athlete1",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),
            timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("home",LoadUnit.LB,
                mapOf<String,VerifiedLoadStock>("db" to stock)+
                    if(barbellAvailable)mapOf("bar" to
                        VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
                            mapOf(n("10") to 2)))else emptyMap(),
                emptySet(),emptySet()),
            allowBodyweight=body,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=barbellCap,
            focusedMuscles=if(focus)setOf(TrainingMuscle.CHEST)else emptySet(),
            reducedMuscles=emptySet(),excludedExerciseIds=excluded,
            lockedTrainingDays=setOf(0),
            hardTrainingDays=if(hard)setOf(2)else emptySet(),
            longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
    private fun movement(id:String,muscle:TrainingMuscle,
        pattern:ComposerPattern,role:ComposerRole=ComposerRole.SECONDARY)=
        CatalogMovement(id,id,"dumbbell",null,pattern,role,
            setOf(ComposerFocus.FULL_BODY),"db",
            LoadBasis.DUMBBELL_PER_HAND,
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"review-v1")
    private val chest=movement("press",TrainingMuscle.CHEST,
        ComposerPattern.HORIZONTAL_PRESS,ComposerRole.PRIMARY)
    private val lats=movement("row",TrainingMuscle.LATS,
        ComposerPattern.HORIZONTAL_PULL)
    private val quads=movement("squat",TrainingMuscle.QUADS,
        ComposerPattern.SQUAT)
    private val catalog=listOf(chest,lats,quads)
    private fun choice(id:String)=CatalogExerciseChoice(
        id,n("25"),90,120,2,4,3,1,1,5)
    private fun bounds()=TrainingMuscle.entries.map {m->
        val active=m in setOf(TrainingMuscle.CHEST,TrainingMuscle.LATS,
            TrainingMuscle.QUADS)
        DeclaredMuscleDoseBounds(m,n(if(active)"2"else"0"),
            n(if(active)"5"else"0"),n(if(active)"8"else"0"),
            if(active)1 else 0)
    }
    private fun request()=AthleteCatalogCandidateRequest(setup(),
        TrainingPhase.HYPERTROPHY,
        mapOf(0 to ComposerFocus.FULL_BODY,
            2 to ComposerFocus.FULL_BODY,4 to ComposerFocus.FULL_BODY),
        bounds(),mapOf(0 to 0,2 to 0,4 to 0),
        listOf(0,2,4).map {
            AthleteCalendarIntent(it,ComposerSessionPurpose.HYPERTROPHY)
        },catalog,catalog.map {choice(it.exerciseId)},3,1)

    @Test fun incompleteSetupNeverSuppliesInventedScheduleOrGym() {
        val result=CoachAthleteCatalogCandidateDomain.review(request().copy(
            setup=CoachAthleteSetupDomain.review(AthleteSetupDraft())))
        assertEquals(AthleteCandidateStatus.SETUP_UNRESOLVED,result.status)
        assertNull(result.catalogReview)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun confirmedAthleteWeekProjectsExactDaysAndTimePreferences() {
        val r=CoachAthleteCatalogCandidateDomain.review(request())
        val drafted=checkNotNull(r.catalogReview)
        val template=checkNotNull(drafted.composedRequest)
        assertEquals(listOf(0,2,4),template.days.map {it.calendarDay})
        assertEquals(ComposerSplit.FULL_BODY,template.split)
        assertTrue(template.days.all {
            it.window.preferredMinimumMinutes==40 &&
                it.window.preferredMaximumMinutes==60 &&
                it.window.userHardLimitMinutes==null &&
                it.window.declaredNonSetSeconds==0
        })
        assertEquals(setOf("press","row","squat"),
            template.exercises.map {it.exerciseId}.toSet())
        val press=template.exercises.first {it.exerciseId=="press"}
        assertEquals(n("25"),(press.equipment as ComposerEquipment.Verified).plannedLoad)
        assertFalse(r.maySaveProgram)
        assertFalse(r.mayIssueWorkout)
    }

    @Test fun dayOrPhaseInputsCannotOverrideConfirmedSchedule() {
        for(bad in listOf(
            request().copy(focusByWeekday=mapOf(
                0 to ComposerFocus.FULL_BODY,2 to ComposerFocus.FULL_BODY)),
            request().copy(focusByWeekday=mapOf(
                0 to ComposerFocus.UPPER,2 to ComposerFocus.LOWER,
                4 to ComposerFocus.UPPER)),
            request().copy(nonSetSecondsByWeekday=mapOf(0 to 60,2 to 60)),
            request().copy(intents=listOf(AthleteCalendarIntent(0,
                ComposerSessionPurpose.HYPERTROPHY))),
            request().copy(bounds=bounds().drop(1))
        )) {
            val result=CoachAthleteCatalogCandidateDomain.review(bad)
            assertEquals(AthleteCandidateStatus.CONSTRAINTS_UNRESOLVED,result.status)
            assertNull(result.catalogReview)
        }
    }

    @Test fun malformedDosePoliciesAreNotReplacedWithDefaultThresholds() {
        val invalid=CoachAthleteCatalogCandidateDomain.review(request().copy(
            bounds=bounds().map {
                if(it.muscle==TrainingMuscle.CHEST)
                    it.copy(mev=n("8"),mav=n("2")) else it
            }))
        assertEquals(AthleteCandidateStatus.CONSTRAINTS_UNRESOLVED,invalid.status)
        assertNull(invalid.catalogReview)
        assertTrue(invalid.issues.any {it.field=="catalogOrDosePolicy"})
    }

    @Test fun excludesUserBlockedMovementsAndDisallowedBodyweightOrBarbells() {
        val banned=CoachAthleteCatalogCandidateDomain.review(request().copy(
            setup=setup(excluded=setOf("row"))))
        assertTrue(banned.issues.any {
            it.kind==AthleteCandidateIssueKind.EXCLUDED_EXERCISE
        })
        assertNull(banned.catalogReview)
        val pushup=chest.copy(exerciseId="pushup",familyId="pushup",
            equipmentId=null,expectedLoadBasis=null)
        val forbiddenBody=CoachAthleteCatalogCandidateDomain.review(
            request().copy(catalog=catalog+pushup,
                choices=request().choices+choice("pushup").copy(
                    selectedLoad=null)))
        assertTrue(forbiddenBody.issues.any {
            it.kind==AthleteCandidateIssueKind.BODYWEIGHT_DISABLED
        })
        val barbell=chest.copy(exerciseId="barbell-bench",
            familyId="barbell-bench",equipmentId="bar",
            expectedLoadBasis=LoadBasis.BARBELL_TOTAL)
        val forbiddenBar=CoachAthleteCatalogCandidateDomain.review(
            request().copy(catalog=catalog+barbell,
                choices=request().choices+choice("barbell-bench")))
        assertTrue(forbiddenBar.issues.any {
            it.kind==AthleteCandidateIssueKind.BARBELL_LIMIT
        })
    }

    @Test fun focusAndHardDayWithoutPrescribedPoliciesRemainUnresolved() {
        val focus=CoachAthleteCatalogCandidateDomain.review(request().copy(
            setup=setup(focus=true)))
        assertTrue(focus.issues.any {it.field=="musclePriorities"})
        assertEquals(AthleteCandidateStatus.CONSTRAINTS_UNRESOLVED,focus.status)
        val hard=CoachAthleteCatalogCandidateDomain.review(request().copy(
            setup=setup(hard=true)))
        assertTrue(hard.issues.any {it.field=="dayIntensity"})
        assertEquals(AthleteCandidateStatus.CONSTRAINTS_UNRESOLVED,hard.status)
    }

    @Test fun confirmedBarbellLimitTravelsThroughCatalogIntoActualWeekSearch() {
        val benchBar=chest.copy(exerciseId="bar-bench",
            familyId="bar-bench",variantId="barbell",
            equipmentId="bar",expectedLoadBasis=LoadBasis.BARBELL_TOTAL)
        val rowBar=lats.copy(exerciseId="bar-row",
            familyId="bar-row",variantId="barbell",
            equipmentId="bar",expectedLoadBasis=LoadBasis.BARBELL_TOTAL)
        val options=listOf(benchBar,rowBar)
        val original=request()
        val input=original.copy(
            setup=setup(barbellCap=1,barbellAvailable=true),
            catalog=catalog+options,
            choices=original.choices+options.map {
                choice(it.exerciseId).copy(selectedLoad=n("65"))
            })
        val outcome=CoachAthleteCatalogCandidateDomain.review(input)
        val catalogDraft=checkNotNull(outcome.catalogReview)
        val proposed=checkNotNull(catalogDraft.composedRequest)
        assertEquals(1,proposed.maximumBarbellExercisesPerDay)
        assertFalse(proposed.allowDirectTricepsOnLower)
        val visits=checkNotNull(catalogDraft.draft).visits
        val barbellIds=setOf("bar-bench","bar-row")
        assertTrue(visits.any {it.exerciseId=="bar-bench"})
        assertTrue(visits.any {it.exerciseId=="bar-row"})
        assertTrue(visits.filter {it.exerciseId in barbellIds}
            .groupBy {it.dayIndex}.values.all {it.size<=1})
        assertFalse(outcome.mayIssueWorkout)
        assertFalse(outcome.maySaveProgram)
        assertEquals(input,original.copy(
            setup=input.setup,catalog=input.catalog,choices=input.choices))
    }

    @Test fun candidateReviewIsDeterministicAndNeverAltersAuthoring() {
        val input=request()
        val a=CoachAthleteCatalogCandidateDomain.review(input)
        val b=CoachAthleteCatalogCandidateDomain.review(input)
        assertEquals(a,b)
        assertEquals(3,input.choices.size)
        assertEquals(3,input.nonSetSecondsByWeekday.size)
    }
}
