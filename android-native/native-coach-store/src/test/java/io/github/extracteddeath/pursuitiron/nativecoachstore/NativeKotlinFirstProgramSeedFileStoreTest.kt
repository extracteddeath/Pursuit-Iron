package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinFirstProgramSeedFileStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private var signedIn:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private fun store()=NativeKotlinFirstProgramSeedFileStore(
        temp.root.toPath(),
        NativeKotlinApprovalRevisionFence(temp.root.toPath()),
        NativeKotlinCurrentIdentity{signedIn})
    private fun seed()=NativeFirstProgramSeed(
        cycleId="my-first",title="My first program",
        blockWeeks=4,sourceRevision=12,
        policyVersion=CoachFirstProgramStartDomain.POLICY_VERSION,
        phase=TrainingPhase.HYPERTROPHY,
        startingLoads=mapOf("db_bench" to BigDecimal("25.0"),
            "db_row" to BigDecimal("30")),
        bodyweightExercises=setOf("pullup"),
        extraFocuses=listOf(ComposerFocus.UPPER),
        nonSetSeconds=900)

    @Test fun coldRecoverExactCalibrationWithoutWorkoutAuthority() {
        val source=seed()
        assertEquals(NativeFirstProgramSeedRead.Missing,
            store().read("owner-1","my-first"))
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            store().create("owner-1",source))
        val restored=(store().read("owner-1","my-first")
            as NativeFirstProgramSeedRead.Found).seed
        assertEquals(source.copy(
            startingLoads=mapOf("db_bench" to BigDecimal("25"),
                "db_row" to BigDecimal("30"))),restored)
        assertEquals(NativeFirstProgramSeedWrite.Rejected(
            NativeFirstProgramSeedRefusal.ALREADY_EXISTS),
            store().create("owner-1",source.copy(nonSetSeconds=1200)))
        assertEquals(restored,
            (store().read("owner-1","my-first")
                as NativeFirstProgramSeedRead.Found).seed)
    }

    @Test fun ownerAndCalibratedIdentityCannotBeSwapped() {
        val source=seed()
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            store().create("owner-1",source))
        signedIn=NativeKotlinOwnerIdentity("owner-2","athlete-2")
        assertEquals(NativeFirstProgramSeedRead.Missing,
            store().read("owner-2",source.cycleId))
        assertEquals(NativeFirstProgramSeedRead.Rejected(
            NativeFirstProgramSeedRefusal.NOT_AUTHORIZED),
            store().read("owner-1",source.cycleId))
        signedIn=null
        assertEquals(NativeFirstProgramSeedWrite.Rejected(
            NativeFirstProgramSeedRefusal.NOT_AUTHORIZED),
            store().create("owner-1",seed().copy(cycleId="another")))
    }

    @Test fun tamperOrUnknownSeedVersionFailsClosed() {
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            store().create("owner-1",seed()))
        val file=Files.list(temp.root.toPath()).use{stream->
            stream.filter{
                it.fileName.toString().startsWith("first-program-") &&
                    it.fileName.toString().endsWith(".bin")
            }.findFirst().orElseThrow()
        }
        val bytes=Files.readAllBytes(file)
        bytes[bytes.size-6]=(bytes[bytes.size-6].toInt() xor 0x7F).toByte()
        Files.write(file,bytes)
        assertEquals(NativeFirstProgramSeedRead.Rejected(
            NativeFirstProgramSeedRefusal.CORRUPT),
            store().read("owner-1","my-first"))
    }

    @Test fun invalidAndContradictoryCalibrationsCannotBeStored() {
        val inputs=listOf(
            seed().copy(startingLoads=mapOf("x" to BigDecimal("-25"))),
            seed().copy(startingLoads=mapOf("x" to BigDecimal("25.1234"))),
            seed().copy(startingLoads=mapOf("pullup" to BigDecimal("25"))),
            seed().copy(nonSetSeconds=-1),
            seed().copy(policyVersion="not valid with spaces"),
            seed().copy(extraFocuses=List(3){ComposerFocus.UPPER})
        )
        for(input in inputs) {
            assertEquals(NativeFirstProgramSeedWrite.Rejected(
                NativeFirstProgramSeedRefusal.INVALID_SEED),
                store().create("owner-1",input))
        }
    }
    @Test fun optInUsesDistinctVersionAndExactColdRecoveredTechniqueConsent() {
        val legacy=seed().copy(cycleId="legacy")
        val enabled=seed().copy(cycleId="opted-in",includeMyoFinisher=true)
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            store().create("owner-1",legacy))
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            store().create("owner-1",enabled))
        val recovered=(store().read("owner-1","opted-in")
            as NativeFirstProgramSeedRead.Found).seed
        assertTrue(recovered.includeMyoFinisher)
        assertEquals(enabled.copy(startingLoads=mapOf(
            "db_bench" to BigDecimal("25"),"db_row" to BigDecimal("30"))),
            recovered)
        assertFalse((store().read("owner-1","legacy")
            as NativeFirstProgramSeedRead.Found).seed.includeMyoFinisher)
        val wireVersions=Files.list(temp.root.toPath()).use{stream->
            stream.filter{
                it.fileName.toString().startsWith("first-program-") &&
                    it.fileName.toString().endsWith(".bin")
            }.map { path ->
                val bytes=Files.readAllBytes(path)
                java.io.DataInputStream(java.io.ByteArrayInputStream(bytes)).use { input ->
                    input.readInt() // authenticated payload length
                    assertEquals(0x50494653,input.readInt())
                    input.readInt()
                }
            }.toList()
        }
        assertEquals(setOf(1,2),wireVersions.toSet())
    }

    @Test fun newFinisherSeedsColdReopenWithoutRewritingLegacyVersions() {
        for(kind in listOf(CustomAmplifierKind.DROP_SET,CustomAmplifierKind.LENGTHENED_PARTIALS)) {
            val source=seed().copy(cycleId=kind.name.lowercase(),optionalFinisher=kind)
            assertEquals(NativeFirstProgramSeedWrite.Committed,store().create("owner-1",source))
            val recovered=(store().read("owner-1",source.cycleId) as NativeFirstProgramSeedRead.Found).seed
            assertEquals(source.copy(startingLoads=mapOf("db_bench" to BigDecimal("25"),"db_row" to BigDecimal("30"))),recovered)
            assertFalse(recovered.includeMyoFinisher)
            assertEquals(NativeFirstProgramSeedWrite.Rejected(NativeFirstProgramSeedRefusal.INVALID_SEED),
                store().create("owner-1",source.copy(includeMyoFinisher=true)))
        }
        assertEquals(NativeFirstProgramSeedWrite.Rejected(NativeFirstProgramSeedRefusal.INVALID_SEED),
            store().create("owner-1",seed().copy(optionalFinisher=CustomAmplifierKind.CLUSTER)))
        assertEquals(setOf(CustomAmplifierKind.DROP_SET,CustomAmplifierKind.LENGTHENED_PARTIALS),
            (store().inventoryOwner("owner-1") as NativeFirstProgramSeedInventory.Verified).seeds.mapNotNull{it.optionalFinisher}.toSet())
    }

    @Test fun changingOnlyOptInFlagDoesNotOverwritePriorSeed() {
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            store().create("owner-1",seed()))
        assertEquals(NativeFirstProgramSeedWrite.Rejected(
            NativeFirstProgramSeedRefusal.ALREADY_EXISTS),
            store().create("owner-1",seed().copy(includeMyoFinisher=true)))
        assertFalse((store().read("owner-1","my-first")
            as NativeFirstProgramSeedRead.Found).seed.includeMyoFinisher)
    }

    private fun physicalSeeds():List<java.nio.file.Path> =
        Files.list(temp.root.toPath()).use { stream ->
            stream.filter{it.fileName.toString().startsWith("first-program-") &&
                it.fileName.toString().endsWith(".bin")}.toList()
        }

    @Test fun stagedInventoryRetainsLegacyAndOptInSeedsIncludingValidOrphans() {
        val empty=store().inventoryOwner("owner-1")
        assertEquals(NativeFirstProgramSeedInventory.Verified(emptyList()),empty)
        val old=seed().copy(cycleId="old-program")
        val opt=seed().copy(cycleId="opt-program",
            includeMyoFinisher=true)
        assertEquals(NativeFirstProgramSeedWrite.Committed,store().create("owner-1",old))
        assertEquals(NativeFirstProgramSeedWrite.Committed,store().create("owner-1",opt))
        val reread=store().inventoryOwner("owner-1")
            as NativeFirstProgramSeedInventory.Verified
        assertEquals(listOf("old-program","opt-program"),
            reread.seeds.map{it.cycleId})
        assertEquals(listOf(false,true),
            reread.seeds.map{it.includeMyoFinisher})
        // A seed is intentionally created before its cycle draft. No
        // approved workout or even a saved draft is required to retain it.
        assertEquals(2,reread.seeds.size)
        assertEquals(2,physicalSeeds().size)
    }

    @Test fun physicalSeedTamperOrRenameRejectsEntireStagedInventory() {
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            store().create("owner-1",seed().copy(includeMyoFinisher=true)))
        val file=physicalSeeds().single()
        val bytes=Files.readAllBytes(file)
        val damaged=bytes.clone()
        damaged[damaged.lastIndex]=(damaged.last().toInt() xor 1).toByte()
        Files.write(file,damaged)
        assertEquals(NativeFirstProgramSeedInventory.Rejected(
            NativeFirstProgramSeedRefusal.CORRUPT),store().inventoryOwner("owner-1"))
        Files.write(file,bytes)
        val wrong=file.resolveSibling("first-program-"+("f".repeat(64))+".bin")
        Files.move(file,wrong)
        assertEquals(NativeFirstProgramSeedInventory.Rejected(
            NativeFirstProgramSeedRefusal.CORRUPT),store().inventoryOwner("owner-1"))
    }

    @Test fun foreignOwnerPendingWriteAndUnsafeFileCannotBeDroppedSilently() {
        assertEquals(NativeFirstProgramSeedWrite.Committed,
            store().create("owner-1",seed()))
        signedIn=NativeKotlinOwnerIdentity("owner-2","athlete-2")
        assertEquals(NativeFirstProgramSeedInventory.Rejected(
            NativeFirstProgramSeedRefusal.CORRUPT),store().inventoryOwner("owner-2"))
        signedIn=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        val pending=temp.root.toPath().resolve("first-program-unsafe.pending")
        Files.write(pending,byteArrayOf(1))
        assertEquals(NativeFirstProgramSeedInventory.Rejected(
            NativeFirstProgramSeedRefusal.CORRUPT),store().inventoryOwner("owner-1"))
        Files.delete(pending)
        assertEquals(1,(store().inventoryOwner("owner-1")
            as NativeFirstProgramSeedInventory.Verified).seeds.size)
    }

}
