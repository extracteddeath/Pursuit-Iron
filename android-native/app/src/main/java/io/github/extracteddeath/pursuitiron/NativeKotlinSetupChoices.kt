package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/** Named UI choices retain the exact source IDs, including advanced entries. */
@Composable
internal fun NativeKotlinNamedSetupChoices(
    title:String,
    raw:String,
    choices:List<Pair<String,String>>,
    onChange:(String)->Unit,
    searchable:Boolean=false
) {
    var search by rememberSaveable(title){mutableStateOf("")}
    var advanced by rememberSaveable(title){mutableStateOf(false)}
    val entries=raw.split(',').map{it.trim()}.filter{it.isNotBlank()}
    val valid=entries.distinct().size==entries.size &&
        entries.all{it.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$"))}
    val selected=entries.toSet()
    Text(title,style=MaterialTheme.typography.titleMedium)
    if(searchable)OutlinedTextField(search,{search=it},
        label={Text("Find an exercise")},singleLine=true,
        modifier=Modifier.fillMaxWidth())
    val visible=choices.filter{it.second.contains(search,ignoreCase=true)}
    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
        visible.forEach{(id,label)->
            NativeKotlinSetupConfirmation(label,id in selected,{checked->
                val updated=if(checked)selected+id else selected-id
                onChange(updated.sorted().joinToString(","))
            },enabled=valid)
        }
    }
    if(visible.isEmpty())Text("No matching exercises.",
        style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={advanced=!advanced}){
        Text(if(advanced)"Hide advanced IDs" else "Advanced IDs")
    }
    if(advanced || !valid)OutlinedTextField(raw,onChange,
        label={Text("Source IDs · comma separated")},
        supportingText={Text("Additional saved IDs are preserved when you use the named choices.")},
        isError=!valid,modifier=Modifier.fillMaxWidth())
}

/** One accessible touch target for each explicit user confirmation. */
@Composable
internal fun NativeKotlinSetupConfirmation(
    label:String,checked:Boolean,onChange:(Boolean)->Unit,enabled:Boolean=true
) {
    Row(Modifier.fillMaxWidth().toggleable(value=checked,role=Role.Checkbox,
        enabled=enabled,onValueChange=onChange).padding(vertical=PursuitSpace.xs),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
        Checkbox(checked=checked,onCheckedChange=null,enabled=enabled)
        Text(label,modifier=Modifier.weight(1f),
            style=MaterialTheme.typography.bodyMedium)
    }
}

internal val kotlinSetupFacilities=listOf(
    "flat-bench" to "Flat bench",
    "rack" to "Squat rack",
    "incline-bench" to "Incline bench",
    "pullup-bar" to "Pull-up bar"
)
