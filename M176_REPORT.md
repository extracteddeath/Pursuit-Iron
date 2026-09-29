# M176 interrupted workout restore hardening

App **3.226.0**, build **782**, Pursuit Engine **0.63.2**.

The current live-session persistence implementation already contains the right mobile/PWA behavior, so M176 protects it rather than replacing it.

## Release-gated invariants

- Same program/day/week resumes the exact saved session.
- A changed exercise list performs a partial resume and keeps logged work for unchanged exercises.
- Malformed stored rows are rejected instead of crashing workout restore.
- The current program owns exercise order/structure while the snapshot owns logged sets and notes.
- Workout elapsed time resumes from actual training time, not wall time spent away from the app.
- Paused and running rest timers restore correctly.
- `visibilitychange`, `pagehide`, and `freeze` all flush the current snapshot before mobile teardown/background kill.

Physical Android interaction certification is still separate because source/Node tests cannot simulate the OS killing an installed PWA process.
