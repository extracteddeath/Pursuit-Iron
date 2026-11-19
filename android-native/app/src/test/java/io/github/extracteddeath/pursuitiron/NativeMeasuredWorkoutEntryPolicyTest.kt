package io.github.extracteddeath.pursuitiron

import org.junit.Assert.assertEquals
import org.junit.Test

/** No generated targets or unsaved actuals may be treated as a durable session. */
class NativeMeasuredWorkoutEntryPolicyTest {
    @Test fun freshSessionStartsOnlyAfterDiskLookupAndWithoutEarlierJournal() {
        assertEquals(NativeMeasuredWorkoutEntryState.VERIFYING,
            NativeMeasuredWorkoutEntryPolicy.resolve(
                expectsExisting=false,lookupComplete=false,hasVerifiedJournal=false))
        assertEquals(NativeMeasuredWorkoutEntryState.AUTHORING,
            NativeMeasuredWorkoutEntryPolicy.resolve(
                expectsExisting=false,lookupComplete=true,hasVerifiedJournal=false))
        assertEquals(NativeMeasuredWorkoutEntryState.RECORDING,
            NativeMeasuredWorkoutEntryPolicy.resolve(
                expectsExisting=false,lookupComplete=true,hasVerifiedJournal=true))
    }

    @Test fun savedSessionCannotBecomeNewEditorAfterMissingOrFailedRead() {
        assertEquals(NativeMeasuredWorkoutEntryState.VERIFYING,
            NativeMeasuredWorkoutEntryPolicy.resolve(
                expectsExisting=true,lookupComplete=false,hasVerifiedJournal=false))
        assertEquals(NativeMeasuredWorkoutEntryState.RECOVERY_REQUIRED,
            NativeMeasuredWorkoutEntryPolicy.resolve(
                expectsExisting=true,lookupComplete=true,hasVerifiedJournal=false))
        assertEquals(NativeMeasuredWorkoutEntryState.RECORDING,
            NativeMeasuredWorkoutEntryPolicy.resolve(
                expectsExisting=true,lookupComplete=true,hasVerifiedJournal=true))
    }

    @Test fun pendingReadIsNeverPermissionToRecordEvenIfOldSnapshotIsPresent() {
        assertEquals(NativeMeasuredWorkoutEntryState.VERIFYING,
            NativeMeasuredWorkoutEntryPolicy.resolve(
                expectsExisting=true,lookupComplete=false,hasVerifiedJournal=true))
        assertEquals(NativeMeasuredWorkoutEntryState.VERIFYING,
            NativeMeasuredWorkoutEntryPolicy.resolve(
                expectsExisting=false,lookupComplete=false,hasVerifiedJournal=true))
    }
}
