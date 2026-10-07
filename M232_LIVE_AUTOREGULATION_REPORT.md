# M232 — Conservative live autoregulation

The actual Workout completion and reported-effort handlers now call one pure engine overlay. Two comparable rep-floor/effort misses can reduce untouched pending automatic external loads by at most 5%, returning automatic reps to the prescribed floor. If the available rack cannot provide that small a reduction, the targets stay unchanged.

Completed work, typed/manual rows, added rows, warmups and technique extensions are protected. Percentage protocols, manually prescribed programs and assistance/bodyweight loading are excluded. The stored program is immutable. Adjusted rows carry `recoveryLimited` through the real log serializer, so completing the adjusted targets cannot earn an increase. Retapping a source set to undo it restores only still-untouched pending values; later typing and completed adjusted work remain preserved.

Saved overlays survive autosave/reload and are not overwritten by resumed prescription refresh. UI wording is the plain-language “Adjusted for today's performance”.

Validation: headless ownership/progression/serialization suite; 28 adaptation gates; release integrity; existing custom-progression browser gate; new phone-width browser test that completes a source set, verifies the load reduction and manual-row protection, undoes/redoes it, and reloads the actual Workout component without exceptions.
