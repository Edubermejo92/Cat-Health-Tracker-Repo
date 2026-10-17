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
import padelpulseapp2.netlify.app.sync.CloudHistory
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.WatchAccount
import padelpulseapp2.netlify.app.sync.SyncProtocol
import padelpulseapp2.netlify.app.ui.Lexend
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPCard
import padelpulseapp2.netlify.app.ui.PPChip
import padelpulseapp2.netlify.app.ui.iconSp
import padelpulseapp2.netlify.app.ui.isLargeFont
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
    val scoreState = rememberScalingLazyListState()
    val historyState = rememberScalingLazyListState()
    val inviteState = rememberScalingLazyListState()
    val nameState = rememberScalingLazyListState()
    // Tambien las pantallas cortas son listas: con la letra del sistema al
    // maximo ya no caben enteras, y Play rechazo la app por texto cortado en
    // los bordes. Asi se pueden desplazar y llevan su indicador.
    val accountState = rememberScalingLazyListState()
    val splashState = rememberScalingLazyListState()
    val resumeState = rememberScalingLazyListState()
    val endState = rememberScalingLazyListState()

    // El indicador sigue a la lista de la pantalla visible.
    //
    // Dentro de "score" hay listas que se abren encima (el
    // editor de nombre, el selector de idioma): esas avisan aqui cual es la
    // suya con onActiveList, y mientras estan abiertas mandan sobre la lista
    // de la pantalla. Sin este aviso, Play detecta esas listas como "sin
    // barra de desplazamiento" porque el indicador se quedaba pegado a la de
    // fuera.
    var subActive by remember { mutableStateOf<androidx.wear.compose.foundation.lazy.ScalingLazyListState?>(null) }
    val disclosureState = rememberScalingLazyListState()
    val activeState = if (activity.showHealthDisclosure) disclosureState else subActive ?: when (engine.currentScreen) {
        "lang" -> langState
        "score" -> scoreState
        "history" -> historyState
        "invite" -> inviteState
        "account" -> accountState
        "resume" -> resumeState
        "end" -> endState
        else -> splashState
    }

    MaterialTheme(
        colors = MaterialTheme.colors.copy(
            primary = accent,
            background = PP.Bg,
            surface = PP.Surface,
            onPrimary = PP.OnAccent,
            onBackground = PP.TextBright,
            onSurface = PP.TextBright,
            error = PP.Danger
        ),
        // Lexend en toda la app, como el movil
        typography = Typography(defaultFontFamily = Lexend)
    ) {
        Scaffold(
            timeText = { if (engine.currentScreen != "splash") TimeText() },
            // En el marcador no: oscureceria la banda de FALTA pegada al borde
            vignette = {
                if (engine.currentScreen != "score") Vignette(vignettePosition = VignettePosition.TopAndBottom)
            },
            positionIndicator = {
                activeState?.let { PositionIndicator(scalingLazyListState = it) }
            }
        ) {
            Box(modifier = Modifier.fillMaxSize().background(PP.Bg)) {
                // El aviso del pulso va antes que nada y se pide el permiso
                // solo si la persona acepta.
                if (activity.showHealthDisclosure) {
                    HealthDisclosureScreen(
                        lang = engine.lang,
                        listState = disclosureState,
                        onPrivacy = {
                            runCatching {
                                activity.startActivity(android.content.Intent(activity, PrivacyPolicyActivity::class.java))
                            }
                        },
                        onAnswer = { activity.answerHealthDisclosure(it) }
                    )
                } else Crossfade(targetState = engine.currentScreen, label = "nav") { current ->
                    when (current) {
                        "account" -> AccountScreen(engine, activity, accountState)
                        "splash" -> SplashScreen(engine, activity, splashState)
                        "resume" -> ResumeScreen(engine, activity, resumeState)
                        "lang" -> LangScreen(engine, activity, langState)
                        // Ya no hay vinculacion con codigo: Wear OS solo deja
                        // hablar a las dos apps del mismo emparejamiento.
                        "bt" -> LaunchedEffect(Unit) { engine.goToPage(3) }
                        "score" -> ScoreScreen(
                            engine, activity, scoreState, nameState,
                            onInvite = { engine.currentScreen = "invite" },
                            onEnd = { engine.currentScreen = "end" },
                            onHistory = { engine.currentScreen = "history" },
                            onActiveList = { subActive = it }
                        )
                        // Ya no hay pantalla de Ajustes aparte: es la pagina 3
                        // del marcador. Por si algo antiguo pide "settings".
                        "settings" -> LaunchedEffect(Unit) { engine.goToPage(2) }
                        // Cada pantalla vuelve a la pagina desde la que se abrio
                        "history" -> HistoryScreen(engine, activity, historyState) { engine.goToPage(3) }
                        "invite" -> InviteScreen(engine, activity, inviteState) { engine.goToPage(2) }
                        "end" -> EndScreen(engine, activity, endState)
                        else -> SplashScreen(engine, activity, splashState)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Estado del enlace, reutilizado en varias pantallas
// ─────────────────────────────────────────────────────────────────────

/** Proporcion ancho/alto de res/drawable-nodpi/logo_wordmark (470 x 240). */
const val LOGO_RATIO = 470f / 240f

/**
 * Estado del enlace en una palabra. Nada de rojo: jugar sin movil es normal y
 * el marcador funciona igual. El detalle (que falla y como arreglarlo) esta
 * en Sincronizacion.
 */
@Composable
fun linkLabel(engine: GameEngine): String {
    val es = engine.lang == "es"
    return when (PhoneLink.link) {
        PhoneLink.Link.SYNCED -> if (es) "SINCRONIZADO" else "SYNCED"
        PhoneLink.Link.WAITING -> if (es) "BUSCANDO PADELPULSE…" else "LOOKING FOR PADELPULSE…"
        PhoneLink.Link.NO_APP -> if (es) "FALTA LA APP EN EL MÓVIL" else "APP MISSING ON PHONE"
        PhoneLink.Link.NO_PHONE -> if (es) "SOLO RELOJ" else "WATCH ONLY"
    }
}

@Composable
fun linkColor(engine: GameEngine): Color = when (PhoneLink.link) {
    PhoneLink.Link.SYNCED -> ThemeUtils.getColor(engine.theme)
    PhoneLink.Link.NO_APP -> PP.Warn
    else -> PP.TextMuted
}

@Composable
fun LinkPill(engine: GameEngine, modifier: Modifier = Modifier) {
    PPStatusPill(
        label = linkLabel(engine),
        color = linkColor(engine),
        pulsing = PhoneLink.link == PhoneLink.Link.SYNCED,
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
fun AccountScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"

    // En cuanto llega la sesion del movil, seguimos solos
    LaunchedEffect(WatchAccount.signedIn) {
        if (WatchAccount.signedIn) {
            delay(1200)
            engine.currentScreen = "splash"
        }
    }

    // Lista y no columna fija: con la letra grande el texto ocupa el doble y
    // una columna centrada se salia por arriba y por abajo, cortada por el
    // bisel (motivo del rechazo de Play). En la lista se desplaza con el dedo
    // o la corona y nada queda fuera.
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.logo_wordmark),
                    contentDescription = "PadelPulse Live",
                    modifier = Modifier.fillMaxWidth(0.46f).aspectRatio(LOGO_RATIO).padding(bottom = 2.dp)
                )
                PPLabel(
                    if (WatchAccount.signedIn) (if (es) "SESION INICIADA" else "SIGNED IN")
                    else (if (es) "TU CUENTA" else "YOUR ACCOUNT"),
                    color = accent, size = PP.Label
                )
            }
        }

        if (WatchAccount.signedIn) {
            item {
                Text(
                    WatchAccount.name.ifEmpty { WatchAccount.email },
                    color = PP.TextBright, fontSize = PP.Body,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
            }
        } else {
            item {
                Text(
                    if (es) "Inicia sesion en PadelPulse del movil y el reloj entra solo."
                    else "Sign in on the phone app and the watch follows automatically.",
                    color = PP.TextDim, fontSize = PP.Micro, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item {
                PPChip(
                    if (es) "JUGAR SIN CUENTA" else "PLAY WITHOUT ACCOUNT",
                    onClick = {
                        WatchAccount.skip(activity)
                        engine.currentScreen = "splash"
                    },
                    content = accent, weight = FontWeight.Black, center = true
                )
            }
            item { LinkPill(engine, Modifier.padding(top = 4.dp)) }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────
// Splash
// ─────────────────────────────────────────────────────────────────────

@Composable
fun SplashScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val fade by animateFloatAsState(if (visible) 1f else 0f, tween(700), label = "fade")
    val logoScale by animateFloatAsState(if (visible) 1f else 0.85f, tween(700, easing = FastOutSlowInEasing), label = "scale")

    // Arranque corto: el logo un momento y al marcador, sin botones ni
    // preguntas. El idioma es español salvo que se cambie en Ajustes (o llegue
    // otro del movil), asi que no se pregunta cada vez. Si hay un partido a
    // medias, antes la pantalla de Continuar / Nueva partida.
    LaunchedEffect(Unit) {
        delay(1200)
        if (engine.hasSavedMatch()) {
            engine.currentScreen = "resume"
        } else {
            activity.startTimer()
            engine.currentScreen = "score"
        }
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().alpha(fade).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        // El mismo logo que la cabecera del movil
        item {
            Image(
                painter = painterResource(id = R.drawable.logo_wordmark),
                contentDescription = "PadelPulse Live",
                modifier = Modifier
                    .fillMaxWidth(0.72f * logoScale)
                    .aspectRatio(LOGO_RATIO)
                    .padding(bottom = 4.dp)
            )
        }
        if (PhoneLink.link == PhoneLink.Link.SYNCED) item { LinkPill(engine, Modifier.padding(vertical = 4.dp)) }
        // Por si alguien no quiere esperar el segundo del logo
        item {
            PPChip(
                ui.start.uppercase(),
                onClick = {
                    if (engine.hasSavedMatch()) engine.currentScreen = "resume"
                    else { activity.startTimer(); engine.currentScreen = "score" }
                },
                modifier = Modifier.fillMaxWidth(0.84f).padding(top = 4.dp),
                background = accent, content = PP.OnAccent,
                fontSize = PP.Body, weight = FontWeight.Black, center = true
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
        modifier = Modifier.fillMaxSize().rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding(squareVertical = 28.dp)
    ) {
        item { PPLabel(ui.chooseLang, color = accent, size = PP.Label) }
        item { Spacer(Modifier.height(4.dp)) }

        items(Translations.langs) { l ->
            val sel = engine.lang == l.id
            PPChip(
                l.name,
                onClick = {
                    engine.lang = l.id
                    engine.saveState()
                    activity.sendSettingsToPhone()
                },
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp),
                background = if (sel) accent.copy(alpha = 0.18f) else PP.Surface,
                content = if (sel) accent else PP.TextBright,
                icon = l.flag, fontSize = PP.Body,
                weight = if (sel) FontWeight.Bold else FontWeight.Normal
            )
        }

        item {
            PPChip(
                ui.done.uppercase(),
                onClick = { activity.startTimer(); engine.currentScreen = "score" },
                modifier = Modifier.fillMaxWidth(0.84f).padding(top = 8.dp),
                background = accent, content = PP.OnAccent,
                fontSize = PP.Body, weight = FontWeight.Black, center = true
            )
        }
    }
}

/** Volumen de la voz, en Ajustes. */
@Composable
fun VoiceVolumeCard(engine: GameEngine, activity: MainActivity, accent: Color) {
    val es = engine.lang == "es"
    PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
        PPLabel(if (es) "VOLUMEN VOZ" else "VOICE VOLUME", size = PP.Micro)
        InlineSlider(
            value = activity.voiceLevel.toFloat(),
            onValueChange = { activity.changeVoiceLevel(Math.round(it)) },
            valueRange = 0f..5f,
            steps = 4,
            increaseIcon = { Text("+", color = accent) },
            decreaseIcon = { Text("−", color = accent) }
        )
    }
}

@Composable
fun SettingChip(label: String, accent: Color, onClick: () -> Unit) {
    PPChip(
        label.uppercase(), onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
        content = accent, weight = FontWeight.Normal
    )
}

/** "‹ VOLVER" al final de las listas, igual en todas. */
@Composable
fun BackChip(engine: GameEngine, accent: Color, onBack: () -> Unit) {
    PPChip(
        if (engine.lang == "es") "‹ VOLVER" else "‹ BACK",
        onClick = onBack,
        modifier = Modifier.fillMaxWidth(0.94f).padding(top = 4.dp),
        content = accent, center = true
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
            .heightIn(min = 48.dp)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label.uppercase(),
            color = if (checked) PP.TextBright else PP.TextDim,
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
fun EndScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
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

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🏆", fontSize = (30 * scale).sp)
                Text(
                    if (es) "¡GANA ${winName.uppercase()}!" else "${winName.uppercase()} WINS!",
                    color = accent,
                    fontSize = PP.Title,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            // Todo visto desde quien gana: 2-0 y 6-0 6-0, no 0-2 y 0-6 0-6
            val ganaA = engine.winner != "B"
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (ganaA) "${engine.setsA} – ${engine.setsB}" else "${engine.setsB} – ${engine.setsA}",
                    color = PP.TextBright,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
                // El resultado de cada set, que es lo que se cuenta despues
                if (engine.setScores.isNotEmpty()) {
                    Text(
                        engine.setScores.joinToString("  ·  ") { if (ganaA) "${it.a}-${it.b}" else "${it.b}-${it.a}" },
                        color = PP.TextDim, fontSize = PP.Body, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
                PPLabel(
                    "${activity.getTimerDisplay()} · ${engine.calories} KCAL",
                    color = PP.TextMuted, size = PP.Micro
                )
            }
        }
        item {
            PPChip(
                ui.newMatch.uppercase(),
                onClick = {
                    // El partido terminado ya se guardo al acabar
                    activity.startNewMatch()
                    engine.currentScreen = "score"
                },
                modifier = Modifier.fillMaxWidth(0.88f).padding(top = 8.dp),
                background = accent, content = PP.OnAccent,
                weight = FontWeight.Black, center = true
            )
        }
    }
}

@Composable
fun ResumeScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(id = R.drawable.logo_wordmark),
                    contentDescription = "PadelPulse Live",
                    modifier = Modifier.fillMaxWidth(0.42f).aspectRatio(LOGO_RATIO).padding(bottom = 2.dp)
                )
                PPLabel(
                    if (es) "PARTIDO ANTERIOR" else "PREVIOUS MATCH",
                    color = accent, size = PP.Label
                )
            }
        }
        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 4.dp)) {
                Text(
                    "${engine.nameA} · ${engine.nameB}",
                    color = PP.TextBright, fontSize = PP.Micro,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${engine.setsA}–${engine.setsB}  ·  ${engine.gamesA}–${engine.gamesB}",
                    color = accent, fontSize = PP.Title, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
            }
        }
        item {
            PPChip(
                if (es) "CONTINUAR" else "CONTINUE",
                onClick = { activity.startTimer(); engine.currentScreen = "score" },
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp),
                background = accent, content = PP.OnAccent,
                weight = FontWeight.Black, center = true
            )
        }
        item {
            PPChip(
                if (es) "NUEVA PARTIDA" else "NEW MATCH",
                onClick = {
                    // El partido a medias se guarda en el historial y se va
                    // directo al marcador: el idioma ya no se pregunta
                    activity.startNewMatch()
                    engine.currentScreen = "score"
                },
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp),
                background = Color(0xFF2A1212), content = PP.Danger, center = true
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

    // El historial es de quien tiene cuenta, igual que en el movil. El reloj
    // no registra a nadie -no se teclean contraseñas en la muñeca-, asi que
    // la sesion tiene que llegar del movil.
    if (!WatchAccount.signedIn) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = roundSafePadding()
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🔒", fontSize = 22.sp)
                    PPLabel(if (es) "HACE FALTA CUENTA" else "ACCOUNT NEEDED", color = accent, size = PP.Label)
                }
            }
            item {
                Text(
                    if (es) "Inicia sesion en PadelPulse del movil y el historial aparece aqui solo."
                    else "Sign in on the phone app and your history shows up here on its own.",
                    color = PP.TextDim, fontSize = PP.Micro, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
            item { BackChip(engine, accent, onBack) }
        }
        return
    }

    // El historial de la cuenta, que manda el movil ya sincronizado con la
    // nube, mas los partidos guardados en este reloj que aun no estan en el:
    // por ejemplo, uno a medias guardado al pulsar Nuevo partido. Si la cuenta
    // no ha mandado nada todavia, solo los del reloj.
    val cloud = remember(CloudHistory.json) { CloudHistory.matches() }
    val fromCloud = CloudHistory.fromAccount || cloud.isNotEmpty()
    val local = remember {
        val prefs = activity.getSharedPreferences("padel_prefs", Context.MODE_PRIVATE)
        try {
            val array = org.json.JSONArray(prefs.getString("match_history", "[]") ?: "[]")
            (0 until array.length()).map { array.getJSONObject(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    val soloReloj = remember(cloud, local, fromCloud) {
        if (fromCloud) local.filter { l -> cloud.none { c -> sameHistoryMatch(c, l) } } else local
    }
    val matches = remember(cloud, soloReloj, fromCloud) {
        if (fromCloud) cloud + soloReloj else local
    }
    val summary = remember(CloudHistory.json, CloudHistory.played, CloudHistory.won, fromCloud, soloReloj) {
        if (fromCloud) {
            val played = maxOf(CloudHistory.played, cloud.size) + soloReloj.size
            val won = CloudHistory.won + soloReloj.count { it.optString("winner", "") == "A" }
            Triple(played, won, if (played > 0) won * 100 / played else 0)
        } else engine.historySummary()
    }
    val porDia = remember(matches, engine.lang) { groupMatchesByDay(matches, engine.lang) }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PPLabel(if (es) "HISTORIAL" else "HISTORY", color = accent, size = PP.Label)
                PPLabel(
                    when {
                        fromCloud -> if (es) "DE TU CUENTA" else "FROM YOUR ACCOUNT"
                        else -> if (es) "DE ESTE RELOJ" else "ON THIS WATCH"
                    },
                    color = PP.TextMuted, size = PP.Micro
                )
                Spacer(Modifier.height(4.dp))
            }
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
                    // Con la letra grande las tres cifras no caben en fila sin
                    // partir las palabras: van una debajo de otra
                    if (isLargeFont()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            HistoryStat(summary.first.toString(), if (es) "JUGADOS" else "PLAYED", PP.TextBright)
                            HistoryStat(summary.second.toString(), if (es) "GANADOS" else "WON", accent)
                            HistoryStat("${summary.third}%", if (es) "RATIO" else "WIN %", accent)
                        }
                    } else Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HistoryStat(summary.first.toString(), if (es) "JUGADOS" else "PLAYED", PP.TextBright)
                        Box(Modifier.width(1.dp).height(18.dp).background(PP.Line))
                        HistoryStat(summary.second.toString(), if (es) "GANADOS" else "WON", accent)
                        Box(Modifier.width(1.dp).height(18.dp).background(PP.Line))
                        HistoryStat("${summary.third}%", if (es) "RATIO" else "WIN %", accent)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            // Por dias: una cabecera con la fecha y debajo los partidos de ese
            // dia, del mas reciente al mas antiguo.
            porDia.forEach { (dia, lista) ->
                item {
                    PPLabel(
                        dia, color = accent, size = PP.Micro,
                        modifier = Modifier.padding(top = 6.dp, bottom = 1.dp)
                    )
                }
                items(lista) { m -> HistoryMatchCard(m, accent, es) }
            }
        }

        item { BackChip(engine, accent, onBack) }
    }
}

