package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinShorterSessionReviewDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",1,"program-1",1,"gym-1",1,"catalog-1","rules-1")
    private val inventory=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
    private val press=ComposerExerciseOption(
        "press",ComposerPattern.HORIZONTAL_PRESS,
        setOf(ComposerFocus.FULL_BODY),
        ComposerEquipment.Verified(LoadUnit.LB,n("30"),inventory),
        listOf(DeclaredMuscleCredit(TrainingMuscle.CHEST,MuscleCreditRole.DIRECT,n("1"))),
        120,120,2,4,3,0,1,1,role=ComposerRole.PRIMARY)
    private val row=ComposerExerciseOption(
        "row",ComposerPattern.HORIZONTAL_PULL,
        setOf(ComposerFocus.FULL_BODY),
        ComposerEquipment.Verified(LoadUnit.LB,n("30"),inventory),
        listOf(DeclaredMuscleCredit(TrainingMuscle.LATS,MuscleCreditRole.DIRECT,n("1"))),
        120,120,2,4,3,0,1,1,role=ComposerRole.SECONDARY)
    private val curl=ComposerExerciseOption(
        "curl",ComposerPattern.ISOLATION,
        setOf(ComposerFocus.FULL_BODY),
        ComposerEquipment.Verified(LoadUnit.LB,n("30"),inventory),
        listOf(DeclaredMuscleCredit(TrainingMuscle.BICEPS,MuscleCreditRole.DIRECT,n("1"))),
        120,120,2,4,3,0,1,1,role=ComposerRole.ACCESSORY)

    private fun program(
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        options:List<ComposerExerciseOption> =listOf(press,row,curl),
        overhead:Int=300
    ):ComposerRequest {
        val bounds=TrainingMuscle.entries.map {
            DeclaredMuscleDoseBounds(it,n("0"),n("0"),n("0"),0)
        }
        return ComposerRequest(
            ComposerSplit.FULL_BODY,phase,
            listOf(0,2,4).map {day ->
                ComposerDay(day,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,120,null,overhead))
            },bounds,options,4,1)
    }
    private fun set(exercise:String,role:ComposerRole,ordinal:Int,
        type:DraftSetType=DraftSetType.WORKING,load:String="30",
        reps:RepBand=RepBand(8,12),rir:Int?=2,rest:Int=120,
        active:Int=120)=KotlinWorkoutTarget(
            exercise,role,ComposerSessionPurpose.HYPERTROPHY,
            "$exercise:d0:${if(type==DraftSetType.WORKING)"s" else "w"}$ordinal",
            type,ordinal,DraftSetLoad.Verified(
                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n(load)),
            reps,rir,active,rest
        )
    private fun snapshot(
        main:Int=4,companion:Int=4,optional:Int=4,
        warmups:Int=0,
        purpose:ComposerSessionPurpose=ComposerSessionPurpose.HYPERTROPHY,
        varyingAccessory:Boolean=false
    ):KotlinStagedWorkout {
        val targets=buildList {
            repeat(warmups) {i->add(set("press",ComposerRole.PRIMARY,i,
                DraftSetType.WARMUP,load="20",reps=RepBand(8,8),
                rir=null,rest=60,active=60))}
            repeat(main){i->add(set("press",ComposerRole.PRIMARY,i))}
            repeat(companion){i->add(set("row",ComposerRole.SECONDARY,i))}
            repeat(optional){i->add(set("curl",ComposerRole.ACCESSORY,i,
                load=if(varyingAccessory && i==optional-1)"25" else "30"))}
        }
        val elapsed=300+targets.sumOf {it.activeSeconds+it.restSeconds} -
            (targets.lastOrNull()?.restSeconds ?:0)
        return KotlinStagedWorkout(
            source,"grant-1","workout-1",0,0,purpose,
            (elapsed.toLong()+59L)/60L,targets)
    }

    @Test fun shorterHypertrophySessionDropsOptionalExtrasBeforeKeyWork() {
        val stage=snapshot()
        val plan=program()
        val full=stage.targets.map {it.setKey}
        val at45=KotlinShorterSessionReviewDomain.inspectStaged(stage,plan,45)
        assertEquals(KotlinShortSessionStatus.READY_FOR_ATHLETE_REVIEW,at45.status)
        assertTrue(checkNotNull(at45.estimatedSeconds)<=45L*60L)
        assertTrue(checkNotNull(at45.originalEstimatedSeconds)>45L*60L)
        assertTrue(at45.omittedSetKeys.isNotEmpty())
        assertTrue(at45.omittedSetKeys.all { it.startsWith("curl:") })
        assertEquals(4,stage.targets.count {it.exerciseId=="press"})
        assertEquals(full,stage.targets.map {it.setKey})
        assertTrue(at45.keptSetKeys.all {it in full})
        assertEquals(full.size-at45.keptSetKeys.size,at45.omittedSetKeys.size)
        assertFalse(at45.canStartWorkout)
        assertFalse(at45.canModifyJournal)
        assertFalse(at45.canSaveProgram)
    }

    @Test fun twentyMinuteSessionKeepsTwoKeyAndCompanionSetsAndOriginalOrder() {
        val stage=snapshot(warmups=2)
        val reviewed=KotlinShorterSessionReviewDomain.inspectStaged(
            stage,program(overhead=300),20)
        assertEquals(KotlinShortSessionStatus.READY_FOR_ATHLETE_REVIEW,reviewed.status)
        assertTrue(checkNotNull(reviewed.estimatedSeconds)<=1200L)
        val targets=stage.targets.filter {it.setKey in reviewed.keptSetKeys}
        assertEquals(2,targets.count {it.type==DraftSetType.WARMUP &&
            it.exerciseId=="press"})
        assertEquals(2,targets.count {it.type==DraftSetType.WORKING &&
            it.exerciseId=="press"})
        assertEquals(2,targets.count {it.type==DraftSetType.WORKING &&
            it.exerciseId=="row"})
        assertTrue(targets.none {it.exerciseId=="curl"})
        assertEquals(listOf("curl"),reviewed.omittedExerciseIds)
        assertEquals(targets.map {it.setKey},reviewed.keptSetKeys)
        assertTrue(targets.all {it.targetRir==2 ||
            it.type==DraftSetType.WARMUP})
        assertFalse(stage.persisted)
    }

    @Test fun protectedStrengthAndPinnedMovementsBlockImpossibleShortcuts() {
        val stage=snapshot()
        val strength=KotlinShorterSessionReviewDomain.inspectStaged(
            stage,program(phase=TrainingPhase.STRENGTH),20)
        assertEquals(KotlinShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT,
            strength.status)
        assertTrue(checkNotNull(strength.estimatedSeconds)>1200L)
        assertTrue(strength.keptSetKeys.containsAll(stage.targets.filter {
            it.exerciseId=="press" || it.exerciseId=="row"
        }.map {it.setKey}))
        val pinned=KotlinShorterSessionReviewDomain.inspectStaged(
            stage,program(options=listOf(press,row,curl.copy(requiredVisits=1))),20)
        assertEquals(KotlinShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT,
            pinned.status)
        assertTrue(pinned.keptSetKeys.containsAll(
            stage.targets.filter{it.exerciseId=="curl"}.map{it.setKey}))
        val skill=KotlinShorterSessionReviewDomain.inspectStaged(
            snapshot(purpose=ComposerSessionPurpose.STRENGTH_SKILL),
            program(),20)
        assertEquals(KotlinShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT,
            skill.status)
        assertFalse(skill.canStartWorkout)
    }

    @Test fun distinctLastSetBackoffIsNotSilentlyConvertedToStraightSets() {
        val stage=snapshot(varyingAccessory=true)
        val result=KotlinShorterSessionReviewDomain.inspectStaged(stage,program(),45)
        val originalAccessory=stage.targets.filter {it.exerciseId=="curl"}
        val remainingAccessory=originalAccessory.filter {
            it.setKey in result.keptSetKeys
        }
        // It can be retained intact or dropped as one entire optional
        // exercise, but not truncated into a different set-loading scheme.
        assertTrue(remainingAccessory.isEmpty() ||
            remainingAccessory==originalAccessory)
        assertEquals(n("25"),
            (stage.targets.last().load as DraftSetLoad.Verified).value)
    }

    @Test fun fullAlreadyFitsDoesNotCreateAnEditedWorkout() {
        val short=snapshot(main=2,companion=2,optional=0)
        val review=KotlinShorterSessionReviewDomain.inspectStaged(short,program(),20)
        assertEquals(KotlinShortSessionStatus.ALREADY_FITS,review.status)
        assertEquals(short.targets.map {it.setKey},review.keptSetKeys)
        assertTrue(review.omittedSetKeys.isEmpty())
        assertFalse(review.changesPrescription)
        assertEquals(0,review.omittedWorkingSets)
    }

    @Test fun reviewMustStageCurrentAuthenticatedSourceRatherThanTrustingPreviewKeys() {
        val program=program()
        val stale=KotlinWorkoutSnapshotRequest(
            source,source.copy(gymRevision=5),
            KotlinWorkoutSelectionGrant("grant-1","workout-1",source,0),
            program,emptyList()
        )
        val rejected=KotlinShorterSessionReviewDomain.review(stale,20)
        assertEquals(KotlinShortSessionStatus.SOURCE_OR_PROGRAM_UNRESOLVED,rejected.status)
        assertNull(rejected.estimatedSeconds)
        assertTrue(rejected.keptSetKeys.isEmpty())
        assertFalse(rejected.canStartWorkout)
        assertEquals(KotlinShortSessionStatus.INVALID_LIMIT,
            KotlinShorterSessionReviewDomain.review(stale,32).status)
    }

    @Test fun timeAllowanceIsNeverFalselyReducedByOriginalRoundedDuration() {
        val stage=snapshot(main=4,companion=4,optional=4)
            .copy(estimatedMinutes=75)
        val result=KotlinShorterSessionReviewDomain.inspectStaged(stage,program(),45)
        assertTrue(checkNotNull(result.originalEstimatedSeconds)>=75L*60L)
        // Trimming cannot claim 45 minutes by discarding explicitly stated
        // setup and preparation work.
        assertEquals(KotlinShortSessionStatus.PROTECTED_WORK_EXCEEDS_LIMIT,
            result.status)
    }
}
