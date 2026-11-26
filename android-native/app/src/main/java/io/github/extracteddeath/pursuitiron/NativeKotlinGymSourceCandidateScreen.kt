package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

private fun parseIds(text:String):Set<String>? {
    val values=text.split(",").map{it.trim()}.filter{it.isNotBlank()}
    val pattern=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    return if(values.size<=128 && values.distinct().size==values.size &&
        values.all{pattern.matches(it)}) values.toSet() else null
}

/** Labels are presentation only; attachment source IDs remain authoritative. */
internal fun kotlinGymAttachmentLabel(id:String):String=when(id) {
    "straight-bar" -> "Straight cable bar"
    "wide-lat-bar" -> "Wide lat bar"
    "v-bar" -> "V-bar"
    "single-handle" -> "Single cable handle"
    "dual-handles" -> "Dual cable handles"
    "dual-rope" -> "Dual rope"
    "rope" -> "Rope"
    "ankle-cuff" -> "Ankle cuff"
    "wrist-cuff" -> "Wrist cuff"
    "ez-bar-attachment" -> "EZ-grip cable bar"
    else -> id.replace('-', ' ').replace('_',' ')
        .replaceFirstChar{it.uppercase()}
}

/**
 * Native equipment and exercise-library home, using shared Material components.
 * Only the controller's verified source can supply catalog or gym facts.
 */
