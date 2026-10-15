package padelpulseapp2.netlify.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPChip

/**
 * Salir de la app. Pide un segundo toque en 3 s para no cerrarla sin querer
 * con la muñeca en pleno partido. El partido queda guardado: al volver se
 * ofrece continuarlo.
 */
@Composable
fun ExitButton(engine: GameEngine, activity: MainActivity) {
    val es = engine.lang == "es"
    var armed by remember { mutableStateOf(false) }
    LaunchedEffect(armed) {
        if (armed) {
            delay(3000)
            armed = false
        }
    }
    PPChip(
        if (armed) (if (es) "TOCA OTRA VEZ PARA SALIR" else "TAP AGAIN TO EXIT")
        else (if (es) "⏻ SALIR DE LA APP" else "⏻ EXIT APP"),
        onClick = { if (armed) activity.exitApp() else armed = true },
        modifier = Modifier.fillMaxWidth(0.94f).padding(top = 2.dp),
        background = if (armed) PP.Danger else PP.Surface,
        content = if (armed) Color.White else PP.Danger,
        fontSize = PP.Micro, weight = FontWeight.Black, center = true
    )
}
