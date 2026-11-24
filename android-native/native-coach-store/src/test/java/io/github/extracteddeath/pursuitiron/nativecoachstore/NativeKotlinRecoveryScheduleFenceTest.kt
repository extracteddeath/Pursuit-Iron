package io.github.extracteddeath.pursuitiron.nativecoachstore

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinRecoveryScheduleFenceTest {
    private fun fact(id:String,week:Int,issued:Boolean=true,completed:Boolean=true,
        cycle:String="cycle")=NativeCustomInventoryFact(id,cycle,week,0,issued,completed)

    @Test fun appendPreservesCompletedWeeksButNotOutstandingPreparationsOrJournals() {
        val previous=listOf(fact("w1",1),fact("w4",4))
        val policy=NativeRecoveryScheduleInventoryPolicy
        assertTrue(policy.permits(previous,"cycle",5))
        assertTrue(policy.permits(previous+fact("other",5,completed=false,cycle="other"),"cycle",5))
        assertFalse(policy.permits(previous+fact("prepared",3,issued=false,completed=false),"cycle",5))
        assertFalse(policy.permits(previous+fact("unfinished",4,completed=false),"cycle",5))
        assertFalse(policy.permits(previous+fact("future",5),"cycle",5))
    }

    @Test fun revisionBoundCalendarOrActivationCannotBeSilentlyInvalidated() {
        val p=NativeRecoveryScheduleInventoryPolicy
        assertTrue(p.calendarPermits("cycle",false,emptySet(),emptySet()))
        assertTrue(p.calendarPermits("cycle",false,setOf("other"),setOf("other")))
        assertFalse(p.calendarPermits("cycle",true,emptySet(),emptySet()))
        assertFalse(p.calendarPermits("cycle",false,setOf("cycle"),emptySet()))
        assertFalse(p.calendarPermits("cycle",false,emptySet(),setOf("cycle")))
    }

    @Test fun futureInsertionCannotShiftAnyObservedWeekAtOrAfterItsBoundary() {
        val policy=NativeRecoveryScheduleInventoryPolicy
        val completed=listOf(fact("w1",1),fact("w3",3))
        assertTrue(policy.permits(completed,"cycle",4))
        assertFalse(policy.permits(completed,"cycle",3))
        assertFalse(policy.permits(completed,"cycle",2))
        assertFalse(policy.permits(emptyList(),"cycle",0))
        assertFalse(policy.permits(emptyList(),"cycle",105))
    }
}
