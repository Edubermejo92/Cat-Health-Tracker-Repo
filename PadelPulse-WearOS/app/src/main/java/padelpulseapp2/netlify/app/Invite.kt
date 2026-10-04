package padelpulseapp2.netlify.app

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.*
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPChip
import padelpulseapp2.netlify.app.ui.PPLabel

/**
 * Enlace para invitar a un amigo.
 *
 * La ficha de Play. El movil manda la suya con los ajustes, asi que el QR del
 * reloj y el mensaje del movil llevan siempre al mismo sitio.
 */
object InviteLink {

    private const val PREFS = "padel_prefs"
    private const val KEY = "invite_url"
    const val DEFAULT = "https://play.google.com/store/apps/details?id=padelpulseapp2.netlify.app"

    var url by mutableStateOf(DEFAULT)
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, DEFAULT) ?: DEFAULT
        // Las versiones anteriores guardaban el enlace de la prueba cerrada
        url = if (saved.contains("/apps/testing/")) DEFAULT else saved
    }

    fun applyFromPhone(context: Context, value: String) {
        // Solo enlaces de Play: el QR lo va a escanear otra persona
        if (!value.startsWith("https://play.google.com/") || value.contains("/apps/testing/") || value == url) return
        url = value
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, value).apply()
    }
}

/**
 * Codifica [text] en QR. Sin margen: el borde blanco lo pone quien lo pinta.
 * Correccion L: en una pantalla que emite luz no hay manchas que corregir, y
 * con menos redundancia los modulos salen mas grandes y se lee mejor de lejos.
 */
fun qrMatrix(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(
            EncodeHintType.MARGIN to 0,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L
        )
    )
}.getOrNull()

/**
 * QR negro sobre blanco, con dos modulos de margen. Los lectores necesitan ese
 * contraste -un QR en el color del tema no lo lee cualquier camara-, y en un
 * reloj se escanea desde cerca, asi que se pinta tan grande como quepa.
 */
@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qrMatrix(text) }
    Canvas(modifier = modifier.aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(Color.White)) {
        val m = matrix ?: return@Canvas
        val quiet = 2
        val cells = m.width + quiet * 2
        val cell = size.minDimension / cells
        // Un pixel de mas en cada modulo para que no queden rayas entre ellos
        val px = Size(cell + 0.5f, cell + 0.5f)
        for (y in 0 until m.height) {
            for (x in 0 until m.width) {
                if (m.get(x, y)) {
                    drawRect(Color.Black, Offset((x + quiet) * cell, (y + quiet) * cell), px)
                }
            }
        }
    }
}

/**
 * Invita a un amigo: el QR para que lo escanee con su movil ahi mismo, en la
 * pista, y un boton que abre la hoja de compartir del movil vinculado con el
 * mensaje de siempre -WhatsApp incluido-.
 */
@Composable
fun InviteScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: ScalingLazyListState,
    onBack: () -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"
    val haptic = LocalHapticFeedback.current
    var sent by remember { mutableStateOf(false) }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            PPLabel(if (es) "INVITA A UN AMIGO" else "INVITE A FRIEND", color = accent, size = PP.Label)
        }
        item {
            // Cuadrado inscrito en la esfera: el 70 % del ancho cabe entero
            // incluso cuando la lista lo lleva al centro.
            QrCode(InviteLink.url, Modifier.fillMaxWidth(0.62f).padding(vertical = 4.dp))
        }
        item {
            Text(
                if (es) "Que lo escanee con la camara de su movil"
                else "Scan it with their phone camera",
                color = PP.TextDim, fontSize = PP.Micro,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.86f)
            )
        }
        item {
            val paired = PhoneLink.paired
            PPChip(
                when {
                    !paired -> if (es) "VINCULA EL MOVIL" else "LINK YOUR PHONE"
                    sent -> if (es) "MIRA EL MOVIL ✓" else "CHECK YOUR PHONE ✓"
                    else -> if (es) "ENVIAR DESDE EL MOVIL" else "SEND FROM PHONE"
                },
                onClick = {
                    if (activity.sendInviteToPhone()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        sent = true
                    }
                },
                enabled = paired,
                modifier = Modifier.fillMaxWidth(0.94f).padding(top = 6.dp),
                background = accent, content = PP.OnAccent,
                icon = "📲", fontSize = PP.Micro, weight = FontWeight.Black
            )
        }
        item { BackChip(engine, accent, onBack) }
    }
}
