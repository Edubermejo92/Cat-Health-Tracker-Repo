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
    var mode by mutableStateOf(SyncProtocol.MODE_SOLO)
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
        if (over || isRemoteControlled()) return faultCount
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
        if (over || isRemoteControlled()) return
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
        if (over || isRemoteControlled()) return
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
            .put("syncMode", mode)
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


    /** True cuando manda el movil: el reloj es un mando, no puntua por su cuenta. */
    fun isRemoteControlled(): Boolean = mode == SyncProtocol.MODE_PHONE

    /** True cuando este reloj es la fuente de verdad del marcador. */
    fun isMaster(): Boolean = mode == SyncProtocol.MODE_WATCH

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
            .put("mode", SyncProtocol.normalizeMode(mode))
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
        if (obj.has("mode")) mode = SyncProtocol.normalizeMode(obj.optString("mode"))
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
        if (obj.has("mode")) mode = SyncProtocol.normalizeMode(obj.optString("mode"))
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
        obj.put("mode", mode)
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
            mode = SyncProtocol.normalizeMode(obj.optString("mode", SyncProtocol.MODE_SOLO))
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
