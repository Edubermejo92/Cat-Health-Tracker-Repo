# Prompt para Gemini — PadelPulse Live, app de RELOJ (Wear OS)

Pega esto entero en el chat de Gemini con el proyecto `PadelPulse-WearOS/`
abierto. Lleva **todo el código fuente Kotlin y de configuración al día**, para
que trabajes sobre lo que hay de verdad y no sobre una copia vieja.

> **Aviso importante.** El último AAB que se generó de esta app salió de una
> copia de trabajo muy desactualizada: iba con `standalone="true"`, sin
> `MatchOngoingService` y sin el `pathPrefix="/padel"`, y llevaba dentro una
> interfaz WebView (`watch_code.html`) que se sustituyó por Compose. Antes de
> tocar nada, comprueba que el proyecto abierto coincide con este código.

---

## El encargo

**PadelPulse Live** es un marcador de pádel formado por **dos apps Android que
tienen que ir a la par**. Ésta es la del reloj.

| | Móvil | Reloj (ésta) |
|---|---|---|
| Carpeta | `PadelPulse-Movil/` | `PadelPulse-WearOS/` |
| Módulo | `:mobile` | `:app` |
| Interfaz | WebView que carga `assets/code.html` | Jetpack Compose for Wear OS |
| minSdk | 25 | 30 |
| versionCode | 517 | 5170 |

Las dos comparten `applicationId` (`padelpulseapp2.netlify.app`): son **una
sola ficha de Google Play con dos formatos**. compileSdk 35, Gradle 9.5.0.

Son **dos proyectos Gradle independientes**: se abren por separado con
*File → Open* sobre la carpeta que contiene `settings.gradle.kts`.

---

## Reglas que no se pueden romper

Si incumples alguna, la app compila igual y falla en producción — o la rechaza
Play. Las dos últimas ya costaron un rechazo real.

### 1. Las dos apps se publican juntas

Comparten `applicationId`, así que Play exige **el mismo keystore** y
**versionCode distinto**. Los dos AAB se suben a la **misma versión** de Play.
Si sólo subes uno, los usuarios se quedan con una app nueva y otra vieja y **la
sincronización deja de funcionar**.

### 2. El protocolo está escrito en tres sitios

Las dos apps hablan por el **protocolo v3** sobre el Wearable Data Layer (no es
Bluetooth crudo). El contrato está mirrorado en tres implementaciones que
tienen que ir a la par:

```
PadelPulse-Movil/mobile/src/main/assets/code.html        → objeto PPSync
PadelPulse-Movil/mobile/src/main/java/.../sync/SyncProtocol.kt
PadelPulse-WearOS/app/src/main/java/.../sync/SyncProtocol.kt
```

Si cambias una ruta o un campo en una sola, **no hay ningún error**: ni al
compilar ni al ejecutar. Simplemente el marcador deja de sincronizarse.

### 3. `standalone=false`

`com.google.android.wearable.standalone` tiene que estar en **`false`**: es lo
que hace que Play instale sola la app en el reloj del tester en cuanto instala
la del móvil. Con `true` hay que buscarla a mano en la muñeca.

### 4. El `pathPrefix` del manifiesto

`WearListenerService` filtra por `android:pathPrefix="/padel"`. Tiene que ser
idéntico al del móvil o los mensajes **no llegan en segundo plano**.

### 5. Requisitos de calidad de Wear OS

Play ya rechazó una actualización por esto. No lo toques sin saber:

- **Actividad en curso** (`MatchOngoingService.kt`): el partido tiene que salir
  en la esfera del reloj con el crono corriendo. Usa `Status.StopwatchPart`
  para que el reloj avance sin republicar.
- **Indicador de scroll**: el `PositionIndicator` va atado al
  `ScalingLazyListState` **de la pantalla visible**, no a uno compartido. Por
  eso cada pantalla tiene el suyo en `PadelApp.kt`.

### 6. Compila siempre en limpio

El fallo del último AAB fue exactamente éste. Usa `./gradlew clean
bundleRelease`, no `bundleRelease` a secas.

---

## Cómo funciona esta app

Compose for Wear OS. `GameEngine.kt` lleva las reglas de pádel; `PadelApp.kt`
la navegación entre pantallas; `ScoreScreen.kt` el marcador;
`MatchOngoingService.kt` la actividad en curso.

El reloj **no pide contraseña**: teclear un correo en 45 mm es una tortura. La
sesión le llega ya hecha desde el móvil por `/padel/account`, y
`WatchAccount.kt` la guarda. El historial se bloquea si no hay sesión.

### Sincronización: sin modos, por revisión

**No hay modo maestro/esclavo.** Antes había que elegir quién mandaba y se
quitó: el usuario quiere puntuar desde donde tenga la mano libre. Si ves una
`ModeScreen` o un campo `mode`, es código viejo.

Las dos apps puntúan y difunden **el estado entero** (no la acción suelta: por
Bluetooth se pierden mensajes, y así el siguiente estado vuelve a ponerlas de
acuerdo). Los choques se resuelven con `syncRev`, que sube en cada cambio
local:

| Situación | Qué hace `acceptRemoteRev()` |
|---|---|
| `rev` recibida mayor | La aplica |
| `rev` menor | Ignora el marcador, **pero aplica la salud** |
| `rev` iguales | Gana el móvil (por eso la comparación es `>=`) |

El empate se resuelve a favor del móvil no por preferencia, sino porque hace
falta una regla estable: si cada aparato eligiera distinto, los marcadores
quedarían diferentes para siempre.

**Anti-eco**: mientras se aplica un estado remoto hay un flag que impide
reemitir. Sin él las dos apps se mandan el mismo estado sin parar.

---

## Qué mejorar en esta app

1. **No hay buzón para mensajes con la app cerrada.** El móvil tiene
   `sync/PendingInbox.kt` y el reloj no: en `WearListenerService.kt`, si
   `MainActivity.gameEngine` es null se descarta todo salvo la cuenta. Un punto
   marcado en el móvil con la app del reloj cerrada **se pierde**. Hacer el
   equivalente de `PendingInbox` para el reloj es lo más rentable de esta lista.

2. **No hay tests.** No existe `src/test` ni `src/androidTest`. Lo más rentable
   sería cubrir `GameEngine.kt` (ventajas, punto de oro, tie-break, super
   tie-break) con JUnit: es lógica pura y sin Android.

3. **`PadelApp.kt` tiene ~1.100 líneas** con toda la navegación y todas las
   pantallas secundarias. Se puede partir por pantalla sin tocar el
   `PositionIndicator` de cada una (regla 5).

4. **El reloj no tiene agenda ni invitaciones.** El móvil sí (amigos, pareja
   habitual, invitar). Decidir si tiene sentido en la muñeca; si no,
   documentarlo para que nadie lo intente.

5. **El protocolo mirrorado en tres sitios** (regla 2). Generarlo desde una
   única fuente evitaría la clase de fallo más peligrosa del proyecto.

---

## Cómo verificar lo que toques

Compilar no basta: casi todos los fallos de este proyecto compilan bien.

```sh
./gradlew clean installRelease   # reloj por Wi-Fi (adb connect)
```

Para probar la sincronización de verdad hacen falta **móvil y reloj emparejados
con la misma cuenta de Google**. Con el emulador no vale.

Prueba mínima tras tocar la sincronización:

1. Puntúa en el móvil → el reloj lo refleja.
2. Puntúa en el reloj → el móvil lo refleja.
3. Cierra la app del reloj, puntúa en el móvil, ábrela → ver si se pone al día.
4. Puntúa en los dos casi a la vez → los dos tienen que acabar igual.

Y tras tocar cualquier pantalla: que el partido siga saliendo en la esfera y
que el indicador de scroll se mueva en esa pantalla (regla 5).

---

## Cómo quiero que trabajes

- **Pregunta antes de cambiar el protocolo.** Es donde un error no se ve hasta
  que falla en la pista.
- **No toques los requisitos de calidad de Wear OS** sin decirlo: ya costaron
  un rechazo de Play.
- **Explica el porqué**, no sólo el qué: este proyecto tiene decisiones que
  parecen raras y tienen motivo (el estado entero en vez de la acción, el
  empate a favor del móvil, el reloj sin contraseñas).
- Comentarios y mensajes de commit **en español**, como el resto del proyecto.

---

# Código fuente actual

Todo lo que sigue es el contenido exacto de los ficheros del proyecto.


## `app/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />
    <!-- Pulso y podometro: sin ACTIVITY_RECOGNITION Android 10+ no entrega pasos -->
    <uses-permission android:name="android.permission.BODY_SENSORS" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />

    <!-- Actividad en curso del partido (obligatoria en Wear OS): notificacion
         persistente + servicio en primer plano mientras se juega -->
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />

    <uses-feature android:name="android.hardware.type.watch" android:required="true" />

    <application
        android:label="@string/app_name"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:theme="@android:style/Theme.DeviceDefault"
        android:allowBackup="false"
        android:supportsRtl="true">

        <uses-library android:name="com.google.android.wearable" android:required="false" />

        <!--
          standalone=false: la app del reloj es la companera del movil.
          Con esto Play instala automaticamente PadelPulse en el reloj del tester
          en cuanto instala la app del movil, sin buscarla a mano en la muñeca.
          Si algun dia quieres que se pueda instalar sola desde el reloj, ponlo a true.
        -->
        <meta-data
            android:name="com.google.android.wearable.standalone"
            android:value="false" />

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTop"
            android:taskAffinity=""
            android:screenOrientation="portrait"
            tools:ignore="LockedOrientationActivity">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <!-- Mantiene viva la actividad en curso del partido: es lo que hace que
             el marcador salga en la esfera, en recientes y en el carrusel. -->
        <service
            android:name=".MatchOngoingService"
            android:exported="false"
            android:foregroundServiceType="health" />

        <!-- Recibe los mensajes del movil tambien con la app cerrada.
             El pathPrefix tiene que coincidir con el del movil: /padel -->
        <service
            android:name=".WearListenerService"
            android:exported="true"
            android:permission="com.google.android.wearable.permission.BIND_LISTENER">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
                <action android:name="com.google.android.gms.wearable.CAPABILITY_CHANGED" />
                <data android:scheme="wear" android:host="*" android:pathPrefix="/padel" />
            </intent-filter>
        </service>

    </application>
</manifest>
```

## `app/src/main/java/padelpulseapp2/netlify/app/GameEngine.kt`

```kotlin
package padelpulseapp2.netlify.app

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import padelpulseapp2.netlify.app.sync.SyncProtocol

class GameEngine(context: Context? = null) {

    companion object {
        /** Tope de partidos guardados en el reloj. */
        const val MAX_HISTORY = 50
    }

    private val prefs: SharedPreferences? = context?.getSharedPreferences("padel_prefs", Context.MODE_PRIVATE)

    var currentScreen by mutableStateOf("splash")
    var theme by mutableStateOf("neon")
    var lang by mutableStateOf("es")
    var voiceEnabled by mutableStateOf(true)
    var pairingCode by mutableStateOf("----")
    var isConnected by mutableStateOf(false)
    
    var nameA by mutableStateOf("YO Y PAREJA")
    var nameB by mutableStateOf("PAREJA B")

