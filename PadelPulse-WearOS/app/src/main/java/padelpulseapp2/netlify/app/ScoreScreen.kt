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
    val accent = ThemeUtils.getColor(engine.theme)
    var showPicker by remember { mutableStateOf<String?>(null) }
    var editingTeam by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(engine.over) { if (engine.over) onEnd() }

    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    // Cuando manda el movil, el reloj es un visor: nada de puntuar aqui
    val readOnly = SyncProtocol.normalizeMode(engine.mode) == SyncProtocol.MODE_PHONE

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

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 26.dp)
    ) {
        // Cabecera: pista, saque, cronometro y estado del enlace
        item {
            Row(
                modifier = Modifier.fillMaxWidth(0.96f),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CourtDiagram(engine.serving, engine.getServeSide(), accent)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        activity.getTimerDisplay(),
                        color = PP.TextDim, fontSize = PP.Label, fontWeight = FontWeight.Bold
                    )
                    PPLabel(matchPhaseLabel(engine, ui), color = accent, size = PP.Micro)
                }
                LinkPill(engine)
            }
        }

        // Sets y juegos, editables con un toque
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .clip(PP.CardShape)
                    .background(PP.Surface)
                    .padding(vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreStat(ui.sets, "${engine.setsA}-${engine.setsB}", accent, !readOnly) {
                    showPicker = "sets"
                }
                Box(Modifier.width(1.dp).height(20.dp).background(PP.Line))
                ScoreStat(ui.games, "${engine.gamesA}-${engine.gamesB}", Color.White, !readOnly) {
                    showPicker = "games"
                }
            }
        }

        // Marcador grande
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreBox("A", engine, activity, accent, readOnly) { editingTeam = "A" }
                Text(
                    "·",
                    color = PP.Line,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
                ScoreBox("B", engine, activity, accent, readOnly) { editingTeam = "B" }
            }
        }

        // Aviso claro cuando el reloj no puede puntuar
        if (readOnly) {
            item {
                PPLabel(
                    if (engine.lang == "es") "MANDA EL MOVIL · SOLO LECTURA"
                    else "PHONE LEADS · READ ONLY",
                    color = PP.Warn, size = PP.Micro
                )
            }
        }

        item { HealthCard(engine, accent) }

        // Barra de acciones
        item {
            Row(
                modifier = Modifier.fillMaxWidth(0.98f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionButton("⚙", PP.TextDim, onSettings)
                ActionButton(if (PhoneLink.paired) "⌚" else "📱", accent, onMode)
                FaultButton(engine, activity, ui, readOnly)
                ActionButton(if (engine.voiceEnabled) "🔊" else "🔇", PP.TextDim) {
                    engine.voiceEnabled = !engine.voiceEnabled
                    engine.saveState()
                }
                ActionButton("↩", PP.TextDim, enabled = !readOnly) {
                    engine.undo()
                    activity.onLocalScoreAction("undo")
                }
            }
        }

        item {
            Button(
                onClick = {
                    engine.resetMatch()
                    activity.resetTimer()
                    activity.startTimer()
                    activity.onLocalScoreAction("reset")
                },
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF2A1212)),
                modifier = Modifier.fillMaxWidth(0.9f).height(30.dp).clip(RoundedCornerShape(15.dp))
            ) {
                Text(
                    ui.newMatch.uppercase(),
                    color = PP.Danger, fontSize = PP.Micro, fontWeight = FontWeight.Black
                )
            }
        }
    }
}

/** Texto de fase: set N, tie-break o super tie-break. */
private fun matchPhaseLabel(engine: GameEngine, ui: UIStrings): String = when {
    engine.isSuperTbActive() -> "SUPER TB"
    engine.isTb -> "TIE-BREAK"
    else -> "${ui.sets.uppercase()} ${engine.setsA + engine.setsB + 1}/${engine.bestOf}"
}

