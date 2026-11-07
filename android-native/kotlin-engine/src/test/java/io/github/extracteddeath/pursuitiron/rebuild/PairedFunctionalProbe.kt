package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties

/** Execute real policies; external checks verify integrity, not rep-prediction quality. */
object PairedFunctionalProbe {
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun quote(s:String)="\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\""
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2)
        val bytes=Files.readAllBytes(Path.of(args[0]))
        val text=String(bytes,Charsets.UTF_8)
        val lines=text.lines().filter{it.isNotBlank()&&!it.startsWith("#")}
        require(lines.all{Regex("^[A-Za-z0-9_.-]+=[A-Za-z0-9_:.,-]+$").matches(it)})
        require(lines.map{it.substringBefore('=')}.distinct().size==lines.size)
        val p=Properties().also{it.load(text.reader())}
        fun field(k:String)=requireNotNull(p.getProperty(k))
        require(field("schemaVersion")=="1"&&field("unit")=="lb")
        require(field("caseIds")=="dp-rep-target-jumps,machine-increments,bar-stock")
        val fixture=hash(bytes)
        fun digest(id:String)=hash("$fixture\n$id\n".toByteArray())
        val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
            field("rep.availableLoads").split(',').map(::BigDecimal))
        val repResults=field("rep.scenarios").split(',').map{id->
            val reps=field("rep.$id.reps").split(',').map(String::toInt)
            val rir=field("rep.$id.rir").split(',').map{it.takeUnless{v->v=="null"}?.toInt()}
            val count=field("rep.count").toInt();require(reps.size==count&&rir.size==count)
            val load=BigDecimal(field("rep.load"));val goal=field("rep.goalRir").toInt()
            val session=ProgressionWorkoutEvidence(id,field("rep.exerciseId"),LoadUnit.LB,
                RepBand(field("rep.floor").toInt(),field("rep.ceiling").toInt()),count,
                (0 until count).map{i->WorkingSetFact(i,SetOutcome.COMPLETED,load,reps[i],rir[i],load,goal)})
            val original=session.copy(observations=session.observations.toList())
            val result=FatigueAwareRepTargetReviewDomain.assess(session,stock,
                LoadBasis.DUMBBELL_PER_HAND,CoachReadiness.NORMAL,false)
            val targets=result.candidateRepTargets ?:reps
            "{\"id\":${quote(id)},\"targets\":["+targets.joinToString(","){
                "{\"weight\":$load,\"reps\":$it}"}+"],\"reason\":${quote(result.reason.name)},"+
                "\"actualRirCount\":${rir.count{it!=null}},\"assumedGoalRirSetCount\":${result.assumedGoalRirSetCount},"+
                "\"forecastLimitations\":[${result.forecastLimitations.joinToString(","){quote(it.name)}}],"+
                "\"mayAutoApply\":${result.canAutoApply},\"mutatedInput\":${original!=session}}"
        }
        fun inspect(id:String,stock:VerifiedLoadStock,requested:BigDecimal):String {
            val before=stock.toString()
            val result=EquipmentLoadDomain.inspect(stock,requested,LoadUnit.LB)
            val confirmed=result as? LoadInspection.Confirmed
            return "{\"id\":${quote(id)},\"requested\":$requested,"+
                "\"floor\":${confirmed?.atOrBelow ?: "null"},\"next\":${confirmed?.nextAbove ?: "null"},"+
                "\"mutatedInput\":${before!=stock.toString()}}"
        }
        val machine=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
            field("machine.loads").split(',').map(::BigDecimal))
        val machineResults=field("machine.requested").split(',').map{v->inspect(v,machine,BigDecimal(v))}
        val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,BigDecimal(field("bar.weight")),
            field("bar.platePairs").split(',').associate{v->
                val pair=v.split(':');BigDecimal(pair[0]) to pair[1].toInt()})
        val barResults=listOf(inspect(field("bar.requested"),bar,BigDecimal(field("bar.requested"))))
        val groups=listOf("dp-rep-target-jumps" to repResults,"machine-increments" to machineResults,"bar-stock" to barResults)
        val inputs=p.stringPropertyNames().sorted().joinToString(",") {quote(it)+":"+quote(field(it))}
        val results=groups.joinToString(","){(id,rows)->
            "{\"caseId\":${quote(id)},\"inputDigest\":${quote(digest(id))},\"subcases\":[${rows.joinToString(",")}] }"}
        val output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().parent)
        Files.writeString(output,"{\"schemaVersion\":1,\"fixtureDigest\":${quote(fixture)},"+
            "\"inputProperties\":{$inputs},\"results\":[$results]}\n")
        println("Executed four rep-target and three physical-stock scenarios; no self-assigned outcomes.")
    }
}
