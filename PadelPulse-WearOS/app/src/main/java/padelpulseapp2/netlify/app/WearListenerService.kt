package padelpulseapp2.netlify.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.SyncProtocol
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
        val engine = MainActivity.gameEngine ?: return
        val obj = runCatching { JSONObject(payload) }.getOrElse {
            Log.w(TAG, "Payload no es JSON en $path")
            return
        }

        // Si el movil nos habla, el movil esta ahi
        PhoneLink.setConnected(true)

        if (!PhoneLink.acceptSeq(obj.optLong("seq", 0L))) return

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
        // Solo obedecemos al movil cuando el movil es el maestro
        if (SyncProtocol.normalizeMode(engine.mode) != SyncProtocol.MODE_PHONE) return
        PhoneLink.applyingRemote = true
        try {
            // v2 anidaba el estado en {"snapshot": "..."}
            val state = if (obj.has("snapshot"))
                runCatching { JSONObject(obj.getString("snapshot")) }.getOrDefault(obj)
            else obj
            val clock = engine.applyState(state)
            if (clock >= 0) MainActivity.instance?.setMatchClock(clock)
            MainActivity.instance?.refreshOngoingActivity()
            if (engine.over && engine.currentScreen == "score") engine.currentScreen = "end"
        } catch (e: Exception) {
            Log.e(TAG, "Error aplicando estado", e)
        } finally {
            PhoneLink.applyingRemote = false
        }
    }

    /** Comando del movil cuando manda el reloj (el movil se usa como mando). */
    private fun onCommand(engine: GameEngine, obj: JSONObject) {
        if (SyncProtocol.normalizeMode(engine.mode) != SyncProtocol.MODE_WATCH) return
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
        MainActivity.instance?.syncModeFromEngine()
    }

    companion object {
        private const val TAG = "PadelPulseWatchSvc"
    }
}
