package padelpulseapp2.netlify.app.sync

/**
 * Contrato de mensajes entre el movil y el reloj.
 * Espejo exacto de docs/PROTOCOLO_SINCRONIZACION.md, del SyncProtocol del reloj
 * y del objeto PPSync de assets/code.html. Los tres tienen que ir a la par.
 */
object SyncProtocol {

    const val VERSION = 3

    // El prefijo /padel es obligatorio: el intent-filter de nuestro
    // WearableListenerService filtra por pathPrefix="/padel".
    const val PATH_HELLO = "/padel/hello"
    const val PATH_PAIR = "/padel/pair"
    const val PATH_STATE = "/padel/state"
    const val PATH_CMD = "/padel/cmd"
    const val PATH_SETTINGS = "/padel/settings"
    const val PATH_HEALTH = "/padel/health"
    /** Sesion de la cuenta: el movil se la pasa al reloj, no sale a internet. */
    const val PATH_ACCOUNT = "/padel/account"

    // Rutas v2 que seguimos aceptando
    const val PATH_LEGACY_SYNC = "/padel/sync"
    const val PATH_LEGACY_POINT = "/padel/point"
    const val PATH_LEGACY_BT = "/padel/bt"

    val ALL_PATHS = listOf(
        PATH_HELLO, PATH_PAIR, PATH_STATE, PATH_CMD, PATH_SETTINGS, PATH_HEALTH, PATH_ACCOUNT,
        PATH_LEGACY_SYNC, PATH_LEGACY_POINT, PATH_LEGACY_BT
    )

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
}
