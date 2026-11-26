package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class BasicProgressionPolicyDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val band=RepBand(6,10)
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf("90","95","100","105","110","115").map(::n))
    private val scope=BasicProgressionScope(
        athleteId="athlete",programId="program",gymId="gym-1",blockId="block-1",
        trainingDayId="Upper-1",exerciseId="machine-press",
        machineInstanceId="machine-A",unit=LoadUnit.LB,basis=LoadBasis.MACHINE_STACK)

    private fun workout(reps:List<Int>,loads:List<String>,
        issued:List<String> = loads,
        outcome:SetOutcome=SetOutcome.COMPLETED,
        unit:LoadUnit=LoadUnit.LB,
        trainingDayId:String?=scope.trainingDayId
    )=ProgressionWorkoutEvidence(
        workoutId="last-week",exerciseId=scope.exerciseId,
        unit=unit,targetReps=band,expectedWorkingSets=loads.size,
        trainingDayId=trainingDayId,
        observations=reps.mapIndexed{i,repsDone ->
            WorkingSetFact(
                workingOrdinal=i,outcome=outcome,
                actualLoad=if(outcome==SetOutcome.SKIPPED)null else n(loads[i]),
                actualReps=if(outcome==SetOutcome.COMPLETED)repsDone else
                    if(outcome==SetOutcome.FAILED_ATTEMPT)0 else null,
                actualRir=if(outcome==SetOutcome.COMPLETED)0 else null,
                issuedLoad=n(issued[i]),issuedRir=2)
        }
    )
    private fun request(
        strategy:BasicProgressionStrategy,
        reps:List<Int> = listOf(10,10,9),
        loads:List<String> = listOf("100","100","100"),
        issued:List<String> = loads,
        previous:ProgressionWorkoutEvidence?=workout(reps,loads,issued),
        nextScope:BasicProgressionScope=scope,
        previousScope:BasicProgressionScope?=scope,
        verifiedStock:VerifiedLoadStock?=stock,
        maximumIncrement:String="5"
    )=BasicProgressionRequest(
        strategy=strategy,nextWorkoutId="next-week",
        nextScope=nextScope,previousScope=previousScope,previous=previous,
        repBand=band,issuedWorkingLoads=loads.map(::n),
        verifiedStock=verifiedStock,maximumAuthorizedIncrement=n(maximumIncrement))

    private fun candidate(request:BasicProgressionRequest)=
        BasicProgressionPolicyDomain.review(request) as BasicProgressionResult.Candidate

    @Test fun basicProgressionNeverEarnsFromMachineTaggedFreeBarButManualRepeatRemainsNonwriting() {
        val freeBar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),mapOf(
            n("25") to 1,n("5") to 1,n("2.5") to 1))
        val correct=scope.copy(basis=LoadBasis.BARBELL_TOTAL,machineInstanceId=null)
        val invalid=correct.copy(machineInstanceId="machine-A")
        for(mode in listOf(BasicProgressionStrategy.MANUAL_LOGGING,
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION)) {
            val base=request(mode,reps=listOf(10,10,10),
                nextScope=correct,previousScope=correct,verifiedStock=freeBar)
            val good=candidate(base)
            assertEquals(List(3){n(if(mode==BasicProgressionStrategy.MANUAL_LOGGING)
                "100" else "105")},good.proposedWorkingLoads)
            val bad=base.copy(nextScope=invalid,previousScope=invalid)
            if(mode==BasicProgressionStrategy.MANUAL_LOGGING) {
                val manual=candidate(bad.copy(previous=null,previousScope=null,verifiedStock=null))
                assertEquals(List(3){n("100")},manual.proposedWorkingLoads)
                assertEquals(BasicProgressionDecision.MANUAL_REPEAT,manual.decision)
                assertTrue(manual.increasedOrdinals.isEmpty())
                assertFalse(manual.maySaveFutureProgram)
                assertFalse(manual.mayModifyIssuedWorkout)
            } else assertEquals(BasicProgressionResult.Refused(BasicProgressionRefusal.INVALID_SOURCE_OR_LOAD),
                BasicProgressionPolicyDomain.review(bad))
        }
    }

    @Test fun repeatedPhysicalLoadReviewDoesNotRebuildPairedStockForEveryOrdinal() {
        val backing=linkedMapOf(n("25") to 1,n("5") to 1,n("2.5") to 1)
        var enumerations=0
        val counting=object:Map<BigDecimal,Int> by backing {
            override val entries:Set<Map.Entry<BigDecimal,Int>>
                get() { enumerations++;return backing.entries }
        }
        val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("45"),counting)
        val scope=scope.copy(basis=LoadBasis.BARBELL_TOTAL,machineInstanceId=null)
        val uniform=request(BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            reps=List(4){10},loads=listOf("100","100.0","100.00","100.000"),
            nextScope=scope,previousScope=scope,verifiedStock=bar)
        val earned=candidate(uniform)
        assertEquals(List(4){n("105")},earned.proposedWorkingLoads)
        assertEquals(setOf(0,1,2,3),earned.increasedOrdinals)
        assertEquals(earned.increasedOrdinals,earned.approvedFutureLoadFacts.keys)
        assertEquals(1,earned.approvedFutureLoadFacts.values.toSet().size)
        // One initial inspection and two independent authority inspections.
        // This counts real stock enumerations, not elapsed CI time.
        assertEquals(3,enumerations)
        enumerations=0
        val partial=candidate(uniform.copy(strategy=BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            previous=workout(listOf(10,9,10,9),listOf("100","100.0","100.00","100.000"))))
        assertEquals(listOf(n("105"),n("100.0"),n("105"),n("100.000")),
            partial.proposedWorkingLoads)
        assertEquals(setOf(0,2),partial.increasedOrdinals)
        assertEquals(setOf(0,2),partial.approvedFutureLoadFacts.keys)
        assertEquals(3,enumerations)
        // No stale cross-request cache. Changed inventory cannot approve a
        // formerly attainable load or inherit its previously earned step.
        backing.remove(n("2.5"))
        assertEquals(BasicProgressionResult.Refused(BasicProgressionRefusal.STOCK_NOT_VERIFIED),
            BasicProgressionPolicyDomain.review(uniform))
        assertEquals(List(4){n("105")},earned.proposedWorkingLoads)
    }

    @Test fun manualLoggingKeepsApprovedNumbersWithoutHistoryStockOrCoaching() {
        val r=candidate(request(BasicProgressionStrategy.MANUAL_LOGGING,
            previous=null,previousScope=null,verifiedStock=null,
            loads=listOf("100","105","100")))
        assertEquals(listOf(n("100"),n("105"),n("100")),r.proposedWorkingLoads)
        assertEquals(emptySet<Int>(),r.increasedOrdinals)
        assertEquals(BasicProgressionDecision.MANUAL_REPEAT,r.decision)
        assertNull(r.evidenceWorkoutId)
        assertFalse(r.mayModifyLoggedSets)
        assertFalse(r.mayModifyIssuedWorkout)
        assertFalse(r.maySaveFutureProgram)
    }

    @Test fun reported200To215NearFailureMustNeverPromoteEverySetTo215() {
        val target=RepBand(5,8)
        val weights=listOf("200","205","210","215")
        val misses=listOf(3,3,2,2)
        val available=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
            (195..225 step 5).map{n(it.toString())})
        fun case(mode:BasicProgressionStrategy,loads:List<String>,reps:List<Int>,
            issued:List<String> = loads)=request(mode,reps,loads,issued,
                previous=workout(reps,loads,issued).copy(targetReps=target),
                verifiedStock=available).copy(repBand=target)
        val strict=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
        val dynamic=BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
        // A mixed baseline cannot masquerade as strict straight-set DP.
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.UNSUPPORTED_STRAIGHT_SET_SCHEME),
            BasicProgressionPolicyDomain.review(case(strict,weights,misses)))
        val held=candidate(case(dynamic,weights,misses))
        assertEquals(weights.map(::n),held.proposedWorkingLoads)
        assertTrue(held.increasedOrdinals.isEmpty())
        assertTrue(held.dynamicSetDecisions.all{it==BasicProgressionDecision.BUILD_REPS})
        assertFalse(held.maySaveFutureProgram)
        // Uniform 215 was the earlier bad suggestion, not a justified
        // new baseline; the actual corrected facts must fence it out.
        val imposed=List(4){"215"}
        for(mode in listOf(strict,dynamic))
            assertEquals(BasicProgressionResult.Refused(
                BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY),
                BasicProgressionPolicyDomain.review(case(mode,weights,misses,imposed)))
        val repeat=candidate(case(strict,imposed,List(4){2}))
        assertEquals(imposed.map(::n),repeat.proposedWorkingLoads)
        assertEquals(BasicProgressionDecision.BUILD_REPS,repeat.decision)
        assertTrue(repeat.increasedOrdinals.isEmpty())
        val partial=candidate(case(dynamic,weights,listOf(8,3,2,2)))
        assertEquals(listOf("205","205","210","215").map(::n),partial.proposedWorkingLoads)
        assertEquals(setOf(0),partial.increasedOrdinals)
        val manual=candidate(case(BasicProgressionStrategy.MANUAL_LOGGING,weights,misses)
            .copy(previous=null,previousScope=null,verifiedStock=null))
        assertEquals(weights.map(::n),manual.proposedWorkingLoads)
        assertEquals(BasicProgressionDecision.MANUAL_REPEAT,manual.decision)
        assertFalse(manual.mayModifyIssuedWorkout)
    }

    @Test fun strictDoubleNeedsAllWorkingSetsAtCeilingBeforeAllAdvance() {
        val mode=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
        val hold=candidate(request(mode))
        assertEquals(listOf(n("100"),n("100"),n("100")),hold.proposedWorkingLoads)
        assertEquals(emptySet<Int>(),hold.increasedOrdinals)
        assertEquals(BasicProgressionDecision.BUILD_REPS,hold.decision)
        val advance=candidate(request(mode,reps=listOf(10,10,10)))
        assertEquals(listOf(n("105"),n("105"),n("105")),advance.proposedWorkingLoads)
        assertEquals(setOf(0,1,2),advance.increasedOrdinals)
        assertEquals("last-week",advance.evidenceWorkoutId)
    }

    @Test fun explicitE1rmUsesTheSamePhysicalStepOnlyAtCompleteTopRangeAndGoalEffort() {
        val mode=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
        val base=request(mode,reps=listOf(10,10,10)).copy(effortAdjustedE1rm=true)
        val prior=checkNotNull(base.previous)
        fun review(reserves:List<Int?>,reps:List<Int> = listOf(10,10,10))=
            candidate(base.copy(previous=prior.copy(observations=prior.observations.mapIndexed{i,fact->
                fact.copy(actualRir=reserves[i],actualReps=reps[i])})))
        val earned=review(listOf(2,null,2))
        assertEquals(List(3){n("105")},earned.proposedWorkingLoads)
        assertEquals(setOf(0,1,2),earned.increasedOrdinals)
        assertEquals(BasicE1rmEvidence(n("140.0"),1,true),earned.e1rmEvidence)
        assertEquals(setOf(0,1,2),earned.approvedFutureLoadFacts.keys)
        val effortMiss=review(listOf(2,0,2))
        assertEquals(List(3){n("100")},effortMiss.proposedWorkingLoads)
        assertEquals(BasicE1rmEvidence(n("140.0"),0,false),effortMiss.e1rmEvidence)
        assertTrue(effortMiss.approvedFutureLoadFacts.isEmpty())
        val repMiss=review(listOf(2,2,2),listOf(10,9,10))
        assertEquals(List(3){n("100")},repMiss.proposedWorkingLoads)
        assertTrue(repMiss.increasedOrdinals.isEmpty())
        // Coaching-off strict DP stays a simple all-set ceiling rule.
        val ordinary=candidate(base.copy(effortAdjustedE1rm=false))
        assertEquals(List(3){n("105")},ordinary.proposedWorkingLoads)
        assertNull(ordinary.e1rmEvidence)
        val missingGoal=base.copy(previous=prior.copy(observations=prior.observations.map{
            it.copy(issuedRir=null,actualRir=null)}))
        assertEquals(BasicProgressionResult.Refused(BasicProgressionRefusal.HISTORY_NOT_COMPARABLE),
            BasicProgressionPolicyDomain.review(missingGoal))
        val noisy=base.copy(previous=prior.copy(observations=prior.observations.map{
            it.copy(actualReps=15,actualRir=2)}))
        assertEquals(BasicProgressionResult.Refused(BasicProgressionRefusal.HISTORY_NOT_COMPARABLE),
            BasicProgressionPolicyDomain.review(noisy))
        assertEquals(BasicProgressionResult.Refused(BasicProgressionRefusal.INVALID_SOURCE_OR_LOAD),
            BasicProgressionPolicyDomain.review(base.copy(strategy=BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION)))
        assertFalse(earned.maySaveFutureProgram)
        assertFalse(earned.mayModifyIssuedWorkout)
    }

    @Test fun dynamicOnlyIncreasesSetPositionsThatHitTheirOwnCeiling() {
        val result=candidate(request(BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            reps=listOf(10,9,10)))
        assertEquals(listOf(n("105"),n("100"),n("105")),result.proposedWorkingLoads)
        assertEquals(setOf(0,2),result.increasedOrdinals)
        assertEquals(BasicProgressionDecision.EARNED_INCREMENT,result.decision)
        assertFalse(result.maySaveFutureProgram)
    }

    @Test fun dynamicPreviewKeepsEachSetsReasonWhenEquipmentAndRepLimitsDiffer() {
        val available = VerifiedLoadStock.Totals(LoadUnit.LB, LoadBasis.MACHINE_STACK,
            listOf("95", "100", "105", "115").map(::n))
        val result = candidate(request(BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            reps=listOf(10, 9, 10, 10), loads=listOf("95", "100", "105", "115"),
            verifiedStock=available))
        assertEquals(listOf("100", "100", "105", "115").map(::n), result.proposedWorkingLoads)
        assertEquals(setOf(0), result.increasedOrdinals)
        assertEquals(listOf(BasicProgressionDecision.EARNED_INCREMENT,
            BasicProgressionDecision.BUILD_REPS, BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL,
            BasicProgressionDecision.MAXIMUM_LOAD_REACHED), result.dynamicSetDecisions)
        val hold = candidate(request(BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            reps=listOf(6, 8, 9)))
        assertEquals(List(3) { BasicProgressionDecision.BUILD_REPS }, hold.dynamicSetDecisions)
    }

    @Test fun mixedVerifiedWorkingLoadsCanProgressIndividuallyButNotAsStrictStraightSets() {
        val weights=listOf("100","95","90")
        val dynamic=candidate(request(BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            reps=listOf(10,9,10),loads=weights))
        assertEquals(listOf(n("105"),n("95"),n("95")),dynamic.proposedWorkingLoads)
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.UNSUPPORTED_STRAIGHT_SET_SCHEME),
            BasicProgressionPolicyDomain.review(request(
                BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
                reps=listOf(10,10,10),loads=weights)))
    }

    @Test fun unapprovedOrManuallyLoweredLoadsNeverBecomeSilentFutureDrops() {
        val prior=workout(listOf(10,10,10),
            listOf("100","100","100"),issued=listOf("105","105","105"))
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY),
            BasicProgressionPolicyDomain.review(request(
                BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
                reps=listOf(10,10,10),previous=prior)))
        val differentBase=request(BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            reps=listOf(10,10,10),loads=listOf("105","105","105"),
            previous=workout(listOf(10,10,10),
                listOf("100","100","100")))
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY),
            BasicProgressionPolicyDomain.review(differentBase))
    }

    @Test fun physicalStationDayAndBlockNeverBorrowAnotherProgressionThread() {
        val changes=listOf(
            scope.copy(machineInstanceId="machine-B"),
            scope.copy(trainingDayId="Push-2"),
            scope.copy(blockId="block-2"),
            scope.copy(athleteId="other"),
            scope.copy(programId="other"),
            scope.copy(gymId="gym-2"),
            scope.copy(exerciseId="incline-press"))
        for(other in changes) {
            assertEquals(other.toString(),BasicProgressionResult.Refused(
                BasicProgressionRefusal.HISTORY_NOT_COMPARABLE),
                BasicProgressionPolicyDomain.review(request(
                    BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
                    reps=listOf(10,10,10),nextScope=other)))
        }
        val untrustedDay=workout(listOf(10,10,10),
            listOf("100","100","100"),trainingDayId="Push-2")
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.HISTORY_NOT_COMPARABLE),
            BasicProgressionPolicyDomain.review(request(
                BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
                previous=untrustedDay)))
    }

    @Test fun failedSkippedMissingOrDuplicateSetCannotQualifyForAnIncrease() {
        val mode=BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
        for(outcome in listOf(SetOutcome.SKIPPED,SetOutcome.FAILED_ATTEMPT)) {
            val previous=workout(listOf(10,10,10),
                listOf("100","100","100"),outcome=outcome)
            assertEquals(BasicProgressionResult.Refused(
                BasicProgressionRefusal.INCOMPLETE_OR_UNVERIFIED_SETS),
                BasicProgressionPolicyDomain.review(request(
                    mode,previous=previous)))
        }
        val full=workout(listOf(10,10,10),
            listOf("100","100","100"))
        for(edited in listOf(
            full.copy(observations=full.observations.dropLast(1)),
            full.copy(observations=full.observations.map {
                if(it.workingOrdinal==2)it.copy(workingOrdinal=1) else it
            }),
            full.copy(observations=full.observations.map {
                if(it.workingOrdinal==1)it.copy(issuedLoad=null) else it
            }))) {
            assertEquals(BasicProgressionResult.Refused(
                BasicProgressionRefusal.INCOMPLETE_OR_UNVERIFIED_SETS),
                BasicProgressionPolicyDomain.review(request(mode,previous=edited)))
        }
    }

    @Test fun allLoadIncreasesUseVerifiedAdjacentStockAndAuthorizedLimit() {
        val mode=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.STOCK_NOT_VERIFIED),
            BasicProgressionPolicyDomain.review(request(
                mode,reps=listOf(10,10,10),verifiedStock=null)))
        val stockTooLow=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
            listOf(n("95"),n("105")))
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.STOCK_NOT_VERIFIED),
            BasicProgressionPolicyDomain.review(request(
                mode,reps=listOf(10,10,10),verifiedStock=stockTooLow)))
        val tooSmall=candidate(request(
            mode,reps=listOf(10,10,10),maximumIncrement="2.5"))
        assertEquals(BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL,
            tooSmall.decision)
        assertEquals(listOf(n("100"),n("100"),n("100")),
            tooSmall.proposedWorkingLoads)
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.INVALID_SOURCE_OR_LOAD),
            BasicProgressionPolicyDomain.review(request(mode,maximumIncrement="0")))
    }

    @Test fun maximumStockAndAssistedWeightsNeverInventFurtherLoadIncreases() {
        val mode=BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
        val atTop=candidate(request(mode,reps=listOf(10,10,10),
            loads=listOf("115","100","115")))
        assertEquals(listOf(n("115"),n("105"),n("115")),atTop.proposedWorkingLoads)
        assertEquals(setOf(1),atTop.increasedOrdinals)
        val assistedScope=scope.copy(basis=LoadBasis.ASSISTANCE_COUNTERWEIGHT)
        assertEquals(BasicProgressionResult.Refused(
            BasicProgressionRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION),
            BasicProgressionPolicyDomain.review(request(mode,
                nextScope=assistedScope,previousScope=assistedScope,
                reps=listOf(10,10,10))))
    }

    @Test fun explicitRirZeroDoesNotInvokeCoachingForBasicProgression() {
        val past=workout(listOf(10,10,10),listOf("100","100","100"))
        assertTrue(past.observations.all{it.actualRir==0})
        assertEquals(setOf(0,1,2),candidate(request(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            previous=past)).increasedOrdinals)
    }

    @Test fun exhaustiveThreeSetCeilingMatrixHasNoAccidentalOrHiddenChanges() {
        val strict=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
        val dynamic=BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION
        for(a in 6..11) for(b in 6..11) for(c in 6..11) {
            val achieved=listOf(a,b,c)
            val x=candidate(request(strict,reps=achieved))
            val y=candidate(request(dynamic,reps=achieved))
            val allEarned=achieved.all{it>=10}
            assertEquals(achieved.toString(),
                if(allEarned)listOf(n("105"),n("105"),n("105"))
                else listOf(n("100"),n("100"),n("100")),
                x.proposedWorkingLoads)
            assertEquals(achieved.toString(),achieved.map{
                if(it>=10)n("105") else n("100")
            },y.proposedWorkingLoads)
            assertEquals(achieved.toString(),
                achieved.indices.filter{achieved[it]>=10}.toSet(),
                y.increasedOrdinals)
        }
    }


    @Test fun basicProgressionCarriesExactApprovedPhysicalEvidencePerChangedSet() {
        val result=candidate(request(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            reps=listOf(10,9,10)))
        assertEquals(scope,result.sourceScope)
        assertEquals(result.increasedOrdinals,result.approvedFutureLoadFacts.keys)
        assertEquals(setOf(0,2),result.approvedFutureLoadFacts.keys)
        for((ordinal,fact) in result.approvedFutureLoadFacts) {
            assertEquals(n("100"),fact.issuedLoad)
            assertEquals(result.proposedWorkingLoads[ordinal],fact.proposedLoad)
            assertEquals(n("105"),fact.physicalProof.nextHigher)
            assertEquals(n("95"),fact.physicalProof.nextLower)
            assertEquals(n("5"),fact.physicalProof.absoluteChange)
            assertEquals(KotlinFutureLoadPhysicalReason.EARNED_ADJACENT_HIGHER_SETTING,
                fact.physicalProof.reason)
            assertFalse(fact.maySaveFutureProgram)
        }
        assertEquals(n("100"),result.proposedWorkingLoads[1])
        assertNull(result.approvedFutureLoadFacts[1])
    }

    @Test fun heldAtomicAndManualModesNeverLeakSpeculativePhysicalApproval() {
        val strict=BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION
        val heldByReps=candidate(request(strict,reps=listOf(10,10,9)))
        assertTrue(heldByReps.approvedFutureLoadFacts.isEmpty())
        val heldByLimit=candidate(request(strict,
            reps=listOf(10,10,10),maximumIncrement="2.5"))
        assertTrue(heldByLimit.approvedFutureLoadFacts.isEmpty())
        assertEquals(listOf(n("100"),n("100"),n("100")),
            heldByLimit.proposedWorkingLoads)
        val manual=candidate(request(BasicProgressionStrategy.MANUAL_LOGGING,
            previous=null,previousScope=null,verifiedStock=null,
            loads=listOf("100","105","100")))
        assertTrue(manual.approvedFutureLoadFacts.isEmpty())
        assertEquals(scope,manual.sourceScope)
        val eligible=candidate(request(strict,reps=listOf(10,10,10)))
        assertEquals(setOf(0,1,2),eligible.approvedFutureLoadFacts.keys)
    }

    @Test fun dynamicReviewDoesNotPublishUnverifiedLimitsOrMixedSetDecisions() {
        val available=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
            listOf("95","100","105","115").map(::n))
        val result=candidate(request(
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION,
            reps=listOf(10,9,10,10),loads=listOf("95","100","105","115"),
            verifiedStock=available))
        assertEquals(setOf(0),result.approvedFutureLoadFacts.keys)
        val accepted=checkNotNull(result.approvedFutureLoadFacts[0])
        assertEquals(n("95"),accepted.physicalProof.verifiedIssued)
        assertEquals(n("100"),accepted.physicalProof.verifiedCandidate)
        assertEquals(n("100"),accepted.physicalProof.nextHigher)
        assertEquals(n("5"),accepted.physicalProof.absoluteChange)
        assertNull(result.approvedFutureLoadFacts[2])
        assertNull(result.approvedFutureLoadFacts[3])
    }
}