@Composable
private fun ScoreStat(
    label: String, value: String, color: Color,
    clickable: Boolean, onClick: () -> Unit
) {
    Column(
        modifier = if (clickable) Modifier.clickable { onClick() } else Modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PPLabel(label, size = PP.Micro)
        Text(value, color = color, fontSize = PP.Title, fontWeight = FontWeight.Black)
    }
}

@Composable
private fun ActionButton(
    glyph: String, color: Color, enabled: Boolean = true, onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(PP.Surface)
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            glyph,
            fontSize = 13.sp,
            color = if (enabled) color else PP.TextMuted
        )
    }
}

@Composable
private fun FaultButton(
    engine: GameEngine, activity: MainActivity, ui: UIStrings, readOnly: Boolean
) {
    val first = engine.faultCount == 1
    val c = if (first) PP.Warn else PP.TextDim
    Box(
        modifier = Modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (first) PP.Warn.copy(alpha = 0.18f) else PP.Surface)
            .border(1.dp, if (first) PP.Warn else PP.Line, RoundedCornerShape(16.dp))
            .then(
                if (readOnly) Modifier else Modifier.clickable {
                    engine.handleFault(engine.serving)
                    activity.onLocalScoreAction("fault", engine.serving)
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (first) "2ª" else ui.fault.uppercase(),
            fontSize = PP.Micro,
            color = if (readOnly) PP.TextMuted else c,
            fontWeight = FontWeight.Black
        )
    }
}

