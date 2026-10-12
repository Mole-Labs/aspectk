package sample.multiplatform

import sample.multiplatform.annotations.LogExecution

// A target in the application module itself: its advice lives in :core, two modules away
class AppStartup {
    @LogExecution(tag = "App", level = "INFO")
    fun warmUp(reason: String): String = "warm:$reason"
}
