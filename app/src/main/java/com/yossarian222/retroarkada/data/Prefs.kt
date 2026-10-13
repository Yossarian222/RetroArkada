package com.yossarian222.retroarkada.data

import android.content.Context
import com.swordfish.libretrodroid.ShaderConfig
import org.json.JSONArray

enum class VideoFilter(val label: String, val shader: ShaderConfig) {
    SHARP("Ostrý (pixely)", ShaderConfig.Sharp),
    CRT("CRT televízor", ShaderConfig.CRT),
    LCD("LCD mriežka", ShaderConfig.LCD),
    SMOOTH("Vyhladený", ShaderConfig.CUT2()),
    RAW("Pôvodný", ShaderConfig.Default),
    ;

    fun next(): VideoFilter = entries[(ordinal + 1) % entries.size]
}

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("arkada", Context.MODE_PRIVATE)

    var videoFilter: VideoFilter
        get() = runCatching { VideoFilter.valueOf(sp.getString("filter", null)!!) }.getOrDefault(VideoFilter.SHARP)
        set(value) = sp.edit().putString("filter", value.name).apply()

    /** Nízka odozva zvuku = menšia vyrovnávacia pamäť; na RPi cez HDMI potom zvuk praská. */
    var lowLatencyAudio: Boolean
        get() = sp.getBoolean("low_latency_audio", false)
        set(value) = sp.edit().putBoolean("low_latency_audio", value).apply()

    /** PS1 bez dynarecu – pomalšie, ale niektoré hry potom nezamŕzajú (videá, scény). */
    var ps1Compat: Boolean
        get() = sp.getBoolean("ps1_compat", false)
        set(value) = sp.edit().putBoolean("ps1_compat", value).apply()

    var favorites: Set<String>
        get() = sp.getStringSet("favorites", emptySet())!!.toSet()
        set(value) = sp.edit().putStringSet("favorites", value).apply()

    /** Premenované arkádové zipy: "pôvodný názov|veľkosť" -> nový názov (aby ich skript neposielal znova). */
    fun arcadeAlias(originalName: String, size: Long): String? = sp.getString("alias:$originalName|$size", null)

    fun setArcadeAlias(originalName: String, size: Long, newName: String) =
        sp.edit().putString("alias:$originalName|$size", newName).apply()

    var recent: List<String>
        get() {
            val arr = runCatching { JSONArray(sp.getString("recent", "[]")) }.getOrElse { JSONArray() }
            return List(arr.length()) { arr.getString(it) }
        }
        set(value) = sp.edit().putString("recent", JSONArray(value).toString()).apply()
}
