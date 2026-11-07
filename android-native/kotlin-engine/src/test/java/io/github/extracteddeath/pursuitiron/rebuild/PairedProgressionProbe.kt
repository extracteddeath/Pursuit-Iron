package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Properties

/**
 * Executes real load-review policies on shared frozen inputs. This is NOT a
 * unit-test count, workout issuance, disk-history proof or Android device run.
 * The external oracle grades the outputs; this probe never grades itself.
 */
object PairedProgressionProbe {
    private fun digest(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
    private fun quoted(value: String) = "\"" + value.replace("\\", "\\\\")
        .replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""

    @JvmStatic fun main(args: Array<String>) {
        require(args.size == 2)
        val bytes=Files.readAllBytes(Path.of(args[0]))
        val text=String(bytes,Charsets.UTF_8)
        // Restrict the shared Properties dialect: no continuations, escapes,
        // whitespace surprises or duplicate keys decoded differently in JS.
        val lines=text.lines().filter {it.isNotBlank() && !it.startsWith("#")}
        require(lines.all {Regex("^[A-Za-z0-9_.-]+=[A-Za-z0-9_:.,-]+$").matches(it)})
        require(lines.map {it.substringBefore('=')}.distinct().size==lines.size)
        val input=Properties().also {it.load(text.reader())}
        fun field(key:String)=requireNotNull(input.getProperty(key)) {"Missing $key"}
        require(field("schemaVersion")=="1" && field("unit")=="lb")
        require(field("effortPolicy")=="assume_original_goal_when_unreported")
        val fixtureDigest=digest(bytes)
        val count=field("workingSets").toInt()
        val band=RepBand(field("repFloor").toInt(),field("repCeiling").toInt())
        val targetRir=field("issuedRir").toInt()
        val stock=VerifiedLoadStock.Barbell(LoadUnit.LB,BigDecimal(field("barWeight")),
            field("platePairs").split(',').associate {
                val pair=it.split(':'); BigDecimal(pair[0]) to pair[1].toInt()
            })
        val caseIds=field("caseIds").split(',')
        require(caseIds.distinct().size==caseIds.size)
        val outputs=caseIds.map {caseId ->
            fun numbers(visit:String,name:String)=field("$caseId.$visit.$name").split(',')
                .also {require(it.size==count)}
            fun evidence(visit:String): ProgressionWorkoutEvidence {
                val loads=numbers(visit,"loads")
                val reps=numbers(visit,"reps")
                val rir=numbers(visit,"rir")
                val issued=numbers(visit,"issuedLoads")
                return ProgressionWorkoutEvidence("$caseId-$visit",field("exerciseId"),
                    LoadUnit.LB,band,count,(0 until count).map {ordinal ->
                        WorkingSetFact(ordinal,SetOutcome.COMPLETED,BigDecimal(loads[ordinal]),
                            reps[ordinal].toInt(),rir[ordinal].takeUnless {it=="null"}?.toInt(),
                            BigDecimal(issued[ordinal]),targetRir)
                    })
            }
            val previous=evidence("previous")
            val latest=evidence("latest")
            val originalPrevious=previous.copy(observations=previous.observations.toList())
            val originalLatest=latest.copy(observations=latest.observations.toList())
            val increase=ConservativeProgressionReview.assess(previous,latest,stock,
                BigDecimal(field("maximumIncrease")))
            val recovery=OverloadRecoveryLoadReviewDomain.assess(previous,latest,stock,
                LoadBasis.BARBELL_TOTAL)
            val candidate=increase.reviewCandidate ?: recovery.candidateReturnLoad
            val facts=DoubleProgressionEvidenceDomain.assess(band,count,latest.observations)
            fun echo(workout:ProgressionWorkoutEvidence):String = workout.observations.joinToString(
                prefix="[",postfix="]") {row ->
                "{\"ordinal\":${row.workingOrdinal},\"load\":${row.actualLoad},\"reps\":${row.actualReps},\"rir\":${row.actualRir},\"issuedLoad\":${row.issuedLoad},\"issuedRir\":${row.issuedRir}}"
            }
            "{\"caseId\":${quoted(caseId)},\"inputDigest\":${quoted(digest("$fixtureDigest\n$caseId\n".toByteArray()))},"+
                "\"previous\":${echo(previous)},\"latest\":${echo(latest)},"+
                "\"candidateLoad\":${candidate?.toPlainString() ?: "null"},"+
                "\"increaseReason\":${quoted(increase.reason.name)},\"recoveryReason\":${quoted(recovery.reason.name)},"+
                "\"completedSets\":${facts.completedWorkingSets},\"actualRirCount\":${facts.actualRirReportedOrdinals.size},"+
                "\"assumedGoalRirSetCount\":${increase.assumedGoalRirSetCount},"+
                "\"mutatedInput\":${previous!=originalPrevious || latest!=originalLatest},\"mayAutoApply\":false}"
        }
        val json="{\"schemaVersion\":1,\"fixtureDigest\":${quoted(fixtureDigest)},\"results\":[${outputs.joinToString(",")}] }\n"
        val output=Path.of(args[1])
        Files.createDirectories(output.toAbsolutePath().parent)
        Files.writeString(output,json)
        println("Executed ${outputs.size} frozen native load-review cases; no self-assigned outcomes.")
    }
}
