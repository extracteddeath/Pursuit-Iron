# M169 swap sheet scrolling

App **3.219.0**, build **775**, Pursuit Engine **0.62.5**.

## Root cause

The Swap Exercise sheet had two nested vertical scroll owners: the full results region and a second `wpb-scroll` around the ranked exercise buttons. The shared `wpb-scroll` class uses contained overscroll and momentum scrolling. On touch devices, a swipe beginning on an exercise row could be captured by the inner element even though the parent was the element with the usable scroll range.

## Fix

- The `wpb-swap-results` region is now the only vertical scroller in the replacement results area.
- It has `minHeight: 0`, native `overflowY: auto`, momentum scrolling, contained overscroll, and explicit `touchAction: pan-y`.
- The ranked replacement list is a normal content container, so swipes on exercise cards move the parent results scroller.
- The existing sheet drag gesture remains limited to the top grab band.

## Verification

- Added `verification/swap-scroll-integrity-test.mjs`.
- Full release verification must pass before this workflow publishes.
- No engine prescription, progression, history, or saved-program semantics were changed. Physical-device validation remains separate from static release verification.
