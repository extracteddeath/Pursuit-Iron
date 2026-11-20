# Training-number authority: one Kotlin engine, one auditable chain

Status: architecture/acceptance contract, **not** a claim that all paths are
implemented or that native has web parity. The native replacement must never
package or call the web/JavaScript engine as a hidden second decision system.

## The four different kinds of numbers

1. **Authored prescription**: an athlete-approved cycle/program version
   determines exercise, physical load and basis, working-set count, rep range,
   RIR target, rest, amplifier and planned progression rule. The issued
   session holds an immutable snapshot with athlete, gym, machine, day, source
   revision and set identity. There is one source of truth.
2. **Editor starting value**: a read-only projection. For a regular set it
   may start from the issued target on the selected verified machine. For
   myo-reps, rest-pause, same-load cluster and lengthened partials it MUST
   begin from **that exact activation set's last saved performed load and
   station**, not a first working set, prior workout, stale UI draft or a
   program-level first-set weight. A drop uses its independently authored
   stage-specific load. A legacy explicitly authored override is not silently
   migrated. No usable activation => no guessed same-load default.
3. **Recorded performance**: the athlete's confirmed reps, weight, unit,
   RIR, station and outcome. Revisioned journal receipts are immutable
   historical evidence until an explicitly accepted correction. A logged
   set need not equal its target. Warmups, failed attempts and amplifier
   mini-sets do not silently become regular working sets.
4. **Coach/future-plan proposal**: a separately traced recommendation,
   initially uncommitted. Never overwrites an active workout, typed value,
   completed set, old session, or machine-specific history. Changes to a
   future prescription require the relevant review/automation consent.

The labels **Target**, **Last**, **Actual**, and **Next recommendation**
must never be interchanged. Load is not a scalar alone: include unit,
load basis, physical equipment/Machine A-B, authored scheme, exercise,
day and set role. Compare decimals numerically, but never compare
different physical machines or assistance counterweights as strength.

## Decision graph

```text
athlete goal & constraints / current history / scoped preferences
  -> one Kotlin program + phase/dose/exercise candidate
  -> scientific bounds + equipment + time + cycle audit
  -> accepted program version / immutable issued session targets
  -> edit seed (source-specific; myo uses saved activation receipt)
  -> athlete record -> native owner-fenced durable journal
  -> factual History (separate by machine/day/technique)
  -> longitudinal evidence -> reviewed future candidate
  -> approved new version (never rewrite existing targets)
```

Every decision that can produce a **number** needs a durable provenance
record in the longer-term unified policy API: source kind, version, evidence
receipts, exercise/day/machine, unit/basis, constraint range, chosen value,
rounding/increment, explicit reason, and whether athlete consent is required.
Missing source or violated guard => refusal or request for a user choice,
not silent fallback to another numeric rule. UI projections should call
the same policy, not reimplement load/rep/scheme arithmetic.

## Continuous goal-directed program: intended next architecture

A future upgrade to Endless Plan should accept **goal** (hypertrophy,
strength, powerbuilding), training experience, training days and schedule,
available session time, split preference versus adaptive split, verified gym
and machines, injuries/reduced movements, focused muscle groups, and
progression/fatigue-coaching opt-outs. It should also learn from **durable**
logged performance, adherence, measured fatigue/readiness and recovery.
When the athlete permits it, the cycle planner can periodically reconsider:

- phase duration and progression (including hypertrophy -> strength -> peak
  when appropriate); deloads and realistic main-lift exposure frequency
- per-muscle direct/indirect sets versus explicit MEV/MAV/MRV constraints,
  target muscles, exercise pattern balance and equipment feasibility
- exercise selection and variants, session distribution and duration,
  rep ranges and RIR targets matched to movement/goal/phase
- load/rep progression based on comparable day- and machine-specific
  completed sets, not a single exceptional last set or unknown RIR
- rep-range or exercise plateau changes, substitution, and lower/upper
  session doses; progressive choices should be bounded and explainable

**Autopilot is future-version consent, not silent authority over old work.**
Offer permission scopes independently for changing the split, exercises,
working-set count, rep range, future load and phases, with per-muscle caps,
gym-inventory checks, injury overrides, time budget, and undo/review of
meaningful changes. Active sessions, manually locked elements, custom
pinned slots, saved evidence and physical machine histories remain fixed.
A missed session/reduced readiness may revise a future plan; it must not
pretend a set was completed or lower a recorded load.

## Acceptance evidence before calling this complete

A deterministic replay and matrix of generated and fully custom workouts,
3-6 days, each goal/phase/split, warmups, straight and authored back-offs,
shorter sessions, A/B machine swaps, Last/Target, Leveling and DP, repeats,
partial/failed sets, drops, 1-5 myo stages, corrections, offline/cold
restarts, gym stock changes, deload and next week. For every field compare
**issued target -> suggested seed -> athlete edit -> durable actual ->
history -> future proposal**, with the provenance and every refusal.
The web case (myo entries seeded from last prescribed row instead of the
saved activation) must be kept as a reproducible negative fixture.

The full UI, real Android device accessibility/restore tests and independent
web-to-Kotlin comparisons remain beta release gates. No APK until full UI.
