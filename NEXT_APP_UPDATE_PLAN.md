# Whole-app launch readiness

Updated October 21, 2026. This is the single current next-update plan. It supersedes the October 8 workout proposal: compact logging, coaching, session choices and program-change previews already exist. Preserve the user's current Workout and Focus experience.

## Current baseline and ownership

App 4.0.0 build 905 / Engine 0.65.9 / M234. `RELEASE_MANIFEST.json` is authoritative for shipped versions and hashes; `BUILD_PROFILE.json` records the current UI slice. Same-build UI repairs receive a new content-addressed service-worker cache. No new training-model identity is implied by this plan.

| Area | Maintained owner | Next work and acceptance |
|---|---|---|
| Shared UI | UiAction, UiChoice and semantic theme roles in `modules/App.js`; `app.css` | Continue migrating existing controls and removing provably superseded declarations. Preserve palette, focus, disabled, motion and forced-colors behavior; never solve conflicts by adding another override layer. |
| Home, Plan, Profile, Progress | Existing screen components and shared navigation state | Review empty/populated/error states, reading order, action hierarchy and 320px/200% text layouts. Profile and chart editors already share controls. Preserve saved selections and record content. |
| Settings and transfers | SettingsView preferences; BackupRestorePanel transfers and Undo; StorageStatusCard browser capability state | This slice separates backup ownership, shares action styling, reports transfer outcomes, prevents duplicate transfers and stale file reads, and distinguishes CSV from a full restore copy. Validate real restore/Undo and storage denial/grant. |
| Workout and Focus | Existing shared workout state, runtime and mobile lifecycle | Retain compact rows, direct input, steppers, target/last-time behavior, rest timers, effort provenance and Undo. Verify interruption, keyboard, safe areas and exact-once completion; no visual redesign. |
| Engine | `modules/engine-api.js`, canonical next-engine and training-domain modules | Continue existing independent, differential and replay gates. Change policy only for a reproduced defect with evidence and a regression. No duplicate engine, adapter or exporter. |
| Persistence and offline updates | Existing store migration/merge/queue and service worker | Corrupt/future saves must not be overwritten. Preview before restore, preserve drafts, require successful persistence before completing a workout, verify offline cache integrity and cancellation recovery. |
| Android / Play | `play/README.md`, final TWA package and Play Console | Resolve package identity and signing certificate association at the domain root, build the release bundle, validate actual permissions/SDKs and complete account-specific declarations/testing. Repository/browser checks cannot certify a signed Android package. |

## This slice

- A full backup marker changes only for full-backup transfer actions. CSV copy and cancelled/failed transfers do not suppress the backup reminder.
- Every transfer reports its outcome. Download messaging says a request was made and asks the user to verify the file, without claiming browser delivery proves a saved copy.
- File selection clears the old executable preview immediately. Oversized/unreadable files cannot leave an old restore ready to confirm; newer selection, typing, closing and unmounting invalidate pending reads.
- Transfer controls have one shared style owner, a minimum 48px target and wrapping action rows. Backup fields retain accessible names and native text selection.
- Storage protection displays only what the browser reports. Denial/error is visible, and a failed usage estimate cannot hide a successful persistence check.
- Undo stays in the restore panel until explicitly closed or Settings is left. No delayed automatic dismissal can close a later operation.

## Required release evidence

Use the existing contract registry and four workflows/ten checks on the exact candidate head. Run relevant real-browser cases before publication; verify Pages and deployed hashes after merging. Existing engine generation, adaptation, quality and integration gates remain required even for UI-only work. Record evidence in the PR and existing changelog/design plan, rather than creating competing audit documents.

Actual Android certification remains open: TalkBack navigation, soft-keyboard resize, gesture Back, safe areas, offline cold start/update, process interruption/recovery, long-session timing, notifications and file sharing through the final signed wrapper. Test the package distributed by Play internal testing; a local upload-key build may use a different signing certificate. Do not describe browser emulation as device certification.

## Subsequent slices

1. Continue cross-screen form/action ownership, prioritizing visible inconsistencies and removing redundant CSS only after computed-style or interaction proof.
2. Review navigation, dialogs, loading/refusal and recovery states across compact/expanded windows and enlarged text, retaining one navigation/data owner.
3. Fix reproduced engine or persistence defects against the existing replay/differential baselines. Keep all prescribed and observed effort, authored-day/revision ownership and manual input protections intact.
4. Complete signed Android packaging, store listing/declarations and physical-device checks before claiming launch readiness.
