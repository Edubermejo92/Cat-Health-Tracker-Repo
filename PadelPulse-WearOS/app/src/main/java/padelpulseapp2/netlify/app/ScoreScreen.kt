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
import padelpulseapp2.netlify.app.ui.PPLabel

@Composable
fun ScoreScreen(
    engine: GameEngine,
    activity: MainActivity,
    listState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    nameState: androidx.wear.compose.foundation.lazy.ScalingLazyListState,
    onSettings: () -> Unit,
    onMode: () -> Unit,
    onEnd: () -> Unit
) {
    var showPicker by remember { mutableStateOf<String?>(null) }
    var editingTeam by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(engine.over) { if (engine.over) onEnd() }

    val editing = editingTeam
    val picker = showPicker
    if (editing != null) {
        NameEditorScreen(editing, engine, activity, nameState) { editingTeam = null }
        return
    }
    if (picker != null) {
        ScorePicker(picker, engine, activity) { showPicker = null }
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
            Chip(
                onClick = {
                    activity.startSpeechToText { text ->
                        if (text.isNotBlank()) apply(if (isPlayer) text else text.uppercase()) else onClose()
                    }
                },
                label = {
                    Text(
                        if (es) "Dictar 🎙" else "Dictate 🎙",
                        fontSize = PP.Body, fontWeight = FontWeight.Bold
                    )
                },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = accent, contentColor = PP.OnAccent
                ),
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 2.dp)
            )
        }

        if (namesHistory.isNotEmpty()) {
            item { PPLabel(if (es) "RECIENTES" else "RECENT", size = PP.Micro) }
            items(namesHistory) { n ->
                Chip(
                    onClick = { apply(n) },
                    label = { Text(n, fontSize = PP.Label) },
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = PP.Surface, contentColor = Color.LightGray
                    ),
                    modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 1.dp)
                )
            }
        }

        item { PPLabel("PRESETS", size = PP.Micro) }
        items(presets) { p ->
            Chip(
                onClick = { apply(p) },
                label = { Text(p, fontSize = PP.Label) },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = PP.Surface, contentColor = PP.TextBright
                ),
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 1.dp)
            )
        }

        if (isPlayer && engine.getName(team).isNotBlank()) {
            item {
                Chip(
                    onClick = { apply("") },
                    label = { Text(if (es) "Sin jugador" else "No player", fontSize = PP.Label) },
                    colors = ChipDefaults.primaryChipColors(backgroundColor = PP.Surface, contentColor = PP.Danger),
                    modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 1.dp)
                )
            }
        }

        item {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(backgroundColor = PP.SurfaceHigh),
                modifier = Modifier.size(40.dp)
            ) { Text("✕", color = PP.TextBright, fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun ScorePicker(
    type: String, engine: GameEngine, activity: MainActivity, onClose: () -> Unit
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

    Box(
        modifier = Modifier.fillMaxSize().background(PP.Bg),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            PPLabel(type, color = accent, size = PP.Label)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Picker(state = stateA, modifier = Modifier.size(52.dp, 84.dp), contentDescription = null) {
                    Text("$it", fontSize = 26.sp, color = if (it == stateA.selectedOption) accent else PP.TextMuted)
                }
                Text("–", color = PP.TextBright, fontSize = 20.sp)
                Picker(state = stateB, modifier = Modifier.size(52.dp, 84.dp), contentDescription = null) {
                    Text("$it", fontSize = 26.sp, color = if (it == stateB.selectedOption) accent else PP.TextMuted)
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
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
                colors = ButtonDefaults.buttonColors(backgroundColor = accent),
                modifier = Modifier.height(34.dp)
            ) {
                Text("OK", color = PP.OnAccent, fontWeight = FontWeight.Black, fontSize = PP.Label)
            }
        }
    }
}
