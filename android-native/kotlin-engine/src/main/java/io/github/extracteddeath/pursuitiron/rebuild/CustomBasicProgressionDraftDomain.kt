package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Translates an already reviewed basic progression into optional athlete-edited
 * week prescriptions. It is NOT a progression evaluator or a save authority.
 *
 * Only the set fields authorized by the upstream evaluator may change.
 * The caller must separately verify the current cycle, week, gym revision,
 * movement/attachment identity and the exact original week prescriptions,
 * then route the resulting draft through the normal full-cycle review.
 */
object CustomBasicProgressionDraftDomain {
    fun forWeekEdit(
        slotId: String,
        original: List<CustomAuthoredSet>,
        projection: CustomBasicHistoryResult
    ): List<CustomAuthoredSet>? {
        val rows = reviewedSets(slotId, original, projection) ?: return null
        return rows.takeIf { rows != original }
    }

    /** The preview and week editor consume the same checked rows. A valid hold
     * returns the original prescription, so only a real change creates an edit. */
    fun reviewedSets(
        slotId: String,
        original: List<CustomAuthoredSet>,
        projection: CustomBasicHistoryResult
    ): List<CustomAuthoredSet>? {
        if (slotId.isBlank() || original.size !in 1..20) return null
        val indexes = original.indices.filter { original[it].type == DraftSetType.WORKING }
        if (indexes.isEmpty()) return null
        val working = indexes.map { original[it] }

        return when (projection) {
            is CustomBasicHistoryResult.Reviewed -> {
                if (projection.slotId != slotId) return null
                val candidate = projection.candidate
                // The optional future-week edit must carry the same last
                // verified workout as the sole progression evaluator. Manual
                // logging has no historic evidence and must not impersonate it.
                if (candidate.strategy == BasicProgressionStrategy.MANUAL_LOGGING) {
                    if (projection.historyWorkoutId != null ||
                        projection.completionOrder != null ||
                        candidate.evidenceWorkoutId != null) return null
                } else if (projection.historyWorkoutId.isNullOrBlank() ||
                    projection.completionOrder?.let { it > 0 } != true ||
                    candidate.evidenceWorkoutId != projection.historyWorkoutId)
                    return null
                if (candidate.proposedWorkingLoads.size != working.size ||
                    candidate.proposedRepTargets?.size?.let { it != working.size } == true ||
                    working.any { it.load !is DraftSetLoad.Verified ||
                        it.amplifier != null && candidate.strategy != BasicProgressionStrategy.MANUAL_LOGGING })
                    return null

                val changedLoads = mutableSetOf<Int>()
                val changedReps = mutableSetOf<Int>()
                val edits = original.toMutableList()
                for ((workingIndex, setIndex) in indexes.withIndex()) {
                    val before = original[setIndex]
                    val oldLoad = before.load as DraftSetLoad.Verified
                    val proposedLoad = candidate.proposedWorkingLoads[workingIndex]
                    if (!KotlinSetNumberAuthorityDomain.validPrescribedLoad(oldLoad.value,oldLoad.basis) ||
                        !KotlinSetNumberAuthorityDomain.validPrescribedLoad(proposedLoad,oldLoad.basis))
                        return null
                    if (proposedLoad.compareTo(oldLoad.value) != 0)
                        changedLoads.add(workingIndex)

                    val proposedReps = candidate.proposedRepTargets?.get(workingIndex)
                        ?: before.exactRepTarget
                    if (proposedReps != null &&
                        proposedReps !in before.reps.minimum..before.reps.maximum) return null
                    if (proposedReps != before.exactRepTarget)
                        changedReps.add(workingIndex)
                    edits[setIndex] = before.copy(
                        load = if (workingIndex in changedLoads) oldLoad.copy(value = proposedLoad) else oldLoad,
                        exactRepTarget = proposedReps
                    )
                }
                if (changedLoads != candidate.increasedOrdinals ||
                    changedReps != candidate.changedRepOrdinals ||
                    !validPhysicalProof(projection, working, changedLoads) ||
                    !validWeightedClaim(working, candidate, changedLoads, changedReps)) null
                else edits
            }
            is CustomBasicHistoryResult.UnloadedReviewed -> {
                if (projection.slotId != slotId) return null
                val candidate = projection.candidate
                if (projection.historyWorkoutId.isBlank() ||
                    projection.completionOrder <= 0 ||
                    candidate.evidenceWorkoutId != projection.historyWorkoutId)
                    return null
                if (candidate.proposedExactReps.size != working.size ||
                    working.any { it.load != DraftSetLoad.ExplicitBodyweight ||
                        it.exactRepTarget == null || it.amplifier != null })
                    return null
                val advanced = mutableSetOf<Int>()
                val edits = original.toMutableList()
                for ((workingIndex, setIndex) in indexes.withIndex()) {
                    val before = original[setIndex]
                    val target = candidate.proposedExactReps[workingIndex]
                    if (target !in before.reps.minimum..before.reps.maximum ||
                        target != before.exactRepTarget &&
                        target != checkNotNull(before.exactRepTarget) + 1) return null
                    if (target != before.exactRepTarget) advanced.add(workingIndex)
                    edits[setIndex] = before.copy(exactRepTarget = target)
                }
                if (advanced != candidate.advancedOrdinals ||
                    candidate.atRepCeiling && (advanced.isNotEmpty() ||
                        working.any { it.exactRepTarget != it.reps.maximum })) null
                else edits
            }
            is CustomBasicHistoryResult.ManualBodyweight -> original.takeIf {
                projection.slotId == slotId && working.all { it.load == DraftSetLoad.ExplicitBodyweight }
            }
            is CustomBasicHistoryResult.Rejected -> null
        }
    }

