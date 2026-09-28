# Pursuit Iron M159 — Standalone Program → Training Cycle

**App:** 3.209.0 · **Build:** 764  
**PWA cache:** `pursuit-iron-production-m159-cycle-conversion-r1`  
**Base:** M158 Variant Coverage & Setup Fidelity + the previous-version restore/removal hotfixes.  
**Repository:** this package was built locally; GitHub was not modified while producing M159.

## What changed

### 1. Standalone programs no longer show “Start next block”
The overflow menu previously added **Start next block** to every fixed-length program, even when the program was not part of a training cycle. M159 removes that action from standalone programs. Real block advancement remains a cycle-level action where the app has the cycle template, active block, and transition history available.

### 2. A standalone program can become a training cycle
Eligible standalone Pursuit programs now show **Turn into training cycle** in the program menu.

The conversion sheet lets the lifter:
- name the new cycle;
- choose a cycle pathway supported by the current engine;
- preview only the future blocks that come after the current program’s phase;
- choose whether exercises may adapt between future blocks.

The current program is **not regenerated or duplicated**. It becomes Block 1 using the same program ID, which keeps existing workout history and user edits attached to the program. Only the future blocks are created.

If the current phase already occurs inside the selected template, M159 starts after that phase. Example: a current hypertrophy block converted to Powerbuilding remains Block 1, followed by Strength and Peak. A current Strength block converted to that pathway is followed only by Peak.

If the current phase is not part of the chosen template, it is retained as a custom first block and the template’s future blocks follow it. A pathway that has no later phase after the current block is disabled/rejected instead of creating an empty cycle.

Endless programs are intentionally not convertible because they do not have a defined block boundary.

### 3. Future blocks still use the real transition engine
The conversion creates future blocks as previews. When a block is actually advanced, Pursuit’s existing cycle transition path can rebuild the next block from completed workout history when **Adapt exercises between blocks** is enabled. The conversion does not bypass that behavior.

## Carried forward

M159 includes the recent M158 saved-plan recovery fixes: previous-version plans can be hidden, restored, or removed from the restore queue without deleting workout history; old cycle blocks may be restored as standalone programs.

## Validation performed

- All 76 service-worker precache entries resolve inside the portable package.
- All 53 authored JavaScript module files pass `node --check`.
- All relative module imports resolve.
- `Start next block` is absent from the M159 app shell.
- `Turn into training cycle` is wired from the standalone program menu through the root handler to `convertProgramToNextCycleForShell`.
- Conversion-engine test: a standalone hypertrophy program retained its original program ID as Block 1 and produced Strength + Peak previews in a Powerbuilding cycle.
- Service-worker cache identity and `BUILD_PROFILE.json` agree.

Run `node scripts/verify-release.mjs` from the extracted package root for the package-level release checks.

## Files to use

- **Full PWA ZIP:** use for a complete replacement/deploy. The ZIP opens directly at the app root (`index.html`, `modules/`, `vendor/`, etc.).
- **Patch ZIP:** contains only the M159 changed files plus this handoff and verification metadata. Apply it over the current M158/r4 tree while preserving paths.

Because the production runtime is modular, do not flatten the `modules/` paths when applying the patch.
