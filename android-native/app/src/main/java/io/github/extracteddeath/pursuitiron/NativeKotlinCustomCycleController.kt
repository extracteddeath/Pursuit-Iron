package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Candidate-only Compose state holder. All disk operations run on IO;
 * accepted UI state comes ONLY from a durable native write or a disk read.
 * No WebView/JS engine, optimistic save, fictitious program approval or
 * concurrent, silently overwritten auto-save.
 */
internal data class NativeKotlinCustomCycleState(
    val draft: CustomTrainingCycle? = null,
    val cycleId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val refusal: NativeCustomDraftRefusal? = null,
    val canUndo: Boolean = false
)

internal class NativeKotlinCustomCycleController(
    private val host: NativeKotlinCandidateHost
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = AtomicBoolean(false)
    private val current = MutableStateFlow(NativeKotlinCustomCycleState())
    val state: StateFlow<NativeKotlinCustomCycleState> = current.asStateFlow()
    private var lastUndo: CustomCycleDraftReceipt? = null

    private fun run(action: () -> Unit) {
        if (!pending.compareAndSet(false, true)) return
        current.value = current.value.copy(busy = true, refusal = null, message = null)
        scope.launch {
            try {
                action()
            } catch (_: Exception) {
                current.value = current.value.copy(
                    message = "Native draft operation failed; reload the saved version.")
            } finally {
                current.value = current.value.copy(busy = false)
                pending.set(false)
            }
        }
    }

    private fun applyRead(cycleId: String, result: NativeCustomDraftReadResult) {
        lastUndo = null
        current.value = when (result) {
            is NativeCustomDraftReadResult.Found ->
                NativeKotlinCustomCycleState(
                    draft = result.draft,
                    cycleId = result.draft.cycleId,
                    message = "Saved custom cycle restored · revision ${result.draft.revision}")
            NativeCustomDraftReadResult.Missing ->
                NativeKotlinCustomCycleState(cycleId = cycleId,
                    message = "No saved draft exists with that ID.")
            is NativeCustomDraftReadResult.Deleted ->
                NativeKotlinCustomCycleState(cycleId = cycleId,
                    message = "This draft was deleted at revision ${result.revision}.")
            is NativeCustomDraftReadResult.Rejected ->
                NativeKotlinCustomCycleState(cycleId = cycleId,
                    refusal = result.reason,
                    message = "Saved draft could not be verified. Nothing was overwritten.")
        }
    }

    fun open(cycleId: String) = run {
        applyRead(cycleId, host.readCustomCycleDraft(cycleId))
    }

    fun create(
        title: String,
        goal: AthleteTrainingGoal,
        weeks: Int,
        phase: TrainingPhase,
        phaseName: String,
        customSplit: String
    ) = run {
        val cycleId = "cycle-" + UUID.randomUUID().toString().replace("-", "")
        val result = host.createBlankCustomCycleDraft(
            cycleId, goal, title, "phase-1", phaseName, weeks, phase,
            CustomSplitIdentity.Freeform(customSplit))
        when (result) {
            is NativeCustomDraftWriteResult.Committed ->
                applyRead(cycleId, host.readCustomCycleDraft(cycleId))
            is NativeCustomDraftWriteResult.Rejected ->
                current.value = current.value.copy(refusal = result.reason,
                    message = "Could not save this new custom cycle.")
        }
    }

    private fun edit(
        command: (CustomTrainingCycle) -> CustomCycleBuilderResult
    ) = run {
        val saved = current.value.draft ?: return@run
        val result = command(saved)
        when (result) {
            is CustomCycleBuilderResult.Rejected ->
                current.value = current.value.copy(
                    message = "Edit refused: ${result.reason.name.replace('_', ' ').lowercase()}.")
            is CustomCycleBuilderResult.Proposed -> {
                val write = host.saveCustomCycleDraft(result.draft, saved.revision)
                when (write) {
                    is NativeCustomDraftWriteResult.Committed -> {
                        lastUndo = result.receipt
                        current.value = current.value.copy(
                            draft = result.draft,
                            cycleId = result.draft.cycleId,
                            canUndo = true,
                            message = "Saved revision ${write.revision} · ${result.receipt.operation.replace('-', ' ')}.")
                    }
                    is NativeCustomDraftWriteResult.Rejected -> {
                        // The disk wins, never a stale Compose draft. A storage
                        // failure should not cause this screen to show success.
                        lastUndo = null
                        val latest = host.readCustomCycleDraft(saved.cycleId)
                        applyRead(saved.cycleId, latest)
                        current.value = current.value.copy(
                            refusal = write.reason,
                            message = "Edit not saved: ${write.reason.name.replace('_', ' ').lowercase()}. Reloaded durable state.")
                    }
                }
            }
        }
    }

    fun editMetadata(title: String, note: String) = edit { draft ->
        CustomCycleBuilderDomain.editCycleMetadata(
            draft, draft.revision, title, note)
    }

    fun addDay(
        blockId: String, weekday: Int, label: String,
        purpose: ComposerSessionPurpose
    ) = edit { draft ->
        CustomCycleBuilderDomain.addDay(draft, draft.revision, blockId,
            CustomTrainingDay(weekday, label, purpose, emptyList()))
    }

    fun editDay(
        blockId: String, weekday: Int, label: String,
        purpose: ComposerSessionPurpose
    ) = edit { draft ->
        CustomCycleBuilderDomain.editDayMetadata(
            draft, draft.revision, blockId, weekday, label, purpose)
    }

    fun renameDay(blockId: String, weekday: Int, label: String) = edit { draft ->
        CustomCycleBuilderDomain.renameDay(
            draft, draft.revision, blockId, weekday, label)
    }

    fun changeDayPurpose(
        blockId: String, weekday: Int, purpose: ComposerSessionPurpose
    ) = edit { draft ->
        CustomCycleBuilderDomain.changeDayPurpose(
            draft, draft.revision, blockId, weekday, purpose)
    }

    fun addBlock(
        label: String, phase: TrainingPhase,
        weeks: Int, split: String
    ) = edit { draft ->
        val newId = "phase-" + UUID.randomUUID().toString().replace("-", "")
        val block = CustomTrainingBlock(newId, label, phase,
            CustomBlockPurpose.TRAINING, weeks,
            CustomSplitIdentity.Freeform(split), emptyList())
        CustomCycleBuilderDomain.insertBlock(draft, draft.revision, block)
    }

    fun editBlock(
        blockId: String, label: String, phase: TrainingPhase,
        purpose: CustomBlockPurpose, weeks: Int, split: String
    ) = edit { draft ->
        CustomCycleBuilderDomain.editBlockMetadata(
            draft, draft.revision, blockId, label, phase,
            purpose, weeks, CustomSplitIdentity.Freeform(split))
    }

    fun duplicateBlock(blockId: String) = edit { draft ->
        CustomCycleBuilderDomain.duplicateBlock(
            draft, draft.revision, blockId,
            "phase-" + UUID.randomUUID().toString().replace("-", ""))
    }

    fun reorderBlock(blockId: String, direction: Int) = edit { draft ->
        val order = draft.blocks.map { it.blockId }.toMutableList()
        val currentIndex = order.indexOf(blockId)
        val target = currentIndex + direction
        if (currentIndex < 0 || target !in order.indices)
            CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        else {
            val moved = order.removeAt(currentIndex)
            order.add(target, moved)
            CustomCycleBuilderDomain.reorderBlocks(
                draft, draft.revision, order)
        }
    }

    fun undo() = run {
        val saved = current.value.draft ?: return@run
        val receipt = lastUndo ?: return@run
        val result = CustomCycleBuilderDomain.undo(saved, receipt)
        if (result !is CustomCycleBuilderResult.Proposed) {
            lastUndo = null
            current.value = current.value.copy(
                canUndo = false, message = "Undo is no longer valid for this revision.")
            return@run
        }
        val write = host.saveCustomCycleDraft(result.draft, saved.revision)
        lastUndo = null
        when (write) {
            is NativeCustomDraftWriteResult.Committed ->
                current.value = current.value.copy(
                    draft = result.draft, canUndo = false,
                    message = "Undo saved · revision ${write.revision}")
            is NativeCustomDraftWriteResult.Rejected -> {
                applyRead(saved.cycleId, host.readCustomCycleDraft(saved.cycleId))
                current.value = current.value.copy(refusal = write.reason,
                    message = "Undo was not saved; restored disk state.")
            }
        }
    }

    fun delete() = run {
        val draft = current.value.draft ?: return@run
        val write = host.deleteCustomCycleDraft(draft.cycleId, draft.revision)
        when (write) {
            is NativeCustomDraftWriteResult.Committed -> {
                lastUndo = null
                current.value = NativeKotlinCustomCycleState(
                    cycleId = draft.cycleId,
                    message = "Draft deleted · revision ${write.revision}. Older autosaves cannot restore it.")
            }
            is NativeCustomDraftWriteResult.Rejected -> {
                applyRead(draft.cycleId, host.readCustomCycleDraft(draft.cycleId))
                current.value = current.value.copy(refusal = write.reason,
                    message = "Delete was not saved; restored disk state.")
            }
        }
    }

    override fun close() {
        scope.cancel()
        lastUndo = null
    }
}
