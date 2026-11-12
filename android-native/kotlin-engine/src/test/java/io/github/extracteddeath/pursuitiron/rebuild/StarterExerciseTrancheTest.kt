package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class StarterExerciseTrancheTest {
    private fun n(value:String)=BigDecimal(value)
    private val data=StarterExerciseTranche.entries()
    private val availableBar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),
        mapOf(n("45") to 2,n("25") to 2,n("10") to 2,n("5") to 2))
    private val db=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n("15"),n("20"),n("25"),
            n("30"),n("35"),n("40"),n("45"),n("50")))
    private fun gym(
        facilities:Set<String> =setOf("flat-bench","rack","incline-bench"),
        stock:Map<String,VerifiedLoadStock> =mapOf(
            "straight-bar" to availableBar,"dumbbell-rack" to db)
    )=CatalogGym(stock,emptySet(),facilities)
    private fun bounds():List<DeclaredMuscleDoseBounds> =
        TrainingMuscle.entries.map {muscle->
            val minimum=if(muscle in setOf(TrainingMuscle.CHEST,
                TrainingMuscle.UPPER_BACK,TrainingMuscle.QUADS))n("2")else n("0")
            DeclaredMuscleDoseBounds(muscle,minimum,n("20"),n("30"),
                if(minimum>BigDecimal.ZERO)1 else 0)
        }
    private fun template():ComposerRequest=ComposerRequest(
        ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
        listOf(0,2,4).map {day->
            ComposerDay(day,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,90,null,60))
        },bounds(),emptyList(),4,1)
    private fun choice(id:String,load:BigDecimal?)=CatalogExerciseChoice(
        id,load,90,150,2,3,3,1,1,5)
    private fun selected()=listOf(choice("barbell_bench",n("135")),
        choice("one_arm_db_row",n("30")),choice("back_squat",n("135")))

    @Test fun exactSourceIdsAreStableAndNoVariantsShareIdentity() {
        val originalIds=listOf(
            "barbell_bench","paused_bench","back_squat","barbell_ohp",
            "incline_db_press","one_arm_db_row","db_lateral_raise",
            "db_curl","db_overhead_triceps","db_split_squat","db_rdl",
            "db_calf_raise","bodyweight_crunch","barbell_row","rdl",
            "conventional_deadlift","db_rear_delt_raise","pullup",
            "leg_extension","seated_leg_curl"
        )
        // The first 20 curated movements are unchanged and stable.
        // New source-linked variants must append rather than renumber
        // old identities used by serialized gym/workout/history data.
        assertEquals(originalIds,data.take(20).map{it.legacyCuratedId})
        assertEquals(listOf("wrist-curl","reverse-wrist-curl"),
            data.drop(20).take(2).map{it.legacyCuratedId})
        assertEquals(listOf("inc-bb-bench","incline_smith","machine_press","leg_press"),
            data.drop(22).map{it.legacyCuratedId})
        assertEquals(26,data.size)
        assertEquals(26,data.map {it.legacyCuratedId}.distinct().size)
        assertTrue(data.all {it.legacyCuratedId==it.movement.exerciseId})
        assertEquals(26,data.map {
            it.movement.familyId to it.movement.variantId
        }.distinct().size)
        assertTrue(data.all {
            it.movement.creditReviewReference=="engineering-provisional-v1"
        })
        assertTrue(data.all {it.mappingNotes.isNotBlank()})
        assertTrue(data.none {it.mayTransferPreviousLoad})
        assertTrue(data.all {
            it.practicalRepBand==it.movement.sourcePracticalReps
        })
        assertEquals(RepBand(1,8),data.first {
            it.legacyCuratedId=="conventional_deadlift"
        }.movement.sourcePracticalReps)
    }

    @Test fun allRealGymMovementsRequireCorrectLoadBasis() {
        val required=StarterExerciseTranche.movements()
        assertTrue(required.filter {it.equipmentId=="straight-bar"}
            .all {it.expectedLoadBasis==LoadBasis.BARBELL_TOTAL})
        assertTrue(required.filter {it.equipmentId=="dumbbell-rack"}
            .all {it.expectedLoadBasis==LoadBasis.DUMBBELL_PER_HAND})
        assertEquals(setOf("flat-bench","rack"),
            required.first {it.exerciseId=="barbell_bench"}.requiredFacilityIds)
        assertEquals(setOf("incline-bench"),
            required.first {it.exerciseId=="incline_db_press"}.requiredFacilityIds)
        assertEquals(null,required.first {
            it.exerciseId=="bodyweight_crunch"
        }.equipmentId)
    }

    @Test fun addedPressAndLegMovementsRequireExactPhysicalEquipment() {
        val ids=setOf("inc-bb-bench","incline_smith","machine_press","leg_press")
        val added=data.filter{it.legacyCuratedId in ids}
        assertEquals(4,added.size)
        assertTrue(added.none {it.mayTransferPreviousLoad})
        assertEquals(LoadBasis.BARBELL_TOTAL,
            added.single{it.legacyCuratedId=="inc-bb-bench"}
                .movement.expectedLoadBasis)
        assertEquals(LoadBasis.SMITH_TOTAL,
            added.single{it.legacyCuratedId=="incline_smith"}
                .movement.expectedLoadBasis)
        assertTrue(added.filter {it.legacyCuratedId in
            setOf("machine_press","leg_press")}
            .all{it.movement.expectedLoadBasis==LoadBasis.EXERCISE_OVERRIDE})
        assertEquals(RepBand(6,10),
            added.single{it.legacyCuratedId=="inc-bb-bench"}.practicalRepBand)
        assertEquals(RepBand(5,20),
            added.single{it.legacyCuratedId=="incline_smith"}.practicalRepBand)
        assertEquals(RepBand(5,25),
            added.single{it.legacyCuratedId=="machine_press"}.practicalRepBand)
        assertEquals(RepBand(6,25),
            added.single{it.legacyCuratedId=="leg_press"}.practicalRepBand)
        val incline=added.filter{it.legacyCuratedId in
            setOf("inc-bb-bench","incline_smith")}
        assertTrue(incline.all{"incline-bench" in it.movement.requiredFacilityIds})
        assertTrue("rack" in added.single{
            it.legacyCuratedId=="inc-bb-bench"
        }.movement.requiredFacilityIds)
        assertEquals(4,added.map{it.movement.equipmentId}.distinct().size)
        assertEquals("heavy-bench",added.single{
            it.legacyCuratedId=="inc-bb-bench"
        }.movement.recoveryGroupId)
    }

    @Test fun newVariantDoesNotAcceptUnavailableBenchOrBorrowOtherMachineStock() {
        val smith=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.SMITH_TOTAL,
            listOf(n("45"),n("50"),n("55")))
        val machine=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("40"),n("50"),n("60")))
        val gymStock=mapOf(
            "straight-bar" to availableBar,
            "incline-smith-station" to smith,
            "chest-press-machine" to machine,
            "leg-press-machine" to machine)
        fun assess(
            id:String,load:String,stock:Map<String,VerifiedLoadStock> =gymStock,
            facilities:Set<String> =setOf("incline-bench","rack")
        )=CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
            template(),listOf(choice(id,n(load))),gym(facilities,stock)))
        for(id in listOf("inc-bb-bench","incline_smith")) {
            val withoutIncline=assess(id,"50",facilities=setOf("rack"))
            assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,
                withoutIncline.status)
            assertTrue(withoutIncline.issues.any{
                it.exerciseId==id && it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
            })
        }
        val missingSmith=assess("incline_smith","50",
            stock=gymStock-"incline-smith-station")
        assertTrue(missingSmith.issues.any{
            it.exerciseId=="incline_smith" &&
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
        })
        val wrongSmithBasis=assess("incline_smith","50",
            stock=gymStock+("incline-smith-station" to machine))
        assertTrue(wrongSmithBasis.issues.any{
            it.exerciseId=="incline_smith" &&
                it.kind==CatalogIssueType.EQUIPMENT_BASIS_MISMATCH
        })
        val impossible=assess("machine_press","55")
        assertTrue(impossible.issues.any{
            it.exerciseId=="machine_press" &&
                it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        val otherMachineOnly=assess("machine_press","50",
            stock=gymStock-"chest-press-machine")
        assertTrue(otherMachineOnly.issues.any{
            it.exerciseId=="machine_press" &&
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
        })
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,
            assess("leg_press","55").status)
        assertTrue(assess("leg_press","55").issues.any{
            it.exerciseId=="leg_press" && it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        // Passing catalog validation does not bypass whole-week constraints,
        // history, consent or workout issuance. Nothing silently alters load.
        val reviewed=assess("machine_press","50")
        assertTrue(reviewed.issues.isEmpty())
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.mayOverwriteAuthoredProgram)
    }

    @Test fun realThreeDayCompoundWeekComposesOnlyWithConfirmedPhysicalGym() {
        val request=StarterExerciseTranche.forGym(template(),selected(),gym())
        val result=CoachExerciseCatalogDomain.review(request)
        assertEquals(CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
            result.status)
        assertTrue(result.issues.isEmpty())
        val draft=checkNotNull(result.draft)
        assertTrue(draft.audit.satisfiesDeclaredHardConstraints)
        assertTrue(draft.visits.any {it.exerciseId=="barbell_bench"})
        assertTrue(draft.visits.any {it.exerciseId=="one_arm_db_row"})
        assertTrue(draft.visits.any {it.exerciseId=="back_squat"})
        assertEquals(3,draft.audit.sessions.size)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayOverwriteAuthoredProgram)
        assertTrue(request.template.exercises.isEmpty())
    }

    @Test fun theSameBarbellCannotBenchWithoutBenchOrSquatWithoutRack() {
        val withoutBench=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selected(),
                gym(facilities=setOf("rack"))))
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,
            withoutBench.status)
        assertTrue(withoutBench.issues.any {
            it.exerciseId=="barbell_bench" &&
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
        val withoutRack=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selected(),
                gym(facilities=setOf("flat-bench"))))
        assertTrue(withoutRack.issues.any {
            it.exerciseId=="back_squat" &&
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
    }

    @Test fun aFlatBenchDoesNotConfirmAnInclineBench() {
        val request=StarterExerciseTranche.forGym(template(),listOf(
            choice("incline_db_press",n("30")),
            choice("one_arm_db_row",n("30")),
            choice("back_squat",n("135"))
        ),gym(facilities=setOf("flat-bench","rack")))
        val result=CoachExerciseCatalogDomain.review(request)
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,result.status)
        assertTrue(result.issues.any {
            it.exerciseId=="incline_db_press" &&
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
    }

    @Test fun missingOrInvalidAthleteWeightIsNeverInventedFromTheCatalog() {
        val missing=selected().map {if(it.exerciseId=="barbell_bench")
            it.copy(selectedLoad=null)else it}
        val noLoad=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),missing,gym()))
        assertTrue(noLoad.issues.any {
            it.exerciseId=="barbell_bench" &&
                it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        val improbable=selected().map {
            if(it.exerciseId=="one_arm_db_row")
                it.copy(selectedLoad=n("38.5"))else it
        }
        val fabricated=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),improbable,gym()))
        assertTrue(fabricated.issues.any {
            it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        assertNull(fabricated.composedRequest)
    }

    @Test fun bicepsAndBrachialisAreNotConflatedInCurlPrescriptions() {
        val curl=data.first {it.legacyCuratedId=="db_curl"}.movement
        assertEquals(listOf(TrainingMuscle.BICEPS),
            curl.credits.map {it.muscle})
        assertFalse(curl.credits.any {it.muscle==TrainingMuscle.BRACHIALIS})
        val row=data.first {it.legacyCuratedId=="one_arm_db_row"}.movement
        assertEquals(TrainingMuscle.UPPER_BACK,
            row.credits.first {it.role==MuscleCreditRole.DIRECT}.muscle)
        assertTrue(row.credits.any {it.muscle==TrainingMuscle.LATS &&
            it.role==MuscleCreditRole.INDIRECT})
        assertTrue(row.credits.any {it.muscle==TrainingMuscle.BICEPS &&
            it.role==MuscleCreditRole.INDIRECT})
        assertTrue(data.first {it.legacyCuratedId=="db_rdl"}
            .mappingNotes.contains("quarantined"))
    }

    @Test fun bodyweightAndCalvesAreNotRewrittenAsGenericZeroPoundEquipment() {
        val core=data.first {it.legacyCuratedId=="bodyweight_crunch"}.movement
        assertNull(core.equipmentId)
        assertEquals(TrainingMuscle.ABS,core.credits.single().muscle)
        val calf=data.first {it.legacyCuratedId=="db_calf_raise"}.movement
        assertEquals(TrainingMuscle.CALVES,calf.credits.single().muscle)
        val invalid=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),listOf(
                choice("bodyweight_crunch",n("0"))),gym()))
        assertTrue(invalid.issues.any {
            it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
        })
    }

    @Test fun newlyMappedPullAndHingeMovementsPreservePhysicalSemantics() {
        val byId=StarterExerciseTranche.movements().associateBy {it.exerciseId}
        assertEquals(LoadBasis.BARBELL_TOTAL,byId.getValue("barbell_row").expectedLoadBasis)
        assertEquals(LoadBasis.BARBELL_TOTAL,byId.getValue("rdl").expectedLoadBasis)
        assertEquals(LoadBasis.BARBELL_TOTAL,
            byId.getValue("conventional_deadlift").expectedLoadBasis)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,
            byId.getValue("db_rear_delt_raise").expectedLoadBasis)
        assertNotEquals(byId.getValue("db_rdl").variantId,byId.getValue("rdl").variantId)
        assertNull(byId.getValue("pullup").equipmentId)
        assertEquals(setOf("pullup-bar"),byId.getValue("pullup").requiredFacilityIds)
        assertTrue(byId.getValue("pullup").credits.any {
            it.muscle==TrainingMuscle.LATS && it.role==MuscleCreditRole.DIRECT
        })
        assertEquals(setOf(TrainingMuscle.HAMSTRINGS,TrainingMuscle.GLUTES),
            byId.getValue("conventional_deadlift").credits
                .filter {it.role==MuscleCreditRole.DIRECT}.map {it.muscle}.toSet())
        assertFalse(byId.getValue("conventional_deadlift").credits.any {
            it.muscle==TrainingMuscle.LATS || it.muscle==TrainingMuscle.UPPER_BACK
        })
        assertFalse(byId.getValue("db_rear_delt_raise").credits.any {
            it.muscle==TrainingMuscle.LATS || it.muscle==TrainingMuscle.UPPER_BACK
        })
    }

    @Test fun unweightedPullupRequiresActualBarAndCannotAcceptFictitiousLoad() {
        val noBar=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),
                listOf(choice("pullup",null)),gym()))
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,noBar.status)
        assertTrue(noBar.issues.any {
            it.exerciseId=="pullup" &&
                it.kind==CatalogIssueType.FACILITY_UNAVAILABLE
        })
        val loaded=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),
                listOf(choice("pullup",n("0"))),
                gym(facilities=setOf("pullup-bar"))))
        assertTrue(loaded.issues.any {
            it.exerciseId=="pullup" &&
                it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
        })
        val available=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),
                listOf(choice("pullup",null)),
                gym(facilities=setOf("pullup-bar"))))
        assertTrue(available.issues.none {
            it.kind==CatalogIssueType.FACILITY_UNAVAILABLE ||
                it.kind==CatalogIssueType.INCOMPATIBLE_EXERCISE_IDENTITY
        })
        assertFalse(available.mayIssueWorkout)
    }

    @Test fun newRealLiftsFailClosedWhenPhysicalLoadNotAvailable() {
        val invalid=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),listOf(
                choice("barbell_row",n("159.5")),
                choice("db_rear_delt_raise",n("38.5"))
            ),gym()))
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,invalid.status)
        assertEquals(setOf("barbell_row","db_rear_delt_raise"),
            invalid.issues.filter {it.kind==CatalogIssueType.LOAD_UNVERIFIED}
                .map {it.exerciseId}.toSet())
        assertNull(invalid.composedRequest)
    }

    @Test fun separateMachinesRequireSeparateActualSelectableLoads() {
        val leg=StarterExerciseTranche.movements().first {
            it.exerciseId=="leg_extension"
        }
        val curl=StarterExerciseTranche.movements().first {
            it.exerciseId=="seated_leg_curl"
        }
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,leg.expectedLoadBasis)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,curl.expectedLoadBasis)
        assertNotEquals(leg.equipmentId,curl.equipmentId)
        assertEquals(listOf(TrainingMuscle.QUADS),leg.credits.map {it.muscle})
        assertEquals(listOf(TrainingMuscle.HAMSTRINGS),curl.credits.map {it.muscle})
        assertEquals(RepBand(8,30),leg.sourcePracticalReps)
        assertEquals(RepBand(6,25),curl.sourcePracticalReps)

        val legMachine=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("40"),n("50")))
        val curlMachine=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("35"),n("60")))
        val selections=listOf(choice("leg_extension",n("50")),
            choice("seated_leg_curl",n("60")))
        val separate=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selections,
                gym(stock=mapOf(
                    "leg-extension-machine" to legMachine,
                    "seated-leg-curl-machine" to curlMachine))))
        assertTrue(separate.issues.isEmpty())
        assertNotNull(separate.composedRequest)
        assertFalse(separate.mayIssueWorkout)

        val missingCurl=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selections,
                gym(stock=mapOf("leg-extension-machine" to legMachine))))
        assertEquals(CatalogWeekStatus.CATALOG_OR_GYM_UNRESOLVED,
            missingCurl.status)
        assertTrue(missingCurl.issues.any {
            it.exerciseId=="seated_leg_curl" &&
                it.kind==CatalogIssueType.EQUIPMENT_UNAVAILABLE
        })
        val wrongBasis=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selections,
                gym(stock=mapOf(
                    "leg-extension-machine" to legMachine,
                    "seated-leg-curl-machine" to
                        VerifiedLoadStock.Totals(LoadUnit.LB,
                            LoadBasis.MACHINE_STACK,listOf(n("60")))))))
        assertTrue(wrongBasis.issues.any {
            it.exerciseId=="seated_leg_curl" &&
                it.kind==CatalogIssueType.EQUIPMENT_BASIS_MISMATCH
        })
        assertNull(wrongBasis.composedRequest)
    }

    @Test fun missingMachineIncrementNeverBecomesAnInventedHalfStep() {
        val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("40"),n("50")))
        val invalid=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),
                listOf(choice("leg_extension",n("45"))),
                gym(stock=mapOf("leg-extension-machine" to stock))))
        assertTrue(invalid.issues.any {
            it.exerciseId=="leg_extension" &&
                it.kind==CatalogIssueType.LOAD_UNVERIFIED
        })
        assertNull(invalid.composedRequest)
    }

    @Test fun relatedHeavyVariantsRespectReviewedRecoveryAcrossCalendarDays() {
        val byId=StarterExerciseTranche.movements().associateBy {it.exerciseId}
        for(group in listOf(listOf("barbell_bench","paused_bench"),
            listOf("rdl","conventional_deadlift"))) {
            val a=byId.getValue(group[0])
            val b=byId.getValue(group[1])
            assertNotNull(a.recoveryGroupId)
            assertEquals(a.recoveryGroupId,b.recoveryGroupId)
            assertEquals(2,a.minimumRecoveryGroupDayGap)
            assertEquals(2,b.minimumRecoveryGroupDayGap)
        }
        assertNull(byId.getValue("db_curl").recoveryGroupId)
        val choices=listOf("barbell_bench","paused_bench").map {
            choice(it,n("135")).copy(requiredVisits=1,maximumVisits=1)
        }+listOf(choice("back_squat",n("135")),
            choice("one_arm_db_row",n("30")))
        val schedule=template().copy(
            days=listOf(0,1,4).map {day ->
                ComposerDay(day,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,90,null,60))
            },
            sessionIntents=listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,
                    "barbell_bench"),
                ComposerSessionIntent(1,ComposerSessionPurpose.STRENGTH_SKILL,
                    "paused_bench")))
        val result=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(schedule,choices,gym()))
        val composed=checkNotNull(result.composedRequest)
        val benchOptions=composed.exercises.filter {
            it.exerciseId=="barbell_bench" || it.exerciseId=="paused_bench"
        }
        assertTrue(benchOptions.all {
            it.recoveryGroup=="heavy-bench" &&
                it.minimumGroupCalendarDayGap==2
        })
        assertEquals(CatalogWeekStatus.WEEK_CONSTRAINTS_UNRESOLVED,
            result.status)
        assertTrue(checkNotNull(result.draft).daysWithUnmetPrimary.isNotEmpty())
        val visits=checkNotNull(result.draft).visits.filter {
            it.exerciseId in setOf("barbell_bench","paused_bench")
        }
        for(a in visits)for(b in visits) {
            if(a.dayIndex==b.dayIndex)continue
            val gap=kotlin.math.abs(schedule.days[a.dayIndex].calendarDay-
                schedule.days[b.dayIndex].calendarDay)
            assertTrue(minOf(gap,7-gap)>=2)
        }
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun returnedTrancheAndSourceChoicesCannotMutateProgramState() {
        val entries=StarterExerciseTranche.entries()
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val writable=entries as MutableList<StarterCatalogEntry>
            writable.clear()
        }
        val choices=selected()
        val selected=StarterExerciseTranche.forGym(template(),choices,gym())
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val writable=selected.chosen as MutableList<CatalogExerciseChoice>
            writable.clear()
        }
        assertEquals(n("135"),choices.first().selectedLoad)
        assertFalse(CoachExerciseCatalogDomain.review(selected).mayIssueWorkout)
    }
}
