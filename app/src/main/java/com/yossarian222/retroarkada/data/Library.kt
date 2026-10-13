package com.yossarian222.retroarkada.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.Collections
import java.util.zip.ZipFile

/**
 * Knižnica hier. Hry sú uložené v roms/<konzola>/ v úložisku aplikácie (sem idú hry nahrané
 * cez web), plus sa prehľadávajú priečinky "RetroArkada" a "Roms" na internom úložisku a USB,
 * ak má aplikácia povolený prístup k súborom.
 */
object Library {
    private lateinit var app: Context
    lateinit var prefs: Prefs
        private set

    val romsDir: File by lazy { File(app.getExternalFilesDir(null) ?: app.filesDir, "roms").apply { mkdirs() } }
    val biosDir: File by lazy { File(app.filesDir, "system").apply { mkdirs() } }
    private val savesRoot: File by lazy { File(app.filesDir, "saves").apply { mkdirs() } }
    private val statesRoot: File by lazy { File(app.filesDir, "states").apply { mkdirs() } }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _games = MutableStateFlow<List<Game>>(emptyList())
    val games: StateFlow<List<Game>> = _games

    private val _favorites = MutableStateFlow<Set<String>>(emptySet())
    val favorites: StateFlow<Set<String>> = _favorites

    private val _recent = MutableStateFlow<List<String>>(emptyList())
    val recent: StateFlow<List<String>> = _recent

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning

    fun init(context: Context) {
        app = context.applicationContext
        prefs = Prefs(app)
        ArcadeIndex.init(app)
        _favorites.value = prefs.favorites
        _recent.value = prefs.recent
        Systems.ALL.forEach { File(romsDir, it.id).mkdirs() }
        refresh()
    }

    /** Konzoly, ktorých jadro je pribalené v APK. */
    val availableSystems: List<GameSystem> by lazy { Systems.ALL.filter { corePath(it) != null } }

    fun corePath(system: GameSystem): String? {
        val f = File(app.applicationInfo.nativeLibraryDir, system.coreLibrary)
        return f.absolutePath.takeIf { f.exists() }
    }

    fun savesDir(system: GameSystem) = File(savesRoot, system.id).apply { mkdirs() }

    fun sramFile(game: Game) = File(savesDir(game.system), "${game.baseName}.srm")

    fun stateFile(game: Game, slot: String) =
        File(File(statesRoot, game.system.id).apply { mkdirs() }, "${game.baseName}.$slot.state")

    fun find(id: String): Game? = _games.value.firstOrNull { it.id == id }

    fun refresh() {
        scope.launch {
            _scanning.value = true
            try {
                _games.value = scan()
            } catch (e: Exception) {
                // Chybný súbor alebo nedostupné USB nesmie zhodiť celú aplikáciu
                Log.e("Library", "Scan failed", e)
            } finally {
                _scanning.value = false
            }
        }
    }

    fun toggleFavorite(game: Game) {
        val set = _favorites.value.toMutableSet()
        if (!set.add(game.id)) set.remove(game.id)
        _favorites.value = set
        prefs.favorites = set
    }

