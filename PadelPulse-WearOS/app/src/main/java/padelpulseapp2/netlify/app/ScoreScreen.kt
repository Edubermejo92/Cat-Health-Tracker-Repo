package padelpulseapp2.netlify.app

import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.SyncProtocol
import padelpulseapp2.netlify.app.sync.WatchAccount
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPCard
import padelpulseapp2.netlify.app.ui.PPChip
import padelpulseapp2.netlify.app.ui.PPLabel
import padelpulseapp2.netlify.app.ui.iconSp

@Composable
fun ScoreScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    nameState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onInvite: () -> Unit,
    onEnd: () -> Unit,
    onHistory: () -> Unit,
    onActiveList: (androidx.wear.compose.foundation.lazy.ScalingLazyListState?) -> Unit = {}
) {
    var showPicker by remember { mutableStateOf<String?>(null) }
    var editingTeam by remember { mutableStateOf<String?>(null) }
    var confirmNew by remember { mutableStateOf(false) }
    var showLanguage by remember { mutableStateOf(false) }
    var pageList by remember { mutableStateOf<androidx.wear.compose.foundation.lazy.ScalingLazyListState?>(null) }
    val pickerState = rememberScalingLazyListState()
    val confirmState = rememberScalingLazyListState()
    val languageState = rememberScalingLazyListState()
    val syncState = rememberScalingLazyListState()
    val serveState = rememberScalingLazyListState()
    // Quien saca, al empezar un partido nuevo. Si ya se ha jugado algun punto
    // (por voz o desde el movil) la pregunta sobra y no sale.
    val chooseServe = engine.askServe && !engine.hasSavedMatch()

    // Guarda el partido jugado en el historial y empieza otro (MainActivity)
    fun startNewMatch() = activity.startNewMatch()

    // Nuevo partido. Si ya hay algo jugado, se pregunta antes: un toque sin
    // querer no puede borrar un partido a medias.
    fun askNewMatch() {
        if (engine.hasSavedMatch()) confirmNew = true else startNewMatch()
    }

    LaunchedEffect(engine.over) { if (engine.over) onEnd() }

    // Cada subpantalla (nombre, sets/juegos, confirmacion, idioma) tiene su
    // propia lista, y las paginas de Ajustes y Sincronizacion la suya. Hay que
    // decirle al indicador de desplazamiento cual es la visible de verdad: sin
    // esto Play rechaza la app por "falta la barra de desplazamiento".
    LaunchedEffect(editingTeam, showPicker, confirmNew, showLanguage, pageList, chooseServe) {
        onActiveList(
            when {
                editingTeam != null -> nameState
                showPicker != null -> pickerState
                confirmNew -> confirmState
                showLanguage -> languageState
                chooseServe -> serveState
                else -> pageList
            }
        )
    }
    DisposableEffect(Unit) { onDispose { onActiveList(null) } }

    val editing = editingTeam
    val picker = showPicker
    if (editing != null) {
        NameEditorScreen(editing, engine, activity, nameState) { editingTeam = null }
        return
    }
    if (picker != null) {
        ScorePicker(picker, engine, activity, pickerState) { showPicker = null }
        return
    }
    if (confirmNew) {
        ConfirmNewMatchScreen(
            engine, confirmState,
            onConfirm = { confirmNew = false; startNewMatch() },
            onCancel = { confirmNew = false }
        )
        return
    }
    if (showLanguage) {
        LanguagePickerScreen(engine, activity, languageState) { showLanguage = false }
        return
    }
    if (chooseServe) {
        ServeChooserScreen(engine, activity, serveState)
        return
    }

    ScorePager(
        engine = engine,
        activity = activity,
        settingsState = listState,
        syncState = syncState,
        onEditName = { editingTeam = it },
        onPicker = { showPicker = it },
        onNewMatch = { askNewMatch() },
        onLanguage = { showLanguage = true },
        onInvite = onInvite,
        onHistory = onHistory,
        onPageList = { pageList = it }
    )
}

/**
 * Idioma, desde Ajustes. Español de serie; aqui se cambia y se manda al
 * movil, que lo aplica tambien.
 */
@Composable
fun LanguagePickerScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onClose: () -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item { PPLabel(ui.chooseLang, color = accent, size = PP.Label) }
        items(Translations.langs) { l ->
            val sel = engine.lang == l.id
            PPChip(
                l.name,
                onClick = {
                    engine.lang = l.id
                    engine.persist()
                    activity.sendSettingsToPhone()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp),
                background = if (sel) accent.copy(alpha = 0.18f) else PP.Surface,
                content = if (sel) accent else PP.TextBright,
                icon = l.flag, fontSize = PP.Body,
                weight = if (sel) FontWeight.Bold else FontWeight.Normal
            )
        }
        item {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(backgroundColor = PP.SurfaceHigh),
                modifier = Modifier.padding(top = 6.dp).size(48.dp)
            ) { Text("✕", color = PP.TextBright, fontSize = iconSp(18.dp)) }
        }
    }
}

