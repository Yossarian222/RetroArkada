package com.yossarian222.retroarkada.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * Databáza arkádových ROM setov FBNeo (assets/fbneo.idx.gz, generuje ju CI z oficiálneho DAT).
 *
 * FBNeo spozná hru iba podľa názvu zipu ("mslug.zip"), takže "Metal Slug (World).zip" hlási
 * "Romset unknown". Podľa CRC súborov vo vnútri zipu tu nájdeme správny názov setu.
 */
object ArcadeIndex {
    private class Entry(
        val name: String,
        val parent: String?,
        val bios: Boolean,
        val description: String,
        val own: IntArray,
        val shared: IntArray,
    )

    private lateinit var app: Context

    fun init(context: Context) {
        app = context.applicationContext
    }

    private val entries: Map<String, Entry> by lazy { load() }

    /** CRC -> sety, v ktorých sa súbor nachádza (vlastný alebo zdieľaný s rodičom / BIOSom). */
    private val byCrc: Map<Int, List<Entry>> by lazy {
        val map = HashMap<Int, MutableList<Entry>>()
        for (e in entries.values) {
            e.own.forEach { map.getOrPut(it) { mutableListOf() } += e }
            e.shared.forEach { map.getOrPut(it) { mutableListOf() } += e }
        }
        map
    }

    private fun load(): Map<String, Entry> =
        runCatching {
            val map = HashMap<String, Entry>(10000)
            GZIPInputStream(app.assets.open("fbneo.idx.gz")).bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val p = line.split('\t')
                    if (p.size < 6) return@forEach
                    map[p[0]] = Entry(p[0], p[1].ifEmpty { null }, p[2] == "1", p[3], crcs(p[4]), crcs(p[5]))
                }
            }
            map
        }.getOrElse {
            Log.e("ArcadeIndex", "Databázu arkádových hier sa nepodarilo načítať", it)
            emptyMap()
        }

    private fun crcs(s: String): IntArray =
        if (s.isEmpty()) IntArray(0) else s.split(',').map { it.toLong(16).toInt() }.toIntArray()

    /** Pozná FBNeo zip s týmto názvom (bez prípony)? */
    fun isKnown(setName: String) = setName.lowercase() in entries

    /** BIOS set (neogeo, pgm…) – nie je to hra, do knižnice nepatrí. */
    fun isBios(setName: String) = entries[setName.lowercase()]?.bios == true

    /** Celý názov hry, napr. "mslug" -> "Metal Slug - Super Vehicle-001". */
    fun description(setName: String): String? = entries[setName.lowercase()]?.description?.ifBlank { null }

    /**
     * Názov FBNeo setu podľa obsahu zipu, alebo null, ak hru nepoznáme. Zvládne sety
     * „non-merged“ (všetko v jednom zipe) aj „split“ (klon má len svoje súbory).
     */
    fun identify(zip: File): String? {
        val crcs =
            runCatching {
                ZipFile(zip).use { zf ->
                    zf.entries().toList().filter { !it.isDirectory && it.crc != -1L }.map { it.crc.toInt() }.toSet()
                }
            }.getOrNull()
        if (crcs.isNullOrEmpty()) return null

        val hits = HashMap<Entry, Int>()
        for (crc in crcs) byCrc[crc]?.forEach { hits[it] = (hits[it] ?: 0) + 1 }
        val best =
            hits.entries.maxWithOrNull(
                compareBy<Map.Entry<Entry, Int>> { it.value }
                    // Rovnaký počet zhôd: vyhráva set, ktorému chýba najmenej vlastných súborov…
                    .thenByDescending { e -> e.key.own.count { it !in crcs } }
                    // …potom pôvodná verzia hry pred klonom
                    .thenByDescending { it.key.parent != null },
            ) ?: return null

        val matched = best.value
        val own = best.key.own.size
        // Väčšina súborov v zipe musí patriť hre a zip musí mať aspoň polovicu jej vlastných súborov
        return best.key.name.takeIf { matched * 2 > crcs.size && matched * 2 >= own }
    }
}
