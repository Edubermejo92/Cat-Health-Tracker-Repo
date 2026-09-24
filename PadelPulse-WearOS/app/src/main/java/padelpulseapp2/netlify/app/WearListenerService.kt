package padelpulseapp2.netlify.app

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import padelpulseapp2.netlify.app.sync.CloudHistory
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
        PhoneLink.heardFromPhone()

        if (!PhoneLink.acceptSeq(obj.optLong("seq", 0L))) return

        // La cuenta se guarda aunque la app no este abierta: el servicio
        // arranca solo y la sesion tiene que estar lista para cuando el
        // usuario levante la muñeca.
        if (path == SyncProtocol.PATH_ACCOUNT) {
            WatchAccount.applyFromPhone(applicationContext, obj)
            MainActivity.instance?.onAccountChanged()
            return
        }
        // El historial igual: se guarda aunque la app este cerrada
        if (path == SyncProtocol.PATH_HISTORY) {
            CloudHistory.applyFromPhone(applicationContext, obj)
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
        MainActivity.instance?.onPhoneHello(
            obj.optString("app", ""), obj.optInt("proto", 0),
            if (obj.has("paired")) obj.optBoolean("paired", false) else null
        )
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
        SyncProtocol.optNullableString(obj, "inviteUrl")?.let { InviteLink.applyFromPhone(applicationContext, it) }
    }

    companion object {
        private const val TAG = "PadelPulseWatchSvc"
    }
}
