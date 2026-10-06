package com.chronotext.app

import android.app.Application
import com.chronotext.app.notify.Notifier

class ChronoApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 通知渠道尽早创建，确保首次触发前已就绪
        Notifier.ensureChannels(this)
    }
}
