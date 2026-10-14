# R92 — Home session outline

**Current behavior, Build 876:** The user wants the actual workout visible before starting; a split label such as “Push” alone is insufficient. The outline now starts expanded and opens for each new program/day/week, showing every real exercise and its prescribed sets/reps. The existing 48px disclosure can still collapse the list, and resizing retains that choice. Start/Resume stays ahead of the outline.

R53 now verifies default-visible exercise names and dose, explicit collapse/reopen, and responsive persistence across the existing dark/light window matrix. The older collapsed-default requirement below is superseded.

## Build 873 historical density change

## Diagnosis
Build 872 corrected content order, but a second visual problem remained. The Start Workout hero eagerly displayed three exercise rows, an extra “+N more” button and multiple large nested containers beneath a full-height top summary/orbit. On phone widths, supporting progress could require unnecessary scrolling. A scheduled program with two exercises had no disclosure at all. The Home summary already shows its day, estimated minutes and exercise count, so default row details repeated context that is more valuable during a deliberate preview.

## Implementation
- Retain summary and primary Start action; show a single **Session outline / N exercises** 48px row by default for every session containing exercises. ARIA button exposes expanded state and a stable controlled-list ID.
- Open state shows every actual exercise, prescribed sets and rep range using the same `computeCell` engine UI read as before, without additional generation. Never use a mocked workout outline.
- Close resets on changing program/day/week; route and training values unchanged. No new custom animation layer.
- A quiet divider and flat token-native surface guide scan order without another colored CTA or rounded card stack.
- Label the rolling seven-day workout count accurately rather than claiming a calendar-week count.

## Acceptance
In R53 browser contract, prove default collapsed height less than 95px, named 44px+ disclosure, honest ARIA ownership and zero hidden exercise rows; click and prove four actual fixture exercises become visible, no clipping, and expansion survives responsive resize through 320–1920px across Rose dark/Rosewater light. Original Home/Plan/workout/export/offline contracts remain unchanged. Release finalizer and all four permanent final-head workflows must pass, then Pages and four post-merge checks.

Physical Android keyboard/TalkBack, animation frame performance and subjective density preference remain for device review.
