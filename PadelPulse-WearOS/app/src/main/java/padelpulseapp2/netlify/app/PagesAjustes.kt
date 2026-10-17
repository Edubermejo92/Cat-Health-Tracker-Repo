package padelpulseapp2.netlify.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.Text
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.WatchAccount
import padelpulseapp2.netlify.app.ui.PP
import padelpulseapp2.netlify.app.ui.PPCard
import padelpulseapp2.netlify.app.ui.PPChip
import padelpulseapp2.netlify.app.ui.PPLabel

/*
 * Paginas 3 y 4 del marcador: Ajustes y Sincronizacion.
 *
 * Antes habia dos sitios de ajustes (Controles, en el marcador, y una pantalla
 * de Ajustes aparte) con cosas repetidas. Ahora hay uno solo, ordenado por lo
 * que se busca: partido, equipos, color, sets, juegos, voz e idioma. Todo lo
 * del movil (enlace, cuenta, historial) va en Sincronizacion.
 */

/** Rotulo de cada grupo de Ajustes. */
@Composable
private fun SectionHeader(text: String, accent: Color) {
    PPLabel(text, color = accent, size = PP.Micro, modifier = Modifier.padding(top = 8.dp, bottom = 1.dp))
}

/** Fila de ajuste: titulo y, debajo, su valor actual. Crece con la letra. */
@Composable
private fun SettingRow(
    label: String,
    value: String?,
    accent: Color,
    danger: Boolean = false,
    icon: String? = null,
    onClick: () -> Unit
) {
    PPChip(
        label, onClick = onClick,
        modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
        background = if (danger) Color(0xFF2A1212) else PP.Surface,
        content = if (danger) PP.Danger else PP.TextBright,
        secondary = value, secondaryColor = if (danger) PP.Danger.copy(alpha = 0.8f) else accent,
        secondarySize = 12.sp, fontSize = 14.sp, icon = icon
    )
}

