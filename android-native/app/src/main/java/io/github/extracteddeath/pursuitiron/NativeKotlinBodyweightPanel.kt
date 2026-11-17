package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeSavedSetupResume
import io.github.extracteddeath.pursuitiron.rebuild.LoadUnit
import io.github.extracteddeath.pursuitiron.rebuild.StrengthStandardsMeasurementDomain
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun bodyweightLabel(amount:BigDecimal,unit:LoadUnit):String =
    "${amount.stripTrailingZeros().toPlainString()} ${unit.name.lowercase()}"

private fun bodyweightShown(amount:BigDecimal,unit:LoadUnit):String =
    "${amount.setScale(1,RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()} ${unit.name.lowercase()}"

private fun bodyweightFailure(reason:NativeBodyweightRefusal):String=when(reason) {
    NativeBodyweightRefusal.OWNER_NOT_VERIFIED ->
        "Confirm your current local athlete profile to save measurements."
    NativeBodyweightRefusal.INVALID_ENTRY ->
        "Enter a bodyweight above zero and at most 1000 in the selected unit, with up to two decimal places."
    NativeBodyweightRefusal.DATE_IN_FUTURE ->
        "The entry date cannot be in the future."
    NativeBodyweightRefusal.REVISION_CONFLICT ->
        "The measurements changed in another view. Refresh before making this edit."
    NativeBodyweightRefusal.NO_MATCHING_DATE ->
        "That dated measurement no longer exists. Refresh before deleting."
    NativeBodyweightRefusal.REPLACEMENT_CONFIRMATION_REQUIRED ->
        "Confirm the correction before replacing an existing dated measurement."
    NativeBodyweightRefusal.FULL ->
        "This local ledger has reached its record limit. Nothing was overwritten."
    NativeBodyweightRefusal.CORRUPT ->
        "Saved measurements failed integrity checks. They were not overwritten."
    NativeBodyweightRefusal.STORAGE_UNAVAILABLE ->
        "Measurement storage is unavailable. No record was changed."
}

