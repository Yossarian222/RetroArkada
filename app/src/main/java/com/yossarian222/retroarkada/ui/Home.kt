package com.yossarian222.retroarkada.ui

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.yossarian222.retroarkada.data.Game
import com.yossarian222.retroarkada.data.Library
import com.yossarian222.retroarkada.game.GameActivity
import com.yossarian222.retroarkada.server.UploadServer
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed interface Screen {
    data object Home : Screen

    data object AddGames : Screen

    data object Settings : Screen

    data class Detail(val game: Game) : Screen
}

private class GameRowData(val key: String, val title: String, val subtitle: String?, val games: List<Game>)

@Composable
fun ArkadaApp() {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val listState = rememberLazyListState()
    var restoreKey by remember { mutableStateOf<String?>(null) }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "screen") { s ->
            when (s) {
                Screen.Home ->
                    HomeScreen(
                        listState = listState,
                        restoreKey = restoreKey,
                        onFocusKey = { restoreKey = it },
                        onAdd = { screen = Screen.AddGames },
                        onSettings = { screen = Screen.Settings },
                        onDetail = { screen = Screen.Detail(it) },
                    )
                Screen.AddGames -> AddGamesScreen(onBack = { screen = Screen.Home })
                Screen.Settings -> SettingsScreen(onAdd = { screen = Screen.AddGames })
                is Screen.Detail -> DetailScreen(s.game, onClose = { screen = Screen.Home })
            }
        }
    }
}

// ---------------------------------------------------------------------- domov

