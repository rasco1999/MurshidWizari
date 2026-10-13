package com.muriq.murshid.iosparity

import android.app.Application
import com.muriq.murshid.iosparity.notifications.SchoolFirebasePush

class MurshidNativeApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        SchoolFirebasePush.initialize(this)
    }
}
