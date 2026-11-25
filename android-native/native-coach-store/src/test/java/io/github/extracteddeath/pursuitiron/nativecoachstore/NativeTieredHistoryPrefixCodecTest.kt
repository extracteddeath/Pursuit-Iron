package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeTieredHistoryPrefixCodecTest {
    private fun n(s:String)=BigDecimal(s)
    private val scope=BasicProgressionScope("athlete","cycle","gym","block",
        "block:d0","press","machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
    private val tier=PercentageTier.PRIMARY
    private val slot="slot-A"
    private val stock=VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,
        n("5"),n("5"),n("300"))
    private fun request(prefix:List<TieredLinearExposure>,id:String="next",
        recovery:Boolean=false)=TieredLinearRequest(id,scope,tier,TieredLinearState(0,n("100")),
        true,prefix,stock,n("5"),1,recovery,slot)
    private fun exposure(r:TieredLinearRequest,order:Long,reps:Int,rir:Int?=null):TieredLinearExposure {
        val recipe=TieredLinearProgressionDomain.review(r) as TieredLinearResult.Candidate
        return TieredLinearExposure(r.nextWorkoutId,order,scope,tier,recipe.state.stage,recipe.targets,
            recipe.targets.map{t->WorkingSetFact(t.ordinal,
                if(reps==0)SetOutcome.FAILED_ATTEMPT else SetOutcome.COMPLETED,
                t.load,reps,rir,t.load,2)},!recipe.recovery,slot,
            recipe.state.load.takeIf{recipe.recovery})
    }
    private fun encode(prefix:List<TieredLinearExposure>)=
        NativeTieredHistoryPrefixCodec.encode(scope,tier,slot,prefix)
    private fun decode(bytes:ByteArray)=NativeTieredHistoryPrefixCodec.decode(bytes,scope,tier,slot)
    private fun hash(prefix:List<TieredLinearExposure>)=
        NativeTieredHistoryPrefixCodec.fingerprint(scope,tier,slot,prefix)
    @Test fun exactPrefixPreservesFailedAttemptsRepOutMissingRirAndRecoveryAcrossColdReplay() {
        val a=exposure(request(emptyList(),"a"),1,0)
        val b=exposure(request(listOf(a),"b"),2,2,0)
        val light=exposure(request(listOf(a,b),"light",true),3,10)
        val prefix=listOf(a,b,light)
        val bytes=encode(prefix)
        val cold=decode(bytes)
        assertEquals(prefix,cold)
        assertArrayEquals(bytes,encode(cold))
        assertEquals(hash(prefix),hash(cold))
        assertEquals(0,cold.first().observations.single().actualReps)
        assertNull(cold.first().observations.single().actualRir)
        assertEquals(0,cold[1].observations.single().actualRir)
        assertTrue(cold[1].issuedTargets.single().repOut)
        assertFalse(cold.last().issuedTargets.single().repOut)
        assertEquals(n("105"),cold.last().recoveryReference)
        val expected=TieredLinearProgressionDomain.review(request(prefix))
        assertEquals(expected,TieredLinearProgressionDomain.review(request(cold)))
        assertEquals(TieredLinearState(1,n("105")),(expected as TieredLinearResult.Candidate).state)
        val corrected=prefix.toMutableList().also{list->
            list[0]=a.copy(observations=a.observations.map{it.copy(
                outcome=SetOutcome.COMPLETED,actualReps=3)})
        }
        assertNotEquals(hash(prefix),hash(corrected))
        assertTrue(TieredLinearProgressionDomain.review(request(corrected)) is TieredLinearResult.Refused)
        assertEquals(expected,TieredLinearProgressionDomain.review(request(decode(bytes))))
    }

    @Test fun physicalScopeAndMalformedOrAmbiguousPrefixRefuse() {
        val a=exposure(request(emptyList(),"a"),1,3)
        val bytes=encode(listOf(a))
        for(changed in listOf(scope.copy(gymId="other"),scope.copy(trainingDayId="block:d2"),
            scope.copy(machineInstanceId="machine-B"),scope.copy(unit=LoadUnit.KG))) {
            assertThrows(Exception::class.java){
                NativeTieredHistoryPrefixCodec.decode(bytes,changed,tier,slot)}
        }
        assertThrows(Exception::class.java){
            NativeTieredHistoryPrefixCodec.decode(bytes,scope,PercentageTier.SECONDARY,slot)}
        assertThrows(Exception::class.java){
            NativeTieredHistoryPrefixCodec.decode(bytes,scope,tier,"other-slot")}
        assertThrows(Exception::class.java){decode(bytes+byteArrayOf(0))}
        assertThrows(Exception::class.java){decode(bytes.copyOf(bytes.size-1))}
        assertThrows(Exception::class.java){encode(emptyList())}
        assertThrows(Exception::class.java){encode(listOf(a,a))}
        assertThrows(Exception::class.java){encode(listOf(a.copy(observations=emptyList())))}
        assertThrows(Exception::class.java){encode(listOf(a.copy(observations=a.observations.map{
            it.copy(outcome=SetOutcome.SKIPPED)})))}
        assertThrows(Exception::class.java){encode(listOf(a.copy(observations=a.observations.map{
            it.copy(actualLoad=n("95"))})))}
        assertThrows(Exception::class.java){encode(listOf(a.copy(comparableTraining=false)))}
        assertThrows(Exception::class.java){encode(listOf(a.copy(issuedTargets=a.issuedTargets.map{
            it.copy(repOut=false)})))}
        assertThrows(Exception::class.java){encode(listOf(a.copy(scope=scope.copy(machineInstanceId="machine-B"))))}
    }
}
