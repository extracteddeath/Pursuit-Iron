# M174 causal cycle-state hardening

App **3.224.0**, build **780**, Pursuit Engine **0.63.1**.

M170 made recovery assessment causal, but the cycle-state layer still classified raw `hold`, `review`, and `decrease_load` action labels. M174 removes that last semantic mismatch.

## Behavior

- Neutral action labels no longer become fatigue evidence.
- Broad causal negative evidence can still trigger recovery review after sufficient exposure.
- Recovery exit requires two positive causal confirmations after the minimum recovery dose.
- A neutral-only recovery workout does not count as an exit confirmation.
- A real negative cause still resets recovery-exit evidence even when its UI action is the generic `hold`.

## Validation

Focused M174 cycle-state tests plus the complete release-integrity suite must pass before publication. Physical Android interaction certification remains separate.
