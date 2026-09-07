package padelpulseapp2.netlify.app

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.MessageClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.SyncProtocol
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener, SensorEventListener {

    companion object {
        const val TAG = "PadelPulseWatch"
        const val APP_VERSION = "5.0.0"
        var gameEngine: GameEngine? = null
        var instance: MainActivity? = null
    }

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    /** Version de protocolo del movil; si no cuadra con la nuestra, hay que avisar. */
    var phoneProtocol by mutableIntStateOf(0)
    var phoneAppVersion by mutableStateOf("")

    var matchTimeSeconds by mutableIntStateOf(0)
    var timerRunning by mutableStateOf(false)
    private var timerJob: Job? = null
    private var helloJob: Job? = null

    private var speechResultCallback: ((String) -> Unit)? = null

    // ── Enlace con el movil ─────────────────────────────────────────────

    // El contenido lo procesa WearListenerService; aqui solo refrescamos presencia.
    private val messageListener = MessageClient.OnMessageReceivedListener {
        PhoneLink.setConnected(true)
    }

    private val capabilityListener = CapabilityClient.OnCapabilityChangedListener { info ->
        val near = info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
        PhoneLink.setConnected(near != null, near?.displayName ?: "")
    }

    /** Modo actual normalizado (SOLO / PHONE / WATCH). Fuente unica: el engine. */
    val syncMode: String
        get() = SyncProtocol.normalizeMode(gameEngine?.mode)

    val isPaired: Boolean
        get() = PhoneLink.paired

    /** Hook para que el servicio normalice el modo tras aplicar ajustes remotos. */
    fun syncModeFromEngine() {
        gameEngine?.let { it.mode = SyncProtocol.normalizeMode(it.mode) }
    }

    fun onPhoneHello(appVersion: String, proto: Int) {
        phoneAppVersion = appVersion
        phoneProtocol = proto
        PhoneLink.setConnected(true)
    }

    fun onPairingConfirmed(code: String) {
        PhoneLink.paired = true
        PhoneLink.pairedCode = code
        PhoneLink.lastError = null
        gameEngine?.pairingCode = code
        sendSettingsToPhone()
        pushStateToPhone()
    }

    fun onPairingRejected() {
        PhoneLink.paired = false
        PhoneLink.lastError = "codigo"
    }

    fun sendPairRequest(code: String) {
        PhoneLink.pairedCode = code
        gameEngine?.pairingCode = code
        PhoneLink.send(
            this, SyncProtocol.PATH_PAIR,
            SyncProtocol.pairRequest(PhoneLink.nextSeq(), code, android.os.Build.MODEL ?: "Wear OS")
        )
    }

    fun sendHello() {
        val engine = gameEngine ?: return
        PhoneLink.send(
            this, SyncProtocol.PATH_HELLO,
            SyncProtocol.hello(PhoneLink.nextSeq(), APP_VERSION, engine.mode, PhoneLink.paired)
        )
    }

    /** Difunde el estado. Solo si mandamos nosotros: si no, callamos. */
    fun pushStateToPhone() {
        val engine = gameEngine ?: return
        if (PhoneLink.applyingRemote) return
        if (syncMode != SyncProtocol.MODE_WATCH || !PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_STATE,
            engine.buildState(PhoneLink.nextSeq(), matchTimeSeconds)
        )
    }

    /** Manda una accion al movil cuando el maestro es el movil. */
    fun sendCommandToPhone(action: String, team: String? = null) {
        if (syncMode != SyncProtocol.MODE_PHONE || !PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_CMD,
            SyncProtocol.command(PhoneLink.nextSeq(), action, team)
        )
    }

    /** La salud siempre va del reloj al movil, en cualquier modo. */
    fun pushHealthToPhone() {
        val engine = gameEngine ?: return
        if (!PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_HEALTH,
            SyncProtocol.health(
                PhoneLink.nextSeq(), engine.heartRate, engine.calories,
                engine.distanceKm, totalSteps
            )
        )
    }

    fun sendSettingsToPhone() {
        val engine = gameEngine ?: return
        if (!PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_SETTINGS,
            SyncProtocol.settings(
                PhoneLink.nextSeq(), engine.lang, engine.theme,
                ThemeUtils.getHexColor(engine.theme), engine.goldenPoint,
                engine.superTb, engine.bestOf, engine.nameA, engine.nameB, engine.mode
            )
        )
    }

    /**
     * Punto unico tras cualquier accion local sobre el marcador: si mandamos
     * nosotros difundimos el estado; si manda el movil, mandamos el comando.
     */
    fun onLocalScoreAction(action: String, team: String? = null) {
        when (syncMode) {
            SyncProtocol.MODE_WATCH -> pushStateToPhone()
            SyncProtocol.MODE_PHONE -> sendCommandToPhone(action, team)
        }
        refreshOngoingActivity()
    }

    fun setMatchClock(seconds: Int) {
        if (seconds >= 0) matchTimeSeconds = seconds
    }

    // ── Voz ──────────────────────────────────────────────────────────────

    private val speechLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.getOrNull(0)?.let { speechResultCallback?.invoke(it) }
        }
    }

    fun startSpeechToText(callback: (String) -> Unit) {
        speechResultCallback = callback
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        }
        try {
            speechLauncher.launch(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Dictado no disponible", e)
        }
    }

    // ── Cronometro ───────────────────────────────────────────────────────

    fun startTimer() {
        timerRunning = true
        // Publica la actividad en curso: partido en marcha visible desde la
        // esfera del reloj y recientes, con un toque para volver al marcador.
        MatchOngoingService.start(this)
        if (timerJob?.isActive == true) return
        timerJob = lifecycleScope.launch {
            while (true) {
                delay(1000)
                if (timerRunning) {
                    matchTimeSeconds++
                    // Empuja salud al movil entre puntos, sin esperar a que pase nada
                    if (matchTimeSeconds % 10 == 0) pushHealthToPhone()
                    if (matchTimeSeconds % 30 == 0) sendHello()
                }
            }
        }
    }

    fun stopTimer() { timerRunning = false }

    fun resetTimer() {
        matchTimeSeconds = 0
        timerRunning = false
        MatchOngoingService.stop(this)
    }

    /**
     * Refresca el marcador que se ve en la actividad en curso. El cronometro
     * corre solo, asi que esto solo hace falta cuando cambia el tanteo.
     */
    fun refreshOngoingActivity() {
        val engine = gameEngine
        if (engine != null && engine.over) {
            // Partido acabado: ya no hay nada "en curso" que mostrar
            MatchOngoingService.stop(this)
            return
        }
        if (timerRunning) MatchOngoingService.start(this)
    }

    fun getTimerDisplay(): String {
        val h = matchTimeSeconds / 3600
        val m = (matchTimeSeconds % 3600) / 60
        val s = matchTimeSeconds % 60
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    // ── Sensores ─────────────────────────────────────────────────────────

    private var isHrRegistered = false
    private var isStepRegistered = false
    private var initialStepCount = -1f
    var totalSteps by mutableIntStateOf(0)
        private set

    fun registerHeartRateSensor() {
        if (isHrRegistered) return
        if (checkSelfPermission(android.Manifest.permission.BODY_SENSORS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            isHrRegistered = true
        }
    }

    fun registerStepSensor() {
        if (isStepRegistered) return
        // Android 10+: el podometro no entrega eventos sin ACTIVITY_RECOGNITION
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) return
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
            isStepRegistered = true
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val engine = gameEngine ?: return
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_HEART_RATE -> {
                val hr = event.values.getOrNull(0)?.toInt() ?: 0
                if (hr > 0) engine.heartRate = hr
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val steps = event.values.getOrNull(0) ?: 0f
                if (initialStepCount < 0) initialStepCount = steps
                val activeSteps = steps - initialStepCount
                totalSteps = activeSteps.toInt()
                engine.calories = (activeSteps * 0.045f).toInt()
                engine.distanceKm = activeSteps * 0.00075
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != 101) return
        permissions.forEachIndexed { i, perm ->
            if (grantResults.getOrNull(i) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                when (perm) {
                    android.Manifest.permission.BODY_SENSORS -> registerHeartRateSensor()
                    android.Manifest.permission.ACTIVITY_RECOGNITION -> registerStepSensor()
                }
            }
        }
    }

    // ── Ciclo de vida ────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instance = this
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        tts = TextToSpeech(this, this)

        val engine = GameEngine(this)
        gameEngine = engine
        engine.onSpeak = { text -> speak(text, engine.lang) }

        updateBrightness(engine.brightness)
        PhoneLink.announce(this)

        val neededPerms = mutableListOf<String>()
        if (checkSelfPermission(android.Manifest.permission.BODY_SENSORS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            neededPerms.add(android.Manifest.permission.BODY_SENSORS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q &&
            checkSelfPermission(android.Manifest.permission.ACTIVITY_RECOGNITION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            neededPerms.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        // Sin este permiso (Android 13+) no se puede publicar la actividad en
        // curso, que es requisito de Play para las apps de Wear OS.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            neededPerms.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (neededPerms.isNotEmpty()) requestPermissions(neededPerms.toTypedArray(), 101)

        registerHeartRateSensor()
        registerStepSensor()

        setContent { PadelApp(engine = engine, activity = this) }
    }

    override fun onResume() {
        super.onResume()
        registerHeartRateSensor()
        registerStepSensor()
        PhoneLink.addListeners(this, messageListener, capabilityListener)
        PhoneLink.refreshConnection(this)
        sendHello()
        helloJob?.cancel()
        helloJob = lifecycleScope.launch {
            while (true) {
                delay(30_000)
                PhoneLink.refreshConnection(this@MainActivity)
                sendHello()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        PhoneLink.removeListeners(this, messageListener, capabilityListener)
        helloJob?.cancel()
    }

    fun updateBrightness(value: Float) {
        val lp = window.attributes
        lp.screenBrightness = value / 255f
        window.attributes = lp
    }

    fun speak(text: String, currentLang: String = "es") {
        if (!ttsReady) return
        val voiceLang = Translations.langs.find { it.id == currentLang }?.voiceLang ?: "es-ES"
        tts.language = Locale.forLanguageTag(voiceLang)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "padel_voice")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true
            tts.language = Locale("es", "ES")
        } else {
            Log.e(TAG, "TTS no disponible")
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        timerJob?.cancel()
        helloJob?.cancel()
        (getSystemService(Context.SENSOR_SERVICE) as SensorManager).unregisterListener(this)
        gameEngine = null
        instance = null
        super.onDestroy()
    }
}
