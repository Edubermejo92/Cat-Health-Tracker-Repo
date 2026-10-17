package padelpulseapp2.netlify.app.sync

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Wearable
import java.util.concurrent.atomic.AtomicLong

/**
 * Enlace del movil con el reloj: descubrimiento, estado de conexion, numeracion
 * anti-eco y envio con reintento. Todo lo que sale hacia el reloj pasa por aqui.
 */
object WearLink {

    private const val TAG = "PadelPulseLink"
    const val CAPABILITY_PHONE = "padelpulse_phone"
    const val CAPABILITY_WATCH = "padelpulse_watch"

    @Volatile var connected = false
        private set
    @Volatile var watchName = ""
        private set

    /**
     * PadelPulse esta instalado en el reloj (se ve su capacidad). null = aun no
     * se sabe. El enlace (connected) lo da el emparejamiento del sistema; esto
     * dice si al otro lado hay con quien hablar.
     */
    @Volatile var appOnWatch: Boolean? = null

    /** Se avisa a la capa JS cuando se sabe si la app esta en el reloj. */
    var onWatchApp: ((Boolean?) -> Unit)? = null

    fun setAppOnWatch(value: Boolean?) {
        if (value == appOnWatch) return
        appOnWatch = value
        onWatchApp?.invoke(value)
    }

    /** True mientras aplicamos un estado remoto: impide reemitirlo y crear un bucle. */
    @Volatile var applyingRemote = false

    private val seq = AtomicLong(System.currentTimeMillis() / 1000)
    private var lastSeenWatchSeq = -1L

    /** Se avisa a la capa JS cada vez que cambia la conexion. */
    var onConnectionChanged: ((Boolean, String) -> Unit)? = null

    fun nextSeq(): Long = seq.incrementAndGet()

    @Synchronized
    fun acceptSeq(s: Long): Boolean {
        if (s <= 0) return true // emisor antiguo sin seq
        if (s <= lastSeenWatchSeq) return false
        lastSeenWatchSeq = s
        return true
    }

    @Synchronized
    private fun resetSeqWindow() { lastSeenWatchSeq = -1L }

    fun setConnected(value: Boolean, name: String = "") {
        val changed = value != connected || (name.isNotEmpty() && name != watchName)
        connected = value
        if (name.isNotEmpty()) watchName = name
        if (!value) resetSeqWindow()
        if (changed) onConnectionChanged?.invoke(connected, watchName)
    }

    /** Publica la capacidad del movil para que el reloj pueda descubrirlo. */
    fun announce(context: Context) {
        Thread {
            runCatching {
                Tasks.await(Wearable.getCapabilityClient(context).addLocalCapability(CAPABILITY_PHONE))
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
                if (near != null) {
                    runCatching {
                        val cap = Tasks.await(
                            Wearable.getCapabilityClient(context)
                                .getCapability(CAPABILITY_WATCH, CapabilityClient.FILTER_REACHABLE)
                        )
                        setAppOnWatch(cap.nodes.isNotEmpty() || appOnWatch == true)
                    }
                }
            }.onFailure {
                setConnected(false)
                Log.w(TAG, "No hay relojes conectados", it)
            }
        }.start()
    }

    /**
     * Envia a todos los nodos. Un reintento a los 400 ms: si el reloj esta en
     * reposo el primer envio falla a menudo y el segundo entra.
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
                    ok = true
                }.onFailure { e ->
                    if (attempt == 1) {
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
            Wearable.getCapabilityClient(context).addListener(capabilityListener, CAPABILITY_WATCH)
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
