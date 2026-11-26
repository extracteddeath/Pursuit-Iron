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
import java.util.UUID
import java.math.BigDecimal
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Native gym and exercise source editor. IO and owner fence live off Main.
 * Never creates demo stock, silently imports JS program inventory, or
 * optimistically claims that a change has been durably applied.
 */
internal data class NativeKotlinGymSourceState(
    val source: NativeCustomAuthoringSnapshot? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val refusal: NativeAuthoringSourceRefusal? = null,
    val savedAthleteId: String? = null,
    val machineRoster: NativeMachineRosterSnapshot? = null,
    val savedGyms:NativeSavedGymRead.Ready?=null,
    val lastExclusionEdit:NativeKotlinExerciseExclusionCommit?=null
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
        val previousExclusionEdit=current.value.lastExclusionEdit
        val previousAthlete=host.savedAthleteIdForExplicitResume()
        val result=host.readCustomAuthoringSource()
        val loaded=when(result) {
            is NativeAuthoringSourceRead.Found ->
                NativeKotlinGymSourceState(source=result.source,
                    savedAthleteId=previousAthlete,
                    lastExclusionEdit=previousExclusionEdit?.takeIf{it.matches(result.source)},
                    message=message ?: "Saved gym and exercise choices loaded.")
            NativeAuthoringSourceRead.Missing ->
                NativeKotlinGymSourceState(savedAthleteId=previousAthlete,
                    message=
                    "No native reviewed gym/catalog source yet. Complete Kotlin onboarding and curate the exercise catalog first.")
            is NativeAuthoringSourceRead.Revoked ->
                NativeKotlinGymSourceState(savedAthleteId=previousAthlete,
                    message=
                    "Native gym/catalog source was revoked at revision ${result.revision}.")
            is NativeAuthoringSourceRead.Rejected ->
                if(result.reason==NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                    // A fresh or locked local profile has no readable gym
                    // yet. This is setup guidance, not failed equipment.
                    // source remains null; no owner/stock gate is bypassed.
                    NativeKotlinGymSourceState(savedAthleteId=previousAthlete,
                        message=if(previousAthlete==null)
                            "Choose your training preferences and equipment to get started."
                        else "Confirm your saved athlete to reopen your training setup.")
                else NativeKotlinGymSourceState(refusal=result.reason,
                    savedAthleteId=previousAthlete,
                    message="Native equipment could not be verified; nothing was replaced.")
        }
        current.value=loaded.copy(busy=pending.get())
        if(current.value.source!=null) {
            val gymRead=host.readSavedPhysicalGyms() as? NativeSavedGymRead.Ready
            val shown=current.value.source
            if(gymRead!=null && shown!=null && gymRead.sourceRevision==shown.revision &&
                gymRead.active==shown.setup.reviewed?.gym)
                current.value=current.value.copy(savedGyms=gymRead)
            else current.value=current.value.copy(savedGyms=null,
                message="Saved gyms could not be matched to the current source. Refresh before switching.")
            when(val roster=host.readMachineRoster()) {
                is NativeMachineRosterResult.Saved ->
                    current.value=current.value.copy(machineRoster=roster.snapshot)
                is NativeMachineRosterResult.Rejected ->
                    current.value=current.value.copy(
                        message="Machine alternatives could not be restored: ${roster.reason}.",
                        machineRoster=null)
            }
        }
    }
    fun open()=run {load()}

    /**
     * A deliberate cold-start restoration, never an automatic login.
     * The host verifies both the saved identity and the source's re-reviewed
     * athlete/gym facts before this state receives any protected material.
     */
    fun resumeVerifiedSavedSetup(confirmedAthleteId:String)=run {
        when(val resumed=host.resumeVerifiedSavedSetup(confirmedAthleteId)) {
            is NativeSavedSetupResume.Ready ->
                load("Saved athlete and gym restored · source revision ${resumed.sourceRevision}. No program or workout was replaced.")
            is NativeSavedSetupResume.Rejected -> {
                load()
                current.value=current.value.copy(
                    refusal=NativeAuthoringSourceRefusal.NOT_AUTHORIZED,
                    message=resumed.reason)
            }
        }
    }


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
                load("Native athlete confirmed; reviewed gym/catalog revision ${result.revision} opened or created without replacing existing stock. Confirm physical equipment before selecting loaded exercises.")
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

    private fun switchGym(expectedRevision:Long,operation:()->NativeAuthoringSourceWrite)=run {
        val shown=current.value.savedGyms
        if(shown?.sourceRevision!=expectedRevision || current.value.source?.revision!=expectedRevision) {
            load("The saved gym list changed. Review the current equipment before switching.")
            return@run
        }
        when(val result=operation()) {
            is NativeAuthoringSourceWrite.Committed ->
                load("Saved gym selected. Review your plan for this equipment before starting a new workout; previous issued targets remain unchanged.")
            is NativeAuthoringSourceWrite.Rejected -> {
                load()
                current.value=current.value.copy(refusal=result.reason,
                    message="Gym switch rejected: ${result.reason.name.replace('_',' ').lowercase()}. Check the reloaded gym list.")
            }
        }
    }

    fun selectSavedGym(expectedRevision:Long,gymId:String,athleteConfirmed:Boolean)=
        switchGym(expectedRevision){host.selectSavedPhysicalGym(expectedRevision,gymId,athleteConfirmed)}

    fun addSavedGym(expectedRevision:Long,gymId:String,unit:LoadUnit,athleteConfirmed:Boolean)=
        switchGym(expectedRevision){host.addSavedPhysicalGym(expectedRevision,gymId,unit,athleteConfirmed)}

    fun upsertStock(id:String,stock:VerifiedLoadStock) =
        change {host.upsertNativeGymStock(it,id,stock)}

    fun removeStock(id:String) =
        change {host.removeNativeGymStock(it,id)}

    fun setFacilitiesAndAttachments(
        facilities:Set<String>,attachments:Set<String>
    )=change {host.updateNativeGymFixtures(it,facilities,attachments)}

    fun saveExerciseExclusions(intent:NativeGymExerciseExclusionChange) {
        val captured=intent.copy(previous=intent.previous.toSet(),next=intent.next.toSet())
        writeExerciseExclusions(captured,undo=false)
    }

    fun undoExerciseExclusions(edit:NativeKotlinExerciseExclusionCommit) {
        if(edit==current.value.lastExclusionEdit && edit.matches(current.value.source))
            writeExerciseExclusions(edit.undoRequest(),undo=true)
    }

    private fun writeExerciseExclusions(intent:NativeGymExerciseExclusionChange,undo:Boolean)=run {
        when(val result=host.updateNativeExerciseExclusions(intent)) {
            is NativeAuthoringSourceWrite.Committed -> {
                load(if(undo)"Restored the previous exercise exclusions. Review your plan before starting a new workout."
                    else "Saved exercise exclusions. Review your plan before starting a new workout; issued workouts retain their saved targets.")
                val saved=current.value.source
                val edit=saved?.let{NativeKotlinExerciseExclusionCommit(intent,it)}
                    ?.takeIf{it.matches(saved)}
                current.value=current.value.copy(lastExclusionEdit=if(undo)null else edit)
            }
            is NativeAuthoringSourceWrite.Rejected -> {
                load()
                current.value=current.value.copy(refusal=result.reason,
                    message="The exclusion save could not be confirmed. Check the reloaded saved library before retrying.")
            }
        }
    }

    fun addUserExercise(intent:NativeUserExerciseDraft) {
        val captured=intent.copy(permittedFocus=intent.permittedFocus.toSet(),
            requiredFacilities=intent.requiredFacilities.toSet(),
            directMuscles=intent.directMuscles.toSet())
        run {
            when(val result=host.addNativeUserExercise(captured)) {
                is NativeAuthoringSourceWrite.Committed ->
                    load("Custom exercise saved. Review your plan before issuing a new workout; existing history and targets are unchanged.")
                is NativeAuthoringSourceWrite.Rejected -> {
                    load()
                    current.value=current.value.copy(refusal=result.reason,
                        message="Custom exercise not saved. Check its name, equipment, metadata and current gym before retrying.")
                }
            }
        }
    }

    /** Catalog is curated elsewhere with explicit provisional review facts. */
    fun publishReviewedCatalog(catalog:List<CatalogMovement>)=
        change {host.publishNativeCuratedCatalog(it,catalog)}


    private fun mutateMachineRoster(
        operation:(NativeMachineRosterSnapshot,NativeCustomAuthoringSnapshot)->
            List<KotlinGymMachineAlternatives>
    )=run {
        val source=current.value.source?:return@run
        val roster=current.value.machineRoster?:return@run
        val configs=runCatching{operation(roster,source)}.getOrElse {
            current.value=current.value.copy(
                message="Machine change invalid: ${it.message?: "Check machine settings."}")
            return@run
        }
        when(val result=host.saveMachineRoster(roster.revision,configs)) {
            is NativeMachineRosterResult.Saved ->
                load("Saved physical-machine alternatives. Existing workout history was not modified.")
            is NativeMachineRosterResult.Rejected -> {
                load()
                current.value=current.value.copy(
                    message="Machine change rejected: ${result.reason}. Reloaded saved defaults.")
            }
        }
    }

    fun addPhysicalMachine(exerciseId:String,label:String,increment:BigDecimal?=null,
        minimum:BigDecimal?=null,maximum:BigDecimal?=null)=
        mutateMachineRoster {roster,source->
            require(source.catalog.singleOrNull{it.exerciseId==exerciseId}
                ?.let(CatalogPhysicalMachineEligibility::eligible)==true) {
                "Only reviewed machine or cable-stack exercises can have physical Machine A/B."
            }
            val gymId=checkNotNull(source.setup.reviewed).gym.gymId
            val current=roster.configurations.firstOrNull{
                it.gymId==gymId && it.exerciseId==exerciseId
            }?:KotlinGymMachineAlternatives(gymId,exerciseId,null,emptyList())
            require(current.machines.none {
                it.label.trim().equals(label.trim(),ignoreCase=true)
            }) { "A physical machine with this name already exists at this gym." }
            val uniqueId="machine_"+UUID.randomUUID().toString().replace("-","").take(16)
            val added=KotlinMachineAlternativesDomain.add(current,
                KotlinPhysicalMachine(uniqueId,label.trim(),
                    unit=source.setup.reviewed?.gym?.unit,increment=increment,
                    minimum=minimum,maximum=maximum),
                makeDefault=current.machines.isEmpty())
            roster.configurations.filterNot {
                it.gymId==gymId && it.exerciseId==exerciseId
            }+added
        }

    /** Edits gym stock only; completed sets and accepted workout targets stay immutable. */
    fun calibratePhysicalMachine(
        exerciseId:String,machineId:String,
        minimum:BigDecimal?,increment:BigDecimal?,maximum:BigDecimal?
    )=mutateMachineRoster {roster,source->
        val gymId=checkNotNull(source.setup.reviewed).gym.gymId
        roster.configurations.map {config->
            if(config.gymId==gymId && config.exerciseId==exerciseId) {
                val previous=checkNotNull(config.machines.firstOrNull{it.id==machineId})
                KotlinMachineAlternativesDomain.edit(config,previous.copy(
                    minimum=minimum,increment=increment,maximum=maximum))
            } else config
        }
    }

    fun defaultPhysicalMachine(exerciseId:String,machineId:String)=
        mutateMachineRoster {roster,source->
            val gymId=checkNotNull(source.setup.reviewed).gym.gymId
            roster.configurations.map { config ->
                if(config.gymId==gymId && config.exerciseId==exerciseId)
                    KotlinMachineAlternativesDomain.setDefault(config,machineId)
                else config
            }
        }

    fun setPhysicalMachineActive(exerciseId:String,machineId:String,active:Boolean)=
        mutateMachineRoster {roster,source->
            val gymId=checkNotNull(source.setup.reviewed).gym.gymId
            roster.configurations.map { config ->
                if(config.gymId==gymId && config.exerciseId==exerciseId) {
                    val previous=checkNotNull(config.machines.firstOrNull{it.id==machineId})
                    KotlinMachineAlternativesDomain.edit(config,previous.copy(active=active))
                } else config
            }
        }

    fun renamePhysicalMachine(exerciseId:String,machineId:String,label:String)=
        mutateMachineRoster {roster,source->
            val gymId=checkNotNull(source.setup.reviewed).gym.gymId
            roster.configurations.map { config ->
                if(config.gymId==gymId && config.exerciseId==exerciseId) {
                    val previous=checkNotNull(config.machines.firstOrNull{it.id==machineId})
                    require(label.trim().isNotBlank() && config.machines.none {
                        it.id!=machineId &&
                            it.label.trim().equals(label.trim(),ignoreCase=true)
                    }) { "Choose a unique name for this physical machine." }
                    KotlinMachineAlternativesDomain.edit(config,previous.copy(label=label.trim()))
                } else config
            }
        }

    override fun close() {scope.cancel()}
}
