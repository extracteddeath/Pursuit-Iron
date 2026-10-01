# M216 — Compact block length

Pursuit Iron 4.0.0 build 806 · Pursuit Engine 0.64.10.

Baseline: published M215 build 805, commit `9cd0325543b2df181704b2783203fdd953454569`. Its release, engine, phone and Pages workflows all passed.

## Resulting behavior

Program settings use one compact week field between minus and plus buttons. Custom and standalone programs can adjust by one week, type their duration directly, or use keyboard arrows. The growing row of predefined week choices and its unused option-list helper are removed.

Typing stays in the staged settings draft. Empty, zero, negative, fractional, nonnumeric and unsafe integer values remain editable, show an explanation and disable Save. A valid positive whole number becomes the numeric duration when applied. Decrement stops at one week. Existing uncommon durations remain editable and survive reload.

Generated cycle phases show their actual assigned duration in a compact read-only card with the existing cycle explanation. Duration editing follows the existing cycle ownership contract. The M215 saved-program authority remains in place: the converted ten-week custom entry displays as ten weeks across cycle views, while future phases retain their five- and three-week durations. Deload remains a separate calendar week.

The custom intensifier schedule, exercise prescriptions, history and engine generation policy are unchanged.

## Verification

The existing production browser gate now exercises stepping, keyboard arrows, direct typing, incomplete and invalid drafts, one/two/seven/nine/twelve-week save and reload, cycle-owned phase displays, and 320/360/390-pixel layouts with 44-pixel step-button touch targets. It still verifies cycle list/detail, Home and saved-duration authority against stale metadata. The gate mounts production components; its persistence callback is the test harness. Release gates separately exercise production duration conversion and prescription behavior.

Final local checks passed on October 1, 2026:

| Check | Result |
|---|---|
| Release integrity | Passed; 73 offline entries and 51 authored JavaScript files parse |
| Adaptation contracts | Passed all 16 gates, including duration, intensifier and prescription regressions |
| Browser integrity | Passed all five gates: compact duration, actual workout saving, intensifiers, Auto-fix and service-worker update/reload |
| Layout review | Compact duration field inspected at phone size; 320/360/390-pixel bounds and touch targets passed |

Generation and quality policy files are unchanged; their existing CI workflows remain enabled.

Phone layouts are checked in Chromium viewports.