@Composable
internal fun NativeKotlinGymSourceCandidateScreen(
    controller:NativeKotlinGymSourceController,
    reviewedSetupDraft:AthleteSetupDraft?=null,
    onReviewPlan:(()->Unit)?=null,
    historyHost:NativeKotlinCandidateHost?=null,
    focusedMachineExerciseId:String?=null,
    onMachineFocusConsumed:()->Unit={}
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val source=state.source
    LaunchedEffect(controller) {controller.open()}
    val reviewed=source?.setup?.reviewed
    var editingFixtures by remember {mutableStateOf(false)}
    var showGymDetails by rememberSaveable {mutableStateOf(false)}
    var equipmentEditor by remember {mutableStateOf(false)}
    var removeTarget by remember {mutableStateOf<String?>(null)}
    var equipmentId by rememberSaveable {mutableStateOf("")}
    var equipmentSearch by rememberSaveable {mutableStateOf("")}
    var manualEquipmentId by rememberSaveable {mutableStateOf(false)}
    var showMoreEquipment by rememberSaveable {mutableStateOf(false)}
    var kind by rememberSaveable {
        mutableStateOf(PhysicalStockEditorKind.EXACT_LOADS)
    }
    var basis by rememberSaveable {
        mutableStateOf(LoadBasis.DUMBBELL_PER_HAND)
    }
    var exact by rememberSaveable {mutableStateOf("")}
    var min by rememberSaveable {mutableStateOf("")}
    var step by rememberSaveable {mutableStateOf("")}
    var max by rememberSaveable {mutableStateOf("")}
    var bar by rememberSaveable {mutableStateOf("")}
    var plates by rememberSaveable {mutableStateOf("")}
    fun clearStockFields() {
        exact="";min="";step="";max="";bar="";plates=""
    }
    // An editor opened for one physical gym must never submit another gym's
    // old input after the active source changes (including cold restoration).
    LaunchedEffect(reviewed?.gym?.gymId) {
        equipmentEditor=false;editingFixtures=false;removeTarget=null
        equipmentId="";equipmentSearch="";manualEquipmentId=false;clearStockFields()
    }

    PursuitScreenContent {
        PageHeading("YOUR GYM",
            "Your verified gym",
            "Physical equipment first · workouts never guessed")
        if(state.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        state.message?.let {
            Text(it,style=MaterialTheme.typography.bodyMedium,
                color=if(state.refusal==null)
                    MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.error)
        }
        if(reviewed==null || source==null) {
            PursuitCard(highlighted=true) {
                Text("Finish your training setup",
                    style=MaterialTheme.typography.titleMedium)
                Text("Save your training preferences and confirm the equipment you actually have. No weights or exercise choices are assumed.")
                if(reviewedSetupDraft!=null) {
                    HorizontalDivider()
                    Text("Review your first setup",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Confirm your selected preferences and starting exercise catalog. Your available weights and workout targets remain separate choices.")
                    Text("This action does not replace a different saved athlete or an existing training program.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(
                        onClick={controller.activateReviewedStarter(reviewedSetupDraft)},
                        enabled=!state.busy,
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Save setup and exercises")
                    }
                }
                OutlinedButton(onClick={controller.open()},enabled=!state.busy) {
                    Text("Refresh setup")
                }
            }
            return@PursuitScreenContent
        }
        val gym=reviewed.gym
        state.savedGyms?.takeIf{it.sourceRevision==source.revision && it.active==gym}?.let {
            NativeKotlinSavedGymSelector(controller,it,state.busy)
        }
        PursuitCard(highlighted=true) {
            Text("Your equipment",style=MaterialTheme.typography.titleLarge)
            Text("${gym.confirmedStock.size} confirmed weight sources · " +
                "${gym.availableFacilities.size} facilities · " +
                "${gym.availableAttachments.size} attachments",
                style=MaterialTheme.typography.bodyMedium)
            Text("Your equipment determines which exercises can be reviewed. Editing it never changes previously logged workouts.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick={controller.open()},enabled=!state.busy,
                modifier=Modifier.fillMaxWidth()) {Text("Refresh equipment")}
            TextButton(onClick={showGymDetails=!showGymDetails}) {
                Text(if(showGymDetails)"Hide source details" else "Advanced gym details")
            }
            if(showGymDetails)Text("Gym: ${gym.gymId} · ${gym.unit} · " +
                "source revision ${source.revision} · " +
                "${source.catalog.size} reviewed movements",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // Machine A/B is a primary gym action, above the long equipment and
        // exercise inventory. A Plan deep link selects ONLY the logical
        // exercise in this editor; owner/source checks still guard all edits.
        NativeKotlinMachineAlternativesPanel(
            controller,gym.gymId,source.catalog,state.machineRoster,state.busy,
            focusedMachineExerciseId=focusedMachineExerciseId,
            onMachineFocusConsumed=onMachineFocusConsumed
        )
        key(source.ownerKey,source.athleteId) {
            NativeKotlinExerciseLibraryAccess(source,state.busy,state.message,state.lastExclusionEdit,
                onReviewPlan=onReviewPlan,
                historyHost=historyHost,
                onSave=controller::saveExerciseExclusions,onUndo=controller::undoExerciseExclusions)
        }
        gym.confirmedStock.toSortedMap().forEach { (id, stock) ->
            key(id) {
                PursuitCard {
                    val equipmentName=kotlinGymFacilityLabel(id).takeIf{it!=id}
                        ?:id.replace('-',' ').replace('_',' ')
                            .replaceFirstChar{it.uppercase()}
                    Text(equipmentName,style=MaterialTheme.typography.titleMedium)
                    if(showGymDetails)Text("Source ID: $id",
                        style=MaterialTheme.typography.labelSmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(when(stock) {
                        is VerifiedLoadStock.Totals ->
                            "${kotlinLoadBasisLabel(stock.basis)} · " +
                            stock.loads.joinToString(", ") {it.toPlainString()} +
                            " ${stock.unit}"
                        is VerifiedLoadStock.Stack ->
                            "${kotlinLoadBasisLabel(stock.basis)} · " +
                            "${stock.minimum.toPlainString()} to ${stock.maximum.toPlainString()}" +
                            " in ${stock.increment.toPlainString()} ${stock.unit} steps"
                        is VerifiedLoadStock.PairedBar ->
                            "${if(stock.basis==LoadBasis.SMITH_TOTAL)"Smith empty-bar resistance" else "Bar"} ${stock.barWeight.toPlainString()} ${stock.unit} · " +
                            stock.pairedPlates.entries.joinToString(", ") {
                                "${it.key.toPlainString()} × ${it.value} pairs"
                            }
                    },style=MaterialTheme.typography.bodyMedium)
                    TextButton(onClick={removeTarget=id},enabled=!state.busy) {
                        Text("Remove equipment")
                    }
                }
            }
        }
        OutlinedButton(onClick={equipmentEditor=true},
            enabled=!state.busy && gym.confirmedStock.size<128,
            modifier=Modifier.fillMaxWidth()) {
            Text("Add or correct verified equipment")
        }
        PursuitCard {
            Text("Verified fixtures and attachments",
                style=MaterialTheme.typography.titleMedium)
            Text("Facilities",style=MaterialTheme.typography.labelLarge)
            if(gym.availableFacilities.isEmpty())Text("None confirmed")
            kotlinGymFacilityGroups.forEach { group ->
                val available=group.choices.filter{it.first in gym.availableFacilities}
                if(available.isNotEmpty())Text(
                    "${group.label} · ${available.joinToString {it.second}}",
                    style=MaterialTheme.typography.bodyMedium)
            }
            val mapped=kotlinGymFacilityGroups.flatMap{group->
                group.choices.map{it.first}
            }.toSet()
            val other=gym.availableFacilities-mapped
            if(other.isNotEmpty())Text(
                "Other verified facilities · ${other.sorted().joinToString()}",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Attachments: ${gym.availableAttachments.sorted().joinToString().ifBlank { "None" }}")
            OutlinedButton(onClick={editingFixtures=true},
                enabled=!state.busy) {Text("Edit availability")}
        }

    }

    if(reviewed!=null && source!=null && equipmentEditor) {
        val verifiedChoices=remember(source.revision) {
            kotlinGymStockChoices(source.catalog)
        }
        val selected=verifiedChoices.firstOrNull{it.equipmentId==equipmentId}
        val requiredBasis=selected?.basis
        val usableKinds=PhysicalStockEditorKind.entries.filter {
            if(selected?.equipmentId==RatedResistanceBandDomain.EQUIPMENT_ID)
                it==PhysicalStockEditorKind.EXACT_LOADS
            else when(requiredBasis) {
                LoadBasis.BARBELL_TOTAL ->
                    it==PhysicalStockEditorKind.BARBELL_WITH_PAIRS
                LoadBasis.SMITH_TOTAL -> it in listOf(
                    PhysicalStockEditorKind.SMITH_WITH_PAIRS,PhysicalStockEditorKind.EXACT_LOADS)
                LoadBasis.DUMBBELL_PER_HAND,LoadBasis.KETTLEBELL_EACH,
                    LoadBasis.LANDMINE_ADDED_PLATES ->
                    it==PhysicalStockEditorKind.EXACT_LOADS
                null -> true
                else -> it !in listOf(PhysicalStockEditorKind.BARBELL_WITH_PAIRS,
                    PhysicalStockEditorKind.SMITH_WITH_PAIRS)
            }
        }
        val actualBasis=when(kind) {
            PhysicalStockEditorKind.BARBELL_WITH_PAIRS -> LoadBasis.BARBELL_TOTAL
            PhysicalStockEditorKind.SMITH_WITH_PAIRS -> LoadBasis.SMITH_TOTAL
            else -> basis
        }
        val basisMatches=requiredBasis==null || requiredBasis==actualBasis
        val parsed=if(basisMatches)
            parseVerifiedStock(reviewed.gym.unit,kind,actualBasis,
                exact,min,step,max,bar,plates)?.takeIf {
                    RatedResistanceBandDomain.confirmedDiscreteStock(equipmentId,it)
                } else null
        val matching=verifiedChoices.filter{choice->
            equipmentSearch.isBlank() ||
                choice.displayName.contains(equipmentSearch,ignoreCase=true) ||
                choice.equipmentId.contains(equipmentSearch,ignoreCase=true) ||
                choice.exerciseNames.any {
                    it.contains(equipmentSearch,ignoreCase=true)
                }
        }
        AlertDialog(
            onDismissRequest={if(!state.busy)equipmentEditor=false},
            title={Text("Physical equipment profile")},
            text={
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Find an exercise or machine, then enter only the weights you have physically confirmed.",
                        style=MaterialTheme.typography.bodySmall)
                    if(!manualEquipmentId) {
                        OutlinedTextField(
                            equipmentSearch,{equipmentSearch=it.take(80)},
                            label={Text("Search exercise or equipment")},
                            singleLine=true,modifier=Modifier.fillMaxWidth())
                        if(matching.isEmpty()) {
                            Text("No matching equipment. Try another exercise name or use the advanced editor.",
                                style=MaterialTheme.typography.bodySmall)
                        } else {
                            matching.take(if(showMoreEquipment)24 else 6).forEach { choice ->
                                OutlinedButton(
                                    onClick={
                                        if(equipmentId!=choice.equipmentId)clearStockFields()
                                        equipmentId=choice.equipmentId
                                        basis=choice.basis
                                        kind=when(choice.basis) {
                                            LoadBasis.BARBELL_TOTAL ->
                                                PhysicalStockEditorKind.BARBELL_WITH_PAIRS
                                            LoadBasis.SMITH_TOTAL ->
                                                PhysicalStockEditorKind.SMITH_WITH_PAIRS
                                            LoadBasis.DUMBBELL_PER_HAND,LoadBasis.KETTLEBELL_EACH,
                                            LoadBasis.LANDMINE_ADDED_PLATES ->
                                                PhysicalStockEditorKind.EXACT_LOADS
                                            // Portable physical implements use discrete actual
                                            // weights, not an invented continuous machine stack.
                                            LoadBasis.EXERCISE_OVERRIDE ->
                                                if(choice.equipmentId in setOf(
                                                    "wrist-lever-hammer",
                                                    "wrist-roller-handle",
                                                    "single-weight-plate",
                                                    "hand-gripper-device",
                                                    "neck-extension-device",
                                                    "neck-flexion-device",
                                                    "neck-harness-device",
                                                    "neck-lateral-device",
                                                    "weighted-pullup-resistance",
                                                    RatedResistanceBandDomain.EQUIPMENT_ID))
                                                    PhysicalStockEditorKind.EXACT_LOADS
                                                else PhysicalStockEditorKind.VERIFIED_STACK
                                            else -> PhysicalStockEditorKind.VERIFIED_STACK
                                        }
                                    },
                                    modifier=Modifier.fillMaxWidth()
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(choice.displayName)
                                        Text(choice.exerciseNames.take(2).joinToString(", "),
                                            style=MaterialTheme.typography.bodySmall)
                                        if(choice.equipmentId==equipmentId)
                                            Text("Selected",color=MaterialTheme.colorScheme.primary,
                                                style=MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                            if(matching.size>6)TextButton(
                                onClick={showMoreEquipment=!showMoreEquipment}
                            ) {
                                Text(if(showMoreEquipment)"Show fewer choices"
                                    else "Show more matching equipment")
                            }
                        }
                        TextButton(onClick={
                            manualEquipmentId=true
                            clearStockFields()
                            equipmentId=""
                            kind=PhysicalStockEditorKind.EXACT_LOADS
                            basis=LoadBasis.DUMBBELL_PER_HAND
                        }) {Text("Advanced: enter a unique equipment ID")}
                    } else {
                        Text("Use a stable ID for this physical machine; similar names do not mean equal resistance.",
                            style=MaterialTheme.typography.bodySmall)
                        OutlinedTextField(equipmentId,{
                            if(equipmentId!=it.take(80))clearStockFields()
                            equipmentId=it.take(80)
                        },
                            label={Text("Physical equipment ID")},singleLine=true)
                        TextButton(onClick={
                            manualEquipmentId=false
                            clearStockFields()
                            equipmentId=""
                        }) {Text("Choose from reviewed exercises")}
                    }
                    if(selected!=null && !manualEquipmentId) {
                        Text(if(selected.equipmentId==RatedResistanceBandDomain.EQUIPMENT_ID)
                            "Chosen: rated resistance bands. Enter only manufacturer-labeled nominal tension settings (LB/KG), not estimated constant exercise weights."
                            else "Chosen: ${selected.displayName} · ${kotlinLoadBasisLabel(selected.basis)}. Actual weights are still required.",
                            style=MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        usableKinds.forEach { candidate ->
                            FilterChip(selected=kind==candidate,
                                onClick={if(kind!=candidate)clearStockFields();kind=candidate},
                                label={Text(when(candidate) {
                                    PhysicalStockEditorKind.EXACT_LOADS -> "Exact totals"
                                    PhysicalStockEditorKind.VERIFIED_STACK -> "Stack increments"
                                    PhysicalStockEditorKind.BARBELL_WITH_PAIRS -> "Bar and plate pairs"
                                    PhysicalStockEditorKind.SMITH_WITH_PAIRS -> "Smith and plate pairs"
                                })})
                        }
                    }
                    if(kind !in listOf(PhysicalStockEditorKind.BARBELL_WITH_PAIRS,
                            PhysicalStockEditorKind.SMITH_WITH_PAIRS) &&
                        (manualEquipmentId || selected==null)) {
                        Text("Actual load basis",
                            style=MaterialTheme.typography.labelLarge)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                            LoadBasis.entries.filter {
                                if(kind==PhysicalStockEditorKind.EXACT_LOADS)
                                    it!=LoadBasis.BARBELL_TOTAL
                                else it in listOf(LoadBasis.CABLE_STACK,
                                    LoadBasis.MACHINE_STACK,
                                    LoadBasis.EXERCISE_OVERRIDE)
                            }.forEach { selection ->
                                FilterChip(selected=basis==selection,
                                    onClick={if(basis!=selection)clearStockFields();basis=selection},
                                    label={Text(kotlinLoadBasisLabel(selection))})
                            }
                        }
                    }
                    when(kind) {
                        PhysicalStockEditorKind.EXACT_LOADS ->
                            OutlinedTextField(exact,{exact=it.take(900)},
                                label={Text(if(equipmentId==RatedResistanceBandDomain.EQUIPMENT_ID)
                                    "Rated band tension (${reviewed.gym.unit}), comma-separated"
                                else "Exact ${reviewed.gym.unit} loads${kotlinLoadBasisSuffix(actualBasis)}, comma-separated")},
                                supportingText=if(equipmentId==RatedResistanceBandDomain.EQUIPMENT_ID) {
                                    { Text("Enter discrete positive printed band ratings only. Tension varies with stretch and is NOT equivalent to a dumbbell, stack, or constant load.") }
                                } else if(actualBasis==LoadBasis.KETTLEBELL_EACH) {
                                    { Text(if(equipmentId=="matched-kettlebell-pair")
                                        "Enter each bell's weight only when two matching bells are available."
                                        else "Enter the weight of one bell, including a two-handed swing.") }
                                } else if(actualBasis==LoadBasis.LANDMINE_ADDED_PLATES) {
                                    { Text("Confirm the bar and anchored pivot. Enter added plate totals at the loaded end, excluding bar and base. Enter 0 only when no plates are added; these are not free-bar totals.") }
                                } else null,
                                modifier=Modifier.fillMaxWidth())
                        PhysicalStockEditorKind.VERIFIED_STACK -> {
                            OutlinedTextField(min,{min=it.take(20)},
                                label={Text("Exact minimum load")})
                            OutlinedTextField(step,{step=it.take(20)},
                                label={Text("Confirmed stack increment")})
                            OutlinedTextField(max,{max=it.take(20)},
                                label={Text("Exact maximum load")})
                        }
                        PhysicalStockEditorKind.BARBELL_WITH_PAIRS,
                        PhysicalStockEditorKind.SMITH_WITH_PAIRS -> {
                            val smith=kind==PhysicalStockEditorKind.SMITH_WITH_PAIRS
                            if(smith)Text("Use this machine's confirmed empty-bar resistance, including its counterbalance. Zero is allowed when confirmed. A free bar's weight is not a substitute.",
                                style=MaterialTheme.typography.bodySmall)
                            OutlinedTextField(bar,{bar=it.take(20)},
                                label={Text(if(smith)"Confirmed empty-bar resistance" else "Verified empty bar weight")})
                            OutlinedTextField(plates,{plates=it.take(900)},
                                label={Text("Plate pairs: 2.5x1, 5x2, 25x1")})
                        }
                    }
                }
            },
            confirmButton={Button(onClick={
                val stock=parsed
                if(stock!=null) {
                    controller.upsertStock(equipmentId.trim(),stock)
                    equipmentEditor=false
                }
            },enabled=!state.busy && parsed!=null && basisMatches &&
                equipmentId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$"))) {
                Text("Save verified stock")
            }},
            dismissButton={TextButton(onClick={equipmentEditor=false}) {
                Text("Cancel")
            }}
        )
    }
    if(reviewed!=null && source!=null && editingFixtures) {
        var facilities by remember(source.revision,editingFixtures) {
            mutableStateOf(reviewed.gym.availableFacilities.sorted().joinToString(", "))
        }
        var attachments by remember(source.revision,editingFixtures) {
            mutableStateOf(reviewed.gym.availableAttachments.sorted().joinToString(", "))
        }
        var advancedAttachments by remember(editingFixtures) {
            mutableStateOf(false)
        }
        val facilityIds=parseIds(facilities)
        val attachmentIds=parseIds(attachments)
        val offeredAttachments=remember(source.revision) {
            source.catalog.mapNotNull{it.attachmentId}.distinct().sorted()
        }
        AlertDialog(
            onDismissRequest={if(!state.busy)editingFixtures=false},
            title={Text("Confirmed gym fixtures and attachments")},
            text={
                Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                    NativeKotlinGroupedFacilityChoices(
                        title="Available facilities",raw=facilities,
                        onChange={facilities=it.take(1200)})
                    Text("Cable attachments you physically have",
                        style=MaterialTheme.typography.titleMedium)
                    Text("Select only attachments you can use. Selecting a grip does not change machine stock or workout targets.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    offeredAttachments.forEach {id->
                        val examples=source.catalog.filter{it.attachmentId==id}
                            .map{kotlinExerciseLabel(it.exerciseId)}.distinct()
                        Column {
                            FilterChip(
                                selected=attachmentIds?.contains(id)==true,
                                enabled=!state.busy && attachmentIds!=null,
                                onClick={
                                    val current=attachmentIds ?:emptySet()
                                    val next=if(id in current)current-id else current+id
                                    attachments=next.sorted().joinToString(", ")
                                },
                                label={Text(kotlinGymAttachmentLabel(id))})
                            Text(examples.take(2).joinToString(", "),
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if(offeredAttachments.isEmpty())
                        Text("No cable attachments in the current reviewed catalog.",
                            style=MaterialTheme.typography.bodySmall)
                    TextButton(onClick={advancedAttachments=!advancedAttachments}) {
                        Text(if(advancedAttachments)"Hide advanced attachment IDs"
                            else "Advanced: other physical attachment IDs")
                    }
                    if(advancedAttachments || attachmentIds==null) {
                        OutlinedTextField(attachments,{attachments=it.take(2400)},
                            label={Text("Confirmed attachment IDs")},
                            supportingText={Text("Comma-separated technical IDs for special grips. Nothing is assumed or saved until you confirm.")})
                    }
                }
            },
            confirmButton={Button(onClick={
                if(facilityIds!=null && attachmentIds!=null) {
                    controller.setFacilitiesAndAttachments(
                        facilityIds,attachmentIds)
                    editingFixtures=false
                }
            },enabled=!state.busy && facilityIds!=null &&
                attachmentIds!=null) {Text("Save availability")}},
            dismissButton={TextButton(onClick={editingFixtures=false}) {
                Text("Cancel")
            }}
        )
    }
    removeTarget?.let { id ->
        AlertDialog(onDismissRequest={if(!state.busy)removeTarget=null},
            title={Text("Remove verified equipment?")},
            text={Text("The saved custom program is not erased. Existing exercises using $id will become unavailable for new reviewed prescriptions until compatible verified stock is added again.")},
            confirmButton={Button(onClick={
                controller.removeStock(id);removeTarget=null
            },enabled=!state.busy) {Text("Remove equipment")}},
            dismissButton={TextButton(onClick={removeTarget=null}) {
                Text("Keep equipment")
            }})
    }
}


/** Selection changes the reviewed source only. The UI never invents stock
 * or changes the weight of any issued set. Each confirmation captures the
 * exact displayed source revision, even if the saved list changes meanwhile. */
@Composable
private fun NativeKotlinSavedGymSelector(
    controller:NativeKotlinGymSourceController,
    gyms:io.github.extracteddeath.pursuitiron.nativecoachstore.NativeSavedGymRead.Ready,
    busy:Boolean
) {
    var pending by remember(gyms.sourceRevision){mutableStateOf<ReviewedGymProfile?>(null)}
    var adding by remember(gyms.sourceRevision){mutableStateOf(false)}
    var name by remember(gyms.sourceRevision){mutableStateOf("")}
    var unit by remember(gyms.sourceRevision){mutableStateOf<LoadUnit?>(null)}
    fun label(id:String)=id.replace('-',' ').replace('_',' ').replaceFirstChar{it.uppercase()}
    val id=name.trim().lowercase(java.util.Locale.ROOT).replace(Regex("\\s+"),"-")
    val validId=Regex("^[a-z0-9][a-z0-9_-]{0,79}$").matches(id) &&
        id !in (gyms.inactive.map{it.gymId}+gyms.active.gymId)
    PursuitCard {
        Text("Saved gyms",style=MaterialTheme.typography.titleMedium)
        Text(label(gyms.active.gymId)+" · "+gyms.active.unit.name,
            style=MaterialTheme.typography.titleSmall)
        Text("Current gym",color=MaterialTheme.colorScheme.primary,
            style=MaterialTheme.typography.labelMedium)
        gyms.inactive.forEach{gym->
            key(gym.gymId) {
                OutlinedButton(onClick={pending=gym},enabled=!busy,
                    modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.fillMaxWidth()){
                        Text(label(gym.gymId))
                        Text("${gym.unit.name} · ${gym.confirmedStock.size} confirmed weight sources",
                            style=MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
        OutlinedButton(onClick={name="";unit=null;adding=true},
            enabled=!busy && gyms.inactive.size<15,modifier=Modifier.fillMaxWidth()){
            Text("Add gym")
        }
    }
    pending?.let{gym->
        AlertDialog(onDismissRequest={if(!busy)pending=null},
            title={Text("Switch to ${label(gym.gymId)}?")},
            text={Text("Your current gym's equipment will be saved. Review your plan for the selected gym before starting a new workout. Existing workout targets and history stay unchanged.")},
            confirmButton={Button(onClick={
                pending=null
                controller.selectSavedGym(gyms.sourceRevision,gym.gymId,true)
            },enabled=!busy){Text("Switch gym")}},
            dismissButton={TextButton(onClick={pending=null},enabled=!busy){Text("Cancel")}})
    }
    if(adding)AlertDialog(onDismissRequest={if(!busy)adding=false},
        title={Text("Add a gym")},
        text={Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedTextField(name,{name=it.take(80)},singleLine=true,
                label={Text("Gym name")},modifier=Modifier.fillMaxWidth(),
                supportingText={Text("Use a unique name with letters, numbers, spaces or hyphens.")})
            Text("Weight units",style=MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                LoadUnit.entries.forEach{choice->
                    FilterChip(selected=unit==choice,onClick={unit=choice},
                        label={Text(choice.name)})
                }
            }
            Text("Starts with no confirmed equipment. Add the weights and facilities you actually have. Your previous gym is retained.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={Button(onClick={
            val chosen=unit
            if(chosen!=null && validId){
                adding=false
                controller.addSavedGym(gyms.sourceRevision,id,chosen,true)
            }
        },enabled=!busy && validId && unit!=null){Text("Create and select")}},
        dismissButton={TextButton(onClick={adding=false},enabled=!busy){Text("Cancel")}})
}
