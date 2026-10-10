package com.yossarian222.retroarkada.game

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.GLRetroView.GLRetroEvents
import com.yossarian222.retroarkada.data.Game
import com.yossarian222.retroarkada.data.Library
import kotlinx.coroutines.launch
import java.io.File
import kotlin.concurrent.thread
import kotlin.math.abs

class GameActivity : ComponentActivity() {
    private lateinit var game: Game
    private lateinit var retroView: GLRetroView

    private var autosaveRestored = false
    private var pendingAction: String? = null
    private var fastForward = false

    /** deviceId ovládača -> port (hráč 1..4) */
    private val ports = LinkedHashMap<Int, Int>()
    private val heldButtons = HashMap<Int, MutableSet<Int>>()
    private val triggerState = HashMap<Int, BooleanArray>()

    private val menuLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            pendingAction = result.data?.getStringExtra(MenuActivity.EXTRA_ACTION) ?: MenuActivity.ACTION_RESUME
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val found = intent.getStringExtra(EXTRA_GAME)?.let(Library::find)
        val core = found?.let { Library.corePath(it.system) }
        if (found == null || core == null) {
            Toast.makeText(this, "Hru sa nepodarilo nájsť", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        game = found
        Library.markPlayed(game)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val sram = Library.sramFile(game)
        val data =
            GLRetroViewData(this).apply {
                coreFilePath = core
                gameFilePath = game.file.absolutePath
                systemDirectory = Library.biosDir.absolutePath
                savesDirectory = Library.savesDir(game.system).absolutePath
                variables = game.system.variables.toTypedArray()
                saveRAMState = sram.takeIf { it.exists() }?.readBytes()
                shader = Library.prefs.videoFilter.shader
                rumbleEventsEnabled = true
                preferLowLatencyAudio = true
            }
        retroView = GLRetroView(this, data)
        lifecycle.addObserver(retroView)

        val root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        root.addView(retroView, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        setContentView(root)
        retroView.isFocusable = false

        lifecycleScope.launch {
            retroView.getGLRetroEvents().collect { event ->
                if (event == GLRetroEvents.FrameRendered && !autosaveRestored) {
                    autosaveRestored = true
                    restoreAutosave()
                }
            }
        }
        lifecycleScope.launch {
            retroView.getGLRetroErrors().collect { code ->
                val msg =
                    when (code) {
                        GLRetroView.ERROR_LOAD_LIBRARY -> "Nepodarilo sa načítať emulátor"
                        GLRetroView.ERROR_LOAD_GAME -> "Hru sa nepodarilo spustiť (poškodený súbor alebo chýba BIOS?)"
                        GLRetroView.ERROR_GL_NOT_COMPATIBLE -> "Grafika tohto zariadenia nie je podporovaná"
                        else -> "Chyba emulátora ($code)"
                    }
                Toast.makeText(this@GameActivity, msg, Toast.LENGTH_LONG).show()
                finish()
            }
        }
        lifecycleScope.launch {
            retroView.getRumbleEvents().collect { rumble(it.port, maxOf(it.strengthWeak, it.strengthStrong)) }
        }

        Toast.makeText(this, "Menu: View + Menu na ovládači, tlačidlo Xbox alebo Späť", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        // Akcia z menu sa vykoná až keď emulácia znova beží
        window.decorView.post { pendingAction?.let { pendingAction = null; handleMenuAction(it) } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        saveProgress(wait = true)
    }

    // ------------------------------------------------------------------ ukladanie

    private fun restoreAutosave() {
        val file = Library.stateFile(game, "auto")
        if (!file.exists()) return
        thread(name = "restore") {
            runCatching { retroView.unserializeState(file.readBytes()) }
        }
    }

    /** Uloží SRAM (uloženia v hre) aj automatickú pozíciu. */
    private fun saveProgress(wait: Boolean) {
        if (!::retroView.isInitialized || !autosaveRestored) return
        val t =
            thread(name = "autosave") {
                runCatching {
                    writeAtomically(Library.sramFile(game), retroView.serializeSRAM())
                    writeAtomically(Library.stateFile(game, "auto"), retroView.serializeState())
                }
            }
        if (wait) t.join(2000)
    }

    private fun writeAtomically(file: File, bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeBytes(bytes)
        tmp.renameTo(file)
    }

    private fun openMenu() {
        if (isFinishing) return
        releaseAllButtons()
        saveProgress(wait = true)
        val intent =
            Intent(this, MenuActivity::class.java)
                .putExtra(MenuActivity.EXTRA_TITLE, game.title)
                .putExtra(MenuActivity.EXTRA_SYSTEM, game.system.fullName)
                .putExtra(MenuActivity.EXTRA_HAS_SLOT, Library.stateFile(game, "slot1").exists())
                .putExtra(MenuActivity.EXTRA_FAST, fastForward)
                .putExtra(MenuActivity.EXTRA_DISKS, runCatching { retroView.getAvailableDisks() }.getOrDefault(0))
        menuLauncher.launch(intent)
    }

    private fun handleMenuAction(action: String) {
        when (action) {
            MenuActivity.ACTION_SAVE ->
                thread {
                    val ok = runCatching { writeAtomically(Library.stateFile(game, "slot1"), retroView.serializeState()) }.isSuccess
                    toast(if (ok) "Pozícia uložená" else "Uloženie zlyhalo")
                }
            MenuActivity.ACTION_LOAD ->
                thread {
                    val f = Library.stateFile(game, "slot1")
                    val ok = f.exists() && runCatching { retroView.unserializeState(f.readBytes()) }.getOrDefault(false)
                    toast(if (ok) "Pozícia načítaná" else "Nepodarilo sa načítať pozíciu")
                }
            MenuActivity.ACTION_FAST -> {
                fastForward = !fastForward
                retroView.frameSpeed = if (fastForward) 2 else 1
                toast(if (fastForward) "Rýchlo vpred ZAP" else "Normálna rýchlosť")
            }
            MenuActivity.ACTION_FILTER -> {
                val f = Library.prefs.videoFilter.next()
                Library.prefs.videoFilter = f
                retroView.shader = f.shader
                toast("Obraz: ${f.label}")
            }
            MenuActivity.ACTION_DISK ->
                thread {
                    val count = retroView.getAvailableDisks()
                    if (count > 1) {
                        val next = (retroView.getCurrentDisk() + 1) % count
                        retroView.changeDisk(next)
                        toast("Disk ${next + 1} z $count")
                    }
                }
            MenuActivity.ACTION_RESET -> {
                retroView.reset()
                Library.stateFile(game, "auto").delete()
            }
            MenuActivity.ACTION_QUIT -> {
                saveProgress(wait = true)
                finish()
            }
        }
    }

    private fun toast(msg: String) = runOnUiThread { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    // ------------------------------------------------------------------ ovládanie

    private fun portFor(deviceId: Int): Int = ports.getOrPut(deviceId) { minOf(ports.size, 3) }

    /** Android má A/B a X/Y prehodené oproti RetroPadu – mapujeme podľa polohy tlačidiel. */
    private fun toRetro(keyCode: Int): Int =
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> keyCode
        }

    private fun isGameKey(keyCode: Int) =
        KeyEvent.isGamepadButton(keyCode) ||
            keyCode in
            setOf(
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            )

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::retroView.isInitialized) return super.dispatchKeyEvent(event)
        val code = event.keyCode
        if (code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_BUTTON_MODE || code == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) openMenu()
            return true
        }
        if (!isGameKey(code)) return super.dispatchKeyEvent(event)
        if (event.repeatCount > 0) return true

        val port = portFor(event.deviceId)
        val held = heldButtons.getOrPut(port) { mutableSetOf() }
        if (event.action == KeyEvent.ACTION_DOWN) held += code else held -= code

        // View + Menu naraz otvorí menu
        if (event.action == KeyEvent.ACTION_DOWN &&
            KeyEvent.KEYCODE_BUTTON_SELECT in held && KeyEvent.KEYCODE_BUTTON_START in held
        ) {
            openMenu()
            return true
        }
        retroView.sendKeyEvent(event.action, toRetro(code), port)
        return true
    }

    private fun releaseAllButtons() {
        heldButtons.forEach { (port, keys) ->
            keys.forEach { retroView.sendKeyEvent(KeyEvent.ACTION_UP, toRetro(it), port) }
            keys.clear()
        }
        triggerState.forEach { (port, state) ->
            if (state[0]) retroView.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_L2, port)
            if (state[1]) retroView.sendKeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BUTTON_R2, port)
            state.fill(false)
        }
        ports.values.toSet().forEach { port ->
            retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, 0f, 0f, port)
            retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f, port)
            retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, 0f, 0f, port)
        }
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (!::retroView.isInitialized ||
            event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK ||
            event.action != MotionEvent.ACTION_MOVE
        ) {
            return super.dispatchGenericMotionEvent(event)
        }
        val port = portFor(event.deviceId)
        val lx = deadzone(event.getAxisValue(MotionEvent.AXIS_X))
        val ly = deadzone(event.getAxisValue(MotionEvent.AXIS_Y))
        val hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)

        var dx = hatX
        var dy = hatY
        if (dx == 0f && dy == 0f && game.system.stickAsDpad) {
            dx = if (lx > 0.5f) 1f else if (lx < -0.5f) -1f else 0f
            dy = if (ly > 0.5f) 1f else if (ly < -0.5f) -1f else 0f
        }
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, dx, dy, port)
        retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, lx, ly, port)
        retroView.sendMotionEvent(
            GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
            deadzone(event.getAxisValue(MotionEvent.AXIS_Z)),
            deadzone(event.getAxisValue(MotionEvent.AXIS_RZ)),
            port,
        )

        // Xbox ovládač posiela LT/RT ako osi, nie ako tlačidlá
        val lt = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val rt = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        val state = triggerState.getOrPut(port) { BooleanArray(2) }
        updateTrigger(state, 0, lt, KeyEvent.KEYCODE_BUTTON_L2, port)
        updateTrigger(state, 1, rt, KeyEvent.KEYCODE_BUTTON_R2, port)
        return true
    }

    private fun updateTrigger(state: BooleanArray, index: Int, value: Float, keyCode: Int, port: Int) {
        val pressed = if (state[index]) value > 0.3f else value > 0.5f
        if (pressed != state[index]) {
            state[index] = pressed
            retroView.sendKeyEvent(if (pressed) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP, keyCode, port)
        }
    }

    private fun deadzone(v: Float) = if (abs(v) < 0.15f) 0f else v

    private fun rumble(port: Int, strength: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val deviceId = ports.entries.firstOrNull { it.value == port }?.key ?: return
        runCatching {
            val vibrator = InputDevice.getDevice(deviceId)?.vibratorManager?.defaultVibrator ?: return
            if (strength <= 0f) {
                vibrator.cancel()
            } else {
                val amp = (strength * 255).toInt().coerceIn(1, 255)
                vibrator.vibrate(VibrationEffect.createOneShot(5000, amp))
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
    }

    companion object {
        private const val EXTRA_GAME = "game"

        fun start(context: Context, game: Game) {
            context.startActivity(Intent(context, GameActivity::class.java).putExtra(EXTRA_GAME, game.id))
        }
    }
}
