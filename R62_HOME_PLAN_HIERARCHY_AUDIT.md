# Build 841 — R62 Home and Plan task hierarchy

The next action was below supporting exercise detail on Home, while Plan placed
three aggregate tiles and completion ahead of the next session. This release
moves the actionable content first in DOM, reading and keyboard order.

## Implemented

| Area | Result |
| --- | --- |
| Home | Summary, Start, optional alternative, then exercise outline. Saved programs precede promotion, insight and creation discovery. All existing creation, cycle and freestyle routes remain available. |
| Plan | Next session, four sections, selected-week picker, intent and connected week/session lists precede the program overview. Aggregate tiles and completion remain available below the schedule. |
| Shared components | Native text and row actions, labelled sections, spacing/type/shape roles, target-size roles and reading/workspace width constraints extend the R61 pilot. Existing navigation, disclosure, storage and motion owners remain in place. |
| Wider-window foundations | Home/Plan use 12/16/24px available-window insets and a bounded reading-width role. The existing 460px shell remains; adaptive navigation rails and related panes are subsequent work. |
| Notifications | Passive message content no longer intercepts the Plan session click after switching programs. Undo, labelled actions and Dismiss keep pointer access and existing announcements/lifetimes. |
| Navigation paint | The existing selection track is positioned relative to its bar. Height-only resizing can no longer leave it at the old shell coordinate over page content. |

Connected session rows retain their accessible names, NEXT/done markers, all
detail, separate native buttons, inset keyboard focus and at least 48px targets.
No training prescription, engine, storage schema or logger geometry changes.

## Verification

- Extend the existing R53 gate with actual Home/Plan reading and action order,
  connected row targets, continuous widths of 320, 393, 599/600/601, 768,
  839/840/841, 1024, 1280, 1366, 1600 and 1920px, and a 900×450 short window.
- Resize the same mounted screens: expanded session outline, deliberately
  selected week and saved training data remain intact. Inspect actual selected
  navigation paint within the bar. Check enlarged Plan text at 320px and keyboard
  focus after keyboard input. Paired Rose/Rosewater captures use simulated data.
- Retain R61 cold/warm/theme-switch contrast and truthful Profile checks, R54/R59/
  R60 preview/Back, active-program, exact workout return and browsing-memory flows,
  the expressive interaction gate and 32 integration contracts.
- All 93 non-App runtime file hashes match Build 840. Release integrity verifies
  87 production JS files, 102 unique offline entries and all runtime/UI hashes.
- Full unmodified CI is required on the exact PR head, then on main, followed by
  Pages deployment and deployed-file identity verification.

The inset test waits for the browser's resize frames before inspecting media
query results. This prevents reading an old frame as the new viewport's paint.
The notification issue was reproduced by the existing real preview-click flow;
the height-only navigation drift was reproduced in computed geometry and a
tablet capture before repair. Gates were retained and extended.

## Remaining scope

These changes advance A04–A06, A08–A13 and the A40 foundations. They do not
complete every shared family or the adaptive shell. The master plan retains
A41–A47 navigation rails, related panes, overlay adaptations, input review and
continuous resize/zoom audit, plus remaining Program, Workout, review and creation
screen work. Automated browser geometry is separate from physical-device,
TalkBack, real keyboard/trackpad and usability review.

Design references: [Material Web buttons](https://material-web.dev/components/button/)
and [Android available-window classes](https://developer.android.com/develop/adaptive-apps/guides/use-window-size-classes).
The web inset/width values are product decisions in CSS pixels; native dp size
classes are design guidance, not a claim about physical device size.
