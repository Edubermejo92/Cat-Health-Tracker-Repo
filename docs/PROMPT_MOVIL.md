# Prompt para Gemini — PadelPulse Live, app de MÓVIL

Pega esto entero en el chat de Gemini con el proyecto `PadelPulse-Movil/`
abierto. Lleva **todo el código fuente Kotlin y de configuración al día**, para
que trabajes sobre lo que hay de verdad y no sobre una copia vieja.

> **Aviso importante.** El último AAB que se generó de esta app salió de una
> copia de trabajo desactualizada: llevaba dentro el `code.html` del 7 de
> septiembre, con "entrar con Google" y los tres modos de sincronización, que
> ya estaban quitados. Antes de tocar nada, comprueba que el proyecto abierto
> coincide con el código de este prompt.

---

## El encargo

**PadelPulse Live** es un marcador de pádel formado por **dos apps Android que
tienen que ir a la par**. Ésta es la del móvil.

| | Móvil (ésta) | Reloj |
|---|---|---|
| Carpeta | `PadelPulse-Movil/` | `PadelPulse-WearOS/` |
| Módulo | `:mobile` | `:app` |
| Interfaz | WebView que carga `assets/code.html` | Jetpack Compose for Wear OS |
| minSdk | 25 | 30 |
| versionCode | 518 | 5180 |

Las dos comparten `applicationId` (`padelpulseapp2.netlify.app`): son **una
sola ficha de Google Play con dos formatos**. compileSdk 35, Gradle 9.5.0.

Son **dos proyectos Gradle independientes**: se abren por separado con
*File → Open* sobre la carpeta que contiene `settings.gradle.kts`.

---

## Reglas que no se pueden romper

Si incumples alguna, la app compila igual y falla en producción.

### 1. Las dos apps se publican juntas

Comparten `applicationId`, así que Play exige **el mismo keystore** y
**versionCode distinto**. Los dos AAB se suben a la **misma versión** de Play.
Si sólo subes uno, los usuarios se quedan con una app nueva y otra vieja y **la
sincronización deja de funcionar**.

### 2. El protocolo está escrito en tres sitios

Las dos apps hablan por el **protocolo v3** sobre el Wearable Data Layer (no es
Bluetooth crudo). El contrato está mirrorado en tres implementaciones que
tienen que ir a la par:

```
PadelPulse-Movil/mobile/src/main/assets/code.html        → objeto PPSync
PadelPulse-Movil/mobile/src/main/java/.../sync/SyncProtocol.kt
PadelPulse-WearOS/app/src/main/java/.../sync/SyncProtocol.kt
```

Si cambias una ruta o un campo en una sola, **no hay ningún error**: ni al
compilar ni al ejecutar. Simplemente el marcador deja de sincronizarse.

### 3. Tailwind va precompilado

`assets/code.html` usa clases de Tailwind y `assets/tailwind.css` está
**generado**. Si añades o cambias una clase, hay que regenerar:

```sh
./tools/build-web-assets.sh   # regenera tailwind.css
```

Si no lo haces, la clase nueva **no tiene estilos** y no avisa nadie.

### 4. El `pathPrefix` del manifiesto

`PhoneWearableListenerService` filtra por `android:pathPrefix="/padel"`. Tiene
que ser idéntico al del reloj o los mensajes **no llegan en segundo plano**.

### 5. Compila siempre en limpio

El fallo del último AAB fue exactamente éste: assets cacheados de una
compilación anterior. Usa `./gradlew clean bundleRelease`, no `bundleRelease` a
secas.

---

## Cómo funciona esta app

Todo el interfaz vive en un único `code.html` de ~6.400 líneas, cargado en un
WebView desde `file:///android_asset/`. `MainActivity.kt` es el puente: expone
`AndroidBridge` al JavaScript.

Cosas que **no** funcionan dentro de un WebView y por eso van por el puente:
`window.open`, `navigator.share`, `navigator.clipboard` sobre `file://`, y
`speechSynthesis` (irregular y sin control de volumen). Todo eso está resuelto
con intents nativos y `TextToSpeech`.

