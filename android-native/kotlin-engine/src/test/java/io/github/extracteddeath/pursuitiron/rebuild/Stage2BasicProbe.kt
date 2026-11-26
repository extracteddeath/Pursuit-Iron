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
        val rows=Files.readAllLines(Path.of(args[0])).filter{it.isNotBlank()&&!it.startsWith("#")}
        val output=rows.map{line->
            val f=line.split('|')
            require(f.size==10)
            val style=f[1]
            if(style.substringBefore(':') in setOf("select","select-v13","select-saved-auto","select-week"))
                return@map selection(f)
            val waveGate=style=="wave-gate"
            val band=if(waveGate)RepBand(3,5) else RepBand(5,8)
            val mode=when(style) {
                "double","e1rm","wave-gate" -> BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
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
                effortAdjustedE1rm=style=="e1rm",requireGoalEffort=waveGate)) as BasicProgressionResult.Candidate
            require(!result.mayModifyIssuedWorkout&&!result.mayModifyLoggedSets&&!result.maySaveFutureProgram)
            require(result.approvedFutureLoadFacts.keys==result.increasedOrdinals)
            require(history.observations==facts)
            val weights=result.proposedWorkingLoads.joinToString(","){it.stripTrailingZeros().toPlainString()}
            // A Wave gate probe compares only the shared weight decision, not a
            // fabricated native Wave method or an estimate that Wave does not use.
            if(waveGate)require(result.e1rmEvidence==null && result.waveGoalEffortMet!=null)
            val estimate=if(waveGate)"-" else
                result.e1rmEvidence?.estimatedOneRepMax?.stripTrailingZeros()?.toPlainString()?:"-"
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
        val weekly=f[1].startsWith("select-week:")
        val week=if(weekly)f[1].split(':')[1].toInt() else null
        val weeks=if(weekly)f[1].split(':')[2].toInt() else 6
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
        val slot=CustomExerciseSlot("slot",movement.exerciseId,role,listOf(set,set),
            progressionMethod=if(weekly || f[1]=="select-saved-auto")SavedProgressionMethod.AUTO else null)
        val block=CustomTrainingBlock("block","Block",phase,CustomBlockPurpose.TRAINING,weeks,
            CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
            listOf(CustomTrainingDay(0,"Day",if(weekly)ComposerSessionPurpose.STRENGTH_SKILL
                else ComposerSessionPurpose.UNSPECIFIED,listOf(slot))),
            strengthIntensification=weekly && f[3]=="intensification")
        val cycle=CustomTrainingCycle("cycle",1,"athlete",AthleteTrainingGoal.POWERBUILDING,
            "Selection fixture",listOf(block))
        val key=ProgramProgressionSlotKey("block",0,"slot")
        val selected=f[1].substringAfter(':',"").takeIf{!weekly && it.isNotEmpty()}?.let{
            mapOf(key to NativeProgressionStyle.valueOf(it.uppercase()))
        }.orEmpty()
        val outcome=BasicProgramProgressionPlanDomain.review(cycle,
            selectedStyles=selected,reviewedCatalog=mapOf(movement.exerciseId to movement),
            experience=experience,reviewedAuto=f[1].substringBefore(':')=="select-v13",
            previewWeek=week?.let{ProgramProgressionWeek("block",it)})
        val method=when(outcome) {
            is ProgramProgressionPlan.Refused -> "refused"
            is ProgramProgressionPlan.Ready -> {
                require(!outcome.mayIssueWorkout && !outcome.maySaveProgram)
                val assignment=outcome.assignments.single()
                require(assignment.key==key && assignment.exerciseId==movement.exerciseId)
                if(weekly && assignment.selectedStyle==NativeProgressionStyle.WAVE) {
                    require(BasicProgramProgressionPlanDomain.savedChoice(assignment)==SavedProgressionMethod.AUTO)
                    "wave"
                } else BasicProgramProgressionPlanDomain.savedChoice(assignment)?.name?.lowercase()
                    ?: assignment.route.name.lowercase()
            }
        }
        return f[0]+"|"+method+"|-"
    }
}