    // SOLO / PHONE / WATCH — ver docs/PROTOCOLO_SINCRONIZACION.md
    var goldenPoint by mutableStateOf(false)
    var goldenPointActive by mutableStateOf(false)
    var bestOf by mutableIntStateOf(3)
    var superTb by mutableStateOf(false)

    var ptsA by mutableIntStateOf(0)
    var ptsB by mutableIntStateOf(0)
    var gamesA by mutableIntStateOf(0)
    var gamesB by mutableIntStateOf(0)
    var setsA by mutableIntStateOf(0)
    var setsB by mutableIntStateOf(0)

    var isDeuce by mutableStateOf(false)
    var adv: String? by mutableStateOf(null)
    var isTb by mutableStateOf(false)
    var tbPtsA by mutableIntStateOf(0)
    var tbPtsB by mutableIntStateOf(0)

    var over by mutableStateOf(false)
    var serving by mutableStateOf("A")

    var tbSrv by mutableStateOf("A")
    var tbN by mutableIntStateOf(0)
    var winner by mutableStateOf<String?>(null)
    
    var faultCount by mutableIntStateOf(0)
    var lastPointWinner by mutableStateOf<String?>(null)

    /** Cronometro del partido en segundos. Lo mantiene al dia MainActivity. */
    var clockSeconds by mutableIntStateOf(0)

    // Health data
    var calories by mutableIntStateOf(0)
    var heartRate by mutableIntStateOf(0)
    var distanceKm by mutableStateOf(0.0)

    // New: Screen brightness (0-255)
    var brightness by mutableStateOf(150f)

    var onSpeak: ((String) -> Unit)? = null

    private val history = mutableListOf<StateSnapshot>()

    data class StateSnapshot(
        val ptsA: Int, val ptsB: Int, val gamesA: Int, val gamesB: Int,
        val setsA: Int, val setsB: Int, val isDeuce: Boolean, val adv: String?,
        val isTb: Boolean, val tbPtsA: Int, val tbPtsB: Int, val over: Boolean,
        val serving: String, val tbSrv: String, val tbN: Int, val faultCount: Int, 
        val winner: String?, val lastPointWinner: String?, val goldenPointActive: Boolean
    )

    init {
        loadFromDisk()
    }

    fun saveState() {
        history.add(
            StateSnapshot(
                ptsA, ptsB, gamesA, gamesB, setsA, setsB, isDeuce, adv,
                isTb, tbPtsA, tbPtsB, over, serving, tbSrv, tbN, faultCount, winner, lastPointWinner, goldenPointActive
            )
        )
        saveToDisk()
    }

    fun resetMatch() {
        history.clear()
        ptsA = 0; ptsB = 0; gamesA = 0; gamesB = 0; setsA = 0; setsB = 0
        isDeuce = false; adv = null; isTb = false; tbPtsA = 0; tbPtsB = 0
        over = false; faultCount = 0; tbN = 0; winner = null
        serving = "A"; tbSrv = "A"; lastPointWinner = null
        goldenPointActive = false
        saveToDisk()
    }

    fun hasSavedMatch(): Boolean {
        return (ptsA > 0 || ptsB > 0 || gamesA > 0 || gamesB > 0 || setsA > 0 || setsB > 0) && !over
    }

    fun undo() {
        if (history.isNotEmpty()) {
            val last = history.removeLast()
            ptsA = last.ptsA; ptsB = last.ptsB; gamesA = last.gamesA; gamesB = last.gamesB
            setsA = last.setsA; setsB = last.setsB; isDeuce = last.isDeuce; adv = last.adv
            isTb = last.isTb; tbPtsA = last.tbPtsA; tbPtsB = last.tbPtsB
            over = last.over; serving = last.serving; tbSrv = last.tbSrv
            tbN = last.tbN; faultCount = last.faultCount; winner = last.winner
            lastPointWinner = last.lastPointWinner; goldenPointActive = last.goldenPointActive
            saveToDisk()
        }
    }

    fun handleFault(servingTeam: String): Int {
        if (over) return faultCount
        if (faultCount == 0) {
            faultCount = 1
            speakText("fault")
            saveToDisk()
            return 1
        } else {
            faultCount = 0
            val opp = if (servingTeam == "A") "B" else "A"
            speakText("double_fault")
            addPoint(opp)
            return 2
        }
    }

    fun isSuperTbActive(): Boolean {
        return superTb && (setsA + setsB == bestOf - 1) && !over
    }

    fun addPoint(team: String) {
        if (over) return
        saveState()
        faultCount = 0
        lastPointWinner = team
        
        if (isSuperTbActive()) {
            addSTB(team)
        } else if (isTb) {
            addTB(team)
        } else {
            addNorm(team)
        }
        saveToDisk()
    }

    private fun addNorm(t: String) {
        var speechKey = ""
        var isGameWon = false
        if (isDeuce) {
            if (goldenPointActive || goldenPoint) {
                winGame(t)
                isGameWon = true
            } else if (adv == null) {
                adv = t
                speechKey = "adv"
            } else if (adv == t) {
                winGame(t)
                isGameWon = true
            } else {
                adv = null
                speechKey = "deuce"
            }
        } else {
            if (t == "A") ptsA++ else ptsB++
            if (ptsA >= 3 && ptsB >= 3) {
                isDeuce = true
                adv = null
                goldenPointActive = goldenPoint
                speechKey = "deuce"
            } else if ((t == "A" && ptsA >= 4) || (t == "B" && ptsB >= 4)) {
                winGame(t)
                isGameWon = true
            } else {
                speechKey = "score"
            }
        }
        
        if (!isGameWon) {
            triggerSpeak(speechKey, t)
        }
    }

    private fun addTB(t: String) {
        if (t == "A") tbPtsA++ else tbPtsB++
        rotTB()
        if ((tbPtsA >= 7 || tbPtsB >= 7) && Math.abs(tbPtsA - tbPtsB) >= 2) {
            winSet(if (tbPtsA > tbPtsB) "A" else "B")
        } else {
            triggerSpeak("tb", t)
        }
    }

    private fun addSTB(t: String) {
        if (t == "A") tbPtsA++ else tbPtsB++
        rotTB()
        if ((tbPtsA >= 10 || tbPtsB >= 10) && Math.abs(tbPtsA - tbPtsB) >= 2) {
            winSet(if (tbPtsA > tbPtsB) "A" else "B")
        } else {
            triggerSpeak("tb", t)
        }
    }

    private fun rotTB() {
        tbN++
        if (tbN == 1 || (tbN > 1 && tbN % 2 == 1)) {
            tbSrv = if (tbSrv == "A") "B" else "A"
            serving = tbSrv
        }
    }

    private fun winGame(t: String) {
        if (t == "A") gamesA++ else gamesB++
        ptsA = 0; ptsB = 0
        isDeuce = false; adv = null; goldenPointActive = false
        serving = if (serving == "A") "B" else "A"

        if (gamesA == 6 && gamesB == 6) {
            isTb = true
            tbPtsA = 0; tbPtsB = 0
            tbSrv = serving; tbN = 0
            triggerSpeak("game", t)
            return
        }

        var w: String? = null
        if (gamesA >= 6 && gamesA - gamesB >= 2) w = "A"
        else if (gamesB >= 6 && gamesB - gamesA >= 2) w = "B"

        if (w != null) {
            winSet(w)
        } else {
            triggerSpeak("game", t)
        }
    }

    private fun winSet(tw: String) {
        if (tw == "A") setsA++ else setsB++
        gamesA = 0; gamesB = 0
        ptsA = 0; ptsB = 0
        isDeuce = false; adv = null; goldenPointActive = false
        isTb = false
        tbPtsA = 0; tbPtsB = 0
        serving = if (tw == "A") "B" else "A"

        val need = Math.ceil(bestOf / 2.0).toInt()
        if (setsA >= need || setsB >= need) {
            over = true
            winner = if (setsA > setsB) "A" else "B"
            saveMatchToHistory()
            triggerSpeak("match", tw)
        } else {
            if ((setsA + setsB) == 2 && bestOf == 3 && superTb) {
                isTb = false
                tbPtsA = 0; tbPtsB = 0
                tbSrv = serving; tbN = 0
            }
            triggerSpeak("set", tw)
        }
    }

    private fun triggerSpeak(key: String, t: String) {
        if (!voiceEnabled) return
        val v = Translations.vd[lang] ?: Translations.vd["es"]!!
        val uiStrings = Translations.ui[lang] ?: Translations.ui["es"]!!
        val teamName = if (t == "A") nameA else nameB
        
        val text = when (key) {
            "score" -> {
                val pA = getVScore(ptsA, v)
                val pB = getVScore(ptsB, v)
                if (ptsA == ptsB && ptsA > 0) "$pA ${v.all}" else "$pA $pB"
            }
            "deuce" -> if (goldenPointActive) v.goldenPoint else v.deuce
            "adv" -> "${v.advantage} $teamName"
            "game" -> "${v.game} $teamName, $gamesA ${uiStrings.games} $gamesB"
            "set" -> "${v.set} $teamName, $setsA ${uiStrings.sets} $setsB"
            "match" -> "${v.game} $teamName"
            "tb" -> "$tbPtsA $tbPtsB"
            else -> ""
        }
        if (text.isNotEmpty()) onSpeak?.invoke(text)
    }

    private fun speakText(key: String) {
        if (!voiceEnabled) return
        val v = Translations.vd[lang] ?: Translations.vd["es"]!!
        val text = when (key) {
            "fault" -> v.fault
            "double_fault" -> v.doubleFault
            else -> ""
        }
        if (text.isNotEmpty()) onSpeak?.invoke(text)
    }

    private fun getVScore(pts: Int, v: VoiceData): String {
        return when (pts.coerceAtMost(3)) {
            0 -> v.zero
            1 -> v.fifteen
            2 -> v.thirty
            3 -> v.forty
            else -> v.zero
        }
    }

    fun speakServe(team: String) {
        if (!voiceEnabled) return
        val v = Translations.vd[lang] ?: Translations.vd["es"]!!
        val teamName = if (team == "A") nameA else nameB
        val text = "$teamName ${v.serves}"
        onSpeak?.invoke(text)
    }

    fun decreasePoint(team: String) {
        if (over) return
        saveState()
        faultCount = 0
        if (isTb || (superTb && setsA + setsB == bestOf - 1 && gamesA == 0 && gamesB == 0)) {
            if (team == "A") {
                if (tbPtsA > 0) {
                    tbPtsA--
                    if (tbN > 0) tbN--
                }
            } else {
                if (tbPtsB > 0) {
                    tbPtsB--
                    if (tbN > 0) tbN--
                }
            }
        } else {
            if (isDeuce) {
                if (adv == team) {
                    adv = null
                } else if (adv == null) {
                    isDeuce = false
                    if (team == "A") {
                        ptsA = 3
                        ptsB = 2
                    } else {
                        ptsB = 3
                        ptsA = 2
                    }
                } else {
                    adv = null
                }
            } else {
                if (team == "A") {
                    if (ptsA > 0) ptsA--
                } else {
                    if (ptsB > 0) ptsB--
                }
            }
        }
        saveToDisk()
    }

    fun getScoreStr(team: String): String {
        if (over) return "FIN"
        if (isTb || isSuperTbActive()) return if (team == "A") tbPtsA.toString() else tbPtsB.toString()
        if (isDeuce) {
            if (goldenPointActive) return "40"
            if (adv == team) return "AD"
            if (adv != null) return "40"
            return "40"
        }
        val p = if (team == "A") ptsA else ptsB
        return arrayOf("0", "15", "30", "40")[p.coerceAtMost(3)]
    }

