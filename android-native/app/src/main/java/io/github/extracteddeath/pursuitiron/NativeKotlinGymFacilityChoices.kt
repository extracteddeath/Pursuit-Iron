package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier

/** A physical-facility selection is never a verified weight-stock grant.
 * Existing stable IDs remain unchanged; selections are explicit athlete input.
 */
internal data class KotlinGymFacilityGroup(
    val label:String,val choices:List<Pair<String,String>>
)

internal val kotlinGymFacilityGroups=listOf(
    KotlinGymFacilityGroup("Free weights",listOf(
        "dumbbells" to "Dumbbells",
        "kettlebells" to "Kettlebells",
        "bands" to "Resistance bands")),
    KotlinGymFacilityGroup("Bars",listOf(
        "barbell" to "Barbell",
        "ez-curl-bar" to "EZ curl bar",
        "trap-hex-bar" to "Trap / hex bar",
        "safety-squat-bar" to "Safety squat bar",
        "landmine" to "Landmine")),
    KotlinGymFacilityGroup("Benches & stations",listOf(
        "flat-bench" to "Flat bench",
        "incline-bench" to "Incline bench",
        "decline-bench" to "Decline bench",
        "preacher-bench" to "Preacher bench",
        "seal-bench" to "Seal row bench",
        "rack" to "Squat rack",
        "pullup-bar" to "Pull-up bar",
        "dip-station" to "Dip station",
        "glute-ham-developer" to "Glute-ham developer",
        "reverse-hyper" to "Reverse hyper")),
    KotlinGymFacilityGroup("Cables & general machines",listOf(
        "cable-machine" to "Cable machine",
        "smith-machine" to "Smith machine",
        "selectorized-machines" to "Selectorized machines",
        "assisted-pullup-dip" to "Assisted pull-up / dip")),
    KotlinGymFacilityGroup("Leg machines",listOf(
        "leg-press" to "Leg press",
        "hack-squat" to "Hack squat",
        "belt-squat" to "Belt squat",
        "leg-extension" to "Leg extension",
        "leg-curl" to "Leg curl",
        "calf-machine" to "Calf machine")),
    KotlinGymFacilityGroup("Hip & glute machines",listOf(
        "hip-thrust-machine" to "Hip thrust machine",
        "glute-kickback" to "Glute kickback",
        "hip-abduction" to "Hip abduction",
        "hip-adduction" to "Hip adduction")),
    KotlinGymFacilityGroup("Upper-body machines",listOf(
        "chest-press" to "Chest press",
        "pec-deck" to "Pec deck",
        "shoulder-press" to "Shoulder press",
        "lateral-raise-machine" to "Lateral raise",
        "row-machine" to "Row",
        "pullover-machine" to "Pullover",
        "shrug-machine" to "Shrug")),
    KotlinGymFacilityGroup("Arm & core machines",listOf(
        "curl-machine" to "Curl",
        "triceps-extension-machine" to "Triceps extension",
        "crunch-machine" to "Crunch")),
    // These physical fixtures are needed by reviewed squat/step and
    // bodyweight variants; selecting them never invents working load stock.
    KotlinGymFacilityGroup("Workout accessories",listOf(
        "squat-box" to "Squat box",
        "heel-wedge" to "Heel-elevation wedge",
        "step-platform" to "Step platform",
        "exercise-sliders" to "Exercise sliders",
        "ab-wheel" to "Ab wheel",
        "landmine-base" to "Landmine base / anchor",
        "tbar-handle" to "T-bar row handle",
        "wide-tbar-handle" to "Wide T-bar row handle",
        "safety-pins" to "Rack safety pins",
        "press-board" to "Bench press board",
        "nordic-anchor" to "Nordic curl anchor",
        "pushup-handles" to "Push-up handles",
        "secure-doorway-anchor" to "Secure doorway exercise anchor",
        "stability-ball" to "Stability ball"))
)

internal fun kotlinGymFacilityLabel(id:String):String =
    kotlinGymFacilityGroups.asSequence().flatMap{it.choices.asSequence()}
        .firstOrNull{it.first==id}?.second ?:id

/** Grouped, touch-accessible, non-destructive availability editor.
 * Unknown existing IDs survive named toggles and remain visible in Advanced.
 */
@Composable
internal fun NativeKotlinGroupedFacilityChoices(
    title:String,raw:String,onChange:(String)->Unit
) {
    val ids=raw.split(',').map{it.trim()}.filter{it.isNotEmpty()}
    val pattern=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
    val valid=ids.size<=128 && ids.distinct().size==ids.size &&
        ids.all{pattern.matches(it)}
    val selected=ids.toSet()
    val known=kotlinGymFacilityGroups.flatMap{it.choices.map{choice->choice.first}}.toSet()
    var advanced by rememberSaveable(title){mutableStateOf(false)}
    PursuitSectionHeading(title,
        "Select only equipment you have. Verified loads and attachments are recorded separately.")
    kotlinGymFacilityGroups.forEach { group ->
        val groupSelected=group.choices.count{it.first in selected}
        var expanded by rememberSaveable(title,group.label) {
            mutableStateOf(groupSelected>0)
        }
        Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,
            shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth()) {
            Column(Modifier.padding(PursuitSpace.sm),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                TextButton(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.SpaceBetween) {
                        Text(group.label,style=MaterialTheme.typography.titleSmall)
                        Text("$groupSelected / ${group.choices.size}",
                            style=MaterialTheme.typography.labelMedium)
                    }
                }
                if(expanded)group.choices.forEach { (id,label) ->
                    NativeKotlinSetupConfirmation(label,id in selected,
                        onChange={checked->
                            val updated=if(checked)selected+id else selected-id
                            onChange(updated.sorted().joinToString(","))
                        },enabled=valid)
                }
            }
        }
    }
    val extras=selected-known
    if(extras.isNotEmpty())Text(
        "Other saved facilities: ${extras.sorted().joinToString()}",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    TextButton(onClick={advanced=!advanced}) {
        Text(if(advanced)"Hide advanced facility IDs" else "Advanced facility IDs")
    }
    if(advanced || !valid)OutlinedTextField(
        value=raw,onValueChange=onChange,modifier=Modifier.fillMaxWidth(),
        label={Text("Verified facility IDs")},
        supportingText={Text("Additional saved IDs are preserved when using named choices.")},
        isError=!valid)
}
