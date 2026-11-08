package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.*
import org.junit.Test

/** Shipped catalog plus declared fictional calibration; no toy movement credits. */
internal object StarterProgramReleaseFixture {
    private fun n(value:String)=BigDecimal(value)
    fun request(split:ComposerSplit,phase:TrainingPhase):FirstProgramStartRequest {
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
            ),setOf("flat-bench","rack"),emptySet()),
            allowBodyweight=true,allowSupersets=false,allowTricepsOnLower=false,
            maxBarbellExercisesPerDay=1,focusedMuscles=emptySet(),
            reducedMuscles=emptySet(),excludedExerciseIds=emptySet(),
            lockedTrainingDays=emptySet(),hardTrainingDays=emptySet(),
            longTrainingDays=emptySet(),declaredRestDays=(0..6).toSet()-days))
        return FirstProgramStartRequest(setup,StarterExerciseTranche.movements(),
            mapOf("barbell_bench" to n("135"),"back_squat" to n("135"),
                "one_arm_db_row" to n("30"),"db_rdl" to n("30"),
                "db_lateral_raise" to n("15"),"db_curl" to n("25"),
                "db_overhead_triceps" to n("25"),"db_calf_raise" to n("30")),
            setOf("bodyweight_crunch"),phase,nonSetSecondsPerDay=900)
    }
}

class StarterProgramReleaseMatrixTest {
    @Test fun fourRealHypertrophySplitsPreserveDoseStockRecoveryAndWholeSessionTime() {
        for(split in ComposerSplit.entries) {
            val input=StarterProgramReleaseFixture.request(split,TrainingPhase.HYPERTROPHY)
            val result=CoachFirstProgramStartDomain.review(input)
            assertEquals("$split: ${result.issues}",
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
                    exercise.sets.forEach {set->
                        val load=set.load as? DraftSetLoad.Verified ?:return@forEach
                        val stock=input.setup.reviewed?.gym?.confirmedStock?.get(spec.equipmentId)
                        val checked=EquipmentLoadDomain.inspect(stock,load.value,load.unit)
                        assertTrue(checked is LoadInspection.Confirmed && checked.exact)
                    }
                }
            }
            // Known starter-library gaps remain visible, not waived by a
            // mechanical week passing its declared provisional policy.
            assertTrue(result.unsupportedMuscles.containsAll(setOf(
                TrainingMuscle.BRACHIALIS,TrainingMuscle.WRIST_FLEXORS,
                TrainingMuscle.WRIST_EXTENSORS)))
            assertFalse(result.mayIssueWorkout)
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
