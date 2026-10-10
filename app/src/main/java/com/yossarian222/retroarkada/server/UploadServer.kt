package com.yossarian222.retroarkada.server

import android.content.Context
import android.util.Log
import com.yossarian222.retroarkada.data.Library
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.IHTTPSession
import fi.iki.elonen.NanoHTTPD.Method
import fi.iki.elonen.NanoHTTPD.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Malý webový server bežiaci na TV. Z mobilu alebo PC v tej istej sieti otvoríš
 * http://<ip-tv>:8080 a hry tam jednoducho pretiahneš.
 */
class UploadServer private constructor(private val context: Context, port: Int) : NanoHTTPD(port) {
    override fun serve(session: IHTTPSession): Response =
        try {
            route(session)
        } catch (e: Exception) {
            Log.e(TAG, "Request failed", e)
            json(Response.Status.INTERNAL_ERROR, JSONObject().put("ok", false).put("message", e.message ?: "Chyba"))
        }

    private fun route(session: IHTTPSession): Response {
        val params = session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        return when {
            session.uri == "/" || session.uri == "/index.html" -> {
                val html = context.assets.open("web/index.html").bufferedReader().use { it.readText() }
                newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", html)
            }
            session.uri == "/api/info" -> json(Response.Status.OK, info())
            session.uri == "/api/upload" && (session.method == Method.PUT || session.method == Method.POST) ->
                upload(session, params["name"].orEmpty(), params["system"] ?: "auto", params["dest"] == "usb")
            session.uri == "/api/has" -> {
                val exists = Library.hasFile(params["name"].orEmpty(), params["size"]?.toLongOrNull() ?: -1, params["dest"] == "usb")
                json(Response.Status.OK, JSONObject().put("exists", exists))
            }
            session.uri == "/api/delete" && session.method == Method.POST -> {
                val game = Library.find(params["id"].orEmpty())
                val ok = game != null && Library.delete(game)
                json(Response.Status.OK, JSONObject().put("ok", ok))
            }
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Nenájdené")
        }
    }

    private fun upload(session: IHTTPSession, name: String, system: String, toUsb: Boolean): Response {
        if (name.isBlank()) return json(Response.Status.BAD_REQUEST, JSONObject().put("ok", false).put("message", "Chýba názov"))
        val length = session.headers["content-length"]?.toLongOrNull()
            ?: return json(Response.Status.BAD_REQUEST, JSONObject().put("ok", false).put("message", "Chýba dĺžka"))
        // Pri nahrávaní na USB ukladáme dočasný súbor rovno na USB (šetrí miesto na systémovej karte)
        val targetBase =
            if (toUsb) {
                Library.usbRomsDir()
                    ?: return json(Response.Status.OK, JSONObject().put("ok", false).put("message", "USB kľúč nie je pripojený alebo sa naň nedá zapisovať"))
            } else {
                Library.romsDir
            }
        val incoming = File(targetBase, ".incoming").apply { mkdirs() }
        val temp = File.createTempFile("upload", ".part", incoming)
        try {
            val input = session.inputStream
            temp.outputStream().buffered(1 shl 16).use { out ->
                val buf = ByteArray(1 shl 16)
                var remaining = length
                while (remaining > 0) {
                    val n = input.read(buf, 0, minOf(buf.size.toLong(), remaining).toInt())
                    if (n < 0) break
                    out.write(buf, 0, n)
                    remaining -= n
                }
                if (remaining > 0) error("Prenos sa prerušil")
            }
            val result = Library.import(temp, name, system, toUsb)
            return json(Response.Status.OK, JSONObject().put("ok", result.ok).put("message", result.message))
        } finally {
            temp.delete()
        }
    }

    private fun info(): JSONObject {
        val systems = JSONArray()
        Library.availableSystems.forEach { systems.put(JSONObject().put("id", it.id).put("name", it.fullName)) }
        val games = JSONArray()
        Library.games.value.forEach {
            games.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("file", it.file.name)
                    .put("system", it.system.name)
                    .put("size", it.file.length())
                    .put("deletable", it.managed),
            )
        }
        return JSONObject().put("systems", systems).put("games", games).put("usb", Library.usbRomsDir() != null)
    }

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString())

    companion object {
        private const val TAG = "UploadServer"
        private var instance: UploadServer? = null

        val port: Int? get() = instance?.listeningPort

        fun start(context: Context) {
            if (instance != null) return
            for (p in listOf(8080, 8088, 8181, 9090)) {
                try {
                    val server = UploadServer(context.applicationContext, p)
                    server.start(120_000, true)
                    instance = server
                    Log.i(TAG, "Listening on $p")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "Port $p unavailable", e)
                }
            }
        }

        /** Adresa, ktorú treba otvoriť v prehliadači, napr. http://192.168.1.20:8080 */
        fun address(): String? {
            val p = port ?: return null
            val ip =
                runCatching {
                    NetworkInterface.getNetworkInterfaces().toList()
                        .filter { it.isUp && !it.isLoopback }
                        .sortedBy { if (it.name.startsWith("eth")) 0 else if (it.name.startsWith("wlan")) 1 else 2 }
                        .flatMap { it.inetAddresses.toList() }
                        .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
                        ?.hostAddress
                }.getOrNull() ?: return null
            return "http://$ip:$p"
        }
    }
}
