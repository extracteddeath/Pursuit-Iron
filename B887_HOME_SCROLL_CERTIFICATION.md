# Build 887 — Home masthead motion certification

## Scope
Restore the full **Pursuit Iron** identity in the compact Home header; preserve the semantic theme and 48px actions; collapse greeting height with the real Home scroll position without a 95%-progress chrome swap.

## Regression discovered and resolved
- Earlier B887 browser tests recomputed the nominal expanded greeting height after each scroll gesture. Because an element's `scrollHeight` can decrease as it is clipped, the test stopped the scroll prematurely and reported a falsely expanded terminal header.
- The B887 motion browser test now retains the *original expanded height* for all forward/reverse scroll samples. The focused browser job confirmed the resulting phone/tablet behavior.
- Resize handling now temporarily measures the natural (unclipped) greeting height synchronously and restores the clipped height before the next paint. This avoids accumulation when the viewport changes.
- Home brand, themed header shape and color, stable left inset, accessible header controls and Home-to-Program navigation remain required.

## Acceptance
Run the release integrity verifier, core contracts, independent engine audit, and all four real-browser phone/offline shards on the final commit. Do not merge until CI is green. The workout logger, saved program and engine behavior must remain unchanged.
