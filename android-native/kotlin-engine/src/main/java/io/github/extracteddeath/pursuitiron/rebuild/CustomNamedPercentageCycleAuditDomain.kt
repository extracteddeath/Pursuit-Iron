package io.github.extracteddeath.pursuitiron.rebuild

/** Stable identity of one named-protocol week override. */
data class CustomNamedPercentageWeekKey(
    val blockId:String,val weekInBlock:Int,val weekday:Int,val slotId:String
)

/**
 * Recipe evidence supplied by the eventual owner-fenced reviewer, not by a
 * program editor. In particular the independently confirmed reference and
 * actual physical stock must not be reconstructed from the candidate's own
 * set metadata. This pure type alone does not establish owner approval.
 */
data class CustomNamedPercentageWeekEvidence(
    val key:CustomNamedPercentageWeekKey,
    val request:NamedPercentageRequest
)

/** Independently rederived initial tier evidence. This is NOT a signed
 * receipt: the native owner-fenced reviewer must authenticate it. */
data class CustomTieredInitialWeekEvidence(
    val key:CustomNamedPercentageWeekKey,val request:TieredLinearRequest
)

enum class CustomNamedPercentageCycleRefusal {
    INVALID_SOURCE, UNREVIEWED_BASE_SET, DUPLICATE_WEEK, MISSING_OR_EXTRA_EVIDENCE,
    TIER_HISTORY_REQUIRED, RECIPE_REFUSED, RECIPE_MISMATCH
}

sealed interface CustomNamedPercentageCycleAudit {
    data class Refused(val reason:CustomNamedPercentageCycleRefusal,
        val key:CustomNamedPercentageWeekKey?=null):CustomNamedPercentageCycleAudit
    data class Verified(val reviewedWeeks:Int):CustomNamedPercentageCycleAudit {
        val mayIssueWorkout:Boolean get()=false
        val maySaveProgram:Boolean get()=false
        val maySignoff:Boolean get()=false
    }
}

/**
 * Audit EVERY fixed named override in a candidate, using the existing
 * revisioned draft builder and its canonical recipe/physical-stock policy.
 * Never approve a self-consistent but forged set merely because it is
 * structurally valid, and never validate only the currently selected day.
 * The fixed-only entry refuses GZCLP. The combined entry accepts only
 * independently reviewed first-tier evidence; later tier decisions remain
 * blocked until their corrected-history approval path exists.
 *
 * This does not replace the full program/volume/timing/consent audit, nor
 * lift the ordinary custom-cycle named-protocol issuance prohibition.
 */
object CustomNamedPercentageCycleAuditDomain {
    fun review(cycle:CustomTrainingCycle,stamp:CustomWorkoutSourceStamp,
        evidence:List<CustomNamedPercentageWeekEvidence>):CustomNamedPercentageCycleAudit =
        reviewWithInitialTiers(cycle,stamp,evidence,emptyList())

    fun reviewWithInitialTiers(cycle:CustomTrainingCycle,stamp:CustomWorkoutSourceStamp,
        evidence:List<CustomNamedPercentageWeekEvidence>,
        initialTiers:List<CustomTieredInitialWeekEvidence>):CustomNamedPercentageCycleAudit {
        fun deny(reason:CustomNamedPercentageCycleRefusal,
            key:CustomNamedPercentageWeekKey?=null)=CustomNamedPercentageCycleAudit.Refused(reason,key)
        if(cycle.cycleId!=stamp.cycleId || cycle.athleteId!=stamp.athleteId ||
            cycle.revision!=stamp.cycleRevision || cycle.revision !in 1..9_007_199_254_740_990L ||
            stamp.gymId.isBlank() || stamp.athleteProfileRevision.isBlank() ||
            stamp.gymCatalogSourceRevision !in 1..9_007_199_254_740_991L ||
            cycle.blocks.map{it.blockId}.distinct().size!=cycle.blocks.size)
            return deny(CustomNamedPercentageCycleRefusal.INVALID_SOURCE)
        if(cycle.blocks.any{block->block.days.any{day->
            day.slots.any{slot->slot.sets.any{it.percentageIntent!=null}}
        }})return deny(CustomNamedPercentageCycleRefusal.UNREVIEWED_BASE_SET)
        val all=cycle.weekOverrides.flatMap{(blockId,overrides)->
            overrides.map{override->CustomNamedPercentageWeekKey(blockId,override.weekInBlock,
                override.weekday,override.slotId) to override}
        }
        if(all.size>3000 || all.map{it.first}.distinct().size!=all.size ||
            evidence.size>3000 || initialTiers.size>3000 ||
            evidence.size+initialTiers.size>3000)
            return deny(CustomNamedPercentageCycleRefusal.DUPLICATE_WEEK)
        val keys=evidence.map{it.key}+initialTiers.map{it.key}
        if(keys.distinct().size!=keys.size ||
            initialTiers.map{Triple(it.request.scope,it.request.tier,it.key.slotId)}
                .distinct().size!=initialTiers.size)
            return deny(CustomNamedPercentageCycleRefusal.DUPLICATE_WEEK)
        val named=all.filter{(_,override)->override.sets.any{it.percentageIntent!=null}}
        val byKey=evidence.associateBy{it.key}
        val tiersByKey=initialTiers.associateBy{it.key}
        if(named.map{it.first}.toSet()!=keys.toSet())
            return deny(CustomNamedPercentageCycleRefusal.MISSING_OR_EXTRA_EVIDENCE)
        for((key,override) in named) {
            val fixed=byKey[key]?.request
            val tier=tiersByKey[key]?.request
            val isTiered=override.sets.any{it.percentageIntent?.scheme==NamedPercentageScheme.GZCLP}
            if(isTiered!=(tier!=null) || fixed?.scheme==NamedPercentageScheme.GZCLP ||
                (tier!=null && (tier.initialState.stage!=0 || tier.completed.isNotEmpty() ||
                    tier.recovery || !tier.initialStateConfirmed || tier.sourceSlotId!=key.slotId)))
                return deny(CustomNamedPercentageCycleRefusal.TIER_HISTORY_REQUIRED,key)
            // Remove ONLY the selected override. The draft domain deliberately
            // refuses overwriting a protected week; this local copy is used
            // solely to reconstruct that week's exact expected bytes.
            val retained=cycle.weekOverrides.getValue(key.blockId).filterNot{it==override}
            val without=cycle.copy(weekOverrides=if(retained.isEmpty())
                cycle.weekOverrides-key.blockId else cycle.weekOverrides+(key.blockId to retained))
            val source=CustomPercentageDraftSource(
                without,stamp,key.blockId,key.weekInBlock,key.weekday,key.slotId)
            val derived=(if(tier!=null)CustomPercentageDraftDomain.reviewTiered(source,tier)
                else CustomPercentageDraftDomain.review(source,checkNotNull(fixed)))
                as? CustomPercentageDraftResult.Proposed
                ?:return deny(CustomNamedPercentageCycleRefusal.RECIPE_REFUSED,key)
            val expected=derived.proposal.draft.weekOverrides[key.blockId].orEmpty()
                .singleOrNull{it.weekInBlock==key.weekInBlock && it.weekday==key.weekday &&
                    it.slotId==key.slotId}
                ?:return deny(CustomNamedPercentageCycleRefusal.RECIPE_MISMATCH,key)
            if(expected!=override)
                return deny(CustomNamedPercentageCycleRefusal.RECIPE_MISMATCH,key)
        }
        return CustomNamedPercentageCycleAudit.Verified(named.size)
    }
}
