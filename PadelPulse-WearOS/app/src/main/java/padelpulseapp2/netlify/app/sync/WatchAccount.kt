package padelpulseapp2.netlify.app.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject

/**
 * Cuenta del usuario en el reloj.
 *
 * A proposito NO se teclea la contraseña aqui: escribir un correo y una
 * contraseña en una pantalla de 45 mm es horrible, y Wear OS recomienda
 * delegar el inicio de sesion en el movil. La sesion llega desde el movil
 * por el Data Layer -canal cifrado entre dos dispositivos ya emparejados,
 * no sale a internet- y el reloj solo la guarda para mostrar de quien es.
 *
 * Si alguien no quiere cuenta, se juega igual: la cuenta solo sirve para
 * respaldar el historial.
 */
object WatchAccount {

    private const val PREFS = "padel_prefs"
    private const val KEY_EMAIL = "acc_email"
    private const val KEY_NAME = "acc_name"
    private const val KEY_TOKEN = "acc_token"
    private const val KEY_EXPIRES = "acc_expires"
    private const val KEY_SKIPPED = "acc_skipped"

    var email by mutableStateOf("")
        private set
    var name by mutableStateOf("")
        private set
    var signedIn by mutableStateOf(false)
        private set
    /** El usuario eligio jugar sin cuenta: no volvemos a preguntar. */
    var skipped by mutableStateOf(false)
        private set

    private var token: String = ""
    private var expiresAt: Long = 0L

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        email = p.getString(KEY_EMAIL, "") ?: ""
        name = p.getString(KEY_NAME, "") ?: ""
        token = p.getString(KEY_TOKEN, "") ?: ""
        expiresAt = p.getLong(KEY_EXPIRES, 0L)
        skipped = p.getBoolean(KEY_SKIPPED, false)
        signedIn = token.isNotEmpty() && email.isNotEmpty()
    }

    /** Sesion recibida del movil. */
    fun applyFromPhone(context: Context, obj: JSONObject) {
        when (obj.optString("action", "")) {
            "session" -> {
                email = obj.optString("email", "")
                name = obj.optString("name", "").ifEmpty { email.substringBefore("@") }
                token = obj.optString("token", "")
                expiresAt = obj.optLong("expires", 0L)
                signedIn = token.isNotEmpty() && email.isNotEmpty()
                if (signedIn) skipped = false
                persist(context)
            }
            "signout" -> signOut(context)
        }
    }

    fun signOut(context: Context) {
        email = ""; name = ""; token = ""; expiresAt = 0L; signedIn = false
        persist(context)
    }

    /** Jugar sin cuenta. */
    fun skip(context: Context) {
        skipped = true
        persist(context)
    }

    /** True cuando la sesion caduco y hay que volver a abrir el movil. */
    fun isExpired(): Boolean = signedIn && expiresAt > 0 && System.currentTimeMillis() > expiresAt

    private fun persist(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_NAME, name)
            .putString(KEY_TOKEN, token)
            .putLong(KEY_EXPIRES, expiresAt)
            .putBoolean(KEY_SKIPPED, skipped)
            .apply()
    }
}
