package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.UUID

private val kotlinWeekdayNames=listOf("Mon","Tue","Wed","Thu","Fri","Sat","Sun")
private val kotlinId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")

/**
 * The native setup screen never chooses an athlete's working weights or
 * training rules. Source IDs are generated opaque identifiers (not training
 * choices) and can be replaced with the SAME prior athlete ID on re-enrollment.
 */
private fun selectedDays(mask:Int):Set<Int> =
    (0..6).filter { mask and (1 shl it) != 0 }.toSet()

private fun commaIds(raw:String):Set<String>? {
    val entries=raw.split(",").map{it.trim()}.filter{it.isNotBlank()}
    return entries.takeIf {
        it.size<=128 && it.distinct().size==it.size &&
        it.all{kotlinId.matches(it)}
    }?.toSet()
}
private fun exactPhysicalLoads(raw:String):List<BigDecimal> {
    val values=raw.split(",").map{BigDecimal(it.trim())}
    require(values.isNotEmpty() && values.size<=250 &&
        values.all{it>=BigDecimal.ZERO} &&
        values.map{it.stripTrailingZeros()}.distinct().size==values.size)
    return values
}
private fun platePairs(raw:String):Map<BigDecimal,Int> {
    val pairs=raw.split(",").map {
        val parts=it.trim().split(Regex("[xX]"))
        require(parts.size==2)
        BigDecimal(parts[0].trim()) to parts[1].trim().toInt()
    }
    require(pairs.isNotEmpty() && pairs.size<=12 &&
        pairs.all{it.first>BigDecimal.ZERO && it.second in 1..24} &&
        pairs.map{it.first.stripTrailingZeros()}.distinct().size==pairs.size)
    return pairs.toMap()
}
private fun onboardingStock(
    unit:LoadUnit?,
    db:String,cable:String,barWeight:String,plates:String,
    machineId:String,machineLoads:String
):Map<String,VerifiedLoadStock>? {
    if(unit==null) return null
    return runCatching {
        val stock=linkedMapOf<String,VerifiedLoadStock>()
        if(db.isNotBlank())stock["dumbbell-rack"]=VerifiedLoadStock.Totals(
            unit,LoadBasis.DUMBBELL_PER_HAND,exactPhysicalLoads(db))
        if(cable.isNotBlank()) {
            val parts=cable.split(",").map{BigDecimal(it.trim())}
            require(parts.size==3)
            stock["cable-1"]=VerifiedLoadStock.Stack(
                unit,LoadBasis.CABLE_STACK,parts[0],parts[1],parts[2])
        }
        if(barWeight.isNotBlank() || plates.isNotBlank()) {
            require(barWeight.isNotBlank() && plates.isNotBlank())
            stock["straight-bar"]=VerifiedLoadStock.Barbell(
                unit,BigDecimal(barWeight.trim()),platePairs(plates))
        }
        if(machineId.isNotBlank() || machineLoads.isNotBlank()) {
            require(kotlinId.matches(machineId) && machineLoads.isNotBlank())
            stock[machineId]=VerifiedLoadStock.Totals(
                unit,LoadBasis.EXERCISE_OVERRIDE,
                exactPhysicalLoads(machineLoads))
        }
        stock.toMap()
    }.getOrNull()
}

@Composable
private fun <T> NativeSetupChoice(
    title:String,options:List<T>,selected:T?,
    caption:(T)->String,onChange:(T)->Unit
) {
    Text(title,style=MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        options.forEach { item ->
            FilterChip(selected=item==selected,onClick={onChange(item)},
                label={Text(caption(item))})
        }
    }
}

@Composable
private fun NativeSetupWeekdays(
    title:String,mask:Int,onUpdate:(Int)->Unit
) {
    Text(title,style=MaterialTheme.typography.labelLarge)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        kotlinWeekdayNames.forEachIndexed { index,name ->
            FilterChip(selected=mask and (1 shl index)!=0,
                onClick={onUpdate(mask xor (1 shl index))},
                label={Text(name)})
        }
    }
}

private fun displayEnum(value:Enum<*>):String =
    value.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}

