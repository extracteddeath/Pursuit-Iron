package io.github.extracteddeath.pursuitiron

/** Original curated display names; IDs remain the sole training identity. */
private val kotlinExerciseNames=mapOf(
    "barbell_bench" to "Barbell Bench Press",
    "paused_bench" to "Paused Bench Press",
    "back_squat" to "Back Squat",
    "barbell_ohp" to "Barbell Overhead Press",
    "incline_db_press" to "Incline Dumbbell Press",
    "one_arm_db_row" to "One-Arm Dumbbell Row",
    "db_lateral_raise" to "Dumbbell Lateral Raise",
    "db_curl" to "Dumbbell Curl",
    "db_overhead_triceps" to "Dumbbell Overhead Triceps Extension",
    "db_split_squat" to "Dumbbell Split Squat",
    "db_rdl" to "Dumbbell Romanian Deadlift",
    "db_calf_raise" to "Dumbbell Calf Raise",
    "bodyweight_crunch" to "Bodyweight Crunch",
    "barbell_row" to "Barbell Row",
    "rdl" to "Romanian Deadlift",
    "conventional_deadlift" to "Conventional Deadlift",
    "db_rear_delt_raise" to "Dumbbell Rear-Delt Raise",
    "pullup" to "Pull-Up",
    "leg_extension" to "Leg Extension",
    "seated_leg_curl" to "Seated Leg Curl",
    "split-squat" to "Two-Dumbbell Split Squat",
    "step-up" to "Dumbbell Step-Up",
    "box-squat" to "Barbell Box Squat",
    "cyclist-squat" to "Barbell Cyclist Squat",
    "zercher-squat" to "Zercher Squat",
    "pistol-squat" to "Pistol Squat",
    "hip-thrust" to "Barbell Hip Thrust",
    "smith-hip-thrust" to "Smith Machine Hip Thrust",
    "frog-pump" to "Frog Pump",
    "stiff-deadlift" to "Stiff-Leg Barbell Deadlift",
    "slider-curl" to "Slider Leg Curl",
    "drag-curl" to "Barbell Drag Curl",
    "pinwheel-curl" to "Pinwheel Dumbbell Curl",
    "jm-press" to "JM Press",
    "pjr-pullover" to "EZ-Bar PJR Pullover",
    "california-press" to "EZ-Bar California Press",
    "smith-ohp" to "Smith Machine Shoulder Press",
    "push-press" to "Barbell Push Press",
    "z-press" to "Barbell Z Press",
    "bradford-press" to "Barbell Bradford Press",
    "dead-bug" to "Dead Bug",
    "mountain-climber" to "Mountain Climber",
    "ab-wheel" to "Ab Wheel Rollout",
    "single-cable-curl" to "Single-Arm Cable Curl",
    "cable-tri-kickback" to "Cable Triceps Kickback",
    "incline-cable-press" to "Incline Cable Press",
    "machine-dip" to "Machine Chest Dip",
    "underhand-pushdown" to "Reverse-Grip Triceps Pushdown",
    "cable-crossover" to "Cable Crossover",
    "single-cable-raise" to "Single-Arm Cable Lateral Raise"
)

internal fun kotlinExerciseLabel(id:String):String=kotlinExerciseNames[id]
    ?:id.replace('_',' ').replace('-',' ').split(' ')
        .filter{it.isNotBlank()}.joinToString(" "){word->
            word.replaceFirstChar{it.uppercase()}
        }