    fun getServeSide(): String {
        if (over) return ""
        return if (isTb || isSuperTbActive()) {
            val totalPoints = (tbPtsA + tbPtsB)
            if (totalPoints % 2 == 0) "R" else "L"
        } else {
            if (isDeuce) {
                if (adv == null) "R" else "L"
            } else {
                val total = ptsA + ptsB
                if (total % 2 == 0) "R" else "L"
            }
        }
    }

    fun buildSnapshot(code: String = ""): String {
        return org.json.JSONObject()
            .put("v", 2)
            .put("code", code)
            .put("syncMode", SyncProtocol.MODE_SYNC)
            .put("teams", org.json.JSONObject()
                .put("A", org.json.JSONObject()
                    .put("points", if (isTb || isSuperTbActive()) tbPtsA else ptsA)
                    .put("games", gamesA)
                    .put("sets", setsA)
                    .put("name", nameA))
                .put("B", org.json.JSONObject()
                    .put("points", if (isTb || isSuperTbActive()) tbPtsB else ptsB)
                    .put("games", gamesB)
                    .put("sets", setsB)
                    .put("name", nameB)))
            .put("flags", org.json.JSONObject()
                .put("deuce", isDeuce)
                .put("advantage", adv ?: "")
                .put("tiebreak", isTb)
                .put("superTiebreak", superTb)
                .put("goldenPointActive", goldenPointActive))
            .put("match", org.json.JSONObject()
                .put("bestOf", bestOf)
                .put("goldenPoint", goldenPoint)
                .put("superTieBreak", superTb))
            .put("health", org.json.JSONObject()
                .put("heartRate", heartRate)
                .put("calories", calories)
                .put("distanceKm", distanceKm))
            .toString()
    }


    /**
     * Revision del marcador. Sube en cada cambio hecho aqui y viaja con el
     * estado: si el movil y el reloj puntuan casi a la vez, gana el que traiga
     * la revision mas alta.
     */
    var syncRev by mutableIntStateOf(0)
        private set

    fun bumpRev() { syncRev += 1 }

    /**
     * ¿Hacemos caso al estado que llega del movil?
     *
     * Si trae revision mas alta, si. Si empatan -los dos tocaron en el mismo
     * instante- gana el movil, por decidir algo estable: si cada aparato
     * eligiera distinto, los marcadores quedarian diferentes para siempre.
     * Un estado sin revision viene de una version antigua: se acepta.
     */
    fun acceptRemoteRev(remoteRev: Int?): Boolean {
        if (remoteRev == null) return true
        return remoteRev >= syncRev
    }

    fun adoptRev(remoteRev: Int?) { if (remoteRev != null) syncRev = remoteRev }

    /**
     * Estado canonico v3. Los puntos normales van siempre como indice 0-3 en
     * teams.X.pts; los del tie-break van aparte en "tb". Nunca se mezclan.
     */
    fun buildState(seq: Long, clockSeconds: Int): String {
        val tbActive = isTb || isSuperTbActive()
        return JSONObject()
            .put("v", SyncProtocol.VERSION)
            .put("src", SyncProtocol.SRC_WATCH)
            .put("seq", seq)
            .put("ts", System.currentTimeMillis())
            .put("mode", SyncProtocol.MODE_SYNC)
            .put(SyncProtocol.FIELD_REV, syncRev)
            .put("match", JSONObject()
                .put("bestOf", bestOf)
                .put("goldenPoint", goldenPoint)
                .put("superTieBreak", superTb)
                .put("currentSet", setsA + setsB + 1))
            .put("teams", JSONObject()
                .put("A", JSONObject()
                    .put("name", nameA).put("pts", ptsA.coerceIn(0, 3))
                    .put("games", gamesA).put("sets", setsA))
                .put("B", JSONObject()
                    .put("name", nameB).put("pts", ptsB.coerceIn(0, 3))
                    .put("games", gamesB).put("sets", setsB)))
            .put("flags", JSONObject()
                .put("deuce", isDeuce)
                // adv se manda como cadena vacia, jamas como "null"
                .put("adv", adv ?: "")
                .put("tiebreak", isTb)
                .put("superTiebreak", tbActive && !isTb)
                .put("goldenPointActive", goldenPointActive)
                .put("serving", serving)
                .put("faults", faultCount)
                .put("over", over)
                .put("winner", winner ?: ""))
            .put("tb", JSONObject()
                .put("A", tbPtsA).put("B", tbPtsB)
                .put("serving", tbSrv).put("n", tbN))
            .put("health", JSONObject()
                .put("hr", heartRate).put("kcal", calories).put("km", distanceKm))
            .put("clock", clockSeconds)
            .toString()
    }

    /**
     * Aplica un estado recibido del movil. No recalcula nada: el maestro ya lo hizo.
     * Devuelve el cronometro que venia en el mensaje, o -1 si no venia.
     */
    fun applyState(obj: JSONObject): Int {
        val teams = obj.optJSONObject("teams")
        if (teams != null) {
            teams.optJSONObject("A")?.let { a ->
                ptsA = a.optInt("pts", a.optInt("points", ptsA)).coerceIn(0, 3)
                gamesA = a.optInt("games", gamesA)
                setsA = a.optInt("sets", setsA)
                SyncProtocol.optNullableString(a, "name")?.let { nameA = it }
            }
            teams.optJSONObject("B")?.let { b ->
                ptsB = b.optInt("pts", b.optInt("points", ptsB)).coerceIn(0, 3)
                gamesB = b.optInt("games", gamesB)
                setsB = b.optInt("sets", setsB)
                SyncProtocol.optNullableString(b, "name")?.let { nameB = it }
            }
        }
        obj.optJSONObject("flags")?.let { f ->
            isDeuce = f.optBoolean("deuce", isDeuce)
            adv = SyncProtocol.optNullableString(f, "adv")
                ?: SyncProtocol.optNullableString(f, "advantage")
            isTb = f.optBoolean("tiebreak", isTb)
            goldenPointActive = f.optBoolean("goldenPointActive", goldenPointActive)
            serving = f.optString("serving", serving).ifEmpty { serving }
            faultCount = f.optInt("faults", faultCount)
            over = f.optBoolean("over", over)
            winner = SyncProtocol.optNullableString(f, "winner")
        }
        obj.optJSONObject("tb")?.let { t ->
            tbPtsA = t.optInt("A", tbPtsA)
            tbPtsB = t.optInt("B", tbPtsB)
            tbSrv = t.optString("serving", tbSrv).ifEmpty { tbSrv }
            tbN = t.optInt("n", tbN)
        }
        obj.optJSONObject("match")?.let { m ->
            bestOf = m.optInt("bestOf", bestOf)
            goldenPoint = m.optBoolean("goldenPoint", goldenPoint)
            superTb = m.optBoolean("superTieBreak", superTb)
        }
        // La salud la mide el reloj: un estado del movil nunca la pisa.
        saveToDisk()
        return obj.optInt("clock", -1)
    }

    /** Aplica ajustes (idioma, tema, reglas, nombres) vengan de donde vengan. */
    fun applySettings(obj: JSONObject) {
        SyncProtocol.optNullableString(obj, "lang")?.let { lang = it }
        SyncProtocol.optNullableString(obj, "theme")?.let { theme = it }
            ?: SyncProtocol.optNullableString(obj, "color")?.let { theme = ThemeUtils.themeFromHex(it) }
        if (obj.has("goldenPoint")) goldenPoint = obj.optBoolean("goldenPoint")
        if (obj.has("superTieBreak")) superTb = obj.optBoolean("superTieBreak")
        if (obj.has("bestOf")) bestOf = obj.optInt("bestOf", bestOf)
        else if (obj.has("maxSets")) bestOf = obj.optInt("maxSets", bestOf)
        // Los nombres que llegan del movil se guardan tambien en el historial
        // de nombres del reloj: asi las parejas que escribes en el movil salen
        // luego como sugerencia al editar el nombre desde la muñeca.
        SyncProtocol.optNullableString(obj, "nameA")?.let {
            nameA = it
            saveTeamNameToHistory(it)
        }
        SyncProtocol.optNullableString(obj, "nameB")?.let {
            nameB = it
            saveTeamNameToHistory(it)
        }
        saveToDisk()
    }

    fun saveMatchToHistory() {
        try {
            val matchObj = JSONObject()
                .put("date", System.currentTimeMillis())
                .put("teamA", nameA)
                .put("teamB", nameB)
                .put("scoreA", setsA)
                .put("scoreB", setsB)
                .put("gamesA", gamesA)
                .put("gamesB", gamesB)
                .put("winner", winner ?: if (setsA > setsB) "A" else "B")
                .put("duration", clockSeconds)
                .put("kcal", calories)
                .put("km", distanceKm)
                .put("hr", heartRate)

            // Mas reciente primero, y con tope: en un reloj no tiene sentido
            // arrastrar cientos de partidos en SharedPreferences.
            val previous = org.json.JSONArray(prefs?.getString("match_history", "[]") ?: "[]")
            val out = org.json.JSONArray().put(matchObj)
            for (i in 0 until minOf(previous.length(), MAX_HISTORY - 1)) {
                out.put(previous.getJSONObject(i))
            }
            prefs?.edit()?.putString("match_history", out.toString())?.apply()

            saveTeamNameToHistory(nameA)
            saveTeamNameToHistory(nameB)
        } catch (e: Exception) {}
    }

    /** Resumen del historial para la pantalla de historial del reloj. */
    fun historySummary(): Triple<Int, Int, Int> {
        return try {
            val array = org.json.JSONArray(prefs?.getString("match_history", "[]") ?: "[]")
            var won = 0
            for (i in 0 until array.length()) {
                val m = array.getJSONObject(i)
                // "A" es siempre la pareja de quien lleva el reloj
                if (m.optString("winner", "") == "A") won++
            }
            val played = array.length()
            val pct = if (played > 0) won * 100 / played else 0
            Triple(played, won, pct)
        } catch (e: Exception) {
            Triple(0, 0, 0)
        }
    }

    private fun saveTeamNameToHistory(name: String) {
        if (name.isBlank() || name == "YO Y PAREJA" || name == "PAREJA B" || name == "LOCAL" || name == "VISITA") return
        try {
            val namesJson = prefs?.getString("names_history", "[]") ?: "[]"
            val array = org.json.JSONArray(namesJson)
            val list = mutableListOf<String>()
            for (i in 0 until array.length()) {
                list.add(array.getString(i))
            }
            if (!list.contains(name)) {
                array.put(name)
                prefs?.edit()?.putString("names_history", array.toString())?.apply()
            }
        } catch (e: Exception) {}
    }

    private fun saveToDisk() {
        prefs?.edit()?.apply {
            putString("state", toJSON())
            apply()
        }
    }

    private fun loadFromDisk() {
        val json = prefs?.getString("state", null)
        if (json != null) {
            fromJSON(json)
        }
    }

