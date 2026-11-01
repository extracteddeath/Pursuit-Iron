package io.github.extracteddeath.pursuitiron.engine;

import android.content.Context;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.javascriptengine.JavaScriptIsolate;
import androidx.javascriptengine.JavaScriptSandbox;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Unconnected Android runtime verification seam. It accepts only an externally
 * pinned, local canonical bundle and hard-coded read-only protocol methods.
 * NOT the real workout gateway and NOT permission to write user data.
 */
public final class CanonicalJsSandboxProbe {
    private static final int MAX_BUNDLE_BYTES = 12 * 1024 * 1024;
    private static final int MAX_REQUEST_BYTES = 16 * 1024;
    private static final String SHA256 = "^[0-9a-f]{64}$";
    private final Context context;

    public CanonicalJsSandboxProbe(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    public static String sha256(@NonNull byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte b : digest)
            result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    public static boolean verifyBundleBytes(@NonNull byte[] bytes, @NonNull String expectedSha256)
            throws Exception {
        return bytes.length > 0 && bytes.length <= MAX_BUNDLE_BYTES
                && expectedSha256.matches(SHA256)
                && sha256(bytes).equals(expectedSha256);
    }

    private static void requireSandbox(@NonNull byte[] bundle, @NonNull String expectedSha256)
            throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Sandbox operations are forbidden on the UI thread");
        if (!verifyBundleBytes(bundle, expectedSha256))
            throw new SecurityException("Canonical bundle checksum mismatch");
        if (!JavaScriptSandbox.isSupported())
            throw new UnsupportedOperationException("Android JS sandbox unavailable on this device");
    }

    private static JSONObject request(@NonNull String id, @NonNull String method,
            @NonNull JSONObject params) throws Exception {
        JSONObject request = new JSONObject();
        request.put("protocolVersion", 1);
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        return request;
    }

    /** Evaluate a JSON string literal, never caller-supplied JavaScript source. */
    private static JSONObject dispatch(@NonNull JavaScriptIsolate isolate,
            @NonNull JSONObject request) throws Exception {
        String input = request.toString();
        if (input.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES)
            throw new IllegalArgumentException("Request exceeds transport limit");
        String script = "PursuitIronBridge.dispatchEngineJson(" + JSONObject.quote(input) + ")";
        String output = isolate.evaluateJavaScriptAsync(script).get(5, TimeUnit.SECONDS);
        JSONObject answer = new JSONObject(output);
        if (answer.optInt("protocolVersion") != 1 ||
                !request.getString("id").equals(answer.optString("id")))
            throw new SecurityException("Engine returned invalid envelope");
        return answer;
    }

    private static JSONObject handshake(@NonNull JavaScriptIsolate isolate) throws Exception {
        JSONObject response = dispatch(isolate, request("native-handshake-only",
                "handshake", new JSONObject()));
        if (!response.optBoolean("ok"))
            throw new SecurityException("Incompatible canonical engine handshake");
        JSONObject value = response.getJSONObject("result");
        if (value.optInt("protocolVersion") != 1 ||
                !"modules/engine-api.js".equals(value.optString("engineSource")) ||
                value.optBoolean("engineCertified", true))
            throw new SecurityException("Unexpected canonical engine identity");
        JSONArray capabilities = value.getJSONArray("capabilities");
        boolean hasHandshake = false, hasCatalog = false, hasProgression = false;
        for (int i = 0; i < capabilities.length(); i++) {
            String method = capabilities.getString(i);
            if ("handshake".equals(method)) hasHandshake = true;
            if ("catalogSummary".equals(method)) hasCatalog = true;
            if ("progressionPreview".equals(method)) hasProgression = true;
        }
        if (!hasHandshake || !hasCatalog || !hasProgression)
            throw new SecurityException("Canonical bridge capabilities incomplete");
        return value;
    }

