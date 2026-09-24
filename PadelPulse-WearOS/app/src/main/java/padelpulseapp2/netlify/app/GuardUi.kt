package padelpulseapp2.netlify.app

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPCard
import padelpulseapp2.netlify.app.ui.PPLabel

/**
 * Texto y color del bloqueo de la deteccion automatica. [short] es para la
 * banda de abajo del marcador, donde el bisel deja poco sitio.
 */
@Composable
fun guardStatus(engine: GameEngine, accent: Color, short: Boolean = false): Pair<String, Color> {
    val es = engine.lang == "es"
    fun t(esLong: String, enLong: String, esShort: String, enShort: String) =
        if (short) (if (es) esShort else enShort) else (if (es) esLong else enLong)
    return when {
        !WorkoutGuard.enabled -> t(
            "Detección automática libre", "Auto detection allowed",
            "BLOQUEAR DETECCIÓN", "BLOCK AUTO DETECT"
        ) to PP.TextDim
        WorkoutGuard.state == WorkoutGuard.State.ACTIVE -> t(
            "🛡 Bloqueada: el reloj sabe que estás jugando", "🛡 Blocked: the watch knows you are playing",
            "🛡 DETECCIÓN BLOQUEADA", "🛡 DETECTION BLOCKED"
        ) to accent
        WorkoutGuard.state == WorkoutGuard.State.STARTING -> t(
            "🛡 Bloqueando…", "🛡 Blocking…", "🛡 BLOQUEANDO…", "🛡 BLOCKING…"
        ) to PP.Warn
        WorkoutGuard.state == WorkoutGuard.State.UNSUPPORTED ||
            WorkoutGuard.state == WorkoutGuard.State.FAILED -> t(
            "⚠ Este reloj no deja bloquearla: apágala en su app de salud",
            "⚠ This watch won't allow it: turn it off in its health app",
            "⚠ SIN BLOQUEO", "⚠ NOT BLOCKED"
        ) to PP.Warn
        // Activado pero sin partido corriendo: se pondra solo al empezar
        else -> t(
            "🛡 Se bloquea sola al empezar el partido", "🛡 Blocks itself when the match starts",
            "🛡 BLOQUEO AL JUGAR", "🛡 BLOCKS WHEN PLAYING"
        ) to accent
    }
}

/**
 * Tarjeta de la deteccion automatica, para Controles y Ajustes: el
 * interruptor, en que estado esta y, si el reloj tiene Samsung Health, un
 * acceso directo para apagarla alli del todo.
 */
@Composable
fun DetectionCard(engine: GameEngine, activity: MainActivity, accent: Color) {
    val es = engine.lang == "es"
    val (status, color) = guardStatus(engine, accent)
    val samsung = remember { WorkoutGuard.samsungHealthIntent(activity) }

    PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
        PPLabel(if (es) "DETECCIÓN AUTOMÁTICA" else "AUTO WORKOUT DETECTION", size = PP.Micro)
        Spacer(Modifier.height(2.dp))
        Text(
            status, color = color, fontSize = PP.Micro, fontWeight = FontWeight.Bold,
            maxLines = 3, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        ToggleRow(
            if (es) "Bloquear al jugar" else "Block while playing",
            WorkoutGuard.enabled, accent
        ) { WorkoutGuard.setEnabled(activity, it, activity.timerRunning) }
        if (samsung != null) {
            Spacer(Modifier.height(4.dp))
            CompactChip(
                onClick = { runCatching { activity.startActivity(samsung) } },
                label = { Text("Samsung Health", fontSize = PP.Micro, maxLines = 1) },
                colors = ChipDefaults.primaryChipColors(backgroundColor = PP.SurfaceHigh, contentColor = accent)
            )
            Text(
                if (es) "Para apagarla siempre: Ajustes › Detectar entrenamientos"
                else "To turn it off for good: Settings › Workout detection",
                color = PP.TextMuted, fontSize = PP.Micro, maxLines = 3, textAlign = TextAlign.Center
            )
        }
    }
}

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
    Button(
        onClick = { if (armed) activity.exitApp() else armed = true },
        colors = ButtonDefaults.buttonColors(backgroundColor = if (armed) PP.Danger else PP.Surface),
        modifier = Modifier.fillMaxWidth(0.94f).height(34.dp)
    ) {
        Text(
            if (armed) (if (es) "TOCA OTRA VEZ PARA SALIR" else "TAP AGAIN TO EXIT")
            else (if (es) "⏻ SALIR DE LA APP" else "⏻ EXIT APP"),
            color = if (armed) Color.White else PP.Danger,
            fontSize = PP.Micro, fontWeight = FontWeight.Black, maxLines = 1
        )
    }
}
