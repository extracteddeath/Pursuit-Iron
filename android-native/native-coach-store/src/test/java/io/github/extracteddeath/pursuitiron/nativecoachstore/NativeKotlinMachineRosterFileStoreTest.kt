package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
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
}
