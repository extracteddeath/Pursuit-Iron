package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class KotlinMachineAlternativesDomainTest {
    private fun machine(id:String,label:String,step:String="5")=
        KotlinPhysicalMachine(id,label,increment=BigDecimal(step))
    @Test fun gymDefaultsAndHistoricalDeactivationDoNotChangeStoredMachine() {
        val empty=KotlinGymMachineAlternatives("gymX","machine_press",null,emptyList())
        val a=KotlinMachineAlternativesDomain.add(empty,machine("A","Machine A"),true)
        val b=KotlinMachineAlternativesDomain.add(a,machine("B","Machine B","10"))
        assertEquals("A",KotlinMachineAlternativesDomain.resolve(b).id)
        val moved=KotlinMachineAlternativesDomain.setDefault(b,"B")
        val deactivated=KotlinMachineAlternativesDomain.edit(moved,
            moved.machines.first().copy(active=false))
        assertEquals("B",KotlinMachineAlternativesDomain.resolve(deactivated).id)
        assertEquals("A",KotlinMachineAlternativesDomain.resolve(deactivated,"A",true).id)
        assertFalse(KotlinMachineAlternativesDomain.resolve(deactivated,"A",true).active)
        assertEquals("gone",
            KotlinMachineAlternativesDomain.resolve(deactivated,"gone",true).id)
        assertTrue(KotlinMachineAlternativesDomain.resolve(deactivated,"gone",true).missing)
        assertThrows(IllegalArgumentException::class.java) {
            KotlinMachineAlternativesDomain.setDefault(deactivated,"A")
        }
    }

    @Test fun selectingPhysicalMachineAlwaysUsesIssuedGymNotRosterOrder() {
        val gymX=KotlinGymMachineAlternatives("gymX","machine_press","A",
            listOf(machine("A","Older stack")))
        val gymY=KotlinGymMachineAlternatives("gymY","machine_press","B",
            listOf(machine("B","Different stack","10")))
        val roster=listOf(gymX,gymY)
        assertEquals("B",KotlinMachineAlternativesDomain.forGymExercise(
            roster,"gymY","machine_press")?.defaultMachineId)
        assertEquals("A",KotlinMachineAlternativesDomain.forGymExercise(
            roster.reversed(),"gymX","machine_press")?.defaultMachineId)
        assertNull(KotlinMachineAlternativesDomain.forGymExercise(
            roster,"gymMissing","machine_press"))
        assertNull(KotlinMachineAlternativesDomain.forGymExercise(
            roster,"gymX","other_exercise"))
        // Duplicate matches are unsafe: there is no authoritative default.
        assertNull(KotlinMachineAlternativesDomain.forGymExercise(
            roster+gymX,"gymX","machine_press"))
    }

    @Test fun loadsNeverCompareAcrossMachinesGymsOrIndependentTrainingDays() {
        fun fact(gym:String,machine:String?,day:Int,load:Int)=
            KotlinMachineSetFact(gym,"machine_press",machine,day,"workout",0,
                BigDecimal(load),8,LoadUnit.LB)
        val a=fact("gymX","A",0,200)
        val b=fact("gymX","B",0,140)
        val otherDay=fact("gymX","A",2,210)
        val otherGym=fact("gymY","A",0,200)
        val legacy=fact("gymX",null,0,190)
        assertFalse(KotlinMachineAlternativesDomain.comparable(a,b))
        assertFalse(KotlinMachineAlternativesDomain.comparable(a,otherDay))
        assertFalse(KotlinMachineAlternativesDomain.comparable(a,otherGym))
        assertFalse(KotlinMachineAlternativesDomain.comparable(a,legacy))
        assertTrue(KotlinMachineAlternativesDomain.comparable(a,a.copy(load=BigDecimal(205))))
        val rows=listOf(a,b,otherDay,otherGym,legacy)
        assertEquals(listOf(a),
            KotlinMachineAlternativesDomain.scopedHistory(rows,"gymX","machine_press","A",0))
        assertEquals(listOf(b),
            KotlinMachineAlternativesDomain.scopedHistory(rows,"gymX","machine_press","B",0))
        assertEquals(listOf(legacy),
            KotlinMachineAlternativesDomain.scopedHistory(rows,"gymX","machine_press",
                KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID,0))
    }

    @Test fun twoVerifiedMachineStacksNeverBorrowIncrementOrRange() {
        val a=KotlinPhysicalMachine("A","Hammer Strength",unit=LoadUnit.LB,
            increment=BigDecimal("5"),minimum=BigDecimal("10"),
            maximum=BigDecimal("200"))
        val b=KotlinPhysicalMachine("B","Selectorized Press",unit=LoadUnit.LB,
            increment=BigDecimal("10"),minimum=BigDecimal("20"),
            maximum=BigDecimal("220"))
        val configured=KotlinGymMachineAlternatives("gymX","machine_press",
            "A",listOf(a,b))
        assertTrue(KotlinMachineAlternativesDomain.valid(configured))
        val stockA=KotlinMachineAlternativesDomain.confirmedStack(configured,"A",
            LoadBasis.MACHINE_STACK)
        val stockB=KotlinMachineAlternativesDomain.confirmedStack(configured,"B",
            LoadBasis.MACHINE_STACK)
        assertEquals(BigDecimal("5"),stockA?.increment)
        assertEquals(BigDecimal("10"),stockB?.increment)
        assertEquals(BigDecimal("200"),stockA?.maximum)
        assertEquals(BigDecimal("220"),stockB?.maximum)
        assertNull(KotlinMachineAlternativesDomain.confirmedStack(configured,
            "A",LoadBasis.BARBELL_TOTAL))
        assertNull(KotlinMachineAlternativesDomain.confirmedStack(configured,
            "missing",LoadBasis.MACHINE_STACK))
        assertNull(KotlinMachineAlternativesDomain.confirmedStack(
            configured.copy(machines=listOf(a.copy(minimum=null))),"A",
            LoadBasis.MACHINE_STACK))
        assertFalse(KotlinMachineAlternativesDomain.valid(
            configured.copy(machines=listOf(a.copy(maximum=BigDecimal("9"))))))
        val proofA=EquipmentLoadDomain.inspect(stockA,BigDecimal("25"),LoadUnit.LB)
            as LoadInspection.Confirmed
        val proofB=EquipmentLoadDomain.inspect(stockB,BigDecimal("25"),LoadUnit.LB)
            as LoadInspection.Confirmed
        assertTrue(proofA.exact)
        assertFalse(proofB.exact)
        assertEquals(BigDecimal("30"),proofB.nextAbove)
    }

    @Test fun untrustedOrIncompatibleLoadStepsAreRejected() {
        val empty=KotlinGymMachineAlternatives("gymX","machine_press",null,emptyList())
        assertThrows(IllegalArgumentException::class.java){
            KotlinMachineAlternativesDomain.add(empty,machine("A","Machine A","0"))
        }
        val a=KotlinMachineAlternativesDomain.add(empty,machine("A","Machine A"))
        assertThrows(IllegalArgumentException::class.java){
            KotlinMachineAlternativesDomain.add(a,machine("A","Duplicate A"))
        }
        assertEquals(KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID,
            KotlinMachineAlternativesDomain.normalizeMachineId(null))
    }
}
