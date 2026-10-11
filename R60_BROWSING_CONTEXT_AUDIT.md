# R60 — Browsing context

Build 839 continues from deployed Build 838 / PR #120 at
`4737e5ae3492d8608a7ecbbdde0f8768fab12f33`. All five main-branch
workflows, including phone/offline integrity and Pages deployment, passed.

A production-browser audit reproduced two lost-context journeys: Library
search “press” with Chest selected became an empty search with All muscles
after Compare → Android Back, and Progress → Lifts → Settings → Progress
returned to Sessions. The existing route scroll tracker also used one
position for every Progress section, despite their different content.

Library and Progress now retain their browsing choices for the current app
visit. Library remembers query, muscle, equipment and All/Recent/Goals/Banned.
Progress remembers section, lift pane and query, history query, program/time
filters, grouping, loaded page, expanded session and measurement disclosure.
They use small in-memory objects owned by App; overlays and unfinished numeric
editors retain their existing lifecycle. A deleted history record cannot reopen
an expanded session. Reload and Erase all data clear the browsing choices.

The shared route scroll owner now accepts a content key on its actual primary
scroller. Sessions, Body, Volume and each Lifts pane get separate positions.
A scoped attribute observer restores a changed content key before its next
scroll event; it never scans arbitrary mutations or writes nested-sheet scroll.
Home’s explicit Workout history shortcut selects Sessions at the top, while
ordinary destination switches restore the previously selected section. The
existing Strength shortcut continues to open its Records card.

Library has an accessible Search exercises label and an unobstructed 48px
Clear exercise search button. Clearing returns focus to the input and retains
the other filters. Selected muscle and equipment chips are brought fully into
view when the Library remounts. The existing Clear filters action still resets every filter.
The existing search rule is updated to 48px rather than adding a CSS override.

The existing R54 production-browser gate covers these journeys at 320/393px in
Rose dark and Rosewater light. It checks the actual pointer controls, Compare
Back, all Library filter dimensions, a 64-workout history with search and
program/time filters, loaded pages, grouping and disclosures, independent
Progress scroll positions, the Home shortcut, and fresh-visit reset. Browsing
must preserve the complete saved-program, cycle, history, performance and draft
values and must not start a workout. Reload compares every training field while
allowing the existing startup normalizer to refresh history `updatedAt` stamps.
R59’s typed sets, repeated Resume, completion and next-due-week journeys remain
in this same gate.

Release policy: all existing release, core, independent-engine and phone/offline
workflows must pass on the exact release head before merging. Main-branch checks,
Pages deployment and deployed-asset identity must then pass. No thresholds or
gate ownership change for this release.

Local results: release integrity, all 32 core integration gates and all 17
browser gates passed. The four R60 phone journeys restored Library at 180px,
Sessions at 150px and Records at 120px exactly. The clear-search target measured
48×48px and was unobstructed in every paired palette/width. Visual inspection
confirmed selected filter chips are fully visible. All 93 runtime files outside
App.js match Build 838 byte for byte; the engine aggregate remains
`a4b790fc05526542d040ea5d6322a704c4ceed23ea55ab8106c257a386202a1c`.
