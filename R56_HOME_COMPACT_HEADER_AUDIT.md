# R56 — Home collapsed header spacing and cross-screen design verification

## Screenshot diagnosis

The October 11 Android screenshot shows Pursuit Iron's header still occupying a large band after the content scrolled. The `HomeView` collapse effect only reduced `headRef` (greeting/title); the always-rendered brand/logo and three-action cluster stayed as a full-size toolbar. The older R48 `padding:11px 16px 13px!important` rule also outranked the generic R4 scrolled style, leaving unused vertical height. The brand repeats the app identity on every scrolled screen even when the workout list is the actual focus.

## Scoped implementation

- Derive `data-home-compact` from Home's real `.wpb-page-scroll` scroll offset, at 95% of the greeting collapse, in the same non-React requestAnimationFrame that already drives the header. Do not add per-scroll React renders or alter view persistence.
- Keep the full identity when scrolled to top. In compact mode show the small plate symbol and short **Iron** wordmark instead of the repeated large brand string.
- Compact header to approximately 52dp total by removing old padding. Keep the calculators, Exercise Library, and history actions, each with actual >=44dp hit geometry; reduce the toolbar framing instead of fading its icons.
- Use R55's semantic card/control/divider/on-color roles, with no hardcoded palette/gradient. Preserve keyboard focus and forced-colors readability.
- Do not affect Workout's immersive header, logger, set controls, engine decisions, or app tabs.

## Acceptance gates

Expand R53's genuine paired Rose dark/Rosewater light screenshot test to capture **Home compact** as a seventh view. Assert a >=24dp shrink between full and collapsed headers; collapsed <=64dp; all three toolbar controls >=44dp; no brand/toolbar overlap at 393dp and 320dp; no horizontal overflow; brand expands on scroll to top; Exercise Library action still navigates.

Keep all pre-existing contrast, lifecycle, and source-integrity checks. Run the release finalizer and remove the temporary workflow before a green all-shard merge. The *user-visible* screenshot remains the ultimate density review; passing mechanical gates alone is not a guarantee of subjective perfection.
