package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path

/** Execute real basic/e1RM numerical authority, not a copy of its arithmetic. */
object Stage2BasicProbe {
    @JvmStatic fun main(args:Array<String>) {
        require(args.size==2)
        val scope=BasicProgressionScope("athlete","cycle","gym","block","day",
            "machine_press","machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
        val band=RepBand(5,8)
        val rows=Files.readAllLines(Path.of(args[0])).filter{it.isNotBlank()&&!it.startsWith("#")}
        val output=rows.map{line->
            val f=line.split('|')
            require(f.size==10)
            val style=f[1]
            val mode=when(style) {
                "double","e1rm" -> BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
                "dynamic" -> BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
                "linear" -> BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION
                else -> error("Unknown strategy")
            }
            val loads=f[2].split(',').map{BigDecimal(it)}
            val reps=f[3].split(',').map{it.toInt()}
            val rir=f[4].split(',').map{it.takeUnless{v->v=="null"}?.toInt()}
            require(loads.size==reps.size && reps.size==rir.size)
            val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
                f[5].split(',').map{BigDecimal(it)})
            val facts=loads.indices.map{i->
                WorkingSetFact(i,SetOutcome.COMPLETED,loads[i],reps[i],rir[i],loads[i],2)
            }
            val history=ProgressionWorkoutEvidence("completed",scope.exerciseId,LoadUnit.LB,
                band,loads.size,facts,scope.trainingDayId)
            val result=BasicProgressionPolicyDomain.review(BasicProgressionRequest(
                mode,"next",scope,scope,history,band,loads,stock,BigDecimal("5"),
                effortAdjustedE1rm=style=="e1rm")) as BasicProgressionResult.Candidate
            require(!result.mayModifyIssuedWorkout&&!result.mayModifyLoggedSets&&!result.maySaveFutureProgram)
            require(result.approvedFutureLoadFacts.keys==result.increasedOrdinals)
            require(history.observations==facts)
            val weights=result.proposedWorkingLoads.joinToString(","){it.stripTrailingZeros().toPlainString()}
            val estimate=result.e1rmEvidence?.estimatedOneRepMax?.stripTrailingZeros()?.toPlainString()?:"-"
            f[0]+"|"+weights+"|"+estimate
        }
        val file=Path.of(args[1]);Files.createDirectories(file.toAbsolutePath().parent)
        Files.writeString(file,output.joinToString("\n",postfix="\n"))
    }
}