    private fun toJSON(): String {
        val obj = JSONObject()
        obj.put("ptsA", ptsA)
        obj.put("ptsB", ptsB)
        obj.put("gamesA", gamesA)
        obj.put("gamesB", gamesB)
        obj.put("setsA", setsA)
        obj.put("setsB", setsB)
        obj.put("isDeuce", isDeuce)
        obj.put("adv", adv)
        obj.put("isTb", isTb)
        obj.put("tbPtsA", tbPtsA)
        obj.put("tbPtsB", tbPtsB)
        obj.put("over", over)
        obj.put("serving", serving)
        obj.put("tbSrv", tbSrv)
        obj.put("tbN", tbN)
        obj.put("faultCount", faultCount)
        obj.put("winner", winner)
        obj.put("theme", theme)
        obj.put("lang", lang)
        obj.put("goldenPoint", goldenPoint)
        obj.put("goldenPointActive", goldenPointActive)
        obj.put("bestOf", bestOf)
        obj.put("superTb", superTb)
        obj.put("brightness", brightness)
        obj.put("voiceEnabled", voiceEnabled)
        obj.put("nameA", nameA)
        obj.put("nameB", nameB)
        return obj.toString()
    }

    private fun fromJSON(json: String) {
        try {
            val obj = JSONObject(json)
            ptsA = obj.optInt("ptsA", 0)
            ptsB = obj.optInt("ptsB", 0)
            gamesA = obj.optInt("gamesA", 0)
            gamesB = obj.optInt("gamesB", 0)
            setsA = obj.optInt("setsA", 0)
            setsB = obj.optInt("setsB", 0)
            isDeuce = obj.optBoolean("isDeuce", false)
            adv = if (obj.has("adv") && !obj.isNull("adv")) obj.getString("adv") else null
            isTb = obj.optBoolean("isTb", false)
            tbPtsA = obj.optInt("tbPtsA", 0)
            tbPtsB = obj.optInt("tbPtsB", 0)
            over = obj.optBoolean("over", false)
            serving = obj.optString("serving", "A")
            tbSrv = obj.optString("tbSrv", "A")
            tbN = obj.optInt("tbN", 0)
            faultCount = obj.optInt("faultCount", 0)
            winner = if (obj.has("winner") && !obj.isNull("winner")) obj.getString("winner") else null
            theme = obj.optString("theme", "neon")
            lang = obj.optString("lang", "es")
            goldenPoint = obj.optBoolean("goldenPoint", false)
            goldenPointActive = obj.optBoolean("goldenPointActive", false)
            bestOf = obj.optInt("bestOf", 3)
            superTb = obj.optBoolean("superTb", false)
            brightness = obj.optDouble("brightness", 150.0).toFloat()
            voiceEnabled = obj.optBoolean("voiceEnabled", true)
            nameA = obj.optString("nameA", "YO Y PAREJA")
            nameB = obj.optString("nameB", "PAREJA B")
        } catch (e: Exception) {}
    }
}
```

## `app/src/main/java/padelpulseapp2/netlify/app/MainActivity.kt`

```kotlin
package padelpulseapp2.netlify.app

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.SyncProtocol
import padelpulseapp2.netlify.app.sync.WatchAccount
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener, SensorEventListener {

    companion object {
        const val TAG = "PadelPulseWatch"
        const val APP_VERSION = "5.1.7"
        var gameEngine: GameEngine? = null
        var instance: MainActivity? = null
    }

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    /** Version de protocolo del movil; si no cuadra con la nuestra, hay que avisar. */
    var phoneProtocol by mutableIntStateOf(0)
    var phoneAppVersion by mutableStateOf("")

    var matchTimeSeconds by mutableIntStateOf(0)
    var timerRunning by mutableStateOf(false)
    private var timerJob: Job? = null
    private var helloJob: Job? = null

    private var speechResultCallback: ((String) -> Unit)? = null

    // ── Enlace con el movil ─────────────────────────────────────────────

    // El contenido lo procesa WearListenerService; aqui solo refrescamos presencia.
    private val messageListener = MessageClient.OnMessageReceivedListener {
        PhoneLink.setConnected(true)
    }

    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info ->
        val near = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
        PhoneLink.setConnected(near != null, near?.displayName ?: "")
    }

    /** Modo actual normalizado (SOLO / PHONE / WATCH). Fuente unica: el engine. */
    val syncMode: String
        get() = SyncProtocol.MODE_SYNC

    val isPaired: Boolean
        get() = PhoneLink.paired

    fun onPhoneHello(appVersion: String, proto: Int) {
        phoneAppVersion = appVersion
        phoneProtocol = proto
        PhoneLink.setConnected(true)
    }

    fun onPairingConfirmed(code: String) {
        PhoneLink.paired = true
        PhoneLink.pairedCode = code
        PhoneLink.lastError = null
        gameEngine?.pairingCode = code
        sendSettingsToPhone()
        pushStateToPhone()
    }

    fun onPairingRejected() {
        PhoneLink.paired = false
        PhoneLink.lastError = "codigo"
    }

    /**
     * El movil nos ha mandado -o retirado- la sesion. Los estados de
     * [WatchAccount] son observables, asi que la pantalla se repinta sola;
     * aqui solo hace falta mover al usuario si se quedo sin cuenta estando
     * ya dentro, o sacarlo de la pantalla de cuenta cuando entra.
     */
    fun onAccountChanged() {
        val engine = gameEngine ?: return
        if (!WatchAccount.signedIn && !WatchAccount.skipped &&
            engine.currentScreen == "splash" && !engine.hasSavedMatch()) {
            engine.currentScreen = "account"
        }
    }

    fun sendPairRequest(code: String) {
        PhoneLink.pairedCode = code
        gameEngine?.pairingCode = code
        PhoneLink.send(
            this, SyncProtocol.PATH_PAIR,
            SyncProtocol.pairRequest(PhoneLink.nextSeq(), code, android.os.Build.MODEL ?: "Wear OS")
        )
    }

    fun sendHello() {
        val engine = gameEngine ?: return
        PhoneLink.send(
            this, SyncProtocol.PATH_HELLO,
            SyncProtocol.hello(PhoneLink.nextSeq(), APP_VERSION, SyncProtocol.MODE_SYNC, PhoneLink.paired)
        )
    }

    /** Difunde el estado al movil. Siempre que haya movil vinculado. */
    fun pushStateToPhone() {
        val engine = gameEngine ?: return
        if (PhoneLink.applyingRemote) return
        if (!PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_STATE,
            engine.buildState(PhoneLink.nextSeq(), matchTimeSeconds)
        )
    }

    /** La salud siempre va del reloj al movil, en cualquier modo. */
    fun pushHealthToPhone() {
        val engine = gameEngine ?: return
        if (!PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_HEALTH,
            SyncProtocol.health(
                PhoneLink.nextSeq(), engine.heartRate, engine.calories,
                engine.distanceKm, totalSteps
            )
        )
    }

    fun sendSettingsToPhone() {
        val engine = gameEngine ?: return
        if (!PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_SETTINGS,
            SyncProtocol.settings(
                PhoneLink.nextSeq(), engine.lang, engine.theme,
                ThemeUtils.getHexColor(engine.theme), engine.goldenPoint,
                engine.superTb, engine.bestOf, engine.nameA, engine.nameB, SyncProtocol.MODE_SYNC
            )
        )
    }

    /**
     * Punto unico tras cualquier accion local sobre el marcador: sube la
     * revision y manda el estado entero.
     *
     * Se manda el estado completo y no la accion suelta a proposito: si un
     * mensaje se pierde -y por Bluetooth se pierden-, el siguiente estado
     * vuelve a poner a los dos de acuerdo. Con acciones sueltas, un punto
     * perdido dejaria los marcadores descuadrados hasta el final del partido.
     */
    fun onLocalScoreAction(action: String, team: String? = null) {
        gameEngine?.bumpRev()
        pushStateToPhone()
        refreshOngoingActivity()
    }

    fun setMatchClock(seconds: Int) {
        if (seconds >= 0) matchTimeSeconds = seconds
    }

    // ── Voz ──────────────────────────────────────────────────────────────

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.getOrNull(0)?.let { speechResultCallback?.invoke(it) }
        }
    }

    fun startSpeechToText(callback: (String) -> Unit) {
        speechResultCallback = callback
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Dictado no disponible", e)
        }
    }

    // ── Cronometro ───────────────────────────────────────────────────────

    fun startTimer() {
        timerRunning = true
        // Publica la actividad en curso: partido en marcha visible desde la
        // esfera del reloj y recientes, con un toque para volver al marcador.
        MatchOngoingService.start(this)
        if (timerJob?.isActive == true) return
        timerJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                if (timerRunning) {
                    matchTimeSeconds++
                    // El engine necesita el cronometro para guardarlo al terminar
                    gameEngine?.clockSeconds = matchTimeSeconds
                    // Empuja salud al movil entre puntos, sin esperar a que pase nada
                    if (matchTimeSeconds % 10 == 0) pushHealthToPhone()
                    if (matchTimeSeconds % 30 == 0) sendHello()
                }
            }
        }
    }

    fun stopTimer() { timerRunning = false }

    fun resetTimer() {
        matchTimeSeconds = 0
        timerRunning = false
        MatchOngoingService.stop(this)
    }

    /**
     * Refresca el marcador que se ve en la actividad en curso. El cronometro
     * corre solo, asi que esto solo hace falta cuando cambia el tanteo.
     */
    fun refreshOngoingActivity() {
        val engine = gameEngine
        if (engine != null && engine.over) {
            // Partido acabado: ya no hay nada "en curso" que mostrar
            MatchOngoingService.stop(this)
            return
        }
        if (timerRunning) MatchOngoingService.start(this)
    }

    fun getTimerDisplay(): String {
        val h = matchTimeSeconds / 3600
        val m = (matchTimeSeconds % 3600) / 60
        val s = matchTimeSeconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    // ── Sensores ─────────────────────────────────────────────────────────

    private var isHrRegistered = false
    private var isStepRegistered = false
    private var initialStepCount = -1f
    var totalSteps by mutableIntStateOf(0)
        private set

    fun registerHeartRateSensor() {
        if (isHrRegistered) return
        if (checkSelfPermission(android.Manifest.permission.BODY_SENSORS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            isHrRegistered = true
        }
    }

    fun registerStepSensor() {
        if (isStepRegistered) return
        // Android 10+: el podometro no entrega eventos sin ACTIVITY_RECOGNITION
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            isStepRegistered = true
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val engine = gameEngine ?: return
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values.getOrNull(0)?.toInt() ?: 0
                if (hr > 0) engine.heartRate = hr
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values.getOrNull(0) ?: 0f
                if (initialStepCount < 0) initialStepCount = steps
                val activeSteps = steps - initialStepCount
                totalSteps = activeSteps.toInt()
                engine.calories = (activeSteps * 0.045f).toInt()
                engine.distanceKm = activeSteps * 0.00075
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 101) return
        permissions.forEachIndexed { i, perm ->
            if (grantResults.getOrNull(i) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                when (perm) {
                    android.Manifest.permission.BODY_SENSORS -> registerHeartRateSensor()
                    android.Manifest.permission.ACTIVITY_RECOGNITION -> registerStepSensor()
                }
            }
        }
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tts = TextToSpeech(this, this)

        val engine = GameEngine(this)
        gameEngine = engine
        engine.onSpeak = { text -> speak(text, engine.lang) }

        // La app arranca por la cuenta: o llega la sesion del movil, o el
        // usuario elige jugar sin ella. Solo se pregunta una vez.
        WatchAccount.load(this)
        if (!WatchAccount.signedIn && !WatchAccount.skipped) {
            engine.currentScreen = "account"
        }

        updateBrightness(engine.brightness)
        PhoneLink.announce(this)

        val neededPerms = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.BODY_SENSORS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            neededPerms.add(android.Manifest.permission.BODY_SENSORS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            neededPerms.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        // Sin este permiso (Android 13+) no se puede publicar la actividad en
        // curso, que es requisito de Play para las apps de Wear OS.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            neededPerms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (neededPerms.isNotEmpty()) requestPermissions(neededPerms.toTypedArray(), 101)

        registerHeartRateSensor()
        registerStepSensor()

        setContent { PadelApp(engine = engine, activity = this) }
    }

    override fun onResume() {
        super.onResume()
        registerHeartRateSensor()
        registerStepSensor()
        PhoneLink.addListeners(this, messageListener, capabilityListener)
        PhoneLink.refreshConnection(this)
        sendHello()
        helloJob?.cancel()
        helloJob = lifecycleScope.launch {
            while (true) {
                delay(30_000)
                PhoneLink.refreshConnection(this@MainActivity)
                sendHello()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        PhoneLink.removeListeners(this, messageListener, capabilityListener)
        helloJob?.cancel()
    }

    fun updateBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value / 255f
        window.attributes = lp
    }

    fun speak(text: String, currentLang: String = "es") {
        if (!ttsReady) return
        val voiceLang = Translations.langs.find { it.id == currentLang }?.voiceLang ?: "es-ES"
        tts.language = Locale.forLanguageTag(voiceLang)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "padel_voice")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts.language = Locale("es", "ES")
        } else {
            Log.e(TAG, "TTS no disponible")
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        timerJob?.cancel()
        helloJob?.cancel()
        (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        gameEngine = null
        instance = null
        super.onDestroy()
    }
}
```

## `app/src/main/java/padelpulseapp2/netlify/app/PadelApp.kt`

```kotlin
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
    val pairState = rememberScalingLazyListState()
    val scoreState = rememberScalingLazyListState()
    val settingsState = rememberScalingLazyListState()
    val historyState = rememberScalingLazyListState()
    val nameState = rememberScalingLazyListState()

    // El indicador sigue a la lista de la pantalla visible. En las pantallas
    // que no tienen scroll (splash, resume, fin) no se muestra ninguno.
    val activeState = when (engine.currentScreen) {
        "lang" -> langState
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
                        "bt" -> PairScreen(engine, activity, pairState)
                        "score" -> ScoreScreen(
                            engine, activity, scoreState, nameState,
                            { engine.currentScreen = "settings" },
                            { engine.currentScreen = "bt" },
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
                onClick = { activity.startTimer(); engine.currentScreen = "score" },
                colors = ButtonDefaults.buttonColors(backgroundColor = accent),
                modifier = Modifier.fillMaxWidth(0.78f).height(38.dp)
            ) {
                Text(ui.done.uppercase(), color = Color.Black, fontWeight = FontWeight.Black, fontSize = PP.Body)
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
        item { BackRow(engine) { engine.currentScreen = "score" } }

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
                    // Jugar ya, sin esperar al movil. Si aparece mas tarde, se
                    // vincula solo y el marcador se pone al dia.
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
                        onClick = { engine.currentScreen = "bt" },
                        label = { Text(if (engine.lang == "es") "MOVIL" else "PHONE", fontSize = PP.Micro) },
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
            // Con candado si no hay sesion: asi se ve antes de entrar por que
            // no hay nada dentro.
            SettingChip(
                if (WatchAccount.signedIn) (if (es) "HISTORIAL" else "HISTORY")
                else (if (es) "HISTORIAL 🔒" else "HISTORY 🔒"),
                accent
            ) { engine.currentScreen = "history" }
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

    // El historial es de quien tiene cuenta, igual que en el movil. El reloj
    // no registra a nadie -no se teclean contraseñas en la muñeca-, asi que
    // la sesion tiene que llegar del movil.
    if (!WatchAccount.signedIn) {
        Column(
            modifier = Modifier.fillMaxSize().background(PP.Bg).padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔒", fontSize = 22.sp)
            Spacer(Modifier.height(6.dp))
            PPLabel(if (es) "HACE FALTA CUENTA" else "ACCOUNT NEEDED", color = accent, size = PP.Label)
            Spacer(Modifier.height(6.dp))
            Text(
                if (es) "Inicia sesion en PadelPulse del movil y el historial aparece aqui solo."
                else "Sign in on the phone app and your history shows up here on its own.",
                color = PP.TextDim, fontSize = PP.Micro,
                textAlign = TextAlign.Center, maxLines = 4
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(backgroundColor = PP.Surface),
                modifier = Modifier.height(34.dp).fillMaxWidth(0.8f).clip(RoundedCornerShape(17.dp))
            ) {
                Text(
                    if (es) "VOLVER" else "BACK",
                    color = accent, fontSize = PP.Micro, fontWeight = FontWeight.Black
                )
            }
        }
        return
    }

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
```

## `app/src/main/java/padelpulseapp2/netlify/app/ScoreScreen.kt`

```kotlin
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
    // Ya no hay modo de solo lectura: se puntua desde el reloj siempre, y el
    // movil se entera. Se conserva la variable para no tocar todo el layout.
    val readOnly = false

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
```

## `app/src/main/java/padelpulseapp2/netlify/app/MatchOngoingService.kt`

```kotlin
package padelpulseapp2.netlify.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import androidx.wear.ongoing.Status

/**
 * Actividad en curso del partido.
 *
 * Wear OS exige que, mientras hay algo "en marcha" (aqui: un partido con el
 * cronometro corriendo), la app publique una Ongoing Activity. Eso es lo que
 * hace que el partido aparezca en la esfera del reloj, en el carrusel de
 * tarjetas y en recientes, y que un toque devuelva al marcador.
 *
 * Sin esto Google Play rechaza la app: "Directrices de calidad de Wear OS:
 * Falta la actividad en curso".
 */
class MatchOngoingService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }
        return START_STICKY
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION") stopForeground(true)
        }
    }

    private fun buildNotification(): Notification {
        createChannel()

        val engine = MainActivity.gameEngine
        val es = engine?.lang == "es"

        // Toque -> vuelve al marcador
        val touchIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (es) "Partido en curso" else "Match in progress"
        val scoreText = scoreSummary(engine)

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ongoing_match)
            .setContentTitle(title)
            .setContentText(scoreText)
            .setContentIntent(touchIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        // El cronometro lo pinta el sistema y corre solo: no hay que estar
        // republicando la notificacion cada segundo.
        val timeZero = SystemClock.elapsedRealtime() -
            ((MainActivity.instance?.matchTimeSeconds ?: 0) * 1000L)

        val status = Status.Builder()
            .addTemplate("#score# · #time#")
            .addPart("score", Status.TextPart(scoreText))
            .addPart("time", Status.StopwatchPart(timeZero))
            .build()

        OngoingActivity.Builder(applicationContext, NOTIFICATION_ID, builder)
            .setStaticIcon(R.drawable.ic_ongoing_match)
            .setTouchIntent(touchIntent)
            .setStatus(status)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .build()
            .apply(applicationContext)

        return builder.build()
    }

    private fun scoreSummary(engine: GameEngine?): String {
        if (engine == null) return "0-0"
        val points = if (engine.isTb || engine.isSuperTbActive()) {
            "${engine.tbPtsA}-${engine.tbPtsB}"
        } else {
            "${engine.getScoreStr("A")}-${engine.getScoreStr("B")}"
        }
        return "${engine.setsA}-${engine.setsB} · ${engine.gamesA}-${engine.gamesB} · $points"
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Partido en curso",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Marcador del partido mientras se juega"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "padelpulse_match"
        private const val NOTIFICATION_ID = 4821
        private const val ACTION_STOP = "padelpulse.STOP_ONGOING"

        /** Arranca o refresca la actividad en curso (marcador actualizado). */
        fun start(context: Context) {
            val intent = Intent(context, MatchOngoingService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        /** La quita: partido terminado o reiniciado. */
        fun stop(context: Context) {
            runCatching {
                context.startService(
                    Intent(context, MatchOngoingService::class.java).setAction(ACTION_STOP)
                )
            }
        }
    }
}
```

## `app/src/main/java/padelpulseapp2/netlify/app/WearListenerService.kt`

```kotlin
package padelpulseapp2.netlify.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.SyncProtocol
import padelpulseapp2.netlify.app.sync.WatchAccount
import java.nio.charset.StandardCharsets

/**
 * Entrada unica de los mensajes del movil en el reloj.
 * Sigue vivo con la app en segundo plano, asi que no asume que haya Activity.
 */
class WearListenerService : WearableListenerService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onMessageReceived(event: MessageEvent) {
        val payload = String(event.data, StandardCharsets.UTF_8)
        val path = event.path
        mainHandler.post { dispatch(path, payload) }
    }

    private fun dispatch(path: String, payload: String) {
        val obj = runCatching { JSONObject(payload) }.getOrElse {
            Log.w(TAG, "Payload no es JSON en $path")
            return
        }

        // Si el movil nos habla, el movil esta ahi
        PhoneLink.setConnected(true)

        if (!PhoneLink.acceptSeq(obj.optLong("seq", 0L))) return

        // La cuenta se guarda aunque la app no este abierta: el servicio
        // arranca solo y la sesion tiene que estar lista para cuando el
        // usuario levante la muñeca.
        if (path == SyncProtocol.PATH_ACCOUNT) {
            WatchAccount.applyFromPhone(applicationContext, obj)
            MainActivity.instance?.onAccountChanged()
            return
        }

        val engine = MainActivity.gameEngine ?: return

        when (path) {
            SyncProtocol.PATH_HELLO -> onHello(obj)
            SyncProtocol.PATH_PAIR, SyncProtocol.PATH_LEGACY_BT -> onPair(obj)
            SyncProtocol.PATH_STATE, SyncProtocol.PATH_LEGACY_SYNC -> onState(engine, obj)
            SyncProtocol.PATH_CMD, SyncProtocol.PATH_LEGACY_POINT -> onCommand(engine, obj)
            SyncProtocol.PATH_SETTINGS -> onSettings(engine, obj)
            // El movil pide salud: se la mandamos nosotros, que tenemos los sensores
            SyncProtocol.PATH_HEALTH -> MainActivity.instance?.pushHealthToPhone()
            else -> Log.d(TAG, "Ruta ignorada: $path")
        }
    }

    private fun onHello(obj: JSONObject) {
        MainActivity.instance?.onPhoneHello(obj.optString("app", ""), obj.optInt("proto", 0))
    }

    private fun onPair(obj: JSONObject) {
        val activity = MainActivity.instance
        val code = obj.optString("code", "")
        when {
            obj.optString("action", "") == "accept" -> activity?.onPairingConfirmed(code)
            obj.optString("action", "") == "reject" -> activity?.onPairingRejected()
            // v2: el movil confirmaba con {confirmed:true, code:"1234"}
            obj.optBoolean("confirmed", false) && code.isNotEmpty() ->
                activity?.onPairingConfirmed(code)
        }
    }

    private fun onState(engine: GameEngine, obj: JSONObject) {
        // v2 anidaba el estado en {"snapshot": "..."}
        val state = if (obj.has("snapshot"))
            runCatching { JSONObject(obj.getString("snapshot")) }.getOrDefault(obj)
        else obj

        // Ya no hay modos: se hace caso al movil si su marcador es mas nuevo
        // que el nuestro. Asi puntua quien quiera desde donde quiera.
        val remoteRev = if (state.has(SyncProtocol.FIELD_REV))
            state.optInt(SyncProtocol.FIELD_REV) else null
        if (!engine.acceptRemoteRev(remoteRev)) return

        PhoneLink.applyingRemote = true
        try {
            val clock = engine.applyState(state)
            engine.adoptRev(remoteRev)
            if (clock >= 0) MainActivity.instance?.setMatchClock(clock)
            MainActivity.instance?.refreshOngoingActivity()
            if (engine.over && engine.currentScreen == "score") engine.currentScreen = "end"
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando estado", e)
        } finally {
            PhoneLink.applyingRemote = false
        }
    }

    /**
     * Accion suelta del movil. Las versiones nuevas mandan el estado entero,
     * pero un movil sin actualizar sigue mandando esto y hay que atenderlo.
     */
    private fun onCommand(engine: GameEngine, obj: JSONObject) {
        val team = obj.optString("team", "A").ifEmpty { "A" }
        when (obj.optString("action", "")) {
            "point" -> engine.addPoint(team)
            "minus" -> engine.decreasePoint(team)
            "undo" -> engine.undo()
            "fault" -> engine.handleFault(engine.serving)
            "serve" -> { engine.serving = team; engine.faultCount = 0 }
            "reset" -> { engine.resetMatch(); MainActivity.instance?.resetTimer() }
            // v2: {"action":"point_A"}
            "point_A" -> engine.addPoint("A")
            "point_B" -> engine.addPoint("B")
            "undo_v2" -> engine.undo()
        }
        MainActivity.instance?.pushStateToPhone()
    }

    private fun onSettings(engine: GameEngine, obj: JSONObject) {
        engine.applySettings(obj)
    }

    companion object {
        private const val TAG = "PadelPulseWatchSvc"
    }
}
```

## `app/src/main/java/padelpulseapp2/netlify/app/Translations.kt`

```kotlin
package padelpulseapp2.netlify.app

data class Language(val id: String, val flag: String, val name: String, val voiceLang: String)

data class VoiceData(
    val zero: String, val fifteen: String, val thirty: String, val forty: String,
    val all: String, val deuce: String, val advantage: String, val game: String,
    val set: String, val goldenPoint: String, val fault: String, val doubleFault: String, val serves: String
)

data class UIStrings(
    val local: String, val away: String, val sets: String, val games: String,
    val settings: String, val chooseLang: String, val mode: String, val play: String,
    val newMatch: String, val undo: String, val fault: String, val doubleFault: String,
    val serves: String, val teamA: String, val teamB: String, val goldenPt: String,
    val superTB: String, val bestOf: String, val theme: String, val voice: String,
    val bluetooth: String, val skip: String, val done: String, val connected: String,
    val notConnected: String, val chMode: String, val start: String, val chooseMode: String,
    val solo: String, val watchCtrl: String, val phoneCtrl: String, val btMode: String
)

object Translations {
    val langs = listOf(
        Language("es", "🇪🇸", "Español", "es-ES"),
        Language("en", "🇬🇧", "English", "en-GB"),
        Language("it", "🇮🇹", "Italiano", "it-IT"),
        Language("fr", "🇫🇷", "Français", "fr-FR"),
        Language("de", "🇩🇪", "Deutsch", "de-DE"),
        Language("fi", "🇫🇮", "Suomi", "fi-FI"),
        Language("zh", "🇨🇳", "中文", "zh-CN"),
        Language("ja", "🇯🇵", "日本語", "ja-JP"),
        Language("ar", "🇸🇦", "عربي", "ar-SA"),
        Language("pt", "🇵🇹", "Português", "pt-PT"),
        Language("ko", "🇰🇷", "한국어", "ko-KR"),
        Language("nl", "🇳🇱", "Nederlands", "nl-NL"),
        Language("sv", "🇸🇪", "Svenska", "sv-SE"),
        // El movil ofrece ruso: sin esta entrada el idioma no viajaria al reloj
        Language("ru", "🇷🇺", "Русский", "ru-RU")
    )

    val vd = mapOf(
        "es" to VoiceData("cero", "quince", "treinta", "cuarenta", "iguales", "iguales", "ventaja", "juego", "set", "punto de oro", "Falta.", "Doble falta.", "Saca"),
        "en" to VoiceData("love", "fifteen", "thirty", "forty", "all", "deuce", "advantage", "game", "set", "golden point", "Fault.", "Double fault.", "Serves"),
        "it" to VoiceData("zero", "quindici", "trenta", "quaranta", "pari", "parità", "vantaggio", "gioco", "set", "punto d'oro", "Fallo.", "Doppio fallo.", "Batte"),
        "fr" to VoiceData("zéro", "quinze", "trente", "quarante", "égalité", "égalité", "avantage", "jeu", "set", "point doré", "Faute.", "Double faute.", "Sert"),
        "de" to VoiceData("null", "fünfzehn", "dreißig", "vierzig", "gleich", "einstand", "vorteil", "spiel", "satz", "goldener punkt", "Fehler.", "Doppelfehler.", "Aufschlag"),
        "fi" to VoiceData("nolla", "viisitoista", "kolmekymmentä", "neljäkymmentä", "tasapeli", "deuce", "etu", "peli", "erä", "kultainen piste", "Virhe.", "Kaksoishuti.", "Syöttää"),
        "zh" to VoiceData("零", "十五", "三十", "四十", "平局", "平分", "占先", "局", "盘", "黄金分", "失误。", "双误。", "发球"),
        "ja" to VoiceData("ラブ", "フィフティーン", "サーティ", "フォーティ", "オール", "デュース", "アドバンテージ", "ゲーム", "セット", "ゴールデンポイント", "フォルト。", "ダブルフォルト。", "サーブ"),
        "ar" to VoiceData("صفر", "خمسة عشر", "ثلاثون", "أربعون", "تعادل", "مساواة", "ميزة", "لعبة", "مجموعة", "نقطة ذهبية", "خطأ.", "خطأ مزدوج.", "يسرف"),
        "pt" to VoiceData("zero", "quinze", "trinta", "quarenta", "iguais", "deuce", "vantagem", "jogo", "set", "ponto de oro", "Falta.", "Dupla falta.", "Saca"),
        "ko" to VoiceData("러브", "피프틴", "서티", "포티", "올", "듀스", "어드밴티지", "게임", "세트", "골든포인트", "폴트.", "더블 폴트.", "서브"),
        "nl" to VoiceData("nul", "vijftien", "dertig", "veertig", "gelijk", "deuce", "voordeel", "game", "set", "gouden punt", "Fout.", "Dubbele fout.", "Serveert"),
        "sv" to VoiceData("noll", "femton", "trettio", "fyrtio", "lika", "deuce", "fördel", "game", "set", "gyllene poäng", "Fel.", "Dubbelfel.", "Servar"),
        "ru" to VoiceData("ноль", "пятнадцать", "тридцать", "сорок", "ровно", "ровно", "больше", "гейм", "сет", "золотое очко", "Ошибка.", "Двойная ошибка.", "Подаёт")
    )

    val ui = mapOf(
        "es" to UIStrings("Local", "Visita", "Sets", "Juegos", "Ajustes", "Elige tu idioma", "Modo", "Jugar", "Nueva Partida", "Deshacer", "Falta", "Doble Falta", "Saca", "Pareja A", "Pareja B", "Punto Oro", "Super TB", "Mejor de", "Tema", "Altavoz", "Bluetooth", "Omitir", "Listo", "Conectado", "Sin conexión", "Cambiar modo", "Comenzar", "Elige el modo", "Solo", "Manda Reloj", "Manda Móvil", "Código BT"),
        "en" to UIStrings("Local", "Away", "Sets", "Games", "Settings", "Choose language", "Mode", "Play", "New Match", "Undo", "Fault", "Double Fault", "Serves", "Team A", "Team B", "Golden Pt", "Super TB", "Best of", "Theme", "Voice", "Bluetooth", "Skip", "Done", "Connected", "Not connected", "Change Mode", "Start", "Choose mode", "Solo", "Watch Ctrl", "Phone Ctrl", "BT Code"),
        "it" to UIStrings("Locale", "Ospite", "Set", "Giochi", "Impostazioni", "Scegli la lingua", "Modalità", "Gioca", "Nuova Partida", "Annulla", "Fallo", "Doppio Fallo", "Batte", "Coppia A", "Coppia B", "Pt Oro", "Super TB", "Al mejor de", "Tema", "Voce", "Bluetooth", "Salta", "Fatto", "Connesso", "Non connesso", "Cambia modalità", "Inizia", "Scegli modalità", "Solo", "Controlla Orologio", "Controlla Telefono", "Codice BT"),
        "fr" to UIStrings("Local", "Visiteur", "Sets", "Jeux", "Réglages", "Choisir la langue", "Mode", "Jouer", "Nouveau Match", "Annuler", "Faute", "Double Faute", "Sert", "Équipe A", "Équipe B", "Pt Or", "Super TB", "Au meilleur de", "Thème", "Voix", "Bluetooth", "Passer", "Prêt", "Connecté", "Non connecté", "Changer de mode", "Commencer", "Choisir le mode", "Solo", "Contrôle Montre", "Contrôle Téléphone", "Code BT"),
        "de" to UIStrings("Lokal", "Gast", "Sätze", "Spiele", "Einstellungen", "Sprache wählen", "Modus", "Spielen", "Neues Spiel", "Rückgängig", "Fehler", "Doppelfehler", "Aufschlag", "Team A", "Team B", "Gold Pkt", "Super TB", "Bester von", "Thema", "Stimme", "Bluetooth", "Überspringen", "Fertig", "Verbunden", "Nicht verbunden", "Modus ändern", "Starten", "Modus wählen", "Solo", "Uhr Steuerung", "Handy Steuerung", "BT Code"),
        "fi" to UIStrings("Koti", "Vieras", "Erät", "Pelit", "Asetukset", "Valitse kieli", "Tila", "Pelaa", "Uusi Ottelu", "Kumoa", "Virhe", "Kaksoishuti", "Syöttää", "Joukkue A", "Joukkue B", "Kulta Pist", "Super TB", "Paras", "Teema", "Ääni", "Bluetooth", "Ohita", "Valmis", "Yhdistetty", "Ei yhteyttä", "Vaihda tilaa", "Aloita", "Valitse tila", "Solo", "Kello Ohjaus", "Puhelin Ohjaus", "BT Koodi"),
        "zh" to UIStrings("本地", "访客", "盘", "局", "设置", "选择语言", "模式", "开始", "新比赛", "撤销", "失误", "双误", "发球", "A队", "B队", "黄金分", "超级TB", "决胜", "主题", "声音", "蓝牙", "跳过", "完成", "已连接", "未连接", "切换模式", "开始", "选择模式", "单机", "手表控制", "手机控制", "BT码"),
        "ja" to UIStrings("ホーム", "アウェイ", "セット", "ゲーム", "設定", "言語を選択", "モード", "プレイ", "新しい試合", "元に戻す", "フォルト", "ダブルフォルト", "サーブ", "チームA", "チームB", "ゴールドPt", "スーパーTB", "ベスト", "テーマ", "音声", "Bluetooth", "スキップ", "完了", "接続済み", "未接続", "モード変更", "開始", "モード選択", "ソロ", "時計制御", "電話制御", "BTコード"),
        "ar" to UIStrings("محلي", "زائر", "مجموعات", "ألعاب", "إعدادات", "اختر اللغة", "الوضع", "العب", "مباراة جديدة", "تراجع", "خطأ", "خطأ مزدوج", "يسرف", "الفريق أ", "الفريق ب", "نقطة ذهبية", "سوبر TB", "الأفضل", "السمة", "الصوت", "بلوتوث", "تخطي", "تم", "متصل", "غير متصل", "تغيير الوضع", "بدء", "اختر الوضع", "منفرد", "تحكم الساعة", "تحكم الهاتف", "رمز BT"),
        "pt" to UIStrings("Local", "Visita", "Sets", "Jogos", "Configurações", "Escolha o idioma", "Modo", "Jogar", "Nova Partida", "Desfazer", "Falta", "Dupla Falta", "Saca", "Dupla A", "Dupla B", "Pt Ouro", "Super TB", "Melhor de", "Tema", "Voz", "Bluetooth", "Ignorar", "Pronto", "Conectado", "Não conectado", "Mudar modo", "Começar", "Escolha o modo", "Solo", "Controle Relógio", "Controle Telefone", "Código BT"),
        "ko" to UIStrings("홈", "어웨이", "세트", "게임", "설정", "언어 선택", "모드", "플레이", "새 경기", "실행 취소", "폴트", "더블 폴트", "서브", "팀 A", "팀 B", "골든 Pt", "슈퍼 TB", "베스트", "테마", "음성", "블루투스", "건너뛰기", "완료", "연결됨", "연결 안됨", "모드 변경", "시작", "모드 선택", "솔로", "시계 제어", "전화 제어", "BT 코드"),
        "nl" to UIStrings("Thuis", "Gast", "Sets", "Games", "Instellingen", "Kies taal", "Modus", "Spelen", "Nieuw Spel", "Ongedaan", "Fout", "Dubbele Fout", "Serveert", "Team A", "Team B", "Goud Pt", "Super TB", "Best van", "Thema", "Stem", "Bluetooth", "Overslaan", "Klaar", "Verbonden", "Niet verbonden", "Modus wijzigen", "Starten", "Kies modus", "Solo", "Horloge Ctrl", "Telefoon Ctrl", "BT Code"),
        "sv" to UIStrings("Hemma", "Borta", "Set", "Spel", "Inställningar", "Välj språk", "Läge", "Spela", "Ny Match", "Ångra", "Fel", "Dubbelfel", "Servar", "Lag A", "Lag B", "Guld Pt", "Super TB", "Bäst av", "Tema", "Röst", "Bluetooth", "Hoppa över", "Klar", "Ansluten", "Ej ansluten", "Ändra läge", "Starta", "Välj läge", "Solo", "Klocka Ctrl", "Telefon Ctrl", "BT Kod"),
        "ru" to UIStrings("Хозяева", "Гости", "Сеты", "Геймы", "Настройки", "Выберите язык", "Режим", "Играть", "Новый матч", "Отменить", "Ошибка", "Двойная ошибка", "Подаёт", "Пара A", "Пара B", "Золотое очко", "Супер ТБ", "До", "Тема", "Голос", "Bluetooth", "Пропустить", "Готово", "Подключено", "Нет связи", "Сменить режим", "Начать", "Выберите режим", "Соло", "Ведут часы", "Ведёт телефон", "Код BT")
    )
}
```

## `app/src/main/java/padelpulseapp2/netlify/app/ThemeUtils.kt`

```kotlin
package padelpulseapp2.netlify.app

import androidx.compose.ui.graphics.Color

object ThemeUtils {
    fun getColor(themeName: String): Color {
        return when (themeName.lowercase()) {
            "neon" -> Color(0xFF00FD87)
            "fuego" -> Color(0xFFFF6B1A)
            "hielo" -> Color(0xFF38D4FF)
            "clasico" -> Color(0xFFC8E840)
            "noche" -> Color(0xFFA070FF)
            "oro" -> Color(0xFFFFD700)
            else -> Color(0xFF00FD87)
        }
    }

    fun getBgColor(themeName: String): Color {
        return getColor(themeName).copy(alpha = 0.2f)
    }

    fun getHexColor(theme: String): String = when (theme.lowercase()) {
        "neon" -> "#00FD87"
        "fuego" -> "#FF6B1A"
        "hielo" -> "#38D4FF"
        "clasico" -> "#C8E840"
        "noche" -> "#A070FF"
        "oro" -> "#FFD700"
        else -> "#00FD87"
    }

    fun getDotColor(theme: String): Color = getColor(theme)

    /** Inverso de getHexColor: el movil v2 manda el tema como color hex. */
    fun themeFromHex(hex: String): String = when (hex.uppercase().trim()) {
        "#00FD87" -> "neon"
        "#FF6B1A" -> "fuego"
        "#38D4FF" -> "hielo"
        "#C8E840" -> "clasico"
        "#A070FF" -> "noche"
        "#FFD700" -> "oro"
        else -> "neon"
    }

    /** Fondo tenue con el acento del tema, para tarjetas y estados activos. */
    fun tint(theme: String, alpha: Float): Color = getColor(theme).copy(alpha = alpha)

    val themesList = listOf("neon", "fuego", "hielo", "clasico", "noche", "oro")
}
```

## `app/src/main/java/padelpulseapp2/netlify/app/ui/PadelTheme.kt`

```kotlin
package padelpulseapp2.netlify.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Text

/**
 * Piezas visuales compartidas por todas las pantallas del reloj.
 * Objetivo: una sola escala tipografica y un solo lenguaje de tarjetas, para que
 * la app no parezca seis pantallas distintas pegadas.
 */
object PP {

    // Superficies (negro puro de fondo: en pantallas OLED gasta menos bateria)
    val Bg = Color(0xFF000000)
    val Surface = Color(0xFF121212)
    val SurfaceHigh = Color(0xFF1C1C1C)
    val Line = Color(0xFF262626)
    val TextDim = Color(0xFF8A8A8A)
    val TextMuted = Color(0xFF5C5C5C)
    val Danger = Color(0xFFFF4E50)
    val Warn = Color(0xFFFFB020)

    // Escala tipografica: 5 tamaños, ni uno mas
    val Display = 34.sp
    val Title = 15.sp
    val Body = 12.sp
    val Label = 10.sp
    val Micro = 8.sp

    val CardShape = RoundedCornerShape(16.dp)
    val PillShape = RoundedCornerShape(50)
}

/** Etiqueta en mayusculas con tracking: el patron de titulillo de toda la app. */
@Composable
fun PPLabel(
    text: String,
    color: Color = PP.TextDim,
    size: androidx.compose.ui.unit.TextUnit = PP.Micro,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        color = color,
        fontSize = size,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = modifier
    )
}

