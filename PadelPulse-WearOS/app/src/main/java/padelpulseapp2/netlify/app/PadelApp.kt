package padelpulseapp2.netlify.app

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import kotlinx.coroutines.delay
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.WatchAccount
import padelpulseapp2.netlify.app.sync.SyncProtocol
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPCard
import padelpulseapp2.netlify.app.ui.PPLabel
import padelpulseapp2.netlify.app.ui.PPStatusPill

@Composable
fun PadelApp(engine: GameEngine, activity: MainActivity) {
    val accent = ThemeUtils.getColor(engine.theme)

    // Un estado de scroll POR PANTALLA. Compartir uno solo hacia que el
    // indicador de desplazamiento no siguiera a la lista que se estaba viendo
    // (Play rechazo la app por "falta la barra de desplazamiento"), y ademas
    // durante el Crossfade se componen dos pantallas a la vez y se peleaban
    // por el mismo estado.
    val langState = rememberScalingLazyListState()
    val modeState = rememberScalingLazyListState()
    val pairState = rememberScalingLazyListState()
    val scoreState = rememberScalingLazyListState()
    val settingsState = rememberScalingLazyListState()
    val historyState = rememberScalingLazyListState()
    val nameState = rememberScalingLazyListState()

    // El indicador sigue a la lista de la pantalla visible. En las pantallas
    // que no tienen scroll (splash, resume, fin) no se muestra ninguno.
    val activeState = when (engine.currentScreen) {
        "lang" -> langState
        "mode" -> modeState
        "bt" -> pairState
        "score" -> scoreState
        "settings" -> settingsState
        "history" -> historyState
        else -> null
    }

    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary = accent,
            background = PP.Bg,
            surface = PP.Surface,
            onPrimary = Color.Black
        )
    ) {
        Scaffold(
            timeText = { if (engine.currentScreen != "splash") TimeText() },
            vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
            positionIndicator = {
                activeState?.let { PositionIndicator(scalingLazyListState = it) }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize().background(PP.Bg)) {
                Crossfade(targetState = engine.currentScreen, label = "nav") { current ->
                    when (current) {
                        "account" -> AccountScreen(engine, activity)
                        "splash" -> SplashScreen(engine, activity)
                        "resume" -> ResumeScreen(engine, activity)
                        "lang" -> LangScreen(engine, activity, langState)
                        "mode" -> ModeScreen(engine, activity, modeState)
                        "bt" -> PairScreen(engine, activity, pairState)
                        "score" -> ScoreScreen(
                            engine, activity, scoreState, nameState,
                            { engine.currentScreen = "settings" },
                            { engine.currentScreen = "mode" },
                            { engine.currentScreen = "end" }
                        )
                        "settings" -> SettingsScreen(engine, activity, settingsState) { engine.currentScreen = "score" }
                        "history" -> HistoryScreen(engine, activity, historyState) { engine.currentScreen = "settings" }
                        "end" -> EndScreen(engine, activity)
                        else -> SplashScreen(engine, activity)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Estado del enlace, reutilizado en varias pantallas
// ─────────────────────────────────────────────────────────────────────

@Composable
fun linkLabel(engine: GameEngine): String {
    val es = engine.lang == "es"
    return when {
        !PhoneLink.connected -> if (es) "SIN MOVIL" else "NO PHONE"
        !PhoneLink.paired -> if (es) "SIN VINCULAR" else "NOT LINKED"
        SyncProtocol.normalizeMode(engine.mode) == SyncProtocol.MODE_SOLO ->
            if (es) "SOLO" else "SOLO"
        else -> if (es) "CONECTADO" else "CONNECTED"
    }
}

@Composable
fun linkColor(engine: GameEngine): Color = when {
    !PhoneLink.connected -> PP.Danger
    !PhoneLink.paired -> PP.Warn
    else -> ThemeUtils.getColor(engine.theme)
}

@Composable
fun LinkPill(engine: GameEngine, modifier: Modifier = Modifier) {
    PPStatusPill(
        label = linkLabel(engine),
        color = linkColor(engine),
        pulsing = PhoneLink.connected && PhoneLink.paired,
        modifier = modifier
    )
}

/** Boton de volver, identico en todas las pantallas. */
@Composable
fun BackRow(engine: GameEngine, onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(PP.PillShape)
            .clickable { onBack() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        PPLabel(
            if (engine.lang == "es") "‹ VOLVER" else "‹ BACK",
            color = PP.TextDim, size = PP.Micro
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Cuenta
// ─────────────────────────────────────────────────────────────────────

/**
 * Pantalla de cuenta del reloj.
 *
 * No se piden aqui correo y contraseña a proposito: teclearlos en una
 * pantalla de reloj es una tortura y Wear OS recomienda que la sesion la
 * inicie el movil. Cuando el movil entra, manda la sesion por el Data
 * Layer y esta pantalla pasa sola al marcador. Y quien no quiera cuenta,
 * entra sin ella: la cuenta solo sirve para respaldar el historial.
 */
@Composable
fun AccountScreen(engine: GameEngine, activity: MainActivity) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"

    // En cuanto llega la sesion del movil, seguimos solos
    LaunchedEffect(WatchAccount.signedIn) {
        if (WatchAccount.signedIn) {
            delay(1200)
            engine.currentScreen = "splash"
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().background(PP.Bg).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_logo),
            contentDescription = "PadelPulse",
            modifier = Modifier.width(44.dp).padding(bottom = 6.dp)
        )

        if (WatchAccount.signedIn) {
            PPLabel(if (es) "SESION INICIADA" else "SIGNED IN", color = accent, size = PP.Label)
            Spacer(Modifier.height(4.dp))
            Text(
                WatchAccount.name.ifEmpty { WatchAccount.email },
                color = Color.White, fontSize = PP.Body,
                fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center
            )
        } else {
            PPLabel(if (es) "TU CUENTA" else "YOUR ACCOUNT", color = accent, size = PP.Label)
            Spacer(Modifier.height(6.dp))
            Text(
                if (es) "Inicia sesion en PadelPulse del movil y el reloj entra solo."
                else "Sign in on the phone app and the watch follows automatically.",
                color = PP.TextDim, fontSize = PP.Micro,
                textAlign = TextAlign.Center, maxLines = 4
            )
            Spacer(Modifier.height(10.dp))
            LinkPill(engine)
            Spacer(Modifier.height(10.dp))

            Button(
                onClick = {
                    WatchAccount.skip(activity)
                    engine.currentScreen = "splash"
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = PP.Surface),
                modifier = Modifier.height(36.dp).fillMaxWidth(0.9f).clip(RoundedCornerShape(18.dp))
            ) {
                Text(
                    if (es) "JUGAR SIN CUENTA" else "PLAY WITHOUT ACCOUNT",
                    color = accent, fontSize = PP.Micro, fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Splash
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SplashScreen(engine: GameEngine, activity: MainActivity) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(700), label = "fade")
    val logoScale by animateFloatAsState(if (visible) 1f else 0.85f, tween(700, easing = FastOutSlowInEasing), label = "scale")

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp).alpha(fade),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.splash_logo),
            contentDescription = "PadelPulse",
            modifier = Modifier
                .width((66 * logoScale).dp)
                .padding(bottom = 8.dp)
        )
        Text(
            "PadelPulse",
            color = accent,
            fontWeight = FontWeight.Black,
            fontSize = PP.Title
        )
        PPLabel("LIVE WATCH", color = PP.TextMuted, size = PP.Micro)

        Spacer(Modifier.height(14.dp))
        LinkPill(engine)
        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                engine.currentScreen = if (engine.hasSavedMatch()) "resume" else "lang"
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = accent),
            modifier = Modifier.height(40.dp).fillMaxWidth(0.78f).clip(RoundedCornerShape(20.dp))
        ) {
            Text(
                ui.start.uppercase(),
                color = Color.Black,
                fontWeight = FontWeight.Black,
                fontSize = PP.Body
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Idioma
// ─────────────────────────────────────────────────────────────────────

@Composable
fun LangScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 28.dp)
    ) {
        item { PPLabel(ui.chooseLang, color = accent, size = PP.Label) }
        item { Spacer(Modifier.height(4.dp)) }

        items(Translations.langs) { l ->
            val sel = engine.lang == l.id
            Chip(
                onClick = {
                    engine.lang = l.id
                    engine.saveState()
                    activity.sendSettingsToPhone()
                },
                label = {
                    Text(
                        l.name,
                        fontSize = PP.Body,
                        fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal
                    )
                },
                icon = { Text(l.flag, fontSize = 17.sp) },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = if (sel) accent.copy(alpha = 0.18f) else PP.Surface,
                    contentColor = if (sel) accent else Color.White
                ),
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp)
            )
        }

        item {
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { engine.currentScreen = "mode" },
                colors = ButtonDefaults.buttonColors(backgroundColor = accent),
                modifier = Modifier.fillMaxWidth(0.78f).height(38.dp)
            ) {
                Text(ui.done.uppercase(), color = Color.Black, fontWeight = FontWeight.Black, fontSize = PP.Body)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Modo de sincronizacion
// ─────────────────────────────────────────────────────────────────────

private data class ModeOption(
    val id: String, val emoji: String, val title: String, val subtitle: String
)

@Composable
fun ModeScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    val es = engine.lang == "es"

    val options = listOf(
        ModeOption(SyncProtocol.MODE_SOLO, "⌚", ui.solo,
            if (es) "El reloj va por su cuenta" else "Watch on its own"),
        ModeOption(SyncProtocol.MODE_WATCH, "⌚→📱", ui.watchCtrl,
            if (es) "Puntuas en la muñeca" else "You score on the wrist"),
        ModeOption(SyncProtocol.MODE_PHONE, "📱→⌚", ui.phoneCtrl,
            if (es) "El reloj solo muestra" else "Watch only displays")
    )

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 26.dp)
    ) {
        item { BackRow(engine) { engine.currentScreen = "lang" } }
        item { PPLabel(ui.chooseMode, color = accent, size = PP.Label) }
        item { Spacer(Modifier.height(2.dp)) }

        items(options) { opt ->
            val sel = SyncProtocol.normalizeMode(engine.mode) == opt.id
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .padding(vertical = 2.dp)
                    .clip(PP.CardShape)
                    .background(if (sel) accent.copy(alpha = 0.16f) else PP.Surface)
                    .border(
                        1.dp,
                        if (sel) accent.copy(alpha = 0.55f) else PP.Line,
                        PP.CardShape
                    )
                    .clickable {
                        engine.mode = opt.id
                        engine.saveState()
                        activity.sendSettingsToPhone()
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(opt.emoji, fontSize = 14.sp)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        opt.title.uppercase(),
                        color = if (sel) accent else Color.White,
                        fontSize = PP.Label,
                        fontWeight = FontWeight.Black,
                        maxLines = 1
                    )
                    Text(
                        opt.subtitle,
                        color = PP.TextMuted,
                        fontSize = PP.Micro,
                        maxLines = 2
                    )
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            LinkPill(engine)
            Spacer(Modifier.height(6.dp))
        }

        item {
            Button(
                onClick = {
                    val mode = SyncProtocol.normalizeMode(engine.mode)
                    if (mode == SyncProtocol.MODE_SOLO) {
                        activity.startTimer()
                        engine.currentScreen = "score"
                    } else {
                        engine.currentScreen = "bt"
                    }
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = accent),
                modifier = Modifier.fillMaxWidth(0.78f).height(38.dp)
            ) {
                Text(ui.play.uppercase(), color = Color.Black, fontWeight = FontWeight.Black, fontSize = PP.Body)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Vinculacion con el movil
// ─────────────────────────────────────────────────────────────────────

@Composable
fun PairScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    val es = engine.lang == "es"
    var code by remember { mutableStateOf(listOf<Int>()) }
    var sentFor by remember { mutableStateOf("") }
    var autoTried by remember { mutableStateOf(false) }

    // Cuando el movil acepta, entramos al marcador solos
    LaunchedEffect(PhoneLink.paired) {
        if (PhoneLink.paired) {
            delay(1100)
            activity.startTimer()
            engine.currentScreen = "score"
        }
    }

    // Zero-touch: en cuanto el reloj ve el movil, intenta emparejar solo, sin
    // esperar a que nadie teclee nada. El movil acepta automaticamente si tiene
    // "Vincular sin codigo" activado (lo esta por defecto). Si el movil lo tiene
    // desactivado, esto no hace nada y queda el codigo manual como alternativa.
    LaunchedEffect(PhoneLink.connected, PhoneLink.paired) {
        if (PhoneLink.connected && !PhoneLink.paired && !autoTried) {
            autoTried = true
            activity.sendPairRequest("AUTO")
        }
    }

    // El codigo se manda al completar los 4 digitos, una sola vez por combinacion
    LaunchedEffect(code) {
        if (code.size == 4) {
            val text = code.joinToString("")
            if (text != sentFor) {
                sentFor = text
                activity.sendPairRequest(text)
            }
        }
    }

    val statusText = when {
        PhoneLink.paired -> if (es) "¡VINCULADO!" else "LINKED!"
        PhoneLink.lastError == "codigo" -> if (es) "CODIGO INCORRECTO" else "WRONG CODE"
        !PhoneLink.connected -> if (es) "ABRE LA APP DEL MOVIL" else "OPEN THE PHONE APP"
        autoTried && code.isEmpty() -> if (es) "EMPAREJANDO…" else "PAIRING…"
        code.size == 4 -> if (es) "ESPERANDO AL MOVIL…" else "WAITING FOR PHONE…"
        else -> if (es) "CODIGO DEL MOVIL" else "CODE FROM PHONE"
    }
    val statusColor = when {
        PhoneLink.paired -> accent
        PhoneLink.lastError == "codigo" -> PP.Danger
        !PhoneLink.connected -> PP.Warn
        else -> PP.TextDim
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 24.dp)
    ) {
        item { BackRow(engine) { engine.currentScreen = "mode" } }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                repeat(4) { idx ->
                    val filled = idx < code.size
                    val ch = if (filled) code[idx].toString() else "·"
                    val c = if (filled) accent else PP.Line
                    Box(
                        modifier = Modifier
                            .size(30.dp, 40.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(if (filled) accent.copy(alpha = 0.12f) else PP.Surface)
                            .border(1.dp, c, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            ch,
                            color = if (filled) accent else PP.TextMuted,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        }

        item {
            PPLabel(statusText, color = statusColor, size = PP.Micro)
            Spacer(Modifier.height(4.dp))
        }

        // Teclado numerico
        item {
            Column(modifier = Modifier.padding(bottom = 6.dp)) {
                listOf(
                    listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9), listOf(-1, 0, -2)
                ).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(vertical = 2.dp)
                    ) {
                        row.forEach { digit ->
                            val label = when (digit) {
                                -1 -> "⌫"
                                -2 -> "OK"
                                else -> digit.toString()
                            }
                            val bg = when (digit) {
                                -2 -> accent
                                -1 -> PP.SurfaceHigh
                                else -> PP.Surface
                            }
                            Box(
                                modifier = Modifier
                                    .size(38.dp, 30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable {
                                        when (digit) {
                                            -1 -> if (code.isNotEmpty()) {
                                                code = code.dropLast(1); sentFor = ""
                                            }
                                            -2 -> if (code.size == 4) {
                                                sentFor = ""
                                                activity.sendPairRequest(code.joinToString(""))
                                            }
                                            else -> if (code.size < 4) code = code + digit
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    label,
                                    color = if (digit == -2) Color.Black else Color.White,
                                    fontSize = PP.Body,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Vincular sin codigo: el emparejado Bluetooth ya lo hizo el sistema,
        // el codigo solo evita confundir dos moviles en la misma pista.
        item {
            Chip(
                onClick = { activity.sendPairRequest("AUTO") },
                label = {
                    Text(
                        if (es) "Vincular sin codigo" else "Link without code",
                        fontSize = PP.Label
                    )
                },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = PP.Surface, contentColor = accent
                ),
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp)
            )
        }

        item {
            Chip(
                onClick = {
                    engine.mode = SyncProtocol.MODE_SOLO
                    engine.saveState()
                    activity.startTimer()
                    engine.currentScreen = "score"
                },
                label = { Text(ui.skip, fontSize = PP.Label) },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = PP.Surface, contentColor = PP.TextDim
                ),
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Ajustes
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SettingsScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onBack: () -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    val es = engine.lang == "es"
    var showLangPicker by remember { mutableStateOf(false) }

    if (showLangPicker) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize().background(PP.Bg),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 26.dp)
        ) {
            item { PPLabel(ui.chooseLang, color = accent, size = PP.Label) }
            items(Translations.langs) { l ->
                Chip(
                    onClick = {
                        engine.lang = l.id
                        showLangPicker = false
                        engine.saveState()
                        activity.sendSettingsToPhone()
                    },
                    label = { Text(l.name, fontSize = PP.Body) },
                    icon = { Text(l.flag, fontSize = 16.sp) },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = if (engine.lang == l.id) accent.copy(alpha = 0.18f) else PP.Surface,
                        contentColor = if (engine.lang == l.id) accent else Color.White
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp)
                )
            }
            item {
                Button(
                    onClick = { showLangPicker = false },
                    colors = ButtonDefaults.buttonColors(backgroundColor = PP.SurfaceHigh),
                    modifier = Modifier.padding(top = 6.dp).size(40.dp)
                ) { Text("✕", color = Color.White) }
            }
        }
        return
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 26.dp)
    ) {
        item {
            PPLabel(ui.settings, color = accent, size = PP.Label)
            Spacer(Modifier.height(4.dp))
        }

        // Estado del enlace, primero: es lo que mas se consulta en pista
        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                LinkPill(engine)
                if (PhoneLink.paired) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (PhoneLink.phoneName.isNotEmpty()) PhoneLink.phoneName
                        else if (es) "Movil vinculado" else "Phone linked",
                        color = PP.TextMuted, fontSize = PP.Micro, maxLines = 1
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    CompactChip(
                        onClick = {
                            activity.sendHello()
                            activity.sendSettingsToPhone()
                            activity.pushStateToPhone()
                            activity.pushHealthToPhone()
                        },
                        label = { Text(if (es) "Sincronizar" else "Sync", fontSize = PP.Micro) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = accent, contentColor = Color.Black
                        )
                    )
                    CompactChip(
                        onClick = { engine.currentScreen = "mode" },
                        label = { Text(ui.chMode, fontSize = PP.Micro) },
                        colors = ChipDefaults.primaryChipColors(
                            backgroundColor = PP.SurfaceHigh, contentColor = accent
                        )
                    )
                }
            }
        }

        item {
            SettingChip(ui.voice + ": " + engine.lang.uppercase(), accent) { showLangPicker = true }
        }
        item {
            SettingChip(if (es) "HISTORIAL" else "HISTORY", accent) { engine.currentScreen = "history" }
        }

        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                PPLabel(if (es) "BRILLO" else "BRIGHTNESS", size = PP.Micro)
                InlineSlider(
                    value = engine.brightness,
                    onValueChange = {
                        engine.brightness = it
                        activity.updateBrightness(it)
                        engine.saveState()
                    },
                    valueRange = 10f..255f,
                    steps = 5,
                    increaseIcon = { Text("+", color = accent) },
                    decreaseIcon = { Text("−", color = accent) }
                )
            }
        }

        item { ToggleRow(ui.voice, engine.voiceEnabled, accent) { engine.voiceEnabled = it; engine.saveState() } }
        item {
            ToggleRow(ui.goldenPt, engine.goldenPoint, accent) {
                engine.goldenPoint = it
                engine.saveState()
                activity.sendSettingsToPhone()
            }
        }
        item {
            ToggleRow(ui.superTB, engine.superTb, accent) {
                engine.superTb = it
                engine.saveState()
                activity.sendSettingsToPhone()
            }
        }

        // Mejor de 1 / 3 / 5
        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                PPLabel(ui.bestOf, size = PP.Micro)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5).forEach { n ->
                        val sel = engine.bestOf == n
                        Box(
                            modifier = Modifier
                                .size(34.dp, 26.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (sel) accent else PP.SurfaceHigh)
                                .clickable {
                                    engine.bestOf = n
                                    engine.saveState()
                                    activity.sendSettingsToPhone()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$n",
                                color = if (sel) Color.Black else Color.White,
                                fontSize = PP.Body,
                                fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }

        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                PPLabel(ui.theme, size = PP.Micro)
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ThemeUtils.themesList.forEach { th ->
                        val sel = engine.theme == th
                        Box(
                            modifier = Modifier
                                .size(if (sel) 24.dp else 20.dp)
                                .clip(CircleShape)
                                .background(ThemeUtils.getDotColor(th))
                                .border(
                                    if (sel) 2.dp else 0.dp, Color.White, CircleShape
                                )
                                .clickable {
                                    engine.theme = th
                                    engine.saveState()
                                    activity.sendSettingsToPhone()
                                }
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    engine.resetMatch()
                    activity.resetTimer()
                    activity.onLocalScoreAction("reset")
                    onBack()
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A1212)),
                modifier = Modifier.fillMaxWidth(0.94f).height(34.dp)
            ) {
                Text(ui.newMatch.uppercase(), color = PP.Danger, fontSize = PP.Label, fontWeight = FontWeight.Black)
            }
        }

        item {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(backgroundColor = PP.Surface),
                modifier = Modifier.fillMaxWidth(0.94f).height(34.dp).padding(top = 2.dp)
            ) {
                Text(
                    if (es) "‹ VOLVER" else "‹ BACK",
                    color = accent, fontSize = PP.Label, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SettingChip(label: String, accent: Color, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(label.uppercase(), fontSize = PP.Label) },
        colors = ChipDefaults.primaryChipColors(backgroundColor = PP.Surface, contentColor = accent),
        modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, accent: Color, onCheck: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .padding(vertical = 2.dp)
            .clip(PP.CardShape)
            .background(PP.Surface)
            .clickable { onCheck(!checked) }
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            color = if (checked) Color.White else PP.TextDim,
            fontSize = PP.Micro,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheck,
            colors = SwitchDefaults.colors(checkedThumbColor = accent)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────
// Fin de partido / reanudar / historial
// ─────────────────────────────────────────────────────────────────────

@Composable
fun EndScreen(engine: GameEngine, activity: MainActivity) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    val es = engine.lang == "es"
    val winName = if (engine.winner == "A") engine.nameA else engine.nameB

    var pop by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { pop = true }
    val scale by animateFloatAsState(
        if (pop) 1f else 0.6f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "pop"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(PP.Bg).padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("🏆", fontSize = (34 * scale).sp)
        Spacer(Modifier.height(2.dp))
        Text(
            if (es) "¡GANA ${winName.uppercase()}!" else "${winName.uppercase()} WINS!",
            color = accent,
            fontSize = PP.Title,
            fontWeight = FontWeight.Black,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
        Text(
            "${engine.setsA} – ${engine.setsB}",
            color = Color.White,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black
        )
        PPLabel(
            "${activity.getTimerDisplay()} · ${engine.calories} KCAL",
            color = PP.TextMuted, size = PP.Micro
        )

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                engine.resetMatch()
                activity.resetTimer()
                activity.startTimer()
                activity.onLocalScoreAction("reset")
                engine.currentScreen = "score"
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = accent),
            modifier = Modifier.height(38.dp).fillMaxWidth(0.82f).clip(RoundedCornerShape(19.dp))
        ) {
            Text(ui.newMatch.uppercase(), fontSize = PP.Label, color = Color.Black, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ResumeScreen(engine: GameEngine, activity: MainActivity) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PPLabel(
            if (es) "PARTIDO ANTERIOR" else "PREVIOUS MATCH",
            color = accent, size = PP.Label
        )
        Spacer(Modifier.height(6.dp))

        PPCard(modifier = Modifier.fillMaxWidth(0.92f)) {
            Text(
                "${engine.nameA} · ${engine.nameB}",
                color = Color.White, fontSize = PP.Micro,
                fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${engine.setsA}–${engine.setsB}  ·  ${engine.gamesA}–${engine.gamesB}",
                color = accent, fontSize = PP.Title, fontWeight = FontWeight.Black
            )
        }

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = { activity.startTimer(); engine.currentScreen = "score" },
            colors = ButtonDefaults.buttonColors(backgroundColor = accent),
            modifier = Modifier.height(34.dp).fillMaxWidth(0.9f).clip(RoundedCornerShape(17.dp))
        ) {
            Text(
                if (es) "CONTINUAR" else "CONTINUE",
                color = Color.Black, fontWeight = FontWeight.Black, fontSize = PP.Label
            )
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                engine.resetMatch()
                activity.resetTimer()
                engine.currentScreen = "lang"
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A1212)),
            modifier = Modifier.height(32.dp).fillMaxWidth(0.9f).clip(RoundedCornerShape(16.dp))
        ) {
            Text(
                if (es) "NUEVA PARTIDA" else "NEW MATCH",
                color = PP.Danger, fontWeight = FontWeight.Bold, fontSize = PP.Micro
            )
        }
    }
}

@Composable
fun HistoryScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onBack: () -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"

    val matches = remember {
        val prefs = activity.getSharedPreferences("padel_prefs", Context.MODE_PRIVATE)
        val json = prefs.getString("match_history", "[]") ?: "[]"
        try {
            val array = org.json.JSONArray(json)
            // Ya se guardan con el mas reciente primero
            (0 until array.length()).map { array.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    val summary = remember { engine.historySummary() }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 26.dp)
    ) {
        item {
            PPLabel(if (es) "HISTORIAL" else "HISTORY", color = accent, size = PP.Label)
            Spacer(Modifier.height(4.dp))
        }

        if (matches.isEmpty()) {
            item {
                PPLabel(
                    if (es) "AUN NO HAY PARTIDOS" else "NO MATCHES YET",
                    color = PP.TextMuted, size = PP.Micro
                )
            }
        } else {
            // Balance general: jugados, ganados y porcentaje
            item {
                PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HistoryStat(summary.first.toString(), if (es) "JUGADOS" else "PLAYED", Color.White)
                        Box(Modifier.width(1.dp).height(18.dp).background(PP.Line))
                        HistoryStat(summary.second.toString(), if (es) "GANADOS" else "WON", accent)
                        Box(Modifier.width(1.dp).height(18.dp).background(PP.Line))
                        HistoryStat("${summary.third}%", if (es) "RATIO" else "WIN %", accent)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            items(matches) { m ->
                val won = m.optString("winner", "") == "A"
                val dur = m.optInt("duration", 0)
                val date = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
                    .format(java.util.Date(m.optLong("date", 0L)))
                PPCard(
                    modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                    accent = if (won) accent else null
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "${m.optString("teamA", "").uppercase()} · ${m.optString("teamB", "").uppercase()}",
                                color = PP.TextDim, fontSize = PP.Micro,
                                fontWeight = FontWeight.Bold, maxLines = 1
                            )
                            Text(
                                "$date · ${formatWatchDuration(dur)}",
                                color = PP.TextMuted, fontSize = PP.Micro, maxLines = 1
                            )
                        }
                        Text(
                            "${m.optInt("scoreA", 0)}–${m.optInt("scoreB", 0)}",
                            color = if (won) accent else PP.TextDim,
                            fontSize = PP.Title, fontWeight = FontWeight.Black
                        )
                    }
                    // Juegos y desgaste, si se guardaron
                    val games = "${m.optInt("gamesA", 0)}-${m.optInt("gamesB", 0)}"
                    val kcal = m.optInt("kcal", 0)
                    if (kcal > 0 || games != "0-0") {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            buildString {
                                if (games != "0-0") append(if (es) "Juegos $games" else "Games $games")
                                if (kcal > 0) {
                                    if (isNotEmpty()) append(" · ")
                                    append("$kcal kcal")
                                }
                            },
                            color = PP.TextMuted, fontSize = PP.Micro, maxLines = 1
                        )
                    }
                }
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(backgroundColor = PP.Surface),
                modifier = Modifier.fillMaxWidth(0.94f).height(34.dp)
            ) {
                Text(
                    if (es) "‹ VOLVER" else "‹ BACK",
                    color = accent, fontSize = PP.Label, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HistoryStat(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = PP.Label, fontWeight = FontWeight.Black)
        PPLabel(label, size = PP.Micro)
    }
}

/** mm:ss o h:mm si el partido paso de la hora. */
fun formatWatchDuration(seconds: Int): String {
    if (seconds <= 0) return "--"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}
