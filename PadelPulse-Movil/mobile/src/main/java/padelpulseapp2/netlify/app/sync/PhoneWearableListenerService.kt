package padelpulseapp2.netlify.app.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import org.json.JSONObject
import padelpulseapp2.netlify.app.MainActivity

/**
 * Recibe los mensajes del reloj tambien con la app en segundo plano o cerrada.
 * No interpreta el marcador: el estado vive en la capa JS, asi que este servicio
 * solo entrega el mensaje intacto a PPSync (o lo guarda si el WebView no esta listo).
 */
class PhoneWearableListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        val payload = String(event.data, Charsets.UTF_8)
        Log.d(TAG, "Del reloj: $path")

        WearLink.setConnected(true)

        val delivered = MainActivity.instance?.deliverToWeb(path, payload) ?: false
        if (!delivered) {
            // App cerrada o WebView aun sin cargar: se guarda y se entrega al abrir
            PendingInbox.put(applicationContext, path, payload)
            if (path == SyncProtocol.PATH_PAIR) answerPairInBackground(payload)
        }
    }

    /**
     * Con la app cerrada, la web no puede contestar al reloj cuando pide el
     * vinculo, y el reloj se quedaba esperando hasta que alguien abriera el
     * movil. Se contesta desde aqui con las mismas reglas que PPSync: el
     * codigo del movil, o sin codigo si "Vincular sin codigo" esta activado.
     *
     * La peticion queda ademas en el buzon: al abrir la app, la web la vuelve
     * a atender y se entera de que ya esta vinculada.
     */
    private fun answerPairInBackground(payload: String) {
        val obj = runCatching { JSONObject(payload) }.getOrNull() ?: return
        if (obj.optString("action", "request") != "request") return
        val prefs = getSharedPreferences("padel", 0)
        val myCode = prefs.getString("pairingCode", "") ?: ""
        val code = obj.optString("code", "")
        val ok = (code.isNotEmpty() && code == myCode) ||
            (prefs.getBoolean("autoPair", true) && (code == "AUTO" || code.isEmpty()))
        // Codigo que no cuadra: que conteste la web al abrirse, con su aviso
        if (!ok) return
        prefs.edit().putBoolean("watchPaired", true).apply()
        WearLink.send(
            applicationContext, SyncProtocol.PATH_PAIR,
            JSONObject()
                .put("v", SyncProtocol.VERSION)
                .put("seq", WearLink.nextSeq())
                .put("action", "accept")
                .put("code", myCode)
                .put("device", "movil")
                .toString()
        )
    }

    companion object {
        private const val TAG = "PadelPulseSvc"
    }
}