@Composable
private fun NativeBodyweightTrend(
    entries:List<NativeBodyweightEntry>,unit:LoadUnit
) {
    val recent=entries.takeLast(30)
    if(recent.isEmpty()) {
        Text("No bodyweight measurements yet. Save your first dated entry to begin.",
            style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val normalized=recent.map {entry->
        entry.date to StrengthStandardsMeasurementDomain.convert(
            entry.amount,entry.unit,unit)
    }
    val first=normalized.first()
    val latest=normalized.last()
    Text("Latest · ${latest.first} · ${bodyweightShown(latest.second,unit)}",
        style=MaterialTheme.typography.titleSmall)
    if(normalized.size<2) {
        Text("A second dated measurement is needed to show change.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val delta=latest.second-first.second
    val deltaLabel=(if(delta.signum()>0)"+" else "")+bodyweightShown(delta,unit)
    PursuitMetricStrip(listOf(
        "First shown" to bodyweightShown(first.second,unit),
        "Latest" to bodyweightShown(latest.second,unit),
        "Difference" to deltaLabel
    ))
    val firstDay=first.first.toEpochDay().toFloat()
    val lastDay=latest.first.toEpochDay().toFloat()
    val daySpan=(lastDay-firstDay).takeIf{it>0f} ?:1f
    val low=normalized.minOf{it.second}.toFloat()
    val high=normalized.maxOf{it.second}.toFloat()
    val weightSpan=(high-low).takeIf{it>0f} ?:1f
    val line=MaterialTheme.colorScheme.primary
    val guide=MaterialTheme.colorScheme.outlineVariant
    val spoken=normalized.joinToString("; "){(date,amount)->
        "$date, ${bodyweightShown(amount,unit)}"
    }
    Canvas(Modifier.fillMaxWidth().height(164.dp)
        .semantics {contentDescription="Dated bodyweight trend. $spoken"}) {
        val left=14.dp.toPx()
        val right=size.width-14.dp.toPx()
        val top=10.dp.toPx()
        val bottom=size.height-10.dp.toPx()
        for(fraction in listOf(0f,0.5f,1f)) {
            val y=top+(bottom-top)*fraction
            drawLine(guide,Offset(left,y),Offset(right,y),
                strokeWidth=1.dp.toPx())
        }
        fun pos(index:Int):Offset {
            val (date,weight)=normalized[index]
            val x=(date.toEpochDay().toFloat()-firstDay)/daySpan
            val y=(weight.toFloat()-low)/weightSpan
            return Offset(left+(right-left)*x,bottom-(bottom-top)*y)
        }
        for(i in 0 until normalized.lastIndex)
            drawLine(line,pos(i),pos(i+1),
                strokeWidth=3.dp.toPx(),cap=StrokeCap.Round)
        for(i in normalized.indices)
            drawCircle(line,radius=4.dp.toPx(),center=pos(i))
    }
    Text("Dated values, not a weight-loss goal or health assessment. " +
        "The chart uses actual elapsed days.",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
    if(entries.size>30)Text("Showing the latest 30 of ${entries.size} records.",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Full, opt-in athlete-entered measurement UI. Source owner identity comes
 * from the host's verified active profile. Exercise loads and issued targets
 * are deliberately absent; no progression authority enters this panel.
 */
@Composable
internal fun NativeKotlinBodyweightPanel(
    host:NativeKotlinCandidateHost,
    athleteId:String?,
    preferredUnit:LoadUnit,
    onUseForComparison:(BigDecimal,LoadUnit)->Unit
) {
    val context=LocalContext.current.applicationContext
    val store=remember(context){NativeKotlinBodyweightStore(context)}
    val scope=rememberCoroutineScope()
    var refresh by remember{mutableIntStateOf(0)}
    var readOwner by remember {mutableStateOf<String?>(null)}
    var loaded by remember{mutableStateOf<NativeBodyweightRead?>(null)}
    var saving by remember{mutableStateOf(false)}
    var notice by remember{mutableStateOf<String?>(null)}
    var entryDate by rememberSaveable(athleteId){
        mutableStateOf(LocalDate.now().toString())
    }
    var entryAmount by rememberSaveable(athleteId){mutableStateOf("")}
    var entryUnit by rememberSaveable(athleteId){
        mutableStateOf(preferredUnit.name)
    }
    var showEntry by rememberSaveable(athleteId){mutableStateOf(false)}
    var pendingReplace by remember {mutableStateOf<NativeBodyweightEntry?>(null)}
    var pendingDelete by remember {mutableStateOf<LocalDate?>(null)}
    var showAll by rememberSaveable(athleteId){mutableStateOf(false)}
    val owner=athleteId?.takeIf(NativeBodyweightLedgerDomain::validOwner)
    LaunchedEffect(host,owner,refresh) {
        loaded=null
        readOwner=null
        if(owner!=null) {
            val source=withContext(Dispatchers.IO){
                val confirmed=runCatching {
                    (host.reviewActiveAthleteSetup() as? NativeSavedSetupResume.Ready)
                        ?.setup?.athleteProfileId
                }.getOrNull()
                if(confirmed==owner)store.read(owner)
                else NativeBodyweightRead.Rejected(
                    NativeBodyweightRefusal.OWNER_NOT_VERIFIED)
            }
            if(athleteId==owner) {
                readOwner=owner
                loaded=source
            }
        }
    }
    val ready=(loaded as? NativeBodyweightRead.Ready)
        ?.takeIf{readOwner==owner && owner!=null}
    val records=ready?.ledger?.entries.orEmpty()
    val date=runCatching {LocalDate.parse(entryDate)}.getOrNull()
    val amount=entryAmount.trim().toBigDecimalOrNull()
    val unit=LoadUnit.entries.firstOrNull{it.name==entryUnit} ?:LoadUnit.LB
    val pending=if(date!=null && amount!=null)
        NativeBodyweightEntry(date,amount,unit) else null
    val refusal=if(pending!=null)
        NativeBodyweightLedgerDomain.validEntry(pending,LocalDate.now())
        else null
    val canSave=pending!=null && refusal==null && ready!=null && !saving
    val existing=records.firstOrNull{it.date==date}

    fun submit(change:NativeBodyweightEntry?,remove:LocalDate?,
               confirmedReplace:Boolean) {
        val selectedOwner=owner ?:return
        val source=ready ?:return
        if(saving || (change==null && remove==null))return
        saving=true
        notice=null
        scope.launch {
            val result=withContext(Dispatchers.IO) {
                val confirmed=runCatching {
                    (host.reviewActiveAthleteSetup() as? NativeSavedSetupResume.Ready)
                        ?.setup?.athleteProfileId
                }.getOrNull()
                if(confirmed!=selectedOwner)NativeBodyweightWrite.Rejected(
                    NativeBodyweightRefusal.OWNER_NOT_VERIFIED)
                else if(remove!=null)store.delete(
                    selectedOwner,source.ledger.revision,remove)
                else store.save(selectedOwner,source.ledger.revision,
                    checkNotNull(change),LocalDate.now(),confirmedReplace)
            }
            if(athleteId==selectedOwner) {
                notice=when(result) {
                    is NativeBodyweightWrite.Saved ->
                        if(remove!=null)"Dated measurement deleted."
                        else "Dated measurement saved. The workout journal was not changed."
                    is NativeBodyweightWrite.Rejected ->
                        bodyweightFailure(result.reason)
                }
                if(result is NativeBodyweightWrite.Saved) {
                    loaded=NativeBodyweightRead.Ready(result.ledger)
                    readOwner=selectedOwner
                } else refresh++
            }
            saving=false
        }
    }

    PursuitCard {
        PursuitSectionHeading("Bodyweight history",
            "Log your measurements by date. These are private observations, not workout sets.")
        when {
            owner==null ->
                Text("Confirm your current athlete setup to view or record measurements.",
                    style=MaterialTheme.typography.bodyMedium)
            readOwner!=owner || loaded==null ->
                LinearProgressIndicator(Modifier.fillMaxWidth())
            loaded is NativeBodyweightRead.Rejected ->
                PursuitHistoryStatus("Measurement history unavailable",
                    bodyweightFailure((loaded as NativeBodyweightRead.Rejected).reason))
            else -> {
                NativeBodyweightTrend(records,preferredUnit)
                OutlinedButton(
                    onClick={showEntry=!showEntry},
                    modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                    Text(if(showEntry)"Close measurement editor"
                        else "Log or correct bodyweight")
                }
                if(showEntry) {
                    Text("One entry per date. Replacing a date requires confirmation.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value=entryDate,onValueChange={entryDate=it.take(10)},
                        label={Text("Date (YYYY-MM-DD)")},
                        singleLine=true,
                        isError=date==null,
                        modifier=Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(),
                        horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                        OutlinedTextField(
                            value=entryAmount,onValueChange={
                                if(it.length<=10 && it.all{c->c.isDigit()||c=='.'})
                                    entryAmount=it
                            },
                            label={Text("Bodyweight")},
                            keyboardOptions=KeyboardOptions(
                                keyboardType=KeyboardType.Decimal),
                            singleLine=true,
                            modifier=Modifier.weight(1f),
                            isError=entryAmount.isNotBlank() &&
                                (amount==null || refusal!=null))
                        Column {
                            FilterChip(selected=unit==LoadUnit.LB,
                                onClick={entryUnit=LoadUnit.LB.name},
                                label={Text("lb")})
                            FilterChip(selected=unit==LoadUnit.KG,
                                onClick={entryUnit=LoadUnit.KG.name},
                                label={Text("kg")})
                        }
                    }
                    if(date==null && entryDate.isNotBlank())Text(
                        "Enter an actual calendar date as YYYY-MM-DD.",
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                    if(refusal!=null)Text(bodyweightFailure(refusal),
                        color=MaterialTheme.colorScheme.error,
                        style=MaterialTheme.typography.bodySmall)
                    if(existing!=null)Text(
                        "An entry already exists on ${existing.date}: "+
                            bodyweightLabel(existing.amount,existing.unit)+
                            ". Saving will ask before replacing it.",
                        style=MaterialTheme.typography.bodySmall)
                    Button(
                        onClick={
                            val observation=checkNotNull(pending)
                            if(existing!=null)pendingReplace=observation
                            else submit(observation,null,false)
                        },
                        enabled=canSave,
                        modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                        Text(if(existing!=null)"Review correction" else "Save weigh-in")
                    }
                }
                if(records.isNotEmpty()) {
                    Text("Dated entries",style=MaterialTheme.typography.titleSmall)
                    val shown=if(showAll)records.asReversed()
                        else records.asReversed().take(8)
                    shown.forEach {record->
                        Column(Modifier.fillMaxWidth()) {
                            Text("${record.date} · "+
                                bodyweightLabel(record.amount,record.unit),
                                style=MaterialTheme.typography.bodyMedium)
                            Row(Modifier.fillMaxWidth().horizontalScroll(
                                rememberScrollState()),
                                horizontalArrangement=Arrangement.spacedBy(
                                    PursuitSpace.xs)) {
                                TextButton(onClick={
                                    entryDate=record.date.toString()
                                    entryAmount=record.amount.stripTrailingZeros()
                                        .toPlainString()
                                    entryUnit=record.unit.name
                                    showEntry=true
                                },enabled=!saving,
                                    modifier=Modifier.heightIn(min=48.dp)) {
                                    Text("Edit")
                                }
                                TextButton(onClick={
                                    onUseForComparison(record.amount,record.unit)
                                },enabled=!saving,
                                    modifier=Modifier.heightIn(min=48.dp)) {
                                    Text("Use for strength comparison")
                                }
                                TextButton(onClick={pendingDelete=record.date},
                                    enabled=!saving,
                                    modifier=Modifier.heightIn(min=48.dp)) {
                                    Text("Delete")
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    if(records.size>8)TextButton(onClick={showAll=!showAll},
                        modifier=Modifier.heightIn(min=48.dp)) {
                        Text(if(showAll)"Show recent entries"
                            else "See all ${records.size} entries")
                    }
                }
            }
        }
        notice?.let {Text(it,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)}
        Text("Bodyweight history is private to this installation and confirmed "+
            "athlete. It does not feed the training engine, PRs, fatigue advice or "+
            "automatic strength comparisons. Export and migration acceptance "+
            "remain separate beta work.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }

    pendingReplace?.let {replacement->
        AlertDialog(onDismissRequest={pendingReplace=null},
            title={Text("Replace dated measurement?")},
            text={Text("Replace the ${replacement.date} entry with "+
                bodyweightLabel(replacement.amount,replacement.unit)+
                "? Training records and other days will not change.")},
            confirmButton={Button(onClick={
                pendingReplace=null
                submit(replacement,null,true)
            },enabled=!saving){Text("Save correction")}},
            dismissButton={TextButton(onClick={pendingReplace=null}){
                Text("Keep existing")
            }})
    }
    pendingDelete?.let {deletedDate->
        AlertDialog(onDismissRequest={pendingDelete=null},
            title={Text("Delete bodyweight entry?")},
            text={Text("Remove the entry dated $deletedDate? "+
                "The other dates and your workout history are unchanged.")},
            confirmButton={Button(onClick={
                pendingDelete=null
                submit(null,deletedDate,false)
            },enabled=!saving){Text("Delete entry")}},
            dismissButton={TextButton(onClick={pendingDelete=null}){
                Text("Cancel")
            }})
    }
}