    /**
     * Inspect the EXACT physical facts emitted by BasicProgressionPolicyDomain.
     * Do not call an inventory inspector a second time: its stock could have
     * changed between recommendation and review. An unchanged/rep-only row
     * has no approved load-change fact. A changed row must have exactly one
     * authenticated-scope physical approval from the shared future-number
     * gate. Owner-fenced acceptance must still independently replay history,
     * inventory, consent and source revision before changing any program.
     */
    private fun validPhysicalProof(
        projection: CustomBasicHistoryResult.Reviewed,
        working: List<CustomAuthoredSet>,
        changedLoads: Set<Int>
    ): Boolean {
        val candidate=projection.candidate
        val facts=candidate.approvedFutureLoadFacts
        if(facts.keys!=changedLoads)return false
        if(changedLoads.isEmpty())return true
        val scope=candidate.sourceScope ?:return false
        if(!scope.valid() || scope.blockId!=projection.blockId ||
            scope.trainingDayId!=projection.blockId+":d"+projection.weekday ||
            scope.machineInstanceId!=projection.machineInstanceId)return false
        for(index in changedLoads) {
            val approved=facts[index] ?:return false
            val original=working[index].load as? DraftSetLoad.Verified ?:return false
            val proposed=candidate.proposedWorkingLoads[index]
            val evidence=approved.physicalProof
            if(scope.unit!=original.unit || scope.basis!=original.basis ||
                approved.unit!=original.unit || approved.basis!=original.basis ||
                approved.issuedLoad.compareTo(original.value)!=0 ||
                approved.proposedLoad.compareTo(proposed)!=0 ||
                evidence.verifiedIssued.compareTo(approved.issuedLoad)!=0 ||
                evidence.verifiedCandidate.compareTo(approved.proposedLoad)!=0 ||
                evidence.absoluteChange.compareTo(
                    proposed.subtract(original.value).abs())!=0 ||
                approved.authorizedIncrease<=java.math.BigDecimal.ZERO ||
                evidence.absoluteChange>approved.authorizedIncrease ||
                !evidence.requiresIndependentPerformanceEvidence ||
                evidence.requiresPreviouslyDemonstratedLoad ||
                evidence.mayIssueWorkout || evidence.maySaveProgram ||
                approved.mayChangeIssuedWorkout || approved.maySaveFutureProgram ||
                approved.mayModifyHistory)return false
            when(approved.source) {
                KotlinFutureLoadSource.EARNED_NEXT_INCREMENT -> {
                    if(original.basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT ||
                        evidence.reason!=
                            KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_HIGHER_SETTING ||
                        proposed<=original.value ||
                        evidence.nextHigher?.compareTo(proposed)!=0)return false
                }
                KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT -> {
                    if(original.basis!=LoadBasis.ASSISTANCE_COUNTERWEIGHT ||
                        evidence.reason!=
                            KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_LOWER_ASSISTANCE ||
                        proposed>=original.value ||
                        proposed.signum()==0 ||
                        evidence.nextLower?.compareTo(proposed)!=0)return false
                }
                // A separately demonstrated lower return is COACH review,
                // never a basic progression award.
                KotlinFutureLoadSource.RETURN_TO_DEMONSTRATED_LOAD -> return false
            }
        }
        return true
    }

