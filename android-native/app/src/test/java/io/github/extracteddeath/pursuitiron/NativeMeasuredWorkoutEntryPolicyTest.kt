package io.github.extracteddeath.pursuitiron

import org.junit.Assert.assertEquals
import org.junit.Test

/** Only verified owner/disk results may open any measured set editor. */
class NativeMeasuredWorkoutEntryPolicyTest {
    private fun resolve(
        expected:Boolean=false,
        complete:Boolean=true,
        saved:Boolean=false,
        notIssued:Boolean=false
    )=NativeMeasuredWorkoutEntryPolicy.resolve(
        expected,complete,saved,notIssued)

    @Test fun onlyExplicitEmptyDiskReceiptCanAuthorizeFreshAuthoring() {
        assertEquals(NativeMeasuredWorkoutEntryState.VERIFYING,
            resolve(complete=false))
        assertEquals(NativeMeasuredWorkoutEntryState.AUTHORING,
            resolve(notIssued=true))
        // A failed I/O read, foreign owner, corrupt session or exception
        // does not mean that a new workout ID is safe to issue.
        assertEquals(NativeMeasuredWorkoutEntryState.RECOVERY_REQUIRED,
            resolve())
        assertEquals(NativeMeasuredWorkoutEntryState.RECOVERY_REQUIRED,
            resolve(expected=false,complete=true,saved=false,
                notIssued=false))
    }

    @Test fun savedSessionDoesNotBecomeBlankEditorEvenWithNotIssuedReceipt() {
        assertEquals(NativeMeasuredWorkoutEntryState.VERIFYING,
            resolve(expected=true,complete=false,notIssued=true))
        assertEquals(NativeMeasuredWorkoutEntryState.RECOVERY_REQUIRED,
            resolve(expected=true,complete=true,notIssued=true))
        assertEquals(NativeMeasuredWorkoutEntryState.RECOVERY_REQUIRED,
            resolve(expected=true,complete=true))
        assertEquals(NativeMeasuredWorkoutEntryState.RECORDING,
            resolve(expected=true,saved=true))
    }

    @Test fun durableJournalBeatsNewAuthoringAndUnverifiedCachedResult() {
        assertEquals(NativeMeasuredWorkoutEntryState.RECORDING,
            resolve(saved=true,notIssued=true))
        assertEquals(NativeMeasuredWorkoutEntryState.VERIFYING,
            resolve(expected=true,complete=false,saved=true))
        assertEquals(NativeMeasuredWorkoutEntryState.VERIFYING,
            resolve(complete=false,saved=true,notIssued=true))
    }

    @Test fun authoringSourceRequiresCompletedVerifiedAthleteGymRead() {
        assertEquals(NativeMeasuredSourceState.VERIFYING,
            NativeMeasuredSourcePolicy.resolve(false,false))
        assertEquals(NativeMeasuredSourceState.VERIFYING,
            NativeMeasuredSourcePolicy.resolve(false,true))
        assertEquals(NativeMeasuredSourceState.RECOVERY_REQUIRED,
            NativeMeasuredSourcePolicy.resolve(true,false))
        assertEquals(NativeMeasuredSourceState.READY,
            NativeMeasuredSourcePolicy.resolve(true,true))
    }
}
