package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Candidate-only native gym source editor. IO and owner fence live off Main.
 * Never creates demo stock, silently imports JS program inventory, or
 * optimistically claims that a change has been durably applied.
 */
internal data class NativeKotlinGymSourceState(
    val source: NativeCustomAuthoringSnapshot? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val refusal: NativeAuthoringSourceRefusal? = null,
    val savedAthleteId: String? = null
)

internal class NativeKotlinGymSourceController(
    private val host: NativeKotlinCandidateHost
): AutoCloseable {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    private val pending=AtomicBoolean(false)
    private val current=MutableStateFlow(NativeKotlinGymSourceState())
    val state:StateFlow<NativeKotlinGymSourceState> = current.asStateFlow()

    private fun run(action:()->Unit) {
        if(!pending.compareAndSet(false,true))return
        current.value=current.value.copy(busy=true,refusal=null,message=null)
        scope.launch {
            try {action()}
            catch(_:Exception) {
                current.value=current.value.copy(
                    message="Native gym change failed. Reload saved equipment before editing.")
            } finally {
                current.value=current.value.copy(busy=false)
                pending.set(false)
            }
        }
    }

    private fun load(message:String?=null) {
        val previousAthlete=host.savedAthleteIdForExplicitResume()
        val result=host.readCustomAuthoringSource()
        current.value=when(result) {
            is NativeAuthoringSourceRead.Found ->
                NativeKotlinGymSourceState(source=result.source,
                    savedAthleteId=previousAthlete,
                    message=message ?: "Verified native gym and catalog restored · revision ${result.source.revision}")
            NativeAuthoringSourceRead.Missing ->
                NativeKotlinGymSourceState(savedAthleteId=previousAthlete,
                    message=
                    "No native reviewed gym/catalog source yet. Complete Kotlin onboarding and curate the exercise catalog first.")
            is NativeAuthoringSourceRead.Revoked ->
                NativeKotlinGymSourceState(savedAthleteId=previousAthlete,
                    message=
                    "Native gym/catalog source was revoked at revision ${result.revision}.")
            is NativeAuthoringSourceRead.Rejected ->
                NativeKotlinGymSourceState(refusal=result.reason,
                    savedAthleteId=previousAthlete,
                    message="Native equipment could not be verified; nothing was replaced.")
        }
    }
    fun open()=run {load()}

    /**
     * ONLY a direct first-time native onboarding confirmation may call this.
     * Review all explicit athlete facts again, activate the matching local
     * athlete and publish the provisional curated catalog with no guessed stock.
     */
    fun activateReviewedStarter(draft:AthleteSetupDraft) = run {
        val reviewed=host.reviewSetup(draft)
        if(reviewed.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW) {
            current.value=current.value.copy(
                message="Native athlete or gym setup is incomplete. Resolve review issues before enrollment.",
                refusal=NativeAuthoringSourceRefusal.INVALID_SOURCE)
            return@run
        }
        when(val result=host.activateReviewedStarterAuthoringSource(draft)) {
            is NativeAuthoringSourceWrite.Committed ->
                load("Native reviewed athlete and provisional starter catalog saved · revision ${result.revision}. Confirm physical equipment before selecting loaded exercises.")
            is NativeAuthoringSourceWrite.Rejected -> {
                load()
                current.value=current.value.copy(
                    refusal=result.reason,
                    message="Initial native setup not published: ${result.reason.name.replace('_',' ').lowercase()}. Existing source was not overwritten.")
            }
        }
    }



    private fun change(
        operation:(Long)->NativeAuthoringSourceWrite
    )=run {
        val before=current.value.source ?:return@run
        when(val result=operation(before.revision)) {
            is NativeAuthoringSourceWrite.Committed ->
                load("Saved verified gym source revision ${result.revision}. Training targets were not changed.")
            is NativeAuthoringSourceWrite.Rejected -> {
                load()
                current.value=current.value.copy(refusal=result.reason,
                    message="Gym edit rejected: ${result.reason.name.replace('_',' ').lowercase()}. Reloaded authoritative stock.")
            }
        }
    }

    fun upsertStock(id:String,stock:VerifiedLoadStock) =
        change {host.upsertNativeGymStock(it,id,stock)}

    fun removeStock(id:String) =
        change {host.removeNativeGymStock(it,id)}

    fun setFacilitiesAndAttachments(
        facilities:Set<String>,attachments:Set<String>
    )=change {host.updateNativeGymFixtures(it,facilities,attachments)}

    /** Catalog is curated elsewhere with explicit provisional review facts. */
    fun publishReviewedCatalog(catalog:List<CatalogMovement>)=
        change {host.publishNativeCuratedCatalog(it,catalog)}

    override fun close() {scope.cancel()}
}
