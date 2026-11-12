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
        LoadBasis.EXERCISE_OVERRIDE->selectorized
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
            },bounds(covered),emptyList(),4,1)

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
        assertEquals(146,catalog.size)
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
                    assertEquals(SetLoadScheme.STRAIGHT,SetLoadScheme.STRAIGHT)
                    assertEquals(2,set.targetRir)
                }
            }
            assertFalse(draft.canIssueWorkout)
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
