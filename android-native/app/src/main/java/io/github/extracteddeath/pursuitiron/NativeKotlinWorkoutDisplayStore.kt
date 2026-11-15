package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One process-owned display source. No journal, approval or history access. */
internal class NativeKotlinWorkoutDisplayStore(context:Context) {
    private val preferences=context.applicationContext.getSharedPreferences(
        NativeKotlinWorkoutDisplaySettings.FILE,Context.MODE_PRIVATE)
    private fun read()=runCatching {
        NativeKotlinWorkoutDisplaySettings.decode(preferences.all)
    }.getOrDefault(NativeKotlinWorkoutDisplaySettings())
    private val current=MutableStateFlow(read())
    val state=current.asStateFlow()
    // Keep a strong reference: SharedPreferences holds listeners weakly.
    // Lifetime is the application's process, not a disposable workout screen.
    private val listener=SharedPreferences.OnSharedPreferenceChangeListener { _,_ ->
        current.value=read()
    }
    init {preferences.registerOnSharedPreferenceChangeListener(listener)}

    fun reference(mode:WorkoutReferenceMode) {
        preferences.edit().putString(NativeKotlinWorkoutDisplaySettings.REFERENCE,mode.name).apply()
        current.value=read()
    }
    fun layout(value:WorkoutStartLayout) {
        preferences.edit().putString(NativeKotlinWorkoutDisplaySettings.LAYOUT,value.name).apply()
        current.value=read()
    }
    fun awake(value:Boolean) {
        preferences.edit().putBoolean(NativeKotlinWorkoutDisplaySettings.AWAKE,value).apply()
        current.value=read()
    }
}

@Composable
internal fun rememberNativeWorkoutDisplayStore():NativeKotlinWorkoutDisplayStore {
    val app=LocalContext.current.applicationContext as PursuitIronApplication
    return remember(app){app.workoutDisplay}
}
