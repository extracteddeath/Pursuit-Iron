package io.github.extracteddeath.pursuitiron.engine;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupLimits;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Explicitly unconnected, read-only sandbox service. The public API never
 * returns training suggestions to Compose and cannot log or persist sessions.
 * Future call sites MUST supply trusted bundle identity and a parity-certified
 * per-feature Kotlin gate before displaying results to an athlete.
 */
public final class CanonicalJsReadOnlyService implements AutoCloseable {
    public enum State {
        UNCONNECTED, VERIFYING, READY_READ_ONLY, UNAVAILABLE, CLOSED
    }

    private static final AtomicBoolean SANDBOX_LEASE = new AtomicBoolean(false);
    private final Context context;
    private final byte[] bundle;
    private final String expectedSha256;
    private final ExecutorService worker =
            Executors.newSingleThreadExecutor(task -> {
                Thread thread = new Thread(task, "pursuit-canonical-readonly");
                thread.setDaemon(true);
                return thread;
            });
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    private volatile State state = State.UNCONNECTED;
    private JavaScriptSandbox sandbox;  // only accessed on worker thread
    private JavaScriptIsolate isolate;   // only accessed on worker thread
    private boolean ownsLease;          // only accessed on worker thread

    public CanonicalJsReadOnlyService(@NonNull Context context,
            @NonNull byte[] bundle, @NonNull String expectedSha256) {
        this.context = Objects.requireNonNull(context).getApplicationContext();
        this.bundle = Objects.requireNonNull(bundle).clone();
        this.expectedSha256 = Objects.requireNonNull(expectedSha256);
    }

    @NonNull public State state() { return state; }

    /** Nonblocking signal that close actually released the process-scoped sandbox. */
    @NonNull public CompletableFuture<Void> whenClosed() { return termination; }

    /**
     * This is transport validation, not an engine prescription.
     * Unknown writes, arbitrary methods and malformed requests fail before
     * JavaScript evaluation. The JS dispatcher independently validates params.
     */
    static JSONObject checkedPreviewRequest(@NonNull String json) throws Exception {
        Objects.requireNonNull(json);
        if (json.getBytes(StandardCharsets.UTF_8).length > 16384)
            throw new IllegalArgumentException("Oversized read-only engine request");
        JSONObject request = new JSONObject(json);
        if (request.length() != 4 || request.getInt("protocolVersion") != 1 ||
                !"progressionPreview".equals(request.getString("method")) ||
                !(request.get("id") instanceof String) ||
                request.getString("id").isEmpty() || request.getString("id").length() > 128 ||
                !(request.get("params") instanceof JSONObject))
            throw new IllegalArgumentException("Only bounded read-only progression previews permitted");
        return request;
    }

