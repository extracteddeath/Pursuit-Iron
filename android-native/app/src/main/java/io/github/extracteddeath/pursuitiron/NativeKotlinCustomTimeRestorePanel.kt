package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomTimeUndoPreview
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/** This summary is display evidence only: the native store rechecks owner,
 * signed consent, the exact Full preview and the immutable prepared checksum
 * before any replacement. Never synthesize an issued workout here. */
internal data class NativeKotlinFullRestoreSlot(
    val exerciseId:String,
    val originalPosition:Int,
    val originalWorkingSets:Int,
    val returningWorkingSets:Int,
    val returningWarmups:Int,
    val linkedSuperset:Boolean
)

internal data class NativeKotlinFullRestoreSummary(
    val retainedWorkingSets:Int,
    val originalWorkingSets:Int,
    val returningWorkingSets:Int,
    val returningWarmups:Int,
    val slots:List<NativeKotlinFullRestoreSlot>
)

/**
 * Reject display authority when the Full and shortened views don't describe
 * the same source, set order, or immutable surviving prescriptions. No label
 * or count is inferred from a guessed exercise or missing history record.
 */
internal object NativeKotlinCustomRestorePresentation {
    fun inspect(
        shorter:CustomWorkoutDaySnapshot,
        full:CustomWorkoutDaySnapshot
    ):NativeKotlinFullRestoreSummary? {
        if(shorter.shortSessionLimitMinutes !in setOf(20,30,45) ||
            full.shortSessionLimitMinutes!=null ||
            full.shortSessionOmittedSetKeys.isNotEmpty() ||
            shorter.stamp!=full.stamp ||
            full.slots.isEmpty() || full.slots.size>32)return null
        val original=full.slots.flatMap{it.sets}
        val retained=shorter.slots.flatMap{it.sets}
        if(original.size !in 1..256 ||
            original.map{it.setKey}.distinct().size!=original.size ||
            retained.map{it.setKey}.distinct().size!=retained.size)return null
        val keptIds=retained.map{it.setKey}.toSet()
        val removedIds=original.map{it.setKey}.filterNot{it in keptIds}
        if(removedIds!=shorter.shortSessionOmittedSetKeys ||
            original.filter{it.setKey in keptIds}!=retained)return null
        val removedSet=removedIds.toSet()
        val expectedSlots=full.slots.mapNotNull{slot->
            val kept=slot.sets.filterNot{it.setKey in removedSet}
            if(kept.isEmpty())null else slot.copy(sets=kept)
        }
        if(shorter.slots!=expectedSlots)return null
        val knownSeconds=retained.sumOf {set->
            val p=set.prescription
            p.activeSeconds.toLong()+p.restSeconds+
                (p.amplifier?.followUpRestSeconds?:0)+
                (p.amplifier?.followUpActiveSeconds?:0)
        }
        if(knownSeconds!=shorter.knownSetAndRestSeconds ||
            full.copy(
                slots=shorter.slots,
                knownSetAndRestSeconds=shorter.knownSetAndRestSeconds,
                shortSessionLimitMinutes=shorter.shortSessionLimitMinutes,
                shortSessionOmittedSetKeys=shorter.shortSessionOmittedSetKeys
            )!=shorter)return null
        val returning=original.filter{it.setKey in removedSet}
        val returningWorking=returning.count{
            it.prescription.type==DraftSetType.WORKING
        }
        val returningWarmups=returning.count{
            it.prescription.type==DraftSetType.WARMUP
        }
        val slotSummaries=full.slots.mapIndexed{index,slot->
            val working=slot.sets.filter{
                it.prescription.type==DraftSetType.WORKING
            }
            NativeKotlinFullRestoreSlot(
                slot.exerciseId,index+1,working.size,
                working.count{it.setKey in removedSet},
                slot.sets.count{it.setKey in removedSet &&
                    it.prescription.type==DraftSetType.WARMUP},
                slot.supersetGroup!=null
            )
        }
        return NativeKotlinFullRestoreSummary(
            retained.count{it.prescription.type==DraftSetType.WORKING},
            original.count{it.prescription.type==DraftSetType.WORKING},
            returningWorking,returningWarmups,
            Collections.unmodifiableList(slotSummaries))
    }
}

