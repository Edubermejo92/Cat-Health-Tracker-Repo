package padelpulseapp2.netlify.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
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
import padelpulseapp2.netlify.app.sync.PendingInbox
import padelpulseapp2.netlify.app.sync.SyncProtocol
import padelpulseapp2.netlify.app.sync.WearLink
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    companion object {
        const val TAG = "PadelPulse"
        const val APP_VERSION = "5.0.5"
        var webView: WebView? = null
        var instance: MainActivity? = null
    }

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
        } catch (e: Exception) {
            Log.e(TAG, "Error en onCreate", e)
        }
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

    private fun evalJs(js: String) {
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

    internal fun startMic() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val txt = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
                evalJs("if(typeof processVoiceCommand==='function') processVoiceCommand(${JSONObject.quote(txt)});")
            }
            override fun onReadyForSpeech(p0: Bundle?) {
                Toast.makeText(this@MainActivity, "Escuchando...", Toast.LENGTH_SHORT).show()
            }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(p0: Float) {}
            override fun onBufferReceived(p0: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(p0: Int) { Log.e(TAG, "Error de voz: $p0") }
            override fun onPartialResults(p0: Bundle?) {}
            override fun onEvent(p0: Int, p1: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) tts?.setLanguage(Locale("es", "ES"))
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

        @JavascriptInterface
        fun startVoiceCommand() {
            activity.runOnUiThread {
                if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) activity.startMic()
                else activity.requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        @JavascriptInterface
        fun speak(text: String) {
            activity.runOnUiThread {
                activity.tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "bridge")
            }
        }

        @JavascriptInterface
        fun setLanguage(langCode: String) {
            activity.runOnUiThread {
                runCatching {
                    val locale = if (langCode.contains("-")) {
                        val parts = langCode.split("-")
                        Locale(parts[0], parts[1])
                    } else Locale(langCode)
                    activity.tts?.setLanguage(locale)
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
