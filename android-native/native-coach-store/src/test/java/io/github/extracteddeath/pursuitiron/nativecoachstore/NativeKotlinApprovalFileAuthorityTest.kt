package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeKotlinApprovalFileAuthorityTest {
    @get:Rule val temp=TemporaryFolder()
    private fun n(s:String)=BigDecimal(s)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",4,"program-1",8,"gym-1",3,"catalog-5","coach-rules-2")
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("20"),n("25"),n("30"),n("35"))
    )
    private fun option(id:String,muscle:TrainingMuscle,role:ComposerRole)=
        ComposerExerciseOption(id,if(muscle==TrainingMuscle.CHEST)
            ComposerPattern.HORIZONTAL_PRESS else ComposerPattern.HORIZONTAL_PULL,
            setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n("30"),stock),
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,n("1"))),
            90,120,2,3,3,2,1,2,role=role)
    private val press=option("db-press",TrainingMuscle.CHEST,ComposerRole.PRIMARY)
    private val row=option("db-row",TrainingMuscle.LATS,ComposerRole.SECONDARY)
    private fun program():ComposerRequest {
        val window=DeclaredSessionWindow(1,120,null,60)
        val bounds=TrainingMuscle.entries.map { muscle ->
            when(muscle) {
                TrainingMuscle.CHEST,TrainingMuscle.LATS ->
                    DeclaredMuscleDoseBounds(muscle,n("2"),n("8"),n("10"),1)
                else -> DeclaredMuscleDoseBounds(muscle,n("0"),n("0"),n("0"),0)
            }
        }
        return ComposerRequest(ComposerSplit.FULL_BODY,TrainingPhase.STRENGTH,
            listOf(ComposerDay(0,ComposerFocus.FULL_BODY,window),
                ComposerDay(2,ComposerFocus.FULL_BODY,window),
                ComposerDay(4,ComposerFocus.FULL_BODY,window)),
            bounds,listOf(press,row),3,1,listOf(
                ComposerSessionIntent(0,ComposerSessionPurpose.STRENGTH_SKILL,"db-press"),
                ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
                ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY)))
    }
    private fun policy(id:String,purpose:ComposerSessionPurpose,reps:RepBand,
        rir:Int?,rest:Int,scheme:SetLoadScheme=SetLoadScheme.STRAIGHT,
        backoff:BigDecimal?=null,warmups:List<DeclaredWarmupStep> = emptyList(),
        backoffReps:RepBand?=null,backoffRir:Int?=null)=
        ExercisePrescriptionPolicy(id,purpose,reps,rir,90,rest,scheme,
            backoff,warmups,backoffReps,backoffRir)
    private fun policies()=listOf(
        policy("db-press",ComposerSessionPurpose.STRENGTH_SKILL,
            RepBand(3,5),3,240,SetLoadScheme.LAST_SET_BACKOFF,n("5"),
            listOf(DeclaredWarmupStep(n("20"),8,60,60)),RepBand(6,8),2),
        policy("db-press",ComposerSessionPurpose.HYPERTROPHY,RepBand(8,12),2,120),
        policy("db-row",ComposerSessionPurpose.STRENGTH_SKILL,RepBand(6,10),2,180),
        policy("db-row",ComposerSessionPurpose.HYPERTROPHY,RepBand(10,15),null,120))
    private fun approval(revision:Long=1,grantId:String="approval-1")=
        NativeKotlinWorkoutApproval("owner-1","athlete-1",source,source,
            KotlinWorkoutSelectionGrant(grantId,"workout-1",source,0),
            program(),policies(),"commit-"+revision,revision)

    private var signedIn:NativeKotlinOwnerIdentity?=
        NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private fun fence()=NativeKotlinApprovalRevisionFence(temp.root.toPath())
    private fun authority()=NativeKotlinApprovalFileAuthority(
        temp.root.toPath(),fence(),NativeKotlinCurrentIdentity{signedIn})
    private fun success(result:NativeApprovalWriteResult):Long {
        assertTrue(result.toString(),result is NativeApprovalWriteResult.Committed)
        return (result as NativeApprovalWriteResult.Committed).revision
    }
    private fun got(result:NativeKotlinIssueResult):NativeKotlinIssueResult.Committed {
        assertTrue(result.toString(),result is NativeKotlinIssueResult.Committed)
        return result as NativeKotlinIssueResult.Committed
    }

    @Test fun acceptedProgramRoundtripsAllExplicitCoachInputsExactly() {
        val a=approval()
        val bytes=NativeKotlinApprovalCodec.encode(a)
        assertEquals(a,NativeKotlinApprovalCodec.decode(bytes))
        val weight=(NativeKotlinApprovalCodec.decode(bytes)
            .program.exercises.first().equipment as ComposerEquipment.Verified)
        assertEquals(stock,weight.inventory)
        assertEquals(n("30"),weight.plannedLoad)
        assertEquals(SetLoadScheme.LAST_SET_BACKOFF,
            NativeKotlinApprovalCodec.decode(bytes).policies.first().scheme)
        assertThrows(java.io.IOException::class.java) {
            NativeKotlinApprovalCodec.decode(bytes+byteArrayOf(0))
        }
        val wrong=approval().copy(program=program().copy(exercises=listOf(
            press.copy(equipment=ComposerEquipment.Verified(
                LoadUnit.LB,n("38.5"),stock)),row)))
        assertThrows(IllegalArgumentException::class.java) {
            NativeKotlinApprovalCodec.encode(wrong)
        }
    }

    @Test fun legacyVersionOneApprovalRemainsReadableDuringRepProofUpgrade() {
        // Rebuild the genuine v2 and v1 bytes: v3 added a null-or-decimal
        // marker AFTER EACH policy, not merely a new trailing byte. A real
        // old approval must decode without accidentally consuming the next
        // policy's exercise ID as a load.
        val current=NativeKotlinApprovalCodec.encode(approval())
        assertEquals(0,current.last().toInt())
        fun utf(vararg strings:String):ByteArray {
            val out=java.io.ByteArrayOutputStream()
            java.io.DataOutputStream(out).use {d->
                strings.forEach {d.writeUTF(it)}
            }
            return out.toByteArray()
        }
        fun indexOfToken(value:ByteArray,needle:ByteArray):Int =
            (0..value.size-needle.size).firstOrNull {start ->
                needle.indices.all {j->value[start+j]==needle[j]}
            } ?:error("Expected legacy policy marker")
        val nextFields=listOf(
            utf("db-press","HYPERTROPHY"),
            utf("db-row","STRENGTH_SKILL"),
            utf("db-row","HYPERTROPHY"),
            utf("commit-1"))
        val loadPresenceIndices=nextFields.map {marker ->
            indexOfToken(current,marker)-1
        }
        assertEquals(4,loadPresenceIndices.distinct().size)
        assertTrue(loadPresenceIndices.all {it in current.indices &&
            current[it]==0.toByte()})
        val legacy=current.filterIndexed {index,_ ->
            index !in loadPresenceIndices
        }.toByteArray()
        // v2 retained the single optional accepted-rep-proof presence byte.
        val v2=legacy.clone().also {it[7]=2}
        assertEquals(approval(),NativeKotlinApprovalCodec.decode(v2))
        // v1 ended at the storage revision and had no proof flag at all.
        val v1=v2.copyOf(v2.size-1).also {it[7]=1}
        assertEquals(approval(),NativeKotlinApprovalCodec.decode(v1))
        assertEquals(approval(),NativeKotlinApprovalCodec.decode(
            NativeKotlinApprovalCodec.encode(
                NativeKotlinApprovalCodec.decode(v1))))
        assertThrows(java.io.IOException::class.java) {
            NativeKotlinApprovalCodec.decode(v1.copyOf(v1.size-1))
        }
    }

    @Test fun purposeSpecificWorkingLoadsRemainExactAfterV3DiskRoundtrip() {
        val authored=approval().copy(policies=policies().map {p->
            if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                p.copy(purposeWorkingLoad=n("35"))
            else if(p.exerciseId=="db-press" &&
                p.purpose==ComposerSessionPurpose.HYPERTROPHY)
                p.copy(purposeWorkingLoad=n("25"))
            else p
        })
        assertEquals(authored,NativeKotlinApprovalCodec.decode(
            NativeKotlinApprovalCodec.encode(authored)))
        assertEquals(NativeApprovalWriteResult.Committed(1),
            authority().publish("owner-1",0,authored))
        val restored=checkNotNull(authority().currentOwnedApproval("owner-1"))
        assertEquals(authored,restored)
        assertEquals(n("35"),restored.policies.first {
            it.exerciseId=="db-press" &&
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
        }.purposeWorkingLoad)
        assertEquals(n("25"),restored.policies.first {
            it.exerciseId=="db-press" &&
            it.purpose==ComposerSessionPurpose.HYPERTROPHY
        }.purposeWorkingLoad)
    }

    @Test fun forgedMultiRepApprovalOrUnknownSetKeyIsNotPersistable() {
        val fixed=program().copy(exercises=listOf(press,row).map {
            it.copy(minimumSetsPerVisit=2,maximumSetsPerVisit=2,
                maximumVisits=3,requiredVisits=3)
        })
        val baseline=approval().copy(
            program=fixed,
            grant=KotlinWorkoutSelectionGrant(
                "grant-2","future-session",source,1))
        val proof=NativeKotlinAcceptedRepTargets(
            "past-session",7,"db-press","db-press:d1:s0",
            listOf(11,12),listOf(12,12),CoachReadiness.NORMAL)
        val valid=baseline.copy(acceptedRepTargets=proof)
        assertEquals(valid,NativeKotlinApprovalCodec.decode(
            NativeKotlinApprovalCodec.encode(valid)))
        val unsafe=listOf(
            proof.copy(newRepTargets=listOf(14,12)),
            proof.copy(newRepTargets=listOf(12,13)),
            proof.copy(changedSetKey="db-press:d1:s999"),
            proof.copy(reviewedReadiness=CoachReadiness.LOW),
            proof.copy(historicalWorkoutId="future-session"),
            proof.copy(newRepTargets=listOf(12)),
            proof.copy(observedReps=listOf(11,11),newRepTargets=listOf(12,12))
        )
        for(forged in unsafe) {
            assertThrows(IllegalArgumentException::class.java) {
                NativeKotlinApprovalCodec.encode(
                    baseline.copy(acceptedRepTargets=forged))
            }
        }
    }

    @Test fun explicitBarbellPlatesAndMachineStackSurviveApprovalRoundtrip() {
        // One pair of 2.5 and one pair of 5 lbs permits 20,25,30,35...
        // It never invents impossible 38.5 lb suggestions.
        val bar=VerifiedLoadStock.Barbell(LoadUnit.LB,n("20"),
            mapOf(n("2.5") to 1,n("5") to 1))
        val machine=VerifiedLoadStock.Stack(LoadUnit.LB,
            LoadBasis.MACHINE_STACK,n("20"),n("5"),n("40"))
        val exerciseList=listOf(
            press.copy(equipment=ComposerEquipment.Verified(LoadUnit.LB,
                n("30"),bar)),
            row.copy(equipment=ComposerEquipment.Verified(LoadUnit.LB,
                n("30"),machine))
        )
        val changed=approval().copy(program=program().copy(exercises=exerciseList))
        val roundtrip=NativeKotlinApprovalCodec.decode(
            NativeKotlinApprovalCodec.encode(changed))
        assertEquals(changed,roundtrip)
        val restored=roundtrip.program.exercises
        assertEquals(bar,(restored.first().equipment as ComposerEquipment.Verified).inventory)
        assertEquals(machine,(restored.last().equipment as ComposerEquipment.Verified).inventory)
        assertEquals(NativeApprovalWriteResult.Committed(1),
            authority().publish("owner-1",0,changed))
        val session=got(NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temp.root.toPath()),authority(),fence())
            .issue("owner-1","workout-1")).journal.snapshot
        assertEquals(LoadBasis.BARBELL_TOTAL,
            (session.targets.first().load as DraftSetLoad.Verified).basis)
        assertEquals(n("20"),
            (session.targets.first().load as DraftSetLoad.Verified).value)
    }

    @Test fun currentOwnedApprovalRequiresCurrentIdentityAndSavedGrant() {
        val authority=authority()
        assertNull(authority.currentOwnedApproval("owner-1"))
        assertNull(authority.currentOwnedApproval("owner-2"))
        assertEquals(1L,success(authority.publish("owner-1",0,approval())))
        assertEquals(approval(),authority().currentOwnedApproval("owner-1"))
        assertEquals("workout-1",
            authority.currentOwnedApproval("owner-1")?.grant?.workoutId)
        assertNull(authority.currentOwnedApproval("owner-2"))
        signedIn=NativeKotlinOwnerIdentity("owner-1","other-athlete")
        assertNull(authority.currentOwnedApproval("owner-1"))
        signedIn=null
        assertNull(authority.currentOwnedApproval("owner-1"))
        signedIn=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(2L,success(authority.revokeCurrentGrant("owner-1",1)))
        assertNull(authority.currentOwnedApproval("owner-1"))
        assertNull(authority().currentOwnedApproval("owner-1"))
    }

    @Test fun currentOwnedApprovalNeverInventsFromCorruptSavedBytes() {
        val authority=authority()
        success(authority.publish("owner-1",0,approval()))
        val file=Files.list(temp.root.toPath()).use { entries ->
            entries.filter {
                it.fileName.toString().startsWith("approved-")
            }.toList().single()
        }
        val bytes=Files.readAllBytes(file)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        Files.write(file,bytes)
        assertNull(authority().currentOwnedApproval("owner-1"))
        assertNull(authority.currentOwnedApproval("owner-1"))
    }

    @Test fun nativePersistentAuthorityPublishesAndIssuesRealKotlinWorkout() {
        val owner=authority()
        assertNull(owner.currentApprovedWorkout("owner-1","workout-1"))
        assertEquals(1L,success(owner.publish("owner-1",0,approval())))
        val recovered=authority()
        assertEquals(approval(),recovered.currentApprovedWorkout("owner-1","workout-1"))
        assertNull(recovered.currentApprovedWorkout("owner-1","another-workout"))
        assertNull(recovered.currentApprovedWorkout("owner-2","workout-1"))
        val issued=got(NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temp.root.toPath()),
            recovered,fence()).issue("owner-1","workout-1"))
        assertEquals("workout-1",issued.journal.snapshot.workoutId)
        assertEquals(DraftSetType.WARMUP,issued.journal.snapshot.targets.first().type)
        assertEquals(0L,issued.journal.revision)
        assertEquals(approval().storageReceiptId,issued.journal.storedIdentity.storageReceiptId)
    }

    @Test fun everyProgramChangeRequiresCasAndOldWorkoutStaysFrozen() {
        val a=authority()
        success(a.publish("owner-1",0,approval()))
        val before=got(NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temp.root.toPath()),
            a,fence()).issue("owner-1","workout-1"))
        val changed=approval(2,"approval-2")
        assertEquals(2L,success(a.publish("owner-1",1,changed)))
        assertEquals(NativeApprovalWriteResult.Rejected(
            NativeApprovalWriteRefusal.STALE_REVISION),
            a.publish("owner-1",1,changed))
        assertEquals(changed,authority().currentApprovedWorkout("owner-1","workout-1"))
        val service=NativeKotlinWorkoutCoordinator(
            KotlinNativeJournalFileStore(temp.root.toPath()),
            authority(),fence())
        assertEquals(before.journal.snapshot,
            (service.open("owner-1","workout-1") as NativeJournalFileResult.Durable)
                .journal.snapshot)
        assertEquals(NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.STORAGE_REJECTED,
            storageReason=NativeJournalFileRefusal.ISSUE_CONFLICT),
            service.issue("owner-1","workout-1"))
        assertEquals(3L,success(a.revokeCurrentGrant("owner-1",2)))
        assertNull(authority().currentApprovedWorkout("owner-1","workout-1"))
        assertEquals(before.journal.snapshot,
            (service.open("owner-1","workout-1") as NativeJournalFileResult.Durable)
                .journal.snapshot)
    }

    @Test fun signoutCrossAccountAndDeletedCurrentGrantAlwaysFailClosed() {
        val a=authority()
        assertEquals(NativeApprovalWriteResult.Rejected(
            NativeApprovalWriteRefusal.NOT_AUTHORIZED),
            a.publish("owner-2",0,approval()))
        signedIn=null
        assertEquals(NativeApprovalWriteResult.Rejected(
            NativeApprovalWriteRefusal.NOT_AUTHORIZED),
            a.publish("owner-1",0,approval()))
        assertNull(a.currentApprovedWorkout("owner-1","workout-1"))
        signedIn=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        success(a.publish("owner-1",0,approval()))
        signedIn=NativeKotlinOwnerIdentity("owner-1","other-athlete")
        assertNull(a.currentApprovedWorkout("owner-1","workout-1"))
        assertFalse(a.canAccessIssuedWorkout("owner-1",source))
        assertEquals(NativeApprovalWriteResult.Rejected(
            NativeApprovalWriteRefusal.NOT_AUTHORIZED),
            a.revokeCurrentGrant("owner-1",1))
        signedIn=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(approval(),a.currentApprovedWorkout("owner-1","workout-1"))
    }

    @Test fun corruptApprovalBytesCannotBeOverwrittenOrConsideredAuthorized() {
        val a=authority()
        success(a.publish("owner-1",0,approval()))
        val files=Files.list(temp.root.toPath()).use { stream ->
            stream.filter{it.fileName.toString().startsWith("approved-")}
                .toArray().map {it as java.nio.file.Path}
        }
        assertEquals(1,files.size)
        val bytes=Files.readAllBytes(files[0])
        bytes[bytes.size/2]=(bytes[bytes.size/2].toInt() xor 1).toByte()
        Files.write(files[0],bytes)
        assertNull(authority().currentApprovedWorkout("owner-1","workout-1"))
        assertEquals(NativeApprovalWriteResult.Rejected(
            NativeApprovalWriteRefusal.CORRUPT_EXISTING_RECORD),
            authority().publish("owner-1",1,approval(2)))
        assertEquals(NativeApprovalWriteResult.Rejected(
            NativeApprovalWriteRefusal.CORRUPT_EXISTING_RECORD),
            authority().revokeCurrentGrant("owner-1",1))
    }
}
