package padelpulseapp2.netlify.app

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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

@Composable
private fun ScoreDial(engine: GameEngine, activity: MainActivity, ui: UIStrings, accent: Color, w: Dp) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    fun sz(frac: Float): TextUnit = with(density) { (w * frac).toSp() }
    val es = engine.lang == "es"

    val servingA = engine.serving == "A"
    val colA = if (servingA) accent else PP.TextBright.copy(alpha = 0.85f)
    val colB = if (!servingA) accent else PP.TextBright.copy(alpha = 0.85f)
    val need = Math.ceil(engine.bestOf / 2.0).toInt()

    fun point(team: String) {
        engine.addPoint(team)
        activity.onLocalScoreAction("point", team)
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Box(Modifier.fillMaxSize()) {
        // Arcos pegados al bisel: juegos del set en curso, llenandose de abajo
        // arriba, y un punto por cada set que haga falta ganar.
        Canvas(Modifier.fillMaxSize()) {
            val side = min(size.width, size.height)
            val sw = side * 0.022f
            val r = side / 2f - sw / 2f - side * 0.012f
            val c = center
            val tl = Offset(c.x - r, c.y - r)
            val box = Size(r * 2f, r * 2f)
            val stroke = Stroke(width = sw, cap = StrokeCap.Round)
            fun arc(start: Float, sweep: Float, color: Color) =
                drawArc(color, start, sweep, useCenter = false, topLeft = tl, size = box, style = stroke)
            fun dot(deg: Float, color: Color) {
                val a = Math.toRadians(deg.toDouble())
                drawCircle(color, radius = sw * 0.55f,
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
                dot(241f + i * 6.5f, if (i < engine.setsA) colA else PP.Line)
                dot(299f - i * 6.5f, if (i < engine.setsB) colB else PP.Line)
            }
        }

        // Mitades tocables: cada una suma a su pareja. Sin ondulacion porque en
        // un rectangulo recortado por el circulo queda raro; el aviso es el
        // numero que late y la vibracion.
        val tapA = remember { MutableInteractionSource() }
        val tapB = remember { MutableInteractionSource() }
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

        At(w, 0f, -0.33f, 0.6f) {
            Text(
                "● " + linkLabel(engine), color = linkColor(engine), fontSize = sz(0.046f),
                fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center
            )
        }

        At(w, -0.22f, -0.235f, 0.38f) { TeamName(engine.nameA, servingA, accent, sz(0.05f)) }
        At(w, 0.22f, -0.235f, 0.38f) { TeamName(engine.nameB, !servingA, accent, sz(0.05f)) }

        At(w, -0.25f, -0.03f, 0.32f) { BigScore(engine.getScoreStr("A"), if (servingA) accent else PP.TextBright, sz(0.23f)) }
        At(w, 0.25f, -0.03f, 0.32f) { BigScore(engine.getScoreStr("B"), if (!servingA) accent else PP.TextBright, sz(0.23f)) }

        // Pista vista desde arriba, con el cuadro de saque encendido. Tocarla
        // cambia quien saca.
        At(w, 0f, -0.03f, 0.16f) {
            Box(
                Modifier
                    .size(w * 0.16f, w * 0.22f)
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

        At(w, -0.25f, 0.135f, 0.2f) { Stat(engine.gamesA.toString(), PP.TextBright, sz(0.075f)) }
        At(w, 0f, 0.137f, 0.2f) { Stat(ui.games.uppercase(), PP.TextMuted, sz(0.038f), FontWeight.Bold) }
        At(w, 0.25f, 0.135f, 0.2f) { Stat(engine.gamesB.toString(), PP.TextBright, sz(0.075f)) }

        At(w, 0f, 0.225f, 0.9f) {
            val phase = if (engine.goldenPointActive) ui.goldenPt.uppercase() else matchPhaseLabel(engine, ui)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "$phase · ${activity.getTimerDisplay()}", color = PP.TextDim,
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

@Composable
private fun TeamName(name: String, serving: Boolean, accent: Color, size: TextUnit) {
    Text(
        name.uppercase(), color = if (serving) accent else PP.TextDim, fontSize = size,
        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun BigScore(text: String, color: Color, size: TextUnit) {
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
        maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.scale(scale)
    )
}

@Composable
private fun Stat(text: String, color: Color, size: TextUnit, weight: FontWeight = FontWeight.ExtraBold) {
    Text(text, color = color, fontSize = size, fontWeight = weight, maxLines = 1, textAlign = TextAlign.Center)
}

/**
 * Pista en horizontal: A a la izquierda, B a la derecha, red en medio. Quien
 * saca lo hace desde su cuadro derecho o izquierdo, y mirando a la red la
 * derecha de A cae abajo y la de B arriba.
 */
@Composable
private fun CourtMini(serving: String, side: String, accent: Color, w: Dp) {
    Canvas(Modifier.size(w * 0.11f, w * 0.17f)) {
        val cw = size.width / 2f
        val ch = size.height / 2f
        val thin = Stroke(1.dp.toPx())
        fun cell(col: Int, row: Int, on: Boolean) {
            val tl = Offset(col * cw, row * ch)
            if (on) drawRect(accent.copy(alpha = 0.65f), topLeft = tl, size = Size(cw, ch))
            drawRect(PP.Line, topLeft = tl, size = Size(cw, ch), style = thin)
        }
        cell(0, 0, serving == "A" && side == "L")
        cell(0, 1, serving == "A" && side == "R")
        cell(1, 0, serving == "B" && side == "R")
        cell(1, 1, serving == "B" && side == "L")
        drawRoundRect(
            Color(0xFF555555), size = size,
            cornerRadius = CornerRadius(3.dp.toPx()), style = Stroke(1.5.dp.toPx())
        )
        drawLine(
            PP.TextBright.copy(alpha = 0.55f),
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
        item { ControlChip(ui.teamA, engine.nameA) { onEditName("A") } }
        item { ControlChip(ui.teamB, engine.nameB) { onEditName("B") } }
        item { ControlChip(if (es) "Móvil" else "Phone", linkLabel(engine)) { onMode() } }
        item {
            val paused = activity.sensorsPaused
            ControlChip(
                if (paused) (if (es) "Reanudar sensores" else "Resume sensors")
                else (if (es) "Pausar sensores" else "Pause sensors"),
                null
            ) { if (paused) activity.resumeSensors() else activity.pauseSensors() }
        }
        item {
            ControlChip(ui.newMatch, null, danger = true) {
                engine.resetMatch()
                activity.resetTimer()
                activity.startTimer()
                activity.onLocalScoreAction("reset")
            }
        }
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

        val paused = activity.sensorsPaused
        EdgeBand(
            w = w,
            label = if (paused) (if (es) "▶ REANUDAR SENSORES" else "▶ RESUME SENSORS")
                    else (if (es) "⏸ PAUSAR SENSORES" else "⏸ PAUSE SENSORS"),
            labelColor = if (paused) accent else PP.TextDim,
            background = if (paused) ThemeUtils.tint(engine.theme, 0.16f) else PP.SurfaceHigh,
            line = if (paused) accent else PP.Line,
            labelSize = sz(0.046f)
        ) { if (paused) activity.resumeSensors() else activity.pauseSensors() }
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
