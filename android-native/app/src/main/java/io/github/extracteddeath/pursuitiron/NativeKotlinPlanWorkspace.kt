package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** Keep the editor's composition and scroll owner when its outline moves. */
@Composable
internal fun NativeKotlinPlanWorkspace(
    showNavigator:Boolean,navigationKey:Pair<String,String>?,scrollState:ScrollState,
    navigator:@Composable ColumnScope.()->Unit,
    content:@Composable ColumnScope.(Boolean)->Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val twoPane=showNavigator &&
            nativePlanUsesTwoPanes(maxWidth.value,LocalDensity.current.fontScale)
        val outlineWidth=(maxWidth*0.28f).coerceIn(260.dp,320.dp)
        val navigationScroll=key(navigationKey) { rememberScrollState() }
        // The detail always has the same keyed parent. Moving the outline must
        // not recreate unsaved policy fields, dialogs or the detail's scroll.
        Row(Modifier.fillMaxSize()) {
            if(twoPane)key("plan-outline") {
                PursuitScreenContent(
                    modifier=Modifier.width(outlineWidth),
                    scrollState=navigationScroll,content=navigator
                )
            }
            key("plan-content") {
                PursuitScreenContent(modifier=Modifier.weight(1f),scrollState=scrollState) {
                    content(twoPane)
                }
            }
        }
    }
}
