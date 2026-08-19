# Pursuit Iron — Changelog

196 changes across 98 releases. Newest first.

_Generated from the app's own changelog data — do not edit by hand._

## v3.37.1 — build 577

- **Two forearm slots go to the front and back of the wrist, not the side** — Radial and ulnar deviation is a small assistance role through a short range, so when a week only has room for two wrist movements they should be the palm-side and back-of-hand ones. That is what happened already, but only by accident of where the new movements sat in the exercise list — nothing was actually enforcing it, and reordering the library would have quietly changed it. The side is now explicitly the third slot's direction: it is picked once flexion and extension are both covered, and not before.

## v3.37.0 — build 576

- **Side-of-wrist work exists now — and programs actually use it** — The last release could spread forearm work between the front and back of the wrist but not the side, because the library had no radial or ulnar deviation movement in it at all. Four are added: hammer radial and ulnar deviation, which need nothing but a hammer, plus cable and band versions. Adding them was not enough on its own — measured across 42 forearm-focused programs, the new movements were picked zero times, losing every slot to the older staples and to carries. A program that buys a third forearm slot now trains all three directions, and grip work keeps its place.
- **Wrist spread was being applied for the wrong reason** — The rule that spreads forearm work recorded which wrist direction it had trained in only one of the two places it picks exercises — so for almost every program it recorded nothing, and grip holds were being pushed out of every slot unconditionally rather than making way for whatever was actually missing. The spread you saw was right; the reason behind it was not. It now tracks what the week has covered, which is what lets the third direction be found at all.

## v3.36.1 — build 575

- **Myo-reps grow past three on every lift, not just loaded ones** — The 3-to-5 rule shipped yesterday only ran once a weight was on the row. On a bodyweight or assisted movement — and on any lift the first time you do it, before there is a suggestion to fill in — the row opens blank, so logging the third mini added nothing and the set ended early. A blank load means you haven’t typed a weight yet, not that the extension is over.

## v3.36.0 — build 574

- **Myo-reps and drop sets no longer cost you weight** — An extension of a set is short by design and always comes last, and four separate places read “the last set” straight off the list — so a mini counted as the set that decides next session’s load. Taking a 50 lb lateral raise to 20/20/20 and then adding three 4-rep minis had earned an increase to 60; the app prescribed 45 and told you to ease back. Using the technique the block asked for was costing you load every session, and the harder you extended the set the more it took away. Extensions are now invisible to progression — the same session with and without them gives the same answer.
- **Working up in weight is no longer read as a missed set** — 180×6 and then 210×4 came back as “ease back to 180” — a weight you had already beaten for more reps that day. At a fixed load the last set is the right thing to judge; when the load itself moves, the last set is the TOP set, and being at the bottom of the rep range is what a top set is for. A session with varying loads is now judged on what it averaged, which is the same basis the “Last” reference already used. Straight sets are unchanged.
- **Myo-reps run 3 to 5 mini-sets, and stop when you do** — It always asked for exactly three. Three are still laid out up front, and a fourth and a fifth are added only when you actually hit the mini rep range — come in under it and the extension is over. Whichever comes first, which is how the technique is meant to run.
- **Wrist work now covers both directions, not just one** — Every forearm movement — wrist curls, reverse wrist curls, carries, hangs, pinches, a gripper — read as the same movement internally, so the app could not tell a palm-side curl from a back-of-hand one, or wrist work from a static grip hold. Measured across 252 forearm-focused programs: 325 grip slots against 48 wrist, and not one program trained both wrist directions. A program with more than one forearm slot now trains flexion and extension, and grip work keeps its place once both are covered. Side-of-wrist work (radial and ulnar deviation) still has no movement in the library, so that direction is not yet coverable.

## v3.35.0 — build 573

- **Five splits finally have a ready-made program** — Full Body · Pattern Rotation, Torso / Limbs, Push / Pull / Legs / Arms, Upper / Lower / Arms and Powerlifting · SBD Wave were all in the app, and none of them appeared anywhere in Templates — the only way to reach one was to build a custom split, which is the route you take when you already know what you want. There are now nine templates covering them, so the browse list runs to 74. Nothing that already existed has changed: no template’s configuration was touched, and a release check now fails the build if a split is ever left with no way in again.

## v3.34.1 — build 572

- **Fixed: the hamstring template was the same program as the plain PPL** — “PPL — hamstring emphasis” in the shared programs list promised swaps for more direct hamstring work, and was in fact an identical copy of the standard six-day Push / Pull / Legs — same exercises, same volume, different name. It is now a real hamstring build: leg curls and Nordic curls in place of hip thrusts and walking lunges, with about a quarter more hamstring volume a week. Every shared program is now checked before release, so a template cannot claim something it does not do.

## v3.34.0 — build 571

- **Four new themes** — Emerald and Gold join the dark themes, Sage and Ochre the light ones. They fill gaps rather than repeat what was there — a true green, a warm gold, a muted sage and a deep ochre — so every theme still gives you a different app to look at. All eighteen are now checked for readable text on buttons before they ship.

## v3.33.1 — build 570

- **Your Profile score now says what it rests on, and what is next** — The Strength Score on your Profile is the one people screenshot, and it was the only place still presenting the number flat. If your score comes entirely from dumbbell or bodyweight lifts it is now marked as an estimate here too, exactly as it is on your home screen. Both cards also name the next target on your weakest pattern — in the weight you actually load, so a dumbbell target reads per hand.

## v3.33.0 — build 569

- **The Lifts tab is split into four views** — It had grown into one long scroll of ten sections, and several of them were not about individual lifts at all — volume against targets, next block’s volume, fatigue and recovery had all collected there. It now works like the Plan tab: Exercises for your per-lift charts and search, which is where it opens; Records for personal bests, strength standards and goals; Load for intensity and volume; Recovery for fatigue. Nothing was removed, and tapping the Strength Score card on your home screen still takes you straight to the standards.

## v3.32.1 — build 568

- **Fixed: “+3 more exercises” on Up Next now shows them** — The Up Next card lists the first five exercises of the session and counted the rest underneath. That count looked like something you could tap, and was not. Tap it now and the rest of the session opens in place, with the sets and reps for each; tap again to fold it back to a summary.

## v3.32.0 — build 567

- **Search your lifts on the Progress screen** — The Lifts tab keeps one chart for every exercise you have ever logged, so the longer you train the further you scroll to reach any one of them. There is now a search box above the list. It matches muscles and shorthand as well as names — typing “chest” finds your presses, “rdl” finds Romanian deadlifts — using the same matching as the exercise picker, so the same words find the same lifts wherever you search from.

## v3.31.1 — build 566

- **Drag to reorder today's exercises** — The day list now has a handle on each row — press and drag to move a lift up or down the session, past as many positions as you like in one go. The arrows are still there for a quick single step, and remain the way to do it with a keyboard or screen reader. Undo covers the whole drag rather than each step of it.
- **Fixed: rearranging a later exercise moved you onto it** — Moving a lift from the day list jumped the workout to that lift, so reorganising what was still to come lost your place. You now stay on the exercise you were actually doing.

## v3.31.0 — build 565

- **See the whole day, and rearrange it, from one list** — Tap the exercise counter at the top of a workout to pull up everything you are doing today. Each lift shows how many sets you have left, the one you are on is marked, and tapping any of them jumps straight there. You can move lifts up and down to change the order — useful when a rack or machine is taken — skip one for today, or add another, all without leaving the list. Reordering, skipping and adding were already possible, but only from the card of the lift you happened to be standing on, so changing the order meant walking to each one in turn.

## v3.30.0 — build 564

- **Supersets show the whole group, with everyone's progress** — A linked pair or trio only ever named the exercise either side of the one you were on — so standing on the middle movement of three, you were told about two neighbours without being told they were one round, and nothing said how many sets each still had left. Now every movement in the group sits in a row: its position (A1, A2, A3), its name, how far through it is, and which one you are on. Tap any of them to go straight there instead of swiping back and forth between machines. It shows in focused mode too, which is exactly when you are moving between stations.