/** Two explicit steps: inspect the Full original, then confirm its restore.
 * Cancel closes only this read-only review. Issued sessions can never enter
 * the writer, and a reopened stale preview cannot change workout history. */
@Composable
internal fun NativeKotlinCustomTimeRestorePanel(
    shown:NativeCustomTimeUndoPreview.Ready,
    busy:Boolean,
    onConfirm:()->Unit,
    onCancel:()->Unit
) {
    val full=shown.full
    val shorter=shown.shortened.snapshot
    val summary=NativeKotlinCustomRestorePresentation.inspect(shorter,full)
    PursuitCard {
        PursuitSectionHeading("Restore original Full workout",
            "Review the original authored targets before replacing this unissued shortened preparation.")
        if(summary==null) {
            Text("The Full review does not match the current shortened workout. Reopen your saved program and review again.",
                color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick=onCancel,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Close outdated review")
            }
        } else {
            Text("${summary.retainedWorkingSets} → ${summary.originalWorkingSets} working sets · ${summary.returningWorkingSets} restored",
                style=MaterialTheme.typography.titleMedium)
            if(summary.returningWarmups>0)
                Text("${summary.returningWarmups} original warm-up set(s) also return.",
                    style=MaterialTheme.typography.bodySmall)
            if(summary.returningWorkingSets==0 &&
                summary.returningWarmups==0)
                Text("No sets were cut by the saved time preference. Restoring Full removes that preference only.",
                    style=MaterialTheme.typography.bodySmall)
            summary.slots.forEach{slot->
                Column(verticalArrangement=Arrangement.spacedBy(2.dp)) {
                    Text("${kotlinExerciseLabel(slot.exerciseId)} · Exercise ${slot.originalPosition}",
                        style=MaterialTheme.typography.titleSmall)
                    Text("${slot.originalWorkingSets} original working sets · ${slot.returningWorkingSets} returning" +
                        (if(slot.returningWarmups>0)
                            " · ${slot.returningWarmups} warm-up(s) returning" else ""),
                        style=MaterialTheme.typography.bodySmall)
                    if(slot.linkedSuperset)
                        Text("Linked superset · original partners and rounds return together",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            var showAll by rememberSaveable(shown.preparedFingerprint) {
                mutableStateOf(false)
            }
            TextButton(enabled=!busy,onClick={showAll=!showAll}) {
                Text(if(showAll)"Hide exact original targets"
                    else "Inspect every original set target")
            }
            if(showAll) {
                val returningKeys=shorter.shortSessionOmittedSetKeys.toSet()
                full.slots.forEach{slot->
                    Text(kotlinExerciseLabel(slot.exerciseId),
                        style=MaterialTheme.typography.titleSmall)
                    customWorkoutInfoSets(slot.sets).forEach{set->
                        val action=if(set.setKey in returningKeys)"Return" else "Keep"
                        Text(
                            "$action · ${set.title} · ${set.load} · ${set.reps} · " +
                            "RIR ${set.targetRir} · ${set.workSeconds}s work / ${set.restSeconds}s rest",
                            style=MaterialTheme.typography.bodySmall)
                        set.technique?.let{technique->
                            Text(technique,
                                style=MaterialTheme.typography.labelSmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Text("Confirming replaces only this unissued custom workout's shortened preparation. It does not change your authored cycle or any completed set or History entry. Issuance remains separate.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick=onCancel,enabled=!busy,
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Keep shortened workout")
            }
            PursuitPrimaryAction("Confirm restore Full",
                enabled=!busy,onClick=onConfirm)
        }
    }
}
