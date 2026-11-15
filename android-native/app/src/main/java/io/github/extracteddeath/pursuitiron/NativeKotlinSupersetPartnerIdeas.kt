package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/** This changes only a pending sheet selection through onChoose. Saving
 * the program remains the separate owner/source/revision-checked action. */
@Composable
internal fun NativeKotlinSupersetPartnerIdeas(
    ideas:CustomSupersetPartnerIdeas,anchorSlotId:String,enabled:Boolean,
    onChoose:(String)->Unit
) {
    var visibleIdeas by rememberSaveable(anchorSlotId){mutableIntStateOf(3)}
    if(ideas.status in setOf(CustomSupersetIdeasStatus.COACHING_OFF,
            CustomSupersetIdeasStatus.SUPERSETS_OFF))return
    PursuitCard {
        Text("Partner ideas",style=MaterialTheme.typography.titleSmall)
        when(ideas.status) {
            CustomSupersetIdeasStatus.READY -> {
                Text("Based on recorded muscle targets and working-set counts. Main lifts remain manual choices.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                ideas.partners.take(visibleIdeas).forEach {idea->
                    val sets=if(idea.workingSets==1)"1 working set" else "${idea.workingSets} working sets"
                    OutlinedButton(onClick={onChoose(idea.slotId)},enabled=enabled,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                            Text("Select ${kotlinExerciseLabel(idea.exerciseId)}",style=MaterialTheme.typography.labelLarge)
                            Text(if(idea.workingSets==idea.anchorWorkingSets)
                                "$sets · matching set count"
                                else "$sets · first movement has ${idea.anchorWorkingSets}",
                                style=MaterialTheme.typography.bodySmall)
                            Text(if(idea.sharedSupportingMuscles.isEmpty())
                                "No shared recorded muscle targets"
                                else "Different direct targets · some supporting targets overlap",
                                style=MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if(visibleIdeas<ideas.partners.size)TextButton(onClick={visibleIdeas+=3},enabled=enabled,
                    modifier=Modifier.heightIn(min=48.dp)){Text("Show more partner ideas")}
                Text("Choosing an idea updates this selection. Use Save superset to change your program.",
                    style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CustomSupersetIdeasStatus.ANCHOR_PROTECTED ->Text(
                "Ideas focus on accessory isolation and core movements. Choose other pairings manually.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            CustomSupersetIdeasStatus.NO_MATCH ->Text(
                "No matching partner ideas in this day. You can still choose a partner from the full list.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            else ->Text("Partner ideas need a matching reviewed exercise source. You can still choose manually.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
