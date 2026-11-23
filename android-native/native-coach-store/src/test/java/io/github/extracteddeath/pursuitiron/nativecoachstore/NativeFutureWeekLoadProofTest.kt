package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.math.BigDecimal
import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

class NativeFutureWeekLoadProofTest {
    private fun proof(
        owner:String="owner-1",athlete:String="athlete-1",
        activation:String="a".repeat(64),
        history:Long=31,draft:Long=8,source:Long=4,
        settings:Long=2,permission:Long=3,
        week:Int=9,weekday:Int=1,slot:String="bench",
        before:String="200",after:String="205",
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,limit:String="5",
        receipts:List<String> = listOf("upper-previous","upper-latest"),
        proposed:ByteArray=byteArrayOf(0x01,0x13,0x35),
        machineInstanceId:String?=null,
        stockLoads:List<String> = listOf("195","200","205","210")
    ):String {
        val physical=VerifiedLoadStock.Totals(
            LoadUnit.LB,LoadBasis.MACHINE_STACK,stockLoads.map(::BigDecimal))
        val reviewed=KotlinFutureLoadNumberAuthorityDomain.review(
            BigDecimal(before),BigDecimal(after),LoadUnit.LB,
            LoadBasis.MACHINE_STACK,physical,BigDecimal(limit),
            KotlinFutureLoadSource.EARNED_NEXT_INCREMENT)
        require(reviewed is KotlinFutureLoadReview.Accepted)
        return NativeFutureWeekLoadProof.fingerprint(
        ownerKey=owner,athleteId=athlete,cycleId="cycle-1",
        blockId="block-2",optionId="history_earned_week_9_1_bench",
        proposedBytes=proposed,activationFingerprint=activation,
        historyRevision=history,draftRevision=draft,sourceRevision=source,
        settingsRevision=settings,permissionRevision=permission,
        absoluteWeek=week,weekday=weekday,slotId=slot,
        previousLoad=BigDecimal(before),candidateLoad=BigDecimal(after),
        readiness=readiness,movementRestricted=restricted,
        maximumAuthorizedIncrease=BigDecimal(limit),
        evidenceWorkoutIds=receipts,
        numberFact=reviewed.fact,machineInstanceId=machineInstanceId)
    }

    @Test fun consistentAfterColdRecomputationAndDecimalNormalization() {
        assertEquals(proof(),proof())
        assertEquals(proof(),proof(before="200.000",after="205.0"))
        assertTrue(Regex("[a-f0-9]{64}").matches(proof()))
    }

    @Test fun anyRevisionOrActivatedContextChangeInvalidatesReview() {
        val initial=proof()
        assertNotEquals(initial,proof(owner="owner-2"))
        assertNotEquals(initial,proof(athlete="athlete-2"))
        assertNotEquals(initial,proof(activation="b".repeat(64)))
        assertNotEquals(initial,proof(history=32))
        assertNotEquals(initial,proof(draft=9))
        assertNotEquals(initial,proof(source=5))
        assertNotEquals(initial,proof(settings=3))
        assertNotEquals(initial,proof(permission=4))
    }

    @Test fun changedWeekSlotLoadEvidenceOrExactProposalCannotReuseConsent() {
        val initial=proof()
        assertNotEquals(initial,proof(week=10))
        assertNotEquals(initial,proof(weekday=4))
        assertNotEquals(initial,proof(slot="incline-bench"))
        assertNotEquals(initial,proof(before="195",after="200"))
        assertNotEquals(initial,proof(before="205",after="210"))
        assertNotEquals(initial,proof(readiness=CoachReadiness.LOW))
        assertNotEquals(initial,proof(restricted=true))
        assertNotEquals(initial,proof(limit="6"))
        assertNotEquals(initial,proof(receipts=listOf("upper-previous","upper-new")))
        assertNotEquals(initial,proof(receipts=listOf("upper-latest","upper-previous")))
        assertNotEquals(initial,proof(proposed=byteArrayOf(0x01,0x13,0x36)))
        assertNotEquals(initial,proof(machineInstanceId="machine-A"))
        assertNotEquals(proof(machineInstanceId="machine-A"),
            proof(machineInstanceId="machine-B"))
        // Even with the SAME displayed 200 -> 205 and source revisions,
        // changing verified physical neighbors invalidates old consent.
        assertNotEquals(initial,proof(stockLoads=listOf("198","200","205","210")))
        // A distant stock change that leaves the reviewed issued-load
        // neighbors unchanged is not a different immediate load decision.
        assertEquals(initial,proof(stockLoads=listOf("195","200","205","208")))
    }

    @Test fun malformedOrNonEarnedProofFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            proof(activation="unreviewed")
        }
        assertThrows(IllegalArgumentException::class.java) {
            proof(after="199")
        }
        assertThrows(IllegalArgumentException::class.java) {
            proof(limit="4")
        }
        assertThrows(IllegalArgumentException::class.java) {
            proof(receipts=listOf("duplicate","duplicate"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            proof(proposed=byteArrayOf())
        }
        assertThrows(IllegalArgumentException::class.java) {
            proof(machineInstanceId="not a machine id")
        }
        assertThrows(IllegalArgumentException::class.java) {
            proof(stockLoads=listOf("200","210"))
        }
    }
}
