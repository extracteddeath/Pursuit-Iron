import { setShellEquipmentExpander } from '../next-engine/shell-equipment.js';
// Canonical catalog domain. Maintained production source; independent of React and browser APIs.



const EQUIPMENT = [
    { id: "barbell", label: "Barbell", cat: "Bars" }, { id: "dumbbell", label: "Dumbbells", cat: "Free Weights" },
    { id: "bench", label: "Flat Bench", cat: "Benches & Racks" }, { id: "cable", label: "Cable Machine", cat: "Machines" },
    { id: "machine", label: "Selectorized Machines", cat: "Machines" }, { id: "smith", label: "Smith Machine", cat: "Machines" },
    { id: "ezbar", label: "EZ Curl Bar", cat: "Bars" }, { id: "pullup", label: "Pull-up Bar", cat: "Bodyweight" },
    { id: "dip", label: "Dip Station", cat: "Bodyweight" }, { id: "kettlebell", label: "Kettlebells", cat: "Free Weights" },
    { id: "bands", label: "Resistance Bands", cat: "Bands" },
    /* APPEND-ONLY BOUNDARY — CODE_EQUIP is index-based in every program code ever issued. New ids go
       BELOW; nothing above may be reordered or removed.
       "Selectorized Machines" was one bucket holding 44 distinct machines, so a gym with a leg press
       but no hack squat had no way to say so. These split the ones that most often differ between
       gyms. Existing users are granted every id their old coarse category implied (see the store
       migration and LEGACY_EQUIP_IMPLIES), so nobody's available exercises change on upgrade. */
    { id: "legpress", label: "Leg Press", cat: "Machines" },
    { id: "hacksquat", label: "Hack / V-Squat", cat: "Machines" },
    { id: "legext", label: "Leg Extension", cat: "Machines" },
    { id: "legcurl", label: "Leg Curl", cat: "Machines" },
    { id: "pecdeck", label: "Pec Deck", cat: "Machines" },
    { id: "machinerow", label: "Chest-Supported Row", cat: "Machines" },
    { id: "assisted", label: "Assisted Pull-up / Dip", cat: "Machines" },
    { id: "calfmachine", label: "Calf Raise Machine", cat: "Machines" },
    { id: "abduction", label: "Hip Abduction", cat: "Machines" },
    { id: "reversehyper", label: "Reverse Hyper", cat: "Benches & Racks" },
    { id: "ghd", label: "Glute-Ham Developer", cat: "Benches & Racks" },
    { id: "preacher", label: "Preacher Bench", cat: "Benches & Racks" },
    // Tranche 2 — the rest of the selectorized bucket. All cat:"Machines", so isMachineLike picks
    // them up automatically and no engine predicate needs touching.
    { id: "machinepress", label: "Chest Press Machine", cat: "Machines" },
    { id: "machineshoulder", label: "Shoulder Press Machine", cat: "Machines" },
    { id: "machinelatraise", label: "Lateral Raise Machine", cat: "Machines" },
    { id: "machinecurl", label: "Curl Machine", cat: "Machines" },
    { id: "machineext", label: "Triceps Machine", cat: "Machines" },
    { id: "beltsquat", label: "Belt Squat", cat: "Machines" },
    { id: "hipthrustmachine", label: "Hip Thrust Machine", cat: "Machines" },
    { id: "kickback", label: "Glute Kickback Machine", cat: "Machines" },
    { id: "machinecrunch", label: "Ab Crunch Machine", cat: "Machines" },
    { id: "machineshrug", label: "Shrug Machine", cat: "Machines" },
    { id: "machinepullover", label: "Pullover Machine", cat: "Machines" },
    { id: "adduction", label: "Hip Adduction", cat: "Machines" },
    // Tranche 3 — loadable bars that are NOT a straight barbell. Unlike the machine tranches these
    // cannot be split blind: `includes("barbell")` drives the fatigue model, axial-load cost, the
    // per-day bar-lift cap, the increment model and the progression style. Every one of those means
    // "a loaded bar" and now asks isBarLike; only the plate maths still means a specific bar, because
    // a trap bar weighs 25kg and a straight bar 20kg.
    { id: "trapbar", label: "Trap / Hex Bar", cat: "Bars" },
    { id: "safetybar", label: "Safety Squat Bar", cat: "Bars" },
    { id: "landmine", label: "Landmine", cat: "Bars" },
    /* Tranche 4 — benches. `bench` appears in NO engine predicate (unlike barbell), so this is a pure
       availability split: it only changes which exercises a gym can do. `bench` now means a flat bench;
       an adjustable one adds inclinebench. Most people who own a bench own an adjustable, few own a
       decline, and the preacher station finally has exercises pointing at it. */
    { id: "inclinebench", label: "Adjustable / Incline Bench", cat: "Benches & Racks" },
    { id: "declinebench", label: "Decline Bench", cat: "Benches & Racks" },
];

const ALL_EQUIP_IDS = EQUIPMENT.map(e => e.id);

