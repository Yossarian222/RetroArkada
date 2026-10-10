package com.yossarian222.retroarkada.data

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aktualizácia priamo z aplikácie. CI ku každému buildu zverejní v release „latest“ súbor
 * version.json; ak je tam vyššie číslo verzie, stiahne sa nové APK a nainštaluje cez
 * PackageInstaller (Android sa len spýta na potvrdenie).
 */
object Updater {
    private const val BASE = "https://github.com/Yossarian222/RetroArkada/releases/download/latest"
    private const val TAG = "Updater"

    sealed interface State {
        data object Idle : State

        data object Checking : State

        data object UpToDate : State

        data class Available(val versionName: String) : State

        data class Downloading(val percent: Int) : State

        data object Installing : State

        data class Error(val message: String) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state
    private var checkedOnce = false

    fun currentVersionCode(context: Context): Long =
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        }.getOrDefault(0)

    /** Pri štarte appky sa kontroluje len raz a potichu (bez chybových hlášok). */
    fun checkOnStart(context: Context) {
        if (checkedOnce) return
        checkedOnce = true
        check(context, silent = true)
    }

    fun check(context: Context, silent: Boolean = false) {
        val app = context.applicationContext
        scope.launch {
            _state.value = State.Checking
            try {
                val json = JSONObject(httpGet("$BASE/version.json"))
                val latest = json.getLong("versionCode")
                _state.value =
                    if (latest > currentVersionCode(app)) {
                        State.Available(json.optString("versionName", latest.toString()))
                    } else {
                        State.UpToDate
                    }
            } catch (e: Exception) {
                Log.w(TAG, "Update check failed", e)
                _state.value = if (silent) State.Idle else State.Error("Nepodarilo sa overiť aktualizáciu")
            }
        }
    }

    fun downloadAndInstall(activity: Activity) {
        // Android vyžaduje jednorazové povolenie „Inštalovať neznáme aplikácie“ pre túto appku
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            runCatching {
                activity.startActivity(
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")),
                )
            }
            _state.value = State.Error("Povoľ inštaláciu z tejto aplikácie a skús znova")
            return
        }
        val app = activity.applicationContext
        scope.launch {
            try {
                val apk = File(app.cacheDir, "update.apk")
                download("$BASE/RetroArkada.apk", apk)
                _state.value = State.Installing
                install(app, apk)
            } catch (e: Exception) {
                Log.e(TAG, "Update failed", e)
                _state.value = State.Error("Sťahovanie zlyhalo: ${e.message ?: "neznáma chyba"}")
            }
        }
    }

    internal fun onInstallFailed(message: String?) {
        _state.value = State.Error("Inštalácia zlyhala: ${message ?: "neznáma chyba"}")
    }

    private fun open(url: String): HttpURLConnection {
        var current = url
        // GitHub presmerúva release súbory na iný server – sledujeme presmerovania ručne
        repeat(5) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "RetroArkada")
            val code = conn.responseCode
            if (code in 300..399) {
                current = conn.getHeaderField("Location") ?: error("Chýba presmerovanie")
                conn.disconnect()
            } else {
                if (code != 200) error("HTTP $code")
                return conn
            }
        }
        error("Priveľa presmerovaní")
    }

    private fun httpGet(url: String): String = open(url).inputStream.bufferedReader().use { it.readText() }

    private fun download(url: String, target: File) {
        val conn = open(url)
        val total = conn.contentLengthLong
        var done = 0L
        var lastPercent = -1
        _state.value = State.Downloading(0)
        conn.inputStream.use { input ->
            target.outputStream().use { out ->
                val buf = ByteArray(1 shl 16)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val percent = (done * 100 / total).toInt()
                        if (percent != lastPercent) {
                            lastPercent = percent
                            _state.value = State.Downloading(percent)
                        }
                    }
                }
            }
        }
    }

    private fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(context.packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val pending = PendingIntent.getBroadcast(context, sessionId, Intent(context, UpdateReceiver::class.java), flags)
            session.commit(pending.intentSender)
        }
    }
}

/** Výsledok inštalácie od systému – pri potrebe potvrdenia otvorí systémový dialóg. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { context.startActivity(it) }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> Updater.onInstallFailed(intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
        }
    }
}