La app es **offline-first**: Tailwind, la tipografía Lexend y los iconos van
empaquetados (~195 KB). Sólo salen a la red la cuenta, el respaldo del
historial y compartir.

### Sincronización: sin modos, por revisión

**No hay modo maestro/esclavo.** Antes había que elegir quién mandaba y se
quitó: el usuario quiere puntuar desde donde tenga la mano libre.

Las dos apps puntúan y difunden **el estado entero** (no la acción suelta: por
Bluetooth se pierden mensajes, y así el siguiente estado vuelve a ponerlas de
acuerdo). Los choques se resuelven con un número de **revisión** que sube en
cada cambio local:

| Situación | Qué hace quien recibe |
|---|---|
| `rev` recibida mayor | La aplica |
| `rev` menor | Ignora el marcador, **pero aplica la salud** |
| `rev` iguales | Gana el móvil |

El empate se resuelve a favor del móvil no por preferencia, sino porque hace
falta una regla estable: si cada aparato eligiera distinto, los marcadores
quedarían diferentes para siempre.

**Anti-eco**: mientras se aplica un estado remoto hay un flag que impide
reemitir. Sin él las dos apps se mandan el mismo estado sin parar.

### La pareja A eres tú

Invariante del producto, no una convención suelta. De ella cuelgan los comandos
de voz (`nosotros` → A, `ellos` → B), a quién se le apuntan las estadísticas y
quién sale como jugador principal en el historial. Si la A fuese unas veces
tuya y otras del rival, nada de eso cuadraría.

Se aplica en `initTeamNames()`: la pareja A coge tu nombre en cuanto lo tienes
en el perfil. Un nombre que hayas escrito a mano **no se pisa nunca** —
`esNombreGenerico()` distingue un nombre propio del "Pareja A" por defecto, en
los 13 idiomas.

Ojo con el orden de arranque: `initTeamNames()` corre antes de que exista
`Contacts`, así que lee el nombre con guardas y se vuelve a llamar después de
`Contacts.load()`. Es el mismo tropiezo que ya costó una pantalla en blanco
con `Auth`.

### Cuentas (Supabase)

Proyecto `fdlcdzlvvxqhzougcjwd`. Cuatro tablas con RLS contra `auth.uid()`.
Sin `supabase-js`: llamadas REST a mano desde `code.html`.

- El **historial pide cuenta**; el marcador funciona sin ella.
- Los partidos se guardan localmente aunque no haya cuenta, y suben al
  registrarse.
- **No hay "entrar con Google"**: se quitó, exigía credenciales de Google Cloud
  y dar de alta a cada tester a mano. Si lo ves en algún sitio, es código viejo.
- La vuelta de los enlaces del correo entra por `padelpulse://auth`
  (`AuthLink.kt` + intent-filter en el manifiesto).

Las contraseñas **no se pueden ver**, ni el usuario ni nadie: Supabase guarda un
hash bcrypt. No añadas ninguna pantalla que las muestre.

---

## Qué mejorar en esta app

1. **`code.html` tiene 6.445 líneas en un solo fichero.** Es el mayor problema
   de mantenimiento del proyecto. Partirlo en módulos (estado, reglas de pádel,
   sincronización, cuentas, vistas) sin romper el offline-first: no puede haber
   peticiones de red para cargar la app.

2. **Reconocimiento de voz sólo rico en español.** `processVoiceLocal()` tiene
   expresiones regulares muy completas para español y parciales para el resto.
   La app soporta 13 idiomas.

3. **Estado global `S`.** Todo el estado del partido está en un objeto global
   mutable. Cualquier refactor tiene que mantener `saveState()` /
   `saveStateOnly()`, que distinguen si hay que difundir al reloj.

4. **No hay tests.** No existe `src/test` ni `src/androidTest`. La verificación
   se ha hecho con Playwright contra el `code.html`, fuera del proyecto.

