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
import padelpulseapp2.netlify.app.sync.WatchAccount
import padelpulseapp2.netlify.app.voice.VoiceReferee
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import padelpulseapp2.netlify.app.ui.FitText
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPChip
import padelpulseapp2.netlify.app.ui.PPLabel
import padelpulseapp2.netlify.app.ui.iconSp
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Marcador pensado para la esfera redonda.
 *
 * Cuatro paginas que se pasan deslizando, como las apps de entreno de Wear OS:
 *   0  marcador   lo unico que hace falta en pista: tocar una mitad suma punto;
 *                 tocar el nombre de una pareja hace que saque ella; debajo del
 *                 tanteo, "−" de cada pareja y la franja de juegos y sets
 *                 (tocar para corregirlos); el logo lleva a Ajustes
 *   1  salud      pulso, calorias y distancia
 *   2  ajustes    partido, equipos, color, sets, juegos, voz e idioma
 *   3  sincronizacion  movil, cuenta e historial
 *
 * El marcador va primero a proposito. En la primera pagina, deslizar a la
 * derecha es el gesto del sistema para salir; si los controles fueran la
 * pagina 0, ese gesto pelearia con el pager.
 *
 * Todas las medidas son fracciones del ancho de pantalla (w) y se colocan desde
 * el centro, porque en redondo lo que decide si algo se corta es su distancia
 * al centro, no al borde de un rectangulo que no existe.
 */

private const val PAGES = 4

/** Arranque de la banda de abajo (FALTA), desde arriba, en fracciones del ancho. */
private const val BAND_TOP = 0.80f

/**
 * Fila de control bajo el tanteo (restar punto, juegos y sets), en fracciones
 * del ancho desde el centro: su centro y su borde de arriba. Las mitades que
 * suman punto terminan en ese borde.
 */
private const val CONTROL_ROW_Y = 0.135f
private const val CONTROL_ROW_TOP = 0.035f

/**
 * Cuanto sigue la esfera a la letra del sistema. El marcador esta dibujado a
 * medida -cada cosa en su sitio, sin scroll-, asi que los textos crecen con
 * la letra del usuario hasta un 20 % y, si aun asi no caben, FitText los
 * encoge antes que cortarlos. El tanteo grande no crece: ya ocupa todo lo que
 * la esfera da de si.
 */
