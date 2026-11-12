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
            data.drop(22).take(4).map{it.legacyCuratedId})
        assertEquals(listOf("hack_squat","hip_thrust",
            "cable_lateral_raise","lat-pulldown","rope-pushdown"),
            data.drop(26).take(5).map{it.legacyCuratedId})
        assertEquals(listOf("cable_fly","chest_supported_row",
            "neutral_pulldown","rear_delt_fly","machine_ohp",
            "preacher_curl","cable_curl","cable_triceps",
            "overhead_cable_triceps","standing_calf","cable_crunch"),
            data.drop(31).take(11).map{it.legacyCuratedId})
        // The old 42 identities and their ordering are a saved-data
        // compatibility contract. Seven more active-web IDs append only.
        assertEquals(listOf("db-bench","db-fly","db-shoulder",
            "inc-curl","hammer","face-pull","pec-deck"),
            data.drop(42).take(7).map{it.legacyCuratedId})
        // Historical tranches are stable prefixes, not hardcoded total size.
        assertTrue(data.size>=49)
        assertEquals(data.size,data.map {it.legacyCuratedId}.distinct().size)
        assertTrue(data.all {it.legacyCuratedId==it.movement.exerciseId})
        assertEquals(data.size,data.map {
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

    @Test fun sourceLinkedMachineAndCableVariantsRequirePhysicalGymTruth() {
        val ids=listOf("hack_squat","hip_thrust","cable_lateral_raise",
            "lat-pulldown","rope-pushdown")
        val newEntries=data.filter{it.legacyCuratedId in ids}
        assertEquals(ids,newEntries.map{it.legacyCuratedId})
        assertTrue(newEntries.none{it.mayTransferPreviousLoad})
        val byId=newEntries.associateBy{it.legacyCuratedId}
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,
            byId.getValue("hack_squat").movement.expectedLoadBasis)
        assertEquals(LoadBasis.BARBELL_TOTAL,
            byId.getValue("hip_thrust").movement.expectedLoadBasis)
        assertEquals(setOf("flat-bench"),
            byId.getValue("hip_thrust").movement.requiredFacilityIds)
        assertEquals(listOf(TrainingMuscle.GLUTES),
            byId.getValue("hip_thrust").movement.credits.map{it.muscle})
        assertEquals("single-handle",
            byId.getValue("cable_lateral_raise").movement.attachmentId)
        assertEquals("rope",byId.getValue("rope-pushdown").movement.attachmentId)
        assertEquals(RepBand(8,12),byId.getValue("lat-pulldown").practicalRepBand)
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,
            byId.getValue("lat-pulldown").movement.expectedLoadBasis)
        assertEquals(LoadBasis.CABLE_STACK,
            byId.getValue("rope-pushdown").movement.expectedLoadBasis)
        assertEquals(listOf(TrainingMuscle.TRICEPS),
            byId.getValue("rope-pushdown").movement.credits.map{it.muscle})
        assertFalse(byId.getValue("lat-pulldown").movement.credits.any{
            it.muscle==TrainingMuscle.UPPER_BACK
        })
        val machine=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,
            listOf(n("40"),n("50"),n("60"),n("100")))
        val cable=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.CABLE_STACK,
            listOf(n("20"),n("25"),n("30")))
        val stocked=mapOf("hack-squat-machine" to machine,
            "lat-pulldown-machine" to machine,
            "cable-station" to cable,"straight-bar" to availableBar)
        fun check(id:String,load:String,
            actualStock:Map<String,VerifiedLoadStock> =stocked,
            attachments:Set<String> =emptySet(),
            facilities:Set<String> =setOf("flat-bench"))=
            CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
                template(),listOf(choice(id,n(load))),
                CatalogGym(actualStock,attachments,facilities)))
        fun has(review:CatalogWeekReview,kind:CatalogIssueType)=
            review.issues.any{it.kind==kind}
        assertTrue(has(check("hack_squat","100",
            actualStock=stocked-"hack-squat-machine"),
            CatalogIssueType.EQUIPMENT_UNAVAILABLE))
        assertTrue(has(check("hack_squat","55"),CatalogIssueType.LOAD_UNVERIFIED))
        assertTrue(has(check("hip_thrust","135",facilities=emptySet()),
            CatalogIssueType.FACILITY_UNAVAILABLE))
        assertTrue(has(check("cable_lateral_raise","25",
            attachments=setOf("rope")),
            CatalogIssueType.ATTACHMENT_UNAVAILABLE))
        assertFalse(has(check("cable_lateral_raise","25",
            attachments=setOf("single-handle")),
            CatalogIssueType.ATTACHMENT_UNAVAILABLE))
        assertTrue(has(check("rope-pushdown","25"),
            CatalogIssueType.ATTACHMENT_UNAVAILABLE))
        val validRope=check("rope-pushdown","25",attachments=setOf("rope"))
        assertFalse(has(validRope,CatalogIssueType.ATTACHMENT_UNAVAILABLE))
        assertFalse(has(validRope,CatalogIssueType.LOAD_UNVERIFIED))
        assertTrue(has(check("lat-pulldown","55"),
            CatalogIssueType.LOAD_UNVERIFIED))
        assertTrue(has(check("lat-pulldown","50",
            actualStock=stocked-"lat-pulldown-machine"),
            CatalogIssueType.EQUIPMENT_UNAVAILABLE))
        assertTrue(has(check("lat-pulldown","50",actualStock=
            stocked+("lat-pulldown-machine" to cable)),
            CatalogIssueType.EQUIPMENT_BASIS_MISMATCH))
        assertFalse(validRope.mayIssueWorkout)
        assertFalse(validRope.mayOverwriteAuthoredProgram)
    }

    @Test fun reviewedHackSquatCanReplaceSquatWithinRealThreeDayCompoundWeek() {
        val machine=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("90"),n("100"),n("110")))
        val stock=mapOf("straight-bar" to availableBar,
            "dumbbell-rack" to db,"hack-squat-machine" to machine)
        val chosen=listOf(choice("barbell_bench",n("135")),
            choice("one_arm_db_row",n("30")),
            choice("hack_squat",n("100")))
        val verified=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),chosen,gym(
                facilities=setOf("flat-bench","rack"),stock=stock)))
        assertEquals(CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
            verified.status)
        val draft=checkNotNull(verified.draft)
        assertTrue(draft.audit.satisfiesDeclaredHardConstraints)
        assertTrue(draft.visits.any{it.exerciseId=="hack_squat"})
        assertTrue(draft.visits.none{it.exerciseId=="back_squat"})
        assertEquals(n("100"),chosen.last().selectedLoad)
        assertFalse(verified.mayIssueWorkout)
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

    @Test fun sevenSourceVariantsFailClosedOnRealGymEquipmentAndAttachments() {
        // No global "machine" stack, blanket cable attachment or guessed
        // per-hand dumbbell weight may satisfy a distinct physical movement.
        val pec=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("40"),n("50"),n("60")))
        val cable=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.CABLE_STACK,listOf(n("20"),n("25"),n("30")))
        val stock=mapOf("dumbbell-rack" to db,
            "cable-station" to cable,"pec-deck-machine" to pec,
            "chest-press-machine" to pec)
        fun check(id:String,weight:String,
            actual:Map<String,VerifiedLoadStock> =stock,
            attachments:Set<String> =emptySet(),
            facilities:Set<String> =setOf("flat-bench","incline-bench"))=
            CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
                template(),listOf(choice(id,n(weight))),
                CatalogGym(actual,attachments,facilities)))
        fun issue(id:String,review:CatalogWeekReview,kind:CatalogIssueType)=
            review.issues.any{it.exerciseId==id && it.kind==kind}
        for(id in listOf("db-bench","db-fly"))
            assertTrue("$id wrongly bypassed the actual bench",
                issue(id,check(id,"25",facilities=emptySet()),
                    CatalogIssueType.FACILITY_UNAVAILABLE))
        assertTrue(issue("inc-curl",check("inc-curl","25",
                facilities=setOf("flat-bench")),
            CatalogIssueType.FACILITY_UNAVAILABLE))
        assertTrue(issue("hammer",check("hammer","25",
                actual=stock-"dumbbell-rack"),
            CatalogIssueType.EQUIPMENT_UNAVAILABLE))
        val wrongDb=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("25"),n("30")))
        assertTrue(issue("db-shoulder",check("db-shoulder","25",
                actual=stock+("dumbbell-rack" to wrongDb)),
            CatalogIssueType.EQUIPMENT_BASIS_MISMATCH))
        assertTrue(issue("face-pull",check("face-pull","25"),
            CatalogIssueType.ATTACHMENT_UNAVAILABLE))
        val rope=check("face-pull","25",attachments=setOf("rope"))
        assertFalse(issue("face-pull",rope,CatalogIssueType.ATTACHMENT_UNAVAILABLE))
        assertFalse(issue("face-pull",rope,CatalogIssueType.LOAD_UNVERIFIED))
        assertTrue(issue("pec-deck",check("pec-deck","50",
                actual=stock-"pec-deck-machine"),
            CatalogIssueType.EQUIPMENT_UNAVAILABLE))
        assertTrue(issue("pec-deck",check("pec-deck","55"),
            CatalogIssueType.LOAD_UNVERIFIED))
        assertFalse(rope.mayIssueWorkout)
        assertFalse(rope.mayOverwriteAuthoredProgram)
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

    @Test fun newInclineBarbellBenchComposesARealThreeDayWeek() {
        val selections=listOf(
            choice("inc-bb-bench",n("135")),
            choice("one_arm_db_row",n("30")),
            choice("back_squat",n("135")))
        val reviewed=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selections,gym()))
        assertEquals(CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
            reviewed.status)
        val draft=checkNotNull(reviewed.draft)
        assertTrue(draft.audit.satisfiesDeclaredHardConstraints)
        assertTrue(draft.visits.any{it.exerciseId=="inc-bb-bench"})
        assertTrue(draft.visits.none{it.exerciseId=="barbell_bench"})
        assertFalse(reviewed.mayIssueWorkout)
        // Review-only composition cannot rewrite athlete-entered working loads.
        assertEquals(n("135"),selections.first().selectedLoad)
    }

    @Test fun realMachinePressAndLegPressCanComposeWithoutInventedBarbells() {
        val gymStock=mapOf(
            "chest-press-machine" to VerifiedLoadStock.Totals(
                LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                listOf(n("40"),n("50"),n("60"))),
            "leg-press-machine" to VerifiedLoadStock.Totals(
                LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                listOf(n("90"),n("100"),n("110"))),
            "dumbbell-rack" to db)
        val selections=listOf(
            choice("machine_press",n("50")),
            choice("one_arm_db_row",n("30")),
            choice("leg_press",n("100")))
        val reviewed=CoachExerciseCatalogDomain.review(
            StarterExerciseTranche.forGym(template(),selections,
                gym(facilities=setOf("flat-bench"),stock=gymStock)))
        assertEquals(CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW,
            reviewed.status)
        val draft=checkNotNull(reviewed.draft)
        assertTrue(draft.audit.satisfiesDeclaredHardConstraints)
        assertTrue(draft.visits.any{it.exerciseId=="machine_press"})
        assertTrue(draft.visits.any{it.exerciseId=="leg_press"})
        assertTrue(draft.visits.none{it.exerciseId=="back_squat"})
        assertTrue(reviewed.issues.isEmpty())
        assertFalse(reviewed.mayIssueWorkout)
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
    @Test fun curatedAccessoryIdentitiesRequireExactImplementFacilityAndAttachment() {
        val newIds=listOf("cable_fly","chest_supported_row",
            "neutral_pulldown","rear_delt_fly","machine_ohp",
            "preacher_curl","cable_curl","cable_triceps",
            "overhead_cable_triceps","standing_calf","cable_crunch")
        // This historical eleven-ID tranche is a stable prefix, not the
        // entire tail once further exact web identities are appended.
        val entries=data.drop(31).take(11)
        assertEquals(newIds,entries.map{it.legacyCuratedId})
        assertTrue(entries.none{it.mayTransferPreviousLoad})
        val byId=entries.associateBy{it.legacyCuratedId}
        val machineIds=listOf("rear_delt_fly","machine_ohp",
            "preacher_curl","standing_calf")
        assertTrue(machineIds.all{
            byId.getValue(it).movement.expectedLoadBasis==
                LoadBasis.EXERCISE_OVERRIDE
        })
        assertEquals(4,machineIds.map{
            byId.getValue(it).movement.equipmentId
        }.distinct().size)
        assertEquals(LoadBasis.DUMBBELL_PER_HAND,
            byId.getValue("chest_supported_row").movement.expectedLoadBasis)
        assertEquals(setOf("incline-bench"),
            byId.getValue("chest_supported_row").movement.requiredFacilityIds)
        val cables=newIds.filter{it in setOf("cable_fly","neutral_pulldown",
            "cable_curl","cable_triceps","overhead_cable_triceps","cable_crunch")}
        assertEquals(6,cables.size)
        assertTrue(cables.all {
            byId.getValue(it).movement.expectedLoadBasis==LoadBasis.CABLE_STACK &&
                byId.getValue(it).movement.equipmentId=="cable-station" &&
                byId.getValue(it).movement.attachmentId!=null
        })
        assertEquals("dual-handles",
            byId.getValue("cable_fly").movement.attachmentId)
        assertEquals("neutral-grip-bar",
            byId.getValue("neutral_pulldown").movement.attachmentId)
        assertEquals("straight-bar",
            byId.getValue("cable_curl").movement.attachmentId)
        assertEquals("straight-bar",
            byId.getValue("cable_triceps").movement.attachmentId)
        assertEquals("rope",
            byId.getValue("overhead_cable_triceps").movement.attachmentId)
        assertEquals("rope",
            byId.getValue("cable_crunch").movement.attachmentId)
        assertEquals(listOf(TrainingMuscle.ABS),
            byId.getValue("cable_crunch").movement.credits.map{it.muscle})
        assertEquals(listOf(TrainingMuscle.BICEPS),
            byId.getValue("preacher_curl").movement.credits.map{it.muscle})
        assertEquals(listOf(TrainingMuscle.REAR_DELTS),
            byId.getValue("rear_delt_fly").movement.credits.map{it.muscle})
        assertEquals(TrainingMuscle.UPPER_BACK,
            byId.getValue("chest_supported_row").movement.credits
                .single{it.role==MuscleCreditRole.DIRECT}.muscle)
        val machine=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.EXERCISE_OVERRIDE,listOf(n("40"),n("50"),n("60")))
        val cable=VerifiedLoadStock.Totals(LoadUnit.LB,
            LoadBasis.CABLE_STACK,listOf(n("15"),n("20"),n("25")))
        val stocks=mapOf("cable-station" to cable,
            "reverse-pec-deck-machine" to machine,
            "shoulder-press-machine" to machine,
            "preacher-curl-machine" to machine,
            "standing-calf-machine" to machine,
            "dumbbell-rack" to db)
        fun reviewed(id:String,weight:String="20",
            stock:Map<String,VerifiedLoadStock> =stocks,
            attached:Set<String> =emptySet(),
            facilities:Set<String> =setOf("incline-bench"))=
            CoachExerciseCatalogDomain.review(StarterExerciseTranche.forGym(
                template(),listOf(choice(id,n(weight))),
                CatalogGym(stock,attached,facilities)))
        fun issue(id:String,kind:CatalogIssueType,
            result:CatalogWeekReview)=result.issues.any {
                it.exerciseId==id && it.kind==kind
            }
        assertTrue(issue("cable_fly",CatalogIssueType.ATTACHMENT_UNAVAILABLE,
            reviewed("cable_fly")))
        assertFalse(issue("cable_fly",CatalogIssueType.ATTACHMENT_UNAVAILABLE,
            reviewed("cable_fly",attached=setOf("dual-handles"))))
        assertTrue(issue("neutral_pulldown",
            CatalogIssueType.ATTACHMENT_UNAVAILABLE,
            reviewed("neutral_pulldown",attached=setOf("rope"))))
        assertTrue(issue("cable_curl",CatalogIssueType.ATTACHMENT_UNAVAILABLE,
            reviewed("cable_curl",attached=setOf("rope"))))
        assertTrue(issue("chest_supported_row",CatalogIssueType.FACILITY_UNAVAILABLE,
            reviewed("chest_supported_row","30",facilities=emptySet())))
        assertTrue(issue("preacher_curl",CatalogIssueType.EQUIPMENT_UNAVAILABLE,
            reviewed("preacher_curl","50",
                stock=stocks-"preacher-curl-machine")))
        assertTrue(issue("standing_calf",CatalogIssueType.LOAD_UNVERIFIED,
            reviewed("standing_calf","55")))
        assertTrue(issue("machine_ohp",CatalogIssueType.EQUIPMENT_BASIS_MISMATCH,
            reviewed("machine_ohp","20",stock=stocks+
                ("shoulder-press-machine" to cable))))
        val crunch=reviewed("cable_crunch",attached=setOf("rope"))
        assertFalse(issue("cable_crunch",CatalogIssueType.LOAD_UNVERIFIED,crunch))
        assertFalse(issue("cable_crunch",CatalogIssueType.ATTACHMENT_UNAVAILABLE,crunch))
        assertFalse(crunch.mayIssueWorkout)
        assertFalse(crunch.mayOverwriteAuthoredProgram)
    }

}
