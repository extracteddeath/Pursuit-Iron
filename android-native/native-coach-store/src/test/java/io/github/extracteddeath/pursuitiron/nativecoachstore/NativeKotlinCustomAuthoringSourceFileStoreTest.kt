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
        assertEquals(22,starter.catalog.size)
        assertEquals(listOf("wrist-curl","reverse-wrist-curl"),
            starter.catalog.takeLast(2).map{it.exerciseId})
        assertEquals(20,starter.catalog.dropLast(2).size)
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
