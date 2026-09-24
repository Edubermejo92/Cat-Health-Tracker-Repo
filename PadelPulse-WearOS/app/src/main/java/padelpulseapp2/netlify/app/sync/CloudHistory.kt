package padelpulseapp2.netlify.app.sync

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/**
 * Historial de la cuenta, tal y como lo tiene el movil.
 *
 * El reloj no habla con Supabase: el movil ya sincroniza el historial de cada
 * usuario y nos manda sus ultimos partidos por /padel/history, en el mismo
 * formato que los que guarda el reloj. Asi el Historial de la muñeca es el de
 * la cuenta, y no solo lo que se jugo con este reloj.
 *
 * Si llega vacio -sin sesion o cuenta nueva-, la pantalla vuelve a los
 * partidos locales del reloj.
 */
object CloudHistory {

    private const val PREFS = "padel_prefs"
    private const val KEY = "cloud_history"

    /** Partidos en JSON, el mas reciente primero. */
    var json by mutableStateOf("[]")
        private set
    /** Balance sobre todo el historial de la cuenta, no solo lo que viaja. */
    var played by mutableStateOf(0)
        private set
    var won by mutableStateOf(0)
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        json = p.getString(KEY, "[]") ?: "[]"
        played = p.getInt("${KEY}_played", 0)
        won = p.getInt("${KEY}_won", 0)
    }

    fun applyFromPhone(context: Context, obj: JSONObject) {
        val matches = obj.optJSONArray("matches") ?: JSONArray()
        json = matches.toString()
        played = obj.optInt("played", matches.length())
        won = obj.optInt("won", 0)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json)
            .putInt("${KEY}_played", played)
            .putInt("${KEY}_won", won)
            .apply()
    }

    fun matches(): List<JSONObject> = runCatching {
        val array = JSONArray(json)
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())
}
