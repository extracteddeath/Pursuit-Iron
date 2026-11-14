package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.mapSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Candidate-only entry of ACTUAL athlete/coach supplied policy constraints.
 * No universal MEV/MAV/MRV, goal-specific frequency, transition seconds or
 * volume-change percentages are prefilled by UI or copied from observed work.
 *
 * This screen only constructs inputs for the independent native whole-cycle
 * audit. Its draft has no approval, issuance, or persistence authority.
 */
@Composable
internal fun NativeKotlinCustomCoachPolicyEditor(
    cycle:CustomTrainingCycle,
    source:AthleteSetupReview?,
    sourceRevision:Long?,
    busy:Boolean,
    reviewedInputs:CustomCoachAuditInputs?,
    observedEvidence:CustomCoachPolicyEvidence?,
    onInspectEvidence:()->Unit,
    onDraftChanged:()->Unit,
    onDiscardDraft:()->Unit={},
    onReview:(CustomCoachAuditInputs)->Unit
) {
    val phaseList=remember(cycle.cycleId,cycle.revision) {
        cycle.blocks.map{it.phase}.distinct()
    }
    var selectedPhase by rememberSaveable(cycle.cycleId,cycle.revision) {
        mutableStateOf(phaseList.first())
    }
    var selectedMuscle by rememberSaveable(cycle.cycleId,cycle.revision) {
        mutableStateOf(TrainingMuscle.CHEST)
    }
    val fields=rememberSaveable(cycle.cycleId,cycle.revision,sourceRevision,
        saver=mapSaver<MutableMap<String,String>>(
            save={it.toMap()},
            restore={saved->mutableStateMapOf<String,String>().apply {
                saved.forEach{(key,value)->if(value is String)put(key,value)}
            }})) { mutableStateMapOf<String,String>() }
    val liftRows=rememberSaveable(cycle.cycleId,cycle.revision,sourceRevision,
        saver=mapSaver<MutableMap<TrainingPhase,Int>>(
            save={rows->rows.entries.associate{it.key.name to it.value}},
            restore={saved->mutableStateMapOf<TrainingPhase,Int>().apply {
                saved.forEach{(key,value)->
                    val phase=runCatching{TrainingPhase.valueOf(key)}.getOrNull()
                    if(phase!=null && value is Int)put(phase,value)
                }
            }})) { mutableStateMapOf<TrainingPhase,Int>() }
    var edited by rememberSaveable(cycle.cycleId,cycle.revision,sourceRevision) {
        mutableStateOf(false)
    }
    LaunchedEffect(cycle.cycleId,cycle.revision,sourceRevision,reviewedInputs) {
        val saved=reviewedInputs
        // Recover visible fields once; a later audit callback must not
        // overwrite a newer unsaved policy edit.
        if(saved!=null && !edited) {
            fields.clear();liftRows.clear()
            saved.policiesByPhase.forEach {(phase,policy)->
                val key=phase.name
                fields["$key:id"]=policy.policyId
                fields["$key:revision"]=policy.revision
                fields["$key:rationale"]=policy.rationale
                fields["$key:muscleScope"]=if(policy.selectedBounds.map{it.muscle}.toSet()==
                    TrainingMuscleCoverage.legacyV1.toSet())"v1" else "v2"
                listOf("baseline" to policy.baselineBounds,
                    "selected" to policy.selectedBounds).forEach {(side,bounds)->
                    bounds.forEach {b->
                        val prefix="$key:${b.muscle.name}:$side"
                        fields["$prefix:mev"]=b.mev.toPlainString()
                        fields["$prefix:mav"]=b.mav.toPlainString()
                        fields["$prefix:mrv"]=b.mrv.toPlainString()
                        fields["$prefix:days"]=b.minimumDirectDays.toString()
                    }
                }
                policy.minimumPurposeDays.forEach {(purpose,days)->
                    fields["$key:purpose:${purpose.name}"]=days.toString()
                }
                liftRows[phase]=policy.primaryLiftMinimumDays.size
                policy.primaryLiftMinimumDays.entries.forEachIndexed {i,(exercise,days)->
                    fields["$key:lift:$i:id"]=exercise
                    fields["$key:lift:$i:days"]=days.toString()
                }
            }
            saved.nonSetSecondsByWeekday.forEach {(day,seconds)->
                fields["time:$day"]=seconds.toString()
            }
            saved.continuityPolicy?.let {c->
                fields["continuity:dropTotal"]=c.maximumTotalWorkingSetDropFraction.toPlainString()
                fields["continuity:dropDirect"]=c.maximumDirectMuscleDropFraction.toPlainString()
                c.maximumTotalWorkingSetRiseFraction?.let {
                    fields["continuity:riseTotal"]=it.toPlainString()
                }
                c.maximumDirectMuscleRiseFraction?.let {
                    fields["continuity:riseDirect"]=it.toPlainString()
                }
            }
        } else if(saved!=null && edited) {
            // Reopening may reload the saved program's prior policy. A
            // restored, unsubmitted form is still a draft, never its audit.
            onDraftChanged()
        }
    }
    var issues by rememberSaveable(cycle.cycleId,cycle.revision,sourceRevision) {
        mutableStateOf<List<String>>(emptyList())
    }
    var expanded by rememberSaveable(cycle.cycleId,cycle.revision) {
        mutableStateOf(false)
    }
    val profile=source?.reviewed?.takeIf {
        source?.status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
            it.athleteProfileId==cycle.athleteId && it.goal==cycle.goal
    }
    fun get(key:String)=fields[key].orEmpty()
    fun put(key:String,value:String) {
        edited=true
        fields[key]=value
        issues=emptyList()
        onDraftChanged()
    }
    val phaseKey=selectedPhase.name
    fun muscleScope(phase:TrainingPhase):List<TrainingMuscle> {
        val saved=reviewedInputs?.policiesByPhase?.get(phase)?.selectedBounds
            ?.map{it.muscle}?.toSet()
        val marker=fields["${phase.name}:muscleScope"]
        return if(marker=="v1" || marker==null &&
            saved==TrainingMuscleCoverage.legacyV1.toSet())
            TrainingMuscleCoverage.legacyV1 else TrainingMuscleCoverage.current
    }
    val visibleMuscles=muscleScope(selectedPhase)
    LaunchedEffect(selectedPhase,visibleMuscles) {
        if(selectedMuscle !in visibleMuscles)selectedMuscle=TrainingMuscle.CHEST
    }
    fun keyFor(muscle:TrainingMuscle,side:String,field:String)=
        "$phaseKey:${muscle.name}:$side:$field"

    Text("Explicit coaching policy",
        style=MaterialTheme.typography.titleLarge)
    Text("Enter reviewed athlete-specific phase targets and real non-set time. Blank values are not assumed to be zero. This editor does not design your program or auto-approve any dose.",
        style=MaterialTheme.typography.bodySmall)
    if(edited)TextButton(enabled=!busy,onClick={
        fields.clear();liftRows.clear();issues=emptyList();edited=false
        onDiscardDraft()
    }) {Text("Discard unsaved review")}
    OutlinedButton(onClick=onInspectEvidence,enabled=!busy,
        modifier=Modifier.fillMaxWidth()) {
        Text("Inspect actual weekly workload in my plan")
    }
    if(observedEvidence!=null) {
        Text("Verified authored workload · cycle revision ${observedEvidence.auditedCycleRevision}",
            style=MaterialTheme.typography.titleMedium)
        Text("These are factual scheduled working-set credits from the reviewed native exercise catalog. They are not safe minimums, optimal doses, recovery limits, or automatic MEV/MAV/MRV recommendations.",
            style=MaterialTheme.typography.bodySmall)
        observedEvidence.phaseWorkloads.toSortedMap(compareBy{it.ordinal})
            .forEach{(phase,volume)->
                val range=if(volume.minimumWorkingSets==volume.maximumWorkingSets)
                    "${volume.minimumWorkingSets}"
                else "${volume.minimumWorkingSets}–${volume.maximumWorkingSets}"
                Text("${phase.name.replace('_',' ')} · ${volume.trainingWeekCount} training weeks · $range authored working sets/week",
                    style=MaterialTheme.typography.bodyMedium)
            }
        if(observedEvidence.transitions.any{
                it.workingSetsBefore!=it.workingSetsAfter
            }) {
            Text("Observed changes between training weeks",
                style=MaterialTheme.typography.labelLarge)
            observedEvidence.transitions.filter{
                it.workingSetsBefore!=it.workingSetsAfter
            }.take(8).forEach{transition->
                Text("Week ${transition.fromWeek} → ${transition.toWeek}: ${transition.workingSetsBefore} → ${transition.workingSetsAfter} sets (${transition.workingSetChangeFraction?.multiply(java.math.BigDecimal("100"))?.stripTrailingZeros()?.toPlainString()?.plus("%") ?: "change from zero baseline"})",
                    style=MaterialTheme.typography.bodySmall)
            }
        }
    }
    OutlinedButton(onClick={expanded=!expanded},
        enabled=!busy,modifier=Modifier.fillMaxWidth()) {
        Text(if(expanded)"Hide authored policy editor"
            else "Author complete phase policy and session overhead")
    }
    if(!expanded)return

    if(profile==null) {
        Text("The saved athlete and gym source must be reviewed before authoring a policy. Reopen your native program after gym setup.",
            color=MaterialTheme.colorScheme.error)
        return
    }
    Text("Policy context: ${profile.goal.name.lowercase()} · ${profile.experience.name.lowercase()} · ${profile.split.name.replace('_',' ')} · ${profile.trainingWeekdays.size} days per week",
        style=MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        phaseList.forEach {phase ->
            FilterChip(selected=selectedPhase==phase,
                enabled=!busy,
                onClick={selectedPhase=phase},
                label={Text(phase.name.lowercase().replaceFirstChar{
                    it.uppercase()})})
        }
    }
    @Composable
    fun Entry(label:String,id:String,isDecimal:Boolean=false) {
        OutlinedTextField(
            value=get(id),
            onValueChange={put(id,it)},
            enabled=!busy,
            label={Text(label)},
            singleLine=true,
            keyboardOptions=KeyboardOptions(keyboardType=if(isDecimal)
                KeyboardType.Decimal else KeyboardType.Text),
            modifier=Modifier.fillMaxWidth())
    }
    Entry("Policy ID","$phaseKey:id")
    Entry("Policy revision","$phaseKey:revision")
    Entry("Coach's rationale (why these targets?)","$phaseKey:rationale")

    HorizontalDivider()
    Text("Day-purpose minimums",
        style=MaterialTheme.typography.titleMedium)
    Text("Specify minimum sessions for the purposes already scheduled in this phase. The engine checks the core hypertrophy or strength-skill purpose.",
        style=MaterialTheme.typography.bodySmall)
    val scheduled=cycle.blocks.filter{it.phase==selectedPhase}
        .flatMap{it.days}.groupingBy{it.purpose}.eachCount()
    scheduled.keys.sortedBy{it.name}.forEach {purpose ->
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(purpose.name.replace('_',' '),
                    style=MaterialTheme.typography.bodyMedium)
                Text("Scheduled: ${scheduled[purpose]}",
                    style=MaterialTheme.typography.bodySmall)
            }
            Box(Modifier.width(118.dp)) {
                OutlinedTextField(value=get("$phaseKey:purpose:${purpose.name}"),
                    onValueChange={put("$phaseKey:purpose:${purpose.name}",it)},
                    enabled=!busy,singleLine=true,label={Text("Minimum")},
                    keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
            }
        }
    }

    HorizontalDivider()
    Text("Direct muscle targets · ${visibleMuscles.size} groups",
        style=MaterialTheme.typography.titleMedium)
    if(visibleMuscles==TrainingMuscleCoverage.legacyV1) {
        Text("This saved policy retains its original muscle coverage. Adding hip targets requires your own reviewed values.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(enabled=!busy,onClick={put("$phaseKey:muscleScope","v2")},
            modifier=Modifier.heightIn(min=48.dp)) {Text("Add hip muscle targets")}
    }
    Text("Enter a complete baseline and selected MEV/MAV/MRV for each group, including an explicitly entered 0 when that is your reviewed target. Direct-frequency days are separate from set volume. Focused and reduced muscles require a real authored change from baseline.",
        style=MaterialTheme.typography.bodySmall)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        visibleMuscles.forEach{muscle->
            val complete=listOf("baseline","selected").all{side->
                listOf("mev","mav","mrv","days").all{field->
                    get("$phaseKey:${muscle.name}:$side:$field").isNotBlank()
                }
            }
            FilterChip(selected=selectedMuscle==muscle,
                enabled=!busy,onClick={selectedMuscle=muscle},
                label={Text((if(complete)"✓ " else "")+
                    muscle.name.replace('_',' ').lowercase()
                        .replaceFirstChar{it.uppercase()})})
        }
    }
    observedEvidence?.phaseWorkloads?.get(selectedPhase)?.let{phase->
        phase.muscleRanges[selectedMuscle]?.let{measured->
            fun show(n:java.math.BigDecimal)=
                n.stripTrailingZeros().toPlainString()
            Text("Authored plan evidence · ${selectedMuscle.name.replace('_',' ')}",
                style=MaterialTheme.typography.titleSmall)
            Text("Weekly total credit: ${show(measured.minimumWeeklyCredit)}–${show(measured.maximumWeeklyCredit)} · Direct: ${show(measured.minimumWeeklyDirectCredit)}–${show(measured.maximumWeeklyDirectCredit)} · Direct days: ${measured.minimumDirectWeekdays}–${measured.maximumDirectWeekdays}",
                style=MaterialTheme.typography.bodySmall)
            Text("These are observed training-plan exposures, NOT a validated MEV, MAV, MRV or frequency recommendation. You must independently review the targets entered below.",
                style=MaterialTheme.typography.bodySmall)
        }
    }
    if(selectedMuscle in profile.focusedMuscles)
        Text("Focused muscle: your selected dose and/or direct frequency must increase relative to baseline.",
            color=MaterialTheme.colorScheme.secondary,
            style=MaterialTheme.typography.bodySmall)
    if(selectedMuscle in profile.reducedMuscles)
        Text("Reduced muscle: your selected dose and/or direct frequency must decrease relative to baseline.",
            color=MaterialTheme.colorScheme.secondary,
            style=MaterialTheme.typography.bodySmall)
    listOf("baseline" to "Baseline policy",
        "selected" to "Selected athlete policy").forEach{(side,title)->
        Text(title,style=MaterialTheme.typography.titleMedium)
        Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)){
                    Entry("MEV",keyFor(selectedMuscle,side,"mev"),true)
                }
                Box(Modifier.weight(1f)){
                    Entry("MAV",keyFor(selectedMuscle,side,"mav"),true)
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)){
                    Entry("MRV",keyFor(selectedMuscle,side,"mrv"),true)
                }
                Box(Modifier.weight(1f)){
                    Entry("Direct days",keyFor(selectedMuscle,side,"days"))
                }
            }
        }
    }
    OutlinedButton(enabled=!busy,onClick={
        edited=true
        listOf("mev","mav","mrv","days").forEach{field->
            val sourceKey=keyFor(selectedMuscle,"baseline",field)
            val dest=keyFor(selectedMuscle,"selected",field)
            if(get(sourceKey).isNotBlank())
                fields[dest]=get(sourceKey)
        }
        issues=emptyList()
        onDraftChanged()
    }) {Text("Copy this muscle's baseline into selected")}
    Text("Copying is an explicit choice; it does not satisfy a focused or reduced muscle without a different reviewed target.",
        style=MaterialTheme.typography.bodySmall)

    HorizontalDivider()
    Text("Required primary lift frequency",
        style=MaterialTheme.typography.titleMedium)
    Text("Optional named lifts. Add one row per primary exercise that your phase policy explicitly requires; these must appear in the authored schedule.",
        style=MaterialTheme.typography.bodySmall)
    val rowCount=liftRows[selectedPhase] ?: 0
    (0 until rowCount).forEach {i->
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(2f)){
                Entry("Stable exercise ID","$phaseKey:lift:$i:id")
            }
            Box(Modifier.weight(1f)){
                Entry("Days","$phaseKey:lift:$i:days")
            }
        }
    }
    if(rowCount<16)
        OutlinedButton(enabled=!busy,onClick={
            edited=true
            liftRows[selectedPhase]=rowCount+1
            onDraftChanged()
        }){Text("Require another primary lift")}

    HorizontalDivider()
    Text("Observed non-set time, by training weekday",
        style=MaterialTheme.typography.titleMedium)
    Text("Enter explicit seconds for setup, travel between stations, transitions and cooldown. Do not count authored working/rest seconds again.",
        style=MaterialTheme.typography.bodySmall)
    observedEvidence?.phaseWorkloads?.get(selectedPhase)?.let{phase->
        Text("Known authored set/rest time in this phase (excludes all unknown non-set time):",
            style=MaterialTheme.typography.labelMedium)
        phase.minimumKnownSecondsByDay.keys.sorted().forEach{day->
            Text("Day ${day+1}: ${phase.minimumKnownSecondsByDay[day]}–${phase.maximumKnownSecondsByDay[day]} seconds",
                style=MaterialTheme.typography.bodySmall)
        }
    }
    val weekdayLabels=listOf("Monday","Tuesday","Wednesday","Thursday",
        "Friday","Saturday","Sunday")
    profile.trainingWeekdays.sorted().forEach{day->
        Entry("${weekdayLabels[day]} · overhead seconds","time:$day")
    }

    HorizontalDivider()
    Text("Continuity limits for changing training weeks",
        style=MaterialTheme.typography.titleMedium)
    Text("Where week-level sets, direct muscle exposure, or blocks change, enter reviewed maximum decline fractions (e.g. 0.20) and, when weeks increase, rise fractions. Leave all four blank only when no continuity policy is needed; the native audit verifies this.",
        style=MaterialTheme.typography.bodySmall)
    Entry("Maximum total-set drop fraction","continuity:dropTotal",true)
    Entry("Maximum direct-muscle drop fraction","continuity:dropDirect",true)
    Entry("Maximum total-set rise fraction","continuity:riseTotal",true)
    Entry("Maximum direct-muscle rise fraction","continuity:riseDirect",true)

    if(issues.isNotEmpty()) {
        Text("${issues.size} policy inputs need attention",
            color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.titleSmall)
        issues.take(12).forEach{issue->
            Text("• $issue",color=MaterialTheme.colorScheme.error,
                style=MaterialTheme.typography.bodySmall)
        }
        if(issues.size>12)Text("${issues.size-12} more requirements. Resolve each phase and muscle before auditing.",
            style=MaterialTheme.typography.bodySmall)
    }
    Button(enabled=!busy,modifier=Modifier.fillMaxWidth(),onClick={
        val result=run {
            val phases=phaseList.map{phase->
                val key=phase.name
                fun entries(side:String)=muscleScope(phase).associateWith{muscle->
                    CustomMuscleDoseEntry(
                        get("$key:${muscle.name}:$side:mev"),
                        get("$key:${muscle.name}:$side:mav"),
                        get("$key:${muscle.name}:$side:mrv"),
                        get("$key:${muscle.name}:$side:days"))
                }
                val purpose=cycle.blocks.filter{it.phase==phase}
                    .flatMap{it.days}.map{it.purpose}.distinct()
                    .associateWith{get("$key:purpose:${it.name}")}
                val lifts=linkedMapOf<String,String>()
                var invalidLift=false
                (0 until (liftRows[phase] ?:0)).forEach{i->
                    val name=get("$key:lift:$i:id").trim()
                    val days=get("$key:lift:$i:days")
                    if(name.isBlank() || lifts.containsKey(name))
                        invalidLift=true
                    else lifts[name]=days
                }
                if(invalidLift)
                    issues=listOf("$key: primary lift IDs must be unique and nonempty.")
                CustomPhasePolicyEntry(phase,get("$key:id"),
                    get("$key:revision"),get("$key:rationale"),
                    entries("baseline"),entries("selected"),
                    purpose,lifts)
            }
            if(issues.isNotEmpty()) CustomCoachPolicyAuthoringResult.Blocked(issues)
            else CustomCoachPolicyAuthoringDomain.inspect(
                cycle,checkNotNull(source),CustomCoachPolicyAuthoringRequest(
                    phases,profile.trainingWeekdays.associateWith{
                        get("time:$it")},
                    get("continuity:dropTotal"),
                    get("continuity:dropDirect"),
                    get("continuity:riseTotal"),
                    get("continuity:riseDirect")))
        }
        when(result) {
            is CustomCoachPolicyAuthoringResult.Ready -> {
                issues=emptyList()
                edited=false
                onReview(result.inputs)
            }
            is CustomCoachPolicyAuthoringResult.Blocked ->
                issues=result.missingOrInvalid
        }
    }) {Text("Validate my policy and audit the full cycle")}
    Text("No consent or workout is saved here. The native controller reopens the current gym/source and exact cycle again before showing any audit result.",
        style=MaterialTheme.typography.bodySmall)
}