5. **El protocolo mirrorado en tres sitios** (regla 2). Generarlo desde una
   única fuente evitaría la clase de fallo más peligrosa del proyecto.

---

## Cómo verificar lo que toques

Compilar no basta: casi todos los fallos de este proyecto compilan bien.

```sh
./gradlew clean installRelease
```

Para probar la sincronización de verdad hacen falta **móvil y reloj emparejados
con la misma cuenta de Google**. Con el emulador no vale.

Prueba mínima tras tocar la sincronización:

1. Puntúa en el móvil → el reloj lo refleja.
2. Puntúa en el reloj → el móvil lo refleja.
3. Cierra la app del reloj, puntúa en el móvil, ábrela → ver si se pone al día.
4. Puntúa en los dos casi a la vez → los dos tienen que acabar igual.

---

## Cómo quiero que trabajes

- **Pregunta antes de cambiar el protocolo o el esquema de Supabase.** Son los
  dos sitios donde un error no se ve hasta que falla en la pista.
- **Explica el porqué**, no sólo el qué: este proyecto tiene decisiones que
  parecen raras y tienen motivo (el estado entero en vez de la acción, el
  empate a favor del móvil, el reloj sin contraseñas).
- Comentarios y mensajes de commit **en español**, como el resto del proyecto.

---

# Código fuente actual

Todo lo que sigue es el contenido exacto de los ficheros del proyecto.

> **`assets/code.html` (401 KB, 6.445 líneas) y `assets/tailwind.css` no están
> aquí**: no caben en un prompt. `code.html` lo tienes abierto en el proyecto y
> `tailwind.css` es generado — nunca se edita a mano.


## `mobile/src/main/AndroidManifest.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <!-- Red: la app es 100% local (Tailwind, tipografias e iconos van en assets).
         INTERNET solo hace falta para compartir resultado y para la IA opcional. -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.VIBRATE" />

    <!-- Comandos de voz -->
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-feature android:name="android.hardware.microphone" android:required="false" />

    <!-- La app funciona igual en movil, tablet y Chromebook -->
    <uses-feature android:name="android.hardware.touchscreen" android:required="false" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.AppCompat.NoActionBar"
        android:usesCleartextTraffic="false"
        tools:targetApi="34">

        <!-- singleTask para que la vuelta del enlace del correo reentre en la
             misma pantalla en vez de abrir una segunda copia de la app. -->
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:launchMode="singleTask"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden|uiMode">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>

            <!-- Vuelta del enlace de "he perdido la contraseña". Supabase
                 redirige aqui con la sesion temporal y esta pantalla pide la
                 contraseña nueva. El esquema hay que darlo de alta en Supabase
                 (Authentication > URL Configuration > Additional Redirect
                 URLs) o el correo no vuelve a la app. -->
            <intent-filter android:autoVerify="false">
                <action android:name="android.intent.action.VIEW" />
                <category android:name="android.intent.category.DEFAULT" />
                <category android:name="android.intent.category.BROWSABLE" />
                <data android:scheme="padelpulse" android:host="auth" />
            </intent-filter>
        </activity>

        <!-- Recibe los mensajes del reloj tambien con la app en segundo plano o
             cerrada. Sin esto la sincronizacion solo funciona con la app abierta.
             El pathPrefix tiene que coincidir con el del reloj: /padel -->
        <service
            android:name=".sync.PhoneWearableListenerService"
            android:exported="true"
            android:permission="com.google.android.wearable.permission.BIND_LISTENER">
            <intent-filter>
                <action android:name="com.google.android.gms.wearable.MESSAGE_RECEIVED" />
                <action android:name="com.google.android.gms.wearable.CAPABILITY_CHANGED" />
                <data android:scheme="wear" android:host="*" android:pathPrefix="/padel" />
            </intent-filter>
        </service>

        <uses-library android:name="wear-sdk" tools:node="remove" />
    </application>
