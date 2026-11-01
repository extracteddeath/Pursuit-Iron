package io.github.extracteddeath.pursuitiron.engine;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
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

    private static JSONObject call(JavaScriptIsolate isolate, JSONObject request) throws Exception {
        String script = "PursuitIronBridge.dispatchEngineJson(" +
                JSONObject.quote(request.toString()) + ")";
        String raw = isolate.evaluateJavaScriptAsync(script).get(5, TimeUnit.SECONDS);
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
        boolean hasPreview = false, hasLookup = false;
        for (int i = 0; i < methods.length(); i++) {
            if ("progressionPreview".equals(methods.getString(i))) hasPreview = true;
            if ("exerciseLookup".equals(methods.getString(i))) hasLookup = true;
        }
        if (!hasPreview || !hasLookup)
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

    private CompletableFuture<String> submitReadOnly(
            @NonNull String requestJson, boolean catalog) {
        final CompletableFuture<String> outcome = new CompletableFuture<>();
        if (closed.get()) {
            outcome.completeExceptionally(new IllegalStateException("Canonical service is closed"));
            return outcome;
        }
        // Invalid methods are refused before touching the worker or its lease.
        // A bad caller cannot poison an otherwise healthy canonical sandbox.
        final JSONObject validated;
        try {
            validated = catalog
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
                    JSONObject response = call(isolate, validated);
                    if (response.getBoolean("ok")) {
                        JSONObject value = response.getJSONObject("result");
                        if (catalog) {
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
