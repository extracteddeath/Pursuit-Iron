# M230 — Session realization decomposition

The 2,725-line realizer is now a stable re-export API backed by nine maintained modules: shared rules, candidate ranking, prescriptions, session clock, sequencing/pairing, strength anchors, hypertrophy allocation, transactional repair and session coordination. The session coordinator is 34 lines. Strength allocation, major-muscle work, direct isolation and repair retain their execution order and share an explicit per-generation context.

Thirty-one original declarations retain byte-identical source. The large repair stage is isolated with its existing bounded passes and closures; its algorithms are unchanged in this milestone. This is a responsibility split, not a claim that every individual repair has been redesigned.

The standalone export now records nested engine-module paths correctly. Verification checks source preservation, the complete acyclic module graph and all 13 Build 816 program/prescription/volume/schedule goldens. Generation and quality regression groups also pass. No new training decision is introduced by this structural milestone.
