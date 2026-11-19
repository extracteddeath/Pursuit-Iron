package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import io.github.extracteddeath.pursuitiron.rebuild.*

private val compareWeekdays=listOf(
    "Mon","Tue","Wed","Thu","Fri","Sat","Sun"
)
private fun compareWeekday(day:Int)=compareWeekdays.getOrElse(day){"Day"}
private fun compareSplit(split:CustomSplitIdentity):String=when(split) {
    is CustomSplitIdentity.Named->split.split.name
        .lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}
    is CustomSplitIdentity.Freeform->split.label
}
private fun signedCount(value:Int)=when {
    value>0 -> "+$value"
    else -> value.toString()
}
private fun displayedDirectCredits(value:BigDecimal):String=
    value.stripTrailingZeros().toPlainString()
private fun namedMuscle(muscle:TrainingMuscle):String=
    muscle.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}
private fun auditedRange(sets:List<Int>):String=
    if(sets.minOrNull()==sets.maxOrNull())
        sets.firstOrNull()?.toString() ?: "—"
    else "${sets.minOrNull()}–${sets.maxOrNull()}"

@Composable
private fun BlockComparisonColumn(
    title:String,
    facts:CustomBlockComparisonFacts,
    modifier:Modifier=Modifier
) {
    Surface(
        modifier=modifier.fillMaxWidth(),
        color=MaterialTheme.colorScheme.surfaceContainerLow,
        shape=MaterialTheme.shapes.large
    ) {
        Column(
            Modifier.padding(PursuitSpace.md),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
        ) {
            Text(title,style=MaterialTheme.typography.labelLarge,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(facts.label,style=MaterialTheme.typography.titleMedium)
            Text("${facts.phase.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}} " +
                "· ${facts.weeks} weeks",
                style=MaterialTheme.typography.bodySmall)
            Text("Split: ${compareSplit(facts.split)}",
                style=MaterialTheme.typography.bodyMedium)
            Text("${facts.weekdays.size} days/week · " +
                facts.weekdays.joinToString{compareWeekday(it)},
                style=MaterialTheme.typography.bodySmall)
            Text("${facts.baseWorkingSets} base working sets/week · " +
                "${facts.baseWarmupSets} warm-up sets",
                style=MaterialTheme.typography.bodyMedium)
            Text("${facts.exerciseVisits} exercise visits · " +
                "${facts.uniqueExercises} movements · " +
                "${facts.pinnedVisits} pinned visits",
                style=MaterialTheme.typography.bodySmall)
            if(facts.weekOverrideCount>0)
                Text("${facts.weekOverrideCount} week-specific override(s)",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Compare actual saved adjacent cycle blocks. This is NEVER a generated
 * alternative, coach recommendation or permission to replace a training day.
 * Revision/source-gated audit measurements are presented separately from
 * the base authored work so week overrides cannot silently disappear.
 */
@Composable
internal fun NativeKotlinCycleBlockComparisonPanel(
    cycle:CustomTrainingCycle,
    currentAudit:CustomCoachAudit?,
    onEditBlock:(String)->Unit,
    enabled:Boolean=true,
    repairReview:NativeStrengthVolumeRepairReady?=null,
    canRequestStrengthRepair:Boolean=false,
    onRequestStrengthRepair:((String,Int)->Unit)?=null,
    onAcceptStrengthRepair:(()->Unit)?=null,
    menuRecoveryReview:NativeStrengthMenuRecoveryReady?=null,
    canRequestMenuRecovery:Boolean=false,
    onRequestMenuRecovery:((String,Int)->Unit)?=null,
    onAcceptMenuRecovery:(()->Unit)?=null
) {
    if(cycle.blocks.size<2)return
    var selected by rememberSaveable(cycle.cycleId){
        mutableStateOf(cycle.blocks[1].blockId)
    }
    val id=selected.takeIf {candidate->
        cycle.blocks.drop(1).any{it.blockId==candidate}
    } ?:cycle.blocks[1].blockId
    val comparison=remember(cycle,id,currentAudit){
        CustomCycleBlockComparisonDomain.compare(cycle,id,currentAudit)
    } ?:return
    // Only a matching, verified source audit can expose the exact resolved
    // final-Hypertrophy/first-Strength day dose (including overrides).
    val strengthDayDose=remember(cycle,id,currentAudit){
        val source=currentAudit?.takeIf {
            it.auditedCycleRevision==cycle.revision &&
                it.structuralReview?.verifiedDraft==cycle
        }?.structuralReview
        source?.let{StrengthDoseBoundaryGuardDomain.review(it,id)}
    }
    val affectedStrengthDays=strengthDayDose?.affectedDays.orEmpty()
    // Adjacent-block comparisons still show the deload as the previous
    // block. Exercise recovery must instead use the final real hypertrophy
    // week even when a recovery/test bridge precedes Strength.
    val strengthRoster=remember(cycle,id,currentAudit){
        val source=currentAudit?.takeIf {
            it.auditedCycleRevision==cycle.revision &&
                it.structuralReview?.verifiedDraft==cycle
        }?.structuralReview
        source?.let{StrengthRosterBoundaryGuardDomain.review(it,id)}
    }
    val affectedStrengthMenus=strengthRoster?.affectedDays.orEmpty()
    val strengthCoverageReviewRequired=
        comparison.strengthExerciseCollapseReviewRequired ||
            strengthRoster?.status==
                StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW
    val prior=comparison.prior
    val next=comparison.next
    val selectedBlock=cycle.blocks.single {it.blockId==next.blockId}
    val strengthSourceDose=strengthDayDose?.takeIf {review ->
        review.status in setOf(
            StrengthDoseBoundaryStatus.CONTINUOUS,
            StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW) &&
            review.hypertrophyExitWeek!=null &&
            review.strengthEntryWeek!=null &&
            review.hypertrophyWorkingSets!=null &&
            review.strengthWorkingSets!=null
    }
    val adjacentExitWeek=cycle.blocks.take(
        cycle.blocks.indexOf(selectedBlock)).sumOf{it.weeks}
    val hasInterveningBridge=strengthSourceDose?.hypertrophyExitWeek?.let{
        it<adjacentExitWeek
    }==true
    // Arm discontinuity is derived from the same EXACT source Hypertrophy
    // exit and Strength entry week as the verified roster/dose guard.
    // Looking at an adjacent deload week would hide lost direct arm work.
    val actualDirectArmLosses=remember(
        cycle,id,currentAudit,strengthSourceDose) {
        val exit=strengthSourceDose?.hypertrophyExitWeek
        val entry=strengthSourceDose?.strengthEntryWeek
        val before=currentAudit?.weeks?.singleOrNull{
            it.absoluteWeek==exit &&
                it.phase==TrainingPhase.HYPERTROPHY &&
                it.blockPurpose==CustomBlockPurpose.TRAINING
        }
        val after=currentAudit?.weeks?.singleOrNull{
            it.absoluteWeek==entry &&
                it.phase==TrainingPhase.STRENGTH &&
                it.blockPurpose==CustomBlockPurpose.TRAINING
        }
        if(before==null || after==null || currentAudit?.auditedCycleRevision!=cycle.revision ||
            currentAudit?.structuralReview?.verifiedDraft!=cycle ||
            before.workingSets!=strengthSourceDose?.hypertrophyWorkingSets ||
            after.workingSets!=strengthSourceDose?.strengthWorkingSets) null
        else setOf(TrainingMuscle.BICEPS,TrainingMuscle.BRACHIALIS,
            TrainingMuscle.TRICEPS,TrainingMuscle.WRIST_FLEXORS,
            TrainingMuscle.WRIST_EXTENSORS).filter{muscle->
            val earlier=before.directSets[muscle]
            val later=after.directSets[muscle]
            earlier!=null && later!=null &&
                earlier.signum()>0 && later.signum()==0
        }.toSet()
    }
    val visibleDirectArmLosses=actualDirectArmLosses
        ?:comparison.strengthDirectArmLosses
    val protectedVisits=selectedBlock.days.sumOf {day ->
        day.slots.count {slot ->
            slot.pinned || slot.attachmentId!=null ||
                slot.supersetGroup!=null || slot.coachNote.isNotBlank() ||
                slot.sets.any {it.amplifier!=null}
        }
    }
    val customWeekOverrides=cycle.weekOverrides[next.blockId].orEmpty().size
    // Snapshot cross-module nullable properties before Compose branches.
    // A null check on a public property alone is not a Kotlin smart cast.
    val priorAuditedSets=prior.auditedWeeklyWorkingSets
    val nextAuditedSets=next.auditedWeeklyWorkingSets
    val fontScale=LocalDensity.current.fontScale
    var expandAllMuscles by rememberSaveable(cycle.cycleId,id){
        mutableStateOf(false)
    }
    PursuitCard {
        PursuitSectionHeading("Compare training blocks",
            "See how the next authored block differs before you approve a cycle change.")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
            cycle.blocks.drop(1).forEach {block->
                FilterChip(
                    selected=id==block.blockId,
                    onClick={selected=block.blockId},
                    label={Text(block.label)}
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if(maxWidth>=670.dp && fontScale<1.35f) {
                Row(
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
                ) {
                    BlockComparisonColumn("Previous block",prior,
                        modifier=Modifier.weight(1f))
                    BlockComparisonColumn("Selected block",next,
                        modifier=Modifier.weight(1f))
                }
            } else Column(
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)
            ) {
                BlockComparisonColumn("Previous block",prior)
                BlockComparisonColumn("Selected block",next)
            }
        }
        PursuitMetricStrip(listOf(
            "Base working sets" to "${prior.baseWorkingSets} → ${next.baseWorkingSets}",
            "Set change" to signedCount(comparison.baseWorkingSetDelta),
            "Days per week" to "${prior.weekdays.size} → ${next.weekdays.size}"
        ))
        Text("Base figures are authored per-week targets, not completed workouts. " +
            "The comparison is factual: fewer sets do not automatically mean " +
            "better recovery or sufficient strength training.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(priorAuditedSets!=null && nextAuditedSets!=null) {
            HorizontalDivider()
            PursuitSectionHeading("Full-cycle measurements",
                "Effective working sets include saved week overrides. These "+
                    "are the adjacent block exit and entry. A separate Strength "+
                    "training baseline appears below when a deload intervenes.")
            PursuitMetricStrip(listOf(
                "Previous exit week" to (priorAuditedSets.lastOrNull()?.toString() ?: "—"),
                "Next entry week" to (nextAuditedSets.firstOrNull()?.toString() ?: "—"),
                "Entry change" to (
                    comparison.boundaryWorkingSetDelta?.let(::signedCount) ?: "—")
            ))
            Text(
                "Next block by week: " +
                    nextAuditedSets.mapIndexed {index,sets->
                        "W${index+1} $sets"
                    }.joinToString(" · ") +
                    (if(hasInterveningBridge)
                        ". The previous block is a recovery/test bridge; "+
                            "its low-volume exit is NOT the Strength retention baseline."
                    else ". Lowest week retains " +
                        (comparison.lowestNextWeekRetentionPercent?.let{"$it%"} ?: "—") +
                        " of the previous block's exit week."),
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                if(comparison.matchingAuditStatus==
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW)
                    "These weeks are from the current source-backed full-cycle review."
                else "These measurements have ${comparison.auditIssueCount ?: 0} " +
                    "unresolved audit finding(s). They are not coaching clearance.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Exact block-entry volume is not audited. Run the " +
                "full-cycle review before approving strength or peak; " +
                "base working sets exclude week overrides.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(strengthSourceDose!=null) {
            HorizontalDivider()
            PursuitSectionHeading("Hypertrophy → Strength training baseline",
                "Last normal Hypertrophy training week → first Strength week, "+
                    "including actual saved week overrides.")
            PursuitMetricStrip(listOf(
                "H exit · week ${strengthSourceDose.hypertrophyExitWeek}" to
                    "${strengthSourceDose.hypertrophyWorkingSets} sets",
                "Strength · week ${strengthSourceDose.strengthEntryWeek}" to
                    "${strengthSourceDose.strengthWorkingSets} sets",
                "Training dose change" to signedCount(
                    checkNotNull(strengthSourceDose.strengthWorkingSets)-
                        checkNotNull(strengthSourceDose.hypertrophyWorkingSets))
            ))
            if(hasInterveningBridge)Text(
                "Recovery and performance-test weeks are not used as the "+
                    "training-dose baseline. The previous-block comparison "+
                    "above still shows the actual adjacent block.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("These numbers are observed authored working sets, not "+
                "an automatic recommendation or clearance to change volume.",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(affectedStrengthDays.isNotEmpty()) {
            PursuitSectionHeading("Strength day lost working sets",
                "Final effective Hypertrophy week → first Strength week · actual reviewed days")
            PursuitMetricStrip(affectedStrengthDays.map{loss->
                compareWeekday(loss.weekday) to
                    "${loss.beforeWorkingSets} → ${loss.afterWorkingSets} sets"
            })
            Text("Another training day adding sets cannot hide this loss. "+
                "Review an individual dose adjustment; no existing workout is changed.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(comparison.strengthHalfVolumeReviewRequired ||
            strengthSourceDose?.status==
                StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW) {
            HorizontalDivider()
            Text("Strength working-set drop needs review",
                style=MaterialTheme.typography.titleMedium,
                color=MaterialTheme.colorScheme.error)
            Text(
                if(strengthSourceDose?.status==
                    StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW)
                    "The first Strength week has half or fewer of the working "+
                        "sets in the athlete's real final Hypertrophy training "+
                        "week, either overall or on a matched training day. "+
                        "Recovery and test weeks cannot hide this change."
                else if(comparison.strengthHalfVolumeUsesAuditedWeeks)
                    "At least one strength week has half or fewer of the working " +
                        "sets in the final hypertrophy week. Check your " +
                        "individualized dose, session limits and recovery audit."
                else "The authored strength block has half or fewer of the base " +
                    "working sets. Obtain the complete audited schedule, "+
                    "including overrides.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No loads, sets, training history or program approval were changed.",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        visibleDirectArmLosses?.takeIf{it.isNotEmpty()}?.let{lost->
            HorizontalDivider()
            PursuitSectionHeading("Direct arm work disappeared",
                "Audited final hypertrophy week → first Strength week")
            Text("These previously trained arm functions now have zero " +
                    "direct working-set credit: "+
                    lost.sortedBy{it.ordinal}.joinToString{namedMuscle(it)}+".",
                style=MaterialTheme.typography.bodyMedium,
                color=MaterialTheme.colorScheme.error)
            Text("Indirect assistance from bench presses and rows is " +
                    "still valuable, but it is not the same as the " +
                    "athlete's previous direct curls or extensions. " +
                    "Review available low-fatigue accessories, individual " +
                    "load histories, recovery, muscle targets and real " +
                    "session time before approving this block.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick={onEditBlock(next.blockId)},enabled=enabled,
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
            ) {Text("Review strength arm exercises")}
            Text("This review does not restore exercises automatically, " +
                    "overwrite completed sessions or waive the full " +
                    "native coaching audit.",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(strengthCoverageReviewRequired) {
            HorizontalDivider()
            PursuitSectionHeading("Strength exercise coverage needs review",
                if(affectedStrengthMenus.isNotEmpty() ||
                    comparison.exerciseRosterUsesReviewedWeeks)
                    "Final hypertrophy week → first strength week · reviewed exercise rosters"
                else "Based on saved block templates only; run the complete cycle review to verify week overrides.")
            if(affectedStrengthMenus.isNotEmpty())Text(
                "Source training week ${strengthRoster?.sourceExitWeek} → "+
                    "Strength week ${strengthRoster?.targetEntryWeek}. "+
                    "Intervening recovery or performance-test weeks are not "+
                    "the athlete's normal hypertrophy baseline.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            val actualRosters=if(affectedStrengthMenus.isNotEmpty())
                affectedStrengthMenus.map{loss->
                    CustomBlockDayExerciseChange(loss.weekday,
                        loss.beforeWorkingExercises,loss.afterWorkingExercises,
                        loss.missingExerciseIds)
                }
                else comparison.strengthDayExerciseChanges.filter {change->
                    change.priorExerciseCount>=6 &&
                        change.nextExerciseCount*2<change.priorExerciseCount
                }
            actualRosters.forEach{change->
                Text(compareWeekday(change.weekday)+": "+
                        "${change.priorExerciseCount} → ${change.nextExerciseCount} exercises",
                    style=MaterialTheme.typography.titleSmall,
                    color=MaterialTheme.colorScheme.error)
                if(change.droppedExerciseIds.isNotEmpty())Text(
                    "Not present: "+change.droppedExerciseIds.sorted()
                        .joinToString{ kotlinExerciseLabel(it) },
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Heavier strength work can require more rest and a different " +
                    "working-set dose, but it does not by itself justify " +
                    "losing most established movement patterns. Review " +
                    "muscle coverage, priority-lift practice, equipment and " +
                    "session time. There is no universal eight-exercise quota.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick={onEditBlock(next.blockId)},
                enabled=enabled,
                modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
            ) {Text("Review or restore strength exercises")}
            Text("Set-count repair alone cannot restore an omitted exercise. " +
                    "No exercise, prescription or training history was changed.",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(strengthCoverageReviewRequired &&
            onRequestMenuRecovery!=null) {
            HorizontalDivider()
            PursuitSectionHeading("Restore missing Strength exercises",
                "Compare the actual last Hypertrophy week with the reviewed Strength entry. No exercise-count quota or invented prescription.")
            var menuCapacity by rememberSaveable(cycle.cycleId,id) {
                mutableIntStateOf(8)
            }
            Text("Maximum exercises on one training day",
                style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                listOf(6,8,10,12).forEach{cap->
                    FilterChip(selected=menuCapacity==cap,enabled=enabled,
                        onClick={menuCapacity=cap},
                        label={Text(cap.toString()+" exercises")})
                }
            }
            val current=menuRecoveryReview?.takeIf{receipt->
                receipt.request.cycleId==cycle.cycleId &&
                    receipt.request.expectedCycleRevision==cycle.revision &&
                    receipt.request.blockId==next.blockId &&
                    receipt.request.maximumExercisesPerDay==menuCapacity
            }
            if(current==null) {
                OutlinedButton(
                    enabled=enabled && canRequestMenuRecovery,
                    onClick={onRequestMenuRecovery(next.blockId,menuCapacity)},
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                ) {Text("Preview source exercise recovery")}
                if(!canRequestMenuRecovery)Text(
                    "A current full-cycle coach review and source-verified gym are required first.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Proposed: "+current.restored.size+
                    " original source exercise(s), with their actual authored "+
                    "sets, loads, reps, RIR and rest. No saved program changed yet.",
                    style=MaterialTheme.typography.bodyMedium)
                current.restored.forEach{item->
                    Text(compareWeekday(item.weekday)+" · "+
                        kotlinExerciseLabel(item.exerciseId)+" · "+
                        item.workingSets+" working sets",
                        style=MaterialTheme.typography.bodySmall)
                }
                Text("The full native cycle audit accepted this exact candidate. "+
                    "Saving invalidates the old program approval, never rewrites "+
                    "existing workouts or transfers Machine A/B histories.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                var menuAthleteConfirmed by remember(current) {
                    mutableStateOf(false)
                }
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=menuAthleteConfirmed,enabled=enabled,
                        onCheckedChange={menuAthleteConfirmed=it})
                    Text("I reviewed the recovered exercises and their working "+
                        "sets. Save a new program draft that requires renewed approval.",
                        modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.bodyMedium)
                }
                Button(
                    enabled=enabled && menuAthleteConfirmed &&
                        onAcceptMenuRecovery!=null,
                    onClick={onAcceptMenuRecovery?.invoke()},
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)
                ) {Text("Confirm and save recovered exercises")}
                OutlinedButton(
                    enabled=enabled && canRequestMenuRecovery,
                    onClick={onRequestMenuRecovery(next.blockId,menuCapacity)},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
                ) {Text("Recheck latest source")}
            }
        }
        val sourceContinuityIssue=currentAudit?.issues?.any {issue->
            issue.kind==CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP &&
                issue.location.contains(".block.${next.blockId}")
        }==true
        if(next.phase==TrainingPhase.STRENGTH &&
            (comparison.strengthHalfVolumeReviewRequired ||
                strengthSourceDose?.status==
                    StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW ||
                affectedStrengthDays.isNotEmpty() ||
                sourceContinuityIssue || repairReview?.request?.blockId==next.blockId) &&
            onRequestStrengthRepair!=null) {
            HorizontalDivider()
            PursuitSectionHeading("Adjust strength working sets",
                "A proposed change keeps your exercises, individual loads, " +
                    "RIR, days and other blocks intact. Nothing changes until you confirm.")
            var maximumSets by rememberSaveable(cycle.cycleId,id) {
                mutableIntStateOf(5)
            }
            Text("Maximum working sets for one exercise",
                style=MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                listOf(4,5,6,8).forEach {cap->
                    FilterChip(selected=maximumSets==cap,
                        enabled=enabled,
                        onClick={maximumSets=cap},
                        label={Text("$cap sets")})
                }
            }
            val verified=repairReview?.takeIf{
                it.request.cycleId==cycle.cycleId &&
                    it.request.expectedCycleRevision==cycle.revision &&
                    it.request.blockId==next.blockId &&
                    it.request.maximumWorkingSetsPerSlot==maximumSets
            }
            if(verified==null) {
                OutlinedButton(
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
                    enabled=enabled && canRequestStrengthRepair,
                    onClick={onRequestStrengthRepair(next.blockId,maximumSets)}
                ) {Text("Review proposed strength adjustments")}
                if(!canRequestStrengthRepair)Text(
                    "Run the current complete cycle and coaching-policy review " +
                        "and enable adaptive set-count proposals first.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("${verified.additionsPerWeek} extra working sets in the " +
                        "weekly template, for ${verified.weeksCovered} reviewed " +
                        "strength weeks. No previously issued session is changed.",
                    style=MaterialTheme.typography.bodyMedium)
                verified.changedSlots.forEach {change->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text(kotlinExerciseLabel(change.exerciseId)+
                            " · "+compareWeekday(change.weekday),
                            modifier=Modifier.weight(1f),
                            style=MaterialTheme.typography.bodyMedium)
                        Text("${change.beforeWorkingSets} → ${change.afterWorkingSets}",
                            style=MaterialTheme.typography.labelLarge)
                    }
                }
                var athleteConfirmed by remember(verified) {
                    mutableStateOf(false)
                }
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically) {
                    Checkbox(checked=athleteConfirmed,enabled=enabled,
                        onCheckedChange={athleteConfirmed=it})
                    Text("I reviewed these working-set changes. Save them to " +
                        "my custom program and require renewed program approval.",
                        modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.bodyMedium)
                }
                Button(
                    enabled=enabled && athleteConfirmed &&
                        onAcceptStrengthRepair!=null,
                    modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
                    onClick={onAcceptStrengthRepair?.invoke()}
                ) {Text("Save reviewed strength adjustments")}
                Text("This saves a new, undoable program draft. Historical " +
                    "sets and finished workouts remain unchanged.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        comparison.newlyTrackedMuscles?.takeIf{it.isNotEmpty()}?.let{muscles->
            Text("Newly tracked muscles: ${muscles.sortedBy{it.ordinal}.joinToString{namedMuscle(it)}}. Earlier targets did not track these groups.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        comparison.noLongerTrackedMuscles?.takeIf{it.isNotEmpty()}?.let{muscles->
            Text("No longer tracked: ${muscles.sortedBy{it.ordinal}.joinToString{namedMuscle(it)}}.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        comparison.auditedDirectMuscleChanges?.let{changes->
            if(changes.isNotEmpty()) {
                HorizontalDivider()
                PursuitSectionHeading("Audited direct muscle sets",
                    "Final prior week → first next week, using reviewed direct-set " +
                        "credits and saved week overrides. No completion or recovery inferred.")
                changes.take(if(expandAllMuscles)changes.size else 6).forEach{change->
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm),
                        verticalAlignment=Alignment.CenterVertically) {
                        Text(namedMuscle(change.muscle),
                            modifier=Modifier.weight(1f),
                            style=MaterialTheme.typography.bodyMedium)
                        Text(
                            "${displayedDirectCredits(change.previousDirectSets)} → " +
                                displayedDirectCredits(change.nextDirectSets),
                            style=MaterialTheme.typography.labelLarge)
                    }
                }
                if(changes.size>6)TextButton(
                    onClick={expandAllMuscles=!expandAllMuscles},
                    modifier=Modifier.heightIn(min=48.dp)
                ) {Text(if(expandAllMuscles)"Show fewer muscles"
                    else "Show all ${changes.size} muscle differences")}
                Text("Biceps, brachialis, wrist flexors and wrist extensors are " +
                    "counted independently. Differences are measurements, " +
                    "not a prescription or a safe volume threshold.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if(comparison.addedWeekdays.isNotEmpty() ||
            comparison.removedWeekdays.isNotEmpty()) {
            HorizontalDivider()
            PursuitSectionHeading("Calendar differences")
            if(comparison.addedWeekdays.isNotEmpty())
                Text("New training days: " +
                    comparison.addedWeekdays.joinToString {compareWeekday(it)})
            if(comparison.removedWeekdays.isNotEmpty())
                Text("Days no longer scheduled: " +
                    comparison.removedWeekdays.joinToString {compareWeekday(it)})
        }
        if(comparison.exerciseChanges.isNotEmpty()) {
            HorizontalDivider()
            PursuitSectionHeading("Exercise exposure changes",
                "Counts are visits per base week, not sets or predicted progress.")
            comparison.exerciseChanges.take(8).forEach {change->
                Row(Modifier.fillMaxWidth(),
                    verticalAlignment=Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                    Text(kotlinExerciseLabel(change.exerciseId),
                        modifier=Modifier.weight(1f),
                        style=MaterialTheme.typography.bodyMedium)
                    Text("${change.beforeVisits} → ${change.afterVisits}",
                        style=MaterialTheme.typography.labelLarge)
                }
            }
            if(comparison.exerciseChanges.size>8)
                Text("${comparison.exerciseChanges.size-8} more movement differences " +
                    "are available in the full program editor.",
                    style=MaterialTheme.typography.bodySmall)
        }
        if(protectedVisits>0 || customWeekOverrides>0) {
            HorizontalDivider()
            PursuitSectionHeading("Protect your custom work",
                "These are saved athlete-authored details, not coach suggestions.")
            if(protectedVisits>0)Text(
                "$protectedVisits protected exercise visit(s): pinned movements, " +
                    "notes, attachments, supersets or advanced sets.",
                style=MaterialTheme.typography.bodyMedium)
            if(customWeekOverrides>0)Text(
                "$customWeekOverrides week-specific custom override(s) are retained.",
                style=MaterialTheme.typography.bodyMedium)
            Text(
                "An adaptive replacement must keep protected visits in their " +
                    "exact session and position, including their original sets. " +
                    "Week-specific overrides still require a separate lossless " +
                    "migration before that block can be regenerated.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(
            onClick={onEditBlock(next.blockId)},
            enabled=enabled,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
        ) {Text("Inspect and edit ${next.label}")}
        Text("No new program has been generated or approved here. " +
            "Adaptive alternatives require a complete athlete-specific " +
            "generator, verified coaching policies and a separate review.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
