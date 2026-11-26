package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

/** Gym is the one library/preference home; its source owner performs every save. */
@Composable
internal fun NativeKotlinExerciseLibraryAccess(
    source:NativeCustomAuthoringSnapshot,busy:Boolean,feedback:String?,
    lastEdit:NativeKotlinExerciseExclusionCommit?,
    onReviewPlan:(()->Unit)?,
    historyHost:NativeKotlinCandidateHost?,
    onSave:(NativeGymExerciseExclusionChange)->Unit,
    onUndo:(NativeKotlinExerciseExclusionCommit)->Unit,
    onCreate:(NativeUserExerciseDraft)->Unit
) {
    var open by rememberSaveable(source.ownerKey,source.athleteId) { mutableStateOf(false) }
    var creating by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf(false)}
    PursuitCard {
        PursuitSectionHeading("Exercise library",
            "${source.catalog.size+MeasuredExerciseCatalogDomain.all.size} exercises · ${source.setup.reviewed?.excludedExerciseIds?.size ?: 0} excluded")
        Text("Find movements by muscle, equipment or attachment. Exclusions apply to future exercise choices; saved workout targets stay separate.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick={open=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
            Text("Browse exercises")
        }
        TextButton(onClick={creating=true},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
            Text("Create custom exercise")
        }
        lastEdit?.takeIf{it.matches(source)}?.let { edit ->
            TextButton(onClick={onUndo(edit)},enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text("Undo last exclusion change")
            }
        }
        onReviewPlan?.let { review ->
            TextButton(onClick=review,enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text("Review your program")
            }
        }
    }
    if(open) NativeKotlinExerciseLibrarySheet(source,busy,feedback,lastEdit,historyHost,onSave) {open=false}
    if(creating) NativeUserExerciseSheet(source,busy,feedback,onCreate) {creating=false}
}

