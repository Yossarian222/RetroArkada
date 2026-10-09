package com.yossarian222.retroarkada

import android.app.Application
import com.yossarian222.retroarkada.data.Library
import com.yossarian222.retroarkada.server.UploadServer

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Library.init(this)
        UploadServer.start(this)
    }
}
