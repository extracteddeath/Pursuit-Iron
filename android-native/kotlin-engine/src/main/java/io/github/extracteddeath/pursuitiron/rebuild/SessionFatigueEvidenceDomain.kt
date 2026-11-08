package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Describes how real completed reps vary within one workout at a stable
 * external load. This is observed history, not a prediction of next set
 * capacity, a new training load, or evidence of a medical diagnosis.
 */
enum class FatiguePattern {
    INCOMPLETE_OR_UNCOMPARABLE,
    MIXED_LOADS,
    REPS_STABLE,
    REPS_DECLINING,
    REPS_INCREASING,
    NON_MONOTONIC_REPS
}

data class SessionFatigueEvidence(
    val pattern: FatiguePattern,
    val repChangesBetweenSets: List<Int>,
    val firstSetReps: Int?,
    val lastSetReps: Int?,
    val totalRepChange: Int?,
    val targetFloorMisses: Set<Int>,
    val reportedRirChange: Int?,
    val actualRirReportedSets: Set<Int>
) {
    init {
        if(pattern==FatiguePattern.INCOMPLETE_OR_UNCOMPARABLE ||
            pattern==FatiguePattern.MIXED_LOADS) {
            require(repChangesBetweenSets.isEmpty() && totalRepChange==null &&
                reportedRirChange==null)
        }
    }
}

/** Repeated, observed same-day performance decline; not a fatigue
 * diagnosis, physiological forecast, or a change authorization. */
enum class RepeatedSessionFatigueSignal {
    NO_REPEATED_DECLINE, BROAD_DECLINE, LATER_SET_DECLINE
}
data class RepeatedSessionFatigueEvidence(
    val signal:RepeatedSessionFatigueSignal,
    val trainingDayId:String,
    val workoutIds:List<String>,
    val decliningOrdinals:Set<Int>,
    /** Completed reps + REPORTed RIR - unchanged issued goal RIR. */
    val effortAlignedRepsBySession:List<List<Int>>
) {
    val mayChangeWorkout:Boolean get()=false
}

object SessionFatigueEvidenceDomain {
    /**
     * Three most recent appearances of the exact training-day cell.
     * Require the identical issued AND actual physical weight for EACH set
     * across all three sessions and a reported actual RIR for each set.
     * One bad workout cannot establish a repeatable decline.
     */
    fun inspectRecent(
        chronological:List<ProgressionWorkoutEvidence>,
        verifiedStock:VerifiedLoadStock?,
        verifiedPerSetLoading:Boolean=false
    ):RepeatedSessionFatigueEvidence? {
        if(chronological.size<3 || verifiedStock==null)return null
        val recent=chronological.takeLast(3)
        val first=recent.first()
        val cell=first.trainingDayId?.takeIf{it.isNotBlank()} ?:return null
        val n=first.expectedWorkingSets
        if(n !in 2..32 || verifiedStock.unit!=first.unit ||
            recent.map{it.workoutId}.distinct().size!=3 ||
            recent.any{it.workoutId.isBlank() || it.exerciseId!=first.exerciseId ||
                it.trainingDayId!=cell || it.unit!=first.unit ||
                it.targetReps!=first.targetReps || it.expectedWorkingSets!=n})
            return null
        val sessions=recent.map{session->
            val facts=DoubleProgressionEvidenceDomain.assess(
                session.targetReps,n,session.observations)
            if(facts.completedWorkingSets!=n ||
                (!verifiedPerSetLoading && facts.mixedActualLoads) ||
                session.observations.size!=n ||
                session.observations.any{it.outcome!=SetOutcome.COMPLETED})
                return null
            session.observations.sortedBy{it.workingOrdinal}
        }
        val base=sessions.first()
        val scores=ArrayList<List<Int>>()
        for(sets in sessions) {
            val row=ArrayList<Int>()
            for(i in 0 until n) {
                val original=base[i]
                val actual=sets[i]
                val weight=actual.actualLoad ?:return null
                val priorWeight=original.actualLoad ?:return null
                val goal=original.issuedRir ?:return null
                val rir=actual.actualRir ?:return null
                if(actual.issuedRir!=goal ||
                    original.issuedLoad?.compareTo(priorWeight)!=0 ||
                    actual.issuedLoad?.compareTo(weight)!=0 ||
                    weight.compareTo(priorWeight)!=0 ||
                    actual.actualReps==null)return null
                val stock=EquipmentLoadDomain.inspect(
                    verifiedStock,weight,first.unit)
                if(stock !is LoadInspection.Confirmed || !stock.exact ||
                    stock.basis!=verifiedStock.basis)return null
                row.add(checkNotNull(actual.actualReps)+rir-goal)
            }
            scores.add(row.toList())
        }
        val declines=(0 until n).filter{i->
            scores[0][i]>scores[1][i] &&
                scores[1][i]>scores[2][i]
        }.toSet()
        val signal=when {
            0 in declines && declines.size*2>=n ->
                RepeatedSessionFatigueSignal.BROAD_DECLINE
            0 !in declines && scores[0][0]<=scores[1][0] &&
                scores[1][0]<=scores[2][0] &&
                declines.count{it>0}>=2 ->
                RepeatedSessionFatigueSignal.LATER_SET_DECLINE
            else ->RepeatedSessionFatigueSignal.NO_REPEATED_DECLINE
        }
        return RepeatedSessionFatigueEvidence(signal,cell,
            recent.map{it.workoutId},declines,scores.map{it.toList()})
    }

    fun inspect(
        repBand: RepBand,
        expectedWorkingSets: Int,
        observations: List<WorkingSetFact>
    ): SessionFatigueEvidence {
        val facts=DoubleProgressionEvidenceDomain.assess(repBand,expectedWorkingSets,observations)
        val ordered=observations.sortedBy {it.workingOrdinal}
        val comparable=facts.completedWorkingSets==expectedWorkingSets &&
            expectedWorkingSets>=2

        val first=ordered.firstOrNull {it.outcome==SetOutcome.COMPLETED}
        val last=ordered.lastOrNull {it.outcome==SetOutcome.COMPLETED}
        val pattern=when {
            !comparable -> FatiguePattern.INCOMPLETE_OR_UNCOMPARABLE
            facts.mixedActualLoads -> FatiguePattern.MIXED_LOADS
            else -> {
                val changes=ordered.zipWithNext {a,b->b.actualReps!!-a.actualReps!!}
                when {
                    changes.all {it==0} -> FatiguePattern.REPS_STABLE
                    changes.all {it<=0} -> FatiguePattern.REPS_DECLINING
                    changes.all {it>=0} -> FatiguePattern.REPS_INCREASING
                    else -> FatiguePattern.NON_MONOTONIC_REPS
                }
            }
        }
        val details=comparable && !facts.mixedActualLoads
        val rirChange=if(details && first?.actualRir!=null && last?.actualRir!=null)
            last.actualRir-first.actualRir else null
        return SessionFatigueEvidence(
            pattern=pattern,
            repChangesBetweenSets=if(details)
                ordered.zipWithNext {a,b->b.actualReps!!-a.actualReps!!} else emptyList(),
            firstSetReps=first?.actualReps,
            lastSetReps=last?.actualReps,
            totalRepChange=if(details)last!!.actualReps!!-first!!.actualReps!! else null,
            targetFloorMisses=facts.belowFloorOrdinals,
            reportedRirChange=rirChange,
            actualRirReportedSets=facts.actualRirReportedOrdinals
        )
    }
}