@Composable
private fun NativeKotlinExerciseLibrarySheet(
    source:NativeCustomAuthoringSnapshot,busy:Boolean,feedback:String?,
    lastEdit:NativeKotlinExerciseExclusionCommit?,
    historyHost:NativeKotlinCandidateHost?,
    onSave:(NativeGymExerciseExclusionChange)->Unit,onDismiss:()->Unit
) {
    var baseRevision by rememberSaveable(source.ownerKey,source.athleteId) { mutableLongStateOf(source.revision) }
    var original by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf(source.setup.reviewed?.excludedExerciseIds.orEmpty().toList())}
    var draft by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf(original)}
    var query by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf("")}
    var muscleId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var gearId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var familyId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var angleId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var attachmentId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var patternId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var roleId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var availability by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf(NativeLibraryAvailability.ALL)}
    var showFilters by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf(false)}
    var visibleCount by rememberSaveable(source.ownerKey,source.athleteId,
        query,muscleId,gearId,familyId,angleId,attachmentId,patternId,roleId,
        availability) {mutableIntStateOf(20)}
    var selectedId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var historyId by rememberSaveable(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var filterPicker by remember(source.ownerKey,source.athleteId) {mutableStateOf<String?>(null)}
    var discard by remember(source.ownerKey,source.athleteId) {mutableStateOf(false)}
    var submitted by remember(source.ownerKey,source.athleteId) {mutableStateOf<NativeGymExerciseExclusionChange?>(null)}
    val changed=draft.toSet()!=original.toSet()
    val stale=baseRevision!=source.revision
    val equipmentNames=remember(source.ownerKey,source.athleteId,source.revision) {
        kotlinGymStockChoices(source.catalog).associate{it.equipmentId to it.displayName}
    }
    fun gearLabel(id:String):String=when(id) {
        nativeLibraryBodyweight -> "Bodyweight"
        else -> equipmentNames[id] ?: kotlinGymFacilityLabel(id).takeIf{it!=id}
            ?: kotlinGymAttachmentLabel(id)
    }
    val records=remember(source) {
        nativeExerciseLibraryEntries(source,
            {equipmentNames[it] ?: kotlinGymFacilityLabel(it)},
            ::kotlinGymFacilityLabel,::kotlinGymAttachmentLabel)
    }
    val muscle=TrainingMuscle.entries.firstOrNull{it.name==muscleId}
    val pattern=ComposerPattern.entries.firstOrNull{it.name==patternId}
    val role=ComposerRole.entries.firstOrNull{it.name==roleId}
    val angle=NativeBenchAngle.entries.firstOrNull{it.name==angleId}
    val matches=filterNativeExerciseLibrary(records,query,muscle,gearId,availability,
        draft.toSet(),pattern,role,familyId,angle,attachmentId)
    val overLimit=draft.size>CoachAthleteSetupDomain.MAX_EXCLUDED_EXERCISES
    LaunchedEffect(lastEdit,source) {
        if(submitted!=null && lastEdit!=null && lastEdit.request==submitted && lastEdit.matches(source))onDismiss()
    }
    fun close() {if(changed)discard=true else onDismiss()}
    NativeEditSheet(busy,onDismiss=::close,canDismiss={
        if(changed) {discard=true;false} else true
    },footer={
        val additions=(draft.toSet()-original.toSet()).size
        val removals=(original.toSet()-draft.toSet()).size
        if(changed)Text("Pending: exclude $additions · include $removals. Save only changes exercise preferences. Your plan needs renewed review before new workouts.",
            style=MaterialTheme.typography.bodySmall)
        if(overLimit)Text("At most ${CoachAthleteSetupDomain.MAX_EXCLUDED_EXERCISES} exercises can be excluded.",
            color=MaterialTheme.colorScheme.error)
        PursuitResponsiveActionPair(
            primary={modifier->Button(onClick={
                val request=NativeGymExerciseExclusionChange(source.ownerKey,source.athleteId,
                    baseRevision,original.toSet(),draft.toSet())
                submitted=request;onSave(request)
            },enabled=!busy && changed && !stale && !overLimit && records.isNotEmpty(),modifier=modifier) {Text("Save exclusions")}},
            secondary={modifier->OutlinedButton(onClick=::close,enabled=!busy,modifier=modifier) {Text("Close library")}})
    }) {
        PursuitSectionHeading("Exercise library","Browse your saved catalog. Eligibility is checked against your gym and preferences.")
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(stale) {
            Text("Your setup changed while this library was open. Reload saved choices before editing.",
                color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={
                baseRevision=source.revision
                original=source.setup.reviewed?.excludedExerciseIds.orEmpty().toList()
                draft=original;submitted=null;selectedId=null
            },enabled=!busy) {Text("Reload saved library · discard pending changes")}
        }
        feedback?.let{Text(it,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)}
        OutlinedTextField(query,{query=it.take(80)},
            label={Text("Search name, muscle, equipment or attachment")},
            modifier=Modifier.fillMaxWidth(),singleLine=true)
        PursuitDisclosureRow("Filters",
            listOf(muscle?.let{nativeLibraryWords(it.name)} ?: "All muscles",
                pattern?.let{nativeLibraryWords(it.name)} ?: "Any movement pattern",
                role?.let{nativeLibraryWords(it.name)} ?: "Any program role",
                gearId?.let(::gearLabel) ?: "Any equipment",
                familyId?.let(::nativeLibraryWords) ?: "Any exercise family",
                angle?.display ?: "Any bench angle",
                attachmentId?.let(::kotlinGymAttachmentLabel) ?: "Any attachment",
                nativeLibraryWords(availability.name)).joinToString(" · "),
            showFilters,{showFilters=!showFilters})
        if(showFilters) {
            for((id,label) in listOf(
                "muscle" to (muscle?.let{nativeLibraryWords(it.name)} ?: "All muscles"),
                "pattern" to (pattern?.let{nativeLibraryWords(it.name)} ?: "Any movement pattern"),
                "role" to (role?.let{nativeLibraryWords(it.name)} ?: "Any program role"),
                "gear" to (gearId?.let(::gearLabel) ?: "Any equipment or fixture"),
                "family" to (familyId?.let(::nativeLibraryWords) ?: "Any exercise family"),
                "angle" to (angle?.display ?: "Any incline level"),
                "attachment" to (attachmentId?.let(::kotlinGymAttachmentLabel) ?: "Any attachment"),
                "availability" to nativeLibraryWords(availability.name))) {
                OutlinedButton(onClick={filterPicker=id},modifier=Modifier.fillMaxWidth()) {Text(label)}
            }
        }
        if(query.isNotBlank() || muscleId!=null || patternId!=null || roleId!=null ||
            gearId!=null || familyId!=null || angleId!=null || attachmentId!=null ||
            availability!=NativeLibraryAvailability.ALL)
            TextButton(onClick={
                query="";muscleId=null;patternId=null;roleId=null
                gearId=null;familyId=null;angleId=null;attachmentId=null
                availability=NativeLibraryAvailability.ALL
            }) {Text("Clear search and filters")}
        val inherited=original.toSet()-records.map{it.exerciseId}.toSet()
        if(inherited.isNotEmpty())Text("${inherited.size} exclusion(s) from an older catalog will be retained.",
            style=MaterialTheme.typography.bodySmall)
        Text("${matches.size} matching exercises",style=MaterialTheme.typography.labelLarge)
        if(records.isEmpty())Text("No verified exercise definitions are available. Reload your gym before changing preferences.")
        else if(matches.isEmpty())Text("No exercises match. Clear the search or filters to browse the saved catalog.")
        matches.take(visibleCount).forEach { record ->
            val id=record.exerciseId
            val pending=(id in draft)!=(id in original)
            val status=when {
                pending -> if(id in draft)"Exclude pending" else "Include pending · save to recheck eligibility"
                record.savedExcluded -> "Excluded"
                record.eligible -> "Eligible for exercise selection"
                else -> "Needs gym or preference review"
            }
            PursuitDestinationRow(record.label,
                "${record.variantDescription} · ${record.equipmentLabel} · $status",
                onClick={selectedId=id},icon={Icon(Icons.Outlined.FitnessCenter,contentDescription=null)})
        }
        if(visibleCount<matches.size)TextButton(onClick={visibleCount=(visibleCount+20).coerceAtMost(matches.size)},
            modifier=Modifier.fillMaxWidth()) {Text("Show more exercises (${matches.size-visibleCount} remaining)")}

    }
    selectedId?.let { id ->records.singleOrNull{it.exerciseId==id}?.let { record ->
        val move=record.standard
        val athlete=checkNotNull(source.setup.reviewed)
        NativeEditSheet(busy,onDismiss={selectedId=null}) {
            PursuitSectionHeading(record.label,if(record.eligible)"Eligible for exercise selection" else "Review saved requirements and preferences")
            if(record is NativeMeasuredLibraryRecord) {
                val definition=record.definition
                Text(record.equipmentLabel+" · "+record.variantDescription)
                Text("Measurement · "+when(definition.kind){
                    MeasuredExerciseKind.HOLD_SECONDS->"Elapsed seconds"
                    MeasuredExerciseKind.CARRY_CENTIMETERS->"Distance"
                    MeasuredExerciseKind.BANDED_REPETITIONS->"Repetitions with a verified band"
                })
                Text("Load · "+nativeLibraryWords(definition.loadKind.name))
                Text("Sides · "+nativeLibraryWords(definition.sideRule.name))
                definition.requiredFixtures.sorted().forEach{Text("Required · "+kotlinGymFacilityLabel(it))}
                Text("Measured sessions use typed targets and actuals. No repetition-based load or muscle credits are inferred.",
                    style=MaterialTheme.typography.bodySmall)
                if(!record.eligible && !record.savedExcluded)Text("Review gym stock, fixtures and preferences before choosing this exercise.")
            } else if(move!=null) {
            Text(record.equipmentLabel+" · "+kotlinLoadBasisLabel(move.expectedLoadBasis))
            Text("Variant · "+record.variantDescription,
                style=MaterialTheme.typography.bodyMedium)
            Text("Named incline levels are categorical, not verified angles in degrees. Different benches, attachments and physical machines never share a load baseline.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            val direct=move.credits.filter{it.role==MuscleCreditRole.DIRECT}.map{nativeLibraryWords(it.muscle.name)}
            val supporting=move.credits.filter{it.role==MuscleCreditRole.INDIRECT}.map{nativeLibraryWords(it.muscle.name)}
            Text("Direct targets · "+direct.joinToString().ifBlank{"None declared"})
            if(supporting.isNotEmpty())Text("Supporting targets · "+supporting.joinToString())
            Text("${nativeLibraryWords(move.pattern.name)} · ${nativeLibraryWords(move.role.name)}",
                style=MaterialTheme.typography.bodySmall)
            move.sourcePracticalReps?.let{Text("Catalog rep range · ${it.minimum}–${it.maximum}")}
            Text("Catalog targets are provisional. Your reviewed program supplies actual sets, loads and effort.",
                style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            move.equipmentId?.let { equipment ->
                Text(if(equipment in athlete.gym.confirmedStock)"Weight source saved · ${record.equipmentLabel}"
                    else "Confirm weight source · ${record.equipmentLabel}")
            }
            move.requiredFacilityIds.sorted().forEach { fixture ->
                Text((if(fixture in athlete.gym.availableFacilities)"Confirmed · " else "Needed · ")+kotlinGymFacilityLabel(fixture))
            }
            move.attachmentId?.let { attachment ->
                Text((if(attachment in athlete.gym.availableAttachments)"Confirmed · " else "Needed · ")+kotlinGymAttachmentLabel(attachment))
            }
            if(move.equipmentId==null && !athlete.allowBodyweight)Text("Bodyweight exercises are disabled in your saved setup.")
            if(move.expectedLoadBasis?.countsTowardBarbellLimit==true && athlete.maxBarbellExercisesPerDay==0)
                Text("Your saved barbell-per-day limit is zero.")
            if(!record.eligible && !record.savedExcluded)Text("This movement is not currently eligible. Review gym stock, fixtures and preferences; browsing cannot approve it.")
            val related=nativeExerciseFamilyVariants(records.filterIsInstance<NativeKotlinExerciseLibraryRecord>(),id)
            if(related.isNotEmpty()) {
                var expandVariants by rememberSaveable(source.ownerKey,id) {
                    mutableStateOf(false)
                }
                HorizontalDivider()
                Text("Other variants in this exercise family",
                    style=MaterialTheme.typography.titleMedium)
                Text("${related.size} separately named variants · equipment and attachments are verified individually. Viewing does not switch a program or workout.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                (if(expandVariants)related else related.take(6)).forEach { sibling ->
                    PursuitDestinationRow(sibling.label,
                        sibling.variantDescription+" · "+
                        (if(sibling.eligible)"Eligible at this gym"
                            else "Needs equipment or preference review"),
                        onClick={selectedId=sibling.movement.exerciseId},
                        icon={Icon(Icons.Outlined.FitnessCenter,
                            contentDescription=null)})
                }
                if(related.size>6)TextButton(
                    onClick={expandVariants=!expandVariants},
                    modifier=Modifier.heightIn(min=48.dp)) {
                    Text(if(expandVariants)"Show fewer variants"
                        else "Show all ${related.size} variants")
                }
            }
            }
            val excluded=id in draft
            OutlinedButton(onClick={draft=if(excluded)draft-id else draft+id},
                enabled=!busy && !stale && (excluded || draft.size<CoachAthleteSetupDomain.MAX_EXCLUDED_EXERCISES),
                modifier=Modifier.fillMaxWidth()) {
                Text(if(move?.creditReviewReference=="athlete-declared-v1")
                    if(excluded)"Restore custom exercise" else "Retire custom exercise"
                    else if(excluded)"Include in future exercise choices" else "Exclude from future exercise choices")
            }
            if(move?.creditReviewReference=="athlete-declared-v1")
                Text("Retiring keeps this exact exercise and all recorded history. Save exclusions to apply the change.",
                    style=MaterialTheme.typography.bodySmall)
            Text(if((id in draft)!=(id in original))"Change pending · use Save exclusions in the library."
                else if(excluded)"Excluded in saved preferences." else "Included in saved preferences.",
                style=MaterialTheme.typography.bodySmall)
            if(historyHost!=null)OutlinedButton(onClick={historyId=id},enabled=!busy,
                modifier=Modifier.fillMaxWidth()) {Text("View recorded exercise history")}
            TextButton(onClick={selectedId=null},enabled=!busy,modifier=Modifier.fillMaxWidth()) {Text("Back to library")}
        }
    }}
    if(historyHost!=null)historyId?.let { id ->
        NativeKotlinExerciseHistorySheet(historyHost,id,onDismiss={historyId=null})
    }
    filterPicker?.let { kind ->
        val options:List<Pair<String?,String>> = when(kind) {
            "muscle" -> listOf(null to "All muscles")+TrainingMuscle.entries.map{it.name to nativeLibraryWords(it.name)}
            "pattern" -> listOf(null to "Any movement pattern")+records.mapNotNull{it.standard?.pattern}.distinct()
                .sortedBy{nativeLibraryWords(it.name)}.map{it.name to nativeLibraryWords(it.name)}
            "role" -> listOf(null to "Any program role")+records.mapNotNull{it.standard?.role}.distinct()
                .sortedBy{nativeLibraryWords(it.name)}.map{it.name to nativeLibraryWords(it.name)}
            "gear" -> listOf(null to "Any equipment or fixture")+records.flatMap{it.gearIds}.distinct()
                .sortedBy{gearLabel(it)}.map{it to gearLabel(it)}
            "family" -> listOf(null to "Any exercise family")+records.mapNotNull{it.standard?.familyId}.distinct()
                .sortedBy{nativeLibraryWords(it)}.map{it to nativeLibraryWords(it)}
            "angle" -> listOf(null to "Any incline level")+NativeBenchAngle.entries.map{it.name to it.display}
            "attachment" -> listOf(null to "Any attachment")+records.mapNotNull{it.standard?.attachmentId}.distinct()
                .sortedBy{kotlinGymAttachmentLabel(it)}.map{it to kotlinGymAttachmentLabel(it)}
            else -> NativeLibraryAvailability.entries.map{
                it.name to nativeLibraryWords(it.name)
            }
        }
        AlertDialog(onDismissRequest={filterPicker=null},title={Text(when(kind){
            "muscle"->"Muscle";"pattern"->"Movement pattern";"role"->"Program role"
            "gear"->"Equipment and fixtures";"family"->"Exercise family"
            "angle"->"Incline level";"attachment"->"Cable attachment"
            else->"Availability"
        })},
            text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                options.forEach{(id,label)->
                    val checked=when(kind){
                        "muscle"->muscleId==id;"pattern"->patternId==id
                        "role"->roleId==id;"gear"->gearId==id
                        "family"->familyId==id;"angle"->angleId==id
                        "attachment"->attachmentId==id
                        else->availability.name==id
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .selectable(checked,role=Role.RadioButton,onClick={
                            when(kind){
                                "muscle"->muscleId=id;"pattern"->patternId=id
                                "role"->roleId=id;"gear"->gearId=id
                                "family"->familyId=id;"angle"->angleId=id
                                "attachment"->attachmentId=id
                                else->availability=NativeLibraryAvailability.valueOf(checkNotNull(id))
                            }
                            filterPicker=null
                        }).padding(PursuitSpace.xs),verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=checked,onClick=null)
                        Text(label,modifier=Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
                    }
                }
            }},confirmButton={TextButton(onClick={filterPicker=null}) {Text("Cancel")}})
    }
    if(discard)AlertDialog(onDismissRequest={discard=false},title={Text("Discard exclusion changes?")},
        text={Text("Your saved preferences stay as they are. Search and browsing have not changed your program.")},
        confirmButton={TextButton(onClick={discard=false;onDismiss()}) {Text("Discard changes")}},
        dismissButton={TextButton(onClick={discard=false}) {Text("Keep editing")}})
}


