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
import java.util.concurrent.TimeUnit;

/**
 * Worker-thread, read-only first execution boundary. No sandbox code is
 * invoked from the Compose app. No persisted data or Android objects are
 * exposed to JavaScript. Each probe closes all resources after one handshake.
 *
 * A successful handshake is not behavioral parity, certification or
 * authorization to display real workout prescriptions.
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
        for (byte b : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }

    public static boolean verifyBundleBytes(@NonNull byte[] bytes, @NonNull String expectedSha256)
            throws Exception {
        return bytes.length > 0 && bytes.length <= MAX_BUNDLE_BYTES
                && expectedSha256.matches(SHA256)
                && sha256(bytes).equals(expectedSha256);
    }

    /**
     * Call only on a background thread. AndroidX supports one JS sandbox per
     * process; the host will need a shared service when this becomes active.
     *
     * @return a strict handshake fact, never a parity certificate.
     */
    public JSONObject inspectHandshake(@NonNull byte[] bundle, @NonNull String expectedSha256)
            throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper())
            throw new IllegalStateException("Sandbox operations are forbidden on the UI thread");
        if (!verifyBundleBytes(bundle, expectedSha256))
            throw new SecurityException("Canonical bundle checksum mismatch");
        if (!JavaScriptSandbox.isSupported())
            throw new UnsupportedOperationException("Android JS sandbox unavailable on this device");

        try (JavaScriptSandbox sandbox =
                    JavaScriptSandbox.createConnectedInstanceAsync(context).get(10, TimeUnit.SECONDS);
             JavaScriptIsolate isolate = sandbox.createIsolate()) {
            isolate.evaluateJavaScriptAsync(new String(bundle, StandardCharsets.UTF_8))
                    .get(15, TimeUnit.SECONDS);
            JSONObject request = new JSONObject();
            request.put("protocolVersion", 1);
            request.put("id", "native-handshake-only");
            request.put("method", "handshake");
            request.put("params", new JSONObject());
            String input = request.toString();
            if (input.getBytes(StandardCharsets.UTF_8).length > MAX_REQUEST_BYTES)
                throw new IllegalArgumentException("Request exceeds the transport limit");
            // Encode a JSON string literal; never evaluate caller-supplied JS.
            String expression = "PursuitIronBridge.dispatchEngineJson(" +
                    JSONObject.quote(input) + ")";
            String output = isolate.evaluateJavaScriptAsync(expression).get(5, TimeUnit.SECONDS);
            JSONObject response = new JSONObject(output);
            if (response.optInt("protocolVersion") != 1 ||
                    !"native-handshake-only".equals(response.optString("id")) ||
                    !response.optBoolean("ok")) {
                throw new SecurityException("Incompatible canonical engine handshake");
            }
            JSONObject value = response.getJSONObject("result");
            if (value.optInt("protocolVersion") != 1 ||
                    !"modules/engine-api.js".equals(value.optString("engineSource")) ||
                    value.optBoolean("engineCertified", true))
                throw new SecurityException("Unexpected engine handshake identity");
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
    }
}
