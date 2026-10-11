# Build 842 — Themed materials and quiet workout tools

The October 11 owner screenshots establish the preferred direction: the original tinted workout/menu surfaces, a themed setup note, compact unfilled exercise tools, and unobtrusive coaching. This corrects the earlier interpretation that every supporting component should use a universal gray material.

## Delivered behavior

- The existing runtime theme owner publishes its card and raised-card roles. Dark canvas, card and control surfaces resolve the saved palette's own values. Legacy R38/R41/R51 consumers resolve the same semantic materials, including Exercise Detail. Light tonal surfaces remain readable.
- Selected controls use the saved palette's paired accent and foreground. Rose retains plum/pink materials, Amethyst retains purple, and deliberately neutral presets retain their own character.
- Warm-up, Superset, Swap and Plates have transparent resting fills and borders, compact 7px/9px padding and an 8px radius. Their established 48px real targets and horizontal access remain intact.
- Pinned notes retain a restrained theme tint and a subtle theme boundary. Collapsed coaching retains a 40px summary with a theme icon/label and subtle boundary; expansion and detailed guidance stay available.
- Opening a workout sheet temporarily removes the scrolling ancestor's motion transform. An identity transform otherwise captures a fixed descendant at the scrolled content origin. The sheet keeps its own spring/drag owner, and its Close action remains in the viewport after note editing.

## Verification

The existing gates cover the corrected behavior; no additional standalone gate or persistence/motion owner is introduced.

- R53 exercises all 20 actual Settings theme choices, then checks rendered Plan names and selected week text for at least 4.5:1 contrast. The paired screen checks cover Home, Plan, Progress, Profile, Settings and expanded Program, including live theme changes, stale startup cache and warm reload.
- R54 exercises a resumed user-owned set, edits its pinned note, opens Exercise Info, resizes between 320px and 393px, and closes the sheet through its visible real pointer target. Weight/reps and the saved prescription remain intact. Library and Exercise Detail retain the same theme canvas.
- The workout browser shard covers typed numeric entry, steppers, faded completed rows, effort, Undo, restore and autoregulation persistence. The integration shard covers expressive motion, menus, browsing context, generators and workout/program transitions.
- Release certification checks the offline cache revision and all runtime/UI hashes. All 93 non-App runtime files match Build 841; the engine and training persistence implementation are unchanged.

Local result: all seven integration browser gates, all four workout browser gates, all 32 integration source contracts and release integrity passed on the finalized Build 842 candidate.

Browser captures use simulated training data. Visual review covers the paired main destinations and workout/info sheet; physical Android feel and TalkBack certification remain separate evidence. The larger adaptive shell/panes are still future work in the master plan.