/**
 * ¿Quien saca? Sale al empezar un partido nuevo: el saque decide el cuadro y
 * lo que canta la voz, y antes habia que acordarse de tocar el nombre de la
 * pareja. Un toque y al marcador; "Mas tarde" deja el saque a la pareja A y
 * se puede cambiar tocando su nombre como siempre.
 */
@Composable
fun ServeChooserScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"

    fun choose(team: String) {
        engine.serving = team
        engine.faultCount = 0
        engine.askServe = false
        engine.persist()
        engine.speakServe(team)
        activity.onLocalScoreAction("serve", team)
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PPLabel(if (es) "¿QUIÉN SACA?" else "WHO SERVES?", color = accent, size = PP.Label)
                PPLabel(if (es) "PRIMER JUEGO" else "FIRST GAME", color = PP.TextMuted, size = PP.Micro)
                Spacer(Modifier.height(4.dp))
            }
        }
        for (team in listOf("A", "B")) {
            item {
                val players = engine.players(team)
                PPChip(
                    engine.getName(team).uppercase(),
                    onClick = { choose(team) },
                    modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                    secondary = if (players.isNotEmpty()) players.joinToString(" · ") else null,
                    icon = "🎾",
                    fontSize = PP.Body,
                    weight = FontWeight.Black
                )
            }
        }
        item {
            PPChip(
                if (es) "MÁS TARDE" else "LATER",
                onClick = { engine.askServe = false },
                modifier = Modifier.fillMaxWidth(0.7f).padding(top = 4.dp),
                content = PP.TextDim, center = true
            )
        }
    }
}

/** Confirmacion antes de borrar un partido a medias. */
@Composable
fun ConfirmNewMatchScreen(
    engine: GameEngine,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item { PPLabel(if (es) "¿NUEVO PARTIDO?" else "NEW MATCH?", color = PP.Danger, size = PP.Label) }
        // El partido que se va a guardar, en la misma tarjeta que Continuar
        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 4.dp)) {
                Text(
                    "${engine.nameA} · ${engine.nameB}",
                    color = PP.TextDim, fontSize = PP.Micro,
                    fontWeight = FontWeight.Bold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "${engine.setsA}–${engine.setsB}  ·  ${engine.gamesA}–${engine.gamesB}",
                    color = accent, fontSize = PP.Title, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                PPLabel(
                    (if (es) "SETS · JUEGOS" else "SETS · GAMES"),
                    color = PP.TextMuted, size = PP.Micro
                )
            }
        }
        item {
            Text(
                if (es) "Este partido se guarda en el historial con su resultado, la fecha y las parejas, y empieza uno nuevo."
                else "This match is saved to history with its score, date and teams, and a new one starts.",
                color = PP.TextDim, fontSize = PP.Micro, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f).padding(bottom = 4.dp)
            )
        }
        item {
            PPChip(
                if (es) "SÍ, GUARDAR Y EMPEZAR" else "YES, SAVE AND START", onClick = onConfirm,
                modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                background = Color(0xFF2A1212), content = PP.Danger,
                weight = FontWeight.Black, center = true
            )
        }
        item {
            PPChip(
                if (es) "NO, SEGUIR JUGANDO" else "NO, KEEP PLAYING", onClick = onCancel,
                modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                background = accent, content = PP.OnAccent,
                weight = FontWeight.Black, center = true
            )
        }
    }
}

