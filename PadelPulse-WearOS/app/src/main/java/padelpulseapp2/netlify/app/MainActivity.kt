package padelpulseapp2.netlify.app

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioManager
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
import padelpulseapp2.netlify.app.sync.CloudHistory
import padelpulseapp2.netlify.app.sync.PhoneLink
import padelpulseapp2.netlify.app.sync.SyncProtocol
import padelpulseapp2.netlify.app.sync.WatchAccount
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener, SensorEventListener {

    companion object {
        const val TAG = "PadelPulseWatch"
        const val APP_VERSION = "5.2.2"
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

    var sensorsPaused by mutableStateOf(false)

    // ── Volumen de la voz ────────────────────────────────────────────────
    //
    // La voz sale por el volumen multimedia del reloj, asi que el control
    // mueve ese volumen. El parametro de volumen del TTS no vale para esto:
    // solo puede bajar la voz respecto al volumen del sistema, nunca subirla,
    // y en una pista con ruido lo que hace falta es mas fuerte.

    private val audio: AudioManager by lazy { getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    /** Nivel de 0 a 5. Se lee del sistema, asi que sigue a los botones fisicos. */
    var voiceLevel by mutableIntStateOf(3)

    fun refreshVoiceLevel() {
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            voiceLevel = Math.round(audio.getStreamVolume(AudioManager.STREAM_MUSIC) * 5f / max)
        }
    }

    /** Pone el nivel y lo canta, para oir como queda sin esperar a un punto. */
    fun setVoiceLevel(level: Int) {
        val l = level.coerceIn(0, 5)
        runCatching {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(l * max / 5f), 0)
        }
        voiceLevel = l
        val engine = gameEngine ?: return
        if (l > 0 && engine.voiceEnabled) {
            val v = Translations.vd[engine.lang] ?: Translations.vd["es"]!!
            speak("${v.fifteen}, ${v.zero}.", engine.lang)
        }
    }

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
        get() = SyncProtocol.MODE_SYNC

    val isPaired: Boolean
        get() = PhoneLink.paired

    /**
     * [phonePaired] es lo que el movil cree: null si es un movil antiguo que
     * no lo dice. Asi los dos lados se ponen de acuerdo solos, sin pantallas.
     */
    fun onPhoneHello(appVersion: String, proto: Int, phonePaired: Boolean? = null) {
        phoneAppVersion = appVersion
        phoneProtocol = proto
        PhoneLink.setConnected(true)
        when {
            // El movil nos tiene vinculados y nosotros lo habiamos olvidado
            phonePaired == true && !PhoneLink.paired -> {
                PhoneLink.paired = true
                PhoneLink.save(this)
                sendSettingsToPhone()
                pushStateToPhone()
            }
            // El movil ya no nos tiene (reinstalado, o desvinculado alli)
            phonePaired == false && PhoneLink.paired -> {
                PhoneLink.paired = false
                PhoneLink.save(this)
                autoPairIfNeeded()
            }
        }
    }

    /**
     * Pide el vinculo sin codigo si hay movil y aun no estamos vinculados. El
     * movil lo acepta solo -tambien con su app cerrada- mientras tenga activado
     * "Vincular sin codigo", que viene activado de serie.
     */
    fun autoPairIfNeeded() {
        if (PhoneLink.paired || !PhoneLink.connected) return
        PhoneLink.countUnanswered()
        PhoneLink.send(
            this, SyncProtocol.PATH_PAIR,
            SyncProtocol.pairRequest(PhoneLink.nextSeq(), "AUTO", android.os.Build.MODEL ?: "Wear OS")
        )
    }

    fun onPairingConfirmed(code: String) {
        PhoneLink.paired = true
        PhoneLink.pairedCode = code
        PhoneLink.lastError = null
        PhoneLink.save(this)
        gameEngine?.pairingCode = code
        sendSettingsToPhone()
        pushStateToPhone()
    }

    fun onPairingRejected() {
        PhoneLink.paired = false
        PhoneLink.save(this)
        PhoneLink.lastError = "codigo"
    }

    /**
     * El movil nos ha mandado -o retirado- la sesion. Los estados de
     * [WatchAccount] son observables, asi que la pantalla se repinta sola;
     * aqui solo hace falta mover al usuario si se quedo sin cuenta estando
     * ya dentro, o sacarlo de la pantalla de cuenta cuando entra.
     */
    fun onAccountChanged() {
        val engine = gameEngine ?: return
        // La sesion trae tu nombre: la pareja A pasa a ser tuya.
        if (engine.adoptarMiNombre(WatchAccount.name)) engine.saveState()
        if (!WatchAccount.signedIn && !WatchAccount.skipped &&
            engine.currentScreen == "splash" && !engine.hasSavedMatch()) {
            engine.currentScreen = "account"
        }
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
            SyncProtocol.hello(PhoneLink.nextSeq(), APP_VERSION, SyncProtocol.MODE_SYNC, PhoneLink.paired)
        )
    }

    /** Difunde el estado al movil. Siempre que haya movil vinculado. */
    fun pushStateToPhone() {
        val engine = gameEngine ?: return
        if (PhoneLink.applyingRemote) return
        if (!PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_STATE,
            engine.buildState(PhoneLink.nextSeq(), matchTimeSeconds)
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

    /** Pide al movil que abra su hoja de compartir con la invitacion. */
    fun sendInviteToPhone(): Boolean {
        if (!PhoneLink.paired) return false
        PhoneLink.send(this, SyncProtocol.PATH_CMD, SyncProtocol.command(PhoneLink.nextSeq(), "invite", null))
        return true
    }

    fun sendSettingsToPhone() {
        val engine = gameEngine ?: return
        if (!PhoneLink.paired) return
        PhoneLink.send(
            this, SyncProtocol.PATH_SETTINGS,
            SyncProtocol.settings(
                PhoneLink.nextSeq(), engine.lang, engine.theme,
                ThemeUtils.getHexColor(engine.theme), engine.goldenPoint,
                engine.superTb, engine.bestOf, engine.nameA, engine.nameB, SyncProtocol.MODE_SYNC
            )
        )
    }

    /**
     * Punto unico tras cualquier accion local sobre el marcador: sube la
     * revision y manda el estado entero.
     *
     * Se manda el estado completo y no la accion suelta a proposito: si un
     * mensaje se pierde -y por Bluetooth se pierden-, el siguiente estado
     * vuelve a poner a los dos de acuerdo. Con acciones sueltas, un punto
     * perdido dejaria los marcadores descuadrados hasta el final del partido.
     */
    fun onLocalScoreAction(action: String, team: String? = null) {
        gameEngine?.bumpRev()
        pushStateToPhone()
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
                    // El engine necesita el cronometro para guardarlo al terminar
                    gameEngine?.clockSeconds = matchTimeSeconds
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

    fun pauseSensors() {
        if (sensorsPaused) return
        sensorsPaused = true
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        sensorManager.unregisterListener(this)
        isHrRegistered = false
        isStepRegistered = false
        Log.i(TAG, "Sensores pausados (actividad automática deshabilitada)")
    }

    fun resumeSensors() {
        if (!sensorsPaused) return
        sensorsPaused = false
        registerHeartRateSensor()
        registerStepSensor()
        Log.i(TAG, "Sensores reanudados")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val engine = gameEngine ?: return
        if (event == null) return
        if (sensorsPaused) return
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

        // La app arranca por la cuenta: o llega la sesion del movil, o el
        // usuario elige jugar sin ella. Solo se pregunta una vez.
        WatchAccount.load(this)
        CloudHistory.load(this)
        InviteLink.load(this)
        // La pareja A eres tu, igual que en el movil.
        if (engine.adoptarMiNombre(WatchAccount.name)) engine.saveState()
        if (!WatchAccount.signedIn && !WatchAccount.skipped) {
            engine.currentScreen = "account"
        }

        updateBrightness(engine.brightness)
        PhoneLink.load(this)
        engine.pairingCode = PhoneLink.pairedCode.ifEmpty { engine.pairingCode }
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
        refreshVoiceLevel()
        registerHeartRateSensor()
        registerStepSensor()
        PhoneLink.addListeners(this, messageListener, capabilityListener)
        PhoneLink.refreshConnection(this)
        sendHello()
        helloJob?.cancel()
        helloJob = lifecycleScope.launch {
            // Un primer intento enseguida -refreshConnection va en otro hilo y
            // tarda un poco en saber si hay movil- y despues uno cada 30 s
            // mientras siga sin vincular.
            delay(2_000)
            autoPairIfNeeded()
            while (true) {
                delay(30_000)
                PhoneLink.refreshConnection(this@MainActivity)
                sendHello()
                autoPairIfNeeded()
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
