package io.github.extracteddeath.pursuitiron

import android.content.Context
import io.github.extracteddeath.pursuitiron.domain.BackupSavedProgramInventory
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupSchemaResult
import io.github.extracteddeath.pursuitiron.domain.UnverifiedBackupFingerprint
import io.github.extracteddeath.pursuitiron.engine.CanonicalJsReadOnlyService
import io.github.extracteddeath.pursuitiron.engine.CanonicalJsSandboxProbe
import io.github.extracteddeath.pursuitiron.engine.SandboxBackupSchemaInspector
import io.github.extracteddeath.pursuitiron.engine.SandboxWorkoutOutline
import io.github.extracteddeath.pursuitiron.engine.SandboxCanonicalWorkoutStoreEngine
import io.github.extracteddeath.pursuitiron.engine.SandboxCanonicalCurrentPlanProjector
import io.github.extracteddeath.pursuitiron.domain.WorkoutOutline
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.InputStream

/**
 * Only accepts LOCAL, app-packaged debug assets produced from an independently
 * verified canonical source snapshot. No URL or user-supplied script/manifest.
 *
 * The default source checkout intentionally contains no such bundled asset,
 * so creation stays unavailable without verified assets. Backup consumers see
 * metadata probes only; process-owned onboarding can prepare a first program.
 * This worker never receives file handles or enables workout logging.
 */
internal class PackagedReadOnlySchemaVerifier private constructor(
    private val worker: CanonicalJsReadOnlyService
) : CanonicalBackupSchemaVerifier, CanonicalSyntheticOutlineReader, AutoCloseable {
    private val schema = SandboxBackupSchemaInspector(worker)

    private val outlines = SandboxWorkoutOutline(worker)
    // The backup interface never exposes these capabilities. The process-owned
    // onboarding flow uses the same verified worker, without another sandbox.
    internal val programEngine by lazy { SandboxCanonicalWorkoutStoreEngine(worker) }
    internal val currentPlanProjector by lazy { SandboxCanonicalCurrentPlanProjector(worker) }

    override suspend fun inspectSyntheticOutline(split: String, dayIndex: Int): Result<WorkoutOutline> =
        outlines.inspect(split, dayIndex)

    override suspend fun inspect(
        exactSmallCopy: ByteArray,
        fingerprint: UnverifiedBackupFingerprint
    ): Result<ReadOnlyBackupSchemaResult> = schema.inspect(exactSmallCopy, fingerprint)

    override suspend fun inspectPrograms(
        exactSmallCopy: ByteArray,
        fingerprint: UnverifiedBackupFingerprint
    ): Result<BackupSavedProgramInventory> =
        schema.inspectPrograms(exactSmallCopy, fingerprint)

    override fun close() { worker.close() }

    companion object {
        private const val SCRIPT = "canonical-readonly-engine.js"
        private const val MANIFEST = "canonical-readonly-manifest.json"
        private val sha = Regex("^[0-9a-f]{64}$")

        private fun readBounded(stream: InputStream, max: Int): ByteArray = stream.use { input ->
            val output = ByteArrayOutputStream()
            val bytes = ByteArray(32 * 1024)
            var size = 0
            while (true) {
                val count = input.read(bytes)
                if (count == -1) break
                if (count == 0 || size > max - count)
                    throw IllegalArgumentException("Invalid packaged engine asset")
                output.write(bytes, 0, count)
                size += count
            }
            require(size > 0)
            output.toByteArray()
        }

        fun openIfPackaged(context: Context): PackagedReadOnlySchemaVerifier? {
            // No file assets means no canonical engine. This is the ordinary
            // Android Studio/development preview behavior, not an error.
            val binary = try {
                context.assets.open(SCRIPT).use { readBounded(it, 12 * 1024 * 1024) }
            } catch (_: FileNotFoundException) { return null }
              catch (_: Exception) { return null }
            val worker = try {
                val metadata = context.assets.open(MANIFEST).use {
                    val raw = readBounded(it, 16 * 1024)
                    JSONObject(String(raw, Charsets.UTF_8))
                }
                require(metadata.getInt("schemaVersion") == 1 &&
                    metadata.getInt("protocolVersion") == 1 &&
                    metadata.getString("canonicalSource") == "modules/engine-api.js" &&
                    metadata.get("approvedForWorkoutExecution") === false &&
                    metadata.get("androidSandboxParityVerified") === false &&
                    sha.matches(metadata.getString("canonicalSourceDigest")) &&
                    sha.matches(metadata.getString("fixtureDigest")) &&
                    metadata.getJSONObject("bundler").getString("globalName") == "PursuitIronBridge" &&
                    metadata.getInt("bundleSizeBytes") == binary.size)
                val expected = metadata.getString("bundleSha256")
                require(sha.matches(expected))
                require(CanonicalJsSandboxProbe.verifyBundleBytes(binary, expected))
                CanonicalJsReadOnlyService(context.applicationContext,binary,expected)
            } catch (_: Exception) { return null }
            return PackagedReadOnlySchemaVerifier(worker)
        }
    }
}
