# Build 872 / R91 — Task-first Home hierarchy

## Observed problem
The active Home route showed the next-workout hero, then the program library and install/release promotions before any recent training summary. A large always-visible **Create Program** button remained pinned near navigation even for a lifter with an active next workout. Randomized non-scheduled headline copy further weakened the task identity. This created competing calls to action and pushed today's useful training context behind browsing content.

## Resulting reading and action order
1. A stable Home greeting and **Today's training / Your next workout** headline, followed by the existing clickable next-session summary, primary Start workout, optional alternate and expandable exercise outline.
2. **This week** completed-workout count and weekly **Streak** as two compact supporting values when recent history and an active training program exist.
3. **Your programs** for explicit switching and management.
4. Relevant coaching: deload/recovery before secondary Strength Score, weekly recap and detailed cycle progression; continue to protect backup reminders.
5. Optional creation, freestyle and template discovery. Install prompts and release notes live with these lower-frequency discovery surfaces rather than interrupting the training task.

The no-program state retains the prominent Start here path and the existing persistent creation/freestyle dock; returning lifters without an active program retain their history summary lower down. Creation and freestyle continue to use the same callbacks without duplicate data owners.

## Material guidance applied
The Home screen should be a contextual task entry point, not an undifferentiated feed. Keep **one** visually strongest primary action for the current task, give related information close proximity, move infrequent actions after current work, and preserve DOM, screen reader and keyboard order along with visual order. This uses existing Pursuit components and saved-theme semantics; no new gradient, theme, routing layer, modal or elevated dashboard cards.

## Certification
Extend permanent R53 tests in both Rose dark and Rosewater light to require the active Start action ahead of the session outline, the program library before deferred Create/Freestyle, removal of the pinned secondary dock when a workout is ready, and pointer-hit visibility after scrolling secondary actions into view. Confirm the weekly summary sits ahead of the program list on a recorded-history fixture. Keep 320–1920px responsive/zoom, Plan/preview/workout/history flows, all four final-head checks, Pages and main/postmerge checks.

No engine, workout prescription, history, actual set entry or theme tokens change. Physical Android usability review and TalkBack remain separate and are not claimed by CI.