## v3.29.1 — build 563

- **Focused mode stops hiding things you need** — The row of actions under a lift — Info, Warm-up, Superset, History, Swap, Pin a note, Plates — was hidden in focused mode, so using it meant dropping out of the mode and back again. The warm-up button was the one that hurt: a ramp is only built when the app already knows your working weight, so the first session of a brand-new program has none, and that button is the only way to add them. In focused mode there was no way to reach it. The last-set technique — partials, myo-reps, a loaded stretch — now also shows on the set it actually applies to, which matters more here than anywhere, because focused mode shows you one set at a time.

## v3.29.0 — build 562

- **Dumbbells and bodyweight lifts now count toward your Strength Score** — Every strength standard in the app was a barbell one, so anyone training with dumbbells got a permanently empty panel telling them to log a squat, bench, deadlift or press — lifts their own program never prescribed and their gym might not contain. Four of the twelve programs the app itself offers could never produce a score. Dumbbell presses, rows and RDLs, and weighted pull-ups, chin-ups and dips, are now measured against their closest barbell equivalent and marked “Est.” so it is clear which numbers came off a bar. Push-ups, goblet squats and split squats are deliberately left out: a rep count is not a load, and a made-up level is worse than none. If you already train with a barbell, your score has not moved by a single point.
- **Your score now says why it moved, and what to lift next** — Strength standards are relative to bodyweight, so gaining weight lowers the score even when every lift holds — correct arithmetic, and baffling to read. The card now separates the two: how much came from your training and how much from the scale. It also names the next target on your weakest pattern, taken from the same table that set your level, so the goal can never disagree with the rank beside it.
- **Fixed: one unknown exercise could stop the app from opening** — A saved program can name an exercise a given version does not have — after restoring a backup from a newer build, or on an older install. That single unknown movement stopped the whole app from starting, and because the program is stored on the device, reloading hit the same wall every time. The exercise now shows as unavailable in that one slot and everything else carries on. Your program is left untouched, so the lift returns intact once the app knows it again.
- **Fixed: the backup reminder could go quiet without a backup existing** — Opening the backup screen was enough to mark you as backed up, so tapping the reminder, glancing at it and going back silenced it for another two weeks with nothing saved anywhere. Only actually sharing, downloading or copying the file counts now. The error screen can also save a backup before you do anything else, since reloading cannot help when the problem is the stored data itself.
- **Fixed: personal records could show the wrong unit** — A workout entry that arrived without a unit recorded on it — from an imported backup, or a much older version — was labelled in kilograms no matter which unit you train in. It now follows your setting, while entries that do carry their own unit keep it, because a record of what you lifted should not change because a preference did.
- **Fixed: panels showed two grab handles** — The bar you pull to close a panel was drawn twice on most of them, leaving a doubled line across the top. Toasts and the resume banner were also being treated as draggable panels, which put a stray handle on them and pushed their text sideways.

## v3.28.1 — build 561

- **Fixed: pulling a sheet down did nothing** — Dragging a panel down by its top was released last version and did not actually work on a device. Panels open with a slide-up animation, and a browser gives an animation priority over the position the drag was setting — so the sheet was being told to move and the animation kept overruling it. The drag now takes that animation off before it starts moving anything.

## v3.28.0 — build 560

- **Pull a sheet down to close it** — Every panel that slides up from the bottom — exercise pickers, plan info, settings, the lot — can now be dragged down by its top to dismiss, with a grab bar showing where. Closing one previously meant reaching for a small X or tapping the strip above it, which on a large phone is exactly where a thumb does not reach. A short pull springs back, so a scroll that overshoots will not throw the panel away.

## v3.27.3 — build 559

- **Internal: every program the app can build is now measured** — The release sweep that checks generated programs against volume landmarks was walking a hand-written list of splits, session lengths and training days — and that list had fallen behind. Ten of the twenty-three splits, both two-hour session lengths, and every two-day program were never checked. The sweep now covers all of them, and a new check fails the build if anything the app offers is ever left out again.

## v3.27.2 — build 558

- **Internal: the set shortcuts are now tested by using them** — The buttons that restore a suggested load or fill in last time’s numbers were checked only by inspecting the code that draws them. A release check now builds a training history, walks into a real session and taps them, confirming each fills the row with exactly the numbers it names — and that it withdraws once taken.

## v3.27.1 — build 557

- **The two shortcuts under a set are readable now** — Restoring the suggested load, or filling in what you lifted last time, were the smallest text in the app — a pair of nine-pixel numbers squeezed under the target, distinguishable only by a tiny arrow, with nothing on screen saying what tapping one would do. They are now proper buttons under the row, each labelled: “Suggested 185×8” and “Last time 180×7”.

## v3.27.0 — build 556

- **Programs now say what they cannot fit** — At 20 to 40 minutes a session holds three or four exercises, so a week is about a dozen movements for the whole body — and something has to give, usually side and rear delts. That prioritising is right, but it was happening silently: you could train a program for months without knowing it contained no lateral raise. A short program now states which muscles get no direct work, so you can lengthen the session, add the movement yourself, or accept it knowingly. It only appears when a muscle gets nothing at all, and never on programs of 90 minutes or more.

## v3.26.1 — build 555

- **Fixed: two bench options that read as the same thing** — Your gym’s equipment list offered “Adjustable Bench” and “Adjustable / Incline Bench” side by side, so it was natural to tick one and move on — and whichever you skipped quietly removed a whole class of pressing from your programs. Ticking only the incline one is what made an incline day open on a flat barbell bench, because no incline press was available to choose. The first is now labelled “Flat Bench”, and selecting an adjustable or decline bench now implies you can also bench flat, since those benches do. Worth re-checking your gym: if you only ticked one, you should now see both flat and incline work appear.

## v3.26.0 — build 554

- **Session times now include the gym, not just the lifting** — The estimate counted your sets, your rest and your warm-up ramp — and nothing for walking to the next station, waiting for it, or loading the first working weight. It read about a quarter short of a real session. Changing exercise is now costed, more for a barbell than a cable and almost nothing for a superset, so the time on the card is the time on the clock. Your programs are unchanged: this corrects what is shown, not how sessions are built.

## v3.25.1 — build 553

- **Fixed: exercises that did not match the day they were on** — In Full Body · Pattern Rotation the squat day could open on a leg press and the unilateral day on a bilateral squat — the pattern was in the day’s name and nowhere in how exercises were chosen. Each day now states the movement it wants, so the squat day opens on a squat, the hinge day on a hinge, the unilateral day on a lunge or split squat, and the deadlift day on a deadlift. Later exercises in the day are still free to vary. A floor press could also open an incline day, because the app did not recognize it as a flat press and so never scored it against the day’s angle.

## v3.25.0 — build 552

- **Fixed: Full Body · Pattern Rotation never programmed a deadlift, and pressed with dumbbells** — Every day in that split was led by a leg movement, and only a day’s main lift draws the heavy barbell variant — so pressing stayed an accessory even in strength and peak blocks, and picked a dumbbell version. No day asked for lower back work either, so a conventional deadlift could never be chosen. The rotation now leads with squat, hinge, incline press, deadlift, then the pump day, so each of the big lifts anchors a session and waves down into low reps like the original Full Body does. Existing programs are unchanged.
- **Session times now learn how long you actually take** — The estimate priced your sets and rest but not the gym around them — walking to the next rack, waiting for one, loading plates. It now compares what past sessions were estimated at against the clock they actually took and adjusts what it shows you. It needs four completed sessions before it changes anything, ignores one left running by accident, and does not alter how programs are built.

## v3.24.0 — build 551