@Composable
fun SettingsPage(
    engine: GameEngine,
    activity: MainActivity,
    accent: Color,
    listState: ScalingLazyListState,
    onNewMatch: () -> Unit,
    onEditName: (String) -> Unit,
    onPicker: (String) -> Unit,
    onLanguage: () -> Unit,
    onInvite: () -> Unit
) {
    val ui = Translations.ui[engine.lang] ?: Translations.ui["es"]!!
    val es = engine.lang == "es"
    fun sinNombre(v: String) = v.ifBlank { if (es) "Sin nombre" else "No name" }
    fun saveSettings() {
        engine.saveState()
        activity.sendSettingsToPhone()
    }

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        // un poco mas abajo: la ultima fila tiene que poder subir por encima
        // de los puntos del pager
        contentPadding = roundSafePadding(extraVertical = 10.dp)
    ) {
        item { PPLabel(ui.settings, color = accent, size = PP.Label) }

        // ── Partido ──
        item { SectionHeader(if (es) "PARTIDO" else "MATCH", accent) }
        item {
            SettingRow(
                ui.newMatch, if (es) "Guarda este y empieza otro" else "Saves this one and starts another",
                accent, danger = true, icon = "↺"
            ) { onNewMatch() }
        }
        item {
            SettingRow(ui.undo, if (es) "Último punto" else "Last point", accent, icon = "↩") {
                engine.undo()
                activity.onLocalScoreAction("undo")
            }
        }

        // ── Nombre de equipos ──
        item { SectionHeader(if (es) "NOMBRE DE EQUIPOS" else "TEAM NAMES", accent) }
        for (t in listOf("A", "B")) {
            item { SettingRow(if (t == "A") ui.teamA else ui.teamB, engine.getName(t), accent) { onEditName(t) } }
            for (n in 1..2) {
                item {
                    SettingRow(
                        (if (es) "Jugador " else "Player ") + "$t$n",
                        sinNombre(engine.getName("$t$n")), accent
                    ) { onEditName("$t$n") }
                }
            }
        }

        // ── Color de la app ──
        item { SectionHeader(if (es) "COLOR DE LA APP" else "APP COLOR", accent) }
        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ThemeUtils.themesList.forEach { th ->
                        val sel = engine.theme == th
                        // El circulo se ve pequeño, pero el toque es mas grande
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .clickable {
                                    engine.theme = th
                                    saveSettings()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(if (sel) 24.dp else 18.dp)
                                    .clip(CircleShape)
                                    .background(ThemeUtils.getDotColor(th))
                                    .border(if (sel) 2.dp else 0.dp, PP.TextBright, CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // ── Sets ──
        item { SectionHeader(ui.sets.uppercase(), accent) }
        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                PPLabel(ui.bestOf, size = PP.Micro)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(1, 3, 5).forEach { n ->
                        val sel = engine.bestOf == n
                        Box(
                            modifier = Modifier
                                .defaultMinSize(44.dp, 40.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) accent else PP.SurfaceHigh)
                                .clickable {
                                    engine.bestOf = n
                                    saveSettings()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$n", color = if (sel) PP.OnAccent else PP.TextBright,
                                fontSize = PP.Body, fontWeight = FontWeight.Black
                            )
                        }
                    }
                }
            }
        }
        item {
            ToggleRow(ui.superTB, engine.superTb, accent) {
                engine.superTb = it
                saveSettings()
            }
        }
        item {
            SettingRow(
                if (es) "Corregir sets" else "Fix sets", "${engine.setsA} – ${engine.setsB}", accent, icon = "✎"
            ) { onPicker("sets") }
        }

        // ── Juegos ──
        item { SectionHeader(ui.games.uppercase(), accent) }
        item {
            ToggleRow(ui.goldenPt, engine.goldenPoint, accent) {
                engine.goldenPoint = it
                saveSettings()
            }
        }
        item {
            SettingRow(
                if (es) "Corregir juegos" else "Fix games", "${engine.gamesA} – ${engine.gamesB}", accent, icon = "✎"
            ) { onPicker("games") }
        }

        // ── Voz e idioma ──
        item { SectionHeader(if (es) "VOZ E IDIOMA" else "VOICE AND LANGUAGE", accent) }
        item {
            val actual = Translations.langs.find { it.id == engine.lang }
            SettingRow(
                if (es) "Idioma" else "Language",
                actual?.let { "${it.flag} ${it.name}" } ?: engine.lang.uppercase(), accent
            ) { onLanguage() }
        }
        item {
            ToggleRow(if (es) "Voz del marcador" else "Scoreboard voice", engine.voiceEnabled, accent) {
                engine.voiceEnabled = it
                engine.persist()
                if (!it) activity.stopSpeaking()
            }
        }
        item {
            val v = activity.voice
            SettingRow(
                if (es) "Árbitro por voz" else "Voice referee",
                if (v.on) (if (es) "Escuchando · canta los puntos" else "Listening · call the points")
                else (if (es) "Apagado" else "Off"),
                accent, icon = "🎙"
            ) { v.toggle() }
        }
        item { VoiceVolumeCard(engine, activity, accent) }

        // ── Pantalla ──
        item { SectionHeader(if (es) "PANTALLA" else "DISPLAY", accent) }
        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                PPLabel(if (es) "BRILLO" else "BRIGHTNESS", size = PP.Micro)
                InlineSlider(
                    value = engine.brightness,
                    onValueChange = {
                        engine.brightness = it
                        activity.updateBrightness(it)
                        engine.persist()
                    },
                    valueRange = 10f..255f,
                    steps = 5,
                    increaseIcon = { Text("+", color = accent) },
                    decreaseIcon = { Text("−", color = accent) }
                )
            }
        }

        // ── Mas ──
        item { SectionHeader(if (es) "MÁS" else "MORE", accent) }
        // El pulso no se pregunta al arrancar: se activa aqui. Primero sale la
        // explicacion de para que se usa (politica de salud de Play) y, si
        // acepta, el permiso del sistema. Solo existe hasta Wear OS 5.
        if (activity.heartRateAvailable) item {
            val on = WorkoutGuard.hasHeartRatePermission(activity)
            SettingRow(
                if (es) "Pulso ♥" else "Heart rate ♥",
                if (on) (if (es) "Activado" else "On")
                else (if (es) "Toca para verlo en Salud" else "Tap to show it in Health"),
                accent
            ) { if (!on) activity.askHeartRate() }
        }
        item {
            SettingRow(if (es) "Invita a un amigo" else "Invite a friend", null, accent, icon = "✉") { onInvite() }
        }
        // Play exige enlazar la politica de privacidad dentro de la app,
        // sobre todo con permisos de salud (el pulso)
        item {
            SettingRow(if (es) "Política de privacidad" else "Privacy policy", null, accent, icon = "🔒") {
                runCatching {
                    activity.startActivity(android.content.Intent(activity, PrivacyPolicyActivity::class.java))
                }
            }
        }
        item { ExitButton(engine, activity) }
        item {
            Text(
                "PadelPulse Live ${MainActivity.APP_VERSION}",
                color = PP.TextMuted, fontSize = PP.Micro, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** Una comprobacion: ✓ hecho, ✕ falla, … aun no se sabe. */
@Composable
private fun CheckLine(ok: Boolean?, text: String, accent: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            when (ok) { true -> "✓"; false -> "✕"; null -> "…" },
            color = when (ok) { true -> accent; false -> PP.Warn; null -> PP.TextMuted },
            fontSize = PP.Micro, fontWeight = FontWeight.Black,
            modifier = Modifier.width(14.dp)
        )
        Text(text, color = if (ok == true) PP.TextBright else PP.TextDim, fontSize = PP.Micro)
    }
}

