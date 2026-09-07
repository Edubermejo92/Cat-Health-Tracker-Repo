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
