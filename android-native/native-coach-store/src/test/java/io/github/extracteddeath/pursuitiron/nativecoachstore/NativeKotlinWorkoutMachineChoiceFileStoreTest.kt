package io.github.extracteddeath.pursuitiron.nativecoachstore

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinWorkoutMachineChoiceFileStoreTest {
    @get:Rule val temporary=TemporaryFolder()
    private fun store()=NativeKotlinWorkoutMachineChoiceFileStore(temporary.root.toPath())
    private fun saved(value:NativeWorkoutMachineChoiceResult)=
        (value as NativeWorkoutMachineChoiceResult.Saved).snapshot

    @Test fun machineBChoiceSurvivesActualColdRestartAndNewGymDefaults() {
        val initial=saved(store().read("owner","workout-1",false,"gymX"))
        assertEquals(0L,initial.revision)
        val pinned=saved(store().save("owner","workout-1",false,"gymX",0,
            mapOf("press" to "A")))
        assertEquals(1L,pinned.revision)
        assertEquals("A",saved(store().read("owner","workout-1",false,"gymX"))
            .machines["press"])
        val changed=saved(store().save("owner","workout-1",false,"gymX",1,
            mapOf("press" to "B")))
        assertEquals(2L,changed.revision)
        // A new store object simulates a fresh process with no Compose state.
        val reopened=saved(store().read("owner","workout-1",false,"gymX"))
        assertEquals("B",reopened.machines["press"])
        assertEquals(changed,reopened)
        assertEquals(NativeWorkoutMachineChoiceResult.Rejected("STALE_REVISION"),
            store().save("owner","workout-1",false,"gymX",1,mapOf("press" to "A")))
        assertEquals("B",saved(store().read("owner","workout-1",false,"gymX"))
            .machines["press"])
    }

    @Test fun athleteWorkoutAndJournalKindAreIndependentlyScoped() {
        saved(store().save("owner","workout-1",false,"gymX",0,mapOf("press" to "A")))
        assertTrue(saved(store().read("otherOwner","workout-1",false,"gymX"))
            .machines.isEmpty())
        assertTrue(saved(store().read("owner","workout-2",false,"gymX"))
            .machines.isEmpty())
        assertTrue(saved(store().read("owner","workout-1",true,"gymX"))
            .machines.isEmpty())
        assertEquals(NativeWorkoutMachineChoiceResult.Rejected("WORKOUT_SCOPE_CHANGED"),
            store().read("owner","workout-1",false,"gymY"))
    }

    @Test fun tamperedStorageAndInvalidInstanceIdsFailClosed() {
        assertEquals(NativeWorkoutMachineChoiceResult.Rejected("INVALID_SELECTION"),
            store().save("owner","workout-1",false,"gymX",0,
                mapOf("press" to "not valid / machine")))
        saved(store().save("owner","workout-1",false,"gymX",0,
            mapOf("press" to "A")))
        java.nio.file.Files.list(temporary.root.toPath()).use{files->
            val path=files.findFirst().orElseThrow()
            val bytes=java.nio.file.Files.readAllBytes(path)
            bytes[bytes.lastIndex]=(bytes.last().toInt() xor 0x01).toByte()
            java.nio.file.Files.write(path,bytes)
        }
        assertEquals(NativeWorkoutMachineChoiceResult.Rejected("UNREADABLE_OR_INVALID"),
            store().read("owner","workout-1",false,"gymX"))
    }

    @Test fun emptySelectionIsDurableAndDoesNotInventMachineForLegacyWorkout() {
        assertEquals(1L,saved(store().save("owner","legacy",true,"gymX",0,
            emptyMap())).revision)
        assertEquals(emptyMap<String,String>(),
            saved(store().read("owner","legacy",true,"gymX")).machines)
    }
}
