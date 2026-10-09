package com.yossarian222.retroarkada.ui

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.yossarian222.retroarkada.data.Library

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArkadaTheme { ArkadaApp() } }
    }

    override fun onResume() {
        super.onResume()
        Library.refresh()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean = translateGamepadKey(event) { super.dispatchKeyEvent(it) }
}