</manifest>
```

## `mobile/src/main/java/padelpulseapp2/netlify/app/MainActivity.kt`

```kotlin
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
        const val APP_VERSION = "5.1.8"

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
            // Un punto se canta en dos palabras: no hace falta esperar mas.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                escuchando = false
                val txt = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
                evalJs("if(typeof processVoiceCommand==='function') processVoiceCommand(${JSONObject.quote(txt)});")
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
```

## `mobile/src/main/java/padelpulseapp2/netlify/app/sync/SyncProtocol.kt`

```kotlin
package padelpulseapp2.netlify.app.sync

/**
 * Contrato de mensajes entre el movil y el reloj.
 * Espejo exacto de docs/PROTOCOLO_SINCRONIZACION.md, del SyncProtocol del reloj
 * y del objeto PPSync de assets/code.html. Los tres tienen que ir a la par.
 */
object SyncProtocol {

    const val VERSION = 3

    // El prefijo /padel es obligatorio: el intent-filter de nuestro
    // WearableListenerService filtra por pathPrefix="/padel".
    const val PATH_HELLO = "/padel/hello"
    const val PATH_PAIR = "/padel/pair"
    const val PATH_STATE = "/padel/state"
    const val PATH_CMD = "/padel/cmd"
    const val PATH_SETTINGS = "/padel/settings"
    const val PATH_HEALTH = "/padel/health"
    /** Sesion de la cuenta: el movil se la pasa al reloj, no sale a internet. */
    const val PATH_ACCOUNT = "/padel/account"

    // Rutas v2 que seguimos aceptando
    const val PATH_LEGACY_SYNC = "/padel/sync"
    const val PATH_LEGACY_POINT = "/padel/point"
    const val PATH_LEGACY_BT = "/padel/bt"

    val ALL_PATHS = listOf(
        PATH_HELLO, PATH_PAIR, PATH_STATE, PATH_CMD, PATH_SETTINGS, PATH_HEALTH, PATH_ACCOUNT,
        PATH_LEGACY_SYNC, PATH_LEGACY_POINT, PATH_LEGACY_BT
    )

    const val SRC_PHONE = "phone"

    /**
     * Ya no hay modos: las dos apps van siempre a la vez y puntua quien
     * quiera. Las constantes se quedan para entenderse con versiones
     * anteriores, que siguen mandando su modo en cada mensaje.
     */
    const val MODE_SYNC = "SYNC"

    /**
     * Quien gana cuando los dos tocan a la vez. Cada cambio local sube el
     * numero de revision; el que llegue con revision mas alta manda. Si
     * empatan -dos toques en el mismo instante- gana el movil, por decidir
     * algo estable: si cada uno eligiera distinto, los marcadores quedarian
     * diferentes para siempre.
     */
    const val FIELD_REV = "rev"

    const val MODE_SOLO = "SOLO"
    const val MODE_PHONE = "PHONE"
    const val MODE_WATCH = "WATCH"

    /** Normaliza cualquier variante historica de modo a SOLO / PHONE / WATCH. */
    fun normalizeMode(raw: String?): String = when (raw?.uppercase()?.trim()) {
        "PHONE", "MOVIL_MANDA", "MOBILE", "MOVIL" -> MODE_PHONE
        "WATCH", "RELOJ_MANDA", "RELOJ" -> MODE_WATCH
        else -> MODE_SOLO
    }
}
```

## `mobile/src/main/java/padelpulseapp2/netlify/app/sync/WearLink.kt`

```kotlin
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
```

## `mobile/src/main/java/padelpulseapp2/netlify/app/sync/PhoneWearableListenerService.kt`

```kotlin
package padelpulseapp2.netlify.app.sync

import android.util.Log
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
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
        }
    }

    companion object {
        private const val TAG = "PadelPulseSvc"
    }
}
```

## `mobile/src/main/java/padelpulseapp2/netlify/app/sync/PendingInbox.kt`

```kotlin
package padelpulseapp2.netlify.app.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Buzon para los mensajes que llegan del reloj con la app cerrada o con el WebView
 * todavia sin cargar. Se guarda el ultimo mensaje de cada ruta (no hace falta el
 * historial: el estado es completo) y la capa JS lo vacia al arrancar.
 */
