# Segmented native-history shadow — candidate gate

The owner-fenced `NativeKotlinSegmentedHistoryShadowStore` is **not** the
history authority and is not wired to production Compose. The existing
version-2 ledger and its journal/correction redo protocols still decide which
completions exist. This candidate provides a separate, deliberately limited
storage transition toward scalable lifetime history.

## Implemented

- Under the existing athlete/owner approval fence, `sync` reads the entire
  enrolled version-2 manifest, refuses uncommitted redo, and writes immutable,
  content-addressed segments of at most 128 chronological completion entries.
- Only after all segment files are durable via individually forced temporary
  writes and atomic moves is a checksummed root index atomically published.
  An interruption before root publication leaves unreferenced content files,
  never a partially indexed completion. No orphan is promoted automatically.
- A new source revision leaves unchanged immutable segments intact, replacing
  only the changed segment(s) and root index. Existing corrupted files cannot
  be silently regenerated, and an older/same-revision-but-different source
  cannot replace a newer published root.
- `readPage` returns exact newest-first pages of up to 128 manifest records
  and an explicit next offset; it checks signed-in ownership, current source
  revision and canonical source contents under the same fence, then verifies
  the checksum/identity of every segment touched by the page.
- Read-only manifest records preserve the original stored receipt IDs,
  Finish IDs, chronological order, original journal revisions and post-Finish
  correction revisions, but **do not** constitute full set-level historical
  evidence. Existing replay-verified native readers remain the workout/Last
  factual authorities.

## Deliberate restrictions

This prototype still opens the entire 4,096-completion version-2 manifest on
each page request to prove its current revision/content fingerprint. Thus
**indexed page payload reads are bounded, but total read cost is not yet
constant-time**. The on-disk shadow is bounded by the current 4,096-entry v2
source. This is not unlimited history and not a replacement for source-owner
journal reconciliation.

The production follow-up must give a dedicated paged ledger authenticated
write authority, integrate Finish/Composer/custom correction two-phase redo
without dual writers, preserve page snapshot/cursor identity, verify backup and
legacy JS migration, permit more than 4,096 lifetime workouts, and test
cross-process crashes, device power loss, and full Compose navigation.

Nothing in the legacy JavaScript web engine, `main`, other native branches,
or APK pipeline was intentionally changed.
