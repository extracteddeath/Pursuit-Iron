package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Uses the shipped starter catalog and first-program policy, not an authored
 * one-exercise cycle. Every cold reader uses the real native file owners. */
class NativeKotlinGeneratedProgramJourneyTest {
    @get:Rule val temp=TemporaryFolder()
    private val owner="owner-1"
    private val athlete="athlete-1"
    private val cycleId="generated-first"
    private val identity=NativeKotlinCurrentIdentity {
        NativeKotlinOwnerIdentity(owner,athlete)
    }
    private fun n(value:String)=BigDecimal(value)
    private val loads=mapOf(
        "barbell_bench" to n("135"),"back_squat" to n("135"),
        "one_arm_db_row" to n("30"),"db_rdl" to n("30"),
        "db_lateral_raise" to n("15"),"db_curl" to n("25"),
        "db_overhead_triceps" to n("25"),"db_calf_raise" to n("30"))
    private val bodyweight=setOf("bodyweight_crunch")
    private fun setup()=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="profile-1",athleteProfileId=athlete,
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,split=ComposerSplit.UPPER_LOWER,
        trainingWeekdays=setOf(0,1,3,4),timezoneId="America/New_York",
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
        longTrainingDays=emptySet(),declaredRestDays=setOf(2,5,6)))
    private val catalog=StarterExerciseTranche.movements()
    private fun request()=FirstProgramStartRequest(
        setup(),catalog,loads,bodyweight,TrainingPhase.HYPERTROPHY,
        nonSetSecondsPerDay=900)

    private inner class Disk {
        val root=temp.root.toPath()
        val fence=NativeKotlinApprovalRevisionFence(root)
        val drafts=NativeCustomCycleDraftFileStore(root,fence,identity)
        val sources=NativeKotlinCustomAuthoringSourceFileStore(root,fence,identity)
        val consent=NativeKotlinCustomCycleSignoffFileStore(
            root,fence,identity,drafts,sources)
        val preflight=NativeKotlinCustomWorkoutPreflight(
            fence,identity,drafts,sources,consent)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(
            root,fence,identity,preflight)
        val history=NativeKotlinHistoryManifestFileStore(root)
        val composer=KotlinNativeJournalFileStore(root)
        val journals=NativeKotlinCustomWorkoutJournalFileStore(
            root,fence,identity,prepared,preflight,history,composer)
        val corrections=NativeKotlinCustomSavedCorrectionFileStore(
            root,fence,identity,journals,history)
        val authority=object:NativeKotlinWorkoutAuthority {
            override fun currentApprovedWorkout(ownerKey:String,workoutId:String)
                :NativeKotlinWorkoutApproval?=null
            override fun canAccessIssuedWorkout(ownerKey:String,
                source:KotlinTrainingSourceRevision)=false
        }
        val timeline=NativeKotlinMixedHistoryReadCoordinator(
            history,composer,KotlinNativeSavedCorrectionFileStore(root,composer),
            journals,authority,identity,fence,corrections)
        fun next(inputs:CustomCoachAuditInputs)=NativeKotlinCustomNextCycleSessionReview(
            fence,identity,drafts,sources,consent,timeline,
            NativeKotlinCustomOutstandingWorkoutInventory(prepared,journals),prepared
        ).review(owner,cycleId,inputs)
    }

    private fun saveGenerated(
        policyVersion:String=CoachFirstProgramStartDomain.POLICY_VERSION
    ):Pair<CustomTrainingCycle,CustomCoachAuditInputs> {
        val input=request().copy(policyVersion=policyVersion)
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,input.setup.status)
        val generated=CoachFirstProgramStartDomain.review(input)
        val draft=generated.generation?.candidate?.catalogReview?.draft
        assertEquals(generated.issues.joinToString(" | ")+"; sessions="+draft?.audit?.sessions+
            "; unmet muscles="+draft?.audit?.muscles?.values?.filter {
                it.finding!=DoseFinding.WITHIN_BOUNDS
            }+
            "; explanation="+draft?.explanations,
            FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW,generated.status)
        val proposed=CoachFirstProgramDraftConversionDomain.propose(
            generated,input.setup,catalog,cycleId,"Four day first program",4)
        assertEquals(proposed.reason,FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT,
            proposed.status)
        val cycle=checkNotNull(proposed.cycle)
        val inputs=checkNotNull(CoachFirstProgramDraftConversionDomain
            .policyForInitialFullCycleReview(generated,input.setup))
        val disk=Disk()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            disk.sources.publish(owner,0,NativeCustomAuthoringSnapshot(
                owner,athlete,1,input.setup,catalog)))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),
            disk.drafts.save(owner,cycleId,0,cycle))
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            NativeKotlinFirstProgramSeedFileStore(disk.root,disk.fence,identity)
                .create(owner,NativeFirstProgramSeed(
                    cycleId,cycle.title,4,1,input.policyVersion,
                    TrainingPhase.HYPERTROPHY,loads,bodyweight,emptyList(),900)))
        assertTrue(disk.consent.confirm(owner,cycleId,1,1,0,inputs,true)
            is NativeCustomSignoffWrite.Committed)
        val enrollment=NativeKotlinHistoryEnrollmentCoordinator(
            disk.composer,KotlinNativeSavedCorrectionFileStore(disk.root,disk.composer),
            disk.history,disk.fence,disk.authority).enrollSeparateLocalHistory(
                owner,athlete,true,
                authenticateOwner={o,a->
                    identity.signedInIdentity()==NativeKotlinOwnerIdentity(o,a)
                },verifyIndependentLocalOwner={o,a->
                    identity.signedInIdentity()==NativeKotlinOwnerIdentity(o,a)
                },customInventory=NativeKotlinCustomOutstandingWorkoutInventory(
                    disk.prepared,disk.journals))
        assertTrue(enrollment.toString(),enrollment is NativeHistoryEnrollmentResult.Enrolled)
        return cycle to inputs
    }

    @Test fun previousPolicyReproducesTheCompoundOmissionForTheSameRealCatalog() {
        val legacy=CoachFirstProgramStartDomain.review(request().copy(
            policyVersion=CoachFirstProgramStartDomain.LEGACY_POLICY_VERSION))
        assertEquals(FirstProgramStartStatus.DRAFT_NEEDS_REVIEW,legacy.status)
        val bounds=checkNotNull(legacy.policy).selectedBounds
        assertEquals(n("0"),bounds.single{it.muscle==TrainingMuscle.FRONT_DELTS}.mrv)
        val doses=checkNotNull(legacy.generation?.candidate?.catalogReview?.draft)
            .audit.muscles
        assertEquals(n("0"),doses.getValue(TrainingMuscle.CHEST).directWorkingSets)
        assertEquals(n("0"),doses.getValue(TrainingMuscle.QUADS).directWorkingSets)
    }

    @Test fun savedV2ProgramColdReopensWithoutBeingRegeneratedWithV3Timing() {
        val (cycle,inputs)=saveGenerated(CoachFirstProgramStartDomain.PREVIOUS_POLICY_VERSION)
        val disk=Disk()
        val seed=(NativeKotlinFirstProgramSeedFileStore(disk.root,disk.fence,identity)
            .read(owner,cycleId) as NativeFirstProgramSeedRead.Found).seed
        assertEquals("first-program-reference-v2",seed.policyVersion)
        val replay=CoachFirstProgramStartDomain.review(request().copy(
            startingLoads=seed.startingLoads,
            selectedBodyweightExerciseIds=seed.bodyweightExercises,
            nonSetSecondsPerDay=seed.nonSetSeconds,policyVersion=seed.policyVersion))
        assertTrue(checkNotNull(replay.generation?.candidate?.catalogReview?.composedRequest)
            .visitTimings.isEmpty())
        assertEquals(cycle,CoachFirstProgramDraftConversionDomain.propose(
            replay,setup(),catalog,cycleId,seed.title,seed.blockWeeks).cycle)
        // Frozen from execution of the previously published v2 policy,
        // before visit-aware timing was added to the composer.
        val frozen=checkNotNull(CoachFirstProgramDraftConversionDomain.propose(
            replay,setup(),catalog,"frozen-first","My first program",4).cycle)
        assertEquals("be2fcc9b5d97f0697085708efa8afbecd66437d9f3642b000d97d53be16c0e54",
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(NativeCustomCycleDraftCodec.encode(frozen))
                .joinToString(""){"%02x".format(it)})
        val day=cycle.blocks.single().days.first()
        val expected=(disk.preflight.inspect(owner,cycleId,1,day.weekday,inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        assertTrue(disk.prepared.prepare(owner,"old-first-day",cycleId,1,
            day.weekday,inputs) is NativeCustomPrepareResult.Prepared)
        val issued=(Disk().journals.issue(owner,"old-first-day",cycleId,1,
            day.weekday,inputs) as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(expected,issued.snapshot)
    }

    @Test fun generatedFourDayProgramCompletesAllSetsAndColdReturnsToNextWeek() {
        val (cycle,inputs)=saveGenerated()
        val days=cycle.blocks.single().days.sortedBy{it.weekday}
        assertEquals(listOf(0,1,3,4),days.map{it.weekday})
        assertTrue(days.all{it.slots.size>=3})
        val selected=days.flatMap{it.slots}.map{it.exerciseId}.toSet()
        assertTrue(selected.containsAll(setOf("barbell_bench","back_squat",
            "one_arm_db_row","db_rdl")))
        assertTrue(days.flatMap{it.slots}.flatMap{it.sets}
            .any{it.type==DraftSetType.WARMUP})
        val completed=mutableListOf<String>()
        for((index,day) in days.withIndex()) {
            val id="first-day-$index"
            assertEquals(NativeCustomNextCycleSlot.Next(1,day.weekday,day.label),
                Disk().next(inputs))
            val preparation=Disk().prepared.prepare(owner,id,cycleId,1,
                day.weekday,inputs) as NativeCustomPrepareResult.Prepared
            // A cold reopen must prove the entire snapshot and exact consent,
            // including warmups, all working sets, rest and intended RIR.
            val reopened=Disk().prepared.open(owner,id)
                as NativeCustomPreparedRead.Found
            assertEquals(preparation.workout,reopened.workout)
            val fresh=Disk().preflight.inspect(owner,cycleId,1,day.weekday,inputs)
                as NativeCustomPreflightResult.ReviewedPreview
            assertEquals(reopened.workout.snapshot,fresh.session)
            assertEquals(reopened.workout.consentFingerprint,
                fresh.receipt.fingerprintSha256)
            assertEquals(reopened.workout.consentRevision,fresh.receipt.recordRevision)
            assertEquals(NativeCustomNextCycleSlot.Prepared(id,1,day.weekday,day.label),
                Disk().next(inputs))
            var journal=(Disk().journals.issue(owner,id,cycleId,1,day.weekday,inputs)
                as NativeCustomJournalResult.Durable).issued.journal
            val original=journal.snapshot
            val allSets=original.slots.flatMap{slot->slot.sets}
            assertTrue(allSets.size>6)
            for((setIndex,set) in allSets.withIndex()) {
                val prescription=set.prescription
                val actual=when(val load=prescription.load) {
                    is DraftSetLoad.Verified -> KotlinPerformedSet(
                        load.value,load.unit,prescription.reps.minimum,null)
                    DraftSetLoad.ExplicitBodyweight -> KotlinPerformedSet(
                        null,null,prescription.reps.minimum,null)
                }
                val operation=CustomWorkoutJournalOperation.Record(
                    "record-$index-$setIndex",journal.revision,
                    CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,actual)
                journal=(Disk().journals.record(owner,id,operation)
                    as NativeCustomJournalResult.Durable).issued.journal
                if(setIndex==0) {
                    // Correct an actual entry and lose/retry its acknowledgment.
                    val edit=operation.copy(eventId="correct-$index",
                        expectedRevision=journal.revision,
                        performed=actual.copy(reps=actual.reps+1),
                        correctsEventId=operation.eventId)
                    val saved=Disk().journals.record(owner,id,edit)
                        as NativeCustomJournalResult.Durable
                    val retry=Disk().journals.record(owner,id,edit)
                        as NativeCustomJournalResult.Durable
                    assertEquals(saved.issued,retry.issued)
                    journal=retry.issued.journal
                }
                assertEquals(original,journal.snapshot)
            }
            assertEquals(0,journal.unresolvedVisitCount)
            assertEquals(NativeCustomNextCycleSlot.ResumeIssued(id,1,day.weekday,day.label),
                Disk().next(inputs))
            val finish=CustomWorkoutJournalOperation.Finish("finish-$index",journal.revision)
            fun finishAgain():NativeCustomFinishResult {
                val d=Disk()
                return NativeKotlinCustomHistoryTransaction(d.history,d.journals,
                    d.fence,identity).complete(owner,athlete,id,finish)
            }
            assertTrue(finishAgain() is NativeCustomFinishResult.Committed)
            assertTrue(finishAgain() is NativeCustomFinishResult.Committed)
            completed+=id
            val ledger=(Disk().history.open(owner,athlete)
                as NativeHistoryLedgerResult.Durable).ledger
            assertEquals(completed,ledger.completions.map{it.workoutId})
            val finished=(Disk().journals.open(owner,id)
                as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(original,finished.snapshot)
            assertEquals(allSets.size,finished.effectiveVisits.size)
            assertTrue(finished.effectiveVisits.values.all {
                (it.operation as CustomWorkoutJournalOperation.Record)
                    .performed?.actualRir==null
            })
        }
        assertEquals(NativeCustomNextCycleSlot.Next(2,days.first().weekday,
            days.first().label),Disk().next(inputs))
        assertEquals(cycle,(Disk().drafts.read(owner,cycleId)
            as NativeCustomDraftReadResult.Found).draft)
        val seed=(NativeKotlinFirstProgramSeedFileStore(
            temp.root.toPath(),Disk().fence,identity).read(owner,cycleId)
            as NativeFirstProgramSeedRead.Found).seed
        val regenerated=CoachFirstProgramStartDomain.review(request().copy(
            startingLoads=seed.startingLoads,
            selectedBodyweightExerciseIds=seed.bodyweightExercises,
            nonSetSecondsPerDay=seed.nonSetSeconds,policyVersion=seed.policyVersion))
        assertEquals(cycle,CoachFirstProgramDraftConversionDomain.propose(
            regenerated,setup(),catalog,cycleId,seed.title,seed.blockWeeks).cycle)
        // Editing the first finished workout cannot reorder newer results
        // or rewind next-session selection. Preserve the issued original.
        val firstId=completed.first()
        val d=Disk()
        val original=(d.journals.open(owner,firstId)
            as NativeCustomJournalResult.Durable).issued.journal
        val working=original.snapshot.slots.flatMap{it.sets}
            .first{it.prescription.type==DraftSetType.WORKING}
        val visit=CustomWorkoutVisitKey(working.setKey,CustomWorkoutVisitPart.MAIN)
        val old=original.effectiveVisits.getValue(visit).operation
            as CustomWorkoutJournalOperation.Record
        val actual=checkNotNull(old.performed)
        val edit=CustomSavedVisitCorrection("saved-first-edit",0,visit,
            old.eventId,KotlinJournalSetOutcome.COMPLETED,
            actual.copy(reps=actual.reps+1))
        val saved=NativeKotlinCustomHistoryCorrectionTransaction(
            d.history,d.journals,d.corrections,d.fence,identity)
            .correct(owner,athlete,firstId,edit)
        assertTrue(saved.toString(),saved is NativeCustomCorrectionTransactionResult.Committed)
        val timeline=(Disk().timeline.read(owner,athlete)
            as NativeKotlinMixedHistoryReadResult.Review).timeline
        assertEquals(completed,timeline.completions.map{it.manifest.workoutId})
        val corrected=timeline.completions.first()
            as NativeKotlinMixedHistoryCompletion.Custom
        assertEquals(edit.performed,corrected.evidence.visits
            .single{it.setKey==working.setKey}.actual)
        assertEquals(original,(Disk().journals.open(owner,firstId)
            as NativeCustomJournalResult.Durable).issued.journal)
        assertEquals(NativeCustomNextCycleSlot.Next(2,days.first().weekday,
            days.first().label),Disk().next(inputs))
    }

    @Test fun finishedGeneratedWorkoutWithWarmupsProvidesRealV2CoachingEvidence() {
        val (cycle,inputs)=saveGenerated()
        val day=cycle.blocks.single().days.minBy {it.weekday}
        val workoutId="generated-v2-history-1"
        val before=Disk().prepared.prepare(owner,workoutId,cycleId,1,
            day.weekday,inputs)
        assertTrue(before.toString(),before is NativeCustomPrepareResult.Prepared)
        var journal=(Disk().journals.issue(owner,workoutId,cycleId,1,
            day.weekday,inputs) as NativeCustomJournalResult.Durable).issued.journal
        val source=journal.snapshot
        val all=source.slots.flatMap{it.sets}
        assertTrue(all.any{it.prescription.type==DraftSetType.WARMUP})
        // The coached movement itself must have issued warmups; a warmup
        // attached to some other slot is not evidence for this exercise.
        val selected=source.slots.first{slot->
            slot.sets.any{it.prescription.type==DraftSetType.WARMUP} &&
                slot.sets.any{set->
                    set.prescription.type==DraftSetType.WORKING &&
                        set.prescription.reps.minimum<set.prescription.reps.maximum &&
                        set.prescription.targetRir!=null
                }
        }
        val target=selected.sets.first{set->
            set.prescription.type==DraftSetType.WORKING &&
                set.prescription.reps.minimum<set.prescription.reps.maximum &&
                set.prescription.targetRir!=null
        }
        for((i,set) in all.withIndex()) {
            val p=set.prescription
            val actual=when(val load=p.load) {
                is DraftSetLoad.Verified ->KotlinPerformedSet(
                    load.value,load.unit,p.reps.minimum,
                    if(p.type==DraftSetType.WORKING)
                        (if(set.setKey==target.setKey)
                            (p.targetRir?:2)+2
                         else maxOf(2,p.targetRir?:2)).coerceAtMost(10)
                    else null)
                DraftSetLoad.ExplicitBodyweight ->KotlinPerformedSet(
                    null,null,p.reps.minimum,
                    if(p.type==DraftSetType.WORKING)
                        (if(set.setKey==target.setKey)
                            (p.targetRir?:2)+2
                         else maxOf(2,p.targetRir?:2)).coerceAtMost(10)
                    else null)
            }
            journal=(Disk().journals.record(owner,workoutId,
                CustomWorkoutJournalOperation.Record(
                    "generated-v2-set-$i",journal.revision,
                    CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,actual))
                as NativeCustomJournalResult.Durable).issued.journal
        }
        assertEquals(0,journal.unresolvedVisitCount)
        val persisted=Disk()
        val finish=NativeKotlinCustomHistoryTransaction(
            persisted.history,persisted.journals,persisted.fence,identity).complete(
                owner,athlete,workoutId,CustomWorkoutJournalOperation.Finish(
                    "generated-v2-finish",journal.revision))
        assertTrue(finish.toString(),finish is NativeCustomFinishResult.Committed)

        val recorded=(Disk().timeline.read(owner,athlete)
            as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.single()
            as NativeKotlinMixedHistoryCompletion.Custom
        val reopened=(Disk().journals.open(owner,workoutId)
            as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(source,reopened.snapshot)
        assertEquals(workoutId,recorded.evidence.workoutId)
        assertTrue(recorded.evidence.visits.any {
            it.setType==DraftSetType.WARMUP
        })
        val next=(Disk().preflight.inspect(owner,cycleId,2,day.weekday,inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        val coaching=CustomNextWorkoutCoachingDomain.review(
            CustomNextWorkoutCoachingRequest(next,selected.slotId,
                listOf(CustomCoachingHistoricalSession(
                    recorded.manifest.completionOrder,reopened.snapshot,
                    recorded.evidence)),
                null,n("5"),CoachReadiness.NORMAL,false,
                CustomRepForecastPolicy.TARGET_RIR_V2))
        assertEquals(coaching.toString(),
            CustomNextWorkoutCoachingStatus.BUILD_REPS,coaching.status)
        assertEquals(workoutId,coaching.latestWorkoutId)
        assertNull(coaching.loadReviewCandidate)
        assertTrue(coaching.nextSetSuggestions.any{it.changed})
        val amended=CustomNextRepAmendmentDomain.propose(
            next,selected.slotId,coaching)
        assertTrue(amended.toString(),
            amended is CustomNextRepAmendmentResult.Proposed)
        val nextSlot=next.slots.single{it.slotId==selected.slotId}
        val adjusted=(amended as CustomNextRepAmendmentResult.Proposed)
            .amendment.amended.slots.single{it.slotId==selected.slotId}
        assertEquals(nextSlot.sets.filter{
            it.prescription.type==DraftSetType.WARMUP
        },adjusted.sets.filter{it.prescription.type==DraftSetType.WARMUP})
        assertEquals(nextSlot.sets.map{it.prescription.load},
            adjusted.sets.map{it.prescription.load})
        assertEquals(source,reopened.snapshot)
    }
}