object PendingInbox {

    private const val PREFS = "padel"
    private const val KEY = "pending_inbox"

    @Synchronized
    fun put(context: Context, path: String, payload: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val map = read(prefs.getString(KEY, null))
        map[path] = payload
        val out = JSONObject()
        map.forEach { (k, v) -> out.put(k, v) }
        prefs.edit().putString(KEY, out.toString()).apply()
    }

    /** Devuelve los mensajes pendientes como array JSON y vacia el buzon. */
    @Synchronized
    fun drain(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val map = read(prefs.getString(KEY, null))
        prefs.edit().remove(KEY).apply()
        val arr = JSONArray()
        // Orden estable: ajustes y emparejado antes que el estado del partido
        val order = listOf(
            SyncProtocol.PATH_HELLO, SyncProtocol.PATH_PAIR, SyncProtocol.PATH_LEGACY_BT,
            SyncProtocol.PATH_SETTINGS, SyncProtocol.PATH_HEALTH,
            SyncProtocol.PATH_CMD, SyncProtocol.PATH_LEGACY_POINT,
            SyncProtocol.PATH_STATE, SyncProtocol.PATH_LEGACY_SYNC
        )
        (order + map.keys.filterNot { it in order }).distinct().forEach { path ->
            map[path]?.let { arr.put(JSONObject().put("path", path).put("payload", it)) }
        }
        return arr.toString()
    }

    private fun read(raw: String?): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        if (raw.isNullOrEmpty()) return map
        runCatching {
            val obj = JSONObject(raw)
            obj.keys().forEach { k -> map[k] = obj.optString(k, "") }
        }
        return map
    }
}
```

## `mobile/src/main/java/padelpulseapp2/netlify/app/sync/AuthLink.kt`

```kotlin
package padelpulseapp2.netlify.app.sync

import android.content.Context
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Vuelta del enlace de "he perdido la contraseña".
 *
 * Supabase manda un correo con un enlace de un solo uso que termina abriendo
 * la app por su propio esquema, padelpulse://auth, con la sesion temporal
 * detras de la almohadilla. Esa sesion no sirve para entrar sin mas: solo
 * demuestra que quien abrio el correo es el dueño de la cuenta, y la app la
 * usa unicamente para dejarle escribir una contraseña nueva.
 */
object AuthLink {

    private const val TAG = "PadelPulseAuthLink"

    const val REDIRECT = "padelpulse://auth"

    /**
     * Lee el enlace de vuelta. Devuelve el JSON de la sesion, un JSON con
     * "error" si el enlace trae un fallo, o null si no hay nada aprovechable.
     */
    fun handleCallback(uri: Uri, supabaseUrl: String, apiKey: String): String? {
        val error = uri.getQueryParameter("error_description")
            ?: uri.getQueryParameter("error")
            ?: fragmento(uri)["error_description"]
        if (error != null) {
            Log.w(TAG, "El enlace trae error: $error")
            return JSONObject().put("error", error).toString()
        }

        val datos = fragmento(uri)
        val token = datos["access_token"] ?: return null

        val sesion = JSONObject()
            .put("access_token", token)
            .put("refresh_token", datos["refresh_token"] ?: "")
            .put("expires_in", (datos["expires_in"] ?: "3600").toIntOrNull() ?: 3600)
            .put("type", datos["type"] ?: "")
        usuario(supabaseUrl, apiKey, token)?.let { sesion.put("user", it) }
        return sesion.toString()
    }

    /** Los pares clave=valor que van detras de la almohadilla. */
    private fun fragmento(uri: Uri): Map<String, String> {
        val frag = uri.fragment ?: return emptyMap()
        return frag.split("&").mapNotNull {
            val p = it.split("=", limit = 2)
            if (p.size == 2) p[0] to Uri.decode(p[1]) else null
        }.toMap()
    }

