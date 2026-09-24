package padelpulseapp2.netlify.app

import androidx.compose.animation.core.Spring
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.border
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Text
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.voice.VoiceReferee
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPLabel
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Marcador pensado para la esfera redonda.
 *
 * Tres paginas que se pasan deslizando, como las apps de entreno de Wear OS:
 *   0  marcador   lo unico que hace falta en pista: tocar una mitad suma punto
 *   1  controles  todo lo demas, en lista, con la corona
 *   2  salud      pulso, calorias, distancia y la pausa de sensores
 *
 * El marcador va primero a proposito. En la primera pagina, deslizar a la
 * derecha es el gesto del sistema para salir; si los controles fueran la
 * pagina 0, ese gesto pelearia con el pager.
 *
 * Todas las medidas son fracciones del ancho de pantalla (w) y se colocan desde
 * el centro, porque en redondo lo que decide si algo se corta es su distancia
 * al centro, no al borde de un rectangulo que no existe.
 */

private const val PAGES = 3

/** Borde inferior de la zona tocable y arranque de la banda de abajo. */
private const val BAND_TOP = 0.80f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScorePager(
    engine: GameEngine,
    activity: MainActivity,
    listState: ScalingLazyListState,
    onSettings: () -> Unit,
    onMode: () -> Unit,
    onEditName: (String) -> Unit,
    onPicker: (String) -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    val pager = rememberPagerState(pageCount = { PAGES })

    BoxWithConstraints(Modifier.fillMaxSize().background(PP.Bg)) {
        val w = minOf(maxWidth, maxHeight)
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ScoreDial(engine, activity, ui, accent, w)
                1 -> ControlsPage(engine, activity, ui, accent, listState, onSettings, onMode, onEditName, onPicker)
                else -> HealthDial(engine, activity, accent, w)
            }
        }
        PageDots(
            current = pager.currentPage,
            accent = accent,
            size = w * 0.026f,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = w * 0.035f)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Pagina 0 · marcador
// ─────────────────────────────────────────────────────────────────────

/** Amarillo pelota: marca quien saca sin depender del color del tema. */
private val BALL = Color(0xFFE8FF3A)

