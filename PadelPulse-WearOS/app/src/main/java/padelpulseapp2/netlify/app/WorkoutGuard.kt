package padelpulseapp2.netlify.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.ExerciseConfig
import androidx.health.services.client.data.ExerciseType
import com.google.common.util.concurrent.ListenableFuture

/**
 * Evita que salte la pantalla de "deteccion automatica de ejercicio" del reloj
 * en mitad de un partido.
 *
 * Esa pantalla es de la app de salud del reloj (Samsung Health en los Galaxy
 * Watch): al notar movimiento durante unos minutos cree que empiezas un
 * entreno, lo arranca y se pone encima del marcador. Ninguna app puede quitar
 * la ventana de otra, pero si puede decirle al sistema que ya hay un entreno
 * en marcha: mientras dura el partido registramos uno propio en Health
 * Services -solo puede haber uno activo a la vez en el reloj- y la deteccion
 * no tiene nada que empezar.
 *
 * Solo se usa como "ocupado": el pulso y los pasos siguen saliendo de los
 * sensores como hasta ahora, asi que si el reloj no tiene Health Services
 * el marcador funciona igual.
 */
object WorkoutGuard {

    private const val TAG = "PadelPulseGuard"
    private const val PREFS = "padel_prefs"
    private const val KEY_ENABLED = "guard_enabled"

    /** Paquete de Samsung Health en los Galaxy Watch con Wear OS. */
    const val SAMSUNG_HEALTH = "com.samsung.android.wear.shealth"

    // ExerciseTrackedStatus. Van como numeros para no depender del nombre
    // exacto de las constantes, que ha cambiado entre versiones beta.
    private const val OTHER_APP_IN_PROGRESS = 1
    private const val OWNED_EXERCISE_IN_PROGRESS = 2

    enum class State { OFF, STARTING, ACTIVE, UNSUPPORTED, FAILED }

    /** El usuario quiere bloquear la deteccion mientras juega. Por defecto, si. */
    var enabled by mutableStateOf(true)
        private set
    var state by mutableStateOf(State.OFF)
        private set

    val active: Boolean get() = state == State.ACTIVE

