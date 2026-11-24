package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

/** Executes actual Kotlin percentage policy on the exact same input fixture
 * as the pinned current web function. The external runner grades outputs. */
object Stage2PercentageProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2)
        val lines=Files.readAllLines(Path.of(args[0])).filter{it.isNotBlank()&&!it.startsWith("#")}
        val scope=BasicProgressionScope("athlete","cycle","gym","block","day","press",
            "machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
        val stock=VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,
            BigDecimal("5"),BigDecimal("5"),BigDecimal("500"))
        val results=lines.map{line->
            val f=line.split('|')
            require(f.size==12)
            val scheme=NamedPercentageScheme.valueOf(f[2])
            val tier=when(f[3]) {
                "t1" -> PercentageTier.PRIMARY
                "t2" -> PercentageTier.SECONDARY
                else -> error("Unknown tier")
            }
            val role=when(f[4]) {
                "standard" -> PercentageDayRole.STANDARD
                "tx_volume" -> PercentageDayRole.TEXAS_VOLUME
                "tx_recovery" -> PercentageDayRole.TEXAS_RECOVERY
                "tx_intensity" -> PercentageDayRole.TEXAS_INTENSITY
                else -> error("Unknown day")
            }
            val request=NamedPercentageRequest(scope,scheme,tier,role,
                NamedPercentageProtocolDomain.referenceKind(scheme),BigDecimal(f[8]),true,
                f[5].toInt(),f[6].toInt(),f[7].toInt(),stock)
            val result=NamedPercentageProtocolDomain.review(request)
            require(result is NamedPercentageResult.Candidate) { "${f[0]}: $result" }
            require(!result.mayIssueWorkout && !result.mayModifyHistory &&
                !result.maySaveProgram && !result.mayChangeReference)
            val repOut=result.sets.mapIndexedNotNull{i,s->if(s.repOut)i+1 else null}
            val fields=listOf(f[0],result.sets.joinToString(","){it.verifiedLoad.toPlainString()},
                result.sets.joinToString(","){it.minimumReps.toString()},
                repOut.joinToString(",").ifEmpty{"-"})
            fields.joinToString("|")
        }
        val output=Path.of(args[1]);Files.createDirectories(output.toAbsolutePath().parent)
        Files.writeString(output,results.joinToString("\n",postfix="\n"))
    }
}
