package unsa.rfr.com

import android.app.Application

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        RefractorLog.init(this)
    }
}
