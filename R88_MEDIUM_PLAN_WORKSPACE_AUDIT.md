# Build 871 / R88 — Medium-width Plan workspace

## Evidence and decision

The proposed "add a two-column Plan" was already implemented in R68 at >=1060x700. Duplicating it would create another layout owner. The remaining demonstrated breakpoint gap is narrower: R65 intentionally leaves a 460px app frame on 600–839px windows even though the screen can support a wider, single-column reading surface.

## Scoped change

- On available windows 600–839px wide and at least 640px tall, the existing five-primary-tab shell grows to at most 760px, with bottom navigation retained. Every primary destination uses that same frame width, so switching tabs cannot cause a Plan-only width jump.
- The existing Plan scroll container retains its 720px reading limit and retains the established 24px medium-window page gutters. Week picker, Up Next, Plan section buttons, selected week and Program overview stay in the same mobile reading order, using the same React nodes.
- At compact widths below 600px, short landscapes below 640px high, and expanded widths >=840px, all established navigation and Plan geometry remains unchanged. R68's real desktop supporting column remains the only two-pane Plan owner.
- The live workout, detail/preview routes, set logger, numerical inputs, all themes, motion, storage and engine 0.65.7 are not changed. No new viewport state, second controller, duplicate program card, or extra overlay.

## Verifiable acceptance

The permanent R65 real-browser integration test now covers 393x852, 600x800, 768x900, 900x480, 900x1000 and 1280x850 in Rose dark and Rosewater light. Its checks require:
- A real medium shell at >=min(window width,760)-3px, never exceeding 760px; five semantic, >=44px navigation controls with correct position and hit-testing on every tab.
- A Plan reading surface width of at least min(window width,720)-4px on medium windows, no document horizontal overflow, and the original vertically stacked Plan supporting content.
- An actual week-2 selection retained while resizing 768x900 -> 393x852 -> 768x900, without route forks or prescription mutation.
- Existing expanded rail, two-column Plan, Library, Progress, Wizard, workout, forced-color, motion and source contracts continue to pass.

No assertion claims a 2-pane medium layout, a measured tap-count reduction, physical-device certification, or changed training algorithms. Require all four final-commit workflows before merging, followed by successful Pages and post-merge checks.
