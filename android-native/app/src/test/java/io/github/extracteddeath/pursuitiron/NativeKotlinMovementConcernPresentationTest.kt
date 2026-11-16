package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

/** The concern guide itself must never approve a skip or rewrite history. */
internal class NativeKotlinMovementConcernPresentationTest {
    private val selection=NativeMovementConcernRequest("slot-a",7L)
    private fun review(revision:Long=7L,
        values:List<String> = listOf("a:s2","a:s3"),
        preserved:Int=1)=RemainingExerciseReview(
            "dumbbell-press",revision,values,0,values.size,0,preserved)

    @Test fun verifiedSelectionOnlyOpensExistingSecondSkipConfirmation() {
        val options=NativeMovementConcernPresentation.review(
            selection,7L,true,review())
        assertTrue(options.current)
        assertEquals("dumbbell-press",options.exerciseId)
        assertEquals(2,options.remaining)
        assertEquals(1,options.alreadyRecorded)
        assertEquals(NativeExerciseSkipRequest("slot-a",7L),
            options.skipRequest)
    }

    @Test fun onlyUnstartedExerciseCanOpenIndependentReplacementReview() {
        val untouched=review(preserved=0)
        val eligible=NativeMovementConcernPresentation.review(
            selection,7L,true,untouched,canReviewReplacement=true)
        assertEquals("slot-a",eligible.replacementScopeId)
        assertEquals(NativeExerciseSkipRequest("slot-a",7L),eligible.skipRequest)
        assertNull(NativeMovementConcernPresentation.review(
            selection,7L,true,untouched,canReviewReplacement=false)
                .replacementScopeId)
        assertNull(NativeMovementConcernPresentation.review(
            selection,7L,true,review(),canReviewReplacement=true)
                .replacementScopeId)
        assertNull(NativeMovementConcernPresentation.review(
            selection,8L,true,untouched,canReviewReplacement=true)
                .replacementScopeId)
        assertNull(NativeMovementConcernPresentation.review(
            selection,7L,true,review(revision=6L),canReviewReplacement=true)
                .replacementScopeId)
        assertNull(NativeMovementConcernPresentation.review(
            NativeMovementConcernRequest(null,7L),7L,true,
            null,canReviewReplacement=true).replacementScopeId)
    }

    @Test fun staleOrFinishedWorkoutNeverOffersNoteOrSkip() {
        for(options in listOf(
            NativeMovementConcernPresentation.review(selection,8L,true,review()),
            NativeMovementConcernPresentation.review(selection,7L,false,review()),
            NativeMovementConcernPresentation.review(selection,null,true,review())
        ))assertEquals(NativeMovementConcernOptions(),options)
        val staleSource=NativeMovementConcernPresentation.review(
            selection,7L,true,review(revision=6L))
        assertTrue(staleSource.current)
        assertNull(staleSource.skipRequest)
        assertEquals(0,staleSource.remaining)
    }

    @Test fun noSelectedExerciseAllowsDocumentingConcernNotSkipping() {
        val missing=NativeMovementConcernRequest(null,9L)
        val projected=NativeMovementConcernPresentation.review(
            missing,9L,true,null)
        assertTrue(projected.current)
        assertNull(projected.skipRequest)
        assertNull(projected.exerciseId)
        assertEquals(0,projected.alreadyRecorded)
    }

    @Test fun alreadyResolvedExerciseCannotBeSkippedAgain() {
        val projected=NativeMovementConcernPresentation.review(
            selection,7L,true,review(values=emptyList(),preserved=3))
        assertTrue(projected.current)
        assertEquals(3,projected.alreadyRecorded)
        assertNull(projected.skipRequest)
        assertEquals(0,projected.remaining)
    }
}
