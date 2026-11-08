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
}
