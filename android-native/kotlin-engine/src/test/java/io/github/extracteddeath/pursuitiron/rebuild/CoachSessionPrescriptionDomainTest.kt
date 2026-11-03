package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachSessionPrescriptionDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val loads=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30"),n("35")))
    private val chest=TrainingMuscle.CHEST
    private val lats=TrainingMuscle.LATS
    private fun bounds()=TrainingMuscle.entries.map {muscle->
        when(muscle) {
            chest,lats -> DeclaredMuscleDoseBounds(muscle,n("2"),n("8"),n("10"),1)
            else -> DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
        }
    }
    private fun option(id:String,muscle:TrainingMuscle,role:ComposerRole)=
        ComposerExerciseOption(id,
            if(muscle==chest)ComposerPattern.HORIZONTAL_PRESS
            else ComposerPattern.HORIZONTAL_PULL,
            setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("30"),loads),
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            90,120,2,3,3,2,1,2,role=role)
    private val press=option("db-press",chest,ComposerRole.PRIMARY)
    private val row=option("db-row",lats,ComposerRole.SECONDARY)
    private fun request(
        options:List<ComposerExerciseOption> = listOf(press,row),
        hard:Int?=null
    ):ComposerRequest {
        val day=DeclaredSessionWindow(1,120,hard,60)
        return ComposerRequest(
            ComposerSplit.FULL_BODY,TrainingPhase.STRENGTH,
            listOf(ComposerDay(0,ComposerFocus.FULL_BODY,day),
                ComposerDay(2,ComposerFocus.FULL_BODY,day),
                ComposerDay(4,ComposerFocus.FULL_BODY,day)),
            bounds(),options,3,1,
            listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,
                    "db-press"),
                ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
                ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY)
            ))
    }
    private val warm=DeclaredWarmupStep(n("20"),8,60,60)
    private fun policy(
        id:String,purpose:ComposerSessionPurpose,
        reps:RepBand=RepBand(8,12),
        rir:Int?=2,
        rest:Int=150,
        scheme:SetLoadScheme=SetLoadScheme.STRAIGHT,
        backoff:BigDecimal?=null,
        warmups:List<DeclaredWarmupStep> = emptyList(),
        backoffReps:RepBand?=null,
        backoffRir:Int?=null
    )=ExercisePrescriptionPolicy(id,purpose,reps,rir,90,rest,scheme,
        backoff,warmups,backoffReps,backoffRir)
    private fun policies()=listOf(
        policy("db-press",ComposerSessionPurpose.STRENGTH_SKILL,
            reps=RepBand(3,5),rir=3,rest=240,
            scheme=SetLoadScheme.LAST_SET_BACKOFF,
            backoff=n("5"),warmups=listOf(warm),
            backoffReps=RepBand(6,8),backoffRir=2),
        policy("db-press",ComposerSessionPurpose.HYPERTROPHY,
            reps=RepBand(8,12),rir=2,rest=120),
        policy("db-row",ComposerSessionPurpose.STRENGTH_SKILL,
            reps=RepBand(6,10),rir=2,rest=180),
        policy("db-row",ComposerSessionPurpose.HYPERTROPHY,
            reps=RepBand(10,15),rir=null,rest=120)
    )

    @Test fun goalSpecificDayUsesExplicitRepsRirRestAndPhysicalBackoff() {
        val report=CoachSessionPrescriptionDomain.draft(request(),policies())
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,report.status)
        assertNotNull(report.finalAudit)
        assertFalse(report.canIssueWorkout)
        val monday=report.sessions.first()
        assertEquals(ComposerSessionPurpose.STRENGTH_SKILL,monday.purpose)
        val primary=monday.exercises.first()
        assertEquals("db-press",primary.exerciseId)
        assertEquals(ComposerRole.PRIMARY,primary.role)
        val warmup=primary.sets.first()
        assertEquals(DraftSetType.WARMUP,warmup.type)
        assertEquals(n("20"),(warmup.load as DraftSetLoad.Verified).value)
        assertNull(warmup.targetRir)
        val sets=primary.sets.filter {it.type==DraftSetType.WORKING}
        assertTrue(sets.size>=2)
        assertTrue(sets.dropLast(1).all {
            it.targetReps==RepBand(3,5) && it.targetRir==3 &&
                it.restSeconds==240
        })
        assertEquals(240,sets.last().restSeconds)
        assertEquals(n("25"),(sets.last().load as DraftSetLoad.Verified).value)
        assertEquals(RepBand(6,8),sets.last().targetReps)
        assertEquals(2,sets.last().targetRir)
        assertTrue(sets.dropLast(1).all {
            (it.load as DraftSetLoad.Verified).value==n("30")
        })
        assertTrue(sets.all {it.stableSetKey.startsWith("db-press:d0:s")})
    }

    @Test fun hypertrophyDayCanHaveAnotherRepAndEffortStrategy() {
        val report=CoachSessionPrescriptionDomain.draft(request(),policies())
        val found=report.sessions.drop(1).flatMap {it.exercises}
        assertTrue(found.isNotEmpty())
        assertTrue(found.all {ex ->
            ex.sets.filter {it.type==DraftSetType.WORKING}.all {s ->
                if(ex.exerciseId=="db-press")
                    s.targetReps==RepBand(8,12) && s.targetRir==2
                else s.targetReps==RepBand(10,15) && s.targetRir==null
            }
        })
    }

    @Test fun noExtraWarmupMuscleVolumeIsInventedByPrescription() {
        val report=CoachSessionPrescriptionDomain.draft(request(),policies())
        val audit=checkNotNull(report.finalAudit)
        for(muscle in listOf(chest,lats)) {
            assertEquals(report.composer.audit.muscles[muscle]?.effectiveWorkingSets,
                audit.muscles[muscle]?.effectiveWorkingSets)
        }
        assertTrue(report.sessions.flatMap {it.exercises}.flatMap {it.sets}
            .any {it.type==DraftSetType.WARMUP})
    }

    @Test fun newSessionTimeIsAuditedAfterRestAndWarmupsNotCopiedFromComposer() {
        val report=CoachSessionPrescriptionDomain.draft(request(),policies())
        val first=checkNotNull(report.finalAudit).sessions.first()
        assertEquals(first.roundedMinutes,report.sessions.first().estimatedMinutes)
        assertTrue(first.declaredSeconds>
            report.composer.audit.sessions.first().declaredSeconds)
    }

    @Test fun changedRestCanBreakHardTimeLimitEvenWhenComposerPassed() {
        val long=policies().map {
            it.copy(restSecondsPerSet=3600)
        }
        val report=CoachSessionPrescriptionDomain.draft(request(hard=30),long)
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,report.status)
        assertTrue(report.composer.audit.satisfiesDeclaredHardConstraints)
        assertNotNull(report.finalAudit)
        assertFalse(checkNotNull(report.finalAudit).satisfiesDeclaredHardConstraints)
        assertTrue(report.reviewNotes.any {it.contains("hard time")})
    }

    @Test fun noArbitraryBackoffIfStockOrApprovedStepDoesNotSupportIt() {
        val tooSmall=policies().map {
            if(it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                it.copy(maximumAuthorizedBackoff=n("2"))
            else it
        }
        val result=CoachSessionPrescriptionDomain.draft(request(),tooSmall)
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,result.status)
        assertNull(result.finalAudit)
        assertTrue(result.unresolvedExerciseVisits.any {
            it.contains("db-press:d0:unverified-scheme")
        })
    }

    @Test fun historicalPracticalRepEnvelopeRejectsImpossibleWorkingAndBackoffPolicy() {
        val sourced=press.copy(sourcePracticalReps=RepBand(5,12))
        val exerciseRequest=request(options=listOf(sourced,row))
        val below=CoachSessionPrescriptionDomain.draft(exerciseRequest,policies())
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,below.status)
        assertNull(below.finalAudit)
        assertTrue(below.unresolvedExerciseVisits.any {
            it=="db-press:d0:source-rep-envelope"
        })
        val adjusted=policies().map {policy->
            if(policy.exerciseId=="db-press" &&
                policy.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                policy.copy(workingReps=RepBand(5,6)) else policy
        }
        val inside=CoachSessionPrescriptionDomain.draft(exerciseRequest,adjusted)
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,inside.status)
        val brokenBackoff=adjusted.map {policy->
            if(policy.exerciseId=="db-press" &&
                policy.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                policy.copy(backoffReps=RepBand(2,4)) else policy
        }
        val invalid=CoachSessionPrescriptionDomain.draft(
            exerciseRequest,brokenBackoff)
        assertTrue(invalid.unresolvedExerciseVisits.any {
            it=="db-press:d0:source-rep-envelope"
        })
        assertNull(invalid.finalAudit)
        assertFalse(invalid.canIssueWorkout)
    }

    @Test fun unverifiedWarmupWeightFailsClosedWithoutRounding() {
        val invalid=policies().map {
            if(it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                it.copy(warmups=listOf(warm.copy(load=n("22.5"))))
            else it
        }
        val report=CoachSessionPrescriptionDomain.draft(request(),invalid)
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,report.status)
        assertNull(report.finalAudit)
    }

    @Test fun missingPurposeSpecificPolicyDoesNotBorrowAnotherDayPolicy() {
        val available=policies().filterNot {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        }
        val report=CoachSessionPrescriptionDomain.draft(request(),available)
        assertNull(report.finalAudit)
        assertTrue(report.unresolvedExerciseVisits.any {
            it.contains("missing-policy")
        })
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,report.status)
    }

    @Test fun bodyweightIsExplicitNotLoggedAsZeroPounds() {
        val body=press.copy(equipment=ComposerEquipment.Bodyweight)
        val straight=policies().map {
            if(it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                it.copy(scheme=SetLoadScheme.STRAIGHT,
                    maximumAuthorizedBackoff=null,warmups=emptyList(),
                    backoffReps=null,backoffRir=null)
            else it
        }
        val valid=CoachSessionPrescriptionDomain.draft(
            request(options=listOf(body,row)),straight)
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,valid.status)
        val top=valid.sessions.first().exercises.first().sets.first()
        assertEquals(DraftSetLoad.ExplicitBodyweight,top.load)
        val invalid=CoachSessionPrescriptionDomain.draft(
            request(options=listOf(body,row)),policies())
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,invalid.status)
        assertNull(invalid.finalAudit)
    }

    @Test fun policiesWithFalseRirDuplicateKeysOrIllegalWarmupFailValidation() {
        val bad=listOf(
            policies()+policies().first(),
            policies().map {it.copy(targetRir=11)},
            policies().map {it.copy(activeSecondsPerSet=0)},
            policies().map {it.copy(warmups=listOf(warm.copy(reps=0)))},
            policies().map {it.copy(warmups=listOf(warm.copy(load=n("-5"))))},
            policies().map {it.copy(
                scheme=SetLoadScheme.LAST_SET_BACKOFF,
                maximumAuthorizedBackoff=null)},
            policies().map {it.copy(
                scheme=SetLoadScheme.LAST_SET_BACKOFF,backoffReps=null)},
            policies().map {it.copy(backoffRir=11)},
            policies().map {it.copy(scheme=SetLoadScheme.STRAIGHT,
                backoffReps=RepBand(6,8))}
        )
        bad.forEach {p->
            assertThrows(IllegalArgumentException::class.java) {
                CoachSessionPrescriptionDomain.draft(request(),p)
            }
        }
    }

    @Test fun resultsCannotBeMutatedAndUserAuthoredInputsArePreserved() {
        val original=policies()
        val draft=CoachSessionPrescriptionDomain.draft(request(),original)
        assertEquals(n("30"),(press.equipment as ComposerEquipment.Verified).plannedLoad)
        assertEquals(3,original.first().targetRir)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val writable=draft.sessions as MutableList<DraftPrescribedSession>
            writable.clear()
        }
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val writable=draft.sessions.first().exercises.first().sets
                as MutableList<DraftPrescribedSet>
            writable.clear()
        }
    }

    @Test fun identicalPolicyInputProducesSameDraftAndStableIds() {
        val a=CoachSessionPrescriptionDomain.draft(request(),policies())
        val b=CoachSessionPrescriptionDomain.draft(request(),policies())
        assertEquals(a,b)
        val ids=a.sessions.flatMap {it.exercises}.flatMap {it.sets}
            .map {it.stableSetKey}
        assertEquals(ids.size,ids.distinct().size)
    }
}
