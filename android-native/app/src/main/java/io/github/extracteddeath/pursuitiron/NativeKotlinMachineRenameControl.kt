package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.KotlinPhysicalMachine

/**
 * A physical station has a stable private ID; only its athlete-facing label
 * changes. The existing owner-fenced Gym controller commits the rename.
 *
 * Never rewrite a set receipt, progression baseline or machine identifier.
 * Keep the editor open on a rejected save, dismiss only when the source
 * actually publishes the requested name.
 */
@Composable
internal fun NativeKotlinMachineRenameControl(
    gymId:String,
    machine:KotlinPhysicalMachine,
    peers:List<KotlinPhysicalMachine>,
    busy:Boolean,
    onRename:(String)->Unit
) {
    var editing by rememberSaveable(gymId,machine.id) { mutableStateOf(false) }
    var draft by rememberSaveable(gymId,machine.id,machine.label) {
        mutableStateOf(machine.label)
    }
    var pendingName by remember(gymId,machine.id) { mutableStateOf<String?>(null) }
    LaunchedEffect(machine.label,pendingName) {
        if(pendingName!=null && machine.label==pendingName) {
            editing=false
            pendingName=null
        }
    }

    TextButton(onClick={
        if(editing) draft=machine.label // Cancel must discard unsaved text.
        editing=!editing
        pendingName=null
    },enabled=!busy,modifier=Modifier.heightIn(min=48.dp)) {
        Text(if(editing) "Cancel rename" else "Rename this machine")
    }
    if(editing) {
        val candidate=draft.trim()
        val duplicate=peers.any {
            it.id!=machine.id && it.label.trim().equals(candidate,ignoreCase=true)
        }
        val changed=candidate!=machine.label
        OutlinedTextField(
            value=draft,
            onValueChange={draft=it.take(80);pendingName=null},
            label={Text("Physical machine name")},
            supportingText={
                Text(when {
                    candidate.isBlank() -> "Enter a name for this physical station."
                    duplicate -> "Another station at this gym already uses that name."
                    else -> "Only the label changes. Recorded sets and progression stay separate."
                })
            },
            singleLine=true,
            isError=candidate.isBlank() || duplicate,
            enabled=!busy,
            modifier=Modifier.fillMaxWidth()
        )
        Button(
            onClick={
                pendingName=candidate
                onRename(candidate)
            },
            enabled=!busy && changed && candidate.isNotBlank() && !duplicate,
            modifier=Modifier.heightIn(min=48.dp)
        ) { Text("Save machine name") }
    }
}
