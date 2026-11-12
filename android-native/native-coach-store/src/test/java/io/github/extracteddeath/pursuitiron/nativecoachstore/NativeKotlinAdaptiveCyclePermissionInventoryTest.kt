package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CustomCycleChangeKind
import java.nio.file.Files
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Grants are not approvals of workouts or unattended plan changes.
 * Recovery must reproduce every exact athlete choice, including revocations.
 */
class NativeKotlinAdaptiveCyclePermissionInventoryTest {
    @get:Rule val temp=TemporaryFolder()
    private val root get()=temp.root.toPath()
    private var signed=NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{signed}
    private fun store()=NativeKotlinAdaptiveCyclePermissionFileStore(
        root,NativeKotlinApprovalRevisionFence(root),identity)
    private fun inventory()=store().inventoryOwner("owner-1")

    @Test fun exactPermissionGrantsAndRevocationsSurviveColdReopen() {
        assertEquals(NativeAdaptivePermissionInventory.Verified(emptyList()),
            inventory())
        val active=setOf(CustomCycleChangeKind.SPLIT,
            CustomCycleChangeKind.EXERCISES)
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            store().confirm("owner-1","active-cycle",0,active,true))
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            store().confirm("owner-1","retired-cycle",0,
                setOf(CustomCycleChangeKind.DAY_COUNT),true))
        assertEquals(NativeAdaptivePermissionWrite.Committed(2),
            store().revoke("owner-1","retired-cycle",1))
        val facts=inventory() as NativeAdaptivePermissionInventory.Verified
        assertEquals(2,facts.records.size)
        assertEquals(NativeAdaptivePermissionInventoryFact(
            "active-cycle",1,active,false),
            facts.records.single{it.cycleId=="active-cycle"})
        assertEquals(NativeAdaptivePermissionInventoryFact(
            "retired-cycle",2,null,true),
            facts.records.single{it.cycleId=="retired-cycle"})
        assertEquals(NativeAdaptivePermissionRead.Revoked(2),
            store().read("owner-1","retired-cycle"))
        assertEquals(active,(store().read("owner-1","active-cycle")
            as NativeAdaptivePermissionRead.Found).receipt.allowedKinds)
        assertEquals(NativeAdaptivePermissionInventory.InvalidOrForeign,
            store().inventoryOwner("other-owner"))
    }

    @Test fun corruptOrRenamedPhysicalPermissionCannotDisappearFromRestore() {
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            store().confirm("owner-1","cycle-1",0,
                setOf(CustomCycleChangeKind.BLOCKS),true))
        val path=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("adapt-permission-")}
                .findFirst().orElseThrow()
        }
        val original=Files.readAllBytes(path)
        val broken=original.copyOf()
        broken[broken.lastIndex]=(broken.last().toInt() xor 1).toByte()
        Files.write(path,broken)
        assertEquals(NativeAdaptivePermissionInventory.InvalidOrForeign,
            inventory())
        assertArrayEquals(broken,Files.readAllBytes(path))
        Files.write(path,original)
        val renamed=path.resolveSibling(
            "adapt-permission-"+"a".repeat(64)+".bin")
        Files.move(path,renamed)
        assertEquals(NativeAdaptivePermissionInventory.InvalidOrForeign,
            inventory())
        Files.move(renamed,path)
        signed=NativeKotlinOwnerIdentity("owner-1","athlete-other")
        assertEquals(NativeAdaptivePermissionInventory.InvalidOrForeign,
            inventory())
        signed=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(1,(inventory() as NativeAdaptivePermissionInventory.Verified)
            .records.size)
    }

    @Test fun incompleteTransactionsUnknownFilesAndOtherAthletesBlockRestore() {
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            store().confirm("owner-1","cycle-1",0,
                setOf(CustomCycleChangeKind.REPS),true))
        val pending=root.resolve("adapt-permission-"+"a".repeat(64)+".pending")
        Files.write(pending,byteArrayOf(1))
        assertEquals(NativeAdaptivePermissionInventory.InvalidOrForeign,
            inventory())
        Files.delete(pending)
        val other=root.resolve("adapt-permission-"+"b".repeat(64)+".invalid")
        Files.write(other,byteArrayOf(1))
        assertEquals(NativeAdaptivePermissionInventory.InvalidOrForeign,
            inventory())
        Files.delete(other)
        signed=NativeKotlinOwnerIdentity("other-owner","other-athlete")
        assertEquals(NativeAdaptivePermissionWrite.Committed(1),
            store().confirm("other-owner","cycle-2",0,
                setOf(CustomCycleChangeKind.LOADS),true))
        signed=NativeKotlinOwnerIdentity("owner-1","athlete-1")
        assertEquals(NativeAdaptivePermissionInventory.InvalidOrForeign,
            inventory())
        val file=Files.list(root).use{stream->
            stream.filter{it.fileName.toString().startsWith("adapt-permission-") &&
                it.fileName.toString().endsWith(".bin")}
                .findFirst().orElseThrow()
        }
        val link=root.resolve("adapt-permission-"+"c".repeat(64)+".bin")
        Files.createSymbolicLink(link,file)
        assertEquals(NativeAdaptivePermissionInventory.InvalidOrForeign,
            inventory())
        Files.delete(link)
    }
}