@Composable
fun ScoreBox(
    team: String,
    engine: GameEngine,
    activity: MainActivity,
    accent: Color,
    readOnly: Boolean,
    onEditName: () -> Unit
) {
    val serving = engine.serving == team
    val score = engine.getScoreStr(team)
    val name = if (team == "A") engine.nameA else engine.nameB

    // Late acompañando al punto: feedback inmediato sin mirar el numero
    var bump by remember { mutableStateOf(false) }
    LaunchedEffect(score) {
        bump = true
        kotlinx.coroutines.delay(140)
        bump = false
    }
    val scale by animateFloatAsState(
        if (bump) 1.12f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "bump"
    )

    Column(modifier = Modifier.width(74.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            name.uppercase(),
            color = if (serving) accent else PP.TextDim,
            fontSize = PP.Micro,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.clickable { onEditName() }.padding(vertical = 1.dp)
        )

        Spacer(Modifier.height(2.dp))

        // Indicador de saque: pulsarlo cambia quien saca
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (serving) accent.copy(alpha = 0.22f) else PP.Surface)
                .border(1.dp, if (serving) accent else PP.Line, CircleShape)
                .then(
                    if (readOnly) Modifier else Modifier.clickable {
                        engine.serving = team
                        engine.faultCount = 0
                        engine.speakServe(team)
                        activity.onLocalScoreAction("serve", team)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (serving) Text("🎾", fontSize = 10.sp)
        }

        Spacer(Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .size(72.dp, 58.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(if (serving) accent.copy(alpha = 0.16f) else PP.Surface)
                .border(1.dp, if (serving) accent else PP.Line, RoundedCornerShape(14.dp))
                .then(
                    if (readOnly) Modifier else Modifier.clickable {
                        engine.addPoint(team)
                        activity.onLocalScoreAction("point", team)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                score,
                fontSize = PP.Display,
                fontWeight = FontWeight.Black,
                color = if (serving) accent else Color.White,
                modifier = Modifier.scale(scale)
            )
        }

        Spacer(Modifier.height(3.dp))

        Box(
            modifier = Modifier
                .size(40.dp, 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (readOnly) PP.Surface else Color(0xFF2A1212))
                .then(
                    if (readOnly) Modifier else Modifier.clickable {
                        engine.decreasePoint(team)
                        activity.onLocalScoreAction("minus", team)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "−",
                fontSize = 13.sp,
                color = if (readOnly) PP.TextMuted else PP.Danger,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
fun HealthCard(engine: GameEngine, accent: Color) {
    // Solo lecturas reales del sensor. Sin dato -> "–", nunca un numero inventado.
    val kcal = if (engine.calories > 0) engine.calories.toString() else "–"
    val km = if (engine.distanceKm > 0.0) "%.2f".format(engine.distanceKm) else "–"
    val hr = if (engine.heartRate > 0) engine.heartRate.toString() else "–"

    Row(
        modifier = Modifier
            .fillMaxWidth(0.96f)
            .clip(PP.CardShape)
            .background(PP.Surface)
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        HealthStat("🔥", kcal, "KCAL", Color.White)
        Box(Modifier.width(1.dp).height(16.dp).background(PP.Line))
        HealthStat("🏃", km, "KM", Color.White)
        Box(Modifier.width(1.dp).height(16.dp).background(PP.Line))
        HealthStat("💓", hr, "PPM", accent)
    }
}

@Composable
private fun HealthStat(icon: String, value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("$icon $label", color = PP.TextMuted, fontSize = PP.Micro, fontWeight = FontWeight.Bold)
        Text(value, color = color, fontSize = PP.Label, fontWeight = FontWeight.Black)
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
    val presets = listOf("YO", "RIVAL", "LOCAL", "VISITA", "PAREJA A", "PAREJA B")

    val namesHistory = remember {
        val prefs = activity.getSharedPreferences("padel_prefs", Context.MODE_PRIVATE)
        try {
            val array = org.json.JSONArray(prefs.getString("names_history", "[]") ?: "[]")
            (0 until array.length()).map { array.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun apply(name: String) {
        if (team == "A") engine.nameA = name else engine.nameB = name
        engine.saveState()
        activity.sendSettingsToPhone()
        activity.pushStateToPhone()
        onClose()
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 26.dp)
    ) {
        item {
            PPLabel(
                if (es) "NOMBRE PAREJA $team" else "TEAM $team NAME",
                color = accent, size = PP.Label
            )
            Spacer(Modifier.height(4.dp))
        }

        item {
            Chip(
                onClick = {
                    activity.startSpeechToText { text ->
                        if (text.isNotBlank()) apply(text.uppercase()) else onClose()
                    }
                },
                label = {
                    Text(
                        if (es) "Dictar 🎙" else "Dictate 🎙",
                        fontSize = PP.Body, fontWeight = FontWeight.Bold
                    )
                },
                colors = ChipDefaults.primaryChipColors(
                    backgroundColor = accent, contentColor = Color.Black
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
                    backgroundColor = PP.Surface, contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(0.92f).padding(vertical = 1.dp)
            )
        }

        item {
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(backgroundColor = PP.SurfaceHigh),
                modifier = Modifier.size(40.dp)
            ) { Text("✕", color = Color.White, fontWeight = FontWeight.Bold) }
        }
    }
}

/**
 * Pista vista desde arriba con el cuadro de saque encendido.
 * En pádel el saque empieza por la derecha, y esto ahorra discusiones.
 */
@Composable
fun CourtDiagram(servingTeam: String, side: String, accent: Color) {
    Column(
        modifier = Modifier
            .size(30.dp, 34.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, PP.Line, RoundedCornerShape(4.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Pareja B, arriba
        Row(modifier = Modifier.weight(1f)) {
            CourtCell(servingTeam == "B" && side == "R", accent)
            CourtCell(servingTeam == "B" && side == "L", accent)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.5f)))
        // Pareja A, abajo
        Row(modifier = Modifier.weight(1f)) {
            CourtCell(servingTeam == "A" && side == "L", accent)
            CourtCell(servingTeam == "A" && side == "R", accent)
        }
    }
}

@Composable
private fun RowScope.CourtCell(active: Boolean, accent: Color) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .background(if (active) accent.copy(alpha = 0.65f) else Color.Transparent)
            .border(0.5.dp, PP.Line)
    )
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
                Text("–", color = Color.White, fontSize = 20.sp)
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
                Text("OK", color = Color.Black, fontWeight = FontWeight.Black, fontSize = PP.Label)
            }
        }
    }
}
