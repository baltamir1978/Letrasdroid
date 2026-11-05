package es.altamirano.letras.data

import android.content.Context
import es.altamirano.letras.core.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/** Una canción reconocida, para volver a ella cuando ya no suena (`HeardSong`). */
@Serializable
data class HeardSong(
    val id: String = UUID.randomUUID().toString(),
    val song: Song,
    val heardAt: Long,
    /** El corazón: se resalta y sobrevive a «Borrar» y al tope. */
    val favorite: Boolean = false,
)

/** El historial, lo más nuevo primero, en `historial.json` (`HistoryStore`). */
class History(context: Context) {
    private val file = File(context.filesDir, "historial.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(HeardSong.serializer())
    private val _songs = MutableStateFlow(runCatching { json.decodeFromString(serializer, file.readText()) }.getOrDefault(emptyList()))
    val songs: StateFlow<List<HeardSong>> = _songs.asStateFlow()

    /** Sólo las canciones nuevas: la misma otra vez en menos de 15 min es la misma escucha. */
    fun add(song: Song, at: Long = System.currentTimeMillis()) {
        val last = _songs.value.firstOrNull()
        if (last != null && last.song.isSameSong(song) && at - last.heardAt < REPEAT_WINDOW) return
        set(trim(listOf(HeardSong(song = song, heardAt = at)) + _songs.value))
    }

    fun toggleFavorite(id: String) = set(_songs.value.map { if (it.id == id) it.copy(favorite = !it.favorite) else it })
    fun delete(id: String) = set(_songs.value.filterNot { it.id == id })
    /** Borra todo salvo las del corazón. */
    fun clearAll() = set(_songs.value.filter { it.favorite })

    private fun set(list: List<HeardSong>) {
        _songs.value = list
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(json.encodeToString(serializer, list)); tmp.renameTo(file)
    }

    companion object {
        const val REPEAT_WINDOW = 15 * 60_000L
        const val MAX = 500

        /** Las favoritas nunca salen por el tope; se van las más viejas de las demás. */
        fun trim(list: List<HeardSong>): List<HeardSong> {
            var excess = list.size - MAX
            if (excess <= 0) return list
            return list.asReversed().filter { if (excess > 0 && !it.favorite) { excess--; false } else true }.asReversed()
        }
    }
}
