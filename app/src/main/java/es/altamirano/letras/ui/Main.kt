@file:OptIn(ExperimentalMaterial3Api::class)

package es.altamirano.letras.ui

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import es.altamirano.letras.R
import es.altamirano.letras.core.Lyrics
import es.altamirano.letras.core.Song
import es.altamirano.letras.core.currentIndex
import es.altamirano.letras.data.HeardSong
import es.altamirano.letras.data.History
import es.altamirano.letras.data.ListenState
import es.altamirano.letras.data.Listener
import es.altamirano.letras.data.LyricsState
import es.altamirano.letras.data.Phase
import es.altamirano.letras.net.Lrclib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

class LetrasApp : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var history: History; private set
    lateinit var listener: Listener; private set

    override fun onCreate() {
        super.onCreate()
        history = History(this)
        listener = Listener(this, scope, history)
    }
}

private val android.content.Context.app get() = applicationContext as LetrasApp

// --- Colores de `Theme.swift`: brand 5,7:1 en claro y 10,4:1 en oscuro sobre el fondo -----

private val BrandLight = Color(0xFF0B6F53); private val BrandDark = Color(0xFF4FD8A6)
private val BgLight = Color(0xFFF2F8F5); private val BgDark = Color(0xFF0A1411)
private val SurfaceLight = Color(0xFFE1F0EA); private val SurfaceDark = Color(0xFF14261F)

@Composable
fun LetrasTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    // Nada blanco sobre el verde: en oscuro es menta claro; lo que va encima usa el fondo.
    val scheme = if (dark) darkColorScheme(primary = BrandDark, onPrimary = BgDark, background = BgDark, surface = BgDark,
        surfaceContainer = SurfaceDark, surfaceVariant = SurfaceDark, onBackground = Color(0xFFE8F2EE), onSurface = Color(0xFFE8F2EE),
        onSurfaceVariant = Color(0xFF9DB3AA))
    else lightColorScheme(primary = BrandLight, onPrimary = BgLight, background = BgLight, surface = BgLight,
        surfaceContainer = SurfaceLight, surfaceVariant = SurfaceLight, onBackground = Color(0xFF0E1A16), onSurface = Color(0xFF0E1A16),
        onSurfaceVariant = Color(0xFF4A5E56))
    MaterialTheme(colorScheme = scheme, content = content)
}