/** One source-owned editor. Explicit choices only; no guessed stock, muscle
 * credit, training role, or exercise prescription from a typed name. */
@Composable
private fun NativeUserExerciseSheet(
    source:NativeCustomAuthoringSnapshot,busy:Boolean,feedback:String?,
    onSave:(NativeUserExerciseDraft)->Unit,onDismiss:()->Unit
) {
    val athlete=source.setup.reviewed ?:return
    val gym=athlete.gym
    val revision=rememberSaveable(source.ownerKey,source.athleteId){source.revision}
    var name by rememberSaveable{mutableStateOf("")}
    var pattern by rememberSaveable{mutableStateOf<String?>(null)}
    var role by rememberSaveable{mutableStateOf<String?>(null)}
    var equipment by rememberSaveable{mutableStateOf<String?>(null)}
    var attachment by rememberSaveable{mutableStateOf<String?>(null)}
    var facilities by rememberSaveable{mutableStateOf(emptyList<String>())}
    var muscles by rememberSaveable{mutableStateOf(emptyList<String>())}
    var focus by rememberSaveable{mutableStateOf(emptyList<String>())}
    var low by rememberSaveable{mutableStateOf("")}
    var high by rememberSaveable{mutableStateOf("")}
    var confirmed by remember{mutableStateOf(false)}
    var picker by remember{mutableStateOf<String?>(null)}
    var submitted by rememberSaveable{mutableStateOf<String?>(null)}
    val stableId=NativeKotlinGymCatalogEditingDomain.userExerciseId(name)
    val min=low.toIntOrNull()
    val max=high.toIntOrNull()
    val stale=revision!=source.revision
    val unique=stableId!=null && source.catalog.none{it.exerciseId==stableId}
    val valid=unique && pattern!=null && role!=null && equipment!=null &&
        muscles.isNotEmpty() && focus.isNotEmpty() &&
        min!=null && max!=null && min in 1..100 && max in min..100
    LaunchedEffect(source.revision,submitted) {
        if(submitted!=null && source.revision>revision &&
            source.catalog.any{it.exerciseId==submitted &&
                it.creditReviewReference=="athlete-declared-v1"})onDismiss()
    }
    LaunchedEffect(name,pattern,role,equipment,attachment,facilities,muscles,focus,low,high) {
        confirmed=false
    }
    NativeEditSheet(busy,onDismiss=onDismiss) {
        PursuitSectionHeading("Create custom exercise",
            "Choose the real movement, equipment and primary muscle targets.")
        OutlinedTextField(name,{name=it.take(50)},label={Text("Exercise name")},
            singleLine=true,modifier=Modifier.fillMaxWidth(),enabled=!busy&&!stale)
        stableId?.let{Text("Saved name: "+kotlinExerciseLabel(it),
            style=MaterialTheme.typography.bodySmall)}
        if(stableId!=null&&!unique)Text("That custom exercise already exists. Restore it in the library instead.",
            color=MaterialTheme.colorScheme.error)
        for((kind,label) in listOf(
            "pattern" to (pattern?.let(::nativeLibraryWords) ?: "Choose movement pattern"),
            "role" to (role?.let(::nativeLibraryWords) ?: "Choose program role"),
            "equipment" to (equipment?.let{
                if(it==nativeLibraryBodyweight)"Bodyweight" else
                    nativeLibraryWords(it)+" · "+kotlinLoadBasisLabel(gym.confirmedStock[it]?.basis)
            } ?: "Choose confirmed equipment"),
            "attachment" to (attachment?.let(::kotlinGymAttachmentLabel) ?: "Attachment · none"),
            "facilities" to ("Required fixtures · "+facilities.size),
            "muscles" to ("Primary muscles · "+muscles.size),
            "focus" to ("Training-day roles · "+focus.size))) {
            OutlinedButton(onClick={picker=kind},enabled=!busy&&!stale,
                modifier=Modifier.fillMaxWidth()){Text(label)}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(low,{low=it.filter(Char::isDigit).take(3)},
                label={Text("Min reps")},singleLine=true,modifier=Modifier.weight(1f))
            OutlinedTextField(high,{high=it.filter(Char::isDigit).take(3)},
                label={Text("Max reps")},singleLine=true,modifier=Modifier.weight(1f))
        }
        Text("Primary muscles receive provisional direct-set credit. Your review supplies the movement metadata; no work sets or loads are created.",
            style=MaterialTheme.typography.bodySmall)
        Row(verticalAlignment=Alignment.CenterVertically) {
            Checkbox(confirmed,{confirmed=it},enabled=valid&&!busy&&!stale)
            Text("I confirm these movement and equipment details.")
        }
        if(stale)Text("Gym or catalog changed. Close and review the current setup before saving.",
            color=MaterialTheme.colorScheme.error)
        feedback?.let{Text(it,style=MaterialTheme.typography.bodySmall)}
        Button(onClick={
            val id=checkNotNull(stableId)
            submitted=id
            onSave(NativeUserExerciseDraft(source.ownerKey,source.athleteId,revision,
                name,ComposerPattern.valueOf(checkNotNull(pattern)),
                ComposerRole.valueOf(checkNotNull(role)),focus.map{ComposerFocus.valueOf(it)}.toSet(),
                equipment==nativeLibraryBodyweight,equipment?.takeUnless{it==nativeLibraryBodyweight},
                attachment,facilities.toSet(),muscles.map{TrainingMuscle.valueOf(it)}.toSet(),
                RepBand(checkNotNull(min),checkNotNull(max)),confirmed))
        },enabled=valid&&confirmed&&!busy&&!stale,modifier=Modifier.fillMaxWidth()){
            Text("Save custom exercise")
        }
        TextButton(onClick=onDismiss,enabled=!busy,modifier=Modifier.fillMaxWidth()){Text("Cancel")}
    }
    picker?.let { kind ->
        val options:List<Pair<String?,String>> = when(kind) {
            "pattern"->ComposerPattern.entries.map{it.name to nativeLibraryWords(it.name)}
            "role"->ComposerRole.entries.map{it.name to nativeLibraryWords(it.name)}
            "equipment"->(if(athlete.allowBodyweight)listOf(nativeLibraryBodyweight to "Bodyweight")
                else emptyList())+gym.confirmedStock.toSortedMap().map{(id,stock)->
                    id to (nativeLibraryWords(id)+" · "+kotlinLoadBasisLabel(stock.basis))}
            "attachment"->listOf(null to "None")+gym.availableAttachments.sorted().map{it to kotlinGymAttachmentLabel(it)}
            "facilities"->gym.availableFacilities.sorted().map{it to kotlinGymFacilityLabel(it)}
            "muscles"->TrainingMuscle.entries.map{it.name to nativeLibraryWords(it.name)}
            else->ComposerFocus.entries.map{it.name to nativeLibraryWords(it.name)}
        }
        val multi=kind in setOf("facilities","muscles","focus")
        AlertDialog(onDismissRequest={picker=null},title={Text(nativeLibraryWords(kind))},
            text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState())) {
                options.forEach{(id,label)->
                    val checked=when(kind){
                        "pattern"->pattern==id;"role"->role==id;"equipment"->equipment==id
                        "attachment"->attachment==id;"facilities"->id in facilities
                        "muscles"->id in muscles;else->id in focus
                    }
                    Row(Modifier.fillMaxWidth().heightIn(min=48.dp)
                        .selectable(checked,role=if(multi)Role.Checkbox else Role.RadioButton,onClick={
                            fun toggle(values:List<String>):List<String>{
                                val item=checkNotNull(id)
                                return if(item in values)values-item else values+item
                            }
                            when(kind){
                                "pattern"->pattern=id;"role"->role=id;"equipment"->equipment=id
                                "attachment"->attachment=id;"facilities"->facilities=toggle(facilities)
                                "muscles"->muscles=toggle(muscles);else->focus=toggle(focus)
                            }
                            if(!multi)picker=null
                        }).padding(PursuitSpace.xs),verticalAlignment=Alignment.CenterVertically) {
                        if(multi)Checkbox(checked,onCheckedChange=null)
                        else RadioButton(checked,onClick=null)
                        Text(label,modifier=Modifier.weight(1f))
                    }
                }
            }},confirmButton={TextButton(onClick={picker=null}){Text("Done")}})
    }
}
