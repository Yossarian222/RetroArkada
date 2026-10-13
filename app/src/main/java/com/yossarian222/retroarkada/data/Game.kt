package com.yossarian222.retroarkada.data

import android.net.Uri
import java.io.File

data class Game(
    val file: File,
    val system: GameSystem,
    /** Hra je v internom úložisku aplikácie (dá sa zmazať z aplikácie). */
    val managed: Boolean,
    /** Ak je hra zabalená v zipe (napr. na USB), názov súboru hry vo vnútri zipu. */
    val archiveEntry: String? = null,
) {
    val id: String get() = file.absolutePath
    val baseName: String get() = archiveEntry?.let { File(it).nameWithoutExtension } ?: file.nameWithoutExtension
    val title: String = prettyTitle(baseName, system)

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
            // Arkádové sety majú len skratku (mslug) – celý názov je v databáze FBNeo
            val name = if (system.id == "arcade") ArcadeIndex.description(fileName) ?: fileName else fileName
            var t = name.replace('_', ' ').replace(tags, "").trim()
            // "Legend of Zelda, The" -> "The Legend of Zelda"
            Regex("^(.*), (The|A|An|Die|Der|Das|Le|La|Les)( - .*)?$").find(t)?.let { m ->
                t = "${m.groupValues[2]} ${m.groupValues[1]}${m.groupValues[3]}"
            }
            return t.ifBlank { fileName }
        }
    }
}
