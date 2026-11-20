package com.aryannayak.globalradio

import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.AsyncImage
import com.aryannayak.globalradio.data.*
import com.aryannayak.globalradio.player.RadioPlaybackService
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import java.util.concurrent.Executor

private val Background = Color(0xFF080B12)
private val Panel = Color(0xFF141A27)
private val Accent = Color(0xFF9C8CFF)
private val Mint = Color(0xFF64DCC5)
private val Muted = Color(0xFF929BAE)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GlobalRadioTheme {
                GlobalRadioApp()
            }
        }
    }
}

@Composable
fun GlobalRadioTheme(content: @Composable () -> Unit) {
    val colors = darkColorScheme(
        primary = Accent,
        secondary = Mint,
        background = Background,
        surface = Panel,
        onBackground = Color.White,
        onSurface = Color.White
    )

    MaterialTheme(
        colorScheme = colors,
        content = content
    )
}

@Composable
fun GlobalRadioApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val database = remember { RadioDatabase.get(context) }
    val dao = remember { database.radioDao() }

    val favorites by dao.favorites().collectAsState(initial = emptyList())
    val history by dao.history().collectAsState(initial = emptyList())

    var tab by remember { mutableStateOf("Home") }
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var countries by remember { mutableStateOf<List<RadioCountry>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var currentStation by remember { mutableStateOf<RadioStation?>(null) }
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var playing by remember { mutableStateOf(false) }
    var showPlayer by remember { mutableStateOf(false) }
    var showAdd by remember { mutableStateOf(false) }

    // Connect the interface to the background media session.
    DisposableEffect(context) {
        val token = SessionToken(
            context,
            ComponentName(context, RadioPlaybackService::class.java)
        )

        val future = MediaController.Builder(context, token).buildAsync()
        val mainExecutor = Executor { command ->
            Handler(Looper.getMainLooper()).post(command)
        }

        future.addListener({
            controller = runCatching { future.get() }.getOrNull()
        }, mainExecutor)

        onDispose {
            controller?.release()
        }
    }

    DisposableEffect(controller) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playing = isPlaying
            }
        }

        controller?.addListener(listener)

        onDispose {
            controller?.removeListener(listener)
        }
    }

    fun load(block: suspend () -> List<RadioStation>) {
        scope.launch {
            loading = true
            error = ""
            try {
                stations = block()
                if (stations.isEmpty()) {
                    error = "No stations found. Try another search."
                }
            } catch (e: Exception) {
                error = e.message ?: "Unable to load stations."
            } finally {
                loading = false
            }
        }
    }

    fun playStation(station: RadioStation) {
        val mediaController = controller
        if (mediaController == null) {
            error = "Audio player is connecting. Try again."
            return
        }

        currentStation = station
        showPlayer = false

        val metadata = MediaMetadata.Builder()
            .setTitle(station.name)
            .setArtist(station.country.ifBlank { "Global Radio" })
            .build()

        val item = MediaItem.Builder()
            .setUri(station.streamUrl)
            .setMediaMetadata(metadata)
            .build()

        mediaController.setMediaItem(item)
        mediaController.prepare()
        mediaController.play()

        scope.launch {
            dao.addHistory(station.toHistory())
        }
    }

    fun search() {
        if (query.isNotBlank()) {
            load { RadioApi.search(query.trim()) }
        } else {
            load { RadioApi.top() }
        }
    }

    LaunchedEffect(Unit) {
        load { RadioApi.top() }
    }

    LaunchedEffect(tab) {
        if (tab == "Explore" && countries.isEmpty()) {
            try {
                countries = RadioApi.countries()
            } catch (_: Exception) {
                error = "Unable to load countries."
            }
        }
    }

    val visibleStations = when (tab) {
        "Favorites" -> favorites.map { it.toStation() }
        "History" -> history.map { it.toStation() }
        else -> stations
    }

    if (showPlayer && currentStation != null) {
        FullPlayer(
            station = currentStation!!,
            playing = playing,
            onBack = { showPlayer = false },
            onToggle = {
                if (playing) controller?.pause()
                else controller?.play()
            }
        )
        return
    }

    Scaffold(
        containerColor = Background,
        bottomBar = {
            Column {
                currentStation?.let { station ->
                    MiniPlayer(
                        station = station,
                        playing = playing,
                        onOpen = { showPlayer = true },
                        onToggle = {
                            if (playing) controller?.pause()
                            else controller?.play()
                        }
                    )
                }

                NavigationBar(
                    containerColor = Color(0xFF0D111B),
                    contentColor = Color.White
                ) {
                    val tabs = listOf(
                        Triple("Home", Icons.Filled.Home, "Home"),
                        Triple("Explore", Icons.Filled.Explore, "Explore"),
                        Triple("Favorites", Icons.Filled.Favorite, "Favorites"),
                        Triple("History", Icons.Filled.History, "History")
                    )

                    tabs.forEach { (label, icon, destination) ->
                        NavigationBarItem(
                            selected = tab == destination,
                            onClick = { tab = destination },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Accent,
                                selectedTextColor = Accent,
                                indicatorColor = Color(0xFF25213D),
                                unselectedIconColor = Muted,
                                unselectedTextColor = Muted
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Background)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "GLOBAL RADIO",
                        color = Accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        when (tab) {
                            "Home" -> "Discover the world."
                            "Explore" -> "Explore everywhere."
                            "Favorites" -> "Your stations."
                            else -> "Recently played."
                        },
                        fontSize = 25.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(onClick = { showAdd = true }) {
                    Icon(
                        Icons.Filled.Add,
                        "Add custom station",
                        tint = Accent
                    )
                }

                IconButton(
                    onClick = {
                        load {
                            val list = RadioApi.top()
                            list.shuffled().take(1)
                        }
                        scope.launch {
                            try {
                                val result = RadioApi.top().shuffled()
                                result.firstOrNull()?.let { playStation(it) }
                            } catch (e: Exception) {
                                error = e.message ?: "Surprise Me failed."
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Shuffle, "Surprise me", tint = Mint)
                }
            }

            if (tab == "Home" || tab == "Explore") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    placeholder = {
                        Text("Search stations, countries, music...")
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.Search, null)
                    },
                    trailingIcon = {
                        IconButton(onClick = { search() }) {
                            Icon(Icons.Filled.ArrowForward, "Search")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = { search() }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Color(0xFF303849),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Accent
                    )
                )
            }

            if (tab == "Home") {
                Spacer(Modifier.height(14.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Panel
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        Color(0xFF29204D),
                                        Color(0xFF153B43),
                                        Color(0xFF171D2C)
                                    )
                                )
                            )
                            .padding(20.dp)
                    ) {
                        Column {
                            Text(
                                "ONE WORLD. EVERY RADIO.",
                                color = Mint,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Listen beyond\nborders.",
                                fontSize = 29.sp,
                                lineHeight = 35.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Discover live stations from around the globe.",
                                color = Color.LightGray
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
            }

            if (tab == "Explore") {
                Text(
                    "COUNTRIES",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = 20.dp, top = 14.dp, bottom = 8.dp
                    )
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(countries.take(30), key = { it.code }) { country ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                load { RadioApi.byCountry(country.code) }
                            },
                            label = {
                                Text("${country.name} · ${country.stationCount}")
                            }
                        )
                    }
                }

                Text(
                    "GENRES",
                    color = Muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(
                        start = 20.dp, top = 12.dp, bottom = 8.dp
                    )
                )

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        listOf(
                            "Pop", "Rock", "Jazz", "Classical",
                            "Electronic", "News", "Sports", "Talk"
                        )
                    ) { genre ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                load { RadioApi.byTag(genre) }
                            },
                            label = { Text(genre) }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    when (tab) {
                        "Home" -> "Popular stations"
                        "Explore" -> "Discover stations"
                        "Favorites" -> "Saved stations"
                        else -> "Listening history"
                    },
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 19.sp,
                    modifier = Modifier.weight(1f)
                )

                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = Accent
                    )
                }
            }

            if (error.isNotBlank()) {
                Text(
                    error,
                    color = Color(0xFFFF9A9A),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            if (!loading && visibleStations.isEmpty() && error.isBlank()) {
                Text(
                    "Nothing here yet.",
                    color = Muted,
                    modifier = Modifier.padding(20.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = 20.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    visibleStations,
                    key = { it.id.ifBlank { it.streamUrl } }
                ) { station ->
                    StationCard(
                        station = station,
                        favorite = favorites.any {
                            it.stationUuid == station.id
                        },
                        onPlay = { playStation(station) },
                        onFavorite = {
                            scope.launch {
                                if (favorites.any {
                                    it.stationUuid == station.id
                                }) {
                                    dao.removeFavorite(station.id)
                                } else {
                                    dao.addFavorite(station.toEntity())
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddStationDialog(
            onDismiss = { showAdd = false },
            onAdd = { name, url ->
                val station = RadioStation(
                    id = "custom-${url.hashCode()}",
                    name = name,
                    streamUrl = url,
                    favicon = "",
                    country = "Custom station",
                    countryCode = "",
                    language = "",
                    tags = "Custom",
                    bitrate = 0,
                    codec = ""
                )

                scope.launch {
                    dao.addFavorite(station.toEntity())
                }
                stations = listOf(station) + stations
                showAdd = false
                playStation(station)
            }
        )
    }
}

@Composable
fun StationCard(
    station: RadioStation,
    favorite: Boolean,
    onPlay: () -> Unit,
    onFavorite: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Panel
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFF55468D),
                                Color(0xFF226C70)
                            )
                        ),
                        RoundedCornerShape(15.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (station.favicon.startsWith("http")) {
                    AsyncImage(
                        model = station.favicon,
                        contentDescription = station.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(15.dp)),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Filled.Radio,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(29.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    station.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )

                Text(
                    station.country.ifBlank { "Worldwide" },
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1
                )

                Text(
                    listOfNotNull(
                        station.codec.takeIf { it.isNotBlank() },
                        station.bitrate.takeIf { it > 0 }
                            ?.let { "$it kbps" }
                    ).joinToString(" · ").ifBlank { station.tags.take(35) },
                    color = Mint,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }

            IconButton(onClick = onFavorite) {
                Icon(
                    if (favorite) Icons.Filled.Favorite
                    else Icons.Filled.FavoriteBorder,
                    "Favorite",
                    tint = if (favorite) Color(0xFFFF718C) else Muted
                )
            }

            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Filled.PlayCircle,
                    "Play station",
                    tint = Accent,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    station: RadioStation,
    playing: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1A2030))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                station.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            Text(
                station.country.ifBlank { "Global Radio" },
                color = Muted,
                fontSize = 11.sp
            )
        }

        IconButton(onClick = onOpen) {
            Icon(Icons.Filled.OpenInFull, "Open player", tint = Muted)
        }

        IconButton(onClick = onToggle) {
            Icon(
                if (playing) Icons.Filled.Pause
                else Icons.Filled.PlayArrow,
                "Play or pause",
                tint = Accent
            )
        }
    }
}

@Composable
fun FullPlayer(
    station: RadioStation,
    playing: Boolean,
    onBack: () -> Unit,
    onToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.Filled.ArrowBack,
                    "Back",
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "NOW PLAYING",
                color = Accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.weight(1f))
        }

        Spacer(Modifier.weight(1f))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(32.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF4B397B),
                            Color(0xFF17666C),
                            Color(0xFF18233C)
                        )
                    ),
                    RoundedCornerShape(32.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            if (station.favicon.startsWith("http")) {
                AsyncImage(
                    model = station.favicon,
                    contentDescription = station.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    Icons.Filled.Radio,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(110.dp)
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        Text(
            station.name,
            color = Color.White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2
        )

        Spacer(Modifier.height(8.dp))

        Text(
            station.country.ifBlank { "Worldwide radio" },
            color = Mint,
            fontSize = 15.sp
        )

        Spacer(Modifier.weight(1f))

        IconButton(
            onClick = onToggle,
            modifier = Modifier
                .size(82.dp)
                .background(Accent, CircleShape)
        ) {
            Icon(
                if (playing) Icons.Filled.Pause
                else Icons.Filled.PlayArrow,
                "Toggle playback",
                tint = Color(0xFF10101C),
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(Modifier.height(50.dp))
    }
}

@Composable
fun AddStationDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add radio station") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Station name") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Streaming URL") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() &&
                    (url.startsWith("https://") ||
                     url.startsWith("http://")),
                onClick = { onAdd(name.trim(), url.trim()) }
            ) {
                Text("Add station")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
