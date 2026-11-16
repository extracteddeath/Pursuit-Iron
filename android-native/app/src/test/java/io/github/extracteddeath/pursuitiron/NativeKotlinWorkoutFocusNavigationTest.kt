package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutVisitKey
import io.github.extracteddeath.pursuitiron.rebuild.CustomWorkoutVisitPart
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinWorkoutFocusNavigationTest {
    private val policy=NativeKotlinWorkoutFocusNavigationPolicy

    @Test fun generatedWorkoutCanBrowseResolvedAndUnresolvedSetsWithoutChangingRecords() {
        val entries=listOf(
            NativeKotlinFocusStep("warmup-1",true),
            NativeKotlinFocusStep("work-1",true),
            NativeKotlinFocusStep("work-2",false),
            NativeKotlinFocusStep("work-3",false))
        val review=checkNotNull(policy.inspect(entries,"work-1","work-2"))
        assertEquals(2,review.position)
        assertEquals(4,review.total)
        assertEquals("warmup-1",review.previous)
        assertEquals("work-2",review.next)
        assertEquals("work-2",review.nextUnfinished)
        assertTrue(review.reviewingResolved)
        assertTrue(review.canReturnToUnfinished)
        assertEquals("work-1",
            policy.inspect(entries,"warmup-1","work-2")?.next)
        assertNull(policy.inspect(entries,"not-an-issued-set","work-2"))
        assertEquals(listOf(false,false),entries.takeLast(2).map{it.resolved})
    }

    @Test fun typedCustomMainAndAmplifierFollowupStaySeparateIdentities() {
        val main=CustomWorkoutVisitKey("exercise:set:1",CustomWorkoutVisitPart.MAIN)
        val follow=CustomWorkoutVisitKey("exercise:set:1",CustomWorkoutVisitPart.FOLLOW_UP)
        val next=CustomWorkoutVisitKey("exercise:set:2",CustomWorkoutVisitPart.MAIN)
        val steps=listOf(NativeKotlinFocusStep(main,true),
            NativeKotlinFocusStep(follow,false),
            NativeKotlinFocusStep(next,false))
        val initial=checkNotNull(policy.inspect(steps,main,follow))
        assertEquals(follow,initial.next)
        assertEquals(follow,initial.nextUnfinished)
        assertTrue(initial.canReturnToUnfinished)
        val followFocus=checkNotNull(policy.inspect(steps,follow,follow))
        assertEquals(main,followFocus.previous)
        assertEquals(next,followFocus.next)
        assertFalse(followFocus.canReturnToUnfinished)
        assertEquals("exercise:set:1",followFocus.selected.setKey)
        assertEquals(CustomWorkoutVisitPart.FOLLOW_UP,followFocus.selected.part)
    }

    @Test fun staleOrForgedJournalVisitsDisableNavigationRatherThanGuess() {
        val steps=listOf(
            NativeKotlinFocusStep("first",true),
            NativeKotlinFocusStep("second",false))
        assertNull(policy.inspect(steps,"first","first"))
        assertNull(policy.inspect(steps,"first","phantom"))
        assertNull(policy.inspect(steps,"removed","second"))
        assertNull(policy.inspect(emptyList<NativeKotlinFocusStep<String>>(),
            "first",null))
        assertNull(policy.inspect(
            listOf(NativeKotlinFocusStep("first",false),
                NativeKotlinFocusStep("first",true)),
            "first",null))
    }

    @Test fun finalEntryAndCompletedWorkoutsNeverInventAnotherUnfinishedSet() {
        val steps=listOf(
            NativeKotlinFocusStep("one",true),
            NativeKotlinFocusStep("two",true))
        val review=checkNotNull(policy.inspect(steps,"two",null))
        assertEquals("one",review.previous)
        assertNull(review.next)
        assertNull(review.nextUnfinished)
        assertFalse(review.canReturnToUnfinished)
        val first=checkNotNull(policy.inspect(steps,"one",null))
        assertNull(first.previous)
        assertEquals("two",first.next)
        assertFalse(first.canReturnToUnfinished)
    }

    @Test fun mutableUiReviewCannotAlterIssuedSetIdentityOrOutcome() {
        val ids=listOf("x", "y", "z")
        val steps=ids.mapIndexed{index,id->
            NativeKotlinFocusStep(id,index==0)
        }
        val selected=policy.inspect(steps,"x","y")
        assertNotNull(selected)
        assertEquals(listOf("x","y","z"),ids)
        assertEquals(listOf(true,false,false),steps.map{it.resolved})
        assertEquals("y",policy.inspect(steps,"z","y")?.nextUnfinished)
    }
}
