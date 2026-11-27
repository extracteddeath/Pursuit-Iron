package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * End-to-end acceptance of actual, source-linked Kotlin movement choices.
 *
 * Source-name matching is NOT sufficient: every reviewed movement must be
 * usable with individually confirmed physical stock, real attachments and
 * fixtures. Four representative gyms must compose actual complete weeks and
 * preserve athlete-calibrated WORKING loads in purpose-specific prescriptions.
 *
 * This is engineering evidence, NOT independent exercise science or evidence
 * that Kotlin forecasts outperform the web engine. It never issues a workout.
 */
class StarterCatalogPrescribingAcceptanceTest {
    private fun n(value:String)=BigDecimal(value)
    private val catalog=StarterExerciseTranche.movements()
    private val byId=catalog.associateBy{it.exerciseId}

    private val dumbbells=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("20"),n("25"),n("30")))
    private val barbell=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
        mapOf(n("10") to 2,n("5") to 2))
    private val cable=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.CABLE_STACK,listOf(n("20"),n("25"),n("30")))
    private val selectorized=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.EXERCISE_OVERRIDE,listOf(n("20"),n("25"),n("30")))

    /** A calibrated gym does NOT allow loads to migrate between stations. */
    private fun exactPhysicalStock(basis:LoadBasis):VerifiedLoadStock=when(basis) {
        LoadBasis.BARBELL_TOTAL->barbell
        LoadBasis.DUMBBELL_PER_HAND->dumbbells
        LoadBasis.CABLE_STACK->cable
        LoadBasis.MACHINE_STACK->VerifiedLoadStock.Totals(LoadUnit.LB,
            basis,listOf(n("20"),n("25"),n("30")))
        LoadBasis.SMITH_TOTAL->VerifiedLoadStock.Totals(LoadUnit.LB,
            basis,listOf(n("20"),n("25"),n("30")))
        LoadBasis.KETTLEBELL_EACH->VerifiedLoadStock.Totals(LoadUnit.LB,
            basis,listOf(n("20"),n("25"),n("30")))
        LoadBasis.LANDMINE_ADDED_PLATES->VerifiedLoadStock.Totals(LoadUnit.LB,
            basis,listOf(n("0"),n("20"),n("25"),n("30")))
        LoadBasis.EXERCISE_OVERRIDE->selectorized
        LoadBasis.ASSISTANCE_COUNTERWEIGHT->VerifiedLoadStock.Stack(LoadUnit.LB,
            basis,n("20"),n("5"),n("30"))
    }
    private fun calibratedLoad(spec:CatalogMovement):BigDecimal?=when(
        spec.expectedLoadBasis) {
        null->null
        LoadBasis.BARBELL_TOTAL->n("65")
        else->n("25")
    }
    private fun choice(spec:CatalogMovement)=CatalogExerciseChoice(
        spec.exerciseId,calibratedLoad(spec),90,120,
        2,4,3,1,1,5)

    private fun bounds(covered:Set<TrainingMuscle> =emptySet())=
        TrainingMuscle.entries.map{muscle->
            DeclaredMuscleDoseBounds(muscle,
                if(muscle in covered)n("2") else BigDecimal.ZERO,
                n("12"),n("20"),if(muscle in covered)1 else 0)
        }

    private fun template(covered:Set<TrainingMuscle> =emptySet())=
        ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
            listOf(0,2,4).map{weekday->
                ComposerDay(weekday,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,90,null,60))
            },bounds(covered),emptyList(),4,1,
            listOf(0,1,2).map{dayIndex->
                ComposerSessionIntent(dayIndex,
                    ComposerSessionPurpose.HYPERTROPHY)
            })

    private fun stockFor(ids:List<String>):CatalogGym {
        val movements=ids.map{byId.getValue(it)}
        val keyed=movements.filter{it.equipmentId!=null}
            .groupBy{checkNotNull(it.equipmentId)}
        // A shared physical station may not be made to support two
        // incompatible basis conventions (e.g. per-hand vs stack).
        val stock=keyed.mapValues{(_,movements)->
            val bases=movements.map{checkNotNull(it.expectedLoadBasis)}.toSet()
            require(bases.size==1){"Physical stock basis conflict: ${movements.map{it.exerciseId}}"}
            exactPhysicalStock(bases.single())
        }
        return CatalogGym(stock,
            movements.mapNotNull{it.attachmentId}.toSet(),
            movements.flatMap{it.requiredFacilityIds}.toSet())
    }

    @Test fun everyRealCatalogIdentityAcceptsOnlyConfirmedGymMetadata() {
        assertTrue(catalog.size>=178)
        val allStock=stockFor(catalog.map{it.exerciseId})
        assertTrue(allStock.confirmedStock.size<=128)
        assertEquals(catalog.size,byId.size)
        for(movement in catalog) {
            val requested=choice(movement)
            val candidate=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(template(),
                    listOf(requested),allStock))
            assertTrue("${movement.exerciseId}: ${candidate.issues}",
                candidate.issues.isEmpty())
            val actual=checkNotNull(candidate.composedRequest)
                .exercises.single()
            assertEquals(movement.exerciseId,actual.exerciseId)
            assertEquals(movement.credits,actual.credits)
            assertEquals(movement.sourcePracticalReps,actual.sourcePracticalReps)
            val equipment=actual.equipment
            if(requested.selectedLoad==null) {
                assertEquals(ComposerEquipment.Bodyweight,equipment)
            } else {
                val verified=equipment as ComposerEquipment.Verified
                assertEquals(requested.selectedLoad,verified.plannedLoad)
                assertEquals(movement.expectedLoadBasis,verified.inventory?.basis)
                val inspected=EquipmentLoadDomain.inspect(verified.inventory,
                    verified.plannedLoad,verified.unit)
                assertTrue("${movement.exerciseId}: ${inspected}",
                    inspected is LoadInspection.Confirmed && inspected.exact)
            }
            assertFalse(candidate.mayIssueWorkout)
            assertFalse(candidate.mayOverwriteAuthoredProgram)
        }
    }

    @Test fun everyStandardIdentityRefusesUnverifiedPhysicalInputs() {
        val schedule=template()
        for(movement in catalog) {
            val selected=choice(movement)
            val gym=stockFor(listOf(movement.exerciseId))
            // Isolate one real, unchanged catalog entry per case. There is
            // no need to rebuild the whole catalog or compose a rejected week.
            val request=CatalogWeekRequest(schedule,listOf(movement),
                listOf(selected),gym)
            fun refuse(kind:CatalogIssueType,
                changed:CatalogWeekRequest) {
                val result=CoachExerciseCatalogDomain.review(changed)
                assertEquals(movement.exerciseId,
                    CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,result.status)
                assertTrue("${movement.exerciseId}: expected $kind; ${result.issues}",
                    result.issues.any{it.exerciseId==movement.exerciseId && it.kind==kind})
                assertNull(result.composedRequest)
                assertNull(result.draft)
                assertFalse(result.mayIssueWorkout)
                assertFalse(result.mayOverwriteAuthoredProgram)
            }
            val equipmentId=movement.equipmentId
            if(equipmentId==null) {
                refuse(CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY,
                    request.copy(chosen=listOf(selected.copy(selectedLoad=n("25")))))
            } else {
                refuse(CatalogIssueType.EQUIPMENT_UNAVAILABLE,
                    request.copy(gym=gym.copy(confirmedStock=emptyMap())))
                val wrongBasis=if(movement.expectedLoadBasis==LoadBasis.DUMBBELL_PER_HAND)
                    cable else dumbbells
                refuse(CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,
                    request.copy(gym=gym.copy(confirmedStock=mapOf(equipmentId to wrongBasis))))
                refuse(CatalogIssueType.LOAD_UNVERIFIED,
                    request.copy(chosen=listOf(selected.copy(selectedLoad=null))))
                refuse(CatalogIssueType.LOAD_UNVERIFIED,
                    request.copy(chosen=listOf(selected.copy(
                        selectedLoad=checkNotNull(selected.selectedLoad).add(n("0.5"))))))
            }
            movement.attachmentId?.let{attachment->
                refuse(CatalogIssueType.ATTACHMENT_UNAVAILABLE,
                    request.copy(gym=gym.copy(
                        availableAttachmentIds=gym.availableAttachmentIds-attachment)))
            }
            for(facility in movement.requiredFacilityIds) {
                refuse(CatalogIssueType.FACILITY_UNAVAILABLE,
                    request.copy(gym=gym.copy(
                        availableFacilityIds=gym.availableFacilityIds-facility)))
            }
        }
    }

    private fun fullCatalogAthlete():ReviewedAthleteSetup {
        val allStock=stockFor(catalog.map{it.exerciseId})
        val gym=ReviewedGymProfile("full-catalog-gym",LoadUnit.LB,
            allStock.confirmedStock,allStock.availableFacilityIds,allStock.availableAttachmentIds)
        return ReviewedAthleteSetup(
            "profile-1","athlete-1",AthleteTrainingGoal.HYPERTROPHY,
            AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,
            setOf(0,2,4),"America/New_York",SessionTimeBucket.SIXTY_TO_NINETY,
            HardSessionLimit.NO_HARD_LIMIT,gym,true,false,true,3,
            emptySet(),emptySet(),emptySet(),emptySet(),emptySet(),emptySet(),setOf(1,3,5,6))
    }
    private fun review(profile:ReviewedAthleteSetup)=AthleteSetupReview(
        AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,emptyList(),profile)

    @Test fun explicitWebVariantsRequireTheirOwnCalibrationBeforeFirstProgramSelection() {
        val athlete=fullCatalogAthlete()
        val explicit=listOf("high-inc-db-press","bottom-half-db-fly",
            "deficit-pendlay-row","low-to-high-face-pull","leaning-cable-ohext",
            "ad-press-barbell","ad-press-smith")
        val baseIds=listOf("barbell_bench","back_squat","one_arm_db_row",
            "db_rdl","db_lateral_raise","db_curl","db_overhead_triceps","db_calf_raise")
        val baseLoads=baseIds.associateWith{checkNotNull(calibratedLoad(byId.getValue(it)))}
        val request=FirstProgramStartRequest(review(athlete),catalog,
            baseLoads,setOf("bodyweight_crunch"),TrainingPhase.HYPERTROPHY,
            nonSetSecondsPerDay=900)
        val unselected=CoachFirstProgramStartDomain.review(request)
        assertTrue(explicit.all{id->unselected.eligibleMovements.any{it.exerciseId==id}})
        assertEquals(baseIds.toSet()+setOf("bodyweight_crunch"),unselected.chosenExerciseIds.toSet())
        assertTrue(unselected.generation?.candidate?.catalogReview?.requestedExerciseIds
            ?.none{it in explicit}==true)
        for(id in explicit) {
            val selection=baseLoads+(id to checkNotNull(calibratedLoad(byId.getValue(id))))
            val selected=CoachFirstProgramStartDomain.review(request.copy(startingLoads=selection))
            assertEquals(id,selection.keys+setOf("bodyweight_crunch"),selected.chosenExerciseIds.toSet())
            assertFalse(id,selected.mayIssueWorkout)
            val invalid=CoachFirstProgramStartDomain.review(request.copy(startingLoads=
                selection+(id to selection.getValue(id).add(n("0.5")))))
            assertEquals(id,FirstProgramStartStatus.INVALID_CALIBRATION,invalid.status)
            assertNull(id,invalid.generation)
        }
    }

    @Test fun everyRealStandardIdentityIsAuthorableAndProjectsCorrectedActualsWithItsOwnStock() {
        val athlete=fullCatalogAthlete()
        val all=CustomExerciseAuthoringDomain.picker(review(athlete),catalog)
        assertEquals(catalog.map{it.exerciseId}.toSet(),all.map{it.exerciseId}.toSet())
        assertEquals(catalog.size,all.size)
        val options=all.associateBy{it.exerciseId}
        for(move in catalog) {
            val option=options.getValue(move.exerciseId)
            assertEquals(move.familyId,option.familyId)
            assertEquals(move.variantId,option.variantId)
            assertEquals(move.attachmentId,option.attachmentId)
            assertEquals(move.expectedLoadBasis,option.loadBasis)
            assertEquals(move.credits,option.muscleCredits)
            assertEquals(move.role,option.role)
            assertEquals(move.pattern,option.pattern)
            val load=calibratedLoad(move)?.let{
                DraftSetLoad.Verified(LoadUnit.LB,checkNotNull(move.expectedLoadBasis),it)
            }?:DraftSetLoad.ExplicitBodyweight
            val set=CustomAuthoredSet(DraftSetType.WORKING,load,
                checkNotNull(move.sourcePracticalReps),2,40,90)
            fun validate(profile:ReviewedAthleteSetup,selected:CustomAuthoredSet=set)=
                CustomExerciseAuthoringDomain.reviewFreshSets(review(profile),
                    listOf(move),move.exerciseId,listOf(selected))
            assertNull(move.exerciseId,validate(athlete))
            // Traverse the same validated snapshot/reducer/history owners used
            // after native consent. No fake zero for bodyweight or inferred RIR.
            val cycle=CustomTrainingCycle("catalog-cycle",1,"athlete-1",
                AthleteTrainingGoal.HYPERTROPHY,"Catalog acceptance",listOf(
                    CustomTrainingBlock("base","Hypertrophy",TrainingPhase.HYPERTROPHY,
                        CustomBlockPurpose.TRAINING,3,CustomSplitIdentity.Freeform("Catalog"),
                        listOf(0,2,4).map{day->
                            CustomTrainingDay(day,"Day $day",ComposerSessionPurpose.HYPERTROPHY,
                                listOf(CustomExerciseSlot("slot-$day",move.exerciseId,
                                    move.role,listOf(set),attachmentId=move.attachmentId)))
                        })))
            val preview=CustomWorkoutSnapshotDomain.preview(cycle,review(athlete),
                listOf(move),1,1,0)
            assertTrue("${move.exerciseId}: $preview",preview is CustomWorkoutSnapshotResult.Preview)
            val snapshot=(preview as CustomWorkoutSnapshotResult.Preview).snapshot
            val slot=snapshot.slots.single()
            val target=slot.sets.single()
            assertEquals(move.exerciseId,slot.exerciseId)
            assertEquals(move.familyId,slot.familyId)
            assertEquals(move.variantId,slot.variantId)
            assertEquals(move.attachmentId,slot.attachmentId)
            assertEquals(set,target.prescription)
            var journal=CustomWorkoutJournalDomain.begin("catalog-workout",snapshot,"a".repeat(64))
            fun accept(operation:CustomWorkoutJournalOperation) {
                val result=CustomWorkoutJournalDomain.reduce(journal,operation)
                assertTrue("${move.exerciseId}: $result",result is CustomWorkoutJournalDecision.Proposed)
                journal=(result as CustomWorkoutJournalDecision.Proposed).draft
            }
            val actual=KotlinPerformedSet((load as? DraftSetLoad.Verified)?.value,
                (load as? DraftSetLoad.Verified)?.unit,set.reps.minimum,null)
            val key=CustomWorkoutVisitKey(target.setKey,CustomWorkoutVisitPart.MAIN)
            accept(CustomWorkoutJournalOperation.Record("catalog-actual",0,key,
                KotlinJournalSetOutcome.COMPLETED,actual))
            val corrected=actual.copy(actualRir=1)
            accept(CustomWorkoutJournalOperation.Record("catalog-correction",1,key,
                KotlinJournalSetOutcome.COMPLETED,corrected,"catalog-actual"))
            accept(CustomWorkoutJournalOperation.Finish("catalog-finish",2))
            val history=CustomWorkoutHistoryEvidenceDomain.project(journal)
            assertTrue("${move.exerciseId}: $history",history is CustomHistoryEvidenceResult.Candidate)
            val fact=(history as CustomHistoryEvidenceResult.Candidate).evidence.visits.single()
            assertEquals(move.exerciseId,fact.exerciseId)
            assertEquals(target.setKey,fact.setKey)
            assertEquals(load,fact.prescribedLoad)
            assertEquals(set.reps,fact.prescribedReps)
            assertEquals(2,fact.prescribedRir)
            assertEquals(corrected,fact.actual)
            assertEquals("catalog-correction",fact.effectiveEventId)
            assertEquals(snapshot,journal.originalSnapshot)
            assertEquals(move.exerciseId,CustomAuthoringRefusal.EXERCISE_UNAVAILABLE,
                validate(athlete.copy(excludedExerciseIds=setOf(move.exerciseId))))
            if(load is DraftSetLoad.Verified) {
                val equipmentId=checkNotNull(move.equipmentId)
                assertEquals(move.exerciseId,CustomAuthoringRefusal.EXERCISE_UNAVAILABLE,
                    validate(athlete.copy(gym=gym.copy(confirmedStock=gym.confirmedStock-equipmentId))))
                assertEquals(move.exerciseId,CustomAuthoringRefusal.INATTAINABLE_LOAD,
                    validate(athlete,set.copy(load=load.copy(value=load.value.add(n("0.5"))))))
                assertEquals(move.exerciseId,CustomAuthoringRefusal.INATTAINABLE_LOAD,
                    validate(athlete,set.copy(load=load.copy(unit=LoadUnit.KG))))
            } else {
                assertEquals(move.exerciseId,CustomAuthoringRefusal.EXERCISE_UNAVAILABLE,
                    validate(athlete.copy(allowBodyweight=false)))
            }
            move.attachmentId?.let{attachment->
                assertEquals(move.exerciseId,CustomAuthoringRefusal.EXERCISE_UNAVAILABLE,
                    validate(athlete.copy(gym=gym.copy(availableAttachments=gym.availableAttachments-attachment))))
            }
            for(facility in move.requiredFacilityIds)
                assertEquals(move.exerciseId,CustomAuthoringRefusal.EXERCISE_UNAVAILABLE,
                    validate(athlete.copy(gym=gym.copy(availableFacilities=gym.availableFacilities-facility))))
        }
    }

    private data class RealGymScenario(
        val title:String,
        val exercises:List<String>,
        val missingMuscleIfAbsent:TrainingMuscle
    )

    private val representativeGyms=listOf(
        RealGymScenario("dumbbell bench + supported row + lunges",
            listOf("db-neutral-press","helms-row","walking-lunge"),
            TrainingMuscle.QUADS),
        RealGymScenario("barbell floor press + underhand row + pinned squat",
            listOf("floor-press","underhand-row","pin-squat"),
            TrainingMuscle.QUADS),
        RealGymScenario("independent chest/row/pendulum machines",
            listOf("incline-machine-press","machine-row","pendulum-squat"),
            TrainingMuscle.QUADS),
        RealGymScenario("cable pressing and rowing + bodyweight squat",
            listOf("cable-press","seated-row","bw-squat"),
            TrainingMuscle.QUADS)
    )

    @Test fun realGymVariantsComposeThreeDayWeeksAndPreserveStraightSetLoads() {
        for(scenario in representativeGyms) {
            val selected=scenario.exercises.map{byId.getValue(it)}
            val direct=selected.flatMap{it.credits}.filter{
                it.role==MuscleCreditRole.DIRECT
            }.map{it.muscle}.toSet()
            assertEquals(scenario.title,setOf(TrainingMuscle.CHEST,
                TrainingMuscle.UPPER_BACK,TrainingMuscle.QUADS),direct)
            val reviewed=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(template(direct),
                    selected.map(::choice),stockFor(scenario.exercises)))
            assertEquals("${scenario.title}: ${reviewed.issues} / ${reviewed.draft}",
                CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
                reviewed.status)
            val week=checkNotNull(reviewed.draft)
            assertTrue(scenario.title,week.audit.satisfiesDeclaredHardConstraints)
            assertEquals(3,week.sessions.size)
            assertTrue(scenario.title,week.audit.daysWithoutWorkingSets.isEmpty())
            assertEquals(scenario.exercises.toSet(),
                week.visits.map{it.exerciseId}.toSet())
            assertTrue(scenario.title,
                week.audit.muscles.getValue(scenario.missingMuscleIfAbsent)
                    .gapToMev.signum()==0)

            val policies=selected.map{movement->
                ExercisePrescriptionPolicy(movement.exerciseId,
                    ComposerSessionPurpose.HYPERTROPHY,
                    checkNotNull(movement.sourcePracticalReps),2,90,120,
                    SetLoadScheme.STRAIGHT)
            }
            val draft=CoachSessionPrescriptionDomain.draft(
                checkNotNull(reviewed.composedRequest),policies)
            assertEquals("${scenario.title}: ${draft.reviewNotes}",
                PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,draft.status)
            assertTrue(scenario.title,
                checkNotNull(draft.finalAudit).satisfiesDeclaredHardConstraints)
            for(exercise in draft.sessions.flatMap{it.exercises}) {
                val expected=calibratedLoad(byId.getValue(exercise.exerciseId))
                val working=exercise.sets.filter{it.type==DraftSetType.WORKING}
                assertTrue(scenario.title+": missing working sets ${exercise.exerciseId}",
                    working.isNotEmpty())
                for(set in working) {
                    if(expected==null)
                        assertEquals(DraftSetLoad.ExplicitBodyweight,set.load)
                    else
                        assertEquals(expected,
                            (set.load as DraftSetLoad.Verified).value)
                    assertEquals(2,set.targetRir)
                }
            }
            assertFalse(draft.canIssueWorkout)
        }
    }

    @Test fun deficitPendlayRowDoesNotRequireADedicatedStepPlatform() {
        val id="deficit-pendlay-row"
        val move=byId.getValue(id)
        val gym=stockFor(listOf(id))
        assertFalse(move.requiredFacilityIds.contains("step-platform"))
        fun inspect(candidate:CatalogGym)=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),listOf(choice(move)),candidate))
        val ordinaryBarbellGym=gym.copy(availableFacilityIds=emptySet())
        assertTrue(inspect(ordinaryBarbellGym).issues.isEmpty())
        val noBarbell=inspect(ordinaryBarbellGym.copy(confirmedStock=emptyMap()))
        assertNull(noBarbell.composedRequest)
        assertTrue(noBarbell.issues.any{
            it.exerciseId==id && it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
        })
    }

    @Test fun smallDeficitLiftsAllowOrdinaryStockWhileStepUpsRetainFixtureRequirements() {
        for(id in listOf("deficit-barbell-deadlift","db-deficit-lunge","deficit-db-rdl")) {
            val move=byId.getValue(id)
            val gym=stockFor(listOf(id)).copy(availableFacilityIds=emptySet())
            fun inspect(candidate:CatalogGym)=CoachExerciseCatalogDomain.review(
                StarterExerciseTranche.forGym(template(),listOf(choice(move)),candidate))
            assertTrue(id,move.requiredFacilityIds.isEmpty())
            assertTrue(id,inspect(gym).issues.isEmpty())
            assertTrue(id,inspect(gym.copy(confirmedStock=emptyMap())).issues.any{
                it.exerciseId==id && it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
            })
        }
        for(id in listOf("step-up","db-deficit-step-up")) {
            val move=byId.getValue(id)
            val gym=stockFor(listOf(id))
            assertTrue(id,"step-platform" in move.requiredFacilityIds)
            val missing=CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
                template(),listOf(choice(move)),
                gym.copy(availableFacilityIds=gym.availableFacilityIds-"step-platform")))
            assertTrue(id,missing.issues.any{
                it.exerciseId==id && it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
            })
        }
    }

    @Test fun switchingMachinesHandlesAndUnverifiedWeightsRefusesInvalidDraft() {
        fun inspect(id:String,gym:CatalogGym,value:BigDecimal?=n("25"))=
            CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
                template(),listOf(choice(byId.getValue(id)).copy(
                    selectedLoad=value)),gym))

        val row=stockFor(listOf("machine-row"))
        // The physical machine is not a generic "row" inventory alias.
        val a=inspect("machine-row",row)
        assertTrue(a.issues.isEmpty())
        val b=inspect("machine-row",stockFor(listOf("pendulum-squat")))
        assertEquals(null,b.composedRequest)
        assertTrue(b.issues.any{it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE})
        // A second real machine with a different load scale cannot satisfy A.
        val invalid=inspect("machine-row",CatalogGym(mapOf(
            "machine-row-station" to VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.EXERCISE_OVERRIDE,listOf(n("35"),n("45")))),
            emptySet()))
        assertTrue(invalid.issues.any{it.kind==CatalogIssueType.LOAD_UNVERIFIED})
        assertNull(invalid.composedRequest)
        val cableGym=stockFor(listOf("seated-row"))
        val noHandle=inspect("seated-row",cableGym.copy(
            availableAttachmentIds=emptySet()))
        assertTrue(noHandle.issues.any{
            it.kind==CatalogIssueType.ATTACHMENT_UNAVAILABLE
        })
        assertNull(noHandle.composedRequest)
        val missingPins=inspect("pin-squat",stockFor(listOf("pin-squat"))
            .copy(availableFacilityIds=setOf("rack")),n("65"))
        assertTrue(missingPins.issues.any{
            it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
        assertNull(missingPins.composedRequest)
        val unbuildable=inspect("pin-squat",stockFor(listOf("pin-squat")),
            n("66"))
        assertTrue(unbuildable.issues.any{
            it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        assertNull(unbuildable.composedRequest)
    }
}
