# M162 progression-safety hotfix

App **3.212.0**, build **768**, Pursuit Engine **0.62.5**. Based on the user-delivered M161.1 release.

## Bug reproduced

A 3-set Back Squat prescription of **5–8 reps @ 2 RIR** was evaluated after the lifter logged **200×3, 205×3, 210×2, 215×2**, with the final **215×2 at 0 RIR**. M161.1 classified that as a hold at **215 lb**, which could prefill the next session as three sets of 215 despite every logged set being below the 5-rep floor.

## Fix

- Missing the bottom of a rep range is now separated from ordinary add-reps/hold behavior.
- When the miss is hard (reported RIR below target) or effort is unknown and the miss is widespread/deep, the engine derives a conservative e1RM from the relevant missed sets.
- It converts that estimate back to the **bottom of the prescribed rep range at the planned RIR**, then rounds **down** to a load the configured equipment can make.
- It never promotes the heaviest logged ramp set into the next straight-set prescription after a rep-floor miss.
- Explicitly easy short sets (for example, stopping at 4 reps with 5 RIR against a 5-rep floor) are treated as execution evidence, not a too-heavy-load signal.
- Runtime history/recovery/simulation logic recognizes `decrease_load` as a conservative/negative progression action.
- The shell-history numeric parser now preserves `null`/blank RIR as missing. Previously `Number(null) === 0` could silently convert unreported effort into 0 RIR and distort calibration.
- The custom-program double-progression path receives the same protection.

## User case after fix

The exact reported Back Squat sequence now resolves to **185 lb**, target **5 reps**, action **decrease_load**, rather than 215 lb. The recommendation is derived from the 215×2 @ 0 RIR evidence and snapped to a valid barbell load.

## Scope

This is a targeted progression/runtime hotfix. M161.1 UI work remains intact. No program-generation topology, volume, exercise selection, or cycle structure was changed.