@Composable
private fun ScoreDial(engine: GameEngine, activity: MainActivity, ui: UIStrings, accent: Color, w: Dp) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    fun sz(frac: Float): TextUnit = with(density) { (w * frac).toSp() }
    val es = engine.lang == "es"
    val v = Translations.vd[engine.lang] ?: Translations.vd["es"]!!

    val servingA = engine.serving == "A"
    val colA = if (servingA) accent else PP.TextBright.copy(alpha = 0.9f)
    val colB = if (!servingA) accent else PP.TextBright.copy(alpha = 0.9f)
    val need = Math.ceil(engine.bestOf / 2.0).toInt()

    // Cada mitad se ilumina un instante al tocarla: en un reloj no hay otra
    // forma de saber que el toque ha entrado antes de que cambie el numero.
    val tapA = remember { MutableInteractionSource() }
    val tapB = remember { MutableInteractionSource() }
    val pressA by tapA.collectIsPressedAsState()
    val pressB by tapB.collectIsPressedAsState()
    val flashA by animateFloatAsState(if (pressA) 0.14f else 0f, tween(if (pressA) 60 else 260), label = "fa")
    val flashB by animateFloatAsState(if (pressB) 0.14f else 0f, tween(if (pressB) 60 else 260), label = "fb")

    fun point(team: String) {
        engine.addPoint(team)
        activity.onLocalScoreAction("point", team)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            val side = min(size.width, size.height)
            val c = center

            // Halo detras del tanteo de quien saca: se sabe de un vistazo, de lejos
            val gx = c.x + side * (if (servingA) -0.27f else 0.27f)
            val gy = c.y - side * 0.03f
            drawCircle(
                Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(gx, gy), radius = side * 0.30f
                ),
                radius = side * 0.30f, center = Offset(gx, gy)
            )
            // Destello de la mitad tocada
            if (flashA > 0f) drawRect(accent.copy(alpha = flashA), Offset(0f, 0f), Size(c.x, size.height * BAND_TOP))
            if (flashB > 0f) drawRect(accent.copy(alpha = flashB), Offset(c.x, 0f), Size(c.x, size.height * BAND_TOP))

            // Aros pegados al bisel: juegos del set en curso llenandose de abajo
            // arriba, y un punto por cada set que haga falta ganar.
            val sw = side * 0.026f
            val r = side / 2f - sw / 2f - side * 0.012f
            val tl = Offset(c.x - r, c.y - r)
            val box = Size(r * 2f, r * 2f)
            val stroke = Stroke(width = sw, cap = StrokeCap.Round)
            fun arc(start: Float, sweep: Float, color: Color) =
                drawArc(color, start, sweep, useCenter = false, topLeft = tl, size = box, style = stroke)
            fun dot(deg: Float, color: Color) {
                val a = Math.toRadians(deg.toDouble())
                drawCircle(color, radius = sw * 0.6f,
                    center = Offset(c.x + r * cos(a).toFloat(), c.y + r * sin(a).toFloat()))
            }
            // A a la izquierda (145°-235°), B a la derecha (305°-395°). Paran
            // antes de la banda de abajo y dejan libre arriba para la hora.
            arc(145f, 90f, PP.Line)
            arc(305f, 90f, PP.Line)
            val fa = min(engine.gamesA, 6) / 6f
            val fb = min(engine.gamesB, 6) / 6f
            if (fa > 0f) arc(145f, 90f * fa, colA)
            if (fb > 0f) arc(35f - 90f * fb, 90f * fb, colB)
            for (i in 0 until need) {
                dot(243f + i * 7f, if (i < engine.setsA) colA else PP.Line)
                dot(297f - i * 7f, if (i < engine.setsB) colB else PP.Line)
            }
        }

        // Mitades tocables: cada una suma a su pareja
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = w * 0.19f)
                .fillMaxWidth()
                .height(w * (BAND_TOP - 0.19f))
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = tapA, indication = null) { point("A") })
            Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = tapB, indication = null) { point("B") })
        }

        // Logo arriba, como la cabecera del movil, con un punto que dice si el
        // movil esta enlazado. Cabe entre la hora y los nombres: a -0.34 la
        // esfera mide 0.74 de ancho y el logo solo 0.22.
        val linkOk = PhoneLink.connected && PhoneLink.paired
        // Tocar la fila del logo enciende o apaga el arbitro por voz: se
        // cantan los puntos ("punto para Edu", "quince treinta") y se suman.
        val voice = activity.voice
        val micOn = voice.on
        val micPulse by animateFloatAsState(
            if (voice.state == VoiceReferee.State.PROCESSING) 0.45f else 1f, tween(250), label = "mic"
        )
        At(w, 0f, -0.34f, 0.5f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(PP.PillShape)
                    .clickable {
                        voice.toggle()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    .padding(horizontal = w * 0.02f, vertical = w * 0.006f)
            ) {
                Box(
                    Modifier
                        .size(w * 0.06f)
                        .clip(CircleShape)
                        .background(if (micOn) PP.Danger.copy(alpha = 0.25f * micPulse + 0.1f) else PP.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "🎙", fontSize = sz(0.034f),
                        color = if (micOn) PP.Danger else PP.TextMuted,
                        modifier = Modifier.alpha(if (micOn) micPulse else 0.6f)
                    )
                }
                Spacer(Modifier.width(w * 0.015f))
                Image(
                    painter = painterResource(id = R.drawable.logo_wordmark),
                    contentDescription = "PadelPulse Live",
                    modifier = Modifier.width(w * 0.22f).aspectRatio(LOGO_RATIO)
                )
                Spacer(Modifier.width(w * 0.015f))
                Box(Modifier.size(w * 0.02f).clip(CircleShape).background(linkColor(engine)))
            }
        }

        At(w, -0.22f, -0.225f, 0.36f) { SideNames(engine, "A", servingA, accent, w, ::sz) }
        At(w, 0.22f, -0.225f, 0.36f) { SideNames(engine, "B", !servingA, accent, w, ::sz) }

        // Estado especial del juego, en una pastilla entre nombres y tanteo
        val tbActive = engine.isTb || engine.isSuperTbActive()
        val pill: Pair<String, Color>? = when {
            engine.goldenPointActive -> v.goldenPoint.uppercase() to Color(0xFFFFD24A)
            engine.isDeuce && engine.adv != null ->
                "${v.advantage.uppercase()} ${if (engine.adv == "A") engine.nameA else engine.nameB}" to accent
            engine.isDeuce -> v.deuce.uppercase() to PP.Warn
            engine.isSuperTbActive() -> "SUPER TIE-BREAK" to PP.Warn
            engine.isTb -> "TIE-BREAK" to PP.Warn
            else -> null
        }
        if (pill != null) At(w, 0f, -0.148f, 0.5f) { Pill(pill.first, pill.second, sz(0.034f), w) }

        val strA = engine.getScoreStr("A")
        val strB = engine.getScoreStr("B")
        At(w, -0.27f, -0.035f, 0.3f) { BigScore(strA, if (servingA) accent else PP.TextBright, sz(if (strA.length > 1) 0.225f else 0.25f), servingA) }
        At(w, 0.27f, -0.035f, 0.3f) { BigScore(strB, if (!servingA) accent else PP.TextBright, sz(if (strB.length > 1) 0.225f else 0.25f), !servingA) }

        // Pista vista desde arriba, con la pelota en el cuadro de saque.
        // Tocarla cambia quien saca.
        At(w, 0f, -0.035f, 0.14f) {
            Box(
                Modifier
                    .size(w * 0.13f, w * 0.2f)
                    .clip(CircleShape)
                    .clickable {
                        val next = if (servingA) "B" else "A"
                        engine.serving = next
                        engine.faultCount = 0
                        engine.speakServe(next)
                        activity.onLocalScoreAction("serve", next)
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center
            ) {
                CourtMini(engine.serving, engine.getServeSide(), accent, w)
            }
        }

        // Juegos, y entre ellos los sets ya jugados (6-4 · 3-6)
        At(w, -0.27f, 0.14f, 0.2f) { Stat(engine.gamesA.toString(), PP.TextBright, sz(0.08f), FontWeight.Black) }
        At(w, 0.27f, 0.14f, 0.2f) { Stat(engine.gamesB.toString(), PP.TextBright, sz(0.08f), FontWeight.Black) }
        At(w, 0f, 0.142f, 0.3f) {
            val sets = engine.setScores.takeLast(3)
            if (sets.isEmpty()) {
                Stat(ui.games.uppercase(), PP.TextMuted, sz(0.038f), FontWeight.Bold)
            } else {
                Stat(sets.joinToString(" · "), PP.TextDim, sz(if (sets.size > 2) 0.036f else 0.042f), FontWeight.Bold)
            }
        }

        At(w, 0f, 0.225f, 0.9f) {
            val phase = if (tbActive) (if (engine.isTb) "TIE-BREAK" else "SUPER TB")
                        else matchPhaseLabel(engine, ui)
            // Lo que ha oido la voz, un momento: asi se sabe si ha entendido
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(voice.heardAt) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(3000)
                now = System.currentTimeMillis()
            }
            val showHeard = micOn && voice.heard.isNotBlank() && now - voice.heardAt < 3000
            if (showHeard) {
                Text(
                    "🎙 «${voice.heard}»", color = PP.Danger, fontSize = sz(0.042f),
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            } else Row(verticalAlignment = Alignment.CenterVertically) {
                // Si el enlace falla, eso va primero
                if (!linkOk) {
                    Text(
                        "● ${linkLabel(engine)} · ", color = linkColor(engine),
                        fontSize = sz(0.044f), fontWeight = FontWeight.Bold, maxLines = 1
                    )
                }
                Text(
                    (if (linkOk) "$phase · " else "") + activity.getTimerDisplay(), color = PP.TextDim,
                    fontSize = sz(0.044f), fontWeight = FontWeight.Bold, maxLines = 1
                )
                if (engine.heartRate > 0) {
                    Text(
                        " · ♥ ${engine.heartRate}", color = accent,
                        fontSize = sz(0.044f), fontWeight = FontWeight.Bold, maxLines = 1
                    )
                }
            }
        }

        // FALTA pegada al borde de abajo. Es una banda a todo lo ancho: en
        // redondo la recorta el propio bisel y queda como un boton de borde;
        // en cuadrado es una franja. El texto va siempre dentro de la zona
        // segura.
        val second = engine.faultCount == 1
        EdgeBand(
            w = w,
            label = if (second) (if (es) "2º SAQUE" else "2ND SERVE") else ui.fault.uppercase(),
            labelColor = if (second) PP.Warn else PP.TextBright,
            background = if (second) PP.Warn.copy(alpha = 0.20f) else PP.SurfaceHigh,
            line = if (second) PP.Warn else PP.Line,
            labelSize = sz(0.055f)
        ) {
            engine.handleFault(engine.serving)
            activity.onLocalScoreAction("fault", engine.serving)
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }
}

