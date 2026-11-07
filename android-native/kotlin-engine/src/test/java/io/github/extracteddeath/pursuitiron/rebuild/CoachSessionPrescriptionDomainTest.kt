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

    @Test fun finalRestIsNotCountedAsSessionTimeButTargetRemainsImmutable() {
        val policies=policies()
        val result=CoachSessionPrescriptionDomain.draft(request(),policies)
        val audit=checkNotNull(result.finalAudit)
        for(session in result.sessions) {
            val actualSets=session.exercises.flatMap {it.sets}
            assertTrue(actualSets.isNotEmpty())
            val sum=actualSets.sumOf {it.activeSeconds.toLong()+it.restSeconds}
            val last=actualSets.last()
            val window=request().days[session.dayIndex].window
            val expected=sum-last.restSeconds+window.declaredNonSetSeconds
            assertEquals(expected,audit.sessions[session.dayIndex].declaredSeconds)
            assertTrue(last.restSeconds>0)
            assertEquals(audit.sessions[session.dayIndex].roundedMinutes,
                session.estimatedMinutes)
        }
        // Rest targets remain part of the authored draft; not rewritten to
        // zero to fit a time bucket or misinterpreted as user observations.
        assertTrue(result.sessions.first().exercises.first().sets.any {
            it.restSeconds==240
        })
        assertFalse(result.canIssueWorkout)
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

    @Test fun strengthAndHypertrophyUseDistinctVerifiedPurposeLoadsAndBackoff() {
        val authored=policies().map {p->
            if(p.exerciseId!="db-press")p
            else if(p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("35"))
            else p.copy(purposeWorkingLoad=n("25"))
        }
        val draft=CoachSessionPrescriptionDomain.draft(request(),authored)
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,draft.status)
        val strength=draft.sessions.first().exercises.single {it.exerciseId=="db-press"}
        val top=strength.sets.filter {it.type==DraftSetType.WORKING}
        assertTrue(top.size>=2)
        assertTrue(top.dropLast(1).all {
            (it.load as DraftSetLoad.Verified).value==n("35")
        })
        assertEquals(n("30"),(top.last().load as DraftSetLoad.Verified).value)
        assertEquals(n("20"),(strength.sets.first().load as DraftSetLoad.Verified).value)
        val volume=draft.sessions.drop(1).flatMap {it.exercises}
            .filter {it.exerciseId=="db-press"}
        assertTrue(volume.isNotEmpty())
        assertTrue(volume.flatMap {it.sets}.all {
            (it.load as DraftSetLoad.Verified).value==n("25")
        })
        assertEquals(n("30"),(press.equipment as ComposerEquipment.Verified).plannedLoad)
        assertFalse(draft.canIssueWorkout)
    }

    @Test fun invalidPurposeSpecificLoadWarmupAndBodyweightRefuseTheDraft() {
        val invented=policies().map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("32.5")) else p
        }
        val invalid=CoachSessionPrescriptionDomain.draft(request(),invented)
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,invalid.status)
        assertNull(invalid.finalAudit)
        val tooHeavyWarmup=policies().map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("20")) else p
        }
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,
            CoachSessionPrescriptionDomain.draft(request(),tooHeavyWarmup).status)
        assertThrows(IllegalArgumentException::class.java) {
            CoachSessionPrescriptionDomain.draft(request(),
                policies().map {it.copy(purposeWorkingLoad=n("-5"))})
        }
        val body=press.copy(equipment=ComposerEquipment.Bodyweight)
        val bodyPolicy=policies().map {p ->
            if(p.exerciseId=="db-press")p.copy(
                scheme=SetLoadScheme.STRAIGHT,
                maximumAuthorizedBackoff=null,warmups=emptyList(),
                backoffReps=null,backoffRir=null,
                purposeWorkingLoad=n("25"))
            else p
        }
        val bodyDraft=CoachSessionPrescriptionDomain.draft(
            request(options=listOf(body,row)),bodyPolicy)
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,bodyDraft.status)
        assertNull(bodyDraft.finalAudit)
    }


    @Test fun samePurposeOnDifferentDaysUsesIndependentSetsRepsAndVerifiedWeights() {
        val baseline=policies()
        val independent=baseline+policy(
            "db-press",ComposerSessionPurpose.HYPERTROPHY,
            reps=RepBand(10,12),rir=3,rest=180).copy(
                purposeWorkingLoad=n("25"),dayIndex=2)
        val preview=CoachSessionPrescriptionDomain.draft(request(),independent)
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,preview.status)
        fun pressOn(day:Int)=preview.sessions.single {it.dayIndex==day}
            .exercises.single {it.exerciseId=="db-press"}
            .sets.filter {it.type==DraftSetType.WORKING}
        val upper=pressOn(1)
        val push=pressOn(2)
        assertTrue(upper.isNotEmpty() && push.isNotEmpty())
        assertTrue(upper.all {it.targetReps==RepBand(8,12) &&
            it.targetRir==2 && it.restSeconds==120 &&
            (it.load as DraftSetLoad.Verified).value==n("30")})
        assertTrue(push.all {it.targetReps==RepBand(10,12) &&
            it.targetRir==3 && it.restSeconds==180 &&
            (it.load as DraftSetLoad.Verified).value==n("25")})
        assertTrue(push.all {it.stableSetKey.startsWith("db-press:d2:s")})
        assertTrue(upper.all {it.stableSetKey.startsWith("db-press:d1:s")})
        assertFalse(preview.canIssueWorkout)
    }

    @Test fun sharedPurposeProgressionCannotClaimDayWithOwnPrescription() {
        val approved=request()
        val source=KotlinTrainingSourceRevision(
            "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
        fun completed(id:String)=ProgressionWorkoutEvidence(
            id,"db-press",LoadUnit.LB,RepBand(8,12),2,
            (0..1).map { ordinal ->
                WorkingSetFact(ordinal,SetOutcome.COMPLETED,n("30"),
                    12,3,n("30"),2)
            })
        val independentDay=policies().single {
            it.exerciseId=="db-press" &&
                it.purpose==ComposerSessionPurpose.HYPERTROPHY
        }.copy(dayIndex=2,purposeWorkingLoad=n("25"))
        val allPolicies=policies()+independentDay
        fun review(policies:List<ExercisePrescriptionPolicy>,day:Int?)=
            CoachNextProgramLoadReviewDomain.review(
                CoachNextProgramLoadRequest(
                    approved,source,source,7,7,
                    completed("earlier-day1"),completed("latest-day1"),
                    "db-press",policies,n("5"),CoachReadiness.NORMAL,false,
                    ComposerSessionPurpose.HYPERTROPHY,day
                ))
        val unsafe=review(allPolicies,null)
        assertEquals(
            CoachNextLoadReviewStatus.PURPOSE_SPECIFIC_LOADING_REQUIRES_SEPARATE_REVIEW,
            unsafe.status)
        assertNull(unsafe.candidateProgram)
        assertNull(unsafe.candidatePolicies)
        assertTrue(unsafe.affectedDayIndices.isEmpty())

        val scoped=review(allPolicies,1)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            scoped.status)
        assertEquals(listOf(1),scoped.affectedDayIndices)
        assertEquals(n("35"),scoped.candidateLoad)
        val updated=CoachSessionPrescriptionDomain.draft(
            checkNotNull(scoped.candidateProgram),
            checkNotNull(scoped.candidatePolicies))
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,updated.status)
        fun firstLoad(day:Int)=(
            updated.sessions.single {it.dayIndex==day}
                .exercises.single {it.exerciseId=="db-press"}
                .sets.first {it.type==DraftSetType.WORKING}.load
                as DraftSetLoad.Verified).value
        assertEquals(n("35"),firstLoad(1))
        assertEquals(n("25"),firstLoad(2))
        assertEquals(n("30"),(
            approved.exercises.single {it.exerciseId=="db-press"}
                .equipment as ComposerEquipment.Verified).plannedLoad)
        assertFalse(scoped.maySaveProgram)
        assertFalse(scoped.mayIssueWorkout)

        // Without day-specific overrides, a deliberately shared-purpose
        // change may legitimately affect BOTH matched training days.
        val broad=review(policies(),null)
        assertEquals(CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW,
            broad.status)
        assertEquals(listOf(1,2),broad.affectedDayIndices)
        val shared=CoachSessionPrescriptionDomain.draft(
            checkNotNull(broad.candidateProgram),
            checkNotNull(broad.candidatePolicies))
        assertTrue(shared.sessions.filter {
            it.purpose==ComposerSessionPurpose.HYPERTROPHY
        }.flatMap {it.exercises}.filter {it.exerciseId=="db-press"}
            .flatMap {it.sets}.filter {it.type==DraftSetType.WORKING}
            .all {(it.load as DraftSetLoad.Verified).value==n("35")})
    }

    @Test fun conflictingOrForgedDayScopedPolicyCannotProduceApprovedTargets() {
        val shared=policies()
        val specific=shared[1].copy(dayIndex=2,purposeWorkingLoad=n("25"))
        val illegal=shared+specific.copy(dayIndex=99)
        assertThrows(IllegalArgumentException::class.java) {
            CoachSessionPrescriptionDomain.draft(request(),illegal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoachSessionPrescriptionDomain.draft(request(),
                shared+specific+specific.copy(purposeWorkingLoad=n("35")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            CoachSessionPrescriptionDomain.draft(request(),shared+specific.copy(
                purpose=ComposerSessionPurpose.STRENGTH_SKILL))
        }
        val unstocked=CoachSessionPrescriptionDomain.draft(request(),
            shared+specific.copy(purposeWorkingLoad=n("32.5")))
        assertEquals(PrescriptionDraftStatus.UNRESOLVED,unstocked.status)
        assertNull(unstocked.finalAudit)
    }

}
