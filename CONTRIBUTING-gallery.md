# Curating the shared-program gallery

The gallery is zero-backend. `gallery.json` is a static file served next to the app; the Browse
screen fetches it read-only, and submissions arrive as GitHub issues you merge by hand. You are the
single curator — that is the moderation model.

## How a submission arrives

A lifter taps **Share → Submit to the gallery** in the app. That opens a pre-filled issue labelled
`gallery-submission` containing:

1. A ready-to-merge JSON entry.
2. A **decoded preview** — the actual lifts the code rebuilds, day by day.

You review the preview, not the token. The code carries a fingerprint that must match on import, so
the preview cannot drift from what actually imports.

## Review checklist (about 30 seconds)

- [ ] **It decodes.** Paste the code into *Settings → Add a shared program* on a test install. If it
      imports cleanly, the code is valid and the fingerprint matched. If it errors, reject.
- [ ] **The preview is real training.** Skim the decoded lift list. Reject joke/troll entries, empty
      days, or anything unsafe (e.g. a "program" that is one lift to failure every day).
- [ ] **Title and note are honest.** No medical claims, no "guaranteed", no spam links. Trim marketing.
- [ ] **Tags are sane.** Keep to a small vocabulary: `hypertrophy`, `strength`, `beginner-friendly`,
      `3-day`…`6-day`, `coached`, `classic`. Drop the rest.
- [ ] **Attribution.** If `author` is set, it's a handle the submitter chose — fine. Blank is fine too.
- [ ] **`coached` entries** (v2 codes, shown with a *Coached* badge) carry a coach's exercise swaps.
      That's expected — the recipient's own calibration and progression still run on top.

## Merging

Add the entry object to the `programs` array in `gallery.json` and commit. That's the whole publish
step — no build, no deploy of the app itself. The Browse screen fetches `gallery.json` with
`no-cache`, so the new program appears on next open.

```jsonc
{
  "version": 1,
  "updated": "YYYY-MM-DD",     // bump so you can tell caches apart
  "programs": [
    { "code": "PI-…", "title": "…", "author": "", "tags": ["…"], "note": "…" }
    // …append reviewed submissions here
  ]
}
```

## Keeping it healthy

- **Order matters for discovery**, not correctness — put stronger / more general programs first.
- **Prune** entries that stop decoding after an engine change. A v1/v2 code only fails to import if
  the frozen tables it references were reordered; keep `CODE_EXERCISES`, `CODE_SPLITS`, `CODE_EQUIP`
  strictly append-only and this never happens.
- **Size**: `gallery.json` is fetched on every Browse open. A few hundred entries is fine; if it grows
  large, split by category and fetch lazily.
