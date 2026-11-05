package es.altamirano.letras.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import es.altamirano.letras.core.Lyrics
import es.altamirano.letras.core.ShazamSignature
import es.altamirano.letras.core.Song
import es.altamirano.letras.net.Lrclib
import es.altamirano.letras.net.Shazam
import es.altamirano.letras.net.ShazamOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Phase { IDLE, LISTENING, PERMISSION_DENIED }

sealed interface LyricsState {
    data object Loading : LyricsState
    data class Loaded(val lyrics: Lyrics) : LyricsState
    data object NotFound : LyricsState
    data object Failed : LyricsState
}

data class ListenState(
    val phase: Phase = Phase.IDLE,
    val song: Song? = null,
    val lyrics: LyricsState = LyricsState.Loading,
    /** Intentos seguidos sin reconocer: distingue «escuchando» de «no reconozco lo que suena». */
    val misses: Int = 0,
    /** Segundo de la canción en el instante [anchorAt] (`SystemClock.elapsedRealtime`, ms). */
    val anchorOffset: Double = 0.0,
    val anchorAt: Long = 0,
) {
    /** El segundo de la canción ahora, o null sin canción. */
    fun position(now: Long = SystemClock.elapsedRealtime()): Double? = song?.let { anchorOffset + (now - anchorAt) / 1000.0 }
}

/**
 * Escucha la música de alrededor, la reconoce con Shazam y mantiene la letra a su paso
 * (`SongListener` de Letrasios). Mientras la app está a la vista:
 * - un reconocimiento de una canción **nueva** la cambia y busca su letra;
 * - uno de la **misma** sólo reancla el reloj, así se corrige cada pocos segundos;
 * - cuatro fallos seguidos, o pasar de lo que dura, quitan la canción.
 *
 * Sin ShazamKit: el micrófono se lee con `AudioRecord` a 16 kHz mono (lo que pide la huella) en
 * un búfer circular de 12 s, y cada intento manda los últimos segundos. Shazam dice en qué
 * segundo de la canción **empieza** el trozo, y se sabe cuándo se grabó ese principio: la
 * posición es ese segundo más lo que ha pasado desde entonces.
 */
class Listener(private val context: Context, private val scope: CoroutineScope, private val history: History) {
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var loop: Job? = null
    private var lyricsJob: Job? = null
    private var pausedByUser = false
    private var songDuration: Double? = null

    fun hasPermission() = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** La app vuelve a estar a la vista: se escucha, salvo que se haya parado con el botón. */
    fun appStarted() { if (!pausedByUser) start() }

    /** La app se va: no hay escucha en segundo plano. Al volver se empieza otra vez. */
    fun appStopped() { stop(); pausedByUser = false }

    fun toggle() {
        if (_state.value.phase == Phase.LISTENING) { pausedByUser = true; stop() } else { pausedByUser = false; start() }
    }

    fun permissionDenied() { _state.update { it.copy(phase = Phase.PERMISSION_DENIED) } }

    fun retryLyrics() { _state.value.song?.let(::loadLyrics) }

    private fun start() {
        if (loop?.isActive == true) return
        if (!hasPermission()) { _state.update { it.copy(phase = Phase.PERMISSION_DENIED) }; return }
        _state.update { it.copy(phase = Phase.LISTENING, misses = 0) }
        loop = scope.launch(Dispatchers.Default) { runCatching { run() }.onFailure { Log.w(TAG, "escucha", it) } }
    }

    private fun stop() {
        loop?.cancel(); loop = null
        _state.update { if (it.phase == Phase.LISTENING) it.copy(phase = Phase.IDLE) else it }
    }

