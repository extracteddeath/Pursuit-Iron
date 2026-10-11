# R55 — expanded Program Material design audit

Screenshot issue: the October 11 Android capture shows a slate-gray current-day container surrounding almost-black plum exercise rows. Exercise names truncate behind KEY LIFT and PLATEAU badges, muscle-volume chips are dim, and progression actions lack surface contrast. R53's paired visual test photographed Program before expanding a day; R54 improved manual week selection and broader route coverage but did not change these nested exercise paints.

This update applies the existing Material 3 semantic color roles to the expanded Program and removes inline legacy paint from exercise rows and muscle chips. The shared dark surface ladder follows each saved theme's neutral base rather than using universal slate gray. The exercise name can wrap naturally and retain badges, set/rep/RIR prescription, and previous-set metadata. Next day is indicated through a restrained accent edge, while Start remains the primary action. The rest of Program and shared Settings/Plan/Progress/Home/Profile use the same roles.

The existing R53 two-theme, six-destination Android browser test now opens an actual exercise list and checks nested surface elevation, visible chips, nontruncated names, >=4.5:1 text contrast, and no overflow. R54's separate Library/Detail/Workout/Manual Program regression is retained untouched.

Unchanged: training engine, session data, saved history, live logging steppers, rest timers, program week selection behavior from R54, and existing motion springs.

Material guidance: https://developer.android.com/develop/ui/compose/designsystems/material3 (semantic foreground/background roles; typography and shape hierarchy).
