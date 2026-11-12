package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** Shipped catalog plus declared fictional calibration; no toy movement credits. */
internal object StarterProgramReleaseFixture {
    private fun n(value:String)=BigDecimal(value)
    fun request(split:ComposerSplit,phase:TrainingPhase,
        includeIncline:Boolean=false):FirstProgramStartRequest {
        val days=when(split) {
            ComposerSplit.FULL_BODY->setOf(0,2,4)
            ComposerSplit.UPPER_LOWER->setOf(0,1,3,4)
            ComposerSplit.ULPPL->setOf(0,1,2,4,5)
            ComposerSplit.PUSH_PULL_LEGS->setOf(0,1,2,3,4,5)
        }
        val setup=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-1",athleteProfileId="athlete-1",
            goal=if(phase==TrainingPhase.HYPERTROPHY)AthleteTrainingGoal.HYPERTROPHY
                else AthleteTrainingGoal.POWERBUILDING,
            experience=AthleteExperience.ADVANCED,split=split,
            trainingWeekdays=days,timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,mapOf(
                "straight-bar" to VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
                    mapOf(n("45") to 2,n("25") to 2,n("10") to 2,n("5") to 2)),
                "dumbbell-rack" to VerifiedLoadStock.Totals(LoadUnit.LB,
                    LoadBasis.DUMBBELL_PER_HAND,(5..50 step 5).map{BigDecimal(it)})
            ),setOf("flat-bench","rack")+
                (if(includeIncline)setOf("incline-bench") else emptySet()),
                emptySet()),
            allowBodyweight=true,allowSupersets=false,allowTricepsOnLower=false,
            maxBarbellExercisesPerDay=1,focusedMuscles=emptySet(),
            reducedMuscles=emptySet(),excludedExerciseIds=emptySet(),
            lockedTrainingDays=emptySet(),hardTrainingDays=emptySet(),
            longTrainingDays=emptySet(),declaredRestDays=(0..6).toSet()-days))
        return FirstProgramStartRequest(setup,StarterExerciseTranche.movements(),
            mapOf("barbell_bench" to n("135"),"back_squat" to n("135"),
                "one_arm_db_row" to n("30"),"db_rdl" to n("30"),
                "db_lateral_raise" to n("15"),"db_curl" to n("25"),
                "db_overhead_triceps" to n("25"),"db_calf_raise" to n("30"))+
                (if(includeIncline)mapOf("incline_db_press" to n("30"))
                    else emptyMap()),
            setOf("bodyweight_crunch"),phase,nonSetSecondsPerDay=900)
    }
}

