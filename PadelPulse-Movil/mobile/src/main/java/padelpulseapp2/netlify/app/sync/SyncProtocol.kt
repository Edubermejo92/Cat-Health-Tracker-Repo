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
    /** Ultimos partidos de la cuenta, para el Historial del reloj. */
    const val PATH_HISTORY = "/padel/history"

    // Rutas v2 que seguimos aceptando
    const val PATH_LEGACY_SYNC = "/padel/sync"
    const val PATH_LEGACY_POINT = "/padel/point"
    const val PATH_LEGACY_BT = "/padel/bt"

    val ALL_PATHS = listOf(
        PATH_HELLO, PATH_PAIR, PATH_STATE, PATH_CMD, PATH_SETTINGS, PATH_HEALTH, PATH_ACCOUNT, PATH_HISTORY,
        PATH_LEGACY_SYNC, PATH_LEGACY_POINT, PATH_LEGACY_BT
    )

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
}
