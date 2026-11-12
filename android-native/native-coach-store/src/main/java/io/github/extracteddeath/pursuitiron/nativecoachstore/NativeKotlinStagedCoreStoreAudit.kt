package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

/**
 * Read-only, owner-scoped CORE recovery audit for an already decrypted,
 * isolated Kotlin snapshot. It builds the same native journal, consent,
 * program and History store readers as the app; it does NOT decode arbitrary
 * bytes as believable training results.
 *
 * This is a milestone toward a full beta restore verifier, NOT the final
 * release gate. Advanced future-block approvals, workout-machine-choice
 * orphans, every retained legacy format and pinned-exercise notes,
 * advanced approvals/signoffs still need explicit inventory coverage before
 * UI import is permitted. Machine choices and coaching opt-out settings now
 * use their existing native readers with complete physical file scans.
 * No workout, approval, note or training file is ever mutated by this API.
 */
enum class NativeKotlinStagedCoreRefusal {
    INVALID_DIRECTORY_OR_IDENTITY,
    AUTHORING_SOURCE_UNAVAILABLE,
    DRAFTS_UNVERIFIED,
    MACHINE_ROSTER_UNVERIFIED,
    COACH_SETTINGS_UNVERIFIED,
    MACHINE_CHOICES_UNVERIFIED,
    HISTORY_UNVERIFIED,
    CUSTOM_INVENTORY_UNVERIFIED,
    GENERATED_INVENTORY_UNVERIFIED,
    JOURNAL_MANIFEST_MISMATCH,
    STORE_IO_UNAVAILABLE
}

sealed interface NativeKotlinStagedCoreAuditResult {
    data class Verified(
        val athleteId:String,
        val historyRevision:Long,
        val finishedWorkouts:Int,
        val generatedIssued:Int,
        val generatedUnfinished:Int,
        val customPrepared:Int,
        val customIssued:Int,
        val customUnfinished:Int,
        val machineChoiceRecords:Int=0,
        val coachingSettingsRevision:Long=0
    ):NativeKotlinStagedCoreAuditResult
    data class Rejected(val reason:NativeKotlinStagedCoreRefusal):
        NativeKotlinStagedCoreAuditResult
}

object NativeKotlinStagedCoreStoreAudit {
    private fun reject(reason:NativeKotlinStagedCoreRefusal)=
        NativeKotlinStagedCoreAuditResult.Rejected(reason)

