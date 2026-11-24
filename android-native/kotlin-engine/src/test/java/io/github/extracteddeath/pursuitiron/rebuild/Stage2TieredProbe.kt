package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

/** The external pinned-web runner independently checks numeric outputs. */
object Stage2TieredProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2)
        val scope=BasicProgressionScope("athlete","cycle","gym","block","day","press",
            "machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
        val stock=VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,
            BigDecimal("5"),BigDecimal("5"),BigDecimal("500"))
        val rows=Files.readAllLines(Path.of(args[0])).filter{it.isNotBlank()&&!it.startsWith("#")}
        val output=rows.map{line->
            val f=line.split('|')
            require(f.size==6)
            val tier=when(f[1]) {
                "t1" -> PercentageTier.PRIMARY
                "t2" -> PercentageTier.SECONDARY
                else -> error("Unknown tier")
            }
            val ledger=if(f[2]=="-")emptyList() else f[2].split(';').mapIndexed{i,entry->
                val e=entry.split(',')
                require(e.size==5)
                val stage=e[0].toInt()
                val weight=BigDecimal(e[1])
                val reps=e[2].toInt()
                val count=e[3].toInt()
                val kind=e[4]
                require(kind in setOf("normal","failed","skipped","interrupted"))
                val minimum=if(tier==PercentageTier.PRIMARY)listOf(3,2,1)[stage]
                    else listOf(10,8,6)[stage]
                TieredLinearExposure("workout-$i",(i+1).toLong(),scope,tier,stage,
                    List(count){j->TieredLinearSetTarget(j,weight,minimum,j==count-1)},
                    List(count){j->
                        if(j==count-1 && kind=="skipped")
                            WorkingSetFact(j,SetOutcome.SKIPPED,null,null,null,weight,2)
                        else if(j==count-1 && kind=="failed")
                            WorkingSetFact(j,SetOutcome.FAILED_ATTEMPT,weight,0,null,weight,2)
                        else WorkingSetFact(j,SetOutcome.COMPLETED,weight,reps,null,weight,2)
                    },kind!="interrupted")
            }
            val result=TieredLinearProgressionDomain.review(TieredLinearRequest(
                "next",scope,tier,TieredLinearState(0,BigDecimal("100")),true,ledger,stock,
                BigDecimal("5"),3))
            val state=when(result) {
                is TieredLinearResult.Refused -> "REFUSED:"+result.reason.name
                is TieredLinearResult.Candidate -> {
                    require(!result.mayIssueWorkout&&!result.maySaveProgram&&!result.mayModifyHistory)
                    result.state.stage.toString()+","+result.state.load.toPlainString()
                }
            }
            f[0]+"|"+state
        }
        val file=Path.of(args[1]);Files.createDirectories(file.toAbsolutePath().parent)
        Files.writeString(file,output.joinToString("\n",postfix="\n"))
    }
}
