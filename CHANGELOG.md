# Pursuit Iron — Changelog

45 changes across 26 releases. Newest first.

_Generated from the app's own changelog data — do not edit by hand._

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