- **The session length picker tells the truth now** — It said “~6 exercises” next to “90 to 120 minutes” — six exercises is about an hour. The count shown is now measured from what the app actually builds, and it accounts for your goal, because rest length decides how much fits: the same time holds more hypertrophy work than strength work. An automated check regenerates programs before each release and fails if any of these numbers drifts from reality.
- **Known: long sessions still come up short** — Choosing 90-120 minutes or longer currently produces days of around 80 minutes. The picker no longer overstates what you will get, but the underlying shortfall is not fixed yet — it is now measured on every release rather than going unnoticed.

## v3.23.2 — build 550

- **Internal: two blind spots closed in the program checks** — The measurement sweep only ever built 40, 60 and 90 minute sessions, so the two longest lengths the app offers — where it struggles most to fill a day — were never checked. It now covers every length. The check harness that deliberately breaks the app to prove the tests still work also no longer writes to the source it is testing.

## v3.23.1 — build 549

- **Internal: the release checks now prove they can fail** — A suite of automated checks is only evidence if it is capable of going red. Eight known past defects are now deliberately reintroduced before each release and every one must be caught, with a harmless control change that must NOT be flagged — so a check that has quietly stopped testing anything is detected rather than trusted. The program measurement sweep also refuses to compare a run that generated nothing, and now reads volume at the first week of a block as well as the peak.

## v3.23.0 — build 548

- **The Plan tab now has four views** — Schedule, Volume, Progression and Blueprint. Schedule is what it always was, with a week strip across the top carrying each week’s sets and running time. Volume shows the week against the landmarks, and tapping a muscle lists the lifts that fed it — including compounds feeding it indirectly, and at what rate. Progression is every lift week by week with its sets, rep target and effort, searchable and filterable. Blueprint charts the block’s planned sets per week alongside its phases. The header also states the week, your session frequency, the sets that week prescribes and the phase it belongs to, over a bar of workouts completed.
- **Home is more compact** — Browse templates, build your own and training cycles were three full-width buttons stacked down the page. They are now one block of four tiles alongside a quick unguided session, each showing its own count. A new program can also be started straight from the header of your program list.

## v3.22.0 — build 547

- **See the session before you start it** — The card on Home now lists the first lifts in today's workout with their sets and reps, and says how many more are in the session — so you can tell what you are walking into without starting it first.
- **New: lift progression** — A table in Plan info showing every lift week by week — sets, rep target and effort — so you can see where the block steps up and what the deload actually removes, instead of paging through one week at a time. Searchable, and filterable to primary lifts, upper or lower.
- **New: volume load wave** — The block’s planned sets per week as a chart, so an accumulation ramp or a deload trough is visible at a glance.
- **Weekly volume now shows what fed each muscle** — Tap a muscle to see which lifts contributed to its weekly total, including compounds that feed it indirectly and at what rate — the reason a total can read higher than the direct work you remember doing.
- **Fixed: muscles at the top of their healthy range read as out of range** — Splitting the volume bands last release left three places still treating only the lower half of the MEV–MRV range as productive, so a well-trained muscle could be counted as outside its range on the progress and volume screens.

## v3.21.0 — build 546

- **Four more splits** — Torso / Limbs (4-5 days) alternates chest-back-shoulder days with leg-and-arm days, so arms are trained in a session where they are not already spent. Push / Pull / Legs / Arms adds a dedicated shoulders-and-arms day to PPL. Upper / Lower / Arms does the same for a 5-day upper-lower. Powerlifting · SBD Wave builds the week around squat, bench and deadlift with hypertrophy accessories behind them. All four are additions — no existing program changes.
- **Weekly volume now reads in four bands, not three** — A muscle between its minimum and its ceiling used to read simply as “productive”, whether it sat in the middle of the range or right at the top. The band now splits at the point the app itself uses when deciding whether to trim a session, so “high” is distinguishable from “productive”, and each band explains what it means.

## v3.20.0 — build 545

- **New split: Full Body · Pattern Rotation** — A second way to run full body, alongside the existing one — each day pairs a leg pattern with an upper push or pull rather than hanging everything off one anchor lift: squat with horizontal push/pull, hinge with vertical, then unilateral, posterior chain, and a pump day. Spreading the pressing across the week means the front delt takes much less of it. In exchange, side and rear delts get slightly less coverage than the original full body, so it suits you better if you already do your own lateral and rear work. The original Full Body is unchanged and still the default.

## v3.19.0 — build 544

- **New: focused mode for the workout screen** — A button in the workout header strips the screen back to one large card for the set you are on — load and reps as big numbers in their own labelled tiles, each with full-size hold-to-repeat steppers, the target and what you lifted at that set position last time on one quiet line above, and a row of pips showing every set in the exercise. Everything you set up before a lift steps out of the way: the exercise strip, coach note, pinned cues, the suggestion banner, warm-up and superset controls, notes. It starts at your first working set rather than walking you through the warm-up ramp, the arrows move between sets so you can go back and correct one, and the rest clock is much larger. Logging is the one wide button in the bar at the bottom, with the effort question offered beside it rather than demanded. Your choice is remembered if you reload mid-workout.
- **Fixed: unreadable text on light themes** — A few buttons and labels referred to a color that does not exist, so they fell back to the page text color and became near-invisible on Mint, Clay, Stone, Slate, Lilac and Rosewater. All of them now use the correct color.
- **Tighter set list** — The effort tag under each logged set was taking a full line of padding, which made a finished exercise read as loose.

## v3.18.1 — build 536

- **Fixed: the rest timer never started on myo-reps in the last exercise** — Once the final working set of a session was logged, the app treated the workout as over and stopped starting rest timers — so any myo-rep or drop set still waiting got no timer at all. Because it triggered on the last working set rather than a position in the list, it hit whichever exercise you happened to finish last. Rest now runs whenever anything is still unlogged.

## v3.18.0 — build 535

- **Rope exercises now show you holding the ends, not the middle** — Every rope movement drew the rope splitting at your hands, so it looked like you were pinching the middle of it. A rope hangs from a carabiner and each hand takes an end, and the strands pull in a straight line toward the machine — face pulls, rope pushdowns, rope hammer curls and rope rows all show that now.

## v3.17.9 — build 534

- **Front raises and face pulls now show the right movement** — Both were drawn as a lateral raise — arms sweeping out to the sides. A front raise is the arm travelling forward to shoulder height, and a face pull is a high pull toward the head with the elbows above the wrists, so both now animate the movement you are actually doing. Every other shoulder raise is unchanged.

## v3.17.8 — build 533

- **Fixed: dynamic double progression could prescribe less than you just lifted** — A lift on Dynamic double could open with weights BELOW the ones in its own Last Time column, and announce a set advancing when nothing in that column had topped the rep range. The app was progressing it from a different workout than the one you were looking at: it normally anchors on your best of the last three sessions of that day, so one off day can't drag your loads down, and it ranks those sessions by the single best set in each. On per-set progression that's the wrong question — an older session can win on set 1 alone while every other set in it was lighter, and then sets 2, 3 and 4 get prescribed from those lighter numbers. Dynamic double now advances each set from your most recent session, so every set progresses from what you actually did last time and the Last Time column is what the prescription is built on. Every other progression style keeps the best-of-three anchor and its protection against a single bad day.

## v3.17.7 — build 532

- **Saved program cards are readable by screen readers** — The card that opens a program had no label of its own, so a screen reader read out everything printed on it run together — the name, the Active badge, the split, the day count — as one long string. It now announces itself as "Open <program name>".

## v3.17.6 — build 531

- **Fixed: correcting an old workout didn't update your training insights** — If you went back and fixed a weight or rep count in a session from a while ago, the app kept using its previous read of your training — plateau detection, effort calibration, recovery and every coach note carried on from the numbers you'd just corrected. It only noticed changes to how MANY workouts you had and which was newest, so an edit in the middle was invisible to it. It now notices any change to what's actually in them.

## v3.17.5 — build 530

