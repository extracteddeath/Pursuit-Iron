package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Recovery retains archived prospective context bytes without making them
 * an approval or assuming their superseded cycle/draft still exists.
 */
class NativeKotlinFutureBlockContextInventoryTest {
    @get:Rule val temp=TemporaryFolder()
    private val root get()=temp.root.toPath()
    private fun n(value:String)=BigDecimal(value)
    private var signed=NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private fun store()=NativeKotlinFutureBlockContextFileStore(
        root,NativeKotlinApprovalRevisionFence(root),
        NativeKotlinCurrentIdentity{signed},
        NativeCustomCycleDraftFileStore(root,NativeKotlinApprovalRevisionFence(root),
            NativeKotlinCurrentIdentity{signed}),
        NativeKotlinCustomAuthoringSourceFileStore(
            root,NativeKotlinApprovalRevisionFence(root),
            NativeKotlinCurrentIdentity{signed}))

    private fun setup()=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision="profile-1",athleteProfileId="athlete-1",
        goal=AthleteTrainingGoal.HYPERTROPHY,
        experience=AthleteExperience.ADVANCED,
        split=ComposerSplit.UPPER_LOWER,
        trainingWeekdays=setOf(0,1,3,5),timezoneId="America/New_York",
        sessionTimeBucket=SessionTimeBucket.SIXTY_TO_NINETY,
        hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
        gym=GymProfileDraft("gym-1",LoadUnit.LB,emptyMap(),
            emptySet(),emptySet()),
        allowBodyweight=true,allowSupersets=false,
        allowTricepsOnLower=true,maxBarbellExercisesPerDay=0,
        focusedMuscles=emptySet(),reducedMuscles=emptySet(),
        excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
        hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
        declaredRestDays=setOf(2,4,6)))
    private fun policy():AuthoredGoalProgramPolicy {
        val bounds=TrainingMuscleCoverage.current.map {muscle->
            DeclaredMuscleDoseBounds(muscle,n("0"),n("8"),n("16"),0)
        }
        return AuthoredGoalProgramPolicy(
            "future-policy","future-rev","Athlete future block review",
            "athlete-1","profile-1",
            AthleteTrainingGoal.HYPERTROPHY,AthleteExperience.ADVANCED,
            ComposerSplit.UPPER_LOWER,TrainingPhase.HYPERTROPHY,4,
            bounds,bounds,emptyMap(),emptyMap())
    }
    private fun file(owner:String="owner-1",cycle:String="retired-cycle",
        block:String="future-block"):java.nio.file.Path {
        val bytes=(owner+"\u0000"+cycle+"\u0000"+block)
            .toByteArray(Charsets.UTF_8)
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return root.resolve("future-block-$digest.bin")
    }
    private fun archive(owner:String="owner-1",cycle:String="retired-cycle",
        block:String="future-block"):ByteArray {
        val setup=setup()
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup.status)
        val source=NativeCustomAuthoringSnapshot(
            "owner-1","athlete-1",1,setup,emptyList())
        val future=ProspectiveBlockCoachingContext(setup,policy(),
            mapOf(0 to 900,1 to 900,3 to 900,5 to 900))
        val receipt=NativeFutureBlockContextReceipt(
            owner,"athlete-1",cycle,block,3,1,
            "a".repeat(64),future)
        return NativeFutureBlockContextCodec.encode(
            NativeFutureBlockRecord(receipt,"b".repeat(64),"c".repeat(64)),
            source)
    }

    @Test fun archivedContextScansWithoutCurrentCycleOrActivation() {
        assertEquals(NativeFutureContextInventory.Verified(emptyList()),
            store().inventoryOwner("owner-1"))
        val path=file()
        Files.write(path,archive())
        val before=Files.readAllBytes(path)
        val scanned=store().inventoryOwner("owner-1")
        assertEquals(NativeFutureContextInventory.Verified(listOf(
            NativeFutureContextInventoryFact(
                "retired-cycle","future-block",3,1,"a".repeat(64)))),
            scanned)
        assertArrayEquals(before,Files.readAllBytes(path))
        // A context census does not validate a now-absent authored draft,
        // signal a future-block activation or fabricate a saved signoff.
        assertTrue(scanned is NativeFutureContextInventory.Verified)
        assertEquals(NativeFutureBlockContextRead.Rejected(
            NativeFutureBlockContextRefusal.STALE_DRAFT_OR_SOURCE),
            store().read("owner-1","retired-cycle","future-block"))
    }

    @Test fun damagedRenamedAndInterruptedRecordsBlockRestoreInventory() {
        val path=file()
        Files.write(path,archive())
        val original=Files.readAllBytes(path)
        val broken=original.copyOf().also{
            it[it.lastIndex]=(it.last().toInt() xor 1).toByte()
        }
        Files.write(path,broken)
        assertEquals(NativeFutureContextInventory.InvalidOrForeign,
            store().inventoryOwner("owner-1"))
        assertArrayEquals(broken,Files.readAllBytes(path))
        Files.write(path,original)
        val wrong=root.resolve("future-block-"+"f".repeat(64)+".bin")
        Files.move(path,wrong)
        assertEquals(NativeFutureContextInventory.InvalidOrForeign,
            store().inventoryOwner("owner-1"))
        Files.move(wrong,path)
        val pending=root.resolve("future-block-"+"d".repeat(64)+".pending")
        Files.write(pending,byteArrayOf(1))
        assertEquals(NativeFutureContextInventory.InvalidOrForeign,
            store().inventoryOwner("owner-1"))
        Files.delete(pending)
        assertEquals(1,(store().inventoryOwner("owner-1")
            as NativeFutureContextInventory.Verified).records.size)
    }

    @Test fun foreignOwnersAndSymlinkedOrUnknownPhysicalContextsFailClosed() {
        val path=file()
        Files.write(path,archive())
        signed=NativeKotlinOwnerIdentity("owner-1","athlete-2")
        assertEquals(NativeFutureContextInventory.InvalidOrForeign,
            store().inventoryOwner("owner-1"))
        signed=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val alias=root.resolve("future-block-"+"e".repeat(64)+".bin")
        Files.createSymbolicLink(alias,path)
        assertEquals(NativeFutureContextInventory.InvalidOrForeign,
            store().inventoryOwner("owner-1"))
        Files.delete(alias)
        val unknown=root.resolve("future-block-"+"9".repeat(64)+".dat")
        Files.write(unknown,byteArrayOf(9))
        assertEquals(NativeFutureContextInventory.InvalidOrForeign,
            store().inventoryOwner("owner-1"))
        Files.delete(unknown)
        assertEquals(1,(store().inventoryOwner("owner-1")
            as NativeFutureContextInventory.Verified).records.size)
    }
}
