# Native history format v2 — bounded capacity expansion

The isolated Kotlin candidate now reads **both** exact legacy v1 history
manifests (maximum 128 completions) and the new v2 manifests (maximum 4,096
completions; 2 MiB encoded ledger limit). Existing records are not re-sorted,
recreated, or dropped. The first transaction against a v1 manifest writes a
version-2 file under the same private owner lock and atomic rename; prepared
Finish and correction intents and their recovery rules remain unchanged.

The same shared `KotlinHistoryCapacityPolicy` now bounds native enrollment,
Composer and mixed custom history readers, global factual index, and read-only
progression timeline. Inconsistent reader-only 128-session barriers are gone.
A signed-out owner, a forged v1 file containing 129 entries, unknown format,
bad checksum, oversized file, or over-capacity enrollment must fail closed.
Unit regressions cover v1 -> v2 upgrade through a crash-like pending Finish,
129th completion, 512-completion correction and cold reopen, 4,096-record
bound, and corrupt-file refusal.

**This is not unlimited lifetime history.** The v2 manifest is still rewritten
and the owned journals are still fully replayed on a global read; long-term
production needs an atomic segmented/paged manifest, authenticated lossless
legacy JS migration, indexes that never omit older failed/corrected sessions,
backup/reconciliation, and full on-device crash endurance. Synthetic manifest
regressions do not certify the corresponding 512 real workouts on disk.

No live JavaScript web engine, production runtime switch, other native branch,
or APK is modified by this candidate storage expansion.