private const val DIAL_FONT_CAP = 1.2f

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ScorePager(
    engine: GameEngine,
    activity: MainActivity,
    settingsState: ScalingLazyListState,
    syncState: ScalingLazyListState,
    onEditName: (String) -> Unit,
    onPicker: (String) -> Unit,
    onNewMatch: () -> Unit,
    onLanguage: () -> Unit,
    onInvite: () -> Unit,
    onHistory: () -> Unit,
    onPageList: (ScalingLazyListState?) -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    val pager = rememberPagerState(
        initialPage = engine.requestedPage.coerceIn(0, PAGES - 1),
        pageCount = { PAGES }
    )
    val scope = rememberCoroutineScope()

    // Volver al marcador en una pagina concreta (p. ej. desde el Historial,
    // a Sincronizacion)
    LaunchedEffect(engine.requestedPage) {
        val p = engine.requestedPage
        if (p in 0 until PAGES) {
            pager.scrollToPage(p)
            engine.requestedPage = -1
        }
    }
    // El indicador de desplazamiento sigue a la lista de la pagina visible
    LaunchedEffect(pager.currentPage) {
        onPageList(
            when (pager.currentPage) {
                2 -> settingsState
                3 -> syncState
                else -> null
            }
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize().background(PP.Bg)) {
        val w = minOf(maxWidth, maxHeight)
        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> ScoreDial(
                    engine, activity, ui, accent, w,
                    onOpenSettings = { scope.launch { pager.animateScrollToPage(2) } },
                    onEditScore = { onPicker("all") }
                )
                1 -> HealthDial(engine, activity, accent, w)
                2 -> SettingsPage(engine, activity, accent, settingsState, onNewMatch, onEditName, onPicker, onLanguage, onInvite)
                else -> SyncPage(engine, activity, accent, syncState, onHistory)
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
private fun ScoreDial(
    engine: GameEngine,
    activity: MainActivity,
    ui: UIStrings,
    accent: Color,
    w: Dp,
    onOpenSettings: () -> Unit,
    onEditScore: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    fun sz(frac: Float): TextUnit = with(density) { (w * frac).toSp() }
    val fs = density.fontScale.coerceIn(1f, DIAL_FONT_CAP)
    /** Texto que sigue la letra del sistema, con tope. */
    fun szT(frac: Float): TextUnit = sz(frac) * fs
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

        // Mitades tocables: cada una suma a su pareja. Llegan hasta justo
        // encima de la fila de control (restar y corregir juegos/sets), que
        // tiene sus propios botones: asi un toque ahi nunca suma un punto.
        Row(
            Modifier
                .align(Alignment.TopCenter)
                .padding(top = w * 0.19f)
                .fillMaxWidth()
                .height(w * (0.5f + CONTROL_ROW_TOP - 0.19f))
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = tapA, indication = null) { point("A") })
            Box(Modifier.weight(1f).fillMaxHeight().clickable(interactionSource = tapB, indication = null) { point("B") })
        }

        // Quien saca: se elige tocando el nombre de su pareja, que se pone en
        // verde con la pelota delante. Tambien vale tocar la pista del centro.
        fun chooseServe(team: String) {
            if (engine.serving != team) {
                engine.serving = team
                engine.faultCount = 0
                engine.speakServe(team)
                activity.onLocalScoreAction("serve", team)
            }
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
        for (team in listOf("A", "B")) {
            val serves = engine.serving == team
            At(w, if (team == "A") -0.22f else 0.22f, -0.235f, 0.36f) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = w * 0.16f)
                        .clip(PP.PillShape)
                        .clickable(onClickLabel = if (es) "Saca esta pareja" else "This team serves") { chooseServe(team) },
                    contentAlignment = Alignment.Center
                ) { SideNames(engine, team, serves, accent, w, ::szT) }
            }
        }

        // Logo arriba, como la cabecera del movil, con un punto que dice si el
        // movil esta enlazado. Cabe entre la hora y los nombres: a -0.34 la
        // esfera mide 0.74 de ancho y el logo solo 0.22.
        // A los lados del logo, dos botones: a la izquierda el arbitro por voz
        // (cantas "punto para Edu" y se suma), a la derecha silenciar la voz
        // que canta los puntos. Cada uno tiene su propia zona para tocar, mas
        // grande que el circulo que se ve.
        val voice = activity.voice
        val micOn = voice.on
        val muted = !engine.voiceEnabled
        val micPulse by animateFloatAsState(
            if (voice.state == VoiceReferee.State.PROCESSING) 0.45f else 1f, tween(250), label = "mic"
        )
        @Composable
        fun TopButton(glyph: String, active: Boolean, activeColor: Color, pulse: Float, onClick: () -> Unit) {
            Box(
                Modifier
                    .size(w * 0.12f, w * 0.11f)
                    .clip(CircleShape)
                    .clickable {
                        onClick()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(w * 0.065f)
                        .clip(CircleShape)
                        .background(if (active) activeColor.copy(alpha = 0.25f * pulse + 0.1f) else PP.SurfaceHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        glyph, fontSize = sz(0.034f),
                        color = if (active) activeColor else PP.TextMuted,
                        modifier = Modifier.alpha(if (active) pulse else 0.7f)
                    )
                }
            }
        }
        At(w, 0f, -0.34f, 0.72f) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TopButton("🎙", micOn, PP.Danger, micPulse) { voice.toggle() }
                // El logo lleva a Ajustes (tambien se llega deslizando).
                // 48 dp de alto para el dedo.
                Row(
                    Modifier
                        .heightIn(min = 48.dp)
                        .clip(PP.PillShape)
                        .clickable {
                            onOpenSettings()
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        .padding(horizontal = w * 0.01f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.logo_wordmark),
                        contentDescription = if (es) "Ajustes" else "Settings",
                        modifier = Modifier.width(w * 0.22f).aspectRatio(LOGO_RATIO)
                    )
                    Spacer(Modifier.width(w * 0.01f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(w * 0.02f).clip(CircleShape).background(linkColor(engine)))
                        Text("≡", fontSize = sz(0.04f), color = PP.TextMuted, fontWeight = FontWeight.Bold)
                    }
                }
                TopButton(if (muted) "🔇" else "🔊", muted, PP.Danger, 1f) {
                    engine.voiceEnabled = !engine.voiceEnabled
                    engine.persist()
                    // Al silenciar se corta tambien la frase que estuviera sonando
                    if (!engine.voiceEnabled) activity.stopSpeaking() else engine.speakScore()
                }
                // El UNICO interruptor de la deteccion automatica de ejercicio
                // (la app de salud del reloj que tapa el marcador al moverte).
                // Encendido por defecto: con el partido en marcha el reloj ya
                // tiene un entreno y no detecta otro. Aviso ⚠ si el reloj no
                // deja bloquearla.
                val guardOn = WorkoutGuard.enabled
                val guardFails = WorkoutGuard.state == WorkoutGuard.State.UNSUPPORTED ||
                    WorkoutGuard.state == WorkoutGuard.State.FAILED
                TopButton(if (guardOn && guardFails) "⚠" else "🛡", guardOn, if (guardFails) PP.Warn else accent, 1f) {
                    val on = !WorkoutGuard.enabled
                    WorkoutGuard.setEnabled(activity, on, activity.timerRunning)
                    android.widget.Toast.makeText(
                        activity,
                        if (on) (if (es) "Detección automática bloqueada mientras juegas" else "Auto detection blocked while playing")
                        else (if (es) "Detección automática permitida" else "Auto detection allowed"),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }


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
        if (pill != null) At(w, 0f, -0.16f, 0.5f) { Pill(pill.first, pill.second, szT(0.034f), w) }

        val strA = engine.getScoreStr("A")
        val strB = engine.getScoreStr("B")
        // Algo mas pequeño que antes: debajo va la fila de control
        At(w, -0.26f, -0.07f, 0.3f) { BigScore(strA, if (servingA) accent else PP.TextBright, sz(if (strA.length > 1) 0.19f else 0.21f), servingA) }
        At(w, 0.26f, -0.07f, 0.3f) { BigScore(strB, if (!servingA) accent else PP.TextBright, sz(if (strB.length > 1) 0.19f else 0.21f), !servingA) }

        // Pista vista desde arriba, con la pelota en el cuadro de saque.
        // Tocarla cambia quien saca.
        At(w, 0f, -0.07f, 0.14f) {
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

        // Fila de control, justo debajo del tanteo. A los lados, restar un
        // punto a cada pareja; en medio, juegos y sets: al tocarla se corrigen.
        // Todos los toques miden 48 dp como minimo, aunque el circulo que se ve
        // sea mas pequeño, para que se acierten con el dedo en plena pista.
        val touch = maxOf(48.dp, w * 0.2f)
        val touchFrac = touch / w
        At(w, -0.36f, CONTROL_ROW_Y, touchFrac) {
            MinusButton(w, touch, colA, sz(0.07f), if (es) "Restar punto a ${engine.nameA}" else "Remove point from ${engine.nameA}") {
                engine.decreasePoint("A")
                activity.onLocalScoreAction("minus", "A")
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        At(w, 0.36f, CONTROL_ROW_Y, touchFrac) {
            MinusButton(w, touch, colB, sz(0.07f), if (es) "Restar punto a ${engine.nameB}" else "Remove point from ${engine.nameB}") {
                engine.decreasePoint("B")
                activity.onLocalScoreAction("minus", "B")
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        }
        At(w, 0f, CONTROL_ROW_Y, 0.46f) {
            // Toque de 48 dp como minimo; la pastilla que se ve, algo mas baja
            // para no pisar la linea de estado en los relojes pequeños.
            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = touch)
                    .clip(PP.PillShape)
                    .clickable(onClickLabel = if (es) "Corregir juegos y sets" else "Fix games and sets") {
                        onEditScore()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = w * 0.17f)
                        .clip(PP.PillShape)
                        .background(PP.Surface)
                        .border(1.dp, PP.Line, PP.PillShape),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GamesCell(engine.gamesA.toString(), ui.games, szT(0.075f), szT(0.026f), Modifier.weight(1f))
                    GamesCell("${engine.setsA}-${engine.setsB}", ui.sets, szT(0.05f), szT(0.026f), Modifier.weight(1f), PP.TextDim)
                    GamesCell(engine.gamesB.toString(), ui.games, szT(0.075f), szT(0.026f), Modifier.weight(1f))
                }
            }
        }

        // Por debajo de la fila de control. A esa altura el bisel deja ~0.84
        // del ancho, y FitText encoge la linea si no cabe.
        At(w, 0f, 0.268f, 0.78f) {
            // Los sets ya jugados (6-4 · 3-6) dicen mas que "SET 2/3"
            val sets = engine.setScores.takeLast(3)
            val phase = when {
                tbActive -> if (engine.isTb) "TIE-BREAK" else "SUPER TB"
                sets.isNotEmpty() -> sets.joinToString(" · ")
                else -> matchPhaseLabel(engine, ui)
            }
            // Lo que ha oido la voz, un momento: asi se sabe si ha entendido
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(voice.heardAt) {
                now = System.currentTimeMillis()
                kotlinx.coroutines.delay(3000)
                now = System.currentTimeMillis()
            }
            val showHeard = micOn && voice.heard.isNotBlank() && now - voice.heardAt < 3000
            // En el hueco que deja el bisel a esa altura (0.9 del ancho): si
            // no cabe, la linea se encoge entera en vez de perder el final
            if (showHeard) {
                FitText("🎙 «${voice.heard}»", PP.Danger, szT(0.042f))
            } else {
                // El estado del movil no va aqui: lo dice el punto junto al
                // logo (verde = sincronizado) y el detalle esta en Sincronizacion
                val status = buildAnnotatedString {
                    append("$phase · " + activity.getTimerDisplay())
                    if (engine.heartRate > 0) withStyle(SpanStyle(color = accent)) { append(" · ♥ ${engine.heartRate}") }
                }
                FitText(status, PP.TextDim, szT(0.044f))
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
            labelSize = szT(0.055f)
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
        // Nombres largos: la letra baja hasta que caben, no se cortan
        if (players.size == 2) {
            val size = sz(0.041f)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                players.forEach { p ->
                    FitText(p.uppercase(), color, size, style = TextStyle(lineHeight = size * 1.05f))
                }
            }
        } else {
            FitText((players.firstOrNull() ?: engine.getName(team)).uppercase(), color, sz(0.05f))
        }
    }
}

/** Pastilla de estado: iguales, ventaja, punto de oro, tie-break. */
@Composable
private fun Pill(text: String, color: Color, size: TextUnit, w: Dp) {
    FitText(
        text, color, size, FontWeight.Black,
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
    FitText(text, color, size, weight)
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
// Pagina 1 · salud
// ─────────────────────────────────────────────────────────────────────

@Composable
private fun HealthDial(engine: GameEngine, activity: MainActivity, accent: Color, w: Dp) {
    val density = LocalDensity.current
    fun sz(frac: Float): TextUnit = with(density) { (w * frac).toSp() }
    val fs = density.fontScale.coerceIn(1f, DIAL_FONT_CAP)
    fun szT(frac: Float): TextUnit = sz(frac) * fs
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

        At(w, 0f, -0.31f, 0.6f) { Stat("⏱ " + activity.getTimerDisplay(), PP.TextDim, szT(0.05f), FontWeight.Bold) }
        At(w, 0f, -0.205f, 0.3f) { Stat("♥", accent, sz(0.06f)) }
        At(w, 0f, -0.07f, 0.6f) { Stat(hr, PP.TextBright, sz(0.22f), FontWeight.Black) }
        At(w, 0f, 0.065f, 0.4f) { Stat("PPM", PP.TextMuted, szT(0.042f), FontWeight.Bold) }
        At(w, -0.16f, 0.15f, 0.3f) { Stat(kcal, PP.TextBright, szT(0.07f)) }
        At(w, 0.16f, 0.15f, 0.3f) { Stat(km, PP.TextBright, szT(0.07f)) }
        At(w, -0.16f, 0.215f, 0.28f) { Stat("🔥 KCAL", PP.TextMuted, szT(0.038f), FontWeight.Bold) }
        At(w, 0.16f, 0.215f, 0.28f) { Stat("🏃 KM", PP.TextMuted, szT(0.038f), FontWeight.Bold) }

        // El bloqueo de la deteccion automatica ya no esta aqui: hay un solo
        // interruptor, el 🛡 de arriba del marcador.
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
        // A esa altura el bisel deja ~0.68 del ancho: el texto se encoge para
        // caber ahi dentro, sea cual sea la letra del sistema
        FitText(label, labelColor, labelSize, FontWeight.Black, modifier = Modifier.width(w * 0.62f))
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

/**
 * "−" de una pareja. El circulo que se ve es pequeño para no tapar la esfera,
 * pero el toque ocupa [touch] (48 dp como minimo).
 */
@Composable
private fun MinusButton(w: Dp, touch: Dp, color: Color, glyphSize: TextUnit, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(touch)
            .clip(CircleShape)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .size(w * 0.14f)
                .clip(CircleShape)
                .background(PP.SurfaceHigh)
                .border(1.5.dp, color.copy(alpha = 0.7f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("−", color = color, fontSize = glyphSize, fontWeight = FontWeight.Black, maxLines = 1)
        }
    }
}

/** Una cifra de la franja de juegos y sets, con su rotulo debajo. */
@Composable
private fun GamesCell(
    value: String,
    label: String,
    valueSize: TextUnit,
    labelSize: TextUnit,
    modifier: Modifier,
    color: Color = PP.TextBright
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FitText(value, color, valueSize, FontWeight.Black, modifier = Modifier.fillMaxWidth())
        FitText(label.uppercase(), PP.TextMuted, labelSize, FontWeight.Bold, modifier = Modifier.fillMaxWidth())
    }
}

/** Texto de fase: set N, tie-break o super tie-break. */
private fun matchPhaseLabel(engine: GameEngine, ui: UIStrings): String = when {
    engine.isSuperTbActive() -> "SUPER TB"
    engine.isTb -> "TIE-BREAK"
    else -> "${ui.sets.uppercase()} ${engine.setsA + engine.setsB + 1}/${engine.bestOf}"
}
