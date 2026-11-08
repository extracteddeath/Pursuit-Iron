package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.activity.compose.BackHandler
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
import java.time.ZoneId

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

private fun displayEnum(value:Enum<*>):String = when(value) {
    is SessionTimeBucket -> "${value.minimumMinutes}"+
        (value.preferredMaximumMinutes?.let{"–$it"}?:"+")+" min"
    ComposerSplit.FULL_BODY -> "Full body"
    ComposerSplit.UPPER_LOWER -> "Upper / Lower"
    ComposerSplit.PUSH_PULL_LEGS -> "Push / Pull / Legs"
    ComposerSplit.ULPPL -> "Upper / Lower / Push / Pull / Legs"
    else -> value.name.lowercase().replace('_',' ').replaceFirstChar{it.uppercase()}
}

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
    onContinue:()->Unit={},
    onBuildProgram:()->Unit={}
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
    var showSchedule by rememberSaveable {mutableStateOf(false)}
    var showPriorities by rememberSaveable {mutableStateOf(false)}
    var showExclusions by rememberSaveable {mutableStateOf(false)}
    var showExtraEquipment by rememberSaveable {mutableStateOf(false)}
    var pendingUnit by rememberSaveable {mutableStateOf<String?>(null)}
    var buildAfterSave by remember {mutableStateOf(false)}
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
    fun changeUnit(next:String) {
        if(unit.isNotBlank() && unit!=next) {
            dumbbells="";cable="";barWeight="";plates=""
            machineId="";machineLoads=""
        }
        unit=next;gymConfirmed=false
    }
    LaunchedEffect(existing,sourceState.busy,sourceState.refusal,buildAfterSave) {
        if(buildAfterSave && !sourceState.busy) {
            if(existing!=null && sourceState.refusal==null) {
                buildAfterSave=false
                onBuildProgram()
            } else if(sourceState.refusal!=null)buildAfterSave=false
        }
    }
    BackHandler(enabled=sourceState.busy ||
        (step>0 && pendingUnit==null && !awaitingConfirmation && !awaitingSavedResume)) {
        if(!sourceState.busy)step=(step-1).coerceAtLeast(0)
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        PageHeading("GET STARTED",
            when(step) {0->"Your training";1->"Your gym";else->"Review your setup"},
            "Choose how you train. Your first program comes next.")
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
                Text("Continue with the profile and gym saved on this device.",
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
                Text("Your training setup is ready",style=MaterialTheme.typography.titleLarge)
                existing.setup.reviewed?.let { setup ->
                    Text("${displayEnum(setup.goal)} · ${displayEnum(setup.split)} · ${setup.trainingWeekdays.size} days a week")
                }
                Text("Continue your saved program or build a new one from these settings.")
                Button(onClick=onContinue,enabled=!sourceState.busy) {Text("Continue to my program")}
                TextButton(onClick=onBuildProgram,enabled=!sourceState.busy) {
                    Text("Build a new program")
                }
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
                Text("Training preferences",style=MaterialTheme.typography.titleLarge)
                if(showIdentity) {
                sourceState.savedAthleteId?.takeIf { it != athleteId }?.let { prior ->
                    OutlinedButton(
                        onClick={ athleteId=prior;showIdentity=true },
                        enabled=!sourceState.busy,
                        modifier=Modifier.fillMaxWidth()
                    ) { Text("Use saved local athlete ID") }
                    Text("This selects an identifier only. You must still review and explicitly confirm your native settings before it opens protected data.",
                        style=MaterialTheme.typography.bodySmall)
                }
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
                    label={Text("Time zone")},
                    modifier=Modifier.fillMaxWidth(),singleLine=true)
                TextButton(onClick={timezone=ZoneId.systemDefault().id}) {
                    Text("Use this device's time zone")
                }
                NativeSetupWeekdays("Your training days (3–6)",weekdays) {
                    weekdays=it
                    calendarConfirmed=false;restConfirmed=false
                }
                TextButton(onClick={showSchedule=!showSchedule}) {
                    Text(if(showSchedule)"Hide schedule preferences" else "Optional schedule preferences")
                }
                if(showSchedule) {
                NativeSetupWeekdays("Locked training days",lockDays) {
                    lockDays=it;calendarConfirmed=false
                }
                NativeSetupWeekdays("Hard-session days",hardDays) {
                    hardDays=it;calendarConfirmed=false
                }
                NativeSetupWeekdays("Long-session days",longDays) {
                    longDays=it;calendarConfirmed=false
                }
                }
                val restNames=(0..6).filter{it !in selectedDays(weekdays)}
                    .joinToString{ kotlinWeekdayNames[it] }
                Text("Rest days: $restNames",style=MaterialTheme.typography.bodySmall)
                NativeKotlinSetupConfirmation(
                    "Use these training days and keep the other days for rest",
                    calendarConfirmed && restConfirmed,{
                        calendarConfirmed=it;restConfirmed=it
                    },enabled=selectedDays(weekdays).size in 3..6)
                Text("Muscle priorities",style=MaterialTheme.typography.titleMedium)
                FilterChip(selected=musclesConfirmed && focusMask.isBlank() && reducedMask.isBlank(),
                    onClick={focusMask="";reducedMask="";musclesConfirmed=true;showPriorities=false},
                    label={Text("Balanced training")})
                TextButton(onClick={showPriorities=!showPriorities}) {
                    Text(if(showPriorities)"Hide muscle choices" else "Choose focus or reduced muscles")
                }
                if(showPriorities) {
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
                NativeKotlinSetupConfirmation("Use these muscle priorities",musclesConfirmed,
                    {musclesConfirmed=it})
                }
                if(focusMask.isNotBlank() || reducedMask.isNotBlank()) {
                    Text("${maskMuscles(focusMask).orEmpty().size} focused · ${maskMuscles(reducedMask).orEmpty().size} reduced",
                        style=MaterialTheme.typography.bodySmall)
                }
                Text("Exercise preferences",style=MaterialTheme.typography.titleMedium)
                FilterChip(selected=excludedConfirmed && excludedIds.isBlank(),
                    onClick={excludedIds="";excludedConfirmed=true;showExclusions=false},
                    label={Text("Use all eligible exercises")})
                TextButton(onClick={showExclusions=!showExclusions}) {
                    Text(if(showExclusions)"Hide excluded exercises" else "Exclude exercises")
                }
                if(showExclusions) {
                    NativeKotlinNamedSetupChoices("Exercises to leave out",excludedIds,
                        StarterExerciseTranche.entries().map{
                            it.legacyCuratedId to kotlinExerciseLabel(it.legacyCuratedId)
                        },{excludedIds=it;excludedConfirmed=false},searchable=true)
                    NativeKotlinSetupConfirmation("Use these exclusions",excludedConfirmed,
                        {excludedConfirmed=it})
                }
                TextButton(onClick={showIdentity=!showIdentity}) {
                    Text(if(showIdentity)"Hide profile details" else "Advanced profile details")
                }
            }
        } else if(step==1) {
            PursuitCard {
                Text("Training options",style=MaterialTheme.typography.titleLarge)
                if(showIdentity)OutlinedTextField(gymId,{gymId=it.take(63)},
                    label={Text("Gym ID")},modifier=Modifier.fillMaxWidth())
                NativeSetupChoice("Weight units",LoadUnit.entries,
                    chosenUnit,{it.name.lowercase()}){
                    // Switching units always clears the ENTIRE provisional
                    // inventory; these numbers must never be converted.
                    if(unit!=it.name) {
                        if(unit.isNotBlank() && listOf(dumbbells,cable,barWeight,plates,machineLoads)
                                .any{raw->raw.isNotBlank()})pendingUnit=it.name
                        else changeUnit(it.name)
                    }
                }
                NativeSetupChoice("Allow bodyweight exercises",listOf("YES","NO"),
                    bodyweight.takeIf{it.isNotBlank()},{if(it=="YES")"Allow" else "Don't allow"}){
                    bodyweight=it
                }
                NativeSetupChoice("Allow supersets",listOf("YES","NO"),
                    supersets.takeIf{it.isNotBlank()},{if(it=="YES")"Allow" else "Don't allow"}){supersets=it}
                NativeSetupChoice("Triceps on lower days",listOf("YES","NO"),
                    tricepsLower.takeIf{it.isNotBlank()},{if(it=="YES")"Allow" else "Don't allow"}){tricepsLower=it}
                OutlinedTextField(barbellLimit,{barbellLimit=it.take(2)},
                    label={Text("Maximum barbell exercises per day (0–12)")},
                    modifier=Modifier.fillMaxWidth())
            }
            PursuitCard {
                Text("Available weights",style=MaterialTheme.typography.titleLarge)
                Text("Enter the weights your gym actually has. You'll choose your starting workout weights next.",
                    style=MaterialTheme.typography.bodySmall)
                OutlinedTextField(dumbbells,{dumbbells=it.take(900);
                    gymConfirmed=false},
                    label={Text("Available dumbbells · per hand")},
                    supportingText={Text("Exact ${unit.lowercase().ifBlank{"weight"}} values, separated by commas")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(barWeight,{barWeight=it.take(30);gymConfirmed=false},
                    label={Text("Empty barbell weight")},modifier=Modifier.fillMaxWidth())
                OutlinedTextField(plates,{plates=it.take(500);gymConfirmed=false},
                    label={Text("Available plate pairs")},
                    supportingText={Text("Weight × matching pairs, e.g. 2.5x2, 5x1")},
                    modifier=Modifier.fillMaxWidth())
                NativeKotlinNamedSetupChoices("Benches and stations",fixtures,
                    kotlinSetupFacilities,{fixtures=it;gymConfirmed=false})
                TextButton(onClick={showExtraEquipment=!showExtraEquipment}) {
                    Text(if(showExtraEquipment)"Hide additional equipment" else "Additional equipment")
                }
                if(showExtraEquipment) {
                OutlinedTextField(cable,{cable=it.take(100);gymConfirmed=false},
                    label={Text("Cable stack · min, increment, max")},
                    modifier=Modifier.fillMaxWidth())
                OutlinedTextField(machineId,{machineId=it.take(63);gymConfirmed=false},
                    label={Text("Optional machine's stable equipment ID")})
                OutlinedTextField(machineLoads,{machineLoads=it.take(900);
                    gymConfirmed=false},
                    label={Text("That machine's exact logged selectable loads")})
                OutlinedTextField(attachments,{attachments=it.take(1000);
                    gymConfirmed=false},
                    label={Text("Confirmed attachments, comma-separated")},
                    modifier=Modifier.fillMaxWidth())
                }
                if(chosenUnit!=null && gymInventory==null) Text(
                    "Please correct inventory format. A partial bar/plate pair or invalid weight cannot be saved.",
                    color=MaterialTheme.colorScheme.error)
                NativeKotlinSetupConfirmation("I checked this equipment and these available weights",
                    gymConfirmed,{gymConfirmed=it},
                    enabled=chosenUnit!=null && gymInventory!=null &&
                        confirmedFacilities!=null && confirmedAttachments!=null)
            }
        } else {
            PursuitCard(highlighted=true) {
                Text("Your setup",style=MaterialTheme.typography.titleLarge)
                Text(if(review.issues.isEmpty())"Ready to save" else "A few choices need attention",
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
                    review.reviewed?.let {saved->
                        Text("${displayEnum(saved.goal)} · ${displayEnum(saved.experience)}")
                        Text("${displayEnum(saved.split)} · ${displayEnum(saved.sessionTimeBucket)}")
                        Text("Training: "+saved.trainingWeekdays.sorted().joinToString{ kotlinWeekdayNames[it] })
                        Text("Rest: "+saved.declaredRestDays.sorted().joinToString{ kotlinWeekdayNames[it] })
                        Text("${saved.gym.confirmedStock.size} verified weight inventories · ${saved.gym.unit.name.lowercase()}")
                    }
                    Text("Save these choices, then review your exercises and starting weights.")
                    Button(onClick={awaitingConfirmation=true},
                        enabled=!sourceState.busy && existing==null,
                        modifier=Modifier.fillMaxWidth()) {
                        Text("Save setup and build my program")
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
    if(pendingUnit!=null)AlertDialog(
        onDismissRequest={pendingUnit=null},
        title={Text("Change weight units?")},
        text={Text("Changing units clears the entered weights so they cannot be reused under a different unit. Your other choices stay saved in this form.")},
        confirmButton={Button(onClick={
            pendingUnit?.let(::changeUnit);pendingUnit=null
        }){Text("Change units and clear weights")}},
        dismissButton={TextButton(onClick={pendingUnit=null}){Text("Keep my weights")}})
    if(awaitingSavedResume)AlertDialog(
        onDismissRequest={if(!sourceState.busy)awaitingSavedResume=false},
        title={Text("Resume your saved setup?")},
        text={Text("Reopen the profile and gym saved on this device to continue your training.")},
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
        title={Text("Save your training setup?")},
        text={Text("Save your reviewed preferences, schedule and gym on this device, then choose the starting weights for your first program.")},
        confirmButton={Button(onClick={
            awaitingConfirmation=false
            buildAfterSave=true
            controller.activateReviewedStarter(input.toDraft())
        },enabled=!sourceState.busy &&
            review.status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
            existing==null) {Text("Save setup")}},
        dismissButton={TextButton(onClick={awaitingConfirmation=false}) {
            Text("Cancel")
        }}
    )
}