/**
 * Abrir la app es toda la interacción: empieza a escuchar sola y deja de hacerlo al irse
 * (no hay escucha en segundo plano, así que no hace falta servicio).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val listener = app.listener
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { if (listener.hasPermission()) listener.appStarted() }
            override fun onStop(owner: LifecycleOwner) { listener.appStopped() }
        })
        setContent { LetrasTheme { Root() } }
    }
}

private enum class Screen { MAIN, HISTORY, ABOUT }

@Composable
private fun Root() {
    val context = LocalContext.current
    val listener = context.app.listener
    val state by listener.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf(Screen.MAIN) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) listener.appStarted() else listener.permissionDenied()
    }
    LaunchedEffect(Unit) { if (!listener.hasPermission()) ask.launch(Manifest.permission.RECORD_AUDIO) }
    // Quien lee la letra no toca la pantalla: que no se apague a mitad de canción.
    val view = LocalView.current
    LaunchedEffect(state.phase) { view.keepScreenOn = state.phase == Phase.LISTENING }

    when (screen) {
        Screen.HISTORY -> { BackHandler { screen = Screen.MAIN }; HistoryScreen { screen = Screen.MAIN }; return }
        Screen.ABOUT -> { BackHandler { screen = Screen.MAIN }; AboutScreen { screen = Screen.MAIN }; return }
        Screen.MAIN -> Unit
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = {},
            navigationIcon = {
                Row {
                    IconButton(onClick = { screen = Screen.ABOUT }) { Icon(Icons.Filled.Info, stringResource(R.string.about)) }
                    IconButton(onClick = { screen = Screen.HISTORY }) { Icon(Icons.Filled.History, stringResource(R.string.history)) }
                }
            },
            actions = {
                if (state.phase != Phase.PERMISSION_DENIED) {
                    val on = state.phase == Phase.LISTENING
                    // El botón dice lo que hará al tocarlo.
                    IconButton(onClick = { listener.toggle() }) {
                        Icon(if (on) Icons.Filled.Mic else Icons.Filled.MicOff, stringResource(if (on) R.string.stop_listening else R.string.listen))
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background,
                navigationIconContentColor = MaterialTheme.colorScheme.primary, actionIconContentColor = MaterialTheme.colorScheme.primary))
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val song = state.song
            when {
                state.phase == Phase.PERMISSION_DENIED -> MicDenied { if (listener.hasPermission()) listener.appStarted() else ask.launch(Manifest.permission.RECORD_AUDIO) }
                song != null -> SongView(song, state)
                else -> ListeningView(state) { listener.toggle() }
            }
        }
    }
}

// --- Esperando una canción ------------------------------------------------------------------

@Composable
private fun ListeningView(state: ListenState, onListen: () -> Unit) {
    val on = state.phase == Phase.LISTENING
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        // El micrófono en un disco suave, con una onda que se mueve mientras escucha.
        val pulse by rememberInfiniteTransition(label = "onda").animateFloat(0.55f, 1f,
            infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "onda")
        Box(Modifier.size(136.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer), contentAlignment = Alignment.Center) {
            Icon(if (on) Icons.Filled.GraphicEq else Icons.Filled.MicOff, null, Modifier.size(56.dp).graphicsLayer { if (on) alpha = pulse },
                tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(24.dp))
        Text(stringResource(if (on) R.string.listening else R.string.paused), fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(when { !on -> R.string.tap_mic_hint; state.misses >= 2 -> R.string.cant_recognize; else -> R.string.play_music_hint }),
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
        if (!on) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onListen) { Icon(Icons.Filled.Mic, null); Text("  " + stringResource(R.string.listen), fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun MicDenied(onRetry: () -> Unit) {
    val context = LocalContext.current
    Empty(Icons.Filled.MicOff, stringResource(R.string.no_mic), stringResource(R.string.mic_needed)) {
        Button(onClick = {
            context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
        }) { Text(stringResource(R.string.open_settings)) }
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun Empty(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, text: String, actions: @Composable () -> Unit = {}) {
    Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(icon, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp))
        Spacer(Modifier.height(16.dp))
        actions()
    }
}

// --- La canción y su letra ------------------------------------------------------------------

private fun openUrl(context: android.content.Context, url: String) =
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

@Composable
private fun SongView(song: Song, state: ListenState) {
    val listener = LocalContext.current.app.listener
    Column(Modifier.fillMaxSize()) {
        SongHeader(song)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LyricsContent(song, state.lyrics, synced = { lines -> SyncedLyrics(lines) { listener.state.value.position() } }) { listener.retryLyrics() }
        }
    }
}

@Composable
private fun LyricsContent(song: Song, lyrics: LyricsState, synced: @Composable (List<es.altamirano.letras.core.LyricLine>) -> Unit, onRetry: () -> Unit) {
    val context = LocalContext.current
    when (lyrics) {
        LyricsState.Loading -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(stringResource(R.string.searching_lyrics), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        is LyricsState.Loaded -> when (val l = lyrics.lyrics) {
            is Lyrics.Synced -> synced(l.lines)
            is Lyrics.Plain -> PlainLyrics(l.text, label = true)
            Lyrics.Instrumental -> Empty(Icons.Filled.MusicNote, stringResource(R.string.instrumental), stringResource(R.string.no_lyrics_song))
        }
        LyricsState.NotFound -> Empty(Icons.Filled.MusicNote, stringResource(R.string.no_lyrics), stringResource(R.string.not_found)) {
            TextButton(onClick = { openUrl(context, song.youtubeUrl) }) { Text(stringResource(R.string.search_youtube)) }
        }
        LyricsState.Failed -> Empty(Icons.Filled.WifiOff, stringResource(R.string.no_connection), stringResource(R.string.download_failed)) {
            Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}

/** Carátula, título y artista, con el enlace a YouTube. */
@Composable
private fun SongHeader(song: Song) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).padding(20.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(song.artworkUrl, 56)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp).semantics(mergeDescendants = true) {}) {
            Text(song.title, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(song.artist, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = { openUrl(context, song.youtubeUrl) }) {
            Icon(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.open_youtube), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Artwork(url: String?, sizeDp: Int) {
    Box(Modifier.size(sizeDp.dp).clip(RoundedCornerShape((sizeDp / 6).dp)).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
        if (url != null) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}

/** La línea se enciende 0,3 s antes: una que llega tarde parece más desfasada que una que llega pronto. */
private const val LEAD = 0.3

/**
 * La letra sincronizada (`SyncedLyricsView`): la línea que se canta resaltada y a un tercio de
 * la pantalla. Al arrastrar, deja de seguir la canción 4 s en vez de quitarle el texto del dedo.
 */
@Composable
private fun SyncedLyrics(lines: List<es.altamirano.letras.core.LyricLine>, position: () -> Double?) {
    var current by remember(lines) { mutableStateOf<Int?>(null) }
    LaunchedEffect(lines) {
        while (true) { current = position()?.let { lines.currentIndex(it + LEAD) }; delay(200) }
    }
    val list = rememberLazyListState()
    var pausedUntil by remember { mutableLongStateOf(0L) }
    val drag = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) pausedUntil = System.currentTimeMillis() + 4000
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(current) {
        val i = current ?: return@LaunchedEffect
        if (System.currentTimeMillis() < pausedUntil) return@LaunchedEffect
        list.animateScrollToItem(i, -(list.layoutInfo.viewportSize.height * 0.3f).toInt())
    }
    val instrumental = stringResource(R.string.instrumental_break)
    LazyColumn(Modifier.fillMaxSize().nestedScroll(drag), state = list,
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 32.dp, bottom = 320.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)) {
        itemsIndexed(lines, key = { _, l -> l.id }) { i, line ->
            val isCurrent = i == current
            val past = current?.let { i < it } ?: false
            val scale = if (isCurrent) 1.04f else 1f
            Text(line.text.ifEmpty { "♪" }, fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp,
                color = if (isCurrent) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp)
                    .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        alpha = if (isCurrent) 1f else if (past) 0.45f else 0.7f }
                    .semantics { selected = isCurrent; if (line.text.isEmpty()) contentDescription = instrumental })
        }
    }
}

