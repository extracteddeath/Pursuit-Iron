package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachAthleteProgramGeneratorTest {
    private fun n(v:String)=BigDecimal(v)
    private val db=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
    private fun setup(
        split:ComposerSplit=ComposerSplit.FULL_BODY,
        weekdays:Set<Int> =setOf(0,2,4),
        stock:VerifiedLoadStock=db
    )=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="revision2",athleteProfileId="athlete2",
        goal=AthleteTrainingGoal.POWERBUILDING,
        experience=AthleteExperience.ADVANCED,
        split=split,trainingWeekdays=weekdays,
        timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
        hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
        gym=GymProfileDraft("main-gym",LoadUnit.LB,
            mapOf("dumbbells" to stock),emptySet(),emptySet()),
        allowBodyweight=false,allowSupersets=false,
        allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=(0..6).toSet()-weekdays))
    private fun movement(
        id:String,muscle:TrainingMuscle,
        pattern:ComposerPattern,
        focus:Set<ComposerFocus> =ComposerFocus.entries.toSet(),
        role:ComposerRole=ComposerRole.SECONDARY
    )=CatalogMovement(id,id,"dumbbell",null,pattern,role,focus,
        "dumbbells",LoadBasis.DUMBBELL_PER_HAND,
        listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"reviewed-volume-v1")
    private val bench=movement("bench",TrainingMuscle.CHEST,
        ComposerPattern.HORIZONTAL_PRESS,role=ComposerRole.PRIMARY)
    private val row=movement("row",TrainingMuscle.LATS,
        ComposerPattern.HORIZONTAL_PULL)
    private val squat=movement("squat",TrainingMuscle.QUADS,
        ComposerPattern.SQUAT)
    private val movements=listOf(bench,row,squat)
    private fun choice(id:String,visits:Int=3,gap:Int=1)=
        CatalogExerciseChoice(id,n("25"),90,120,2,4,visits,1,gap,5)
    private fun bounds():List<DeclaredMuscleDoseBounds> =
        TrainingMuscle.entries.map {muscle ->
            val active=muscle in setOf(TrainingMuscle.CHEST,
                TrainingMuscle.LATS,TrainingMuscle.QUADS)
            DeclaredMuscleDoseBounds(muscle,
                n(if(active)"2"else"0"),
                n(if(active)"5"else"0"),
                n(if(active)"10"else"0"),if(active)1 else 0)
        }
    private fun policy(
        s:AthleteSetupReview,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        purposes:Map<ComposerSessionPurpose,Int> =
            mapOf(ComposerSessionPurpose.HYPERTROPHY to
                checkNotNull(s.reviewed).trainingWeekdays.size),
        primaries:Map<String,Int> =emptyMap()
    ):AuthoredGoalProgramPolicy {
        val athlete=checkNotNull(s.reviewed)
        val dose=bounds()
        return AuthoredGoalProgramPolicy("authored-week","v2",
            "Stable explicit doses and primary lift frequency.",
            athlete.athleteProfileId,athlete.profileRevision,
            athlete.goal,athlete.experience,athlete.split,
            phase,athlete.trainingWeekdays.size,dose,dose,purposes,primaries)
    }
    private fun request(
        s:AthleteSetupReview=setup(),
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        policy:AuthoredGoalProgramPolicy=policy(s,phase),
        extra:List<ComposerFocus> =emptyList(),
        choices:List<CatalogExerciseChoice> =
            movements.map {choice(it.exerciseId)}
    )=AthleteProgramGenerationRequest(
        s,phase,policy,movements,choices,
        checkNotNull(s.reviewed).trainingWeekdays.associateWith {1200},
        3,1,extra)

    @Test fun independentlyReviewedGoalCannotPromoteZeroSelectorFromCachedGreenWeek() {
        val input=GeneratedAdaptiveBlockFixture.generation()
        val generated=CoachAthleteProgramGenerator.generate(input)
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,generated.status)
        val original=checkNotNull(generated.candidate?.catalogReview)
        val template=checkNotNull(original.composedRequest)
        val athlete=checkNotNull(input.setup.reviewed)
        val validated=CoachGoalProgramRealizationDomain.review(
            athlete,input.phase,generated.intents,checkNotNull(input.policy),original)
        assertEquals(GoalRealizationStatus.DELIVERED_FOR_REVIEW,validated.status)

        // Simulate an independently presented, formerly green source:
        // the exact stock is real (and includes 0), but zero dumbbell load
        // is not a valid issued workout. Keep the cached week/audit intact.
        val prior=athlete.gym.confirmedStock.getValue("dumbbells")
            as VerifiedLoadStock.Totals
        val withZero=prior.copy(loads=listOf(n("0"))+prior.loads)
        val rewritten=template.copy(exercises=
            template.exercises.mapIndexed{index,option->
                val equipment=option.equipment as ComposerEquipment.Verified
                option.copy(equipment=equipment.copy(
                    plannedLoad=if(index==0)n("0") else equipment.plannedLoad,
                    inventory=withZero))
            })
        val current=athlete.copy(gym=athlete.gym.copy(
            confirmedStock=athlete.gym.confirmedStock+
                ("dumbbells" to withZero)))
        val candidate=original.copy(composedRequest=rewritten)
        val review=CoachGoalProgramRealizationDomain.review(
            current,input.phase,generated.intents,
            checkNotNull(input.policy),candidate)
        assertEquals(GoalRealizationStatus.UNRESOLVED,review.status)
        assertTrue("Cannot approve merely because stock has a zero setting: "+
            review.issues,review.issues.any{
                it.field=="week.gym" &&
                it.kind==GoalRealizationIssueKind.UNVERIFIED_CANDIDATE
            })
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.maySaveProgram)
    }

    @Test fun realizationRejectsUnverifiedOrSourceMismatchedWeeksEvenWhenDoseLooksGood() {
        val input=GeneratedAdaptiveBlockFixture.generation()
        val generated=CoachAthleteProgramGenerator.generate(input)
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,generated.status)
        val original=checkNotNull(generated.candidate?.catalogReview)
        val initial=checkNotNull(original.draft)
        val template=checkNotNull(original.composedRequest)
        val profile=checkNotNull(input.setup.reviewed)
        val policy=checkNotNull(input.policy)
        fun review(candidate:CatalogWeekReview)=
            CoachGoalProgramRealizationDomain.review(
                profile,input.phase,generated.intents,policy,candidate)
        val verified=review(original)
        assertEquals(verified.issues.toString(),
            GoalRealizationStatus.DELIVERED_FOR_REVIEW,verified.status)
        assertFalse(verified.mayIssueWorkout)

        val wrongPhase=TrainingPhase.STRENGTH
        val forged=listOf(
            original.copy(status=CatalogWeekStatus.WEEK_CONSTRAINTS_UNRESOLVED),
            original.copy(draft=initial.copy(status=ComposerStatus.CONSTRAINTS_UNRESOLVED)),
            original.copy(composedRequest=template.copy(phase=wrongPhase)),
            original.copy(draft=initial.copy(week=initial.week.copy(phase=wrongPhase))),
            original.copy(draft=initial.copy(audit=initial.audit.copy(phase=wrongPhase))),
            original.copy(composedRequest=template.copy(
                days=template.days.mapIndexed {index,day ->
                    if(index==0)day.copy(calendarDay=1) else day
                })),
            original.copy(draft=initial.copy(
                week=initial.week.copy(windows=initial.week.windows.mapIndexed { index,window ->
                    if(index==0)window.copy(declaredNonSetSeconds=
                        window.declaredNonSetSeconds+60) else window
                }))),
            original.copy(draft=initial.copy(sessions=initial.sessions.reversed()))
        )
        forged.forEachIndexed { index,candidate ->
            val result=review(candidate)
            assertEquals("Candidate "+index+" passed source gate: "+result.issues,
                GoalRealizationStatus.UNRESOLVED,result.status)
            assertTrue("Missing source issue for "+index+": "+result.issues,
                result.issues.any {
                    it.kind==GoalRealizationIssueKind.UNVERIFIED_CANDIDATE
                })
            assertFalse(result.maySaveProgram)
            assertFalse(result.mayIssueWorkout)
        }
    }

    @Test fun realizationReauditsActualEmittedSetsInsteadOfTrustingCachedResults() {
        val input=GeneratedAdaptiveBlockFixture.generation()
        val generated=CoachAthleteProgramGenerator.generate(input)
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,generated.status)
        val original=checkNotNull(generated.candidate?.catalogReview)
        val draft=checkNotNull(original.draft)
        val profile=checkNotNull(input.setup.reviewed)
        val policy=checkNotNull(input.policy)
        fun review(candidate:CatalogWeekReview)=
            CoachGoalProgramRealizationDomain.review(
                profile,input.phase,generated.intents,policy,candidate)
        assertEquals(GoalRealizationStatus.DELIVERED_FOR_REVIEW,
            review(original).status)
        val damaged=listOf(
            // A removed actual set with the formerly green audit left intact.
            draft.copy(week=draft.week.copy(
                exposures=draft.week.exposures.dropLast(1))),
            // A forged successful audit over the original prescribed week.
            draft.copy(audit=draft.audit.copy(daysWithoutWorkingSets=setOf(0))),
            // The UI session removes a movement although dose still looks fine.
            draft.copy(sessions=draft.sessions.mapIndexed {index,session ->
                if(index==0)session.copy(orderedVisits=
                    session.orderedVisits.dropLast(1)) else session
            }),
            // The displayed roster silently drops a working set; the
            // authoritative working exposures have not been rewritten.
            draft.copy(visits=draft.visits.mapIndexed {index,visit ->
                if(index==0)visit.copy(sets=visit.sets-1) else visit
            }),
            // Session-time audit must also come from exact working rows.
            draft.copy(week=draft.week.copy(exposures=
                draft.week.exposures.mapIndexed {index,row ->
                    if(index==0)row.copy(activeSeconds=row.activeSeconds+1)
                    else row
                }))
        )
        damaged.forEachIndexed {index,malformed ->
            val result=review(original.copy(draft=malformed))
            assertEquals("Stale candidate "+index+" was delivered: "+result.issues,
                GoalRealizationStatus.UNRESOLVED,result.status)
            assertTrue(result.issues.any {
                it.kind==GoalRealizationIssueKind.UNVERIFIED_CANDIDATE &&
                    it.field=="week.replay"
            })
            assertFalse(result.mayIssueWorkout)
            assertFalse(result.maySaveProgram)
        }
    }

    @Test fun futureStrengthComposerPreservesSourceArmVisitsButNotWithoutConsent() {
        val athlete=setup()
        val strengthPolicy=policy(athlete,TrainingPhase.STRENGTH,
            purposes=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 1,
                ComposerSessionPurpose.GENERAL_PREPARATION to 2),
            primaries=mapOf("bench" to 1))
        val editedBounds=strengthPolicy.selectedBounds.map {
            if(it.muscle in setOf(TrainingMuscle.BICEPS,
                TrainingMuscle.BRACHIALIS,TrainingMuscle.TRICEPS))
                it.copy(mav=n("4"),mrv=n("8"))
            else it
        }
        val authored=strengthPolicy.copy(baselineBounds=editedBounds,
            selectedBounds=editedBounds)
        val extras=listOf(
            movement("supinated-curl",TrainingMuscle.BICEPS,
                ComposerPattern.ISOLATION,role=ComposerRole.ACCESSORY),
            movement("neutral-hammer",TrainingMuscle.BRACHIALIS,
                ComposerPattern.ISOLATION,role=ComposerRole.ACCESSORY),
            movement("triceps-extension",TrainingMuscle.TRICEPS,
                ComposerPattern.ISOLATION,role=ComposerRole.ACCESSORY))
        val source=StrengthSourceArmReview(
            StrengthSourceArmsStatus.REVIEW_READY,"prior-cycle",
            "athlete2",6,"hypertrophy",listOf(
                StrengthSourceArmCandidate("supinated-curl",
                    TrainingMuscle.BICEPS,3),
                StrengthSourceArmCandidate("neutral-hammer",
                    TrainingMuscle.BRACHIALIS,2),
                StrengthSourceArmCandidate("triceps-extension",
                    TrainingMuscle.TRICEPS,3)),
            "exact prior custom program and audited phase exit")
        val original=request(s=athlete,phase=TrainingPhase.STRENGTH,
            policy=authored,choices=movements.map{choice(it.exerciseId)}+
                extras.map{choice(it.exerciseId).copy(requiredVisits=0)})
            .copy(catalog=movements+extras,
                maximumExercisesPerDay=5,
                selectFromOfferedCatalog=true,
                sourceStrengthArms=source)
        val generated=CoachAthleteProgramGenerator.generate(original)
        assertEquals("Source-backed arm retention should create actual " +
                "audited strength exercise visits: "+generated.issues,
            AthleteGenerationStatus.GENERATED_FOR_REVIEW,generated.status)
        val visits=checkNotNull(generated.candidate?.catalogReview?.draft)
            .visits.map{it.exerciseId}
        for(ex in extras)assertTrue("No actual "+ex.exerciseId+
            " strength visit in emitted week: "+visits,
            ex.exerciseId in visits)
        val chosen=checkNotNull(generated.exerciseSelection)
        for(ex in extras)assertTrue(chosen.choices.any {
            it.exerciseId==ex.exerciseId && it.requiredVisits>=1
        })
        assertFalse(generated.mayIssueWorkout)
        assertFalse(generated.maySaveProgram)

        val noPermission=CoachAthleteProgramGenerator.generate(
            original.copy(selectFromOfferedCatalog=false))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,
            noPermission.status)
        assertTrue(noPermission.issues.any {
            it.kind==AthleteGenerationIssueKind.EXERCISE_SELECTION_UNRESOLVED
        })
        val peak=CoachAthleteProgramGenerator.generate(
            original.copy(phase=TrainingPhase.PEAK))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,peak.status)
    }

    @Test fun refusesIncompleteProfileOrMissingPolicyWithoutGuessing() {
        val valid=request()
        val incomplete=CoachAthleteProgramGenerator.generate(
            valid.copy(setup=CoachAthleteSetupDomain.review(AthleteSetupDraft())))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,incomplete.status)
        assertNull(incomplete.candidate)
        assertTrue(incomplete.focusByWeekday.isEmpty())
        val noPolicy=CoachAthleteProgramGenerator.generate(
            valid.copy(policy=null))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,noPolicy.status)
        assertTrue(noPolicy.issues.any {
            it.kind==AthleteGenerationIssueKind.POLICY_UNRESOLVED
        })
    }

    @Test fun threeDayUpperLowerRequiresExplicitExtraFocusAndKeepsWeekdays() {
        val s=setup(ComposerSplit.UPPER_LOWER,setOf(0,2,4))
        val original=request(s=s)
        val missing=CoachAthleteProgramGenerator.generate(original)
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,missing.status)
        assertTrue(missing.issues.any {
            it.kind==AthleteGenerationIssueKind.FOCUS_DISTRIBUTION_INVALID
        })
        val generated=CoachAthleteProgramGenerator.generate(original.copy(
            extraFocuses=listOf(ComposerFocus.LOWER)))
        assertEquals(setOf(0,2,4),generated.focusByWeekday.keys)
        assertEquals(2,generated.focusByWeekday.values.count {
            it==ComposerFocus.LOWER
        })
        assertEquals(1,generated.focusByWeekday.values.count {
            it==ComposerFocus.UPPER
        })
        assertEquals(setOf(0,2,4),generated.intents.map {it.weekday}.toSet())
        assertFalse(generated.maySaveProgram)
        assertFalse(generated.mayIssueWorkout)
    }

    @Test fun fiveDayULPPLProducesAllFiveDistinctSplitDayFocuses() {
        val s=setup(ComposerSplit.ULPPL,setOf(0,1,2,4,5))
        val generated=CoachAthleteProgramGenerator.generate(request(s=s))
        assertEquals(ComposerFocus.entries.filter {
            it in setOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
                ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)
        }.toSet(),generated.focusByWeekday.values.toSet())
        assertEquals(5,generated.intents.size)
        assertEquals(5,generated.focusByWeekday.size)
        assertNotNull(generated.candidate)
    }

    @Test fun PPLFourDaysRequiresExtraFocusButSixDaysDoesNot() {
        val four=setup(ComposerSplit.PUSH_PULL_LEGS,setOf(0,1,3,5))
        val unresolved=CoachAthleteProgramGenerator.generate(request(s=four))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,unresolved.status)
        val fourDay=CoachAthleteProgramGenerator.generate(request(s=four,
            extra=listOf(ComposerFocus.PULL)))
        assertEquals(2,fourDay.focusByWeekday.values.count {
            it==ComposerFocus.PULL
        })
        val six=setup(ComposerSplit.PUSH_PULL_LEGS,setOf(0,1,2,3,4,5))
        val sixDay=CoachAthleteProgramGenerator.generate(request(s=six))
        assertEquals(6,sixDay.focusByWeekday.size)
        for(focus in listOf(ComposerFocus.PUSH,ComposerFocus.PULL,
            ComposerFocus.LEGS))
            assertEquals(2,sixDay.focusByWeekday.values.count {it==focus})
    }

    @Test fun priorityLiftCannotClaimZeroDumbbellStockAsRealStrengthWork() {
        val confirmed=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.DUMBBELL_PER_HAND,
            listOf(n("0"),n("20"),n("25"),n("30")))
        val s=setup(stock=confirmed)
        val p=policy(s,primaries=mapOf("bench" to 2))
        val zero=request(s=s,policy=p,choices=listOf(
            choice("bench").copy(selectedLoad=n("0")),
            choice("row"),choice("squat")))
        val refused=CoachAthleteProgramGenerator.generate(zero)
        // Catalog input review rejects this even before optional priority
        // scheduling; the later generator gate remains independent defense.
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,refused.status)
        assertNull(refused.candidate)
        assertTrue(refused.intents.none{
            it.requiredPrimaryExerciseId=="bench"
        })

        // A real positive selector remains numerically issuable and
        // physically exact on this same equipment. The separate goal
        // program planner can still refuse placement for other reasons;
        // this test must not invent a schedule-approval guarantee.
        assertTrue(KotlinSetNumberAuthorityDomain.validPrescribedLoad(
            n("25"),LoadBasis.DUMBBELL_PER_HAND))
        val at25=EquipmentLoadDomain.inspect(
            confirmed,n("25"),LoadUnit.LB) as LoadInspection.Confirmed
        assertTrue(at25.exact)
    }

    @Test fun priorityLiftPlacementUsesVerifiedLoadAndRealDayFrequency() {
        val s=setup()
        val p=policy(s,primaries=mapOf("bench" to 2))
        val result=CoachAthleteProgramGenerator.generate(request(s=s,policy=p))
        assertEquals(3,result.intents.size)
        // The reviewed policy is explicitly a minimum, not an exact
        // exposure lock. Every selected exposure must have its own day.
        val benchDays=result.intents.filter {
            it.requiredPrimaryExerciseId=="bench"
        }
        assertTrue(benchDays.size in 2..3)
        assertEquals(benchDays.size,benchDays.map {it.weekday}.distinct().size)
        val matching=checkNotNull(result.candidate)
        assertNotNull(matching.realization)
        assertTrue(matching.issues.none {
            it.kind==AthleteCandidateIssueKind.GOAL_POLICY_UNRESOLVED
        })
        assertEquals(2,matching.realization?.primaryFrequencies?.single()?.requiredDays)
    }

    @Test fun extraPrimaryWinsOnlyForSubstantiveQualityNotClockPadding() {
        fun better(a:List<Long>,b:List<Long>):Boolean {
            val index=a.indices.firstOrNull{a[it]!=b[it]} ?:return false
            return a[index]<b[index]
        }
        val two=CoachAthleteProgramGenerator.primaryPlacementQuality(
            listOf(0L,1000L,20L,0L,30L,10L),0)
        val productiveThird=CoachAthleteProgramGenerator.primaryPlacementQuality(
            listOf(0L,0L,20L,0L,30L,10L),1)
        val betterSpacingThird=CoachAthleteProgramGenerator.primaryPlacementQuality(
            listOf(0L,1000L,0L,0L,30L,10L),1)
        val cosmeticThird=CoachAthleteProgramGenerator.primaryPlacementQuality(
            listOf(0L,1000L,20L,0L,0L,0L),1)
        val fatiguedThird=CoachAthleteProgramGenerator.primaryPlacementQuality(
            listOf(1L,0L,0L,0L,0L,0L),1)
        assertTrue(better(productiveThird,two))
        assertTrue(better(betterSpacingThird,two))
        assertTrue(better(two,cosmeticThird))
        assertTrue(better(two,fatiguedThird))
    }

    @Test fun strengthPurposeDaysGetActualPrimaryLiftsNotJustSkillLabels() {
        val s=setup()
        val p=policy(s,phase=TrainingPhase.STRENGTH,
            purposes=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 2,
                ComposerSessionPurpose.GENERAL_PREPARATION to 1),
            primaries=mapOf("bench" to 2))
        val generated=CoachAthleteProgramGenerator.generate(request(
            s=s,phase=TrainingPhase.STRENGTH,policy=p))
        assertEquals(2,generated.intents.count {
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        })
        assertEquals(2,generated.intents.count {
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL &&
                it.requiredPrimaryExerciseId=="bench"
        })
        assertEquals(1,generated.intents.count {
            it.purpose==ComposerSessionPurpose.GENERAL_PREPARATION
        })
        assertTrue(generated.candidate?.issues?.none {
            it.kind==AthleteCandidateIssueKind.GOAL_POLICY_UNRESOLVED
        } ==true)
    }

    @Test fun badLiftRecoveryAcrossSundayMondayIsNotSilentlyRelaxed() {
        val s=setup(weekdays=setOf(0,2,6))
        val p=policy(s,primaries=mapOf("bench" to 3))
        val constrained=request(s=s,policy=p,choices=listOf(
            choice("bench",visits=3,gap=2),
            choice("row"),choice("squat")))
        val result=CoachAthleteProgramGenerator.generate(constrained)
        assertEquals(AthleteGenerationStatus.PLACEMENTS_UNRESOLVED,result.status)
        assertNull(result.candidate)
        assertTrue(result.issues.any {
            it.kind==AthleteGenerationIssueKind.SCHEDULE_CONFLICT
        })
    }

    @Test fun unachievablePrimaryMaxVisitsIsReportedNotDropped() {
        val s=setup()
        val p=policy(s,primaries=mapOf("bench" to 3))
        val constrained=request(s=s,policy=p,choices=listOf(
            choice("bench",visits=1),choice("row"),choice("squat")))
        val result=CoachAthleteProgramGenerator.generate(constrained)
        assertEquals(AthleteGenerationStatus.PLACEMENTS_UNRESOLVED,result.status)
        assertTrue(result.focusByWeekday.isEmpty())
        assertNull(result.candidate)
    }

    @Test fun invalidPurposeTotalCannotCreateInventedGeneralWorkouts() {
        val s=setup()
        val incomplete=policy(s,purposes=
            mapOf(ComposerSessionPurpose.HYPERTROPHY to 1))
        val generated=CoachAthleteProgramGenerator.generate(request(
            s=s,policy=incomplete))
        assertEquals(AthleteGenerationStatus.INPUTS_UNRESOLVED,generated.status)
        assertTrue(generated.intents.isEmpty())
    }

    @Test fun optedInSelectorPrunesUnknownOfferButPreservesPinnedLifts() {
        val original=request()
        val extra=choice("unknown-machine",visits=2).copy(requiredVisits=0)
        val authored=original.choices.map {
            if(it.exerciseId=="bench")it.copy(requiredVisits=1)
            else it.copy(requiredVisits=0)
        }
        val optIn=original.copy(
            choices=authored+extra,selectFromOfferedCatalog=true,
            maximumSelectedOptions=3)
        val review=CoachAthleteProgramGenerator.generate(optIn)
        val selected=checkNotNull(review.exerciseSelection)
        assertEquals(AthleteSelectionStatus.SHORTLIST_FOR_REVIEW,
            selected.status)
        assertEquals(setOf("bench","row","squat"),
            selected.choices.map {it.exerciseId}.toSet())
        assertTrue(selected.excluded.any {
            it.exerciseId=="unknown-machine"
        })
        assertNotNull(review.candidate)
        assertTrue(review.candidate?.catalogReview?.requestedExerciseIds
            ?.contains("unknown-machine")==false)
        assertFalse(review.maySaveProgram)
    }

    @Test fun defaultSelectionModeNeverSilentlyPrunesUnknownAuthoredChoice() {
        val original=request()
        val offered=original.choices+choice("unknown-machine")
        val noOptIn=CoachAthleteProgramGenerator.generate(
            original.copy(choices=offered))
        assertNull(noOptIn.exerciseSelection)
        assertTrue(noOptIn.status!=AthleteGenerationStatus.GENERATED_FOR_REVIEW)
    }

    @Test fun generationIsDeterministicAndDoesNotMutateCallerInputs() {
        val input=request()
        val a=CoachAthleteProgramGenerator.generate(input)
        val b=CoachAthleteProgramGenerator.generate(input)
        assertEquals(a,b)
        assertEquals(setOf(0,2,4),input.setup.reviewed?.trainingWeekdays)
        assertEquals(3,input.choices.size)
        assertFalse(a.mayIssueWorkout)
    }

    @Test fun feasibleWeekRankingPrefersRecoveryDoseAndBalancedSessionLength() {
        val generated=CoachAthleteProgramGenerator.generate(request())
        val actual=generated.candidate?.catalogReview?.draft
        if(generated.status==AthleteGenerationStatus.GENERATED_FOR_REVIEW) {
            assertNotNull(actual)
            val evidence=generated.search
            assertTrue(evidence.feasibleWeeksCompared>=1)
            assertTrue(evidence.feasibleWeeksCompared<=5)
            assertTrue(evidence.draftsAudited>=evidence.feasibleWeeksCompared)
            assertTrue(evidence.placementsChecked>=evidence.draftsAudited)
            assertTrue(evidence.draftsAudited<=32)
            val focus=checkNotNull(generated.candidate?.catalogReview)
                .composedRequest?.days?.map {it.focus}
            val purpose=generated.intents.map {it.purpose}
            val score=CoachAthleteProgramGenerator.qualityScore(
                checkNotNull(actual),checkNotNull(focus),purpose,
                checkNotNull(generated.candidate?.catalogReview)
                    .composedRequest!!.days.map {it.calendarDay})
            assertEquals(6,score.size)
            assertTrue(score.all {it>=0L})
        } else {
            assertEquals(0,generated.search.feasibleWeeksCompared)
            assertTrue(generated.issues.isNotEmpty())
        }
        assertEquals(generated,CoachAthleteProgramGenerator.generate(request()))
    }


    @Test fun qualityRankingPrefersActualMavDoseBeforeCosmeticPurposeSpacing() {
        // Use the same explicitly feasible 40–60 minute source-backed
        // program fixture as the generation matrix. The bare request()
        // intentionally supplies only 20 minutes of non-set work and may
        // remain unresolved under the hard program-quality audit.
        val generated=CoachAthleteProgramGenerator.generate(
            GeneratedAdaptiveBlockFixture.generation())
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,
            generated.status)
        val draft=checkNotNull(generated.candidate?.catalogReview?.draft)
        val weekdays=listOf(0,2,4)
        val focuses=List(3){ComposerFocus.FULL_BODY}
        val ordered=listOf(ComposerSessionPurpose.HYPERTROPHY,
            ComposerSessionPurpose.GENERAL_PREPARATION,
            ComposerSessionPurpose.HYPERTROPHY)
        val repetitive=List(3){ComposerSessionPurpose.HYPERTROPHY}
        fun forceGap(gap:String)=draft.copy(audit=draft.audit.copy(
            muscles=draft.audit.muscles.mapValues {(_,finding)->
                finding.copy(gapToMav=n(gap))
            }))
        val delivered=CoachAthleteProgramGenerator.qualityScore(
            forceGap("0"),focuses,repetitive,weekdays)
        val underdosed=CoachAthleteProgramGenerator.qualityScore(
            forceGap("1"),focuses,ordered,weekdays)
        assertEquals(6,delivered.size)
        assertEquals(0L,delivered[0])
        assertEquals(0L,delivered[1])
        assertTrue(underdosed[1]>delivered[1])
        // Purpose spacing no longer hides an otherwise materially better
        // week that delivers the athlete's reviewed phase MAV.
        assertTrue(delivered[1]<underdosed[1])
        assertEquals(delivered,CoachAthleteProgramGenerator.qualityScore(
            forceGap("0"),focuses,repetitive,weekdays))
    }

    @Test fun actualAdjacentRepeatStillOutranksSlightlyBetterSoftDose() {
        // Use the same explicitly feasible 40–60 minute source-backed
        // program fixture as the generation matrix. The bare request()
        // intentionally supplies only 20 minutes of non-set work and may
        // remain unresolved under the hard program-quality audit.
        val generated=CoachAthleteProgramGenerator.generate(
            GeneratedAdaptiveBlockFixture.generation())
        assertEquals(AthleteGenerationStatus.GENERATED_FOR_REVIEW,
            generated.status)
        val draft=checkNotNull(generated.candidate?.catalogReview?.draft)
        val days=listOf(0,1,4)
        val focus=listOf(ComposerFocus.UPPER,
            ComposerFocus.LOWER,ComposerFocus.UPPER)
        val purpose=List(3){ComposerSessionPurpose.HYPERTROPHY}
        val spacedVisits=listOf(
            ComposerVisit(0,"bench",ComposerPattern.HORIZONTAL_PRESS,2),
            ComposerVisit(2,"bench",ComposerPattern.HORIZONTAL_PRESS,2))
        val adjacentVisits=spacedVisits.mapIndexed {index,visit->
            if(index==1)visit.copy(dayIndex=1)else visit
        }
        val safe=draft.copy(visits=spacedVisits)
        val closerToMav=draft.copy(visits=adjacentVisits,
            audit=draft.audit.copy(muscles=draft.audit.muscles.mapValues {
                (_,finding)->finding.copy(gapToMav=n("0"))
            }))
        val good=CoachAthleteProgramGenerator.qualityScore(
            safe,focus,purpose,days)
        val adjacent=CoachAthleteProgramGenerator.qualityScore(
            closerToMav,focus,purpose,days)
        assertEquals(0L,good[0])
        assertTrue(adjacent[0]>good[0])
        // Avoid adjacent same-exercise days before optimizing soft MAV gap.
        assertTrue(good[0]<adjacent[0])
        assertEquals(1L,CoachAthleteProgramGenerator.adjacentRepeatCount(
            adjacentVisits,focus,days))
        assertEquals(0L,CoachAthleteProgramGenerator.adjacentRepeatCount(
            spacedVisits,focus,days))
        // Sunday -> Monday is also adjacent in a repeating cycle.
        assertEquals(1L,CoachAthleteProgramGenerator.adjacentRepeatCount(
            listOf(ComposerVisit(0,"bench",ComposerPattern.HORIZONTAL_PRESS,2),
                ComposerVisit(1,"bench",ComposerPattern.HORIZONTAL_PRESS,2)),
            listOf(ComposerFocus.UPPER,ComposerFocus.LOWER),
            listOf(6,0)))
    }

    @Test fun overlappingSplitDayRecoveryIsNotLimitedToIdenticalFocusNames() {
        val none=emptyList<ComposerVisit>()
        fun repeats(a:ComposerFocus,b:ComposerFocus,days:List<Int>)=
            CoachAthleteProgramGenerator.adjacentRepeatCount(
                none,listOf(a,b),days)
        // Repeating-week boundary must be treated exactly like midweek.
        assertEquals(1L,repeats(ComposerFocus.UPPER,
            ComposerFocus.PUSH,listOf(6,0)))
        assertEquals(1L,repeats(ComposerFocus.UPPER,
            ComposerFocus.PULL,listOf(0,1)))
        assertEquals(1L,repeats(ComposerFocus.LOWER,
            ComposerFocus.LEGS,listOf(0,1)))
        assertEquals(1L,repeats(ComposerFocus.FULL_BODY,
            ComposerFocus.LEGS,listOf(0,1)))
        // Standard push/pull sequencing must not be falsely penalized.
        assertEquals(0L,repeats(ComposerFocus.PUSH,
            ComposerFocus.PULL,listOf(0,1)))
        assertEquals(0L,repeats(ComposerFocus.UPPER,
            ComposerFocus.PUSH,listOf(0,2)))
        // The second ranking tier must agree: there is no hidden
        // upper-body penalty for consecutive Push and Pull sessions.
        val source=CoachAthleteProgramGenerator.generate(
            GeneratedAdaptiveBlockFixture.generation())
        val draft=checkNotNull(source.candidate?.catalogReview?.draft)
            .copy(visits=emptyList())
        val score=CoachAthleteProgramGenerator.qualityScore(
            draft,listOf(ComposerFocus.PUSH,ComposerFocus.PULL,
                ComposerFocus.LEGS),
            List(3){ComposerSessionPurpose.HYPERTROPHY},
            listOf(0,1,3))
        assertEquals(0L,score[0])
        assertEquals(0L,score[2])
        // A repeated lift still counts even when its day focus differs.
        val sameLift=listOf(
            ComposerVisit(0,"bench",ComposerPattern.HORIZONTAL_PRESS,2),
            ComposerVisit(1,"bench",ComposerPattern.HORIZONTAL_PRESS,2))
        assertEquals(1L,CoachAthleteProgramGenerator.adjacentRepeatCount(
            sameLift,listOf(ComposerFocus.PUSH,ComposerFocus.PULL),
            listOf(6,0)))
    }

    @Test fun repeatLiftRecoveryRankingRecognizesWeekBoundaryAndSeparateExercises() {
        val calendar=listOf(0,2,4,6)
        fun lift(day:Int,id:String="bench")=ComposerVisit(
            day,id,ComposerPattern.HORIZONTAL_PRESS,3)
        val spaced=listOf(lift(0),lift(2))
        val twoDayGap=listOf(lift(0),lift(3))
        assertEquals(0L,CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
            spaced,calendar))
        // Saturday -> Monday is a two-day calendar gap (the wrap counts).
        assertEquals(20L,CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
            twoDayGap,listOf(0,2,4,5)))
        val sundayMonday=listOf(lift(0),lift(3))
        assertEquals(100L,CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
            sundayMonday,listOf(0,2,3,6)))
        assertEquals(0L,CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
            listOf(lift(0),lift(0),lift(3,"row")),calendar))
        assertEquals(
            CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(spaced,calendar),
            CoachAthleteProgramGenerator.repeatLiftSpacingPenalty(
                spaced.reversed(),calendar))
    }

    @Test fun spacingPenaltyNeverOverridesEquipmentVolumeOrAuthoredConstraints() {
        val input=request()
        val generated=CoachAthleteProgramGenerator.generate(input)
        val draft=generated.candidate?.catalogReview?.draft
        if(generated.status==AthleteGenerationStatus.GENERATED_FOR_REVIEW) {
            val audited=checkNotNull(draft)
            assertTrue(audited.audit.satisfiesDeclaredHardConstraints)
            assertTrue(audited.daysWithUnmetPrimary.isEmpty())
            assertTrue(audited.missingRequiredVisits.isEmpty())
            assertTrue(audited.excludedUnverifiedExerciseIds.isEmpty())
            assertEquals(0,generated.issues.size)
            assertTrue(generated.search.feasibleWeeksCompared in 1..5)
        }else {
            assertFalse(generated.maySaveProgram)
            assertFalse(generated.mayIssueWorkout)
        }
        assertEquals(generated,CoachAthleteProgramGenerator.generate(input))
    }


    @Test fun primaryPlacementEnumeratesAlternativesWithoutBreakingFrequencyOrRecovery() {
        val s=setup(weekdays=setOf(0,1,3,5))
        val p=policy(s,primaries=mapOf("bench" to 2))
        val calendar=listOf(0,1,3,5)
        val focus=List(4){ComposerFocus.FULL_BODY}
        val purpose=List(4){ComposerSessionPurpose.HYPERTROPHY}
        val options=movements.associateBy {it.exerciseId}
        val choices=movements.associate {it.exerciseId to choice(it.exerciseId,
            visits=3,gap=2)}
        val placements=CoachAthleteProgramGenerator.placements(
            calendar,focus,purpose,p,options,choices)
        assertTrue("More than one valid coach placement must be reviewed",
            placements.size>=2)
        assertTrue(placements.size<=8)
        assertEquals(placements.distinct().size,placements.size)
        assertTrue(placements.all {schedule ->
            val assigned=schedule.filter {it.requiredPrimaryExerciseId=="bench"}
                .map {it.weekday}
            assigned.size>=2 && assigned.size<=3 &&
                assigned.indices.all {a->assigned.indices.all {b->
                    a==b || minOf(kotlin.math.abs(assigned[a]-assigned[b]),
                        7-kotlin.math.abs(assigned[a]-assigned[b]))>=2
                }}
        })
        assertEquals(placements,
            CoachAthleteProgramGenerator.placements(calendar,focus,purpose,
                p,options,choices))
        assertEquals(0,p.primaryLiftMinimumDays["row"] ?: 0)
    }

    @Test fun primaryLiftSearchRanksSpacedWeekRatherThanFirstEightDfsLeaves() {
        val s=setup(weekdays=setOf(0,1,2,3,4,5))
        val required=policy(s,primaries=mapOf("bench" to 2))
        val calendar=(0..5).toList()
        val placements=CoachAthleteProgramGenerator.placements(
            calendar,List(6){ComposerFocus.FULL_BODY},
            List(6){ComposerSessionPurpose.HYPERTROPHY},
            required,movements.associateBy {it.exerciseId},
            movements.associate {it.exerciseId to choice(it.exerciseId,
                visits=2,gap=1)})
        assertEquals(8,placements.size)
        fun selectedDays(plan:List<AthleteCalendarIntent>)=plan.filter {
            it.requiredPrimaryExerciseId=="bench"
        }.map {it.weekday}.toSet()
        // All five pairs with circular recovery gaps of at least
        // three days lead the ranking (including Monday/Thursday,
        // which the old DFS-first-eight list omitted).
        assertEquals(setOf(setOf(0,3),setOf(0,4),setOf(1,4),
            setOf(1,5),setOf(2,5)),
            placements.take(5).map(::selectedDays).toSet())
        assertTrue(placements.all {selectedDays(it).size==2})
        assertEquals(placements,CoachAthleteProgramGenerator.placements(
            calendar,List(6){ComposerFocus.FULL_BODY},
            List(6){ComposerSessionPurpose.HYPERTROPHY},required,
            movements.associateBy {it.exerciseId},
            movements.associate {it.exerciseId to choice(it.exerciseId,
                visits=2,gap=1)}))
    }

    @Test fun boundedPrimarySearchRetainsOptionalExtraFrequencyForWholeDoseAudit() {
        val s=setup(weekdays=setOf(0,1,2,3,4,5))
        val required=policy(s,primaries=mapOf("bench" to 2))
        val plans=CoachAthleteProgramGenerator.placements(
            (0..5).toList(),List(6){ComposerFocus.FULL_BODY},
            List(6){ComposerSessionPurpose.HYPERTROPHY},
            required,movements.associateBy {it.exerciseId},
            movements.associate {it.exerciseId to choice(it.exerciseId,
                visits=3,gap=1)})
        assertEquals(8,plans.size)
        val frequencies=plans.map {plan->plan.count {
            it.requiredPrimaryExerciseId=="bench"
        }}
        assertEquals(2,frequencies.first())
        assertEquals(3,frequencies.last())
        assertTrue(frequencies.all {it in 2..3})
    }

    @Test fun alternatePrimaryPlacementsRespectSundayToMondayRecovery() {
        val s=setup(weekdays=setOf(0,2,4,6))
        val policy=policy(s,primaries=mapOf("bench" to 2))
        val assignments=CoachAthleteProgramGenerator.placements(
            listOf(0,2,4,6),List(4){ComposerFocus.FULL_BODY},
            List(4){ComposerSessionPurpose.HYPERTROPHY},
            policy,movements.associateBy {it.exerciseId},
            movements.associate {it.exerciseId to choice(it.exerciseId,
                visits=3,gap=2)})
        assertTrue(assignments.isNotEmpty())
        assertTrue(assignments.all {a->
            val days=a.filter {it.requiredPrimaryExerciseId=="bench"}
                .map {it.weekday}.sorted()
            days.size>=2 &&
                days.zipWithNext().all { (x,y)->y-x>=2 } &&
                (days.first()+7-days.last())>=2
        })
    }

}
