package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal

/** Display real corrected native facts. No JS history, estimated PRs or invented dates. */
@Composable
internal fun NativeKotlinCandidateHistoryScreen(
    host:NativeKotlinCandidateHost,
    preferredWorkoutId:String?=null,
    onClearWorkoutFocus:()->Unit={},
    onOpenProgram:()->Unit={},
    onOpenCoach:(String?)->Unit={},
    onOpenProgress:()->Unit={},
    onOpenExerciseTrend:(String)->Unit={}
) {
    val scope=rememberCoroutineScope()
    // An in-flight read for the old host must never render as the new
    // athlete's verified timeline while cold-start revalidation runs.
    var busy by remember(host){mutableStateOf(false)}
    var result by remember(host){mutableStateOf<NativeKotlinMixedHistoryReadResult?>(null)}
    // Separate-History consent belongs to the exact signed local owner that
    // reviewed NOT_ENROLLED. It is intentionally not saveable across process
    // death or a new authenticated host.
    var showSeparateHistoryConsent by remember(host){mutableStateOf(false)}
    var understandsSeparateHistory by remember(host){mutableStateOf(false)}
    var consentedHistoryOwner by remember(host){
        mutableStateOf<NativeKotlinOwnerIdentity?>(null)
    }
    var enrollmentMessage by remember{mutableStateOf<String?>(null)}
    var finishRecoveryNotice by remember{mutableStateOf<String?>(null)}
    val context=LocalContext.current
    // This is an export intent, not a cache of any athlete's sensitive data.
    // Recheck the complete owner-verified history after Android returns from
    // the document picker; account switches or new corrections fail closed.
    var pendingCsvExport by remember {
        // ownerKey, athleteId, approved History revision
        mutableStateOf<Triple<String,String,Long>?>(null)
    }
    var exporting by remember {mutableStateOf(false)}
    var exportNotice by remember {mutableStateOf<String?>(null)}
    var historyOptionsExpanded by rememberSaveable{mutableStateOf(false)}
    val csvDocument=rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val expected=pendingCsvExport
        pendingCsvExport=null
        if(uri==null) {
            exportNotice="History export canceled. No training data was shared."
        } else if(expected==null) {
            exportNotice="Could not verify your export request. The selected file may be empty; please delete it."
        } else {
            exporting=true
            scope.launch {
                exportNotice=withContext(Dispatchers.IO) {
                    val ownerBefore=runCatching {
                        host.currentHistoryExportOwner()
                    }.getOrNull()
                    val reread=runCatching {
                        host.reviewVerifiedKotlinHistory()
                    }.getOrNull()
                    val ownerAfter=runCatching {
                        host.currentHistoryExportOwner()
                    }.getOrNull()
                    val trusted=(reread as?
                        NativeKotlinMixedHistoryReadResult.Review)?.timeline
                    if(ownerBefore==null || ownerBefore!=ownerAfter ||
                        ownerBefore.ownerKey!=expected.first ||
                        ownerBefore.athleteId!=expected.second ||
                        trusted==null ||
                        trusted.athleteId!=expected.second ||
                        trusted.manifestRevision!=expected.third) {
                        "History changed or could not be verified. Nothing was exported. Delete the empty file and try again."
                    } else when(val csv=
                        NativeKotlinVerifiedHistoryCsvExport.prepare(trusted)) {
                        is NativeKotlinHistoryCsvResult.Ready -> {
                            // The signed owner cannot silently change while
                            // we produce a file selected in a prior Activity.
                            val success=runCatching {
                                check(host.currentHistoryExportOwner()==ownerBefore)
                                context.contentResolver.openOutputStream(uri,"wt")
                                    ?.use {output->
                                        output.write(csv.csv.toByteArray(Charsets.UTF_8))
                                        output.flush()
                                    } ?:error("Could not open destination")
                            }.isSuccess
                            if(success)
                                "Exported ${csv.sessionCount} completed workouts and ${csv.rowCount} set entries. Keep the file private; CSV is not a restorable app backup."
                            else
                                "Could not write the history file. It may be empty or incomplete; delete it and try again."
                        }
                        NativeKotlinHistoryCsvResult.InvalidHistory ->
                            "History could not be safely exported. Delete the empty file; saved records are unchanged."
                        NativeKotlinHistoryCsvResult.TooLarge ->
                            "This history exceeds the CSV export limit. Delete the empty file; saved records are unchanged."
                    }
                }
                exporting=false
            }
        }
    }
    var historyExerciseId by rememberSaveable{mutableStateOf<String?>(null)}
    var historyExerciseAthleteId by rememberSaveable{mutableStateOf<String?>(null)}
    var exerciseSearch by rememberSaveable{mutableStateOf("")}
    var exerciseSearchAthleteId by rememberSaveable{mutableStateOf<String?>(null)}
    fun reload() {
        if(busy)return
        busy=true;result=null;finishRecoveryNotice=null
        scope.launch {
            result=withContext(Dispatchers.IO) {
                runCatching{host.reviewVerifiedKotlinHistory()}.getOrElse{
                    NativeKotlinMixedHistoryReadResult.Rejected(NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)}
            }
            busy=false
        }
    }
    LaunchedEffect(host){reload()}
    val verified=(result as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
    // A URL/Compose ID alone never grants access. The only admitted focus
    // is a unique completed workout in the CURRENT replay-verified timeline.
    val focusedWorkoutId=remember(verified,preferredWorkoutId) {
        NativeKotlinHistoryFocusPolicy.admit(
            preferredWorkoutId,
            verified?.completions?.map{it.manifest.workoutId}.orEmpty())
    }
    val focusedWorkout=verified?.completions?.singleOrNull {
        it.manifest.workoutId==focusedWorkoutId
    }
    val searchableMovements=remember(verified) {
        verified?.completions.orEmpty().flatMap{entry->
            when(entry) {
                is NativeKotlinMixedHistoryCompletion.Composer ->
                    entry.evidence.sets.map{it.exerciseId}
                is NativeKotlinMixedHistoryCompletion.Custom ->
                    entry.evidence.visits.map{it.exerciseId}
                is NativeKotlinMixedHistoryCompletion.Measured ->
                    entry.stored.journal.approvedPlan.sets.map{it.exerciseId}
            }
        }.distinct().sortedBy(::kotlinExerciseLabel)
    }
    // A timed hold/distance result must not open a repetition-based
    // exercise-history or Strength Standards chart.
    val measuredOnlyExercises=remember(verified) {
        val typed=verified?.completions.orEmpty()
            .filterIsInstance<NativeKotlinMixedHistoryCompletion.Measured>()
            .flatMap{it.stored.journal.approvedPlan.sets.map{p->p.exerciseId}}
            .toSet()
        val rep=verified?.completions.orEmpty().flatMap{entry->
            when(entry) {
                is NativeKotlinMixedHistoryCompletion.Composer ->
                    entry.evidence.sets.map{it.exerciseId}
                is NativeKotlinMixedHistoryCompletion.Custom ->
                    entry.evidence.visits.map{it.exerciseId}
                is NativeKotlinMixedHistoryCompletion.Measured -> emptyList()
            }
        }.toSet()
        typed-rep
    }
    // A remembered sheet survives process restoration, but only an exact
    // current-owner repetition movement may reopen it. Never confuse
    // measured hold/distance observations with ordinary rep-based history.
    val verifiedRepMovements=remember(searchableMovements,measuredOnlyExercises) {
        searchableMovements.filterNot {it in measuredOnlyExercises}.toSet()
    }
    val admittedHistoryExerciseId=remember(historyExerciseId,
        historyExerciseAthleteId,verified,verifiedRepMovements) {
        NativeKotlinHistoryFocusPolicy.admitExercise(
            historyExerciseId,historyExerciseAthleteId,
            verified?.athleteId,verifiedRepMovements)
    }
    // Saveable query text is a browser preference, not evidence that another
    // athlete has matching workouts. A change of authenticated owner opens
    // the new verified timeline without the previous athlete's stale filter.
    val activeExerciseSearch=remember(exerciseSearch,exerciseSearchAthleteId,verified?.athleteId) {
        NativeKotlinHistoryFocusPolicy.admitSearch(
            exerciseSearch,exerciseSearchAthleteId,verified?.athleteId)
    }
    val filteredMovements=remember(searchableMovements,activeExerciseSearch) {
        val query=activeExerciseSearch.trim()
        if(query.isEmpty())emptyList()
        else searchableMovements.filter{
            it.contains(query,ignoreCase=true) ||
                kotlinExerciseLabel(it).contains(query,ignoreCase=true)
        }
    }
    val visibleSessions=remember(verified,filteredMovements,activeExerciseSearch,
        focusedWorkoutId,preferredWorkoutId) {
        val query=activeExerciseSearch.trim()
        if(focusedWorkoutId!=null)verified?.completions.orEmpty().filter{
            it.manifest.workoutId==focusedWorkoutId
        }
        else if(preferredWorkoutId!=null || query.isEmpty())
            verified?.completions.orEmpty()
        else verified?.completions.orEmpty().filter{ entry ->
            when(entry) {
                is NativeKotlinMixedHistoryCompletion.Composer ->
                    entry.evidence.sets.any{it.exerciseId in filteredMovements}
                is NativeKotlinMixedHistoryCompletion.Custom ->
                    entry.evidence.visits.any{it.exerciseId in filteredMovements}
                is NativeKotlinMixedHistoryCompletion.Measured ->
                    entry.stored.journal.approvedPlan.sets.any{
                        it.exerciseId in filteredMovements}
            }
        }
    }
    val historyZone=java.time.ZoneId.systemDefault()
    val historyLocale=java.util.Locale.getDefault()
    val historyMonths=remember(verified,historyZone){nativeHistoryMonths(verified?.completions.orEmpty(),historyZone)}
    var dateSelectionAthlete by rememberSaveable{mutableStateOf<String?>(null)}
    var historyDateKey by rememberSaveable{mutableStateOf("all")}
    var historyDayKey by rememberSaveable{mutableStateOf<String?>(null)}
    val dateSelection=remember(historyDateKey,historyDayKey,dateSelectionAthlete,verified?.athleteId,historyMonths,historyZone) {
        if(dateSelectionAthlete!=verified?.athleteId)NativeKotlinHistoryDateSelection.All
        else nativeHistoryDateSelection(historyDateKey,historyDayKey,historyMonths,historyZone)
    }
    val displayedSessions=remember(visibleSessions,dateSelection,historyZone,
        preferredWorkoutId) {
        if(preferredWorkoutId!=null)visibleSessions
        else nativeHistorySessionsInSelection(visibleSessions,dateSelection,historyZone)
    }
    val monthSections=remember(displayedSessions,historyZone){nativeHistoryMonths(displayedSessions,historyZone)}
    var visibleExerciseCount by rememberSaveable(activeExerciseSearch){mutableStateOf(12)}
    val historyListState=rememberLazyListState()
    // Opening an exact completed session must not reuse the old History
    // browser's distant scroll position and hide the finished result.
    LaunchedEffect(preferredWorkoutId,focusedWorkoutId) {
        if(preferredWorkoutId!=null)historyListState.scrollToItem(0)
    }
    LazyColumn(Modifier.fillMaxSize(),state=historyListState,
        contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item {
            PageHeading("TRAINING LOG","Workout history",
                "Your completed workouts and corrections. Dates use your device’s time zone.")
            OutlinedButton(onClick=::reload,enabled=!busy){Text(if(busy)"Loading…" else "Refresh history")}
            finishRecoveryNotice?.let { Text(it,
                style=MaterialTheme.typography.bodySmall) }
            enrollmentMessage?.let { Text(it,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodyMedium) }
            exportNotice?.let { Text(it,
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodyMedium) }
        }
        when(val read=result) {
            is NativeKotlinMixedHistoryReadResult.Review -> {
                if(preferredWorkoutId!=null)item(key="finished-history-focus"){
                    PursuitCard {
                        if(focusedWorkout!=null) {
                            PursuitSectionHeading("Just finished",
                                "Showing the exact verified workout you opened from Train.")
                            Text("You can inspect targets, actuals and corrections "+
                                "below. Your previous search and calendar "+
                                "selection are preserved for the full log.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            PursuitSectionHeading("Workout not found",
                                "The saved workout link could not be verified "+
                                    "for the current athlete. Nothing was changed.")
                        }
                        OutlinedButton(
                            onClick=onClearWorkoutFocus,
                            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                            Text("Show all workout history")
                        }
                    }
                }
                item {
                    PursuitDisclosureRow("History options","Export, program and coaching shortcuts",
                        historyOptionsExpanded,{historyOptionsExpanded=!historyOptionsExpanded})
                    if(historyOptionsExpanded)Column(Modifier.fillMaxWidth(),
                        verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                        PursuitPrimaryAction("Continue to my program",enabled=!busy) {
                            onOpenProgram()
                        }
                        OutlinedButton(onClick=onOpenProgress,
                            enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                            Text("Strength standards")
                        }
                        if(read.timeline.completions.isNotEmpty())
                            OutlinedButton(
                                onClick={onOpenCoach(null)},enabled=!busy,
                                modifier=Modifier.fillMaxWidth()) {
                                Text("Review next workout with Coach")
                            }
                        if(read.timeline.completions.isNotEmpty()) {
                            OutlinedButton(
                                onClick={
                                    exporting=true
                                    exportNotice=null
                                    scope.launch {
                                        val current=withContext(Dispatchers.IO) {
                                            runCatching {
                                                val first=host.currentHistoryExportOwner()
                                                val timeline=(host.reviewVerifiedKotlinHistory()
                                                    as? NativeKotlinMixedHistoryReadResult.Review)
                                                    ?.timeline
                                                val second=host.currentHistoryExportOwner()
                                                if(first!=null && first==second &&
                                                    timeline!=null &&
                                                    first.athleteId==timeline.athleteId &&
                                                    timeline.athleteId==read.timeline.athleteId &&
                                                    timeline.manifestRevision==
                                                        read.timeline.manifestRevision)
                                                    Triple(first.ownerKey,first.athleteId,
                                                        timeline.manifestRevision)
                                                else null
                                            }.getOrNull()
                                        }
                                        exporting=false
                                        if(current==null)
                                            exportNotice="History or athlete changed. Refresh History before exporting."
                                        else {
                                            pendingCsvExport=current
                                            csvDocument.launch("Pursuit-Iron-training-history.csv")
                                        }
                                    }
                                },
                                enabled=!busy && !exporting,
                                modifier=Modifier.fillMaxWidth()
                            ) {
                                Text(if(exporting)"Preparing history export…"
                                    else "Export completed workouts (CSV)")
                            }
                            Text(
                                "Choose where to save your completed workout and set history. Includes target and actual weights, corrections, gym IDs and machine A/B IDs. No dates are invented. This is a personal record, not a full backup or restore.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if(read.timeline.completions.isEmpty())item {Text("Your first finished workout will appear here.")}
                if(read.timeline.completions.isNotEmpty() &&
                    focusedWorkoutId==null && preferredWorkoutId==null)item {
                    PursuitCard {
                        PursuitSectionHeading("Find an exercise",
                            "Search completed sessions, or open a movement's full history.")
                        OutlinedTextField(
                            value=activeExerciseSearch,
                            onValueChange={
                                exerciseSearch=it.take(80)
                                exerciseSearchAthleteId=read.timeline.athleteId
                            },
                            singleLine=true,
                            label={Text("Search exercises")},
                            modifier=Modifier.fillMaxWidth()
                        )
                        if(activeExerciseSearch.isNotBlank()) {
                            Row(Modifier.fillMaxWidth(),
                                horizontalArrangement=Arrangement.SpaceBetween) {
                                Text("${displayedSessions.size} matching sessions in this view",
                                    style=MaterialTheme.typography.bodySmall)
                                TextButton(onClick={exerciseSearch=""}) {Text("Clear")}
                            }
                            if(filteredMovements.isEmpty())
                                Text("No matching saved exercises.",
                                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                                    style=MaterialTheme.typography.bodySmall)
                            else {
                                Row(Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                                    filteredMovements.take(visibleExerciseCount).forEach{exercise->
                                        if(exercise in measuredOnlyExercises) {
                                            FilterChip(
                                                selected=activeExerciseSearch==exercise,
                                                onClick={
                                                    exerciseSearch=exercise
                                                    exerciseSearchAthleteId=read.timeline.athleteId
                                                },
                                                label={Text("Measured · ${kotlinExerciseLabel(exercise)}")}
                                            )
                                        } else Row(horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                                            OutlinedButton(onClick={
                                                historyExerciseId=exercise
                                                historyExerciseAthleteId=read.timeline.athleteId
                                            }) {
                                                Text(kotlinExerciseLabel(exercise))
                                            }
                                            TextButton(onClick={onOpenExerciseTrend(exercise)},
                                                modifier=Modifier.heightIn(min=48.dp)) {
                                                Text("Trend: ${kotlinExerciseLabel(exercise)}")
                                            }
                                        }
                                    }
                                }
                                if(filteredMovements.size>visibleExerciseCount)
                                    TextButton(onClick={visibleExerciseCount+=12}) {
                                        Text("Show more matching exercises")
                                    }
                            }
                        }
                    }
                }
                if(read.timeline.completions.isNotEmpty() &&
                    focusedWorkoutId==null && preferredWorkoutId==null)
                    item(key="history-date-browser") {
                    key(read.timeline.athleteId) {
                        NativeKotlinHistoryDateBrowser(historyMonths,visibleSessions,dateSelection,historyZone,historyLocale) {selection->
                            dateSelectionAthlete=read.timeline.athleteId
                            historyDayKey=(selection as? NativeKotlinHistoryDateSelection.Month)?.day?.toString()
                            historyDateKey=when(selection) {
                                NativeKotlinHistoryDateSelection.All -> "all"
                                NativeKotlinHistoryDateSelection.Undated -> "undated"
                                is NativeKotlinHistoryDateSelection.Month -> selection.month.toString()
                            }
                        }
                    }
                    if(displayedSessions.isEmpty()) {
                        Text("No saved workouts match this date view and exercise search.",
                            style=MaterialTheme.typography.bodyMedium)
                        TextButton(onClick={historyDateKey="all";historyDayKey=null;exerciseSearch=""},
                            modifier=Modifier.heightIn(min=48.dp)){Text("Show all workouts")}
                    }
                }
                monthSections.forEach {section->
                item(key="history-month:${section.key}") {
                    PursuitSectionHeading(section.title(historyLocale),
                        "${section.sessions.size} ${if(section.sessions.size==1)"workout" else "workouts"}")
                    if(section.month==null)Text("Older sessions and sessions without a recorded clock value stay undated.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(section.sessions,key={"session:${it.manifest.workoutId}"}) {entry->
                    var expanded by rememberSaveable(entry.manifest.workoutId){
                        mutableStateOf(entry.manifest.workoutId==focusedWorkoutId)
                    }
                    LaunchedEffect(focusedWorkoutId,entry.manifest.workoutId) {
                        if(entry.manifest.workoutId==focusedWorkoutId)
                            expanded=true
                    }
                    PursuitCard {
                        Text(nativeHistoryDateLabel(entry.manifest,historyZone,historyLocale)
                            ?:"Session ${entry.manifest.completionOrder}",style=MaterialTheme.typography.titleMedium)
                        if(entry.manifest.completedAtEpochMillis!=null)Text("Session ${entry.manifest.completionOrder}",
                            style=MaterialTheme.typography.labelSmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(when(entry) {
                            is NativeKotlinMixedHistoryCompletion.Custom -> "Custom program"
                            is NativeKotlinMixedHistoryCompletion.Composer -> "Generated program"
                            is NativeKotlinMixedHistoryCompletion.Measured -> "Measured workout"
                        },style=MaterialTheme.typography.bodySmall)
                        // A held second or carried centimeter is not a
                        // Composer repetition and must never count as a PR.
                        if(entry is NativeKotlinMixedHistoryCompletion.Measured) {
                            val journal=entry.stored.journal
                            val results=journal.bySetKey.values.mapNotNull{
                                it.effectiveRecord?.operation as?
                                    MeasuredJournalOperation.Record}
                            val completed=results.count{
                                it.outcome==MeasuredSetOutcome.COMPLETED}
                            val skipped=results.count{
                                it.outcome==MeasuredSetOutcome.SKIPPED}
                            val failed=results.count{
                                it.outcome==MeasuredSetOutcome.FAILED_ATTEMPT}
                            Text("$completed of ${journal.approvedPlan.sets.size} measured sets completed",
                                style=MaterialTheme.typography.titleSmall)
                            if(skipped>0 || failed>0)Text(
                                "$skipped skipped · $failed failed attempts",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Measurements retain their actual seconds, distance, sides and equipment. No rep PRs are inferred.",
                                style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            // These are effective, correction-aware facts from
                            // the verified owner history, not estimated PRs.
                            val performance=when(entry) {
                                is NativeKotlinMixedHistoryCompletion.Composer ->
                                    KotlinHistoryPerformanceSummaryDomain.fromComposer(entry.evidence.sets)
                                is NativeKotlinMixedHistoryCompletion.Custom ->
                                    KotlinHistoryPerformanceSummaryDomain.fromCustom(entry.evidence.visits)
                                is NativeKotlinMixedHistoryCompletion.Measured ->
                                    error("Handled by typed branch")
                            }
                            Text("${performance.completedWorkingSets} of ${performance.workingSets} working sets completed",
                                style=MaterialTheme.typography.titleSmall)
                            if(performance.skippedWorkingSets>0 || performance.failedWorkingSets>0)
                                Text("${performance.skippedWorkingSets} skipped · ${performance.failedWorkingSets} failed attempts",
                                    color=MaterialTheme.colorScheme.onSurfaceVariant,
                                    style=MaterialTheme.typography.bodySmall)
                            NativeKotlinPerformanceEvidenceLines(performance)
                            if(performance.followUpVisits>0)
                                Text("Amplifier follow-ups: ${performance.completedFollowUpVisits} of ${performance.followUpVisits} completed (not counted as main sets)",
                                    style=MaterialTheme.typography.bodySmall)
                        }
                        // Generated exercise selection is only a navigation
                        // hint. Coach still intersects with the CURRENT
                        // approved program; custom training stays in its
                        // separately consented cycle review workflow.
                        // Full exercise ledger is separate from Coach.
                        // Any completed custom or generated movement can open
                        // complete owner-verified, correction-aware history.
                        val allMovements=when(entry) {
                            is NativeKotlinMixedHistoryCompletion.Composer ->
                                entry.evidence.sets.map{it.exerciseId}
                            is NativeKotlinMixedHistoryCompletion.Custom ->
                                entry.evidence.visits.map{it.exerciseId}
                            is NativeKotlinMixedHistoryCompletion.Measured ->
                                emptyList()
                        }.distinct()
                        if(allMovements.isNotEmpty()) {
                            PursuitSectionHeading("Exercise history",
                                "Open all logged visits without leaving History.")
                            Row(Modifier.fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                                allMovements.forEach { exercise ->
                                    OutlinedButton(enabled=!busy,
                                        onClick={
                                            historyExerciseId=exercise
                                            historyExerciseAthleteId=read.timeline.athleteId
                                        }) {
                                        Text(kotlinExerciseLabel(exercise))
                                    }
                                }
                            }
                        }
                        if(entry is NativeKotlinMixedHistoryCompletion.Composer) {
                            val movements=entry.evidence.exerciseReviews
                                .filter{it.workingSets.isNotEmpty()}
                                .map{it.exerciseId}.distinct()
                            if(movements.isNotEmpty()) {
                                Text("Review a completed movement",
                                    style=MaterialTheme.typography.labelLarge)
                                Row(Modifier.fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                    movements.forEach{exercise->
                                        OutlinedButton(
                                            enabled=!busy,
                                            onClick={onOpenCoach(exercise)}) {
                                            Text(exercise.replace('_',' ').replace('-',' '))
                                        }
                                    }
                                }
                            }
                        }
                        TextButton(onClick={expanded=!expanded}){
                            Text(if(expanded)"Hide sets"
                                else if(entry is NativeKotlinMixedHistoryCompletion.Measured)
                                    "View recorded measurements"
                                else "View targets, notes and actuals")
                        }
                        if(expanded)when(entry) {
                            is NativeKotlinMixedHistoryCompletion.Custom ->
                                NativeKotlinCustomHistoryNote(host,entry)
                            is NativeKotlinMixedHistoryCompletion.Composer ->
                                NativeKotlinGeneratedHistoryNote(host,entry)
                            is NativeKotlinMixedHistoryCompletion.Measured ->
                                NativeKotlinMeasuredHistoryRows(entry)
                        }
                        if(expanded)when(entry) {
                            is NativeKotlinMixedHistoryCompletion.Composer ->entry.evidence.sets.forEach {set->
                                NativeKotlinSavedComposerHistorySetRow(
                                    host,entry,set,onChanged={reload()})
                            }
                            is NativeKotlinMixedHistoryCompletion.Custom ->
                                NativeKotlinSavedCustomHistoryTechniqueLedger(
                                    host,entry,onChanged={reload()})
                            is NativeKotlinMixedHistoryCompletion.Measured -> Unit
                        }
                    }
                }
                }
            }
            is NativeKotlinMixedHistoryReadResult.Rejected ->item {
                Text(when(read.reason) {
                    NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED ->"Confirm your saved athlete setup to open history."
                    NativeKotlinMixedHistoryRefusal.NOT_ENROLLED ->
                        "This Kotlin athlete has no enrolled native workout history. Existing web training history is NOT migrated here."
                    NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION ->
                        "A saved workout Finish or correction was interrupted. Recover its original transaction before using History."
                    else ->"Saved history could not be verified. Your saved workouts have been kept."
                },color=MaterialTheme.colorScheme.error)
                if(read.reason==NativeKotlinMixedHistoryRefusal.PENDING_TRANSACTION) {
                    OutlinedButton(enabled=!busy,modifier=Modifier.fillMaxWidth(),
                        onClick={
                            busy=true
                            finishRecoveryNotice=null
                            scope.launch {
                                try {
                                    // The host reopens the exact pending ledger
                                    // intent and selects its single native
                                    // coordinator (generated, custom, measured
                                    // or correction). It never starts a new
                                    // Finish and returns only independently
                                    // verified mixed History.
                                    val refreshed=withContext(Dispatchers.IO) {
                                        runCatching {
                                            host.recoverPendingNativeHistory()
                                        }.getOrElse {
                                            NativeKotlinMixedHistoryReadResult.Rejected(
                                                NativeKotlinMixedHistoryRefusal.STORAGE_UNAVAILABLE)
                                        }
                                    }
                                    result=refreshed
                                    finishRecoveryNotice=when(refreshed) {
                                        is NativeKotlinMixedHistoryReadResult.Review ->
                                            "The saved transaction was checked and your complete History is verified."
                                        is NativeKotlinMixedHistoryReadResult.Rejected ->
                                            "The pending transaction could not be verified ("+
                                                refreshed.reason.name.replace('_',' ').lowercase()+
                                                "). Saved records were not discarded; retry or reopen the workout."
                                    }
                                } finally {busy=false}
                            }
                        }) {Text("Recover pending History")}
                }
                if(read.reason==NativeKotlinMixedHistoryRefusal.NOT_ENROLLED) {
                    PursuitCard {
                        Text("Start an independent Kotlin history",
                            style=MaterialTheme.typography.titleMedium)
                        Text("For a new, separately owned Kotlin profile only. This creates an empty local training history without importing or changing your JavaScript/web workouts. Prior experience and fatigue history will NOT be known to the Kotlin coach. If you need your old history included, do not start separately: wait for verified migration.",
                            style=MaterialTheme.typography.bodyMedium)
                        Button(
                            enabled=!busy,
                            modifier=Modifier.fillMaxWidth(),
                            onClick={
                                understandsSeparateHistory=false
                                consentedHistoryOwner=null
                                showSeparateHistoryConsent=false
                                busy=true
                                scope.launch {
                                    // Fresh owner and fresh NOT_ENROLLED must
                                    // agree on both sides of the disk read.
                                    // The UI cannot infer consent from an old
                                    // rejected result or a different athlete.
                                    val owner=withContext(Dispatchers.IO) {
                                        val before=runCatching {
                                            host.currentHistoryExportOwner()
                                        }.getOrNull()
                                        val current=runCatching {
                                            host.reviewVerifiedKotlinHistory()
                                        }.getOrNull()
                                        val after=runCatching {
                                            host.currentHistoryExportOwner()
                                        }.getOrNull()
                                        NativeKotlinHistoryEnrollmentConsentPolicy.capture(
                                            before,current,after)
                                    }
                                    busy=false
                                    if(owner!=null &&
                                        (result as? NativeKotlinMixedHistoryReadResult.Rejected)
                                            ?.reason==NativeKotlinMixedHistoryRefusal.NOT_ENROLLED) {
                                        consentedHistoryOwner=owner
                                        showSeparateHistoryConsent=true
                                    } else {
                                        enrollmentMessage="Athlete identity or History changed. Refresh History before choosing a new separate native history."
                                    }
                                }
                            }) {Text("Review separate-history choice")}
                    }
                }
            }
            null ->if(busy)item {LinearProgressIndicator(Modifier.fillMaxWidth())}
        }
    }
    admittedHistoryExerciseId?.let { exerciseId ->
        NativeKotlinExerciseHistorySheet(host,exerciseId,
            onDismiss={
                historyExerciseId=null
                historyExerciseAthleteId=null
            })
    }
    if(showSeparateHistoryConsent)AlertDialog(
        onDismissRequest={
            if(!busy) {
                showSeparateHistoryConsent=false
                understandsSeparateHistory=false
                consentedHistoryOwner=null
            }
        },
        title={Text("Start a separate Kotlin training history?")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Text("This is NOT a migration. It does not bring over old web or JavaScript workouts, personal records, fatigue, or progression history. It does not erase them either. The coach will start with only future Kotlin logs.")
                Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Checkbox(
                        checked=understandsSeparateHistory,
                        onCheckedChange={understandsSeparateHistory=it},
                        enabled=!busy)
                    Text("I want a new independent history, not an import.")
                }
            }
        },
        confirmButton={
            Button(
                enabled=understandsSeparateHistory&&consentedHistoryOwner!=null&&!busy,
                onClick={
                    val approvedOwner=consentedHistoryOwner
                    showSeparateHistoryConsent=false
                    understandsSeparateHistory=false
                    consentedHistoryOwner=null
                    busy=true
                    enrollmentMessage=null
                    scope.launch {
                        val enrollment=withContext(Dispatchers.IO) {
                            runCatching {
                                if(approvedOwner==null)
                                    NativeHistoryEnrollmentResult.Rejected(
                                        NativeHistoryEnrollmentRefusal.NOT_AUTHORIZED)
                                else host.enrollSeparateKotlinHistory(true,approvedOwner)
                            }.getOrNull()
                        }
                        busy=false
                        when(enrollment) {
                            is NativeHistoryEnrollmentResult.Enrolled -> {
                                enrollmentMessage=
                                    "Separate native history verified. Web app workouts remain untouched and are not included."
                                reload()
                            }
                            is NativeHistoryEnrollmentResult.Rejected ->
                                enrollmentMessage="Separate history was not created: ${enrollment.reason.name.replace('_',' ').lowercase()}. Existing records were kept."
                            null ->enrollmentMessage=
                                "Could not verify a new native history. No enrollment was claimed."
                        }
                    }
                }) {Text("Start separate history")}
        },
        dismissButton={
            TextButton(
                enabled=!busy,
                onClick={
                    showSeparateHistoryConsent=false
                    understandsSeparateHistory=false
                    consentedHistoryOwner=null
                }
            ) {Text("Keep existing history unchanged")}
        }
    )
}

private fun kotlinHistoryLoadLabel(load:DraftSetLoad):String=kotlinIssuedLoadLabel(load)

@Composable
private fun KotlinHistoryActualRow(
    exercise:String,label:String,issuedLoad:DraftSetLoad,issuedReps:RepBand,
    issuedRir:Int?,outcome:KotlinJournalSetOutcome,actual:KotlinPerformedSet?
) {
    Text("${kotlinExerciseLabel(exercise)} · $label",
        style=MaterialTheme.typography.titleSmall)
    val goalReps=if(issuedReps.minimum==issuedReps.maximum)
        "${issuedReps.minimum} reps" else "${issuedReps.minimum}–${issuedReps.maximum} reps"
    Text("Target: ${kotlinHistoryLoadLabel(issuedLoad)} × $goalReps · "+
        (issuedRir?.let{"$it RIR target"} ?: "No RIR target"),
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    val performed=actual?.let{
        val weight=kotlinActualLoadLabel(it,issuedLoad)
        "$weight × ${it.reps} reps · "+
            (it.actualRir?.let{rir->"$rir actual RIR"} ?: "Actual RIR not logged")
    } ?: "No performed values available"
    val values=when(outcome) {
        KotlinJournalSetOutcome.SKIPPED -> "Skipped"
        KotlinJournalSetOutcome.FAILED_ATTEMPT -> "Failed attempt · $performed"
        KotlinJournalSetOutcome.COMPLETED -> "Actual: $performed"
    }
    Text(values,style=MaterialTheme.typography.bodyMedium)
}

/**
 * Share parsing between generated and custom correction forms. Unknown actual
 * RIR is never filled from the goal, and skipped sets never acquire fake reps.
 * The native reducers remain the final authority for valid load/outcome pairs.
 */
internal fun kotlinHistoryCorrectionActual(
    outcome:KotlinJournalSetOutcome,
    enteredLoad:String,enteredReps:String,enteredRir:String,
    previousActual:KotlinPerformedSet?, issuedLoad:DraftSetLoad
):KotlinPerformedSet? {
    if(outcome==KotlinJournalSetOutcome.SKIPPED)return null
    val unit=previousActual?.unit ?: (issuedLoad as? DraftSetLoad.Verified)?.unit
    val load=if(enteredLoad.isBlank())null else
        runCatching{java.math.BigDecimal(enteredLoad.trim())}.getOrNull()
    if (enteredLoad.isNotBlank() && load == null) return null
    val completed = outcome == KotlinJournalSetOutcome.COMPLETED
    val reps = if (completed) enteredReps.trim().toIntOrNull() ?: return null else 0
    val rir = if (completed && enteredRir.isNotBlank())
        enteredRir.trim().toIntOrNull() ?: return null else null
    val actual = KotlinPerformedSet(load, if (load == null) null else unit, reps, rir,
        machineInstanceId = previousActual?.machineInstanceId)
    return actual.takeIf { KotlinSetNumberAuthorityDomain.valid(issuedLoad, outcome, it) }
}

@Composable
private fun KotlinHistoryCorrectionFields(
    outcome: KotlinJournalSetOutcome, issued: DraftSetLoad,
    load: String, onLoad: (String) -> Unit, reps: String, onReps: (String) -> Unit,
    rir: String, onRir: (String) -> Unit, enabled: Boolean
) {
    if (outcome == KotlinJournalSetOutcome.SKIPPED) return
    if (issued is DraftSetLoad.Verified) {
        OutlinedTextField(value = load, onValueChange = onLoad, enabled = enabled,
            label = { Text("Actual weight (${issued.unit.name.lowercase()})") },
            supportingText = { Text(kotlinLoadBasisLabel(issued.basis)) },
            singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    if (outcome == KotlinJournalSetOutcome.FAILED_ATTEMPT) {
        Text("Failed attempt · 0 completed reps", style = MaterialTheme.typography.bodySmall)
        return
    }
    OutlinedTextField(value = reps, onValueChange = onReps, enabled = enabled,
        label = { Text("Actual reps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = rir, onValueChange = onRir, enabled = enabled,
        label = { Text("Actual RIR (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
}

/** Generated/composer history uses the same committed native history
 * transaction as Finish. Neither this UI nor a direct correction-file append
 * may change already-issued targets or publish uncommitted observations. */
@Composable
private fun NativeKotlinSavedComposerHistorySetRow(
    host:NativeKotlinCandidateHost,
    entry:NativeKotlinMixedHistoryCompletion.Composer,
    fact:KotlinHistoricalSetFact,
    onChanged:()->Unit
) {
    val label=fact.type.name.lowercase().replaceFirstChar{it.uppercase()}+
        " ${fact.originalOrdinal+1}"
    KotlinHistoryActualRow(fact.exerciseId,label,
        fact.issuedLoad,fact.issuedReps,fact.issuedRir,
        fact.outcome,fact.actual)
    var editing by remember(entry.manifest.workoutId,fact.setKey,
        fact.effectiveEventId){mutableStateOf(false)}
    var submitting by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    var outcome by remember(entry.manifest.workoutId,fact.effectiveEventId){
        mutableStateOf(fact.outcome)
    }
    var load by remember(entry.manifest.workoutId,fact.effectiveEventId){
        mutableStateOf(fact.actual?.load?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var reps by remember(entry.manifest.workoutId,fact.effectiveEventId){
        mutableStateOf(fact.actual?.reps?.toString().orEmpty())
    }
    var rir by remember(entry.manifest.workoutId,fact.effectiveEventId){
        mutableStateOf(fact.actual?.actualRir?.toString().orEmpty())
    }
    val scope=rememberCoroutineScope()
    TextButton(onClick={editing=!editing;error=null},enabled=!submitting){
        Text(if(editing)"Cancel correction" else "Correct saved set")
    }
    if(editing) {
        Text("The prescribed goal stays unchanged. Only the logged result will be corrected.",
            style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            KotlinJournalSetOutcome.entries.forEach {choice->
                FilterChip(selected=outcome==choice,enabled=!submitting,
                    onClick={outcome=choice},
                    label={Text(choice.name.replace('_',' ').lowercase()
                        .replaceFirstChar{it.uppercase()})})
            }
        }
        KotlinHistoryCorrectionFields(outcome, fact.issuedLoad, load, { load = it },
            reps, { reps = it }, rir, { rir = it }, enabled = !submitting)
        Button(enabled=!submitting,onClick={
            val performed=kotlinHistoryCorrectionActual(
                outcome,load,reps,rir,
                fact.actual, fact.issuedLoad)
            val selectedOutcome=outcome
            if(selectedOutcome!=KotlinJournalSetOutcome.SKIPPED &&
                performed==null) {
                error="Enter a valid weight, rep count and optional actual RIR."
            } else {
                submitting=true;error=null
                scope.launch {
                    val result=withContext(Dispatchers.IO){
                        runCatching{
                            // Re-read the full owner-fenced corrected ledger
                            // before sending a CAS event. A stale edit is
                            // rejected rather than overwriting later work.
                            val verified=host.reviewVerifiedKotlinHistory()
                                as? NativeKotlinMixedHistoryReadResult.Review
                            val current=verified?.timeline?.completions
                                ?.filterIsInstance<NativeKotlinMixedHistoryCompletion.Composer>()
                                ?.singleOrNull{
                                    it.manifest.workoutId==entry.manifest.workoutId
                                }
                            val currentSet=current?.evidence?.sets?.singleOrNull{
                                it.setKey==fact.setKey
                            }
                            if(currentSet==null ||
                                currentSet.effectiveEventId!=fact.effectiveEventId ||
                                currentSet.issuedLoad!=fact.issuedLoad ||
                                currentSet.issuedReps!=fact.issuedReps ||
                                currentSet.issuedRir!=fact.issuedRir ||
                                current?.manifest?.savedCorrectionRevision!=
                                    entry.manifest.savedCorrectionRevision) null
                            else host.correctSavedKotlinWorkout(
                                entry.manifest.workoutId,
                                KotlinSavedSetCorrection(
                                    "composer-correction-"+java.util.UUID.randomUUID()
                                        .toString().replace("-",""),
                                    entry.manifest.savedCorrectionRevision,
                                    fact.setKey,fact.effectiveEventId,
                                    selectedOutcome,performed))
                        }.getOrNull()
                    }
                    submitting=false
                    if(result is NativeSavedCorrectionFileResult.Durable){
                        editing=false;error=null;onChanged()
                    } else error = when ((result as? NativeSavedCorrectionFileResult.Rejected)?.correctionReason) {
                        KotlinSavedCorrectionRefusal.INVALID_VALUES -> "Check the weight, reps and RIR for this result."
                        KotlinSavedCorrectionRefusal.TOO_MANY_CORRECTIONS -> "This workout has reached its saved edit limit."
                        else -> "Refresh History and reopen this set before trying again."
                    }
                }
            }
        },modifier=Modifier.fillMaxWidth()){
            Text(if(submitting)"Saving actual correction…" else "Save corrected result")
        }
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall)}
    }
}

/**
 * Full-ROM main results remain first and always accessible. Technique
 * follow-ups belong to that exact working set, not a disconnected exercise
 * footer. The compact factual caption is visible without expanding; each
 * original stage keeps its independent correction action on disclosure.
 *
 * If grouping ever detects malformed/foreign stage relationships, show ALL
 * original visits without suppressing a saved observation.
 */
@Composable
private fun NativeKotlinSavedCustomHistoryTechniqueLedger(
    host:NativeKotlinCandidateHost,
    entry:NativeKotlinMixedHistoryCompletion.Custom,
    onChanged:()->Unit
) {
    val groups=CustomHistoricalTechniqueGroupingDomain.group(entry.evidence.visits)
    if(groups==null) {
        Text("Technique grouping could not be verified. Original saved set records are shown without hiding any entry.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.error)
        entry.evidence.visits.forEach {fact->
            NativeKotlinSavedCustomHistoryVisitRow(host,entry,fact,onChanged)
        }
        return
    }
    groups.forEach {group->
        key(entry.manifest.workoutId,group.main.setKey) {
            Column(Modifier.fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                NativeKotlinSavedCustomHistoryVisitRow(
                    host,entry,group.main,onChanged)
                val caption=group.techniqueCaption
                if(caption!=null) {
                    Surface(color=MaterialTheme.colorScheme.surfaceContainerLow,
                        shape=MaterialTheme.shapes.small) {
                        Column(Modifier.fillMaxWidth().padding(PursuitSpace.xs),
                            verticalArrangement=Arrangement.spacedBy(2.dp)) {
                            Text(caption,style=MaterialTheme.typography.bodySmall,
                                color=MaterialTheme.colorScheme.primary)
                            if(group.followUps.isNotEmpty()) {
                                var showStages by rememberSaveable(
                                    entry.manifest.workoutId,group.main.setKey
                                ) {mutableStateOf(false)}
                                TextButton(onClick={showStages=!showStages},
                                    modifier=Modifier.heightIn(min=48.dp)) {
                                    Text(if(showStages)
                                        "Hide technique stage details" else
                                        "View technique stage details")
                                }
                                if(showStages)group.followUps.forEach{stage->
                                    NativeKotlinSavedCustomHistoryVisitRow(
                                        host,entry,stage,onChanged)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Post-Finish corrections never mutate the issued prescription or set
 * journal. They produce a separate owner-fenced correction transaction and
 * cause the whole verified history screen to refresh after commitment.
 */
@Composable
private fun NativeKotlinSavedCustomHistoryVisitRow(
    host:NativeKotlinCandidateHost,
    entry:NativeKotlinMixedHistoryCompletion.Custom,
    fact:CustomHistoricalVisitFact,
    onChanged:()->Unit
) {
    KotlinHistoryActualRow(fact.exerciseId,
        if(fact.part==CustomWorkoutVisitPart.FOLLOW_UP)
            when(fact.amplifierKind) {
                CustomAmplifierKind.MYO_REPS -> "Myo-reps · mini ${fact.stage}"
                CustomAmplifierKind.REST_PAUSE -> "Rest-pause · effort ${fact.stage}"
                CustomAmplifierKind.CLUSTER -> "Cluster · effort ${fact.stage}"
                CustomAmplifierKind.DROP_SET -> "Drop set · weight drop ${fact.stage}"
                CustomAmplifierKind.LENGTHENED_PARTIALS ->
                    "Lengthened partials · partial reps"
                null -> "Amplifier follow-up"
            }
        else fact.setType.name.lowercase().replaceFirstChar{it.uppercase()}+
            (if(fact.amplifierKind==CustomAmplifierKind.LENGTHENED_PARTIALS)
                " · final-set partial extension" else ""),
        fact.prescribedLoad,fact.prescribedReps,fact.prescribedRir,
        fact.outcome,fact.actual)
    var editing by remember(entry.manifest.workoutId,fact.setKey,
        fact.part,fact.stage,fact.effectiveEventId) {mutableStateOf(false)}
    var submitting by remember {mutableStateOf(false)}
    var error by remember {mutableStateOf<String?>(null)}
    var outcome by remember(entry.manifest.workoutId,fact.effectiveEventId) {
        mutableStateOf(fact.outcome)
    }
    var load by remember(entry.manifest.workoutId,fact.effectiveEventId) {
        mutableStateOf(fact.actual?.load?.stripTrailingZeros()?.toPlainString().orEmpty())
    }
    var reps by remember(entry.manifest.workoutId,fact.effectiveEventId) {
        mutableStateOf(fact.actual?.reps?.toString().orEmpty())
    }
    var rir by remember(entry.manifest.workoutId,fact.effectiveEventId) {
        mutableStateOf(fact.actual?.actualRir?.toString().orEmpty())
    }
    val scope=rememberCoroutineScope()
    TextButton(onClick={editing=!editing;error=null},enabled=!submitting) {
        Text(if(editing)"Cancel correction" else "Correct saved set")
    }
    if(editing) {
        Text("Original prescribed targets remain unchanged. Only your recorded result will be corrected.",
            style=MaterialTheme.typography.bodySmall)
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            KotlinJournalSetOutcome.entries.forEach {choice->
                FilterChip(selected=outcome==choice,
                    enabled=!submitting,
                    onClick={outcome=choice},
                    label={Text(choice.name.replace('_',' ').lowercase()
                        .replaceFirstChar{it.uppercase()})})
            }
        }
        KotlinHistoryCorrectionFields(outcome, fact.prescribedLoad, load, { load = it },
            reps, { reps = it }, rir, { rir = it }, enabled = !submitting)
        Button(enabled=!submitting,onClick={
            val actual=kotlinHistoryCorrectionActual(
                outcome,load,reps,rir,
                fact.actual, fact.prescribedLoad)
            val selectedOutcome=outcome
            if(selectedOutcome!=KotlinJournalSetOutcome.SKIPPED &&
                actual==null) {
                error="Enter a valid actual load, rep count and optional RIR."
            } else {
                submitting=true;error=null
                scope.launch {
                    val result=withContext(Dispatchers.IO) {
                        runCatching {
                        val verified=host.reviewVerifiedKotlinHistory()
                            as? NativeKotlinMixedHistoryReadResult.Review
                        val current=verified?.timeline?.completions
                            ?.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                            ?.singleOrNull{it.manifest.workoutId==entry.manifest.workoutId}
                        val currentFact=current?.evidence?.visits?.singleOrNull {
                            it.setKey==fact.setKey && it.part==fact.part &&
                                it.stage==fact.stage
                        }
                        val correction=host.readFinishedCustomCorrections(
                            entry.manifest.workoutId)
                            as? NativeCustomSavedCorrectionResult.Opened
                        if(currentFact==null || correction==null ||
                            currentFact.effectiveEventId!=fact.effectiveEventId ||
                            currentFact.prescribedLoad!=fact.prescribedLoad ||
                            currentFact.prescribedReps!=fact.prescribedReps ||
                            currentFact.prescribedRir!=fact.prescribedRir ||
                            current?.manifest?.savedCorrectionRevision!=
                                entry.manifest.savedCorrectionRevision ||
                            correction.state.correctionRevision!=
                                entry.manifest.savedCorrectionRevision)
                            null
                        else host.correctFinishedCustomWorkout(
                            entry.manifest.workoutId,
                            CustomSavedVisitCorrection(
                                "custom-correction-"+java.util.UUID.randomUUID()
                                    .toString().replace("-",""),
                                entry.manifest.savedCorrectionRevision,
                                CustomWorkoutVisitKey(fact.setKey,fact.part,fact.stage),
                                fact.effectiveEventId,selectedOutcome,actual))
                        }.getOrNull()
                    }
                    submitting=false
                    if(result is NativeCustomCorrectionTransactionResult.Committed) {
                        editing=false;error=null;onChanged()
                    } else {
                        error = when ((result as? NativeCustomCorrectionTransactionResult.Rejected)?.correctionReason) {
                            CustomSavedCorrectionRefusal.INCONSISTENT_AMPLIFIER ->
                                "This edit conflicts with the recorded amplifier sequence. Check the activation and each follow-up set."
                            CustomSavedCorrectionRefusal.INVALID_VALUES -> "Check the weight, reps and RIR for this result."
                            CustomSavedCorrectionRefusal.TOO_MANY_CORRECTIONS -> "This workout has reached its saved edit limit."
                            else -> "Refresh History and reopen this set before trying again."
                        }
                    }
                }
            }
        },modifier=Modifier.fillMaxWidth()) {
            Text(if(submitting)"Saving actual correction…" else "Save corrected result")
        }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error,
            style=MaterialTheme.typography.bodySmall) }
    }
}

/** Exact identity projection shared by workout and exercise History. */
internal fun nativeMeasuredHistoryPlans(
    plans:List<MeasuredExerciseSetPlan>,exerciseId:String?=null
):List<MeasuredExerciseSetPlan> = if(exerciseId==null)plans
    else plans.filter{it.exerciseId==exerciseId}

/** Source-exact Material 3 presentation for typed measured results. */
@Composable
internal fun NativeKotlinMeasuredHistoryRows(
    entry:NativeKotlinMixedHistoryCompletion.Measured,
    exerciseId:String?=null
) {
    val journal=entry.stored.journal
    PursuitSectionHeading("Recorded measurements",
        "Each side and its original physical setup are preserved. These are not loaded-rep personal records.")
    nativeMeasuredHistoryPlans(journal.approvedPlan.sets,exerciseId).forEach{plan->
        val definition=MeasuredExerciseCatalogDomain.find(plan.exerciseId)
        val review=MeasuredWorkoutResultReviewDomain.review(
            plan,journal.bySetKey[plan.setKey])
        val target=when(definition?.kind) {
            MeasuredExerciseKind.HOLD_SECONDS -> plan.targetAmount.toString()+" s"
            MeasuredExerciseKind.CARRY_CENTIMETERS ->
                BigDecimal(plan.targetAmount).movePointLeft(2)
                    .stripTrailingZeros().toPlainString()+" m"
            MeasuredExerciseKind.BANDED_REPETITIONS ->
                plan.targetAmount.toString()+" reps"
            null -> "measurement unavailable"
        }
        Column(Modifier.fillMaxWidth().padding(vertical=PursuitSpace.xs),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
            Text((definition?.displayName?:kotlinExerciseLabel(plan.exerciseId))+
                " · Target "+target,
                style=MaterialTheme.typography.labelLarge)
            Text(review.statusLabel,
                style=MaterialTheme.typography.labelMedium,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            review.actualSummary?.let { summary ->
                Text((if(review.status==MeasuredSetReviewStatus.FAILED_ATTEMPT)
                    "Attempt · " else "Actual · ")+summary,
                    style=MaterialTheme.typography.bodyMedium)
            }
            // A band, external mass or distance is displayed as performed
            // only when it belongs to the effective verified observation.
            // Original targets and physical stations remain separate facts.
            Text("Physical setup · ${plan.physicalSetupId}",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()
    }
}
