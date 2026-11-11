package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinCustomMachineBindingPolicyTest {
    private fun n(value:String)=BigDecimal(value)
    private val movement=StarterExerciseTranche.movements()
        .single{it.exerciseId=="leg_extension"}
    private fun load(weight:String)=DraftSetLoad.Verified(
        LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,n(weight))
    private fun authored(weight:String="55")=CustomAuthoredSet(
        DraftSetType.WORKING,load(weight),RepBand(8,12),2,55,90)
    private fun cycle(machine:String?=null)=CustomTrainingCycle(
        "cycle-1",2,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "Machine-specific plan",listOf(CustomTrainingBlock(
            "build","Build",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,4,
            CustomSplitIdentity.Freeform("Lower"),
            listOf(CustomTrainingDay(0,"Lower",ComposerSessionPurpose.HYPERTROPHY,
                listOf(CustomExerciseSlot("extension-1","leg_extension",
                    ComposerRole.ACCESSORY,listOf(authored()),
                    machineInstanceId=machine)))))))
    private fun snapshot(machine:String?=null,weight:String="55")=
        CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","rev-1",
                "cycle-1",2,"gym-1",1),
            1,1,"build",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("Lower"),0,"Lower",
            ComposerSessionPurpose.HYPERTROPHY,
            listOf(CustomWorkoutSlotSnapshot("extension-1","leg_extension",
                movement.familyId,movement.variantId,ComposerRole.ACCESSORY,
                null,null,true,"",
                listOf(CustomWorkoutSetSnapshot(
                    "cycle-1:w1:d0:extension-1:s1","extension-1",1,
                    authored(weight))),machine)),
            145,200)
    private fun roster(default:String="B",aActive:Boolean=true)=
        NativeMachineRosterSnapshot("owner-1",1,listOf(
            KotlinGymMachineAlternatives("gym-1","leg_extension",default,
                listOf(KotlinPhysicalMachine("A","Leg extension A",
                    active=aActive,unit=LoadUnit.LB,
                    increment=n("5"),minimum=n("20"),maximum=n("160")),
                    KotlinPhysicalMachine("B","Leg extension B",
                        unit=LoadUnit.LB,increment=n("10"),
                        minimum=n("20"),maximum=n("180"))))))
    private fun review(machine:String?,r:NativeMachineRosterSnapshot?=roster())=
        NativeKotlinCustomMachineBindingPolicy.review(
            "owner-1",snapshot(machine),listOf(movement),r)

    @Test fun separatePhysicalMachineOverridesGymDefaultAndMustHaveExactStack() {
        assertEquals(NativeCustomMachineBindingReview.Verified(
            mapOf("leg_extension" to "A")),review("A"))
        assertEquals(NativeCustomMachineBindingReview.Verified(emptyMap()),
            review(null,roster()))
        assertEquals(NativeCustomMachineBindingReview.Rejected(
            "LOAD_NOT_AVAILABLE_ON_MACHINE"),review("B"))
        assertEquals(NativeCustomMachineBindingReview.Rejected(
            "MACHINE_STOCK_NOT_CONFIRMED"),review("A",roster(aActive=false)))
        assertEquals(NativeCustomMachineBindingReview.Rejected(
            "MACHINE_NOT_IN_GYM"),review("C"))
        assertEquals(NativeCustomMachineBindingReview.Rejected(
            "MACHINE_ROSTER_UNAVAILABLE"),review("A",null))
        assertEquals(NativeCustomMachineBindingReview.Rejected(
            "MACHINE_ROSTER_UNAVAILABLE"),
            review("A",roster().copy(ownerId="other-owner")))
    }

    @Test fun machineBindingsSurviveDraftAndIssuedSnapshotColdRoundTrips() {
        val old=cycle()
        val savedLegacy=NativeCustomCycleDraftCodec.encode(old)
        assertEquals(1,savedLegacy[7].toInt())
        val bound=cycle("A")
        val raw=NativeCustomCycleDraftCodec.encode(bound)
        assertEquals(3,raw[7].toInt())
        assertEquals(bound,NativeCustomCycleDraftCodec.decode(raw))
        assertArrayEquals(raw,NativeCustomCycleDraftCodec.encode(
            NativeCustomCycleDraftCodec.decode(raw)))
        assertArrayEquals(savedLegacy,NativeCustomCycleDraftCodec.encode(old))

        val original=NativeKotlinCustomWorkoutSnapshotCodec.encode(snapshot())
        assertEquals(1,original[7].toInt())
        val machine=NativeKotlinCustomWorkoutSnapshotCodec.encode(snapshot("A"))
        assertEquals(4,machine[7].toInt())
        assertEquals(snapshot("A"),
            NativeKotlinCustomWorkoutSnapshotCodec.decode(machine))
        assertArrayEquals(machine,NativeKotlinCustomWorkoutSnapshotCodec.encode(
            NativeKotlinCustomWorkoutSnapshotCodec.decode(machine)))
        val slot=snapshot("A").slots.single()
        assertEquals(slot,NativeKotlinCustomWorkoutSnapshotCodec.decodeSlot(
            NativeKotlinCustomWorkoutSnapshotCodec.encodeSlot(slot)))
        assertArrayEquals(original,
            NativeKotlinCustomWorkoutSnapshotCodec.encode(snapshot()))
    }

    @Test fun invalidAndConflictingPhysicalIdsCannotBeSavedOrUsed() {
        val illegal=cycle("bad id")
        assertThrows(IllegalArgumentException::class.java) {
            NativeCustomCycleDraftCodec.encode(illegal)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinCustomWorkoutSnapshotCodec.encode(snapshot("bad id"))
        }
        val prepared=snapshot("A")
        val conflicting=prepared.copy(slots=prepared.slots+prepared.slots.single()
            .copy(slotId="extension-2",machineInstanceId="B",sets=listOf(
                CustomWorkoutSetSnapshot(
                    "cycle-1:w1:d0:extension-2:s1","extension-2",1,
                    authored("60")))),knownSetAndRestSeconds=290)
        assertEquals(NativeCustomMachineBindingReview.Rejected(
            "CONFLICTING_MACHINE_CHOICES"),
            NativeKotlinCustomMachineBindingPolicy.review(
                "owner-1",conflicting,listOf(movement),roster()))
    }
}
