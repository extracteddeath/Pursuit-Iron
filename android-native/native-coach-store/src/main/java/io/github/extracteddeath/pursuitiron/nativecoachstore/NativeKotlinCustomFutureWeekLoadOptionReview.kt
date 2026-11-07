package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Collections

/**
 * A native-owned, read-only bridge from ACTUAL, corrected custom workout
 * receipts to one future custom-cycle slot. The existing native next-workout
 * reviewer remains the ONLY progression calculator and source of history.
 *
 * Preview is not athlete acceptance: it cannot save drafts, approve a new
 * cycle, issue workouts or change the already-issued historical journals.
 */
enum class NativeFutureWeekLoadRefusal {
    INVALID_SELECTION, NOT_AUTHORIZED, SETTINGS_UNAVAILABLE,
    ASSISTANCE_DISABLED, SOURCE_UNAVAILABLE, DRAFT_UNAVAILABLE,
    SOURCE_CHANGED, NOT_CURRENT_TRAINING_BLOCK, CUSTOM_WEEK_PROTECTED,
    PERMISSION_REQUIRED, HISTORY_UNVERIFIED, NOT_EARNED,
    WORKOUT_ALREADY_PREPARED_OR_ISSUED, PRESCRIPTION_CHANGED,
    EQUIPMENT_UNVERIFIED, FULL_CYCLE_AUDIT_FAILED, STORAGE_UNAVAILABLE
}

sealed interface NativeFutureWeekLoadOptionResult {
    data class Ready(
        val option:AdaptiveCycleOption,
        val comparison:AdaptiveCycleComparison,
        val historyRevision:Long,
        val draftRevision:Long,
        val sourceRevision:Long,
        val settingsRevision:Long,
        val permissionRevision:Long,
        val comparedWorkoutIds:List<String>,
        val affectedAbsoluteWeek:Int,
        val affectedWeekday:Int,
        val affectedSlotId:String,
        val previousLoad:BigDecimal,
        val candidateLoad:BigDecimal
    ):NativeFutureWeekLoadOptionResult {
        val mayApplyAutomatically:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
        val mayEditHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativeFutureWeekLoadRefusal):
        NativeFutureWeekLoadOptionResult
}