class StarterProgramReleaseMatrixTest {
    @Test fun calibratedWristMovementsCanBecomeRealDistinctProgramWork() {
        val source=StarterProgramReleaseFixture.request(
            ComposerSplit.ULPPL,TrainingPhase.HYPERTROPHY).copy(
            nonSetSecondsPerDay=600)
        val chosen=source.copy(startingLoads=source.startingLoads+
            mapOf("wrist-curl" to BigDecimal("15"),
                "reverse-wrist-curl" to BigDecimal("10")))
        val review=CoachFirstProgramStartDomain.review(chosen)
        assertEquals("A calibrated forearm choice should become a reviewable week: ${review.issues}",
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,review.status)
        assertTrue(review.chosenExerciseIds.containsAll(
            listOf("wrist-curl","reverse-wrist-curl")))
        assertFalse(TrainingMuscle.WRIST_FLEXORS in review.unsupportedMuscles)
        assertFalse(TrainingMuscle.WRIST_EXTENSORS in review.unsupportedMuscles)
        val week=checkNotNull(review.prescription)
        assertTrue(checkNotNull(week.finalAudit).satisfiesDeclaredHardConstraints)
        val visits=week.sessions.flatMap{it.exercises}
        for(id in listOf("wrist-curl","reverse-wrist-curl")) {
            val actual=visits.filter{it.exerciseId==id}
            assertTrue("$id was selected and calibrated but never programmed",actual.isNotEmpty())
            assertTrue(actual.any {exercise ->
                exercise.sets.any{it.type==DraftSetType.WORKING}
            })
        }
        assertEquals(BigDecimal("15"),chosen.startingLoads.getValue("wrist-curl"))
        assertEquals(BigDecimal("10"),chosen.startingLoads.getValue("reverse-wrist-curl"))
        // One machine/exercise cannot silently borrow the other's
        // baseline or absorb the other's explicit direct-muscle credit.
        val invalid=CoachFirstProgramStartDomain.review(chosen.copy(
            startingLoads=chosen.startingLoads+
                ("reverse-wrist-curl" to BigDecimal("12.5"))))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,invalid.status)
        assertNull(invalid.prescription)
        assertFalse(review.mayIssueWorkout)
    }

    @Test fun wristFlexionAndExtensionRemainSeparateAndSourceLinked() {
        val entries=StarterExerciseTranche.entries()
        assertTrue(entries.size>=22)
        assertEquals(listOf("wrist-curl","reverse-wrist-curl"),
            entries.drop(20).take(2).map{it.legacyCuratedId})
        val flex=entries.single{it.legacyCuratedId=="wrist-curl"}
        val ext=entries.single{it.legacyCuratedId=="reverse-wrist-curl"}
        assertEquals("wrist-curl",flex.movement.exerciseId)
        assertEquals("reverse-wrist-curl",ext.movement.exerciseId)
        assertEquals("wrist_flexion",flex.movement.familyId)
        assertEquals("wrist_extension",ext.movement.familyId)
        assertEquals(RepBand(12,20),flex.practicalRepBand)
        assertEquals(RepBand(12,20),ext.practicalRepBand)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,flex.movement.expectedLoadBasis)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,ext.movement.expectedLoadBasis)
        assertEquals("dumbbell-rack",flex.movement.equipmentId)
        assertEquals("dumbbell-rack",ext.movement.equipmentId)
        assertEquals(listOf(DeclaredMuscleCredit(
            TrainingMuscle.WRIST_FLEXORS,MuscleCreditRole.DIRECT,BigDecimal.ONE)),
            flex.movement.credits)
        assertEquals(listOf(DeclaredMuscleCredit(
            TrainingMuscle.WRIST_EXTENSORS,MuscleCreditRole.DIRECT,BigDecimal.ONE)),
            ext.movement.credits)
        assertFalse(flex.mayTransferPreviousLoad)
        assertFalse(ext.mayTransferPreviousLoad)
        // Wrist isolation must not be silently promoted into brachialis
        // training. A separately source-linked hammer curl MAY train that
        // distinct muscle; its existence is not a wrist-credit regression.
        assertTrue(listOf(flex,ext).none{
            it.movement.credits.any{credit->
                credit.muscle==TrainingMuscle.BRACHIALIS
            }
        })
        // No new weight is invented: these require athlete-specific
        // verified dumbbell stock and explicit starting-load calibration.
        assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
            flex.movement.creditStatus)
        assertEquals(CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,
            ext.movement.creditStatus)
    }

    @Test fun twelveRealStarterCasesPreserveDoseStockRecoveryAndWholeSessionTime() {
        for(split in ComposerSplit.entries)for(phase in TrainingPhase.entries) {
            val input=StarterProgramReleaseFixture.request(split,phase)
            val result=CoachFirstProgramStartDomain.review(input)
            assertEquals("$split/$phase: ${result.issues}",
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
            val draft=checkNotNull(result.prescription)
            val audit=checkNotNull(draft.finalAudit)
            assertTrue(audit.satisfiesDeclaredHardConstraints)
            assertTrue(audit.daysOutsidePreferredWindow.isEmpty())
            assertEquals(checkNotNull(input.setup.reviewed).trainingWeekdays.size,draft.sessions.size)
            assertEquals(draft.composer.audit.muscles,audit.muscles)
            for(session in draft.sessions) {
                val sets=session.exercises.flatMap{it.sets}
                val seconds=900L+sets.sumOf{it.activeSeconds.toLong()+it.restSeconds}-
                    sets.last().restSeconds
                assertEquals(seconds,audit.sessions[session.dayIndex].declaredSeconds)
                assertTrue(checkNotNull(session.estimatedMinutes) in 40L..60L)
                session.exercises.forEach {exercise->
                    val spec=input.catalog.single{it.exerciseId==exercise.exerciseId}
                    exercise.sets.forEach setLoop@{set->
                        val load=set.load as? DraftSetLoad.Verified ?:return@setLoop
                        val stock=input.setup.reviewed?.gym?.confirmedStock?.get(spec.equipmentId)
                        val checked=EquipmentLoadDomain.inspect(stock,load.value,load.unit)
                        assertTrue(checked is LoadInspection.Confirmed && checked.exact)
                    }
                }
            }
            if(phase!=TrainingPhase.HYPERTROPHY) {
                assertTrue(result.phasePoliciesChecked in 1..32)
                assertFalse(result.phaseSearchBudgetReached)
                val policy=checkNotNull(result.policy)
                val generated=checkNotNull(result.generation)
                val skill=generated.intents.filter {
                    it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
                }
                assertEquals(setOf("barbell_bench","back_squat"),policy.primaryLiftMinimumDays.keys)
                // Provisional strength/peak starters must not claim two
                // exposures by coincidence while the authored minimum
                // remains one. On splits with two upper and two lower
                // slots, enforce two distinct primary days for each lift.
                if(split!=ComposerSplit.FULL_BODY)
                    assertEquals(mapOf("barbell_bench" to 2,"back_squat" to 2),
                        policy.primaryLiftMinimumDays)
                assertEquals(if(split==ComposerSplit.FULL_BODY)3 else 4,skill.size)
                for(intent in skill) {
                    val lift=input.catalog.single{it.exerciseId==intent.requiredPrimaryExerciseId}
                    assertTrue(generated.focusByWeekday.getValue(intent.weekday) in lift.permittedFocus)
                    val dayIndex=checkNotNull(input.setup.reviewed).trainingWeekdays.sorted()
                        .indexOf(intent.weekday)
                    val session=draft.sessions.single{it.dayIndex==dayIndex}
                    assertEquals(lift.exerciseId,session.exercises.first().exerciseId)
                    val work=session.exercises.first().sets.filter{it.type==DraftSetType.WORKING}
                    assertTrue(work.all{it.targetReps==RepBand(3,6) && it.targetRir==2})
                }
                for(intent in generated.intents.filter{it !in skill}) {
                    assertEquals(ComposerSessionPurpose.GENERAL_PREPARATION,intent.purpose)
                    assertEquals(ComposerFocus.PULL,generated.focusByWeekday[intent.weekday])
                }
                val frequencies=checkNotNull(generated.candidate?.realization).primaryFrequencies
                assertTrue(frequencies.all{it.deliveredDays>=it.requiredDays})
                if(split!=ComposerSplit.FULL_BODY)
                    assertTrue(frequencies.all{it.deliveredDays==2})
            }
            // Known starter-library gaps remain visible, not waived by a
            // mechanical week passing its declared provisional policy.
            assertTrue(result.unsupportedMuscles.containsAll(setOf(
                TrainingMuscle.BRACHIALIS,TrainingMuscle.WRIST_FLEXORS,
                TrainingMuscle.WRIST_EXTENSORS)))
            assertFalse(result.mayIssueWorkout)
        }
    }

    @Test fun fiveDayUlpplStrengthAndPeakGuaranteeTwoBenchAndTwoSquatSkills() {
        for(phase in listOf(TrainingPhase.STRENGTH,TrainingPhase.PEAK)) {
            val result=CoachFirstProgramStartDomain.review(
                StarterProgramReleaseFixture.request(ComposerSplit.ULPPL,phase))
            assertEquals("$phase: ${result.issues}",
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
            assertEquals(mapOf("barbell_bench" to 2,"back_squat" to 2),
                result.policy?.primaryLiftMinimumDays)
            val generation=checkNotNull(result.generation)
            val benchDays=generation.intents.filter {
                it.requiredPrimaryExerciseId=="barbell_bench"
            }.map{it.weekday}.toSet()
            val squatDays=generation.intents.filter {
                it.requiredPrimaryExerciseId=="back_squat"
            }.map{it.weekday}.toSet()
            assertEquals(2,benchDays.size)
            assertEquals(2,squatDays.size)
            assertTrue(benchDays.intersect(squatDays).isEmpty())
            assertEquals(setOf(ComposerFocus.UPPER,ComposerFocus.PUSH),
                benchDays.map{generation.focusByWeekday.getValue(it)}.toSet())
            assertEquals(setOf(ComposerFocus.LOWER,ComposerFocus.LEGS),
                squatDays.map{generation.focusByWeekday.getValue(it)}.toSet())
            assertTrue(checkNotNull(result.prescription?.finalAudit)
                .satisfiesDeclaredHardConstraints)
        }
    }

    @Test fun calibratedInclineVariantRemainsInUlpplStrengthAndPeak() {
        for(phase in listOf(TrainingPhase.STRENGTH,TrainingPhase.PEAK))
            for(includeOHP in listOf(false,true)) {
            val baseline=StarterProgramReleaseFixture.request(
                ComposerSplit.ULPPL,phase,includeIncline=true).copy(
                    nonSetSecondsPerDay=600)
            val source=if(includeOHP)baseline.copy(
                startingLoads=baseline.startingLoads+
                    ("barbell_ohp" to BigDecimal("95")))
            else baseline
            val result=CoachFirstProgramStartDomain.review(source)
            assertEquals("$phase: ${result.issues}",
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
            assertEquals(mapOf("barbell_bench" to 2,"back_squat" to 2),
                result.policy?.primaryLiftMinimumDays)
            val draft=checkNotNull(result.prescription)
            val inclineDays=draft.sessions.filter {session->
                session.exercises.any{it.exerciseId=="incline_db_press"}
            }
            assertTrue("Calibrated incline cannot silently be displaced: ${result.issues}",
                inclineDays.isNotEmpty())
            assertTrue(inclineDays.any{session->
                session.exercises.single{it.exerciseId=="incline_db_press"}
                    .sets.any{it.type==DraftSetType.WORKING}
            })
            assertTrue(checkNotNull(draft.finalAudit).satisfiesDeclaredHardConstraints)
            // Neither overhead pressing nor a different load convention may
            // replace the athlete's verified incline-dumbbell selection.
            val incline=source.catalog.single{it.exerciseId=="incline_db_press"}
            assertEquals(LoadBasis.DUMBBELL_PER_HAND,incline.expectedLoadBasis)
        }
    }

    @Test fun archivedV5PreservesSingleDayMinimumsAndOriginalPhaseSearch() {
        assertTrue(CoachFirstProgramStartDomain.supportsPolicy(
            CoachFirstProgramStartDomain.PREVIOUS_PHASE_POLICY_VERSION))
        assertEquals("first-program-reference-v6",
            CoachFirstProgramStartDomain.V6_POLICY_VERSION)
        assertEquals("first-program-reference-v7",
            CoachFirstProgramStartDomain.POLICY_VERSION)
        for(split in ComposerSplit.entries)
            for(phase in TrainingPhase.entries) {
            val archived=CoachFirstProgramStartDomain.review(
                StarterProgramReleaseFixture.request(split,phase).copy(
                    policyVersion=CoachFirstProgramStartDomain
                        .PREVIOUS_PHASE_POLICY_VERSION))
            assertEquals("$split/$phase: ${archived.issues}",
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,
                archived.status)
            assertEquals("v5",archived.policy?.revision)
            if(phase!=TrainingPhase.HYPERTROPHY) {
                assertTrue(checkNotNull(archived.policy)
                    .primaryLiftMinimumDays.values.all{it==1})
                assertTrue(archived.phasePoliciesChecked in 1..32)
                assertTrue(archived.issues.any{
                    it=="Main-lift practice and supporting days match your chosen split and verified movements."
                })
            }
        }
    }

    @Test fun archivedV4RetainsItsEightUnresolvedPhasesWithoutChangingSavedRules() {
        var oldReady=0
        for(split in ComposerSplit.entries)for(phase in TrainingPhase.entries) {
            val input=StarterProgramReleaseFixture.request(split,phase)
                .copy(policyVersion=CoachFirstProgramStartDomain.ALLOCATION_POLICY_VERSION)
            val old=CoachFirstProgramStartDomain.review(input)
            if(old.status==FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW)oldReady++
            assertEquals(if(phase==TrainingPhase.HYPERTROPHY)
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW
                else FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,old.status)
            assertEquals("v4",old.policy?.revision)
        }
        assertEquals(4,oldReady)
    }

    @Test fun phaseSearchCannotInventAnUncalibratedPrimaryOrRoundInvalidStock() {
        val source=StarterProgramReleaseFixture.request(ComposerSplit.UPPER_LOWER,
            TrainingPhase.STRENGTH)
        val noSquat=CoachFirstProgramStartDomain.review(source.copy(
            startingLoads=source.startingLoads-"back_squat"))
        assertEquals(setOf("barbell_bench"),noSquat.policy?.primaryLiftMinimumDays?.keys)
        assertTrue(noSquat.generation?.intents.orEmpty().none{
            it.requiredPrimaryExerciseId=="back_squat"
        })
        val bad=CoachFirstProgramStartDomain.review(source.copy(
            startingLoads=source.startingLoads+("barbell_bench" to BigDecimal("137.5"))))
        assertEquals(FirstProgramStartStatus.INVALID_CALIBRATION,bad.status)
        assertNull(bad.generation)
        assertNull(bad.prescription)
    }

    @Test fun overlappingMainLiftOptionsUseBoundedCompleteWeekAudits() {
        val base=StarterProgramReleaseFixture.request(ComposerSplit.UPPER_LOWER,
            TrainingPhase.STRENGTH)
        for(extra in listOf(mapOf("barbell_ohp" to BigDecimal("95")),
            mapOf("barbell_ohp" to BigDecimal("95"),"paused_bench" to BigDecimal("135")))) {
            val input=base.copy(startingLoads=base.startingLoads+extra)
            val result=CoachFirstProgramStartDomain.review(input)
            assertEquals(result.issues.toString(),
                FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,result.status)
            assertTrue(result.phasePoliciesChecked in 1..32)
            assertTrue(checkNotNull(result.prescription?.finalAudit).satisfiesDeclaredHardConstraints)
            assertTrue(checkNotNull(result.prescription?.finalAudit).daysOutsidePreferredWindow.isEmpty())
            assertTrue(checkNotNull(result.policy).primaryLiftMinimumDays.keys.all {
                it in input.startingLoads
            })
            for(session in checkNotNull(result.prescription).sessions) {
                assertTrue(session.exercises.count {exercise->
                    input.catalog.single{it.exerciseId==exercise.exerciseId}
                        .expectedLoadBasis==LoadBasis.BARBELL_TOTAL
                }<=1)
            }
            assertEquals(input.startingLoads.keys,result.chosenExerciseIds.toSet()-"bodyweight_crunch")
        }
    }

    @Test fun v4FindsInRangeFullBodyWithoutLoweringTheV3DoseOrChangingSavedV3() {
        val input=StarterProgramReleaseFixture.request(
            ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY)
        val old=CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.TIMED_POLICY_VERSION))
        assertEquals(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,old.status)
        val oldWeek=checkNotNull(old.generation?.candidate?.catalogReview?.draft)
        assertEquals(listOf(64L,73L,73L),oldWeek.audit.sessions.map{it.roundedMinutes})
        val current=CoachFirstProgramStartDomain.review(input)
        assertEquals(FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,current.status)
        assertEquals(old.policy?.selectedBounds,current.policy?.selectedBounds)
        assertEquals(old.policy?.primaryLiftMinimumDays,current.policy?.primaryLiftMinimumDays)
        assertEquals(old.unsupportedMuscles,current.unsupportedMuscles)
        assertEquals(listOf(56L,56L,57L),current.prescription?.sessions?.map{it.estimatedMinutes})
        assertEquals(old,CoachFirstProgramStartDomain.review(input.copy(
            policyVersion=CoachFirstProgramStartDomain.TIMED_POLICY_VERSION)))
    }
}

