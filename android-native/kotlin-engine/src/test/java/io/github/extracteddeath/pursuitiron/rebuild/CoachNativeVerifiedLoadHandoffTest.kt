package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachNativeVerifiedLoadHandoffTest {
    private fun n(text:String)=BigDecimal(text)
    private val revision=CoachCanonicalLoadRevision("native-program",1000,1000,13,"a".repeat(64))
    private val candidates=listOf(
        CoachEligibleLoadCandidate("leg-ext","Leg Extension","external_load"),
        CoachEligibleLoadCandidate("lat-pulldown","Lat Pulldown","cable_stack"),
        CoachEligibleLoadCandidate("hack-squat","Hack Squat","external_load"),
        CoachEligibleLoadCandidate("machine-row","Machine Row","machine_stack")
    )
    private val exact=CoachNativeExerciseLoadFact.Exact(
        "leg-ext","Leg Extension","lb",listOf(n("12.5"),n("17.5"),n("22.5")))
    private val range=CoachNativeExerciseLoadFact.Range(
        "lat-pulldown","Lat Pulldown","lb",n("5"),n("7.5"),n("200"))
    private fun evidence(vararg facts:CoachNativeExerciseLoadFact)=
        CoachNativeLoadEvidence(revision,candidates.size,facts.toList())
    private fun review(vararg facts:CoachNativeExerciseLoadFact)=
        CoachNativeVerifiedLoadHandoff.review(evidence(*facts),revision,candidates,LoadUnit.LB)
    private fun accepted(vararg facts:CoachNativeExerciseLoadFact):CoachLoadHandoffReview.Accepted =
        review(*facts) as CoachLoadHandoffReview.Accepted

    @Test fun nativeEvidenceMapsToActuallySelectableKotlinWeights() {
        val safe=accepted(exact,range)
        assertFalse(safe.canIssueWorkout)
        assertFalse(safe.canApplyProgression)
        assertEquals(setOf("hack-squat","machine-row"),safe.manualExerciseIds)
        assertEquals(2,safe.byExerciseId.size)
        val leg=safe.byExerciseId.getValue("leg-ext") as VerifiedLoadStock.Totals
        assertEquals(LoadBasis.EXERCISE_OVERRIDE,leg.basis)
        assertEquals(LoadUnit.LB,leg.unit)
        assertEquals(listOf(n("12.5"),n("17.5"),n("22.5")),leg.loads)

        val at20=CoachNativeVerifiedLoadHandoff.inspect(
            safe,revision,"leg-ext",n("20"),LoadUnit.LB) as LoadInspection.Confirmed
        assertFalse(at20.exact)
        assertEquals(n("17.5"),at20.atOrBelow)
        assertEquals(n("22.5"),at20.nextAbove)
        val cable=CoachNativeVerifiedLoadHandoff.inspect(
            safe,revision,"lat-pulldown",n("159.5"),LoadUnit.LB)
            as LoadInspection.Confirmed
        assertFalse(cable.exact)
        assertEquals(n("155"),cable.atOrBelow)
        assertEquals(n("162.5"),cable.nextAbove)
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.NO_CONFIRMED_STOCK),
            CoachNativeVerifiedLoadHandoff.inspect(
                safe,revision,"hack-squat",n("45"),LoadUnit.LB))
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH),
            CoachNativeVerifiedLoadHandoff.inspect(
                safe,revision,"leg-ext",n("20"),LoadUnit.KG))
    }

    @Test fun revisionAndGymUnitMustBeCurrentAndAuthenticatedElseFailClosed() {
        val evidence=evidence(exact)
        val old=revision.copy(snapshotSha256="b".repeat(64))
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.STALE_REVISION),
            CoachNativeVerifiedLoadHandoff.review(evidence,old,candidates,LoadUnit.LB))
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_REVISION),
            CoachNativeVerifiedLoadHandoff.review(evidence,
                revision.copy(snapshotSha256="bad"),candidates,LoadUnit.LB))
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_EVIDENCE),
            CoachNativeVerifiedLoadHandoff.review(evidence,revision,candidates,LoadUnit.KG))
        val safe=accepted(exact)
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.NO_CONFIRMED_STOCK),
            CoachNativeVerifiedLoadHandoff.inspect(
                safe,old,"leg-ext",n("20"),LoadUnit.LB))
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.NO_CONFIRMED_STOCK),
            CoachNativeVerifiedLoadHandoff.inspect(
                safe,revision,"unknown-exercise",n("20"),LoadUnit.LB))
    }

    @Test fun unconfirmedAndGenericSharedHardwareNeverCreateAnExerciseStep() {
        val safe=accepted()
        assertTrue(safe.byExerciseId.isEmpty())
        assertEquals(candidates.map {it.id}.toSet(),safe.manualExerciseIds)
        assertEquals(LoadInspection.Unavailable(LoadUnavailableReason.NO_CONFIRMED_STOCK),
            CoachNativeVerifiedLoadHandoff.inspect(
                safe,revision,"leg-ext",n("20"),LoadUnit.LB))
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_EVIDENCE),
            CoachNativeVerifiedLoadHandoff.review(
                CoachNativeLoadEvidence(revision,0,listOf(exact)),
                revision,candidates,LoadUnit.LB))
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_CANDIDATES),
            CoachNativeVerifiedLoadHandoff.review(
                evidence(exact),revision,candidates+candidates.first(),LoadUnit.LB))
    }

    @Test fun specialtyRangesAndTamperedPhysicalNumbersAreRejectedEntirely() {
        val specialtyRange=CoachNativeExerciseLoadFact.Range(
            "leg-ext","Leg Extension","lb",n("5"),n("5"),n("150"))
        val wrongName=exact.copy(name="Someone else's machine")
        val duplicated=exact.copy(loads=listOf(n("12.5"),n("12.50")))
        val impossible=exact.copy(loads=listOf(n("-5"),n("10")))
        val oversized=exact.copy(loads=listOf(n("10.0001"),n("15")))
        val giant=range.copy(increment=n("0.001"),maximum=n("1000"))
        for(fact in listOf(specialtyRange,wrongName,duplicated,impossible,oversized,giant)) {
            assertTrue("Unexpected acceptance: $fact",
                review(exact,range,fact) is CoachLoadHandoffReview.Rejected)
        }
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_PHYSICAL_STOCK),
            review(specialtyRange))
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_PHYSICAL_STOCK),
            review(giant))
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_EVIDENCE),
            review(wrongName))
        val unknown=CoachNativeExerciseLoadFact.Exact(
            "not-in-program","Invented","lb",listOf(n("10"),n("15")))
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_EVIDENCE),
            review(unknown))
        val badUnit=exact.copy(unit="kg")
        assertEquals(CoachLoadHandoffReview.Rejected(CoachLoadHandoffRejection.INVALID_EVIDENCE),
            review(badUnit))
    }

    @Test fun acceptedSnapshotsCannotBeMutatedIntoPhantomWeights() {
        val source=mutableListOf(n("12.5"),n("17.5"),n("22.5"))
        val fact=exact.copy(loads=source)
        val safe=accepted(fact)
        source.add(n("999"))
        val loads=(safe.byExerciseId.getValue("leg-ext") as VerifiedLoadStock.Totals).loads
        assertEquals(3,loads.size)
        assertTrue(runCatching {
            (loads as MutableList<BigDecimal>).add(n("999"))
        }.isFailure)
        assertTrue(runCatching {
            (safe.byExerciseId as MutableMap<String,VerifiedLoadStock>).clear()
        }.isFailure)
        assertTrue(runCatching {
            (safe.manualExerciseIds as MutableSet<String>).clear()
        }.isFailure)
        assertEquals(n("22.5"),(
            CoachNativeVerifiedLoadHandoff.inspect(
                safe,revision,"leg-ext",n("20"),LoadUnit.LB)
                as LoadInspection.Confirmed).nextAbove)
    }

    private fun successfulWorkout(id:String,reps:List<Int> = listOf(8,8,8),
        rir:List<Int?> = listOf(2,2,2)):ProgressionWorkoutEvidence =
        ProgressionWorkoutEvidence(id,"lat-pulldown",LoadUnit.LB,RepBand(5,8),3,
            reps.indices.map {i->
                WorkingSetFact(i,SetOutcome.COMPLETED,n("155"),reps[i],rir[i],
                    n("155"),3)
            })

    @Test fun progressionOnlyOffersEvidenceQualifiedVerifiedNextStep() {
        val previous=successfulWorkout("prior")
        val latest=successfulWorkout("latest")
        val safe=accepted(range)
        val candidate=CoachNativeVerifiedLoadHandoff.progressionReview(
            safe,revision,previous,latest,n("10"))
        assertEquals(IncreaseReviewReason.QUALIFIED_FOR_REVIEW,candidate?.reason)
        assertEquals(n("162.5"),candidate?.reviewCandidate)
        assertFalse(safe.canApplyProgression)
        val blocked=CoachNativeVerifiedLoadHandoff.progressionReview(
            safe,revision,previous,latest,n("5"))
        assertEquals(IncreaseReviewReason.INCREMENT_EXCEEDS_USER_LIMIT,blocked?.reason)
        val missing=CoachNativeVerifiedLoadHandoff.progressionReview(
            accepted(),revision,previous,latest,n("10"))
        assertEquals(IncreaseReviewReason.CONFIRMED_INVENTORY_REQUIRED,missing?.reason)
        val lowEffort=successfulWorkout("latest",rir=listOf(2,1,2))
        assertEquals(IncreaseReviewReason.TOO_CLOSE_TO_FAILURE,
            CoachNativeVerifiedLoadHandoff.progressionReview(
                safe,revision,previous,lowEffort,n("10"))?.reason)
        assertNull(CoachNativeVerifiedLoadHandoff.progressionReview(
            safe,revision.copy(snapshotSha256="c".repeat(64)),
            previous,latest,n("10")))
        assertNull(CoachNativeVerifiedLoadHandoff.progressionReview(
            safe,revision,previous,latest.copy(exerciseId="hijack"),n("10")))
    }
}
