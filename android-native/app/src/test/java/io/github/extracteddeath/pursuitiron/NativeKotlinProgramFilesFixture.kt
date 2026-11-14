package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Shared fixture uses actual native files and canonical edit receipts. */
internal open class NativeKotlinProgramFilesFixture {
    @get:Rule val temp=TemporaryFolder()
    protected var signedIn:NativeKotlinOwnerIdentity?=NativeKotlinOwnerIdentity("owner-1","athlete-1")
    protected val identity=NativeKotlinCurrentIdentity {signedIn}
    protected var sourceReads=0
    protected var censusReads=0
    protected val draftReads=mutableListOf<String>()
    protected fun n(value:String)=BigDecimal(value)
    protected fun fence()=NativeKotlinApprovalRevisionFence(temp.root.toPath())
    protected fun drafts()=NativeCustomCycleDraftFileStore(temp.root.toPath(),fence(),identity)
    protected fun sources()=NativeKotlinCustomAuthoringSourceFileStore(temp.root.toPath(),fence(),identity)
    protected fun source(revision:Long=1,owner:NativeKotlinOwnerIdentity=checkNotNull(signedIn)):
        NativeCustomAuthoringSnapshot {
        val setup=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-$revision",athleteProfileId=owner.athleteId,
            goal=AthleteTrainingGoal.HYPERTROPHY,experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,trainingWeekdays=setOf(0,2,4),
            timezoneId="America/New_York",sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,mapOf("dumbbell-rack" to
                VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                    listOf(n("15"),n("20"),n("25"),n("30")))),emptySet(),emptySet()),
            allowBodyweight=true,allowSupersets=false,allowTricepsOnLower=false,
            maxBarbellExercisesPerDay=0,focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),hardTrainingDays=emptySet(),
            longTrainingDays=emptySet(),declaredRestDays=setOf(1,3,5,6)))
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup.status)
        return NativeCustomAuthoringSnapshot(owner.ownerKey,owner.athleteId,revision,
            setup,StarterExerciseTranche.movements())
    }
    protected fun load(value:String)=DraftSetLoad.Verified(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,n(value))
    protected fun cycle(id:String,athlete:String=checkNotNull(signedIn).athleteId):CustomTrainingCycle {
        val working=CustomAuthoredSet(DraftSetType.WORKING,load("20"),RepBand(8,12),2,50,90)
        return CustomTrainingCycle(id,1,athlete,AthleteTrainingGoal.HYPERTROPHY,
            "Program $id",listOf(CustomTrainingBlock("phase-1","Foundation",
                TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,3,
                CustomSplitIdentity.Freeform("My custom program"),listOf(0,2,4).map { day->
                    CustomTrainingDay(day,"Day $day",ComposerSessionPurpose.HYPERTROPHY,
                        listOf(CustomExerciseSlot("curl-$day","db_curl",ComposerRole.ACCESSORY,
                            listOf(working.copy(type=DraftSetType.WARMUP,load=load("15"),
                                reps=RepBand(5,5),targetRir=null),working,
                                working.copy(amplifier=CustomSetAmplifier(
                                    CustomAmplifierKind.DROP_SET,6,20,load("15"),30))),
                            pinned=true,coachNote="Keep the exact attachment and tempo")))
                })),mapOf("phase-1" to listOf(CustomWeekSlotOverride(2,0,"curl-0",
                    listOf(working.copy(load=load("25")))))),"Original author notes")
    }
    protected data class Saved(
        val before:CustomTrainingCycle,
        val change:CustomCycleBuilderResult.Proposed,
        val source:NativeCustomAuthoringSnapshot
    )
    protected fun seed(count:Int=2):Saved {
        val owner=checkNotNull(signedIn)
        val context=source()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(owner.ownerKey,0,context))
        if(count>1)assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts().save(owner.ownerKey,"cycle-a",0,cycle("cycle-a")))
        val before=cycle("cycle-b")
        val change=CustomCycleBuilderDomain.editCycleMetadata(before,1,
            "My selected second program","Edited author notes") as CustomCycleBuilderResult.Proposed
        if(count>0) {
            assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts().save(owner.ownerKey,"cycle-b",0,before))
            assertEquals(NativeCustomDraftWriteResult.Committed(2),drafts().save(owner.ownerKey,"cycle-b",1,change.draft))
        }
        return Saved(before,change,context)
    }
    protected fun enter(
        saved:Saved,visible:CustomTrainingCycle?=saved.change.draft,
        seenSource:NativeCustomAuthoringSnapshot?=saved.source,
        undo:CustomCycleDraftReceipt?=saved.change.receipt,
        afterDraftRead:()->Unit={},selection:NativeKotlinProgramSelection?=null
    ):NativeKotlinPlanEntry {
        val owner=signedIn
        fun read()=NativeKotlinPlanEntryReview.review(identity,visible,seenSource,undo,
            readSource={sourceReads++;sources().read(checkNotNull(owner).ownerKey)},
            readDraft={id->draftReads+=id
                drafts().read(checkNotNull(owner).ownerKey,id).also{afterDraftRead()}},
            listDrafts={censusReads++;drafts().listOwned(checkNotNull(owner).ownerKey)},
            selection=selection)
        return if(owner==null)read() else fence().withOwnerTransaction(owner.ownerKey){read()}
    }
    protected fun hashes():Map<String,String> = Files.walk(temp.root.toPath()).use { paths->
        paths.filter{Files.isRegularFile(it)}.toList().associate {
            temp.root.toPath().relativize(it).toString() to MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(it)).joinToString(""){byte->"%02x".format(byte)}
        }
    }

}
