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
        if (slotId.isBlank() || original.size !in 1..20) return null
        val indexes = original.indices.filter { original[it].type == DraftSetType.WORKING }
        if (indexes.isEmpty()) return null
        val working = indexes.map { original[it] }

        return when (projection) {
            is CustomBasicHistoryResult.Reviewed -> {
                if (projection.slotId != slotId) return null
                val candidate = projection.candidate
                if (candidate.proposedWorkingLoads.size != working.size ||
                    candidate.proposedRepTargets?.size?.let { it != working.size } == true ||
                    working.any { it.load !is DraftSetLoad.Verified || it.amplifier != null })
                    return null

                val changedLoads = mutableSetOf<Int>()
                val changedReps = mutableSetOf<Int>()
                val edits = original.toMutableList()
                for ((workingIndex, setIndex) in indexes.withIndex()) {
                    val before = original[setIndex]
                    val oldLoad = before.load as DraftSetLoad.Verified
                    val proposedLoad = candidate.proposedWorkingLoads[workingIndex]
                    if (!KotlinSetNumberAuthorityDomain.validPrescribedLoad(proposedLoad,oldLoad.basis))
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
                        load = oldLoad.copy(value = proposedLoad),
                        exactRepTarget = proposedReps
                    )
                }
                if (changedLoads != candidate.increasedOrdinals ||
                    changedReps != candidate.changedRepOrdinals ||
                    !validWeightedClaim(working, candidate, changedLoads, changedReps)) null
                else edits
            }
            is CustomBasicHistoryResult.UnloadedReviewed -> {
                if (projection.slotId != slotId) return null
                val candidate = projection.candidate
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
                if (advanced != candidate.advancedOrdinals || advanced.isEmpty()) null
                else edits
            }
            else -> null
        }
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
        if (changedLoads.isEmpty() && changedReps.isEmpty()) return false
        val strategy = candidate.strategy
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
        if (candidate.dynamicSetDecisions.isNotEmpty() &&
            (strategy != BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION ||
                candidate.dynamicSetDecisions.size != working.size ||
                changedLoads.any {
                    candidate.dynamicSetDecisions[it] != BasicProgressionDecision.EARNED_INCREMENT
                })) return false

        // Uniform strategies cannot turn an atomic earned progression into
        // a single changed working row; the real evaluator always advances
        // every comparable set or leaves the entire exercise unchanged.
        val uniform = strategy in setOf(
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION,
            BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION)
        if (uniform && loads.any { it.unit != loads.first().unit ||
                it.basis != loads.first().basis ||
                it.value.compareTo(loads.first().value) != 0 }) return false

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
