package com.yossarian222.retroarkada.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.yossarian222.retroarkada.BuildInfo
import com.yossarian222.retroarkada.data.Library
import com.yossarian222.retroarkada.data.Systems
import com.yossarian222.retroarkada.data.Updater

@Composable
fun SettingsScreen(onAdd: () -> Unit) {
    val context = LocalContext.current
    var filter by remember { mutableStateOf(Library.prefs.videoFilter) }
    var storage by remember { mutableStateOf(Library.hasStorageAccess()) }
    val games by Library.games.collectAsState()
    val scanning by Library.scanning.collectAsState()

    // Po návrate z nastavení Androidu skontroluj povolenie znova
    LifecycleResumeEffect(Unit) {
        storage = Library.hasStorageAccess()
        Library.refresh()
        onPauseOrDispose { }
    }

    Box(Modifier.fillMaxSize().background(Palette.backdrop)) {
        Row(Modifier.fillMaxSize().padding(48.dp)) {
            Column(Modifier.width(420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Nastavenia", style = TextStyle(brush = Palette.logo, fontSize = 34.sp, fontWeight = FontWeight.Black))
                Spacer(Modifier.height(12.dp))
                val first = remember { FocusRequester() }
                FocusButton("Filter obrazu", Modifier.width(400.dp).focusRequester(first), subtitle = filter.label) {
                    filter = filter.next()
                    Library.prefs.videoFilter = filter
                }
                var ps1Compat by remember { mutableStateOf(Library.prefs.ps1Compat) }
                FocusButton(
                    "PS1: kompatibilný režim",
                    Modifier.width(400.dp),
                    subtitle = if (ps1Compat) "Zapnutý – pomalší, menej zamŕzania" else "Vypnutý – zapni, ak PS1 hra zamŕza",
                ) {
                    ps1Compat = !ps1Compat
                    Library.prefs.ps1Compat = ps1Compat
                }
                var rumble by remember { mutableStateOf(Library.prefs.rumble) }
                FocusButton(
                    "Vibrácie ovládača",
                    Modifier.width(400.dp),
                    subtitle = if (rumble) "Zapnuté – vypni, ak ovládač vypadáva" else "Vypnuté",
                ) {
                    rumble = !rumble
                    Library.prefs.rumble = rumble
                }
                var lowLatency by remember { mutableStateOf(Library.prefs.lowLatencyAudio) }
                FocusButton(
                    "Zvuk",
                    Modifier.width(400.dp),
                    subtitle = if (lowLatency) "Nízka odozva (môže praskať)" else "Plynulý – bez praskania (odporúčané)",
                ) {
                    lowLatency = !lowLatency
                    Library.prefs.lowLatencyAudio = lowLatency
                }
                FocusButton(
                    "Prístup k USB a úložisku",
                    Modifier.width(400.dp),
                    subtitle = if (storage) "Povolený – hľadám priečinky Roms / RetroArkada" else "Nepovolený – klikni a povoľ",
                ) {
                    requestStorage(context as Activity)
                }
                FocusButton(
                    "Obnoviť knižnicu",
                    Modifier.width(400.dp),
                    subtitle = if (scanning) "Hľadám hry…" else "${games.size} hier v knižnici",
                ) { Library.refresh() }
                FocusButton("Pridať hry z mobilu / PC", Modifier.width(400.dp), onClick = onAdd)
                val update by Updater.state.collectAsState()
                FocusButton(
                    "Aktualizácia aplikácie",
                    Modifier.width(400.dp),
                    subtitle =
                        when (val s = update) {
                            Updater.State.Checking -> "Overujem…"
                            Updater.State.UpToDate -> "Máš najnovšiu verziu"
                            is Updater.State.Available -> "Nová verzia ${s.versionName} – klikni a nainštaluj"
                            is Updater.State.Downloading -> "Sťahujem ${s.percent} %"
                            Updater.State.Installing -> "Inštalujem…"
                            is Updater.State.Error -> s.message
                            Updater.State.Idle -> "Klikni a over, či je nová verzia"
                        },
                ) {
                    when (update) {
                        is Updater.State.Available -> Updater.downloadAndInstall(context as Activity)
                        is Updater.State.Downloading, Updater.State.Installing -> Unit
                        else -> Updater.check(context)
                    }
                }
                LaunchedEffect(Unit) { runCatching { first.requestFocus() } }
            }
            Spacer(Modifier.width(56.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Ovládanie (Xbox ovládač)", color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Info("A – spustiť hru · B – späť")
                Info("Y – pridať / odobrať z obľúbených · X – detail hry")
                Info("V hre: View + Menu naraz, tlačidlo Xbox alebo Späť = menu")
                Info("Hra sa pri odchode automaticky uloží a nabudúce pokračuje")
                Spacer(Modifier.height(16.dp))
                Text("Konzoly", color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                // Dva stĺpce, aby sa zoznam zmestil na obrazovku
                Systems.ALL.chunked((Systems.ALL.size + 1) / 2).let { cols ->
                    Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                        cols.forEach { col ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                col.forEach { s ->
                                    val ok = s in Library.availableSystems
                                    val count = games.count { it.system == s }
                                    Info("${if (ok) "●" else "○"}  ${s.name} · ${if (ok) "$count" else "chýba jadro"}")
                                }
                            }
                        }
                    }
                }
                Info("Priečinky na USB: Roms/nes, Roms/snes, Roms/psx, Roms/arcade…")
                val bios = remember(games) { Library.ps1BiosName() }
                Info(if (bios != null) "PS1 BIOS: ✓ $bios" else "PS1 BIOS: ✗ chýba – daj scph5501.bin na USB do Roms (PS1 hry môžu zamŕzať)")
                Spacer(Modifier.height(16.dp))
                Info("Verzia ${BuildInfo.version(context)}")
            }
        }
    }
}

@Composable
private fun Info(text: String) = Text(text, color = Palette.muted, fontSize = 14.sp)

private fun requestStorage(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val intents =
            listOf(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${activity.packageName}")),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${activity.packageName}")),
            )
        for (intent in intents) {
            if (runCatching { activity.startActivity(intent) }.isSuccess) return
        }
    } else {
        activity.requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
    }
}