- **Fixed: a lift stuck at the same numbers wasn't reported as stalled** — Plateau detection could only see a lift that was getting WEAKER. If your estimated max simply stopped moving — which is what a plateau nearly always looks like — every session tied for "best" and the newest one was read as a fresh record, so the count reset to zero every time. The result was the app switching a stuck lift to recalibrated loading and telling you why in the session, while the coach card said nothing at all. Both now measure it the same way, and adding reps or sets at the same weight still correctly counts as progress rather than a stall.

## v3.17.4 — build 529

- **Fixed: the 1RM calculator's percentage table could ask for more than your max** — Training percentages were rounded to the nearest plate increment, which rounds up as often as down — so an estimated max of 263 lb printed "100% = 265", four pounds heavier than the number directly above it. Every row leaned the same way. They now round down, so no percentage ever asks for more load than the estimate it came from, and 100% stops contradicting the max it's derived from.

## v3.17.3 — build 528

- **Fixed: a workout with a missing date could poison a lift's stats** — A backup restored from another device can contain a workout entry with no date on it — the app never drops those, because losing a logged session is worse than carrying an odd one. But the training model measured "days since you last trained this" straight off that date and produced a nonsense value for the whole lift. Nothing displayed it yet, so nobody saw it; it would have surfaced the first time something did.

## v3.17.2 — build 527

- **The bottom navigation is now readable by screen readers** — The five tabs along the bottom were unlabelled buttons — a screen reader announced them without saying which was which, and nothing marked the one you were on beyond its color. Each tab now carries its name and reports itself as the current page.

## v3.17.1 — build 526

- **What's new on the home screen is a summary again** — Each entry was printing its full write-up — some of them over a hundred words — three deep on the screen you open to start training. They're now trimmed to a line or two each, and the complete text is still there under Full changelog.

## v3.17.0 — build 525

- **Program cards: one button to switch, everything else in a menu** — A saved program carried four icon buttons in a row — switch, next block, duplicate and delete — all the same size and shape, sitting side by side on a screen you scroll past. Delete looked exactly like the one next to it. Switching is the only one you reach for on purpose, so it keeps its tick; the rest moved behind a three-dot menu that names each action in words. The program name gets the space back, and the row stops growing every time a new action is added.

## v3.16.2 — build 524

- **Fixed: unsaved program edits could disappear when opened from the Plan tab** — Editing a saved program holds your changes until you hit Save, so a half-finished edit survives leaving the screen. Opening that program from the Plan tab's "Open full program" loaded the last SAVED version instead of your pending edit — and because the app then saw nothing pending, it discarded the edit entirely. No prompt, no undo. That route now resumes your in-progress changes like every other way into a program.

## v3.16.1 — build 523

- **Fixed: updating a cycle block's engine could scramble other blocks in the cycle** — The block you updated always updated correctly — the damage landed on the OTHER phases of the same cycle. Saving any change to a cycle block was treated as "carry this block's exercises to every phase," with no check for whether anything had actually changed. An engine update regenerates a block from its own settings and usually doesn't touch which exercises it uses at all — but every other phase in the cycle got overwritten with that block's list anyway. The same flaw meant swapping a single exercise on one day could silently overwrite unrelated days on other phases too. Only what actually changed is carried across the cycle now; everything else — including a lift you're mid-block on — is left exactly as it was.

## v3.16.0 — build 522

- **Your plan stops losing a set to autoregulation every session** — If you train a muscle again before it's had time to recover — a five- or six-day full body, most often — the app was trimming an accessory set at the start of most sessions. It was only ever removing volume it already considered spare, but it made that decision fresh every workout, so the volume your program said it would train was never the volume you actually trained. That decision now happens once, when the program is built. You get a plan that's honest about what it's asking for, and autoregulation goes back to responding to a bad day rather than to your schedule.
- **What it will and won't take** — It only removes volume above the point where extra sets stop paying for themselves, never below what a muscle needs to grow, never from your main lift, and never from a muscle you asked for more of. A muscle whose schedule already leaves it time to recover is left completely alone — measured across 864 program configurations, four- and two-day-a-week splits come out identical.
- **Existing programs are offered the update, not given it** — Programs keep running the engine they were built on, so nothing changes mid-block. The Plan screen offers the update and says what it changes; you can take it or keep the program as it is.

## v3.15.1 — build 521

- **Fixed: an effort reading of "0.050000000000000044 RIR"** — The app measures how far your own effort ratings sit from what your performance implies, and quietly corrects for it — so a logged 1 RIR might be read as 0.05. That correction is right and worth keeping, but the number was being printed exactly as computed, floating-point noise and all. Effort now displays to the nearest half rep everywhere it appears. The correction itself keeps its full precision where the load maths uses it.
- **Fixed: un-ticking a set left its numbers behind** — Completing a set retunes the next one from what you just lifted. Un-ticking it put the tick back but left the retune in place — so weights and reps written by a set you had un-logged stayed on screen with nothing to explain them, and a few mis-taps while setting up filled an exercise with numbers you never asked for. Un-ticking now puts the affected set back exactly as the program wrote it. Anything you typed yourself is left alone.
- **The plate breakdown follows the set you're about to do** — The loading strip under an exercise was pinned to the first working set and stayed there. On a ramp it told you to load the bar for set 1 while you were standing at it for set 3. It now reads the next set you haven't logged.

## v3.15.0 — build 520

- **Fixed: doing exactly what the app asked could take weight off the bar** — Every set is prefilled at the bottom of its rep range — that is what the range means: start there and build up. But after you logged one, the app predicted you would get one or two fewer reps on the next set from fatigue, treated that prediction as a failed set, and stripped an increment. Log the 40x10 it asked for and the next set became 35; log 95x10 and the next became 90. The weight now only changes when a set you actually PERFORMED lands outside the range — too many reps and it goes up, a real miss and it comes down. Reps drifting down across your sets is normal and no longer costs you load.
- **Fixed: tightening your RIR did nothing at all** — A block that moves from 2 reps-in-reserve to 1 is asking for more effort at the same reps, so the load should step up. It never did. Unless you had rated every set by hand, the strength estimate quietly used THIS week's target as the effort for LAST week's sets, which cancels out exactly — so 2 RIR, 1 RIR and 0 RIR produced identical suggestions forever. Your logged sets already record the RIR they were prescribed at, and that is what gets read now.
- **Fixed: one lift, three different answers about how it progresses** — The style badge, the advice line under it and the set weights themselves each worked out the progression style separately, and could each get a different answer, because one of them was not told which day it was looking at. That is how a lift could be labelled "e1RM autoregulation" while the advice underneath said "same load, build toward 15 reps" and the sets below were laid out with per-set weights. They all ask one function now.
- **e1RM autoregulation is temporary, and says so** — When a lift stalls, the app switches it to e1RM autoregulation to recalibrate the load. It had no way back: it checked whether you were still stalled by looking at your recent sessions, but autoregulation holds you steady on purpose, so the check could only ever see more flat sessions. The switch is now time-boxed — it hands the lift back to your program's own progression on a new best or after a few sessions either way — and the badge says why it engaged and what ends it.
- **Myo-reps rest for 15 seconds, not two minutes** — The short pause is the whole technique, and the first one never happened: the set that takes you to failure is an ordinary working set, so finishing it started the full working rest. The timer now looks at the set you are about to do rather than the one you just finished, so the pause before the first mini-set is short — and the first drop of a drop set is immediate.
- **Myo-reps and drop sets follow your actual working weight** — The mini-sets copied the weight once, when the exercise was first laid out, and then ignored it. Change the working weight afterwards — by hand, with the steppers, or because the app retuned it mid-exercise — and the extensions were left showing the old number. They are worked out from the set they extend now, every time, so a myo-rep always matches the set it came from and a drop set stays the right fraction of it.
- **No delete button on a myo-rep the program asked for** — Skipping a prescribed mini-set costs you nothing, so a delete control sitting at the end of the row was all risk and no benefit. Extensions you added yourself keep theirs, and the Myo button still removes a prescribed set deliberately.
- **Fixed: the target column could disagree with the weight box** — When the app retuned a set from the one you just logged, it changed the weight but left the target beside it reading the old prescription — so the row showed a target of 40 and a weight of 35 with nothing explaining the difference. The target follows the retune now.
- **The suggestion names the set it came from** — The line above your sets showed your BEST set from last time while the advice beside it was worked out from your LAST one. If your reps fell across the exercise you would read "last 40x15 — build toward 15 reps", which looks like the app failing to notice a set it had just shown you. It now shows the set the suggestion was actually built from.

