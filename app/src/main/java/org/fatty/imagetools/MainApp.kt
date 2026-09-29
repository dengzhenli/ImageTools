package org.fatty.imagetools

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import org.fatty.imagetools.utils.ALog

class MainApp : Application() {
    override fun onCreate() {
        super.onCreate()
        ALog.init(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                ALog.removeExpiredData()
            }
        })
    }
}
