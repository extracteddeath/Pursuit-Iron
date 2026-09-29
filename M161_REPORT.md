# M161.1 search field correction

App 3.211.1, build 767. Based on the delivered M161 release.

The global non-workout input styles used `!important`, overriding the search input's transparent background and border reset. A scoped, more-specific reset now keeps the inner input transparent, borderless and without a second focus shadow in both idle and focused states. The enclosing label retains its visible focus ring. Search behavior is unchanged.

Validation: JavaScript syntax and full module import; CSS cascade reviewed: the scoped selector has specificity (0,5,1), exceeding the global idle (0,3,1) and focused (0,4,1) selectors; all competing surface declarations use !important. Browser/phone visual verification remains pending.

---

# M161 · All Programs redesign

Base: user-supplied pursuit-iron-m160-release.zip. App 3.211.0, build 766.

## What changed

The M160 list forced every cycle block to use its parent cycle icon. It also rendered the collection as large groups in the Home flex column. The replacement gives All Programs its own content-sized section, bordered cards and a fixed 10px list gap.

- Training now appears first, followed by saved programs in newest-first order (completed cycles last).
- Each cycle counts and renders once, including when its blocks have different folders.
- The cycle header retains its chosen icon. Phase chips use Layers for hypertrophy, Flame for strength, Trophy for peak, Zap for strength & size, and Battery for recovery.
- Phases stay visible in order even with block details collapsed. The current phase is highlighted.
- Both the current cycle and saved cycles expand or collapse through View blocks / Hide blocks.
- Search covers names, phase labels, split names and folders. Filters cover All, Cycles, Standalone and Completed when applicable.
- Initially shows the current plan plus four saved entries. Show more reveals six more entries at a time; Show fewer restores the compact list.
- Expanded blocks retain duration, training status, differing split/day metadata and focus/reduced-muscle details. Standalone focus details remain visible.
- Existing open, switch, compare and standalone menu callbacks are preserved. Planned future blocks remain unavailable for opening or switching.
- New service-worker cache name lets the updated shell replace M160 on the same deployment origin.

## Verification

29 real-React-element component interaction checks passed with a controlled hooks dispatcher. Full App module import and JavaScript syntax checks passed. All 51 next-engine and shadow-engine files are byte-identical to the supplied M160 archive.

The tests exercise rendering and callbacks without a browser DOM. Browser-based layout, touch scrolling, focus movement and on-device installation were not verified: Chromium was not installed and its download failed in this environment. This is not device certification.

The verification folder includes the executable checks. From the extracted root, run:

    node --no-warnings --experimental-loader ./verification/import-loader.mjs ./verification/programs-test.mjs

## Phone check

1. Install/deploy this whole archive using the same process and origin as M160. Do not clear app data.
2. On Home, confirm that Training now is followed by compact Saved programs cards, with distinct phase icons.
3. Expand/collapse a cycle, open its current block, and verify future blocks stay marked Planned.
4. Search for a saved program or folder, try the filters, then Show more / Show fewer.
5. Check the smallest phone width you use, your light and dark themes, and keyboard search; the last card and controls should scroll above the Create/Quick bar.

Source for the new section is HomePrograms in modules/App.js. The archive is a complete portable release, including the editable browser modules.