@Composable
fun NameEditorScreen(
    team: String,
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onClose: () -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val es = engine.lang == "es"
    // El primer atajo es tu nombre si la sesion ya lo trajo: la pareja A eres tu.
    val presets = listOf(
        WatchAccount.name.trim().uppercase().ifEmpty { "YO" },
        "RIVAL", "LOCAL", "VISITA", "PAREJA A", "PAREJA B"
    )

    val namesHistory = remember {
        val prefs = activity.getSharedPreferences("padel_prefs", Context.MODE_PRIVATE)
        try {
            val array = org.json.JSONArray(prefs.getString("names_history", "[]") ?: "[]")
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // team: "A"/"B" para el nombre de la pareja, "A1".."B2" para un jugador
    val isPlayer = team.length == 2
    fun apply(name: String) {
        // Los jugadores se guardan como se dicen ("Edu"); la pareja, en mayusculas
        engine.setName(team, if (isPlayer) name.trim().lowercase().replaceFirstChar { it.titlecase() } else name)
        engine.saveState()
        activity.sendSettingsToPhone()
        activity.pushStateToPhone()
        onClose()
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            PPLabel(
                if (isPlayer) (if (es) "PAREJA ${team[0]} · JUGADOR ${team[1]}" else "TEAM ${team[0]} · PLAYER ${team[1]}")
                else (if (es) "NOMBRE PAREJA $team" else "TEAM $team NAME"),
                color = accent, size = PP.Label
            )
            Spacer(Modifier.height(4.dp))
        }

        item {
            PPChip(
                if (es) "Dictar 🎙" else "Dictate 🎙",
                onClick = {
                    activity.startSpeechToText { text ->
                        if (text.isNotBlank()) apply(if (isPlayer) text else text.uppercase()) else onClose()
                    }
                },
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp),
                background = accent, content = PP.OnAccent, fontSize = PP.Body
            )
        }

        if (namesHistory.isNotEmpty()) {
            item { PPLabel(if (es) "RECIENTES" else "RECENT", size = PP.Micro) }
            items(namesHistory) { n ->
                PPChip(
                    n, onClick = { apply(n) },
                    modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 1.dp),
                    content = Color.LightGray, weight = FontWeight.Normal
                )
            }
        }

        item { PPLabel("PRESETS", size = PP.Micro) }
        items(presets) { p ->
            PPChip(
                p, onClick = { apply(p) },
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 1.dp),
                weight = FontWeight.Normal
            )
        }

        if (isPlayer && engine.getName(team).isNotBlank()) {
            item {
                PPChip(
                    if (es) "Sin jugador" else "No player", onClick = { apply("") },
                    modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 1.dp),
                    content = PP.Danger, weight = FontWeight.Normal
                )
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(backgroundColor = PP.SurfaceHigh),
                modifier = Modifier.size(48.dp)
            ) { Text("✕", color = PP.TextBright, fontWeight = FontWeight.Bold, fontSize = iconSp(18.dp)) }
        }
    }
}

