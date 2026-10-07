# M226 Engine Boundary Integrity — Build 816 / Engine 0.64.15

M226 is a bug-prevention sweep over progression ownership, malformed/imported engine state, and silent wrong-answer paths.

- Custom-program progression requires comparable history from the same program and authored day.
- Cross-day/cross-program history may seed only a low-confidence starting load; it cannot earn progression.
- Adaptive plateau, fatigue, and linear-stall logic scope tagged history to the active program.
- Percentage-cycle TM evidence is program-owned and numeric strings are parsed numerically.
- Imported GZCLP load/stage state is parsed and bounded.
- Request, phase, and generation boundaries reject malformed semantic state rather than producing downstream undefined/NaN behavior.

Permanent gate: `verification/m226-engine-boundary-integrity-test.mjs`.

Design rule: lifetime analytics may use broad history, but progression decisions use only comparable owned evidence. Ambiguous evidence may inform a starting reference but never earned progression.