/** Tarjeta base. Todo lo que agrupa informacion usa esta, sin excepciones. */
@Composable
fun PPCard(
    modifier: Modifier = Modifier,
    accent: Color? = null,
    padding: PaddingValues = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(PP.CardShape)
            .background(PP.Surface)
            .then(
                if (accent != null) Modifier.border(1.dp, accent.copy(alpha = 0.35f), PP.CardShape)
                else Modifier
            )
            .padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

/**
 * Pildora de estado del enlace con el movil. Se ve en todas las pantallas:
 * en pista hay que saber de un vistazo si el marcador esta viajando o no.
 */
@Composable
fun PPStatusPill(
    label: String,
    color: Color,
    pulsing: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(PP.PillShape)
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.35f), PP.PillShape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (pulsing) color else color.copy(alpha = 0.6f))
        )
        PPLabel(label, color = color, size = PP.Micro)
    }
}

/** Degradado sutil detras del marcador, con el acento del tema activo. */
fun accentGlow(accent: Color): Brush = Brush.verticalGradient(
    listOf(accent.copy(alpha = 0.14f), Color.Transparent)
)
```

## `app/src/main/java/padelpulseapp2/netlify/app/sync/SyncProtocol.kt`

```kotlin
package padelpulseapp2.netlify.app.sync

