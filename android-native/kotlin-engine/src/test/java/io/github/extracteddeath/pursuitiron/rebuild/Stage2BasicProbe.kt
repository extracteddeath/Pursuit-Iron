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
            if(style=="select" || style.startsWith("select:"))
                return@map selection(f)
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

    // Exercise the production saved-method selector on the same actual
    // catalog identity, role, rep band and coarse native phase as the web.
    // This is a source recommendation comparison, not a generated program
    // or approval grant; no new runtime policy is embedded in the probe.
    private fun selection(f:List<String>):String {
        val movement=StarterExerciseTranche.movements().single{it.exerciseId==f[2]}
        val phase=when(f[3]) {
            "hypertrophy_accumulation" -> TrainingPhase.HYPERTROPHY
            "strength_accumulation","intensification" -> TrainingPhase.STRENGTH
            "peak" -> TrainingPhase.PEAK
            else -> error("Unsupported comparison phase")
        }
        val experience=when(f[4]) {
            "novice" -> AthleteExperience.BEGINNER
            "intermediate" -> AthleteExperience.INTERMEDIATE
            "advanced" -> AthleteExperience.ADVANCED
            else -> error("Unsupported comparison experience")
        }
        val role=when {
            f[5].startsWith("primary_") -> ComposerRole.PRIMARY
            f[5].startsWith("secondary_") -> ComposerRole.SECONDARY
            f[5]=="accessory" -> ComposerRole.ACCESSORY
            else -> error("Unsupported comparison role")
        }
        val reps=f[6].split(',').map{it.toInt()}
        require(reps.size==2)
        val band=RepBand(reps[0],reps[1])
        val load=movement.expectedLoadBasis?.let{
            DraftSetLoad.Verified(LoadUnit.LB,it,BigDecimal("100"))
        } ?: DraftSetLoad.ExplicitBodyweight
        val set=CustomAuthoredSet(DraftSetType.WORKING,load,band,2,45,90,
            exactRepTarget=band.minimum)
        val slot=CustomExerciseSlot("slot",movement.exerciseId,role,listOf(set,set))
        val block=CustomTrainingBlock("block","Block",phase,CustomBlockPurpose.TRAINING,6,
            CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
            listOf(CustomTrainingDay(0,"Day",ComposerSessionPurpose.UNSPECIFIED,listOf(slot))))
        val cycle=CustomTrainingCycle("cycle",1,"athlete",AthleteTrainingGoal.POWERBUILDING,
            "Selection fixture",listOf(block))
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val selected=f[1].substringAfter(':',"").takeIf{it.isNotEmpty()}?.let{
            mapOf(key to NativeProgressionStyle.valueOf(it.uppercase()))
        }.orEmpty()
        val outcome=BasicProgramProgressionPlanDomain.review(cycle,
            selectedStyles=selected,reviewedCatalog=mapOf(movement.exerciseId to movement),
            experience=experience)
        val method=when(outcome) {
            is ProgramProgressionPlan.Refused -> "refused"
            is ProgramProgressionPlan.Ready -> {
                require(!outcome.mayIssueWorkout && !outcome.maySaveProgram)
                val assignment=outcome.assignments.single()
                require(assignment.key==key && assignment.exerciseId==movement.exerciseId)
                BasicProgramProgressionPlanDomain.savedChoice(assignment)?.name?.lowercase()
                    ?: assignment.route.name.lowercase()
            }
        }
        return f[0]+"|"+method+"|-"
    }
}
