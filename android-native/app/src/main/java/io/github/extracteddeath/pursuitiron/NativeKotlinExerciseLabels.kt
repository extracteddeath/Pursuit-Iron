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
    "seated_leg_curl" to "Seated Leg Curl"
)

internal fun kotlinExerciseLabel(id:String):String=kotlinExerciseNames[id]
    ?:id.replace('_',' ').replace('-',' ').split(' ')
        .filter{it.isNotBlank()}.joinToString(" "){word->
            word.replaceFirstChar{it.uppercase()}
        }
