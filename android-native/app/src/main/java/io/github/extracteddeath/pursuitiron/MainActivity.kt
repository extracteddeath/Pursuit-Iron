package io.github.extracteddeath.pursuitiron

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val runtime=(application as PursuitIronApplication).nativeRuntime
        // Render before any asset I/O. The Application owns the worker/writer
        // so rotation cannot create competing file owners or sandbox leases.
        setContent {
            val state by runtime.state.collectAsStateWithLifecycle()
            val readOnlyVerifier=state.verifier
            PursuitIronTheme {
                NativePreviewApp(
                    schemaVerifier=readOnlyVerifier,
                    schemaVerifierLoading=state.loading,
                    syntheticOutlineReader = readOnlyVerifier,
                    activeProgramId=state.saved?.program?.id,
                    currentPlanSource=state.planSource,
                    canonicalStoreChangeKey=state.storeChangeKey,
                    onboarding=state,
                    onReviewProgram=runtime::review,
                    onSaveProgram=runtime::save,
                    onEditProgram=runtime::edit,
                    onStartWorkout={runtime.startWorkout()},
                    onPreviewShortSession=runtime::previewShorterSession,
                    onStartShortWorkout={runtime.startWorkout(it)},
                    onRecordSet=runtime::recordSet,
                    onCorrectSet=runtime::correctSet,
                    onSkipExercise=runtime::skipRemainingExercise,
                    onInspectMuscles=runtime::inspectWorkoutMuscles,
                    onSkipAffectedMuscle=runtime::skipAffectedMuscle,
                    onReduceAffectedMuscle=runtime::reduceAffectedMuscle,
                    onFinishWorkout=runtime::finishWorkout,
                    onCorrectHistory=runtime::correctHistory,
                    onReloadHistory=runtime::reloadHistory,
                    onOpenHistory=runtime::openHistory,
                    onHistoryPage=runtime::loadHistoryPage,
                    onHistoryList=runtime::showHistoryList,
                    onFilterHistory=runtime::filterHistory,
                    onHistoryCalendar=runtime::loadHistoryCalendar,
                    onExportBackup=runtime::exportBackup,
                    onResetBackupExport=runtime::resetBackupExportStatus
                )
            }
        }
        runtime.start()
    }
}

private val PursuitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(
        topStart = 32.dp, topEnd = 20.dp, bottomEnd = 32.dp, bottomStart = 32.dp
    )
)

@Composable
fun PursuitIronTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(),
        typography = Typography(), shapes = PursuitShapes, content = content)
}