const RAW = [
    ["bb-bench", "Barbell Bench Press", "chest", "compound", ["barbell", "bench", "rack"], 5, 10],
    ["inc-db-press", "Incline Dumbbell Press", "chest", "compound", ["dumbbell", "inclinebench"], 8, 12],
    ["inc-bb-bench", "Incline Barbell Press", "chest", "compound", ["barbell", "inclinebench", "rack"], 6, 10],
    ["low-inc-bb-bench", "Low-Incline Barbell Press", "chest", "compound", ["barbell", "inclinebench", "rack"], 6, 10],
    ["high-inc-bb-bench", "High-Incline Barbell Press", "chest", "compound", ["barbell", "inclinebench", "rack"], 6, 10],
    ["machine-press", "Machine Chest Press", "chest", "compound", ["machinepress"], 8, 12],
    ["db-bench", "Dumbbell Bench Press", "chest", "compound", ["dumbbell", "bench"], 8, 12],
    ["dips-chest", "Chest Dip", "chest", "compound", ["dip"], 6, 12],
    ["assisted-dip", "Assisted Dip", "chest", "compound", ["assisted"], 8, 12],
    ["cable-fly", "Cable Fly", "chest", "isolation", ["cable"], 12, 20],
    ["inc-cable-fly", "Low-to-High Cable Fly", "chest", "isolation", ["cable"], 12, 20],
    ["pec-deck", "Pec Deck", "chest", "isolation", ["pecdeck"], 12, 20],
    ["smith-bench", "Smith Machine Bench Press", "chest", "compound", ["smith", "bench"], 6, 10],
    ["db-fly", "Dumbbell Fly", "chest", "isolation", ["dumbbell", "bench"], 12, 18],
    ["pushup", "Push-Up", "chest", "compound", [], 10, 20],
    ["pullup", "Weighted Pull-Up", "lats", "compound", ["pullup"], 6, 12],
    ["lat-pulldown", "Lat Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["chest-row", "Chest-Supported Row", "upper_back", "compound", ["machinerow"], 8, 12],
    ["bb-row", "Barbell Row", "upper_back", "compound", ["barbell"], 6, 10],
    ["seated-row", "Seated Cable Row", "upper_back", "compound", ["cable"], 8, 12],
    ["db-row", "One-Arm Dumbbell Row", "upper_back", "compound", ["dumbbell", "bench"], 8, 12],
    ["tbar-row", "T-Bar Row", "upper_back", "compound", ["barbell"], 8, 12],
    ["machine-row", "Machine Row", "upper_back", "compound", ["machinerow"], 8, 12],
    ["deadlift", "Deadlift", "lower_back", "compound", ["barbell"], 4, 6],
    ["straight-pulldown", "Straight-Arm Pulldown", "lats", "isolation", ["cable"], 12, 20],
    ["inv-row", "Inverted Row", "upper_back", "compound", [], 8, 15],
    ["superman", "Superman Hold", "lower_back", "isolation", [], 12, 20],
    ["ohp", "Overhead Press", "shoulders", "compound", ["barbell", "rack"], 5, 10],
    ["db-shoulder", "Dumbbell Shoulder Press", "shoulders", "compound", ["dumbbell"], 8, 12],
    ["lat-raise", "Dumbbell Lateral Raise", "shoulders", "isolation", ["dumbbell"], 12, 20],
    ["cable-lat-raise", "Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 20],
    ["band-lateral-raise", "Band Lateral Raise", "shoulders", "isolation", ["bands"], 15, 30, null, "shoulder-abduction"],
    ["machine-shoulder", "Machine Shoulder Press", "shoulders", "compound", ["machineshoulder"], 8, 12],
    ["rear-fly", "Rear Delt Fly", "shoulders", "isolation", ["dumbbell"], 12, 20],
    ["reverse-pec", "Reverse Pec Deck", "shoulders", "isolation", ["pecdeck"], 12, 20],
    ["face-pull", "Face Pull", "shoulders", "isolation", ["cable"], 12, 20],
    ["arnold", "Arnold Press", "shoulders", "compound", ["dumbbell"], 8, 12],
    ["front-raise", "Front Raise", "shoulders", "isolation", ["dumbbell"], 10, 15],
    ["pike-pushup", "Pike Push-Up", "shoulders", "compound", [], 8, 15],
    ["inc-curl", "Incline Dumbbell Curl", "biceps", "isolation", ["dumbbell", "inclinebench"], 8, 12],
    ["db-curl", "Dumbbell Curl", "biceps", "isolation", ["dumbbell"], 8, 12],
    ["bayesian-curl", "Bayesian Cable Curl", "biceps", "isolation", ["cable"], 10, 15],
    ["ez-curl", "EZ-Bar Curl", "biceps", "isolation", ["ezbar"], 8, 12],
    ["preacher", "Preacher Curl", "biceps", "isolation", ["ezbar", "preacher"], 10, 15],
    ["cable-curl", "Cable Curl", "biceps", "isolation", ["cable"], 10, 15],
    ["hammer", "Hammer Curl", "biceps", "isolation", ["dumbbell"], 10, 15, "brachialis"],
    ["bb-curl", "Barbell Curl", "biceps", "isolation", ["barbell"], 8, 12],
    ["chinup", "Chin-Up", "lats", "compound", ["pullup"], 6, 12],
    ["assisted-chinup", "Assisted Chin-Up", "lats", "compound", ["assisted"], 8, 12],
    ["band-curl", "Band Curl", "biceps", "isolation", ["bands"], 12, 20],
    ["oh-cable-ext", "Overhead Cable Extension", "triceps", "isolation", ["cable"], 10, 15],
    ["pushdown", "Tricep Pushdown (Bar)", "triceps", "isolation", ["cable"], 10, 15],
    ["rope-pushdown", "Rope Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["ez-pushdown", "EZ-Bar Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["cgbp", "Close-Grip Bench Press", "triceps", "compound", ["barbell", "bench", "rack"], 6, 10],
    ["skullcrusher", "Skull Crusher", "triceps", "isolation", ["ezbar", "bench"], 8, 12],
    ["db-oh-ext", "DB Overhead Extension", "triceps", "isolation", ["dumbbell"], 10, 15],
    ["dips-tri", "Triceps Dip", "triceps", "compound", ["dip"], 8, 12],
    ["diamond-pushup", "Diamond Push-Up", "triceps", "compound", [], 10, 20],
    ["bench-dip", "Bench Dip", "triceps", "compound", [], 10, 20],
    ["back-squat", "Back Squat", "quads", "compound", ["barbell", "rack"], 5, 8],
    ["hack-squat", "Hack Squat", "quads", "compound", ["hacksquat"], 8, 12],
    ["leg-press", "Leg Press", "quads", "compound", ["legpress"], 8, 15],
    ["front-squat", "Front Squat", "quads", "compound", ["barbell", "rack"], 5, 8],
    ["bulgarian", "Bulgarian Split Squat", "quads", "compound", ["dumbbell"], 8, 12],
    ["leg-ext", "Leg Extension", "quads", "isolation", ["legext"], 12, 20],
    ["smith-squat", "Smith Machine Squat", "quads", "compound", ["smith"], 8, 12],
    ["goblet", "Goblet Squat", "quads", "compound", ["dumbbell"], 8, 15],
    ["walking-lunge", "Walking Lunge", "quads", "compound", ["dumbbell"], 10, 15],
    ["bw-squat", "Bodyweight Squat", "quads", "compound", [], 15, 25],
    ["bw-lunge", "Bodyweight Lunge", "quads", "compound", [], 12, 20],
    ["bw-bulgarian", "BW Bulgarian Split Squat", "quads", "compound", [], 10, 20],
    ["rdl", "Romanian Deadlift", "hamstrings", "compound", ["barbell"], 6, 10],
    ["lying-curl", "Lying Leg Curl", "hamstrings", "isolation", ["legcurl"], 10, 15],
    ["seated-curl", "Seated Leg Curl", "hamstrings", "isolation", ["legcurl"], 10, 15],
    ["db-rdl", "Dumbbell RDL", "hamstrings", "compound", ["dumbbell"], 8, 12],
    ["good-morning", "Good Morning", "hamstrings", "compound", ["barbell", "rack"], 8, 12],
    ["slrdl", "Single-Leg RDL", "hamstrings", "compound", [], 10, 15],
    ["nordic", "Nordic Curl", "hamstrings", "isolation", [], 5, 10],
    ["hip-thrust", "Barbell Hip Thrust", "glutes", "compound", ["barbell", "bench"], 8, 12],
    ["db-hip-thrust", "Dumbbell Hip Thrust", "glutes", "compound", ["dumbbell", "bench"], 10, 15],
    ["cable-kickback", "Cable Kickback", "glutes", "isolation", ["cable"], 12, 20],
    ["sl-hip-thrust", "Single-Leg Hip Thrust", "glutes", "compound", [], 10, 15],
    ["glute-bridge", "Glute Bridge", "glutes", "compound", [], 12, 20],
    ["sumo-dl", "Sumo Deadlift", "glutes", "compound", ["barbell"], 4, 8],
    ["standing-calf", "Standing Calf Raise", "calves", "isolation", ["calfmachine"], 8, 15],
    ["seated-calf", "Seated Calf Raise", "calves", "isolation", ["calfmachine"], 12, 20],
    ["smith-calf", "Smith Calf Raise", "calves", "isolation", ["smith"], 10, 15],
    ["db-calf", "Dumbbell Calf Raise", "calves", "isolation", ["dumbbell"], 12, 20],
    ["bw-calf", "Bodyweight Calf Raise", "calves", "isolation", [], 15, 25],
    ["cable-crunch", "Cable Crunch", "abs", "isolation", ["cable"], 10, 20],
    ["hanging-raise", "Hanging Leg Raise", "abs", "isolation", ["pullup"], 8, 15],
    ["leg-raise", "Lying Leg Raise", "abs", "isolation", [], 10, 20],
    ["crunch", "Crunch", "abs", "isolation", [], 12, 25],
    ["dumbbell-crunch", "Dumbbell Crunch", "abs", "isolation", ["dumbbell"], 10, 20],
    ["bicycle", "Bicycle Crunch", "abs", "isolation", [], 15, 25],
    ["russian-twist", "Russian Twist", "abs", "isolation", [], 15, 25, "obliques"],
    ["plank", "Plank", "abs", "isolation", [], 12, 20],
    ["cable-woodchop", "Cable Woodchopper", "abs", "isolation", ["cable"], 12, 20, "obliques"],
    ["oblique-crunch", "Oblique Crunch", "abs", "isolation", [], 15, 25, "obliques"],
    ["hanging-oblique", "Hanging Oblique Raise", "abs", "isolation", ["pullup"], 10, 15, "obliques"],
    ["serratus-pushup", "Serratus Push-Up", "abs", "isolation", [], 12, 20, "serratus"],
    ["cable-serratus", "Cable Serratus Pull", "abs", "isolation", ["cable"], 12, 20, "serratus"],
    ["banded-tib-raise", "Banded Tibialis Raise", "calves", "isolation", ["bands"], 15, 25, "tibialis"],
    ["bb-shrug", "Barbell Shrug", "traps", "isolation", ["barbell"], 10, 15],
    ["db-shrug", "Dumbbell Shrug", "traps", "isolation", ["dumbbell"], 12, 20],
    ["cable-shrug", "Cable Shrug", "traps", "isolation", ["cable"], 12, 20],
    ["wrist-curl", "Wrist Curl", "forearms", "isolation", ["dumbbell"], 12, 20, "wrist_flexors"],
    ["reverse-curl", "Reverse Curl", "biceps", "isolation", ["ezbar"], 10, 15, "brachialis"],
    ["farmers", "Farmer's Carry", "forearms", "compound", ["dumbbell"], 10, 15, "grip"],
    // ---- extended library (more variety for generation, swaps & manual add) ----
    // chest
    ["decline-bench", "Decline Barbell Press", "chest", "compound", ["barbell", "declinebench", "rack"], 6, 10],
    ["decline-db-press", "Decline Dumbbell Press", "chest", "compound", ["dumbbell", "declinebench"], 8, 12],
    ["incline-machine-press", "Incline Machine Press", "chest", "compound", ["machinepress"], 8, 12],
    ["floor-press", "Floor Press", "chest", "compound", ["barbell"], 6, 10],
    ["machine-dip", "Machine Chest Dip", "chest", "compound", ["machinepress"], 8, 12],
    ["high-cable-fly", "High-to-Low Cable Fly", "chest", "isolation", ["cable"], 12, 20],
    ["incline-pushup", "Incline Push-Up", "chest", "compound", [], 12, 20],
    ["decline-pushup", "Decline Push-Up", "chest", "compound", [], 10, 20],
    ["cable-press", "Standing Cable Press", "chest", "compound", ["cable"], 10, 15],
    // back
    ["pendlay-row", "Pendlay Row", "upper_back", "compound", ["barbell"], 5, 8],
    ["meadows-row", "Meadows Row", "upper_back", "compound", ["landmine"], 8, 12],
    ["seal-row", "Seal Row", "upper_back", "compound", ["barbell", "bench"], 8, 12],
    ["inc-db-row", "Incline Dumbbell Row", "upper_back", "compound", ["dumbbell", "inclinebench"], 8, 12],
    ["wide-pulldown", "Wide-Grip Lat Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["close-pulldown", "Close-Grip Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["neutral-pulldown", "Neutral-Grip Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["one-arm-pulldown", "Single-Arm Lat Pulldown", "lats", "compound", ["cable"], 10, 15],
    ["assisted-pullup", "Assisted Pull-Up", "lats", "compound", ["assisted"], 8, 12],
    ["rack-pull", "Rack Pull", "lower_back", "compound", ["barbell", "rack"], 4, 8],
    ["kroc-row", "Kroc Row", "upper_back", "compound", ["dumbbell"], 10, 20],
    ["db-pullover", "Dumbbell Pullover", "lats", "isolation", ["dumbbell", "bench"], 10, 15],
    ["floor-db-pullover", "Floor Dumbbell Pullover", "lats", "isolation", ["dumbbell"], 10, 15],
    ["bent-db-pullover", "Bent-Over Dumbbell Pullover", "lats", "isolation", ["dumbbell"], 12, 15],
    // shoulders
    ["seated-ohp", "Seated Barbell Press", "shoulders", "compound", ["barbell", "bench", "rack"], 6, 10],
    ["seated-db-press", "Seated Dumbbell Press", "shoulders", "compound", ["dumbbell", "bench"], 8, 12],
    ["smith-ohp", "Smith Machine Shoulder Press", "shoulders", "compound", ["smith"], 8, 12],
    ["landmine-press", "Landmine Press", "shoulders", "compound", ["landmine"], 8, 12],
    ["machine-lat-raise", "Machine Lateral Raise", "shoulders", "isolation", ["machinelatraise"], 12, 20],
    ["leaning-lat-raise", "Leaning Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 20],
    ["cable-rear-fly", "Cable Rear Delt Fly", "shoulders", "isolation", ["cable"], 12, 20],
    ["cable-front-raise", "Cable Front Raise", "shoulders", "isolation", ["cable"], 10, 15],
    ["upright-row", "Upright Row", "shoulders", "compound", ["barbell"], 8, 12],
    ["db-upright-row", "Dumbbell Upright Row", "shoulders", "compound", ["dumbbell"], 10, 15],
    // biceps
    ["concentration-curl", "Concentration Curl", "biceps", "isolation", ["dumbbell"], 10, 15],
    ["spider-curl", "Spider Curl", "biceps", "isolation", ["dumbbell", "inclinebench"], 10, 15],
    ["machine-curl", "Machine Curl", "biceps", "isolation", ["machinecurl"], 10, 15],
    ["rope-hammer-curl", "Cable Rope Hammer Curl", "biceps", "isolation", ["cable"], 10, 15, "brachialis"],
    ["cross-hammer", "Cross-Body Hammer Curl", "biceps", "isolation", ["dumbbell"], 10, 15, "brachialis"],
    ["zottman-curl", "Zottman Curl", "biceps", "isolation", ["dumbbell"], 10, 15, "brachialis"],
    ["drag-curl", "Drag Curl", "biceps", "isolation", ["barbell"], 10, 15],
    // triceps
    ["v-bar-pushdown", "V-Bar Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["straight-bar-pushdown", "Straight-Bar Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["dual-rope-pushdown", "Dual-Rope Pushdown", "triceps", "isolation", ["cable"], 10, 15],
    ["single-pushdown", "Single-Arm Pushdown", "triceps", "isolation", ["cable"], 12, 20],
    ["jm-press", "JM Press", "triceps", "compound", ["barbell", "bench", "rack"], 6, 10],
    ["tricep-kickback", "Tricep Kickback", "triceps", "isolation", ["dumbbell"], 12, 20],
    ["machine-ext", "Machine Triceps Extension", "triceps", "isolation", ["machineext"], 10, 15],
    ["ez-oh-ext", "EZ-Bar Overhead Extension", "triceps", "isolation", ["ezbar"], 10, 15],
    // quads
    ["pendulum-squat", "Pendulum Squat", "quads", "compound", ["hacksquat"], 8, 12],
    ["belt-squat", "Belt Squat", "quads", "compound", ["beltsquat"], 10, 15],
    ["box-squat", "Box Squat", "quads", "compound", ["barbell", "rack"], 5, 8],
    ["split-squat", "Split Squat", "quads", "compound", ["dumbbell"], 8, 12],
    ["step-up", "Dumbbell Step-Up", "quads", "compound", ["dumbbell"], 10, 15],
    ["reverse-lunge", "Reverse Lunge", "quads", "compound", ["dumbbell"], 10, 15],
    ["single-leg-press", "Single-Leg Press", "quads", "compound", ["legpress"], 10, 15],
    ["sissy-squat", "Sissy Squat", "quads", "isolation", [], 10, 20],
    // hamstrings
    ["stiff-deadlift", "Stiff-Leg Deadlift", "hamstrings", "compound", ["barbell"], 6, 10],
    ["standing-curl", "Standing Leg Curl", "hamstrings", "isolation", ["legcurl"], 10, 15],
    ["ghr", "Glute-Ham Raise", "hamstrings", "compound", [], 6, 12],
    ["pull-through", "Cable Pull-Through", "hamstrings", "compound", ["cable"], 12, 20],
    ["kb-swing", "Kettlebell Swing", "hamstrings", "compound", ["kettlebell"], 12, 20],
    // glutes
    ["machine-hip-thrust", "Machine Hip Thrust", "glutes", "compound", ["hipthrustmachine"], 8, 12],
    ["smith-hip-thrust", "Smith Machine Hip Thrust", "glutes", "compound", ["smith", "bench"], 8, 12],
    ["hip-abduction", "Hip Abduction Machine", "abductors", "isolation", ["abduction"], 12, 20],
    ["cable-abduction", "Cable Hip Abduction", "abductors", "isolation", ["cable"], 12, 20],
    ["reverse-hyper", "Reverse Hyperextension", "glutes", "isolation", ["reversehyper"], 10, 15],
    ["frog-pump", "Frog Pump", "glutes", "isolation", [], 15, 25],
    // calves
    ["leg-press-calf", "Leg Press Calf Raise", "calves", "isolation", ["legpress"], 10, 15],
    ["donkey-calf", "Donkey Calf Raise", "calves", "isolation", ["calfmachine"], 12, 20],
    ["single-calf", "Single-Leg Calf Raise", "calves", "isolation", ["dumbbell"], 12, 20],
    // abs
    ["ab-wheel", "Ab Wheel Rollout", "abs", "isolation", [], 8, 15],
    ["hanging-knee", "Hanging Knee Raise", "abs", "isolation", ["pullup"], 10, 20],
    ["machine-crunch", "Machine Crunch", "abs", "isolation", ["machinecrunch"], 12, 20],
    ["decline-situp", "Decline Sit-Up", "abs", "isolation", ["declinebench"], 12, 20],
    ["toes-to-bar", "Toes to Bar", "abs", "isolation", ["pullup"], 8, 15],
    ["side-plank", "Side Plank", "abs", "isolation", [], 12, 20, "obliques"],
    ["pallof", "Pallof Press", "abs", "isolation", ["cable"], 12, 20],
    ["woodchopper", "Low-to-High Cable Chop", "abs", "isolation", ["cable"], 12, 20, "obliques"],
    ["v-up", "V-Up", "abs", "isolation", [], 12, 20],
    ["mountain-climber", "Mountain Climber", "abs", "isolation", [], 20, 40],
    // traps
    ["smith-shrug", "Smith Machine Shrug", "traps", "isolation", ["smith"], 12, 20],
    ["machine-shrug", "Machine Shrug", "traps", "isolation", ["machineshrug"], 12, 20],
    ["behind-shrug", "Behind-the-Back Shrug", "traps", "isolation", ["barbell", "rack"], 12, 20],
    // forearms
    ["reverse-wrist-curl", "Reverse Wrist Curl", "forearms", "isolation", ["dumbbell"], 12, 20, "wrist_extensors"],
    ["behind-wrist-curl", "Behind-Back Wrist Curl", "forearms", "isolation", ["barbell"], 15, 20, "wrist_flexors"],
    ["wrist-roller", "Wrist Roller", "forearms", "isolation", [], 10, 15, "wrist_extensors"],
    /* RADIAL / ULNAR DEVIATION — the third wrist direction, and until now the library had none of it.
       v574 measured the coverage problem and could only fix two thirds of it: flexion and extension had
       movements to select, "side" had nothing, so no generation rule could reach it and check 15 of
       gates/extensions.mjs pinned the gap rather than leaving it to be rediscovered.
       Deviation needs a torque ACROSS the hand, which takes either an offset lever (a hammer, a sledge,
       a bar held at one end) or a cable/band pulling perpendicular to the arc. A symmetric dumbbell held
       in the middle produces no deviation moment at all, which is why there is no dumbbell entry here.
       The hammer variants carry NO equipment id for the same reason the wrist roller and the hand
       gripper do not: a hammer is a dedicated implement the gym editor does not model, and minting an id
       for it would append to CODE_EQUIP, which is index-based and shared by every program code ever
       issued. Rep ranges sit with the other wrist work — small, endurance-biased musculature moving
       through roughly 50 degrees, not a loadable prime mover. */
    ["hammer-radial-dev", "Hammer Radial Wrist Deviation", "forearms", "isolation", [], 12, 20, "wrist_deviators"],
    ["hammer-ulnar-dev", "Hammer Ulnar Wrist Deviation", "forearms", "isolation", [], 12, 20, "wrist_deviators"],
    ["cable-radial-dev", "Cable Radial Wrist Deviation", "forearms", "isolation", ["cable"], 12, 20, "wrist_deviators"],
    ["band-ulnar-dev", "Band Ulnar Wrist Deviation", "forearms", "isolation", ["bands"], 15, 25, "wrist_deviators"],
    ["plate-pinch", "Plate Pinch Hold", "forearms", "isolation", ["dumbbell"], 10, 15, "grip"],
    // ---- granular variations (grip / angle / unilateral) ----
    ["push-press", "Barbell Push Press", "shoulders", "compound", ["barbell", "rack"], 4, 8],
    ["side-lying-raise", "Side-Lying Lateral Raise", "shoulders", "isolation", ["dumbbell", "bench"], 12, 20],
    ["single-cable-raise", "Single-Arm Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 20],
    ["underhand-row", "Underhand-Grip Barbell Row", "upper_back", "compound", ["barbell"], 8, 12],
    ["underhand-pulldown", "Underhand-Grip Lat Pulldown", "lats", "compound", ["cable"], 8, 12],
    ["single-cable-row", "Single-Arm Cable Row", "upper_back", "compound", ["cable"], 10, 15],
    ["close-cable-row", "Close-Grip Seated Cable Row", "lats", "compound", ["cable"], 8, 12],
    ["wide-cable-row", "Wide-Grip Seated Cable Row", "upper_back", "compound", ["cable"], 10, 15],
    ["underhand-cable-row", "Underhand Seated Cable Row", "lats", "compound", ["cable"], 8, 12],
    ["rope-cable-row", "Rope Seated Cable Row", "upper_back", "compound", ["cable"], 10, 15],
    ["dual-cable-row", "Dual-Handle Seated Cable Row", "upper_back", "compound", ["cable"], 8, 12],
    ["wide-machine-row", "Wide-Grip Machine Row", "upper_back", "compound", ["machinerow"], 10, 15],
    ["neutral-machine-row", "Neutral-Grip Machine Row", "upper_back", "compound", ["machinerow"], 8, 12],
    ["wide-tbar-row", "Wide-Grip T-Bar Row", "upper_back", "compound", ["barbell"], 8, 12],
    ["kelso-shrug", "Kelso Shrug", "traps", "isolation", ["machineshrug"], 12, 20],
    ["single-cable-curl", "Single-Arm Cable Curl", "biceps", "isolation", ["cable"], 10, 15],
    ["incline-hammer", "Incline Hammer Curl", "biceps", "isolation", ["dumbbell", "inclinebench"], 10, 15, "brachialis"],
    // neck
    ["neck-extension", "Weighted Neck Extension", "neck", "isolation", [], 12, 20],
    ["neck-curl", "Weighted Neck Curl", "neck", "isolation", [], 12, 20],
    ["neck-harness", "Neck Harness Raise", "neck", "isolation", [], 12, 20],
    ["neck-lateral", "Lateral Neck Raise", "neck", "isolation", [], 12, 20],
    // --- expansion: additional staples across thinner categories ---
    ["cable-crossover", "Cable Crossover", "chest", "isolation", ["cable"], 12, 20],
    ["squeeze-press", "Dumbbell Squeeze Press", "chest", "compound", ["dumbbell", "bench"], 10, 15],
    ["tate-press", "Tate Press", "triceps", "isolation", ["dumbbell"], 10, 15],
    ["z-press", "Z Press", "shoulders", "compound", ["barbell", "rack"], 5, 10],
    ["cable-y-raise", "Cable Y-Raise", "shoulders", "isolation", ["cable"], 12, 20],
    ["bstance-hip-thrust", "B-Stance Hip Thrust", "glutes", "compound", ["dumbbell", "bench"], 10, 15],
    ["curtsy-lunge", "Curtsy Lunge", "glutes", "compound", ["dumbbell"], 10, 15],
    ["lateral-walk", "Banded Lateral Walk", "abductors", "isolation", ["bands"], 15, 25],
    ["band-leg-curl", "Banded Leg Curl", "hamstrings", "isolation", ["bands"], 12, 20],
    ["jefferson-curl", "Jefferson Curl", "hamstrings", "isolation", ["dumbbell"], 8, 12],
    ["bw-single-calf", "Single-Leg BW Calf Raise", "calves", "isolation", [], 15, 25],
    ["trap-bar-shrug", "Trap Bar Shrug", "traps", "isolation", ["trapbar"], 10, 15],
    ["power-shrug", "Power Shrug", "traps", "isolation", ["barbell"], 6, 10],
    ["dead-hang", "Dead Hang", "forearms", "isolation", ["pullup"], 10, 15, "grip"],
    ["suitcase-carry", "Suitcase Carry", "forearms", "compound", ["dumbbell"], 10, 15, "grip"],
    ["dead-bug", "Dead Bug", "abs", "isolation", [], 10, 20],
    ["hollow-hold", "Hollow Body Hold", "abs", "isolation", [], 15, 30],
    ["dragon-flag", "Dragon Flag", "abs", "isolation", [], 5, 12],
    ["cable-side-bend", "Cable Side Bend", "abs", "isolation", ["cable"], 12, 20],
    ["copenhagen", "Copenhagen Plank", "adductors", "isolation", [], 10, 20],
    ["cyclist-squat", "Cyclist Squat", "quads", "compound", ["barbell", "rack"], 8, 12],
    ["spanish-squat", "Spanish Squat", "quads", "isolation", ["bands"], 12, 20],
    // ---- expansion 2: deeper variety per muscle (angles, implements, unilateral) ----
    // chest
    ["incline-cable-press", "Incline Cable Press", "chest", "compound", ["cable"], 10, 15],
    ["smith-incline", "Smith Machine Incline Press", "chest", "compound", ["smith", "inclinebench"], 6, 10],
    ["low-incline-db", "Low-Incline Dumbbell Press", "chest", "compound", ["dumbbell", "inclinebench"], 8, 12],
    ["band-pushup", "Banded Push-Up", "chest", "compound", ["bands"], 10, 20],
    ["svend-press", "Svend Press", "chest", "isolation", [], 12, 20],
    // back
    ["yates-row", "Yates Row", "upper_back", "compound", ["barbell"], 8, 12],
    ["cs-db-row", "Chest-Supported Dumbbell Row", "upper_back", "compound", ["dumbbell", "bench"], 8, 12],
    ["cable-pullover", "Cable Lat Pullover", "lats", "isolation", ["cable"], 12, 20],
    ["machine-pullover", "Machine Pullover", "lats", "isolation", ["machinepullover"], 10, 15],
    ["trap-bar-deadlift", "Trap Bar Deadlift", "lower_back", "compound", ["trapbar"], 4, 8],
    ["snatch-deadlift", "Snatch-Grip Deadlift", "lower_back", "compound", ["barbell"], 4, 6],
    ["renegade-row", "Renegade Row", "upper_back", "compound", ["dumbbell"], 8, 12],
    ["band-pulldown", "Banded Lat Pulldown", "lats", "compound", ["bands"], 12, 20],
    // shoulders
    ["cable-upright-row", "Cable Upright Row", "shoulders", "compound", ["cable"], 10, 15],
    ["bradford-press", "Bradford Press", "shoulders", "compound", ["barbell", "rack"], 8, 12],
    ["viking-press", "Viking Press", "shoulders", "compound", ["machineshoulder"], 8, 12],
    ["rear-band-pull-apart", "Band Pull-Apart", "shoulders", "isolation", ["bands"], 15, 25],
    ["plate-front-raise", "Plate Front Raise", "shoulders", "isolation", ["dumbbell"], 12, 20],
    ["prone-y-raise", "Prone Y-Raise", "shoulders", "isolation", ["inclinebench"], 12, 20],
    // biceps
    ["ez-cable-curl", "EZ-Bar Cable Curl", "biceps", "isolation", ["cable"], 10, 15],
    ["waiter-curl", "Waiter Curl", "biceps", "isolation", ["dumbbell"], 10, 15],
    ["seated-incline-curl", "Seated Incline Cable Curl", "biceps", "isolation", ["cable", "inclinebench"], 10, 15],
    ["rev-grip-curl", "Reverse-Grip Barbell Curl", "biceps", "isolation", ["barbell"], 10, 15, "brachialis"],
    ["pinwheel-curl", "Pinwheel Curl", "biceps", "isolation", ["dumbbell"], 10, 15, "brachialis"],
    // triceps
    ["underhand-pushdown", "Reverse-Grip Pushdown", "triceps", "isolation", ["cable"], 12, 20],
    ["pjr-pullover", "PJR Pullover", "triceps", "isolation", ["ezbar", "bench"], 8, 12],
    ["lying-db-ext", "Lying Dumbbell Extension", "triceps", "isolation", ["dumbbell", "bench"], 10, 15],
    ["cable-tri-kickback", "Cable Triceps Kickback", "triceps", "isolation", ["cable"], 12, 20],
    ["california-press", "California Press", "triceps", "compound", ["ezbar", "bench"], 8, 12],
    // quads
    ["zercher-squat", "Zercher Squat", "quads", "compound", ["barbell", "rack"], 6, 10],
    ["lm-squat", "Landmine Squat", "quads", "compound", ["landmine"], 8, 12],
    ["heels-up-goblet", "Heels-Elevated Goblet Squat", "quads", "compound", ["dumbbell"], 10, 15],
    ["wall-sit", "Wall Sit", "quads", "isolation", [], 20, 40],
    ["pistol-squat", "Pistol Squat", "quads", "compound", [], 5, 12],
    ["v-squat", "V-Squat Machine", "quads", "compound", ["hacksquat"], 8, 12],
    // hamstrings
    ["cable-rdl", "Cable Romanian Deadlift", "hamstrings", "compound", ["cable"], 10, 15],
    ["slider-curl", "Slider Leg Curl", "hamstrings", "isolation", [], 8, 15],
    ["band-good-morning", "Banded Good Morning", "hamstrings", "compound", ["bands"], 12, 20],
    ["single-lying-curl", "Single-Leg Lying Curl", "hamstrings", "isolation", ["legcurl"], 10, 15],
    // glutes
    ["kas-glute-bridge", "Kas Glute Bridge", "glutes", "compound", ["barbell", "bench"], 10, 15],
    ["band-hip-thrust", "Banded Hip Thrust", "glutes", "compound", ["bands"], 15, 25],
    ["glute-kickback-machine", "Glute Kickback Machine", "glutes", "isolation", ["kickback"], 12, 20],
    ["sumo-squat", "Sumo Squat", "glutes", "compound", ["dumbbell"], 10, 15],
    ["step-through-lunge", "Step-Through Lunge", "glutes", "compound", ["dumbbell"], 10, 15],
    // calves
    ["hack-calf", "Hack Squat Calf Raise", "calves", "isolation", ["hacksquat"], 10, 15],
    ["tibialis-raise", "Tibialis Raise", "calves", "isolation", [], 15, 25, "tibialis"],
    ["single-leg-press-calf", "Single-Leg Press Calf Raise", "calves", "isolation", ["legpress"], 12, 20],
    // abs
    ["reverse-crunch", "Reverse Crunch", "abs", "isolation", [], 12, 20],
    ["cable-reverse-crunch", "Cable Reverse Crunch", "abs", "isolation", ["cable"], 12, 20],
    ["l-sit", "L-Sit Hold", "abs", "isolation", [], 10, 20],
    ["weighted-plank", "Weighted Plank", "abs", "isolation", ["dumbbell"], 20, 40],
    ["windshield-wiper", "Hanging Windshield Wiper", "abs", "isolation", ["pullup"], 8, 15],
    ["stir-pot", "Stir the Pot", "abs", "isolation", [], 10, 20],
    // traps
    ["incline-shrug", "Incline Dumbbell Shrug", "traps", "isolation", ["dumbbell", "inclinebench"], 12, 20],
    ["cable-face-shrug", "Cable Face Shrug", "traps", "isolation", ["cable"], 12, 20],
    // forearms
    ["cable-wrist-curl", "Cable Wrist Curl", "forearms", "isolation", ["cable"], 12, 20, "wrist_flexors"],
    ["gripper", "Hand Gripper", "forearms", "isolation", [], 10, 20, "grip"],
    ["towel-hang", "Towel Dead Hang", "forearms", "isolation", ["pullup"], 10, 15, "grip"],
    // ---- expansion 3: commonly-expected staples ----
    ["bw-pullup", "Pull-Up", "lats", "compound", ["pullup"], 5, 12],
    ["back-ext-45", "45° Back Extension", "lower_back", "compound", ["bench"], 12, 20],
    ["situp", "Sit-Up", "abs", "isolation", [], 15, 25],
    // ---- adductors (inner thigh) ----
    ["adduction-machine", "Hip Adduction Machine", "adductors", "isolation", ["adduction"], 12, 20],
    ["cable-adduction", "Cable Hip Adduction", "adductors", "isolation", ["cable"], 12, 20],
    ["band-adduction", "Banded Hip Adduction", "adductors", "isolation", ["bands"], 15, 25],
    ["cossack-squat", "Cossack Squat", "adductors", "compound", ["dumbbell"], 10, 15],
    ["adductor-sumo", "Wide-Stance Sumo Squat", "adductors", "compound", ["dumbbell"], 10, 15],
    // ---- abductors (hip / glute medius) ----
    ["standing-cable-abduction", "Standing Cable Hip Abduction", "abductors", "isolation", ["cable"], 12, 20],
    ["side-lying-abduction", "Side-Lying Hip Abduction", "abductors", "isolation", [], 15, 25],
    ["band-abduction", "Seated Banded Abduction", "abductors", "isolation", ["bands"], 15, 25],
    // ---- expansion 4: additional movement variations (de-duplicated) ----
    ["db-floor-press", "Dumbbell Floor Press", "chest", "compound", ["dumbbell"], 8, 12],
    ["db-neutral-press", "Neutral-Grip Dumbbell Bench Press", "chest", "compound", ["dumbbell", "bench"], 8, 12],
    ["db-incline-fly", "Incline Dumbbell Fly", "chest", "isolation", ["dumbbell", "inclinebench"], 10, 15],
    ["bodyweight-deficit-pushup", "Deficit Push-Up", "chest", "compound", [], 10, 20],
    ["helms-row", "Helms Dumbbell Row", "lats", "compound", ["dumbbell", "bench"], 8, 12],
    ["kb-gorilla-row", "Kettlebell Gorilla Row", "upper_back", "compound", ["kettlebell"], 8, 12],
    ["t-bar-chest-supported", "Chest-Supported T-Bar Row", "upper_back", "compound", ["machinerow"], 8, 12],
    ["bodyweight-doorway-row", "Bodyweight Doorway Row", "upper_back", "compound", [], 12, 20],
    ["doorway-biceps-curl", "Doorway Biceps Curl", "biceps", "isolation", [], 10, 20],
    ["prone-rear-delt-raise", "Prone Rear Delt Raise", "shoulders", "isolation", [], 12, 25],
    ["db-lu-raise", "Lu Lateral Raise", "shoulders", "isolation", ["dumbbell"], 12, 15],
    ["prone-db-rear-fly", "Prone Incline Bench Rear Delt Fly", "shoulders", "isolation", ["dumbbell", "inclinebench"], 12, 20],
    ["cable-behind-back-lateral", "Behind-the-Back Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 15],
    ["db-6-way-raise", "Dumbbell 6-Way Lateral Raise", "shoulders", "isolation", ["dumbbell"], 10, 12],
    ["cable-rear-delt-row", "Standing Cable Rear Delt Row", "shoulders", "isolation", ["cable"], 12, 15],
    ["bands-face-pull", "Banded Face Pull", "shoulders", "isolation", ["bands"], 15, 20],
    ["db-deficit-lunge", "Deficit Dumbbell Reverse Lunge", "quads", "compound", ["dumbbell"], 8, 12],
    ["smith-reverse-lunge", "Smith Machine Reverse Lunge", "quads", "compound", ["smith"], 8, 12],
    ["db-deficit-step-up", "Deficit Dumbbell Step-Up", "quads", "compound", ["dumbbell", "bench"], 10, 12],
    ["jefferson-squat", "Barbell Jefferson Squat", "quads", "compound", ["barbell"], 6, 10],
    ["bodyweight-reverse-nordic", "Bodyweight Reverse Nordic Curl", "quads", "isolation", [], 8, 12],
    ["bands-squat", "Banded Resistance Squat", "quads", "compound", ["bands"], 15, 25],
    ["deficit-db-rdl", "Deficit Dumbbell Romanian Deadlift", "hamstrings", "compound", ["dumbbell"], 8, 12],
    ["db-good-morning", "Dumbbell Good Morning", "hamstrings", "compound", ["dumbbell"], 10, 15],
    ["deficit-barbell-deadlift", "Deficit Barbell Deadlift", "hamstrings", "compound", ["barbell"], 4, 6],
    ["cable-behind-back-curl", "Behind-the-Back Cable Bicep Curl", "biceps", "isolation", ["cable"], 12, 15],
    ["cable-cross-body-extension", "Cross-Body Cable Tricep Extension", "triceps", "isolation", ["cable"], 12, 15],
    ["bands-tricep-ext", "Banded Overhead Tricep Extension", "triceps", "isolation", ["bands"], 15, 20],
    ["db-weighted-crunch", "Incline Dumbbell Weighted Crunch", "abs", "isolation", ["dumbbell", "inclinebench"], 10, 15],
    ["bands-chest-press", "Banded Horizontal Chest Press", "chest", "compound", ["bands"], 12, 15],
    ["db-incline-shrug", "Incline Bench Dumbbell Shrug", "traps", "isolation", ["dumbbell", "inclinebench"], 12, 15],
    // --- Powerlifting competition-lift variations (specialized; appended last so they stay opt-in
    // choices in swap/library rather than crowding out staples in auto-generation) ---
    ["high-bar-squat", "High-Bar Back Squat", "quads", "compound", ["barbell", "rack"], 5, 8],
    ["low-bar-squat", "Low-Bar Back Squat", "quads", "compound", ["barbell", "rack"], 4, 8],
    ["paused-squat", "Paused Squat", "quads", "compound", ["barbell", "rack"], 3, 6],
    ["pin-squat", "Pin Squat", "quads", "compound", ["barbell", "rack"], 3, 6],
    ["tempo-squat", "Tempo Squat", "quads", "compound", ["barbell", "rack"], 4, 8],
    ["spoto-press", "Spoto Press", "chest", "compound", ["barbell", "rack"], 3, 6],
    ["larsen-press", "Larsen Press", "chest", "compound", ["barbell", "rack"], 4, 8],
    ["board-press", "Board Press", "chest", "compound", ["barbell", "rack"], 3, 6],
    ["pin-bench", "Pin Press (Bench)", "chest", "compound", ["barbell", "rack"], 3, 6],
    ["pause-bench", "3s Paused Bench Press", "chest", "compound", ["barbell", "rack"], 3, 6],
    ["paused-deadlift", "Paused Deadlift", "lower_back", "compound", ["barbell"], 3, 6],
    ["pin-press-ohp", "Pin Press (Overhead)", "shoulders", "compound", ["barbell", "rack"], 4, 8],
    ["barbell-skullover", "Barbell Skullover", "triceps", "isolation", ["barbell"], 8, 12],
    // Appended so saved exercise indices and existing generation priorities stay stable.
    ["seated-db-lat-raise", "Seated Dumbbell Lateral Raise", "shoulders", "isolation", ["dumbbell", "bench"], 12, 20, "side_delts", "shoulder-abduction"],
    ["single-db-lat-raise", "Single-Arm Dumbbell Lateral Raise", "shoulders", "isolation", ["dumbbell"], 12, 20, "side_delts", "shoulder-abduction"],
    ["chest-supported-lat-raise", "Chest-Supported Dumbbell Lateral Raise", "shoulders", "isolation", ["dumbbell", "inclinebench"], 12, 20, "side_delts", "shoulder-abduction"],
    ["seated-cable-lat-raise", "Seated Cable Lateral Raise", "shoulders", "isolation", ["cable", "bench"], 12, 20, "side_delts", "shoulder-abduction"],
    ["cuff-cable-lat-raise", "Cuff Cable Lateral Raise", "shoulders", "isolation", ["cable"], 12, 20, "side_delts", "shoulder-abduction"],
    ["seated-rear-fly", "Seated Dumbbell Rear Delt Fly", "shoulders", "isolation", ["dumbbell", "bench"], 12, 20, "rear_delts", "shoulder-horizontal-abduction"],
    ["single-cable-rear-fly", "Single-Arm Cable Rear Delt Fly", "shoulders", "isolation", ["cable"], 12, 20, "rear_delts", "shoulder-horizontal-abduction"],
    ["chest-supported-rear-fly", "Chest-Supported Dumbbell Rear Delt Fly", "shoulders", "isolation", ["dumbbell", "inclinebench"], 12, 20, "rear_delts", "shoulder-horizontal-abduction"],
];

const PATTERN_BY_ID = { "doorway-biceps-curl": "elbow-flexion", "prone-rear-delt-raise": "shoulder-horizontal-abduction", "ab-wheel": "anti-extension", "adduction-machine": "hip-adduction", "adductor-sumo": "hip-adduction", "arnold": "vertical-push", "assisted-chinup": "vertical-pull", "assisted-dip": "horizontal-push", "assisted-pullup": "vertical-pull", "back-ext-45": "hip-hinge", "back-squat": "squat", "band-abduction": "hip-abduction", "band-adduction": "hip-adduction", "band-curl": "elbow-flexion", "band-good-morning": "hip-hinge", "band-hip-thrust": "hip-extension", "band-leg-curl": "knee-flexion", "band-pulldown": "vertical-pull", "band-pushup": "horizontal-push", "band-ulnar-dev": "wrist-deviation", "banded-tib-raise": "dorsiflexion", "bands-chest-press": "horizontal-push", "bands-face-pull": "shoulder-horizontal-abduction", "bands-squat": "squat", "bands-tricep-ext": "elbow-extension-overhead", "barbell-skullover": "elbow-extension-overhead", "bayesian-curl": "elbow-flexion", "bb-bench": "horizontal-push", "bb-curl": "elbow-flexion", "bb-row": "horizontal-pull", "bb-shrug": "shrug", "behind-shrug": "shrug", "behind-wrist-curl": "wrist-flexion", "belt-squat": "squat", "bench-dip": "elbow-extension-neutral", "bent-db-pullover": "pullover", "bicycle": "spinal-flexion", "board-press": "horizontal-push", "bodyweight-deficit-pushup": "horizontal-push", "bodyweight-doorway-row": "horizontal-pull", "bodyweight-reverse-nordic": "knee-extension", "box-squat": "squat", "bradford-press": "vertical-push", "bstance-hip-thrust": "hip-extension", "bulgarian": "lunge", "bw-bulgarian": "lunge", "bw-calf": "plantarflexion-straight-knee", "bw-lunge": "lunge", "bw-pullup": "vertical-pull", "bw-single-calf": "plantarflexion-straight-knee", "bw-squat": "squat", "cable-abduction": "hip-abduction", "cable-adduction": "hip-adduction", "cable-behind-back-curl": "elbow-flexion", "cable-behind-back-lateral": "shoulder-abduction", "cable-cross-body-extension": "elbow-extension-neutral", "cable-crossover": "shoulder-horizontal-adduction", "cable-crunch": "spinal-flexion", "cable-curl": "elbow-flexion", "cable-face-shrug": "shrug", "cable-fly": "shoulder-horizontal-adduction", "cable-front-raise": "shoulder-flexion", "cable-kickback": "hip-abduction", "cable-lat-raise": "shoulder-abduction", "cable-press": "horizontal-push", "cable-pullover": "pullover", "cable-radial-dev": "wrist-deviation", "cable-rdl": "hip-hinge", "cable-rear-delt-row": "shoulder-horizontal-abduction", "cable-rear-fly": "shoulder-horizontal-abduction", "cable-reverse-crunch": "spinal-flexion", "cable-serratus": "anti-extension", "cable-shrug": "shrug", "cable-side-bend": "lateral-flexion", "cable-tri-kickback": "elbow-extension-neutral", "cable-upright-row": "shoulder-abduction", "cable-woodchop": "anti-rotation", "cable-wrist-curl": "wrist-flexion", "cable-y-raise": "scapular-upward-rotation", "california-press": "elbow-extension-neutral", "cgbp": "elbow-extension-neutral", "chest-row": "horizontal-pull", "chinup": "vertical-pull", "close-cable-row": "horizontal-pull", "close-pulldown": "vertical-pull", "concentration-curl": "elbow-flexion", "copenhagen": "hip-adduction", "cossack-squat": "hip-adduction", "cross-hammer": "elbow-flexion-neutral", "crunch": "spinal-flexion", "cs-db-row": "horizontal-pull", "curtsy-lunge": "hip-extension", "cyclist-squat": "knee-extension", "db-6-way-raise": "shoulder-abduction", "db-bench": "horizontal-push", "db-calf": "plantarflexion-straight-knee", "db-curl": "elbow-flexion", "db-deficit-lunge": "lunge", "db-deficit-step-up": "lunge", "db-floor-press": "horizontal-push", "db-fly": "shoulder-horizontal-adduction", "db-good-morning": "hip-hinge", "db-hip-thrust": "hip-extension", "db-incline-fly": "shoulder-horizontal-adduction", "db-incline-shrug": "shrug", "db-lu-raise": "shoulder-abduction", "db-neutral-press": "horizontal-push", "db-oh-ext": "elbow-extension-overhead", "db-pullover": "pullover", "db-rdl": "hip-hinge", "db-row": "horizontal-pull", "db-shoulder": "vertical-push", "db-shrug": "shrug", "db-upright-row": "shoulder-abduction", "db-weighted-crunch": "spinal-flexion", "dead-bug": "anti-extension", "dead-hang": "grip-static", "deadlift": "hip-hinge", "decline-bench": "horizontal-push", "decline-db-press": "horizontal-push", "decline-pushup": "horizontal-push", "decline-situp": "spinal-flexion", "deficit-barbell-deadlift": "hip-hinge", "deficit-db-rdl": "hip-hinge", "diamond-pushup": "elbow-extension-neutral", "dips-chest": "horizontal-push", "dips-tri": "elbow-extension-neutral", "donkey-calf": "plantarflexion-straight-knee", "drag-curl": "elbow-flexion", "dragon-flag": "anti-extension", "dual-cable-row": "horizontal-pull", "dual-rope-pushdown": "elbow-extension-neutral", "ez-cable-curl": "elbow-flexion", "ez-curl": "elbow-flexion", "ez-oh-ext": "elbow-extension-overhead", "ez-pushdown": "elbow-extension-neutral", "face-pull": "shoulder-horizontal-abduction", "farmers": "grip-static", "floor-db-pullover": "pullover", "floor-press": "horizontal-push", "frog-pump": "hip-extension", "front-raise": "shoulder-flexion", "front-squat": "squat", "ghr": "hip-hinge", "glute-bridge": "hip-extension", "glute-kickback-machine": "hip-abduction", "goblet": "squat", "good-morning": "hip-hinge", "gripper": "grip-static", "hack-calf": "plantarflexion-bent-knee", "hack-squat": "squat", "hammer": "elbow-flexion-neutral", "hammer-radial-dev": "wrist-deviation", "hammer-ulnar-dev": "wrist-deviation", "hanging-knee": "spinal-flexion", "hanging-oblique": "anti-rotation", "hanging-raise": "spinal-flexion", "heels-up-goblet": "squat", "helms-row": "horizontal-pull", "high-bar-squat": "squat", "high-cable-fly": "shoulder-horizontal-adduction", "high-inc-bb-bench": "incline-push", "hip-abduction": "hip-abduction", "hip-thrust": "hip-extension", "hollow-hold": "anti-extension", "inc-bb-bench": "incline-push", "inc-cable-fly": "shoulder-horizontal-adduction", "inc-curl": "elbow-flexion", "inc-db-press": "incline-push", "inc-db-row": "horizontal-pull", "incline-cable-press": "incline-push", "incline-hammer": "elbow-flexion-neutral", "incline-machine-press": "incline-push", "incline-pushup": "incline-push", "incline-shrug": "shrug", "inv-row": "horizontal-pull", "jefferson-curl": "knee-flexion", "jefferson-squat": "squat", "jm-press": "elbow-extension-neutral", "kas-glute-bridge": "hip-extension", "kb-gorilla-row": "horizontal-pull", "kb-swing": "hip-hinge", "kelso-shrug": "shrug", "kroc-row": "horizontal-pull", "l-sit": "anti-extension", "landmine-press": "vertical-push", "larsen-press": "horizontal-push", "lat-pulldown": "vertical-pull", "lat-raise": "shoulder-abduction", "lateral-walk": "hip-abduction", "leaning-lat-raise": "shoulder-abduction", "leg-ext": "knee-extension", "leg-press": "squat", "leg-press-calf": "plantarflexion-bent-knee", "leg-raise": "spinal-flexion", "lm-squat": "squat", "low-bar-squat": "squat", "low-inc-bb-bench": "incline-push", "low-incline-db": "incline-push", "lying-curl": "knee-flexion", "lying-db-ext": "elbow-extension-neutral", "machine-crunch": "spinal-flexion", "machine-curl": "elbow-flexion", "machine-dip": "horizontal-push", "machine-ext": "elbow-extension-neutral", "machine-hip-thrust": "hip-extension", "machine-lat-raise": "shoulder-abduction", "machine-press": "horizontal-push", "machine-pullover": "pullover", "machine-row": "horizontal-pull", "machine-shoulder": "vertical-push", "machine-shrug": "shrug", "meadows-row": "horizontal-pull", "mountain-climber": "anti-extension", "neck-curl": "neck", "neck-extension": "neck", "neck-harness": "neck", "neck-lateral": "neck", "neutral-machine-row": "horizontal-pull", "neutral-pulldown": "vertical-pull", "nordic": "knee-flexion", "oblique-crunch": "anti-rotation", "oh-cable-ext": "elbow-extension-overhead", "ohp": "vertical-push", "one-arm-pulldown": "vertical-pull", "pallof": "anti-rotation", "pause-bench": "horizontal-push", "paused-deadlift": "hip-hinge", "paused-squat": "squat", "pec-deck": "shoulder-horizontal-adduction", "pendlay-row": "horizontal-pull", "pendulum-squat": "squat", "pike-pushup": "vertical-push", "pin-bench": "horizontal-push", "pin-press-ohp": "vertical-push", "pin-squat": "squat", "pinwheel-curl": "elbow-flexion", "pistol-squat": "lunge", "pjr-pullover": "elbow-extension-overhead", "plank": "anti-extension", "plate-front-raise": "shoulder-flexion", "plate-pinch": "grip-static", "power-shrug": "shrug", "preacher": "elbow-flexion", "prone-db-rear-fly": "shoulder-horizontal-abduction", "prone-y-raise": "scapular-upward-rotation", "pull-through": "hip-hinge", "pullup": "vertical-pull", "push-press": "vertical-push", "pushdown": "elbow-extension-neutral", "pushup": "horizontal-push", "rack-pull": "hip-hinge", "rdl": "hip-hinge", "rear-band-pull-apart": "shoulder-horizontal-abduction", "rear-fly": "shoulder-horizontal-abduction", "renegade-row": "horizontal-pull", "rev-grip-curl": "elbow-flexion-neutral", "reverse-crunch": "spinal-flexion", "reverse-curl": "wrist-extension", "reverse-hyper": "hip-extension", "reverse-lunge": "lunge", "reverse-pec": "shoulder-horizontal-abduction", "reverse-wrist-curl": "wrist-extension", "rope-cable-row": "horizontal-pull", "rope-hammer-curl": "elbow-flexion-neutral", "rope-pushdown": "elbow-extension-neutral", "russian-twist": "anti-rotation", "seal-row": "horizontal-pull", "seated-calf": "plantarflexion-bent-knee", "seated-curl": "knee-flexion", "seated-db-press": "vertical-push", "seated-incline-curl": "elbow-flexion", "seated-ohp": "vertical-push", "seated-row": "horizontal-pull", "serratus-pushup": "anti-extension", "side-lying-abduction": "hip-abduction", "side-lying-raise": "shoulder-abduction", "side-plank": "anti-extension", "single-cable-curl": "elbow-flexion", "single-cable-raise": "shoulder-abduction", "single-cable-row": "horizontal-pull", "single-calf": "plantarflexion-straight-knee", "single-leg-press": "squat", "single-leg-press-calf": "plantarflexion-bent-knee", "single-lying-curl": "knee-flexion", "single-pushdown": "elbow-extension-neutral", "sissy-squat": "knee-extension", "situp": "spinal-flexion", "skullcrusher": "elbow-extension-overhead", "sl-hip-thrust": "hip-extension", "slider-curl": "knee-flexion", "slrdl": "hip-hinge", "smith-bench": "horizontal-push", "smith-calf": "plantarflexion-straight-knee", "smith-hip-thrust": "hip-extension", "smith-incline": "incline-push", "smith-ohp": "vertical-push", "smith-reverse-lunge": "lunge", "smith-shrug": "shrug", "smith-squat": "squat", "snatch-deadlift": "hip-hinge", "spanish-squat": "squat", "spider-curl": "elbow-flexion", "split-squat": "lunge", "spoto-press": "horizontal-push", "squeeze-press": "shoulder-horizontal-adduction", "standing-cable-abduction": "hip-abduction", "standing-calf": "plantarflexion-straight-knee", "standing-curl": "knee-flexion", "step-through-lunge": "hip-extension", "step-up": "lunge", "stiff-deadlift": "hip-hinge", "stir-pot": "anti-extension", "straight-bar-pushdown": "elbow-extension-neutral", "straight-pulldown": "vertical-pull", "suitcase-carry": "grip-static", "sumo-dl": "hip-extension", "sumo-squat": "squat", "superman": "hip-hinge", "svend-press": "shoulder-horizontal-adduction", "t-bar-chest-supported": "horizontal-pull", "tate-press": "elbow-extension-neutral", "tbar-row": "horizontal-pull", "tempo-squat": "squat", "tibialis-raise": "dorsiflexion", "toes-to-bar": "spinal-flexion", "towel-hang": "grip-static", "trap-bar-deadlift": "hip-hinge", "trap-bar-shrug": "shrug", "tricep-kickback": "elbow-extension-neutral", "underhand-cable-row": "horizontal-pull", "underhand-pulldown": "vertical-pull", "underhand-pushdown": "elbow-extension-neutral", "underhand-row": "horizontal-pull", "upright-row": "shoulder-abduction", "v-bar-pushdown": "elbow-extension-neutral", "v-squat": "squat", "v-up": "spinal-flexion", "viking-press": "vertical-push", "waiter-curl": "elbow-flexion", "walking-lunge": "lunge", "wall-sit": "isometric-hold", "weighted-plank": "anti-extension", "wide-cable-row": "horizontal-pull", "wide-machine-row": "horizontal-pull", "wide-pulldown": "vertical-pull", "wide-tbar-row": "horizontal-pull", "windshield-wiper": "anti-rotation", "woodchopper": "anti-rotation", "wrist-curl": "wrist-flexion", "wrist-roller": "grip-static", "yates-row": "horizontal-pull", "z-press": "vertical-push", "zercher-squat": "squat", "zottman-curl": "elbow-flexion" };

const EXERCISES = RAW.map(([id, name, part, type, equip, lo, hi, region, pattern], i) => ({ id, name, part, type, equip, rep: [lo, hi], pri: i, ...(region ? { region } : {}), ...((pattern || PATTERN_BY_ID[id]) ? { pattern: pattern || PATTERN_BY_ID[id] } : {}) }));

const EX_BY_ID = Object.fromEntries(EXERCISES.map(e => [e.id, e]));

const SPLITS = {
    full_body: { name: "Full Body", /* Was "Train everything each session", which is not what it builds. Measured across 3/4/5/6 days
             at every session length, a day covers 3-5 of the 7 major muscle groups and NEVER all seven —
             there is no session budget in which eight movement patterns fit. The rotation is the design and
             it is a good one, so the blurb now describes the rotation instead of promising a session that
             cannot exist. A split's blurb is read before anything is generated, which makes it the one
             claim a user cannot check before committing. */
        blurb: "Rotating full-body days — each session anchors on a heavy compound and covers several movement patterns, so every muscle is trained two or more times a week. Spreads fatigue across patterns and scales from low to high frequency.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb_a", "fb_b", "fb_c", "fb_d", "fb_e"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    upper_lower: { name: "Upper / Lower", blurb: "Alternate upper & lower days, rotating horizontal/vertical and quad/hinge emphasis.", days: [2, 4, 6], build: (d) => Array.from({ length: d }, (_, i) => `${i % 2 === 0 ? "upper" : "lower"}${Math.floor(i / 2) % 2 === 0 ? "_a" : "_b"}`) },
    ppl: { name: "Push / Pull / Legs", blurb: "The classic, with each repeat rotating emphasis (chest/delt, width/thickness, quad/posterior).", days: [3, 5, 6], build: (d) => { const A = ["push_a", "pull_a", "legs_a"], B = ["push_b", "pull_b", "legs_b"]; return Array.from({ length: d }, (_, i) => (Math.floor(i / 3) % 2 === 0 ? A : B)[i % 3]); } },
    ulppl: { name: "Upper·Lower·Push·Pull·Legs", blurb: "Hybrid 5-day blending UL frequency with PPL volume.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"] },
    hybrid: { name: "Hybrid Strength + Size", blurb: "Heavy strength Upper & Lower to open the week, then a higher-volume hypertrophy Push/Pull/Legs — strength and size in one week, the way modern physique programs run it.", days: [5], build: () => ["upper", "lower", "push", "pull", "legs"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    phul: { name: "Power / Hypertrophy Upper-Lower", blurb: "Heavy upper/lower sessions followed by higher-rep upper/lower sessions; The plan adapts the exact prescription and progression.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "upper_hyp", "lower_hyp"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy"] },
    phat: { name: "Power–Hypertrophy 5-Day", blurb: "Two heavy power days then three higher-rep hypertrophy days.", days: [5], minSession: "s60", maxSession: "s120", build: () => ["upper_power", "lower_power", "back_shoulders", "lower_hyp", "chest_arms"], focus: () => ["strength", "strength", "hypertrophy", "hypertrophy", "hypertrophy"] },
    bro: { name: "Bro Split", blurb: "One muscle group per day, max volume each.", days: [5, 6], build: (d) => d >= 6 ? ["chest_day", "back_day", "shoulders_day", "legs_day", "arms", "legs_day"] : ["chest_day", "back_day", "shoulders_day", "legs_day", "arms"] },
    arnold: { name: "Chest + Back / Shoulders + Arms / Legs", blurb: "Chest+Back · Shoulders+Arms · Legs, twice over, with Pursuit-owned dose and progression.", days: [6], build: () => ["chest_back", "shoulders_arms", "legs_day", "chest_back", "shoulders_arms", "legs_day"] },
    five_three_one: { name: "Main-Lift Waves", blurb: "Four days, each built around one main barbell lift (Press · Deadlift · Bench · Squat) plus targeted accessories, loaded off a training max.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["t531_press", "t531_deadlift", "t531_bench", "t531_squat"] },
    five31_beginner: { minBarbells: 2, name: "Main-Lift Waves · Novice", blurb: "Three days a week, two main lifts each — fixed-rep top sets plus a same-load back-off block — so every lift is trained twice a week.", days: [3], minSession: "s60", maxSession: "s90", build: (_n) => ["b531_a", "b531_b", "b531_a"] },
    strength_fb: { name: "Strength Full Body", blurb: "Squat, press and pull every session — the classic barbell linear-progression template for building base strength.", days: [3], build: () => ["full_a", "full_b", "full_c"] },
    academy_prep: { name: "Academy Prep", blurb: "Builds the strength base for the law-enforcement academy fitness test — pressing endurance for push-ups, a trained trunk for sit-ups, and posterior-chain/leg work to drive sprints and the 1.5-mile run. Pair with Pursuit Rated for the running side.", days: [3, 4], build: (d) => { const seq = ["acad_a", "acad_b", "acad_c"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    texas: { name: "Volume / Recovery / Intensity", blurb: "Three-day strength undulation: a higher-volume session, a deliberately lower-fatigue session, then an intensity-focused session. The plan adapts the exact sets, reps and loading.", days: [3], minSession: "s60", maxSession: "s120", build: () => ["tx_volume", "tx_recovery", "tx_intensity"] },
    gzclp: { minBarbells: 2, name: "Tiered Linear Progression", blurb: "Four-day tiered strength structure with a heavy primary lift, secondary volume work and accessories; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    rippler: { minBarbells: 2, name: "Tiered Wave", blurb: "Four-day tiered strength structure with changing intensity emphasis plus secondary volume and accessories; The plan adapts the exact wave.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    jt: { name: "Tiered Powerbuilding", blurb: "Four-day tiered powerbuilding structure balancing heavy primary work with higher-volume secondary and accessory work; The plan adapts the exact prescription.", days: [4], minSession: "s60", maxSession: "s120", build: () => ["gz_a1", "gz_b1", "gz_a2", "gz_b2"] },
    // APPEND-ONLY BOUNDARY: CODE_SPLITS = Object.keys(SPLITS) is the split index inside every program
    // code ever issued. New splits go BELOW this line; nothing above it may be reordered or removed.
    glute_focus: { name: "Glutes & Lower Body", blurb: "Hip-dominant training — a hinge or thrust anchors each lower day with direct abduction work, balanced by a pull-led upper day, and a dedicated trunk day once you're training five times a week.", days: [3, 4, 5], build: (d) => { const seq = ["glute_a", "upper_tone", "glute_b", "upper_a", "core_abs"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    /* APPENDED below the boundary, so every program code ever issued keeps its meaning: this takes the
       next free split index rather than shifting any existing one. */
    full_body_patterns: { name: "Full Body \u00B7 Pattern Rotation", blurb: "Full-body days built as pattern PAIRS rather than one anchor lift \u2014 squat with horizontal push/pull, hinge with vertical, then unilateral, posterior chain and a pump day. Spreads pressing across the week, so the front delt takes less of it.", days: [2, 3, 4, 5, 6], build: (d) => { const seq = ["fb2_a", "fb2_b", "fb2_c", "fb2_d", "fb2_e"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    torso_limbs: { name: "Torso / Limbs", blurb: "Torso days (chest, back and shoulders) alternate with Limbs days (legs and arms). Separating heavy pressing and pulling from direct arm work gives the arms a session where they are not already fatigued.", days: [4, 5], build: (d) => { const seq = ["torso_a", "limbs_a", "torso_b", "limbs_b", "torso_a"]; return Array.from({ length: d }, (_, i) => seq[i % seq.length]); } },
    ppla: { name: "Push / Pull / Legs / Arms", blurb: "PPL with a dedicated fourth session for shoulders and arms, so side delts, biceps and triceps are trained fresh rather than on whatever is left after pressing.", days: [4, 5], build: (d) => (d >= 5 ? ["push_a", "pull_a", "legs_a", "upper_b", "shoulders_arms"] : ["push_a", "pull_a", "legs_a", "shoulders_arms"]) },
    ula: { name: "Upper / Lower / Arms", blurb: "Two upper days, two lower days, and a dedicated arms and weak-point day \u2014 for lifters whose arms and side delts lag the rest.", days: [5], build: () => ["upper_a", "lower_a", "upper_b", "lower_b", "shoulders_arms"] },
    sbd_power: { name: "Powerlifting \u00B7 SBD Wave", blurb: "Built around the squat, bench and deadlift, with hypertrophy accessories behind them. Three days pairs the lifts across sessions; four gives each its own day plus an upper power day.", days: [3, 4], build: (d) => (d >= 4 ? ["t531_squat", "t531_bench", "t531_deadlift", "upper_power"] : ["sbd_squat_bench", "sbd_bench_deadlift", "sbd_squat_deadlift"]), focus: (d) => (d >= 4 ? ["strength", "strength", "strength", "hypertrophy"] : ["strength", "strength", "hypertrophy"]) },
    /* ⚠ APPENDED AT THE END ON PURPOSE. SPLITS order IS an index: program codes encode a split by its
       position, so inserting anywhere but the end shifts every split after it and old shared codes decode
       to the WRONG PROGRAM. gates/generator check 28 caught exactly that ("prefix drifted") when this sat
       next to `upper_lower`, where it reads better and breaks compatibility. New splits go last, always. */
    /* ⚠ THE ALTERNATING UPPER/LOWER. `upper_lower` offers 2, 4 and 6 days because an even count divides
       cleanly into one week. THREE does not, and the established answer is not a third day type — it is
       to keep alternating ACROSS the week boundary: U L U | L U L, so upper and lower each get THREE
       sessions per fortnight. Balanced, and the reason the method is standard.
       `rotation: 2` makes the volume model judge targets over the FORTNIGHT. Without it the engine
       scores a two-week structure against a one-week yardstick and calls every other week under-dosed —
       which is why I refused this feature once, wrongly.
       FOUR day types so the rolling cursor never repeats a session back to back. `build` returns the
       POOL; the `rotationPool` stage keeps three of them in `program.days` (the week) and parks all four
       in `program.pool`, so `days.length` still means "sessions per week" for its 68 readers. */
    upper_lower_alt: { name: "Upper / Lower \u00b7 Alternating", blurb: "Three sessions a week, alternating upper and lower without resetting each week \u2014 each gets three sessions per fortnight.", days: [3], rotation: 2, minSession: "s40",
        build: () => ["upper_a", "lower_a", "upper_b", "lower_b"] },
};

const SESSIONS = [
    { id: "s20", label: "Up to 20 minutes", count: 2 },
    { id: "s40", label: "20 to 40 minutes", count: 3 },
    { id: "s60", label: "40 to 60 minutes", count: 4 },
    { id: "s90", label: "60 to 90 minutes", count: 5 },
    { id: "s120", label: "90 to 120 minutes", count: 6 },
    { id: "s120p", label: "Over 120 minutes", count: 7 },
];

const EXP = {
    none: { label: "None", sub: "Brand new to lifting", setBase: 2 },
    beginner: { label: "Beginner", sub: "< 1 year training", setBase: 3 },
    intermediate: { label: "Intermediate", sub: "1–3 years training", setBase: 3 },
    advanced: { label: "Advanced", sub: "3+ years, dialled-in", setBase: 4 }
};

const TEMPLATES = [
    { id: "ppl6", cat: "Hypertrophy", featured: true, featuredRank: 10, name: "Push / Pull / Legs", tag: "6 days · Hypertrophy", desc: "High-volume classic, trained six times a week", cfg: { split: "ppl", days: 6, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ppl3", cat: "Hypertrophy", name: "Push / Pull / Legs · 3 Day", tag: "3 days · Hypertrophy", desc: "PPL run once through — full coverage in three sessions", cfg: { split: "ppl", days: 3, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ul4", cat: "Powerbuilding", featured: true, featuredRank: 20, name: "Upper / Lower", tag: "4 days · Strength & Size", desc: "Balanced heavy lifts plus hypertrophy work", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "ul2", cat: "Beginner", name: "Upper / Lower · 2 Day", tag: "2 days · Beginner", desc: "Whole body across two sessions — ideal when time is tight", cfg: { split: "upper_lower", days: 2, session: "s60", goal: "both", experience: "beginner", weeks: 4 } },
    { id: "ul6", cat: "Powerbuilding", name: "Upper / Lower · 6 Day", tag: "6 days · Powerbuilding", desc: "High-frequency upper/lower for advanced lifters", cfg: { split: "upper_lower", days: 6, session: "s90", goal: "both", experience: "advanced", weeks: 6 } },
    { id: "fb3", cat: "Beginner", featured: true, featuredRank: 40, name: "Full Body", tag: "3 days · Beginner", desc: "Hit everything three times a week", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 4 } },
    { id: "fb2", cat: "Beginner", name: "Full Body · 2 Day", tag: "2 days · Beginner", desc: "Minimal-time full body, twice a week", cfg: { split: "full_body", days: 2, session: "s60", goal: "both", experience: "beginner", weeks: 4 } },
    { id: "fb4", cat: "Hypertrophy", name: "Full Body · 4 Day", tag: "4 days · Hypertrophy", desc: "Four rotating full-body sessions for high frequency", cfg: { split: "full_body", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "fb5", cat: "Hypertrophy", name: "Full Body · High Frequency", tag: "5 days · Hypertrophy", desc: "High-frequency full body with rotating emphasis and recovery-aware exercise selection", cfg: { split: "full_body", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ulppl5", cat: "Powerbuilding", name: "Upper · Lower · PPL", tag: "5 days · Powerbuilding", desc: "Hybrid blending upper/lower frequency with PPL volume", cfg: { split: "ulppl", days: 5, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "hybrid5", cat: "Powerbuilding", featured: true, featuredRank: 50, name: "Hybrid Strength + Size", tag: "5 days · Strength + Hypertrophy", desc: "Heavy strength Upper/Lower, then a hypertrophy Push/Pull/Legs", cfg: { split: "hybrid", days: 5, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "phul", cat: "Powerbuilding", name: "Power / Hypertrophy Upper-Lower", tag: "4 days · Power + Size", desc: "Heavy upper/lower sessions followed by higher-rep upper/lower sessions; Pursuit owns the exact prescription", cfg: { split: "phul", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "phat", cat: "Powerbuilding", name: "Power–Hypertrophy 5-Day", tag: "5 days · Power + Size", desc: "Two power days plus three hypertrophy days", cfg: { split: "phat", days: 5, session: "s90", goal: "both", experience: "advanced", weeks: 6 } },
    { id: "arnold", cat: "Hypertrophy", name: "Chest + Back / Shoulders + Arms / Legs", tag: "6 days · Hypertrophy", desc: "Chest+Back · Shoulders+Arms · Legs, twice, with Pursuit-owned dose and progression", cfg: { split: "arnold", days: 6, session: "s90", goal: "hypertrophy", experience: "advanced", weeks: 6 } },
    { id: "bro5", cat: "Hypertrophy", name: "Bro Split", tag: "5 days · Hypertrophy", desc: "One muscle group per day, maximum volume", cfg: { split: "bro", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "bro6", cat: "Hypertrophy", name: "Bro Split · 6 Day", tag: "6 days · Hypertrophy", desc: "Bro split with an extra leg day for more lower-body volume", cfg: { split: "bro", days: 6, session: "s90", goal: "hypertrophy", experience: "advanced", weeks: 6 } },
    { id: "p531", cat: "Strength", featured: true, featuredRank: 70, name: "Main-Lift Waves", tag: "4 days · Strength", desc: "One main barbell lift anchors each day while Pursuit adapts the wave, dose and progression", cfg: { split: "five_three_one", days: 4, session: "s60", goal: "strength", experience: "intermediate", weeks: 4, percentScheme: "531" } },
    { id: "p531beg", cat: "Beginner", featured: true, featuredRank: 80, name: "Main-Lift Waves · Novice", tag: "3 days · Strength + Size", desc: "A novice three-day main-lift wave with two prioritized barbell exposures per session and Pursuit-owned assistance", cfg: { split: "five31_beginner", days: 3, session: "s90", goal: "both", experience: "beginner", weeks: 4, percentScheme: "531beg" } },
    { id: "p531bbb", cat: "Powerbuilding", name: "Main-Lift Waves · High-Volume Supplemental", tag: "4 days · Strength + Size", desc: "Main-lift wave work plus higher-volume supplemental work, with dose adapted to the training goal", cfg: { split: "five_three_one", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 4, percentScheme: "531", assistance: "bbb" } },
    { id: "academy", cat: "Tactical", featured: true, featuredRank: 100, name: "Academy Prep", tag: "3 days · Academy fitness test", desc: "Builds the strength base to pass the law-enforcement academy fitness test — push-up & sit-up endurance plus leg and posterior-chain work for the sprint and 1.5-mile run. Pair with Pursuit Rated for the running side.", cfg: { split: "academy_prep", days: 3, session: "s60", goal: "both", experience: "beginner", weeks: 6 } },
    { id: "academy4", cat: "Tactical", name: "Academy Prep · 4 Day", tag: "4 days · Academy fitness test", desc: "Higher-frequency academy prep — four rotating full-body sessions emphasizing the muscles the Cooper-standard test demands", cfg: { split: "academy_prep", days: 4, session: "s60", goal: "both", experience: "beginner", weeks: 6 } },
    { id: "stronglifts", cat: "Beginner", name: "Straight 5×5", tag: "3 days · Beginner strength", desc: "Straight 5×5 on the big barbell lifts, alternating two workouts", cfg: { split: "strength_fb", days: 3, session: "s60", goal: "strength", experience: "beginner", weeks: 4 } },
    { id: "madcow", cat: "Strength", name: "Ramping 5×5", tag: "3 days · Intermediate strength", desc: "Full-body 5×5 ramping to a top set each session", cfg: { split: "strength_fb", days: 3, session: "s60", goal: "strength", experience: "intermediate", weeks: 6, percentScheme: "madcow" } },
    { id: "nsuns", cat: "Powerbuilding", name: "High-Volume Wave LP", tag: "4 days · Strength + Size", desc: "High-volume primary-lift waves plus secondary work, with load and volume progression built into the plan", cfg: { split: "five_three_one", days: 4, session: "s120", goal: "both", experience: "intermediate", weeks: 4, percentScheme: "nsuns", deload: false } },
    { id: "redditppl", cat: "Powerbuilding", name: "Heavy + Volume PPL", tag: "6 days · Powerbuilding", desc: "Six-day push/pull/legs combining prioritized compounds with hypertrophy accessories", cfg: { split: "ppl", days: 6, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "ppl5", cat: "Hypertrophy", name: "Push / Pull / Legs · 5 Day", tag: "5 days · Hypertrophy", desc: "PPL at five sessions a week for extra push & pull frequency", cfg: { split: "ppl", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "texas", cat: "Strength", name: "Volume / Recovery / Intensity", tag: "3 days · Intermediate strength", desc: "Three-day strength undulation across higher-volume, lower-fatigue and intensity-focused sessions", cfg: { split: "texas", days: 3, session: "s90", goal: "strength", experience: "intermediate", weeks: 6, percentScheme: "texas", deload: false } },
    { id: "gzclp", cat: "Beginner", name: "Tiered Linear Progression", tag: "4 days · Beginner strength + size", desc: "Four-day tiered structure with primary strength, secondary volume and accessory work", cfg: { split: "gzclp", days: 4, session: "s90", goal: "both", experience: "beginner", weeks: 6, percentScheme: "gzclp", deload: false } },
    { id: "greyskull", cat: "Beginner", name: "AMRAP Linear Progression", tag: "3 days · Beginner strength", desc: "Linear progression with an AMRAP final set on every main lift to squeeze out extra reps", cfg: { split: "strength_fb", days: 3, session: "s60", goal: "strength", experience: "beginner", weeks: 4 } },
    { id: "gvt", cat: "Hypertrophy", name: "High-Volume Upper / Lower", tag: "4 days · Hypertrophy", desc: "High-volume upper/lower training with productive dose capped by time and recovery rather than a fixed 10×10 promise", cfg: { split: "upper_lower", days: 4, session: "s120", goal: "hypertrophy", experience: "advanced", weeks: 4, percentScheme: "gvt", deload: false } },
    { id: "rippler", cat: "Strength", name: "Tiered Wave", tag: "4 days · Intermediate strength + size", desc: "Tiered strength work with changing intensity emphasis plus secondary volume and accessories", cfg: { split: "rippler", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 9, percentScheme: "rippler", deload: false } },
    { id: "jt2", cat: "Powerbuilding", name: "Tiered Powerbuilding", tag: "4 days · Strength + Size", desc: "Tiered powerbuilding balancing heavy primary work with higher-volume secondary and accessory work", cfg: { split: "jt", days: 4, session: "s120", goal: "both", experience: "advanced", weeks: 12, percentScheme: "jt", deload: false } },
    { id: "dbhome", cat: "Home", featured: true, featuredRank: 110, name: "Dumbbell Home", tag: "4 days · Dumbbells + bench", desc: "Full muscle coverage with nothing but dumbbells and a bench — built for the spare-room gym", cfg: { split: "upper_lower", days: 4, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 6, equipment: ["dumbbell", "bench"] } },
    { id: "bwanywhere", cat: "Home", name: "Train Anywhere", tag: "3 days · Bands only", desc: "Train in a hotel room, park or living room — bodyweight and a band, 40-minute sessions", cfg: { split: "full_body", days: 3, session: "s40", goal: "hypertrophy", experience: "beginner", weeks: 4, equipment: ["bands"] } },
    { id: "glutebuilder", cat: "Specialization", name: "Glute Builder", tag: "4 days · Lower-body focus", desc: "Upper/lower with doubled glute volume and extra hamstring work — hip thrusts, RDLs and lunges lead", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { glutes: 2, hamstrings: 1 }, focusList: ["glutes", "glutes", "hamstrings"] } },
    { id: "armspec", cat: "Specialization", name: "Arm Specialization", tag: "4 days · Arm focus", desc: "Upper/lower with doubled biceps and triceps volume — curl and extension work layered onto heavy pressing and pulling to bring up lagging arms", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { biceps: 1, triceps: 1 }, focusList: ["biceps", "triceps"] } },
    { id: "deltspec", cat: "Specialization", name: "Boulder Shoulders", tag: "4 days · Delt focus", desc: "Upper/lower with tripled shoulder volume — overhead pressing plus lateral and rear-delt isolation for capped, 3-D delts", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { shoulders: 2 }, focusList: ["shoulders", "shoulders"] } },
    { id: "backspec", cat: "Specialization", name: "Back Width & Thickness", tag: "4 days · Back focus", desc: "Upper/lower with extra lat and upper-back volume — vertical pulls for width, heavy rows for thickness", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { lats: 1, upper_back: 1 }, focusList: ["lats", "upper_back"] } },
    { id: "chestspec", cat: "Specialization", name: "Chest Focus", tag: "4 days · Chest focus", desc: "Upper/lower with doubled chest volume — flat and incline pressing plus a fly variation to build a fuller chest", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { chest: 2 }, focusList: ["chest", "chest"] } },
    { id: "kbhome", cat: "Home", name: "Kettlebell Home", tag: "3 days · Kettlebells", desc: "Full-body strength with kettlebells, a pull-up bar and bands — compact kit, big coverage", cfg: { split: "full_body", days: 3, session: "s40", goal: "both", experience: "intermediate", weeks: 6, equipment: ["kettlebell", "pullup", "bands"] } },
    { id: "machines", cat: "Beginner", name: "Machine Circuit", tag: "3 days · Machines only", desc: "Joint-friendly machines and cables with guided movement paths — ideal for brand-new gym-goers", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "none", weeks: 4, equipment: ["machine", "cable", "smith"] } },
    { id: "fierce5", cat: "Beginner", name: "Balanced Novice A/B", tag: "3 days · Strength + Size", desc: "A balanced novice full-body A/B with prioritized compounds and enough accessory volume to build a physique", cfg: { split: "full_body", days: 3, session: "s60", goal: "both", experience: "beginner", weeks: 6 } },
    { id: "lylegbr", cat: "Hypertrophy", name: "Intermediate Upper / Lower", tag: "4 days · Hypertrophy", desc: "Intermediate upper/lower with moderate productive volume, repeated movement exposure and progression across the week", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 8 } },
    { id: "omnistrength", cat: "Powerbuilding", featured: true, featuredRank: 120, name: "Omni Strength", tag: "4 days · Strength + Aesthetics", desc: "High-frequency full body with a rotating heavy anchor each session and wide exercise variety — built for the jack-of-all-trades lifter chasing strength and an aesthetic physique at once", cfg: { split: "full_body", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 8 } },
    { id: "express_fb", cat: "Quick", featured: false, name: "Express Full Body", tag: "3 days · ~40 min", desc: "Auto-supersetted full-body sessions that fit a lunch break — strength and size in 40 minutes flat", cfg: { split: "full_body", days: 3, session: "s40", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "express_ul", cat: "Quick", name: "Lunch-Break Split", tag: "4 days · ~40 min", desc: "An upper/lower split compressed to 40-minute sessions with paired accessories — busy weeks covered", cfg: { split: "upper_lower", days: 4, session: "s40", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    /* ---- Hip-dominant / glute-led -------------------------------------------------------------
       Training doesn't divide by sex, and these aren't labelled as if it did — but the goal cluster
       the library was NOT serving is real: a hip-dominant physique with the upper-body work biased
       toward back and delts rather than chest. Every previous lower day here opened on a squat, so
       "glute training" meant one accessory bolted onto a quad session. The glute_focus split does the
       structural work (a hinge or thrust anchors the day, abduction gets a real slot) and `focus`
       stacks the volume on top. Named for what they train, not for who's expected to run them. */
    { id: "glutes3", cat: "Specialization", featured: true, featuredRank: 30, name: "Glute Builder · 3 Day", tag: "3 days · Glute focus", desc: "Hip thrusts, RDLs and abduction work anchor two hip-dominant lower days, balanced by one pull-led upper day — the shortest week that still builds glutes properly", cfg: { split: "glute_focus", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, focus: { glutes: 1 }, focusList: ["glutes"] } },
    { id: "glutes4", cat: "Specialization", name: "Glute Builder · 4 Day", tag: "4 days · Glute focus", desc: "Two hip-anchored lower days and two upper days, with doubled glute volume and extra hamstring work spread across the week", cfg: { split: "glute_focus", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { glutes: 2, hamstrings: 1 }, focusList: ["glutes", "glutes", "hamstrings"] } },
    { id: "glutes5", cat: "Specialization", name: "Glutes & Core · 5 Day", tag: "5 days · Glute + trunk focus", desc: "The full hip-dominant week — two glute days, two upper days and a dedicated trunk session training abs, low back and the hip abductors directly", cfg: { split: "glute_focus", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 8, focus: { glutes: 2, abs: 1 }, focusList: ["glutes", "glutes", "abs"] } },
    { id: "deltglute", cat: "Specialization", name: "Delts & Glutes", tag: "4 days · Shoulder + glute focus", desc: "The two muscles that most change a silhouette, trained hard in the same week — lateral and rear delt volume up top, thrusts and abduction below", cfg: { split: "glute_focus", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { glutes: 2, shoulders: 2 }, focusList: ["glutes", "glutes", "shoulders", "shoulders"] } },
    { id: "lowerlean", cat: "Specialization", name: "Lower Body Focus", tag: "4 days · Legs + glutes", desc: "Three lower days to one upper — quads, hamstrings, glutes and adductors all get direct work, for anyone whose legs are the priority rather than an afterthought", cfg: { split: "glute_focus", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6, focus: { glutes: 1, quads: 1, hamstrings: 1 }, focusList: ["glutes", "quads", "hamstrings"] } },
    { id: "fbsculpt", cat: "Beginner", featured: true, featuredRank: 90, name: "Full Body Sculpt", tag: "3 days · Beginner", desc: "A balanced full-body start with extra glute, shoulder and core volume — no 'toning' myths, just the muscle-building work that actually changes how you look", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "none", weeks: 6, focus: { glutes: 1, shoulders: 1, abs: 1 }, focusList: ["glutes", "shoulders", "abs"] } },
    { id: "coreback", cat: "Specialization", name: "Core & Posture", tag: "4 days · Trunk + upper back", desc: "Upper/lower with doubled core and upper-back volume — rows, rear delts and direct trunk work for the desk-bound", cfg: { split: "upper_lower", days: 4, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, focus: { abs: 2, upper_back: 1 }, focusList: ["abs", "abs", "upper_back"] } },
    /* ---- Bodyweight ---------------------------------------------------------------------------
       Minimum kit is a band or a bar, deliberately: with literally nothing, the exercise library has
       zero lat and zero biceps options, so a true no-equipment program would ship a week with no
       back work in it. A £10 band or a doorway bar closes that hole, and both are already first-class
       equipment ids, so the generator handles them without special-casing. */
    /* True zero-equipment. There is no lat or biceps ISOLATION without a band or bar, which is why
       these were held back — but the Inverted Row (a table edge, nothing bought) carries back work
       twice a week, and that is enough for an honest full-body week. These are what make the
       "Bodyweight" filter mean something instead of returning an empty list. */
    { id: "bwzero3", cat: "Home", name: "No Equipment", tag: "3 days · Nothing at all", desc: "Push-ups, squats, lunges, hinges and inverted rows under a table — a complete week needing not one piece of equipment", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "none", weeks: 6, equipment: [] } },
    { id: "bwzerogl", cat: "Specialization", name: "No Equipment · Glutes & Core", tag: "3 days · Nothing at all", desc: "Glute bridges, single-leg hip thrusts and frog pumps with direct trunk work — hip-dominant training that needs no kit and no floor space to speak of", cfg: { split: "glute_focus", days: 3, session: "s60", goal: "hypertrophy", experience: "none", weeks: 6, equipment: [], focus: { glutes: 2, abs: 1 }, focusList: ["glutes", "glutes", "abs"] } },
    { id: "bwband3", cat: "Home", featured: true, featuredRank: 60, name: "Bodyweight & Bands", tag: "3 days · Bands only", desc: "Full-body training with one set of resistance bands — push-ups, squats, hinges and banded pulls, no gym and no weights", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, equipment: ["bands"] } },
    { id: "bwbar4", cat: "Home", name: "Calisthenics Foundations", tag: "4 days · Bar + dip + bands", desc: "Pull-ups, dips, push-ups and single-leg work on a four-day rotation — the classic bodyweight progression, run as a real hypertrophy program", cfg: { split: "full_body", days: 4, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 8, equipment: ["pullup", "dip", "bands"] } },
    { id: "bwppl", cat: "Home", name: "Calisthenics Push / Pull / Legs", tag: "3 days · Bar + dip + bands", desc: "PPL run on bodyweight and bands — pressing, pulling and single-leg volume split across three focused sessions", cfg: { split: "ppl", days: 3, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 6, equipment: ["pullup", "dip", "bands"] } },
    { id: "bwglute", cat: "Specialization", name: "Band Glutes & Core", tag: "3 days · Bands only", desc: "Glute bridges, single-leg hip thrusts and banded abduction plus direct trunk work — a hip-dominant program that fits in a living room", cfg: { split: "glute_focus", days: 3, session: "s40", goal: "hypertrophy", experience: "none", weeks: 6, equipment: ["bands"], focus: { glutes: 2, abs: 1 }, focusList: ["glutes", "glutes", "abs"] } },
    { id: "bwul4", cat: "Home", name: "Calisthenics Upper / Lower", tag: "4 days · Bar + dip + bands", desc: "Four-day upper/lower run without a gym — pull-ups, dips and push-up variations up top, single-leg and hinge work below", cfg: { split: "upper_lower", days: 4, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 8, equipment: ["pullup", "dip", "bands"] } },
    /* ---- Home gym ------------------------------------------------------------------------------ */
    { id: "db1pair", cat: "Home", name: "One Pair of Dumbbells", tag: "3 days · Dumbbells only", desc: "No bench, no rack, one pair of dumbbells — floor presses, goblet squats and rows covering every muscle group in three sessions", cfg: { split: "full_body", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, equipment: ["dumbbell"] } },
    { id: "dbppl", cat: "Home", name: "Dumbbell Push / Pull / Legs", tag: "3 days · Dumbbells + bench", desc: "The PPL structure run on a home kit — pressing, rowing and leg volume with dumbbells and an adjustable bench", cfg: { split: "ppl", days: 3, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 6, equipment: ["dumbbell", "bench"] } },
    { id: "dbglute", cat: "Specialization", name: "Home Glute Builder", tag: "3 days · Dumbbells + bands", desc: "Hip-dominant training for a spare-room setup — dumbbell RDLs, split squats and hip thrusts with banded abduction filling the gaps", cfg: { split: "glute_focus", days: 3, session: "s60", goal: "hypertrophy", experience: "beginner", weeks: 6, equipment: ["dumbbell", "bands", "bench"], focus: { glutes: 2 }, focusList: ["glutes", "glutes"] } },
    { id: "garage4", cat: "Powerbuilding", name: "Garage Gym", tag: "4 days · Barbell + rack", desc: "Barbell, bench, dumbbells and a pull-up bar — the standard home setup, run as a proper strength-and-size upper/lower", cfg: { split: "upper_lower", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 8, equipment: ["barbell", "bench", "dumbbell", "pullup"] } },
    { id: "dbul2", cat: "Home", name: "Dumbbell Upper / Lower · 2 Day", tag: "2 days · Dumbbells + bench", desc: "Whole body across two home sessions — the minimum viable week when training time is the constraint, not equipment", cfg: { split: "upper_lower", days: 2, session: "s60", goal: "both", experience: "beginner", weeks: 6, equipment: ["dumbbell", "bench"] } },
    /* ---- The splits that had no template ---------------------------------------------------------
       Five splits shipped with nothing pointing at them: Full Body · Pattern Rotation and the four
       ported afterwards (Torso / Limbs, PPL / Arms, Upper / Lower / Arms, SBD Wave). A split with no
       template is reachable ONLY through Custom split, which is the one route a lifter takes when they
       already know what they want to build — so the splits added to give people new options were
       invisible to exactly the people the options were for. One entry per day count the split offers
       that is worth a distinct recommendation; gates/templatereach.mjs now fails the build if a split
       is ever left unreachable again. Additions only — no existing template's config is touched. */
    { id: "fbpat3", cat: "Powerbuilding", name: "Full Body · Pattern Rotation", tag: "3 days · Strength & Size", desc: "Each session is led by one of the big patterns — squat, hinge, incline press — with the rest of the body trained around it, so a barbell lift anchors every day", cfg: { split: "full_body_patterns", days: 3, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "fbpat5", cat: "Hypertrophy", name: "Full Body · Pattern Rotation · 5 Day", tag: "5 days · Hypertrophy", desc: "Five rotating full-body sessions, each anchored on a different pattern — high frequency without stacking pressing onto the front delts", cfg: { split: "full_body_patterns", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "torso4", cat: "Powerbuilding", name: "Torso / Limbs", tag: "4 days · Strength & Size", desc: "Chest, back and shoulders on one day; legs and arms on the next — arms get a session where they are not already spent from pressing and rowing", cfg: { split: "torso_limbs", days: 4, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "torso5", cat: "Hypertrophy", name: "Torso / Limbs · 5 Day", tag: "5 days · Hypertrophy", desc: "The torso/limbs alternation run five times a week, so every muscle is trained a little over twice", cfg: { split: "torso_limbs", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ppla4", cat: "Hypertrophy", name: "Push / Pull / Legs / Arms", tag: "4 days · Hypertrophy", desc: "PPL with a fourth session for shoulders and arms, so side delts, biceps and triceps are trained fresh instead of on what is left after pressing", cfg: { split: "ppla", days: 4, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ppla5", cat: "Hypertrophy", name: "Push / Pull / Legs / Arms · 5 Day", tag: "5 days · Hypertrophy", desc: "The arms-day PPL with a fifth session, adding back the second exposure the four-day version trades away", cfg: { split: "ppla", days: 5, session: "s90", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ulalt3", cat: "Hypertrophy", name: "Upper / Lower \u00b7 Alternating", tag: "3 days \u00b7 Balanced over a fortnight", desc: "Upper and lower alternate without resetting each week \u2014 sessions run upper, lower, upper then lower, upper, lower, so each gets three sessions per fortnight instead of an uneven two and one", cfg: { split: "upper_lower_alt", days: 3, session: "s60", goal: "hypertrophy", experience: "intermediate", weeks: 6 } },
    { id: "ula5", cat: "Powerbuilding", name: "Upper / Lower / Arms", tag: "5 days · Strength & Size", desc: "Two upper, two lower and a dedicated arm day — the upper/lower frequency with the direct arm volume it usually has no room for", cfg: { split: "ula", days: 5, session: "s90", goal: "both", experience: "intermediate", weeks: 6 } },
    { id: "sbd3", cat: "Strength", name: "Powerlifting · SBD Wave", tag: "3 days · Strength", desc: "The week is built around squat, bench and deadlift, with hypertrophy accessories behind each of them rather than competing for the session", cfg: { split: "sbd_power", days: 3, session: "s90", goal: "strength", experience: "intermediate", weeks: 8 } },
    { id: "sbd4", cat: "Strength", name: "Powerlifting · SBD Wave · 4 Day", tag: "4 days · Strength", desc: "The same competition-lift wave over four sessions, giving the accessory work its own day instead of the tail of a heavy one", cfg: { split: "sbd_power", days: 4, session: "s90", goal: "strength", experience: "advanced", weeks: 8 } },
];

const ENGINE_V = 33;

const ENGINES = {

    33: { label: "Named programs prescribe their own reps", changes: ["Programs built on a set percentage plan — 5/3/1 and its variants, GZCLP, Texas Method — now show the reps that plan actually calls for on its main lifts. Main-Lift Waves is five reps a set across its waves, for example; the app had been showing a general rep range instead, which did not match the programme you chose."] },



    32: { label: "Volume and recovery, measured the way the research measures them", changes: [
            "Every muscle is now held to the same volume targets instead of a different number for each one. Across sixty-seven studies the growth response turned out to be the same curve for every muscle \u2014 what differs is how much work each already gets from your compound lifts, which Pursuit Iron tracks separately.",
            "Squats no longer count toward hamstring volume, and conventional deadlifts count much less. Imaging studies measure no hamstring growth from squatting: the muscle works hard, but it barely changes length, and length change is what drives growth. Expect more curls, Romanian deadlifts and good mornings."
        ] },
    31: { label: "Your barbell limit is respected everywhere", changes: ["If you cap how many barbell movements a session can have, that limit now holds all the way through. Previously it was applied when the session was first laid out and then ignored by later adjustments, so you could still end up with two barbell lifts — a squat and a row, say — on the same day."] },
    30: { label: "Torso / Limbs trains your delts", changes: ["On the Torso/Limbs split, side and rear delt work now lands on the limbs day alongside your arms \u2014 which is the reason the split exists. Previously it moved your arms there and left the delts behind on an already-full torso day."] },

    29: { label: "Lower days stay lower", changes: ["Shoulder or arm work will no longer be placed on a leg day just because that day had spare time. It already worked this way in reverse — a squat was never bolted onto an upper day — and now both directions match."] },
    28: {
        label: "Days match their own names",
        changes: ["Every training day now states the movement it is built around — a horizontal push day gets a flat press, a hinge day gets a hinge, an incline day gets an incline — instead of leaving it to whichever variation happened to score best."]
    },
    27: {
        label: "Abs, forearms and traps stop being forgotten",
        changes: ["Core and lower back now get a minimum amount of work in every program, and focusing forearms or traps gives them exercises of their own instead of leaving them to whatever your other lifts happen to provide."]
    },
    26: {
        label: "Reducing a muscle actually reduces it",
        changes: ["A muscle you set to reduce no longer competes for its own exercises \u2014 it keeps only what it earns from compound lifts, plus a maintenance slot if it would otherwise fall too low. Previously the setting could leave the number of exercises unchanged."]
    },
    25: {
        label: "More balanced full-body rotation",
        changes: ["Full-body pattern rotation compares a broader layout with its existing layout, preserving muscle coverage, focus work and session limits. Candidate checks cover every training week."]
    },
    24: {
        label: "Your priorities guide program and cycle selection",
        changes: ["Focus points and reduced muscles are interpreted consistently. Reduced muscles get a final maintenance-volume pass without cutting other muscles below their existing coverage. New cycles compare complete candidate cycles while keeping their shared lifts coherent."]
    },
    23: {
        label: "Focusing a small muscle now actually changes your program",
        changes: [
            "The focus picker promises that each level adds an exercise for that muscle, and for the small ones it usually could not keep that promise \u2014 measured, focusing forearms twice left them at zero sets in 58% of programs and neck in 92%, because adding an exercise would break the session's stated exercise count and there was no existing movement to add a set to. A focus point that cannot be filled any other way now takes a slot from a muscle that has sets to spare, and the program is rebuilt both ways and compared, so the trade is kept only when nothing else came off worse."
        ]
    },
    22: {
        label: "Your second curl of the week trains the brachialis",
        changes: [
            "Every program picked the same incline curl for every biceps slot \u2014 measured, one exercise took 53 of 53 slots across the whole config space, because selection ranks by a single priority number and never looks at what the first pick already trained. When the week has two or more biceps slots, the second one now goes to a neutral-grip movement (hammer curl and its variants), which loads the brachialis underneath the biceps rather than repeating the same supinated curl.",
        ]
    },
    21: {
        label: "Full-body pattern rotation trains your delts and stops over-serving legs",
        changes: [
            "The pattern rotation asked for quads and hamstrings on four days each, which took a quarter of every program and split leg work across five movements at two sets apiece. Side and rear delts were not asked for at all, so nine in ten programs came in under the minimum for them. Hamstrings now sit on two days, and the side and rear delt heads are requested directly again. The slots this frees are why focusing a small muscle like forearms can now actually be granted.",
        ]
    },
    20: {
        label: "The pump day stops opening on a squat",
        changes: [
            "The unilateral day in the pattern rotation is meant to be the one day of the week that does not load your spine, but it kept being built around a Smith or hack squat \u2014 so a five-day week could open AND close on a squat. A day can now refuse to be anchored on a movement its own design excludes, and it picks a leg press or a lunge instead. Programs you already saved keep the order they were built with.",
        ]
    },
    19: {
        label: "Two days built on the same movement are not put side by side",
        changes: [
            "The week order compared days on the muscles they shared, so two days whose MAIN lift was the same movement \u2014 two squat-led days, or two hinge-led days \u2014 could still land back to back if the rest of their work differed. Sharing a main movement now counts against putting them on consecutive days.",
        ]
    },
    18: {
        label: "Long sessions fill up to the length you asked for",
        changes: [
            "A two-hour session used to stop early \u2014 days came in around 80 minutes because a day was capped at nine exercises no matter how much time was left. Long sessions can now hold eleven, and a day short of its target is filled with the muscles furthest behind rather than more work for the one that already has the most.",
        ]
    },
    17: {
        label: "Heavy back-to-back days are spaced out",
        changes: [
            "Two days that both load the spine hard \u2014 a deadlift day followed by a squat day \u2014 were only compared on which muscles they shared, and a squat and a deadlift do not share many. They now count as a clash, including between the last day of your week and the first day of the next, which has no rest between it either.",
        ]
    },
    16: {
        label: "Side and rear delts stop being blocked by your pressing volume",
        changes: [
            "Pressing gives the front delt a lot of work, which counted toward your shoulders as a whole and could make the app think your shoulders were already full \u2014 so it refused to add the side and rear delt work you were actually short of. It now reads the same shoulder limit the rest of the app enforces.",
        ]
    },
    15: {
        label: "The session length you asked for is the session length you get",
        changes: [
            "The program builder now budgets the time between exercises \u2014 walking to the next station, loading the bar \u2014 the way the plan screen already showed it. Before, 30% of generated days showed a longer session than the builder believed it had made.",
        ]
    },
    14: {
        label: "A program's assistance setting is honoured when the program is built",
        changes: [
            "\u201cJack Shit\u201d, \u201cTriumvirate\u201d and \u201c5\u00d710 Supplemental\u201d now shape the program the moment it is created or shared, not only after you re-pick them in settings.",
        ]
    },
    /* ENGINE 13 SHIPPED at v623 (the note below is the pre-ship measurement, kept for its numbers).
       ⚠ (was: "gated but not shipped — ENGINE_V is 12")
       It is registered anyway because gates/generator.mjs checks 20 and 23 require the registry to
       cover the shipped engine THE MOMENT ENGINE_V moves, and a behaviour gated on an engine with no
       entry is a half-made change that looks complete until the flag flips. Verified by setting
       ENGINE_V to 13 and running the suite: progcode 8/8 (the code format carries 13 without
       widening), enginefuzz 8/8, changelogcap 11/11, and generator 24/26 — the two failures being
       exactly these registry checks, which this entry answers.
       ⚠ IT IS NOT READY TO SHIP, and the reason is in the numbers. A/B at build 622 confined the whole
       effect to the named-program bucket (the app-designed splits moved no ceiling at all, which is
       what the change intends), but inside that bucket muscle-weeks at zero rose 6,738 → 7,847 and
       side delts at zero rose 12 → 132: pinning the canonical barbell lifts costs more session time
       than the accessory variants it replaces, and the coverage floors cannot recover the difference.
       frontOverMRV 315 → 486 is the one to treat as blocking — volume above maximum recoverable is an
       overreach risk, not a question of how faithful to a published program the app should be.
       ⚠ THOSE FIGURES ARE FROM THE 9,072-CONFIG SWEEP AND MUST BE RE-MEASURED. base.json is now 18,144
       configs (the equipment kits joined it), so the wording above cannot be quoted to a lifter until
       it is re-run — a limited kit is exactly where pinning a barbell lift bites hardest, and that
       population was not in the sweep these numbers came from. */
    13: {
        label: "Programs based on published routines now run the lifts those routines are built on",
        changes: [
            "5/3/1, the GZCL programs, the Texas Method and their relatives are built on specific barbell lifts \\u2014 the whole method is adding weight to the same movement week after week. Picking one of these from the program builder gave you the right day structure but let the app choose each day's main lift, so the deadlift day was often a Romanian deadlift and the bench day an incline press. Chosen from the gallery, the same program was correct. Two different programs under one name.",
            "The main lift is now fixed for these programs whichever way you reach them, and the percentage scheme they are named for comes with them.",
            "Nothing changed for any program the app designs itself, and programs you have already saved keep the engine they were built with.",
        ]
    },
    /* ⚠ THIS ENTRY DESCRIBES THE `emptiesPart` GUARD IN fitSessionTime, WHICH IS STILL MARKED A
       PROTOTYPE THERE. It is registered because ENGINE_V is 12 and gates/generator.mjs checks 20 and
       23 require the registry to cover the shipped engine — an engine whose behaviour is not
       described to the lifter is a defect whatever its merits. Registering it is not a decision to
       ship it: if ENGINE_V goes back to 11 this entry is harmless, and if the guard is reworked the
       wording below must be re-measured before it is trusted.
       The numbers quoted are from an A/B of engine 11 against 12 at build 622, 9,072 configs, taken
       AFTER the day-count duplication in _measure.mjs was fixed — the pre-fix sweep counted
       fixed-schedule splits five times each and every ceiling it ever reported was inflated. */
    12: {
        label: "The clock stops emptying a muscle when it could take the set from somewhere else",
        changes: [
            "When a session runs over its time budget the app removes work until it fits. It ranked what to remove by what each cut would cost, but it had no way to see the difference between taking a muscle from six sets to three and taking it from three sets to nothing. Both read as \\u201cthis muscle is under-trained\\u201d, so emptying one entirely was sometimes ranked among the safest cuts available.",
            "It now prices emptying a muscle separately and takes it last. Across every program the app can build, muscle-weeks trained not at all fell by 564; most of those muscles now sit below the ideal amount instead, which is the same trade the last change made \\u2014 a little of something beats none of it. No program lost a muscle that was previously trained properly.",
            "The programs based on published routines \\u2014 5/3/1, the GZCL family, Texas Method, PHUL, PHAT \\u2014 were measured separately from the ones the app designs itself, because a published program's shape is not the app's choice. Both improved.",
            "Programs you have already saved keep the engine they were built with.",
        ]
    },
    11: {
        label: "Your training days are arranged so back-to-back sessions clash less",
        changes: [
            "The app arranges which day of the week each session lands on, and it was solving the wrong problem: it spread overlap evenly around a ring of days as though every session were the same distance apart. Your week has rest days in it. On a five-day plan only three of the five pairs are genuinely back-to-back, and which two sessions landed next to each other was left to chance.",
            "It now minimises the worst back-to-back clash specifically \u2014 the single morning you would train a muscle that is still sore \u2014 rather than an average nobody experiences. Across every program the app can build, the volume shared by consecutive days fell by nearly half at the median, and the worst case on a split routine dropped from 28 shared sets to 15.",
            "No exercise selection changed and no muscle gets more or less work. Only the order of your week moved. Programs you have already saved keep the schedule they were built with.",
        ]
    },
    10: {
        label: "Sessions priced honestly, and shoulders finally get trained",
        changes: [
            "The app decided whether an exercise fitted your session using a cost that left out setup, walking to the machine and loading the bar \u2014 while the duration it SHOWED you included all three. So it packed sessions against a budget it was not really keeping, and the first thing squeezed out was the small isolation work at the end.",
            "Side and rear delts were the casualty: about one program in ten trained your side delts not at all, and nothing in the app could see it because they are a part of the shoulder rather than a muscle of their own. That is now down to roughly one in a thousand.",
            "Exercises are now priced the way sessions are actually timed. Across every program the app can build, muscles trained not at all dropped by 7,630 muscle-weeks. Some of those now sit below the ideal amount rather than at zero, which is the trade \u2014 a little of something beats none of it \u2014 and no session got longer.",
        ]
    },
    8: {
        label: "Competing needs are settled by one rule",
        changes: [
            "When two parts of your program both needed work and there was room for only one, whichever check happened to run first simply won. A muscle getting no work at all could lose a slot to a muscle that was already trained and merely wanted a little more.",
            "There is now one rule: a muscle with nothing beats a muscle that is short, which beats a preference. Everything states what it needs and one decision point settles it.",
            "Focusing a muscle also changed. It used to make that muscle win more contests for a slot, and the slots it won were isolation work \u2014 so your presses got traded for raises and focusing shoulders could leave you with LESS shoulder work than not focusing at all. Focus now asks for an extra slot outright, and loses to any muscle that is not yet trained enough.",
        ]
    },
    9: {
        label: "Reducing a muscle now means less, not none",
        changes: [
            "Asking for less work on a muscle used to make it lose out on exercises rather than simply doing fewer of them \u2014 and in about one program in four it dropped that muscle from the week entirely. It could also make your week LONGER overall, because the freed time quietly went to other muscles.",
            "A reduced muscle is now held to a light maintenance amount: fewer sets than normal, but never nothing. Time freed up is only used if something else genuinely needs it, so asking for less work now actually gives you less work.",
        ]
    },
    7: {
        label: "Short sessions stay short",
        changes: [
            "A twenty-minute session was allowed to grow to six exercises while the app told you to expect five \u2014 a number that was never true, and was actually higher than the one shown for a forty-minute session.",
            "Short sessions are now held to what fits the time you gave, and the exercise count shown while you pick a session length is the count you will actually get.",
        ]
    },
    6: {
        label: "Every week trains every movement",
        changes: [
            "About one program in ten contained no pulling work at all \u2014 no rows, no pulldowns, nothing for your back \u2014 for the entire week. It was worst on short sessions, and it happened even with a full gym available.",
            "When there is no room to add a movement, the app now trades the least necessary exercise in your week for one that fills the gap, rather than leaving the gap. Your main lift is never the one traded, and a session never gets longer.",
        ]
    },
    5: {
        label: "Volume decided when the plan is written",
        changes: [
            "For a muscle you train on back-to-back days, spare volume is removed while the program is being built instead of a set being trimmed from most sessions. The plan you read is the plan you train.",
            "Only volume the app already treated as expendable is removed, and never below what a muscle needs to grow. Your main lift and anything you asked for more of are untouched.",
        ]
    },
    4: {
        label: "Loadable main lifts",
        changes: [
            "Strength and peak blocks build on a barbell lift you can add weight to, instead of whichever lift suited muscle growth best.",
            "Trimming a session to fit the clock no longer drops your last side- or rear-delt movement.",
            "Filling a muscle's remaining volume avoids repeating a movement pattern the day already has.",
        ]
    },
    3: { label: "Lengthened-position coverage", changes: ["Each muscle gets at least some work in a stretched position each week."] },
    2: { label: "Baseline", changes: [] }
};

const engineInfo = (v) => ENGINES[v] || { label: `Engine ${v}`, changes: [] };

const EQUIP_IMPLIES = { inclinebench: ["bench"], declinebench: ["bench"] };

function expandEquipment(list) {
    const out = new Set(list || []);
    for (const id of [...out])
        (EQUIP_IMPLIES[id] || []).forEach(x => out.add(x));
    /* A barbell implies a rack unless the gym says "No squat rack" — see the note on EQUIPMENT (02-static-data). */
    if (out.has("barbell") && !out.has("no-rack"))
        out.add("rack");
    return out;
}

setShellEquipmentExpander(expandEquipment);

export { ALL_EQUIP_IDS, ENGINES, ENGINE_V, EQUIPMENT, EQUIP_IMPLIES, EXERCISES, EXP, EX_BY_ID, PATTERN_BY_ID, RAW, SESSIONS, SPLITS, TEMPLATES, engineInfo, expandEquipment };
