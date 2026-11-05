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

        /**
         * La pareja A eres tu. Siempre, igual que en el movil.
         *
         * De esa regla cuelgan las estadisticas y quien sale como jugador
         * principal en el historial, asi que el reloj no puede decidirlo de
         * otra forma que el movil.
         */
        const val NOMBRE_A_POR_DEFECTO = "YO Y PAREJA"

        /** True si ese nombre es el generico y no uno que haya puesto nadie. */
        fun esNombreGenerico(nombre: String): Boolean {
            val v = nombre.trim().uppercase()
            return v.isEmpty() || v == NOMBRE_A_POR_DEFECTO || v == "PAREJA A" || v == "TEAM A"
        }
    }

    private val prefs: SharedPreferences? = context?.getSharedPreferences("padel_prefs", Context.MODE_PRIVATE)

    var currentScreen by mutableStateOf("splash")

    /**
     * Pagina del marcador a la que hay que ir al volver a el: 0 marcador,
     * 1 salud, 2 ajustes, 3 sincronizacion. -1 = la que hubiera. Solo
     * navegacion, no se guarda.
     */
    var requestedPage by mutableIntStateOf(-1)

    /** Vuelve al marcador abriendo esa pagina. */
    fun goToPage(page: Int) {
        requestedPage = page
        currentScreen = "score"
    }
    /**
     * Partido recien empezado: el marcador pregunta primero quien saca. Solo
     * navegacion, no se guarda (al reabrir la app ya no se pregunta).
     */
    var askServe by mutableStateOf(false)

    var theme by mutableStateOf("neon")
    var lang by mutableStateOf("es")
    var voiceEnabled by mutableStateOf(true)
    var pairingCode by mutableStateOf("----")
    var isConnected by mutableStateOf(false)
    
    var nameA by mutableStateOf(NOMBRE_A_POR_DEFECTO)
    var nameB by mutableStateOf("PAREJA B")

    // Cada lado es una pareja: dos jugadores. Vacios, el marcador ensena el
    // nombre de la pareja. Viajan con los ajustes, igual que en el movil
    // (teamA.playerA / playerB).
    var playerA1 by mutableStateOf("")
    var playerA2 by mutableStateOf("")
    var playerB1 by mutableStateOf("")
    var playerB2 by mutableStateOf("")

    /** Jugadores con nombre de una pareja, en orden. */
    fun players(team: String): List<String> =
        (if (team == "A") listOf(playerA1, playerA2) else listOf(playerB1, playerB2))
            .map { it.trim() }.filter { it.isNotEmpty() }

    /** "A", "B" (nombre de pareja) o "A1", "A2", "B1", "B2" (jugador). */
    fun getName(key: String): String = when (key) {
        "A" -> nameA; "B" -> nameB
        "A1" -> playerA1; "A2" -> playerA2; "B1" -> playerB1; "B2" -> playerB2
        else -> ""
    }

    fun setName(key: String, value: String) {
        when (key) {
            "A" -> nameA = value; "B" -> nameB = value
            "A1" -> playerA1 = value; "A2" -> playerA2 = value
            "B1" -> playerB1 = value; "B2" -> playerB2 = value
        }
        if (value.isNotBlank()) saveTeamNameToHistory(value.uppercase())
    }

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

    /**
     * Revision del marcador. Sube en cada cambio hecho aqui y viaja con el
     * estado: si el movil y el reloj puntuan casi a la vez, gana el que traiga
     * la revision mas alta. Va antes del init: se lee al cargar el partido
     * guardado, y una propiedad declarada despues aun no existiria.
     */
    var syncRev by mutableIntStateOf(0)
        private set

    /**
     * Que jugador de cada pareja saca en su proximo juego de saque: 0 = el
     * jugador 1, 1 = el jugador 2. Cada vez que una pareja termina un juego de
     * saque le toca al otro, asi que los saques rotan A1, B1, A2, B2... Van
     * antes del init por lo mismo que syncRev.
     */
    var srvA by mutableIntStateOf(0)
    var srvB by mutableIntStateOf(0)

    var tbSrv by mutableStateOf("A")
    var tbN by mutableIntStateOf(0)
    var winner by mutableStateOf<String?>(null)
    
    var faultCount by mutableIntStateOf(0)
    var lastPointWinner by mutableStateOf<String?>(null)

    /**
     * Resultado de cada set terminado, en orden. El marcador los ensena entre
     * los juegos (6-4 · 3-6) y viajan al movil en "setsDetail": sin ellos el
     * movil solo sabia cuantos sets llevaba cada uno y se los inventaba 6-0.
     */
    var setScores by mutableStateOf<List<SetResult>>(emptyList())

    /** Un set terminado. [a] y [b] son juegos, o puntos si fue super tie-break. */
    data class SetResult(val a: Int, val b: Int, val winner: String, val tiebreak: Boolean) {
        override fun toString() = "$a-$b"
    }

    /** Cronometro del partido en segundos. Lo mantiene al dia MainActivity. */
    var clockSeconds by mutableIntStateOf(0)

    // Health data
    var calories by mutableIntStateOf(0)
    var heartRate by mutableIntStateOf(0)
    var distanceKm by mutableStateOf(0.0)

    // Pulso medio y maximo del partido, solo con lecturas reales del sensor
    // mientras corre el cronometro. 0 = sin datos (se ensena "–", nunca un
    // numero inventado). No se guardan: al reabrir la app empiezan de nuevo.
    var hrMax by mutableIntStateOf(0)
    var hrAvg by mutableIntStateOf(0)
    private var hrSum = 0L
    private var hrCount = 0

    fun recordHeartRate(hr: Int) {
        // Fuera de rango = lectura mala del sensor (reloj flojo, sin contacto)
        if (hr !in 30..230) return
        hrSum += hr
        hrCount++
        hrAvg = (hrSum / hrCount).toInt()
        if (hr > hrMax) hrMax = hr
    }

    private fun resetHeartRateStats() {
        hrSum = 0L; hrCount = 0; hrMax = 0; hrAvg = 0
    }

    // New: Screen brightness (0-255)
    var brightness by mutableStateOf(150f)

    var onSpeak: ((String) -> Unit)? = null

    private val history = mutableListOf<StateSnapshot>()

    data class StateSnapshot(
        val ptsA: Int, val ptsB: Int, val gamesA: Int, val gamesB: Int,
        val setsA: Int, val setsB: Int, val isDeuce: Boolean, val adv: String?,
        val isTb: Boolean, val tbPtsA: Int, val tbPtsB: Int, val over: Boolean,
        val serving: String, val tbSrv: String, val tbN: Int, val faultCount: Int, 
        val winner: String?, val lastPointWinner: String?, val goldenPointActive: Boolean,
        val setScores: List<SetResult> = emptyList(),
        val srvA: Int = 0, val srvB: Int = 0
    )

    init {
        loadFromDisk()
    }

    fun saveState() {
        history.add(
            StateSnapshot(
                ptsA, ptsB, gamesA, gamesB, setsA, setsB, isDeuce, adv,
                isTb, tbPtsA, tbPtsB, over, serving, tbSrv, tbN, faultCount, winner, lastPointWinner, goldenPointActive,
                setScores, srvA, srvB
            )
        )
        saveToDisk()
    }

    /**
     * Guarda sin apuntar una jugada para deshacer. Para ajustes como silenciar
     * la voz: con saveState() el siguiente "deshacer" no haria nada visible.
     */
    fun persist() = saveToDisk()

    fun resetMatch() {
        history.clear()
        ptsA = 0; ptsB = 0; gamesA = 0; gamesB = 0; setsA = 0; setsB = 0
        isDeuce = false; adv = null; isTb = false; tbPtsA = 0; tbPtsB = 0
        over = false; faultCount = 0; tbN = 0; winner = null
        serving = "A"; tbSrv = "A"; lastPointWinner = null
        srvA = 0; srvB = 0
        goldenPointActive = false
        setScores = emptyList()
        resetHeartRateStats()
        saveToDisk()
    }

    fun hasSavedMatch(): Boolean {
        return (ptsA > 0 || ptsB > 0 || gamesA > 0 || gamesB > 0 || setsA > 0 || setsB > 0) && !over
    }

    fun undo() {
        if (history.isNotEmpty()) {
            // removeLast() no: en Android 15 java.util.List trae el suyo y tapa
            // la extension de Kotlin, asi que al compilar contra SDK 35+ se
            // enlaza un metodo que en Android 14 y anteriores no existe.
            val last = history.removeAt(history.lastIndex)
            ptsA = last.ptsA; ptsB = last.ptsB; gamesA = last.gamesA; gamesB = last.gamesB
            setsA = last.setsA; setsB = last.setsB; isDeuce = last.isDeuce; adv = last.adv
            isTb = last.isTb; tbPtsA = last.tbPtsA; tbPtsB = last.tbPtsB
            over = last.over; serving = last.serving; tbSrv = last.tbSrv
            tbN = last.tbN; faultCount = last.faultCount; winner = last.winner
            lastPointWinner = last.lastPointWinner; goldenPointActive = last.goldenPointActive
            setScores = last.setScores
            srvA = last.srvA; srvB = last.srvB
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

    // ── Lo que se canta por voz ──────────────────────────────────────────
    // Mismas reglas que los botones: guardan para deshacer y cantan el
    // resultado. MainActivity.applyVoice() los llama con lo que entiende
    // voice/VoiceParser.

    /** "Quince treinta": pone el marcador del juego tal cual (0-3 = 0, 15, 30, 40). */
    fun setPoints(a: Int, b: Int) {
        if (over || isTb || isSuperTbActive()) return
        saveState()
        faultCount = 0
        ptsA = a.coerceIn(0, 3); ptsB = b.coerceIn(0, 3)
        isDeuce = ptsA == 3 && ptsB == 3
        adv = null
        goldenPointActive = isDeuce && goldenPoint
        saveToDisk()
        triggerSpeak(if (isDeuce) "deuce" else "score", serving)
    }

    /** "Ventaja rojos". Si no estaban iguales, es un punto para ellos. */
    fun giveAdvantage(team: String) {
        if (over) return
        if (!isDeuce || goldenPointActive || goldenPoint) { addPoint(team); return }
        saveState()
        faultCount = 0
        adv = team
        saveToDisk()
        triggerSpeak("adv", team)
    }

    /** "Juego Edu". En un tie-break, el juego que falta es el set. */
    fun forceGame(team: String) {
        if (over) return
        saveState()
        faultCount = 0
        if (isTb || isSuperTbActive()) winSet(team) else winGame(team)
        saveToDisk()
    }

    /** "Set rojos": se lo apunta con los juegos que haya. */
    fun forceSet(team: String) {
        if (over) return
        saveState()
        faultCount = 0
        winSet(team)
        saveToDisk()
    }

    /** "Como vamos": canta el marcador sin tocarlo. */
    fun speakScore() {
        when {
            isTb || isSuperTbActive() -> triggerSpeak("tb", serving)
            isDeuce && adv != null -> triggerSpeak("adv", adv!!)
            isDeuce -> triggerSpeak("deuce", serving)
            else -> triggerSpeak("score", serving)
        }
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
        // La pareja que acaba de sacar cambia de jugador para su proximo saque
        if (serving == "A") srvA = 1 - srvA else srvB = 1 - srvB
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
        // Juegos del set con el ganador delante, antes de ponerlos a cero. Un
        // set de tie-break queda 6-6 en juegos, pero nadie lo gana "seis a
        // seis": se canta 7-6. El super tie-break va con sus puntos (10-8).
        val setScore = if (isSuperTbActive()) {
            "${if (tw == "A") tbPtsA else tbPtsB} - ${if (tw == "A") tbPtsB else tbPtsA}"
        } else {
            var gan = if (tw == "A") gamesA else gamesB
            val per = if (tw == "A") gamesB else gamesA
            if (gan == per) gan += 1
            "$gan - $per"
        }

        // Para el marcador y el movil, siempre visto desde A: 6-4, 7-6, 10-8
        setScores = setScores + if (isSuperTbActive()) {
            SetResult(tbPtsA, tbPtsB, tw, true)
        } else {
            var a = gamesA
            var b = gamesB
            if (a == b) { if (tw == "A") a++ else b++ }
            SetResult(a, b, tw, isTb)
        }

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
            triggerSpeak("set", tw, setScore)
        }
    }

    /**
     * Lo que se canta tras cada punto. Tiene que sonar igual que en el movil
     * (marcadorCantado en code.html): si no, con los dos sonando a la vez en la
     * pista uno dice "quince, cero" y el otro "cero, quince".
     *
     * Se canta primero el punto de quien saca, que es como se canta en pista.
     * Al acabar un juego o un set se dice ademas quien saca ahora, que es lo
     * que mas se discute entre punto y punto.
     */
    private fun triggerSpeak(key: String, t: String, setScore: String = "") {
        if (!voiceEnabled) return
        val v = Translations.vd[lang] ?: Translations.vd["es"]!!
        val teamName = if (t == "A") nameA else nameB
        val nowServes = "${if (serving == "A") nameA else nameB} ${v.serves}."

        val text = when (key) {
            "score" -> {
                // Mismo punto por debajo de 40: "treinta iguales"
                if (ptsA == ptsB && ptsA < 3) "${getVScore(ptsA, v)} ${v.all}."
                else {
                    val saca = if (serving == "A") ptsA else ptsB
                    val resto = if (serving == "A") ptsB else ptsA
                    "${getVScore(saca, v)}, ${getVScore(resto, v)}."
                }
            }
            "deuce" -> (if (goldenPointActive) v.goldenPoint else v.deuce) + "."
            "adv" -> "${v.advantage} $teamName."
            "game" -> "${v.game} $teamName. $gamesA - $gamesB." +
                (if (isTb) " Tie-break." else "") + " $nowServes"
            "set" -> "${v.set} $teamName. $setScore. $nowServes"
            "match" -> "${v.game} $teamName."
            // En el desempate el saque ya ha rotado: se canta desde quien saca
            // el punto siguiente, igual que el movil.
            "tb" -> {
                val saca = if (serving == "A") tbPtsA else tbPtsB
                val resto = if (serving == "A") tbPtsB else tbPtsA
                "$saca - $resto."
            }
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

    /**
     * Cual de los dos jugadores de la pareja que saca esta sacando: 0 o 1.
     * En juego normal, el que le toca a esa pareja. En tie-break el saque va
     * 1 punto, luego 2 y 2, rotando A1, B1, A2, B2: el turno k = (n+1)/2 dice
     * si saca el primero de la pareja (k 0-1) o su compañero (k 2-3).
     */
    fun serverIndex(): Int {
        val base = if (serving == "A") srvA else srvB
        if (!(isTb || isSuperTbActive())) return base
        val k = ((tbPtsA + tbPtsB + 1) / 2) % 4
        return if (k < 2) base else 1 - base
    }

    /** Nombre del jugador que saca; si no hay jugadores puestos, el de la pareja. */
    fun serverName(): String {
        val raw = if (serving == "A") listOf(playerA1, playerA2) else listOf(playerB1, playerB2)
        return raw[serverIndex()].trim().ifEmpty { getName(serving) }
    }

    /** Desde donde se saca este punto. Con punto de oro elige el lado quien resta. */
    fun serveSideLabel(es: Boolean): String = when {
        over -> ""
        goldenPointActive -> if (es) "ELIGE RESTO" else "RECEIVER CHOOSES"
        getServeSide() == "R" -> if (es) "DERECHA" else "RIGHT"
        else -> if (es) "IZQUIERDA" else "LEFT"
    }

    /** Cambia a mano quien de la pareja que saca esta sacando (se puede deshacer). */
    fun toggleServerPlayer() {
        saveState()
        if (serving == "A") srvA = 1 - srvA else srvB = 1 - srvB
        saveToDisk()
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
                // Que jugador de cada pareja saca en su proximo juego (0/1).
                // Las versiones anteriores lo ignoran.
                .put("srvPlayer", JSONObject().put("A", srvA).put("B", srvB))
                .put("faults", faultCount)
                .put("over", over)
                .put("winner", winner ?: ""))
            .put("tb", JSONObject()
                .put("A", tbPtsA).put("B", tbPtsB)
                .put("serving", tbSrv).put("n", tbN))
            .put("health", JSONObject()
                .put("hr", heartRate).put("kcal", calories).put("km", distanceKm))
            .put("clock", clockSeconds)
            .put("setsDetail", setsDetailJson())
            .toString()
    }

    private fun setsDetailJson(): org.json.JSONArray = org.json.JSONArray().apply {
        setScores.forEach {
            put(JSONObject().put("a", it.a).put("b", it.b).put("winner", it.winner).put("tiebreak", it.tiebreak))
        }
    }

    private fun parseSetsDetail(arr: org.json.JSONArray?): List<SetResult>? {
        if (arr == null) return null
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let {
                SetResult(it.optInt("a"), it.optInt("b"), it.optString("winner", "A"), it.optBoolean("tiebreak"))
            }
        }
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
            // Jugador que saca en cada pareja; un movil antiguo no lo manda
            f.optJSONObject("srvPlayer")?.let {
                srvA = it.optInt("A", srvA).coerceIn(0, 1)
                srvB = it.optInt("B", srvB).coerceIn(0, 1)
            }
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
        // Los sets que manda el movil, si cuadran con los que lleva cada uno.
        // Un movil antiguo no los manda: nos quedamos los nuestros si siguen
        // valiendo y si no, ninguno, antes que ensenar sets que no son.
        val detalle = parseSetsDetail(obj.optJSONArray("setsDetail"))
        setScores = when {
            detalle != null && detalle.size == setsA + setsB -> detalle
            setScores.size == setsA + setsB -> setScores
            else -> emptyList()
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
        // Los jugadores si pueden llegar vacios: el movil los ha borrado
        if (obj.has("playerA1")) playerA1 = obj.optString("playerA1", "")
        if (obj.has("playerA2")) playerA2 = obj.optString("playerA2", "")
        if (obj.has("playerB1")) playerB1 = obj.optString("playerB1", "")
        if (obj.has("playerB2")) playerB2 = obj.optString("playerB2", "")
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
                // Un partido guardado a medias no tiene ganador: va quien vaya
                // por delante en sets, y si no en juegos; empatados, ninguno.
                .put("winner", winner ?: when {
                    setsA != setsB -> if (setsA > setsB) "A" else "B"
                    gamesA != gamesB -> if (gamesA > gamesB) "A" else "B"
                    else -> ""
                })
                .put("finished", over)
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
        // Los genericos no se guardan en la agenda de nombres: no son de nadie.
        if (esNombreGenerico(name) || name == "PAREJA B" || name == "LOCAL" || name == "VISITA") return
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
        // La revision va con el partido: si volviera a 0 al reabrir la app,
        // el movil (con la suya mas alta) ignoraria los puntos del reloj
        obj.put("syncRev", syncRev)
        obj.put("srvA", srvA)
        obj.put("srvB", srvB)
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
        obj.put("setScores", setsDetailJson())
        obj.put("playerA1", playerA1).put("playerA2", playerA2)
        obj.put("playerB1", playerB1).put("playerB2", playerB2)
        return obj.toString()
    }

    private fun fromJSON(json: String) {
        try {
            val obj = JSONObject(json)
            syncRev = maxOf(syncRev, obj.optInt("syncRev", 0))
            srvA = obj.optInt("srvA", 0).coerceIn(0, 1)
            srvB = obj.optInt("srvB", 0).coerceIn(0, 1)
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
            nameA = obj.optString("nameA", NOMBRE_A_POR_DEFECTO)
            nameB = obj.optString("nameB", "PAREJA B")
            playerA1 = obj.optString("playerA1", ""); playerA2 = obj.optString("playerA2", "")
            playerB1 = obj.optString("playerB1", ""); playerB2 = obj.optString("playerB2", "")
            setScores = parseSetsDetail(obj.optJSONArray("setScores")).orEmpty()
                .takeIf { it.size == setsA + setsB }.orEmpty()
        } catch (e: Exception) {}
    }

    /**
     * Pone tu nombre en la pareja A, igual que hace el movil.
     *
     * Solo si todavia se llama como el generico: un nombre que hayas puesto
     * tu -en el reloj o llegado desde el movil- no se pisa nunca.
     *
     * @return true si ha cambiado algo, para saber si hay que guardar.
     */
    fun adoptarMiNombre(miNombre: String): Boolean {
        val yo = miNombre.trim()
        if (yo.isEmpty()) return false
        var cambio = false
        // Eres el primer jugador de la pareja A mientras no digas otra cosa
        if (playerA1.isBlank()) { playerA1 = yo; cambio = true }
        val nuevo = yo.uppercase()
        if (esNombreGenerico(nameA) && nameA != nuevo) { nameA = nuevo; cambio = true }
        return cambio
    }
}
