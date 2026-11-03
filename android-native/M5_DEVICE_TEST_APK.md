# M5 development APK — one controlled device-test candidate

**Purpose:** Enable a real Android phone to verify the cohesive native core
from first setup to an acknowledged finished workout and saved history.
Previous JS bridge tests covered the individual operations; the new
`native-core-journey-test.mjs` continuously proves onboarding, program
restore, issued workout targets, actual set observations, skipped positions,
retry after interrupted completion, exact history correction, stale correction
rejection, and advancement to the next day in both lb and kg.

**Source evidence before this marker:** continuous canonical-bridge CI
passed at `e76344c08a49929cc473f3b40b5aa68577ec8371`;
the latest changed Compose source compiled successfully at
`47908937a2aa003928d40510f7dec81cbdc1bb3f`.
The marker-triggered APK job must independently rerun the full native
journey tests and Kotlin unit tests before assembling a debug APK.

**Scope:** Android-only debug app with `.preview` application ID suffix,
one device-testing artifact. Does not update the web app, publish to Play,
merge branches, migrate or overwrite real training data, switch engines,
certify progression correctness or enable recurring APK builds.

**Acceptance after install:** Onboarding -> generated plan review/save ->
cold restore -> Today/Train -> actual logged sets including RIR and skips ->
rotation/background/restart -> exact Resume -> confirmed Finish ->
corrected History -> next day; inspect original theme choices, large text,
keyboard, equipment-aware loads and user-facing interaction polish.
Report gaps before replacing the legacy engine with independent Kotlin logic.

**Workflow retry:** This milestone retry quotes the conditional YAML scalar so GitHub can parse it. No native training data or user-facing app logic were changed.