## v3.14.9 — build 519

- **Program cards are roomier again** — The Switch button added last build was a word taking up card width, which squeezed program names down to a few letters — worst inside training cycles, where the cards are already narrow. It is back to a compact tick; hold it for a moment and it tells you what it does. Long names now wrap onto a second line instead of being cut off, and cycle blocks got a little of their width back.
- **Pick a program from a list instead of hunting for its card** — There is a Switch program button on your Up Next card now. It opens a list of everything you have saved, showing how long each program is, how many days a week it runs, how much of it you have finished and which block of a cycle it belongs to — the things you would actually compare. Your current program is marked and the switch still announces itself with an Undo.
- **Switching programs now tells you it happened** — Changing your active program used to do it silently — the card jumped to the top of the list and nothing else. It now says which program you have switched to and offers Undo, which also puts a training cycle back to the block it was on. The control is a labelled Switch button rather than a tick sitting next to Delete.
- **Fixed: six side-scrolling rows could block the page from scrolling** — Rows that scroll sideways — the gym chips on Home, the equipment lists in Gyms, the chart ranges in an exercise, the gallery filters — could swallow an up-and-down swipe that started on them, so the page underneath stayed put. Same fault as the Plan tab last build, in six other places.
- **Plan phase colors now come from your theme** — The phase colors added in the last build were fixed values that fought most of the themes. Each phase now takes its color from your accent, shifted just enough to tell them apart, so the Plan belongs to whichever theme you are using. Deloads still sit apart on purpose.
- **Plan header rows line up** — The phase strip used fixed-width chips while the stat boxes above divided the width evenly, so the two rows did not share a grid. They now line up edge to edge.
- **Fixed: tapping a day in the Plan sent you to Home** — Opening the Plan tab straight after launching the app and tapping any day bounced you back to the Home tab instead of showing the workout. The Plan works out its own program, but the preview screen was still checking for one you had explicitly opened.
- **Training phases now look like what they ask of you** — Each phase carries its own color and icon — building volume, raising intensity, sharpening, and a cooler quieter one for deloads — in the strip at the top and again on the block it heads, so a phase is one recognisable thing wherever it appears rather than four identical chips.
- **Plan days now say what the session actually is** — Each day in the Plan listed only its name, set count and length. It now also shows the lift it is built around, how many exercises it holds and the muscles it works, so you can tell two days apart without opening either. The phase box no longer cuts long names off half way through.
- **The Plan opens on what's next, and days preview before they start** — The plan now leads with the next workout you owe, and the week you are in is already open when you arrive. Days you have logged are ticked off and the next one is marked. Tapping a day opens a preview of it — the lifts, sets and target loads — with Start as a separate step, instead of dropping you straight into a session.
- **Fixed the phase weeks shown for a cycle** — When a program was one block of a training cycle, the phase strip worked out the later blocks' week ranges from the current block's length, showing impossible spans like "W6-6" and "W10-6". Each block's weeks now add up across the cycle.
- **The Plan tab is now a scrollable week list** — The first version laid the weeks out as a wide grid that had to scroll sideways, which on a phone stopped the page underneath from scrolling at all. It is a straight list now: one row per week showing what it holds and how many of its sessions you have done, grouped under the phase it belongs to, and each week opens to its training days. The nav bar also no longer disappears when you open the tab.
- **New Plan tab — your whole program at a glance** — Until now you could only see one week at a time. The Plan tab lays the entire block out: which phase you are in, a grid of every week against every training day with the sets and time each holds, and an expandable table showing how each lift builds week by week. The deload is marked everywhere it appears. Tap any cell to jump straight into that day at that week.
- **The history grouping switch now always appears** — The By cycle / By month switch added in the last build only showed up if you had built a training cycle, so most people never saw it. It is now always above the session list, and it reads By program when you have not run a cycle, since that is what it groups by.
- **History can group by training cycle** — Your workout list can now be grouped by the cycle and block you were running instead of by calendar month, so a block that ran across the end of a month reads as one block rather than two. Switch between By cycle and By month above the list. Sessions from standalone programs keep their own group.
- **Switch the Strength Club between what you lifted and your estimated max** — The club total is now yours to define. Tap Lifted for the heaviest weight you have actually put on the bar across squat, bench and deadlift — the way the 1000 lb club is traditionally counted — or Est. 1RM for what your best sets project to. The card says which one it is showing, and all three lifts always use the same measure.
- **History no longer blanks out on an incomplete entry** — A workout entry missing its volume figure — which can happen with imported backups or sessions saved by much older versions — took the entire History tab down instead of just showing zero for that one line.
- **Strength score over time now tracks strength, not bookkeeping** — The score-over-time chart could show a long decline while every one of your lifts was getting stronger. Lifts you train less often were counted less at first and more later, so the line drifted downward as the weighting caught up, and the card would report something like a 16-point drop and a lower level. Every point on the chart is now weighted the same way, so the line moves when your lifts move and stays put when they don't.
- **Strength standards no longer crashes the Progress tab** — Tapping the edit button on the Strength standards card took the whole Progress tab down with a blank error. The card was doing a piece of its calculation in a place that only ran some of the time, which is something React refuses to allow. It runs every time now. The earlier attempt at this fix in build 501 changed the wrong thing and the crash survived it.
- **Up Next shows the workout, not the gym notes** — The Up Next screen was listing your pinned notes and machine setup under every lift. That is information you need standing at the machine, not deciding whether to train, and it pushed the actual prescription down the screen. Notes and setup still appear in the session itself, where you use them.
- **Myo-reps show correctly in history** — When you view a past session, myo-reps and drop sets are now labeled as M/D instead of numbered like working sets, so you can tell at a glance which sets were extensions. Records (PRs, best e1RM, top volume) now exclude extensions correctly.
- **Strength standards edit fixed** — Clicking the edit button to update your profile on the Strength standards card now works correctly.
- **Search for exercises when swapping** — When replacing an exercise in a workout or program, tap the exercise list to filter it by name instead of scrolling through all available options.
- **Unweighted exercises count in recovery trends** — Pull-ups and dips logged without weight now include set count in exerciseTrends and underRecoveredWeekly, giving an honest recovery cost to bodyweight work.
- **"Sets you've actually logged" means working sets** — Myo mini-sets and drop sets are extensions of the set above, and every other screen already treats them that way — but the weekly logged-volume counter added them in as full sets. Three minis on an overhead press showed as six sets of shoulders instead of three, and then got compared against volume landmarks that are defined in working sets. There were also two copies of that counter, and the weekly volume advice used one of each.
- **Editing one block of a cycle keeps the others intact** — Saving an edited block carries the change to its sibling blocks. The rule that does the carrying existed twice — once for real, once in a simplified copy that the tests were checking — and the copy could not remap a day's main lift or its supersets. Reordering a day in one block could quietly point another block's primary at the wrong exercise.

## v3.12.0 — build 498

- **Tap a muscle to see what trains it** — "This day hits" showed numbers like Glutes 7.7 with no way to ask where 7.7 came from — and on a day with no glute exercise on it, that reads like a bug rather than three lifts each paying a share. Tap any muscle now and it opens the exercises behind it, with the arithmetic shown: direct work counts its full sets, secondary work shows the sets and the share it contributes.
- **The breakdown is the number** — It is built from the same pass that produces the chip rather than a second walk over the day, so what you open always adds up to what you tapped.

