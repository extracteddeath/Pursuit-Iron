# Native migration checkpoint — 2026-11-01

Branch: `architecture/android-compose-foundation`.

The source journey covers native onboarding, generated review and explicit
program save, engine-issued working sets and myo/drop extensions, durable
logging, interruption recovery and canonical completion. This checkpoint adds
one-shot deload execution and atomic consumption. It is not device acceptance.

## One-shot deload owner

`modules/training-domain/one-shot-deload.js` owns choice validation, load scaling
and consumption. Canonical suggestions and final prescriptions use the same
equipment boundary. First-use estimates and percentage targets are reduced once;
extensions derive from the reduced working load. Decision traces retain the
explicit deload reason and final issued load. Ordinary progression is unchanged.
The web finish path uses the same consumption helper.

The native bridge accepts valid numeric factors for known non-assisted lifts.
Its completion candidate merges history and consumed choices into one snapshot.
Only exercises represented by canonical performance consume their choice;
fully skipped exercises and unrelated choices remain armed. Per-program clocks
advance even when finish and prior edit timestamps match. Exact retries retain
the current snapshot, including any later explicitly rearmed choice.

The native writer already commits that complete snapshot and its receipt before
retiring recovery. Packaged-runtime JVM coverage now exercises disk-full before
publication, a lost acknowledgement after publication, restart, exact retry,
retained targets and next-session selection with deload choices.

## Required checks for this checkpoint

- `node verification/one-shot-deload-test.mjs`
- `node android-native/engine-bridge/native-workout-deload-test.mjs`
- Existing native workout, completion, protocol and progression regressions.
- Canonical export, snapshot identity, source parity and packaged IIFE validation.
- `:engine-runtime:testDebugUnitTest`, `:domain-contract:testDebugUnitTest`,
  `:app:compileDebugKotlin` and instrumentation Java source compilation.
- APK-freeze contract. All APK-producing workflows stay hard-disabled.

Shared engine changes require `node scripts/finalize-release.mjs --next-build`
to refresh the source candidate's runtime hashes and offline inventory before
export. This does not deploy the web app or create an APK.

## Next work

Verified correction of completed sets and canonical history/PR recalculation
remain incomplete. Native controls for arming session adjustments, imported web
history and physical-device interaction/interruption/accessibility acceptance
remain separate milestones. Continue from this branch and preserve the APK
freeze until the complete agreed journey has its required evidence.
