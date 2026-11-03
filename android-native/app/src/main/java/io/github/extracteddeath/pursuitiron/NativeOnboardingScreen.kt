package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun NativeOnboardingScreen(
    state: NativeRuntimeState,onReview: (NativeProgramProfile)->Unit,
    onSave: ()->Unit,onEdit: ()->Unit,onExplore: ()->Unit
) {
    val initial=state.proposal?.profile ?: NativeProgramProfile()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf(initial.name) }
    var goal by rememberSaveable { mutableStateOf(initial.goal) }
    var experience by rememberSaveable { mutableStateOf(initial.experience) }
    var unit by rememberSaveable { mutableStateOf(initial.unit) }
    var split by rememberSaveable { mutableStateOf(initial.split) }
    var days by rememberSaveable { mutableIntStateOf(initial.days) }
    var session by rememberSaveable { mutableStateOf(initial.session) }
    var equipment by rememberSaveable { mutableStateOf(ArrayList(initial.equipment)) }
    var bodyweight by rememberSaveable { mutableStateOf(initial.allowBodyweight) }
    var supersets by rememberSaveable { mutableStateOf(initial.allowSupersets) }
    var barbellCap by rememberSaveable { mutableStateOf<Int?>(initial.barbellCap) }
    var focus by rememberSaveable { mutableStateOf(HashMap(initial.focus)) }
    var reduced by rememberSaveable { mutableStateOf(ArrayList(initial.reduced)) }
    var inventoryFields by rememberSaveable {
        mutableStateOf(HashMap(NativeGymInventoryCodec.toDraft(initial.loadingInventory)))
    }
    // Unit changes do not reinterpret physical plates, bars, dumbbells or
    // machine stacks. An accidental toggle must not erase a checked gym setup.
    var pendingUnit by rememberSaveable { mutableStateOf<String?>(null) }
    if(pendingUnit!=null) AlertDialog(
        onDismissRequest={pendingUnit=null},
        title={Text("Change weight units?")},
        text={Text("Your verified gym weights are recorded in $unit. Switching to $pendingUnit will clear those entries because converting plate stock and machine settings could give incorrect selectable loads.")},
        confirmButton={Button(onClick={
            val next=pendingUnit
            if(next in listOf("lb","kg") && next!=unit) {
                unit=checkNotNull(next)
                inventoryFields=HashMap()
            }
            pendingUnit=null
        }) { Text("Change units and clear weights") }},
        dismissButton={TextButton(onClick={pendingUnit=null}) { Text("Keep my weights") }}
    )
    val inventoryValidation = NativeGymInventoryCodec.fromDraft(inventoryFields, unit)
    val splits=state.options?.splits ?: listOf(NativeProgramSplit("full_body","Full body",listOf(3,4,5,6)))
    val selected=splits.firstOrNull { it.id==split } ?: splits.first()
    LaunchedEffect(state.options) {
        split=selected.id
        if (days !in selected.days) days=selected.days.first()
    }
    val proposal=state.proposal
    // Back navigates onboarding steps rather than unexpectedly closing the
    // app. Cancel an in-flight preview build; never cancel an in-flight
    // canonical program save that might already be committing to disk.
    // A visible unit-change dialog receives Back through its own window.
    BackHandler(enabled=pendingUnit==null && (proposal!=null || step>0)) {
        when {
            state.busy && proposal==null -> onEdit()
            state.busy -> Unit
            proposal!=null -> { onEdit();step=0 }
            else -> step=0
        }
    }
    val currentStep=if(proposal!=null) 2 else step
    PageHeading("STEP ${currentStep+1} OF 3",when(currentStep) {
        0->"Train your way";1->"Your equipment";else->"Review your program"
    },when(currentStep) {
        0->"Choose your goal and the week you can commit to."
        1->"Choose the equipment and muscles your plan should emphasize."
        else->"Check the generated plan before saving it on this device."
    })
    LinearProgressIndicator(progress={ (currentStep+1)/3f },modifier=Modifier.fillMaxWidth())
    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
    state.error?.let { Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium) }

    if (proposal!=null) {
        PursuitCard(highlighted=true) {
            Text(proposal.program.name,style=MaterialTheme.typography.headlineSmall)
            Text("${proposal.program.days.size} training days · ${proposal.program.weeks}-week starting block")
            Text("${when(proposal.profile.goal){"strength"->"Strength";"hypertrophy"->"Muscle growth";else->"Strength & size"}} · ${proposal.profile.unit}")
            if(proposal.profile.focus.isNotEmpty())
                Text("Requested priorities: " +
                    proposal.profile.focus.toSortedMap().entries.joinToString(", ") { (part,level) ->
                        "${part.replace('_',' ')} ${if(level==2)"· specialize" else "· focus"}"
                    })
            if(proposal.profile.reduced.isNotEmpty())
                Text("Requested reduced work: " +
                    proposal.profile.reduced.joinToString(", ") { it.replace('_',' ') })
            proposal.program.days.forEachIndexed { index,day ->
                ReadOnlyTrainingDayRow(index,day.label,day.exerciseIds.size)
            }
        }
        PursuitPrimaryAction(label=if(state.busy)"Saving…" else "Save program",
            enabled=!state.busy && !state.blocked,onClick=onSave)
        OutlinedButton(onClick={onEdit();step=0},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
            Text("Edit choices")
        }
        return
    }

    if (step==0) {
        PursuitFormSection("Program identity","Name your program and choose its main goal.") {
        OutlinedTextField(value=name,onValueChange={if(it.length<=60)name=it},enabled=!state.busy,
            label={Text("Program name")},singleLine=true,modifier=Modifier.fillMaxWidth())
        SectionLabel("Goal")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("both" to "Strength & size","hypertrophy" to "Muscle growth","strength" to "Strength").forEach { (id,label) ->
                PreviewChoice(label,goal==id,!state.busy){goal=id}
            }
        }
        }
        PursuitFormSection("Your training week","Choose a manageable schedule and training structure.") {
        SectionLabel("Training experience")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("beginner" to "Beginner","intermediate" to "Intermediate","advanced" to "Advanced").forEach { (id,label) ->
                PreviewChoice(label,experience==id,!state.busy){experience=id}
            }
        }
        SectionLabel("Split")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            splits.forEach { option -> PreviewChoice(option.label,split==option.id,!state.busy) {
                split=option.id;if(days !in option.days)days=option.days.first()
            } }
        }
        SectionLabel("Days per week")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            selected.days.forEach { n -> PreviewChoice(n.toString(),days==n,!state.busy){days=n} }
        }
        SectionLabel("Time per session")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("s60" to "40–60 min","s90" to "60–90 min","s120p" to "120+ min").forEach { (id,label) ->
                PreviewChoice(label,session==id,!state.busy){session=id}
            }
        }
        }
        PursuitPrimaryAction(label="Continue",enabled=name.isNotBlank() && !state.busy,
            onClick={step=1})
    } else {
        PursuitFormSection("Available equipment",
            "Choose what you can actually use. These choices constrain the program.") {
        NativeGroupedEquipmentPicker(
            available = state.options?.equipment ?: NativeEquipmentCatalog.allIds,
            selected = equipment,
            enabled = !state.busy,
            onSelectionChanged = { equipment = ArrayList(it) }
        )
        if(equipment.isEmpty()) Text("Choose at least one equipment option.",
            color=MaterialTheme.colorScheme.error)
        }
        PursuitFormSection("Verified loading",
            "Optional bars, plates and selectable weights prevent impossible loading suggestions.") {
        SectionLabel("Weight units")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("lb","kg").forEach { id -> PreviewChoice(id,unit==id,!state.busy){
                if(unit!=id) {
                    if(inventoryFields.values.any { it.isNotBlank() }) pendingUnit=id
                    else { unit=id; inventoryFields=HashMap() }
                }
            } }
        }
        NativeGymInventoryEditor(unit,inventoryFields,{ key,value ->
            inventoryFields=HashMap(inventoryFields).apply { put(key,value) }
        },inventoryValidation,!state.busy)
        }
        PursuitFormSection("Training preferences",
            "Personalize exercise selection without changing your verified gym weights.") {
        PreviewSettingSwitch("Bodyweight exercises","Include movements such as push-ups and pull-ups.",bodyweight,enabled=!state.busy) {
            if(!state.busy)bodyweight=it
        }
        PreviewSettingSwitch("Supersets","Allow compatible exercises to be paired.",supersets,enabled=!state.busy) {
            if(!state.busy)supersets=it
        }
        SectionLabel("Barbell movements per day")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (listOf<Int?>(null)+ (0..6).map { it }).forEach { cap ->
                PreviewChoice(cap?.toString() ?: "No limit",barbellCap==cap,!state.busy){barbellCap=cap}
            }
        }
        SectionLabel("Muscle priorities")
        NativeMusclePrioritySelector(
            muscles=state.options?.muscles.orEmpty(),
            focus=focus, reduced=reduced,
            enabled=!state.busy
        ) { muscle,mode ->
            val nextFocus=HashMap(focus)
            val nextReduced=ArrayList(reduced)
            nextFocus.remove(muscle)
            nextReduced.remove(muscle)
            if(mode in 1..2 && nextFocus.size<3) nextFocus[muscle]=mode
            else if(mode==-1 && nextReduced.size<3) nextReduced.add(muscle)
            focus=nextFocus
            reduced=nextReduced
        }
        }
        PursuitPrimaryAction(
            label=if(state.busy)"Building your program…" else "Review generated plan",
            onClick={onReview(NativeProgramProfile(name,goal,experience,unit,split,days,session,
                equipment.toList(),bodyweight,supersets,barbellCap,focus.toMap(),reduced.toList(),
                inventoryValidation.json))},
            enabled=state.ready && !state.blocked && !state.busy && equipment.isNotEmpty() &&
                name.isNotBlank() && inventoryValidation.valid
        )
        OutlinedButton(onClick={if(state.busy)onEdit() else step=0},modifier=Modifier.fillMaxWidth()) {
            Text(if(state.busy) "Cancel" else "Back")
        }
    }
    if(!state.ready && !state.loading) TextButton(onClick=onExplore,modifier=Modifier.fillMaxWidth()) { Text("Explore preview") }
}
