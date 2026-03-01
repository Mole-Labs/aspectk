package sample.multiplatform.platform

import android.os.Build

actual fun platformName(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
