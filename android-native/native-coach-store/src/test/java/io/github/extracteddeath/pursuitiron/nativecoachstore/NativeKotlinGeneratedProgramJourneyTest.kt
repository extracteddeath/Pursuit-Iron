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
    private fun weekdays(split:ComposerSplit)=when(split) {
        ComposerSplit.FULL_BODY->setOf(0,2,4)
        ComposerSplit.UPPER_LOWER->setOf(0,1,3,4)
        ComposerSplit.PUSH_PULL_LEGS->setOf(0,1,2,3,4,5)
        ComposerSplit.ULPPL->setOf(0,1,2,4,5)
    }
    private fun setup(split:ComposerSplit=ComposerSplit.UPPER_LOWER,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY)=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="profile-1",athleteProfileId=athlete,
        goal=if(phase==TrainingPhase.HYPERTROPHY)AthleteTrainingGoal.HYPERTROPHY
            else AthleteTrainingGoal.POWERBUILDING,
        experience=AthleteExperience.ADVANCED,split=split,
        trainingWeekdays=weekdays(split),timezoneId="America/New_York",
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
        longTrainingDays=emptySet(),declaredRestDays=(0..6).toSet()-weekdays(split)))
    private val catalog=StarterExerciseTranche.movements()
    private fun request(split:ComposerSplit=ComposerSplit.UPPER_LOWER,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        includeHipMachines:Boolean=false,includeTrapBar:Boolean=false,
        includeNewTargets:Boolean=false):FirstProgramStartRequest {
        val input=FirstProgramStartRequest(setup(split,phase),catalog,loads,
            bodyweight,phase,nonSetSecondsPerDay=900)
        if(includeNewTargets) {
            val profile=checkNotNull(input.setup.reviewed)
            return input.copy(setup=input.setup.copy(reviewed=profile.copy(
                sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
                gym=profile.gym.copy(availableFacilities=profile.gym.availableFacilities+"wall-support"))),
                selectedBodyweightExerciseIds=bodyweight+setOf("serratus-pushup","tibialis-raise"))
        }
        if(includeTrapBar) {
            val profile=checkNotNull(input.setup.reviewed)
            return input.copy(setup=input.setup.copy(reviewed=profile.copy(
                sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,maxBarbellExercisesPerDay=3,
                gym=profile.gym.copy(confirmedStock=profile.gym.confirmedStock+mapOf(
                    "trap-hex-bar" to VerifiedLoadStock.Barbell(LoadUnit.LB,n("55"),mapOf(n("45") to 1)))))),
                startingLoads=loads+mapOf("trap-bar-deadlift" to n("145"),"db-shrug" to n("30")))
        }
        if(!includeHipMachines)return input
        val profile=checkNotNull(input.setup.reviewed)
        val stocks=listOf("hip-abduction-machine","hip-adduction-machine")
            .associateWith {VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.EXERCISE_OVERRIDE,listOf(n("15"),n("20"),n("25")))}
        return input.copy(setup=input.setup.copy(reviewed=profile.copy(
            gym=profile.gym.copy(confirmedStock=profile.gym.confirmedStock+stocks))),
            startingLoads=loads+mapOf("hip-abduction" to n("20"),
                "adduction-machine" to n("20")))
    }

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
        val settings=NativeKotlinCoachAssistanceSettingsStore(root,fence,identity)
        val machines=NativeKotlinMachineRosterFileStore(root)
        val choices=NativeKotlinWorkoutMachineChoiceFileStore(root)
        val liveCoach=NativeKotlinCustomLiveSetCoachReview(
            fence,identity,journals,sources,settings,machines,choices)
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
        policyVersion:String=CoachFirstProgramStartDomain.POLICY_VERSION,
        split:ComposerSplit=ComposerSplit.UPPER_LOWER,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        includeHipMachines:Boolean=false,includeTrapBar:Boolean=false,
        includeNewTargets:Boolean=false
    ):Pair<CustomTrainingCycle,CustomCoachAuditInputs> {
        val input=request(split,phase,includeHipMachines,includeTrapBar,includeNewTargets).copy(policyVersion=policyVersion)
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
                    phase,input.startingLoads,input.selectedBodyweightExerciseIds,emptyList(),900)))
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

    private fun writeLocalAthleteForRestoreProof() {
        val bytes=java.io.ByteArrayOutputStream().also {stream->
            java.io.DataOutputStream(stream).use {out->
                out.writeInt(0x50494F31)
                out.writeInt(1)
                out.writeUTF(owner)
                out.writeUTF(athlete)
            }
        }.toByteArray()
        java.nio.file.Files.write(temp.root.toPath().resolve("local-athlete.bin"),
            bytes+java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes))
    }

    // A real generated strength cycle owns its ordinary draft and consent.
    // Explicit specialist selection must go through the same revisioned
    // named authority as an authored cycle; a generated seed is not consent.
    @Test fun authoredWaveUsesExactWeeklyTargetsThroughApprovalColdIssueAndFinish() {
        val (base,inputs)=saveGenerated()
        val block=base.blocks.single()
        val days=block.days.sortedBy{it.weekday}
        val day=days.first{d->d.slots.any{slot->
            slot.sets.isNotEmpty() && slot.sets.all{set->
                val load=set.load as? DraftSetLoad.Verified
                set.type==DraftSetType.WORKING && set.amplifier==null &&
                    set.percentageIntent==null && load?.basis==LoadBasis.DUMBBELL_PER_HAND &&
                    load.value<=n("45")
            } && slot.sets.map{it.load}.distinct().size==1
        }}
        val slot=day.slots.first{s->
            s.sets.isNotEmpty() && s.sets.all{set->
                val load=set.load as? DraftSetLoad.Verified
                set.type==DraftSetType.WORKING && set.amplifier==null &&
                    set.percentageIntent==null && load?.basis==LoadBasis.DUMBBELL_PER_HAND &&
                    load.value<=n("45")
            } && s.sets.map{it.load}.distinct().size==1
        }
        val initial=(slot.sets.first().load as DraftSetLoad.Verified).value
        val expected=mapOf(1 to initial,2 to initial.add(n("5")),
            3 to initial,4 to initial.add(n("5")))
        fun sets(week:Int)=slot.sets.map{set->
            set.copy(load=(set.load as DraftSetLoad.Verified).copy(value=expected.getValue(week)))
        }
        val amendedBlock=block.copy(days=block.days.map{d->
            if(d.weekday!=day.weekday)d else d.copy(slots=d.slots.map{s->
                if(s.slotId!=slot.slotId)s else s.copy(progressionMethod=SavedProgressionMethod.WAVE)
            })
        })
        val wave=base.copy(revision=base.revision+1,blocks=listOf(amendedBlock),
            weekOverrides=base.weekOverrides+(block.blockId to
                (base.weekOverrides[block.blockId].orEmpty()+(2..4).map{week->
                    CustomWeekSlotOverride(week,day.weekday,slot.slotId,sets(week))
                })))
        val source=(Disk().sources.read(owner) as NativeAuthoringSourceRead.Found).source
        val plan=BasicProgramProgressionPlanDomain.review(wave,
            reviewedCatalog=source.catalog.associateBy{it.exerciseId},
            experience=source.setup.reviewed?.experience)
        assertTrue(plan.toString(),plan is ProgramProgressionPlan.Ready)
        val assignment=(plan as ProgramProgressionPlan.Ready).assignments.single{
            it.key==ProgramProgressionSlotKey(block.blockId,day.weekday,slot.slotId)
        }
        assertEquals(ProgramProgressionRoute.AUTHORED_WEEKLY_PERIODIZATION,assignment.route)
        assertEquals(setOf(2,3,4),assignment.authoredWeekNumbers)
        assertNull(assignment.basicStrategy)
        val incomplete=wave.copy(weekOverrides=wave.weekOverrides+(block.blockId to
            wave.weekOverrides.getValue(block.blockId).filterNot{
                it.weekInBlock==3 && it.weekday==day.weekday && it.slotId==slot.slotId
            }))
        assertTrue(BasicProgramProgressionPlanDomain.review(incomplete,
            reviewedCatalog=source.catalog.associateBy{it.exerciseId},
            experience=source.setup.reviewed?.experience) is ProgramProgressionPlan.Refused)
        assertTrue(Disk().drafts.save(owner,cycleId,base.revision,wave) is NativeCustomDraftWriteResult.Committed)
        assertTrue(Disk().prepared.prepare(owner,"wave-unsigned",cycleId,1,day.weekday,inputs)
            is NativeCustomPrepareResult.Rejected)
        val previous=(Disk().consent.read(owner,cycleId,inputs) as NativeCustomSignoffRead.Found)
        assertFalse(previous.isCurrent)
        val signed=Disk().consent.confirm(owner,cycleId,wave.revision,source.revision,
            previous.receipt.recordRevision,inputs,true)
        assertTrue(signed.toString(),signed is NativeCustomSignoffWrite.Committed)
        assertEquals(wave,(Disk().drafts.read(owner,cycleId) as NativeCustomDraftReadResult.Found).draft)
        val snapshots=linkedMapOf<String,CustomWorkoutDaySnapshot>()
        for(week in 1..4) {
            for(d in days) {
                val disk=Disk()
                assertEquals(NativeCustomNextCycleSlot.Next(week,d.weekday,d.label),disk.next(inputs))
                val id="wave-w$week-d${d.weekday}"
                if(d.weekday==day.weekday && week>1) {
                    val basic=NativeKotlinCustomBasicNextReview(disk.fence,identity,disk.drafts,
                        disk.sources,disk.preflight,disk.timeline,disk.journals,disk.machines)
                    val reviewed=basic.reviewAuthored(owner,cycleId,inputs,week,d.weekday,slot.slotId,
                        "wave-basic-w$week",n("5"))
                    assertTrue(reviewed.toString(),reviewed is NativeCustomBasicNextReadResult.Reviewed)
                    assertEquals(CustomBasicHistoryResult.Rejected(
                        CustomBasicHistoryRefusal.AUTHORED_OR_SPECIALIST_OWNS_LOAD),
                        (reviewed as NativeCustomBasicNextReadResult.Reviewed).projection)
                }
                val prepared=disk.prepared.prepare(owner,id,cycleId,week,d.weekday,inputs)
                assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
                val issued=Disk().journals.issue(owner,id,cycleId,week,d.weekday,inputs)
                assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
                var journal=(issued as NativeCustomJournalResult.Durable).issued.journal
                val snapshot=journal.originalSnapshot
                assertEquals((prepared as NativeCustomPrepareResult.Prepared).workout.snapshot,snapshot)
                if(d.weekday==day.weekday) {
                    val exact=snapshot.slots.single{it.slotId==slot.slotId}.sets.map{it.prescription}
                    assertEquals(sets(week),exact)
                    assertTrue(exact.all{(it.load as DraftSetLoad.Verified).value.compareTo(
                        expected.getValue(week))==0})
                }
                for((index,set) in snapshot.slots.flatMap{it.sets}.withIndex()) {
                    val target=set.prescription
                    val reps=target.exactRepTarget ?: target.reps.maximum
                    val actual=when(val load=target.load) {
                        is DraftSetLoad.Verified -> KotlinPerformedSet(load.value,load.unit,reps,2)
                        DraftSetLoad.ExplicitBodyweight -> KotlinPerformedSet(null,null,reps,2)
                    }
                    val result=Disk().journals.record(owner,id,CustomWorkoutJournalOperation.Record(
                        "wave-actual-$index",journal.revision,
                        CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                        KotlinJournalSetOutcome.COMPLETED,actual))
                    assertTrue(result.toString(),result is NativeCustomJournalResult.Durable)
                    journal=(result as NativeCustomJournalResult.Durable).issued.journal
                }
                assertEquals(0,journal.unresolvedVisitCount)
                val cold=Disk()
                assertTrue(NativeKotlinCustomHistoryTransaction(cold.history,cold.journals,cold.fence,identity)
                    .complete(owner,athlete,id,CustomWorkoutJournalOperation.Finish("wave-finish",journal.revision))
                    is NativeCustomFinishResult.Committed)
                snapshots[id]=snapshot
            }
        }
        val history=(Disk().timeline.read(owner,athlete) as NativeKotlinMixedHistoryReadResult.Review).timeline
        assertEquals(4*days.size,history.completions.size)
        for((id,snapshot) in snapshots)assertEquals(snapshot,(Disk().journals.open(owner,id)
            as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
        assertEquals(wave,(Disk().drafts.read(owner,cycleId) as NativeCustomDraftReadResult.Found).draft)
    }

    @Test fun generatedStrengthCycleRequiresFreshNamedConsentAndIssuesReviewedRecipe() {
        val (cycle,inputs)=saveGenerated(phase=TrainingPhase.STRENGTH)
        val day=cycle.blocks.single().days.first{d->d.slots.any{
            it.exerciseId=="barbell_bench" && it.role==ComposerRole.PRIMARY
        }}
        val slot=day.slots.first{it.exerciseId=="barbell_bench" && it.role==ComposerRole.PRIMARY}
        val disk=Disk()
        val inventory=NativeKotlinCustomOutstandingWorkoutInventory(disk.prepared,disk.journals)
        val reviewer=NativeKotlinNamedPercentageDraftReview(disk.fence,identity,
            disk.drafts,disk.sources,disk.consent,disk.preflight,disk.timeline,inventory)
        val selection=NativeNamedPercentageSelection(owner,cycleId,1,day.weekday,
            slot.slotId,NamedPercentageScheme.FIVE_THREE_ONE,PercentageTier.PRIMARY,
            PercentageDayRole.STANDARD,PercentageReferenceKind.TRAINING_MAX,n("160"))
        val selections=listOf(selection,selection.copy(absoluteWeek=2))
        val preview=reviewer.previewBatch(selections)
        assertTrue(preview.toString(),preview is NativeNamedBatchReview.Ready)
        preview as NativeNamedBatchReview.Ready
        assertFalse(preview.mayIssueWorkout)
        assertTrue(reviewer.acceptBatch(selections,preview,false) is NativeNamedBatchAcceptance.Rejected)
        assertTrue(reviewer.acceptBatch(selections,preview,true) is NativeNamedBatchAcceptance.Saved)
        val cold=Disk()
        assertTrue(cold.prepared.prepare(owner,"ordinary-named-bypass",cycleId,1,
            day.weekday,inputs) is NativeCustomPrepareResult.Rejected)
        val coldInventory=NativeKotlinCustomOutstandingWorkoutInventory(cold.prepared,cold.journals)
        val signedReader=NativeKotlinSavedNamedPercentageReview(cold.fence,identity,
            cold.drafts,cold.sources,cold.consent,cold.timeline,coldInventory)
        assertTrue(cold.prepared.prepareNamed(owner,"generated-named",cycleId,1,
            day.weekday,inputs,cold.timeline,coldInventory) is NativeCustomPrepareResult.Rejected)
        val reviewed=signedReader.review(owner,cycleId,inputs)
        assertTrue(reviewed.toString(),reviewed is NativeSavedNamedReview.Ready)
        val signed=cold.consent.confirmNamed(owner,cycleId,reviewed as NativeSavedNamedReview.Ready,
            true,cold.timeline,coldInventory)
        assertTrue(signed.toString(),signed is NativeCustomSignoffWrite.Committed)
        val prepared=cold.prepared.prepareNamed(owner,"generated-named",cycleId,1,
            day.weekday,inputs,cold.timeline,coldInventory)
        assertTrue(prepared.toString(),prepared is NativeCustomPrepareResult.Prepared)
        val snapshot=(prepared as NativeCustomPrepareResult.Prepared).workout.snapshot
        val expected=preview.draft.weekOverrides.getValue(cycle.blocks.single().blockId)
            .single{it.weekInBlock==1 && it.weekday==day.weekday && it.slotId==slot.slotId}
        val actual=snapshot.slots.single{it.slotId==slot.slotId}.sets
        assertEquals(expected.sets,actual.map{it.prescription})
        val named=actual.filter{it.prescription.type==DraftSetType.WORKING}
        assertEquals(slot.sets.count{it.type==DraftSetType.WORKING},named.size)
        assertTrue(named.all{it.prescription.percentageIntent?.scheme==NamedPercentageScheme.FIVE_THREE_ONE})
        assertTrue(named.any{it.prescription.percentageIntent?.repOut==true})
        assertTrue(named.all{it.prescription.exactRepTarget==5})
        // Every unedited generated slot remains exactly as originally prescribed.
        for(other in day.slots.filter{it.slotId!=slot.slotId})
            assertEquals(other.sets,snapshot.slots.single{it.slotId==other.slotId}.sets.map{it.prescription})
        val issued=Disk().journals.issueNamed(owner,"generated-named",cycleId,1,
            day.weekday,inputs,Disk().timeline)
        assertTrue(issued.toString(),issued is NativeCustomJournalResult.Durable)
        assertEquals(snapshot,(issued as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
        // Independent numerical oracle for the actual confirmed bar/plates:
        // 45 lb plus symmetric pairs of 5, 10, 25, 45 lb; 160 lb reference.
        // The raw percentages 104/120/136 and 112/128/144 are down-only.
        fun assertRecipe(sets:List<CustomAuthoredSet>,week:Int) {
            val loads=if(week==1)listOf("95","115","135") else listOf("105","125","135")
            val percents=if(week==1)listOf("65","75","85") else listOf("70","80","90")
            val reps=if(week==1)5 else 3
            val working=sets.filter{it.type==DraftSetType.WORKING}
            assertEquals(slot.sets.count{it.type==DraftSetType.WORKING},working.size)
            for(set in working) {
                val intent=checkNotNull(set.percentageIntent)
                val ordinal=intent.sourceOrdinal-1
                assertEquals(0,n(loads[ordinal]).compareTo((set.load as DraftSetLoad.Verified).value))
                assertEquals(0,n(percents[ordinal]).compareTo(intent.sourcePercent))
                assertEquals(reps,set.exactRepTarget)
                assertEquals(ordinal==2,intent.repOut)
            }
            assertEquals(1,working.count{it.percentageIntent?.repOut==true})
        }
        assertRecipe(actual.map{it.prescription},1)
        var journal=issued.issued.journal
        for((index,set) in snapshot.slots.flatMap{it.sets}.withIndex()) {
            val prescription=set.prescription
            assertNull(prescription.amplifier)
            val reps=prescription.exactRepTarget ?: prescription.reps.minimum
            val performedReps=reps+if(prescription.percentageIntent?.repOut==true)2 else 0
            val performed=when(val load=prescription.load) {
                is DraftSetLoad.Verified -> KotlinPerformedSet(load.value,load.unit,performedReps,null)
                DraftSetLoad.ExplicitBodyweight -> KotlinPerformedSet(null,null,performedReps,null)
            }
            val visit=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN)
            val saved=Disk().journals.record(owner,"generated-named",
                CustomWorkoutJournalOperation.Record("named-log-$index",journal.revision,
                    visit,KotlinJournalSetOutcome.COMPLETED,performed))
            assertTrue(saved.toString(),saved is NativeCustomJournalResult.Durable)
            journal=(saved as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(snapshot,journal.originalSnapshot)
        }
        assertEquals(0,journal.unresolvedVisitCount)
        val finishDisk=Disk()
        val finished=NativeKotlinCustomHistoryTransaction(finishDisk.history,finishDisk.journals,
            finishDisk.fence,identity).complete(owner,athlete,"generated-named",
                CustomWorkoutJournalOperation.Finish("named-finish",journal.revision))
        assertTrue(finished.toString(),finished is NativeCustomFinishResult.Committed)
        val facts=(finished as NativeCustomFinishResult.Committed).evidence.visits
        val repOut=named.single{it.prescription.percentageIntent?.repOut==true}
        val fact=facts.single{it.setKey==repOut.setKey}
        assertEquals(repOut.prescription.percentageIntent,fact.percentageIntent)
        assertEquals(RepBand(5,5),fact.prescribedReps)
        assertEquals(7,fact.actual?.reps)
        assertNull(fact.actual?.actualRir)
        val reopened=Disk()
        val timeline=(reopened.timeline.read(owner,athlete) as NativeKotlinMixedHistoryReadResult.Review).timeline
        assertEquals(facts,(timeline.completions.single() as NativeKotlinMixedHistoryCompletion.Custom).evidence.visits)
        assertEquals(snapshot,(reopened.journals.open(owner,"generated-named")
            as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
        val next=reopened.prepared.prepareNamed(owner,"generated-named-w2",cycleId,2,
            day.weekday,inputs,reopened.timeline,
            NativeKotlinCustomOutstandingWorkoutInventory(reopened.prepared,reopened.journals))
        assertTrue(next.toString(),next is NativeCustomPrepareResult.Prepared)
        val nextSnapshot=(next as NativeCustomPrepareResult.Prepared).workout.snapshot
        val second=nextSnapshot.slots.single{it.slotId==slot.slotId}.sets.map{it.prescription}
        assertRecipe(second,2)
        val secondIssue=Disk().journals.issueNamed(owner,"generated-named-w2",cycleId,2,
            day.weekday,inputs,Disk().timeline)
        assertTrue(secondIssue.toString(),secondIssue is NativeCustomJournalResult.Durable)
        assertEquals(nextSnapshot,(secondIssue as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
        assertEquals(snapshot,(Disk().journals.open(owner,"generated-named")
            as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
    }

    @Test fun generatedE1rmMethodUsesCorrectedEffortAndExplicitNextWeekConsent() {
        val (base,inputs)=saveGenerated(phase=TrainingPhase.STRENGTH)
        val block=base.blocks.single()
        val day=block.days.first{d->d.slots.any{
            it.exerciseId=="barbell_bench" && it.role==ComposerRole.PRIMARY}}
        val slot=day.slots.first{it.exerciseId=="barbell_bench" && it.role==ComposerRole.PRIMARY}
        val working=slot.sets.filter{it.type==DraftSetType.WORKING}
        assertTrue(working.isNotEmpty())
        assertTrue(working.all{it.amplifier==null && it.targetRir!=null &&
            (it.load as? DraftSetLoad.Verified)?.value?.compareTo(n("135"))==0})
        val cycle=base.copy(revision=base.revision+1,blocks=listOf(block.copy(
            days=block.days.map{d->if(d.weekday!=day.weekday)d else d.copy(
                slots=d.slots.map{s->if(s.slotId==slot.slotId)s.copy(
                    progressionMethod=SavedProgressionMethod.E1RM) else s})})))
        assertTrue(Disk().drafts.save(owner,cycleId,base.revision,cycle)
            is NativeCustomDraftWriteResult.Committed)
        assertTrue(Disk().consent.confirm(owner,cycleId,cycle.revision,1,1,inputs,true)
            is NativeCustomSignoffWrite.Committed)
        // The explicitly saved method is available without optional fatigue
        // coaching; it does not silently enable coach assistance.
        assertTrue(Disk().settings.save(owner,0,CoachAssistancePolicy.MANUAL)
            is NativeCoachSettingsWrite.Saved)
        val id="generated-e1rm-first"
        assertTrue(Disk().prepared.prepare(owner,id,cycleId,1,day.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        var journal=(Disk().journals.issue(owner,id,cycleId,1,day.weekday,inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        val original=journal.originalSnapshot
        for((index,set) in original.slots.flatMap{it.sets}.withIndex()){
            val p=set.prescription
            val reps=if(set.slotId==slot.slotId && p.type==DraftSetType.WORKING)
                p.reps.maximum else p.exactRepTarget ?: p.reps.minimum
            val actual=when(val load=p.load){
                is DraftSetLoad.Verified -> KotlinPerformedSet(load.value,load.unit,reps,null)
                DraftSetLoad.ExplicitBodyweight -> KotlinPerformedSet(null,null,reps,null)
            }
            journal=(Disk().journals.record(owner,id,
                CustomWorkoutJournalOperation.Record("e1rm-log-$index",journal.revision,
                    CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,actual)) as NativeCustomJournalResult.Durable).issued.journal
        }
        assertEquals(0,journal.unresolvedVisitCount)
        val d=Disk()
        assertTrue(NativeKotlinCustomHistoryTransaction(d.history,d.journals,d.fence,identity)
            .complete(owner,athlete,id,CustomWorkoutJournalOperation.Finish("e1rm-finish",journal.revision))
            is NativeCustomFinishResult.Committed)
        fun review():NativeCustomBasicNextReadResult.Reviewed {
            val cold=Disk()
            return NativeKotlinCustomBasicNextReview(cold.fence,identity,cold.drafts,cold.sources,
                cold.preflight,cold.timeline,cold.journals,cold.machines)
                .reviewAuthored(owner,cycleId,inputs,2,day.weekday,slot.slotId,"e1rm-future",n("10"))
                as NativeCustomBasicNextReadResult.Reviewed
        }
        val earned=review()
        val projection=earned.projection as CustomBasicHistoryResult.Reviewed
        assertEquals(working.size,checkNotNull(projection.candidate.e1rmEvidence).assumedGoalRirSetCount)
        assertTrue(projection.candidate.e1rmEvidence!!.everySetMeetsGoal)
        // 45 lb bar, two 45 lb plates and then a real pair of 5s.
        assertEquals(List(working.size){n("145")},projection.candidate.proposedWorkingLoads)
        val target=original.slots.single{it.slotId==slot.slotId}.sets.first{
            it.prescription.type==DraftSetType.WORKING}
        val visit=CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN)
        val logged=journal.effectiveVisits.getValue(visit).operation as CustomWorkoutJournalOperation.Record
        val actual=checkNotNull(logged.performed)
        fun correct(event:String,revision:Long,predecessor:String,rir:Int?):Unit {
            val disk=Disk()
            assertTrue(NativeKotlinCustomHistoryCorrectionTransaction(disk.history,disk.journals,
                disk.corrections,disk.fence,identity).correct(owner,athlete,id,
                CustomSavedVisitCorrection(event,revision,visit,predecessor,
                    KotlinJournalSetOutcome.COMPLETED,actual.copy(actualRir=rir)))
                is NativeCustomCorrectionTransactionResult.Committed)
        }
        correct("e1rm-effort-correction",0,logged.eventId,0)
        val held=review()
        assertTrue(held.historyRevision>earned.historyRevision)
        val heldCandidate=(held.projection as CustomBasicHistoryResult.Reviewed).candidate
        assertEquals(List(working.size){n("135")},heldCandidate.proposedWorkingLoads)
        assertFalse(checkNotNull(heldCandidate.e1rmEvidence).everySetMeetsGoal)
        assertTrue(heldCandidate.increasedOrdinals.isEmpty())
        assertEquals(earned.next,held.next)
        assertEquals(cycle,(Disk().drafts.read(owner,cycleId) as NativeCustomDraftReadResult.Found).draft)
        // Explicitly correct the incorrect RIR entry back to unreported.
        correct("e1rm-effort-restored",1,"e1rm-effort-correction",null)
        val fresh=review()
        val freshProjection=fresh.projection as CustomBasicHistoryResult.Reviewed
        assertEquals(projection.candidate,freshProjection.candidate)
        val before=fresh.next.slots.single{it.slotId==slot.slotId}.sets.map{it.prescription}
        val edits=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(slot.slotId,before,freshProjection))
        val proposal=CustomCoachWeekEditDomain.review(cycle,cycle.revision,2,day.weekday,slot.slotId,
            edits,setup(phase=TrainingPhase.STRENGTH),catalog,inputs)
        assertTrue(proposal.toString(),proposal is CustomCoachWeekEditResult.Reviewed)
        val changed=(proposal as CustomCoachWeekEditResult.Reviewed).review.proposal.draft
        assertTrue(Disk().drafts.save(owner,cycleId,cycle.revision,changed)
            is NativeCustomDraftWriteResult.Committed)
        assertTrue(Disk().prepared.prepare(owner,"unapproved-e1rm-next",cycleId,2,day.weekday,inputs)
            is NativeCustomPrepareResult.Rejected)
        assertTrue(Disk().consent.confirm(owner,cycleId,changed.revision,1,2,inputs,true)
            is NativeCustomSignoffWrite.Committed)
        assertTrue(Disk().prepared.prepare(owner,"e1rm-next",cycleId,2,day.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        val issued=(Disk().journals.issue(owner,"e1rm-next",cycleId,2,day.weekday,inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(edits,issued.originalSnapshot.slots.single{it.slotId==slot.slotId}.sets.map{it.prescription})
        assertEquals(original,(Disk().journals.open(owner,id) as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
        val facts=(Disk().timeline.read(owner,athlete) as NativeKotlinMixedHistoryReadResult.Review)
            .timeline.completions.single() as NativeKotlinMixedHistoryCompletion.Custom
        assertNull(facts.evidence.visits.single{it.setKey==target.setKey}.actual?.actualRir)
    }

    @Test fun nativeStagedAuditPreservesAnUnfinishedGeneratedCustomWorkout() {
        val (cycle,inputs)=saveGenerated()
        writeLocalAthleteForRestoreProof()
        val day=cycle.blocks.single().days.minBy{it.weekday}
        val id="restored-unfinished-first-day"
        val d=Disk()
        assertTrue(d.prepared.prepare(owner,id,cycleId,1,day.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        assertTrue(Disk().journals.issue(owner,id,cycleId,1,day.weekday,inputs)
            is NativeCustomJournalResult.Durable)
        val gymId=checkNotNull((Disk().journals.open(owner,id)
            as NativeCustomJournalResult.Durable).issued.journal
            .originalSnapshot.stamp.gymId)
        val firstExercise=(Disk().journals.open(owner,id)
            as NativeCustomJournalResult.Durable).issued.journal
            .snapshot.slots.first().exerciseId
        val choice=Disk().choices.save(owner,id,true,gymId,0,
            mapOf(firstExercise to "machine-a"))
        assertTrue(choice.toString(),
            choice is NativeWorkoutMachineChoiceResult.Saved)
        val coaching=Disk().settings.save(owner,0,
            CoachAssistancePolicy(false,false,false))
        assertTrue(coaching.toString(),
            coaching is NativeCoachSettingsWrite.Saved)
        val pinned=NativeKotlinPinnedExerciseNoteFileStore(
            d.root,d.fence,identity)
        val savedNote=pinned.save(owner,firstExercise,0,"first-setup",
            "Keep the machine seat low",mapOf("seat" to "3","grip" to "Neutral"))
        assertTrue(savedNote.toString(),savedNote is NativePinnedNoteWrite.Saved)
        val oldExercise=pinned.save(owner,"retired_press",0,"retired-setup",
            "Old machine pad reference",mapOf("oldPadSetting" to "7"))
        assertTrue(oldExercise.toString(),
            oldExercise is NativePinnedNoteWrite.Saved)
        val permissions=NativeKotlinAdaptiveCyclePermissionFileStore(
            d.root,d.fence,identity)
        val currentKinds=setOf(CustomCycleChangeKind.EXERCISES,
            CustomCycleChangeKind.SPLIT)
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            permissions.confirm(owner,cycleId,0,currentKinds,true))
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            permissions.confirm(owner,"retired-cycle",0,
                setOf(CustomCycleChangeKind.DAY_COUNT),true))
        assertEquals(NativeAdaptivePermissionWrite.Committed(2),
            permissions.revoke(owner,"retired-cycle",1))
        val expected=NativeKotlinOwnerIdentity(owner,athlete)
        val before=NativeKotlinStagedCoreStoreAudit.inspect(d.root,expected)
        assertTrue(before.toString(),before is NativeKotlinStagedCoreAuditResult.Verified)
        val active=before as NativeKotlinStagedCoreAuditResult.Verified
        assertEquals(0,active.finishedWorkouts)
        assertEquals(1,active.customPrepared)
        assertEquals(1,active.customIssued)
        assertEquals(1,active.customUnfinished)
        assertEquals(0,active.generatedIssued)
        assertEquals(1,active.machineChoiceRecords)
        assertEquals(1L,active.coachingSettingsRevision)
        assertEquals(2,active.pinnedExerciseNotes)
        assertEquals(2,active.adaptivePermissionRecords)
        assertEquals(1,active.revokedAdaptivePermissions)
        val originalPinned=(pinned.read(owner,firstExercise)
            as NativePinnedNoteRead.Current).note
        val retiredPinned=(pinned.read(owner,"retired_press")
            as NativePinnedNoteRead.Current).note
        val password="generated-day-one-recovery-password".toCharArray()
        val original=(Disk().journals.open(owner,id)
            as NativeCustomJournalResult.Durable).issued.journal
        val archive=NativeKotlinEncryptedTrainingSnapshot.capture(
            d.root,d.fence,expected,{expected},
            {(Disk().timeline.read(owner,athlete) is NativeKotlinMixedHistoryReadResult.Review)},password)
        assertTrue(archive.toString(),
            archive is NativeKotlinTrainingSnapshotResult.Ready)
        val newDevice=temp.newFolder("another-install").toPath()
        val result=NativeKotlinCleanStoreRestore.restoreToAbsentStore(
            newDevice.resolve("kotlin-coach-v1"),
            (archive as NativeKotlinTrainingSnapshotResult.Ready).encrypted,
            password,expected,{stage,who->
                (NativeKotlinStagedCoreStoreAudit.inspect(stage,who) is NativeKotlinStagedCoreAuditResult.Verified)
            })
        assertTrue(result.toString(),
            result is NativeKotlinCleanRestoreResult.Committed)
        val restoredRoot=newDevice.resolve("kotlin-coach-v1")
        val after=NativeKotlinStagedCoreStoreAudit.inspect(restoredRoot,expected)
        assertEquals(before,after)
        val restoredPinned=NativeKotlinPinnedExerciseNoteFileStore(
            restoredRoot,NativeKotlinApprovalRevisionFence(restoredRoot),
            NativeKotlinCurrentIdentity{expected})
        assertEquals(originalPinned,
            (restoredPinned.read(owner,firstExercise)
                as NativePinnedNoteRead.Current).note)
        assertEquals(retiredPinned,
            (restoredPinned.read(owner,"retired_press")
                as NativePinnedNoteRead.Current).note)
        val coldPermissions=NativeKotlinAdaptiveCyclePermissionFileStore(
            restoredRoot,NativeKotlinApprovalRevisionFence(restoredRoot),
            NativeKotlinCurrentIdentity{expected})
        assertEquals(currentKinds,
            (coldPermissions.read(owner,cycleId)
                as NativeAdaptivePermissionRead.Found).receipt.allowedKinds)
        assertEquals(NativeAdaptivePermissionRead.Revoked(2),
            coldPermissions.read(owner,"retired-cycle"))
        val restoredIdentity=NativeKotlinCurrentIdentity{expected}
        val restoredFence=NativeKotlinApprovalRevisionFence(restoredRoot)
        val drafts=NativeCustomCycleDraftFileStore(
            restoredRoot,restoredFence,restoredIdentity)
        val source=NativeKotlinCustomAuthoringSourceFileStore(
            restoredRoot,restoredFence,restoredIdentity)
        val approvals=NativeKotlinCustomCycleSignoffFileStore(
            restoredRoot,restoredFence,restoredIdentity,drafts,source)
        val preflight=NativeKotlinCustomWorkoutPreflight(
            restoredFence,restoredIdentity,drafts,source,approvals)
        val prepared=NativeKotlinCustomWorkoutPreparedStore(
            restoredRoot,restoredFence,restoredIdentity,preflight)
        val reopened=NativeKotlinCustomWorkoutJournalFileStore(
            restoredRoot,restoredFence,restoredIdentity,prepared,preflight,
            NativeKotlinHistoryManifestFileStore(restoredRoot),
            KotlinNativeJournalFileStore(restoredRoot))
            .open(owner,id) as NativeCustomJournalResult.Durable
        assertEquals(original,reopened.issued.journal)
        assertEquals(original,(Disk().journals.open(owner,id)
            as NativeCustomJournalResult.Durable).issued.journal)
    }

    @Test fun stagedAuditRefusesCorruptAndPendingAdaptiveCyclePermissions() {
        saveGenerated()
        writeLocalAthleteForRestoreProof()
        val expected=NativeKotlinOwnerIdentity(owner,athlete)
        val permissions=NativeKotlinAdaptiveCyclePermissionFileStore(
            temp.root.toPath(),Disk().fence,identity)
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            permissions.confirm(owner,cycleId,0,
                setOf(CustomCycleChangeKind.EXERCISES),true))
        val before=NativeKotlinStagedCoreStoreAudit.inspect(
            temp.root.toPath(),expected)
        assertTrue(before.toString(),
            before is NativeKotlinStagedCoreAuditResult.Verified)
        assertEquals(1,(before as NativeKotlinStagedCoreAuditResult.Verified)
            .adaptivePermissionRecords)
        val path=java.nio.file.Files.list(temp.root.toPath()).use{stream->
            stream.filter{it.fileName.toString().startsWith("adapt-permission-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val original=java.nio.file.Files.readAllBytes(path)
        val broken=original.copyOf()
        broken[broken.lastIndex]=(broken.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(path,broken)
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.ADAPTIVE_PERMISSIONS_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(temp.root.toPath(),expected))
        assertArrayEquals(broken,java.nio.file.Files.readAllBytes(path))
        java.nio.file.Files.write(path,original)
        val pending=path.resolveSibling(
            "adapt-permission-"+"e".repeat(64)+".pending")
        java.nio.file.Files.write(pending,byteArrayOf(4))
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.ADAPTIVE_PERMISSIONS_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(temp.root.toPath(),expected))
        java.nio.file.Files.delete(pending)
        assertTrue(NativeKotlinStagedCoreStoreAudit.inspect(
            temp.root.toPath(),expected) is NativeKotlinStagedCoreAuditResult.Verified)
    }

    @Test fun stagedAuditRejectsCorruptOrPendingNotesRatherThanLosingSetupData() {
        saveGenerated()
        writeLocalAthleteForRestoreProof()
        val expected=NativeKotlinOwnerIdentity(owner,athlete)
        val notes=NativeKotlinPinnedExerciseNoteFileStore(
            temp.root.toPath(),Disk().fence,identity)
        assertTrue(notes.save(owner,"barbell_bench",0,"setup-1",
            "Keep old bench setting",mapOf("oldRackHeight" to "6"))
            is NativePinnedNoteWrite.Saved)
        val physical=java.nio.file.Files.list(temp.root.toPath()).use {stream->
            stream.filter{it.fileName.toString().startsWith("pinned-exercise-")}
                .findFirst().orElseThrow()
        }
        val originalBytes=java.nio.file.Files.readAllBytes(physical)
        val initial=NativeKotlinStagedCoreStoreAudit.inspect(temp.root.toPath(),expected)
        assertTrue(initial.toString(),initial is NativeKotlinStagedCoreAuditResult.Verified)
        assertEquals(1,(initial as NativeKotlinStagedCoreAuditResult.Verified)
            .pinnedExerciseNotes)
        val damaged=originalBytes.copyOf()
        damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(physical,damaged)
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.PINNED_NOTES_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(temp.root.toPath(),expected))
        assertArrayEquals(damaged,java.nio.file.Files.readAllBytes(physical))
        java.nio.file.Files.write(physical,originalBytes)
        val pending=physical.resolveSibling(
            physical.fileName.toString().removeSuffix(".bin")+".pending")
        java.nio.file.Files.write(pending,byteArrayOf(1))
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.PINNED_NOTES_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(temp.root.toPath(),expected))
        java.nio.file.Files.delete(pending)
        assertTrue(NativeKotlinStagedCoreStoreAudit.inspect(
            temp.root.toPath(),expected) is NativeKotlinStagedCoreAuditResult.Verified)
    }

    @Test fun stagedAuditRejectsOrphanMachineChoiceAndWrongWorkoutGym() {
        val (cycle,inputs)=saveGenerated()
        writeLocalAthleteForRestoreProof()
        val id="machine-scope-first-day"
        val day=cycle.blocks.single().days.first()
        assertTrue(Disk().prepared.prepare(owner,id,cycleId,1,day.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        val issued=(Disk().journals.issue(owner,id,cycleId,1,day.weekday,inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        val exercise=issued.snapshot.slots.first().exerciseId
        val choices=Disk().choices
        val wrong=choices.save(owner,id,true,"different-gym",0,
            mapOf(exercise to "machine-a"))
        assertTrue(wrong.toString(),
            wrong is NativeWorkoutMachineChoiceResult.Saved)
        val expected=NativeKotlinOwnerIdentity(owner,athlete)
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.MACHINE_CHOICES_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(temp.root.toPath(),expected))
        // A valid separate record for a workout never issued in this
        // program is also disallowed; otherwise it could silently mask
        // missing/foreign machine history during restore.
        val nonexistent=choices.save(owner,"orphan-workout",true,
            issued.originalSnapshot.stamp.gymId,0,
            mapOf(exercise to "machine-b"))
        assertTrue(nonexistent.toString(),
            nonexistent is NativeWorkoutMachineChoiceResult.Saved)
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.MACHINE_CHOICES_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(temp.root.toPath(),expected))
    }

    @Test fun stagedAuditCannotSilentlyReenableCorruptCoachingOptOut() {
        val (cycle,inputs)=saveGenerated()
        writeLocalAthleteForRestoreProof()
        val expected=NativeKotlinOwnerIdentity(owner,athlete)
        assertTrue(NativeKotlinStagedCoreStoreAudit.inspect(
            temp.root.toPath(),expected) is NativeKotlinStagedCoreAuditResult.Verified)
        assertTrue(Disk().settings.save(owner,0,
            CoachAssistancePolicy(false,false,false))
            is NativeCoachSettingsWrite.Saved)
        val saved=NativeKotlinStagedCoreStoreAudit.inspect(
            temp.root.toPath(),expected)
        assertTrue(saved.toString(),
            saved is NativeKotlinStagedCoreAuditResult.Verified)
        assertEquals(1L,(saved as NativeKotlinStagedCoreAuditResult.Verified)
            .coachingSettingsRevision)
        val file=java.nio.file.Files.list(temp.root.toPath()).use {stream->
            stream.filter {it.fileName.toString().startsWith("coach-assistance-")}
                .findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(file,bytes)
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.COACH_SETTINGS_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(temp.root.toPath(),expected))
        assertArrayEquals(bytes,java.nio.file.Files.readAllBytes(file))
    }

    @Test fun stagedAuditFailsClosedOnOrphanPendingMachineFile() {
        saveGenerated()
        writeLocalAthleteForRestoreProof()
        val suspicious=temp.root.toPath().resolve(
            "workout-machine-"+
            "a".repeat(64)+".pending")
        java.nio.file.Files.write(suspicious,byteArrayOf(1,2,3))
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.MACHINE_CHOICES_UNVERIFIED),
            NativeKotlinStagedCoreStoreAudit.inspect(
                temp.root.toPath(),NativeKotlinOwnerIdentity(owner,athlete)))
        assertArrayEquals(byteArrayOf(1,2,3),
            java.nio.file.Files.readAllBytes(suspicious))
    }

    @Test fun stagedAuditRejectsCorruptedUnfinishedCustomJournalWithoutTouchingIt() {
        val (cycle,inputs)=saveGenerated()
        writeLocalAthleteForRestoreProof()
        val id="corrupt-first-day"
        val day=cycle.blocks.single().days.first()
        assertTrue(Disk().prepared.prepare(owner,id,cycleId,1,day.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        assertTrue(Disk().journals.issue(owner,id,cycleId,1,day.weekday,inputs)
            is NativeCustomJournalResult.Durable)
        val path=java.nio.file.Files.list(temp.root.toPath()).use {stream->
            stream.filter{it.fileName.toString().startsWith("issued-custom-")}
                .findFirst().orElseThrow()
        }
        val contents=java.nio.file.Files.readAllBytes(path)
        contents[contents.lastIndex]=(contents.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(path,contents)
        val review=NativeKotlinStagedCoreStoreAudit.inspect(
            temp.root.toPath(),NativeKotlinOwnerIdentity(owner,athlete))
        assertEquals(NativeKotlinStagedCoreAuditResult.Rejected(
            NativeKotlinStagedCoreRefusal.CUSTOM_INVENTORY_UNVERIFIED),review)
        assertArrayEquals(contents,java.nio.file.Files.readAllBytes(path))
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

    @Test fun generatedWorkoutRestReadsDurableReceiptsAndNeverRestartsFromCorrections() {
        val (cycle,inputs)=saveGenerated()
        val day=cycle.blocks.single().days.first()
        val id="rest-first-day"
        assertTrue(Disk().prepared.prepare(owner,id,cycleId,1,day.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        var journal=(Disk().journals.issue(owner,id,cycleId,1,day.weekday,inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        val original=journal.snapshot
        fun projection(j:CustomWorkoutJournalDraft)=KotlinWorkoutRestDomain.inspect(
            j,CustomWorkoutJournalDomain.nextUnresolvedVisit(j))
        assertNull(projection(journal).automaticCue)
        val target=original.slots.first().sets.first()
        val load=target.prescription.load as? DraftSetLoad.Verified
        val event=CustomWorkoutJournalOperation.Record("rest-saved",0,
            CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(
                load?.value,load?.unit,target.prescription.reps.minimum,null))
        assertTrue(Disk().journals.record(owner,id,event) is NativeCustomJournalResult.Durable)
        journal=(Disk().journals.open(owner,id) as NativeCustomJournalResult.Durable)
            .issued.journal
        val expected=KotlinWorkoutRestCue(event.eventId,target.prescription.restSeconds,0)
        assertEquals(expected,projection(journal).automaticCue)
        // A retry is the same event; the timer consumes its ID once rather
        // than interpreting another native acknowledgment as more exercise.
        val retry=Disk().journals.record(owner,id,event) as NativeCustomJournalResult.Durable
        assertEquals(projection(journal),projection(retry.issued.journal))
        val correction=event.copy(eventId="rest-corrected",expectedRevision=journal.revision,
            correctsEventId=event.eventId,performed=event.performed!!.copy(reps=
                target.prescription.reps.minimum+1))
        assertTrue(Disk().journals.record(owner,id,correction) is NativeCustomJournalResult.Durable)
        val cold=(Disk().journals.open(owner,id) as NativeCustomJournalResult.Durable)
            .issued.journal
        assertEquals("rest-corrected",projection(cold).latestReceiptEventId)
        assertNull(projection(cold).automaticCue)
        assertEquals(original,cold.snapshot)
        assertNull((cold.effectiveVisits.getValue(event.visit).operation
            as CustomWorkoutJournalOperation.Record).performed?.actualRir)
    }

    @Test fun generatedWorkoutUsesColdSavedSetsAndCurrentOptOutForLiveAdvice() {
        val (cycle,inputs)=saveGenerated()
        val day=cycle.blocks.single().days.first()
        val id="live-first-day"
        assertTrue(Disk().prepared.prepare(owner,id,cycleId,1,day.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        var journal=(Disk().journals.issue(owner,id,cycleId,1,day.weekday,inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        val original=journal.snapshot
        val slot=original.slots.first {slot->
            val work=slot.sets.filter{it.prescription.type==DraftSetType.WORKING}
            work.size>=2 && (work.first().prescription.load as? DraftSetLoad.Verified)
                ?.basis==LoadBasis.DUMBBELL_PER_HAND
        }
        val work=slot.sets.filter{it.prescription.type==DraftSetType.WORKING}
        val first=work[0];val next=work[1]
        val load=first.prescription.load as DraftSetLoad.Verified
        // Reopen after every append as a restarted phone would. Warmups are
        // actual independent records, not assumed complete for the coach.
        for(warmup in slot.sets.takeWhile{it.prescription.type==DraftSetType.WARMUP}) {
            val weight=warmup.prescription.load as DraftSetLoad.Verified
            journal=(Disk().journals.record(owner,id,CustomWorkoutJournalOperation.Record(
                "live-warmup-${journal.revision}",journal.revision,
                CustomWorkoutVisitKey(warmup.setKey,CustomWorkoutVisitPart.MAIN),
                KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(
                    weight.value,weight.unit,warmup.prescription.reps.minimum,null)))
                as NativeCustomJournalResult.Durable).issued.journal
        }
        val hard=CustomWorkoutJournalOperation.Record("live-hard",journal.revision,
            CustomWorkoutVisitKey(first.setKey,CustomWorkoutVisitPart.MAIN),
            KotlinJournalSetOutcome.COMPLETED,KotlinPerformedSet(
                load.value,load.unit,first.prescription.reps.minimum-1,0))
        journal=(Disk().journals.record(owner,id,hard)
            as NativeCustomJournalResult.Durable).issued.journal
        fun look()=Disk().liveCoach.review(owner,id,next.setKey,
            CoachReadiness.NORMAL,false,n("5"))
        val advice=look() as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,advice.coaching.direction)
        assertEquals(load.value-n("5"),advice.coaching.weightForHumanReview)
        assertEquals(journal.revision,advice.journalRevision)
        assertFalse(advice.canModifyWorkout)
        // Introducing physical A/B stations after an unassigned set must
        // not reinterpret that set's actuals as station A or B fatigue.
        // With no pinned machine and no same-station observations, the
        // entire live review is unavailable, not merely the load step.
        val station=KotlinGymMachineAlternatives("gym-1",slot.exerciseId,"A",
            listOf(KotlinPhysicalMachine("A","Station A"),
                KotlinPhysicalMachine("B","Station B")))
        assertTrue(Disk().machines.save(owner,0,listOf(station))
            is NativeMachineRosterResult.Saved)
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.PHYSICAL_MACHINE_HISTORY_MISMATCH),
            look())
        val rosterFile = java.nio.file.Files.list(Disk().root).use { files ->
            files.filter { it.fileName.toString().startsWith("machine-roster-") &&
                it.fileName.toString().endsWith(".bin") }.findFirst().get()
        }
        val savedRoster = java.nio.file.Files.readAllBytes(rosterFile)
        java.nio.file.Files.write(rosterFile, byteArrayOf(1, 2, 3))
        assertNull(look())
        assertEquals(journal, (Disk().journals.open(owner, id)
            as NativeCustomJournalResult.Durable).issued.journal)
        java.nio.file.Files.write(rosterFile, savedRoster)
        assertTrue(Disk().machines.save(owner,1,listOf(
            KotlinGymMachineAlternatives("gym-1",slot.exerciseId,null,emptyList())))
            is NativeMachineRosterResult.Saved)
        // An empty alternatives draft must not take generic equipment
        // inventory away from a lift that has no physical A/B station.
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            (look() as LiveSetCoachResult.Reviewed).coaching.direction)
        assertTrue(Disk().machines.save(owner,2,emptyList())
            is NativeMachineRosterResult.Saved)
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            (look() as LiveSetCoachResult.Reviewed).coaching.direction)
        val corrected=hard.copy(eventId="live-correction",expectedRevision=journal.revision,
            performed=hard.performed!!.copy(reps=first.prescription.reps.minimum,
                actualRir=3),correctsEventId=hard.eventId)
        journal=(Disk().journals.record(owner,id,corrected)
            as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(InWorkoutDirection.BUILD_REPS_AS_PLANNED,
            (look() as LiveSetCoachResult.Reviewed).coaching.direction)
        assertEquals(original,journal.snapshot)
        assertTrue(Disk().settings.save(owner,0,CoachAssistancePolicy.PROGRESSION_ONLY)
            is NativeCoachSettingsWrite.Saved)
        assertEquals(LiveSetCoachResult.Unavailable(LiveSetCoachUnavailable.ASSISTANCE_DISABLED),look())
        assertTrue(Disk().settings.save(owner,1,CoachAssistancePolicy.MANUAL)
            is NativeCoachSettingsWrite.Saved)
        assertEquals(LiveSetCoachResult.Unavailable(LiveSetCoachUnavailable.ASSISTANCE_DISABLED),look())
        assertTrue(Disk().settings.save(owner,2,CoachAssistancePolicy.STANDARD)
            is NativeCoachSettingsWrite.Saved)
        // Even a physically identical new gym-source revision cannot lend
        // authority to an older workout's suggestion.
        val old=(Disk().sources.read(owner) as NativeAuthoringSourceRead.Found).source
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            Disk().sources.publish(owner,1,old.copy(revision=2)))
        val stale=look() as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.VERIFY_PLANNED_EQUIPMENT,stale.coaching.direction)
        assertNull(stale.coaching.weightForHumanReview)
        assertEquals(original,(Disk().journals.open(owner,id)
            as NativeCustomJournalResult.Durable).issued.journal.snapshot)
        java.nio.file.Files.list(Disk().root).use {files->
            val settingsFile=files.filter {
                it.fileName.toString().startsWith("coach-assistance-") &&
                    it.fileName.toString().endsWith(".bin")
            }.findFirst().get()
            java.nio.file.Files.write(settingsFile,byteArrayOf(1,2,3))
        }
        assertNull(look()) // Corrupt preferences never turn coaching back on.
        assertTrue(Disk().journals.record(owner,id,corrected.copy(
            eventId="live-logging-still-works",expectedRevision=journal.revision,
            correctsEventId=corrected.eventId)) is NativeCustomJournalResult.Durable)
    }

    @Test fun hardSetAdviceCannotRewriteFourOrMoreWorkingSetsOrNextSession() {
        val (cycle, inputs) = saveGenerated()
        val day = cycle.blocks.single().days.minBy { it.weekday }
        val workoutId = "load-stability-four-plus"
        val disk = Disk()
        assertTrue(disk.prepared.prepare(owner, workoutId, cycleId, 1,
            day.weekday, inputs) is NativeCustomPrepareResult.Prepared)
        var journal = (Disk().journals.issue(owner, workoutId, cycleId, 1,
            day.weekday, inputs) as NativeCustomJournalResult.Durable).issued.journal
        val issued = journal.snapshot
        val allSets = issued.slots.flatMap { it.sets }
        assertTrue("A real generated day must contain at least four working sets",
            allSets.count { it.prescription.type == DraftSetType.WORKING } >= 4)
        val selected = issued.slots.first { slot ->
            val work = slot.sets.filter { it.prescription.type == DraftSetType.WORKING }
            work.size >= 2 && (work.first().prescription.load as? DraftSetLoad.Verified)
                ?.basis == LoadBasis.DUMBBELL_PER_HAND
        }
        val work = selected.sets.filter { it.prescription.type == DraftSetType.WORKING }
        val hard = work.first()
        val futureWorking = work[1]
        val intendedLoads = allSets.associate { it.setKey to it.prescription.load }
        var adviceChecked = false
        for ((index, set) in allSets.withIndex()) {
            val prescription = set.prescription
            val isHard = set.setKey == hard.setKey
            val reportedReps = if (isHard) prescription.reps.minimum - 1
                else prescription.reps.minimum
            assertTrue(reportedReps >= 0)
            val actual = when (val load = prescription.load) {
                is DraftSetLoad.Verified ->
                    KotlinPerformedSet(load.value, load.unit, reportedReps,
                        if (isHard) 0 else if (prescription.type == DraftSetType.WORKING)
                            2 else null)
                DraftSetLoad.ExplicitBodyweight ->
                    KotlinPerformedSet(null, null, reportedReps,
                        if (isHard) 0 else if (prescription.type == DraftSetType.WORKING)
                            2 else null)
            }
            journal = (Disk().journals.record(owner, workoutId,
                CustomWorkoutJournalOperation.Record(
                    "load-proof-$index", journal.revision,
                    CustomWorkoutVisitKey(set.setKey, CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED, actual))
                as NativeCustomJournalResult.Durable).issued.journal
            // A lost process/restart must not change targets, even after
            // near-failure actuals are durably recorded.
            val cold = (Disk().journals.open(owner, workoutId)
                as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(issued, cold.snapshot)
            assertEquals(intendedLoads, cold.snapshot.slots.flatMap { it.sets }
                .associate { it.setKey to it.prescription.load })
            assertEquals(actual, (cold.effectiveVisits.getValue(
                CustomWorkoutVisitKey(set.setKey, CustomWorkoutVisitPart.MAIN))
                .operation as CustomWorkoutJournalOperation.Record).performed)
            if (isHard) {
                val review = Disk().liveCoach.review(owner, workoutId,
                    futureWorking.setKey, CoachReadiness.NORMAL, false, n("5"))
                assertTrue(review.toString(), review is LiveSetCoachResult.Reviewed)
                val suggested = review as LiveSetCoachResult.Reviewed
                assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
                    suggested.coaching.direction)
                assertFalse(suggested.canModifyWorkout)
                assertEquals(issued, (Disk().journals.open(owner, workoutId)
                    as NativeCustomJournalResult.Durable).issued.journal.snapshot)
                adviceChecked = true
            }
        }
        assertTrue(adviceChecked)
        assertEquals(0, journal.unresolvedVisitCount)
        val finish = NativeKotlinCustomHistoryTransaction(
            Disk().history, Disk().journals, Disk().fence, identity).complete(
                owner, athlete, workoutId,
                CustomWorkoutJournalOperation.Finish(
                    "load-proof-finish", journal.revision))
        assertTrue(finish.toString(), finish is NativeCustomFinishResult.Committed)
        val finished = (Disk().journals.open(owner, workoutId)
            as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(issued, finished.snapshot)
        assertEquals(allSets.size, finished.effectiveVisits.size)
        val history = (Disk().timeline.read(owner, athlete)
            as NativeKotlinMixedHistoryReadResult.Review).timeline
        val logged = history.completions.single() as NativeKotlinMixedHistoryCompletion.Custom
        assertEquals(workoutId, logged.evidence.workoutId)
        assertEquals(allSets.size, logged.evidence.visits.count {
            it.part == CustomWorkoutVisitPart.MAIN
        })
        // Logged fatigue is historical evidence, never a write to the signed
        // cycle or a retroactive change in the next untrained day.
        assertEquals(cycle, (Disk().drafts.read(owner, cycleId)
            as NativeCustomDraftReadResult.Found).draft)
        val second = cycle.blocks.single().days.sortedBy { it.weekday }[1]
        assertEquals(NativeCustomNextCycleSlot.Next(1, second.weekday,
            second.label), Disk().next(inputs))
        val nextWeek = (Disk().preflight.inspect(owner, cycleId, 2,
            day.weekday, inputs) as NativeCustomPreflightResult.ReviewedPreview).session
        assertEquals(cycle.blocks.single().days.first { it.weekday == day.weekday }
            .slots.map { it.exerciseId }, nextWeek.slots.map { it.exerciseId })
    }

    @Test fun levelingUsesActualPyramidAndNeverRewritesSignedProgramOrOtherCompletedSets() {
        val (cycle,inputs)=saveGenerated()
        val days=cycle.blocks.single().days.sortedBy{it.weekday}
        val first=days.first()
        val selected=first.slots.first{slot->
            val working=slot.sets.filter{it.type==DraftSetType.WORKING}
            working.size in 2..12 && working.all{
                val load=it.load as? DraftSetLoad.Verified
                load?.basis==LoadBasis.DUMBBELL_PER_HAND && it.amplifier==null &&
                    it.targetRir!=null && it.reps==working.first().reps &&
                    it.targetRir==working.first().targetRir}}
        val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            (5..50 step 5).map{BigDecimal(it)})
        val originals=linkedMapOf<String,CustomWorkoutDaySnapshot>()
        val pyramidId="leveling-w1-0"
        for((dayIndex,day) in days.withIndex()){
            val id="leveling-w1-$dayIndex"
            assertTrue(Disk().prepared.prepare(owner,id,cycleId,1,day.weekday,inputs)
                is NativeCustomPrepareResult.Prepared)
            var journal=(Disk().journals.issue(owner,id,cycleId,1,day.weekday,inputs)
                as NativeCustomJournalResult.Durable).issued.journal
            val original=journal.originalSnapshot
            val pyramid=original.slots.singleOrNull{it.slotId==selected.slotId}
                ?.sets?.filter{it.prescription.type==DraftSetType.WORKING}.orEmpty()
            for((i,set) in original.slots.flatMap{it.sets}.withIndex()){
                val p=set.prescription
                val ordinal=if(dayIndex==0)pyramid.indexOf(set) else -1
                val actual=when(val load=p.load){
                    is DraftSetLoad.Verified -> KotlinPerformedSet(
                        if(ordinal<0)load.value else n(if(ordinal==0)"20" else "25"),
                        load.unit,p.reps.minimum,if(ordinal<0)null else p.targetRir)
                    DraftSetLoad.ExplicitBodyweight -> KotlinPerformedSet(null,null,p.reps.minimum,null)
                }
                journal=(Disk().journals.record(owner,id,
                    CustomWorkoutJournalOperation.Record("leveling-log-$i",journal.revision,
                        CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                        KotlinJournalSetOutcome.COMPLETED,actual)
                    ) as NativeCustomJournalResult.Durable).issued.journal
            }
            assertEquals(0,journal.unresolvedVisitCount)
            val finish=Disk()
            assertTrue(NativeKotlinCustomHistoryTransaction(finish.history,finish.journals,
                finish.fence,identity).complete(owner,athlete,id,
                CustomWorkoutJournalOperation.Finish("leveling-finish",journal.revision))
                is NativeCustomFinishResult.Committed)
            originals[id]=original
        }
        assertEquals(NativeCustomNextCycleSlot.Next(2,first.weekday,first.label),Disk().next(inputs))
        val id="leveling-w2"
        assertTrue(Disk().prepared.prepare(owner,id,cycleId,2,first.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        val issued=(Disk().journals.issue(owner,id,cycleId,2,first.weekday,inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        val next=issued.originalSnapshot
        val sets=next.slots.single{it.slotId==selected.slotId}.sets
            .filter{it.prescription.type==DraftSetType.WORKING}
        val keys=sets.map{WorkoutReferenceKey(it.setKey) to it.prescription.load}
        fun history():List<CustomCoachingHistoricalSession> {
            val cold=Disk()
            val timeline=(cold.timeline.read(owner,athlete) as NativeKotlinMixedHistoryReadResult.Review).timeline
            return timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>().map{
                CustomCoachingHistoricalSession(it.manifest.completionOrder,
                    (cold.journals.open(owner,it.manifest.workoutId) as NativeCustomJournalResult.Durable)
                        .issued.journal.originalSnapshot,it.evidence)}
        }
        val completed=history()
        val references=WorkoutLastReferenceDomain.custom(id,next,completed)
        val prior=DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(keys,references,null,
            sets.first().prescription.reps,checkNotNull(sets.first().prescription.targetRir),stock)
            as LevelingReviewResult.Candidate
        assertEquals(n("25"),prior.proposedStraightSetLoad)
        assertEquals(sets.first().prescription.reps.minimum,prior.repsPerSet)
        assertFalse(prior.mayModifyIssuedWorkout)
        // TODAY editor review includes a completed first row but cannot edit it.
        val rows=sets.mapIndexed{i,set->CurrentWorkoutLevelingRow(set.setKey,
            n(if(i==0)"20" else "25"),set.prescription.reps.minimum,i==0,
            LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,null)}
        val today=DoubleProgressionLevelingDomain.reviewCurrentWorkout(rows,stock)
            as CurrentWorkoutLevelingReview.Ready
        assertEquals(n("25"),today.roundedLoad)
        assertEquals(prior.averageObservedLoad,today.rawMean)
        assertEquals(sets.drop(1).map{it.setKey},today.eligibleSetKeys)
        // Reviews are not journal operations. No pending row or completed
        // historical receipt has changed before normal explicit logging.
        assertEquals(issued,(Disk().journals.open(owner,id) as NativeCustomJournalResult.Durable).issued.journal)
        assertEquals(completed,history())
        val old=originals.getValue(pyramidId).slots.single{it.slotId==selected.slotId}
            .sets.first{it.prescription.type==DraftSetType.WORKING}
        val oldJournal=(Disk().journals.open(owner,pyramidId) as NativeCustomJournalResult.Durable).issued.journal
        val visit=CustomWorkoutVisitKey(old.setKey,CustomWorkoutVisitPart.MAIN)
        val record=oldJournal.effectiveVisits.getValue(visit).operation as CustomWorkoutJournalOperation.Record
        val correction=CustomSavedVisitCorrection("leveling-correction",0,visit,record.eventId,
            KotlinJournalSetOutcome.COMPLETED,checkNotNull(record.performed).copy(load=n("25")))
        val d=Disk()
        assertTrue(NativeKotlinCustomHistoryCorrectionTransaction(d.history,d.journals,d.corrections,
            d.fence,identity).correct(owner,athlete,pyramidId,correction)
            is NativeCustomCorrectionTransactionResult.Committed)
        val revised=WorkoutLastReferenceDomain.custom(id,next,history())
        assertEquals(LevelingReviewResult.Rejected(LevelingRefusal.ALREADY_LEVEL),
            DoubleProgressionLevelingDomain.reviewLastForCurrentWorkout(keys,revised,null,
                sets.first().prescription.reps,checkNotNull(sets.first().prescription.targetRir),stock))
        assertEquals(issued,(Disk().journals.open(owner,id) as NativeCustomJournalResult.Durable).issued.journal)
        assertEquals(cycle,(Disk().drafts.read(owner,cycleId) as NativeCustomDraftReadResult.Found).draft)
        for((oldId,snapshot) in originals)
            assertEquals(snapshot,(Disk().journals.open(owner,oldId) as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
        val later=(Disk().preflight.inspect(owner,cycleId,3,first.weekday,inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        assertEquals(sets.map{it.prescription},later.slots.single{it.slotId==selected.slotId}
            .sets.filter{it.prescription.type==DraftSetType.WORKING}.map{it.prescription})
    }

    @Test fun completedBasicProgramEarnsReviewedWeekTwoLoadsOnlyAfterExplicitConsent() =
        completeBasicOrManualWeek(false)

    @Test fun generatedManualMethodAndCoachingOffPreserveCorrectedHistoryAndNextTargets() =
        completeBasicOrManualWeek(true)

    @Test fun correctedGeneratedDoubleProgressionRevokesAnEarnedIncreaseWithoutChangingIssuedTargets() =
        completeBasicOrManualWeek(manual=false,correctBeforeApproval=true)

    @Test fun generatedExactRepTargetsEarnOnlyOneRepWithoutChangingWeights() =
        completeBasicOrManualWeek(false,repMethod=SavedProgressionMethod.REP_TARGETS)

    @Test fun generatedRepLadderEarnsAdjacentWeightAndResetsToItsActualRepFloor() =
        completeBasicOrManualWeek(false,repMethod=SavedProgressionMethod.LADDER)

    @Test fun generatedBodyweightRepProgressionUsesNoLoadAndNoAuthorizedWeightStep() =
        completeBasicOrManualWeek(false,repMethod=SavedProgressionMethod.BODYWEIGHT_REPS)

    private fun completeBasicOrManualWeek(manual:Boolean,correctBeforeApproval:Boolean=false,
        repMethod:SavedProgressionMethod?=null) {
        require(repMethod==null || repMethod in setOf(SavedProgressionMethod.REP_TARGETS,
            SavedProgressionMethod.LADDER,SavedProgressionMethod.BODYWEIGHT_REPS) &&
            !manual && !correctBeforeApproval)
        val unloaded=repMethod==SavedProgressionMethod.BODYWEIGHT_REPS
        val (base, inputs) = if(unloaded)saveGenerated(split=ComposerSplit.FULL_BODY,includeNewTargets=true)
            else saveGenerated()
        assertTrue(Disk().settings.save(owner,0,CoachAssistancePolicy.MANUAL)
            is NativeCoachSettingsWrite.Saved)
        val days = base.blocks.single().days.sortedBy { it.weekday }
        fun unloadedEligible(slot:CustomExerciseSlot)=
            slot.sets.isNotEmpty() && slot.sets.all{it.type==DraftSetType.WORKING &&
                it.load==DraftSetLoad.ExplicitBodyweight && it.amplifier==null &&
                it.reps.minimum<it.reps.maximum} &&
                slot.sets.map{it.reps}.distinct().size==1
        val firstDay = if(unloaded)days.first{day->day.slots.any(::unloadedEligible)}
            else days.first()
        val selected = firstDay.slots.first { slot ->
            if(unloaded)unloadedEligible(slot) else slot.progressionMethod in setOf(SavedProgressionMethod.AUTO,
                SavedProgressionMethod.DOUBLE, SavedProgressionMethod.DYNAMIC,
                SavedProgressionMethod.LINEAR) &&
                slot.sets.filter { it.type == DraftSetType.WORKING }.let { work ->
                    work.size >= 2 && work.all {
                        val load = it.load as? DraftSetLoad.Verified
                        load?.basis == LoadBasis.DUMBBELL_PER_HAND &&
                            it.amplifier == null &&
                            (!(correctBeforeApproval || repMethod!=null) ||
                                it.reps.minimum<it.reps.maximum)
                    } && (!(correctBeforeApproval || repMethod!=null) ||
                        work.map{it.load}.distinct().size==1 &&
                        work.map{it.reps}.distinct().size==1)
                }
        }
        val cycle=if(!manual && !correctBeforeApproval && repMethod==null)base else {
            val block=base.blocks.single()
            val changed=base.copy(revision=base.revision+1,blocks=listOf(block.copy(
                days=block.days.map{day->if(day.weekday!=firstDay.weekday)day else
                    day.copy(slots=day.slots.map{slot->if(slot.slotId!=selected.slotId)slot else
                        slot.copy(progressionMethod=repMethod ?: if(manual)SavedProgressionMethod.MANUAL
                            else SavedProgressionMethod.DOUBLE,
                            sets=if(repMethod==null)slot.sets else slot.sets.map{set->
                                if(set.type!=DraftSetType.WORKING)set else set.copy(
                                    exactRepTarget=if(repMethod==SavedProgressionMethod.LADDER)
                                        set.reps.maximum else set.reps.minimum)
                            })})})))
            assertTrue(Disk().drafts.save(owner,cycleId,base.revision,changed)
                is NativeCustomDraftWriteResult.Committed)
            assertTrue(Disk().prepared.prepare(owner,"unapproved-manual",cycleId,1,
                firstDay.weekday,inputs) is NativeCustomPrepareResult.Rejected)
            assertTrue(Disk().consent.confirm(owner,cycleId,changed.revision,1,1,inputs,true)
                is NativeCustomSignoffWrite.Committed)
            changed
        }
        val finishedWeekOne = linkedMapOf<String, CustomWorkoutDaySnapshot>()
        for ((dayIndex, day) in days.withIndex()) {
            val workoutId = "earned-basic-w1-$dayIndex"
            assertTrue(Disk().prepared.prepare(owner, workoutId, cycleId,
                1, day.weekday, inputs) is NativeCustomPrepareResult.Prepared)
            var journal = (Disk().journals.issue(owner, workoutId, cycleId,
                1, day.weekday, inputs) as NativeCustomJournalResult.Durable).issued.journal
            val immutable = journal.snapshot
            for ((setIndex, set) in immutable.slots.flatMap { it.sets }.withIndex()) {
                val prescription = set.prescription
                val earned = day.weekday == firstDay.weekday &&
                    set.slotId == selected.slotId &&
                    prescription.type == DraftSetType.WORKING
                val performedReps = if (earned) prescription.exactRepTarget ?: prescription.reps.maximum
                    else prescription.exactRepTarget ?: prescription.reps.minimum
                val performed = when (val load = prescription.load) {
                    is DraftSetLoad.Verified ->
                        KotlinPerformedSet(load.value, load.unit, performedReps, null)
                    DraftSetLoad.ExplicitBodyweight ->
                        KotlinPerformedSet(null, null, performedReps, null)
                }
                journal = (Disk().journals.record(owner, workoutId,
                    CustomWorkoutJournalOperation.Record(
                        "earned-w1-$dayIndex-$setIndex", journal.revision,
                        CustomWorkoutVisitKey(set.setKey, CustomWorkoutVisitPart.MAIN),
                        KotlinJournalSetOutcome.COMPLETED, performed))
                    as NativeCustomJournalResult.Durable).issued.journal
                assertEquals(immutable, (Disk().journals.open(owner, workoutId)
                    as NativeCustomJournalResult.Durable).issued.journal.snapshot)
            }
            assertEquals(0, journal.unresolvedVisitCount)
            assertTrue(NativeKotlinCustomHistoryTransaction(
                Disk().history, Disk().journals, Disk().fence, identity).complete(
                owner, athlete, workoutId,
                CustomWorkoutJournalOperation.Finish(
                    "earned-finish-$dayIndex", journal.revision))
                is NativeCustomFinishResult.Committed)
            finishedWeekOne[workoutId] = immutable
        }
        assertEquals(NativeCustomNextCycleSlot.Next(2, days.first().weekday,
            days.first().label), Disk().next(inputs))

        val signedReader = Disk()
        val basic = NativeKotlinCustomBasicNextReview(
            signedReader.fence, identity, signedReader.drafts,
            signedReader.sources, signedReader.preflight, signedReader.timeline,
            signedReader.journals, signedReader.machines)
        val review = basic.reviewAuthored(owner, cycleId, inputs,
            2, firstDay.weekday, selected.slotId, "earned-basic-future",
            if(unloaded)BigDecimal.ZERO else n("5")) as NativeCustomBasicNextReadResult.Reviewed
        assertEquals(cycle.revision, review.next.stamp.cycleRevision)
        if(unloaded){
            val projection=review.projection as CustomBasicHistoryResult.UnloadedReviewed
            assertEquals("earned-basic-w1-${days.indexOf(firstDay)}",projection.historyWorkoutId)
            val work=review.next.slots.single{it.slotId==selected.slotId}.sets
                .map{it.prescription}
            assertTrue(work.all{it.load==DraftSetLoad.ExplicitBodyweight})
            assertEquals(work.map{it.reps.minimum+1},projection.candidate.proposedExactReps)
            assertEquals(work.indices.toSet(),projection.candidate.advancedOrdinals)
            val old=(Disk().timeline.read(owner,athlete) as NativeKotlinMixedHistoryReadResult.Review)
                .timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                .single{it.manifest.workoutId==projection.historyWorkoutId}
            val facts=old.evidence.visits.filter{it.slotId==selected.slotId}
            assertEquals(work.size,facts.size)
            assertTrue(facts.all{it.prescribedLoad==DraftSetLoad.ExplicitBodyweight &&
                it.actual!=null && it.actual?.load==null && it.actual?.unit==null &&
                it.actual?.actualRir==null && it.actual?.machineInstanceId==null})
        } else {
        val projection = review.projection as CustomBasicHistoryResult.Reviewed
        if(manual) {
            val expected=selected.sets.filter{it.type==DraftSetType.WORKING}
                .map{(it.load as DraftSetLoad.Verified).value}
            fun verifyManual(read:NativeCustomBasicNextReadResult.Reviewed) {
                val p=read.projection as CustomBasicHistoryResult.Reviewed
                assertEquals(BasicProgressionDecision.MANUAL_REPEAT,p.candidate.decision)
                assertEquals(expected,p.candidate.proposedWorkingLoads)
                assertTrue(p.candidate.increasedOrdinals.isEmpty())
                assertNull(p.historyWorkoutId)
                assertFalse(read.mayIssueWorkout)
                assertFalse(read.maySaveProgram)
            }
            verifyManual(review)
            val id="earned-basic-w1-0"
            val old=(Disk().journals.open(owner,id) as NativeCustomJournalResult.Durable).issued.journal
            val target=old.snapshot.slots.single{it.slotId==selected.slotId}.sets
                .first{it.prescription.type==DraftSetType.WORKING}
            val visit=CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN)
            val recorded=old.effectiveVisits.getValue(visit).operation as CustomWorkoutJournalOperation.Record
            val correction=CustomSavedVisitCorrection("manual-corrected",0,visit,recorded.eventId,
                KotlinJournalSetOutcome.COMPLETED,
                checkNotNull(recorded.performed).copy(reps=target.prescription.reps.minimum,actualRir=0))
            val d=Disk()
            assertTrue(NativeKotlinCustomHistoryCorrectionTransaction(d.history,d.journals,
                d.corrections,d.fence,identity).correct(owner,athlete,id,correction)
                is NativeCustomCorrectionTransactionResult.Committed)
            val facts=(Disk().timeline.read(owner,athlete) as NativeKotlinMixedHistoryReadResult.Review)
                .timeline.completions.first() as NativeKotlinMixedHistoryCompletion.Custom
            assertEquals(correction.performed,facts.evidence.visits.single{it.setKey==target.setKey}.actual)
            val fresh=Disk()
            val after=NativeKotlinCustomBasicNextReview(fresh.fence,identity,fresh.drafts,
                fresh.sources,fresh.preflight,fresh.timeline,fresh.journals,fresh.machines)
                .reviewAuthored(owner,cycleId,inputs,2,firstDay.weekday,selected.slotId,
                    "manual-next",BigDecimal.ZERO) as NativeCustomBasicNextReadResult.Reviewed
            assertTrue(after.historyRevision>review.historyRevision)
            verifyManual(after)
            assertEquals(review.next,after.next)
            assertTrue(Disk().prepared.prepare(owner,"manual-next",cycleId,2,firstDay.weekday,inputs)
                is NativeCustomPrepareResult.Prepared)
            val issued=Disk().journals.issue(owner,"manual-next",cycleId,2,firstDay.weekday,inputs)
                as NativeCustomJournalResult.Durable
            assertEquals(after.next,issued.issued.journal.originalSnapshot)
            assertEquals(cycle,(Disk().drafts.read(owner,cycleId) as NativeCustomDraftReadResult.Found).draft)
            for((oldId,snapshot) in finishedWeekOne)
                assertEquals(snapshot,(Disk().journals.open(owner,oldId)
                    as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
            return
        }
        assertEquals("earned-basic-w1-0", projection.historyWorkoutId)
        if(repMethod==SavedProgressionMethod.REP_TARGETS) {
            val work=review.next.slots.single{it.slotId==selected.slotId}.sets
                .filter{it.prescription.type==DraftSetType.WORKING}.map{it.prescription}
            assertEquals(BasicProgressionDecision.EARNED_REP_TARGET,projection.candidate.decision)
            assertEquals(work.map{(it.load as DraftSetLoad.Verified).value},
                projection.candidate.proposedWorkingLoads)
            assertEquals(work.map{it.reps.minimum+1},projection.candidate.proposedRepTargets)
            assertEquals(work.indices.toSet(),projection.candidate.changedRepOrdinals)
            assertTrue(projection.candidate.increasedOrdinals.isEmpty())
            assertTrue(projection.candidate.approvedFutureLoadFacts.isEmpty())
        } else {
            assertTrue("Expected a verified physical next increment",
                projection.candidate.increasedOrdinals.isNotEmpty())
            if(repMethod==SavedProgressionMethod.LADDER){
                val work=review.next.slots.single{it.slotId==selected.slotId}.sets
                    .filter{it.prescription.type==DraftSetType.WORKING}.map{it.prescription}
                assertEquals(work.map{(it.load as DraftSetLoad.Verified).value+n("5")},
                    projection.candidate.proposedWorkingLoads)
                assertEquals(work.map{it.reps.minimum},projection.candidate.proposedRepTargets)
                assertEquals(work.indices.toSet(),projection.candidate.increasedOrdinals)
                assertEquals(work.indices.toSet(),projection.candidate.changedRepOrdinals)
            }
        }
        if(correctBeforeApproval) {
            val originalId="earned-basic-w1-0"
            val original=finishedWeekOne.getValue(originalId)
            val working=original.slots.single{it.slotId==selected.slotId}.sets
                .filter{it.prescription.type==DraftSetType.WORKING}
            assertEquals(working.size,projection.candidate.increasedOrdinals.size)
            val target=working.first()
            val visit=CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN)
            val old=(Disk().journals.open(owner,originalId) as NativeCustomJournalResult.Durable).issued.journal
            val actual=old.effectiveVisits.getValue(visit).operation as CustomWorkoutJournalOperation.Record
            val correction=CustomSavedVisitCorrection("double-corrected",0,visit,actual.eventId,
                KotlinJournalSetOutcome.COMPLETED,checkNotNull(actual.performed).copy(
                    reps=target.prescription.reps.minimum,actualRir=0))
            val d=Disk()
            assertTrue(NativeKotlinCustomHistoryCorrectionTransaction(d.history,d.journals,
                d.corrections,d.fence,identity).correct(owner,athlete,originalId,correction)
                is NativeCustomCorrectionTransactionResult.Committed)
            val cold=Disk()
            val timeline=(cold.timeline.read(owner,athlete) as NativeKotlinMixedHistoryReadResult.Review).timeline
            val corrected=timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                .single{it.manifest.workoutId==originalId}
            assertEquals(correction.performed,corrected.evidence.visits.single{it.setKey==target.setKey}.actual)
            val after=NativeKotlinCustomBasicNextReview(cold.fence,identity,cold.drafts,cold.sources,
                cold.preflight,cold.timeline,cold.journals,cold.machines)
                .reviewAuthored(owner,cycleId,inputs,2,firstDay.weekday,selected.slotId,
                    "earned-basic-future",n("5")) as NativeCustomBasicNextReadResult.Reviewed
            val held=after.projection as CustomBasicHistoryResult.Reviewed
            assertTrue(after.historyRevision>review.historyRevision)
            assertEquals(originalId,held.historyWorkoutId)
            assertTrue(held.candidate.increasedOrdinals.isEmpty())
            assertEquals(working.map{(it.prescription.load as DraftSetLoad.Verified).value},
                held.candidate.proposedWorkingLoads)
            assertEquals(review.next,after.next)
            assertFalse(after.maySaveProgram)
            assertFalse(after.mayIssueWorkout)
            assertEquals(cycle,(cold.drafts.read(owner,cycleId) as NativeCustomDraftReadResult.Found).draft)
            assertTrue(cold.prepared.prepare(owner,"held-double-next",cycleId,2,firstDay.weekday,inputs)
                is NativeCustomPrepareResult.Prepared)
            val issued=Disk().journals.issue(owner,"held-double-next",cycleId,2,firstDay.weekday,inputs)
                as NativeCustomJournalResult.Durable
            assertEquals(after.next,issued.issued.journal.originalSnapshot)
            for((id,snapshot) in finishedWeekOne)
                assertEquals(snapshot,(Disk().journals.open(owner,id)
                    as NativeCustomJournalResult.Durable).issued.journal.originalSnapshot)
            return
        }
        }
        val before = review.next.slots.single {
            it.slotId == selected.slotId
        }.sets.map { it.prescription }
        val offered = checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
            selected.slotId, before, review.projection))
        if(unloaded)assertTrue(offered.all{it.load==DraftSetLoad.ExplicitBodyweight})
        assertEquals(before.size, offered.size)
        assertEquals(before.filter { it.type == DraftSetType.WARMUP },
            offered.filter { it.type == DraftSetType.WARMUP })
        assertEquals(before.map { it.reps }, offered.map { it.reps })
        assertEquals(before.map { it.targetRir }, offered.map { it.targetRir })

        val compared = CustomCoachWeekEditDomain.review(
            cycle, cycle.revision, 2, firstDay.weekday, selected.slotId,
            offered, (Disk().sources.read(owner) as NativeAuthoringSourceRead.Found).source.setup,
            catalog, inputs)
        assertTrue(compared.toString(),
            compared is CustomCoachWeekEditResult.Reviewed)
        val approvedDraft = (compared as CustomCoachWeekEditResult.Reviewed)
            .review.proposal.draft
        assertEquals(cycle, (Disk().drafts.read(owner, cycleId)
            as NativeCustomDraftReadResult.Found).draft)
        val beforeConsent = (Disk().preflight.inspect(owner, cycleId,
            2, firstDay.weekday, inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        assertEquals(before, beforeConsent.slots.single {
            it.slotId == selected.slotId
        }.sets.map { it.prescription })
        val untouchedWeekThree = (Disk().preflight.inspect(owner, cycleId,
            3, firstDay.weekday, inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        // Reviewing the optional progression cannot issue or change a set.
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.maySaveProgram)
        assertFalse(compared.review.mayIssueWorkout)
        assertTrue(Disk().drafts.save(owner, cycleId,
            cycle.revision, approvedDraft) is NativeCustomDraftWriteResult.Committed)
        val obsolete = (Disk().consent.read(owner, cycleId, inputs)
            as NativeCustomSignoffRead.Found)
        assertFalse(obsolete.isCurrent)
        assertFalse(Disk().preflight.inspect(owner, cycleId,
            2, firstDay.weekday, inputs)
            is NativeCustomPreflightResult.ReviewedPreview)
        assertTrue(Disk().consent.confirm(owner, cycleId,
            approvedDraft.revision, 1, obsolete.receipt.recordRevision,
            inputs, true) is NativeCustomSignoffWrite.Committed)

        // Only this explicitly accepted week changes. A fresh preparation,
        // issue, and cold journal reopen must agree on all set prescriptions.
        val accepted = (Disk().preflight.inspect(owner, cycleId,
            2, firstDay.weekday, inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        assertEquals(offered, accepted.slots.single {
            it.slotId == selected.slotId
        }.sets.map { it.prescription })
        val verifiedWeekThree = (Disk().preflight.inspect(owner, cycleId,
            3, firstDay.weekday, inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        assertEquals(untouchedWeekThree.copy(
            stamp = untouchedWeekThree.stamp.copy(
                cycleRevision = approvedDraft.revision)), verifiedWeekThree)
        val id = "earned-basic-new-issue"
        assertTrue(Disk().prepared.prepare(owner, id, cycleId,
            2, firstDay.weekday, inputs) is NativeCustomPrepareResult.Prepared)
        val issued = (Disk().journals.issue(owner, id, cycleId,
            2, firstDay.weekday, inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(accepted, issued.snapshot)
        assertEquals(accepted, (Disk().journals.open(owner, id)
            as NativeCustomJournalResult.Durable).issued.journal.snapshot)
        for ((oldId, snapshot) in finishedWeekOne) {
            assertEquals(snapshot, (Disk().journals.open(owner, oldId)
                as NativeCustomJournalResult.Durable).issued.journal.snapshot)
        }
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
        val saved = (Disk().drafts.read(owner, cycleId) as NativeCustomDraftReadResult.Found).draft
        assertArrayEquals(NativeCustomCycleDraftCodec.encode(cycle), NativeCustomCycleDraftCodec.encode(saved))
        val day=cycle.blocks.single().days.first()
        val expected=(disk.preflight.inspect(owner,cycleId,1,day.weekday,inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        assertTrue(disk.prepared.prepare(owner,"old-first-day",cycleId,1,
            day.weekday,inputs) is NativeCustomPrepareResult.Prepared)
        val issued=(Disk().journals.issue(owner,"old-first-day",cycleId,1,
            day.weekday,inputs) as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(expected,issued.snapshot)
    }

    @Test fun serratusAndTibialisWeekLogsEveryBodyweightSetAndColdRestoresItsOwnTargets() {
        completeGeneratedWeek(ComposerSplit.FULL_BODY,includeNewTargets=true)
    }

    @Test fun generatedTrapBarProgramLogsEverySetFinishesAndColdReplaysWithTheCorrectBarMass() {
        completeGeneratedWeek(ComposerSplit.FULL_BODY,includeTrapBar=true)
    }

    @Test fun generatedFourDayProgramCompletesAllSetsAndColdReturnsToNextWeek() {
        completeGeneratedWeek(ComposerSplit.UPPER_LOWER)
    }

    @Test fun generatedWholeWeekKeepsRecordedCompletionDatesThroughCorrectionsAndColdHistory() {
        val first=java.time.Instant.parse("2026-10-31T23:50:00Z").toEpochMilli()
        var reads=0
        completeGeneratedWeek(ComposerSplit.UPPER_LOWER,
            completionClock=NativeHistoryCompletionClock{first+86400000L*reads++})
        assertEquals(4,reads)
        val ledger=(Disk().history.open(owner,athlete) as NativeHistoryLedgerResult.Durable).ledger
        val expected=(0..3).map{first+86400000L*it}
        assertEquals(expected,ledger.completions.map{it.completedAtEpochMillis})
        val timeline=(Disk().timeline.read(owner,athlete) as NativeKotlinMixedHistoryReadResult.Review).timeline
        assertEquals(expected,timeline.completions.map{it.manifest.completedAtEpochMillis})
        val csv=NativeKotlinVerifiedHistoryCsvExport.prepare(timeline) as NativeKotlinHistoryCsvResult.Ready
        assertTrue(csv.csv.lines().first().contains("completed_at_utc"))
        assertTrue(csv.csv.contains("2026-10-31T23:50:00Z"))
    }

    @Test fun hipMachineWeekCompletesAndColdRestoresExactTargetsHistoryAndSeed() {
        completeGeneratedWeek(ComposerSplit.UPPER_LOWER,includeHipMachines=true)
    }

    @Test fun savedV3TimedProgramRetainsItsExactCycleAndTargetsAfterNewPoliciesArrive() {
        verifySavedTimedProgram(CoachFirstProgramStartDomain.TIMED_POLICY_VERSION)
    }

    @Test fun savedV4ProgramRetainsItsExactCycleAndTargetsAfterV5Arrives() {
        verifySavedTimedProgram(CoachFirstProgramStartDomain.ALLOCATION_POLICY_VERSION)
    }

    private fun verifySavedTimedProgram(version:String) {
        val (cycle,inputs)=saveGenerated(version)
        val disk=Disk()
        val seed=(NativeKotlinFirstProgramSeedFileStore(disk.root,disk.fence,identity)
            .read(owner,cycleId) as NativeFirstProgramSeedRead.Found).seed
        assertEquals(version,seed.policyVersion)
        val replay=CoachFirstProgramStartDomain.review(request().copy(
            startingLoads=seed.startingLoads,selectedBodyweightExerciseIds=seed.bodyweightExercises,
            nonSetSecondsPerDay=seed.nonSetSeconds,policyVersion=seed.policyVersion))
        assertEquals(cycle,CoachFirstProgramDraftConversionDomain.propose(
            replay,setup(),catalog,cycleId,seed.title,seed.blockWeeks).cycle)
        val day=cycle.blocks.single().days.first()
        val expected=(disk.preflight.inspect(owner,cycleId,1,day.weekday,inputs)
            as NativeCustomPreflightResult.ReviewedPreview).session
        assertTrue(disk.prepared.prepare(owner,"v3-first-day",cycleId,1,day.weekday,inputs)
            is NativeCustomPrepareResult.Prepared)
        val issued=(Disk().journals.issue(owner,"v3-first-day",cycleId,1,day.weekday,inputs)
            as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(expected,issued.snapshot)
        assertTrue(issued.snapshot.slots.flatMap{it.sets}
            .any{it.prescription.type==DraftSetType.WARMUP})
    }

    @Test fun generatedFullBodyCompletesItsWholeWeekAndColdReturnsToWeekTwo() {
        completeGeneratedWeek(ComposerSplit.FULL_BODY)
    }

    @Test fun generatedStrengthWeekCompletesAndColdRestoresItsOwnPhaseRules() {
        completeGeneratedWeek(ComposerSplit.UPPER_LOWER,TrainingPhase.STRENGTH)
    }

    @Test fun generatedPeakWeekCompletesAndColdRestoresItsOwnPhaseRules() {
        completeGeneratedWeek(ComposerSplit.UPPER_LOWER,TrainingPhase.PEAK)
    }

    @Test fun mixedPurposePplStrengthWeekCompletesAndColdRestoresSupportingDays() {
        completeGeneratedWeek(ComposerSplit.PUSH_PULL_LEGS,TrainingPhase.STRENGTH)
    }

    private fun completeGeneratedWeek(split:ComposerSplit,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        includeHipMachines:Boolean=false,includeTrapBar:Boolean=false,
        completionClock:NativeHistoryCompletionClock=NativeHistoryCompletionClock.Undated,
        includeNewTargets:Boolean=false) {
        val (cycle,inputs)=saveGenerated(split=split,phase=phase,
            includeHipMachines=includeHipMachines,includeTrapBar=includeTrapBar,includeNewTargets=includeNewTargets)
        val days=cycle.blocks.single().days.sortedBy{it.weekday}
        assertEquals(weekdays(split).sorted(),days.map{it.weekday})
        assertTrue(days.all{it.slots.size>=3})
        val selected=days.flatMap{it.slots}.map{it.exerciseId}.toSet()
        assertTrue(selected.containsAll(setOf("barbell_bench","back_squat",
            "one_arm_db_row","db_rdl")))
        if(includeNewTargets) {
            assertTrue(selected.containsAll(setOf("serratus-pushup","tibialis-raise")))
            assertEquals("v12",inputs.policiesByPhase.getValue(phase).revision)
            assertEquals(TrainingMuscleCoverage.legacyV4,inputs.policiesByPhase.getValue(phase).selectedBounds.map{it.muscle})
            for(slot in days.flatMap{it.slots}.filter{it.exerciseId in setOf("serratus-pushup","tibialis-raise")})
                assertTrue(slot.sets.all{it.load==DraftSetLoad.ExplicitBodyweight && it.type==DraftSetType.WORKING})
        }
        if(includeTrapBar) {
            assertTrue(selected.containsAll(setOf("trap-bar-deadlift","db-shrug")))
            assertEquals(TrainingMuscleCoverage.legacyV4,inputs.policiesByPhase
                .getValue(phase).selectedBounds.map{it.muscle})
            for(slot in days.flatMap{it.slots}.filter{it.exerciseId=="trap-bar-deadlift"})
                for(set in slot.sets.filter{it.type==DraftSetType.WORKING})
                    assertEquals(DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.BARBELL_TOTAL,n("145")),set.load)
        }
        if(includeHipMachines) {
            assertTrue(selected.containsAll(setOf("hip-abduction","adduction-machine")))
            assertEquals(TrainingMuscleCoverage.legacyV4,inputs.policiesByPhase
                .getValue(phase).selectedBounds.map{it.muscle})
            for(slot in days.flatMap{it.slots}.filter {
                it.exerciseId in setOf("hip-abduction","adduction-machine")
            }) for(set in slot.sets.filter{it.type==DraftSetType.WORKING}) {
                assertEquals(DraftSetLoad.Verified(LoadUnit.LB,
                    LoadBasis.EXERCISE_OVERRIDE,n("20")),set.load)
                // Source practicality (12–20) intersects the authored
                // hypertrophy accessory prescription (10–15).
                assertEquals(RepBand(12,15),set.reps)
            }
        }
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
                    d.fence,identity,completionClock).complete(owner,athlete,id,finish)
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
        assertEquals(phase,seed.phase)
        val savedSource=(Disk().sources.read(owner) as NativeAuthoringSourceRead.Found).source
        val replayInput=FirstProgramStartRequest(savedSource.setup,savedSource.catalog,
            seed.startingLoads,seed.bodyweightExercises,seed.phase,seed.extraFocuses,
            seed.nonSetSeconds,seed.policyVersion)
        val regenerated=CoachFirstProgramStartDomain.review(replayInput)
        assertEquals(cycle,CoachFirstProgramDraftConversionDomain.propose(
            regenerated,replayInput.setup,catalog,cycleId,seed.title,seed.blockWeeks).cycle)
        if(includeHipMachines)for(id in listOf("hip-abduction","adduction-machine"))
            assertEquals(n("20"),seed.startingLoads[id])
        // Editing the first finished workout cannot reorder newer results
        // or rewind next-session selection. Preserve the issued original.
        val firstId=completed.first()
        val d=Disk()
        val original=(d.journals.open(owner,firstId)
            as NativeCustomJournalResult.Durable).issued.journal
        val working=original.snapshot.slots.filter{!includeNewTargets || it.exerciseId=="tibialis-raise"}.flatMap{it.sets}
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
        writeLocalAthleteForRestoreProof()
        val recovered=NativeKotlinStagedCoreStoreAudit.inspect(
            temp.root.toPath(),NativeKotlinOwnerIdentity(owner,athlete))
        assertTrue(recovered.toString(),
            recovered is NativeKotlinStagedCoreAuditResult.Verified)
        val complete=recovered as NativeKotlinStagedCoreAuditResult.Verified
        assertEquals(days.size,complete.finishedWorkouts)
        assertEquals(days.size,complete.customPrepared)
        assertEquals(days.size,complete.customIssued)
        assertEquals(0,complete.customUnfinished)
        if(includeNewTargets) {
            // Choose an actual generated bodyweight movement with a
            // non-singleton rep band. A 15-15 tibialis prescription can
            // legitimately be at its ceiling and cannot earn another rep.
            val day=days.first { scheduled ->
                scheduled.slots.any {it.exerciseId=="serratus-pushup"}
            }
            val sourceSlot=day.slots.single {it.exerciseId=="serratus-pushup"}
            assertTrue(sourceSlot.progressionMethod in setOf(
                SavedProgressionMethod.BODYWEIGHT_REPS,SavedProgressionMethod.LADDER))
            val workingSets=sourceSlot.sets.filter {it.type==DraftSetType.WORKING}
            assertTrue(workingSets.isNotEmpty() &&
                workingSets.all{it.reps.minimum<it.reps.maximum})
            val firstTargets=workingSets.map {checkNotNull(it.exactRepTarget)}
            // Correct a finished actual on this exact weekday/slot; both
            // the displayed next target and the editor must consume the
            // corrected owner history, not the immutable old receipt alone.
            val sourceWorkoutId=completed[days.indexOf(day)]
            val beforeCorrection=(Disk().journals.open(owner,sourceWorkoutId)
                as NativeCustomJournalResult.Durable).issued.journal
            val firstSet=beforeCorrection.snapshot.slots.single {
                it.slotId==sourceSlot.slotId
            }.sets.first{it.prescription.type==DraftSetType.WORKING}
            val visit=CustomWorkoutVisitKey(firstSet.setKey,CustomWorkoutVisitPart.MAIN)
            val prior=(beforeCorrection.effectiveVisits.getValue(visit).operation
                as CustomWorkoutJournalOperation.Record)
            val oldActual=checkNotNull(prior.performed)
            val correctionDisk=Disk()
            val correctionRevision=(correctionDisk.corrections.open(
                owner,sourceWorkoutId) as NativeCustomSavedCorrectionResult.Opened)
                .state.correctionRevision
            val correction=CustomSavedVisitCorrection(
                "serratus-corrected-reps",correctionRevision,visit,prior.eventId,
                KotlinJournalSetOutcome.COMPLETED,
                oldActual.copy(reps=oldActual.reps+1))
            assertTrue(NativeKotlinCustomHistoryCorrectionTransaction(
                correctionDisk.history,correctionDisk.journals,
                correctionDisk.corrections,correctionDisk.fence,identity)
                .correct(owner,athlete,sourceWorkoutId,correction)
                is NativeCustomCorrectionTransactionResult.Committed)
            val ownerReview=Disk()
            val basic=NativeKotlinCustomBasicNextReview(
                ownerReview.fence,identity,ownerReview.drafts,ownerReview.sources,
                ownerReview.preflight,ownerReview.timeline,ownerReview.journals,
                ownerReview.machines)
            val read=basic.reviewAuthored(owner,cycleId,inputs,2,day.weekday,
                sourceSlot.slotId,"bodyweight-next-2",BigDecimal.ZERO)
            assertTrue(read.toString(),
                read is NativeCustomBasicNextReadResult.Reviewed)
            val signed=read as NativeCustomBasicNextReadResult.Reviewed
            val proposal=signed.projection
            assertTrue(proposal.toString(),proposal is CustomBasicHistoryResult.UnloadedReviewed)
            val target=proposal as CustomBasicHistoryResult.UnloadedReviewed
            assertEquals(sourceWorkoutId,target.historyWorkoutId)
            assertTrue(target.candidate.advancedOrdinals.isNotEmpty())
            assertTrue(target.candidate.proposedExactReps.indices.all { index ->
                target.candidate.proposedExactReps[index] in
                    sourceSlot.sets.filter{it.type==DraftSetType.WORKING}[index].reps.let{
                        it.minimum..it.maximum
                    }
            })
            val originalWeekTwo=signed.next
            val oldSets=originalWeekTwo.slots.single {
                it.slotId==sourceSlot.slotId
            }.sets.map {it.prescription}
            assertEquals(firstTargets,oldSets.filter {
                it.type==DraftSetType.WORKING
            }.map {it.exactRepTarget})
            val offered=checkNotNull(CustomBasicProgressionDraftDomain.forWeekEdit(
                sourceSlot.slotId,oldSets,proposal))
            assertEquals(oldSets.map{it.load},offered.map{it.load})
            assertEquals(oldSets.map{it.reps},offered.map{it.reps})
            assertEquals(oldSets.map{it.targetRir},offered.map{it.targetRir})
            assertNotEquals(oldSets,offered)
            assertFalse(signed.mayIssueWorkout)
            assertFalse(signed.maySaveProgram)

            val compared=CustomCoachWeekEditDomain.review(cycle,cycle.revision,
                2,day.weekday,sourceSlot.slotId,offered,replayInput.setup,
                catalog,inputs)
            assertTrue(compared.toString(),compared is CustomCoachWeekEditResult.Reviewed)
            val changed=(compared as CustomCoachWeekEditResult.Reviewed).review
            assertFalse(changed.mayIssueWorkout)
            assertEquals(cycle,(Disk().drafts.read(owner,cycleId)
                as NativeCustomDraftReadResult.Found).draft)
            val futureBefore=(Disk().preflight.inspect(owner,cycleId,
                3,day.weekday,inputs) as NativeCustomPreflightResult.ReviewedPreview).session
            assertTrue(Disk().drafts.save(owner,cycleId,cycle.revision,
                changed.proposal.draft) is NativeCustomDraftWriteResult.Committed)
            val oldSignoff=(Disk().consent.read(owner,cycleId,inputs)
                as NativeCustomSignoffRead.Found)
            assertFalse(oldSignoff.isCurrent)
            assertFalse(Disk().preflight.inspect(owner,cycleId,2,day.weekday,inputs)
                is NativeCustomPreflightResult.ReviewedPreview)
            assertTrue(Disk().consent.confirm(owner,cycleId,changed.proposal.draft.revision,
                1,oldSignoff.receipt.recordRevision,inputs,true)
                is NativeCustomSignoffWrite.Committed)
            val accepted=(Disk().preflight.inspect(owner,cycleId,2,day.weekday,inputs)
                as NativeCustomPreflightResult.ReviewedPreview).session
            assertEquals(offered,accepted.slots.single {
                it.slotId==sourceSlot.slotId
            }.sets.map{it.prescription})
            assertEquals(futureBefore.slots,(Disk().preflight.inspect(owner,cycleId,
                3,day.weekday,inputs)
                as NativeCustomPreflightResult.ReviewedPreview).session.slots)
            // Preserve the real weekly calendar. Finish any earlier
            // scheduled days before issuing the changed bodyweight day.
            for ((earlierIndex,earlier) in days.withIndex()) {
                if(earlier.weekday>=day.weekday)break
                val earlierId="bodyweight-week2-before-$earlierIndex"
                assertEquals(NativeCustomNextCycleSlot.Next(2,earlier.weekday,
                    earlier.label),Disk().next(inputs))
                assertTrue(Disk().prepared.prepare(owner,earlierId,cycleId,
                    2,earlier.weekday,inputs) is NativeCustomPrepareResult.Prepared)
                var earlierJournal=(Disk().journals.issue(owner,earlierId,cycleId,
                    2,earlier.weekday,inputs)
                    as NativeCustomJournalResult.Durable).issued.journal
                for ((setIndex,set) in earlierJournal.snapshot.slots.flatMap{it.sets}
                    .withIndex()) {
                    val prescription=set.prescription
                    val actual=when(val load=prescription.load) {
                        is DraftSetLoad.Verified -> KotlinPerformedSet(
                            load.value,load.unit,prescription.reps.minimum,null)
                        DraftSetLoad.ExplicitBodyweight -> KotlinPerformedSet(
                            null,null,prescription.reps.minimum,null)
                    }
                    earlierJournal=(Disk().journals.record(owner,earlierId,
                        CustomWorkoutJournalOperation.Record(
                            "bodyweight-before-$earlierIndex-$setIndex",
                            earlierJournal.revision,
                            CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                            KotlinJournalSetOutcome.COMPLETED,actual))
                        as NativeCustomJournalResult.Durable).issued.journal
                }
                assertTrue(NativeKotlinCustomHistoryTransaction(
                    Disk().history,Disk().journals,Disk().fence,identity).complete(
                    owner,athlete,earlierId,CustomWorkoutJournalOperation.Finish(
                        "bodyweight-before-finish-$earlierIndex",earlierJournal.revision))
                    is NativeCustomFinishResult.Committed)
            }
            assertEquals(NativeCustomNextCycleSlot.Next(2,day.weekday,day.label),
                Disk().next(inputs))
            val nextWorkoutId="bodyweight-approved-next"
            assertTrue(Disk().prepared.prepare(owner,nextWorkoutId,cycleId,
                2,day.weekday,inputs) is NativeCustomPrepareResult.Prepared)
            val issued=(Disk().journals.issue(owner,nextWorkoutId,cycleId,
                2,day.weekday,inputs) as NativeCustomJournalResult.Durable).issued.journal
            assertEquals(accepted,issued.snapshot)
            assertEquals(accepted,(Disk().journals.open(owner,nextWorkoutId)
                as NativeCustomJournalResult.Durable).issued.journal.snapshot)
            assertEquals(original.snapshot,(Disk().journals.open(owner,firstId)
                as NativeCustomJournalResult.Durable).issued.journal.snapshot)
        }
    }

    @Test fun basicAndCoachedReadersRejectCorruptMachineRosterWithoutFallback() {
        val (cycle,inputs)=saveGenerated()
        val disk=Disk()
        val day=cycle.blocks.single().days.first()
        val slot=day.slots.first()
        val basic=NativeKotlinCustomBasicNextReview(
            disk.fence,identity,disk.drafts,disk.sources,disk.preflight,
            disk.timeline,disk.journals,disk.machines)
        val clean=basic.reviewAuthored(owner,cycleId,inputs,
            1,day.weekday,slot.slotId,"first-basic-review",n("5"))
        assertTrue(clean.toString(),
            clean is NativeCustomBasicNextReadResult.Reviewed)
        assertTrue(disk.machines.save(owner,0,emptyList())
            is NativeMachineRosterResult.Saved)
        val path=java.nio.file.Files.list(disk.root).use{stream->
            stream.filter{
                it.fileName.toString().startsWith("machine-roster-") &&
                    it.fileName.toString().endsWith(".bin")
            }.findFirst().orElseThrow()
        }
        val damaged=java.nio.file.Files.readAllBytes(path)
        damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
        java.nio.file.Files.write(path,damaged)
        assertEquals(NativeCustomBasicNextReadResult.Rejected(
            NativeCustomBasicNextRefusal.MACHINE_ROSTER_UNAVAILABLE),
            basic.reviewAuthored(owner,cycleId,inputs,1,day.weekday,
                slot.slotId,"first-basic-review",n("5")))
        val coach=NativeKotlinCustomNextWorkoutReview(
            disk.fence,identity,null,null,disk.sources,disk.timeline,
            disk.journals,null,disk.preflight,disk.machines)
        assertEquals(NativeCustomNextCoachResult.Rejected(
            NativeCustomNextCoachRefusal.CURRENT_GYM_UNAVAILABLE),
            coach.reviewAuthored(owner,cycleId,inputs,1,day.weekday,
                slot.slotId,CoachReadiness.NORMAL,false,n("5")))
        assertArrayEquals(damaged,java.nio.file.Files.readAllBytes(path))
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

        // This is the SAME signed ordinary custom-cycle reader the Android
        // controller invokes, not a caller-assembled history shortcut.
        // It re-reads current consent, the source catalog, physical stock,
        // every ledger completion and its immutable issued snapshot.
        val disk=Disk()
        val ownerCoach=NativeKotlinCustomNextWorkoutReview(
            disk.fence,identity,null,null,disk.sources,disk.timeline,
            disk.journals,null,disk.preflight)
        val authenticated=ownerCoach.reviewAuthored(
            owner,cycleId,inputs,2,day.weekday,selected.slotId,
            CoachReadiness.NORMAL,false,n("5"),
            CustomRepForecastPolicy.TARGET_RIR_V2)
        assertTrue(authenticated.toString(),
            authenticated is NativeCustomNextCoachResult.Reviewed)
        val actualCoach=authenticated as NativeCustomNextCoachResult.Reviewed
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_V2,
            actualCoach.review.repForecastPolicy)
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,
            actualCoach.review.status)
        assertEquals(coaching.nextSetSuggestions.map{it.proposedNextReps},
            actualCoach.review.nextSetSuggestions.map{it.proposedNextReps})
        assertEquals(next,actualCoach.nextWorkout)
        assertEquals(selected.slotId,actualCoach.selectedSlotId)
        assertTrue(actualCoach.historyRevision>0)
        assertTrue(actualCoach.approvedConsentRevision>0)
        assertFalse(actualCoach.mayIssueWorkout)
        // Basic progression is a separate read-only owner-fenced path.
        // It is computed from the same signed next day and every verified
        // saved receipt, not from displayed Last or UI-supplied observations.
        val savedDay=cycle.blocks.single().days.single{it.weekday==day.weekday}
        val basicSlot=checkNotNull(savedDay.slots.firstOrNull{slot->
            slot.progressionMethod in setOf(
                SavedProgressionMethod.DOUBLE, SavedProgressionMethod.DYNAMIC,
                SavedProgressionMethod.LINEAR, SavedProgressionMethod.AUTO) &&
                slot.sets.filter{it.type==DraftSetType.WORKING}.let{working->
                    working.isNotEmpty() &&
                        working.all{it.load is DraftSetLoad.Verified &&
                            it.amplifier==null}
                }
        })
        val basicOwner=NativeKotlinCustomBasicNextReview(
            disk.fence,identity,disk.drafts,disk.sources,disk.preflight,
            disk.timeline,disk.journals,disk.machines)
        val basicRead=basicOwner.reviewAuthored(
            owner,cycleId,inputs,2,day.weekday,basicSlot.slotId,
            "generated-next-basic-2",n("5"))
        assertTrue(basicRead.toString(),
            basicRead is NativeCustomBasicNextReadResult.Reviewed)
        val basicVerified=basicRead as NativeCustomBasicNextReadResult.Reviewed
        assertEquals(next,basicVerified.next)
        assertTrue(basicVerified.historyRevision>0)
        assertTrue(basicVerified.approvedConsentRevision>0)
        val basicProjection=basicVerified.projection
        assertTrue(basicProjection.toString(),
            basicProjection is CustomBasicHistoryResult.Reviewed)
        val recommended=basicProjection as CustomBasicHistoryResult.Reviewed
        assertEquals(workoutId,recommended.historyWorkoutId)
        assertEquals(basicSlot.slotId,recommended.slotId)
        assertFalse(recommended.mayIssueWorkout)
        assertFalse(basicVerified.maySaveProgram)
        assertEquals(NativeCustomBasicNextReadResult.Rejected(
            NativeCustomBasicNextRefusal.INVALID_SCOPE),
            basicOwner.reviewAuthored(owner,cycleId,inputs,2,day.weekday,
                basicSlot.slotId,workoutId,n("5")))
        val safer=ownerCoach.reviewAuthored(owner,cycleId,inputs,
            2,day.weekday,selected.slotId,CoachReadiness.NORMAL,false,n("5"),
            CustomRepForecastPolicy.TARGET_RIR_TREND_V3)
        assertTrue(safer.toString(),safer is NativeCustomNextCoachResult.Reviewed)
        val v3=(safer as NativeCustomNextCoachResult.Reviewed).review
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_TREND_V3,
            v3.repForecastPolicy)
        assertEquals(actualCoach.review.nextSetSuggestions.map{
            it.proposedNextReps},v3.nextSetSuggestions.map{it.proposedNextReps})
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,v3.status)
        assertTrue(v3.capacityDropsFromPreviousSession.isEmpty())
        assertFalse(v3.mayIssueWorkout)
        // Fresh owner-authenticated review with NO explicit version must
        // choose the SAME current policy as the athlete's current settings.
        // It must not silently fall back to a frozen V1 recipe.
        val current=ownerCoach.reviewAuthored(owner,cycleId,inputs,
            2,day.weekday,selected.slotId,CoachReadiness.NORMAL,false,n("5"))
        assertTrue(current.toString(),
            current is NativeCustomNextCoachResult.Reviewed)
        val selectedCurrent=(current as NativeCustomNextCoachResult.Reviewed).review
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_TREND_V3,
            selectedCurrent.repForecastPolicy)
        assertEquals(v3.status,selectedCurrent.status)
        assertEquals(v3.nextSetSuggestions,selectedCurrent.nextSetSuggestions)
        // Historical signed V1 replay must remain EXPLICIT and unchanged.
        val replay=ownerCoach.reviewAuthored(owner,cycleId,inputs,
            2,day.weekday,selected.slotId,CoachReadiness.NORMAL,false,n("5"),
            CustomRepForecastPolicy.LEGACY_STEP_V1)
        assertTrue(replay.toString(),replay is NativeCustomNextCoachResult.Reviewed)
        assertEquals(CustomRepForecastPolicy.LEGACY_STEP_V1,
            (replay as NativeCustomNextCoachResult.Reviewed).review.repForecastPolicy)
        assertEquals(source,reopened.snapshot)
        // A real saved off-switch is selected within the SAME owner fence.
        // Progression-only uses the current V2 policy without optional
        // longitudinal-fatigue decisions; MANUAL suppresses new proposals.
        val progressionSave=disk.settings.save(owner,0,
            CoachAssistancePolicy.PROGRESSION_ONLY)
        assertTrue(progressionSave.toString(),
            progressionSave is NativeCoachSettingsWrite.Saved)
        val settingsCoach=NativeKotlinCustomNextWorkoutReview(
            disk.fence,identity,null,null,disk.sources,disk.timeline,
            disk.journals,disk.settings,disk.preflight)
        val progressionOnly=settingsCoach.reviewAuthored(owner,cycleId,
            inputs,2,day.weekday,selected.slotId,
            CoachReadiness.NORMAL,false,n("5"))
        assertTrue(progressionOnly.toString(),
            progressionOnly is NativeCustomNextCoachResult.Reviewed)
        assertEquals(CustomRepForecastPolicy.TARGET_RIR_V2,
            (progressionOnly as NativeCustomNextCoachResult.Reviewed)
                .review.repForecastPolicy)
        assertEquals(actualCoach.review.nextSetSuggestions,
            progressionOnly.review.nextSetSuggestions)
        val manualSave=disk.settings.save(owner,1,
            CoachAssistancePolicy.MANUAL)
        assertTrue(manualSave.toString(),manualSave is NativeCoachSettingsWrite.Saved)
        // Manual coaching opt-out must not disable the athlete's independent
        // basic logging/progression selection or rewrite its source numbers.
        val basicWhileManual=basicOwner.reviewAuthored(
            owner,cycleId,inputs,2,day.weekday,basicSlot.slotId,
            "generated-next-basic-manual",n("5"))
        assertTrue(basicWhileManual.toString(),
            basicWhileManual is NativeCustomBasicNextReadResult.Reviewed)
        assertEquals(basicProjection,
            (basicWhileManual as NativeCustomBasicNextReadResult.Reviewed)
                .projection)
        assertEquals(NativeCustomNextCoachResult.Rejected(
            NativeCustomNextCoachRefusal.ASSISTANCE_DISABLED),
            settingsCoach.reviewAuthored(owner,cycleId,inputs,
                2,day.weekday,selected.slotId,
                CoachReadiness.NORMAL,false,n("5")))
        val restored=disk.settings.save(owner,2,CoachAssistancePolicy.STANDARD)
        assertTrue(restored.toString(),restored is NativeCoachSettingsWrite.Saved)
        // Without activated signoff owners, an activated review cannot be
        // used as a fallback bypass for an ordinary program.
        assertEquals(NativeCustomNextCoachResult.Rejected(
            NativeCustomNextCoachRefusal.ACTIVATED_CONSENT_UNAVAILABLE),
            ownerCoach.review(owner,inputs,2,day.weekday,selected.slotId,
                CoachReadiness.NORMAL,false,n("5"),
                CustomRepForecastPolicy.TARGET_RIR_V2))

        // A real V2 proposal feeds the EXISTING explicit, whole-cycle week
        // edit, not a second unsigned workout issuer. No historical set
        // is modified and only this future weekday/slot gets new rep floors.
        val repOffer=CustomNextRepAmendmentDomain.propose(
            next,selected.slotId,actualCoach.review)
            as CustomNextRepAmendmentResult.Proposed
        val updatedSets=repOffer.amendment.amended.slots.single{
            it.slotId==selected.slotId
        }.sets.map{it.prescription}
        val checked=CustomCoachWeekEditDomain.review(
            cycle,cycle.revision,2,day.weekday,selected.slotId,updatedSets,
            setup(),catalog,inputs)
        assertTrue(checked.toString(),checked is CustomCoachWeekEditResult.Reviewed)
        val comparison=(checked as CustomCoachWeekEditResult.Reviewed).review
        assertFalse(comparison.mayIssueWorkout)
        assertFalse(comparison.mayApprovePolicy)
        assertTrue(comparison.after.issues.toString(),
            comparison.after.issues.isEmpty())
        val revised=comparison.proposal.draft
        assertTrue(revised.revision>cycle.revision)
        assertEquals(cycle,(Disk().drafts.read(owner,cycleId)
            as NativeCustomDraftReadResult.Found).draft)
        val oldDay=(Disk().journals.open(owner,workoutId)
            as NativeCustomJournalResult.Durable).issued.journal
        assertEquals(source,oldDay.snapshot)
        // Correct one actual load after Finish, then reopen the Plan reader from disk.
        val correctedSet = oldDay.snapshot.slots.single { it.slotId == basicSlot.slotId }
            .sets.first { it.prescription.type == DraftSetType.WORKING }
        val visitKey = CustomWorkoutVisitKey(correctedSet.setKey, CustomWorkoutVisitPart.MAIN)
        val priorRecord = oldDay.effectiveVisits.getValue(visitKey).operation as CustomWorkoutJournalOperation.Record
        val performed = checkNotNull(priorRecord.performed)
        val correction = CustomSavedVisitCorrection("correct-basic-load", 0, visitKey,
            priorRecord.eventId, KotlinJournalSetOutcome.COMPLETED,
            performed.copy(load=checkNotNull(performed.load).add(n("5"))))
        val corrected = NativeKotlinCustomHistoryCorrectionTransaction(
            disk.history, disk.journals, disk.corrections, disk.fence, identity)
            .correct(owner, athlete, workoutId, correction)
        assertTrue(corrected.toString(), corrected is NativeCustomCorrectionTransactionResult.Committed)
        val reopenedDisk = Disk()
        val freshReader = NativeKotlinCustomBasicNextReview(reopenedDisk.fence, identity,
            reopenedDisk.drafts, reopenedDisk.sources, reopenedDisk.preflight, reopenedDisk.timeline,
            reopenedDisk.journals, reopenedDisk.machines)
        val refreshed = freshReader.reviewAuthored(owner, cycleId, inputs, 2, day.weekday,
            basicSlot.slotId, "corrected-basic-review", n("5")) as NativeCustomBasicNextReadResult.Reviewed
        assertEquals(BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY,
            (refreshed.projection as CustomBasicHistoryResult.Rejected).evaluatorReason)
        assertEquals(next, refreshed.next)
        assertEquals(cycle, (reopenedDisk.drafts.read(owner, cycleId) as NativeCustomDraftReadResult.Found).draft)

        val diskForEdit=Disk()
        val stored=diskForEdit.drafts.save(
            owner,cycleId,cycle.revision,revised)
        assertTrue(stored.toString(),
            stored is NativeCustomDraftWriteResult.Committed)
        val obsolete=diskForEdit.consent.read(owner,cycleId,inputs)
            as NativeCustomSignoffRead.Found
        assertFalse(obsolete.isCurrent)
        assertTrue(Disk().next(inputs) is NativeCustomNextCycleSlot.Rejected)
        val newSignoff=Disk().consent.confirm(
            owner,cycleId,revised.revision,1,
            obsolete.receipt.recordRevision,inputs,true)
        assertTrue(newSignoff.toString(),
            newSignoff is NativeCustomSignoffWrite.Committed)
        // Old finished Week 1 remains complete. A revised cycle should
        // offer the next NOT-yet-trained day, never restart Monday or
        // pretend the edited Week 2 workout was already performed.
        val nextDay=cycle.blocks.single().days.sortedBy{it.weekday}[1]
        assertEquals(NativeCustomNextCycleSlot.Next(1,nextDay.weekday,
            nextDay.label),Disk().next(inputs))
        val verified=(Disk().preflight.inspect(owner,cycleId,2,
            day.weekday,inputs) as NativeCustomPreflightResult.ReviewedPreview)
            .session
        assertEquals(updatedSets,verified.slots.single{
            it.slotId==selected.slotId
        }.sets.map{it.prescription})
        assertEquals(source,(Disk().journals.open(owner,workoutId)
            as NativeCustomJournalResult.Durable).issued.journal.snapshot)
    }
}
