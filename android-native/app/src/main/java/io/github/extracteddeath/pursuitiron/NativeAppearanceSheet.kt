package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.unit.dp

/**
 * Original 20 native theme choices (10 light and 10 dark).
 * Color selections are UI preferences only, never training-store writes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeAppearanceSheet(selectedId:String,onSelect:(String)->Unit,onDismiss:()->Unit) {
    ModalBottomSheet(onDismissRequest=onDismiss) {
        Column(
            modifier=Modifier.fillMaxWidth().heightIn(max=610.dp)
                .verticalScroll(rememberScrollState()).padding(horizontal=PursuitSpace.md,
                    vertical=PursuitSpace.xs),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
        ) {
            Text("Appearance",style=MaterialTheme.typography.headlineSmall)
            Text("Original Pursuit Iron themes. The selection changes colors only and is saved on this device.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(false,true).forEach { light ->
                Text(if(light)"Light themes" else "Dark themes",
                    style=MaterialTheme.typography.titleMedium)
                PursuitThemes.all.filter {it.isLight==light}.forEach { palette ->
                    Surface(
                        modifier=Modifier.fillMaxWidth().selectable(
                            selected=selectedId==palette.id,role=Role.RadioButton,
                            onClick={onSelect(palette.id)}),
                        shape=MaterialTheme.shapes.large,
                        color=if(selectedId==palette.id)MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        Row(modifier=Modifier.fillMaxWidth().padding(PursuitSpace.sm),
                            verticalAlignment=Alignment.CenterVertically,
                            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                            Surface(color=palette.background,shape=MaterialTheme.shapes.medium,
                                modifier=Modifier.width(76.dp)) {
                                Row(horizontalArrangement=Arrangement.spacedBy(4.dp),
                                    modifier=Modifier.padding(8.dp)) {
                                    Surface(color=palette.accent,shape=MaterialTheme.shapes.small,
                                        modifier=Modifier.heightIn(min=32.dp).weight(1f)) {}
                                    Surface(color=palette.card,shape=MaterialTheme.shapes.small,
                                        modifier=Modifier.heightIn(min=32.dp).weight(1f)) {}
                                }
                            }
                            Text(palette.name,modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.titleMedium)
                            if(selectedId==palette.id) Text("Selected",
                                color=LocalPursuitPalette.current.accentInk,
                                style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            OutlinedButton(onClick=onDismiss,modifier=Modifier.fillMaxWidth()) {
                Text("Done")
            }
        }
    }
}