    /**
     * First native runtime probe: confirms real isolated execution of the
     * canonical IIFE but deliberately returns engineCertified=false.
     */
    public JSONObject inspectHandshake(@NonNull byte[] bundle, @NonNull String expectedSha256)
            throws Exception {
        requireSandbox(bundle, expectedSha256);
        try (JavaScriptSandbox sandbox =
                    JavaScriptSandbox.createConnectedInstanceAsync(context).get(10, TimeUnit.SECONDS);
             JavaScriptIsolate isolate = sandbox.createIsolate()) {
            isolate.evaluateJavaScriptAsync(new String(bundle, StandardCharsets.UTF_8))
                    .get(15, TimeUnit.SECONDS);
            return handshake(isolate);
        }
    }

    /** JSON structural equality, including array order and exact numeric values. */
    private static boolean jsonEquals(Object expected, Object actual) throws Exception {
        if (expected instanceof JSONObject && actual instanceof JSONObject) {
            JSONObject a = (JSONObject) expected, b = (JSONObject) actual;
            if (a.length() != b.length()) return false;
            Iterator<String> keys = a.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (!b.has(key) || !jsonEquals(a.get(key), b.get(key))) return false;
            }
            return true;
        }
        if (expected instanceof JSONArray && actual instanceof JSONArray) {
            JSONArray a = (JSONArray) expected, b = (JSONArray) actual;
            if (a.length() != b.length()) return false;
            for (int i = 0; i < a.length(); i++)
                if (!jsonEquals(a.get(i), b.get(i))) return false;
            return true;
        }
        if (expected instanceof Number && actual instanceof Number)
            return Double.compare(((Number) expected).doubleValue(),
                    ((Number) actual).doubleValue()) == 0;
        return Objects.equals(expected, actual);
    }

    /**
     * Runs every synthetic golden case in one actual Android sandbox. The
     * fixtures live in immutable test assets, not in user history. Returning
     * a passing count is NOT permission to enable a training feature.
     */
    public int verifyGoldenProgression(@NonNull byte[] bundle, @NonNull String expectedSha256,
            @NonNull JSONObject golden) throws Exception {
        if (golden.optInt("schemaVersion") != 1 ||
                !"progressionPreview".equals(golden.optString("feature")))
            throw new IllegalArgumentException("Unsupported progression fixture schema");
        JSONArray cases = golden.getJSONArray("cases");
        if (cases.length() < 7 || cases.length() > 64)
            throw new IllegalArgumentException("Progression fixture set is incomplete or oversized");
        requireSandbox(bundle, expectedSha256);
        try (JavaScriptSandbox sandbox =
                    JavaScriptSandbox.createConnectedInstanceAsync(context).get(10, TimeUnit.SECONDS);
             JavaScriptIsolate isolate = sandbox.createIsolate()) {
            isolate.evaluateJavaScriptAsync(new String(bundle, StandardCharsets.UTF_8))
                    .get(15, TimeUnit.SECONDS);
            handshake(isolate);
            for (int i = 0; i < cases.length(); i++) {
                JSONObject fixture = cases.getJSONObject(i);
                String id = fixture.getString("id");
                if (!id.matches("^[a-zA-Z0-9][a-zA-Z0-9_-]{0,79}$"))
                    throw new IllegalArgumentException("Bad golden fixture identifier");
                JSONObject response = dispatch(isolate, request(id, "progressionPreview",
                        fixture.getJSONObject("params")));
                if (!response.optBoolean("ok"))
                    throw new AssertionError("Canonical progression failed: " + id);
                JSONObject result = response.getJSONObject("result");
                if (!result.optBoolean("previewOnly") ||
                        !"modules/engine-api.js:evaluateWorkoutProgression".equals(
                                result.optString("source")))
                    throw new SecurityException("Noncanonical engine result: " + id);
                JSONObject expected = fixture.getJSONObject("expected");
                Iterator<String> keys = expected.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (!result.has(key) || !jsonEquals(expected.get(key), result.get(key)))
                        throw new AssertionError("Android canonical progression drift: " +
                                id + " field " + key);
                }
            }
            JSONObject blocked = dispatch(isolate, request("native-denied-method",
                    "deleteHistory", new JSONObject()));
            if (blocked.optBoolean("ok") ||
                    !"UNKNOWN_METHOD".equals(blocked.getJSONObject("error").optString("code")))
                throw new SecurityException("Write-capable bridge method was exposed");
            return cases.length();
        }
    }
}
