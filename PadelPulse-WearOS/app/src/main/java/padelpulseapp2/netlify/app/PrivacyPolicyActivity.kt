package padelpulseapp2.netlify.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import padelpulseapp2.netlify.app.ui.Lexend
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPChip
import padelpulseapp2.netlify.app.ui.PPLabel

/**
 * Politica de privacidad dentro del reloj, desde Ajustes y desde el aviso
 * previo al permiso del pulso (solo hasta Wear OS 5). Play exige poder verla
 * dentro de la app.
 *
 * El texto es el resumen de la seccion de salud de la politica publicada; el
 * QR lleva a la politica completa, porque en el reloj no hay navegador.
 */
class PrivacyPolicyActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PrivacyPolicyScreen(lang = MainActivity.gameEngine?.lang ?: "es") { finish() } }
    }

    companion object {
        const val URL = "https://padelpulselive.com/politica-de-privacidad/"
    }
}

@Composable
fun PrivacyPolicyScreen(lang: String, onClose: () -> Unit) {
    val es = lang == "es"
    val listState = rememberScalingLazyListState()
    val accent = ThemeUtils.getColor(PP.themeSource())

    MaterialTheme(typography = Typography(defaultFontFamily = Lexend)) {
        Scaffold(
            timeText = { TimeText() },
            positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
        ) {
            ScalingLazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = roundSafePadding()
            ) {
                item {
                    PPLabel(if (es) "POLÍTICA DE PRIVACIDAD" else "PRIVACY POLICY", color = accent, size = PP.Label)
                }
                item { PolicyText(if (es) "Datos de salud" else "Health data", bold = true) }
                item {
                    PolicyText(
                        if (es) "PadelPulse Live lee el pulso del sensor del reloj solo para mostrártelo en el marcador mientras juegas un partido de pádel."
                        else "PadelPulse Live reads your heart rate from the watch sensor only to show it on the scoreboard while you play a padel match."
                    )
                }
                item {
                    PolicyText(
                        if (es) "Con los pasos estima las calorías y la distancia del partido. No es una app médica y no valora tu salud."
                        else "It uses your steps to estimate the calories and distance of the match. It is not a medical app and does not assess your health."
                    )
                }
                item {
                    PolicyText(
                        if (es) "El dato se queda en tus dispositivos. Solo se guarda en tu cuenta, con el historial del partido, si inicias sesión y sincronizas. No se comparte con terceros ni se usa para publicidad."
                        else "The data stays on your devices. It is only stored in your account, with the match history, if you sign in and sync. It is not shared with third parties or used for advertising."
                    )
                }
                item {
                    PolicyText(
                        if (es) "Puedes retirar el permiso cuando quieras en los ajustes del reloj; el marcador sigue funcionando."
                        else "You can revoke the permission at any time in the watch settings; the scoreboard keeps working."
                    )
                }
                item {
                    PolicyText(if (es) "Política completa:" else "Full policy:", bold = true)
                }
                item {
                    QrCode(PrivacyPolicyActivity.URL, Modifier.fillMaxWidth(0.6f).padding(vertical = 4.dp))
                }
                item {
                    PolicyText("padelpulselive.com/politica-de-privacidad")
                }
                item {
                    PPChip(
                        if (es) "‹ VOLVER" else "‹ BACK",
                        onClick = onClose,
                        modifier = Modifier.fillMaxWidth(0.94f).padding(top = 6.dp),
                        content = accent, center = true
                    )
                }
            }
        }
    }
}

@Composable
internal fun PolicyText(text: String, bold: Boolean = false) {
    Text(
        text,
        color = if (bold) PP.TextBright else PP.TextDim,
        fontSize = if (bold) PP.Body else PP.Micro,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 3.dp)
    )
}

/**
 * Aviso previo al permiso del pulso. Se ve una vez, antes del dialogo del
 * sistema: dice que dato se lee, para que y donde se guarda, y deja elegir.
 * Es la "divulgacion destacada" que pide la politica de permisos de salud.
 */
@Composable
fun HealthDisclosureScreen(
    lang: String,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onPrivacy: () -> Unit,
    onAnswer: (Boolean) -> Unit
) {
    val es = lang == "es"
    val accent = ThemeUtils.getColor(PP.themeSource())
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item { Text("\u2665", color = accent, fontSize = PP.Title) }
        item { PPLabel(if (es) "PULSO EN EL MARCADOR" else "HEART RATE ON THE SCOREBOARD", color = accent, size = PP.Label) }
        item {
            PolicyText(
                if (es) "PadelPulse Live puede leer tu pulso del sensor del reloj para mostrártelo en el marcador mientras juegas."
                else "PadelPulse Live can read your heart rate from the watch sensor to show it on the scoreboard while you play."
            )
        }
        item {
            PolicyText(
                if (es) "No es un uso médico. Se queda en tus dispositivos y solo se guarda en tu cuenta si sincronizas tu historial."
                else "It is not for medical use. It stays on your devices and is only stored in your account if you sync your history."
            )
        }
        item {
            PPChip(
                if (es) "PERMITIR" else "ALLOW",
                onClick = { onAnswer(true) },
                modifier = Modifier.fillMaxWidth(0.94f).padding(top = 4.dp),
                background = accent, content = PP.OnAccent, weight = FontWeight.Black, center = true
            )
        }
        item {
            PPChip(
                if (es) "AHORA NO" else "NOT NOW",
                onClick = { onAnswer(false) },
                modifier = Modifier.fillMaxWidth(0.94f).padding(top = 2.dp),
                content = PP.TextDim, center = true
            )
        }
        item {
            PPChip(
                if (es) "Política de privacidad" else "Privacy policy",
                onClick = onPrivacy,
                modifier = Modifier.fillMaxWidth(0.94f).padding(top = 2.dp),
                content = accent, weight = FontWeight.Normal, center = true
            )
        }
    }
}