## v3.11.2 — build 497

- **One strength curve, not eight** — The formula behind every estimated max in the app was written out by hand in eight different places, in four slightly different variants. That is why the same lift could read 291 in one screen and 300 in another: a fix applied to one copy never reached the rest. There is one now, and everything else asks it.
- **The rep-target table agreed with nothing** — Its one-rep row divided your 1RM by an extra factor, so a 300 lb max was listed as a 291 lb single — while the strength charts, correctly, said 300. Same cause: a second copy of the maths. Fixed, and the whole table now matches the charts.
- **Warm-ups and drop sets counted consistently** — The rule for what counts as a working set was re-typed dozens of times and some copies had drifted, so a few older screens still counted a drop set or a myo mini as a full set. They all ask the same question now.

## v3.11.1 — build 496

- **"Last 225×5" when you did 225×6** — Two screens were printing the wrong number for the same reason. The weight and reps stored against a lift are deliberately the fewest reps you held at the top weight — the cautious figure the app uses to pick your next load. Printed as "last 225×5" on the program view, it reported a set you had already beaten. Personal records had a second version of the same problem: among sets at the same weight it kept whichever it saw first, so the first time you ever touched 225 owned the record no matter what you did on it later. Both now read your best actual set.
- **Drop sets stay drop sets in your history** — Myo mini-sets and drop sets are marked as extensions while you train, but that mark was thrown away when the session was saved — so anything reading your history later counted them as full working sets again, including the new volume-per-set chart. The mark is saved with them now.

## v3.11.0 — build 495

- **The cycles tab tells you where you are** — It listed the plan and nothing else — no progress, no dates, no sense of what you had actually done. Every cycle now carries a progress bar, the week you are on out of the total, how many weeks are left, how many sessions you have logged against it, and the calendar window it runs in. All of that already existed one screen deeper; the list just could not reach it.
- **Finished blocks can be reviewed** — A completed block used to be a checkmark. Tap Review and you get what actually happened in it: which lifts moved and by how much, each muscle's weekly volume set against whether it progressed, and a concrete change to carry into the next block. It is offered when you complete a block too — the moment it is most worth reading.
- **One note at a time, not four** — Every block printed its full intent, so a four-block cycle was four paragraphs you read once months ago. Only the block you are in shows its note now. Finished blocks show what you logged, upcoming ones show which weeks they land in, and the detail page still has every word.

## v3.10.0 — build 494

- **Six ways to read a lift, over any range** — The exercise library charted one thing — estimated 1RM, every session you have ever logged. It now charts estimated 1-RM, 3-RM and 10-RM, your heaviest actual weight, total volume, and volume per set, across 1W, 1M, 3M, 6M, 1Y or all time. The rep maxes come from inverting the same formula the rest of the app uses, so the 1-RM you see here is the same number your records tab calls your best.
- **The exercise page is a full screen now** — It was a sheet sliding up over the list, capped at most of the screen height, with the library still showing behind it. What is in there — the demo, every session you have logged, the charts, your PRs and rep maxes — is a destination, not an aside. It gets the whole screen and its own back button.
- **The charts are spaced by date, not by session** — The old chart put every session an equal step apart, so a fortnight off looked identical to training twice in a week. Points now sit where they actually happened, which is what makes the range buttons mean anything.

## v3.9.3 — build 493

- **Myo-rep minis stopped counting as full sets** — When the app adds myo-reps to a lift, the three short mini-sets were being treated as three more working sets — so a five-set exercise showed eight numbered sets, the session's set total was inflated, and each mini claimed a last-session set that never existed. They're extensions of the set above and now read that way: indented, marked M, and out of the count.
- **A 4-rep myo mini no longer rewrote your set of twelve** — The same problem reached your logs. The summary each lift is remembered by takes the lowest reps you held at the top weight — and it was counting the mini-sets, so twelve reps of lateral raises were recorded as four, and the next session's target was cut to match. Drop sets did the same through their stripped load. Extensions are now excluded from that summary, which is also what your next opening weight is built from.
- **A carried-over weight says it's carried over** — When a lift only has one recorded weight rather than a set-by-set record, that figure stands in for every set — but it looked exactly like a real per-set record, so set five appeared to report a set you may never have done. It's now tagged LIFT.

## v3.9.2 — build 492

- **The set you just finished waits for you** — Ticking a set sent the workout straight on to the next one — and asked how hard the last one was inside the row it had just dimmed and moved past. The set you've finished now stays lit until you've said what it felt like, and says plainly that it's optional. The next set goes live at the same moment, so ignoring the question costs you nothing but reaching for the steppers.
- **The effort a set was asked for stays on the set** — The target — 2 RIR, 85%, to failure — vanished the instant you logged the set, which is the exact moment you're asked what it actually felt like. It stays put now, so the number you're comparing against is still on screen. Logged efforts on earlier sets collapse to a tag you can tap to change.

## v3.9.1 — build 491

- **One coach on the workout screen, not two** — The session had two coach panels stacked at the top saying different things in the same voice — a strip explaining what the block is asking of you this week, and a card explaining what the app changed about today's numbers for you specifically. They're one card now. The block's note is still there, at the end of the coach note, and it's the first thing you read when there's nothing personal to say yet.
- **Plan info reads as one screen** — "How this was built", "Weekly volume" and "Block plan" had each been built with their own card style — different corners, different backgrounds, different heading sizes — which made three sections of the same reference look like three unrelated things. They share one card now.

## v3.9.0 — build 490

- **Hold the + or − to run the weight up** — Getting from an empty bar to a working weight was around eighteen taps. Press and hold either stepper — weight or reps — and it repeats, starting slow enough to stop on the number you want and speeding up the longer you hold. A single tap still moves exactly one increment.

## v3.8.1 — build 489

- **Your record is your best set, not your worst** — Bench 225×6 and then 225×5, and the record read 225×5. The app keeps a cautious summary of each lift — the reps you held across every set — because that's the right number for deciding your next load. It was also being used as the set you're remembered by, where it reports a set you already beat. Records and PR estimates now both come from your best logged set.

## v3.8.0 — build 488

- **Editing a program waits for you to save it** — Swapping an exercise used to commit straight to your saved program — no button press, no warning on the way out. Changes are now held as a draft: the Save button tells you when something's pending, going back asks whether to keep or discard, and the version in your library doesn't change until you say so. The draft is written to your device as you edit, so it survives closing the app or a crash mid-edit — reopen and it's still there, still asking.

## v3.7.0 — build 487

- **The set you're on reads the way it used to** — The target, the effort it's asked at and the set you're beating are stacked in one column again, and the plate breakdown is back under the weight it describes. Both still appear only on the set you're working. The highlight now runs the full width of the screen instead of floating as an inset card, and the set number has room to breathe.
- **A lift with one recorded weight uses it for every set** — If the app only had a summary of a lift — one weight and rep count, which is what older saves and carried-over lifts store — only your first set showed it. Set two onward read "new", as if the same lift in the same session had no history. That summary now stands as the reference for every working set.

## v3.6.3 — build 486

- **Day menu fix, without the extra dependency** — The previous fix worked but pulled in a library the single-file build can't load. Same result, no new dependency: the menu is now rendered above the day list instead of inside a day card.

## v3.6.2 — build 485

- **The set you're on reads as one line again** — The last update put the effort target, technique cue, where the weight came from and last session's set on separate lines — but only on the set you're working, which made that row the tallest on screen and shifted every row below it each time you logged a set. Same information, now a single strip under the row. The plate breakdown moved into it too, so it's back and it's with the rest of what that set needs.

## v3.6.1 — build 484

