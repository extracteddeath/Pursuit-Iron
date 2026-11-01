package io.github.extracteddeath.pursuitiron.engine;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

public class CanonicalJsSandboxProbeTest {
    @Test public void stableBundleIdentityAndRejection() throws Exception {
        byte[] bundle = "var PursuitIronBridge={};".getBytes(StandardCharsets.UTF_8);
        String digest = CanonicalJsSandboxProbe.sha256(bundle);
        assertEquals(64, digest.length());
        assertTrue(digest.matches("^[0-9a-f]{64}$"));
        assertTrue(CanonicalJsSandboxProbe.verifyBundleBytes(bundle, digest));
        assertFalse(CanonicalJsSandboxProbe.verifyBundleBytes(bundle, "not-a-digest"));
        assertFalse(CanonicalJsSandboxProbe.verifyBundleBytes(bundle, ""));
        assertFalse(CanonicalJsSandboxProbe.verifyBundleBytes(bundle, digest.toUpperCase(java.util.Locale.ROOT)));
        assertFalse(CanonicalJsSandboxProbe.verifyBundleBytes(new byte[0],
                CanonicalJsSandboxProbe.sha256(new byte[0])));
        assertFalse(CanonicalJsSandboxProbe.verifyBundleBytes(
                "var PursuitIronBridge={x:1};".getBytes(StandardCharsets.UTF_8), digest));
        assertArrayEquals(bundle, "var PursuitIronBridge={};".getBytes(StandardCharsets.UTF_8));
    }

    @Test public void noImplicitUtf8NormalizationOrOversizedBundles() throws Exception {
        byte[] a = "var café='é';".getBytes(StandardCharsets.UTF_8);
        byte[] b = "var cafe='e';".getBytes(StandardCharsets.UTF_8);
        assertTrue(CanonicalJsSandboxProbe.verifyBundleBytes(a, CanonicalJsSandboxProbe.sha256(a)));
        assertFalse(CanonicalJsSandboxProbe.verifyBundleBytes(b, CanonicalJsSandboxProbe.sha256(a)));
        byte[] oversized = new byte[12 * 1024 * 1024 + 1];
        Arrays.fill(oversized, (byte) 'x');
        assertFalse(CanonicalJsSandboxProbe.verifyBundleBytes(
                oversized, CanonicalJsSandboxProbe.sha256(oversized)));
    }
}
