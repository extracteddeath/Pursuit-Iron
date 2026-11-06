# Pursuit Iron — Coach assistance and manual training

This contract applies to the isolated Android-first Kotlin candidate, not to
the production JavaScript runtime. It keeps optional coaching separate from
training execution. A user who wants to progress by feel must always be able
to log, edit within an ordinary workout, finish, and review factual history.

## One policy, three understandable settings

| Settings label | Kotlin source of truth | What it controls |
| --- | --- | --- |
| Coach suggestions | `coachingSuggestionsEnabled` | Master switch for coaching recommendations and suggested adaptive cycle drafts |
| Fatigue-aware coaching | `fatigueAdaptationEnabled` | Readiness-based modification of recommendations; independent of historical actual RIR safety |
| Progression recommendations | `progressionSuggestionsEnabled` | Suggested per-set reps, load increases and coaching-reviewed progression acceptance |

Recommended *UI presets*, not separate engines:

- **Coach on (default):** all three enabled. Nothing is auto-applied;
  suggestions still require explicit acceptance.
- **Straightforward progression:** suggestions ON; fatigue-aware coaching OFF;
  progression recommendations ON. Uses actual performance and verified stock
  but no readiness-driven adjustment or coach fatigue prompts.
- **Train my own way (Manual):** all three OFF. Do not compute/show coaching
  suggestions; keep the authored plan, logging, custom targets, normal Finish,
  corrections, history, PR facts and gym/equipment validity fully functional.

Only show subordinate controls when Coach suggestions is ON. Persist their
previous values but treat them as *effectively disabled* when the master is OFF.
Allow separate progression ON/OFF even with Coach suggestions ON, so athletes
can receive non-progression informational assistance later.

## Hard boundaries

1. **No feature-off data loss.** OFF never deletes or rewrites workout history,
   signed training cycles, the gym, or a completed session. User-authored
   plans and manually specified loads are unaffected.
2. **No secret auto-progress.** Every new recommended rep/load or adaptive
   split/phase needs active, explicit athlete approval; off suppresses the
   suggestion AND acceptance paths, not only the user-interface card.
3. **Keep safety on.** Equipment validation, realistic increments, a reported
   movement restriction, and factual incomplete/near-failure evidence remain
   strict. Turning fatigue OFF does not claim a missed set was successful.
4. **Independent day contexts.** Upper bench cannot increase Push bench merely
   because they share an exercise ID.
5. **No approval resurrection.** Saved suggestion fingerprints include
   the revision of the coach settings. Toggling OFF and back ON invalidates
   previously accepted but not-yet-issued suggestions, even when history and
   targets are otherwise identical.
6. **Owner-scoped persistence.** The preferences are versioned, checksummed,
   atomically written and bound to the authenticated athlete. Corrupt,
   interrupted or cross-athlete records fail closed: no suggestions; never
   silently re-enable the coach.
7. **Existing issued workouts remain historical facts.** Turning the coach OFF
   does not retrospectively revoke a workout already issued; amendments to
   future, unissued workouts require a fresh review after settings change.

## Implementation and UI integration boundary

- Domain: `CoachAssistancePolicy` — one typed policy, no duplicate flag logic
  hidden in a UI reducer.
- Owner source: `NativeKotlinCoachAssistanceSettingsStore` — cold-recoverable
  CAS revisions, no web or JavaScript dependency.
- Entry points: `NativeKotlinCandidateHost.readCoachAssistance()` and
  `updateCoachAssistance(expectedRevision, policy)`. Present an easy-to-find
  Settings > Training > Coaching destination when the Compose screen becomes
  active. Show the state clearly on the coaching screen.
- Activated custom reviews are gated in
  `NativeKotlinCustomNextWorkoutReview`; both the rep and load approval
  fingerprints incorporate the preferences revision.
- Generated-workout coaching and adaptive-cycle suggestions are gated at
  `NativeKotlinCandidateHost`. The live Compose UI still needs to call
  these entry points before this can be claimed to be a shipped UI control.

## Acceptance cases

- On > Manual: no progress/coach options, while a fully authenticated
  workout can be prepared, issued, logged and finished as authored.
- On > Fatigue OFF: LOW readiness does not independently change rep/load
  coaching, but injury restriction and failed-set safeguards continue to hold.
- Approved suggestion > Manual > On: the old approval is rejected at
  pre-issue revalidation; it cannot silently reappear.
- Owner switch, checksum corruption, pending write: fail closed.
- The same athlete's preferences survive a process cold restart.