/**
 * Un lado del marcador es una pareja: con los dos jugadores puestos se ven
 * los dos, uno encima del otro; si no, el nombre de la pareja. Quien saca
 * lleva delante la pelota y el color del tema.
 */
@Composable
private fun SideNames(engine: GameEngine, team: String, serving: Boolean, accent: Color, w: Dp, sz: (Float) -> TextUnit) {
    val players = engine.players(team)
    val color = if (serving) accent else PP.TextDim
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (serving) {
            Box(Modifier.size(w * 0.022f).clip(CircleShape).background(BALL))
            Spacer(Modifier.width(w * 0.012f))
        }
        if (players.size == 2) {
            val size = sz(0.041f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                players.forEach { p ->
                    Text(
                        p.uppercase(), color = color, fontSize = size, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        style = TextStyle(lineHeight = size * 1.05f)
                    )
                }
            }
        } else {
            Text(
                (players.firstOrNull() ?: engine.getName(team)).uppercase(), color = color, fontSize = sz(0.05f),
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Pastilla de estado: iguales, ventaja, punto de oro, tie-break. */
@Composable
private fun Pill(text: String, color: Color, size: TextUnit, w: Dp) {
    Text(
        text, color = color, fontSize = size, fontWeight = FontWeight.Black,
        maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(PP.PillShape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.8f), PP.PillShape)
            .padding(horizontal = w * 0.022f, vertical = w * 0.004f)
    )
}

@Composable
private fun BigScore(text: String, color: Color, size: TextUnit, glow: Boolean) {
    // Late al cambiar: se nota el punto sin tener que leer el numero
    var bump by remember { mutableStateOf(false) }
    LaunchedEffect(text) {
        bump = true
        kotlinx.coroutines.delay(140)
        bump = false
    }
    val scale by animateFloatAsState(
        if (bump) 1.12f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "bump"
    )
    Text(
        text, color = color, fontSize = size, fontWeight = FontWeight.Black,
        maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.scale(scale),
        style = TextStyle(
            shadow = if (glow) Shadow(color.copy(alpha = 0.45f), Offset.Zero, blurRadius = 24f) else null
        )
    )
}

@Composable
private fun Stat(text: String, color: Color, size: TextUnit, weight: FontWeight = FontWeight.ExtraBold) {
    Text(text, color = color, fontSize = size, fontWeight = weight, maxLines = 1, textAlign = TextAlign.Center)
}

/**
 * Pista en horizontal: A a la izquierda, B a la derecha, red en medio. Quien
 * saca lo hace desde su cuadro derecho o izquierdo, y mirando a la red la
 * derecha de A cae abajo y la de B arriba. La pelota marca el cuadro.
 */
@Composable
private fun CourtMini(serving: String, side: String, accent: Color, w: Dp) {
    Canvas(Modifier.size(w * 0.085f, w * 0.15f)) {
        val cw = size.width / 2f
        val ch = size.height / 2f
        val thin = Stroke(1.dp.toPx())
        fun cell(col: Int, row: Int, on: Boolean) {
            val tl = Offset(col * cw, row * ch)
            if (on) {
                drawRect(accent.copy(alpha = 0.7f), topLeft = tl, size = Size(cw, ch))
                drawCircle(BALL, radius = cw * 0.3f, center = Offset(tl.x + cw / 2f, tl.y + ch / 2f))
                drawCircle(Color.Black, radius = cw * 0.3f, center = Offset(tl.x + cw / 2f, tl.y + ch / 2f), style = thin)
            }
            drawRect(PP.Line, topLeft = tl, size = Size(cw, ch), style = thin)
        }
        cell(0, 0, serving == "A" && side == "L")
        cell(0, 1, serving == "A" && side == "R")
        cell(1, 0, serving == "B" && side == "R")
        cell(1, 1, serving == "B" && side == "L")
        drawRoundRect(
            Color(0xFF666666), size = size,
            cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx())
        )
        drawLine(
            PP.TextBright.copy(alpha = 0.7f),
            Offset(size.width / 2f, -2.dp.toPx()), Offset(size.width / 2f, size.height + 2.dp.toPx()),
            strokeWidth = 1.5.dp.toPx()
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Pagina 1 · controles
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun ControlsPage(
    engine: GameEngine,
    activity: MainActivity,
    ui: UIStrings,
    accent: Color,
    listState: ScalingLazyListState,
    onSettings: () -> Unit,
    onMode: () -> Unit,
    onEditName: (String) -> Unit,
    onPicker: (String) -> Unit
) {
    val es = engine.lang == "es"
    val servingName = if (engine.serving == "A") engine.nameA else engine.nameB

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        // un poco mas abajo: la ultima fila tiene que poder subir por encima
        // de los puntos del pager
        contentPadding = roundSafePadding(extraVertical = 10.dp)
    ) {
        item { PPLabel(if (es) "CONTROLES" else "CONTROLS", color = accent, size = PP.Label) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                RoundAction("↩") {
                    engine.undo()
                    activity.onLocalScoreAction("undo")
                }
                RoundAction(if (engine.voiceEnabled) "🔊" else "🔇") {
                    engine.voiceEnabled = !engine.voiceEnabled
                    engine.saveState()
                }
                RoundAction("⚙") { onSettings() }
            }
        }

        item {
            val v = activity.voice
            ControlChip(
                if (es) "Árbitro por voz" else "Voice referee",
                if (v.on) (if (es) "Escuchando · canta los puntos" else "Listening · call the points")
                else (if (es) "Apagado" else "Off")
            ) { v.toggle() }
        }
        item { VoiceVolumeCard(engine, activity, accent) }

        item {
            ControlChip(if (es) "Restar punto" else "Remove point", engine.nameA) {
                engine.decreasePoint("A")
                activity.onLocalScoreAction("minus", "A")
            }
        }
        item {
            ControlChip(if (es) "Restar punto" else "Remove point", engine.nameB) {
                engine.decreasePoint("B")
                activity.onLocalScoreAction("minus", "B")
            }
        }
        item {
            ControlChip(if (es) "Cambiar saque" else "Switch serve", "${ui.serves}: $servingName") {
                val next = if (engine.serving == "A") "B" else "A"
                engine.serving = next
                engine.faultCount = 0
                engine.speakServe(next)
                activity.onLocalScoreAction("serve", next)
            }
        }
        item { ControlChip(ui.sets, "${engine.setsA} – ${engine.setsB}") { onPicker("sets") } }
        item { ControlChip(ui.games, "${engine.gamesA} – ${engine.gamesB}") { onPicker("games") } }
        // Cada lado es una pareja: su nombre y sus dos jugadores
        for (t in listOf("A", "B")) {
            item { ControlChip(if (t == "A") ui.teamA else ui.teamB, engine.getName(t)) { onEditName(t) } }
            for (n in 1..2) {
                item {
                    ControlChip(
                        (if (es) "Jugador " else "Player ") + "$t$n",
                        engine.getName("$t$n").ifBlank { if (es) "Sin nombre" else "No name" }
                    ) { onEditName("$t$n") }
                }
            }
        }
        item { ControlChip(if (es) "Móvil" else "Phone", linkLabel(engine)) { onMode() } }
        item {
            val paused = activity.sensorsPaused
            ControlChip(
                if (paused) (if (es) "Reanudar sensores" else "Resume sensors")
                else (if (es) "Pausar sensores" else "Pause sensors"),
                null
            ) { if (paused) activity.resumeSensors() else activity.pauseSensors() }
        }
        item { DetectionCard(engine, activity, accent) }
        item {
            ControlChip(ui.newMatch, null, danger = true) {
                engine.resetMatch()
                activity.resetTimer()
                activity.startTimer()
                activity.onLocalScoreAction("reset")
            }
        }
        item { ExitButton(engine, activity) }
    }
}

@Composable
private fun RoundAction(glyph: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(backgroundColor = PP.SurfaceHigh),
        modifier = Modifier.size(44.dp)
    ) {
        Text(glyph, fontSize = 18.sp, color = PP.TextBright)
    }
}

@Composable
private fun ControlChip(label: String, secondary: String?, danger: Boolean = false, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = {
            Text(
                label, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, color = if (danger) PP.Danger else PP.TextBright
            )
        },
        secondaryLabel = if (secondary != null) {
            {
                Text(
                    secondary, fontSize = 12.sp, color = PP.TextDim, maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else null,
        colors = ChipDefaults.primaryChipColors(
            backgroundColor = if (danger) Color(0xFF2A1212) else PP.SurfaceHigh,
            contentColor = PP.TextBright
        ),
        modifier = Modifier.fillMaxWidth()
    )
}

// ─────────────────────────────────────────────────────────────────────
// Pagina 2 · salud
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun HealthDial(engine: GameEngine, activity: MainActivity, accent: Color, w: Dp) {
    val density = LocalDensity.current
    fun sz(frac: Float): TextUnit = with(density) { (w * frac).toSp() }
    val es = engine.lang == "es"

    // Solo lecturas reales del sensor. Sin dato, "–", nunca un numero inventado.
    val hr = if (engine.heartRate > 0) engine.heartRate.toString() else "–"
    val kcal = if (engine.calories > 0) engine.calories.toString() else "–"
    val km = if (engine.distanceKm > 0.0) "%.2f".format(engine.distanceKm) else "–"

    Box(Modifier.fillMaxSize()) {
        // Pulso sobre 200, en un arco partido arriba para no pisar la hora:
        // sube por la izquierda y sigue bajando por la derecha.
        Canvas(Modifier.fillMaxSize()) {
            val side = min(size.width, size.height)
            val sw = side * 0.022f
            val r = side / 2f - sw / 2f - side * 0.012f
            val tl = Offset(center.x - r, center.y - r)
            val box = Size(r * 2f, r * 2f)
            val stroke = Stroke(width = sw, cap = StrokeCap.Round)
            fun arc(start: Float, sweep: Float, color: Color) =
                drawArc(color, start, sweep, useCenter = false, topLeft = tl, size = box, style = stroke)
            arc(145f, 110f, PP.Line)
            arc(285f, 110f, PP.Line)
            val fill = min(engine.heartRate / 200f, 1f) * 220f
            val left = min(fill, 110f)
            if (left > 0f) arc(145f, left, accent)
            if (fill > 110f) arc(285f, fill - 110f, accent)
        }

        At(w, 0f, -0.31f, 0.6f) { Stat("⏱ " + activity.getTimerDisplay(), PP.TextDim, sz(0.05f), FontWeight.Bold) }
        At(w, 0f, -0.205f, 0.3f) { Stat("♥", accent, sz(0.06f)) }
        At(w, 0f, -0.07f, 0.6f) { Stat(hr, PP.TextBright, sz(0.22f), FontWeight.Black) }
        At(w, 0f, 0.065f, 0.4f) { Stat("PPM", PP.TextMuted, sz(0.042f), FontWeight.Bold) }
        At(w, -0.16f, 0.15f, 0.3f) { Stat(kcal, PP.TextBright, sz(0.07f)) }
        At(w, 0.16f, 0.15f, 0.3f) { Stat(km, PP.TextBright, sz(0.07f)) }
        At(w, -0.16f, 0.215f, 0.3f) { Stat("🔥 KCAL", PP.TextMuted, sz(0.038f), FontWeight.Bold) }
        At(w, 0.16f, 0.215f, 0.3f) { Stat("🏃 KM", PP.TextMuted, sz(0.038f), FontWeight.Bold) }

        // Abajo, el bloqueo de la deteccion automatica de ejercicio: es lo que
        // evita que la app de salud del reloj tape el marcador al moverte.
        // Pausar sensores sigue en Controles.
        val (guardText, guardColor) = guardStatus(engine, accent, short = true)
        val on = WorkoutGuard.enabled
        EdgeBand(
            w = w,
            label = guardText,
            labelColor = guardColor,
            background = if (on) ThemeUtils.tint(engine.theme, 0.16f) else PP.SurfaceHigh,
            line = if (on) accent else PP.Line,
            labelSize = sz(0.04f)
        ) { WorkoutGuard.setEnabled(activity, !on, activity.timerRunning) }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Piezas comunes
// ─────────────────────────────────────────────────────────────────────

/**
 * Coloca el contenido con su centro desplazado (cx, cy) desde el centro de la
 * pantalla, todo en fracciones del ancho.
 */
@Composable
private fun BoxScope.At(w: Dp, cx: Float, cy: Float, width: Float, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier = Modifier
            .align(Alignment.Center)
            .offset(x = w * cx, y = w * cy)
            .width(w * width),
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Boton a todo lo ancho pegado al borde de abajo. */
@Composable
private fun BoxScope.EdgeBand(
    w: Dp,
    label: String,
    labelColor: Color,
    background: Color,
    line: Color,
    labelSize: TextUnit,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .height(w * (1f - BAND_TOP))
            .background(background)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.fillMaxWidth().height(1.5.dp).background(line))
        Spacer(Modifier.height(w * 0.035f))
        Text(
            label, color = labelColor, fontSize = labelSize, fontWeight = FontWeight.Black,
            maxLines = 1, textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun PageDots(current: Int, accent: Color, size: Dp, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(size * 0.85f)) {
        repeat(PAGES) { i ->
            Box(
                Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(if (i == current) accent else PP.TextMuted)
            )
        }
    }
}

/** Texto de fase: set N, tie-break o super tie-break. */
private fun matchPhaseLabel(engine: GameEngine, ui: UIStrings): String = when {
    engine.isSuperTbActive() -> "SUPER TB"
    engine.isTb -> "TIE-BREAK"
    else -> "${ui.sets.uppercase()} ${engine.setsA + engine.setsB + 1}/${engine.bestOf}"
}
