package com.yossarian222.retroarkada.data

import com.swordfish.libretrodroid.Variable

/** Jedna herná konzola a libretro jadro, ktoré ju emuluje. */
data class GameSystem(
    val id: String,
    val name: String,
    val maker: String,
    val year: Int,
    val color: Long,
    val core: String,
    /** Prípony súborov, ktoré jadro načíta. */
    val extensions: Set<String>,
    /** Prípony, podľa ktorých sa dá konzola určiť jednoznačne (bez priečinka). */
    val uniqueExtensions: Set<String> = extensions,
    /** Názvy priečinkov (malými písmenami), ktoré patria tejto konzole. */
    val folderAliases: Set<String> = setOf(id),
    /** Názov databázy na thumbnails.libretro.com (obaly hier). */
    val thumbnailDb: String? = null,
    val variables: List<Variable> = emptyList(),
    /** Ľavá páčka ovládača funguje aj ako šípky (konzoly bez analógu). */
    val stickAsDpad: Boolean = true,
) {
    val coreLibrary: String get() = "lib${core}_libretro_android.so"
}

object Systems {
    val ALL: List<GameSystem> =
        listOf(
            GameSystem(
                id = "nes",
                name = "NES",
                maker = "Nintendo",
                year = 1983,
                color = 0xFFE53935,
                core = "fceumm",
                extensions = setOf("nes", "fds", "unf", "unif"),
                folderAliases = setOf("nes", "famicom", "fc", "fds"),
                thumbnailDb = "Nintendo - Nintendo Entertainment System",
            ),
            GameSystem(
                id = "snes",
                name = "Super Nintendo",
                maker = "Nintendo",
                year = 1990,
                color = 0xFF7E57C2,
                core = "snes9x",
                extensions = setOf("sfc", "smc"),
                folderAliases = setOf("snes", "sfc", "superfamicom", "supernintendo"),
                thumbnailDb = "Nintendo - Super Nintendo Entertainment System",
            ),
            GameSystem(
                id = "n64",
                name = "Nintendo 64",
                maker = "Nintendo",
                year = 1996,
                color = 0xFF00A86B,
                core = "mupen64plus_next_gles3",
                extensions = setOf("n64", "z64", "v64"),
                folderAliases = setOf("n64", "nintendo64"),
                thumbnailDb = "Nintendo - Nintendo 64",
                variables =
                    listOf(
                        Variable("mupen64plus-43screensize", "640x480"),
                        Variable("mupen64plus-FrameDuping", "True"),
                    ),
                stickAsDpad = false,
            ),
            GameSystem(
                id = "gb",
                name = "Game Boy",
                maker = "Nintendo",
                year = 1989,
                color = 0xFF8BAC0F,
                core = "gambatte",
                extensions = setOf("gb"),
                folderAliases = setOf("gb", "gameboy"),
                thumbnailDb = "Nintendo - Game Boy",
                variables = listOf(Variable("gambatte_gb_colorization", "internal")),
            ),
            GameSystem(
                id = "gbc",
                name = "Game Boy Color",
                maker = "Nintendo",
                year = 1998,
                color = 0xFF26A69A,
                core = "gambatte",
                extensions = setOf("gbc"),
                folderAliases = setOf("gbc", "gameboycolor"),
                thumbnailDb = "Nintendo - Game Boy Color",
            ),
            GameSystem(
                id = "gba",
                name = "Game Boy Advance",
                maker = "Nintendo",
                year = 2001,
                color = 0xFF5C6BC0,
                core = "mgba",
                extensions = setOf("gba"),
                folderAliases = setOf("gba", "gameboyadvance"),
                thumbnailDb = "Nintendo - Game Boy Advance",
            ),
            GameSystem(
                id = "megadrive",
                name = "Mega Drive",
                maker = "Sega",
                year = 1988,
                color = 0xFF1E88E5,
                core = "genesis_plus_gx",
                extensions = setOf("md", "gen", "smd", "bin", "68k", "sgd"),
                uniqueExtensions = setOf("md", "gen", "smd", "68k", "sgd"),
                folderAliases = setOf("megadrive", "genesis", "md", "segagenesis"),
                thumbnailDb = "Sega - Mega Drive - Genesis",
            ),
            GameSystem(
                id = "mastersystem",
                name = "Master System",
                maker = "Sega",
                year = 1985,
                color = 0xFFC62828,
                core = "genesis_plus_gx",
                extensions = setOf("sms"),
                folderAliases = setOf("mastersystem", "sms"),
                thumbnailDb = "Sega - Master System - Mark III",
            ),
            GameSystem(
                id = "gamegear",
                name = "Game Gear",
                maker = "Sega",
                year = 1990,
                color = 0xFF546E7A,
                core = "genesis_plus_gx",
                extensions = setOf("gg"),
                folderAliases = setOf("gamegear", "gg"),
                thumbnailDb = "Sega - Game Gear",
            ),
            GameSystem(
                id = "psx",
                name = "PlayStation",
                maker = "Sony",
                year = 1994,
                color = 0xFF3949AB,
                core = "pcsx_rearmed",
                extensions = setOf("cue", "chd", "pbp", "m3u", "iso", "img", "bin"),
                uniqueExtensions = setOf("cue", "chd", "pbp", "m3u"),
                folderAliases = setOf("psx", "ps1", "playstation", "psone"),
                thumbnailDb = "Sony - PlayStation",
            ),
            GameSystem(
                id = "pce",
                name = "PC Engine",
                maker = "NEC",
                year = 1987,
                color = 0xFFFB8C00,
                core = "mednafen_pce_fast",
                extensions = setOf("pce"),
                folderAliases = setOf("pce", "pcengine", "tg16", "turbografx", "turbografx16"),
                thumbnailDb = "NEC - PC Engine - TurboGrafx 16",
            ),
            GameSystem(
                id = "atari2600",
                name = "Atari 2600",
                maker = "Atari",
                year = 1977,
                color = 0xFFA1673B,
                core = "stella",
                extensions = setOf("a26"),
                folderAliases = setOf("atari2600", "atari", "a26", "2600"),
                thumbnailDb = "Atari - 2600",
            ),
            GameSystem(
                id = "arcade",
                name = "Arcade",
                maker = "FinalBurn Neo",
                year = 1980,
                color = 0xFFFFB300,
                core = "fbneo",
                extensions = setOf("zip"),
                uniqueExtensions = emptySet(),
                folderAliases = setOf("arcade", "fbneo", "fba", "mame", "neogeo"),
            ),
        )

    private val byId = ALL.associateBy { it.id }

    fun byId(id: String): GameSystem? = byId[id]

    fun byFolder(folder: String): GameSystem? {
        val key = folder.lowercase().filter { it.isLetterOrDigit() }
        return ALL.firstOrNull { key in it.folderAliases }
    }

    fun byUniqueExtension(ext: String): GameSystem? = ALL.firstOrNull { ext.lowercase() in it.uniqueExtensions }
}