import org.json.JSONObject

/**
 * Contrato de mensajes entre el reloj y el movil.
 * Espejo exacto de docs/PROTOCOLO_SINCRONIZACION.md y del objeto PPSync del movil.
 * Si tocas algo aqui, tocalo tambien en el movil y sube versionCode en las dos apps.
 */
object SyncProtocol {

    const val VERSION = 3

    // El prefijo /padel es obligatorio: el intent-filter del servicio del movil
    // filtra por pathPrefix="/padel" y sin el no llegan mensajes en segundo plano.
    const val PATH_HELLO = "/padel/hello"
    const val PATH_PAIR = "/padel/pair"
    const val PATH_STATE = "/padel/state"
    const val PATH_CMD = "/padel/cmd"
    const val PATH_SETTINGS = "/padel/settings"
    const val PATH_HEALTH = "/padel/health"
    /** Sesion de la cuenta: el movil se la pasa al reloj, no sale a internet. */
    const val PATH_ACCOUNT = "/padel/account"

    // Rutas v2 que seguimos aceptando para no romper con una app sin actualizar
    const val PATH_LEGACY_SYNC = "/padel/sync"
    const val PATH_LEGACY_POINT = "/padel/point"
    const val PATH_LEGACY_BT = "/padel/bt"

    const val SRC_WATCH = "watch"
    const val SRC_PHONE = "phone"

