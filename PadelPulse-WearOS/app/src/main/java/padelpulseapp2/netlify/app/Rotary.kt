package padelpulseapp2.netlify.app

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import kotlinx.coroutines.launch

/**
 * Desplaza la lista con la corona o el bisel giratorio.
 *
 * Es requisito de calidad de Wear OS y esta version de ScalingLazyColumn no lo
 * trae de serie. Los eventos de giro solo llegan al nodo que tiene el foco, asi
 * que la lista lo pide al aparecer: si hay dos a la vez -durante el Crossfade
 * entre pantallas-, se lo queda la ultima, que es la que entra.
 */
@Composable
fun Modifier.rotaryScroll(state: ScalingLazyListState): Modifier {
    val focusRequester = remember { FocusRequester() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    return this
        .onRotaryScrollEvent { event ->
            scope.launch { state.scrollBy(event.verticalScrollPixels) }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}
