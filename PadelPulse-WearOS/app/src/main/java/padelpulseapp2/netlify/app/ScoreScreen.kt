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
    onSettings: () -> Unit,
    onMode: () -> Unit,
    onEnd: () -> Unit,
    onActiveList: (androidx.wear.compose.foundation.lazy.ScalingLazyListState?) -> Unit = {}
) {
    var showPicker by remember { mutableStateOf<String?>(null) }
    var editingTeam by remember { mutableStateOf<String?>(null) }
    val pickerState = rememberScalingLazyListState()

    LaunchedEffect(engine.over) { if (engine.over) onEnd() }

    // El editor de nombre tiene su propia lista (nameState), distinta de la
    // del marcador. Sin avisar al indicador de la pantalla de cual es la
    // lista visible de verdad, Play rechaza la app: "falta la barra de
    // desplazamiento" en esta pantalla, aunque la de fuera si la tenga.
    // Lo mismo con el selector de sets/juegos, que ahora tambien es una lista
    // para que con la letra grande se pueda desplazar en vez de cortarse.
    LaunchedEffect(editingTeam, showPicker) {
        onActiveList(
            when {
                editingTeam != null -> nameState
                showPicker != null -> pickerState
                else -> null
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

    ScorePager(
        engine = engine,
        activity = activity,
        listState = listState,
        onSettings = onSettings,
        onMode = onMode,
        onEditName = { editingTeam = it },
        onPicker = { showPicker = it }
    )
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
    val options = if (type == "games") 8 else 4
    val stateA = rememberPickerState(initialNumberOfOptions = options)
    val stateB = rememberPickerState(initialNumberOfOptions = options)

    LaunchedEffect(Unit) {
        if (type == "sets") {
            stateA.scrollToOption(engine.setsA.coerceIn(0, options - 1))
            stateB.scrollToOption(engine.setsB.coerceIn(0, options - 1))
        } else {
            stateA.scrollToOption(engine.gamesA.coerceIn(0, options - 1))
            stateB.scrollToOption(engine.gamesB.coerceIn(0, options - 1))
        }
    }

    // Los rodillos se hacen mas altos con la letra: asi el numero elegido
    // siempre se ve entero, lo ponga el usuario tan grande como lo ponga.
    val scale = LocalDensity.current.fontScale.coerceAtLeast(1f)
    val pickerW = 52.dp * scale
    val pickerH = 84.dp * scale

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding()
    ) {
        item {
            val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
            PPLabel(if (type == "sets") ui.sets else ui.games, color = accent, size = PP.Label)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Picker(state = stateA, modifier = Modifier.size(pickerW, pickerH), contentDescription = null) {
                    Text("$it", fontSize = 26.sp, color = if (it == stateA.selectedOption) accent else PP.TextMuted)
                }
                Text("–", color = PP.TextBright, fontSize = 20.sp)
                Picker(state = stateB, modifier = Modifier.size(pickerW, pickerH), contentDescription = null) {
                    Text("$it", fontSize = 26.sp, color = if (it == stateB.selectedOption) accent else PP.TextMuted)
                }
            }
        }
        item {
            PPChip(
                "OK",
                onClick = {
                    if (type == "sets") {
                        engine.setsA = stateA.selectedOption
                        engine.setsB = stateB.selectedOption
                        // Sets puestos a mano: sus resultados ya no se saben
                        if (engine.setScores.size != engine.setsA + engine.setsB) engine.setScores = emptyList()
                    } else {
                        engine.gamesA = stateA.selectedOption
                        engine.gamesB = stateB.selectedOption
                    }
                    engine.saveState()
                    activity.pushStateToPhone()
                    onClose()
                },
                modifier = Modifier.fillMaxWidth(0.6f).padding(top = 4.dp),
                background = accent, content = PP.OnAccent,
                weight = FontWeight.Black, center = true
            )
        }
    }
}
