package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Read-only join between an ACTUAL verified future-program number trace and
 * factual completed workout history. This domain does not attest storage:
 * the native owner must invoke it only with the full manifest/correction
 * replay from NativeKotlinGlobalHistoryReadCoordinator.
 *
 * A history signal is not a new prescription. In particular no older Upper,
 * other weekday, changed exercise role, Machine A/B, unreported effort or
 * completed-at-a-different-load observation may justify a future number.
 */
enum class KotlinFuturePerformanceCellStatus {
    NO_EXACT_HISTORY,
    NON_STRAIGHT_FUTURE_PROTOCOL,
    FUTURE_PRESCRIPTION_DIFFERS,
    HISTORY_GUARDED,
    REP_CEILING_REVIEW_ONLY
}

data class KotlinFuturePerformanceCell(
    val weekday:Int,
    val dayIndex:Int,
    val slotId:String,
    val exerciseId:String,
    val physicalMachineId:String?,
    val status:KotlinFuturePerformanceCellStatus,
    val historicalGate:KotlinProgramPerformanceGate?,
    val evaluatedWorkoutIds:List<String>
) {
    val canChangeLoad:Boolean get()=false
    val canChangeReps:Boolean get()=false
    val canChangeSetCount:Boolean get()=false
}

data class KotlinFuturePerformanceLink(
    val generatedTraceDigest:String,
    val ownerSourceReplayDigest:String,
    val cells:List<KotlinFuturePerformanceCell>
) {
    val canIssueWorkout:Boolean get()=false
    val canApplyAutomatically:Boolean get()=false
}

enum class KotlinFuturePerformanceLinkRefusal {
    INVALID_FUTURE_TRACE,
    SOURCE_EPOCH_MISMATCH,
    INVALID_HISTORY_EVIDENCE
}

sealed interface KotlinFuturePerformanceLinkResult {
    data class Reviewed(val link:KotlinFuturePerformanceLink):
        KotlinFuturePerformanceLinkResult
    data class Rejected(val reason:KotlinFuturePerformanceLinkRefusal):
        KotlinFuturePerformanceLinkResult
}

object KotlinFutureProgramPerformanceLinkDomain {
    fun review(
        request:GeneratedAdaptiveBlockRequest,
        future:GeneratedAdaptiveBlockResult,
        ledger:KotlinProgramHistoryReadout,
        performance:KotlinProgramPerformanceReport,
        /** Owner-manifest completion order, never guessed from workout IDs. */
        completionOrderByWorkout:Map<String,Long>
    ):KotlinFuturePerformanceLinkResult {
        fun reject(reason:KotlinFuturePerformanceLinkRefusal)=
            KotlinFuturePerformanceLinkResult.Rejected(reason)
        val trace=future.upstreamNumberTrace
            ?:return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
        val candidate=future.proposedOption?.proposedCycle
            ?:return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
        val composed=future.generatedWeek?.candidate?.catalogReview?.composedRequest
            ?:return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
        val block=candidate.blocks.singleOrNull{it.blockId==trace.targetBlockId}
            ?:return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
        val athlete=request.program.setup.reviewed
            ?:return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
        if(future.status!=GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW ||
            trace.schemaVersion!=2 || trace.exactRequestDigest.length!=64 ||
            trace.athleteId!=athlete.athleteProfileId ||
            trace.sourceCycleId!=request.currentCycle.cycleId ||
            trace.sourceCycleRevision!=request.currentCycle.revision ||
            trace.candidateCycleRevision!=candidate.revision ||
            trace.targetBlockId!=request.targetBlockId ||
            trace.goal!=athlete.goal ||
            trace.phase!=block.phase || block.phase!=request.program.phase ||
            candidate.goal!=athlete.goal ||
            candidate.cycleId!=request.currentCycle.cycleId ||
            candidate.athleteId!=athlete.athleteProfileId ||
            candidate.revision!=request.currentCycle.revision+1 ||
            composed.days.size!=block.days.size ||
            composed.days.map{it.calendarDay}.toSet()!=
                block.days.map{it.weekday}.toSet())
            return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)

        // Never infer that an older program revision remains the current
        // training baseline. An eventual optimizer must separately audit
        // complete scheduled-week adherence before any number is changed.
        if(ledger.schemaVersion!=2 ||
            ledger.athleteId!=trace.athleteId ||
            ledger.cycleId!=trace.sourceCycleId ||
            ledger.source.athleteId!=trace.athleteId ||
            ledger.source.programId!=trace.sourceCycleId ||
            ledger.source.programRevision!=trace.sourceCycleRevision ||
            ledger.source.gymId!=athlete.gym.gymId)
            return reject(KotlinFuturePerformanceLinkRefusal.SOURCE_EPOCH_MISMATCH)
        // A digest is not an authentication token. Re-run the ONE
        // historical performance authority on the source ledger and exact
        // owner completion ordinals. Never trust caller-provided "favorable"
        // thread gates, last visits or a copied matching digest.
        val independentlyReviewed=runCatching {
            KotlinProgramPerformanceEvidenceDomain.review(
                ledger,completionOrderByWorkout)
        }.getOrNull()
        val canonical=(independentlyReviewed as?
            KotlinProgramPerformanceResult.Reviewed)?.report
            ?:return reject(
                KotlinFuturePerformanceLinkRefusal.INVALID_HISTORY_EVIDENCE)
        if(performance!=canonical ||
            performance.schemaVersion!=2 ||
            performance.sourceReplayDigest!=ledger.exactReplayDigest ||
            ledger.exactReplayDigest.length!=64)
            return reject(KotlinFuturePerformanceLinkRefusal.INVALID_HISTORY_EVIDENCE)