    private fun usuario(supabaseUrl: String, apiKey: String, token: String): JSONObject? =
        runCatching {
            val c = URL("$supabaseUrl/auth/v1/user").openConnection() as HttpURLConnection
            c.requestMethod = "GET"
            c.connectTimeout = 15000
            c.readTimeout = 15000
            c.setRequestProperty("apikey", apiKey)
            c.setRequestProperty("Authorization", "Bearer $token")
            val ok = c.responseCode in 200..299
            val texto = (if (ok) c.inputStream else c.errorStream)
                ?.bufferedReader()?.use { it.readText() } ?: ""
            c.disconnect()
            if (ok) JSONObject(texto) else null
        }.getOrElse {
            Log.w(TAG, "No se pudo leer el usuario", it)
            null
        }
}
```

## `mobile/build.gradle.kts`

```kotlin
import java.util.Properties
import java.io.FileInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Firma leida de keystore.properties (fuera del control de versiones).
// DEBE ser el MISMO keystore que el del reloj: comparten applicationId
// y Google Play rechaza artefactos del mismo paquete firmados con claves distintas.
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) FileInputStream(keystorePropsFile).use { load(it) }
}
val hasKeystore = keystorePropsFile.exists()

android {
    namespace = "padelpulseapp2.netlify.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "padelpulseapp2.netlify.app"
        minSdk = 25
        targetSdk = 35
        // Serie propia del movil. Debe ser DISTINTO al del reloj, que va en la
        // misma serie x10: movil 518, reloj 5180.
        versionCode = 518
        versionName = "5.1.8"
    }

    signingConfigs {
        create("release") {
            if (hasKeystore) {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlin {
        jvmToolchain(11)
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.gms:play-services-wearable:19.0.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")
    implementation("androidx.webkit:webkit:1.12.1")
}

val copyLogos = tasks.register("copyLogos") {
    doLast {
        val sourceIcon = file("src/main/ic_launcher-playstore.png")
        val resDir = file("src/main/res")
        if (sourceIcon.exists()) {
            listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { d ->
                val dir = resDir.resolve("mipmap-$d")
                dir.mkdirs()
                sourceIcon.copyTo(dir.resolve("ic_launcher.png"), overwrite = true)
                sourceIcon.copyTo(dir.resolve("ic_launcher_round.png"), overwrite = true)
            }
            resDir.resolve("drawable").mkdirs()
            sourceIcon.copyTo(resDir.resolve("drawable/logo.png"), overwrite = true)
        }
    }
}
tasks.named("preBuild") { dependsOn(copyLogos) }
```

## `build.gradle.kts`

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}```

## `settings.gradle.kts`

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PadelPulse Movil"
include(":mobile")
```

## `gradle.properties`

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
android.dependency.useConstraints=true
android.dependency.excludeLibraryComponentsFromConstraints=true
android.defaults.buildfeatures.resvalues=true
android.sdk.defaultTargetSdkToCompileSdkIfUnset=false
android.enableAppCompileTimeRClass=false
android.usesSdkInManifest.disallowed=false
android.uniquePackageNames=false
android.r8.strictFullModeForKeepRules=false
android.r8.optimizedResourceShrinking=false
android.builtInKotlin=false
android.newDsl=false
```

## `mobile/proguard-rules.pro`

```text
# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.

# La clase puente se llama AndroidBridge (antes WebAppInterface). Sin esta regla
# correcta, activar minify romperia en silencio TODA la comunicacion JS <-> Kotlin.
-keep class padelpulseapp2.netlify.app.MainActivity$AndroidBridge { *; }
-keepclassmembers class padelpulseapp2.netlify.app.MainActivity$AndroidBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

-keep class * extends androidx.activity.ComponentActivity { *; }
-keepclassmembers class * extends androidx.activity.ComponentActivity {
    <init>(...);
}

-dontwarn android.webkit.**
```
