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
    LaunchedEffect(editingTeam, showPicker, confirmNew, showLanguage, pageList) {
        onActiveList(
            when {
                editingTeam != null -> nameState
                showPicker != null -> pickerState
                confirmNew -> confirmState
                showLanguage -> languageState
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
        item {
            Text(
                "${engine.nameA} · ${engine.nameB}\n" +
                    "${engine.setsA}–${engine.setsB}  ·  ${engine.gamesA}–${engine.gamesB}",
                color = PP.TextBright, fontSize = PP.Body, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center, modifier = Modifier.padding(vertical = 4.dp)
            )
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
    val gameOptions = 8
    val setOptions = 4
    val gamesA = rememberPickerState(initialNumberOfOptions = gameOptions)
    val gamesB = rememberPickerState(initialNumberOfOptions = gameOptions)
    val setsA = rememberPickerState(initialNumberOfOptions = setOptions)
    val setsB = rememberPickerState(initialNumberOfOptions = setOptions)

    LaunchedEffect(Unit) {
        gamesA.scrollToOption(engine.gamesA.coerceIn(0, gameOptions - 1))
        gamesB.scrollToOption(engine.gamesB.coerceIn(0, gameOptions - 1))
        setsA.scrollToOption(engine.setsA.coerceIn(0, setOptions - 1))
        setsB.scrollToOption(engine.setsB.coerceIn(0, setOptions - 1))
    }

    // Los rodillos se hacen mas altos con la letra: asi el numero elegido
    // siempre se ve entero, lo ponga el usuario tan grande como lo ponga.
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val pickerW = 52.dp * scale
    val pickerH = 84.dp * scale

    @Composable
    fun PickerRow(a: PickerState, b: PickerState) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Picker(state = a, modifier = Modifier.size(pickerW, pickerH), contentDescription = engine.nameA) {
                Text("$it", fontSize = 26.sp, color = if (it == a.selectedOption) accent else PP.TextMuted)
            }
            Text("–", color = PP.TextBright, fontSize = 20.sp)
            Picker(state = b, modifier = Modifier.size(pickerW, pickerH), contentDescription = engine.nameB) {
                Text("$it", fontSize = 26.sp, color = if (it == b.selectedOption) accent else PP.TextMuted)
            }
        }
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
        item {
            Text(
                "${engine.nameA}  ·  ${engine.nameB}",
                color = PP.TextDim, fontSize = PP.Micro, fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
        if (showGames) {
            if (type == "all") item { PPLabel(ui.games, size = PP.Micro) }
            item { PickerRow(gamesA, gamesB) }
        }
        if (showSets) {
            if (type == "all") item { PPLabel(ui.sets, size = PP.Micro) }
            item { PickerRow(setsA, setsB) }
        }
        item {
            PPChip(
                "OK",
                onClick = {
                    // Antes de tocar nada, como un punto: asi "deshacer" vuelve
                    // al marcador de antes de corregirlo
                    engine.saveState()
                    if (showGames) {
                        engine.gamesA = gamesA.selectedOption
                        engine.gamesB = gamesB.selectedOption
                    }
                    if (showSets) {
                        engine.setsA = setsA.selectedOption
                        engine.setsB = setsB.selectedOption
                        // Sets puestos a mano: sus resultados ya no se saben
                        if (engine.setScores.size != engine.setsA + engine.setsB) engine.setScores = emptyList()
                    }
                    engine.persist()
                    // Como cualquier cambio del marcador: sube la revision y lo
                    // manda. Sin subirla, el movil ignoraba la correccion.
                    activity.onLocalScoreAction("edit")
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(0.6f).padding(top = 4.dp),
                background = accent, content = PP.OnAccent,
                weight = FontWeight.Black, center = true
            )
        }
        item {
            PPChip(
                if (es) "CANCELAR" else "CANCEL", onClick = onClose,
                modifier = Modifier.fillMaxWidth(0.6f).padding(top = 2.dp),
                content = PP.TextDim, center = true
            )
        }
    }
}
