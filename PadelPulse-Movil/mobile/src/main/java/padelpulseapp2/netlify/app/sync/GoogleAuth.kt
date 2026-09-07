package padelpulseapp2.netlify.app.sync

import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Inicio de sesion con Google.
 *
 * Google no permite iniciar sesion dentro de un WebView -devuelve
 * "disallowed_useragent"-, asi que la pantalla de Google se abre en el
 * navegador del sistema y vuelve a la app por un enlace propio,
 * padelpulse://auth.
 *
 * Se usa PKCE: aqui se genera un secreto (el verificador), se manda solo su
 * huella, y al volver se canjea el codigo presentando el secreto. Asi, aunque
 * otra app interceptara el enlace de vuelta, sin el verificador el codigo no
 * le sirve de nada. La huella se calcula con MessageDigest en vez de con
 * crypto.subtle porque en un WebView servido desde file:// esa API no siempre
 * esta disponible.
 */
object GoogleAuth {

    private const val TAG = "PadelPulseGoogle"
    private const val PREFS = "padel_auth"
    private const val KEY_VERIFIER = "pkce_verifier"

    const val REDIRECT = "padelpulse://auth"

    /** Url a la que hay que mandar al usuario para que elija su cuenta. */
    fun authorizeUrl(context: Context, supabaseUrl: String): String {
        val verifier = nuevoVerificador()
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_VERIFIER, verifier).apply()

        return Uri.parse("$supabaseUrl/auth/v1/authorize").buildUpon()
            .appendQueryParameter("provider", "google")
            .appendQueryParameter("redirect_to", REDIRECT)
            .appendQueryParameter("code_challenge", reto(verifier))
            .appendQueryParameter("code_challenge_method", "s256")
            .build().toString()
    }

    /**
     * Procesa la vuelta de Google. Devuelve el JSON de la sesion, o null si el
     * enlace no trae nada aprovechable.
     *
     * Se contemplan las dos formas en que puede volver: con un codigo que hay
     * que canjear (PKCE) o con la sesion ya hecha en el fragmento de la url.
     */
    fun handleCallback(
        context: Context,
        uri: Uri,
        supabaseUrl: String,
        apiKey: String
    ): String? {
        val error = uri.getQueryParameter("error_description") ?: uri.getQueryParameter("error")
        if (error != null) {
            Log.w(TAG, "Google devolvio error: $error")
            return JSONObject().put("error", error).toString()
        }

        val code = uri.getQueryParameter("code")
        if (code != null) {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val verifier = prefs.getString(KEY_VERIFIER, "") ?: ""
            prefs.edit().remove(KEY_VERIFIER).apply()
            if (verifier.isEmpty()) {
                Log.w(TAG, "Vuelve un codigo pero no tenemos verificador")
                return JSONObject().put("error", "sesion caducada, vuelve a intentarlo").toString()
            }
            return canjear(supabaseUrl, apiKey, code, verifier)
        }

        // La sesion puede venir ya hecha detras de la almohadilla
        val frag = uri.fragment ?: return null
        val datos = frag.split("&").mapNotNull {
            val p = it.split("=", limit = 2)
            if (p.size == 2) p[0] to Uri.decode(p[1]) else null
        }.toMap()
        val token = datos["access_token"] ?: return null

        val sesion = JSONObject()
            .put("access_token", token)
            .put("refresh_token", datos["refresh_token"] ?: "")
            .put("expires_in", (datos["expires_in"] ?: "3600").toIntOrNull() ?: 3600)
        usuario(supabaseUrl, apiKey, token)?.let { sesion.put("user", it) }
        return sesion.toString()
    }

    // ── Interiores ──

    private fun nuevoVerificador(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return base64Url(bytes)
    }

    private fun reto(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun canjear(supabaseUrl: String, apiKey: String, code: String, verifier: String): String? {
        val cuerpo = JSONObject().put("auth_code", code).put("code_verifier", verifier).toString()
        return post("$supabaseUrl/auth/v1/token?grant_type=pkce", apiKey, null, cuerpo)
    }

    private fun usuario(supabaseUrl: String, apiKey: String, token: String): JSONObject? {
        val txt = get("$supabaseUrl/auth/v1/user", apiKey, token) ?: return null
        return runCatching { JSONObject(txt) }.getOrNull()
    }

    private fun post(url: String, apiKey: String, token: String?, body: String): String? =
        peticion(url, "POST", apiKey, token, body)

    private fun get(url: String, apiKey: String, token: String?): String? =
        peticion(url, "GET", apiKey, token, null)

    private fun peticion(
        url: String, metodo: String, apiKey: String, token: String?, body: String?
    ): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = metodo
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.setRequestProperty("apikey", apiKey)
        c.setRequestProperty("Content-Type", "application/json")
        if (token != null) c.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            c.doOutput = true
            c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val ok = c.responseCode in 200..299
        val texto = (if (ok) c.inputStream else c.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""
        c.disconnect()
        if (!ok) {
            Log.w(TAG, "HTTP ${c.responseCode} en $metodo $url")
            return@runCatching JSONObject().put(
                "error",
                runCatching { JSONObject(texto).optString("error_description", texto) }.getOrDefault(texto)
            ).toString()
        }
        texto
    }.getOrElse {
        Log.w(TAG, "Fallo de red hablando con Supabase", it)
        JSONObject().put("error", "sin conexion").toString()
    }
}
