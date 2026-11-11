package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * A narrow, read-only preference for retaining *already approved* named
 * strength-skill practice across a future block change. This is NOT an
 * evidence claim that any fixed number of sessions per lift is optimal.
 *
 * Count actual working-primary exercise IDs, not movement-family proxies,
 * and resolve EVERY projected week so a week-level override cannot hide a
 * loss of practice. A reduction is a soft ranking cost, never a refusal:
 * athlete-approved changes can still be selected explicitly.
 */
internal object NamedLiftPracticeContinuityDomain {
    private fun daysByWeek(
        review:CustomCycleReview?,blockId:String,exerciseId:String
    ):List<Int> {
        if(review==null ||
            review.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW ||
            review.verifiedDraft==null)return emptyList()
        return (1..review.totalWeeks).mapNotNull {
            CustomTrainingCycleDomain.week(review,it)
        }.filter {it.blockId==blockId &&
            it.purpose==CustomBlockPurpose.TRAINING}.map {week->
            week.days.count {day->
                day.slots.any {slot ->
                    slot.exerciseId==exerciseId &&
                    slot.role==ComposerRole.PRIMARY &&
                    slot.sets.any {it.type==DraftSetType.WORKING}
                }
            }
        }
    }

    fun lostDays(
        approved:CustomCycleReview?,proposed:CustomCycleReview?,
        blockId:String,namedExerciseIds:Set<String>
    ):BigDecimal {
        if(namedExerciseIds.isEmpty())return BigDecimal.ZERO
        // Incomplete provenance is never treated as a verified ranking.
        // The caller supplies independently verified full-cycle audits.
        require(approved?.verifiedDraft!=null &&
            proposed?.verifiedDraft!=null)
        val losses=namedExerciseIds.sumOf {exerciseId->
            val before=daysByWeek(approved,blockId,exerciseId)
            val after=daysByWeek(proposed,blockId,exerciseId)
            // Compare aligned weeks of the selected block. A single bad
            // override cannot be hidden by another week's higher count.
            // Block-duration changes are governed by the full-cycle audit;
            // this preference never counts removed weeks as lost sessions.
            (0 until minOf(before.size,after.size)).maxOfOrNull {index->
                maxOf(0,before[index]-after[index])
            } ?:0
        }
        return BigDecimal(losses)
    }
}