    fun load(context: Context) {
        enabled = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, on: Boolean, matchRunning: Boolean) {
        enabled = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ENABLED, on).apply()
        if (on && matchRunning) start(context) else if (!on) stop(context)
    }

    /** Arranca el entreno propio. Se llama al empezar o reanudar el partido. */
    fun start(context: Context) {
        if (!enabled || state == State.ACTIVE || state == State.STARTING) return
        val app = context.applicationContext
        val client = runCatching { HealthServices.getClient(app).exerciseClient }.getOrElse {
            Log.w(TAG, "Health Services no disponible", it)
            state = State.UNSUPPORTED
            return
        }
        state = State.STARTING
        // Todo lo sincrono tambien va protegido: llamar a Health Services antes
        // de que el sistema termine de conectar el servicio puede lanzar en el
        // sitio, no solo fallar en el futuro. Esto se llama al pulsar Empezar/
        // Continuar/Nueva partida, asi que un fallo aqui sin red de seguridad
        // tumbaba la app en la accion mas repetida de todas.
        runCatching {
            client.getCapabilitiesAsync().onDone(app, { state = State.UNSUPPORTED }) { caps ->
                // No hay "padel" en Health Services: tenis es lo mas parecido, y
                // si el reloj no lo tiene vale cualquier deporte de raqueta.
                val type = listOf(
                    ExerciseType.TENNIS, ExerciseType.SQUASH, ExerciseType.RACQUETBALL,
                    ExerciseType.BADMINTON, ExerciseType.WORKOUT
                ).firstOrNull { it in caps.supportedExerciseTypes }
                if (type == null) {
                    state = State.UNSUPPORTED
                    return@onDone
                }
                // Pulso solo si hay permiso y el reloj lo da para ese deporte. Sin
                // tipos de datos tambien vale: lo que importa es ocupar el hueco.
                val hr = DataType.HEART_RATE_BPM
                val dataTypes: Set<DataType<*, *>> = if (hasHeartRatePermission(app) &&
                    hr in caps.getExerciseTypeCapabilities(type).supportedDataTypes
                ) setOf(hr) else emptySet()
                val config = ExerciseConfig(type, dataTypes, false, false)
                client.startExerciseAsync(config).onDone(app, { state = State.FAILED }) {
                    state = State.ACTIVE
                    Log.i(TAG, "Entreno propio en marcha ($type): sin deteccion automatica")
                }
            }
        }.onFailure {
            Log.w(TAG, "Health Services al arrancar", it)
            state = State.FAILED
        }
    }

    /** Termina el entreno propio: partido acabado, reiniciado o app cerrada. */
    fun stop(context: Context) {
        if (state == State.OFF || state == State.UNSUPPORTED) {
            state = State.OFF
            return
        }
        state = State.OFF
        val app = context.applicationContext
        runCatching {
            HealthServices.getClient(app).exerciseClient.endExerciseAsync().onDone(app, {}) {}
        }
    }

    /**
     * Si la app murio con el entreno en marcha, Health Services lo sigue
     * teniendo a nuestro nombre. Al arrancar sin partido corriendo se cierra,
     * para no dejar al reloj creyendo que seguimos jugando.
     */
    fun cleanUpIfOrphan(context: Context, matchRunning: Boolean) {
        // Con el proceso vivo el estado lo sabemos: solo se mira al arrancar de cero
        if (matchRunning || state != State.OFF) return
        val app = context.applicationContext
        runCatching {
            val client = HealthServices.getClient(app).exerciseClient
            client.getCurrentExerciseInfoAsync().onDone(app, {}) { info ->
                if (info.exerciseTrackedStatus == OWNED_EXERCISE_IN_PROGRESS) {
                    client.endExerciseAsync().onDone(app, {}) {}
                }
            }
        }
    }

    /** Otra app (la de salud del reloj, normalmente) tiene un entreno activo. */
    fun checkOtherApp(context: Context, onResult: (Boolean) -> Unit) {
        val app = context.applicationContext
        runCatching {
            HealthServices.getClient(app).exerciseClient.getCurrentExerciseInfoAsync()
                .onDone(app, { onResult(false) }) { onResult(it.exerciseTrackedStatus == OTHER_APP_IN_PROGRESS) }
        }.onFailure { onResult(false) }
    }

    fun hasHeartRatePermission(context: Context): Boolean {
        val granted = { p: String -> context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED }
        return granted(android.Manifest.permission.BODY_SENSORS) ||
            (Build.VERSION.SDK_INT >= 36 && granted("android.permission.health.READ_HEART_RATE"))
    }

    /** Abre Samsung Health, donde se apaga la deteccion automatica del todo. */
    fun samsungHealthIntent(context: Context): Intent? =
        context.packageManager.getLaunchIntentForPackage(SAMSUNG_HEALTH)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Espera al futuro sin corrutinas ni librerias extra: el aviso llega al
     * hilo principal.
     *
     * onOk tambien va protegido: Result.onSuccess llama al bloque tal cual, sin
     * envolverlo, asi que un fallo del propio Health Services a mitad de
     * partido -el reloj se desconecta del sistema, la app de salud le quita el
     * entreno por su cuenta, etc.- se colaba fuera de cualquier runCatching de
     * quien llama (esos solo cubren la parte sincrona, no lo que pasa despues,
     * cuando responde el sistema) y tumbaba la app entera.
     */
    private fun <T> ListenableFuture<T>.onDone(
        context: Context,
        onError: (Throwable) -> Unit,
        onOk: (T) -> Unit
    ) {
        addListener({
            runCatching { get() }
                .onSuccess { value ->
                    runCatching { onOk(value) }.onFailure {
                        Log.w(TAG, "Health Services (onOk)", it)
                        onError(it)
                    }
                }
                .onFailure {
                    Log.w(TAG, "Health Services", it)
                    onError(it)
                }
        }, ContextCompat.getMainExecutor(context))
    }
}
