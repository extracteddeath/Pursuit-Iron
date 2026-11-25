package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Real files and owner locks, not a fabricated UI "approved" flag. */
class NativeMeasuredWorkoutConsentFileStoreTest {
    @get:Rule val temp=TemporaryFolder()
    private val root by lazy{temp.root.toPath()}
    private val fence by lazy{NativeKotlinApprovalRevisionFence(root)}
    private var signed:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{signed}
    private val sources by lazy{
        NativeKotlinCustomAuthoringSourceFileStore(root,fence,identity)}
    private val history by lazy{NativeKotlinHistoryManifestFileStore(root)}
    private val composer by lazy{KotlinNativeJournalFileStore(root)}
    private val drafts by lazy{NativeCustomCycleDraftFileStore(root,fence,identity)}
    private val signoffs by lazy{
        NativeKotlinCustomCycleSignoffFileStore(root,fence,identity,drafts,sources)}
    private val preflight by lazy{
        NativeKotlinCustomWorkoutPreflight(fence,identity,drafts,sources,signoffs)}
    private val prepared by lazy{
        NativeKotlinCustomWorkoutPreparedStore(root,fence,identity,preflight)}
    private val custom by lazy{
        NativeKotlinCustomWorkoutJournalFileStore(root,fence,identity,
            prepared,preflight,history,composer)}
    private fun approvals()=NativeMeasuredWorkoutConsentFileStore(
        root,fence,identity,sources,history,composer,custom)
    private fun parent()=NativeMeasuredWorkoutParentFileStore(
        root,fence,identity,approvals(),history,composer,custom)
    private fun source(version:Long=1,withBands:Boolean=false,
        withMeasuredLoads:Boolean=false):NativeCustomAuthoringSnapshot {
        val setup=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-1",athleteProfileId="athlete-1",
            goal=AthleteTrainingGoal.HYPERTROPHY,
            experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,
            trainingWeekdays=setOf(0,2,4),timezoneId="America/New_York",
            sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,
                if(withMeasuredLoads)mapOf(
                    "dumbbell-rack" to VerifiedLoadStock.Totals(
                        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                        listOf("20","25","30").map {java.math.BigDecimal(it)}),
                    "single-weight-plate" to VerifiedLoadStock.Totals(
                        LoadUnit.LB,LoadBasis.EXERCISE_OVERRIDE,
                        listOf("10","20","25").map {java.math.BigDecimal(it)}))
                else emptyMap(),
                setOf("pullup-bar","wall-support")+
                    (if(withBands)setOf("bands","verified-band-pushup-setup")
                        else emptySet())+
                    (if(withMeasuredLoads)setOf("dumbbells","pinchable-plates",
                        "secure-plank-load-placement") else emptySet()),
                if(withBands)setOf("band-blue","rope-attachment") else emptySet()),
            allowBodyweight=true,allowSupersets=false,
            allowTricepsOnLower=false,maxBarbellExercisesPerDay=0,
            focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),
            hardTrainingDays=emptySet(),longTrainingDays=emptySet(),
            declaredRestDays=setOf(1,3,5,6)))
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup.status)
        return NativeCustomAuthoringSnapshot("owner-1","athlete-1",version,
            setup,StarterExerciseTranche.movements())
    }
    private fun enroll() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
    }
    private fun plan(id:String="plank")=MeasuredWorkoutPlan("session-1",
        "gym-1",1,listOf(MeasuredExerciseSetPlan("session-1","set-1",
            id,"gym-1",1,"floor",35,setOf(MeasuredSide.BOTH),
            MeasuredExerciseCatalogDomain.find(id)?.requiredFixtures.orEmpty())))

    @Test fun previewRequiresSourceAndHistoryAndCannotIssue() {
        val before=approvals().preview("owner-1",plan(),1)
        assertEquals(NativeMeasuredConsentRefusal.SOURCE_UNAVAILABLE,
            (before as NativeMeasuredConsentResult.Rejected).reason)
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source()))
        val noHistory=approvals().preview("owner-1",plan(),1)
        assertEquals(NativeMeasuredConsentRefusal.HISTORY_NOT_READY,
            (noHistory as NativeMeasuredConsentResult.Rejected).reason)
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
        val candidate=approvals().preview("owner-1",plan(),1)
        assertTrue(candidate.toString(),candidate is NativeMeasuredConsentResult.Preview)
        assertFalse((candidate as NativeMeasuredConsentResult.Preview).mayIssue)
        assertEquals(NativeMeasuredConsentRefusal.CONFIRMATION_REQUIRED,
            (approvals().confirm("owner-1",plan(),1,false)
                as NativeMeasuredConsentResult.Rejected).reason)
    }

    @Test fun consentColdIssueLogAndCanonicalFinishPreserveSeconds() {
        enroll()
        val p=plan()
        val approved=approvals().confirm("owner-1",p,1,true)
        assertTrue(approved.toString(),approved is NativeMeasuredConsentResult.Approved)
        val prior=(approved as NativeMeasuredConsentResult.Approved).approval
        assertEquals(prior,approvals().currentApprovedWorkout(
            "owner-1","athlete-1","session-1"))
        assertTrue((approvals().confirm("owner-1",p,1,true)
            as NativeMeasuredConsentResult.Approved).replayed)
        assertTrue(parent().issue("owner-1","session-1")
            is NativeMeasuredWorkoutStoreResult.Durable)
        val actual=MeasuredExerciseActual("floor",
            listOf(MeasuredSideResult(MeasuredSide.BOTH,43)))
        val recorded=parent().record("owner-1","session-1",
            MeasuredWorkoutOperation.Record("record-1",0,"set-1",
                MeasuredSetOutcome.COMPLETED,actual))
        assertTrue(recorded.toString(),recorded is NativeMeasuredWorkoutStoreResult.Durable)
        val done=NativeKotlinMeasuredHistoryTransaction(
            history,parent(),fence,identity).complete("owner-1","athlete-1",
            "session-1",MeasuredWorkoutOperation.Finish("finish-1",1))
        assertTrue(done.toString(),done is NativeMeasuredFinishResult.Committed)
        val read=NativeKotlinMeasuredHistoryReadCoordinator(
            history,parent(),fence,identity).read("owner-1","athlete-1")
        assertTrue(read.toString(),read is NativeMeasuredHistoryReadResult.Review)
        val stored=(read as NativeMeasuredHistoryReadResult.Review)
            .history.verifiedMeasured.single().stored
        assertEquals(actual,(stored.journal.bySetKey.getValue("set-1")
            .effectiveRecord?.operation as MeasuredJournalOperation.Record).actual)
        assertEquals("finish-1",stored.journal.finishedByEventId)
    }

    @Test fun unissuedConsentInvalidatedByIdentityAndChangedGymSource() {
        enroll()
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Approved)
        signed=NativeKotlinOwnerIdentity("owner-1","other-athlete")
        assertNull(approvals().currentApprovedWorkout(
            "owner-1","athlete-1","session-1"))
        signed=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertNull(approvals().currentApprovedWorkout(
            "owner-1","athlete-1","session-1"))
        assertEquals(NativeMeasuredConsentRefusal.SOURCE_CHANGED,
            (approvals().preview("owner-1",plan(),1)
                as NativeMeasuredConsentResult.Rejected).reason)
        assertEquals(NativeMeasuredWorkoutStoreRefusal.APPROVAL_UNAVAILABLE,
            (parent().issue("owner-1","session-1")
                as NativeMeasuredWorkoutStoreResult.Rejected).reason)
    }

    @Test fun physicalFixtureAndCorruptConsentMustFailClosed() {
        enroll()
        val fake=plan("dead-hang").copy(sets=listOf(
            plan("dead-hang").sets.single().copy(confirmedFixtures=emptySet())))
        assertEquals(NativeMeasuredConsentRefusal.UNVERIFIED_GYM_OR_EXERCISE,
            (approvals().preview("owner-1",fake,1)
                as NativeMeasuredConsentResult.Rejected).reason)
        val honest=plan("dead-hang").copy(sets=listOf(
            plan("dead-hang").sets.single().copy(
                physicalSetupId="pullup-bar")))
        assertTrue(approvals().preview("owner-1",honest,1)
            is NativeMeasuredConsentResult.Preview)
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Approved)
        val file=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("measured-consent-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val bytes=Files.readAllBytes(file)
        bytes[20]=(bytes[20].toInt() xor 1).toByte()
        Files.write(file,bytes)
        assertNull(approvals().currentApprovedWorkout(
            "owner-1","athlete-1","session-1"))
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Rejected)
        assertArrayEquals(bytes,Files.readAllBytes(file))
    }

    @Test fun coldInventoryResumesOnlyUnfinishedCanonicalParents() {
        enroll()
        val first=plan()
        val second=first.copy(workoutId="session-2",
            sets=first.sets.map{it.copy(workoutId="session-2")})
        for(p in listOf(first,second)) {
            assertTrue(approvals().confirm("owner-1",p,1,true)
                is NativeMeasuredConsentResult.Approved)
            assertTrue(parent().issue("owner-1",p.workoutId)
                is NativeMeasuredWorkoutStoreResult.Durable)
        }
        val recorded=parent().record("owner-1","session-1",
            MeasuredWorkoutOperation.Record("record-1",0,"set-1",
                MeasuredSetOutcome.COMPLETED,
                MeasuredExerciseActual("floor",
                    listOf(MeasuredSideResult(MeasuredSide.BOTH,47)))))
        assertTrue(recorded is NativeMeasuredWorkoutStoreResult.Durable)

        // Independent instances simulate process death; no Compose state,
        // cached program or request-supplied workout ID is needed.
        val cold=parent().reviewUnfinished("owner-1")
        assertTrue(cold.toString(),cold is NativeMeasuredInventoryResult.Ready)
        val found=(cold as NativeMeasuredInventoryResult.Ready).workouts
        assertEquals(listOf("session-1","session-2"),
            found.map{it.workoutId})
        assertEquals(1,found.first().recordedSets)
        assertEquals(1,found.first().totalSets)
        assertEquals(listOf("plank"),found.first().exerciseIds)

        val committed=NativeKotlinMeasuredHistoryTransaction(
            history,parent(),fence,identity).complete("owner-1","athlete-1",
                "session-1",MeasuredWorkoutOperation.Finish("finish-1",1))
        assertTrue(committed.toString(),committed is NativeMeasuredFinishResult.Committed)
        val after=parent().reviewUnfinished("owner-1")
        assertTrue(after.toString(),after is NativeMeasuredInventoryResult.Ready)
        assertEquals(listOf("session-2"),
            (after as NativeMeasuredInventoryResult.Ready).workouts.map{
                it.workoutId})

        signed=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeMeasuredInventoryRefusal.NOT_AUTHORIZED,
            (parent().reviewUnfinished("owner-1")
                as NativeMeasuredInventoryResult.Rejected).reason)
    }

    @Test fun coldInventoryFailsClosedOnCorruptOrMisnamedMeasuredParent() {
        enroll()
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Approved)
        assertTrue(parent().issue("owner-1","session-1")
            is NativeMeasuredWorkoutStoreResult.Durable)
        val file=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("measured-parent-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val original=Files.readAllBytes(file)
        val wrong=root.resolve("measured-parent-"+ "0".repeat(64)+".bin")
        Files.write(wrong,original)
        assertEquals(NativeMeasuredInventoryRefusal.CORRUPT_RECORD,
            (parent().reviewUnfinished("owner-1")
                as NativeMeasuredInventoryResult.Rejected).reason)
        Files.delete(wrong)
        original[18]=(original[18].toInt() xor 1).toByte()
        Files.write(file,original)
        assertEquals(NativeMeasuredInventoryRefusal.CORRUPT_RECORD,
            (parent().reviewUnfinished("owner-1")
                as NativeMeasuredInventoryResult.Rejected).reason)
    }


    @Test fun approvedButNotIssuedConsentSurvivesRestartAndDoesNotForgeWorkout() {
        enroll()
        val initial=approvals().reviewConfirmedUnissued("owner-1")
        assertTrue(initial is NativeMeasuredConsentInventoryResult.Ready)
        assertTrue((initial as NativeMeasuredConsentInventoryResult.Ready)
            .confirmed.isEmpty())
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Approved)
        // Reconstruct the store after process death; this is a real saved
        // athlete confirmation, NOT a set journal or completed History.
        val pending=approvals().reviewConfirmedUnissued("owner-1")
        assertTrue(pending.toString(),
            pending is NativeMeasuredConsentInventoryResult.Ready)
        val savedApprovals=(pending as NativeMeasuredConsentInventoryResult.Ready)
            .confirmed
        assertEquals(listOf("session-1"),savedApprovals.map{it.workoutId})
        assertEquals(1,savedApprovals.single().plannedSets)
        assertEquals(listOf("plank"),savedApprovals.single().exerciseIds)
        assertTrue(parent().open("owner-1","session-1")
            is NativeMeasuredWorkoutStoreResult.Rejected)
        assertTrue(parent().issue("owner-1","session-1")
            is NativeMeasuredWorkoutStoreResult.Durable)
        assertTrue((approvals().reviewConfirmedUnissued("owner-1")
            as NativeMeasuredConsentInventoryResult.Ready).confirmed.isEmpty())

        signed=NativeKotlinOwnerIdentity("foreign-owner","foreign-athlete")
        assertEquals(NativeMeasuredConsentInventoryRefusal.NOT_AUTHORIZED,
            (approvals().reviewConfirmedUnissued("owner-1")
                as NativeMeasuredConsentInventoryResult.Rejected).reason)
    }

    @Test fun pendingConsentNeverRestoresAfterSourceChangeOrFileMismatch() {
        enroll()
        assertTrue(approvals().confirm("owner-1",plan(),1,true)
            is NativeMeasuredConsentResult.Approved)
        val file=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("measured-consent-") &&
                it.fileName.toString().endsWith(".bin")}.findFirst().orElseThrow()
        }
        val wrong=root.resolve("measured-consent-"+ "0".repeat(64)+".bin")
        Files.write(wrong,Files.readAllBytes(file))
        assertEquals(NativeMeasuredConsentInventoryRefusal.CORRUPT_RECORD,
            (approvals().reviewConfirmedUnissued("owner-1")
                as NativeMeasuredConsentInventoryResult.Rejected).reason)
        Files.delete(wrong)
        assertEquals(NativeAuthoringSourceWrite.Committed(2),
            sources.publish("owner-1",1,source(2)))
        assertTrue((approvals().reviewConfirmedUnissued("owner-1")
            as NativeMeasuredConsentInventoryResult.Ready).confirmed.isEmpty())
        assertEquals(NativeMeasuredWorkoutStoreRefusal.APPROVAL_UNAVAILABLE,
            (parent().issue("owner-1","session-1")
                as NativeMeasuredWorkoutStoreResult.Rejected).reason)
    }


    /** One more of the 14 web-measured models is now genuinely source-issued. */
    @Test fun verifiedBandedPushupPreservesRealRepsWithoutFakeMassOrRepPr() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source(withBands=true)))
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
        val set=MeasuredExerciseSetPlan("band-session","set-1",
            "band-pushup","gym-1",1,"verified-band-pushup-setup",12,
            setOf(MeasuredSide.BOTH),setOf("verified-band-pushup-setup"),
            bandId="band-blue",gymConfirmedBandIds=setOf("band-blue"))
        val p=MeasuredWorkoutPlan("band-session","gym-1",1,listOf(set))
        assertTrue(approvals().preview("owner-1",p,1)
            is NativeMeasuredConsentResult.Preview)
        assertTrue(approvals().confirm("owner-1",p,1,true)
            is NativeMeasuredConsentResult.Approved)
        assertTrue(parent().issue("owner-1",p.workoutId)
            is NativeMeasuredWorkoutStoreResult.Durable)
        val actual=MeasuredExerciseActual("verified-band-pushup-setup",
            listOf(MeasuredSideResult(MeasuredSide.BOTH,14)),
            bandId="band-blue")
        val recorded=parent().record("owner-1",p.workoutId,
            MeasuredWorkoutOperation.Record("band-record",0,"set-1",
                MeasuredSetOutcome.COMPLETED,actual))
        assertTrue(recorded.toString(),
            recorded is NativeMeasuredWorkoutStoreResult.Durable)
        val wrongMass=parent().record("owner-1",p.workoutId,
            MeasuredWorkoutOperation.Record("wrong-mass",1,"set-1",
                MeasuredSetOutcome.COMPLETED,actual.copy(
                    actualExternalLoad=java.math.BigDecimal("20"),
                    unit=LoadUnit.LB),correctsEventId="band-record"))
        assertTrue(wrongMass is NativeMeasuredWorkoutStoreResult.Rejected)
        val committed=NativeKotlinMeasuredHistoryTransaction(
            history,parent(),fence,identity).complete("owner-1","athlete-1",
                p.workoutId,MeasuredWorkoutOperation.Finish("band-finish",1))
        assertTrue(committed.toString(),committed is NativeMeasuredFinishResult.Committed)
        val reread=NativeKotlinMeasuredHistoryReadCoordinator(
            history,parent(),fence,identity).read("owner-1","athlete-1")
        assertTrue(reread is NativeMeasuredHistoryReadResult.Review)
        val saved=(reread as NativeMeasuredHistoryReadResult.Review)
            .history.verifiedMeasured.single().stored.journal
        val recordedActual=(saved.bySetKey.getValue("set-1")
            .effectiveRecord?.operation as MeasuredJournalOperation.Record).actual
        assertEquals(actual,recordedActual)
        assertNull(recordedActual?.actualExternalLoad)
        assertEquals("band-blue",recordedActual?.bandId)
        assertEquals(14,recordedActual?.sides?.single()?.amount)
    }

    @Test fun ordinaryAttachmentCannotMasqueradeAsConfirmedResistanceBand() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source(withBands=true)))
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
        val candidate=MeasuredExerciseSetPlan("band-source","set-1",
            "band-pushup","gym-1",1,"verified-band-pushup-setup",10,
            setOf(MeasuredSide.BOTH),setOf("verified-band-pushup-setup"),
            bandId="band-blue",gymConfirmedBandIds=setOf("band-blue"))
        val p=MeasuredWorkoutPlan("band-source","gym-1",1,listOf(candidate))
        val notABand=p.copy(sets=listOf(candidate.copy(
            bandId="rope-attachment",
            gymConfirmedBandIds=setOf("rope-attachment"))))
        val forgedExtra=p.copy(sets=listOf(candidate.copy(
            gymConfirmedBandIds=setOf("band-blue","rope-attachment"))))
        val missingBand=p.copy(sets=listOf(candidate.copy(
            gymConfirmedBandIds=emptySet())))
        for(bad in listOf(notABand,forgedExtra,missingBand))
            assertEquals(NativeMeasuredConsentRefusal.UNVERIFIED_GYM_OR_EXERCISE,
                (approvals().preview("owner-1",bad,1)
                    as NativeMeasuredConsentResult.Rejected).reason)
        assertTrue(approvals().preview("owner-1",p,1)
            is NativeMeasuredConsentResult.Preview)
    }


    @Test fun fourLoadedMeasuredModelsUseOnlyVerifiedDumbbellsOrPhysicalPlates() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source(withMeasuredLoads=true)))
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
        val gym=source(withMeasuredLoads=true).setup.reviewed!!.gym
        val ids=listOf("farmers","suitcase-carry","plate-pinch","weighted-plank")
        for((index,id) in ids.withIndex()) {
            val def=MeasuredExerciseCatalogDomain.find(id)!!
            val equipment=MeasuredPhysicalStockSource.equipmentIdFor(id)!!
            val exact=if(equipment=="dumbbell-rack")"25" else "20"
            val workout="loaded-${index+1}"
            val setupId=def.requiredFixtures.firstOrNull()?:equipment
            val sides=if(id=="suitcase-carry" || id=="plate-pinch")
                setOf(MeasuredSide.RIGHT) else setOf(MeasuredSide.BOTH)
            val plan=MeasuredExerciseSetPlan(workout,"set-1",id,"gym-1",1,
                setupId,if(def.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)
                    2200 else 40,
                sides,def.requiredFixtures,
                physicalStock=gym.confirmedStock[equipment],
                intendedExternalLoad=java.math.BigDecimal(exact),
                unit=LoadUnit.LB,matchedPairConfirmed=(id=="farmers"))
            val parentPlan=MeasuredWorkoutPlan(workout,"gym-1",1,listOf(plan))
            assertTrue(id,approvals().preview("owner-1",parentPlan,1)
                is NativeMeasuredConsentResult.Preview)
            assertTrue(id,approvals().confirm("owner-1",parentPlan,1,true)
                is NativeMeasuredConsentResult.Approved)
            assertTrue(id,parent().issue("owner-1",workout)
                is NativeMeasuredWorkoutStoreResult.Durable)
            val actual=MeasuredExerciseActual(setupId,
                sides.map{MeasuredSideResult(it,if(def.kind==
                    MeasuredExerciseKind.CARRY_CENTIMETERS)2250 else 45)},
                actualExternalLoad=java.math.BigDecimal(exact),unit=LoadUnit.LB,
                elapsedSeconds=if(def.kind==MeasuredExerciseKind.CARRY_CENTIMETERS)
                    23 else null)
            assertTrue(id,parent().record("owner-1",workout,
                MeasuredWorkoutOperation.Record("record-${index+1}",0,
                    "set-1",MeasuredSetOutcome.COMPLETED,actual))
                is NativeMeasuredWorkoutStoreResult.Durable)
            val finish=NativeKotlinMeasuredHistoryTransaction(
                history,parent(),fence,identity).complete("owner-1","athlete-1",
                    workout,MeasuredWorkoutOperation.Finish("finish-${index+1}",1))
            assertTrue("$id: $finish",finish is NativeMeasuredFinishResult.Committed)
        }
        val historyRead=NativeKotlinMeasuredHistoryReadCoordinator(
            history,parent(),fence,identity).read("owner-1","athlete-1")
        assertTrue(historyRead is NativeMeasuredHistoryReadResult.Review)
        val measured=(historyRead as NativeMeasuredHistoryReadResult.Review)
            .history.verifiedMeasured
        assertEquals(4,measured.size)
        assertEquals(ids.toSet(),measured.flatMap{entry->
            entry.stored.journal.approvedPlan.sets.map{it.exerciseId}
        }.toSet())
        assertTrue(measured.all{entry->
            entry.stored.journal.bySetKey.values.single().effectiveRecord!=null
        })
    }

    @Test fun loadedMeasuredSessionsRejectUnverifiedStockAndPhantomPairs() {
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,source(withMeasuredLoads=true)))
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
        val actualStock=source(withMeasuredLoads=true).setup.reviewed!!.gym
            .confirmedStock.getValue("dumbbell-rack")
        val base=MeasuredExerciseSetPlan("bad-load","set-1",
            "farmers","gym-1",1,"dumbbell-rack",2000,
            setOf(MeasuredSide.BOTH),emptySet(),physicalStock=actualStock,
            intendedExternalLoad=java.math.BigDecimal("25"),unit=LoadUnit.LB,
            matchedPairConfirmed=true)
        fun preview(p:MeasuredExerciseSetPlan)=approvals().preview(
            "owner-1",MeasuredWorkoutPlan("bad-load","gym-1",1,listOf(p)),1)
        assertTrue(preview(base) is NativeMeasuredConsentResult.Preview)
        for(bad in listOf(
            base.copy(physicalSetupId="floor"),
            base.copy(matchedPairConfirmed=false),
            base.copy(intendedExternalLoad=java.math.BigDecimal("27")),
            base.copy(physicalStock=VerifiedLoadStock.Totals(
                LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                listOf(java.math.BigDecimal("25"))))
        )) assertEquals(NativeMeasuredConsentRefusal.UNVERIFIED_GYM_OR_EXERCISE,
            (preview(bad) as NativeMeasuredConsentResult.Rejected).reason)
        val unilateral=base.copy(exerciseId="suitcase-carry",
            sides=setOf(MeasuredSide.LEFT),matchedPairConfirmed=true)
        assertEquals(NativeMeasuredConsentRefusal.UNVERIFIED_GYM_OR_EXERCISE,
            (preview(unilateral) as NativeMeasuredConsentResult.Rejected).reason)
    }


    @Test fun mixedMeasuredSessionColdReplaysAndFinishesAsOneRealHistoryWorkout() {
        val current=source(withBands=true,withMeasuredLoads=true)
        assertEquals(NativeAuthoringSourceWrite.Committed(1),
            sources.publish("owner-1",0,current))
        assertTrue(history.enroll("owner-1","athlete-1"){o,a->
            signed==NativeKotlinOwnerIdentity(o,a)
        } is NativeHistoryLedgerResult.Durable)
        val setup=checkNotNull(current.setup.reviewed)
        val selections=listOf(
            MeasuredSessionSelection("plank",35,2),
            MeasuredSessionSelection("farmers",2000,1,
                targetExternalLoad=java.math.BigDecimal("25"),
                matchedPairConfirmed=true),
            MeasuredSessionSelection("band-pushup",12,1,bandId="band-blue"),
            MeasuredSessionSelection("plate-pinch",40,1,
                singleSide=MeasuredSide.LEFT,
                targetExternalLoad=java.math.BigDecimal("20")))
        val plan=checkNotNull(MeasuredSessionAuthoringDomain.compose(
            "mixed-verified-1",current.revision,setup,selections))
        assertEquals(5,plan.sets.size)
        assertEquals((1..5).map{"set-$it"},plan.sets.map{it.setKey})
        assertTrue(approvals().preview("owner-1",plan,1)
            is NativeMeasuredConsentResult.Preview)
        assertTrue(approvals().confirm("owner-1",plan,1,true)
            is NativeMeasuredConsentResult.Approved)
        // Exactly this set of heterogeneous measurements must be saved;
        // none may be issued or logged as generic numeric reps.
        assertTrue(parent().issue("owner-1",plan.workoutId)
            is NativeMeasuredWorkoutStoreResult.Durable)
        assertEquals(listOf(plan.workoutId),
            (parent().reviewUnfinished("owner-1")
                as NativeMeasuredInventoryResult.Ready).workouts.map{it.workoutId})
        for((index,set) in plan.sets.withIndex()) {
            // Use the actual typed input boundary used by the native logger.
            // Targets are not copied into performed observations.
            val (unit,quantity)=when(set.exerciseId){
                "farmers"->MeasuredEntryUnit.FEET to "80"
                "band-pushup"->MeasuredEntryUnit.REPETITIONS to "15"
                else->MeasuredEntryUnit.MINUTES_SECONDS to "0:43"
            }
            val input=MeasuredExerciseEntryInput(
                set.sides.associateWith{quantity},unit,
                externalLoadText=when(set.exerciseId){
                    "farmers"->"30"
                    "plate-pinch"->"25"
                    else->null
                },externalLoadUnit=set.unit,physicalSetupId=set.physicalSetupId,
                bandId=set.bandId,carryElapsedSecondsText=if(set.exerciseId=="farmers")"36" else null)
            fun parse(value:MeasuredExerciseEntryInput)=MeasuredExerciseEntryDomain.parse(
                set,MeasuredSetOutcome.COMPLETED,value)
            val untouched=(parent().open("owner-1",plan.workoutId)
                as NativeMeasuredWorkoutStoreResult.Durable).stored.journal
            assertEquals(MeasuredExerciseEntryDecision.Rejected(MeasuredEntryRefusal.WRONG_PHYSICAL_SETUP),
                parse(input.copy(physicalSetupId="unverified-station")))
            if(set.exerciseId=="farmers" || set.exerciseId=="plate-pinch"){
                assertEquals(MeasuredExerciseEntryDecision.Rejected(MeasuredEntryRefusal.UNVERIFIED_LOAD),
                    parse(input.copy(externalLoadText="27")))
                assertEquals(MeasuredExerciseEntryDecision.Rejected(MeasuredEntryRefusal.UNVERIFIED_LOAD),
                    parse(input.copy(externalLoadUnit=LoadUnit.KG)))
                assertEquals(MeasuredExerciseEntryDecision.Rejected(MeasuredEntryRefusal.LOAD_ENTRY_REQUIRED),
                    parse(input.copy(externalLoadText=null)))
            }
            if(set.exerciseId=="band-pushup")
                assertEquals(MeasuredExerciseEntryDecision.Rejected(MeasuredEntryRefusal.WRONG_OR_UNVERIFIED_BAND),
                    parse(input.copy(bandId="rope-attachment")))
            assertEquals(untouched,(parent().open("owner-1",plan.workoutId)
                as NativeMeasuredWorkoutStoreResult.Durable).stored.journal)
            val parsed=parse(input) as MeasuredExerciseEntryDecision.Accepted
            assertFalse(parsed.isSaved)
            assertFalse(parsed.mayPublishHistory)
            assertEquals(set.exerciseId=="farmers",parsed.approximateDistanceConversion)
            val actual=checkNotNull(parsed.actual)
            assertEquals(set.sides.associateWith{when(set.exerciseId){
                "farmers"->2438
                "band-pushup"->15
                else->43
            }},actual.sides.associate{it.side to it.amount})
            val saved=parent().record("owner-1",plan.workoutId,
                MeasuredWorkoutOperation.Record("measured-${index+1}",
                    index.toLong(),set.setKey,
                    MeasuredSetOutcome.COMPLETED,actual))
            assertTrue("${set.exerciseId}: $saved",
                saved is NativeMeasuredWorkoutStoreResult.Durable)
        }
        // Correct only the carry before Finish: typed meters and a different
        // physical dumbbell load. Earlier receipts and approved targets stay exact.
        val carry=plan.sets.single{it.exerciseId=="farmers"}
        val carryInput=MeasuredExerciseEntryInput(setOf(MeasuredSide.BOTH).associateWith{"24.50"},
            MeasuredEntryUnit.METERS,"25",LoadUnit.LB,carry.physicalSetupId,
            carryElapsedSecondsText="35")
        val revised=MeasuredExerciseEntryDomain.parse(carry,MeasuredSetOutcome.COMPLETED,carryInput)
            as MeasuredExerciseEntryDecision.Accepted
        assertFalse(revised.approximateDistanceConversion)
        assertTrue(parent().record("owner-1",plan.workoutId,MeasuredWorkoutOperation.Record(
            "carry-corrected",5L,carry.setKey,MeasuredSetOutcome.COMPLETED,revised.actual,
            "measured-3")) is NativeMeasuredWorkoutStoreResult.Durable)
        val beforeFinish=parent().open("owner-1",plan.workoutId)
        assertTrue(beforeFinish is NativeMeasuredWorkoutStoreResult.Durable)
        assertEquals(6L,(beforeFinish as NativeMeasuredWorkoutStoreResult.Durable)
            .stored.journal.revision)
        val completed=NativeKotlinMeasuredHistoryTransaction(
            history,parent(),fence,identity).complete("owner-1","athlete-1",
                plan.workoutId,MeasuredWorkoutOperation.Finish("mixed-finish",6L))
        assertTrue(completed.toString(),
            completed is NativeMeasuredFinishResult.Committed)
        val replayed=NativeKotlinMeasuredHistoryReadCoordinator(
            history,parent(),fence,identity).read("owner-1","athlete-1")
        assertTrue(replayed is NativeMeasuredHistoryReadResult.Review)
        val historySet=(replayed as NativeMeasuredHistoryReadResult.Review)
            .history.verifiedMeasured.single().stored.journal
        assertEquals("mixed-finish",historySet.finishedByEventId)
        assertEquals(plan.sets.map{it.exerciseId},
            historySet.approvedPlan.sets.map{it.exerciseId})
        assertEquals(5,historySet.bySetKey.values.count{it.effectiveRecord!=null})
        assertEquals(plan,historySet.approvedPlan)
        val expected=listOf("43 s","43 s","24.5 m · External 25 lb per hand · Elapsed 35 s",
            "15 reps · Band band-blue","Left 43 s · External 25 lb added")
        for((index,set) in plan.sets.withIndex()){
            val result=MeasuredWorkoutResultReviewDomain.review(set,historySet.bySetKey.getValue(set.setKey))
            assertEquals(set.exerciseId,MeasuredSetReviewStatus.COMPLETED,result.status)
            assertEquals(set.exerciseId,expected[index],result.actualSummary)
        }
        assertEquals(historySet,(parent().open("owner-1",plan.workoutId)
            as NativeMeasuredWorkoutStoreResult.Durable).stored.journal)
        assertEquals(1,(NativeKotlinMeasuredHistoryReadCoordinator(history,parent(),fence,identity)
            .read("owner-1","athlete-1") as NativeMeasuredHistoryReadResult.Review)
            .history.verifiedMeasured.size)
        assertTrue((parent().reviewUnfinished("owner-1")
            as NativeMeasuredInventoryResult.Ready).workouts.isEmpty())
    }


    @Test fun measuredFocusReopensFirstPendingSetAndDoesNotInventCompletion() {
        enroll()
        val initial=plan()
        val one=initial.sets.single()
        val three=initial.copy(workoutId="focus-three",
            sets=(1..3).map{number->
                one.copy(workoutId="focus-three",setKey="set-$number")
            })
        assertTrue(approvals().confirm("owner-1",three,1,true)
            is NativeMeasuredConsentResult.Approved)
        assertTrue(parent().issue("owner-1","focus-three")
            is NativeMeasuredWorkoutStoreResult.Durable)
        val skipped=parent().record("owner-1","focus-three",
            MeasuredWorkoutOperation.Record("skip-second",0,
                "set-2",MeasuredSetOutcome.SKIPPED,null))
        assertTrue(skipped is NativeMeasuredWorkoutStoreResult.Durable)
        val cold=parent().open("owner-1","focus-three")
            as NativeMeasuredWorkoutStoreResult.Durable
        assertEquals("set-1",MeasuredWorkoutFocusDomain.review(
            cold.stored.journal,null)?.selectedSetKey)
        assertEquals("set-2",MeasuredWorkoutFocusDomain.review(
            cold.stored.journal,"set-2")?.selectedSetKey)
        val actual=MeasuredExerciseActual("floor",
            listOf(MeasuredSideResult(MeasuredSide.BOTH,42)))
        val first=parent().record("owner-1","focus-three",
            MeasuredWorkoutOperation.Record("record-first",1,
                "set-1",MeasuredSetOutcome.COMPLETED,actual))
        assertTrue(first is NativeMeasuredWorkoutStoreResult.Durable)
        val after=(first as NativeMeasuredWorkoutStoreResult.Durable)
            .stored.journal
        assertEquals("set-3",MeasuredWorkoutFocusDomain.afterDurableRecord(
            cold.stored.journal,after,"set-1"))
        assertFalse(after.canProposeFinish)
        assertEquals(1,MeasuredWorkoutFocusDomain.review(after,null)
            ?.remainingSets)
        val premature=NativeKotlinMeasuredHistoryTransaction(
            history,parent(),fence,identity).complete("owner-1","athlete-1",
                "focus-three",MeasuredWorkoutOperation.Finish("too-early",2))
        assertTrue(premature is NativeMeasuredFinishResult.Rejected)
        val third=parent().record("owner-1","focus-three",
            MeasuredWorkoutOperation.Record("record-third",2,
                "set-3",MeasuredSetOutcome.COMPLETED,actual))
        assertTrue(third is NativeMeasuredWorkoutStoreResult.Durable)
        val finished=NativeKotlinMeasuredHistoryTransaction(
            history,parent(),fence,identity).complete("owner-1","athlete-1",
                "focus-three",MeasuredWorkoutOperation.Finish("finish-three",3))
        assertTrue(finished.toString(),
            finished is NativeMeasuredFinishResult.Committed)
        val historyRead=NativeKotlinMeasuredHistoryReadCoordinator(
            history,parent(),fence,identity).read("owner-1","athlete-1")
        assertTrue(historyRead is NativeMeasuredHistoryReadResult.Review)
        val saved=(historyRead as NativeMeasuredHistoryReadResult.Review)
            .history.verifiedMeasured.single().stored.journal
        assertEquals("finish-three",saved.finishedByEventId)
        assertEquals(3,saved.bySetKey.values.count{
            it.effectiveRecord!=null})
    }

}
