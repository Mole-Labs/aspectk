package sample.multiplatform

import android.app.Application

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        appContext = this
    }

    companion object {
        lateinit var appContext: AppApplication
            private set
    }
}
