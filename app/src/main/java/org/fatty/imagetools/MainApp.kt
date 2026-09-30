package org.fatty.imagetools

import android.app.Application
import org.fatty.imagetools.log.ALog

class MainApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ALog.init(this)
    }
}
