import java.nio.file.Path;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.util.Arrays;
import java.util.HexFormat;

/** JDK 17 source-file tool. Only the public certificate digest reaches stdout. */
class KotlinLabSigningKeyCheck {
    public static void main(String[] args) {
        char[] storePassword = System.getenv("PURSUIT_LAB_STORE_PASSWORD").toCharArray();
        char[] keyPassword = System.getenv("PURSUIT_LAB_KEY_PASSWORD").toCharArray();
        try {
            KeyStore store = KeyStore.getInstance(Path.of(args[0]).toFile(), storePassword);
            // Match the Java keystore operation used by Android signing.
            // keytool silently ignores a different PKCS12 key password.
            if (!(store.getKey(args[1], keyPassword) instanceof PrivateKey)) {
                throw new IllegalArgumentException("Private key required");
            }
            byte[] certificate = store.getCertificate(args[1]).getEncoded();
            System.out.println(HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(certificate)));
        } catch (Exception failure) {
            System.err.println("Private signing key validation failed.");
            System.exit(1);
        } finally {
            Arrays.fill(storePassword, '\0');
            Arrays.fill(keyPassword, '\0');
        }
    }
}