/**
 * Agrupa los partidos por dia, del mas reciente al mas antiguo. Cada grupo
 * lleva su cabecera ya escrita: HOY, AYER o la fecha ("MAR 14 OCT").
 */
fun groupMatchesByDay(matches: List<org.json.JSONObject>, lang: String): List<Pair<String, List<org.json.JSONObject>>> {
    val es = lang == "es"
    val locale = java.util.Locale.forLanguageTag(lang)
    val key = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.ROOT)
    val cal = java.util.Calendar.getInstance()
    val hoy = key.format(cal.time)
    cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
    val ayer = key.format(cal.time)
    val esteAno = java.text.SimpleDateFormat("yyyy", java.util.Locale.ROOT).format(java.util.Date())
    val conAno = java.text.SimpleDateFormat("EEE d MMM yyyy", locale)
    val sinAno = java.text.SimpleDateFormat("EEE d MMM", locale)

    return matches
        .sortedByDescending { it.optLong("date", 0L) }
        .groupBy { m ->
            val t = m.optLong("date", 0L)
            if (t <= 0L) "" else key.format(java.util.Date(t))
        }
        .map { (dia, lista) ->
            val titulo = when {
                dia.isEmpty() -> if (es) "SIN FECHA" else "NO DATE"
                dia == hoy -> if (es) "HOY" else "TODAY"
                dia == ayer -> if (es) "AYER" else "YESTERDAY"
                else -> {
                    val d = java.util.Date(lista.first().optLong("date", 0L))
                    val f = if (dia.startsWith(esteAno)) sinAno else conAno
                    f.format(d).replace(".", "")
                }
            }
            titulo.uppercase(locale) to lista
        }
}