        val bySlot=trace.sets.groupBy{it.weekday to it.slotId}
        val proposedSlots=block.days.flatMap{day->
            day.slots.map{day.weekday to it.slotId}
        }
        if(proposedSlots.size!=proposedSlots.distinct().size ||
            proposedSlots.toSet()!=bySlot.keys)
            return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
        val output=mutableListOf<KotlinFuturePerformanceCell>()
        for(day in block.days.sortedBy{it.weekday}) {
            val programDay=composed.days.indexOfFirst{
                it.calendarDay==day.weekday
            }
            if(programDay !in composed.days.indices ||
                composed.sessionIntents.singleOrNull{
                    it.dayIndex==programDay
                }?.purpose!=day.purpose)
                return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
            for(slot in day.slots) {
                val sets=bySlot[day.weekday to slot.slotId]
                    ?.sortedBy{it.ordinal}
                    ?:return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
                if(sets.size!=slot.sets.size || sets.isEmpty() ||
                    sets.withIndex().any{(idx,number)->
                        val authored=slot.sets[idx]
                        number.ordinal!=idx+1 ||
                            number.weekday!=day.weekday ||
                            number.dayIndex!=programDay ||
                            number.slotId!=slot.slotId ||
                            number.exerciseId!=slot.exerciseId ||
                            number.role!=slot.role ||
                            number.purpose!=day.purpose ||
                            number.type!=authored.type ||
                            !KotlinSetNumberAuthorityDomain.sameIssuedTarget(
                                number.load,authored.load) ||
                            number.reps!=authored.reps ||
                            number.targetRir!=authored.targetRir ||
                            number.activeSeconds!=authored.activeSeconds ||
                            number.restSeconds!=authored.restSeconds
                    })
                    return reject(KotlinFuturePerformanceLinkRefusal.INVALID_FUTURE_TRACE)
                val working=sets.filter{it.type==DraftSetType.WORKING}
                val firstLoad=working.firstOrNull()?.load
                    as? DraftSetLoad.Verified
                val matching=performance.threads.singleOrNull{thread->
                    thread.key.dayIndex==programDay &&
                        thread.key.calendarDay==day.weekday &&
                        thread.key.purpose==day.purpose &&
                        thread.key.role==slot.role &&
                        thread.key.exerciseId==slot.exerciseId &&
                        thread.key.physicalMachineId==slot.machineInstanceId &&
                        thread.key.loadUnit==firstLoad?.unit &&
                        thread.key.loadBasis==firstLoad?.basis
                }
                val latest=matching?.visits?.lastOrNull()
                val samePrescription=latest!=null &&
                    latest.workingSets==working.size &&
                    latest.issuedRepBands==working.map{it.reps} &&
                    latest.targetRir==working.map{it.targetRir} &&
                    latest.issuedLoads.size==working.size &&
                    latest.issuedLoads.zip(working.map{it.load}).all{(a,b)->
                        KotlinSetNumberAuthorityDomain.sameIssuedTarget(a,b)
                    }
                val status=when {
                    slot.sets.any{it.amplifier!=null} ->
                        KotlinFuturePerformanceCellStatus.NON_STRAIGHT_FUTURE_PROTOCOL
                    matching==null || latest==null ->
                        KotlinFuturePerformanceCellStatus.NO_EXACT_HISTORY
                    !samePrescription ->
                        KotlinFuturePerformanceCellStatus.FUTURE_PRESCRIPTION_DIFFERS
                    matching.recentGate==
                        KotlinProgramPerformanceGate.REP_CEILING_FOR_REVIEW_ONLY ->
                        KotlinFuturePerformanceCellStatus.REP_CEILING_REVIEW_ONLY
                    else ->KotlinFuturePerformanceCellStatus.HISTORY_GUARDED
                }
                output+=KotlinFuturePerformanceCell(
                    day.weekday,programDay,slot.slotId,slot.exerciseId,
                    slot.machineInstanceId,status,matching?.recentGate,
                    Collections.unmodifiableList(
                        matching?.evaluatedWorkoutIds.orEmpty().toList()))
            }
        }
        return KotlinFuturePerformanceLinkResult.Reviewed(
            KotlinFuturePerformanceLink(trace.exactRequestDigest,
                ledger.exactReplayDigest,
                Collections.unmodifiableList(output)))
    }
}
