package com.yossarian222.retroarkada.ui

import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object Palette {
    val bg = Color(0xFF0B0D17)
    val panel = Color(0xFF151A2E)
    val line = Color(0xFF262D4A)
    val text = Color(0xFFEEF0FF)
    val muted = Color(0xFF8E94B8)
    val accent = Color(0xFFFF4F9A)
    val accent2 = Color(0xFF7C5CFF)
    val logo = Brush.horizontalGradient(listOf(accent, accent2))
    val backdrop =
        Brush.radialGradient(
            listOf(Color(0xFF2A1F5C), Color.Transparent),
            center = androidx.compose.ui.geometry.Offset(0f, 0f),
            radius = 1400f,
        )
}

@Composable
fun ArkadaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
            darkColorScheme(
                background = Palette.bg,
                surface = Palette.panel,
                primary = Palette.accent,
                onBackground = Palette.text,
                onSurface = Palette.text,
            ),
        content = content,
    )
}

/** Tlačidlo/riadok, ktorý sa pri zameraní ovládačom zvýrazní. */
@Composable
fun FocusButton(
    text: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg by animateColorAsState(if (focused) Palette.text else Palette.panel, label = "bg")
    val fg = if (focused) Palette.bg else Palette.text
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "scale")
    Box(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(1.dp, if (focused) Color.Transparent else Palette.line, RoundedCornerShape(14.dp))
            .onFocusChanged { focused = it.isFocused }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        androidx.compose.foundation.layout.Column {
            Text(text, color = fg, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) Text(subtitle, color = if (focused) Palette.bg.copy(alpha = 0.7f) else Palette.muted, fontSize = 13.sp)
        }
    }
}

/**
 * Xbox ovládač v menu: A = potvrdiť, B = späť. Android to väčšinou robí sám,
 * ale nie na všetkých zariadeniach, preto to prekladáme ručne.
 */
fun ComponentActivity.translateGamepadKey(event: KeyEvent, dispatch: (KeyEvent) -> Boolean): Boolean {
    when (event.keyCode) {
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_START -> {
            return dispatch(
                KeyEvent(event.downTime, event.eventTime, event.action, KeyEvent.KEYCODE_DPAD_CENTER, event.repeatCount, event.metaState, event.deviceId, event.scanCode, event.flags, event.source),
            )
        }
        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onBackPressedDispatcher.onBackPressed()
            return true
        }
    }
    return dispatch(event)
}