/**
 * El mismo partido visto desde la cuenta y desde el reloj: mismas parejas y
 * jugado a menos de 20 minutos (cada lado apunta la hora a su manera).
 */
fun sameHistoryMatch(a: org.json.JSONObject, b: org.json.JSONObject): Boolean {
    fun n(o: org.json.JSONObject, k: String) = o.optString(k, "").trim().uppercase()
    val cerca = kotlin.math.abs(a.optLong("date", 0L) - b.optLong("date", 0L)) <= 20 * 60 * 1000L
    return cerca && n(a, "teamA") == n(b, "teamA") && n(a, "teamB") == n(b, "teamB")
}

/** Un partido del historial: parejas, resultado, juegos, duracion y kcal. */
@Composable
private fun HistoryMatchCard(m: org.json.JSONObject, accent: Color, es: Boolean) {
    val won = m.optString("winner", "") == "A"
    val dur = m.optInt("duration", 0)
    val t = m.optLong("date", 0L)
    val hora = if (t > 0L) java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(t)) else ""
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
                    fontWeight = FontWeight.Bold
                )
                Text(
                    listOf(hora, formatWatchDuration(dur)).filter { it.isNotEmpty() }.joinToString(" · "),
                    color = PP.TextMuted, fontSize = PP.Micro
                )
            }
            // Resultado en sets
            Text(
                "${m.optInt("scoreA", 0)}–${m.optInt("scoreB", 0)}",
                color = if (won) accent else PP.TextDim,
                fontSize = PP.Title, fontWeight = FontWeight.Black,
                maxLines = 1, softWrap = false,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        // Juegos y desgaste, si se guardaron
        val games = "${m.optInt("gamesA", 0)}-${m.optInt("gamesB", 0)}"
        val kcal = m.optInt("kcal", 0)
        // Guardado a medias al empezar otro partido
        val aMedias = m.has("finished") && !m.optBoolean("finished", true)
        if (kcal > 0 || games != "0-0" || aMedias) {
            Spacer(Modifier.height(2.dp))
            Text(
                buildString {
                    if (games != "0-0") append(if (es) "Juegos $games" else "Games $games")
                    if (kcal > 0) {
                        if (isNotEmpty()) append(" · ")
                        append("$kcal kcal")
                    }
                    if (aMedias) {
                        if (isNotEmpty()) append(" · ")
                        append(if (es) "sin terminar" else "unfinished")
                    }
                },
                color = PP.TextMuted, fontSize = PP.Micro
            )
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