- **The day menu opens where you tapped** — The last fix wasn't enough: the menu could still vanish on a collapsed day, or open far down the screen on an open one. A performance optimisation on the day card was quietly redefining what "anchored to the screen" meant, so the menu was being placed relative to the card instead of the window. It now renders outside the card entirely.

## v3.6.0 — build 483

- **Tapping your next workout shows just that workout** — It used to open your whole program — every day of the split, every week — and leave you to find the session you were already being pointed at. Now it opens the day itself: what's in it, what each lift asks for, your pinned notes, and Start. The full program is one tap away under "View full program", and Back brings you straight back here.

## v3.5.1 — build 482

- **The day menu no longer gets cut off** — On a collapsed day, the 3-dot menu was clipped by the day's own card. It now opens against the screen, and flips above the button when it would run off the bottom.
- **You can decline an engine update** — The offer to rebuild a program on a newer engine had no "no". It does now — "Keep it as it is" dismisses it for good on that program. A future engine will ask again, since a new correction is a new question, and Plan info keeps a way to change your mind.

## v3.5.0 — build 481

- **The workout screen shows the set you're on** — Every set row looked the same, so finding your place mid-workout meant reading the table. The set you're on now keeps full contrast and an accent rule; the rest dim. The plate breakdown, the effort target, the technique cue and last session's set follow that row instead of appearing under all six at once — same information, arriving as you reach it.
- **Warm-ups get out of the way once they're done** — A ramp set carried the same full-size row as a top set, four of them above the work. Once they're all logged they collapse to one line. Tap it to bring them back — a mis-logged ramp is still fixable.
- **The coach note shrinks once you start lifting** — It's the card you want before the first set and a banner you're reading past by the third. After your first logged set it becomes a one-line pill, still a tap from the full note.
- **Plan info moved off the top of your program** — "How this was built", "Weekly volume" and "Block plan" now live behind a Plan button next to the day selector. All three are unchanged — they're reference you consult, not something you read on the way to training.
- **A saved program stops looking like a construction site** — While you're building, the day controls stay out as buttons. Once saved, rename and duplicate collapse into a 3-dot menu — which also gives Delete a home it never had here. The exercise rows lost their up/down arrows (the drag handle already did that job), the RIR tooltip, and the pinned-note readout; notes still show during the session, where you act on them.
- **Leaving an unsaved program asks first** — Back used to discard a freshly built program silently. It now offers to save it.

## v3.4.0 — build 480

- **Update an older program to the newest engine** — A program keeps the version of the training engine it was built on, so its prescriptions never shift underneath you mid-block. That protection had no exit: a program built last month could never receive a correction made since, including corrections the research forced. Any program built on an earlier engine now says so, lists in plain language what updating would change, and rebuilds on request using the same seed — so everything that changes is the update and nothing is a reshuffle. Your logged sets are untouched, and the version you had waits under Earlier shuffles if you want it back.
- **What the volume ceiling actually means** — Weekly sets above the ceiling were labelled junk volume. The best current evidence — a 2026 analysis of 67 studies — finds size and strength still improving as weekly sets rise, with diminishing returns rather than a point where more starts to hurt. The ceiling is a recovery budget worth watching when progress or readiness slides, not a line past which your sets stopped counting, and it now says so. Same numbers, same colors, honest claim. The same analysis confirmed that counting an indirect set as half a set predicts growth best, which is what this app already does.

## v3.3.4 — build 479

- **Strength and peak blocks lift the barbell** — A strength or peak block was given its heavy sets and reps on whichever lift suited muscle growth best — so a full-gym strength program could be built on a dumbbell bench and a hack squat, with no barbell bench, squat or deadlift anywhere in it. The main lift of a strength-focused day is now the barbell you can add weight to and test. Hypertrophy blocks are unchanged, and every block in a cycle shares the same main lift so your logged weights still carry forward.
- **Ban an exercise from a saved program** — Banning a lift from the exercise menu was only offered while a program was still unsaved — which meant it never appeared for a training cycle at all, since a cycle saves its blocks the moment it's created. It's now available wherever the menu is.
- **The clock stops cutting your side delts** — When a session ran long, the exercise it dropped to fit the time was chosen by looking at the whole shoulder total — a number your pressing already fills out. So the last lateral raise in the day looked free to cut, and side or rear delt work could disappear from a program entirely even though it had just been added on purpose. Trimming for time now knows the difference between the three heads. Across a 1,458-program sweep, programs training no side delts at all fell from 33 to 18, and side and rear delt work below the minimum effective dose fell by about a fifth.

## v3.3.3 — build 478

- **Bench angles match the day** — A day called "Push · Chest" could be built around an incline press while the flat bench landed on the delt day, and "Incline + Pull" could get a flat bench. Days that name an angle now anchor on the right variant.
- **Myo-reps you can actually log** — When a block prescribes myo-reps, the mini-sets now appear in your set list instead of only being mentioned in a note — so those reps count toward your volume and estimates.
- **Report a bug** — Settings has a Report a bug section that emails pursuitiron@gmail.com with your version and build already filled in.

## v3.3.2 — build 477

- **No more empty training days** — With a very limited equipment list and bodyweight excluded, a program could generate a day with no exercises in it at all. The fallback that fills a day only ever considered the first muscle it wanted; it now tries everything the day was meant to cover.

## v3.3.1 — build 476

- **"Train this less" no longer means "barely at all"** — De-emphasising a muscle was cutting it by around 90% — chest went from about 11 sets a week to 1, which is below the level needed to hold what you've built. It now keeps a maintenance dose and gives the rest of the room to what you actually asked for.

## v3.3.0 — build 475

- **Session estimates count your warm-ups** — Longer warm-ups for heavy lifts weren't being counted in the session time estimate, so a session budgeted at 90 minutes could really take 100 or more — and because the app fills sessions up to that estimate, it was overfilling them. Warm-up time is now costed from the actual ramp.

## v3.2.9 — build 474

- **Two muscles were being counted twice** — Reverse Curl and Curtsy Lunge each credited their own main muscle a second time as a supporting one, inflating that muscle's weekly volume by half. That made a muscle look like it had met its target when it hadn't, or trip a ceiling it shouldn't have.

## v3.2.8 — build 473

- **Full Body described accurately** — The Full Body split said it trains everything each session. It doesn't — it rotates, covering several movement patterns a day so every muscle gets trained two or more times a week. That's the better design, and now it's what the description says.

## v3.2.7 — build 472

- **Template labels match what they build** — Two templates were labelled as pure strength programs while being configured for strength and size. Every template's day count, goal and structure is now checked against what it actually generates.

## v3.2.6 — build 470

- **Warm-ups that match the weight** — How many warm-up sets a lift got depended on where it sat in the session, not on how heavy it was — so a 495 lb squat in the second slot ramped straight from 345 to 495 in one jump, and the same lift at 135 got the identical ramp. Warm-ups now scale with the actual load, so heavy work is approached properly and light work isn't padded.
- **Bad data can't become a prescription** — A damaged or hand-edited backup could put an impossible weight into your history — negative, or absurdly large — and the app would hand it straight back as the weight to load next session. Those rows are now ignored the same way a missing one is.

## v3.2.5 — build 469

- **Delt coverage across every split** — The side and rear delt work added recently was only ever verified on a few splits. Checking all eighteen found programs that trained neither head at all — mostly on shorter sessions, where there's no room to add anything. Those now convert one front-delt press, which the rest of your pressing already covers, into the work that's actually missing.

## v3.2.4 — build 468

- **Side and rear delts now hit their target** — Lateral and rear-delt work is spread across two days instead of stacked onto one. Stacking meant a later step that merges duplicate isolations quietly folded them back together and lost sets — so side delts fell short in nearly every program even though the app thought it had fixed them. They now clear their target, and splitting the work across days suits delts better anyway.

## v3.2.3 — build 467

