package io.github.extracteddeath.pursuitiron.engine;

import static org.junit.Assert.*;

import android.content.Context;
import androidx.javascriptengine.JavaScriptSandbox;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Executes the actual canonical JS IIFE on an Android emulator/device.
 * No personal data, no workout log and no write-capable protocol.
 */
@RunWith(AndroidJUnit4.class)
public final class CanonicalJsSandboxInstrumentedTest {
    private static byte[] asset(Context c, String path) throws Exception {
        try (InputStream in = c.getAssets().open(path);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int n;
            while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toByteArray();
        }
    }

    @Test public void canonicalProgressionRunsInAndroidSandbox() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("WebView-provided sandbox must be available for Android runtime certification",
                JavaScriptSandbox.isSupported());
        byte[] bundle = asset(test, "canonical-engine.js");
        String pinnedHash = new String(asset(test,"canonical-engine.sha256"),
                StandardCharsets.UTF_8).trim();
        assertTrue("Local bundle bytes must match temporary build manifest",
                CanonicalJsSandboxProbe.verifyBundleBytes(bundle,pinnedHash));
        JSONObject answer = new CanonicalJsSandboxProbe(app).inspectHandshake(bundle,pinnedHash);
        assertEquals("modules/engine-api.js",answer.getString("engineSource"));
        assertEquals(1,answer.getInt("protocolVersion"));
        assertFalse("Handshake never certifies training predictions",
                answer.getBoolean("engineCertified"));
        assertTrue(answer.getJSONArray("capabilities").length() >= 3);
        JSONObject fixtures = new JSONObject(new String(asset(test,
                "progression-golden.json"), StandardCharsets.UTF_8));
        try {
            int verified = new CanonicalJsSandboxProbe(app)
                    .verifyGoldenProgression(bundle,pinnedHash,fixtures);
            assertEquals("All synthetic canonical fixtures must execute on Android",
                    fixtures.getJSONArray("cases").length(),verified);
        } catch (AssertionError error) {
            if (error.getMessage() == null ||
                    !error.getMessage().contains("ENGINE_UNAVAILABLE")) throw error;
            // Test-only synthetic diagnostic; never send user records to this bundle.
            byte[] diagnostic = asset(test, "sandbox-diagnostic.js");
            String pin = new String(asset(test, "sandbox-diagnostic.sha256"),
                    StandardCharsets.UTF_8).trim();
            String details = diagnoseSyntheticFixture(app, diagnostic, pin,
                    fixtures.getJSONArray("cases").getJSONObject(0).getJSONObject("params"));
            throw new AssertionError("Canonical synthetic fixture error: " + details, error);
        }

    }
    /**
     * Exists only in the Android instrumentation APK: debug diagnostics are
     * intentionally absent from the production engine-runtime library.
     */
    private static String diagnoseSyntheticFixture(Context context, byte[] script, String pin,
            JSONObject params) throws Exception {
        assertTrue(CanonicalJsSandboxProbe.verifyBundleBytes(script,pin));
        assertTrue(JavaScriptSandbox.isSupported());
        String input = params.toString();
        assertTrue(input.getBytes(StandardCharsets.UTF_8).length <= 16384);
        try (JavaScriptSandbox sandbox =
                    JavaScriptSandbox.createConnectedInstanceAsync(context)
                            .get(10, java.util.concurrent.TimeUnit.SECONDS);
             androidx.javascriptengine.JavaScriptIsolate isolate = sandbox.createIsolate()) {
            isolate.evaluateJavaScriptAsync(new String(script,StandardCharsets.UTF_8))
                    .get(15,java.util.concurrent.TimeUnit.SECONDS);
            String expression = "PursuitIronDiagnostic.diagnoseProgressionJson(" +
                    JSONObject.quote(input) + ")";
            String result = isolate.evaluateJavaScriptAsync(expression)
                    .get(5,java.util.concurrent.TimeUnit.SECONDS);
            return new JSONObject(result).toString();
        }
    }

    @Test public void auditedSyntheticWorkoutOutlineUsesNativeReadOnlyEngine() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        byte[] bundle = asset(test, "canonical-engine.js");
        String pin = new String(asset(test,"canonical-engine.sha256"),
                StandardCharsets.UTF_8).trim();
        CanonicalJsReadOnlyService worker = new CanonicalJsReadOnlyService(app,bundle,pin);
        try {
            String id = "android-outline-emulator";
            String request = io.github.extracteddeath.pursuitiron.domain.WorkoutOutlineCodec.INSTANCE
                    .encodeOutline(id,"full_body",0);
            String json = worker.syntheticOutlineAsync(request)
                    .get(45,java.util.concurrent.TimeUnit.SECONDS);
            JSONObject packet = new JSONObject(json);
            assertTrue(packet.getBoolean("ok"));
            JSONObject result = packet.getJSONObject("result");
            assertTrue(result.getBoolean("readOnly"));
            assertTrue(result.getBoolean("syntheticOnly"));
            assertEquals("modules/engine-api.js:generateNextProgramForShell",
                    result.getString("source"));
            assertTrue(result.getJSONArray("exercises").length() >= 1);
            assertTrue(io.github.extracteddeath.pursuitiron.domain.WorkoutOutlineCodec.INSTANCE
                    .decodeOutline(json,id,"full_body",0)
                    instanceof io.github.extracteddeath.pursuitiron.domain.EngineResponse.Success);
            String forbidden = request.replace("workoutOutline","deleteHistory");
            assertTrue(worker.syntheticOutlineAsync(forbidden).isCompletedExceptionally());
            assertEquals(CanonicalJsReadOnlyService.State.READY_READ_ONLY,worker.state());
        } finally {
            worker.close();
            worker.whenClosed().get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(CanonicalJsReadOnlyService.State.CLOSED,worker.state());
    }


    @Test public void canonicalV13SchemaProbeUsesRealAndroidSandboxWithoutLeakingRecords()
            throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        byte[] bundle = asset(test, "canonical-engine.js");
        String pin = new String(asset(test,"canonical-engine.sha256"),
                StandardCharsets.UTF_8).trim();
        CanonicalJsReadOnlyService service = new CanonicalJsReadOnlyService(app,bundle,pin);
        try {
            String privateMarker = "synthetic-private-program-name";
            JSONObject source = new JSONObject()
                    .put("v",13).put("savedAt",100)
                    .put("saved",new org.json.JSONArray()
                            .put(new JSONObject()
                                    .put("id","synthetic-program")
                                    .put("updatedAt",42)
                                    .put("name",privateMarker)
                                    .put("config",new JSONObject().put("goal","hypertrophy"))
                                    .put("weeks",4)
                                    .put("days",new org.json.JSONArray()
                                            .put(new JSONObject().put("id","day-1")
                                                    .put("label","Demo")
                                                    .put("exercises",new org.json.JSONArray())))))
                    .put("history",new org.json.JSONArray())
                    .put("cycles",new org.json.JSONArray())
                    .put("custom",new org.json.JSONArray())
                    .put("gyms",new org.json.JSONArray())
                    .put("tombs",new JSONObject())
                    .put("perf",new JSONObject());
            String requestId = "android-schema-1";
            JSONObject request = new JSONObject()
                    .put("protocolVersion",1)
                    .put("id",requestId)
                    .put("method","backupSchemaCheck")
                    .put("params",new JSONObject().put("rawSnapshot",source.toString()));
            String wire = service.checkBackupSchemaAsync(request.toString())
                    .get(30,java.util.concurrent.TimeUnit.SECONDS);
            JSONObject envelope = new JSONObject(wire);
            assertTrue("Canonical schema probe failed in actual Android V8", envelope.getBoolean("ok"));
            JSONObject value = envelope.getJSONObject("result");
            assertEquals("modules/engine-api.js:parseStoredData",value.getString("source"));
            assertTrue(value.getBoolean("readOnly"));
            assertFalse(value.getBoolean("imported"));
            assertFalse(value.getBoolean("approvedForWorkoutExecution"));
            assertEquals(13,value.getInt("storeVersion"));
            assertEquals(1,value.getJSONObject("counts").getInt("saved"));
            assertFalse("Private synthetic marker leaked into result",wire.contains(privateMarker));
            assertTrue(io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupSchemaCodec.INSTANCE
                    .decode(wire,requestId)
                    instanceof io.github.extracteddeath.pursuitiron.domain.EngineResponse.Success);
            assertEquals(CanonicalJsReadOnlyService.State.READY_READ_ONLY,service.state());

            // Canonical parse rejection stays a returned error, not a partial import.
            JSONObject invalid = new JSONObject(source.toString())
                    .put("history","not-an-array");
            request.put("id","android-schema-rejected")
                    .put("params",new JSONObject().put("rawSnapshot",invalid.toString()));
            JSONObject rejected = new JSONObject(service.checkBackupSchemaAsync(request.toString())
                    .get(30,java.util.concurrent.TimeUnit.SECONDS));
            assertFalse(rejected.getBoolean("ok"));
            assertEquals("SCHEMA_REJECTED",rejected.getJSONObject("error").getString("code"));
            assertEquals(CanonicalJsReadOnlyService.State.READY_READ_ONLY,service.state());

            // A write method must be rejected on the Java call boundary.
            request.put("method","deleteHistory");
            assertTrue(service.checkBackupSchemaAsync(request.toString())
                    .isCompletedExceptionally());
            assertEquals(CanonicalJsReadOnlyService.State.READY_READ_ONLY,service.state());
        } finally {
            service.close();
            service.whenClosed().get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(CanonicalJsReadOnlyService.State.CLOSED,service.state());
    }

    @Test public void readOnlyServiceReusesVerifiedSandboxAndCloses() throws Exception {
        Context test = InstrumentationRegistry.getInstrumentation().getContext();
        Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        byte[] bundle = asset(test, "canonical-engine.js");
        String pin = new String(asset(test,"canonical-engine.sha256"),
                StandardCharsets.UTF_8).trim();
        JSONObject golden = new JSONObject(new String(asset(test,"progression-golden.json"),
                StandardCharsets.UTF_8));
        JSONObject first = golden.getJSONArray("cases").getJSONObject(0);
        CanonicalJsReadOnlyService service = new CanonicalJsReadOnlyService(app,bundle,pin);
        assertEquals(CanonicalJsReadOnlyService.State.UNCONNECTED,service.state());
        JSONObject request = new JSONObject()
                .put("protocolVersion",1).put("id","service-case-1")
                .put("method","progressionPreview")
                .put("params",first.getJSONObject("params"));
        try {
            String result = service.previewAsync(request.toString())
                    .get(35,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue("Read-only service returned a failure",new JSONObject(result).getBoolean("ok"));
            assertEquals(CanonicalJsReadOnlyService.State.READY_READ_ONLY,service.state());
            assertTrue(io.github.extracteddeath.pursuitiron.domain.EngineJsonCodec.INSTANCE
                    .decodeProgressionPreview(result,"service-case-1")
                    instanceof io.github.extracteddeath.pursuitiron.domain.EngineResponse.Success);
            request.put("id","service-case-2");
            String repeated = service.previewAsync(request.toString())
                    .get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(new JSONObject(repeated).getBoolean("ok"));
            assertEquals("service-case-2",new JSONObject(repeated).getString("id"));
            assertEquals(CanonicalJsReadOnlyService.State.READY_READ_ONLY,service.state());
            // Same verified isolate, separate bounded read-only canonical catalog lookup.
            java.util.List<String> exerciseIds = java.util.Arrays.asList(
                    "bb-bench", "inc-db-press", "not-a-real-exercise");
            String requestId = "service-catalog-3";
            JSONObject catalogRequest = new JSONObject()
                    .put("protocolVersion",1)
                    .put("id",requestId)
                    .put("method","exerciseLookup")
                    .put("params",new JSONObject()
                            .put("ids",new org.json.JSONArray(exerciseIds)));
            String catalogJson = service.lookupExercisesAsync(catalogRequest.toString())
                    .get(10,java.util.concurrent.TimeUnit.SECONDS);
            JSONObject catalogEnvelope = new JSONObject(catalogJson);
            assertTrue(catalogEnvelope.getBoolean("ok"));
            JSONObject catalogPayload = catalogEnvelope.getJSONObject("result");
            assertEquals("modules/engine-api.js:EXERCISES",catalogPayload.getString("source"));
            assertTrue(catalogPayload.getBoolean("readOnly"));
            assertEquals("bb-bench",catalogPayload.getJSONArray("exercises")
                    .getJSONObject(0).getString("id"));
            assertEquals("Barbell Bench Press",catalogPayload.getJSONArray("exercises")
                    .getJSONObject(0).getString("name"));
            assertEquals("not-a-real-exercise",catalogPayload
                    .getJSONArray("missingIds").getString(0));
            assertTrue(io.github.extracteddeath.pursuitiron.domain.ExerciseCatalogCodec.INSTANCE
                    .decodeLookup(catalogJson,requestId,exerciseIds)
                    instanceof io.github.extracteddeath.pursuitiron.domain.EngineResponse.Success);
            JSONObject forbidden = new JSONObject(catalogRequest.toString())
                    .put("method","deleteHistory");
            java.util.concurrent.CompletableFuture<String> refused =
                    service.lookupExercisesAsync(forbidden.toString());
            assertTrue("Write-capable methods are refused before JS execution",
                    refused.isCompletedExceptionally());
            assertEquals("Invalid input must not poison the verified sandbox",
                    CanonicalJsReadOnlyService.State.READY_READ_ONLY,service.state());
        } finally {
            service.close();
            service.whenClosed().get(10,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(CanonicalJsReadOnlyService.State.CLOSED,service.state());
        assertTrue(service.previewAsync(request.toString()).isCompletedExceptionally());
    }


}
