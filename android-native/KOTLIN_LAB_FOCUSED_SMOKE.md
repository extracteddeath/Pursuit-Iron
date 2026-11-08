# Trial 12 focused workout Android emulator acceptance

This is a separate test of the disposable **APK only**, not an AAB or
production release. Do not replace any earlier Trial 9 source or evidence.

- Build source: `ab210d86ade815c873a65b8f9d69800166d37694`.
- Successful APK build: https://github.com/extracteddeath/Pursuit-Iron/actions/runs/42521928675
- APK artifact ID: `13633553986` (`pursuit-iron-kotlin-lab-disposable`).
- Test package: `io.github.extracteddeath.pursuitiron.kotlinlabtrial12`.
- Version code: `12`; isolated ephemeral debug signature; no upgrade from Trial 9.

The workflow must verify the pinned receipt, app identity and APK hash
before installation, then exercise the actual Android UI at API 35:
fresh setup, first program, approval, distinct history, workout issue,
enter six **unsubmitted** reps, switch Focus > All sets > Focus and retain
those reps, switch Target/Last, retain the same edit at 1.6 font scale,
commit sets, cold recover, complete every required visit, Finish,
verify History, and show the next independent training day.

The screenshots and UI hierarchy are evidence only for this synthetic
emulator journey. A physical phone, full accessibility, backups, complete
feature parity and personalized coaching superiority remain unverified.
