package padelpulseapp2.netlify.app.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import padelpulseapp2.netlify.app.MainActivity
import padelpulseapp2.netlify.app.R
import padelpulseapp2.netlify.app.Translations

/**
 * Arbitro por voz en el reloj: el microfono se queda abierto durante el
 * partido y lo que se canta ("punto para Edu", "quince treinta", "ventaja
 * rojos", "deshacer"...) se aplica al marcador, en el idioma de la app.
 *
 * Entiende lo mismo que el movil porque usa la misma gramatica y el mismo
 * interprete (VoiceParser). Solo escucha uno de los dos a la vez: al
 * encenderlo aqui se apaga el del movil, y al reves; si no, cada punto
 * cantado se sumaria dos veces.
 */
class VoiceReferee(private val activity: MainActivity) {

    enum class State { OFF, LISTENING, PROCESSING }

    var state by mutableStateOf(State.OFF)
        private set
    /** Lo ultimo que se ha oido, para ensenarlo un momento en el marcador. */
    var heard by mutableStateOf("")
        private set
    var heardAt by mutableLongStateOf(0L)
        private set

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var parser: VoiceParser? = null

    private var lastPhrase = ""
    private var lastPhraseAt = 0L
    private var lastSpoken = ""
    private var lastSpokenAt = 0L
    private var speakingUntil = 0L

    val on: Boolean get() = state != State.OFF

    fun parser(): VoiceParser? = parser ?: runCatching {
        VoiceParser(
            activity.resources.openRawResource(R.raw.voice_grammar).bufferedReader().use { it.readText() }
        )
    }.onFailure { Log.w(TAG, "No se pudo cargar la gramatica de voz", it) }
        .getOrNull()?.also { parser = it }

    fun toggle() = if (on) stop() else start()

    fun start() {
        if (activity.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(activity)) {
            // Relojes sin reconocimiento continuo: se escucha una frase con la
            // pantalla de dictado del sistema y se aplica igual.
            activity.listenOnce()
            return
        }
        state = State.LISTENING
        activity.claimVoice()
        listen()
    }

    fun stop() {
        state = State.OFF
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    /**
     * La app va a hablar: se corta el microfono para no oirse a si misma
     * cantar el punto -"quince, cero" se entenderia como un marcador nuevo- y
     * se vuelve a abrir cuando calla.
     */
    fun onAppSpeaks(text: String) {
        lastSpoken = VoiceParser.norm(text)
        lastSpokenAt = System.currentTimeMillis()
        // Unos 80 ms por letra, que es como canta el TTS a velocidad normal
        speakingUntil = lastSpokenAt + 500 + text.length * 80L
        if (state == State.OFF) return
        handler.removeCallbacksAndMessages(null)
        runCatching { recognizer?.cancel() }
        reopen(speakingUntil - System.currentTimeMillis() + 250)
    }

    /** El TTS ha terminado de verdad: se puede escuchar ya. */
    fun onAppDoneSpeaking() {
        speakingUntil = System.currentTimeMillis() + 250
        if (state != State.OFF) { handler.removeCallbacksAndMessages(null); reopen(300) }
    }

    private fun reopen(delay: Long) {
        handler.postDelayed({ listen() }, delay.coerceAtLeast(50))
    }

    private fun listen() {
        if (state == State.OFF) return
        val now = System.currentTimeMillis()
        if (now < speakingUntil) { reopen(speakingUntil - now + 250); return }
        val r = recognizer ?: SpeechRecognizer.createSpeechRecognizer(activity).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // El idioma de la app, no el del reloj
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLang())
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        state = State.LISTENING
        runCatching { r.startListening(intent) }.onFailure {
            Log.w(TAG, "No se pudo abrir el microfono", it)
            reopen(1500)
        }
    }

    fun voiceLang(): String {
        val lang = MainActivity.gameEngine?.lang ?: "es"
        return Translations.langs.find { it.id == lang }?.voiceLang ?: "es-ES"
    }

    private val listener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            if (state == State.OFF) return
            state = State.LISTENING
            handle(results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty())
            reopen(500)
        }
        override fun onPartialResults(partial: Bundle?) {
            partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }?.let { heard = it; heardAt = System.currentTimeMillis() }
        }
        override fun onEndOfSpeech() { if (state != State.OFF) state = State.PROCESSING }
        override fun onError(error: Int) {
            if (state == State.OFF) return
            state = State.LISTENING
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> stop()
                // Silencio entre punto y punto: lo normal, se vuelve a escuchar
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> reopen(200)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> { runCatching { recognizer?.cancel() }; reopen(800) }
                else -> reopen(1200)
            }
        }
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /** Aplica lo oido. Vale la primera transcripcion que se entienda del todo. */
    fun handle(list: List<String>) {
        val engine = MainActivity.gameEngine ?: return
        if (list.isEmpty()) return
        val now = System.currentTimeMillis()
        val first = VoiceParser.norm(list[0])
        if (first.isEmpty() || now < speakingUntil) return
        // La propia app cantando el punto
        if (lastSpoken.isNotEmpty() && now - lastSpokenAt < 5000 &&
            (first == lastSpoken || lastSpoken.contains(first))) return
        // La misma frase otra vez, demasiado pronto: eco, no un punto nuevo
        if (first == lastPhrase && now - lastPhraseAt < 1500) return
        lastPhrase = first; lastPhraseAt = now
        heard = list[0]; heardAt = now

        val names = mapOf(
            "A" to listOf(engine.nameA, engine.playerA1, engine.playerA2).filter { it.isNotBlank() },
            "B" to listOf(engine.nameB, engine.playerB1, engine.playerB2).filter { it.isNotBlank() }
        )
        val p = parser() ?: return
        fun complete(a: VoiceParser.Action) = !(a.type in listOf("point", "adv", "game", "set") && a.team == null)
        val action = list.asSequence().mapNotNull { p.parse(it, engine.lang, names) }.firstOrNull { complete(it) }
            ?: p.parse(list[0], engine.lang, names)
            ?: return
        activity.applyVoice(action)
    }

    companion object {
        private const val TAG = "PadelPulseVoice"
        const val MIC_REQUEST = 102
    }
}
