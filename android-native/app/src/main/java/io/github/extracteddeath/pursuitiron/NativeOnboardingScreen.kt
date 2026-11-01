package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.*

private val equipmentLabels=mapOf("barbell" to "Barbell","rack" to "Rack","bench" to "Bench",
    "dumbbell" to "Dumbbells","cable" to "Cables","machine" to "Machines","smith" to "Smith machine",
    "pullup" to "Pull-up bar","dip" to "Dip station","legpress" to "Leg press","hacksquat" to "Hack squat",
    "legext" to "Leg extension","legcurl" to "Leg curl","calfmachine" to "Calf machine",
    "ezbar" to "EZ bar","kettlebell" to "Kettlebells","bands" to "Bands")

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
    val splits=state.options?.splits ?: listOf(NativeProgramSplit("full_body","Full body",listOf(3,4,5,6)))
    val selected=splits.firstOrNull { it.id==split } ?: splits.first()
    LaunchedEffect(state.options) {
        split=selected.id
        if (days !in selected.days) days=selected.days.first()
    }
    val proposal=state.proposal
    val currentStep=if(proposal!=null) 2 else step
    PageHeading("STEP ${currentStep+1} OF 3",when(currentStep) {
        0->"Train your way";1->"Your equipment";else->"Review your program"
    },when(currentStep) {
        0->"Choose your goal and the week you can commit to."
        1->"Your plan will use the equipment you select."
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
            proposal.program.days.forEachIndexed { index,day ->
                ReadOnlyTrainingDayRow(index,day.label,day.exerciseIds.size)
            }
        }
        Button(onClick=onSave,enabled=!state.busy && !state.blocked,modifier=Modifier.fillMaxWidth()) {
            Text(if(state.busy) "Saving…" else "Save program")
        }
        OutlinedButton(onClick={onEdit();step=0},enabled=!state.busy,modifier=Modifier.fillMaxWidth()) {
            Text("Edit choices")
        }
        return
    }

    if (step==0) {
        OutlinedTextField(value=name,onValueChange={if(it.length<=60)name=it},enabled=!state.busy,
            label={Text("Program name")},singleLine=true,modifier=Modifier.fillMaxWidth())
        SectionLabel("Goal")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("both" to "Strength & size","hypertrophy" to "Muscle growth","strength" to "Strength").forEach { (id,label) ->
                PreviewChoice(label,goal==id,!state.busy){goal=id}
            }
        }
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
        Button(onClick={step=1},enabled=name.isNotBlank() && !state.busy,modifier=Modifier.fillMaxWidth()) { Text("Continue") }
    } else {
        SectionLabel("Available equipment")
        FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            (state.options?.equipment ?: equipmentLabels.keys.toList()).forEach { id ->
                PreviewChoice(equipmentLabels[id] ?: id,id in equipment,!state.busy) {
                    equipment=ArrayList(equipment).apply { if(id in this) remove(id) else add(id) }
                }
            }
        }
        SectionLabel("Weight units")
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("lb","kg").forEach { id -> PreviewChoice(id,unit==id,!state.busy){unit=id} }
        }
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
        if(equipment.isEmpty()) Text("Choose at least one equipment option.",color=MaterialTheme.colorScheme.error)
        Button(onClick={onReview(NativeProgramProfile(name,goal,experience,unit,split,days,session,
            equipment.toList(),bodyweight,supersets,barbellCap))},
            enabled=state.ready && !state.blocked && !state.busy && equipment.isNotEmpty() && name.isNotBlank(),
            modifier=Modifier.fillMaxWidth()) { Text(if(state.busy) "Building your program…" else "Review generated plan") }
        OutlinedButton(onClick={if(state.busy)onEdit() else step=0},modifier=Modifier.fillMaxWidth()) {
            Text(if(state.busy) "Cancel" else "Back")
        }
    }
    if(!state.ready && !state.loading) TextButton(onClick=onExplore,modifier=Modifier.fillMaxWidth()) { Text("Explore preview") }
}