    /**
     * Ya no hay modos: las dos apps van siempre a la vez y puntua quien
     * quiera. Las constantes se quedan para entenderse con versiones
     * anteriores, que siguen mandando su modo en cada mensaje.
     */
    const val MODE_SYNC = "SYNC"

    /**
     * Quien gana cuando los dos tocan a la vez. Cada cambio local sube el
     * numero de revision; el que llegue con revision mas alta manda. Si
     * empatan -dos toques en el mismo instante- gana el movil, por decidir
     * algo estable: si cada uno eligiera distinto, los marcadores quedarian
     * diferentes para siempre.
     */
    const val FIELD_REV = "rev"

    const val MODE_SOLO = "SOLO"
    const val MODE_PHONE = "PHONE"
    const val MODE_WATCH = "WATCH"

    /** Normaliza cualquier variante historica de modo a SOLO / PHONE / WATCH. */
    fun normalizeMode(raw: String?): String = when (raw?.uppercase()?.trim()) {
        "PHONE", "MOVIL_MANDA", "MOBILE", "MOVIL" -> MODE_PHONE
        "WATCH", "RELOJ_MANDA", "RELOJ" -> MODE_WATCH
        else -> MODE_SOLO
    }

    /**
     * Lee un campo que puede venir como null JSON. `optString` devuelve la cadena
     * "null" cuando el valor es JSONObject.NULL, y esa cadena es truthy: ese era el
     * bug que dejaba la ventaja pegada y el marcador clavado en 40.
     */
    fun optNullableString(obj: JSONObject, key: String): String? {
        if (!obj.has(key) || obj.isNull(key)) return null
        val v = obj.optString(key, "")
        return if (v.isEmpty() || v == "null") null else v
    }