- **Side and rear delts survive a strength block** — The check that guarantees side and rear delt work only ran on hypertrophy goals — and on a strength goal every shoulder slot fills with presses, so a powerbuilding peak block could end up with three pressing movements and no lateral or rear work at all. It now runs whatever your goal is.
- **Sessions closer to the length you picked** — Choosing "90 to 120 minutes" was producing 65-minute sessions, because session length set an exercise count that was never checked against the actual duration. Days now fill toward the bottom of the range you chose, spending the extra time on whichever muscle is furthest below its target.
- **Swaps offer what your program is missing** — Swapping an exercise now floats options that fill a gap in your current program to the top — so replacing an overhead press suggests a lateral raise when your side delts are short, instead of listing every other press first.
- **Barbell limit label fixed** — The program summary said "Barbell cap 1 lb". It's a limit on barbell movements per session, not a weight, and now says so.

## v3.2.2 — build 466

- **See how a program was built** — The choices you made when you created a program — what to emphasise, what to cut back, session length, block length, deload — are now shown on the program screen under "How this was built", and the emphasis and reduction choices appear on each card in your library. Those two in particular change your set counts without changing the exercise list, so there was previously no way to confirm they'd been applied.
- **Strength standards render fix** — The strength standards section could fail to load for some histories. Fixed, and when a section does fail it now names which part of the app it happened in.

## v3.2.1 — build 465

- **Side and rear delts stop getting skipped** — Shoulder volume was being judged as one number, and that number looked healthy while the three heads underneath it were badly out of balance — front delts well past their useful ceiling from all the pressing, side and rear delts below the minimum to grow in almost every program. Each head is now checked against its own targets, so the ones that only grow from deliberate work actually get it.

## v3.2.0 — build 464

- **Volume that stays inside the lines** — High-frequency plans were pushing delts and triceps past the point where extra sets cost recovery without buying growth — worst case a third over the ceiling, and it got worse the more days you trained. Every press was quietly adding shoulder volume the plan wasn't counting. Programs now check both ends: enough work to grow, not so much that you're training your own fatigue.

## v3.1.10 — build 464

- **Every muscle gets enough work** — Generated programs now guarantee each muscle clears its minimum effective volume for the week. Calves were the worst offender — they came in under the minimum in about four of every five programs, at any training frequency. The fix respects your time limit and any muscle you've asked for less of, and it won't push another muscle past its recoverable ceiling to get there.

## v3.1.9 — build 462

- **The plan's volume is the volume you train** — Recovery used to compare your session against the AVERAGE gap between times a muscle is trained — a spacing your split may never actually schedule. On any uneven rotation that made the plan's own session look early, and a set got trimmed from a day the program deliberately chose. It now uses the tightest gap the rotation really produces, so following the plan means training the volume it designed. Train sooner than that and it still adjusts.

## v3.1.8 — build 461

- **It knows its own programming** — A block that programs near-failure work no longer reports it as you overreaching. It still trims an accessory set when you train hard on one day's rest — that's the point of autoregulation — but it names the block instead of telling you that you pushed too hard, because on an intensification week you did exactly what was asked.

## v3.1.7 — build 459

- **History grouped by month** — Your session list now breaks into months with a sticky heading that carries that month's session count and total volume — so scrolling back through a year tells you something instead of just going on forever.
- **Fixes** — Programs failed to open once you had a few sessions logged — that's fixed. The unit you pick in Settings is no longer asked for again when you build a program, gym equipment is sorted alphabetically so the two hip machines sit together, and the Back and Done buttons no longer collide with the text beside them.

## v3.1.6 — build 454

- **Find things in Settings** — Settings jumps straight to a section now instead of making you scroll past everything — Training, Appearance, Workout, Profile, Data, Backup, About. And an empty Progress tab offers your next workout by name rather than just telling you it's empty.

## v3.1.5 — build 453

- **Program setup you can cut short** — The builder now shows which question you're on out of how many, and once you've set your equipment you can generate straight away — the remaining questions all have sensible defaults and are there to refine, not to finish.

## v3.1.4 — build 452

- **The warm-up card remembers you** — If you have your own warm-up, tap Hide once and the checklist stays gone — it used to come back at the top of every single session. Settings can bring it back. Browsing templates also puts the recommended ones first now.

## v3.1.3 — build 450

- **Plateaus counted per day** — If a lift appears on two days of your program — heavy on one, higher reps on the other — the lighter day's sets were being counted as sessions without a PR, so the app called a plateau on a lift that was progressing fine. Stalls are now tracked per training day.
- **Better share cards** — Your top set now breaks ties on reps, so logging 195×4 and then 195×6 shares the 195×6. Shared images also carry the Pursuit Iron name.

## v3.1.2 — build 449

- **Machine settings are back** — Pin the seat, back pad, thigh pad, ankle pad and start position for a machine and the app remembers them for next time. This had quietly stopped working on most machines — leg curls, leg extensions, pec decks, chest presses and more offered no settings at all. All 116 machine exercises have them again, and only the ones each machine actually has.

## v3.1.1 — build 448

- **Back always gets you out** — Opening a cycle block, jumping to its cycle, then opening a block from there could trap the back button bouncing between the two screens with no way home. Navigation now remembers the whole path you took instead of just the last step.

## v3.1.0 — build 447

- **A home screen that answers the question** — Today's workout now sits at the top, above the streak and totals — the thing you opened the app for is the first thing you see. The template carousel retires itself once you're a few sessions into a program, so your own library isn't buried under suggestions you've already passed on.
- **Easier to hit, easier to see** — Small controls — the +/− steppers, reorder arrows, sheet close buttons — now have a full-size touch area even though they still look the same, so you miss them less with a thumb mid-set. Sheets close with Escape, announce themselves properly, and there's a visible focus outline if you're using a keyboard.
- **Templates named for what they do** — Program templates now carry descriptive names — Main-Lift Waves, Tiered Linear Progression, Ramping 5×5, Straight 5×5 — instead of borrowing coaches' and brands' names. The programming is unchanged: same percentages, same progression, same saved programs and share codes. Only the labels moved.
- **Text you can actually read** — Every color the app uses as text was measured against the surface behind it, and the ones that fell short were corrected — in all fourteen themes. Secondary labels, the amber warnings and the accent links are all legible now, including on the light themes where they were faintest. The themes still look like themselves.
- **Your birthday, not your age** — Strength standards scale by age, and a typed-in age quietly went stale — a lifter who entered 39 kept being scored against 39-year-olds for years. Enter a birthday once and it stays right. If you'd set an age before, it's been carried over as an estimate; set the real date and it'll sharpen.
- **Bodyweight tracking that keeps up** — Weigh-ins now hold about five and a half years instead of six months, and each one remembers the unit it was taken in — so switching between lb and kg, or merging two devices, no longer shows a phantom gain or loss. Body measurements convert properly between inches and centimeters too.

## v3.0 — build 441

- **Fits smaller screens properly** — The workout screen now rearranges itself on compact and folded phones — the weight and rep steppers drop to their own line instead of getting squeezed, the elapsed timer keeps its full readout, and Finish shrinks to a check so nothing runs off the edge. Every bottom button clears the gesture bar too.
- **Set up each gym you train in** — Your commercial gym, your garage, the hotel rack — each one keeps its own equipment list, and switching gyms rebuilds what you're offered. Programs are built for the place you're actually standing in.
- **Tell it the weights you own** — Enter your real dumbbell rack — 5, 10, 15 or up to 100 — and it stops asking for loads that don't exist. At the heaviest bell it holds the weight and builds reps instead of inventing a 62.5.
- **Plateau warnings that mean it** — A stall is now judged per training day, so the same lift on push day and leg day is never mixed into one misleading line. And working through a rep range set by set counts as progress, because it is.
- **Share a session** — Any workout in Progress becomes a clean card — your lifts, top sets, volume and time.
- **History where you need it** — Open any exercise in a program to see every session you've logged for it on that day, with your best marked.