/** Letra sin tiempos: se lee a mano. */
@Composable
private fun PlainLyrics(text: String, label: Boolean) {
    SelectionContainer {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp)) {
            if (label) Text(stringResource(R.string.not_synced), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp))
            Text(text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, lineHeight = 30.sp)
        }
    }
}

// --- Historial ------------------------------------------------------------------------------

@Composable
private fun HistoryScreen(onClose: () -> Unit) {
    val history = LocalContext.current.app.history
    val songs by history.songs.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<HeardSong?>(null) }
    var confirm by remember { mutableStateOf(false) }
    open?.let { e -> BackHandler { open = null }; PastLyrics(e.song) { open = null }; return }
    val locale = Locale.getDefault()
    val dayFmt = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
    val timeFmt = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    val zone = ZoneId.systemDefault()
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.history), fontWeight = FontWeight.SemiBold) },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { if (songs.any { !it.favorite }) TextButton(onClick = { confirm = true }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { pad ->
        if (songs.isEmpty()) { Box(Modifier.padding(pad)) { Empty(Icons.Filled.MusicNote, stringResource(R.string.no_songs), stringResource(R.string.history_empty_hint)) }; return@Scaffold }
        val days = songs.groupBy { Instant.ofEpochMilli(it.heardAt).atZone(zone).toLocalDate() }
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            days.forEach { (day: LocalDate, entries) ->
                item(key = "d$day") {
                    Text(day.format(dayFmt).replaceFirstChar { it.titlecase(locale) }, Modifier.padding(20.dp, 18.dp, 20.dp, 6.dp),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                entries.forEach { e ->
                    item(key = e.id) {
                        val swipe = rememberSwipeToDismissBoxState(confirmValueChange = { v ->
                            when (v) {
                                SwipeToDismissBoxValue.EndToStart -> { history.delete(e.id); true }
                                SwipeToDismissBoxValue.StartToEnd -> { history.toggleFavorite(e.id); false }
                                else -> false
                            }
                        })
                        SwipeToDismissBox(swipe, backgroundContent = {
                            val toEnd = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
                            Box(Modifier.fillMaxSize().background(if (toEnd) Color(0xFFE91E63) else MaterialTheme.colorScheme.error).padding(horizontal = 20.dp),
                                contentAlignment = if (toEnd) Alignment.CenterStart else Alignment.CenterEnd) {
                                Text(stringResource(if (toEnd) (if (e.favorite) R.string.unmark_favorite else R.string.mark_favorite) else R.string.delete), color = Color.White)
                            }
                        }) {
                            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).clickable { open = e }.padding(16.dp, 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Artwork(e.song.artworkUrl, 46)
                                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                    Text(e.song.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        color = if (e.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    Text(e.song.artist, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                    Text(Instant.ofEpochMilli(e.heardAt).atZone(zone).format(timeFmt), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { history.toggleFavorite(e.id) }) {
                                    Icon(if (e.favorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                        stringResource(if (e.favorite) R.string.unmark_favorite else R.string.mark_favorite),
                                        tint = if (e.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        text = { Text(stringResource(R.string.clear_confirm)) },
        confirmButton = { TextButton(onClick = { history.clearAll(); confirm = false }) { Text(stringResource(R.string.clear_history), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(stringResource(R.string.cancel)) } })
}

/** La letra de una canción pasada: ya no suena, así que la sincronizada se lee como texto. */
@Composable
private fun PastLyrics(song: Song, onClose: () -> Unit) {
    var state by remember { mutableStateOf<LyricsState>(LyricsState.Loading) }
    var attempt by remember { mutableIntStateOf(0) }
    LaunchedEffect(attempt) {
        state = LyricsState.Loading
        state = runCatching { Lrclib.lyrics(song) }.fold({ r -> r?.let { LyricsState.Loaded(it.lyrics) } ?: LyricsState.NotFound }, { LyricsState.Failed })
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            SongHeader(song)
            LyricsContent(song, state, synced = { lines -> PlainLyrics(lines.joinToString("\n") { it.text.ifEmpty { "♪" } }, label = false) }) { attempt++ }
        }
    }
}

// --- Acerca de ------------------------------------------------------------------------------

@Composable
private fun AboutScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).let { "${it.versionName} (${it.longVersionCode})" } }.getOrDefault("?")
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopAppBar(title = { Text(stringResource(R.string.about), fontWeight = FontWeight.SemiBold) },
            actions = { TextButton(onClick = onClose) { Text(stringResource(R.string.done)) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Section { Text(stringResource(R.string.about_text)) }
            Section(stringResource(R.string.credits)) {
                Pair(stringResource(R.string.music_recognition), "Shazam")
                HorizontalDivider(color = MaterialTheme.colorScheme.background)
                Pair(stringResource(R.string.lyrics_credit), "LRCLIB") { openUrl(context, "https://lrclib.net") }
            }
            Section(stringResource(R.string.privacy)) { Text(stringResource(R.string.privacy_text)) }
            Section { Pair(stringResource(R.string.version), version) }
        }
    }
}

@Composable
private fun Section(title: String? = null, content: @Composable () -> Unit) {
    Column {
        title?.let { Text(it.uppercase(), Modifier.padding(start = 16.dp, bottom = 6.dp), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp, 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}

@Composable
private fun Pair(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)) {
        Text(label, Modifier.weight(1f))
        Text(value, color = if (onClick != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