@Composable
fun ScorePicker(
    type: String,
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onClose: () -> Unit
) {
    val accent = ThemeUtils.getColor(engine.theme)
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    val es = engine.lang == "es"
    // "games", "sets" o "all" (los dos, el que abre la franja del marcador)
    val showGames = type != "sets"
    val showSets = type != "games"
    // Botones − y + de 48 dp en vez de ruedas: con el dedo en plena pista se
    // aciertan mucho mejor que una rueda pequeña.
    var gamesA by remember { mutableIntStateOf(engine.gamesA) }
    var gamesB by remember { mutableIntStateOf(engine.gamesB) }
    var setsA by remember { mutableIntStateOf(engine.setsA) }
    var setsB by remember { mutableIntStateOf(engine.setsB) }
    val maxGames = 7
    val maxSets = (engine.bestOf / 2 + 1).coerceAtLeast(1)

    // Puntos del juego en curso, solo al corregir el marcador entero. En juego
    // normal van de 0 a 4 (0, 15, 30, 40, AD); en tie-break, los puntos tal cual.
    val tbNow = engine.isTb || engine.isSuperTbActive()
    val showPoints = type == "all" && !engine.over
    fun startPts(team: String): Int = when {
        tbNow -> if (team == "A") engine.tbPtsA else engine.tbPtsB
        engine.isDeuce -> if (engine.adv == team) 4 else 3
        else -> (if (team == "A") engine.ptsA else engine.ptsB).coerceIn(0, 3)
    }
    val initA = remember { startPts("A") }
    val initB = remember { startPts("B") }
    var ptsA by remember { mutableIntStateOf(initA) }
    var ptsB by remember { mutableIntStateOf(initB) }
    val ptsLabels = listOf("0", "15", "30", "40", "AD")
    // Ventaja solo con el otro en 40 y sin punto de oro; y solo una a la vez
    fun maxPts(other: Int): Int = when {
        tbNow -> 30
        other == 3 && !engine.goldenPoint -> 4
        else -> 3
    }
    fun setPtsA(v: Int) {
        ptsA = v
        if (!tbNow && ptsB == 4 && (v == 4 || v < 3)) ptsB = 3
    }
    fun setPtsB(v: Int) {
        ptsB = v
        if (!tbNow && ptsA == 4 && (v == 4 || v < 3)) ptsA = 3
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            PPLabel(
                when (type) {
                    "games" -> ui.games
                    "sets" -> ui.sets
                    else -> if (es) "CORREGIR MARCADOR" else "FIX SCORE"
                },
                color = accent, size = PP.Label
            )
        }
        if (showPoints) {
            item {
                PPLabel(
                    if (tbNow) "TIE-BREAK" else (if (es) "PUNTOS" else "POINTS"),
                    size = PP.Micro, modifier = Modifier.padding(top = 4.dp)
                )
            }
            val show: (Int) -> String = { if (tbNow) "$it" else ptsLabels[it.coerceIn(0, 4)] }
            item { ScoreStepper(engine.nameA, ptsA, maxPts(ptsB), accent, show) { setPtsA(it) } }
            item { ScoreStepper(engine.nameB, ptsB, maxPts(ptsA), accent, show) { setPtsB(it) } }
        }
        if (showGames) {
            if (type == "all") item { PPLabel(ui.games, size = PP.Micro, modifier = Modifier.padding(top = 4.dp)) }
            item { ScoreStepper(engine.nameA, gamesA, maxGames, accent) { gamesA = it } }
            item { ScoreStepper(engine.nameB, gamesB, maxGames, accent) { gamesB = it } }
        }
        if (showSets) {
            if (type == "all") item { PPLabel(ui.sets, size = PP.Micro, modifier = Modifier.padding(top = 4.dp)) }
            item { ScoreStepper(engine.nameA, setsA, maxSets, accent) { setsA = it } }
            item { ScoreStepper(engine.nameB, setsB, maxSets, accent) { setsB = it } }
        }
        item {
            PPChip(
                "OK",
                onClick = {
                    // Antes de tocar nada, como un punto: asi "deshacer" vuelve
                    // al marcador de antes de corregirlo
                    engine.saveState()
                    if (showPoints && (ptsA != initA || ptsB != initB)) {
                        engine.faultCount = 0
                        when {
                            tbNow -> {
                                engine.tbPtsA = ptsA
                                engine.tbPtsB = ptsB
                                // El turno de saque del desempate se cuenta por
                                // puntos jugados: que cuadre con el tanteo nuevo
                                engine.tbN = ptsA + ptsB
                            }
                            ptsA == 4 || ptsB == 4 -> {
                                engine.ptsA = 3; engine.ptsB = 3
                                engine.isDeuce = true
                                engine.adv = if (ptsA == 4) "A" else "B"
                                engine.goldenPointActive = false
                            }
                            else -> {
                                engine.ptsA = ptsA; engine.ptsB = ptsB
                                engine.isDeuce = ptsA == 3 && ptsB == 3
                                engine.adv = null
                                engine.goldenPointActive = engine.isDeuce && engine.goldenPoint
                            }
                        }
                    }
                    if (showGames) {
                        engine.gamesA = gamesA
                        engine.gamesB = gamesB
                    }
                    if (showSets) {
                        engine.setsA = setsA
                        engine.setsB = setsB
                        // Sets puestos a mano: sus resultados ya no se saben
                        if (engine.setScores.size != engine.setsA + engine.setsB) engine.setScores = emptyList()
                    }
                    engine.persist()
                    // Como cualquier cambio del marcador: sube la revision y lo
                    // manda. Sin subirla, el movil ignoraba la correccion.
                    activity.onLocalScoreAction("edit")
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(0.7f).padding(top = 6.dp),
                background = accent, content = PP.OnAccent,
                weight = FontWeight.Black, center = true
            )
        }
        item {
            PPChip(
                if (es) "CANCELAR" else "CANCEL", onClick = onClose,
                modifier = Modifier.fillMaxWidth(0.7f).padding(top = 2.dp),
                content = PP.TextDim, center = true
            )
        }
    }
}

/**
 * Una cifra del marcador con − y + de 48 dp. El nombre de la pareja encima,
 * y la cifra en medio en grande. Crece con la letra del sistema.
 */
@Composable
private fun ScoreStepper(
    name: String,
    value: Int,
    max: Int,
    accent: Color,
    display: (Int) -> String = { "$it" },
    onChange: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)
    ) {
        Text(
            name.uppercase(), color = PP.TextDim, fontSize = PP.Micro,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepButton("−", enabled = value > 0) { onChange((value - 1).coerceAtLeast(0)) }
            Text(
                display(value), color = accent, fontSize = 26.sp, fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(min = 44.dp)
            )
            StepButton("+", enabled = value < max) { onChange((value + 1).coerceAtMost(max)) }
        }
    }
}

@Composable
private fun StepButton(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (enabled) PP.SurfaceHigh else PP.Surface)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph, color = if (enabled) PP.TextBright else PP.TextMuted,
            fontSize = iconSp(22.dp), fontWeight = FontWeight.Black
        )
    }
}
