package io.github.extracteddeath.pursuitiron.nativecoachstore

import org.junit.Assert.*
import org.junit.Test

class NativeFutureWeekPreparedReviewBoundaryTest {
    private fun fact(
        id:String="workout-9",cycle:String="cycle-1",
        week:Int=9,day:Int=1,issued:Boolean=false,
        completed:Boolean=false
    )=NativeCustomInventoryFact(id,cycle,week,day,issued,completed)

    private fun permit(
        sessions:List<NativeCustomInventoryFact>,
        acceptedId:String?=null
    )=NativeFutureWeekPreparedReviewBoundary.permits(
        sessions,"cycle-1",9,1,acceptedId)

    @Test fun newOfferCannotReviewAnyPreparedOrIssuedTargetDay() {
        assertTrue(permit(emptyList()))
        assertTrue(permit(listOf(fact(day=3),fact(week=8))))
        assertFalse(permit(listOf(fact())))
        assertFalse(permit(listOf(fact(issued=true))))
        assertFalse(permit(listOf(fact(completed=true))))
    }

    @Test fun issuanceCanRecheckOnlyTheExactUnissuedPreparation() {
        assertTrue(permit(listOf(fact()),"workout-9"))
        assertFalse(permit(emptyList(),"workout-9"))
        assertFalse(permit(listOf(fact()),"another-id"))
        assertFalse(permit(listOf(fact(issued=true)),"workout-9"))
        assertFalse(permit(listOf(fact(completed=true)),"workout-9"))
        assertFalse(permit(listOf(fact(),fact(id="another-id")),"workout-9"))
        assertTrue(permit(listOf(fact(),fact(week=8,issued=true)),
            "workout-9"))
        assertTrue(permit(listOf(fact(),fact(cycle="unrelated",issued=true)),
            "workout-9"))
    }
}
