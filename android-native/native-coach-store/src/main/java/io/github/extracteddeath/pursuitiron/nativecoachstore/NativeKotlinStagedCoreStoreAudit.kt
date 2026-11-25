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
 * release gate. Every retained legacy format, other advanced approvals/signoffs
 * and remaining independent files still need explicit coverage before UI
 * import is permitted. Machine choices, pinned notes and coaching settings
 * use their existing native readers with complete physical file scans.
 * No workout, approval, note or training file is ever mutated by this API.
 */
enum class NativeKotlinStagedCoreRefusal {
    INVALID_DIRECTORY_OR_IDENTITY,
    AUTHORING_SOURCE_UNAVAILABLE,
    DRAFTS_UNVERIFIED,
    FIRST_PROGRAM_SEEDS_UNVERIFIED,
    CUSTOM_SIGNOFFS_UNVERIFIED,
    MACHINE_ROSTER_UNVERIFIED,
    COACH_SETTINGS_UNVERIFIED,
    MACHINE_CHOICES_UNVERIFIED,
    PINNED_NOTES_UNVERIFIED,
    ADAPTIVE_PERMISSIONS_UNVERIFIED,
    FUTURE_APPROVALS_UNVERIFIED,
    FUTURE_CONTEXTS_UNVERIFIED,
    FUTURE_HANDOFF_UNVERIFIED,
    FUTURE_ACTIVATION_INTENT_UNVERIFIED,
    FUTURE_CALENDARS_UNVERIFIED,
    HISTORY_UNVERIFIED,
    CUSTOM_INVENTORY_UNVERIFIED,
    TIER_TRANSITION_INVENTORY_UNVERIFIED,
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
        val coachingSettingsRevision:Long=0,
        val pinnedExerciseNotes:Int=0,
        val adaptivePermissionRecords:Int=0,
        val revokedAdaptivePermissions:Int=0,
        val futureActivationApprovals:Int=0,
        val revokedFutureActivationApprovals:Int=0,
        val stagedFutureHandoffs:Int=0,
        val savedFutureBlockContexts:Int=0,
        val customConsentRecords:Int=0,
        val revokedCustomConsents:Int=0,
        val legacyCustomConsents:Int=0,
        /** Owner-verified v1/v2 generated provenance; not an approval. */
        val generatedFirstProgramSeeds:Int=0,
        /** Current and archived calendar approvals; none are issue grants. */
        val approvedFutureBlockCalendars:Int=0,
        val staleFutureBlockCalendars:Int=0,
        val appliedFutureActivationIntents:Int=0,
        val preparedFutureActivationIntents:Int=0,
        val tierTransitionReceipts:Int=0
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
            val firstProgramSeeds=NativeKotlinFirstProgramSeedFileStore(
                stagedRoot,fence,identity)
            val signoffs=NativeKotlinCustomCycleSignoffFileStore(
                stagedRoot,fence,identity,drafts,sources)
            val handoff=NativeKotlinFutureActivationHandoffFileStore(
                stagedRoot,fence,identity,sources)
            val machineRosters=NativeKotlinMachineRosterFileStore(stagedRoot)
            val machineChoices=NativeKotlinWorkoutMachineChoiceFileStore(stagedRoot)
            val coachSettings=NativeKotlinCoachAssistanceSettingsStore(
                stagedRoot,fence,identity)
            val pinnedNotes=NativeKotlinPinnedExerciseNoteFileStore(
                stagedRoot,fence,identity)
            val adaptivePermissions=NativeKotlinAdaptiveCyclePermissionFileStore(
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
            val contexts=NativeKotlinFutureBlockContextFileStore(
                stagedRoot,fence,identity,drafts,sources)
            val boundary=NativeKotlinFutureBlockActivationPreflight(
                fence,identity,drafts,contexts,mixed,custom,outstanding,
                NativeKotlinComposerActivationInventory(generated,approvals))
            val futureApprovals=NativeKotlinFutureActivationApprovalFileStore(
                stagedRoot,fence,identity,boundary,contexts,drafts,sources,
                adaptivePermissions)
            val appliedFuture=NativeKotlinFutureActivationCommitTransaction(
                stagedRoot,fence,identity,sources,drafts,contexts,
                futureApprovals,handoff)
            val calendars=NativeKotlinActivatedBlockCalendarFileStore(
                stagedRoot,fence,identity,appliedFuture,sources,drafts,
                NativeHistoryCompletionClock.Undated)
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
                // Generated seeds may legitimately predate a later athlete
                // program edit or survive a crash before the first draft
                // save. Preserve all of them without granting approvals.
                // Scan *physical files*, not only IDs in current drafts:
                // otherwise a corrupt or foreign v2 advanced-technique
                // consent could silently disappear during restore.
                val seedInventory=firstProgramSeeds.inventoryOwner(
                    expected.ownerKey) as? NativeFirstProgramSeedInventory.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.FIRST_PROGRAM_SEEDS_UNVERIFIED)
                // Every archived custom-cycle consent and revocation has
                // training significance, even if its draft was retired.
                // A valid checksum alone does not re-approve the program.
                val customConsents=signoffs.inventoryOwner(expected.ownerKey)
                    as? NativeCustomSignoffInventory.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.CUSTOM_SIGNOFFS_UNVERIFIED)
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
                // Pinned notes can outlive old exercise catalogs and may
                // contain legacy setup keys. Preserve them, but require
                // every physical note to belong to the signed-in athlete.
                val notes=pinnedNotes.inventoryOwner(expected.ownerKey)
                    as? NativePinnedNoteInventory.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.PINNED_NOTES_UNVERIFIED)
                // A revoked permission is a durable athlete decision, not
                // equivalent to a missing/default permission. Preserve all
                // valid historical cycle grants, even if a cycle was retired.
                val permissions=adaptivePermissions.inventoryOwner(expected.ownerKey)
                    as? NativeAdaptivePermissionInventory.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.ADAPTIVE_PERMISSIONS_UNVERIFIED)
                // A staged snapshot must account for stale context records
                // as well as the currently approved future block. Inspect
                // all physical files without refreshing their authority.
                val futureContexts=contexts.inventoryOwner(expected.ownerKey)
                    as? NativeFutureContextInventory.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.FUTURE_CONTEXTS_UNVERIFIED)
                val secondConsents=futureApprovals.inventoryOwner(expected.ownerKey)
                    as? NativeFutureApprovalInventory.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.FUTURE_APPROVALS_UNVERIFIED)
                // Read EVERY archived date record, including superseded
                // programs. Never silently discard a foreign, renamed,
                // corrupt or interrupted physical calendar during restore.
                val savedCalendars=calendars.inventoryOwner(expected.ownerKey)
                    as? NativeActivatedBlockCalendarInventory.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.FUTURE_CALENDARS_UNVERIFIED)
                val switchBarrier=handoff.inventoryOwner(expected.ownerKey)
                if(switchBarrier !is NativeFutureHandoffReview.Missing &&
                    switchBarrier !is NativeFutureHandoffReview.Staged)
                    return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.FUTURE_HANDOFF_UNVERIFIED)
                if(switchBarrier is NativeFutureHandoffReview.Staged) {
                    val receipt=switchBarrier.receipt
                    val stored=secondConsents.records.singleOrNull {
                        it.cycleId==receipt.cycleId && it.blockId==receipt.blockId
                    }
                    // Later revocation/reconfirmation does not erase an
                    // unresolved barrier. Missing/older consent is invalid;
                    // the same revision must describe the exact staged grant.
                    val grant=stored?.approval
                    if(stored==null || stored.revision<receipt.approvalRevision ||
                        stored.revision==receipt.approvalRevision &&
                        (grant==null || grant.reviewedFingerprint!=receipt.approvalFingerprint ||
                            grant.sourceRevision!=receipt.sourceRevision ||
                            grant.firstWeek!=receipt.firstWeek ||
                            grant.historyManifestRevision!=receipt.historyManifestRevision))
                        return@withOwnerTransaction reject(
                            NativeKotlinStagedCoreRefusal.FUTURE_HANDOFF_UNVERIFIED)
                }
                // The historical second-consent and the write-ahead
                // source-activation transaction have overlapping filename
                // prefixes but different binary schemas. Validate the
                // physical transaction separately; a malformed .pending
                // or forged source-CAS intent must never restore silently.
                val activationIntents=appliedFuture.inventoryOwner(
                    expected.ownerKey) as? NativeFutureCommitInventory.Verified
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.FUTURE_ACTIVATION_INTENT_UNVERIFIED)
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
                val tierTransitions=NativeKotlinFirstTierTransitionConsentStore(
                    stagedRoot,fence,identity,drafts,sources,signoffs,mixed,outstanding,machineRosters)
                    .inventoryForRecovery(expected.ownerKey)
                    ?:return@withOwnerTransaction reject(
                        NativeKotlinStagedCoreRefusal.TIER_TRANSITION_INVENTORY_UNVERIFIED)
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
                    choices.choices.size,settings.settings.revision,
                    notes.notes.size,permissions.records.size,
                    permissions.records.count{it.revoked},
                    secondConsents.records.size,
                    secondConsents.records.count{it.revoked},
                    if(switchBarrier is NativeFutureHandoffReview.Staged)1 else 0,
                    futureContexts.records.size,
                    customConsents.records.size,
                    customConsents.records.count{it.revoked},
                    customConsents.records.count{!it.revoked && !it.hasReviewedInputs},
                    seedInventory.seeds.size,
                    savedCalendars.records.count{it.sourceStillCurrent},
                    savedCalendars.records.count{!it.sourceStillCurrent},
                    if(activationIntents.state is NativeFutureCommitReview.Applied)1 else 0,
                    if(activationIntents.state is NativeFutureCommitReview.Prepared)1 else 0,
                    tierTransitions)
            }
        } catch(_:Exception) {
            reject(NativeKotlinStagedCoreRefusal.STORE_IO_UNAVAILABLE)
        }
    }
}