    fun envelope(path: String, seq: Long): JSONObject = JSONObject()
        .put("v", VERSION)
        .put("src", SRC_WATCH)
        .put("seq", seq)
        .put("ts", System.currentTimeMillis())
        .put("path", path)

    fun hello(seq: Long, appVersion: String, mode: String, paired: Boolean): String =
        envelope(PATH_HELLO, seq)
            .put("app", appVersion)
            .put("proto", VERSION)
            .put("mode", normalizeMode(mode))
            .put("paired", paired)
            .toString()

    fun pairRequest(seq: Long, code: String, device: String): String =
        envelope(PATH_PAIR, seq)
            .put("action", "request")
            .put("code", code)
            .put("device", device)
            .toString()

    fun command(seq: Long, action: String, team: String?): String =
        envelope(PATH_CMD, seq)
            .put("action", action)
            .apply { if (team != null) put("team", team) }
            .toString()

    fun health(seq: Long, hr: Int, kcal: Int, km: Double, steps: Int): String =
        envelope(PATH_HEALTH, seq)
            .put("hr", hr)
            .put("kcal", kcal)
            .put("km", km)
            .put("steps", steps)
            .toString()

    fun settings(
        seq: Long, lang: String, theme: String, colorHex: String,
        goldenPoint: Boolean, superTieBreak: Boolean, bestOf: Int,
        nameA: String, nameB: String, mode: String
    ): String = envelope(PATH_SETTINGS, seq)
        .put("lang", lang)
        .put("theme", theme)
        .put("color", colorHex)
        .put("goldenPoint", goldenPoint)
        .put("superTieBreak", superTieBreak)
        .put("bestOf", bestOf)
        // maxSets: nombre v2 del mismo campo, para relojes/moviles sin actualizar
        .put("maxSets", bestOf)
        .put("nameA", nameA)
        .put("nameB", nameB)
        .put("mode", normalizeMode(mode))
        .toString()
}
```

## `app/src/main/java/padelpulseapp2/netlify/app/sync/PhoneLink.kt`

```kotlin
package padelpulseapp2.netlify.app.sync

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.atomic.AtomicLong

/**
 * Enlace del reloj con el movil: descubrimiento de nodo, estado de conexion,
 * numeracion anti-eco y envio con reintento. Un solo sitio por el que sale todo.
 */
object PhoneLink {

    private const val TAG = "PadelPulseLink"
    const val CAPABILITY_PHONE = "padelpulse_phone"
    const val CAPABILITY_WATCH = "padelpulse_watch"

    /** Hay un movil alcanzable ahora mismo. */
    var connected by mutableStateOf(false)
        private set

    /** El movil ha aceptado el codigo: la sincronizacion esta activa. */
    var paired by mutableStateOf(false)

    var pairedCode by mutableStateOf("")
    var phoneName by mutableStateOf("")

    /** Ultimo error legible, para poder enseñarlo en pantalla en vez de fallar en silencio. */
    var lastError by mutableStateOf<String?>(null)

    private val seq = AtomicLong(System.currentTimeMillis() / 1000)
    private var lastSeenPhoneSeq = -1L

    /** True mientras aplicamos un estado remoto: impide reemitirlo y crear un bucle. */
    @Volatile
    var applyingRemote = false

    fun nextSeq(): Long = seq.incrementAndGet()

    /**
     * Descarta mensajes repetidos o desordenados del movil.
     * Devuelve true si hay que procesarlo.
     */
    fun acceptSeq(s: Long): Boolean {
        if (s <= 0) return true // emisor antiguo sin seq
        if (s <= lastSeenPhoneSeq) return false
        lastSeenPhoneSeq = s
        return true
    }

    fun resetSeqWindow() {
        lastSeenPhoneSeq = -1L
    }

    fun setConnected(value: Boolean, name: String = "") {
        connected = value
        if (name.isNotEmpty()) phoneName = name
        if (!value) resetSeqWindow()
    }

    /** Publica la capacidad del reloj para que el movil pueda descubrirlo. */
    fun announce(context: Context) {
        Thread {
            runCatching {
                Tasks.await(Wearable.getCapabilityClient(context).addLocalCapability(CAPABILITY_WATCH))
            }.onFailure { Log.w(TAG, "No se pudo publicar la capacidad", it) }
            refreshConnection(context)
        }.start()
    }

    fun refreshConnection(context: Context) {
        Thread {
            runCatching {
                val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                val near = nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull()
                setConnected(near != null, near?.displayName ?: "")
            }.onFailure {
                setConnected(false)
                Log.w(TAG, "No hay nodos", it)
            }
        }.start()
    }

    /**
     * Envia a todos los nodos conectados. Un solo reintento: si el movil esta
     * dormido el primer envio falla y el segundo, 400 ms despues, suele entrar.
     */
    fun send(context: Context, path: String, payload: String) {
        Thread {
            var ok = false
            repeat(2) { attempt ->
                if (ok) return@repeat
                runCatching {
                    val nodes = Tasks.await(Wearable.getNodeClient(context).connectedNodes)
                    if (nodes.isEmpty()) {
                        setConnected(false)
                        return@runCatching
                    }
                    val bytes = payload.toByteArray(Charsets.UTF_8)
                    nodes.forEach { node ->
                        Tasks.await(Wearable.getMessageClient(context).sendMessage(node.id, path, bytes))
                    }
                    setConnected(true, nodes.first().displayName)
                    lastError = null
                    ok = true
                }.onFailure { e ->
                    if (attempt == 1) {
                        lastError = e.message
                        setConnected(false)
                        Log.w(TAG, "Fallo enviando $path", e)
                    } else {
                        Thread.sleep(400)
                    }
                }
            }
        }.start()
    }

    fun addListeners(
        context: Context,
        messageListener: MessageClient.OnMessageReceivedListener,
        capabilityListener: CapabilityClient.OnCapabilityChangedListener
    ) {
        runCatching {
            Wearable.getMessageClient(context).addListener(messageListener)
            Wearable.getCapabilityClient(context)
                .addListener(capabilityListener, CAPABILITY_PHONE)
        }.onFailure { Log.w(TAG, "No se pudieron registrar listeners", it) }
    }

    fun removeListeners(
        context: Context,
        messageListener: MessageClient.OnMessageReceivedListener,
        capabilityListener: CapabilityClient.OnCapabilityChangedListener
    ) {
        runCatching {
            Wearable.getMessageClient(context).removeListener(messageListener)
            Wearable.getCapabilityClient(context).removeListener(capabilityListener)
        }
    }
}
```

## `app/src/main/java/padelpulseapp2/netlify/app/sync/WatchAccount.kt`

```kotlin
package padelpulseapp2.netlify.app.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Cuenta del usuario en el reloj.
 *
 * A proposito NO se teclea la contraseña aqui: escribir un correo y una
 * contraseña en una pantalla de 45 mm es horrible, y Wear OS recomienda
 * delegar el inicio de sesion en el movil. La sesion llega desde el movil
 * por el Data Layer -canal cifrado entre dos dispositivos ya emparejados,
 * no sale a internet- y el reloj solo la guarda para mostrar de quien es.
 *
 * Si alguien no quiere cuenta, se juega igual: la cuenta solo sirve para
 * respaldar el historial.
 */
object WatchAccount {

    private const val PREFS = "padel_prefs"
    private const val KEY_EMAIL = "acc_email"
    private const val KEY_NAME = "acc_name"
    private const val KEY_TOKEN = "acc_token"
    private const val KEY_EXPIRES = "acc_expires"
    private const val KEY_SKIPPED = "acc_skipped"

    var email by mutableStateOf("")
        private set
    var name by mutableStateOf("")
        private set
    var signedIn by mutableStateOf(false)
        private set
    /** El usuario eligio jugar sin cuenta: no volvemos a preguntar. */
    var skipped by mutableStateOf(false)
        private set

    private var token: String = ""
    private var expiresAt: Long = 0L

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        email = p.getString(KEY_EMAIL, "") ?: ""
        name = p.getString(KEY_NAME, "") ?: ""
        token = p.getString(KEY_TOKEN, "") ?: ""
        expiresAt = p.getLong(KEY_EXPIRES, 0L)
        skipped = p.getBoolean(KEY_SKIPPED, false)
        signedIn = token.isNotEmpty() && email.isNotEmpty()
    }

    /** Sesion recibida del movil. */
    fun applyFromPhone(context: Context, obj: JSONObject) {
        when (obj.optString("action", "")) {
            "session" -> {
                email = obj.optString("email", "")
                name = obj.optString("name", "").ifEmpty { email.substringBefore("@") }
                token = obj.optString("token", "")
                expiresAt = obj.optLong("expires", 0L)
                signedIn = token.isNotEmpty() && email.isNotEmpty()
                if (signedIn) skipped = false
                persist(context)
            }
            "signout" -> signOut(context)
        }
    }

    fun signOut(context: Context) {
        email = ""; name = ""; token = ""; expiresAt = 0L; signedIn = false
        persist(context)
    }

    /** Jugar sin cuenta. */
    fun skip(context: Context) {
        skipped = true
        persist(context)
    }

    /** True cuando la sesion caduco y hay que volver a abrir el movil. */
    fun isExpired(): Boolean = signedIn && expiresAt > 0 && System.currentTimeMillis() > expiresAt

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_NAME, name)
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES, expiresAt)
            .putBoolean(KEY_SKIPPED, skipped)
            .apply()
    }
}
```

## `app/build.gradle.kts`

```kotlin
import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma leida de keystore.properties (fuera del control de versiones).
// Si el fichero no existe, el release sale SIN firmar y puedes usar
// Build > Generate Signed App Bundle, que pide el keystore a mano.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
val hasKeystore = keystorePropsFile.exists()

android {
    namespace = "padelpulseapp2.netlify.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "padelpulseapp2.netlify.app"
        minSdk = 30
        targetSdk = 35
        // IMPORTANTE: reloj y movil comparten applicationId, asi que Google Play
        // exige versionCode DISTINTO en cada uno. El del reloj va en su propia
        // serie -el del movil x10- y siempre por encima: movil 517, reloj 5170.
        versionCode = 5170
        versionName = "5.1.7"
    }

    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
        release {
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation(libs.play.services.wearable)
    implementation(libs.activity.compose)
    implementation(platform(libs.compose.bom))
    implementation(libs.ui)
    implementation(libs.ui.graphics)
    implementation(libs.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    // Actividad en curso: obligatoria para pasar la revision de Wear OS
    implementation("androidx.wear:wear-ongoing:1.0.0")
}
```

## `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}```

## `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PadelPulse WearOS"
include(":app")
```

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.dependency.useConstraints=true
android.dependency.excludeLibraryComponentsFromConstraints=true
android.defaults.buildfeatures.resvalues=true
android.sdk.defaultTargetSdkToCompileSdkIfUnset=false
android.enableAppCompileTimeRClass=false
android.usesSdkInManifest.disallowed=false
android.uniquePackageNames=false
android.r8.strictFullModeForKeepRules=false
android.r8.optimizedResourceShrinking=false
android.builtInKotlin=false
android.newDsl=false
```

## `app/proguard-rules.pro`

```text
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\34620\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.

# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any custom rules here that might be necessary for your project.
-keep class padelpulseapp2.netlify.app.** { *; }
```
