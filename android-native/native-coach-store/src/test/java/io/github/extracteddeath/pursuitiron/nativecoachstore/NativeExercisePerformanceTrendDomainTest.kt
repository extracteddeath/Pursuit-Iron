package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Display evidence only; these fixtures do NOT claim owner authentication. */
class NativeExercisePerformanceTrendDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",1,"program-1",1,"gym-1",1,"catalog-1","rules-1")
    private fun manifest(order:Int)=KotlinHistoryManifestEntry(
        order.toLong(),"workout-$order","stored-$order","finish-$order",3,0)
    private fun load(
        value:String="100",unit:LoadUnit=LoadUnit.LB,
        basis:LoadBasis=LoadBasis.DUMBBELL_PER_HAND
    )=DraftSetLoad.Verified(unit,basis,n(value))
    private fun composer(
        order:Int,vararg facts:KotlinHistoricalSetFact
    )=NativeKotlinMixedHistoryCompletion.Composer(
        manifest(order),KotlinWorkoutHistoryEvidence(
            source,"workout-$order","finish-$order",3,facts.toList(),emptyList()))
    private fun fact(
        order:Int,visit:Int=1,reps:Int=8,weight:String?="100",
        unit:LoadUnit?=LoadUnit.LB,issued:DraftSetLoad=load(),
        type:DraftSetType=DraftSetType.WORKING,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        machineId:String?=null
    )=KotlinHistoricalSetFact(
        "db_row","set-$order-$visit",type,0,issued,RepBand(6,10),2,
        outcome,if(outcome==KotlinJournalSetOutcome.SKIPPED)null
            else KotlinPerformedSet(weight?.let(::n),unit,reps,2,machineId),
        "event-$order-$visit",null)
    private fun custom(
        order:Int,vararg visits:CustomHistoricalVisitFact
    )=NativeKotlinMixedHistoryCompletion.Custom(
        manifest(order),CustomWorkoutHistoricalEvidence(
            "athlete-1","workout-$order","cycle-1",2,"finish-$order",
            3,visits.toList()))
    private fun customVisit(
        order:Int,slot:String,reps:Int,weight:String?,
        issued:DraftSetLoad,
        part:CustomWorkoutVisitPart=CustomWorkoutVisitPart.MAIN,
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED
    )=CustomHistoricalVisitFact(
        slot,"db_row","set-$order-$slot",part,DraftSetType.WORKING,
        issued,RepBand(6,10),2,
        if(part==CustomWorkoutVisitPart.FOLLOW_UP)CustomAmplifierKind.DROP_SET
            else null,
        outcome,if(outcome==KotlinJournalSetOutcome.SKIPPED)null
            else KotlinPerformedSet(weight?.let(::n),
                (issued as? DraftSetLoad.Verified)?.unit,reps,2),
        "event-$order-$slot-$part")
    private fun timeline(vararg visits:NativeKotlinMixedHistoryCompletion)=
        NativeKotlinMixedHistoryTimeline("athlete-1",7,visits.toList())
    private fun trend(
        timeline:NativeKotlinMixedHistoryTimeline,
        unit:LoadUnit=LoadUnit.LB
    )=NativeExercisePerformanceTrendDomain.project(timeline,"db_row",unit)

    @Test fun normalizedPhysicalWeightMatchesAcrossUnitsButNeverAcrossBasis() {
        val input=timeline(
            composer(1,fact(1,weight="100")),
            composer(2,fact(2,weight="50",unit=LoadUnit.KG,
                issued=load("50",LoadUnit.KG))),
            composer(3,fact(3,weight="150",issued=load(
                "150",LoadUnit.LB,LoadBasis.CABLE_STACK))))
        val result=trend(input,LoadUnit.KG)
        assertEquals(2,result.series.size)
        val dumbbell=result.series.single {
            it.key.basis==LoadBasis.DUMBBELL_PER_HAND
        }
        assertEquals(NativeExerciseRepWindow.SIX_TO_TEN,dumbbell.key.reps)
        assertEquals(listOf(1L,2L),dumbbell.points.map {it.completionOrder})
        assertEquals(0,dumbbell.points.first().actualLoad!!
            .compareTo(n("45.35923700")))
        assertEquals(n("50"),dumbbell.points.last().actualLoad)
        val cable=result.series.single {it.key.basis==LoadBasis.CABLE_STACK}
        assertEquals(listOf(3L),cable.points.map {it.completionOrder})
        assertFalse(result.canPublishPr)
        assertFalse(result.canPrescribeWorkout)
        assertFalse(result.canEditHistory)
    }

    @Test fun machineStackTrendsNeverMixAAndBAcrossGyms() {
        fun stackFact(order:Int,machine:String?,weight:String)=fact(order,
            weight=weight,issued=load(weight,LoadUnit.LB,LoadBasis.MACHINE_STACK),
            machineId=machine)
        fun atGym(order:Int,gym:String,machine:String?,weight:String):
            NativeKotlinMixedHistoryCompletion.Composer =
            NativeKotlinMixedHistoryCompletion.Composer(manifest(order),
                KotlinWorkoutHistoryEvidence(source.copy(gymId=gym),
                    "workout-$order","finish-$order",3,
                    listOf(stackFact(order,machine,weight)),emptyList()))
        val result=trend(timeline(
            atGym(1,"gym-1","A","200"),
            atGym(2,"gym-1","B","140"),
            atGym(3,"gym-2","A","190"),
            atGym(4,"gym-1","A","205"),
            atGym(5,"gym-1",null,"100")
        ))
        assertEquals(4,result.series.size)
        val a=result.series.single {
            it.key.gymId=="gym-1" && it.key.machineInstanceId=="A"
        }
        assertEquals(listOf(1L,4L),a.points.map{it.completionOrder})
        assertEquals(listOf(n("200"),n("205")),a.points.map{it.actualLoad})
        assertEquals(n("140"),result.series.single {
            it.key.gymId=="gym-1" && it.key.machineInstanceId=="B"
        }.points.single().actualLoad)
        assertEquals(n("190"),result.series.single {
            it.key.gymId=="gym-2" && it.key.machineInstanceId=="A"
        }.points.single().actualLoad)
        assertEquals(n("100"),result.series.single {
            it.key.gymId=="gym-1" && it.key.machineInstanceId==null
        }.points.single().actualLoad)
        assertFalse(result.canPublishPr)
    }

    @Test fun portableDumbbellsStillShareComparableGymIndependentTrend() {
        val otherGym=NativeKotlinMixedHistoryCompletion.Composer(manifest(2),
            KotlinWorkoutHistoryEvidence(source.copy(gymId="gym-2"),
                "workout-2","finish-2",3,listOf(fact(2,weight="110")),emptyList()))
        val result=trend(timeline(composer(1,fact(1,weight="100")),otherGym))
        assertEquals(1,result.series.size)
        assertNull(result.series.single().key.gymId)
        assertNull(result.series.single().key.machineInstanceId)
        assertEquals(listOf(1L,2L),
            result.series.single().points.map{it.completionOrder})
    }

    @Test fun bestPerSessionNeverMixesRepRangesOrIssuedTargets() {
        val input=timeline(
            composer(1,
                fact(1,1,reps=5,weight="140"),
                fact(1,2,reps=8,weight="90"),
                fact(1,3,reps=9,weight="105")),
            composer(2,fact(2,1,reps=10,weight="110")),
            composer(3,fact(3,1,reps=13,weight="80")))
        val result=trend(input)
        val middle=result.series.single {
            it.key.reps==NativeExerciseRepWindow.SIX_TO_TEN
        }
        assertEquals(listOf(n("105"),n("110")),
            middle.points.map {it.actualLoad})
        assertEquals(listOf(9,10),middle.points.map {it.performedReps})
        assertEquals(1,result.series.single {
            it.key.reps==NativeExerciseRepWindow.ONE_TO_FIVE
        }.points.size)
        assertEquals(1,result.series.single {
            it.key.reps==NativeExerciseRepWindow.ELEVEN_TO_FIFTEEN
        }.points.size)
    }

    @Test fun skippedWarmupsFailedAndAmplifierFollowupsDoNotRaiseTrend() {
        val input=timeline(
            composer(1,
                fact(1,1,8,"95"),
                fact(1,2,8,"500",type=DraftSetType.WARMUP),
                fact(1,3,8,"600",
                    outcome=KotlinJournalSetOutcome.FAILED_ATTEMPT)),
            custom(2,
                customVisit(2,"main",8,"100",load()),
                customVisit(2,"amp",8,"900",load(),
                    CustomWorkoutVisitPart.FOLLOW_UP)),
            composer(3,fact(3,outcome=KotlinJournalSetOutcome.SKIPPED)))
        val values=trend(input).series.single().points
        assertEquals(listOf(1L,2L),values.map {it.completionOrder})
        assertEquals(listOf(n("95"),n("100")),values.map {it.actualLoad})
    }

    @Test fun unknownOrIncompatibleLoadAndRepEvidenceIsExcluded() {
        val input=timeline(
            composer(1,fact(1,weight="100",unit=LoadUnit.KG)),
            composer(2,fact(2,reps=0)),
            composer(3,fact(3,reps=101)),
            composer(4,fact(4,weight=null)),
            composer(5,fact(5,weight="-5")),
            composer(6,fact(6,weight="100.1234")),
            composer(7,fact(7,weight="90")))
        val result=trend(input)
        assertEquals(listOf(7L),result.series.single().points.map {
            it.completionOrder
        })
    }

    @Test fun bodyweightSetsShowOnlyCompletedUnassistedRepPerformance() {
        val body=DraftSetLoad.ExplicitBodyweight
        val input=timeline(
            composer(1,fact(1,1,6,null,null,body)),
            composer(2,fact(2,1,7,null,null,body),
                fact(2,2,9,null,null,body)),
            composer(3,fact(3,1,12,null,null,body)),
            composer(4,fact(4,1,8,"10",LoadUnit.LB,body)))
        val result=trend(input)
        val mid=result.series.single {
            it.key.reps==NativeExerciseRepWindow.SIX_TO_TEN
        }
        assertTrue(mid.key.bodyweightOnly)
        assertEquals(listOf(6,9),mid.points.map{it.performedReps})
        assertTrue(mid.points.all{it.actualLoad==null})
        assertEquals(listOf(3L),result.series.single{
            it.key.reps==NativeExerciseRepWindow.ELEVEN_TO_FIFTEEN
        }.points.map{it.completionOrder})
        assertEquals(3,result.series.sumOf{it.points.size})
    }

    @Test fun correctedCustomEvidenceAndOriginalInputRemainSeparate() {
        val edited=customVisit(1,"slot-a",7,"125",load(),
            outcome=KotlinJournalSetOutcome.COMPLETED)
        val recorded=timeline(custom(1,edited))
        val result=trend(recorded)
        assertEquals(n("125"),result.series.single().points.single().actualLoad)
        assertEquals("event-1-slot-a-MAIN",
            recorded.completions.single().let{
                (it as NativeKotlinMixedHistoryCompletion.Custom)
                    .evidence.visits.single().effectiveEventId
            })
        assertEquals(7L,recorded.manifestRevision)
    }
}
