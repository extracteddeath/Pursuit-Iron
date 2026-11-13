package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Machine, gym and approved training-day scope are independent load baselines. */
class KotlinMachineDayIsolationRegressionTest {
    private fun fact(gym:String="gymA",machine:String?="pressA",day:Int=0,
                     load:String="200",unit:LoadUnit=LoadUnit.LB) =
        KotlinMachineSetFact(gym,"chestPress",machine,day,"workout-${gym}-${machine}-${day}",
            0,BigDecimal(load),8,unit)

    @Test fun identicalExerciseNeverMakesOtherMachineOrDayComparable() {
        val heavy=fact()
        assertTrue(KotlinMachineAlternativesDomain.comparable(heavy,fact(load="205")))
        assertFalse(KotlinMachineAlternativesDomain.comparable(heavy,fact(machine="pressB")))
        assertFalse(KotlinMachineAlternativesDomain.comparable(heavy,fact(day=3)))
        assertFalse(KotlinMachineAlternativesDomain.comparable(heavy,fact(gym="gymB")))
        assertFalse(KotlinMachineAlternativesDomain.comparable(heavy,fact(unit=LoadUnit.KG)))
    }

    @Test fun scopedHistoryDoesNotBorrowOtherDayMachineOrGym() {
        val a=fact(load="200")
        val otherDay=fact(day=3,load="120")
        val otherMachine=fact(machine="pressB",load="75")
        val otherGym=fact(gym="gymB",load="90")
        val facts=listOf(a,otherDay,otherMachine,otherGym)
        assertEquals(listOf(a),KotlinMachineAlternativesDomain.scopedHistory(
            facts,"gymA","chestPress","pressA",dayIndex=0))
        assertEquals(listOf(a,otherDay),KotlinMachineAlternativesDomain.scopedHistory(
            facts,"gymA","chestPress","pressA"))
        assertEquals(listOf(otherMachine),KotlinMachineAlternativesDomain.scopedHistory(
            facts,"gymA","chestPress","pressB",dayIndex=0))
    }

    @Test fun missingPhysicalStackCannotBorrowAnotherMachineIncrement() {
        val config=KotlinGymMachineAlternatives(
            gymId="gymA",exerciseId="chestPress",defaultMachineId="pressA",
            machines=listOf(
                KotlinPhysicalMachine("pressA","Press A",unit=LoadUnit.LB,
                    increment=BigDecimal("5"),minimum=BigDecimal("20"),
                    maximum=BigDecimal("300")),
                KotlinPhysicalMachine("pressB","Press B",unit=LoadUnit.LB)
            )
        )
        assertNotNull(KotlinMachineAlternativesDomain.confirmedStack(
            config,"pressA",LoadBasis.MACHINE_STACK))
        assertNull(KotlinMachineAlternativesDomain.confirmedStack(
            config,"pressB",LoadBasis.MACHINE_STACK))
        assertNull(KotlinMachineAlternativesDomain.confirmedStack(
            config,"unknown",LoadBasis.MACHINE_STACK))
    }
}
