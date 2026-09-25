package padelpulseapp2.netlify.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import org.json.JSONObject
import padelpulseapp2.netlify.app.sync.AuthLink
import padelpulseapp2.netlify.app.sync.PendingInbox
import padelpulseapp2.netlify.app.sync.SyncProtocol
import padelpulseapp2.netlify.app.sync.WearLink
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val TAG = "PadelPulse"
        const val APP_VERSION = "7.0.3"

        // Los mismos que usa la capa JS. La clave publicable esta pensada para
        // ir en el cliente; lo que protege los datos son las politicas RLS.
        const val SUPABASE_URL = "https://fdlcdzlvvxqhzougcjwd.supabase.co"
        const val SUPABASE_KEY = "sb_publishable_Q7D-EMj-MW4df-VzAbSNmg_eumYPvtM"
        var webView: WebView? = null
        var instance: MainActivity? = null
    }

    /** Sesion del enlace del correo llegada antes de que el WebView estuviera listo. */
    private var sesionPendiente: String? = null

    /**
     * Volumen de la voz de la app, de 0 a 1.
     *
     * En una pista hay viento, pelotazos y gente hablando: la voz por defecto
     * del sistema se pierde. Este valor se aplica a cada frase, y con
     * [subirVolumenDelMovil] se puede ademas poner el volumen multimedia del
     * telefono al maximo mientras dura el partido.
     */
    internal var volumenVoz: Float = 1.0f

    /** Escucha continua: el microfono se vuelve a abrir solo tras cada frase. */
    internal var escuchaContinua = false

    /** True mientras el TTS canta el punto: el microfono no debe estar abierto. */
    @Volatile private var hablando = false
    private var escuchando = false

    private var speechRecognizer: SpeechRecognizer? = null
    internal var tts: TextToSpeech? = null
    private var webReady = false

    internal val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startMic()
            else Toast.makeText(this, "Permiso de audio denegado", Toast.LENGTH_SHORT).show()
        }

    // El contenido lo procesa PhoneWearableListenerService; aqui solo presencia.
    private val messageListener = MessageClient.OnMessageReceivedListener {
        WearLink.setConnected(true)
    }

    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info ->
        val near = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
        WearLink.setConnected(near != null, near?.displayName ?: "")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        try {
            setContentView(R.layout.activity_main)
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "score_channel", "PadelPulse", NotificationManager.IMPORTANCE_LOW
                )
                getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
            }

            val wv = findViewById<WebView>(R.id.webView)
            webView = wv
            wv?.apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.databaseEnabled = true
                settings.allowFileAccess = true
                settings.allowContentAccess = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                // La app es 100% local (Tailwind, tipografias e iconos van en assets),
                // asi que no hace falta permitir contenido mixto ni trafico en claro.
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                addJavascriptInterface(AndroidBridge(this@MainActivity), "AndroidBridge")

                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        webReady = true
                        WearLink.refreshConnection(this@MainActivity)
                        pushConnectionToWeb(WearLink.connected, WearLink.watchName)
                        // Mensajes que llegaron con la app cerrada
                        val pending = PendingInbox.drain(this@MainActivity)
                        evalJs("if(window.PPSync) PPSync.drainNative(${JSONObject.quote(pending)});")
                        // Si el enlace del correo abrio la app desde cero, la
                        // sesion espera aqui a que la pagina este lista.
                        sesionPendiente?.let { json ->
                            sesionPendiente = null
                            evalJs("if(window.Auth) Auth.onEmailLink(${JSONObject.quote(json)});")
                        }
                    }
                }
                setBackgroundColor(android.graphics.Color.BLACK)
                loadUrl("file:///android_asset/code.html")
            }

            tts = TextToSpeech(this, this)
            if (SpeechRecognizer.isRecognitionAvailable(this)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            }

            WearLink.onConnectionChanged = { connected, name ->
                runOnUiThread { pushConnectionToWeb(connected, name) }
            }
            WearLink.announce(this)

            // El enlace del correo puede haber arrancado la app desde cero
            manejarEnlaceDelCorreo(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Error en onCreate", e)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        manejarEnlaceDelCorreo(intent)
    }

    /**
     * El usuario ha abierto el enlace de "he perdido la contraseña". Se lee la
     * sesion temporal que trae -en segundo plano, que hay una llamada de red-
     * y se le pasa a la capa JS, que pedira la contraseña nueva.
     */
    private fun manejarEnlaceDelCorreo(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "padelpulse") return
        Thread {
            val json = AuthLink.handleCallback(uri, SUPABASE_URL, SUPABASE_KEY)
            if (json == null) {
                Log.w(TAG, "El enlace del correo no traia nada aprovechable")
                return@Thread
            }
            runOnUiThread {
                if (webReady && webView != null) {
                    evalJs("if(window.Auth) Auth.onEmailLink(${JSONObject.quote(json)});")
                } else {
                    sesionPendiente = json
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        WearLink.addListeners(this, messageListener, capabilityListener)
        WearLink.refreshConnection(this)
        sendHello()
    }

    override fun onPause() {
        super.onPause()
        WearLink.removeListeners(this, messageListener, capabilityListener)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        speechRecognizer?.destroy()
        WearLink.onConnectionChanged = null
        webReady = false
        instance = null
        webView = null
        super.onDestroy()
    }

    // ── Puente hacia la capa JS ──────────────────────────────────────────

    internal fun evalJs(js: String) {
        runOnUiThread { runCatching { webView?.evaluateJavascript(js, null) } }
    }

    /**
     * Entrega un mensaje del reloj a PPSync. Devuelve false si el WebView no esta
     * listo, para que el servicio lo guarde en el buzon.
     */
    fun deliverToWeb(path: String, payload: String): Boolean {
        if (!webReady || webView == null) return false
        evalJs(
            "if(window.PPSync) PPSync.onNative(${JSONObject.quote(path)}, ${JSONObject.quote(payload)});"
        )
        return true
    }

    private fun pushConnectionToWeb(connected: Boolean, name: String) {
        evalJs(
            "if(window.PPSync) PPSync.onConnection($connected, ${JSONObject.quote(name)});"
        )
    }

    fun sendToWatch(path: String, payload: String) = WearLink.send(this, path, payload)

    private fun sendHello() {
        val payload = JSONObject()
            .put("v", SyncProtocol.VERSION)
            .put("src", SyncProtocol.SRC_PHONE)
            .put("seq", WearLink.nextSeq())
            .put("ts", System.currentTimeMillis())
            .put("app", APP_VERSION)
            .put("proto", SyncProtocol.VERSION)
            .toString()
        sendToWatch(SyncProtocol.PATH_HELLO, payload)
    }

    // ── Voz ──────────────────────────────────────────────────────────────

    /** Idioma con el que se escucha. Lo pone la capa JS al cambiarlo. */
    internal var idiomaVoz: String = "es-ES"

    internal fun startMic() {
        if (escuchando) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // Sin esto se escucha en el idioma del telefono, que no tiene por
            // que ser el de la app.
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, idiomaVoz)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // Un punto se canta en dos palabras: no hace falta esperar mas.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                escuchando = false
                // Todas las transcripciones, de mas a menos probable: la web se
                // queda con la primera que entienda del todo ("punto para Edu"
                // puede llegar como "punto para él").
                val lista = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                val txt = lista.firstOrNull()?.lowercase() ?: ""
                val todas = org.json.JSONArray(lista).toString()
                evalJs("if(typeof processVoiceAlternatives==='function') processVoiceAlternatives($todas);" +
                       " else if(typeof processVoiceCommand==='function') processVoiceCommand(${JSONObject.quote(txt)});")
                reabrirSiContinua()
            }
            override fun onPartialResults(p0: Bundle?) {
                val txt = p0?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull() ?: return
                // Se enseña lo que va oyendo: sin esto el usuario no sabe si el
                // microfono le esta cogiendo o esta hablando a la nada.
                evalJs("if(typeof onVoicePartial==='function') onVoicePartial(${JSONObject.quote(txt)});")
            }
            override fun onReadyForSpeech(p0: Bundle?) {
                escuchando = true
                evalJs("if(typeof onVoiceState==='function') onVoiceState('listening');")
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {
                evalJs("if(typeof onVoiceState==='function') onVoiceState('processing');")
            }
            override fun onError(code: Int) {
                escuchando = false
                Log.w(TAG, "Error de voz: $code")
                // Que no se oyera nada es lo normal entre punto y punto: se
                // vuelve a abrir sin molestar. Lo demas si se cuenta.
                val silencio = code == SpeechRecognizer.ERROR_NO_MATCH ||
                               code == SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                if (!silencio) {
                    evalJs("if(typeof onVoiceError==='function') onVoiceError($code);")
                }
                reabrirSiContinua()
            }
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        runCatching { speechRecognizer?.startListening(intent) }
            .onFailure { Log.w(TAG, "No se pudo abrir el microfono", it) }
    }

    /**
     * En modo continuo el microfono se vuelve a abrir solo, con un respiro
     * para que no se grabe a si misma la voz de la app cantando el punto.
     */
    private fun reabrirSiContinua() {
        if (!escuchaContinua) {
            evalJs("if(typeof onVoiceState==='function') onVoiceState('off');")
            return
        }
        // Mientras la app canta el punto no se abre el microfono: se oiria a si
        // misma y volveria a procesar el comando que acaba de ejecutar. Se
        // vuelve a mirar cada poco hasta que termine.
        if (hablando) {
            webView?.postDelayed({ reabrirSiContinua() }, 250)
            return
        }
        webView?.postDelayed({ if (escuchaContinua && !hablando) startMic() }, 700)
    }

    internal fun pararMic() {
        escuchaContinua = false
        escuchando = false
        runCatching { speechRecognizer?.cancel() }
        evalJs("if(typeof onVoiceState==='function') onVoiceState('off');")
    }

    // ── Voz de la app ────────────────────────────────────────────────────

    /**
     * Habla. El volumen va por parametro en cada frase porque el TTS no
     * guarda un volumen "de serie": si no se le dice nada, usa el del sistema
     * y en una pista eso no se oye.
     */
    /** Idioma que tiene puesto el TTS ahora mismo, para no recargarlo en cada punto. */
    private var idiomaTts = ""

    /**
     * Canta un texto en el idioma de la app.
     *
     * Antes el TTS se fijaba en español al arrancar y no se volvia a tocar, asi
     * que jugando en ingles o en italiano el movil cantaba los puntos con
     * fonetica española. El idioma llega ya resuelto desde el JS (es-ES, en-GB,
     * ...) y solo se cambia cuando cambia de verdad.
     *
     * Si el telefono no tiene voz para ese idioma se sigue con la que haya: mas
     * vale cantar el punto con acento raro que quedarse callado.
     */
    internal fun decir(texto: String, idioma: String = "") {
        val tag = idioma.ifBlank { "es-ES" }
        if (tag != idiomaTts) {
            val res = runCatching { tts?.setLanguage(Locale.forLanguageTag(tag)) }.getOrNull()
            if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "Sin voz instalada para $tag; se canta con la que haya")
            }
            idiomaTts = tag
        }
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, volumenVoz)
        }
        hablando = true
        tts?.speak(texto, TextToSpeech.QUEUE_FLUSH, params, "pp")
    }

    /**
     * Sube el volumen multimedia del telefono al maximo. Es lo unico que hace
     * que se oiga de verdad al otro lado de la pista: por muy alto que se pida
     * el TTS, nunca pasa del volumen del sistema.
     */
    internal fun subirVolumenDelMovil(): Int {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return -1
        return runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, max, 0)
            100
        }.getOrDefault(-1)
    }

    /** Volumen multimedia del telefono, en porcentaje. */
    internal fun volumenDelMovil(): Int {
        val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return -1
        return runCatching {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            if (max <= 0) -1
            else (am.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / max)
        }.getOrDefault(-1)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        // El idioma lo pone decir() en cada frase, segun el que tenga la app.
        /*
         * Saber cuando la app esta cantando el punto es lo que evita que el
         * microfono se oiga a si misma. Sin esto se reabria a los 700 ms, en
         * mitad del anuncio, y el reconocedor devolvia otra vez la misma frase:
         * el ultimo comando se contaba dos veces.
         */
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { hablando = true }
            override fun onDone(id: String?) { hablando = false }
            @Deprecated("La firma sin errorCode es la que llaman las versiones viejas")
            override fun onError(id: String?) { hablando = false }
            override fun onError(id: String?, errorCode: Int) { hablando = false }
        })
        // Por multimedia y no por notificaciones: es el canal que el usuario
        // sube con los botones del lateral, y el que no se silencia solo.
        runCatching {
            tts?.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    // ── Interfaz que ve el JavaScript ────────────────────────────────────

    class AndroidBridge(private val activity: MainActivity) {

        /** Envio generico: PPSync construye el JSON y lo manda por aqui. */
        @JavascriptInterface
        fun send(path: String, payload: String) {
            if (path.startsWith("/padel/")) activity.sendToWatch(path, payload)
        }

        @JavascriptInterface
        fun isWatchConnected(): Boolean = WearLink.connected

        @JavascriptInterface
        fun watchName(): String = WearLink.watchName

        @JavascriptInterface
        fun nextSeq(): String = WearLink.nextSeq().toString()

        @JavascriptInterface
        fun acceptSeq(seq: String): Boolean =
            WearLink.acceptSeq(seq.toLongOrNull() ?: 0L)

        @JavascriptInterface
        fun appVersion(): String = APP_VERSION

        @JavascriptInterface
        fun protocolVersion(): Int = SyncProtocol.VERSION

        @JavascriptInterface
        fun refreshConnection() = WearLink.refreshConnection(activity)

        @JavascriptInterface
        fun savePairing(code: String, paired: Boolean) {
            activity.getSharedPreferences("padel", 0).edit()
                .putString("pairingCode", code)
                .putBoolean("watchPaired", paired)
                .apply()
        }

        /**
         * La parte nativa tiene que saber si se acepta el vinculo sin codigo:
         * con la app cerrada no hay web que lo decida, y el reloj pide el
         * vinculo en cuanto ve el movil.
         */
        @JavascriptInterface
        fun setAutoPair(on: Boolean) {
            activity.getSharedPreferences("padel", 0).edit().putBoolean("autoPair", on).apply()
        }

        @JavascriptInterface
        fun loadPairing(): String = JSONObject()
            .put("code", activity.getSharedPreferences("padel", 0).getString("pairingCode", "") ?: "")
            .put("paired", activity.getSharedPreferences("padel", 0).getBoolean("watchPaired", false))
            .toString()

        @JavascriptInterface
        fun toast(msg: String) {
            activity.runOnUiThread { Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show() }
        }

        // ── Compartir ────────────────────────────────────────────────────
        // Dentro de un WebView no hay window.open ni navigator.share, asi que
        // los botones de compartir no hacian nada. Todo pasa por intents nativos.

        /** Hoja de compartir del sistema: WhatsApp, Telegram, correo, lo que haya. */
        @JavascriptInterface
        fun shareText(text: String, subject: String) {
            activity.runOnUiThread {
                runCatching {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, text)
                        if (subject.isNotEmpty()) putExtra(Intent.EXTRA_SUBJECT, subject)
                    }
                    activity.startActivity(Intent.createChooser(send, subject.ifEmpty { "PadelPulse" }))
                }.onFailure {
                    Toast.makeText(activity, "No se pudo compartir", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /**
         * Directo a WhatsApp. Si no esta instalado cae en la hoja de compartir
         * normal, para no dejar al usuario con un boton que no hace nada.
         */
        @JavascriptInterface
        fun shareToWhatsApp(text: String) {
            activity.runOnUiThread {
                val direct = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    setPackage("com.whatsapp")
                }
                val ok = runCatching { activity.startActivity(direct); true }.getOrDefault(false)
                if (!ok) {
                    // WhatsApp Business o sin WhatsApp: probamos y si no, hoja generica
                    val business = Intent(direct).setPackage("com.whatsapp.w4b")
                    val ok2 = runCatching { activity.startActivity(business); true }.getOrDefault(false)
                    if (!ok2) shareText(text, "PadelPulse")
                }
            }
        }

        @JavascriptInterface
        fun copyText(text: String) {
            activity.runOnUiThread {
                runCatching {
                    val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("PadelPulse", text))
                    Toast.makeText(activity, "Copiado", Toast.LENGTH_SHORT).show()
                }
            }
        }

        /** Abre una url fuera de la app (navegador), no dentro del WebView. */
        @JavascriptInterface
        fun openExternal(url: String) {
            activity.runOnUiThread {
                runCatching {
                    activity.startActivity(
                        Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }

        @JavascriptInterface
        fun vibrate(ms: Int) {
            runCatching {
                val v = activity.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v.vibrate(android.os.VibrationEffect.createOneShot(
                        ms.toLong().coerceIn(10, 500), android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION") v.vibrate(ms.toLong().coerceIn(10, 500))
                }
            }
        }

        /** Escucha una sola frase. */
        @JavascriptInterface
        fun startVoiceCommand() = pedirMicro(false)

        /** Modo arbitro: el microfono se queda abierto entre punto y punto. */
        @JavascriptInterface
        fun startVoiceContinuous() = pedirMicro(true)

        @JavascriptInterface
        fun stopVoice() {
            activity.runOnUiThread { activity.pararMic() }
        }

        private fun pedirMicro(continuo: Boolean) {
            activity.runOnUiThread {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                    activity.escuchaContinua = continuo
                    activity.startMic()
                } else {
                    activity.requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
        }

        /** Corta la frase que se este cantando: al silenciar la voz, calla ya. */
        @JavascriptInterface
        fun stopSpeaking() {
            activity.runOnUiThread { runCatching { activity.tts?.stop() } }
        }

        @JavascriptInterface
        fun speak(text: String) {
            activity.runOnUiThread { activity.decir(text) }
        }

        /** Canta en el idioma de la app: "es-ES", "en-GB", "it-IT"... */
        @JavascriptInterface
        fun speakIn(text: String, lang: String) {
            activity.runOnUiThread { activity.decir(text, lang) }
        }

        /** Volumen de la voz de la app, 0-100. */
        @JavascriptInterface
        fun setVoiceVolume(pct: Int) {
            activity.volumenVoz = (pct.coerceIn(0, 100)) / 100f
        }

        /**
         * Pone el volumen multimedia del telefono al maximo. Por muy alto que
         * se pida el TTS nunca pasa del volumen del sistema, asi que sin esto
         * no hay forma de que se oiga al otro lado de la pista.
         */
        @JavascriptInterface
        fun maxDeviceVolume(): Int = activity.subirVolumenDelMovil()

        /** Volumen multimedia actual del telefono, en porcentaje (-1 si no se sabe). */
        @JavascriptInterface
        fun deviceVolume(): Int = activity.volumenDelMovil()

        @JavascriptInterface
        fun setLanguage(langCode: String) {
            activity.runOnUiThread {
                runCatching {
                    val locale = if (langCode.contains("-")) {
                        val parts = langCode.split("-")
                        Locale(parts[0], parts[1])
                    } else Locale(langCode)
                    activity.tts?.setLanguage(locale)
                    activity.idiomaVoz = if (langCode.contains("-")) langCode
                                         else locale.language + "-" + locale.language.uppercase()
                }.onFailure { Log.e(TAG, "Idioma TTS no valido", it) }
            }
        }

        // ── Compatibilidad con la v2 (por si queda HTML viejo cacheado) ──

        @JavascriptInterface
        fun syncSnapshot(json: String) = activity.sendToWatch(SyncProtocol.PATH_STATE, json)

        @JavascriptInterface
        fun syncSettings(json: String) = activity.sendToWatch(SyncProtocol.PATH_SETTINGS, json)

        @JavascriptInterface
        fun setBTCode(code: String) {
            savePairing(code, false)
            activity.sendToWatch(
                SyncProtocol.PATH_PAIR,
                JSONObject().put("v", SyncProtocol.VERSION).put("action", "accept").put("code", code).toString()
            )
        }
    }
}
