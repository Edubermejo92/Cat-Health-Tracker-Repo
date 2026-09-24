package padelpulseapp2.netlify.app.voice

import org.json.JSONObject
import java.text.Normalizer

/**
 * Interprete de voz del marcador: lo que el usuario canta en pista, en
 * cualquiera de los idiomas de la app, convertido en una accion.
 *
 * Es una copia exacta, paso a paso, de tools/voice-parser.js, que es el que
 * usa el movil, y los dos leen la misma gramatica (res/raw/voice_grammar.json,
 * copiada de tools/voice_grammar.json). Asi "punto para Edu" o "quince
 * treinta" hacen lo mismo en el reloj y en el movil. tools/probar-voz.sh pasa
 * las mismas frases por los dos y falla si no coinciden.
 */
class VoiceParser(grammarJson: String) {

    /** Lo que se ha entendido. team: "A", "B", "SRV" (saque), "RCV" (resto) o null. */
    data class Action(val type: String, val team: String? = null, val a: Int = -1, val b: Int = -1)

    private class Lang(val sub: Boolean, val words: Map<String, List<String>>) {
        operator fun get(k: String): List<String> = words[k].orEmpty()
    }

    private val root = JSONObject(grammarJson)
    private val langs = root.getJSONObject("langs")
    private val letterA = root.getJSONArray("letterA").let { a -> (0 until a.length()).map { a.getString(it) } }
    private val letterB = root.getJSONArray("letterB").let { a -> (0 until a.length()).map { a.getString(it) } }
    private val cache = HashMap<String, Lang>()

    private fun lang(code: String): Lang = cache.getOrPut(code) {
        val src = langs.optJSONObject(code) ?: langs.getJSONObject("en")
        val words = HashMap<String, List<String>>()
        for (k in src.keys()) {
            val arr = src.optJSONArray(k) ?: continue
            words[k] = (0 until arr.length()).map { norm(arr.getString(it)) }.filter { it.isNotEmpty() }
        }
        Lang(src.optBoolean("sub", false), words)
    }

    /** Donde aparece la frase: pares inicio-fin. Sin espacios (chino...) vale dentro de palabra. */
    private fun find(t: String, p: String, sub: Boolean): List<IntArray> {
        val res = ArrayList<IntArray>()
        if (p.isEmpty()) return res
        if (sub) {
            var i = t.indexOf(p)
            while (i >= 0) { res.add(intArrayOf(i, i + p.length)); i = t.indexOf(p, i + 1) }
        } else {
            val tt = " $t "
            val pp = " $p "
            var i = tt.indexOf(pp)
            while (i >= 0) { res.add(intArrayOf(i, i + p.length)); i = tt.indexOf(pp, i + 1) }
        }
        return res
    }

    private fun has(t: String, list: List<String>, sub: Boolean) = list.any { find(t, it, sub).isNotEmpty() }

    /** Puntos dichos en orden: 0, 15, 30, 40 en palabras del idioma o en cifras. */
    private fun numbers(t: String, g: Lang): List<Int> {
        val hits = ArrayList<IntArray>()
        listOf("zero", "fifteen", "thirty", "forty").forEachIndexed { v, k ->
            g[k].forEach { p -> find(t, p, g.sub).forEach { hits.add(intArrayOf(it[0], it[1], v)) } }
        }
        val valores = mapOf("0" to 0, "00" to 0, "15" to 1, "30" to 2, "40" to 3)
        Regex("\\d+").findAll(t).forEach { m ->
            valores[m.value]?.let { hits.add(intArrayOf(m.range.first, m.range.last + 1, it)) }
        }
        hits.sortWith(compareBy<IntArray> { it[0] }.thenByDescending { it[1] - it[0] })
        val out = ArrayList<Int>()
        var end = -1
        hits.forEach { if (it[0] >= end) { out.add(it[2]); end = it[1] } }
        return out
    }

    private fun side(t: String, g: Lang, names: Map<String, List<String>>, letters: Boolean): String? {
        val min = if (g.sub) 2 else 3
        fun byName(list: List<String>?) = list.orEmpty().any { n ->
            val x = norm(n); x.length >= min && find(t, x, g.sub).isNotEmpty()
        }
        var a = has(t, g["teamA"], g.sub) || byName(names["A"])
        var b = has(t, g["teamB"], g.sub) || byName(names["B"])
        if (!a && !b && letters && !g.sub) {
            val toks = t.split(" ")
            val last = toks.last()
            if (toks.size >= 2) {
                if (last in letterA) a = true
                else if (last in letterB) b = true
            }
        }
        return if (a && !b) "A" else if (b && !a) "B" else null
    }

    fun parse(text: String, code: String, names: Map<String, List<String>>): Action? {
        val g = lang(code)
        val sub = g.sub
        val t = norm(text)
        if (t.isEmpty()) return null
        fun h(k: String) = has(t, g[k], sub)
        val kw = h("point") || h("game") || h("set") || h("advantage") || h("serveChange")

        if (h("undo")) return Action("undo")
        if (h("newMatch")) return Action("newMatch")
        if (h("query")) return Action("query")
        if (h("doubleFault")) return Action("doubleFault")
        if (h("fault")) return Action("fault")
        if (h("serveChange")) return Action("serve", side(t, g, names, true))
        if (h("advantage")) {
            val team = side(t, g, names, true) ?: if (h("receiver")) "RCV" else if (h("server")) "SRV" else null
            return Action("adv", team)
        }
        val n = numbers(t, g)
        if (n.size >= 2) return Action("score", a = n[0], b = n[1])
        if (n.size == 1 && h("all")) return Action("score", a = n[0], b = n[0])
        if (n.isEmpty() && h("deuce")) return Action("score", a = 3, b = 3)
        if (h("game")) return Action("game", side(t, g, names, true))
        if (h("set")) return Action("set", side(t, g, names, true))
        val team = side(t, g, names, kw)
        if (team != null) return Action("point", team)
        if (h("receiver")) return Action("point", "RCV")
        if (h("server")) return Action("point", "SRV")
        if (h("point")) return Action("point", null)
        return null
    }

    companion object {
        fun norm(s: String): String {
            val sb = StringBuilder()
            for (c in s) sb.append(
                when (c) {
                    in '٠'..'٩' -> '0' + (c - '٠')
                    in '۰'..'۹' -> '0' + (c - '۰')
                    else -> c
                }
            )
            var t = Normalizer.normalize(sb.toString(), Normalizer.Form.NFKD).replace(Regex("\\p{M}+"), "")
            t = Normalizer.normalize(t, Normalizer.Form.NFC).lowercase().replace("ß", "ss")
            return t.replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        }
    }
}
