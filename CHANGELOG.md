# Pursuit Iron — Changelog

74 changes across 41 releases. Newest first.

_Generated from the app's own changelog data — do not edit by hand._

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
- **What the volume ceiling actually means** — Weekly sets above the ceiling were labelled junk volume. The best current evidence — a 2026 analysis of 67 studies — finds size and strength still improving as weekly sets rise, with diminishing returns rather than a point where more starts to hurt. The ceiling is a recovery budget worth watching when progress or readiness slides, not a line past which your sets stopped counting, and it now says so. Same numbers, same colours, honest claim. The same analysis confirmed that counting an indirect set as half a set predicts growth best, which is what this app already does.

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
- **Text you can actually read** — Every colour the app uses as text was measured against the surface behind it, and the ones that fell short were corrected — in all fourteen themes. Secondary labels, the amber warnings and the accent links are all legible now, including on the light themes where they were faintest. The themes still look like themselves.
- **Your birthday, not your age** — Strength standards scale by age, and a typed-in age quietly went stale — a lifter who entered 39 kept being scored against 39-year-olds for years. Enter a birthday once and it stays right. If you'd set an age before, it's been carried over as an estimate; set the real date and it'll sharpen.
- **Bodyweight tracking that keeps up** — Weigh-ins now hold about five and a half years instead of six months, and each one remembers the unit it was taken in — so switching between lb and kg, or merging two devices, no longer shows a phantom gain or loss. Body measurements convert properly between inches and centimetres too.

## v3.0 — build 441

- **Fits smaller screens properly** — The workout screen now rearranges itself on compact and folded phones — the weight and rep steppers drop to their own line instead of getting squeezed, the elapsed timer keeps its full readout, and Finish shrinks to a check so nothing runs off the edge. Every bottom button clears the gesture bar too.
- **Set up each gym you train in** — Your commercial gym, your garage, the hotel rack — each one keeps its own equipment list, and switching gyms rebuilds what you're offered. Programs are built for the place you're actually standing in.
- **Tell it the weights you own** — Enter your real dumbbell rack — 5, 10, 15 or up to 100 — and it stops asking for loads that don't exist. At the heaviest bell it holds the weight and builds reps instead of inventing a 62.5.
- **Plateau warnings that mean it** — A stall is now judged per training day, so the same lift on push day and leg day is never mixed into one misleading line. And working through a rep range set by set counts as progress, because it is.
- **Share a session** — Any workout in Progress becomes a clean card — your lifts, top sets, volume and time.
- **History where you need it** — Open any exercise in a program to see every session you've logged for it on that day, with your best marked.

