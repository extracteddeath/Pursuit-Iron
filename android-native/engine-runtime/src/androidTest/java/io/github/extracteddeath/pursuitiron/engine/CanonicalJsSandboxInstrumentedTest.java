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
            String details = new CanonicalJsSandboxProbe(app)
                    .inspectSyntheticFailure(diagnostic,pin,
                            fixtures.getJSONArray("cases").getJSONObject(0).getJSONObject("params"));
            throw new AssertionError("Canonical synthetic fixture error: " + details, error);
        }

    }
}
