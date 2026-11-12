package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinMachineRosterFileStoreTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun store()=NativeKotlinMachineRosterFileStore(temporary.root.toPath())
    private fun saved(v:NativeMachineRosterResult)=
        (v as NativeMachineRosterResult.Saved).snapshot

    @Test fun defaultsAreDurableGymSpecificAndCasProtected() {
        val empty=saved(store().read("athlete1"))
        assertEquals(0,empty.revision)
        val a=KotlinGymMachineAlternatives("gymA","machine_press","A",
            listOf(KotlinPhysicalMachine("A","Machine A",increment=BigDecimal("5")),
                KotlinPhysicalMachine("B","Machine B",increment=BigDecimal("10"))))
        val b=KotlinGymMachineAlternatives("gymB","machine_press","B",
            listOf(KotlinPhysicalMachine("B","Machine B",increment=BigDecimal("10"))))
        val first=saved(store().save("athlete1",0,listOf(a,b)))
        assertEquals(1,first.revision)
        assertEquals(first,saved(store().read("athlete1")))
        assertEquals(NativeMachineRosterResult.Rejected("STALE_REVISION"),
            store().save("athlete1",0,listOf(a)))
        val switched=a.copy(defaultMachineId="B")
        val second=saved(store().save("athlete1",1,listOf(switched,b)))
        assertEquals("B",saved(store().read("athlete1"))
            .configurations.first {it.gymId=="gymA"}.defaultMachineId)
        assertEquals(second,saved(store().read("athlete1")))
        assertEquals(0,saved(store().read("otherAthlete")).configurations.size)
    }

    @Test fun stackCalibrationSurvivesV2ColdReplayWithoutInventingBStock() {
        val configured=KotlinGymMachineAlternatives("gymA","machine_press","A",
            listOf(
                KotlinPhysicalMachine("A","Press A",unit=LoadUnit.LB,
                    increment=BigDecimal("5"),minimum=BigDecimal("10"),
                    maximum=BigDecimal("200")),
                KotlinPhysicalMachine("B","Press B",unit=LoadUnit.LB,
                    increment=BigDecimal("10"))))
        val saved=saved(store().save("athlete1",0,listOf(configured)))
        assertEquals(1,saved.revision)
        val cold=saved(store().read("athlete1")).configurations.single()
        assertEquals(configured,cold)
        assertNotNull(KotlinMachineAlternativesDomain.confirmedStack(
            cold,"A",LoadBasis.MACHINE_STACK))
        assertNull(KotlinMachineAlternativesDomain.confirmedStack(
            cold,"B",LoadBasis.MACHINE_STACK))
        val calibrated=KotlinMachineAlternativesDomain.edit(cold,
            cold.machines.last().copy(minimum=BigDecimal("20"),
                maximum=BigDecimal("220")))
        assertEquals(2,saved(store().save("athlete1",1,listOf(calibrated))).revision)
        val reopened=saved(store().read("athlete1")).configurations.single()
        assertEquals(BigDecimal("10"),
            KotlinMachineAlternativesDomain.confirmedStack(
                reopened,"B",LoadBasis.MACHINE_STACK)?.increment)
        assertEquals(BigDecimal("220"),
            KotlinMachineAlternativesDomain.confirmedStack(
                reopened,"B",LoadBasis.MACHINE_STACK)?.maximum)
    }

    @Test fun olderOffGridStackDoesNotDestroyRosterOrBecomeVerifiedStock() {
        // Simulates a previously accepted v2 calibration. Its written file
        // remains parseable even after stricter *new* calibration rules.
        val old=KotlinGymMachineAlternatives("gymA","machine_press","B",
            listOf(KotlinPhysicalMachine("B","Historical press",
                unit=LoadUnit.LB,increment=BigDecimal("10"),
                minimum=BigDecimal("20"),maximum=BigDecimal("215"))))
        saved(store().save("athlete1",0,listOf(old)))
        val restored=saved(store().read("athlete1"))
        val machine=restored.configurations.single()
        assertEquals("B",machine.defaultMachineId)
        assertEquals("Historical press",machine.machines.single().label)
        assertNull(KotlinMachineAlternativesDomain.confirmedStack(
            machine,"B",LoadBasis.MACHINE_STACK))
        val renamed=KotlinMachineAlternativesDomain.edit(machine,
            machine.machines.single().copy(label="Former upstairs press"))
        saved(store().save("athlete1",restored.revision,listOf(renamed)))
        assertEquals("Former upstairs press",saved(store().read("athlete1"))
            .configurations.single().machines.single().label)
    }

    @Test fun deactivatedMachineRemainsReadableAndDuplicateGymExerciseRejected() {
        val a=KotlinGymMachineAlternatives("gymA","machine_press","B",listOf(
            KotlinPhysicalMachine("A","Machine A",active=false),
            KotlinPhysicalMachine("B","Machine B")))
        saved(store().save("athlete1",0,listOf(a)))
        val recovered=saved(store().read("athlete1")).configurations.single()
        assertEquals("A",
            KotlinMachineAlternativesDomain.resolve(recovered,"A",true).id)
        assertEquals(NativeMachineRosterResult.Rejected("INVALID_ROSTER"),
            store().save("athlete1",1,listOf(a,a)))
    }
    @Test fun independentStoreInstancesCannotBothCommitOneMachineRosterRevision() {
        // A and B share the same exercise but have different physical load
        // increments. Neither athlete edit may erase the other's history or
        // silently win a stale revision after parallel UI/service writes.
        val original=KotlinGymMachineAlternatives("gymA","machine_press","A",
            listOf(
                KotlinPhysicalMachine("A","Press A",unit=LoadUnit.LB,
                    increment=BigDecimal("5"),minimum=BigDecimal("10"),
                    maximum=BigDecimal("210")),
                KotlinPhysicalMachine("B","Press B",unit=LoadUnit.LB,
                    increment=BigDecimal("10"),minimum=BigDecimal("20"),
                    maximum=BigDecimal("220"))
            ))
        var latest=saved(store().save("athlete1",0,listOf(original)))
        val workers=Executors.newFixedThreadPool(2)
        try {
            repeat(24) {round ->
                val expected=latest.revision
                val current=latest.configurations.single()
                val a=current.copy(defaultMachineId="A")
                val b=current.copy(defaultMachineId="B")
                val gate=CountDownLatch(1)
                val first=workers.submit<NativeMachineRosterResult> {
                    gate.await()
                    // Not the same JVM instance, as happens on cold reopen.
                    store().save("athlete1",expected,listOf(a))
                }
                val second=workers.submit<NativeMachineRosterResult> {
                    gate.await()
                    store().save("athlete1",expected,listOf(b))
                }
                gate.countDown()
                val decisions=listOf(
                    first.get(20,TimeUnit.SECONDS),
                    second.get(20,TimeUnit.SECONDS))
                assertEquals("round $round: $decisions",1,
                    decisions.count{it is NativeMachineRosterResult.Saved})
                assertEquals("round $round: $decisions",1,
                    decisions.count{it==NativeMachineRosterResult.Rejected(
                        "STALE_REVISION")})
                latest=saved(store().read("athlete1"))
                assertEquals(expected+1,latest.revision)
                assertEquals(2,latest.configurations.single().machines.size)
                assertEquals(original.machines,
                    latest.configurations.single().machines)
                assertTrue(latest.configurations.single().defaultMachineId
                    in setOf("A","B"))
                assertNotNull(KotlinMachineAlternativesDomain.confirmedStack(
                    latest.configurations.single(),"A",LoadBasis.MACHINE_STACK))
                assertNotNull(KotlinMachineAlternativesDomain.confirmedStack(
                    latest.configurations.single(),"B",LoadBasis.MACHINE_STACK))
            }
        } finally {
            workers.shutdownNow()
        }
        // A different signed-in owner never acquires an older author's roster.
        assertEquals(0,saved(store().read("athlete2")).revision)
        assertEquals(latest,saved(store().read("athlete1")))
    }

    @Test fun corruptedMachineRosterNeverResetsToAnEmptyEditableBaseline() {
        val configured=KotlinGymMachineAlternatives("gymA","machine_press","A",
            listOf(KotlinPhysicalMachine("A","Press A")))
        assertEquals(1,saved(store().save("athlete1",0,listOf(configured))).revision)
        val roster=java.nio.file.Files.list(temporary.root.toPath()).use {files ->
            files.filter {it.fileName.toString().startsWith("machine-roster-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=java.nio.file.Files.readAllBytes(roster)
        bytes[bytes.size-2]=(bytes[bytes.size-2].toInt() xor 0x40).toByte()
        java.nio.file.Files.write(roster,bytes)
        assertEquals(NativeMachineRosterResult.Rejected("UNREADABLE_OR_INVALID"),
            store().read("athlete1"))
        assertEquals(NativeMachineRosterResult.Rejected("UNREADABLE_OR_INVALID"),
            store().save("athlete1",0,listOf(configured)))
        assertTrue(java.nio.file.Files.exists(roster))
    }

}
