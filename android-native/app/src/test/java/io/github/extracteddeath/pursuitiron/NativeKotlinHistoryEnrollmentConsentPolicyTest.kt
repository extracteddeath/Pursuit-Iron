package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinMixedHistoryReadResult
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinMixedHistoryRefusal
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinMixedHistoryTimeline
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeKotlinOwnerIdentity
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinHistoryEnrollmentConsentPolicyTest {
    private val a=NativeKotlinOwnerIdentity("owner_A","athlete_A")
    private val b=NativeKotlinOwnerIdentity("owner_B","athlete_B")
    private val noHistory=NativeKotlinMixedHistoryReadResult.Rejected(
        NativeKotlinMixedHistoryRefusal.NOT_ENROLLED)

    @Test fun onlyTheSameSignedOwnerWithFreshUnenrolledReceiptMayOpenConsent() {
        assertEquals(a,NativeKotlinHistoryEnrollmentConsentPolicy.capture(
            a,noHistory,a))
        assertNull(NativeKotlinHistoryEnrollmentConsentPolicy.capture(
            a,noHistory,b))
        assertNull(NativeKotlinHistoryEnrollmentConsentPolicy.capture(
            a,noHistory,null))
        assertNull(NativeKotlinHistoryEnrollmentConsentPolicy.capture(
            null,noHistory,a))
        assertFalse(NativeKotlinHistoryEnrollmentConsentPolicy.matches(a,b))
        assertFalse(NativeKotlinHistoryEnrollmentConsentPolicy.matches(a,null))
        assertFalse(NativeKotlinHistoryEnrollmentConsentPolicy.matches(null,a))
        assertTrue(NativeKotlinHistoryEnrollmentConsentPolicy.matches(a,a))
    }

    @Test fun nonEnrolledMustBeProvenNotAssumedFromHistoryFailureOrEmpty() {
        val validEmpty=NativeKotlinMixedHistoryReadResult.Review(
            NativeKotlinMixedHistoryTimeline("athlete_A",1,emptyList()))
        assertNull(NativeKotlinHistoryEnrollmentConsentPolicy.capture(
            a,validEmpty,a))
        assertNull(NativeKotlinHistoryEnrollmentConsentPolicy.capture(
            a,null,a))
        for(failure in NativeKotlinMixedHistoryRefusal.entries) {
            if(failure==NativeKotlinMixedHistoryRefusal.NOT_ENROLLED)continue
            assertNull(failure.name,NativeKotlinHistoryEnrollmentConsentPolicy.capture(
                a,NativeKotlinMixedHistoryReadResult.Rejected(failure),a))
        }
    }

    @Test fun ownerAndAthleteMustBothMatchAtTheFinalConfirmation() {
        val differentAthlete=NativeKotlinOwnerIdentity(a.ownerKey,"athlete_B")
        val differentOwner=NativeKotlinOwnerIdentity(b.ownerKey,a.athleteId)
        for(changed in listOf(differentAthlete,differentOwner,b)) {
            assertNull(NativeKotlinHistoryEnrollmentConsentPolicy.capture(
                a,noHistory,changed))
            assertFalse(NativeKotlinHistoryEnrollmentConsentPolicy.matches(
                a,changed))
        }
        assertTrue(NativeKotlinHistoryEnrollmentConsentPolicy.matches(
            a,a.copy()))
    }
}
