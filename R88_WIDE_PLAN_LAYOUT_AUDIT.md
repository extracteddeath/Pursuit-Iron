# Build 871 / R88 — Wide Plan week-grid pilot

## Scope and rationale
The R65 adaptive shell already installs a navigation rail at expanded window sizes but the Plan tab remains a 720px-wide phone-derived list. This candidate only widens **Plan** to a bounded 940px reading area and enables two-column browsing of collapsed week cards when the viewport is at least 1120px wide and 720px tall.

- Phase headings occupy both columns to preserve chronological grouping.
- An expanded week spans both columns; its existing session buttons keep full width, native semantics and their original event owners.
- The existing week picker, scheduled actions, program overview, selected-week memory, Back restoration and four Plan panes are unchanged.
- Phones, short landscape windows and narrower tablet/desktop views retain their proven single-column layouts.
- No colors, themes, gradients, animations, workout logger state or engine behavior change.

## Acceptance evidence
The permanent R65 browser contract now reads live computed grid columns and card rectangles on its normal Rose/Rosewater production-shell scenarios (393x852, 900x480, 900x1000, 1280x850). It requires the 1280x850 Plan to span more than 720px, show two actual grid tracks, give an expanded week at least 90% of the group width, and have no horizontal viewport overflow. The remaining sizes must remain a single column. Existing navigation and seeded-program integrity checks still apply.

## Release gate
This is a targeted pilot, not a full two-pane Plan reconstruction. R88 is only releasable after the final branch head passes the four permanent workflows (release integrity, core contracts, independent engine audit, phone/offline browser suite) and the merged main commit deploys to Pages with the same checks. Physical touch, external monitors, enlarged fonts at intermediate sizes, and real tablet split-screen require follow-up device inspection.

The app cache, App + boot build marker, UI manifest hashes and 102-entry offline-shell fingerprint must be recertified for Build 871 before setting PR ready.
