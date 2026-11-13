# Pursuit Iron — native Android early-launch beta development

**Current destination:** a lean Android beta with **full functional parity**
between the Kotlin training engine and the active web training engine before
release. The beta UI can be smaller; supported engine behavior cannot be
silently dropped. See [Launch beta scope](LAUNCH_BETA_SCOPE.md) for the
authoritative parity, reliability, device acceptance and release gates.

MainActivity launches the Kotlin-owned journey directly. The beta app's
declared Gradle dependencies are `:domain-contract`, `:kotlin-engine` and
`:native-coach-store`; it does **not** directly depend on `:engine-runtime`.
The legacy `:engine-runtime` module is still included as a separate Gradle
project for historical preview/comparison tooling. Gradle project inclusion
alone does not mean the module is on the app's runtime classpath: verify the
actual `:app:dependencies --configuration releaseRuntimeClasspath` graph
before changing the module inventory or claiming legacy-free packaging.

Historical JS-backed workout data is not silently imported. No APK or beta
release is authorized by this document; persisted lifecycle, paired engine
evidence, signed install/update and physical Android acceptance remain gates.

## Development entry points

- [Beta acceptance scope and release order](LAUNCH_BETA_SCOPE.md)
- [Historical native preview and JS-era development notes](HISTORICAL_NATIVE_DEVELOPMENT.md)

The historical archive is intentionally not a build or release checklist.
Routine Kotlin compilation and source CI may run without packaging an APK.
Only the independently gated device milestone may produce a test build, and
beta release still requires parity, persisted lifecycle and device evidence.