@Composable
private fun HomeScreen(
    listState: LazyListState,
    restoreKey: String?,
    onFocusKey: (String) -> Unit,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onDetail: (Game) -> Unit,
) {
    val context = LocalContext.current
    val games by Library.games.collectAsState()
    val favorites by Library.favorites.collectAsState()
    val recent by Library.recent.collectAsState()
    var focused by remember { mutableStateOf<Game?>(null) }

    val rows =
        remember(games, favorites, recent) {
            val byId = games.associateBy { it.id }
            buildList {
                recent.mapNotNull(byId::get).takeIf { it.isNotEmpty() }?.let { add(GameRowData("recent", "Pokračovať v hraní", null, it)) }
                games.filter { it.id in favorites }.takeIf { it.isNotEmpty() }?.let { add(GameRowData("fav", "★ Obľúbené", null, it)) }
                games.groupBy { it.system }.toList().sortedBy { (s, _) -> Library.availableSystems.indexOf(s) }.forEach { (system, list) ->
                    add(GameRowData(system.id, system.name, "${system.maker} · ${system.year} · ${countLabel(list.size)}", list))
                }
            }
        }

    Box(Modifier.fillMaxSize()) {
        // Rozmazaný obal zameranej hry v pozadí
        focused?.coverUrl?.let { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.6f).fillMaxHeight(0.75f).blur(48.dp).alpha(0.35f),
            )
        }
        Box(Modifier.fillMaxSize().background(Palette.backdrop))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.25f to Color.Transparent, 0.7f to Palette.bg)))

        Column(Modifier.fillMaxSize().padding(top = 24.dp)) {
            TopBar(onAdd, onSettings)
            Hero(focused, Modifier.padding(horizontal = 48.dp).height(112.dp))
            if (games.isEmpty()) {
                EmptyLibrary(Modifier.padding(horizontal = 48.dp))
            } else {
                LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 64.dp)) {
                    items(rows, key = { it.key }) { row ->
                        Column(Modifier.padding(top = 10.dp)) {
                            Row(Modifier.padding(horizontal = 48.dp), verticalAlignment = Alignment.Bottom) {
                                Text(row.title, color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                                row.subtitle?.let { Text("   $it", color = Palette.muted, fontSize = 13.sp) }
                            }
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 48.dp, vertical = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(20.dp),
                            ) {
                                items(row.games, key = { it.id }) { game ->
                                    val key = "${row.key}/${game.id}"
                                    val shouldFocus =
                                        if (restoreKey == null) row == rows.first() && game == row.games.first() else key == restoreKey
                                    GameCard(
                                        game = game,
                                        favorite = game.id in favorites,
                                        requestFocus = shouldFocus,
                                        onFocused = {
                                            focused = game
                                            onFocusKey(key)
                                        },
                                        onPlay = { GameActivity.start(context, game) },
                                        onOptions = { onDetail(game) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun countLabel(n: Int) = "$n " + if (n == 1) "hra" else if (n in 2..4) "hry" else "hier"

@Composable
private fun TopBar(onAdd: () -> Unit, onSettings: () -> Unit) {
    var time by remember { mutableStateOf(clock()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = clock()
            delay(15_000)
        }
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("RETRO ARKÁDA", style = TextStyle(brush = Palette.logo, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp))
        Spacer(Modifier.weight(1f))
        FocusButton("＋  Pridať hry", onClick = onAdd)
        Spacer(Modifier.width(12.dp))
        FocusButton("⚙  Nastavenia", onClick = onSettings)
        Spacer(Modifier.width(24.dp))
        Text(time, color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Light)
    }
}

private fun clock() = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

@Composable
private fun Hero(game: Game?, modifier: Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.Center) {
        if (game == null) {
            Text("Vitaj v arkáde", color = Palette.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Vyber si hru ovládačom a stlač A", color = Palette.muted, fontSize = 16.sp)
            return@Column
        }
        Text(game.title, color = Palette.text, fontSize = 32.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(Color(game.system.color)))
            Text("  ${game.system.fullName} · ${game.system.year}", color = Palette.muted, fontSize = 15.sp)
            Spacer(Modifier.width(28.dp))
            Hint("A", Color(0xFF3DDC97), "Hrať")
            Hint("Y", Color(0xFFFFC83D), "Obľúbená")
            Hint("X", Color(0xFF4FA3FF), "Detail")
        }
    }
}

@Composable
private fun Hint(button: String, color: Color, label: String) {
    Box(Modifier.size(22.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
        Text(button, color = Palette.bg, fontSize = 12.sp, fontWeight = FontWeight.Black)
    }
    Text(" $label", color = Palette.text, fontSize = 14.sp, modifier = Modifier.padding(end = 18.dp))
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GameCard(
    game: Game,
    favorite: Boolean,
    requestFocus: Boolean,
    onFocused: () -> Unit,
    onPlay: () -> Unit,
    onOptions: () -> Unit,
) {
    var isFocused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isFocused) 1.12f else 1f, label = "cardScale")
    val shape = RoundedCornerShape(14.dp)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(requestFocus) {
        if (requestFocus) runCatching { focusRequester.requestFocus() }
    }
    Box(
        Modifier
            .size(width = 132.dp, height = 180.dp)
            .scale(scale)
            .shadow(if (isFocused) 28.dp else 0.dp, shape, ambientColor = Color(game.system.color), spotColor = Color(game.system.color))
            .clip(shape)
            .border(if (isFocused) 3.dp else 1.dp, if (isFocused) Color.White else Palette.line, shape)
            .focusRequester(focusRequester)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onPreviewKeyEvent { e ->
                when (e.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_Y -> {
                        if (e.nativeKeyEvent.action == KeyEvent.ACTION_UP) Library.toggleFavorite(game)
                        true
                    }
                    KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_MENU -> {
                        if (e.nativeKeyEvent.action == KeyEvent.ACTION_UP) onOptions()
                        true
                    }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onLongClick = onOptions,
                onClick = onPlay,
            ),
    ) {
        GeneratedCover(game, Modifier.fillMaxSize(), labelEndPadding = if (favorite) 26.dp else 0.dp)
        AsyncImage(model = game.coverUrl, contentDescription = game.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (favorite) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(Color(0xCC000000)),
                contentAlignment = Alignment.Center,
            ) { Text("★", color = Color(0xFFFFC83D), fontSize = 14.sp) }
        }
    }
}

/** Obal pre hry, ku ktorým sa nenašiel obrázok – farby podľa konzoly. */
@Composable
private fun GeneratedCover(game: Game, modifier: Modifier, labelEndPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    val c = Color(game.system.color)
    Box(modifier.background(Brush.linearGradient(listOf(c, Color(0xFF0B0D17)))).padding(10.dp)) {
        Text(
            game.system.name.uppercase(),
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.TopStart).padding(end = labelEndPadding),
        )
        Text(
            game.title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 18.sp,
            maxLines = 5,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            "${game.system.maker} · ${game.system.year}",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 10.sp,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        UploadQr()
        Spacer(Modifier.width(36.dp))
        Column {
            Text("Knižnica je zatiaľ prázdna", color = Palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            UploadSteps()
        }
    }
}

// ---------------------------------------------------------------------- pridávanie hier

@Composable
private fun UploadQr() {
    val address = remember { UploadServer.address() }
    Box(
        Modifier.size(220.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(14.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (address != null) {
            val qr = remember(address) { qrCode(address) }
            Image(qr, contentDescription = address, modifier = Modifier.fillMaxSize())
        } else {
            Text("Bez siete", color = Palette.bg, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun UploadSteps() {
    val address = remember { UploadServer.address() }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Z mobilu alebo počítača v tej istej sieti:", color = Palette.muted, fontSize = 16.sp)
        Text("1.  Naskenuj QR kód alebo otvor v prehliadači", color = Palette.text, fontSize = 17.sp)
        Text(address ?: "TV nie je pripojená k sieti", style = TextStyle(brush = Palette.logo, fontSize = 30.sp, fontWeight = FontWeight.Bold))
        Text("2.  Vyber alebo pretiahni súbory s hrami", color = Palette.text, fontSize = 17.sp)
        Text("3.  Hry sa hneď objavia tu v knižnici", color = Palette.text, fontSize = 17.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "Alebo USB kľúč: priečinok „Roms“ (napr. Roms/snes, Roms/nes…) – povoľ prístup v Nastaveniach.",
            color = Palette.muted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun AddGamesScreen(onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Palette.backdrop).padding(48.dp)) {
        Column {
            Text("Pridať hry", style = TextStyle(brush = Palette.logo, fontSize = 34.sp, fontWeight = FontWeight.Black))
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                UploadQr()
                Spacer(Modifier.width(40.dp))
                Column {
                    UploadSteps()
                    Spacer(Modifier.height(24.dp))
                    val fr = remember { FocusRequester() }
                    FocusButton("Hotovo", Modifier.focusRequester(fr), onClick = onBack)
                    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- detail hry

@Composable
private fun DetailScreen(game: Game, onClose: () -> Unit) {
    val context = LocalContext.current
    val favorites by Library.favorites.collectAsState()
    var confirmDelete by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        AsyncImage(
            model = game.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(60.dp).alpha(0.3f),
        )
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Palette.bg, Palette.bg.copy(alpha = 0.6f)))))
        Row(Modifier.fillMaxSize().padding(56.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 240.dp, height = 330.dp).clip(RoundedCornerShape(18.dp))) {
                GeneratedCover(game, Modifier.fillMaxSize())
                AsyncImage(model = game.coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Spacer(Modifier.width(48.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(game.title, color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${game.system.fullName} · ${game.system.year}", color = Palette.muted, fontSize = 16.sp)
                Text(game.file.name, color = Palette.muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(8.dp))
                val fr = remember { FocusRequester() }
                FocusButton("▶  Hrať", Modifier.width(320.dp).focusRequester(fr)) { GameActivity.start(context, game) }
                FocusButton(if (game.id in favorites) "★  Odobrať z obľúbených" else "☆  Pridať do obľúbených", Modifier.width(320.dp)) {
                    Library.toggleFavorite(game)
                }
                if (Library.stateFile(game, "auto").exists()) {
                    FocusButton("↺  Začať odznova", Modifier.width(320.dp), subtitle = "Zahodí automaticky uloženú pozíciu") {
                        Library.stateFile(game, "auto").delete()
                        GameActivity.start(context, game)
                    }
                }
                if (game.managed) {
                    FocusButton(
                        if (confirmDelete) "Naozaj zmazať? Stlač znova" else "🗑  Zmazať hru",
                        Modifier.width(320.dp),
                    ) {
                        if (confirmDelete) {
                            Library.delete(game)
                            onClose()
                        } else {
                            confirmDelete = true
                        }
                    }
                }
                FocusButton("Späť", Modifier.width(320.dp), onClick = onClose)
                LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
            }
        }
    }
}