    @SuppressLint("MissingPermission")
    private suspend fun run() {
        val minBuf = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, RATE))
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); _state.update { it.copy(phase = Phase.IDLE) }; return }
        val ring = Ring(RATE * WINDOW_MAX_S)
        val reader = scope.launch(Dispatchers.IO) {
            val block = ShortArray(RATE / 10)
            rec.startRecording()
            try {
                while (isActive) {
                    val n = rec.read(block, 0, block.size)
                    if (n > 0) ring.write(block, n, SystemClock.elapsedRealtime())
                }
            } finally { rec.stop(); rec.release() }
        }
        try {
            var nextAt = RATE * FIRST_S.toLong()          // el primer intento, con 5 s de audio
            while (currentCoroutineIsActive()) {
                if (ring.total < nextAt) { delay(200); continue }
                val (pcm, startAt) = ring.last(RATE * WINDOW_S)
                val outcome = Shazam.recognize(ShazamSignature.of(pcm))
                val received = SystemClock.elapsedRealtime()
                when (outcome) {
                    is ShazamOutcome.Match -> {
                        handle(outcome.song, outcome.offset, startAt)
                        delay(RESYNC_MS)
                        nextAt = ring.total + RATE * 4L
                    }
                    ShazamOutcome.NoMatch, ShazamOutcome.Failed -> {
                        _state.update { it.copy(misses = it.misses + 1) }
                        clearIfOver(received)
                        if (outcome == ShazamOutcome.Failed) delay(2000)
                        nextAt = ring.total + RATE * 3L
                    }
                }
            }
        } finally { reader.cancel() }
    }

    private suspend fun currentCoroutineIsActive() = kotlin.coroutines.coroutineContext.isActive

    private fun handle(song: Song, offset: Double, startAt: Long) {
        val same = _state.value.song?.isSameSong(song) == true
        _state.update { it.copy(misses = 0, anchorOffset = offset, anchorAt = startAt, song = if (same) it.song else song,
            lyrics = if (same) it.lyrics else LyricsState.Loading) }
        if (same) return
        Log.i(TAG, "canción nueva: ${song.artist} — ${song.title}")
        songDuration = null
        scope.launch(Dispatchers.Main) { history.add(song) }
        loadLyrics(song)
    }

    /** Un fallo suelto no quita la canción (pasajes suaves, sitios ruidosos): cuatro seguidos, o pasar de lo que dura. */
    private fun clearIfOver(now: Long) {
        val s = _state.value
        if (s.song == null) return
        val pastEnd = songDuration?.let { (s.position(now) ?: 0.0) > it + 5 } ?: false
        if (!pastEnd && s.misses < MISSES_TO_CLEAR) return
        lyricsJob?.cancel()
        songDuration = null
        _state.update { it.copy(song = null, lyrics = LyricsState.Loading) }
    }

    private fun loadLyrics(song: Song) {
        lyricsJob?.cancel()
        _state.update { it.copy(lyrics = LyricsState.Loading) }
        lyricsJob = scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { Lrclib.lyrics(song) } }
            if (_state.value.song?.isSameSong(song) != true) return@launch
            result.onSuccess { r ->
                songDuration = r?.duration
                _state.update { it.copy(lyrics = r?.let { x -> LyricsState.Loaded(x.lyrics) } ?: LyricsState.NotFound) }
            }.onFailure { _state.update { it.copy(lyrics = LyricsState.Failed) } }
        }
    }

    /** Los últimos segundos del micrófono, con la hora a la que se grabó cada muestra. */
    private class Ring(size: Int) {
        private val buf = ShortArray(size)
        @Volatile var total = 0L; private set
        @Volatile private var lastAt = 0L

        @Synchronized fun write(block: ShortArray, n: Int, at: Long) {
            for (i in 0 until n) buf[((total + i) % buf.size).toInt()] = block[i]
            total += n; lastAt = at
        }

        /** Hasta `n` muestras, las más nuevas, y cuándo se grabó la primera (elapsedRealtime, ms). */
        @Synchronized fun last(n: Int): Pair<ShortArray, Long> {
            val count = minOf(n.toLong(), total, buf.size.toLong()).toInt()
            val out = ShortArray(count)
            val start = total - count
            for (i in 0 until count) out[i] = buf[((start + i) % buf.size).toInt()]
            return out to (lastAt - count * 1000L / RATE)
        }
    }

    companion object {
        private const val TAG = "Letras"
        const val RATE = 16000
        /** Shazam reconoce con 8 s (medido en RadioDroid); 10 si los hay, 5 para el primer intento. */
        const val WINDOW_S = 10
        const val WINDOW_MAX_S = 12
        const val FIRST_S = 5
        /** Tras reconocer la que ya suena, esto antes de volver a preguntar. */
        const val RESYNC_MS = 8000L
        const val MISSES_TO_CLEAR = 4

    }
}