/**
 * Pagina 4: todo lo que tiene que ver con el movil. Enlace, sincronizar a
 * mano, la cuenta (iniciar sesion se hace en el movil, a un toque desde aqui)
 * y el historial de la cuenta.
 */
@Composable
fun SyncPage(
    engine: GameEngine,
    activity: MainActivity,
    accent: Color,
    listState: ScalingLazyListState,
    onHistory: () -> Unit
) {
    val es = engine.lang == "es"

    ScalingLazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().background(PP.Bg).rotaryScroll(listState),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding = roundSafePadding(extraVertical = 10.dp)
    ) {
        item { PPLabel(if (es) "SINCRONIZACIÓN" else "SYNC", color = accent, size = PP.Label) }

        // ── Movil: diagnostico en tres pasos ──
        // 1) enlace del sistema (Bluetooth/Wi-Fi), 2) PadelPulse en el movil,
        // 3) PadelPulse contesta. Cada uno dice que hacer si falla.
        item {
            PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                LinkPill(engine)
                Spacer(Modifier.height(4.dp))
                val link = PhoneLink.connected
                val app = PhoneLink.appOnPhone
                val heard = PhoneLink.lastHeardAt > 0
                CheckLine(
                    link,
                    if (link) (if (es) "Enlace con ${PhoneLink.phoneName.ifEmpty { "el móvil" }}" else "Linked to ${PhoneLink.phoneName.ifEmpty { "the phone" }}")
                    else (if (es) "Sin enlace: activa el Bluetooth y abre la app Galaxy Wearable o Wear OS del móvil"
                          else "No link: turn on Bluetooth and open the Galaxy Wearable or Wear OS app on the phone"),
                    accent
                )
                if (link) CheckLine(
                    app,
                    when (app) {
                        true -> if (es) "PadelPulse está en el móvil" else "PadelPulse is on the phone"
                        false -> if (es) "Falta PadelPulse en el móvil" else "PadelPulse is missing on the phone"
                        null -> if (es) "Comprobando PadelPulse en el móvil…" else "Checking PadelPulse on the phone…"
                    },
                    accent
                )
                if (link && app != false) CheckLine(
                    if (heard) true else null,
                    if (heard) (if (es) "PadelPulse del móvil responde" else "Phone app is answering")
                    else (if (es) "Abre PadelPulse en el móvil" else "Open PadelPulse on the phone"),
                    accent
                )
            }
        }
        item {
            PPChip(
                if (es) "BUSCAR MÓVIL" else "FIND PHONE",
                onClick = { activity.findPhone() },
                modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                content = accent, icon = "📡", weight = FontWeight.Black
            )
        }
        if (PhoneLink.connected && PhoneLink.appOnPhone == false) item {
            PPChip(
                if (es) "INSTALAR EN EL MÓVIL" else "INSTALL ON PHONE",
                onClick = { activity.openPlayOnPhone() },
                modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                background = PP.Warn, content = PP.Bg,
                secondary = if (es) "Abre la Play Store en el móvil" else "Opens the Play Store on the phone",
                secondaryColor = PP.Bg, icon = "⬇", weight = FontWeight.Black
            )
        }
        item {
            PPChip(
                if (es) "SINCRONIZAR AHORA" else "SYNC NOW",
                onClick = { activity.syncNow() },
                modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                background = accent, content = PP.OnAccent,
                secondary = if (es) "Marcador, idioma, nombres e historial" else "Score, language, names and history",
                secondaryColor = PP.OnAccent.copy(alpha = 0.8f),
                icon = "⇄", weight = FontWeight.Black
            )
        }

        // ── Cuenta ──
        item {
            PPLabel(if (es) "CUENTA" else "ACCOUNT", color = accent, size = PP.Micro, modifier = Modifier.padding(top = 8.dp))
        }
        if (WatchAccount.signedIn) {
            item {
                PPCard(modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp)) {
                    Text(
                        if (es) "Sesión iniciada" else "Signed in",
                        color = accent, fontSize = PP.Micro, fontWeight = FontWeight.Bold
                    )
                    Text(
                        WatchAccount.name.ifEmpty { WatchAccount.email },
                        color = PP.TextBright, fontSize = PP.Body, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    if (WatchAccount.name.isNotEmpty() && WatchAccount.email.isNotEmpty()) {
                        Text(WatchAccount.email, color = PP.TextMuted, fontSize = PP.Micro, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            item {
                Text(
                    if (es) "En el reloj no se escriben contraseñas: toca el botón, inicia sesión en el móvil y el reloj entra solo."
                    else "No passwords on the watch: tap the button, sign in on your phone and the watch follows.",
                    color = PP.TextDim, fontSize = PP.Micro, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.9f).padding(vertical = 2.dp)
                )
            }
            item {
                PPChip(
                    if (es) "INICIAR SESIÓN EN EL MÓVIL" else "SIGN IN ON THE PHONE",
                    onClick = { activity.openLoginOnPhone() },
                    modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                    background = accent, content = PP.OnAccent,
                    icon = "📱", weight = FontWeight.Black
                )
            }
        }
        item {
            PPChip(
                if (es) "HISTORIAL" else "HISTORY",
                onClick = onHistory,
                modifier = Modifier.fillMaxWidth(0.94f).padding(vertical = 2.dp),
                secondary = if (WatchAccount.signedIn) (if (es) "Partidos por día" else "Matches by day")
                            else (if (es) "🔒 Hace falta cuenta" else "🔒 Account needed"),
                secondaryColor = accent,
                icon = "📅"
            )
        }
        item {
            Text(
                if (es) "Idioma, nombres, color y reglas se sincronizan solos con el móvil."
                else "Language, names, colour and rules sync with the phone by themselves.",
                color = PP.TextMuted, fontSize = PP.Micro, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(0.9f).padding(top = 6.dp)
            )
        }
    }
}
