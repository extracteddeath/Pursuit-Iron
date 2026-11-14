package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import java.util.zip.ZipInputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NativeLegacyMuscleCoverageTest {
    @get:Rule val temp=TemporaryFolder()
    private val owner=NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity{owner}

    private fun restoreFixture(resource:String,sha:String):Path {
        val root=temp.newFolder("legacy").toPath()
        val text=checkNotNull(javaClass.getResourceAsStream(
            "/$resource")).use{it.readBytes()}
        assertEquals(sha,
            MessageDigest.getInstance("SHA-256").digest(text)
                .joinToString(""){"%02x".format(it.toInt() and 255)})
        ZipInputStream(ByteArrayInputStream(Base64.getMimeDecoder().decode(text))).use{zip->
            while(true) {
                val entry=zip.nextEntry ?:break
                val path=root.resolve(entry.name).normalize()
                require(path.startsWith(root) && !entry.isDirectory)
                Files.write(path,zip.readBytes())
            }
        }
        return root
    }
    private fun content(root:Path):Map<String,String> = Files.walk(root).use{paths->
        paths.filter{Files.isRegularFile(it)}.toList().associate {
            root.relativize(it).toString() to Base64.getEncoder().encodeToString(Files.readAllBytes(it))
        }
    }

    @Test fun actualV8StoreColdOpensWithOriginalApprovalsHistoryAndExactBytes() {
        assertFrozen("legacy-v8-sixteen-muscle-store.zip.base64",
            "b085e12305e59ee848b2e54f46ea5f84c1ee6547b23097b73e2ca1d5cec1d942",
            293,"v8",TrainingMuscleCoverage.legacyV1)
    }

    @Test fun actualV9StoreColdOpensWithoutSilentlyUpgradingItsEighteenMuscleApproval() {
        assertFrozen("legacy-v9-eighteen-muscle-store.zip.base64",
            "5ebc95e852a30ea65758d03f8737f26040e13306180dc8cfd584a2f206a8f9a8",301,"v9",TrainingMuscleCoverage.legacyV2)
    }

    private fun assertFrozen(resource:String,sha:String,catalogSize:Int,
        revision:String,scope:List<TrainingMuscle>) {
        val root=restoreFixture(resource,sha)
        val before=content(root)
        assertEquals(21,before.size)
        val audit=NativeKotlinStagedCoreStoreAudit.inspect(root,owner)
        assertTrue(audit.toString(),audit is NativeKotlinStagedCoreAuditResult.Verified)
        val verified=audit as NativeKotlinStagedCoreAuditResult.Verified
        assertEquals(4,verified.finishedWorkouts)
        assertEquals(4,verified.customIssued)
        assertEquals(0,verified.customUnfinished)
        val fence=NativeKotlinApprovalRevisionFence(root)
        val drafts=NativeCustomCycleDraftFileStore(root,fence,identity)
        val sources=NativeKotlinCustomAuthoringSourceFileStore(root,fence,identity)
        val source=(sources.read(owner.ownerKey) as NativeAuthoringSourceRead.Found).source
        assertEquals(catalogSize,source.catalog.size)
        val program=(drafts.read(owner.ownerKey,"generated-first")
            as NativeCustomDraftReadResult.Found).draft
        val consent=NativeKotlinCustomCycleSignoffFileStore(root,fence,identity,drafts,sources)
        val recovered=consent.recoverReviewedInputs(owner.ownerKey,"generated-first")
        assertTrue(recovered.toString(),recovered is NativeCustomSavedCoachReview.Ready)
        val inputs=(recovered as NativeCustomSavedCoachReview.Ready).inputs
        val policy=inputs.policiesByPhase.getValue(TrainingPhase.HYPERTROPHY)
        assertEquals(revision,policy.revision)
        assertEquals(scope,
            policy.selectedBounds.map{it.muscle})
        val signed=consent.read(owner.ownerKey,"generated-first",inputs)
        assertTrue(signed.toString(),signed is NativeCustomSignoffRead.Found && signed.isCurrent)
        assertEquals(program.revision,
            (signed as NativeCustomSignoffRead.Found).receipt.cycleRevision)
        assertEquals(before,content(root))
    }

    @Test fun policyCodecPreservesLegacyAndNewScopesWithoutFillingOrDroppingRows() {
        for(scope in listOf(TrainingMuscleCoverage.legacyV1,TrainingMuscleCoverage.legacyV2,
            TrainingMuscleCoverage.current)) {
            val bounds=scope.reversed().map{DeclaredMuscleDoseBounds(it,
                BigDecimal("0.00"),BigDecimal("3.50"),BigDecimal("12.000"),0)}
            val policy=AuthoredGoalProgramPolicy("explicit","r1","Reviewed exact coverage",
                "athlete-1","profile-1",AthleteTrainingGoal.HYPERTROPHY,
                AthleteExperience.ADVANCED,ComposerSplit.FULL_BODY,TrainingPhase.HYPERTROPHY,
                3,bounds,bounds,mapOf(ComposerSessionPurpose.HYPERTROPHY to 3))
            val inputs=CustomCoachAuditInputs(mapOf(TrainingPhase.HYPERTROPHY to policy),
                mapOf(0 to 900,2 to 900,4 to 900))
            val bytes=NativeCustomCoachAuditInputsCodec.encode(inputs)
            val decoded=NativeCustomCoachAuditInputsCodec.decode(bytes)
            assertEquals(inputs,decoded)
            assertArrayEquals(bytes,NativeCustomCoachAuditInputsCodec.encode(decoded))
            val partial=policy.copy(selectedBounds=bounds.drop(1))
            assertTrue(runCatching{NativeCustomCoachAuditInputsCodec.encode(inputs.copy(
                policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to partial)))}.isFailure)
        }
    }
}
