# Build 863 / R78 slice 2 — exact workout dates and calm consistency

Baseline: Build 862 theme and amplifier repair, preserving Engine 0.65.7.

- Retire the unexplained two-row mini heatmap from Consistency. Show two direct facts: weekly streak and number of distinct training days within the past 28 days. History and streak owners are unchanged.
- Keep the Training calendar visible on opening Sessions. Remove its unexplained leftmost weekly count column. Display a seven-day Sunday–Saturday grid with labelled training dates. A recorded date is a button; selecting it shows that day's existing workout details and exercises. Month-navigation buttons are 44px.
- Place Milestones in a distinct closed-by-default native disclosure below the calendar. Opening achievements never changes the calendar owner or session selection.
- Preserve the selected theme's subtle surface ladder, phone and wide-screen layouts, persisted history, and workout logging.

Acceptance: R65 real-browser fixture checks history dates, tap-to-detail, collapsed milestones, and no confusing mini grid at four window sizes in light/dark palettes. Existing R78 source, engine and app-wide gates remain mandatory. Physical Android/TalkBack still pending.
