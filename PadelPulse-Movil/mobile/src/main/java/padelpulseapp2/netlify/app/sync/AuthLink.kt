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
