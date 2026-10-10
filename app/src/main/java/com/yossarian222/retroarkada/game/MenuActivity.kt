package com.yossarian222.retroarkada.game

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yossarian222.retroarkada.data.Library
import com.yossarian222.retroarkada.ui.ArkadaTheme
import com.yossarian222.retroarkada.ui.FocusButton
import com.yossarian222.retroarkada.ui.Palette
import com.yossarian222.retroarkada.ui.translateGamepadKey

/** Priehľadné menu nad hrou. Kým je otvorené, hra stojí. */
class MenuActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val system = intent.getStringExtra(EXTRA_SYSTEM).orEmpty()
        val hasSlot = intent.getBooleanExtra(EXTRA_HAS_SLOT, false)
        val fast = intent.getBooleanExtra(EXTRA_FAST, false)
        val disks = intent.getIntExtra(EXTRA_DISKS, 0)

        setContent {
            ArkadaTheme {
                Box(Modifier.fillMaxSize().background(Color(0xCC05060C)), contentAlignment = Alignment.CenterStart) {
                    Column(
                        Modifier
                            .padding(start = 64.dp)
                            .width(380.dp)
                            .background(Palette.bg.copy(alpha = 0.92f), RoundedCornerShape(24.dp))
                            .border(1.dp, Palette.line, RoundedCornerShape(24.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("PAUZA", color = Palette.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        Text(title, color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(system, color = Palette.muted, fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        val first = remember { FocusRequester() }
                        FocusButton("▶  Pokračovať", Modifier.fillMaxWidth().focusRequester(first), compact = true) { finishWith(ACTION_RESUME) }
                        FocusButton("💾  Uložiť pozíciu", Modifier.fillMaxWidth(), compact = true) { finishWith(ACTION_SAVE) }
                        if (hasSlot) FocusButton("📂  Načítať pozíciu", Modifier.fillMaxWidth(), compact = true) { finishWith(ACTION_LOAD) }
                        FocusButton(if (fast) "⏩  Vypnúť rýchlo vpred" else "⏩  Rýchlo vpred", Modifier.fillMaxWidth(), compact = true) { finishWith(ACTION_FAST) }
                        FocusButton("🖥  Filter: ${Library.prefs.videoFilter.label}", Modifier.fillMaxWidth(), compact = true) { finishWith(ACTION_FILTER) }
                        if (disks > 1) FocusButton("💿  Vymeniť disk", Modifier.fillMaxWidth(), compact = true) { finishWith(ACTION_DISK) }
                        FocusButton("↺  Reštartovať hru", Modifier.fillMaxWidth(), compact = true) { finishWith(ACTION_RESET) }
                        FocusButton("✕  Ukončiť hru", Modifier.fillMaxWidth(), compact = true, subtitle = "Postup sa uloží automaticky") { finishWith(ACTION_QUIT) }
                        LaunchedEffect(Unit) { first.requestFocus() }
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BUTTON_MODE || event.keyCode == KeyEvent.KEYCODE_MENU) {
            if (event.action == KeyEvent.ACTION_UP) finishWith(ACTION_RESUME)
            return true
        }
        return translateGamepadKey(event) { super.dispatchKeyEvent(it) }
    }

    private fun finishWith(action: String) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_ACTION, action))
        finish()
    }

    companion object {
        const val EXTRA_TITLE = "title"
        const val EXTRA_SYSTEM = "system"
        const val EXTRA_HAS_SLOT = "has_slot"
        const val EXTRA_FAST = "fast"
        const val EXTRA_DISKS = "disks"
        const val EXTRA_ACTION = "action"

        const val ACTION_RESUME = "resume"
        const val ACTION_SAVE = "save"
        const val ACTION_LOAD = "load"
        const val ACTION_FAST = "fast"
        const val ACTION_FILTER = "filter"
        const val ACTION_DISK = "disk"
        const val ACTION_RESET = "reset"
        const val ACTION_QUIT = "quit"
    }
}
