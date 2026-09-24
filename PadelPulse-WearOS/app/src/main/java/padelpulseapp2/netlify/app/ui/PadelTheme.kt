package padelpulseapp2.netlify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text
import padelpulseapp2.netlify.app.R

/**
 * Colores de cada tema, sacados tal cual de THEMES del movil (code.html) para
 * que reloj y movil se vean como la misma app. Lo unico que no se copia es el
 * fondo: en el reloj es negro puro, que en OLED apaga los pixeles y gasta
 * menos bateria. Por eso las tarjetas usan el "surface2" del movil -un punto
 * mas claro que su "surface"-: sobre negro, el surface del movil casi no se ve.
 */
class PPPalette(
    surface: Long, surface2: Long, border: Long, text: Long,
    muted: Long, error: Long, onAccent: Long
) {
    val text = Color(text)
    val surface = Color(surface2)
    val surfaceHigh = lerp(Color(surface2), this.text, 0.07f)
    val line = lerp(Color(border), this.text, 0.06f)
    val textDim = lerp(this.text, Color.Black, 0.42f)
    val textMuted = lerp(Color(muted), this.text, 0.08f)
    val danger = Color(error)
    val onAccent = Color(onAccent)

    companion object {
        private val all = mapOf(
            "neon" to PPPalette(0xFF101010, 0xFF181818, 0xFF1E1E1E, 0xFFF0F0F0, 0xFF555555, 0xFFFF716C, 0xFF003A1F),
            "fuego" to PPPalette(0xFF160800, 0xFF1F0C00, 0xFF2A1200, 0xFFFFF5EE, 0xFF7A4020, 0xFFFF4444, 0xFF1A0000),
            "hielo" to PPPalette(0xFF060F1C, 0xFF0C1828, 0xFF0E2040, 0xFFE8F4FF, 0xFF3A6080, 0xFFFF6B8A, 0xFF001A26),
            "clasico" to PPPalette(0xFF151A00, 0xFF1E2500, 0xFF2A3400, 0xFFF5F0E0, 0xFF6A7040, 0xFFFF6060, 0xFF1A2000),
            "noche" to PPPalette(0xFF10101A, 0xFF18182A, 0xFF20203A, 0xFFE8E8FF, 0xFF4A4A70, 0xFFFF5566, 0xFF0A0020),
            "oro" to PPPalette(0xFF120E00, 0xFF1A1400, 0xFF2A2000, 0xFFFFF8E0, 0xFF806A20, 0xFFFF5555, 0xFF1A1000)
        )

        fun of(theme: String): PPPalette = all[theme.lowercase()] ?: all.getValue("neon")
    }
}

/** Lexend, la letra del movil. Solo los tres pesos que usa el reloj. */
val Lexend = FontFamily(
    Font(R.font.lexend_400, FontWeight.Normal),
    Font(R.font.lexend_700, FontWeight.Bold),
    Font(R.font.lexend_900, FontWeight.Black)
)

/**
 * Piezas visuales compartidas por todas las pantallas del reloj.
 * Objetivo: una sola escala tipografica y un solo lenguaje de tarjetas, para que
 * la app no parezca seis pantallas distintas pegadas.
 */
object PP {

    /**
     * De donde sale el tema activo. Lo pone MainActivity apuntando al tema del
     * marcador, que es estado observable: al cambiar de tema en el movil o en
     * el reloj, todo lo que lee estos colores se repinta solo.
     */
    var themeSource: () -> String = { "neon" }
    val palette: PPPalette get() = PPPalette.of(themeSource())

    // Negro puro de fondo: en pantallas OLED gasta menos bateria
    val Bg = Color(0xFF000000)
    val Surface: Color get() = palette.surface
    val SurfaceHigh: Color get() = palette.surfaceHigh
    val Line: Color get() = palette.line
    val TextBright: Color get() = palette.text
    val TextDim: Color get() = palette.textDim
    val TextMuted: Color get() = palette.textMuted
    val Danger: Color get() = palette.danger
    /** Texto encima del color del tema, como los botones A del movil. */
    val OnAccent: Color get() = palette.onAccent
    val Warn = Color(0xFFFFB020)

    // Escala tipografica: 5 tamaños, ni uno mas
    val Display = 34.sp
    val Title = 15.sp
    val Body = 12.sp
    val Label = 10.sp
    val Micro = 8.sp

    val CardShape = RoundedCornerShape(16.dp)
    val PillShape = RoundedCornerShape(50)
}

/** Etiqueta en mayusculas con tracking: el patron de titulillo de toda la app. */
@Composable
fun PPLabel(
    text: String,
    color: Color = PP.TextDim,
    size: androidx.compose.ui.unit.TextUnit = PP.Micro,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/** Tarjeta base. Todo lo que agrupa informacion usa esta, sin excepciones. */
@Composable
fun PPCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    padding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(PP.CardShape)
            .background(PP.Surface)
            .then(
                if (accent != null) Modifier.border(1.dp, accent.copy(alpha = 0.35f), PP.CardShape)
                else Modifier
            )
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * Pildora de estado del enlace con el movil. Se ve en todas las pantallas:
 * en pista hay que saber de un vistazo si el marcador esta viajando o no.
 */
@Composable
fun PPStatusPill(
    label: String,
    color: Color,
    pulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(PP.PillShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), PP.PillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (pulsing) color else color.copy(alpha = 0.6f))
        )
        PPLabel(label, color = color, size = PP.Micro)
    }
}

/** Degradado sutil detras del marcador, con el acento del tema activo. */
fun accentGlow(accent: Color): Brush = Brush.verticalGradient(
    listOf(accent.copy(alpha = 0.14f), Color.Transparent)
)
