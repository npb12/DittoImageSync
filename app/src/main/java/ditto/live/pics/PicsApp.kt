package ditto.live.pics

import android.app.Application

val prefs: Prefs by lazy {
    PicsApp.prefs!!
}

class PicsApp : Application() {
    companion object {
        var prefs: Prefs? = null
    }

    override fun onCreate() {
        prefs = Prefs(applicationContext)
        super.onCreate()
    }
}