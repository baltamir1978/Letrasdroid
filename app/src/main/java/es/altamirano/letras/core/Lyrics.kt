package es.altamirano.letras.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import java.net.URLEncoder
import java.text.Normalizer

/** La canción que reconoció Shazam (`Song` de Letrasios). */
@Serializable
data class Song(
    val title: String,
    val artist: String,
    val artworkUrl: String? = null,
    /** La página de la canción en Shazam; null si no la dio. */
    val shazamUrl: String? = null,
    val shazamId: String? = null,
) {
    /** ¿La misma grabación? Así un reconocimiento nuevo de la que suena sólo resincroniza. */
    fun isSameSong(other: Song): Boolean {
        if (shazamId != null && other.shazamId != null) return shazamId == other.shazamId
        return title.equals(other.title, ignoreCase = true) && artist.equals(other.artist, ignoreCase = true)
    }

    /**
     * Dónde oírla fuera de la app: **una búsqueda en YouTube** «artista título» (decidido con
     * Bruno: en vez de Apple Music; abre la app de YouTube si está y el navegador si no).
     */
    val youtubeUrl: String get() = "https://www.youtube.com/results?search_query=" + URLEncoder.encode("$artist $title", "UTF-8")
}

/** Una línea con su tiempo; vacía en las pausas instrumentales (se pinta «♪»). */
data class LyricLine(val id: Int, val time: Double, val text: String)

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics
    data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
}

/** La línea que se canta en `time`: la última cuya marca ya pasó (búsqueda binaria); null antes de la primera. */
fun List<LyricLine>.currentIndex(time: Double): Int? {
    var lo = 0; var hi = size
    while (lo < hi) { val mid = (lo + hi) / 2; if (this[mid].time <= time) lo = mid + 1 else hi = mid }
    return if (lo == 0) null else lo - 1
}

/**
 * El formato LRC de LRCLIB: `[mm:ss.xx] texto`, varias marcas en una línea
 * (`[00:12.00][01:40.50] estribillo`), la etiqueta `[offset:±ms]` (positiva adelanta la letra)
 * y las demás etiquetas (`[ar:…]`, `[ti:…]`) se ignoran.
 */
object Lrc {
    fun parse(lrc: String): List<LyricLine> {
        var offset = 0.0
        val stamped = mutableListOf<Pair<Double, String>>()
        for (raw in lrc.lines()) {
            var rest = raw.trim()
            val times = mutableListOf<Double>()
            while (rest.startsWith("[")) {
                val close = rest.indexOf(']').takeIf { it > 0 } ?: break
                val tag = rest.substring(1, close)
                val t = timestamp(tag)
                if (t != null) times += t
                else if (tag.lowercase().startsWith("offset:")) tag.substring(7).trim().toDoubleOrNull()?.let { offset = -it / 1000 }
                rest = rest.substring(close + 1)
            }
            val text = rest.trim()
            times.forEach { stamped += it to text }
        }
        return stamped.sortedBy { it.first }.mapIndexed { i, (t, text) -> LyricLine(i, maxOf(0.0, t + offset), text) }
    }

    private fun timestamp(tag: String): Double? {
        val parts = tag.split(":")
        if (parts.size != 2) return null
        val m = parts[0].toDoubleOrNull() ?: return null
        val s = parts[1].replace(',', '.').toDoubleOrNull() ?: return null
        return m * 60 + s
    }

    /** «Song (feat. X) - Remastered 2011» → «Song». */
    fun cleanTitle(title: String): String {
        var t = title.replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "").replace(Regex("""\s+-\s+.*$"""), "").trim()
        if (t.isEmpty()) t = title
        return t
    }

    /**
     * «A & B» / «A feat. B» → «A»: LRCLIB guarda la letra con el artista principal. Sólo se
     * prueba después del nombre entero, que «&» también está en nombres («Simon & Garfunkel»).
     */
    fun primaryArtist(artist: String): String {
        var r = artist
        for (sep in listOf(" feat. ", " feat ", " ft. ", " featuring ", " & ", ", ")) {
            val i = r.lowercase().indexOf(sep)
            if (i >= 0) r = r.substring(0, i)
        }
        return r.trim()
    }

    /** Las búsquedas por orden: tal cual, con el título limpio y con el artista principal. */
    fun attempts(song: Song): List<Pair<String, String>> {
        val t = cleanTitle(song.title)
        return listOf(song.title to song.artist, t to song.artist, t to primaryArtist(song.artist)).distinct()
    }

    data class Result(val lyrics: Lyrics, val duration: Double?)

    /** Primero la sincronizada, luego el título exacto, luego la de sólo texto. */
    fun pick(records: JsonArray, title: String): Result? {
        val list = records.filterIsInstance<JsonObject>()
        fun synced(r: JsonObject) = !r.str("syncedLyrics").isNullOrEmpty()
        fun exact(r: JsonObject) = normalize(r.str("trackName") ?: "") == normalize(title)
        val ranked = list.sortedByDescending { (if (synced(it)) 2 else 0) + (if (exact(it)) 1 else 0) }
        for (r in ranked) {
            val duration = (r["duration"] as? JsonPrimitive)?.doubleOrNull
            r.str("syncedLyrics")?.takeIf { it.isNotEmpty() }?.let { s -> parse(s).takeIf { it.isNotEmpty() }?.let { return Result(Lyrics.Synced(it), duration) } }
            r.str("plainLyrics")?.takeIf { it.isNotBlank() }?.let { return Result(Lyrics.Plain(it.trim()), duration) }
            if ((r["instrumental"] as? JsonPrimitive)?.booleanOrNull == true) return Result(Lyrics.Instrumental, duration)
        }
        return null
    }

    fun normalize(s: String) = Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
    private fun JsonObject.str(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