    /**
     * Must be run *inside* the clean-install atomic restore's private
     * staging callback, before the directory can be published. Historical
     * ownership is proven using archived physical approval files; the
     * supplied owner is only an expected identity, not proof by itself.
     *
     * The entire audit is made under the staging owner fence and all native
     * readers use that very same lock. Their re-entrant acquisition is safe.
     */
    fun inspect(
        stagedRoot:Path,expected:NativeKotlinOwnerIdentity
    ):NativeKotlinStagedCoreAuditResult {
        if(!Files.isDirectory(stagedRoot,LinkOption.NOFOLLOW_LINKS) ||
            Files.isSymbolicLink(stagedRoot) ||
            !NativeKotlinEncryptedTrainingSnapshot.stagedLocalIdentityMatches(
                stagedRoot,expected))
            return reject(NativeKotlinStagedCoreRefusal.INVALID_DIRECTORY_OR_IDENTITY)
        return try {
            val fence=NativeKotlinApprovalRevisionFence(stagedRoot)
            val identity=NativeKotlinCurrentIdentity { expected }
            val sources=NativeKotlinCustomAuthoringSourceFileStore(
                stagedRoot,fence,identity)
            val drafts=NativeCustomCycleDraftFileStore(
                stagedRoot,fence,identity)
            val signoffs=NativeKotlinCustomCycleSignoffFileStore(
                stagedRoot,fence,identity,drafts,sources)
            val handoff=NativeKotlinFutureActivationHandoffFileStore(
                stagedRoot,fence,identity,sources)
            val machineRosters=NativeKotlinMachineRosterFileStore(stagedRoot)
            val machineChoices=NativeKotlinWorkoutMachineChoiceFileStore(stagedRoot)
            val coachSettings=NativeKotlinCoachAssistanceSettingsStore(
                stagedRoot,fence,identity)
            val preflight=NativeKotlinCustomWorkoutPreflight(
                fence,identity,drafts,sources,signoffs,machineRosters)
            val prepared=NativeKotlinCustomWorkoutPreparedStore(
                stagedRoot,fence,identity,preflight,handoff)
            val history=NativeKotlinHistoryManifestFileStore(stagedRoot)
            val generated=KotlinNativeJournalFileStore(stagedRoot)
            val generatedCorrections=KotlinNativeSavedCorrectionFileStore(
                stagedRoot,generated)
            val approvals=NativeKotlinApprovalFileAuthority(
                stagedRoot,fence,identity)
            val custom=NativeKotlinCustomWorkoutJournalFileStore(
                stagedRoot,fence,identity,prepared,preflight,
                history,generated,handoff,sources)
            val customCorrections=NativeKotlinCustomSavedCorrectionFileStore(
                stagedRoot,fence,identity,custom,history)
            val mixed=NativeKotlinMixedHistoryReadCoordinator(
                history,generated,generatedCorrections,custom,approvals,
                identity,fence,customCorrections)
            val outstanding=NativeKotlinCustomOutstandingWorkoutInventory(
                prepared,custom)
            fence.withOwnerTransaction(expected.ownerKey) {
                val activeSource=sources.read(expected.ownerKey)
                    as? NativeAuthoringSourceRead.Found
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.AUTHORING_SOURCE_UNAVAILABLE)
                if(activeSource.source.ownerKey!=expected.ownerKey ||
                    activeSource.source.athleteId!=expected.athleteId)
                    return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.AUTHORING_SOURCE_UNAVAILABLE)
                val savedDrafts=drafts.listOwned(expected.ownerKey)
                if(savedDrafts !is NativeCustomDraftListResult.Ready)
                    return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.DRAFTS_UNVERIFIED)
                if(machineRosters.read(expected.ownerKey)
                    !is NativeMachineRosterResult.Saved)
                    return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.MACHINE_ROSTER_UNVERIFIED)
                val settings=coachSettings.inventoryOnlyCurrentOwner(
                    expected.ownerKey) as? NativeCoachSettingsRead.Current
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.COACH_SETTINGS_UNVERIFIED)
                if(settings.settings.athleteId!=expected.athleteId)
                    return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.COACH_SETTINGS_UNVERIFIED)
                // Mixed History fully replays every manifest-backed
                // generated/custom completion and all saved corrections.
                val verifiedHistory=mixed.read(
                    expected.ownerKey,expected.athleteId)
                    as? NativeKotlinMixedHistoryReadResult.Review
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.HISTORY_UNVERIFIED)
                val timeline=verifiedHistory.timeline
                if(timeline.athleteId!=expected.athleteId)
                    return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.HISTORY_UNVERIFIED)
                val completedCustom=timeline.completions
                    .filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                    .map{it.manifest.workoutId}.toSet()
                // Includes every prepared and issued CUSTOM workout,
                // including those not yet completed in the History ledger.
                val customScan=outstanding.inspect(
                    expected.ownerKey,expected.athleteId,completedCustom)
                    as? NativeCustomInventoryReview.Reviewed
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.CUSTOM_INVENTORY_UNVERIFIED)
                // The generated inventory replays every issued journal.
                // Do NOT use activation review here; it purposely refuses
                // unfinished sessions which a backup MUST preserve.
                val generatedScan=generated.inventoryOwner(
                    expected.ownerKey,expected.athleteId) { owner,source ->
                    approvals.canAccessIssuedWorkout(owner,source)
                } as? NativeKotlinJournalInventoryResult.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.GENERATED_INVENTORY_UNVERIFIED)
                val completedGenerated=timeline.completions
                    .filterIsInstance<NativeKotlinMixedHistoryCompletion.Composer>()
                    .associate{it.manifest.workoutId to it.manifest.finishEventId}
                if(generatedScan.journals.map{it.snapshot.workoutId}
                        .distinct().size!=generatedScan.journals.size ||
                    completedGenerated.keys.any{finished->
                        generatedScan.journals.none{it.snapshot.workoutId==finished}
                    } || generatedScan.journals.any{journal->
                        journal.finishedByEventId!=
                            completedGenerated[journal.snapshot.workoutId]
                    })
                    return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.JOURNAL_MANIFEST_MISMATCH)
                // All physical machine-choice files must correspond to
                // exactly one owner-verified issued workout with its issued
                // gym and a real exercise in that saved workout. No orphan,
                // cross-gym selection or missing/corrupt record is silently
                // dropped. The current gym roster can legitimately have
                // changed since an older workout was issued.
                val issuedCustom=custom.inventory(expected.ownerKey)
                    as? NativeIssuedInventoryResult.Found
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.CUSTOM_INVENTORY_UNVERIFIED)
                val choices=machineChoices.inventoryOwner(expected.ownerKey)
                    as? NativeWorkoutMachineChoiceInventoryResult.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.MACHINE_CHOICES_UNVERIFIED)
                val generatedById=generatedScan.journals.associateBy {
                    it.snapshot.workoutId
                }
                val customById=issuedCustom.workouts.associateBy {
                    it.journal.workoutId
                }
                if(choices.choices.any { choice ->
                    if(choice.custom) {
                        val issued=customById[choice.workoutId]
                        issued==null ||
                            choice.gymId!=issued.journal.originalSnapshot.stamp.gymId ||
                            choice.machines.keys.any{exercise->
                                issued.journal.snapshot.slots.none{
                                    it.exerciseId==exercise
                                }
                            }
                    } else {
                        val issued=generatedById[choice.workoutId]
                        issued==null ||
                            choice.gymId!=issued.originalSnapshot.source.gymId ||
                            choice.machines.keys.any{exercise->
                                issued.snapshot.targets.none{
                                    it.exerciseId==exercise
                                }
                            }
                    }
                })
                    return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.MACHINE_CHOICES_UNVERIFIED)
                NativeKotlinStagedCoreAuditResult.Verified(
                    expected.athleteId,timeline.manifestRevision,
                    timeline.completions.size,
                    generatedScan.journals.size,
                    generatedScan.journals.count{it.finishedByEventId==null},
                    customScan.sessions.size,
                    customScan.sessions.count{it.issued},
                    customScan.outstanding.count{it.issued},
                    choices.choices.size,settings.settings.revision)
            }
        } catch(_:Exception) {
            reject(NativeKotlinStagedCoreRefusal.STORE_IO_UNAVAILABLE)
        }
    }
}