/**
 * Genuine candidate Kotlin onboarding. Every field needed for
 * CoachAthleteSetupDomain.review is supplied by the athlete, not by JS.
 * Unsaved form values are saveable across Android configuration changes,
 * but no persisted athlete, gym or program is created until confirmation.
 *
 * Not connected to production MainActivity or the legacy web program owner.
 */
@Composable
internal fun NativeKotlinOnboardingCandidateScreen(
    controller:NativeKotlinGymSourceController,
    onContinue:()->Unit={}
) {
    val sourceState by controller.state.collectAsStateWithLifecycle()
    LaunchedEffect(controller) { controller.open() }
    var step by rememberSaveable {mutableIntStateOf(0)}
    var athleteId by rememberSaveable {
        mutableStateOf("athlete-"+UUID.randomUUID().toString().replace("-",""))
    }
    var profileRevision by rememberSaveable {
        mutableStateOf("profile-"+UUID.randomUUID().toString().replace("-",""))
    }
    var gymId by rememberSaveable {
        mutableStateOf("gym-"+UUID.randomUUID().toString().replace("-",""))
    }
    var goal by rememberSaveable {mutableStateOf("")}
    var experience by rememberSaveable {mutableStateOf("")}
    var split by rememberSaveable {mutableStateOf("")}
    var bucket by rememberSaveable {mutableStateOf("")}
    var timezone by rememberSaveable {mutableStateOf("")}
    var hardLimitChoice by rememberSaveable {mutableStateOf("")}
    var hardLimitMinutes by rememberSaveable {mutableStateOf("")}
    var weekdays by rememberSaveable {mutableIntStateOf(0)}
    var lockDays by rememberSaveable {mutableIntStateOf(0)}
    var hardDays by rememberSaveable {mutableIntStateOf(0)}
    var longDays by rememberSaveable {mutableIntStateOf(0)}
    var calendarConfirmed by rememberSaveable {mutableStateOf(false)}
    var restConfirmed by rememberSaveable {mutableStateOf(false)}
    var musclesConfirmed by rememberSaveable {mutableStateOf(false)}
    var excludedConfirmed by rememberSaveable {mutableStateOf(false)}
    var unit by rememberSaveable {mutableStateOf("")}
    var bodyweight by rememberSaveable {mutableStateOf("")}
    var supersets by rememberSaveable {mutableStateOf("")}
    var tricepsLower by rememberSaveable {mutableStateOf("")}
    var barbellLimit by rememberSaveable {mutableStateOf("")}
    var focusMask by rememberSaveable {mutableStateOf("")}
    var reducedMask by rememberSaveable {mutableStateOf("")}
    var excludedIds by rememberSaveable {mutableStateOf("")}
    var fixtures by rememberSaveable {mutableStateOf("")}
    var attachments by rememberSaveable {mutableStateOf("")}
    var dumbbells by rememberSaveable {mutableStateOf("")}
    var cable by rememberSaveable {mutableStateOf("")}
    var barWeight by rememberSaveable {mutableStateOf("")}
    var plates by rememberSaveable {mutableStateOf("")}
    var machineId by rememberSaveable {mutableStateOf("")}
    var machineLoads by rememberSaveable {mutableStateOf("")}
    var gymConfirmed by rememberSaveable {mutableStateOf(false)}
    var showIdentity by rememberSaveable {mutableStateOf(false)}
    var awaitingConfirmation by rememberSaveable {mutableStateOf(false)}
    var awaitingSavedResume by rememberSaveable {mutableStateOf(false)}

    val muscles=TrainingMuscle.entries
    fun maskMuscles(raw:String):Set<TrainingMuscle>? {
        if(raw.isBlank())return emptySet()
        val names=raw.split(",").filter{it.isNotBlank()}
        return if(names.distinct().size!=names.size) null
        else names.mapNotNull { n ->
            muscles.firstOrNull{it.name==n}
        }.takeIf{it.size==names.size}?.toSet()
    }
    fun flipMuscle(value:String,muscle:TrainingMuscle):String {
        val next=maskMuscles(value).orEmpty().toMutableSet()
        if(!next.add(muscle)) next.remove(muscle)
        return next.sortedBy{it.ordinal}.joinToString(","){it.name}
    }
    val chosenUnit=LoadUnit.entries.firstOrNull{it.name==unit}
    val gymInventory=onboardingStock(chosenUnit,dumbbells,cable,
        barWeight,plates,machineId,machineLoads)
    val confirmedFacilities=commaIds(fixtures)
    val confirmedAttachments=commaIds(attachments)
    val confirmedExclusions=commaIds(excludedIds)
    val input=NativeKotlinOnboardingInput(
        profileRevision=profileRevision.trim().takeIf{it.isNotBlank()},
        athleteId=athleteId.trim().takeIf{it.isNotBlank()},
        goal=AthleteTrainingGoal.entries.firstOrNull{it.name==goal},
        experience=AthleteExperience.entries.firstOrNull{it.name==experience},
        split=ComposerSplit.entries.firstOrNull{it.name==split},
        trainingWeekdays=selectedDays(weekdays).takeIf{calendarConfirmed},
        timezoneId=timezone.trim().takeIf{it.isNotBlank()},
        sessionTimeBucket=SessionTimeBucket.entries.firstOrNull{it.name==bucket},
        hardSessionLimit=when(hardLimitChoice) {
            "NONE"->HardSessionLimit.NO_HARD_LIMIT
            "MINUTES"->hardLimitMinutes.toIntOrNull()?.let{HardSessionLimit.Minutes(it)}
            else->null
        },
        gymId=gymId.trim().takeIf{it.isNotBlank()},
        unit=chosenUnit,
        confirmedStock=gymInventory.takeIf{gymConfirmed},
        confirmedFacilities=confirmedFacilities.takeIf{gymConfirmed},
        confirmedAttachments=confirmedAttachments.takeIf{gymConfirmed},
        allowBodyweight=when(bodyweight) {"YES"->true;"NO"->false;else->null},
        allowSupersets=when(supersets) {"YES"->true;"NO"->false;else->null},
        allowTricepsOnLower=when(tricepsLower) {"YES"->true;"NO"->false;else->null},
        maxBarbellExercisesPerDay=barbellLimit.toIntOrNull(),
        focusedMuscles=maskMuscles(focusMask).takeIf{musclesConfirmed},
        reducedMuscles=maskMuscles(reducedMask).takeIf{musclesConfirmed},
        excludedExercises=confirmedExclusions.takeIf{excludedConfirmed},
        lockedDays=selectedDays(lockDays).takeIf{calendarConfirmed},
        hardDays=selectedDays(hardDays).takeIf{calendarConfirmed},
        longDays=selectedDays(longDays).takeIf{calendarConfirmed},
        declaredRestDays=NativeKotlinOnboardingInputDomain.declareRestDays(
            selectedDays(weekdays).takeIf{calendarConfirmed},restConfirmed)
    )
    val review=NativeKotlinOnboardingInputDomain.review(input)
    val existing=sourceState.source

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PageHeading("KOTLIN COACH · CANDIDATE",
            when(step) {0->"Your training";1->"Your actual gym";else->"Review and confirm"},
            "Native onboarding · your choices, no auto-filled workouts")
        if(sourceState.busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        sourceState.message?.let {
            Text(it,style=MaterialTheme.typography.bodyMedium,
                color=if(sourceState.refusal!=null)
                    MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(existing==null && sourceState.savedAthleteId!=null) {
            PursuitCard(highlighted=true) {
                Text("Resume your saved training setup",
                    style=MaterialTheme.typography.titleLarge)
                Text("This device has a previous Kotlin athlete. You can reopen the original verified profile and gym without re-entering every setting. Nothing is overwritten, and a corrupt or revoked setup is refused.",
                    style=MaterialTheme.typography.bodySmall)
                Button(
                    onClick={awaitingSavedResume=true},
                    enabled=!sourceState.busy,
                    modifier=Modifier.fillMaxWidth()
                ) {Text("Resume my saved setup")}
            }
        }
        if(existing!=null) {
            PursuitCard(highlighted=true) {
                Text("Native source active",style=MaterialTheme.typography.titleLarge)
                Text("Current reviewed athlete: ${existing.athleteId}. Source revision ${existing.revision}.")
                Text("Continue to the gym or custom-program editor. No new profile or equipment is created here.")
                Button(onClick=onContinue,enabled=!sourceState.busy) {Text("Continue to my program")}
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("1 · Training","2 · Equipment","3 · Review").forEachIndexed { index,name ->
                FilterChip(selected=step==index,onClick={step=index},
                    label={Text(name)})
            }
        }
        if(step==0) {
            PursuitCard {
                Text("Training identity",style=MaterialTheme.typography.titleLarge)
                Text("The private ID distinguishes saved native athletes. Reconfirm the saved local athlete after an app restart; no workouts or gym settings become readable until the complete setup is reviewed.",
                    style=MaterialTheme.typography.bodySmall)
                sourceState.savedAthleteId?.takeIf { it != athleteId }?.let { prior ->
                    OutlinedButton(
                        onClick={ athleteId=prior;showIdentity=true },
                        enabled=!sourceState.busy,
                        modifier=Modifier.fillMaxWidth()
                    ) { Text("Use saved local athlete ID") }
                    Text("This selects an identifier only. You must still review and explicitly confirm your native settings before it opens protected data.",
                        style=MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick={showIdentity=!showIdentity}) {
                    Text(if(showIdentity)"Hide IDs" else "Show / restore athlete ID")
                }
                if(showIdentity) {
                    OutlinedTextField(athleteId,{athleteId=it.take(63)},
                        label={Text("Local athlete ID")},
                        modifier=Modifier.fillMaxWidth())
                    OutlinedTextField(profileRevision,{profileRevision=it.take(63)},
                        label={Text("Setup revision ID")},
                        modifier=Modifier.fillMaxWidth())
                }
                NativeSetupChoice("Primary goal",AthleteTrainingGoal.entries,
                    AthleteTrainingGoal.entries.firstOrNull{it.name==goal},
                    ::displayEnum){goal=it.name}
                NativeSetupChoice("Experience",AthleteExperience.entries,
                    AthleteExperience.entries.firstOrNull{it.name==experience},
                    ::displayEnum){experience=it.name}
                NativeSetupChoice("Preferred training split",ComposerSplit.entries,
                    ComposerSplit.entries.firstOrNull{it.name==split},
                    ::displayEnum){split=it.name}
                NativeSetupChoice("Session time preference",SessionTimeBucket.entries,
                    SessionTimeBucket.entries.firstOrNull{it.name==bucket},
                    ::displayEnum){bucket=it.name}
                NativeSetupChoice("Hard time limit",listOf("NONE","MINUTES"),
                    hardLimitChoice.takeIf{it.isNotBlank()},
                    {if(it=="NONE")"No hard cap" else "Specific maximum"}){
                    hardLimitChoice=it
                }
                if(hardLimitChoice=="MINUTES")OutlinedTextField(
                    hardLimitMinutes,{hardLimitMinutes=it.take(3)},
                    label={Text("Maximum minutes")})
                OutlinedTextField(timezone,{timezone=it.take(80)},
                    label={Text("IANA time zone (e.g. America/New_York)")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true)
                NativeSetupWeekdays("Your training days (3–6)",weekdays) {
                    weekdays=it
                    calendarConfirmed=false;restConfirmed=false
                }
                NativeSetupWeekdays("Locked training days",lockDays) {
                    lockDays=it;calendarConfirmed=false
                }
                NativeSetupWeekdays("Hard-session days",hardDays) {
                    hardDays=it;calendarConfirmed=false
                }
                NativeSetupWeekdays("Long-session days",longDays) {
                    longDays=it;calendarConfirmed=false
                }
                Checkbox(checked=calendarConfirmed,onCheckedChange={calendarConfirmed=it})
                Text("I reviewed these training days and preferences",
                    style=MaterialTheme.typography.bodySmall)
                Checkbox(checked=restConfirmed,onCheckedChange={restConfirmed=it})
                Text("I confirm every unselected day is a rest day",
                    style=MaterialTheme.typography.bodySmall)
                Text("Muscle priorities",style=MaterialTheme.typography.titleMedium)
                muscles.forEach { muscle ->
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Text(displayEnum(muscle),modifier=Modifier.width(125.dp),
                            style=MaterialTheme.typography.bodySmall)
                        FilterChip(selected=muscle in maskMuscles(focusMask).orEmpty(),
                            onClick={
                                focusMask=flipMuscle(focusMask,muscle)
                                reducedMask=maskMuscles(reducedMask).orEmpty()
                                    .minus(muscle).sortedBy{it.ordinal}
                                    .joinToString(","){it.name}
                                musclesConfirmed=false
                            },label={Text("Focus")})
                        FilterChip(selected=muscle in maskMuscles(reducedMask).orEmpty(),
                            onClick={
                                reducedMask=flipMuscle(reducedMask,muscle)
                                focusMask=maskMuscles(focusMask).orEmpty()
                                    .minus(muscle).sortedBy{it.ordinal}
                                    .joinToString(","){it.name}
                                musclesConfirmed=false
                            },label={Text("Reduced")})
                    }
                }
                Checkbox(checked=musclesConfirmed,onCheckedChange={musclesConfirmed=it})
                Text("I reviewed all muscle focus/reduction choices, including none",
                    style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(excludedIds,{excludedIds=it.take(1000);
                    excludedConfirmed=false},
                    label={Text("Excluded exercise IDs (optional, comma separated)")},
                    modifier=Modifier.fillMaxWidth())
                Checkbox(checked=excludedConfirmed,onCheckedChange={excludedConfirmed=it})
                Text("I confirmed excluded exercises, including none",
                    style=MaterialTheme.typography.bodySmall)
            }
        } else if(step==1) {
            PursuitCard {
                Text("Gym permissions",style=MaterialTheme.typography.titleLarge)
                OutlinedTextField(gymId,{gymId=it.take(63)},
                    label={Text("Gym identity")},modifier=Modifier.fillMaxWidth())
                NativeSetupChoice("Weight units",LoadUnit.entries,
                    chosenUnit,::displayEnum){
                    // Switching units always clears the ENTIRE provisional
                    // inventory; these numbers must never be converted.
                    if(unit!=it.name) {
                        unit=it.name
                        dumbbells="";cable="";barWeight="";plates=""
                        machineId="";machineLoads="";gymConfirmed=false
                    }
                }
                NativeSetupChoice("Allow bodyweight exercises",listOf("YES","NO"),
                    bodyweight.takeIf{it.isNotBlank()},{it}){
                    bodyweight=it
                }
                NativeSetupChoice("Allow supersets",listOf("YES","NO"),
                    supersets.takeIf{it.isNotBlank()},{it}){supersets=it}
                NativeSetupChoice("Triceps on lower days",listOf("YES","NO"),
                    tricepsLower.takeIf{it.isNotBlank()},{it}){tricepsLower=it}
                OutlinedTextField(barbellLimit,{barbellLimit=it.take(2)},
                    label={Text("Maximum barbell exercises per day (0–12)")},
                    modifier=Modifier.fillMaxWidth())
                Text("Only list physically verified equipment. You can add more individual machines and attachments later in the native gym editor. Unknown is NOT zero or a default stack.",
                    style=MaterialTheme.typography.bodyMedium)
                OutlinedTextField(dumbbells,{dumbbells=it.take(900);
                    gymConfirmed=false},
                    label={Text("Dumbbells per hand · exact " + unit.ifBlank { "selected unit" } + " values")},
                    placeholder={Text("Only your real values, comma-separated")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(cable,{cable=it.take(100);gymConfirmed=false},
                    label={Text("Cable 1 · min, increment, max")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(barWeight,{barWeight=it.take(30);gymConfirmed=false},
                    label={Text("Barbell empty bar weight")})
                OutlinedTextField(plates,{plates=it.take(500);gymConfirmed=false},
                    label={Text("Confirmed plate pairs, e.g. 2.5x2, 5x1")})
                OutlinedTextField(machineId,{machineId=it.take(63);gymConfirmed=false},
                    label={Text("Optional machine's stable equipment ID")})
                OutlinedTextField(machineLoads,{machineLoads=it.take(900);
                    gymConfirmed=false},
                    label={Text("That machine's exact logged selectable loads")})
                OutlinedTextField(fixtures,{fixtures=it.take(1000);
                    gymConfirmed=false},
                    label={Text("Confirmed facilities, comma-separated")},
                    supportingText={Text("e.g. flat-bench, rack, incline-bench, pullup-bar")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(attachments,{attachments=it.take(1000);
                    gymConfirmed=false},
                    label={Text("Confirmed attachments, comma-separated")},
                    modifier=Modifier.fillMaxWidth())
                if(chosenUnit!=null && gymInventory==null) Text(
                    "Please correct inventory format. A partial bar/plate pair or invalid weight cannot be saved.",
                    color=MaterialTheme.colorScheme.error)
                Checkbox(checked=gymConfirmed,onCheckedChange={gymConfirmed=it &&
                    gymInventory!=null && confirmedFacilities!=null &&
                    confirmedAttachments!=null})
                Text("I checked these physical gym facts, including any explicitly empty inventory",
                    style=MaterialTheme.typography.bodySmall)
            }
        } else {
            PursuitCard(highlighted=true) {
                Text("Kotlin setup review",style=MaterialTheme.typography.titleLarge)
                Text("Status: ${displayEnum(review.status)}",
                    style=MaterialTheme.typography.titleMedium)
                if(review.issues.isNotEmpty()) {
                    Text("${review.issues.size} fields need attention before saving",
                        style=MaterialTheme.typography.bodyMedium)
                    review.issues.take(25).forEach { issue ->
                        Text("${issue.field} · ${displayEnum(issue.kind)}",
                            color=MaterialTheme.colorScheme.error,
                            style=MaterialTheme.typography.bodySmall)
                        Text(issue.explanation,style=MaterialTheme.typography.bodySmall)
                    }
                    if(review.issues.size>25)Text("Additional issues remain. Complete the displayed fields and review again.")
                } else {
                    Text("Your exact choices passed the independent Kotlin setup review. This does NOT approve a program or issue a workout.")
                    Text("Training: ${review.reviewed?.trainingWeekdays?.sorted()?.joinToString()} · " +
                        "Gym: ${review.reviewed?.gym?.confirmedStock?.size} confirmed implements",
                        style=MaterialTheme.typography.bodyMedium)
                    Text("The first 20 catalog variants are engineering-reviewed provisional metadata, not independent scientific validation. No weights or training prescriptions are assigned from the catalog.")
                    Button(onClick={awaitingConfirmation=true},
                        enabled=!sourceState.busy && existing==null,
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Review native enrollment")
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(),
            horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick={step=(step-1).coerceAtLeast(0)},
                enabled=step>0 && !sourceState.busy) {Text("Back")}
            Button(onClick={step=(step+1).coerceAtMost(2)},
                enabled=step<2 && !sourceState.busy,
                modifier=Modifier.weight(1f)) {
                Text(if(step==1)"Review complete setup" else "Continue")
            }
        }
    }
    if(awaitingSavedResume)AlertDialog(
        onDismissRequest={if(!sourceState.busy)awaitingSavedResume=false},
        title={Text("Resume the existing Kotlin athlete?")},
        text={Text("The app will verify the original local identity and revalidate the saved training setup and physical gym. It will not create a new athlete, change the program, or issue a workout.")},
        confirmButton={Button(
            enabled=!sourceState.busy && sourceState.savedAthleteId!=null,
            onClick={
                awaitingSavedResume=false
                sourceState.savedAthleteId?.let(controller::resumeVerifiedSavedSetup)
            }
        ) {Text("Verify and resume")}},
        dismissButton={TextButton(onClick={awaitingSavedResume=false}) {
            Text("Cancel")
        }}
    )
    if(awaitingConfirmation)AlertDialog(
        onDismissRequest={if(!sourceState.busy)awaitingConfirmation=false},
        title={Text("Confirm your native athlete setup?")},
        text={Text("Create a device-local Kotlin athlete and its reviewed gym/catalog source. Your selected equipment and schedule will be stored exactly as reviewed. This does not edit the web app, approve a training program, or replace another athlete's saved data.")},
        confirmButton={Button(onClick={
            awaitingConfirmation=false
            controller.activateReviewedStarter(input.toDraft())
        },enabled=!sourceState.busy &&
            review.status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
            existing==null) {Text("Confirm and save")}},
        dismissButton={TextButton(onClick={awaitingConfirmation=false}) {
            Text("Cancel")
        }}
    )
}
