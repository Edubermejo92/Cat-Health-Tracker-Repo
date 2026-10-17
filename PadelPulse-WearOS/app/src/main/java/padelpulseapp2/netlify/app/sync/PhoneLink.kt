package padelpulseapp2.netlify.app.sync

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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

    /**
     * Hay enlace con el movil (Bluetooth o Wi-Fi). Ese enlace no lo crea
     * PadelPulse: es el emparejamiento del sistema, el de la app Galaxy
     * Wearable / Wear OS del movil. Aqui solo se mira si existe.
     */
    var connected by mutableStateOf(false)
        private set

    /**
     * PadelPulse esta instalado en el movil (se ve su capacidad). null = aun
     * no se sabe. Si el movil ya nos ha hablado, es que esta.
     */
    var appOnPhone by mutableStateOf<Boolean?>(null)

    /** Cuando llego el ultimo mensaje del movil (0 = nunca en esta sesion). */
    var lastHeardAt by mutableLongStateOf(0L)
        private set

    /** Lo que se ensena: de lo mas basico (enlace) a lo completo (sincronizado). */
    enum class Link { NO_PHONE, NO_APP, WAITING, SYNCED }

    val link: Link
        get() = when {
            paired && connected && lastHeardAt > 0 -> Link.SYNCED
            !connected -> Link.NO_PHONE
            appOnPhone == false -> Link.NO_APP
            else -> Link.WAITING
        }

    /** El movil ha aceptado el codigo: la sincronizacion esta activa. */
    var paired by mutableStateOf(false)

    var pairedCode by mutableStateOf("")
    var phoneName by mutableStateOf("")

    /**
     * Peticiones de vinculo seguidas sin oir nada del movil. Si crece, no es
     * que falte pulsar algo: el movil no esta recibiendo (app sin instalar, o
     * las dos apps firmadas distinto y el sistema tira los mensajes).
     */
    var unanswered by mutableIntStateOf(0)
        private set

    private const val PREFS = "padel_link"

    /**
     * El vinculo se guarda en disco. Wear OS cierra las apps en segundo plano a
     * menudo, y sin esto cada arranque empezaba sin vincular: el reloj dejaba
     * de mandar el marcador hasta que alguien volvia a vincular a mano.
     */
    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        paired = p.getBoolean("paired", false)
        pairedCode = p.getString("code", "") ?: ""
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean("paired", paired)
            .putString("code", pairedCode)
            .apply()
    }

    fun heardFromPhone() {
        unanswered = 0
        lastHeardAt = System.currentTimeMillis()
        appOnPhone = true
    }

    fun countUnanswered() { unanswered += 1 }

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
                // Con enlace, se mira si PadelPulse esta en ese movil. Sin
                // esto, "no conecta" no distinguia entre Bluetooth y app.
                if (near != null) {
                    runCatching {
                        val cap = Tasks.await(
                            Wearable.getCapabilityClient(context)
                                .getCapability(CAPABILITY_PHONE, CapabilityClient.FILTER_REACHABLE)
                        )
                        appOnPhone = cap.nodes.isNotEmpty() || lastHeardAt > 0
                    }
                }
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
