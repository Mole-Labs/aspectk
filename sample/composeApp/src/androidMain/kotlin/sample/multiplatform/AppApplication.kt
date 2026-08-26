package sample.multiplatform

import android.app.Application
import sample.multiplatform.db.DatabaseContext

class AppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        DatabaseContext.appContext = this
    }
}