/** Audit output is deliberately NOT a release approval or a test pass count. */
object StarterProgramReleaseProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==1)
        fun quote(s:String)="\""+s.replace("\\","\\\\")
            .replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r")+"\""
        var reviewable=0
        val rows=ComposerSplit.entries.flatMap {split->TrainingPhase.entries.map {phase->
            val input=StarterProgramReleaseFixture.request(split,phase)
            val result=CoachFirstProgramStartDomain.review(input)
            if(result.status==FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW)reviewable++
            "{"+listOf("split" to quote(split.name),"phase" to quote(phase.name),
                "days" to checkNotNull(input.setup.reviewed).trainingWeekdays.size.toString(),
                "status" to quote(result.status.name),
                "phasePoliciesChecked" to result.phasePoliciesChecked.toString(),
                "phaseSearchBudgetReached" to result.phaseSearchBudgetReached.toString(),
                "purposeByWeekday" to result.generation?.intents.orEmpty()
                    .joinToString(prefix="{",postfix="}"){
                        quote(it.weekday.toString())+":"+quote(it.purpose.name)
                    },
                "namedPrimaryByWeekday" to result.generation?.intents.orEmpty()
                    .joinToString(prefix="{",postfix="}"){
                        quote(it.weekday.toString())+":"+
                            (it.requiredPrimaryExerciseId?.let(::quote) ?:"null")
                    },
                "primaryFrequency" to result.generation?.candidate?.realization
                    ?.primaryFrequencies.orEmpty().joinToString(prefix="[",postfix="]"){
                        "{\"exerciseId\":"+quote(it.exerciseId)+
                            ",\"requiredDays\":${it.requiredDays},\"deliveredDays\":${it.deliveredDays}}"
                    },
                "minutes" to result.prescription?.sessions.orEmpty()
                    .joinToString(prefix="[",postfix="]"){it.estimatedMinutes?.toString() ?:"null"},
                "unsupportedMuscles" to result.unsupportedMuscles
                    .joinToString(prefix="[",postfix="]"){quote(it.name)},
                "issues" to result.issues.joinToString(prefix="[",postfix="]"){quote(it)})
                .joinToString(","){(k,v)->quote(k)+":"+v}+"}"
        }}
        val output="{\"schemaVersion\":1,\"policyVersion\":"+
            quote(CoachFirstProgramStartDomain.POLICY_VERSION)+
            ",\"fixture\":\"advanced, 40–60 min, declared bar/dumbbell gym, one barbell exercise per day\""+
            ",\"totalCases\":${rows.size},\"reviewablePrograms\":$reviewable,"+
            "\"unresolvedCases\":${rows.size-reviewable},\"releaseApprovalGranted\":false,"+
            "\"cases\":["+rows.joinToString(",")+"]}"
        val file=Path.of(args.single())
        file.parent?.let{Files.createDirectories(it)}
        Files.writeString(file,output)
        println("Starter release audit: $reviewable/${rows.size} reviewable; "+
            "${rows.size-reviewable} unresolved. This is not release approval.")
    }
}