    /** Native-bound catalog lookup is limited to canonical IDs; no program or user history. */
    static JSONObject checkedExerciseLookupRequest(@NonNull String json) throws Exception {
        Objects.requireNonNull(json);
        if (json.getBytes(StandardCharsets.UTF_8).length > 16384)
            throw new IllegalArgumentException("Oversized exercise catalog request");
        JSONObject request = new JSONObject(json);
        if (request.length() != 4 || request.getInt("protocolVersion") != 1 ||
                !"exerciseLookup".equals(request.getString("method")) ||
                !(request.get("id") instanceof String) ||
                request.getString("id").isEmpty() || request.getString("id").length() > 128)
            throw new IllegalArgumentException("Only bounded canonical exercise lookups permitted");
        JSONObject params = request.getJSONObject("params");
        if (params.length() != 1 || !params.has("ids"))
            throw new IllegalArgumentException("Lookup must include only IDs");
        JSONArray ids = params.getJSONArray("ids");
        if (ids.length() < 1 || ids.length() > 12)
            throw new IllegalArgumentException("Exercise lookup count out of bounds");
        java.util.HashSet<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < ids.length(); i++) {
            Object raw = ids.get(i);
            if (!(raw instanceof String) ||
                    !((String)raw).matches("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$") ||
                    !seen.add((String)raw))
                throw new IllegalArgumentException("Invalid or repeated canonical exercise ID");
        }
        return request;
    }

    /** No personal context can be supplied to this audited synthetic outline. */
    static JSONObject checkedSyntheticOutlineRequest(@NonNull String json) throws Exception {
        Objects.requireNonNull(json);
        if (json.getBytes(StandardCharsets.UTF_8).length > 16384)
            throw new IllegalArgumentException("Oversized synthetic outline request");
        JSONObject request = new JSONObject(json);
        if (request.length() != 4 || request.getInt("protocolVersion") != 1 ||
                !"workoutOutline".equals(request.getString("method")) ||
                !(request.get("id") instanceof String) ||
                request.getString("id").isEmpty() || request.getString("id").length() > 128)
            throw new IllegalArgumentException("Synthetic outline transport only");
        JSONObject params = request.getJSONObject("params");
        if (params.length() != 2 || !params.has("split") || !params.has("dayIndex") ||
                !(params.get("split") instanceof String) ||
                !(params.get("dayIndex") instanceof Number))
            throw new IllegalArgumentException("Synthetic outline accepts only split and day index");
        String split = params.getString("split");
        int days = "full_body".equals(split) ? 3 :
                "upper_lower".equals(split) ? 4 : "ulppl".equals(split) ? 5 : 0;
        Number raw = (Number) params.get("dayIndex");
        double idx = raw.doubleValue();
        if (days == 0 || !Double.isFinite(idx) || idx != Math.floor(idx) ||
                idx < 0 || idx >= days)
            throw new IllegalArgumentException("Unsupported synthetic outline day");
        return request;
    }


    /**
     * Canonical v13 schema PROBE only. The incoming bytes must have come from
     * a deliberately selected, bounded source; this service has no URI access.
     * Not an importer, persisted revision or permission to display a program.
     */
    static JSONObject checkedBackupSchemaRequest(@NonNull String json) throws Exception {
        Objects.requireNonNull(json);
        if (json.getBytes(StandardCharsets.UTF_8).length > ReadOnlyBackupLimits.MAX_WIRE_BYTES)
            throw new IllegalArgumentException("Oversized canonical backup schema probe");
        JSONObject request = new JSONObject(json);
        if (request.length() != 4 || request.getInt("protocolVersion") != 1 ||
                !"backupSchemaCheck".equals(request.get("method")) ||
                !(request.get("id") instanceof String) ||
                request.getString("id").isEmpty() || request.getString("id").length() > 128)
            throw new IllegalArgumentException("Only read-only schema probes are permitted");
        JSONObject params = request.getJSONObject("params");
        if (params.length() != 1 || !(params.get("rawSnapshot") instanceof String))
            throw new IllegalArgumentException("Backup schema probe accepts only rawSnapshot");
        String raw = params.getString("rawSnapshot");
        if (raw.isEmpty() || raw.length() > ReadOnlyBackupLimits.MAX_SOURCE_CHARACTERS ||
                raw.getBytes(StandardCharsets.UTF_8).length > ReadOnlyBackupLimits.MAX_SOURCE_BYTES)
            throw new IllegalArgumentException("Bounded canonical JSON source required");
        return request;
    }

    /** Dedicated inventory method cannot inherit write/preview authority. */
    static JSONObject checkedBackupInventoryRequest(@NonNull String json) throws Exception {
        JSONObject request = checkedBoundedBackupRequest(json, "backupProgramInventory");
        return request;
    }

    private static JSONObject checkedBoundedBackupRequest(
            @NonNull String json, @NonNull String method) throws Exception {
        Objects.requireNonNull(json);
        if (json.getBytes(StandardCharsets.UTF_8).length > ReadOnlyBackupLimits.MAX_WIRE_BYTES)
            throw new IllegalArgumentException("Oversized backup metadata request");
        JSONObject request = new JSONObject(json);
        if (request.length() != 4 || request.getInt("protocolVersion") != 1 ||
                !method.equals(request.get("method")) ||
                !(request.get("id") instanceof String) ||
                request.getString("id").isEmpty() || request.getString("id").length() > 128)
            throw new IllegalArgumentException("Read-only backup metadata request required");
        JSONObject params = request.getJSONObject("params");
        if (params.length() != 1 || !(params.get("rawSnapshot") instanceof String))
            throw new IllegalArgumentException("Exactly one source field required");
        String raw = params.getString("rawSnapshot");
        if (raw.isEmpty() || raw.length() > ReadOnlyBackupLimits.MAX_SOURCE_CHARACTERS ||
                raw.getBytes(StandardCharsets.UTF_8).length > ReadOnlyBackupLimits.MAX_SOURCE_BYTES)
            throw new IllegalArgumentException("Unbounded source copy");
        return request;
    }

    /**
     * Bounded, SHA-bound, read-only current-store projection. The caller STILL
     * must be an authenticated CURRENT store owner through the Kotlin port.
     * This API has no document/URI access and never authorizes workouts.
     */
    static JSONObject checkedCurrentProgramProjectionRequest(
            @NonNull String json) throws Exception {
        Objects.requireNonNull(json);
        if (json.getBytes(StandardCharsets.UTF_8).length > ReadOnlyBackupLimits.MAX_WIRE_BYTES)
            throw new IllegalArgumentException("Oversized current-program request");
        JSONObject request = new JSONObject(json);
        if (request.length() != 4 || request.getInt("protocolVersion") != 1 ||
                !"currentProgramProjection".equals(request.get("method")) ||
                !(request.get("id") instanceof String) ||
                request.getString("id").isEmpty() ||
                request.getString("id").length() > 128)
            throw new IllegalArgumentException("Read-only current-program projection required");
        JSONObject params = request.getJSONObject("params");
        if (params.length() != 3 ||
                !(params.get("rawSnapshot") instanceof String) ||
                !(params.get("programId") instanceof String) ||
                !(params.get("snapshotSha256") instanceof String))
            throw new IllegalArgumentException("Exactly one bounded source and revision required");
        String raw = params.getString("rawSnapshot");
        String programId = params.getString("programId");
        String sha = params.getString("snapshotSha256");
        byte[] bytes = raw.getBytes(StandardCharsets.UTF_8);
        try {
        if (bytes.length == 0 || bytes.length > ReadOnlyBackupLimits.MAX_SOURCE_BYTES ||
                raw.length() > ReadOnlyBackupLimits.MAX_SOURCE_CHARACTERS ||
                !programId.matches("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$") ||
                !sha.matches("^[0-9a-f]{64}$"))
            throw new IllegalArgumentException("Invalid current-program source bounds");
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        final char[] alphabet = "0123456789abcdef".toCharArray();
        StringBuilder actual = new StringBuilder(64);
        for (byte b : digest) {
            int v = b & 0xff;
            actual.append(alphabet[v >>> 4]).append(alphabet[v & 0x0f]);
        }
        if (!MessageDigest.isEqual(
                actual.toString().getBytes(StandardCharsets.US_ASCII),
                sha.getBytes(StandardCharsets.US_ASCII)))
            throw new SecurityException("Snapshot SHA does not match current-program source");
        return request;
        } finally {
            // The raw String is immutable, but do not retain an extra
            // sensitive UTF-8 byte copy after hashing, even on failure.
            java.util.Arrays.fill(bytes, (byte) 0);
        }
    }

    private static JSONObject call(JavaScriptIsolate isolate, JSONObject request) throws Exception {
        return call(isolate, request, 5);
    }

    private static JSONObject call(JavaScriptIsolate isolate, JSONObject request,
            long timeoutSeconds) throws Exception {
        String method = request.getString("method");
        String entry = ("backupSchemaCheck".equals(method) ||
                "backupProgramInventory".equals(method) ||
                "currentProgramProjection".equals(method))
                ? "PursuitIronBridge.dispatchBackupSchemaJson"
                : "PursuitIronBridge.dispatchEngineJson";
        String script = entry + "(" + JSONObject.quote(request.toString()) + ")";
        String raw = isolate.evaluateJavaScriptAsync(script).get(timeoutSeconds, TimeUnit.SECONDS);
        if (raw.getBytes(StandardCharsets.UTF_8).length > 65536)
            throw new SecurityException("Oversized canonical engine response");
        JSONObject response = new JSONObject(raw);
        if (response.getInt("protocolVersion") != 1 ||
                !request.getString("id").equals(response.getString("id")) ||
                !(response.get("ok") instanceof Boolean))
            throw new SecurityException("Invalid canonical protocol envelope");
        return response;
    }

    private void verifyHandshake() throws Exception {
        JSONObject req = new JSONObject()
                .put("protocolVersion", 1)
                .put("id", "native-service-handshake")
                .put("method", "handshake")
                .put("params", new JSONObject());
        JSONObject response = call(isolate, req);
        if (!response.getBoolean("ok"))
            throw new SecurityException("Canonical handshake failed");
        JSONObject result = response.getJSONObject("result");
        if (result.getInt("protocolVersion") != 1 ||
                !"modules/engine-api.js".equals(result.getString("engineSource")) ||
                result.getBoolean("engineCertified"))
            throw new SecurityException("Incompatible canonical engine identity");
        JSONArray methods = result.getJSONArray("capabilities");
        boolean hasPreview = false, hasLookup = false, hasOutline = false, hasSchemaCheck = false, hasInventory = false, hasCurrent = false;
        for (int i = 0; i < methods.length(); i++) {
            if ("progressionPreview".equals(methods.getString(i))) hasPreview = true;
            if ("exerciseLookup".equals(methods.getString(i))) hasLookup = true;
            if ("workoutOutline".equals(methods.getString(i))) hasOutline = true;
            if ("backupSchemaCheck".equals(methods.getString(i))) hasSchemaCheck = true;
            if ("backupProgramInventory".equals(methods.getString(i))) hasInventory = true;
            if ("currentProgramProjection".equals(methods.getString(i))) hasCurrent = true;
        }
        if (!hasPreview || !hasLookup || !hasOutline || !hasSchemaCheck || !hasInventory || !hasCurrent)
            throw new SecurityException("Canonical read-only bridge capabilities unavailable");
    }

    /** Runs only on the dedicated worker. No automatic retry on failure. */
    private void initialize() throws Exception {
        if (isolate != null) return;
        if (state == State.UNAVAILABLE || state == State.CLOSED)
            throw new IllegalStateException("Canonical sandbox is not available");
        state = State.VERIFYING;
        if (!CanonicalJsSandboxProbe.verifyBundleBytes(bundle, expectedSha256))
            throw new SecurityException("Canonical bundle identity mismatch");
        if (!JavaScriptSandbox.isSupported())
            throw new UnsupportedOperationException("Android JavaScript sandbox unavailable");
        if (!SANDBOX_LEASE.compareAndSet(false, true))
            throw new IllegalStateException("Another Android engine sandbox owns the process");
        ownsLease = true;
        sandbox = JavaScriptSandbox.createConnectedInstanceAsync(context)
                .get(10, TimeUnit.SECONDS);
        isolate = sandbox.createIsolate();
        isolate.evaluateJavaScriptAsync(new String(bundle, StandardCharsets.UTF_8))
                .get(15, TimeUnit.SECONDS);
        verifyHandshake();
        state = State.READY_READ_ONLY;
    }

    /**
     * Enqueue a strictly read-only synthetic progression request; never blocks
     * the UI thread. The caller must decode using EngineJsonCodec and must not
     * bypass the independently certified Kotlin per-feature gate.
     */
    public CompletableFuture<String> previewAsync(@NonNull String requestJson) {
        return submitReadOnly(requestJson, false);
    }

    /** Bounded canonical catalog metadata only; it cannot edit an exercise. */
    public CompletableFuture<String> lookupExercisesAsync(@NonNull String requestJson) {
        return submitReadOnly(requestJson, true);
    }

    public CompletableFuture<String> syntheticOutlineAsync(@NonNull String requestJson) {
        return submitReadOnly(requestJson, 2);
    }

    /** Read-only v13 structural probe. No personal backup storage or UI access. */
    public CompletableFuture<String> checkBackupSchemaAsync(@NonNull String requestJson) {
        return submitReadOnly(requestJson, 3);
    }

    /** Bounded snapshot-only saved-program metadata, no active-store access. */
    public CompletableFuture<String> backupProgramInventoryAsync(@NonNull String requestJson) {
        return submitReadOnly(requestJson, 4);
    }

    /** Source-only projection; no personal store is opened by this worker. */
    public CompletableFuture<String> currentProgramProjectionAsync(
            @NonNull String requestJson) {
        return submitReadOnly(requestJson, 5);
    }

    private CompletableFuture<String> submitReadOnly(
            @NonNull String requestJson, boolean catalog) {
        return submitReadOnly(requestJson, catalog ? 1 : 0);
    }

    /** Modes: progression 0, catalog 1, outline 2, schema 3, inventory 4, current read-only 5. */
    private CompletableFuture<String> submitReadOnly(
            @NonNull String requestJson, int mode) {
        final CompletableFuture<String> outcome = new CompletableFuture<>();
        if (closed.get()) {
            outcome.completeExceptionally(new IllegalStateException("Canonical service is closed"));
            return outcome;
        }
        // Invalid methods are refused before touching the worker or its lease.
        // A bad caller cannot poison an otherwise healthy canonical sandbox.
        final JSONObject validated;
        try {
            validated = mode == 5
                    ? checkedCurrentProgramProjectionRequest(requestJson)
                    : mode == 4
                    ? checkedBackupInventoryRequest(requestJson)
                    : mode == 3
                    ? checkedBackupSchemaRequest(requestJson)
                    : mode == 2
                    ? checkedSyntheticOutlineRequest(requestJson)
                    : mode == 1
                        ? checkedExerciseLookupRequest(requestJson)
                        : checkedPreviewRequest(requestJson);
        } catch (Exception invalidRequest) {
            outcome.completeExceptionally(invalidRequest);
            return outcome;
        }
        try {
            worker.execute(() -> {
                if (closed.get()) {
                    outcome.completeExceptionally(new IllegalStateException("Canonical service is closed"));
                    return;
                }
                try {
                    initialize();
                    JSONObject response = call(isolate, validated, mode == 2 ? 30 : mode == 3 || mode == 4 || mode == 5 ? 12 : 5);
                    if (response.getBoolean("ok")) {
                        JSONObject value = response.getJSONObject("result");
                        if (mode == 5) {
                            JSONObject reference = value.getJSONObject("reference");
                            JSONObject params = validated.getJSONObject("params");
                            if (!value.getBoolean("readOnly") ||
                                    !"modules/engine-api.js:parseStoredData".equals(
                                        value.getString("source")) ||
                                    !params.getString("programId").equals(
                                        reference.getString("programId")) ||
                                    !params.getString("snapshotSha256").equals(
                                        reference.getString("snapshotSha256")) ||
                                    reference.getInt("storeVersion") != 13)
                                throw new SecurityException("Unexpected current-program provenance");
                        } else if (mode == 4) {
                            if (!value.getBoolean("readOnly") ||
                                    !value.getBoolean("snapshotOnly") ||
                                    value.getBoolean("imported") ||
                                    value.getBoolean("approvedForWorkoutExecution") ||
                                    value.getInt("storeVersion") != 13 ||
                                    !"canonical-v13-backup-program-inventory".equals(
                                            value.getString("validation")) ||
                                    !"modules/engine-api.js:parseStoredData".equals(
                                            value.getString("source")) ||
                                    value.getJSONArray("programs").length() > 24)
                                throw new SecurityException("Unexpected program inventory provenance");
                        } else if (mode == 3) {
                            if (!value.getBoolean("readOnly") ||
                                    value.getBoolean("imported") ||
                                    value.getBoolean("approvedForWorkoutExecution") ||
                                    value.getInt("storeVersion") != 13 ||
                                    !"canonical-v13-structure-only".equals(value.getString("validation")) ||
                                    !"modules/engine-api.js:parseStoredData".equals(
                                            value.getString("source")))
                                throw new SecurityException("Canonical schema response cannot grant import");
                        } else if (mode == 2) {
                            if (!value.getBoolean("readOnly") || !value.getBoolean("syntheticOnly") ||
                                    !"modules/engine-api.js:generateNextProgramForShell".equals(
                                            value.getString("source")))
                                throw new SecurityException("Unexpected canonical synthetic outline provenance");
                        } else if (mode == 1) {
                            if (!value.getBoolean("readOnly") ||
                                    !"modules/engine-api.js:EXERCISES".equals(
                                            value.getString("source")))
                                throw new SecurityException("Unexpected canonical catalog provenance");
                        } else if (!value.getBoolean("previewOnly") ||
                                !"modules/engine-api.js:evaluateWorkoutProgression"
                                        .equals(value.getString("source")))
                            throw new SecurityException("Unexpected read-only progression provenance");
                    }
                    if (closed.get())
                        outcome.completeExceptionally(new IllegalStateException("Canonical service closed while evaluating"));
                    else
                        outcome.complete(response.toString());
                } catch (Throwable failure) {
                    state = State.UNAVAILABLE;
                    releaseOnWorker();
                    outcome.completeExceptionally(failure);
                }
            });
        } catch (RejectedExecutionException e) {
            outcome.completeExceptionally(new IllegalStateException("Canonical service stopped",e));
        }
        return outcome;
    }

    /** Close on the serial worker, release the sandbox lease and never replay. */
    private void releaseOnWorker() {
        try {
            if (isolate != null) {
                try { isolate.close(); } catch (RuntimeException ignored) { /* already dead */ }
                finally { isolate = null; }
            }
        } finally {
            try {
                if (sandbox != null) {
                    try { sandbox.close(); } catch (RuntimeException ignored) { /* already dead */ }
                    finally { sandbox = null; }
                }
            } finally {
                if (ownsLease) {
                    ownsLease = false;
                    SANDBOX_LEASE.set(false);
                }
            }
        }
    }

    @Override public void close() {
        if (!closed.compareAndSet(false, true)) return;
        try {
            worker.execute(() -> {
                try { releaseOnWorker(); }
                finally {
                    state = State.CLOSED;
                    termination.complete(null);
                }
            });
        } finally {
            worker.shutdown();
        }
    }
}
