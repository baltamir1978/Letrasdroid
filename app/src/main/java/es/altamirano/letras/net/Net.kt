package es.altamirano.letras.net

import es.altamirano.letras.core.Lrc
import es.altamirano.letras.core.ShazamSignature
import es.altamirano.letras.core.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.TimeUnit

const val USER_AGENT = "Letras-Android/0.1 (https://github.com/baltamir1978/Letrasdroid)"

/** 20 s: LRCLIB llega a tardar 14 s en contestar (aprendido en LinRadio). */
val http: OkHttpClient = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).build()
private val json = Json { ignoreUnknownKeys = true }

sealed interface ShazamOutcome {
    /** [offset]: el segundo de la canción en que empieza el trozo enviado (medido en RadioDroid). */
    data class Match(val song: Song, val offset: Double) : ShazamOutcome
    data object NoMatch : ShazamOutcome
    data object Failed : ShazamOutcome
}

/**
 * Shazam sin ShazamKit (Bruno cierra su cuenta de Apple): la misma petición que hace su app,
 * con la huella calculada en el móvil (`ShazamSignature`, de RadioDroid). **No es una API
 * pública**: si Shazam la cambia, deja de reconocer.
 */
object Shazam {
    suspend fun recognize(signature: ShazamSignature): ShazamOutcome = withContext(Dispatchers.IO) {
        val lang = Locale.getDefault().language.ifEmpty { "en" }
        val country = Locale.getDefault().country.ifEmpty { "ES" }
        val url = "https://amp.shazam.com/discovery/v5/$lang/$country/android/-/tag/" +
            "${UUID.randomUUID().toString().uppercase()}/${UUID.randomUUID().toString().uppercase()}" +
            "?sync=true&webv3=true&sampling=true&connected=&shazamapiversion=v3&sharehub=true" +
            "&hubv5minorversion=v5.1&hidelb=true&video=v3"
        val body = buildJsonObject {
            put("timezone", TimeZone.getDefault().id)
            putJsonObject("signature") { put("uri", signature.toUri()); put("samplems", signature.sampleMs) }
            put("timestamp", System.currentTimeMillis())
            putJsonObject("context") {}
            putJsonObject("geolocation") {}
        }.toString()
        val req = Request.Builder().url(url)
            .header("User-Agent", "Dalvik/2.1.0 (Linux; U; Android 14)").header("Accept", "*/*").header("Accept-Language", lang)
            .post(body.toRequestBody("application/json".toMediaType())).build()
        try {
            http.newCall(req).execute().use { r -> if (!r.isSuccessful) ShazamOutcome.Failed else parse(r.body?.string().orEmpty()) }
        } catch (e: IOException) { ShazamOutcome.Failed }
    }

    fun parse(text: String): ShazamOutcome {
        val data = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return ShazamOutcome.Failed
        val match = (data["matches"] as? JsonArray)?.firstOrNull() as? JsonObject
        val track = data["track"] as? JsonObject
        if (match == null || track == null) return ShazamOutcome.NoMatch
        fun JsonObject.s(k: String) = (this[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
        val title = track.s("title") ?: return ShazamOutcome.NoMatch
        return ShazamOutcome.Match(
            Song(title, track.s("subtitle") ?: "", (track["images"] as? JsonObject)?.s("coverart"), track.s("url"), track.s("key")),
            (match["offset"] as? JsonPrimitive)?.doubleOrNull ?: 0.0,
        )
    }
}

/** LRCLIB (`LyricsService`): gratis, sin clave, y de las pocas con letra sincronizada. */
object Lrclib {
    /** null si LRCLIB no tiene nada; lanza sólo si falla la red, para distinguir «no hay» de «reintenta». */
    suspend fun lyrics(song: Song): Lrc.Result? = withContext(Dispatchers.IO) {
        for ((title, artist) in Lrc.attempts(song)) {
            val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("track_name", title).addQueryParameter("artist_name", artist).build()
            val text = http.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build()).execute().use { r ->
                if (r.code == 404) "[]" else if (!r.isSuccessful) throw IOException("LRCLIB ${r.code}") else r.body?.string().orEmpty()
            }
            val arr = runCatching { json.parseToJsonElement(text) as JsonArray }.getOrElse { throw IOException("LRCLIB ilegible") }
            Lrc.pick(arr, title)?.let { return@withContext it }
        }
        null
    }
}