    fun markPlayed(game: Game) {
        val list = listOf(game.id) + _recent.value.filter { it != game.id }
        _recent.value = list.take(15)
        prefs.recent = _recent.value
    }

    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            app.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }

    /** Priečinky mimo aplikácie, kde hľadáme hry (interné úložisko + USB kľúče). */
    fun externalRoots(): List<File> {
        if (!hasStorageAccess()) return emptyList()
        val volumes = mutableListOf(Environment.getExternalStorageDirectory())
        File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.let(volumes::addAll)
        File("/mnt/media_rw").listFiles()?.filter { it.isDirectory }?.let(volumes::addAll)
        return volumes.flatMap { vol ->
            listOf("RetroArkada", "Roms", "ROMs", "roms", "ROMS", "Games", "Hry").map { File(vol, it) }
        }.filter { it.isDirectory && it.canRead() }
            .distinctBy { runCatching { it.canonicalPath }.getOrDefault(it.absolutePath) }
    }

    private fun scan(): List<Game> {
        val available = availableSystems.toSet()
        val result = mutableListOf<Game>()
        for (system in Systems.ALL) {
            if (system !in available) continue
            val dir = File(romsDir, system.id)
            result += gamesInFolder(dir, system, managed = true)
        }
        for (root in externalRoots()) {
            importBiosFrom(root)
            scanExternal(root, null, depth = 0, out = result, available = available)
        }
        return result.distinctBy { it.id }.sortedBy { it.title.lowercase() }
    }

    private fun gamesInFolder(dir: File, system: GameSystem, managed: Boolean): List<Game> {
        val files = dir.listFiles()?.filter { it.isFile && !it.name.startsWith(".") && !isBiosFile(it.name) } ?: return emptyList()
        val hasCue = files.any { it.extension.equals("cue", true) }
        val hasM3u = files.any { it.extension.equals("m3u", true) }
        return files.filter { f ->
            val ext = f.extension.lowercase()
            when {
                ext !in system.extensions -> false
                // Pri PlayStation zobraz len .m3u / .cue, nie jednotlivé stopy .bin
                system.id == "psx" && ext in setOf("bin", "img") -> !hasCue && !hasM3u
                system.id == "psx" && ext == "cue" -> !hasM3u
                else -> true
            }
        }.mapNotNull { f -> if (system.id == "arcade") arcadeGame(f, system, managed) else Game(f, system, managed) }
    }

    // ------------------------------------------------------------------ arkádové sety

    private val arcadeChecked: MutableMap<String, Boolean> = Collections.synchronizedMap(HashMap())

    /** Arkádová hra zo zipu; zip so zlým názvom sa premenuje, BIOS sety (neogeo…) sa nezobrazia. */
    private fun arcadeGame(zip: File, system: GameSystem, managed: Boolean): Game? {
        val file = fixArcadeName(zip)
        if (ArcadeIndex.isBios(file.nameWithoutExtension)) return null
        return Game(file, system, managed)
    }

    /**
     * FBNeo spozná hru len podľa názvu zipu. Ak názov nepozná, nájdeme set podľa obsahu
     * a zip premenujeme ("Metal Slug (World).zip" -> "mslug.zip"). Vráti (možno nový) súbor.
     */
    private fun fixArcadeName(zip: File): File {
        if (ArcadeIndex.isKnown(zip.nameWithoutExtension)) return zip
        val key = "${zip.absolutePath}|${zip.lastModified()}|${zip.length()}"
        if (arcadeChecked[key] == false) return zip
        val set = ArcadeIndex.identify(zip)
        val target = set?.let { File(zip.parentFile, "$it.zip") }
        if (target == null || target.exists() || !zip.renameTo(target)) {
            arcadeChecked[key] = false
            return zip
        }
        Log.i("Library", "Arkádový set ${zip.name} premenovaný na ${target.name}")
        prefs.setArcadeAlias(zip.name, target.length(), target.name)
        return target
    }

    private fun scanExternal(dir: File, folderSystem: GameSystem?, depth: Int, out: MutableList<Game>, available: Set<GameSystem>) {
        if (depth > 3) return
        val system = Systems.byFolder(dir.name)?.takeIf { it in available } ?: folderSystem
        val zips = dir.listFiles()?.filter { it.isFile && it.extension.equals("zip", true) }.orEmpty()
        val arcade = Systems.byId("arcade")?.takeIf { it in available }
        if (system != null) {
            out += gamesInFolder(dir, system, managed = false)
            if (system.id != "arcade") {
                zips.forEach { zip -> zipEntry(zip, system)?.let { out += Game(zip, it.first, managed = false, archiveEntry = it.second) } }
            }
        } else {
            dir.listFiles()?.filter { it.isFile }?.forEach { f ->
                val s = Systems.byUniqueExtension(f.extension)
                if (s != null && s in available) out += Game(f, s, managed = false)
            }
            // Zip bez priečinka konzoly: hra pre konzolu vo vnútri, inak arkádový ROM set
            zips.forEach { zip ->
                val found = zipEntry(zip, null)?.takeIf { it.first in available }
                when {
                    found != null -> out += Game(zip, found.first, managed = false, archiveEntry = found.second)
                    arcade != null -> arcadeGame(zip, arcade, managed = false)?.let { out += it }
                }
            }
        }
        dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach {
            scanExternal(it, system, depth + 1, out, available)
        }
    }

    private val ps1Bios = Regex("(?i)^(scph\\d+[a-z]?|psxonpsp\\d+|ps1_rom|openbios)\\.bin$")
    private val knownBios =
        setOf("disksys.rom", "gba_bios.bin", "gb_bios.bin", "gbc_bios.bin", "syscard3.pce", "syscard2.pce", "syscard1.pce")

    private fun isBiosFile(name: String) = ps1Bios.matches(name) || name.lowercase() in knownBios

    /** Nájdený PS1 BIOS (napr. scph5501.bin) v systémovom priečinku emulátorov, alebo null. */
    fun ps1BiosName(): String? = biosDir.listFiles()?.map { it.name }?.filter { ps1Bios.matches(it) }?.minOrNull()

    /**
     * BIOS súbory z USB sa skopírujú do systémového priečinka emulátorov: celý priečinok Roms/bios
     * a známe BIOS súbory (scph5501.bin…) kdekoľvek v Roms a jeho podpriečinkoch.
     */
    private fun importBiosFrom(root: File) {
        val found = mutableListOf<File>()
        listOf("bios", "BIOS", "Bios", "system").map { File(root, it) }.filter { it.isDirectory }.forEach { dir ->
            dir.listFiles()?.filter { it.isFile }?.let(found::addAll)
        }
        root.walkTopDown().maxDepth(2).filter { it.isFile && isBiosFile(it.name) }.forEach(found::add)
        found.filter { it.length() in 1..64L * 1024 * 1024 }.forEach { f ->
            // Názov malými písmenami – emulátory hľadajú napr. presne "scph5501.bin"
            val target = File(biosDir, if (isBiosFile(f.name)) f.name.lowercase() else f.name)
            if (!target.exists() || target.length() != f.length()) {
                runCatching { f.copyTo(target, overwrite = true) }
            }
        }
    }

    // ------------------------------------------------------------------ hry v zipe

    // HashMap (nie ConcurrentHashMap), lebo musí vedieť uložiť aj null = "v zipe nie je hra"
    private val zipIndex: MutableMap<String, Pair<GameSystem, String>?> = Collections.synchronizedMap(HashMap())
    private val preferredEntries = listOf("m3u", "cue", "chd", "pbp")

    /** Nájde v zipe hru pre [hint] (alebo pre konzolu s jednoznačnou príponou). Výsledok sa pamätá. */
    private fun zipEntry(zip: File, hint: GameSystem?): Pair<GameSystem, String>? {
        val key = "${zip.absolutePath}|${zip.lastModified()}|${hint?.id}"
        if (zipIndex.containsKey(key)) return zipIndex[key]
        val result =
            runCatching {
                ZipFile(zip).use { zf ->
                    zf.entries().toList()
                        .filter { !it.isDirectory }
                        .mapNotNull { e ->
                            val ext = e.name.substringAfterLast('.', "").lowercase()
                            val s = if (hint != null) hint.takeIf { ext in it.extensions } else Systems.byUniqueExtension(ext)
                            s?.let { Triple(it, e.name, ext) }
                        }
                        .minByOrNull { (_, _, ext) -> preferredEntries.indexOf(ext).let { if (it < 0) 99 else it } }
                        ?.let { (s, name, _) -> s to name }
                }
            }.getOrNull()
        zipIndex[key] = result
        return result
    }

    /**
     * Vráti súbor, ktorý sa má načítať do emulátora. Hru zo zipu najprv rozbalí do dočasného
     * priečinka; drží sa len pár naposledy hraných, aby nezaberali miesto.
     */
    fun prepareRom(game: Game): File {
        val entry = game.archiveEntry ?: return game.file
        val root = File(app.getExternalFilesDir(null) ?: app.filesDir, "unzipped").apply { mkdirs() }
        val dir = File(root, "${Integer.toHexString(game.id.hashCode())}_${game.file.lastModified()}")
        val done = File(dir, ".done")
        if (!done.exists()) {
            dir.deleteRecursively()
            dir.mkdirs()
            ZipFile(game.file).use { zf ->
                zf.entries().toList().filter { !it.isDirectory }.forEach { e ->
                    File(dir, File(e.name).name).outputStream().use { out -> zf.getInputStream(e).use { it.copyTo(out) } }
                }
            }
            done.createNewFile()
        }
        dir.setLastModified(System.currentTimeMillis())
        root.listFiles()?.filter { it.isDirectory }?.sortedByDescending { it.lastModified() }?.drop(3)?.forEach { it.deleteRecursively() }
        return File(dir, File(entry).name)
    }

    // ------------------------------------------------------------------ import

    class ImportResult(val ok: Boolean, val message: String, val system: String? = null)

    /**
     * Zaradí nahraný súbor do knižnice. [systemHint] je "auto", "bios" alebo id konzoly.
     * Zip s konzolovou hrou sa rozbalí, inak sa berie ako arkádový ROM set (FBNeo).
     */
    fun import(temp: File, originalName: String, systemHint: String, toUsb: Boolean = false): ImportResult {
        val name = safeName(originalName)
        val ext = name.substringAfterLast('.', "").lowercase()
        try {
            if (systemHint.equals("bios", true)) {
                moveFile(temp, File(biosDir, name))
                return ImportResult(true, "BIOS $name uložený", "bios")
            }
            val base =
                if (toUsb) {
                    usbRomsDir() ?: return ImportResult(false, "USB kľúč nie je pripojený alebo sa naň nedá zapisovať")
                } else {
                    romsDir
                }
            // Konzola podľa voľby, alebo podľa názvu priečinka (napr. "genesis" z USB skriptu)
            val hinted = Systems.byId(systemHint) ?: Systems.byFolder(systemHint)
            var zipOnUsb = false
            if (ext == "zip" && hinted?.id != "arcade") {
                if (toUsb) {
                    // Na USB necháme zip tak – appka ho rozbalí až pri spustení
                    zipOnUsb = true
                } else {
                    val extracted = tryExtractConsoleZip(temp, hinted)
                    if (extracted != null) {
                        temp.delete()
                        refresh()
                        return extracted
                    }
                    if (hinted != null) return ImportResult(false, "V zipe som nenašiel hru pre ${hinted.name}")
                }
            }
            val system =
                hinted ?: (if (zipOnUsb) zipEntry(temp, null)?.first else null) ?: Systems.byUniqueExtension(ext) ?: when (ext) {
                    "zip" -> Systems.byId("arcade")
                    "bin" -> Systems.byId("megadrive")
                    "iso", "img" -> Systems.byId("psx")
                    else -> null
                } ?: return ImportResult(false, "Neznámy formát .$ext – vyber konzolu ručne")
            if (ext !in system.extensions && !zipOnUsb) {
                return ImportResult(false, "${system.name} nepodporuje súbory .$ext")
            }
            var finalName = name
            if (system.id == "arcade" && !ArcadeIndex.isKnown(name.substringBeforeLast('.'))) {
                // Zlý názov arkádového setu: FBNeo by hlásil "Romset unknown", tak ho opravíme
                ArcadeIndex.identify(temp)?.let {
                    finalName = "$it.zip"
                    prefs.setArcadeAlias(name, temp.length(), finalName)
                }
            }
            moveFile(temp, File(File(base, system.id).apply { mkdirs() }, finalName))
            refresh()
            val where = if (toUsb) " (USB)" else ""
            val renamed = if (finalName != name) " (premenované na $finalName)" else ""
            return ImportResult(true, "${Game.prettyTitle(finalName.substringBeforeLast('.'), system)} → ${system.name}$where$renamed", system.id)
        } finally {
            temp.delete()
        }
    }

    private fun safeName(originalName: String) = File(originalName).name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "hra" }

    /**
     * Priečinok Roms na USB kľúči, kam sa dá zapisovať. Na RPi je kľúč často len v
     * /mnt/media_rw (nie v /storage), kam adb nemá prístup – appka s povolením k úložisku áno.
     */
    fun usbRomsDir(): File? {
        if (!hasStorageAccess()) return null
        val candidates = mutableListOf<File>()
        File("/storage").listFiles()?.filter { it.isDirectory && it.name != "emulated" && it.name != "self" }?.let(candidates::addAll)
        File("/mnt/media_rw").listFiles()?.filter { it.isDirectory }?.let(candidates::addAll)
        return candidates.firstOrNull { it.canWrite() }?.let { File(it, "Roms") }
    }

    /** Je už súbor s týmto názvom a veľkosťou v knižnici? (aby skript neposielal hry znova) */
    fun hasFile(originalName: String, size: Long, toUsb: Boolean): Boolean {
        val base = (if (toUsb) usbRomsDir() else romsDir) ?: return false
        val names = listOfNotNull(safeName(originalName), prefs.arcadeAlias(safeName(originalName), size))
        return base.listFiles()?.any { dir -> names.any { n -> File(dir, n).let { it.isFile && it.length() == size } } } == true
    }

    private fun tryExtractConsoleZip(zip: File, hinted: GameSystem?): ImportResult? =
        ZipFile(zip).use { zf ->
            val entries = zf.entries().toList().filter { !it.isDirectory }
            val main =
                entries.firstNotNullOfOrNull { e ->
                    val ext = e.name.substringAfterLast('.', "").lowercase()
                    val s = if (hinted != null) hinted.takeIf { ext in it.extensions } else Systems.byUniqueExtension(ext)
                    s?.let { e to it }
                } ?: return@use null
            val system = main.second
            val target = File(romsDir, system.id).apply { mkdirs() }
            for (e in entries) {
                val out = File(target, File(e.name).name)
                zf.getInputStream(e).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
            val title = Game.prettyTitle(File(main.first.name).nameWithoutExtension, system)
            ImportResult(true, "$title → ${system.name}", system.id)
        }

    private fun moveFile(from: File, to: File) {
        to.parentFile?.mkdirs()
        if (!from.renameTo(to)) {
            from.copyTo(to, overwrite = true)
            from.delete()
        }
    }

    fun delete(game: Game): Boolean {
        if (!game.managed) return false
        val dir = game.file.parentFile
        // Pri .cue zmaž aj stopy, na ktoré odkazuje
        if (game.file.extension.equals("cue", true)) {
            Regex("FILE\\s+\"([^\"]+)\"").findAll(game.file.readText()).forEach { File(dir, it.groupValues[1]).delete() }
        }
        val ok = game.file.delete()
        _favorites.value = _favorites.value - game.id
        prefs.favorites = _favorites.value
        refresh()
        return ok
    }
}
