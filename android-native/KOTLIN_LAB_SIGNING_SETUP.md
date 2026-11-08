# Stable private signing for the independent Kotlin Lab

## Why this is a required gate

The isolated Kotlin Lab APK currently uses package
`io.github.extracteddeath.pursuitiron.kotlinlab`. The first two test APKs
were automatically signed using **different ephemeral GitHub debug keys**.
Android will **not** update one from the other. Uninstalling to switch
signatures erases that test app's private stored workouts and settings.

Do **not** treat a higher version code as a way to change signing identity.
We cannot recover those previous ephemeral runner keys.

Starting with the next device-test milestone, the independent Kotlin Lab
APK workflow **fails closed** if a stable signing key has not been configured.
This is intentional: ordinary source-only engine tests still run normally
without the key, but no additional incompatible test APK is published.

### One-time owner setup (on a trusted computer)

1. Install an up-to-date JDK providing `keytool`. Generate an independent
   **development-only** key, not your Play Store release-signing key:

   ```sh
   keytool -genkeypair -v \
     -keystore pursuit-iron-kotlin-lab.jks \
     -storetype PKCS12 -keyalg RSA -keysize 3072 \
     -validity 10000 -alias pursuit-kotlin-lab
   ```

   Choose a strong private password and record it securely. The keystore is
   private. **Never commit it, share it in chat, or use the production app key.**
   Keep a backed-up copy in a secure password manager/encrypted storage.

2. Under **Pursuit-Iron → Settings → Secrets and variables → Actions**, create
   these four repository secrets:
   - `KOTLIN_LAB_KEYSTORE_B64`: the entire base64 encoding of the binary
     `.jks` file, on **one line**.
   - `KOTLIN_LAB_STORE_PASSWORD`: the keystore password.
   - `KOTLIN_LAB_KEY_ALIAS`: `pursuit-kotlin-lab` (or the actual alias).
   - `KOTLIN_LAB_KEY_PASSWORD`: the private-key password, often identical
     to the store password for a PKCS12 key.

   For the first value, on Linux use
   `base64 -w0 pursuit-iron-kotlin-lab.jks`; on macOS use
   `base64 < pursuit-iron-kotlin-lab.jks | tr -d '\\n'`.
   Paste the output only into the GitHub **secret** field. Never paste
   the encoded key into a repository file or log.

3. Test a future **explicitly approved** Kotlin Lab milestone commit.
   The workflow decodes the key into the temporary runner directory,
   validates the alias, and configures the Android debug signing certificate
   **only for that APK assembly**. The key material is never uploaded in
   the APK artifact and should not be printed in logs.

4. Preserve this **same key and alias** for subsequent Kotlin Lab releases
   and increment versionCode. A new key would again prevent in-place
   upgrades and risk test-data loss.

### The first transition remains unavoidable

The previously distributed v1 and v2 Kotlin Lab test APKs use ephemeral
signatures. The **first** stable-key build cannot upgrade either of them.
Before uninstalling an earlier test build, consider its stored workout data.
Do not delete useful records until an explicit export/migration route exists.
Once the stable-key build is installed, future builds using this same key
can be installed as upgrades, preserving normal app-private data.

The production Play Store application and other developer's native preview
have different package IDs and signing identities; this procedure does not
change or overwrite them.

### Acceptance checks

- A new APK workflow without all four private secrets must fail **before**
  assembling a distributable artifact.
- Source-only Kotlin tests and Android compilation must not require secrets.
- The APK should have exactly one Kotlin Lab launcher and correct
  `.kotlinlab` package identity.
- Two sequential stable-signed APKs with increasing version codes must show
  the same signing certificate SHA-256 and install as an in-place Android
  update while retaining a saved test journal.
- Deleting or rotating the signing key requires a new app identity or an
  explicit approved Android-compatible signing-key rotation process; do not
  silently replace it.

The signing configuration is infrastructure for safer device testing.
It does not constitute proof of coaching accuracy or production readiness.
