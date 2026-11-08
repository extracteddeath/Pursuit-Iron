# Stable private signing for the independent Kotlin Lab

## Why this is a required gate

The first two Kotlin Lab test APKs use package
`io.github.extracteddeath.pursuitiron.kotlinlab`. They were signed using
**different ephemeral GitHub debug keys**, so they cannot update each other.
Uninstalling to switch signatures erases that test app's private data.

The new **stable-key** test channel intentionally uses a distinct package:
`io.github.extracteddeath.pursuitiron.kotlinlabstable` (displayed as
**Pursuit Iron Lab Stable**). It can be installed **beside** either old Lab
APK and will not erase the old app's saved training. Its future updates must
keep both this same package ID and the same stable certificate.

Do **not** treat a higher version code as a way to change signing identity.
We cannot recover those previous ephemeral runner keys.

Starting with the next device-test milestone, the independent Kotlin Lab
APK workflow **fails closed** if a stable signing key has not been configured.
This is intentional: ordinary source-only engine tests still run normally
without the key. No new unsigned or ephemerally signed APK is published.

### One-time owner setup (on a trusted computer)

Use Python 3.9+, JDK 17+ (`java` and `keytool`) and GitHub CLI `gh`, signed
into GitHub as the repository owner. From the rebuild checkout, run:

```sh
python3 android-native/tools/kotlin_lab_signing.py configure \
  --create "$HOME/.pursuit-iron-signing/pursuit-iron-kotlin-lab.p12"
```

The command asks for a private password, creates an independent
**development-only** key outside the source checkout, and sends the four
repository secrets directly to GitHub through standard input. The password
and encoded keystore do not appear in the command line or console output.
It also saves the **public** certificate SHA-256 as repository Actions
variable `KOTLIN_LAB_CERT_SHA256`. Back up the keystore and password privately.
**Never commit the key, share it in chat, or use the production app key.**

If a key already exists, reuse it instead of generating another:

```sh
python3 android-native/tools/kotlin_lab_signing.py configure \
  --keystore /absolute/private/path/pursuit-iron-kotlin-lab.p12
```

Supply `--alias ACTUAL_ALIAS` if it differs from `pursuit-kotlin-lab`.
For a previously configured key whose secrets predate the public pin, add
`--adopt-existing-key` **once**, using that same original keystore. The command
will not create a key over existing/partial configuration, overwrite a local
keystore, or replace a pinned certificate with a different key. If an upload
fails halfway through, retain the local keystore and retry `--keystore` with
the same file. Do not retry with `--create`.

The four private Actions secrets remain:

| Secret | Value supplied by the command |
| --- | --- |
| `KOTLIN_LAB_KEYSTORE_B64` | One-line base64 encoding of the binary keystore |
| `KOTLIN_LAB_STORE_PASSWORD` | Keystore password |
| `KOTLIN_LAB_KEY_ALIAS` | Private-key alias |
| `KOTLIN_LAB_KEY_PASSWORD` | Private-key password; the generated PKCS12 key uses the store password |

This setup configures signing; it does not trigger an APK or production release.
The current ChatGPT GitHub connection cannot administer these private settings,
so the owner runs this command locally. No password or key needs to be sent in chat.

### What the device-build workflow verifies

Before assembly, the workflow requires all four secrets **and the public pin**.
It validates both passwords by opening the actual private key through the JDK
keystore API and compares its certificate with the pin. Checking only a public
certificate or using `keytool` with a PKCS12 key would not reliably reject a
wrong private-key password. The decoded file has private permissions and is
removed after the build, including failure paths.

After assembly, Android `apksigner verify --verbose --print-certs` must validate
the real APK with exactly the pinned certificate. The APK manifest must have
the stable package, the milestone version code and the independent Kotlin Lab
launcher. A wrong signer, package, version or launcher prevents artifact upload.

The artifact includes a public `kotlin-lab-build-receipt.json` with the APK
SHA-256, certificate SHA-256, package, version, source commit and size. It
explicitly records device acceptance as **NOT_TESTED**. Preserve the **same key,
alias and public pin** for later Lab builds; version codes increase with the
same device workflow's run number. A new key cannot be substituted for an update.

### Coexistence and data preservation

The previously distributed v1 and v2 APKs use ephemeral signatures, but
**the first stable-key build has a different package ID**, so it can
coexist with either old build. You do **not** need to uninstall v2 to
install the new stable-key test app. The existing v2 workout history
remains inside v2. It is **not automatically transferred** to the stable
app. Do not delete the old app until there is an explicitly tested
export/migration path for any records you need.

Subsequent builds using the same stable key and package ID can be installed
as upgrades, preserving the stable app's own data.

The production Play Store application and other developer's native preview
have different package IDs and signing identities; this procedure does not
change or overwrite them.

### Acceptance checks

- A new APK workflow without all four private secrets and the public pin must fail **before**
  assembling a distributable artifact.
- Source-only Kotlin tests and Android compilation must not require secrets.
- The APK should have exactly one Kotlin Lab launcher and correct
  `.kotlinlabstable` package identity, pinned certificate and expected version.
- A mismatched private-key password or certificate must fail before assembly;
  a final APK certificate/identity mismatch must fail before artifact upload.
- Source CI exercises real disposable JDK keys without using repository signing
  secrets. It checks partial configuration, wrong aliases/passwords/certificates,
  refusal to rotate a pinned key, interrupted upload/retry and artifact rejection.
- Two sequential stable-signed APKs with increasing version codes must show
  the same signing certificate SHA-256 and install as an in-place Android
  update while retaining a saved test journal.
- Deleting or rotating the signing key requires a new app identity or an
  explicit approved Android-compatible signing-key rotation process; do not
  silently replace it.

The signing configuration is infrastructure for safer device testing.
It does not constitute proof of coaching accuracy or production readiness.
