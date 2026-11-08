package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/** Read-only bridge to the existing next-set coach. No new journal or targets. */
class NativeKotlinCustomLiveSetCoachReview(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val journals:NativeKotlinCustomWorkoutJournalFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val settings:NativeKotlinCoachAssistanceSettingsStore
) {
    fun review(
        ownerKey:String,workoutId:String,setKey:String,
        readiness:CoachReadiness,movementRestricted:Boolean,
        maximumReviewStep:BigDecimal
    ):LiveSetCoachResult? = runCatching {
        fence.withOwnerTransaction(ownerKey) {
            val owner=identity.signedInIdentity()?.takeIf {it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction null
            val assistance=(settings.read(ownerKey) as? NativeCoachSettingsRead.Current)
                ?.settings?.takeIf {it.athleteId==owner.athleteId}?.policy
                ?:return@withOwnerTransaction null
            val journal=(journals.open(ownerKey,workoutId)
                as? NativeCustomJournalResult.Durable)?.issued
                ?.takeIf {it.athleteId==owner.athleteId}?.journal
                ?:return@withOwnerTransaction null
            val slot=journal.snapshot.slots.singleOrNull {slot->
                slot.sets.any {it.setKey==setKey}
            } ?:return@withOwnerTransaction null
            val source=(sources.read(ownerKey) as? NativeAuthoringSourceRead.Found)
                ?.source
            val setup=source?.setup?.reviewed
            val stamp=journal.snapshot.stamp
            // Changed gyms, inventories, profiles or variants cannot lend
            // their equipment to an old issued workout. Logging still works.
            val movement=source?.catalog?.singleOrNull {
                it.exerciseId==slot.exerciseId && it.familyId==slot.familyId &&
                    it.variantId==slot.variantId && it.attachmentId==slot.attachmentId
            }
            val stock=if(source?.athleteId==owner.athleteId &&
                setup?.athleteProfileId==stamp.athleteId &&
                setup.profileRevision==stamp.athleteProfileRevision &&
                setup.gym.gymId==stamp.gymId &&
                source.revision==stamp.gymCatalogSourceRevision && movement!=null)
                setup.gym.confirmedStock[movement.equipmentId]
                    ?.takeIf {it.basis==movement.expectedLoadBasis}
            else null
            val result=KotlinJournalLiveSetCoachDomain.review(journal,setKey,
                stock,readiness,movementRestricted,maximumReviewStep,assistance)
            if(identity.signedInIdentity()!=owner)return@withOwnerTransaction null
            result
        }
    }.getOrNull()
}
