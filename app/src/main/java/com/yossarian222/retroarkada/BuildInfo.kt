package com.yossarian222.retroarkada

import android.content.Context

object BuildInfo {
    fun version(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
}
