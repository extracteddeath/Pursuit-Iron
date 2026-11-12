package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinCustomAuthoringSourceFileStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private fun n(s:String)=BigDecimal(s)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.CABLE_STACK,listOf(n("20"),n("25"),n("30")))
    private fun review():AthleteSetupReview =
        CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.POWERBUILDING,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.UPPER_LOWER,
            trainingWeekdays=setOf(0,1,3,5),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                mapOf("cable-1" to stock),
                setOf("cable-station"),setOf("rope","straight-bar")),
            allowBodyweight=true,allowSupersets=false,
            allowTricepsOnLower=true,maxBarbellExercisesPerDay=1,
            focusedMuscles=setOf(TrainingMuscle.CHEST),
            reducedMuscles=setOf(TrainingMuscle.CALVES),
            excludedExerciseIds=emptySet(),lockedTrainingDays=setOf(0),
            hardTrainingDays=setOf(3),longTrainingDays=setOf(5),
            declaredRestDays=setOf(2,4,6)))
    private val movement=CatalogMovement(
        "rope-pushdown","pushdown","rope","rope",
        ComposerPattern.ISOLATION,ComposerRole.SECONDARY,
        setOf(ComposerFocus.UPPER,ComposerFocus.FULL_BODY),
        "cable-1",LoadBasis.CABLE_STACK,
        listOf(DeclaredMuscleCredit(TrainingMuscle.TRICEPS,
            MuscleCreditRole.DIRECT,BigDecimal.ONE)),
        CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"review-1",
        setOf("cable-station"),RepBand(8,15),
        "elbow-extension",1)
    private fun source(revision:Long=1)=NativeCustomAuthoringSnapshot(
        "owner-1","athlete-1",revision,review(),listOf(movement))
    private var signedIn:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private fun store()=NativeKotlinCustomAuthoringSourceFileStore(
        temp.root.toPath(),NativeKotlinApprovalRevisionFence(temp.root.toPath()),
        NativeKotlinCurrentIdentity {signedIn})

    @Test fun sourceCodecColdRestoresExactReviewedGymCatalogAndAttachment() {
        val expected=source()
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,
            expected.setup.status)
        val encoded=NativeKotlinCustomAuthoringSourceCodec.encode(expected)
        val actual=NativeKotlinCustomAuthoringSourceCodec.decode(encoded)
        assertEquals(expected,actual)
        val picked=CustomExerciseAuthoringDomain.picker(
            actual.setup,actual.catalog)
        assertEquals(1,picked.size)
        assertEquals("rope",picked.single().attachmentId)
        assertEquals(stock,picked.single().availability.stock)
        assertFalse(actual.mayIssueWorkout)
        assertThrows(java.io.IOException::class.java) {
            NativeKotlinCustomAuthoringSourceCodec.decode(encoded+byteArrayOf(7))
        }
    }

    @Test fun webSizedCatalogSurvivesExactCodecAndColdStoreRestart() {
        // One live stock option plus 375 explicitly unavailable source IDs.
        // This proves capacity, not exercise-science or workout eligibility.
        val template=source()
        val all=template.catalog+(1..375).map{ i ->
            movement.copy(exerciseId="unavailable-$i",
                familyId="unavailable-family-$i",variantId="provisional",
                equipmentId="not-stocked",attachmentId=null,
                requiredFacilityIds=emptySet())
        }
        assertEquals(376,all.size)
        val expanded=template.copy(catalog=all)
        val encoded=NativeKotlinCustomAuthoringSourceCodec.encode(expanded)
        assertEquals(expanded,
            NativeKotlinCustomAuthoringSourceCodec.decode(encoded))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            store().publish("owner-1",0,expanded))
        val restored=(store().read("owner-1")
            as NativeAuthoringSourceRead.Found).source
        assertEquals(expanded,restored)
        assertEquals(376,restored.catalog.size)
        assertEquals(listOf("rope-pushdown"),
            CustomExerciseAuthoringDomain.picker(
                restored.setup,restored.catalog).map{it.exerciseId})
        assertFalse(restored.mayIssueWorkout)
        val excess=template.copy(catalog=template.catalog+
            (1..CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES).map {i->
                movement.copy(exerciseId="overflow-$i",
                    familyId="overflow-family-$i",variantId="unavailable")
            })
        assertThrows(IllegalArgumentException::class.java){
            NativeKotlinCustomAuthoringSourceCodec.encode(excess)
        }
    }

    @Test fun saveAndReopenIsOwnerScopedAndRevisionChecked() {
        assertEquals(NativeAuthoringSourceRead.Missing,store().read("owner-1"))
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            store().publish("owner-1",0,source()))
        assertEquals(NativeAuthoringSourceRead.Found(source()),
            store().read("owner-1"))
        val changed=source(2).copy(catalog=source().catalog+
            movement.copy(exerciseId="straight-pushdown",
                variantId="straight",attachmentId="straight-bar"))
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            store().publish("owner-1",1,changed))
        assertEquals(NativeAuthoringSourceRead.Found(changed),
            store().read("owner-1"))
        assertEquals(NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.STALE_REVISION),
            store().publish("owner-1",1,changed))
    }

    @Test fun revokeTombstonesCatalogAndQueuedOldSaveCannotResurrectStock() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            store().publish("owner-1",0,source()))
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            store().revoke("owner-1",1))
        assertEquals(NativeAuthoringSourceRead.Revoked(2),
            store().read("owner-1"))
        assertEquals(NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.STALE_REVISION),
            store().publish("owner-1",1,source(2)))
        assertEquals(NativeAuthoringSourceRead.Revoked(2),
            store().read("owner-1"))
    }

    @Test fun identitySwitchAndCorruptionFailClosed() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            store().publish("owner-1",0,source()))
        signedIn=NativeKotlinOwnerIdentity("owner-1","other-athlete")
        assertEquals(NativeAuthoringSourceRead.Rejected(
            NativeAuthoringSourceRefusal.NOT_AUTHORIZED),
            store().read("owner-1"))
        signedIn=NativeKotlinOwnerIdentity("owner-2","athlete-1")
        assertEquals(NativeAuthoringSourceRead.Rejected(
            NativeAuthoringSourceRefusal.NOT_AUTHORIZED),
            store().read("owner-1"))
        signedIn=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val file=Files.list(temp.root.toPath()).use { stream ->
            stream.filter {
                it.fileName.toString().startsWith("authoring-source-")
            }.toArray().single() as java.nio.file.Path
        }
        val raw=Files.readAllBytes(file)
        raw[raw.size/2]=(raw[raw.size/2].toInt() xor 1).toByte()
        Files.write(file,raw)
        assertEquals(NativeAuthoringSourceRead.Rejected(
            NativeAuthoringSourceRefusal.CORRUPT_RECORD),
            store().read("owner-1"))
        assertEquals(NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.CORRUPT_RECORD),
            store().publish("owner-1",1,source(2)))
        assertArrayEquals(raw,Files.readAllBytes(file))
    }

    @Test fun invalidRevisionAndWrongAthleteNeverPublish() {
        assertEquals(NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.INVALID_SOURCE),
            store().publish("owner-1",0,source(2)))
        assertEquals(NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.NOT_AUTHORIZED),
            store().publish("owner-1",0,source().copy(athleteId="other")))
        val bad=source().copy(catalog=listOf(
            movement,movement.copy(exerciseId="rope-pushdown")))
        assertEquals(NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.INVALID_SOURCE),
            store().publish("owner-1",0,bad))
        assertEquals(NativeAuthoringSourceRead.Missing,store().read("owner-1"))
    }
    @Test fun fakeReviewedProfileCannotBePersistedAsValidGymSnapshot() {
        val valid=source()
        val reviewed=checkNotNull(valid.setup.reviewed)
        val forged=valid.copy(setup=AthleteSetupReview(
            AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,emptyList(),
            reviewed.copy(trainingWeekdays=setOf(0,9))))
        assertEquals(NativeAuthoringSourceWrite.Rejected(
            NativeAuthoringSourceRefusal.INVALID_SOURCE),
            store().publish("owner-1",0,forged))
        assertEquals(NativeAuthoringSourceRead.Missing,store().read("owner-1"))
    }

    @Test fun provisionalStarterCatalogPersistsWithoutAssumingAnyMissingGymWeights() {
        val starter=source().copy(catalog=StarterExerciseTranche.movements())
        // Saved identities are append-only: the first 42 retain their
        // exact ordinal/source IDs while the seven new variants follow.
        assertEquals(146,starter.catalog.size)
        assertEquals(listOf("wrist-curl","reverse-wrist-curl"),
            starter.catalog.drop(20).take(2).map{it.exerciseId})
        assertEquals(listOf("inc-bb-bench","incline_smith",
            "machine_press","leg_press"),
            starter.catalog.drop(22).take(4).map{it.exerciseId})
        assertEquals(listOf("hack_squat","hip_thrust",
            "cable_lateral_raise","lat-pulldown","rope-pushdown"),
            starter.catalog.drop(26).take(5).map{it.exerciseId})
        assertEquals(listOf("cable_fly","chest_supported_row",
            "neutral_pulldown","rear_delt_fly","machine_ohp",
            "preacher_curl","cable_curl","cable_triceps",
            "overhead_cable_triceps","standing_calf","cable_crunch"),
            starter.catalog.drop(31).take(11).map{it.exerciseId})
        assertEquals(listOf("db-bench","db-fly","db-shoulder",
            "inc-curl","hammer","face-pull","pec-deck"),
            starter.catalog.drop(42).take(7).map{it.exerciseId})
        assertEquals(listOf("low-inc-bb-bench","high-inc-bb-bench","smith-bench",
            "front-squat","smith-squat","walking-lunge","bulgarian",
            "lying-curl","seated-calf","bb-curl","ez-curl",
            "bayesian-curl","cgbp","skullcrusher","pushdown","ez-pushdown",
            "oh-cable-ext","machine-row","seated-row","front-raise",
            "arnold","pushup","bw-squat","diamond-pushup","pike-pushup",
            "incline-pushup","decline-pushup"),
            starter.catalog.drop(49).take(27).map{it.exerciseId})
        // The third expansion is append-only; exactly the old 76 IDs keep
        // their saved order and unrelated machine/angle histories.
        assertEquals(listOf(
            "spider-curl","low-incline-db","decline-db-press","db-neutral-press","seated-db-press",
            "squeeze-press","db-incline-fly","rear-fly","seated-rear-fly","prone-db-rear-fly",
            "side-lying-raise","seated-db-lat-raise","chest-supported-lat-raise","helms-row","concentration-curl",
            "incline-hammer","cross-hammer","db-rdl","kroc-row","inc-db-row",
            "cs-db-row","lying-db-ext","pin-squat","ohp","seated-ohp",
            "underhand-row","high-bar-squat","low-bar-squat","paused-squat","tempo-squat",
            "good-morning","yates-row","pendlay-row","floor-press","decline-bench",
            "seated-incline-curl","incline-machine-press","seated-cable-lat-raise","chest-supported-rear-fly","machine-lat-raise",
            "machine-curl","machine-ext","belt-squat","machine-hip-thrust","machine-crunch",
            "nordic","slrdl","single-leg-press","smith-incline","bw-lunge",
            "bw-bulgarian","glute-bridge","sl-hip-thrust","bw-calf","crunch",
            "bicycle","oblique-crunch","leg-raise","bench-dip","standing-curl",
            "inc-cable-fly","high-cable-fly","pendulum-squat","cable-press","v-squat",
            "wide-pulldown","close-pulldown","one-arm-pulldown","straight-pulldown","cable-woodchop"
        ),starter.catalog.drop(76).map{it.exerciseId})
        assertEquals(20,starter.catalog.take(20).size)
        val bytes=NativeKotlinCustomAuthoringSourceCodec.encode(starter)
        val restored=NativeKotlinCustomAuthoringSourceCodec.decode(bytes)
        assertEquals(starter,restored)
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            store().publish("owner-1",0,starter))
        val saved=(store().read("owner-1")
            as NativeAuthoringSourceRead.Found).source
        val eligible=CustomExerciseAuthoringDomain.picker(
            saved.setup,saved.catalog)
        assertEquals(starter.catalog.map{it.exerciseId},
            saved.catalog.map{it.exerciseId})
        // Full catalog survives, but cable-only gym inventory must not
        // pretend dumbbells exist for either new wrist movement.
        assertFalse(eligible.any{it.exerciseId=="wrist-curl" ||
            it.exerciseId=="reverse-wrist-curl"})
        // Persisting a source ID never grants it phantom gym equipment.
        // This fixture has a real but differently identified cable station:
        // do not infer that its stack belongs to another station, a pec
        // deck, or an absent dumbbell rack.
        assertFalse(eligible.any{it.exerciseId in setOf(
            "db-bench","db-fly","db-shoulder","inc-curl","hammer",
            "face-pull","pec-deck","front-squat","smith-squat",
            "machine-row","lying-curl","seated-calf","bb-curl",
            "arnold","bayesian-curl")})
        assertTrue(eligible.any{
            it.exerciseId=="bodyweight_crunch" && it.bodyweight
        })
        assertFalse(eligible.any {it.exerciseId=="barbell_bench"})
        assertFalse(eligible.any {it.exerciseId=="leg_extension"})
        assertEquals(setOf("cable-1"),
            saved.setup.reviewed!!.gym.confirmedStock.keys)
        assertFalse(saved.mayIssueWorkout)
    }

}
