package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class GeneratedAdaptiveBlockConversionTest {
    private val fixture=GeneratedAdaptiveBlockFixture

    @Test fun realGeneratorProducesCompleteExactFutureBlockWithoutIssuing() {
        val request=fixture.request()
        val outcome=GeneratedAdaptiveBlockDomain.propose(request)
        assertTrue("Conversion status: "+outcome.status+
            " reason: "+outcome.explanation+
            " generation: "+outcome.generatedWeek?.status+
            " issues: "+outcome.generatedWeek?.issues+
            " prescription: "+outcome.prescribedWeek?.status+
            " notes: "+outcome.prescribedWeek?.reviewNotes,
            outcome.status==GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW)
        val proposed=checkNotNull(outcome.proposedOption).proposedCycle
        assertEquals(5L,proposed.revision)
        assertEquals(3,proposed.blocks.single().weeks)
        assertEquals(setOf(0,2,4),
            proposed.blocks.single().days.map {it.weekday}.toSet())
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,
            outcome.prescribedWeek?.status)
        assertTrue(proposed.blocks.single().days.all {day->
            day.slots.isNotEmpty() && day.slots.all {slot ->
                slot.slotId=="old_"+slot.exerciseId &&
                    !slot.pinned &&
                    slot.sets.any {it.type==DraftSetType.WORKING} &&
                    slot.sets.all {set->
                        set.reps==RepBand(8,12) && set.targetRir==2 &&
                            (set.load as DraftSetLoad.Verified).value==
                            fixture.n("25")
                    }
            }
        })
        assertEquals(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            CustomTrainingCycleDomain.review(proposed,
                CustomExerciseAuthoringDomain.picker(
                    fixture.athlete(),fixture.movements)
                    .map {it.availability}).status)
        assertEquals(4L,request.currentCycle.revision)
        assertFalse(outcome.maySaveProgram)
        assertFalse(outcome.mayIssueWorkout)
        assertEquals(outcome,GeneratedAdaptiveBlockDomain.propose(request))
    }

    @Test fun changedTrainingPurposeGetsNewSlotIdentityWithoutBorrowingSameDayHistory() {
        val original=fixture.cycle()
        val before=original.blocks.single()
        val changed=original.copy(blocks=listOf(before.copy(
            days=before.days.map { day ->
                if(day.weekday==0)
                    day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
                else day
            })))
        val request=fixture.request(original=changed)
        val first=GeneratedAdaptiveBlockDomain.propose(request)
        assertEquals(first.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,first.status)
        assertEquals(first,GeneratedAdaptiveBlockDomain.propose(request))
        val candidate=checkNotNull(first.proposedOption).proposedCycle
        val oldByDay=before.days.associateBy {it.weekday}
        val newByDay=candidate.blocks.single().days.associateBy {it.weekday}
        val generatedSlotCount=newByDay.values.sumOf {it.slots.size}
        assertEquals(generatedSlotCount,first.slotContinuity.size)
        val changedDay=first.slotContinuity.filter {it.weekday==0}
        assertTrue(changedDay.isNotEmpty())
        assertTrue(changedDay.all {
            it.relationship==GeneratedAdaptiveSlotRelationship.CHANGED_SESSION_PURPOSE
        })
        assertEquals(generatedSlotCount-changedDay.size,
            first.slotContinuity.count {
                it.relationship==GeneratedAdaptiveSlotRelationship.UNCHANGED_EXPOSURE
            })
        first.slotContinuity.forEach { history ->
            val old=checkNotNull(oldByDay[history.weekday]).slots.single {
                it.exerciseId==history.exerciseId
            }
            val next=checkNotNull(newByDay[history.weekday]).slots.single {
                it.exerciseId==history.exerciseId
            }
            assertEquals(old.slotId,history.priorSlotId)
            assertEquals(next.slotId,history.proposedSlotId)
            if(history.weekday==0) {
                assertNotEquals(old.slotId,next.slotId)
                assertTrue(next.slotId.startsWith("coach_d0_"))
            } else assertEquals(old.slotId,next.slotId)
        }
        // An edited cycle proposal must never rewrite the source block.
        assertEquals(ComposerSessionPurpose.STRENGTH_SKILL,
            changed.blocks.single().days.first().purpose)
        assertEquals(4L,changed.revision)
        assertFalse(first.mayIssueWorkout)
        assertFalse(first.maySaveProgram)
        // The returned evidence cannot be modified after review.
        assertThrows(UnsupportedOperationException::class.java) {
            (first.slotContinuity as MutableList<GeneratedAdaptiveSlotContinuity>)
                .clear()
        }
    }

    @Test fun changedExerciseRoleCannotReuseOtherwiseMatchingTrainingSlot() {
        val original=fixture.cycle()
        val before=original.blocks.single()
        val amended=original.copy(blocks=listOf(before.copy(
            days=before.days.map { day ->
                day.copy(slots=day.slots.map { slot ->
                    if(slot.exerciseId=="bench")
                        slot.copy(role=ComposerRole.SECONDARY)
                    else slot
                })
            })))
        val result=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(original=amended))
        assertEquals(result.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,result.status)
        val benchVisits=result.slotContinuity.filter {
            it.exerciseId=="bench"
        }
        assertTrue(benchVisits.isNotEmpty())
        assertTrue(benchVisits.all {
            it.relationship==
                GeneratedAdaptiveSlotRelationship.CHANGED_EXERCISE_ROLE &&
            it.priorSlotId=="old_bench" && it.proposedSlotId!="old_bench"
        })
        assertEquals(result.slotContinuity.size-benchVisits.size,
            result.slotContinuity.count {
                it.relationship==GeneratedAdaptiveSlotRelationship.UNCHANGED_EXPOSURE
            })
    }

    @Test fun missingPrescriptionAndImpossibleGymWeightFailClosed() {
        val absent=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(prescriptions=fixture.policies().dropLast(1)))
        assertTrue("missing policy returned "+absent.status+
            " / "+absent.explanation,
            absent.status==GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED)
        assertNull(absent.proposedOption)
        val bad=fixture.policies().map {policy->
            if(policy.exerciseId=="bench")
                policy.copy(purposeWorkingLoad=fixture.n("27.5"))
            else policy
        }
        val impossible=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(prescriptions=bad))
        assertEquals(GeneratedAdaptiveBlockStatus.PRESCRIPTION_UNRESOLVED,
            impossible.status)
        assertNull(impossible.proposedOption)
    }

    @Test fun pinnedCustomVisitIsPreservedExactlyWhenPlacementMatches() {
        val original=fixture.cycle()
        val block=original.blocks.single()
        val first=block.days.first()
        val old=first.slots.first()
        val protected=old.copy(pinned=true,
            sets=listOf(old.sets.single().copy(restSeconds=140)),
            coachNote="Keep this setup and my authored rest")
        val authored=original.copy(blocks=listOf(block.copy(
            days=listOf(first.copy(slots=listOf(protected)+
                first.slots.drop(1)))+block.days.drop(1))))
        val result=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(original=authored))
        assertEquals(result.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,result.status)
        val proposed=checkNotNull(result.proposedOption).proposedCycle
        val preserved=proposed.blocks.single().days.first().slots.first()
        assertEquals(protected,preserved)
        assertEquals(old.slotId,preserved.slotId)
        assertEquals(GeneratedAdaptiveSlotRelationship.UNCHANGED_EXPOSURE,
            result.slotContinuity.first().relationship)
        // No silent program save, history write or workout issuance.
        assertEquals(authored,authored.copy())
        assertEquals(4L,authored.revision)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun authoredNoteWithoutPinAlsoSurvivesMatchingGeneration() {
        val original=fixture.cycle()
        val block=original.blocks.single()
        val first=block.days.first()
        val preserved=first.slots.first().copy(
            coachNote="Use the comfortable grip")
        val amended=original.copy(blocks=listOf(block.copy(
            days=listOf(first.copy(slots=listOf(preserved)+
                first.slots.drop(1)))+block.days.drop(1))))
        val result=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(original=amended))
        assertEquals(result.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,result.status)
        assertEquals(preserved,checkNotNull(result.proposedOption)
            .proposedCycle.blocks.single().days.first().slots.first())
    }

    @Test fun protectedVisitCannotChangeExerciseRoleOrSessionPurpose() {
        val original=fixture.cycle()
        val block=original.blocks.single()
        val first=block.days.first()
        val protected=first.slots.first().copy(
            pinned=true,role=ComposerRole.SECONDARY)
        val changedRole=original.copy(blocks=listOf(block.copy(
            days=listOf(first.copy(slots=listOf(protected)+
                first.slots.drop(1)))+block.days.drop(1))))
        val role=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(original=changedRole))
        assertEquals(GeneratedAdaptiveBlockStatus.PROTECTED_CUSTOM_WORK,
            role.status)
        assertNull(role.proposedOption)
        val changedPurpose=original.copy(blocks=listOf(block.copy(
            days=listOf(first.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL,
                slots=listOf(first.slots.first().copy(pinned=true))+
                    first.slots.drop(1)))+block.days.drop(1))))
        val purpose=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(original=changedPurpose))
        assertEquals(GeneratedAdaptiveBlockStatus.PROTECTED_CUSTOM_WORK,
            purpose.status)
        assertNull(purpose.proposedOption)
    }

    @Test fun missingProtectedDayAndPerWeekOverridesRemainBlocked() {
        val original=fixture.cycle()
        val prior=original.blocks.single()
        val day=prior.days.first()
        val moved=original.copy(blocks=listOf(prior.copy(
            days=listOf(day.copy(weekday=6,
                slots=day.slots.mapIndexed {index,slot ->
                    if(index==0)slot.copy(pinned=true) else slot
                }))+prior.days.drop(1))))
        val missed=GeneratedAdaptiveBlockDomain.propose(
            fixture.request(original=moved))
        assertEquals(GeneratedAdaptiveBlockStatus.PROTECTED_CUSTOM_WORK,
            missed.status)
        assertNull(missed.proposedOption)
        val existing=day.slots.first()
        val override=CustomWeekSlotOverride(2,day.weekday,existing.slotId,
            existing.sets+existing.sets)
        val withOverride=original.copy(weekOverrides=mapOf(
            prior.blockId to listOf(override)))
        assertEquals(GeneratedAdaptiveBlockStatus.PROTECTED_CUSTOM_WORK,
            GeneratedAdaptiveBlockDomain.propose(
                fixture.request(original=withOverride)).status)
        assertEquals(listOf(override),withOverride.weekOverrides[prior.blockId])
    }


    @Test fun strengthGenerationDoesNotSilentlyReplaceEightExerciseDaysWithThree() {
        val old=fixture.cycle()
        val existing=old.blocks.single()
        val extra=(4..8).map{n->
            fixture.movements[1].copy(
                exerciseId="accessory-$n",familyId="accessory-$n")
        }
        val hypertrophy=existing.copy(blockId="hypertrophy",
            label="Established hypertrophy",weeks=2,
            days=existing.days.map{day->
                day.copy(slots=day.slots+extra.map {movement->
                    CustomExerciseSlot("slot_"+movement.exerciseId,
                        movement.exerciseId,ComposerRole.ACCESSORY,
                        day.slots.first().sets,pinned=false)
                })
            })
        val strength=existing.copy(blockId="strength",
            label="Generated strength",phase=TrainingPhase.STRENGTH,
            weeks=2,days=existing.days.map{day->
                day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
            })
        val cycle=old.copy(blocks=listOf(hypertrophy,strength))
        val base=fixture.generation()
        val strengthPolicy=checkNotNull(base.policy).copy(
            phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 3),
            primaryLiftMinimumDays=mapOf("bench" to 1))
        val generated=base.copy(
            phase=TrainingPhase.STRENGTH,policy=strengthPolicy,
            catalog=base.catalog+extra)
        val prescribed=fixture.policies().map{
            it.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
        }
        val result=GeneratedAdaptiveBlockDomain.propose(
            GeneratedAdaptiveBlockRequest(cycle,"strength",
                "review_strength_menu",generated,prescribed))
        assertEquals("Generator outcome: ${result.status} "+
            "${result.explanation} | generation=${result.generatedWeek?.status} "+
            "${result.generatedWeek?.issues} | prescribed=${result.prescribedWeek?.status}",
            GeneratedAdaptiveBlockStatus.STRENGTH_EXERCISE_MENU_LOSS,result.status)
        assertNull(result.proposedOption)
        assertEquals(3,result.strengthRosterBoundary?.affectedDays?.size)
        // The deterministic Kotlin generator can select fewer than three
        // on some days depending on exact pattern coverage. All are still
        // serious source-backed losses, never a fabricated three-exercise quota.
        assertTrue(result.strengthRosterBoundary?.affectedDays?.all{
            it.beforeWorkingExercises==8 &&
                it.afterWorkingExercises in 1..3 &&
                it.missingExerciseIds.size>=5
        }==true)
        assertEquals(old.revision,cycle.revision)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
    }


    @Test fun optInStrengthGenerationActuallyRestoresEverySourceAccessoryOnTheSameDay() {
        val original=fixture.cycle()
        val existing=original.blocks.single()
        val extras=(4..8).map{number->
            fixture.movements[1].copy(
                exerciseId="continuity-$number",
                familyId="continuity-$number",
                role=ComposerRole.ACCESSORY,
                pattern=ComposerPattern.ISOLATION)
        }
        val hyper=existing.copy(blockId="hypertrophy",weeks=2,
            days=existing.days.map{day ->
                day.copy(slots=day.slots+extras.map{move ->
                    CustomExerciseSlot("old_"+move.exerciseId,
                        move.exerciseId,ComposerRole.ACCESSORY,
                        day.slots.first().sets,pinned=false)
                })
            })
        val strength=existing.copy(blockId="strength",
            phase=TrainingPhase.STRENGTH,weeks=2,
            days=existing.days.map{day ->
                day.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
            })
        val cycle=original.copy(blocks=listOf(hyper,strength))
        val base=fixture.generation()
        val policy=checkNotNull(base.policy).copy(
            phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 3),
            primaryLiftMinimumDays=mapOf("bench" to 1))
        val program=base.copy(
            phase=TrainingPhase.STRENGTH,policy=policy,
            catalog=base.catalog+extras,
            selectFromOfferedCatalog=true,
            maximumExercisesPerDay=8)
        val prescriptions=fixture.policies().map {
            it.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
        }
        val proposed=GeneratedAdaptiveBlockRequest(cycle,"strength",
            "recover_source_strength",program,prescriptions,
            allowSourceMenuRecovery=true)
        val result=GeneratedAdaptiveBlockDomain.propose(proposed)
        assertEquals("Unexpected ${result.status}: ${result.explanation}; "+
            "generation=${result.generatedWeek?.issues}",
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,result.status)
        val candidate=checkNotNull(result.proposedOption).proposedCycle
        assertEquals(cycle.revision+1,candidate.revision)
        val target=candidate.blocks.last()
        assertEquals(3,target.days.size)
        assertTrue(target.days.all{it.slots.size==8})
        // No fixed assumed "five accessories per day": the actual
        // composer may also omit secondary movements on some weekdays.
        // Compare against the real blocked generator, not a guessed count.
        val disallowed=GeneratedAdaptiveBlockDomain.propose(
            proposed.copy(allowSourceMenuRecovery=false))
        assertEquals(GeneratedAdaptiveBlockStatus.STRENGTH_EXERCISE_MENU_LOSS,
            disallowed.status)
        assertNull(disallowed.proposedOption)
        assertTrue(disallowed.restoredSourceExercises.isEmpty())
        val actualMissing=checkNotNull(disallowed.strengthRosterBoundary)
            .affectedDays.flatMap{loss ->
                loss.missingExerciseIds.map{loss.weekday to it}
            }.toSet()
        assertEquals(actualMissing.size,result.restoredSourceExercises.size)
        // Recovery must not publish a shorter continuity ledger than the
        // actual reviewed eight-exercise days. Every added Strength slot
        // carries an explicit SOURCE reference but a distinct new slot ID.
        val cycleSlots=target.days.flatMap { day ->
            day.slots.map { slot -> day.weekday to slot.slotId }
        }
        val continuityKeys=result.slotContinuity.map {
            it.weekday to it.proposedSlotId
        }
        assertEquals(cycleSlots.size,result.slotContinuity.size)
        assertEquals(cycleSlots.toSet(),continuityKeys.toSet())
        result.restoredSourceExercises.forEach { restored ->
            val evidence=result.slotContinuity.single {
                it.weekday==restored.weekday &&
                    it.proposedSlotId==restored.proposedSlotId
            }
            assertEquals(restored.exerciseId,evidence.exerciseId)
            assertEquals(restored.sourceSlotId,evidence.priorSlotId)
            assertEquals(
                GeneratedAdaptiveSlotRelationship.NEW_DAY_OR_EXERCISE,
                evidence.relationship)
            assertNotEquals(evidence.priorSlotId,evidence.proposedSlotId)
        }
        assertEquals(actualMissing,result.restoredSourceExercises.map{
            it.weekday to it.exerciseId
        }.toSet())
        // The final dose/time ledger must reflect the ACTUAL 8-exercise
        // Strength candidate, not the earlier 3-exercise composer report.
        val initialDose=checkNotNull(result.prescribedWeek?.finalAudit)
        val rebuiltDose=checkNotNull(result.upstreamNumberTrace).plannedWeekDose
        assertEquals(TrainingPhase.STRENGTH,rebuiltDose.phase)
        assertEquals(initialDose.muscles.keys,rebuiltDose.muscles.keys)
        assertTrue(rebuiltDose.muscles.any{(muscle,final)->
            final.effectiveWorkingSets>
                checkNotNull(initialDose.muscles[muscle]).effectiveWorkingSets
        })
        assertTrue(rebuiltDose.sessions.zip(initialDose.sessions).any{
            (final,initial)->final.declaredSeconds>initial.declaredSeconds
        })
        assertEquals(3,rebuiltDose.sessions.size)
        assertEquals(setOf(0,2,4),
            result.restoredSourceExercises.map{it.weekday}.toSet())
        assertTrue(extras.map{it.exerciseId}.all{exerciseId ->
            result.restoredSourceExercises.any{it.exerciseId==exerciseId}
        })
        assertTrue(result.restoredSourceExercises.all{record ->
            val old=hyper.days.single{it.weekday==record.weekday}
                .slots.single{it.slotId==record.sourceSlotId}
            val now=target.days.single{it.weekday==record.weekday}
                .slots.single{it.slotId==record.proposedSlotId}
            now.sets==old.sets && now.exerciseId==old.exerciseId &&
                now.role==old.role &&
                now.role in setOf(ComposerRole.SECONDARY,ComposerRole.ACCESSORY)
        })
        val numbers=checkNotNull(result.upstreamNumberTrace)
        val restoredKeys=result.restoredSourceExercises.map{
            it.weekday to it.proposedSlotId
        }.toSet()
        assertEquals(result.restoredSourceExercises.sumOf{record->
            target.days.single{it.weekday==record.weekday}.slots
                .single{it.slotId==record.proposedSlotId}.sets.size
        },numbers.sets.count{
            it.origin==GeneratedSetNumberOrigin.VERIFIED_SOURCE_MENU_RESTORATION
        })
        assertEquals(restoredKeys,numbers.sets.filter{
            it.origin==GeneratedSetNumberOrigin.VERIFIED_SOURCE_MENU_RESTORATION
        }.map{it.weekday to it.slotId}.toSet())
        assertEquals(2,checkNotNull(numbers.phaseBoundary).sourceExitWeek)
        assertEquals(3,checkNotNull(numbers.phaseBoundary).destinationEntryWeek)
        assertEquals(TrainingPhase.HYPERTROPHY,
            checkNotNull(numbers.phaseBoundary).sourcePhase)

        // Exact old-slot identity is NOT enough. A source-restored exercise
        // with silently changed reps or set count must lose its lineage.
        val restored=result.restoredSourceExercises.first()
        val originalDay=target.days.single{it.weekday==restored.weekday}
        val originalSlot=originalDay.slots.single{
            it.slotId==restored.proposedSlotId
        }
        val alteredSlot=originalSlot.copy(sets=originalSlot.sets.mapIndexed{
            index,set->if(index==0)set.copy(reps=RepBand(6,8)) else set
        })
        val changed=candidate.copy(blocks=candidate.blocks.map{block->
            if(block.blockId!="strength")block
            else block.copy(days=block.days.map{day->
                if(day.weekday!=restored.weekday)day
                else day.copy(slots=day.slots.map{slot->
                    if(slot.slotId==restored.proposedSlotId)alteredSlot else slot
                })
            })
        })
        val available=CustomExerciseAuthoringDomain.picker(
            fixture.athlete(),program.catalog).map{it.availability}
        val inspected=CustomTrainingCycleDomain.review(changed,available)
        assertEquals(inspected.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,inspected.status)
        assertNull(GeneratedUpstreamNumberTraceDomain.review(
            proposed,inspected,checkNotNull(result.generatedWeek),
            checkNotNull(result.prescribedWeek),result.slotContinuity,
            result.restoredSourceExercises,result.addedStrengthWorkingSets))
        val forged=result.restoredSourceExercises.map{
            if(it==restored)it.copy(sourceSlotId="other-source-slot") else it
        }
        assertNull(GeneratedUpstreamNumberTraceDomain.review(
            proposed,CustomTrainingCycleDomain.review(candidate,available),
            checkNotNull(result.generatedWeek),
            checkNotNull(result.prescribedWeek),result.slotContinuity,
            forged,result.addedStrengthWorkingSets))
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            result.strengthRosterBoundary?.status)
        assertEquals(StrengthDoseBoundaryStatus.CONTINUOUS,
            result.strengthDoseBoundary?.status)
        assertEquals(original.revision,cycle.revision)
        assertEquals(3,cycle.blocks.last().days.first().slots.size)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun generatedStrengthCannotCutWorkingSetsInHalfWhileKeepingSameExercises() {
        val original=fixture.cycle()
        val existing=original.blocks.single()
        val hypertrophy=existing.copy(blockId="hypertrophy",
            label="Prior high-volume block",weeks=2,
            days=existing.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(sets=List(8){slot.sets.single()})
                })
            })
        val strength=existing.copy(blockId="strength",weeks=2,
            phase=TrainingPhase.STRENGTH,
            days=existing.days.map{
                it.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
            })
        val cycle=original.copy(blocks=listOf(hypertrophy,strength))
        val base=fixture.generation()
        val policy=checkNotNull(base.policy).copy(
            phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 3),
            primaryLiftMinimumDays=mapOf("bench" to 1))
        val program=base.copy(phase=TrainingPhase.STRENGTH,policy=policy)
        val prescriptions=fixture.policies().map{
            it.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
        }
        val result=GeneratedAdaptiveBlockDomain.propose(
            GeneratedAdaptiveBlockRequest(cycle,"strength",
                "review_str_half_dose",program,prescriptions))
        assertEquals("Received ${result.status}: ${result.explanation}; "+
            "generated=${result.generatedWeek?.status} "+
            "prescribed=${result.prescribedWeek?.status}",
            GeneratedAdaptiveBlockStatus.STRENGTH_WORKING_DOSE_LOSS,result.status)
        assertNull(result.proposedOption)
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            result.strengthRosterBoundary?.status)
        assertEquals(StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW,
            result.strengthDoseBoundary?.status)
        assertEquals(72,result.strengthDoseBoundary?.hypertrophyWorkingSets)
        assertTrue((result.strengthDoseBoundary?.strengthWorkingSets ?:Int.MAX_VALUE)
            *2<=72)
        assertEquals(4L,cycle.revision)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.maySaveProgram)
        // Even with Adapt Sets enabled, authored Strength ceilings of four
        // sets per visit cannot honestly bridge a 72 -> <=36 source collapse.
        val capped=GeneratedAdaptiveBlockDomain.propose(
            GeneratedAdaptiveBlockRequest(cycle,"strength",
                "review_str_half_dose_capped",program,prescriptions,
                allowSourceDoseRecovery=true))
        assertEquals(GeneratedAdaptiveBlockStatus.STRENGTH_WORKING_DOSE_LOSS,
            capped.status)
        assertNull(capped.proposedOption)
        assertTrue(capped.addedStrengthWorkingSets.isEmpty())
    }


    @Test fun menuRecoveryCanStageForSeparatelyConsentedDoseRepairButNeverIgnoreHalfDose() {
        val original=fixture.cycle()
        val baseBlock=original.blocks.single()
        val accessories=(4..8).map{n->
            fixture.movements[1].copy(
                exerciseId="paired-repair-$n",familyId="paired-repair-$n",
                role=ComposerRole.ACCESSORY,pattern=ComposerPattern.ISOLATION)
        }
        // Adversarial 8-exercise source: the three retained movements had
        // ten working sets each, so restoring the five one-set accessories
        // ALONE cannot cross the 50%-loss boundary. Neither recovery toggle
        // can silently authorize an incomplete combined candidate.
        val hyper=baseBlock.copy(blockId="hypertrophy",weeks=2,
            days=baseBlock.days.map{day->
                day.copy(slots=day.slots.map{slot->
                    slot.copy(sets=List(10){slot.sets.single()})
                }+accessories.map{movement->
                    CustomExerciseSlot("old_"+movement.exerciseId,
                        movement.exerciseId,ComposerRole.ACCESSORY,
                        day.slots.first().sets.take(1),pinned=false)
                })
            })
        val strength=baseBlock.copy(blockId="strength",weeks=2,
            phase=TrainingPhase.STRENGTH,
            days=baseBlock.days.map{it.copy(
                purpose=ComposerSessionPurpose.STRENGTH_SKILL)})
        val cycle=original.copy(blocks=listOf(hyper,strength))
        val base=fixture.generation()
        val policy=checkNotNull(base.policy).copy(
            phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 3),
            primaryLiftMinimumDays=mapOf("bench" to 1))
        val program=base.copy(phase=TrainingPhase.STRENGTH,policy=policy,
            catalog=base.catalog+accessories,
            selectFromOfferedCatalog=true,
            maximumExercisesPerDay=8)
        val prescriptions=fixture.policies().map{
            it.copy(purpose=ComposerSessionPurpose.STRENGTH_SKILL)
        }
        val request=GeneratedAdaptiveBlockRequest(cycle,"strength",
            "dual_recovery_fenced",program,prescriptions,
            allowSourceMenuRecovery=true,allowSourceDoseRecovery=true)
        val both=GeneratedAdaptiveBlockDomain.propose(request)
        assertEquals("Combined decision: ${both.status} ${both.explanation}",
            GeneratedAdaptiveBlockStatus.STRENGTH_WORKING_DOSE_LOSS,
            both.status)
        assertEquals(StrengthRosterBoundaryStatus.CONTINUOUS,
            both.strengthRosterBoundary?.status)
        assertEquals(StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW,
            both.strengthDoseBoundary?.status)
        assertEquals(105,both.strengthDoseBoundary?.hypertrophyWorkingSets)
        assertNull(both.proposedOption)
        assertTrue(both.addedStrengthWorkingSets.isEmpty())
        val menuOnly=GeneratedAdaptiveBlockDomain.propose(
            request.copy(allowSourceDoseRecovery=false))
        assertEquals(GeneratedAdaptiveBlockStatus.STRENGTH_EXERCISE_MENU_LOSS,
            menuOnly.status)
        val doseOnly=GeneratedAdaptiveBlockDomain.propose(
            request.copy(allowSourceMenuRecovery=false))
        assertEquals(GeneratedAdaptiveBlockStatus.STRENGTH_EXERCISE_MENU_LOSS,
            doseOnly.status)
        assertNull(menuOnly.proposedOption)
        assertNull(doseOnly.proposedOption)
        assertEquals(original,fixture.cycle())
        assertEquals(4L,cycle.revision)
        assertFalse(both.mayIssueWorkout)
        assertFalse(both.maySaveProgram)
    }

}