    /**
     * A candidate is numerical advice, never its own consent authority.
     * Defend the edit handoff against an incorrect/stale projection WITHOUT
     * recomputing the next physical stock step or progression decision.
     * That remains owned by BasicProgressionPolicyDomain.
     *
     * "Increased" assistance means a *smaller counterweight*, not a load
     * decrease on ordinary free weights. A ladder may reset reps to its
     * floor only as part of an atomic earned load step.
     */
    private fun validWeightedClaim(
        working: List<CustomAuthoredSet>,
        candidate: BasicProgressionResult.Candidate,
        changedLoads: Set<Int>,
        changedReps: Set<Int>
    ): Boolean {
        val strategy = candidate.strategy
        candidate.e1rmEvidence?.let{evidence->
            if(strategy!=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION ||
                evidence.estimatedOneRepMax<=java.math.BigDecimal.ZERO ||
                evidence.assumedGoalRirSetCount !in 0..working.size ||
                (changedLoads.isNotEmpty() && !evidence.everySetMeetsGoal))return false
        }
        val all = working.indices.toSet()
        val loads = working.map { it.load as DraftSetLoad.Verified }
        if (changedLoads.any { index ->
                val before = loads[index]
                val proposed = candidate.proposedWorkingLoads[index]
                val direction = proposed.compareTo(before.value)
                if (strategy == BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION)
                    before.basis != LoadBasis.ASSISTANCE_COUNTERWEIGHT || direction >= 0
                else before.basis == LoadBasis.ASSISTANCE_COUNTERWEIGHT || direction <= 0
            }) return false
        if (changedLoads.isNotEmpty() &&
            candidate.decision != BasicProgressionDecision.EARNED_INCREMENT) return false
        // The shared evaluator ALWAYS emits one independent decision for
        // every dynamic set, including unchanged sets. Missing vectors and
        // stray vectors on other methods must not become an editable week.
        if (strategy == BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION) {
            if (candidate.dynamicSetDecisions.size != working.size ||
                candidate.dynamicSetDecisions.withIndex().any { (index, decision) ->
                    decision !in setOf(BasicProgressionDecision.EARNED_INCREMENT,
                        BasicProgressionDecision.BUILD_REPS,
                        BasicProgressionDecision.MAXIMUM_LOAD_REACHED,
                        BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL) ||
                        (index in changedLoads) !=
                            (decision == BasicProgressionDecision.EARNED_INCREMENT)
                }) return false
        } else if (candidate.dynamicSetDecisions.isNotEmpty()) return false

        // Uniform strategies cannot turn an atomic earned progression into
        // a single changed working row; the real evaluator always advances
        // every comparable set or leaves the entire exercise unchanged.
        val uniform = strategy in setOf(
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION,
            BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION)
        if (loads.any { it.unit != loads.first().unit || it.basis != loads.first().basis }) return false
        if (uniform && (loads.any { it.value.compareTo(loads.first().value) != 0 } ||
                candidate.proposedWorkingLoads.any { it.compareTo(candidate.proposedWorkingLoads.first()) != 0 }))
            return false

        if (changedLoads.isEmpty() && changedReps.isEmpty()) {
            val repMethod = strategy == BasicProgressionStrategy.REP_TARGET_PROGRESSION ||
                strategy == BasicProgressionStrategy.REP_LADDER_PROGRESSION
            if (repMethod != (candidate.proposedRepTargets != null)) return false
            return when (candidate.decision) {
                BasicProgressionDecision.MANUAL_REPEAT -> strategy == BasicProgressionStrategy.MANUAL_LOGGING
                BasicProgressionDecision.BUILD_REPS -> strategy != BasicProgressionStrategy.MANUAL_LOGGING
                BasicProgressionDecision.MAXIMUM_LOAD_REACHED,
                BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL ->
                    strategy != BasicProgressionStrategy.MANUAL_LOGGING && strategy != BasicProgressionStrategy.REP_TARGET_PROGRESSION
                BasicProgressionDecision.REP_CEILING_REACHED ->
                    strategy == BasicProgressionStrategy.REP_TARGET_PROGRESSION &&
                        working.all { it.exactRepTarget == it.reps.maximum }
                else -> false
            }
        }

        fun oneRepAtATime(): Boolean =
            candidate.proposedRepTargets != null && working.indices.all { index ->
                val current = working[index].exactRepTarget ?: return@all false
                val next = candidate.proposedRepTargets[index]
                next == current || next == current + 1
            }

        return when (strategy) {
            BasicProgressionStrategy.MANUAL_LOGGING -> false
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION ->
                changedLoads == all && changedReps.isEmpty() &&
                    candidate.proposedRepTargets == null
            BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION ->
                loads.all { it.basis == LoadBasis.ASSISTANCE_COUNTERWEIGHT } &&
                    changedLoads == all && changedReps.isEmpty() &&
                    candidate.proposedRepTargets == null
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION ->
                changedLoads.isNotEmpty() && changedReps.isEmpty() &&
                    candidate.proposedRepTargets == null
            BasicProgressionStrategy.REP_TARGET_PROGRESSION ->
                changedLoads.isEmpty() && changedReps.isNotEmpty() &&
                    candidate.decision == BasicProgressionDecision.EARNED_REP_TARGET &&
                    oneRepAtATime()
            BasicProgressionStrategy.REP_LADDER_PROGRESSION -> {
                if (changedLoads.isNotEmpty()) {
                    changedLoads == all &&
                        candidate.proposedRepTargets != null &&
                        working.indices.all { index ->
                            working[index].exactRepTarget != null &&
                                candidate.proposedRepTargets[index] == working[index].reps.minimum
                        }
                } else {
                    changedReps.isNotEmpty() &&
                        candidate.decision == BasicProgressionDecision.EARNED_REP_TARGET &&
                        oneRepAtATime()
                }
            }
        }
    }
}
