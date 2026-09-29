# Pursuit Iron M160 — Programs List Clarity (3.210.0 · build 765)

Base: **live M159** (3.209.0 · 764) — its shipped files, unchanged, except `modules/App.js` (merged), version files and the verifier.
`modules/App.js` = three-way merge of the compiled app: base 3.208.0 (what M159 started from), M159, and the programs-list fixes.
The merge was clean (M159: +132/-19 lines; fixes: +28/-8; no overlap).

## Changes
- Home > All programs: a cycle shows its shared split / days / focus once; blocks show their status (Training now / Up next /
  Later); cycles you aren't training fold away; every block wears the cycle's icon.
- No ✓ on upcoming blocks of a Pursuit cycle (it read as "done" and switching was always refused); standalone programs' switch
  control reads "Switch"; the refusal message, if reached, is plain.
- What's New 211 — also announces M159's "Turn into training cycle" and saved-plan restore/remove, which had no entry.
- Cache `pursuit-iron-production-m160-programs-list-r1`; boot-health build 765; manifest and verifier updated.

## Verified on this exact folder
`node scripts/verify-release.mjs` OK. Update from live 647 and from live M159. 647 upgrade (data byte-identical; restore via
"Restore to library"). Workouts (10 program types), builder, equipment, split feasibility, progression, suggestions, loaded-first,
in-app self-test 0 failures, adaptive loop. Browser: input, edit/restore, workouts incl. offline reload, cycles, history,
previous-version plans, layout (42 screens), custom programs, programs list x2, and "Turn into training cycle" end to end
(Block 1 keeps its id, history and days; future blocks created).
