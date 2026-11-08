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

private enum class PhysicalStockEditorKind {
    EXACT_LOADS, VERIFIED_STACK, BARBELL_WITH_PAIRS
}

private fun exactLoadList(text:String):List<BigDecimal>? =
    runCatching {
        val values=text.split(",").map{BigDecimal(it.trim())}
        values.takeIf {it.isNotEmpty() &&
            values.size<=250 && values.all{it>BigDecimal.ZERO}}
    }.getOrNull()

private fun barbellPairs(text:String):Map<BigDecimal,Int>? =
    runCatching {
        val parts=text.split(",").map { raw ->
            val pair=raw.trim().split(Regex("[xX]"))
            require(pair.size==2)
            BigDecimal(pair[0].trim()) to pair[1].trim().toInt()
        }
        require(parts.isNotEmpty() && parts.size<=12 &&
            parts.map{it.first.stripTrailingZeros()}.distinct().size==parts.size)
        require(parts.all { it.first>BigDecimal.ZERO && it.second in 1..24 })
        parts.toMap()
    }.getOrNull()

/** Never adds an invented implement, load, bar weight or stack interval. */
private fun parseVerifiedStock(
    unit:LoadUnit,kind:PhysicalStockEditorKind,basis:LoadBasis,
    weights:String,minimum:String,increment:String,maximum:String,
    barWeight:String,platePairs:String
):VerifiedLoadStock? = runCatching {
    when(kind) {
        PhysicalStockEditorKind.EXACT_LOADS -> {
            require(basis!=LoadBasis.BARBELL_TOTAL)
            VerifiedLoadStock.Totals(unit,basis,requireNotNull(exactLoadList(weights)))
        }
        PhysicalStockEditorKind.VERIFIED_STACK -> {
            require(basis in listOf(LoadBasis.CABLE_STACK,
                LoadBasis.MACHINE_STACK,LoadBasis.EXERCISE_OVERRIDE))
            VerifiedLoadStock.Stack(unit,basis,BigDecimal(minimum.trim()),
                BigDecimal(increment.trim()),BigDecimal(maximum.trim()))
        }
        PhysicalStockEditorKind.BARBELL_WITH_PAIRS ->
            VerifiedLoadStock.Barbell(unit,BigDecimal(barWeight.trim()),
                requireNotNull(barbellPairs(platePairs)))
    }
}.getOrNull()

private fun parseIds(text:String):Set<String>? {
    val values=text.split(",").map{it.trim()}.filter{it.isNotBlank()}
    val pattern=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    return if(values.size<=128 && values.distinct().size==values.size &&
        values.all{pattern.matches(it)}) values.toSet() else null
}

/**
 * Candidate-only native equipment setup, using Pursuit shared M3 components.
 * The full native onboarding/catalog curator must call the explicit source
 * publication API first. This is NOT attached to legacy MainActivity.
 */
@Composable
internal fun NativeKotlinGymSourceCandidateScreen(
    controller:NativeKotlinGymSourceController,
    reviewedSetupDraft:AthleteSetupDraft?=null
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val source=state.source
    LaunchedEffect(controller) {controller.open()}
    val reviewed=source?.setup?.reviewed
    var editingFixtures by remember {mutableStateOf(false)}
    var showGymDetails by rememberSaveable {mutableStateOf(false)}
    var showMovementCatalog by rememberSaveable {mutableStateOf(false)}
    var equipmentEditor by remember {mutableStateOf(false)}
    var removeTarget by remember {mutableStateOf<String?>(null)}
    var equipmentId by rememberSaveable {mutableStateOf("")}
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
                            "${stock.basis.name.lowercase().replace('_',' ')} · " +
                            stock.loads.joinToString(", ") {it.toPlainString()} +
                            " ${stock.unit}"
                        is VerifiedLoadStock.Stack ->
                            "${stock.basis.name.lowercase().replace('_',' ')} · " +
                            "${stock.minimum.toPlainString()} to ${stock.maximum.toPlainString()}" +
                            " in ${stock.increment.toPlainString()} ${stock.unit} steps"
                        is VerifiedLoadStock.Barbell ->
                            "Bar ${stock.barWeight.toPlainString()} ${stock.unit} · " +
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
        PursuitCard {
            PursuitSectionHeading("Reviewed exercises",
                "${source.catalog.size} exercise definitions. Viewing them never edits your saved gym.")
            TextButton(onClick={showMovementCatalog=!showMovementCatalog}) {
                Text(if(showMovementCatalog)"Hide exercises" else "Show exercise catalog")
            }
            if(showMovementCatalog)source.catalog.forEach { item ->
                Text(kotlinExerciseLabel(item.exerciseId),
                    style=MaterialTheme.typography.bodyMedium)
                if(showGymDetails)Text(
                    "${item.familyId}/${item.variantId} · " +
                    "Equipment ${item.equipmentId ?: "bodyweight"} · " +
                    "Attachment ${item.attachmentId ?: "none"}",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    if(reviewed!=null && source!=null && equipmentEditor) {
        val parsed=parseVerifiedStock(reviewed.gym.unit,kind,basis,
            exact,min,step,max,bar,plates)
        AlertDialog(
            onDismissRequest={if(!state.busy)equipmentEditor=false},
            title={Text("Physical equipment profile")},
            text={
                Column(Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("Use a stable implement ID, e.g. cable-1. Exact weights and increments must be physically confirmed.",
                        style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(equipmentId,{equipmentId=it.take(80)},
                        label={Text("Verified equipment ID")},singleLine=true)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        PhysicalStockEditorKind.entries.forEach { candidate ->
                            FilterChip(selected=kind==candidate,
                                onClick={kind=candidate},
                                label={Text(candidate.name.lowercase().replace('_',' '))})
                        }
                    }
                    if(kind!=PhysicalStockEditorKind.BARBELL_WITH_PAIRS) {
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
                                    onClick={basis=selection},
                                    label={Text(selection.name.replace('_',' ').lowercase())})
                            }
                        }
                    }
                    when(kind) {
                        PhysicalStockEditorKind.EXACT_LOADS ->
                            OutlinedTextField(exact,{exact=it.take(900)},
                                label={Text("Exact ${reviewed.gym.unit} loads, comma-separated")},
                                modifier=Modifier.fillMaxWidth())
                        PhysicalStockEditorKind.VERIFIED_STACK -> {
                            OutlinedTextField(min,{min=it.take(20)},
                                label={Text("Exact minimum load")})
                            OutlinedTextField(step,{step=it.take(20)},
                                label={Text("Confirmed stack increment")})
                            OutlinedTextField(max,{max=it.take(20)},
                                label={Text("Exact maximum load")})
                        }
                        PhysicalStockEditorKind.BARBELL_WITH_PAIRS -> {
                            OutlinedTextField(bar,{bar=it.take(20)},
                                label={Text("Verified empty bar weight")})
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
            },enabled=!state.busy && parsed!=null &&
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
        val facilityIds=parseIds(facilities)
        val attachmentIds=parseIds(attachments)
        AlertDialog(
            onDismissRequest={if(!state.busy)editingFixtures=false},
            title={Text("Confirmed gym fixtures and attachments")},
            text={
                Column(Modifier.heightIn(max=480.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                    NativeKotlinGroupedFacilityChoices(
                        title="Available facilities",raw=facilities,
                        onChange={facilities=it.take(1200)})
                    Text("Attachments are separate from facilities; no attachment is inferred.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(attachments,{attachments=it.take(1200)},
                        label={Text("Confirmed attachment IDs")},
                        supportingText={Text("Comma-separated; only attachments physically in your gym.")})
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
