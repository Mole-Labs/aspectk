package sample.multiplatform.platform

import kotlin.js.Date

actual fun platformName(): String = "JS"

actual fun currentTimeMillis(): Long = Date.now().toLong()