class NativeKotlinCustomFutureWeekLoadOptionReview(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val reviewer:NativeKotlinCustomNextWorkoutReview,
    private val activation:NativeKotlinFutureActivationCommitTransaction,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val permissions:NativeKotlinAdaptiveCyclePermissionFileStore,
    private val settings:NativeKotlinCoachAssistanceSettingsStore,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val inventory:NativeKotlinCustomOutstandingWorkoutInventory
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun same(a:BigDecimal,b:BigDecimal)=a.compareTo(b)==0
    private fun norm(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())

    fun review(
        ownerKey:String,coaching:CustomCoachAuditInputs,
        absoluteWeek:Int,weekday:Int,slotId:String,
        readiness:CoachReadiness,movementRestricted:Boolean,
        maxAuthorizedIncrease:BigDecimal
    ):NativeFutureWeekLoadOptionResult {
        fun no(reason:NativeFutureWeekLoadRefusal)=
            NativeFutureWeekLoadOptionResult.Rejected(reason)
        if(!id.matches(ownerKey) || !id.matches(slotId) ||
            absoluteWeek !in 1..104 || weekday !in 0..6 ||
            maxAuthorizedIncrease<=BigDecimal.ZERO)
            return no(NativeFutureWeekLoadRefusal.INVALID_SELECTION)
        return runCatching {fence.withOwnerTransaction(ownerKey) {
            val owner=identity.signedInIdentity()?.takeIf {
                it.ownerKey==ownerKey && id.matches(it.athleteId)
            } ?:return@withOwnerTransaction no(
                NativeFutureWeekLoadRefusal.NOT_AUTHORIZED)
            val currentSettings=(settings.read(ownerKey)
                as? NativeCoachSettingsRead.Current)?.settings
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.SETTINGS_UNAVAILABLE)
            if(currentSettings.athleteId!=owner.athleteId)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.SETTINGS_UNAVAILABLE)
            if(!currentSettings.policy.offersProgression)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.ASSISTANCE_DISABLED)

            val activated=(activation.read(ownerKey)
                as? NativeFutureCommitReview.Applied)?.context
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.NOT_CURRENT_TRAINING_BLOCK)
            val next=(activation.previewActivatedWorkout(ownerKey,
                coaching,absoluteWeek,weekday)
                as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.NOT_CURRENT_TRAINING_BLOCK)
            if(next.blockId!=activated.blockId ||
                next.stamp.cycleId!=activated.cycleId ||
                next.stamp.athleteId!=owner.athleteId ||
                next.blockPurpose!=CustomBlockPurpose.TRAINING ||
                next.weekInBlock<1)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.NOT_CURRENT_TRAINING_BLOCK)

            val original=(drafts.read(ownerKey,activated.cycleId)
                as? NativeCustomDraftReadResult.Found)?.draft
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.DRAFT_UNAVAILABLE)
            val source=(sources.read(ownerKey)
                as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.SOURCE_UNAVAILABLE)
            if(original.athleteId!=owner.athleteId ||
                original.revision!=next.stamp.cycleRevision ||
                source.athleteId!=owner.athleteId ||
                source.revision!=activated.sourceRevision ||
                next.stamp.gymCatalogSourceRevision!=source.revision ||
                source.setup.reviewed?.gym?.gymId!=next.stamp.gymId)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.SOURCE_CHANGED)
            val block=original.blocks.singleOrNull{
                it.blockId==next.blockId
            } ?:return@withOwnerTransaction no(
                NativeFutureWeekLoadRefusal.NOT_CURRENT_TRAINING_BLOCK)
            val day=block.days.singleOrNull{it.weekday==weekday}
            val slot=day?.slots?.singleOrNull{it.slotId==slotId}
            val issuedSlot=next.slots.singleOrNull{it.slotId==slotId}
            if(block.purpose!=CustomBlockPurpose.TRAINING ||
                next.weekInBlock !in 1..block.weeks ||
                day?.purpose!=next.sessionPurpose ||
                slot==null || issuedSlot==null ||
                slot.exerciseId!=issuedSlot.exerciseId ||
                block.phase!=next.phase ||
                block.split!=next.split)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.NOT_CURRENT_TRAINING_BLOCK)
            if(original.weekOverrides[block.blockId].orEmpty().any {
                it.weekInBlock==next.weekInBlock &&
                    it.weekday==weekday && it.slotId==slotId
            })return@withOwnerTransaction no(
                NativeFutureWeekLoadRefusal.CUSTOM_WEEK_PROTECTED)

            val permission=(permissions.read(ownerKey,original.cycleId)
                as? NativeAdaptivePermissionRead.Found)?.receipt
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.PERMISSION_REQUIRED)
            if(permission.athleteId!=owner.athleteId ||
                !permission.allowedKinds.containsAll(setOf(
                    CustomCycleChangeKind.WEEK_OVERRIDES,
                    CustomCycleChangeKind.LOADS)))
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.PERMISSION_REQUIRED)

            // The reviewer replays ALL corrected mixed-ledger entries and
            // validates the last two actual appearances on this exact day.
            val verified=(reviewer.review(ownerKey,coaching,absoluteWeek,
                weekday,slotId,readiness,movementRestricted,
                maxAuthorizedIncrease)
                as? NativeCustomNextCoachResult.Reviewed)
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.HISTORY_UNVERIFIED)
            val advice=verified.review
            val physical=verified.verifiedStock
            if(advice.status!=CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW ||
                advice.loadReviewCandidate==null ||
                advice.observedStraightSetLoad==null ||
                advice.comparedWorkoutIds.size!=2)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.NOT_EARNED)
            val ledger=(history.read(ownerKey,owner.athleteId)
                as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.HISTORY_UNVERIFIED)
            if(ledger.manifestRevision!=verified.historyRevision ||
                identity.signedInIdentity()!=owner)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.HISTORY_UNVERIFIED)

            // A prepared but unfinished workout is already owned by its
            // immutable issuance contract. Do not put a new target into a
            // future week it might later issue, even if its history is empty.
            val completed=ledger.completions.filterIsInstance<
                NativeKotlinMixedHistoryCompletion.Custom>()
                .map{it.manifest.workoutId}.toSet()
            val owned=inventory.inspect(ownerKey,owner.athleteId,completed)
                as? NativeCustomInventoryReview.Reviewed
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.HISTORY_UNVERIFIED)
            if(owned.sessions.any{
                it.cycleId==original.cycleId &&
                    it.absoluteWeek==absoluteWeek && it.weekday==weekday
            })return@withOwnerTransaction no(
                NativeFutureWeekLoadRefusal.WORKOUT_ALREADY_PREPARED_OR_ISSUED)

            val authored=slot.sets
            val loaded=authored.mapNotNull {it.load as? DraftSetLoad.Verified}
            if(authored.isEmpty() || authored.size>32 ||
                authored.any {it.type!=DraftSetType.WORKING ||
                    it.amplifier!=null || it.targetRir==null ||
                    it.reps!=authored.first().reps ||
                    it.targetRir!=authored.first().targetRir} ||
                loaded.size!=authored.size ||
                issuedSlot.sets.map{it.prescription}!=authored ||
                loaded.any{
                    physical==null || it.unit!=physical.unit ||
                        it.basis!=physical.basis ||
                        !same(it.value,loaded.first().value)
                } || !same(loaded.first().value,
                    advice.observedStraightSetLoad
                        ?:return@withOwnerTransaction no(
                            NativeFutureWeekLoadRefusal.NOT_EARNED)))
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.PRESCRIPTION_CHANGED)
            val current=loaded.first().value
            // Kotlin cannot smart-cast properties on cross-module DTOs.
            // Local non-null bindings also prevent uncertain evidence from
            // entering the physical stock check.
            val candidate=advice.loadReviewCandidate
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.NOT_EARNED)
            if(physical==null || candidate<=current ||
                candidate.subtract(current)>maxAuthorizedIncrease)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.EQUIPMENT_UNVERIFIED)
            val inspected=EquipmentLoadDomain.inspect(
                physical,current,physical.unit)
            val nextPhysical=EquipmentLoadDomain.inspect(
                physical,candidate,physical.unit)
            val nextAvailable=(inspected as? LoadInspection.Confirmed)
                ?.nextAbove
            if(inspected !is LoadInspection.Confirmed ||
                !inspected.exact || nextAvailable==null ||
                !same(nextAvailable,candidate) ||
                nextPhysical !is LoadInspection.Confirmed ||
                !nextPhysical.exact)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.EQUIPMENT_UNVERIFIED)
            val changed=authored.map {set->
                val load=set.load as DraftSetLoad.Verified
                set.copy(load=load.copy(value=norm(candidate)))
            }
            val replacement=CustomWeekSlotOverride(
                next.weekInBlock,weekday,slotId,
                Collections.unmodifiableList(changed))
            val option=AdaptiveCycleOption(
                "history_earned_week_${absoluteWeek}_${weekday}_${slotId}",
                original.copy(
                    revision=original.revision+1,
                    weekOverrides=Collections.unmodifiableMap(
                        original.weekOverrides+(block.blockId to
                            Collections.unmodifiableList(
                                original.weekOverrides[block.blockId].orEmpty()+
                                    replacement)))))
            val allowed=CustomCycleCoachPermissions(
                adaptLoads=true,adaptWeekOverrides=true)
            // The currently activated block has a NEW athlete/gym/split
            // context, while completed earlier blocks must retain their
            // original pre-activation context. Only the activation owner can
            // reconstruct and authenticate both contexts together.
            val compared=activation.reviewActivatedOption(
                ownerKey,coaching,option,allowed)
                ?:return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.FULL_CYCLE_AUDIT_FAILED)
            val assessed=compared.evaluated.singleOrNull()
            if(assessed?.status!=
                    AdaptiveCycleOptionStatus.PROSPECTIVE_REVIEW_REQUIRED ||
                assessed.coachingAudit?.status!=
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                assessed.transition?.status!=
                    CustomCycleTransitionStatus.READY_FOR_ATHLETE_REVIEW)
                return@withOwnerTransaction no(
                    NativeFutureWeekLoadRefusal.FULL_CYCLE_AUDIT_FAILED)
            NativeFutureWeekLoadOptionResult.Ready(
                option,compared,ledger.manifestRevision,original.revision,
                source.revision,currentSettings.revision,permission.revision,
                Collections.unmodifiableList(advice.comparedWorkoutIds),
                absoluteWeek,weekday,slotId,norm(current),norm(candidate))
        }}.getOrElse{no(NativeFutureWeekLoadRefusal.STORAGE_UNAVAILABLE)}
    }
}
