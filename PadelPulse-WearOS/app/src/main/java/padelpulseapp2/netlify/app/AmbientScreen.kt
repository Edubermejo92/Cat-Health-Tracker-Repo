package padelpulseapp2.netlify.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.TimeTextDefaults
import padelpulseapp2.netlify.app.ui.FitText
import padelpulseapp2.netlify.app.ui.Lexend

/*
 * Marcador en modo ambiente (muñeca bajada).
 *
 * Reglas de Wear OS para el ambiente en pantallas OLED:
 * - fondo negro y casi nada encendido: nada de rellenos, halos ni aros;
 * - sin color: gris y blanco apagado (algunas pantallas solo tienen 1 bit);
 * - nada fijo en el mismo pixel mucho rato: se mueve un poco cada minuto;
 * - se refresca una vez por minuto: el cronometro va en minutos, no en
 *   segundos.
 * Tocar la pantalla devuelve al marcador normal (lo hace el sistema).
 */

private val AMB_TEXT = Color(0xFFD8D8D8)
private val AMB_DIM = Color(0xFF8A8A8A)

@Composable
fun AmbientScreen(engine: GameEngine, activity: MainActivity) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val w = minOf(maxWidth, maxHeight)
        val density = LocalDensity.current
        fun sz(frac: Float): TextUnit = with(density) { (w * frac).toSp() }

        // Desplazamiento anti-quemado: hasta ±4 dp, distinto cada minuto
        val shift: Dp = if (activity.ambientBurnIn) ((activity.ambientTick % 5) - 2).dp * 2 else 0.dp
        val shiftY: Dp = if (activity.ambientBurnIn) (((activity.ambientTick / 5) % 5) - 2).dp * 2 else 0.dp

        // Solo minutos: asi no se repinta cada segundo
        val minutes by remember { derivedStateOf { activity.matchTimeSeconds / 60 } }

        TimeText(
            timeTextStyle = TimeTextDefaults.timeTextStyle(color = AMB_TEXT)
        )

        val showScore = engine.currentScreen == "score" || engine.currentScreen == "end"
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = shift, y = shiftY)
                .fillMaxWidth(0.8f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!showScore) FitText("PADELPULSE LIVE", AMB_DIM, sz(0.06f), FontWeight.Bold)
            else AmbientMatch(engine, activity, w, minutes) { sz(it) }
        }
    }
}

/** El partido en ambiente: fase y juegos, tanteo, quien saca, pulso y minutos. */
@Composable
private fun AmbientMatch(
    engine: GameEngine,
    activity: MainActivity,
    w: Dp,
    minutes: Int,
    sz: (Float) -> TextUnit
) {
            val es = engine.lang == "es"
            val tb = engine.isTb || engine.isSuperTbActive()
            val fase = when {
                engine.over -> if (es) "FIN" else "END"
                engine.isSuperTbActive() -> "SUPER TIE-BREAK"
                engine.isTb -> "TIE-BREAK"
                else -> "SET ${engine.setsA + engine.setsB + 1}"
            }
            FitText(
                "$fase  ·  ${engine.gamesA}-${engine.gamesB}",
                AMB_DIM, sz(0.055f), FontWeight.Bold
            )
            Spacer(Modifier.height(w * 0.02f))

            // Tanteo en grande, en trazo normal (no Black): menos pixeles encendidos
            Row(verticalAlignment = Alignment.CenterVertically) {
                AmbientScore(engine.getScoreStr("A"), engine.serving == "A", sz(0.2f), w)
                Text("  ", fontSize = sz(0.2f))
                AmbientScore(engine.getScoreStr("B"), engine.serving == "B", sz(0.2f), w)
            }

            // Quien saca, con palabras: en ambiente no hay color que lo diga
            val saca = if (engine.serving == "A") engine.nameA else engine.nameB
            if (!engine.over) {
                FitText(
                    (if (es) "SACA " else "SERVES ") + saca.uppercase(),
                    AMB_DIM, sz(0.045f), FontWeight.Normal
                )
            }
            if (engine.setScores.isNotEmpty() && !tb) {
                FitText(
                    engine.setScores.takeLast(3).joinToString(" · "),
                    AMB_DIM, sz(0.045f), FontWeight.Normal
                )
            }

            Spacer(Modifier.height(w * 0.04f))
            val linea = buildList {
                if (engine.heartRate > 0) add("♥ ${engine.heartRate}")
                if (activity.timerRunning || minutes > 0) add("$minutes MIN")
            }.joinToString("   ")
            if (linea.isNotEmpty()) FitText(linea, AMB_DIM, sz(0.05f), FontWeight.Normal)
}

/** Una cifra del tanteo; quien saca lleva un punto pequeño al lado, sin color. */
@Composable
private fun AmbientScore(value: String, serving: Boolean, size: TextUnit, w: Dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (serving) {
            Box(
                Modifier
                    .size(w * 0.025f)
                    .clip(CircleShape)
                    .background(AMB_TEXT)
            )
            Spacer(Modifier.width(w * 0.015f))
        }
        Text(
            value, color = AMB_TEXT, fontSize = size,
            fontWeight = FontWeight.Normal, fontFamily = Lexend, maxLines = 1
        )
    }
}
