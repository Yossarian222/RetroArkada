package com.yossarian222.retroarkada.data

import android.net.Uri
import java.io.File

data class Game(
    val file: File,
    val system: GameSystem,
    /** Hra je v internom úložisku aplikácie (dá sa zmazať z aplikácie). */
    val managed: Boolean,
) {
    val id: String get() = file.absolutePath
    val baseName: String get() = file.nameWithoutExtension
    val title: String = prettyTitle(file.nameWithoutExtension, system)

    /** Obal hry z libretro-thumbnails, ak názov súboru zodpovedá No-Intro / Redump názvu. */
    val coverUrl: String?
        get() {
            val db = system.thumbnailDb ?: return null
            val name = baseName.replace(Regex("[&*/:`<>?\\\\|\"]"), "_")
            return "https://thumbnails.libretro.com/${Uri.encode(db)}/Named_Boxarts/${Uri.encode(name)}.png"
        }

    companion object {
        private val tags = Regex("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]")

        fun prettyTitle(fileName: String, system: GameSystem): String {
            if (system.id == "arcade") {
                ArcadeNames.lookup(fileName)?.let { return it }
            }
            var t = fileName.replace('_', ' ').replace(tags, "").trim()
            // "Legend of Zelda, The" -> "The Legend of Zelda"
            Regex("^(.*), (The|A|An|Die|Der|Das|Le|La|Les)( - .*)?$").find(t)?.let { m ->
                t = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
            }
            return t.ifBlank { fileName }
        }
    }
}

/** Pár najznámejších arkádových hier, ktoré majú v ROM sete len skratku. */
object ArcadeNames {
    private val names =
        mapOf(
            "sf2" to "Street Fighter II",
            "sf2ce" to "Street Fighter II' Champion Edition",
            "sf2hf" to "Street Fighter II' Hyper Fighting",
            "ssf2t" to "Super Street Fighter II Turbo",
            "sfa3" to "Street Fighter Alpha 3",
            "mslug" to "Metal Slug",
            "mslug2" to "Metal Slug 2",
            "mslug3" to "Metal Slug 3",
            "mslugx" to "Metal Slug X",
            "kof98" to "The King of Fighters '98",
            "kof2002" to "The King of Fighters 2002",
            "samsho" to "Samurai Shodown",
            "garou" to "Garou: Mark of the Wolves",
            "pacman" to "Pac-Man",
            "mspacman" to "Ms. Pac-Man",
            "galaga" to "Galaga",
            "dkong" to "Donkey Kong",
            "invaders" to "Space Invaders",
            "1942" to "1942",
            "1943" to "1943: The Battle of Midway",
            "ffight" to "Final Fight",
            "dino" to "Cadillacs and Dinosaurs",
            "punisher" to "The Punisher",
            "tmnt" to "Teenage Mutant Ninja Turtles",
            "tmnt2" to "TMNT: Turtles in Time",
            "simpsons" to "The Simpsons",
            "xmen" to "X-Men",
            "ddragon" to "Double Dragon",
            "bublbobl" to "Bubble Bobble",
            "outrun" to "Out Run",
            "gng" to "Ghosts'n Goblins",
            "ghouls" to "Ghouls'n Ghosts",
            "wboy" to "Wonder Boy",
            "rtype" to "R-Type",
            "pbobble" to "Puzzle Bobble",
            "neobombe" to "Neo Bomberman",
            "blazstar" to "Blazing Star",
            "aof" to "Art of Fighting",
            "fatfury1" to "Fatal Fury",
            "rbff2" to "Real Bout Fatal Fury 2",
            "kof97" to "The King of Fighters '97",
            "lastblad" to "The Last Blade",
            "shocktro" to "Shock Troopers",
            "wjammers" to "Windjammers",
            "captcomm" to "Captain Commando",
            "1941" to "1941: Counter Attack",
            "commando" to "Commando",
            "contra" to "Contra",
            "frogger" to "Frogger",
            "digdug" to "Dig Dug",
            "asteroid" to "Asteroids",
            "mk" to "Mortal Kombat",
            "mk2" to "Mortal Kombat II",
            "nbajam" to "NBA Jam",
        )

    fun lookup(romName: String): String? = names[romName.lowercase()]
}
