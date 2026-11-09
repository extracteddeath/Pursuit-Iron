# Engine upgrade from the native app (web branch `web-engine-upgrade`)

Everything found and fixed while building the native Android app since Build 933, brought to the web app. Native
runs the same engine (`modules/`) through `native/core`, so most of these were found by running the native app's
tests against the engine and are web bugs too.

Base: `main` at Build 933 (f9e086f8).

1. **Ported** (first commit): the engine and web changes already made on the native branch (`native-app-next`),
   copied as-is: `modules/`, `app.css`, `privacy.html`, `verification/`, `.gitignore`.
2. **New** (later commits): the fixes below, found by the native month test (a lifter who follows the plan every
   day for a month and beyond) and the native code's notes on web behaviour.

Not changed: release files (`RELEASE_MANIFEST.json`, `sw.js`, build numbers, `CHANGELOG.md`). Bump those as part of
the release. `ENGINE_VERSION` (`modules/next-engine/config.js`, 0.65.9) is also unchanged; generation changes in
fix 5 below, so bump it with the release if your process ties it to generated output.

## 1. Ported from the native branch

The commits, oldest first (all engine or web-side; native-only commits are not included):

- Engine: import `generateProgram` in the shell adapter so split recommendation works
- Engine + native: top set + back-offs, 5×10 supplemental, share text by week, photos on reset
- Engine + web + native: Main-Lift Waves · Novice gets its paired First-Set-Last 5×5
- Web + native: a logged set's load fills the later empty working sets of that lift; unlogging takes it back
- Web + engine: strength-score milestone in the app's unit; recap card says Last 7 days, the window it counts
- Engine: readiness follows the dose (the recovery dip scales with the session's set cost for a muscle)
- Web settings: plate pairs you own and bar weight, shared engine helpers
- Web pain flow by severity; Home protection card with Feels fine now
- Starting estimate for first-time lifts (own or related lift e1RM across implements, else bodyweight × experience)
- Web exercise About: plan slot, gym availability, best e1RM and heaviest set; gym availability check fixed
- Web past-session share: preview sheet with the card, Copy text and Share
- Starting estimate only for the planned lift; a workout start clears an earlier Undo notice
- Percentage programs: the next cycle keeps its training maxes; Tiered Linear percentages read as fractions
- 5/3/1 templates: one deload (the wave's week 4), not two in a row
- Training maxes from a 1RM follow each method's basis
- Audit pass: one time-left clock, consistent experience bands, method-aware TM prompt, volume and filter wording
- Block review reads lifts by their working weeks; badge, share text and next-day wording
- Dumbbell preset reads 'Adjustable 5–52.5 lb' beside 'Adjustable 2–24 kg'
- 5/3/1's week 4 shows as the deload; block review skips deload-week sessions
- Equipment grouped by what it trains (Hip Abduction beside Hip Adduction, leg machines together)
- Faster split ranking: cached pure exercise-pair checks (identical output, ~40% faster)
- `privacy.html` updated

## 2. New fixes

Each has a check in `verification/m377-native-month-engine-test.mjs` (registered in the `adaptation` group). Each
check was confirmed to fail with its fix reverted.

### 2.1 A repeating block never left week 1 on its second pass
- **File:** `modules/training-domain/programs.js`, `nextSessionCursor` (explicit-week path); new `currentPassOf`.
- **Bug:** after a full pass the block wraps to week 1, but the cursor counted every pass's sessions, so the first
  pass filled every week and it wrapped straight back. Measured weeks per session: `1 1 1 1 2 2 2 2 3 3 3 3 4 4 4 4`,
  then `1` forever.
- **Fix:** only the current pass counts: entries back to where the logged week number drops.
- Affects web and native (both read this cursor).

### 2.2 The Plan marked the repeated block's days as already done
- **Files:** `modules/training-domain/programs.js`, new `earlierPassIds(program, history)`;
  `modules/App.js`, `dayDoneThisWeek`.
- **Bug:** a day was "done" for week N if any session ever logged that day in week N, so on the block's second pass
  week 1 (and every later week) showed done before training.
- **Fix:** sessions from earlier passes are excluded. Endless programs and entries without a week are unchanged.

### 2.3 Loads fell for a lifter who hit every target
Found by the native month test: on the old engine 15 of the program's lifts lost 10–50% of their prescribed
estimated max over four weeks while every set was logged on target (e.g. Dumbbell RDL 14 → 8 kg, Rope Hammer Curl
14 → 7.5 kg). Three causes:

- **Dumbbell step** — `modules/training-domain/records.js`, `loadStep`. Dumbbell loads are per hand (the engine's
  `dumbbell_per_hand`) but lower-body dumbbells stepped 4 kg / 10 lb and upper 2 kg, while the engine's default
  dumbbell list is 2.5 kg / 5 lb a hand. A logged 14 kg RDL was "held" at 12, and every engine 12.5 became 12.
  Now 2.5 kg / 5 lb for dumbbells, upper or lower. Kettlebells keep their coarser lower-body jump. A gym with its
  own dumbbell rack set (`gymRackFor`) is unaffected.
- **Snapping** — `modules/training-domain/prescriptions.js`, new `loadableForSuggestion` (exported), used where
  `engineSessionSuggestion` snaps the engine's load. A load the lifter logged on the lift, in the current unit,
  counts as loadable when the step is the app's default guess (a hold of 14 kg stays 14). When the lifter set an
  increment (`EX_LOAD_INC` / `LOAD_INC`, new `loadStepIsUserSet` in `records.js`) or the gym has a rack, that stays
  the authority and an old off-grid load is history (m224 and custom-progression-safety hold this). A
  re-prescription (`action: "represcribe"`) rounds to the nearer loadable weight instead of down (11.6 kg on a 4 kg
  grid: 12, not 8). Decreases still round down.
- **Engine re-set** — `modules/next-engine/workout-history-adapter.js`, `represcribeForWeek`. The week's re-set
  from estimated max floored against the inventory (33.8 kg on a 2.5 kg cable stack → 32.5; next pass 32.1 → 30), so
  loads drifted down block after block. Now rounds to the nearer available weight (ties down) using
  `nextAvailableLoad`. `represcribeForWeek` is now exported for the gate.

After: no lift's prescribed estimated max falls more than 5% over the month. A week that moves the rep target can
still move the load a notch the other way (e.g. 15 kg × 10 → 12.5 kg × 16), which is intended.

### 2.4 One station carried a whole session
- **File:** `modules/next-engine/realizer-ranking.js`, `muscleScore`; new `sharedStationCrowding`.
- **Bug:** cables (and the Smith machine) score well on stability and resistance profile for nearly every slot,
  and nothing limited how many slots one station took. A commercial gym (barbell, bench, dumbbells, cable, machine,
  pull-up bar) hypertrophy Upper came out as six cable lifts. Sweep of 1,934 generated days: 235 had 4+ cable lifts.
- **Fix:** the first two lifts at a shared station (cable, Smith) are free; each further one scores 0.45 lower, so a
  comparable lift elsewhere wins. Dumbbell, barbell and machine zones are untouched (a dumbbell-only day is a normal
  home workout; machine zones are one station each). Core work is exempt: it's a short add-on, and moving a cable
  crunch shuffled a novice full body's second hamstring day away (b879).
- **Time limit wins:** `modules/next-engine/generate.js`, `generateProgram` now wraps `generateProgramOnce`. If a
  plan fails its audit on `TIME_LIMIT`, it's built again without station spread (`request.noStationSpread`) and the
  rebuilt plan is used if it passes. Spreading adds changeovers; a full-gym 2-day 60-minute full body (m198) failed
  without this.
- **Result** (sweep of 1,934 generated days, 4 gyms × 3 goals × 3 levels × 5 splits × 3 lengths): days with 4+
  cable lifts 235 → 31 (the rest are long sessions, the cable-heavy gym, or a cable crunch on top); days with a free
  weight 1,530 → 1,709; generation failures unchanged (the same 3 configs fail before and after).
- **Snapshots:** `verification/fixtures/engine-816-golden.json` (the "Build 816 differential" in m228/m235) is
  regenerated: 8 of 13 scenarios change, each by cable lifts swapped for their dumbbell or bodyweight version (e.g.
  rope hammer curl → hammer curl, cable lateral raise → seated dumbbell lateral raise). It was unchanged since
  Build 816, so this is the first intended generation change since then. `m230-preserved-realizer.json` has the new
  hash for `top.muscleScore`.

### 2.5 Technique cues contradicted each other
- **File:** `modules/training-domain/analytics.js`, `cuesFor`.
- **Bug:** a lift's own cue was followed by its pattern's cues, which could disagree. Bench: "Tuck elbows ~75°"
  then "Elbows ~45° from torso".
- **Fix:** a pattern cue on a topic the lift's own cue covers (elbow, touch, grip, wrist, knee, depth, stance, arch,
  lean) is dropped.

### 2.6 Superset rounds were ignored in the workout
- **Files:** `modules/workout-context.js`, new `withGroupRounds`; `modules/App.js`, WorkoutSession `data`
  initializer.
- **Bug:** the program editor's Rounds control for a superset or circuit stored `program.rounds`, but the web
  workout never read it.
- **Fix:** a lift in a group set to N rounds gets exactly N working sets: extra sets drop off the end with their
  drop/myo sub-sets, missing ones repeat the last working set's target, not logged. Same rule native has used.

### 2.7 Build next block on your own program
- **File:** `modules/App.js`, `startNextBlock`.
- **Bug:** a Build-your-own program (`config.split === "custom"`) fell through to the generator, which has nothing
  to rebuild it from.
- **Fix:** the next block copies your days and lifts as a fresh block at week 1 (new id; drafts, revisions, cycle
  links and next-week prescriptions cleared), training maxes carried, named "· Block N+1".

### 2.8 A comma decimal logged ten times the weight
- **File:** `modules/training-domain/analytics.js`, `sanitizeWeightInput`.
- **Bug:** a decimal keyboard that types a comma ("82,5") had the comma stripped: 825.
- **Fix:** a comma with no point is read as the decimal point.

### 2.9 A browser check measured a sheet mid-animation
- **File:** `verification/b899-stable-screen-browser-test.mjs`.
- The check measured Program settings' choice buttons as soon as the sheet appeared; on a loaded machine it caught
  the last frame of the scale-in (47.9999 px against a 48 px floor) and failed about one run in three. The animation
  is the same on Build 933. It now waits for the sheet's animations to finish before measuring. Since then it passed
  15 of 16 solo runs; the one failure wasn't captured, so if it recurs in CI, read its geometry line first.

## Verification

- Contract suite: all 128 gates pass (`node scripts/verify-engine-contracts.mjs`, each gate run on its own).
  `m199-wizard-feasibility` has a 500 ms budget that ran 505–905 ms on a 2-core machine while another suite ran,
  on `main` too; it passes when run alone.
- Browser contracts: all 7 shards pass (`CHROME_BIN=<chromium> node scripts/verify-browser-contracts.mjs <shard>`).
- Native: the core's tests (`cd native/core && npm test`) pass on this engine, including the month test, which now
  fails if any lift's prescribed estimated max falls more than 5% over the month.
